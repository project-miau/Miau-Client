package idle.miau.module.impl.movement.fly.modes;

import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
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

public final class GrimFireBall extends FlyMode {
    private static final String[] SUB_MODE_NAMES = {"GrimLatest", "3fmc"};
    private static final int MAX_APEX_WAIT_TICKS = 8;
    private static final int REBOOST_DELAY_TICKS = 8;
    private static final int THROW_TIMEOUT_TICKS = 10;
    private static final int FIREBALL_TIMEOUT_TICKS = 30;
    private static final int MAX_SEQUENCE_TICKS = 600;
    private static final int MAX_TRACKED_TRANSACTIONS = 128;
    private static final double APEX_MOTION_Y = 0.03D;
    private static final double REBOOST_MOTION_Y = 0.0D;
    private static final double MIN_BOOST_MOTION_Y = 0.05D;
    private static final long NO_TRANSACTION = Long.MIN_VALUE;

    public final SliderSetting mode = new SliderSetting(
            "Mode", 0, SUB_MODE_NAMES);
    public final SliderSetting loops = new SliderSetting(
            "Loops", 2.0D, 1.0D, 10.0D, 1.0D);

    private final IntaveFireBall2 threeFmc;
    private final Deque<C0FPacketConfirmTransaction> transactions = new ArrayDeque<>();
    private final Set<Long> pendingAcknowledgements = new LinkedHashSet<>();
    private MotionOperation operation = MotionOperation.NONE;
    private Phase phase = Phase.IDLE;
    private boolean previousPacketWasTransaction;
    private int remainingBoosts;
    private int sequenceTicks;
    private int waitTicks;
    private int reboostTicks;
    private int throwStage = -1;
    private int throwWaitTicks;
    private int fireballSlot = -1;
    private int previousSlot = -1;
    private float throwYaw;
    private long openingTransaction = NO_TRANSACTION;
    private long previousTransaction = NO_TRANSACTION;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int activeSubMode;
    private boolean running;

    public GrimFireBall(FlyModule owner) {
        super("GrimFireBall", owner);
        threeFmc = new IntaveFireBall2(owner);
        threeFmc.loops.setValue(1.0D);
        activeSubMode = selectedSubMode();
    }

    @Override
    public boolean usesStopMotionSetting() {
        return false;
    }

    @Override
    public void updateVisibility(boolean active) {
        synchronizeSubMode();
        mode.setVisible(active, owner);
        loops.setVisible(active && activeSubMode == 0, owner);
    }

    @Override
    public synchronized void onEnable() {
        activeSubMode = selectedSubMode();
        running = true;
        if (activeSubMode == 1) {
            enableThreeFmc();
            return;
        }
        enableLatest();
    }

    private void enableLatest() {
        reset();
        transactions.clear();
        clearTransactionTracking();
    }

    @Override
    public synchronized void onDisable() {
        if (activeSubMode == 1) {
            threeFmc.onDisable();
        } else {
            disableLatest();
        }
        running = false;
    }

    private void disableLatest() {
        applyPendingBoost();
        reset();
        releaseTransactions();
        clearTransactionTracking();
    }

    @Override
    public synchronized void onReceivePacket(ReceivePacketEvent event) {
        synchronizeSubMode();
        if (activeSubMode == 1) {
            threeFmc.onReceivePacket(event);
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof S32PacketConfirmTransaction) {
            trackTransaction((S32PacketConfirmTransaction) packet);
            return;
        }

        long precedingTransaction = consumePrecedingTransaction();
        if (packet instanceof S08PacketPlayerPosLook) {
            abort();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            captureVelocity(event, (S12PacketEntityVelocity) packet,
                    precedingTransaction);
            return;
        }

        if (packet instanceof S27PacketExplosion) {
            captureExplosion((S27PacketExplosion) packet, precedingTransaction);
        }
    }

    @Override
    public synchronized void onSendPacket(SendPacketEvent event) {
        synchronizeSubMode();
        if (activeSubMode == 1) {
            threeFmc.onSendPacket(event);
            return;
        }
        if (!(event.getPacket() instanceof C0FPacketConfirmTransaction)
                || !hasPendingBoost()) {
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
        synchronizeSubMode();
        if (activeSubMode == 1) {
            threeFmc.onDispatchPacket(event);
            return;
        }
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
        synchronizeSubMode();
        if (activeSubMode == 1) {
            threeFmc.onPreUpdate(event);
            return;
        }
        if (phase == Phase.IDLE) {
            return;
        }
        if (++sequenceTicks >= MAX_SEQUENCE_TICKS) {
            abort();
            return;
        }

        EntityPlayerSP player = player();
        if (player == null || player.isDead || player.ridingEntity != null) {
            abort();
            return;
        }

        switch (phase) {
            case WAITING_APEX:
                waitForApex(player);
                break;
            case RELEASE_PENDING:
                releaseBoost();
                break;
            case WAITING_REBOOST:
                waitForReboost(player);
                break;
            case THROWING_FIREBALL:
                waitForThrow(player);
                break;
            case WAITING_FEEDBACK:
                waitForFeedback();
                break;
            default:
                break;
        }
    }

    @Override
    public synchronized void onPreMotion(PreMotionEvent event) {
        synchronizeSubMode();
        if (activeSubMode == 1) {
            threeFmc.onPreMotion(event);
            return;
        }
        EntityPlayerSP player = player();
        if (player != null) {
            handleFireballThrow(event, player);
        }
    }

    @Override
    public synchronized void onWorldUnload(WorldEvent.Unload event) {
        if (activeSubMode == 1) {
            threeFmc.onWorldUnload(event);
            return;
        }
        reset();
        transactions.clear();
        clearTransactionTracking();
    }

    private void synchronizeSubMode() {
        int selected = selectedSubMode();
        if (selected == activeSubMode) {
            return;
        }
        if (running) {
            if (activeSubMode == 1) {
                threeFmc.onDisable();
            } else {
                disableLatest();
            }
        }
        activeSubMode = selected;
        if (running) {
            if (activeSubMode == 1) {
                enableThreeFmc();
            } else {
                enableLatest();
            }
        }
    }

    private void enableThreeFmc() {
        threeFmc.loops.setValue(1.0D);
        threeFmc.onEnable();
    }

    private int selectedSubMode() {
        return Math.max(0, Math.min(1, (int) mode.getInput()));
    }

    private void captureVelocity(ReceivePacketEvent event,
                                 S12PacketEntityVelocity packet,
                                 long precedingTransaction) {
        EntityPlayerSP player = player();
        if (player == null || packet.getEntityID() != player.getEntityId()) {
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
                || !validBoost(motionX, motionY, motionZ)
                || !canAcceptBoost()) {
            return false;
        }

        if (phase == Phase.IDLE) {
            remainingBoosts = Math.max(1, (int) loops.getInput());
            sequenceTicks = 0;
        }
        restoreSlot();
        resetThrow();
        clearBoost();
        releaseTransactions();
        operation = nextOperation;
        velocityX = motionX;
        velocityY = motionY;
        velocityZ = motionZ;
        if (pendingAcknowledgements.contains(precedingTransaction)) {
            phase = Phase.ARMED;
            openingTransaction = precedingTransaction;
        } else {
            activateBoost();
        }
        return true;
    }

    private boolean canAcceptBoost() {
        return phase == Phase.IDLE
                || phase == Phase.WAITING_REBOOST
                || phase == Phase.THROWING_FIREBALL
                || phase == Phase.WAITING_FEEDBACK;
    }

    private void activateBoost() {
        phase = Phase.WAITING_APEX;
        waitTicks = 0;
        openingTransaction = NO_TRANSACTION;
        EntityPlayerSP player = player();
        if (canJump(player) && !jumpRequested(player)) {
            player.jump();
        }
    }

    private void waitForApex(EntityPlayerSP player) {
        waitTicks++;
        if (waitTicks < MAX_APEX_WAIT_TICKS
                && player.motionY > APEX_MOTION_Y) {
            return;
        }

        applyBoost(player);
        remainingBoosts--;
        phase = Phase.RELEASE_PENDING;
        waitTicks = 0;
    }

    private void releaseBoost() {
        clearBoost();
        releaseTransactions();
        if (remainingBoosts <= 0) {
            reset();
            return;
        }

        phase = Phase.WAITING_REBOOST;
        reboostTicks = 0;
        resetThrow();
    }

    private void waitForReboost(EntityPlayerSP player) {
        if (player.onGround || player.isInWater()
                || player.worldObj != null && player.isInLava()
                || player.movementInput != null && player.movementInput.sneak) {
            abort();
            return;
        }
        if (++reboostTicks < REBOOST_DELAY_TICKS
                || player.motionY > REBOOST_MOTION_Y) {
            return;
        }
        if (prepareFireball(player)) {
            phase = Phase.THROWING_FIREBALL;
        }
    }

    private void waitForThrow(EntityPlayerSP player) {
        maintainFireballSlot(player);
        if (++throwWaitTicks > THROW_TIMEOUT_TICKS) {
            abort();
        }
    }

    private void waitForFeedback() {
        if (++throwWaitTicks > FIREBALL_TIMEOUT_TICKS) {
            abort();
        }
    }

    private void handleFireballThrow(PreMotionEvent event, EntityPlayerSP player) {
        if (phase != Phase.THROWING_FIREBALL
                || throwStage < 0 || throwStage > 1) {
            return;
        }
        event.setRotations(throwYaw, 90.0F);
        if (throwStage++ == 0) {
            return;
        }

        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null || heldItem.getItem() != Items.fire_charge
                || mc.getNetHandler() == null) {
            abort();
            return;
        }
        mc.getNetHandler().addToSendQueue(
                new C08PacketPlayerBlockPlacement(heldItem));
        player.swingItem();
        restoreSlot();
        throwStage = 2;
        throwWaitTicks = 0;
        phase = Phase.WAITING_FEEDBACK;
    }

    private boolean prepareFireball(EntityPlayerSP player) {
        int slot = findFireball(player);
        if (slot < 0) {
            return false;
        }
        previousSlot = player.inventory.currentItem;
        fireballSlot = slot;
        throwYaw = player.rotationYaw - 180.0F;
        player.inventory.currentItem = slot;
        throwStage = 0;
        throwWaitTicks = 0;
        return true;
    }

    private int findFireball(EntityPlayerSP player) {
        if (player.inventory == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.fire_charge) {
                return slot;
            }
        }
        return -1;
    }

    private void maintainFireballSlot(EntityPlayerSP player) {
        if (fireballSlot >= 0 && player.inventory != null) {
            player.inventory.currentItem = fireballSlot;
        }
    }

    private void restoreSlot() {
        EntityPlayerSP player = player();
        if (player != null && player.inventory != null && previousSlot >= 0) {
            player.inventory.currentItem = previousSlot;
        }
        previousSlot = -1;
        fireballSlot = -1;
    }

    private void resetThrow() {
        throwStage = -1;
        throwWaitTicks = 0;
        fireballSlot = -1;
        previousSlot = -1;
        throwYaw = 0.0F;
    }

    private void applyPendingBoost() {
        if (phase != Phase.ARMED && phase != Phase.WAITING_APEX) {
            return;
        }
        EntityPlayerSP player = player();
        if (player != null) {
            applyBoost(player);
        }
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

    private void abort() {
        reset();
        releaseTransactions();
    }

    private void reset() {
        restoreSlot();
        clearBoost();
        resetThrow();
        phase = Phase.IDLE;
        remainingBoosts = 0;
        sequenceTicks = 0;
        reboostTicks = 0;
    }

    private void clearBoost() {
        operation = MotionOperation.NONE;
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

    private boolean hasPendingBoost() {
        return phase == Phase.ARMED
                || phase == Phase.WAITING_APEX
                || phase == Phase.RELEASE_PENDING;
    }

    private EntityPlayerSP player() {
        return mc == null ? null : mc.thePlayer;
    }

    private static boolean canJump(EntityPlayerSP player) {
        if (player == null || !player.onGround || !player.isCollidedVertically
                || player.isInWater() || player.isRiding()) {
            return false;
        }
        return player.worldObj == null || !player.isInLava();
    }

    private static boolean jumpRequested(EntityPlayerSP player) {
        return player != null && player.movementInput != null
                && player.movementInput.jump;
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
        RELEASE_PENDING,
        WAITING_REBOOST,
        THROWING_FIREBALL,
        WAITING_FEEDBACK
    }
}
