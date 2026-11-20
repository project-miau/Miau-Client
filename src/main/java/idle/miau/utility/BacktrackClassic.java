package idle.miau.utility;

import idle.miau.Miau;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.RunGameLoopEvent;
import idle.miau.mcp.accessor.IAccessorS14PacketEntity;
import idle.miau.mcp.event.entity.player.AttackEntityEvent;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.Backtrack;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class BacktrackClassic {
    private static final String[] ESP_MODES = {"None", "Box"};
    private static final String[] COLOR_MODES = {"Custom", "HUD"};

    private final Backtrack owner;
    private final SliderSetting nextBacktrackDelay = new SliderSetting(
            "Next backtrack delay", "ms", 0, 0, 2000, 10);
    private final SliderSetting maxDelay = new SliderSetting(
            "Max delay", "ms", 80, 0, 2000, 10);
    private final SliderSetting minDelay = new SliderSetting(
            "Min delay", "ms", 80, 0, 2000, 10);
    private final SliderSetting distanceMin = new SliderSetting(
            "Distance min", 2, 0, 6, 0.1);
    private final SliderSetting distanceMax = new SliderSetting(
            "Distance max", 3, 0, 6, 0.1);
    private final ButtonSetting onlyNeed = new ButtonSetting("Smart", true);
    private final SliderSetting espMode = new SliderSetting("ESP mode", 1, ESP_MODES);
    private final SliderSetting colorMode = new SliderSetting("Color mode", 1, COLOR_MODES);
    private final ColorSetting espColor = new ColorSetting("ESP color", 0, 255, 0);
    private final ConcurrentLinkedQueue<QueuedBacktrackPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedDeque<BacktrackPosition> positions = new ConcurrentLinkedDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private EntityLivingBase target;
    private long delayForNextBacktrack;
    private int currentDelay;
    private boolean delayRolled;
    private boolean shouldRender = true;
    private boolean ignoreWholeTick;

    public BacktrackClassic(Backtrack owner) {
        this.owner = owner;
        owner.registerSetting(nextBacktrackDelay);
        owner.registerSetting(maxDelay);
        owner.registerSetting(minDelay);
        owner.registerSetting(distanceMin);
        owner.registerSetting(distanceMax);
        owner.registerSetting(onlyNeed);
        owner.registerSetting(espMode);
        owner.registerSetting(colorMode);
        owner.registerSetting(espColor);
        rollDelay();
    }

    public void setSettingsVisible(boolean visible) {
        nextBacktrackDelay.setVisible(visible, owner);
        maxDelay.setVisible(visible, owner);
        minDelay.setVisible(visible, owner);
        distanceMin.setVisible(visible, owner);
        distanceMax.setVisible(visible, owner);
        onlyNeed.setVisible(visible, owner);
        espMode.setVisible(visible, owner);
        colorMode.setVisible(visible && (int) espMode.getInput() == 1, owner);
        espColor.setVisible(visible
                && (int) espMode.getInput() == 1
                && (int) colorMode.getInput() == 0, owner);
    }

    public void onIncoming(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (event.isCanceled() || inboundBlinkActive()) {
            return;
        }
        if (!canOperate()) {
            clearPackets(true, true);
            target = null;
            return;
        }

        if (packetQueue.isEmpty() && replayQueueEmpty() && !shouldBacktrack()) {
            return;
        }

        if (Backtrack.shouldPassBacktrackImmediately(packet) || handleSafetyPacket(packet)) {
            return;
        }

        if (target != null && ((packet instanceof S14PacketEntity
                && ((IAccessorS14PacketEntity) packet).getEntityId() == target.getEntityId())
                || (packet instanceof S18PacketEntityTeleport
                && ((S18PacketEntityTeleport) packet).getEntityId() == target.getEntityId()))) {
            Vec3 truePosition = truePosition(target);
            if (truePosition != null) {
                positions.offerLast(new BacktrackPosition(
                        truePosition.xCoord,
                        truePosition.yCoord,
                        truePosition.zCoord,
                        System.currentTimeMillis()));
            }
        }

        event.setCanceled(true);
        packetQueue.offer(new QueuedBacktrackPacket(
                packet,
                System.currentTimeMillis(),
                sequence.incrementAndGet()));
    }

    public void onGameLoop(RunGameLoopEvent event) {
        tickSimple();
        ignoreWholeTick = false;
    }

    public void onQueuePacketClear() {
        if (Utils.nullCheck()) {
            updateDelayWindow();
        }
    }

    public void onAttack(AttackEntityEvent event) {
        if (event.entityPlayer != owner.getMinecraftPlayer() || !isSelected(event.target)) {
            return;
        }
        if (target != event.target) {
            clearPackets(true, true);
            reset();
        }
        target = (EntityLivingBase) event.target;
    }

    public void onRender(float partialTicks) {
        if (!shouldBacktrack() || !shouldRender || target == null || !hasTruePosition(target)
                || (int) espMode.getInput() != 1) {
            return;
        }
        Vec3 position = interpolatedPosition(target);
        double x = position.xCoord - owner.getRenderManager().viewerPosX;
        double y = position.yCoord - owner.getRenderManager().viewerPosY;
        double z = position.zCoord - owner.getRenderManager().viewerPosZ;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(
                x - target.posX,
                y - target.posY,
                z - target.posZ);
        Color color = (int) colorMode.getInput() == 1
                ? new Color(idle.miau.module.impl.render.ArrayListModule.getHudColor(0.0D))
                : new Color(espColor.getRGB());
        RenderUtils.drawLiquidBounceBacktrackBox(box, color);
    }

    public void reset(boolean replay) {
        clearPackets(replay, true);
        target = null;
        delayForNextBacktrack = 0L;
        delayRolled = false;
        ignoreWholeTick = false;
    }

    public int getCurrentDelay() {
        return currentDelay;
    }

    public EntityLivingBase getTarget() {
        return target != null && !target.isDead && getCurrentDelay() > 0 ? target : null;
    }

    public Vec3 getTargetPosition() {
        return getTarget() == null ? null : truePosition(target);
    }

    private void tickSimple() {
        if (!shouldBacktrack() || target == null || !hasTruePosition(target)) {
            clearPackets(true, true);
            return;
        }

        Vec3 truePosition = truePosition(target);
        double trueDistance = owner.getMinecraftPlayer().getDistance(
                truePosition.xCoord,
                truePosition.yCoord,
                truePosition.zCoord);
        double clientDistance = owner.getMinecraftPlayer().getDistance(
                target.posX,
                target.posY,
                target.posZ);
        if (inboundBlinkActive() || trueDistance > 6.0D
                || !BacktrackState.passesOnlyNeedCheck(
                onlyNeed.isToggled(), trueDistance, clientDistance)) {
            clearPackets(true, true);
            return;
        }

        shouldRender = true;
        Backtrack.shouldLag = true;
        if (isInsideDistance(distanceToBox(target.getEntityBoundingBox()))) {
            handlePacketsByAge();
        } else {
            handlePacketsByRange();
        }
    }

    private void handlePacketsByAge() {
        long cutoff = System.currentTimeMillis() - supposedDelay();
        releasePacketsThrough(cutoff);
        while (!positions.isEmpty() && positions.peekFirst().getTimestamp() < cutoff) {
            positions.pollFirst();
        }
    }

    private void handlePacketsByRange() {
        long timestamp = BacktrackState.findRangeTimestamp(
                positions,
                position -> distanceToTargetAt(position.asVec3()),
                minimumDistance(),
                maximumDistance());
        if (timestamp < 0L) {
            clearPackets(true, true);
            return;
        }
        releasePacketsThrough(timestamp);
        while (!positions.isEmpty() && positions.peekFirst().getTimestamp() < timestamp) {
            positions.pollFirst();
        }
    }

    private void updateDelayWindow() {
        boolean queuesEmpty = packetQueue.isEmpty() && replayQueueEmpty();
        if (!queuesEmpty) {
            delayRolled = false;
        }
        if (queuesEmpty && !delayRolled && !shouldBacktrack()) {
            long delay = Math.max(0L, (long) nextBacktrackDelay.getInput());
            delayForNextBacktrack = System.currentTimeMillis() > Long.MAX_VALUE - delay
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + delay;
            rollDelay();
            delayRolled = true;
        }
    }

    private void releasePacketsThrough(long timestamp) {
        QueuedBacktrackPacket queued;
        while ((queued = packetQueue.peek()) != null && queued.getTimestamp() <= timestamp) {
            packetQueue.poll();
            replayIncoming(queued.getPacket());
        }
    }

    private boolean handleSafetyPacket(Packet<?> packet) {
        if (packet instanceof S06PacketUpdateHealth
                && ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F) {
            clearPackets(true, true);
            return true;
        }
        if (packet instanceof S13PacketDestroyEntities && target != null) {
            for (int entityId : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (entityId == target.getEntityId()) {
                    clearPackets(true, true);
                    reset();
                    return true;
                }
            }
        }
        if (packet instanceof S1CPacketEntityMetadata && target != null
                && ((S1CPacketEntityMetadata) packet).getEntityId() == target.getEntityId()) {
            List<net.minecraft.entity.DataWatcher.WatchableObject> metadata =
                    ((S1CPacketEntityMetadata) packet).func_149376_c();
            if (metadata == null) {
                return true;
            }
            for (net.minecraft.entity.DataWatcher.WatchableObject value : metadata) {
                if (value.getDataValueId() == 6) {
                    try {
                        double health = Double.parseDouble(String.valueOf(value.getObject()));
                        if (!Double.isNaN(health) && health <= 0.0D) {
                            clearPackets(true, true);
                            reset();
                            return true;
                        }
                    } catch (NumberFormatException ignored) {
                        return true;
                    }
                }
            }
            return true;
        }
        return packet instanceof S19PacketEntityStatus
                && target != null
                && ((S19PacketEntityStatus) packet).getEntity(owner.getWorld()) == target;
    }

    private void clearPackets(boolean replay, boolean stopRendering) {
        QueuedBacktrackPacket queued;
        while ((queued = packetQueue.poll()) != null) {
            if (replay) {
                replayIncoming(queued.getPacket());
            }
        }
        positions.clear();
        Backtrack.shouldLag = false;
        if (stopRendering) {
            shouldRender = false;
            ignoreWholeTick = true;
        }
    }

    private void replayIncoming(Packet<?> packet) {
        if (Miau.backtrackPacketController != null) {
            Miau.backtrackPacketController.scheduleReplay(packet);
        } else {
            PacketUtils.receivePacketNoEvent(packet);
        }
    }

    private void reset() {
        target = null;
        Backtrack.shouldLag = false;
    }

    private boolean shouldBacktrack() {
        return owner.isEnabled()
                && canOperate()
                && target != null
                && owner.getMinecraftPlayer().getHealth() > 0.0F
                && (target.getHealth() > 0.0F || Float.isNaN(target.getHealth()))
                && !owner.isSpectator()
                && System.currentTimeMillis() >= delayForNextBacktrack
                && isSelected(target)
                && owner.getMinecraftPlayer().ticksExisted > 20
                && !ignoreWholeTick;
    }

    private boolean canOperate() {
        return Utils.nullCheck()
                && !owner.isSingleplayer()
                && owner.hasServerData();
    }

    private boolean inboundBlinkActive() {
        return ModuleManager.blink != null
                && ModuleManager.blink.isEnabled()
                && ModuleManager.blink.delaysInboundPackets();
    }

    private boolean isSelected(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == owner.getMinecraftPlayer()) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        if (!living.isEntityAlive() || living.isInvisible()) {
            return false;
        }
        if (AntiBot.isBot(entity)) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.isSpectator()) {
                return false;
            }
            if (Utils.isFriended(player)) {
                return false;
            }
            return !owner.isIgnoredTeam(player);
        }
        return isLiquidBounceMob(entity);
    }

    private static boolean isLiquidBounceMob(Entity entity) {
        return entity instanceof EntityMob
                || entity instanceof EntityVillager
                || entity instanceof EntitySlime
                || entity instanceof EntityGhast
                || entity instanceof EntityDragon;
    }

    private void rollDelay() {
        int minimum = (int) Math.min(minDelay.getInput(), maxDelay.getInput());
        int maximum = (int) Math.max(minDelay.getInput(), maxDelay.getInput());
        currentDelay = minimum == maximum
                ? minimum
                : minimum + (int) (Math.random() * (maximum - minimum + 1));
    }

    private int supposedDelay() {
        return currentDelay;
    }

    private double minimumDistance() {
        return Math.min(distanceMin.getInput(), distanceMax.getInput());
    }

    private double maximumDistance() {
        return Math.max(distanceMin.getInput(), distanceMax.getInput());
    }

    private boolean isInsideDistance(double distance) {
        return distance >= minimumDistance() && distance <= maximumDistance();
    }

    private double distanceToTargetAt(Vec3 position) {
        return target == null ? Double.MAX_VALUE : distanceToEntityAt(target, position);
    }

    private double distanceToEntityAt(Entity entity, Vec3 position) {
        return distanceToBox(entity.getEntityBoundingBox().offset(
                position.xCoord - entity.posX,
                position.yCoord - entity.posY,
                position.zCoord - entity.posZ));
    }

    private double distanceToBox(AxisAlignedBB box) {
        Vec3 eyes = owner.getMinecraftPlayer().getPositionEyes(1.0F);
        double x = Math.max(box.minX, Math.min(eyes.xCoord, box.maxX));
        double y = Math.max(box.minY, Math.min(eyes.yCoord, box.maxY));
        double z = Math.max(box.minZ, Math.min(eyes.zCoord, box.maxZ));
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    private boolean replayQueueEmpty() {
        return Miau.backtrackPacketController == null
                || Miau.backtrackPacketController.isReplayQueueEmpty();
    }

    private boolean hasTruePosition(Entity entity) {
        return Miau.backtrackPacketController != null
                && Miau.backtrackPacketController.hasTruePosition(entity);
    }

    private Vec3 truePosition(Entity entity) {
        return Miau.backtrackPacketController == null
                ? null
                : Miau.backtrackPacketController.getTruePosition(entity);
    }

    private Vec3 interpolatedPosition(Entity entity) {
        return Miau.backtrackPacketController.getInterpolatedPosition(entity);
    }
}
