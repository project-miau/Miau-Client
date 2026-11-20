package idle.miau.module.impl.combat;

import idle.miau.event.AttackEvent;
import idle.miau.event.GameTickEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.UseItemEvent;
import idle.miau.management.LagPacketPolicy;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.ReflectionUtils;
import idle.miau.utility.TimerRangeCycle;
import idle.miau.utility.TimerRangePacketBuffer;
import idle.miau.utility.Utils;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static idle.miau.utility.TimerRangeCycle.Phase.FROZEN;
import static idle.miau.utility.TimerRangeCycle.Phase.RECOVERING;

public final class TimerRange extends Module {
    private static final String[] MODES = {"Regular", "Custom"};
    private static final double MAX_RANGE = 6.0D;
    private static final long FREEZE_POLL_NANOS = 10_000_000L;
    private static final Set<Packet<?>> OWNED_PACKETS = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>()));

    private final TimerRangeCycle cycle = new TimerRangeCycle();
    private final LongSupplier clock;
    private final LongConsumer pause;
    private final TimerRangePacketBuffer inbound;
    private final TimerRangePacketBuffer outbound;
    private final SliderSetting mode;
    private final SliderSetting minimumRange;
    private final SliderSetting duration;
    private final SliderSetting cooldown;
    private final SliderSetting lagTicks;
    private final SliderSetting tickVariation;
    private final SliderSetting customTimerTicks;
    private final SliderSetting customFreezeTicks;
    private final SliderSetting customMaxTargetHurtTime;
    private final SliderSetting customDelay;
    private final ButtonSetting customGroundOnly;
    private final ButtonSetting blockWhileFrozen;
    private final ButtonSetting leftMouseButton;
    private final ButtonSetting holdingWeapon;
    private final ButtonSetting notUsingItem;

    private EntityPlayer requestedTarget;
    private EntityPlayer cycleTarget;
    private EntityPlayerSP player;
    private WorldClient world;
    private NetHandlerPlayClient connection;
    private net.minecraft.util.Timer ownedTimer;
    private float previousTimerSpeed = 1.0F;
    private long frameTime;
    private long previousFrameTime;
    private long cooldownUntil;
    private long cycleCooldown;
    private long cycleGeneration;
    private long lastCycleStart = Long.MIN_VALUE;
    private long lastCycleEnd = Long.MIN_VALUE;
    private boolean correctionPending;
    private boolean resetPending;
    private boolean replaying;
    private boolean freezeBlockingActive;
    private ItemStack blockedItem;

    public TimerRange() {
        this(System::nanoTime, TimerRange::sendOwnedPacket, PacketUtils::receivePacketNoEvent);
    }

    TimerRange(LongSupplier clock, Consumer<Packet<?>> send, Consumer<Packet<?>> receive) {
        this(clock, send, receive, LockSupport::parkNanos);
    }

    TimerRange(LongSupplier clock, Consumer<Packet<?>> send, Consumer<Packet<?>> receive,
               LongConsumer pause) {
        super("TimerRange", category.combat);
        setDescription("Modifies your game timer speed to teleport you towards your opponent.");
        this.clock = clock;
        this.pause = pause;
        outbound = new TimerRangePacketBuffer(send);
        inbound = new TimerRangePacketBuffer(receive);
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        registerSetting(minimumRange = new SliderSetting(
                "Minimum distance", 3.0D, 1.0D, MAX_RANGE, 0.1D));
        registerSetting(duration = new SliderSetting(
                "Duration", "ms", 200.0D, 50.0D, 1000.0D, 50.0D));
        registerSetting(cooldown = new SliderSetting(
                "Cooldown", "ms", 500.0D, 0.0D, 5000.0D, 50.0D));
        registerSetting(lagTicks = new SliderSetting("Lag ticks", 3.0D, 0.0D, 20.0D, 1.0D));
        registerSetting(tickVariation = new SliderSetting(
                "Tick variation", 1.0D, 0.0D, 5.0D, 1.0D));
        registerSetting(customTimerTicks = new SliderSetting(
                "Timer ticks", 3.0D, 1.0D, 10.0D, 1.0D));
        registerSetting(customFreezeTicks = new SliderSetting(
                "Freeze ticks", 1.0D, 1.0D, 20.0D, 1.0D));
        registerSetting(customMaxTargetHurtTime = new SliderSetting(
                "Max target hurt time", 10.0D, 0.0D, 10.0D, 1.0D));
        registerSetting(customDelay = new SliderSetting(
                "Delay", "ms", 250.0D, 0.0D, 5000.0D, 50.0D));
        registerSetting(customGroundOnly = new ButtonSetting("Ground only", false));
        registerSetting(blockWhileFrozen = new ButtonSetting("Block while frozen", true));
        GroupSetting conditions = new GroupSetting("Conditions");
        registerSetting(conditions);
        registerSetting(leftMouseButton = new ButtonSetting(conditions, "Left mouse button", true));
        registerSetting(holdingWeapon = new ButtonSetting(conditions, "Holding a weapon", true));
        registerSetting(notUsingItem = new ButtonSetting(conditions, "Not using an item", true));
        guiUpdate();
    }

    @Override
    public synchronized void onEnable() {
        finishCycle(false, false, true);
    }

    @Override
    public synchronized void onDisable() {
        finishCycle(true, false, true);
    }

    @Override
    public void guiUpdate() {
        boolean custom = isCustomMode();
        duration.setVisible(!custom, this);
        cooldown.setVisible(!custom, this);
        customTimerTicks.setVisible(true, this);
        customFreezeTicks.setVisible(custom, this);
        customMaxTargetHurtTime.setVisible(custom, this);
        customDelay.setVisible(custom, this);
        customGroundOnly.setVisible(custom, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            guiUpdate();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode) {
            guiUpdate();
        }
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    public synchronized boolean isCycleActive() {
        return cycle.isActive();
    }

    public synchronized boolean hasRecentCycle(long windowMillis) {
        long now = clock.getAsLong();
        long window = Math.max(0L, windowMillis) * 1_000_000L;
        return cycle.isActive()
                || lastCycleStart != Long.MIN_VALUE && now - lastCycleStart <= window
                || lastCycleEnd != Long.MIN_VALUE && now - lastCycleEnd <= window;
    }

    public static boolean isLocalActionLockActive() {
        TimerRange instance = getEnabledInstance();
        return instance != null && instance.isCycleActive();
    }

    public static boolean isFreezeActionLockActive() {
        TimerRange instance = getEnabledInstance();
        if (instance == null) {
            return false;
        }
        synchronized (instance) {
            return instance.cycle.phase() == FROZEN || instance.cycle.phase() == RECOVERING;
        }
    }

    public static boolean isClientFreezeActive() {
        TimerRange instance = getEnabledInstance();
        if (instance == null) {
            return false;
        }
        synchronized (instance) {
            return instance.cycle.phase() == FROZEN;
        }
    }

    public static void beforeTimerUpdate(net.minecraft.util.Timer timer) {
        TimerRange instance = getEnabledInstance();
        if (instance != null) {
            instance.beginFrame(timer);
        }
    }

    public static void awaitClientFrame(net.minecraft.util.Timer timer) {
        TimerRange instance = getEnabledInstance();
        if (instance != null) {
            instance.awaitFrame(timer);
        }
    }

    public static void afterTimerUpdate(net.minecraft.util.Timer timer) {
        TimerRange instance = getEnabledInstance();
        if (instance == null) {
            return;
        }
        synchronized (instance) {
            if (instance.cycle.phase() == FROZEN) {
                timer.elapsedTicks = 0;
            } else if (instance.cycle.phase() == RECOVERING) {
                timer.elapsedTicks += instance.cycle.extraTicks(instance.frameTime, timer.elapsedTicks);
            }
        }
    }

    public static boolean shouldCancelClientTick() {
        return isClientFreezeActive();
    }

    public static void onClientTickComplete() {
        TimerRange instance = getEnabledInstance();
        if (instance != null) {
            synchronized (instance) {
                instance.cycle.completeTick();
            }
        }
    }

    public static void onClientTicksComplete() {
        TimerRange instance = getEnabledInstance();
        if (instance == null) {
            return;
        }
        synchronized (instance) {
            if (instance.cycle.isRecovered(instance.frameTime)) {
                if (instance.ownedTimer != null) {
                    float partial = instance.cycle.partialTicks(instance.frameTime);
                    instance.ownedTimer.elapsedPartialTicks = partial;
                    instance.ownedTimer.renderPartialTicks = partial;
                }
                instance.finishCycle(true, true, false);
            }
        }
    }

    public static boolean isOwnedPacket(Packet<?> packet) {
        return OWNED_PACKETS.contains(packet);
    }

    public static void onPacketDispatched(Packet<?> packet) {
        OWNED_PACKETS.remove(packet);
    }

    public static boolean shouldSilenceOutboundPacket(Packet<?> packet) {
        TimerRange instance = getEnabledInstance();
        if (instance == null || packet == null || isOwnedPacket(packet)
                || !LagPacketPolicy.followsMovementTimeline(packet)) {
            return false;
        }
        synchronized (instance) {
            if (!instance.cycle.isActive() || instance.replaying
                    || instance.correctionPending || instance.resetPending
                    || !instance.isSameSession()) {
                return false;
            }
            instance.outbound.add(packet);
            return true;
        }
    }

    public static boolean shouldBufferInboundPacket(Packet<?> packet) {
        TimerRange instance = getEnabledInstance();
        if (instance == null || packet == null || LagPacketPolicy.isConnectionCritical(packet)) {
            return false;
        }
        synchronized (instance) {
            if (!instance.cycle.isActive() || instance.cycle.phase() == RECOVERING
                    || instance.replaying || instance.correctionPending || instance.resetPending
                    || !instance.isSameSession()) {
                return false;
            }
            instance.inbound.add(packet);
            return true;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreAttack(PreAttackEvent event) {
        if (isFreezeActionLockActive()) {
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (isFreezeActionLockActive() && event.attacker == mc.thePlayer) {
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onUseItem(UseItemEvent event) {
        if (isFreezeActionLockActive()) {
            event.setCanceled(true);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public synchronized void onGameTick(GameTickEvent event) {
        if (cycle.isActive() || requestedTarget != null || hasTickConflict()
                || clock.getAsLong() < cooldownUntil) {
            return;
        }
        EntityPlayer target = findTarget();
        if (canActivate(target)) {
            requestedTarget = target;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public synchronized void onWorldLoad(WorldEvent.Load event) {
        finishCycle(false, false, true);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public synchronized void onWorldUnload(WorldEvent.Unload event) {
        finishCycle(false, false, true);
    }

    public synchronized void clearConnection() {
        requestedTarget = null;
        inbound.clear();
        outbound.clear();
        if (cycle.isActive() || ownedTimer != null) {
            resetPending = true;
            scheduleReset(false);
        }
    }

    public synchronized void handleServerCorrection() {
        requestedTarget = null;
        if (cycle.isActive()) {
            correctionPending = true;
            outbound.discardObsoleteMovement();
            scheduleReset(true);
        }
    }

    private void scheduleReset(boolean flush) {
        long generation = cycleGeneration;
        mc.addScheduledTask(() -> {
            synchronized (this) {
                if (generation == cycleGeneration && (resetPending || correctionPending)) {
                    finishCycle(flush && !resetPending, true, resetPending);
                }
            }
        });
    }

    private void awaitFrame(net.minecraft.util.Timer timer) {
        long generation;
        synchronized (this) {
            if (!isCustomMode() || cycle.phase() != FROZEN) {
                return;
            }
            generation = cycleGeneration;
        }
        while (!Thread.currentThread().isInterrupted()) {
            long remaining;
            synchronized (this) {
                if (generation != cycleGeneration || !isEnabled() || !isCustomMode()
                        || cycle.phase() != FROZEN || resetPending || correctionPending
                        || hasTickConflict() || !isSameSession() || needsRecovery()) {
                    break;
                }
                remaining = cycle.remainingFreezeNanos(clock.getAsLong());
            }
            if (remaining == 0L) {
                break;
            }
            pause.accept(Math.min(remaining, FREEZE_POLL_NANOS));
        }
        synchronized (this) {
            float speed = timer.timerSpeed;
            timer.timerSpeed = 0.0F;
            try {
                timer.updateTimer();
            } finally {
                timer.timerSpeed = speed;
            }
            if (generation != cycleGeneration || !isEnabled()) {
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                finishCycle(!resetPending, true, false);
                return;
            }
            beginFrame(timer);
        }
    }

    private synchronized void beginFrame(net.minecraft.util.Timer timer) {
        previousFrameTime = frameTime;
        frameTime = clock.getAsLong();
        if (resetPending || correctionPending) {
            finishCycle(!resetPending, true, resetPending);
        }
        if (cycle.isActive() && (!isSameSession() || hasTickConflict())) {
            finishCycle(isSameSession(), true, false);
        }
        if (!cycle.isActive()) {
            EntityPlayer target = requestedTarget;
            requestedTarget = null;
            if (target == null || hasTickConflict() || !canActivate(target)) {
                return;
            }
            startCycle(target, timer);
        }
        TimerRangeCycle.Phase previousPhase = cycle.phase();
        if (needsRecovery()) {
            cycle.recover(frameTime);
        }
        boolean recovering = cycle.advance(frameTime);
        if (cycle.phase() == RECOVERING && (recovering || previousPhase != RECOVERING)) {
            beginRecovery();
        } else if (cycle.phase() == FROZEN) {
            startFreezeBlocking();
        }
        if (cycle.isActive()) {
            timer.timerSpeed = previousTimerSpeed * cycle.timerSpeed(frameTime);
        }
    }

    private boolean needsRecovery() {
        return cycleTarget == null || cycleTarget.isDead
                || world.getEntityByID(cycleTarget.getEntityId()) != cycleTarget
                || inbound.needsRecovery() || outbound.needsRecovery();
    }

    private void startCycle(EntityPlayer target, net.minecraft.util.Timer timer) {
        player = mc.thePlayer;
        world = mc.theWorld;
        connection = mc.getNetHandler();
        cycleTarget = target;
        ownedTimer = timer;
        previousTimerSpeed = timer.timerSpeed;
        cycleCooldown = (long) (isCustomMode() ? customDelay.getInput() : cooldown.getInput())
                * 1_000_000L;
        lastCycleStart = frameTime;
        ++cycleGeneration;
        int freezeTicks = isCustomMode() ? (int) customFreezeTicks.getInput()
                : (int) Math.ceil(duration.getInput() / 50.0D);
        int variedLagTicks = varyTicks((int) lagTicks.getInput(), 0, 20);
        int variedFreezeTicks = varyTicks(freezeTicks, 1, 20);
        long startedAt = previousFrameTime == 0L ? frameTime : previousFrameTime;
        cycle.start(startedAt, variedLagTicks, variedFreezeTicks,
                (int) customTimerTicks.getInput(), timer.elapsedPartialTicks, previousTimerSpeed);
        if (ModuleManager.lagRange != null && ModuleManager.lagRange.isEnabled()) {
            ModuleManager.lagRange.suspendForTimerRange();
        }
    }

    private int varyTicks(int ticks, int minimum, int maximum) {
        int variation = (int) tickVariation.getInput();
        int adjustment = variation == 0 ? 0
                : ThreadLocalRandom.current().nextInt(-variation, variation + 1);
        return Math.max(minimum, Math.min(maximum, ticks + adjustment));
    }

    private void beginRecovery() {
        stopFreezeBlocking();
        flushInbound();
        if (!hasLivePlayer()) {
            outbound.discardObsoleteMovement();
            finishCycle(true, true, false);
        }
    }

    private void flushInbound() {
        inbound.flush();
        snapRenderState();
    }

    private void finishCycle(boolean flush, boolean applyCooldown, boolean clearCooldown) {
        boolean wasActive = cycle.isActive();
        boolean sameSession = isSameSession();
        boolean replayPackets = flush && sameSession;
        long completedCooldown = cycleCooldown;
        if (wasActive) {
            lastCycleEnd = clock.getAsLong();
        }
        cycle.reset();
        resetPending = !replayPackets;
        restoreTimer();
        replaying = true;
        try {
            if (replayPackets) {
                outbound.flush();
                inbound.flush();
                snapRenderState();
            }
            stopFreezeBlocking();
        } finally {
            inbound.clear();
            outbound.clear();
            replaying = false;
            correctionPending = false;
            resetPending = false;
            requestedTarget = null;
            cycleTarget = null;
            player = null;
            world = null;
            connection = null;
            cycleCooldown = 0L;
        }
        if (clearCooldown) {
            cooldownUntil = 0L;
        } else if (wasActive && applyCooldown) {
            cooldownUntil = clock.getAsLong() + completedCooldown;
        }
    }

    private void restoreTimer() {
        if (ownedTimer != null) {
            ownedTimer.timerSpeed = previousTimerSpeed;
            ownedTimer = null;
        }
        previousTimerSpeed = 1.0F;
    }

    private boolean hasLivePlayer() {
        return mc != null && mc.thePlayer != null && mc.theWorld != null
                && mc.thePlayer.isEntityAlive() && mc.getNetHandler() != null
                && mc.getNetHandler().isDoneLoadingTerrain()
                && mc.getNetHandler().getNetworkManager().isChannelOpen();
    }

    private boolean isSameSession() {
        return player != null && mc != null && mc.thePlayer == player
                && mc.theWorld == world && mc.getNetHandler() == connection
                && connection.getNetworkManager().isChannelOpen();
    }

    private boolean hasTickConflict() {
        return !hasLivePlayer() || mc.thePlayer.ridingEntity != null || mc.currentScreen != null
                || isEnabled(ModuleManager.timer) || isEnabled(ModuleManager.blink)
                || isEnabled(ModuleManager.realLag) || isEnabled(ModuleManager.backtrack);
    }

    private static boolean isEnabled(Module module) {
        return module != null && module.isEnabled();
    }

    private EntityPlayer findTarget() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            EntityPlayer pointed = (EntityPlayer) mc.objectMouseOver.entityHit;
            if (isEligibleTarget(pointed)) {
                return pointed;
            }
        }
        EntityPlayer closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (EntityPlayer target : mc.theWorld.playerEntities) {
            if (!isEligibleTarget(target)) {
                continue;
            }
            double distance = distanceToHitbox(target);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = target;
            }
        }
        return closest;
    }

    private boolean isEligibleTarget(EntityPlayer target) {
        if (!CombatTargeting.isTrackablePlayer(target) || target.isInvisible()
                || target.getHealth() <= 0.0F || !mc.thePlayer.canEntityBeSeen(target)) {
            return false;
        }
        int maximumHurtTime = isCustomMode() ? (int) customMaxTargetHurtTime.getInput() : 4;
        if (target.hurtTime > maximumHurtTime
                || !isCustomMode() && target.hurtResistantTime > 14) {
            return false;
        }
        double distance = distanceToHitbox(target);
        return distance >= minimumRange.getInput() && distance <= MAX_RANGE;
    }

    private boolean canActivate(EntityPlayer target) {
        if (!hasLivePlayer() || target == null || mc.currentScreen != null
                || mc.thePlayer.movementInput == null || !isEligibleTarget(target)) {
            return false;
        }
        if (leftMouseButton.isToggled() && (!Mouse.isCreated() || !Mouse.isButtonDown(0))) {
            return false;
        }
        if (holdingWeapon.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        if (notUsingItem.isToggled() && (mc.thePlayer.isUsingItem()
                || ModuleManager.autoblock != null
                && ModuleManager.autoblock.isLagBlockIntentActive())) {
            return false;
        }
        if ((!isCustomMode() || customGroundOnly.isToggled()) && !mc.thePlayer.onGround) {
            return false;
        }
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward < 0.0F || forward == 0.0F && strafe == 0.0F) {
            return false;
        }
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double moveX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double moveZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        return moveX * (target.posX - mc.thePlayer.posX)
                + moveZ * (target.posZ - mc.thePlayer.posZ) > 0.0D;
    }

    private double distanceToHitbox(Entity entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return Double.MAX_VALUE;
        }
        double dx = eyes.xCoord - Math.max(box.minX, Math.min(box.maxX, eyes.xCoord));
        double dy = eyes.yCoord - Math.max(box.minY, Math.min(box.maxY, eyes.yCoord));
        double dz = eyes.zCoord - Math.max(box.minZ, Math.min(box.maxZ, eyes.zCoord));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void snapRenderState() {
        if (!isSameSession()) {
            return;
        }
        syncRenderState(player);
        for (EntityPlayer other : world.playerEntities) {
            if (other instanceof EntityOtherPlayerMP) {
                ((EntityOtherPlayerMP) other).snapToServerPosition();
                syncRenderState(other);
            }
        }
    }

    private static void syncRenderState(Entity entity) {
        entity.lastTickPosX = entity.prevPosX = entity.posX;
        entity.lastTickPosY = entity.prevPosY = entity.posY;
        entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
        entity.prevRotationYaw = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
    }

    private void startFreezeBlocking() {
        if (freezeBlockingActive || !blockWhileFrozen.isToggled() || !isSameSession()
                || !Utils.holdingSword() || player.isUsingItem()) {
            return;
        }
        blockedItem = player.getHeldItem();
        if (blockedItem != null) {
            sendOwnedPacket(new C08PacketPlayerBlockPlacement(blockedItem));
            player.setItemInUse(blockedItem, blockedItem.getMaxItemUseDuration());
            ReflectionUtils.setItemInUse(true);
            freezeBlockingActive = true;
        }
    }

    private void stopFreezeBlocking() {
        if (!freezeBlockingActive) {
            return;
        }
        if (player != null && player.getItemInUse() == blockedItem) {
            player.clearItemInUse();
            if (isSameSession() && !resetPending) {
                sendOwnedPacket(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
        }
        if (mc != null && mc.thePlayer == player) {
            ReflectionUtils.setItemInUse(false);
        }
        blockedItem = null;
        freezeBlockingActive = false;
    }

    private static void sendOwnedPacket(Packet<?> packet) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            return;
        }
        OWNED_PACKETS.add(packet);
        try {
            PacketUtils.sendPacketNoEvent(packet);
        } catch (RuntimeException exception) {
            OWNED_PACKETS.remove(packet);
            throw exception;
        }
    }

    private boolean isCustomMode() {
        return (int) mode.getInput() == 1;
    }

    private static TimerRange getEnabledInstance() {
        TimerRange instance = ModuleManager.timerRange;
        return instance != null && instance.isEnabled() ? instance : null;
    }
}
