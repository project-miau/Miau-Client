package idle.miau.module.impl.movement.fly.modes;

import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class IntaveFireBall extends FlyMode {
    private static final String[] MOTION_MODES = {"Flat", "High"};
    private static final int MAX_REPLAY_TICKS = 60;
    private static final int MIN_RECOVERY_TICKS = 8;
    private static final int FLOATING_WINDOW_TICKS = 70;
    private static final int MAX_ACTIVE_TICKS = 600;
    private static final int MAX_TRACKED_TRANSACTIONS = 256;
    private static final double DESCENT_MOTION_THRESHOLD = -0.03125D;
    private static final double RETURN_HEIGHT_TOLERANCE = 0.05D;
    private static final long MAX_ACTIVE_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final long NO_TRANSACTION = Long.MIN_VALUE;

    public final SliderSetting motionMode = new SliderSetting(
            "Motion mode", 0, MOTION_MODES);

    private final Deque<C0FPacketConfirmTransaction> transactions = new ArrayDeque<>();
    private final Set<Long> pendingAcknowledgements = new LinkedHashSet<>();
    private boolean armed;
    private boolean active;
    private boolean previousPacketWasTransaction;
    private int activeTicks;
    private int replayCount;
    private int replayTicks;
    private long activatedAt;
    private long openingTransaction = NO_TRANSACTION;
    private long previousTransaction = NO_TRANSACTION;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double replayY;

    public IntaveFireBall(FlyModule owner) {
        super("IntaveFireBall", owner);
    }

    @Override
    public void updateVisibility(boolean active) {
        motionMode.setVisible(active, owner);
    }

    @Override
    public boolean usesMaxFlyTicksSetting() {
        return !isHighMotion();
    }

    @Override
    public synchronized void onEnable() {
        clearState();
        clearTransactionTracking();
    }

    @Override
    public synchronized void onDisable() {
        stop(true);
        clearTransactionTracking();
    }

    @Override
    public synchronized void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S32PacketConfirmTransaction) {
            trackTransaction((S32PacketConfirmTransaction) packet);
            return;
        }

        long precedingTransaction = previousPacketWasTransaction
                ? previousTransaction : NO_TRANSACTION;
        previousPacketWasTransaction = false;
        previousTransaction = NO_TRANSACTION;
        if (packet instanceof S08PacketPlayerPosLook) {
            stop(true);
            return;
        }
        if (!(packet instanceof S12PacketEntityVelocity) || mc == null || mc.thePlayer == null) {
            return;
        }

        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }

        stop(true);
        if (precedingTransaction == NO_TRANSACTION) {
            return;
        }
        double motionY = velocity.getMotionY() / 8000.0D;
        if (motionY <= 0.05D) {
            return;
        }

        velocityX = velocity.getMotionX() / 8000.0D;
        velocityY = motionY;
        velocityZ = velocity.getMotionZ() / 8000.0D;
        int recoveryTicks = recoveryTicks(velocityY);
        replayTicks = Math.max(1, Math.min(
                MAX_REPLAY_TICKS, FLOATING_WINDOW_TICKS - recoveryTicks));
        activeTicks = 0;
        replayCount = 0;
        replayY = mc.thePlayer.posY;
        activatedAt = System.nanoTime();
        if (pendingAcknowledgements.contains(precedingTransaction)) {
            armed = true;
            openingTransaction = precedingTransaction;
        } else {
            active = true;
        }
        event.setCanceled(true);
    }

    @Override
    public synchronized void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C0FPacketConfirmTransaction)) {
            return;
        }
        if (!active) {
            return;
        }
        if (expired()) {
            stop(true);
            return;
        }
        transactions.addLast((C0FPacketConfirmTransaction) event.getPacket());
        event.setCanceled(true);
    }

    @Override
    public synchronized void onDispatchPacket(DispatchPacketEvent event) {
        if (!(event.getPacket() instanceof C0FPacketConfirmTransaction)) {
            return;
        }
        long transaction = transactionKey(
                (C0FPacketConfirmTransaction) event.getPacket());
        pendingAcknowledgements.remove(transaction);
        if (!armed || transaction != openingTransaction) {
            return;
        }
        if (expired()) {
            stop(true);
            return;
        }
        if (mc == null || mc.thePlayer == null) {
            stop(true);
            return;
        }
        armed = false;
        active = true;
        activeTicks = 0;
        replayCount = 0;
        replayY = mc.thePlayer.posY;
        openingTransaction = NO_TRANSACTION;
    }

    @Override
    public synchronized void onPreUpdate(PreUpdateEvent event) {
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (!active || player == null) {
            return;
        }
        if (expired() || activeTicks >= MAX_ACTIVE_TICKS) {
            stop(true);
            return;
        }
        if (isHighMotion()) {
            applyHighMotion(player);
            return;
        }

        applyFlatMotion(player);
    }

    private void applyFlatMotion(EntityPlayerSP player) {
        activeTicks++;
        if (mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindSneak)) {
            return;
        }

        if (replayCount >= owner.getMaxFlyTicksValue()) {
            stop(true);
            return;
        }

        boolean grounded = player.onGround && player.isCollidedVertically;
        boolean returnedToArcHeight = player.motionY <= DESCENT_MOTION_THRESHOLD
                && player.posY <= replayY + RETURN_HEIGHT_TOLERANCE;
        if (replayCount > 0 && !grounded && !returnedToArcHeight) {
            return;
        }

        applyVelocity(player);
        player.fallDistance = 0.0F;
        replayY = player.posY;
        replayCount++;
    }

    private void applyHighMotion(EntityPlayerSP player) {
        if (player.onGround) {
            activeTicks = 0;
        }
        if (activeTicks >= replayTicks
                || (mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindSneak))) {
            return;
        }

        activeTicks++;
        applyVelocity(player);
        player.fallDistance = 0.0F;
    }

    @Override
    public synchronized void onWorldUnload(WorldEvent.Unload event) {
        clearState();
        clearTransactionTracking();
    }

    private void stop(boolean releaseTransactions) {
        active = false;
        armed = false;
        activeTicks = 0;
        replayCount = 0;
        replayTicks = 0;
        activatedAt = 0L;
        openingTransaction = NO_TRANSACTION;
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
        replayY = 0.0D;
        if (releaseTransactions) {
            releaseTransactions();
        } else {
            transactions.clear();
        }
    }

    private void clearState() {
        stop(false);
    }

    private void clearTransactionTracking() {
        pendingAcknowledgements.clear();
        previousPacketWasTransaction = false;
        previousTransaction = NO_TRANSACTION;
    }

    private void trackTransaction(S32PacketConfirmTransaction transaction) {
        if (transaction.func_148888_e()) {
            previousPacketWasTransaction = false;
            previousTransaction = NO_TRANSACTION;
            return;
        }
        long key = transactionKey(transaction);
        if (pendingAcknowledgements.size() >= MAX_TRACKED_TRANSACTIONS
                && !pendingAcknowledgements.contains(key)) {
            Iterator<Long> iterator = pendingAcknowledgements.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        pendingAcknowledgements.add(key);
        previousPacketWasTransaction = true;
        previousTransaction = key;
    }

    private boolean expired() {
        return System.nanoTime() - activatedAt >= MAX_ACTIVE_NANOS;
    }

    private boolean isHighMotion() {
        return (int) motionMode.getInput() == 1;
    }

    private void applyVelocity(EntityPlayerSP player) {
        player.motionX = velocityX;
        player.motionY = velocityY;
        player.motionZ = velocityZ;
    }

    private static int recoveryTicks(double initialVelocity) {
        double velocity = initialVelocity;
        int ticks = 0;
        while (velocity >= DESCENT_MOTION_THRESHOLD && ticks < FLOATING_WINDOW_TICKS) {
            velocity = (velocity - 0.08D) * 0.98D;
            ticks++;
        }
        return Math.max(MIN_RECOVERY_TICKS, ticks + 2);
    }

    private void releaseTransactions() {
        if (mc == null || mc.getNetHandler() == null) {
            transactions.clear();
            return;
        }
        C0FPacketConfirmTransaction transaction;
        while ((transaction = transactions.pollFirst()) != null) {
            PacketUtils.sendPacketNoEvent(transaction);
        }
    }

    private static long transactionKey(S32PacketConfirmTransaction transaction) {
        return transactionKey(transaction.getWindowId(), transaction.getActionNumber());
    }

    private static long transactionKey(C0FPacketConfirmTransaction transaction) {
        return transactionKey(transaction.getWindowId(), transaction.getUid());
    }

    private static long transactionKey(int windowId, short actionNumber) {
        return ((long) windowId << 16) | (actionNumber & 0xFFFFL);
    }
}
