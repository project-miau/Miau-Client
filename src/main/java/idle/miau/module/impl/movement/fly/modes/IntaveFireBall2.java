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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class IntaveFireBall2 extends FlyMode {
    private static final int MAX_APEX_WAIT_TICKS = 8;
    private static final int MAX_TRACKED_TRANSACTIONS = 128;
    private static final double APEX_MOTION_Y = 0.03D;
    private static final double MIN_BOOST_MOTION_Y = 0.05D;
    private static final long NO_TRANSACTION = Long.MIN_VALUE;

    public final SliderSetting loops = new SliderSetting(
            "Intave loops", "", 2.0D, 1.0D, 10.0D, 1.0D, "Loops");

    private final Deque<C0FPacketConfirmTransaction> transactions = new ArrayDeque<>();
    private final Set<Long> pendingAcknowledgements = new LinkedHashSet<>();
    private MotionOperation operation = MotionOperation.NONE;
    private Phase phase = Phase.IDLE;
    private boolean previousPacketWasTransaction;
    private int appliedBoosts;
    private int remainingBoosts;
    private int waitTicks;
    private long openingTransaction = NO_TRANSACTION;
    private long previousTransaction = NO_TRANSACTION;
    private double velocityX;
    private double velocityY;
    private double velocityZ;

    public IntaveFireBall2(FlyModule owner) {
        super("IntaveFireBall2", owner);
    }

    @Override
    public boolean usesStopMotionSetting() {
        return false;
    }

    @Override
    public void updateVisibility(boolean active) {
        loops.setVisible(active, owner);
    }

    @Override
    public synchronized void onEnable() {
        resetBoost();
        transactions.clear();
        clearTransactionTracking();
    }

    @Override
    public synchronized void onDisable() {
        completeBoost(true);
        clearTransactionTracking();
    }

    @Override
    public synchronized void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S32PacketConfirmTransaction) {
            trackTransaction((S32PacketConfirmTransaction) packet);
            return;
        }

        long precedingTransaction = consumePrecedingTransaction();
        if (packet instanceof S08PacketPlayerPosLook) {
            abortBoost();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            captureVelocity(event, (S12PacketEntityVelocity) packet, precedingTransaction);
            return;
        }

        if (packet instanceof S27PacketExplosion) {
            captureExplosion((S27PacketExplosion) packet, precedingTransaction);
        }
    }

    @Override
    public synchronized void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C0FPacketConfirmTransaction) || !hasBoost()) {
            return;
        }

        C0FPacketConfirmTransaction transaction =
                (C0FPacketConfirmTransaction) event.getPacket();
        long key = transactionKey(transaction);
        if (phase == Phase.ARMED && key == openingTransaction) {
            return;
        }

        transactions.addLast(transaction);
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
        if (phase == Phase.ARMED && transaction == openingTransaction) {
            activateBoost();
        }
    }

    @Override
    public synchronized void onPreUpdate(PreUpdateEvent event) {
        if (phase == Phase.RELEASE_PENDING) {
            completeBoost(false);
            return;
        }

        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (phase != Phase.WAITING_APEX || player == null) {
            return;
        }

        waitTicks++;
        if (waitTicks < MAX_APEX_WAIT_TICKS && player.motionY > APEX_MOTION_Y) {
            return;
        }

        applyNextBoost(player);
    }

    @Override
    public synchronized void onWorldUnload(WorldEvent.Unload event) {
        resetBoost();
        transactions.clear();
        clearTransactionTracking();
    }

    private void captureVelocity(ReceivePacketEvent event,
                                 S12PacketEntityVelocity packet,
                                 long precedingTransaction) {
        if (mc == null || mc.thePlayer == null
                || packet.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }

        double motionX = packet.getMotionX() / 8000.0D;
        double motionY = packet.getMotionY() / 8000.0D;
        double motionZ = packet.getMotionZ() / 8000.0D;
        if (beginBoost(MotionOperation.SET, precedingTransaction,
                motionX, motionY, motionZ)) {
            event.setCanceled(true);
        }
    }

    private void captureExplosion(S27PacketExplosion packet,
                                  long precedingTransaction) {
        double motionX = packet.func_149149_c();
        double motionY = packet.func_149144_d();
        double motionZ = packet.func_149147_e();
        if (!beginBoost(MotionOperation.ADD, precedingTransaction,
                motionX, motionY, motionZ)) {
            return;
        }

        packet.setPlayerMotionX(0.0F);
        packet.setPlayerMotionY(0.0F);
        packet.setPlayerMotionZ(0.0F);
    }

    private boolean beginBoost(MotionOperation nextOperation,
                               long precedingTransaction,
                               double motionX, double motionY, double motionZ) {
        if (precedingTransaction == NO_TRANSACTION
                || !validBoost(motionX, motionY, motionZ)) {
            return false;
        }

        if (hasBoost()) {
            completeBoost(true);
        }

        operation = nextOperation;
        velocityX = motionX;
        velocityY = motionY;
        velocityZ = motionZ;
        appliedBoosts = 0;
        remainingBoosts = (int) loops.getInput();
        waitTicks = 0;
        if (pendingAcknowledgements.contains(precedingTransaction)) {
            phase = Phase.ARMED;
            openingTransaction = precedingTransaction;
        } else {
            activateBoost();
        }
        return true;
    }

    private void activateBoost() {
        phase = Phase.WAITING_APEX;
        waitTicks = 0;
        openingTransaction = NO_TRANSACTION;
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        boolean inLava = player != null && player.worldObj != null && player.isInLava();
        if (player != null && player.onGround
                && !player.isInWater() && !inLava && !player.isRiding()
                && (player.movementInput == null || !player.movementInput.jump)) {
            player.jump();
        }
    }

    private void completeBoost(boolean applyPendingBoost) {
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (applyPendingBoost && appliedBoosts == 0
                && hasBoost() && player != null) {
            applyBoost(player);
        }
        resetBoost();
        releaseTransactions();
    }

    private void abortBoost() {
        resetBoost();
        releaseTransactions();
    }

    private void applyBoost(EntityPlayerSP player) {
        if (operation == MotionOperation.SET) {
            player.motionX = velocityX;
            player.motionY = velocityY;
            player.motionZ = velocityZ;
        } else if (operation == MotionOperation.ADD) {
            player.motionX += velocityX;
            player.motionY += velocityY;
            player.motionZ += velocityZ;
        }
    }

    private void applyNextBoost(EntityPlayerSP player) {
        applyBoost(player);
        appliedBoosts++;
        remainingBoosts--;
        waitTicks = 0;
        phase = remainingBoosts > 0
                ? Phase.WAITING_APEX : Phase.RELEASE_PENDING;
    }

    private void resetBoost() {
        operation = MotionOperation.NONE;
        phase = Phase.IDLE;
        appliedBoosts = 0;
        remainingBoosts = 0;
        waitTicks = 0;
        openingTransaction = NO_TRANSACTION;
        velocityX = 0.0D;
        velocityY = 0.0D;
        velocityZ = 0.0D;
    }

    private void clearTransactionTracking() {
        pendingAcknowledgements.clear();
        previousPacketWasTransaction = false;
        previousTransaction = NO_TRANSACTION;
    }

    private long consumePrecedingTransaction() {
        long transaction = previousPacketWasTransaction
                ? previousTransaction : NO_TRANSACTION;
        previousPacketWasTransaction = false;
        previousTransaction = NO_TRANSACTION;
        return transaction;
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

    private void releaseTransactions() {
        boolean canSend = mc != null && mc.getNetHandler() != null;
        C0FPacketConfirmTransaction transaction;
        while ((transaction = transactions.pollFirst()) != null) {
            pendingAcknowledgements.remove(transactionKey(transaction));
            if (canSend) {
                PacketUtils.sendPacketNoEvent(transaction);
            }
        }
    }

    private boolean hasBoost() {
        return phase != Phase.IDLE;
    }

    private static boolean validBoost(double motionX, double motionY, double motionZ) {
        return Double.isFinite(motionX)
                && Double.isFinite(motionY)
                && Double.isFinite(motionZ)
                && motionY > MIN_BOOST_MOTION_Y;
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

    private enum MotionOperation {
        NONE,
        SET,
        ADD
    }

    private enum Phase {
        IDLE,
        ARMED,
        WAITING_APEX,
        RELEASE_PENDING
    }
}
