package idle.miau.module.impl.combat;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.management.LagPacketPolicy;
import idle.miau.management.RealLagBuffer;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RealLag extends Module {
    private static final String[] DIRECTION_MODES = {"Outbound", "Inbound", "Both"};
    private final Random random = new Random();
    private final RealLagBuffer inbound = new RealLagBuffer(PacketUtils::receivePacketNoEvent);
    private final RealLagBuffer outbound = new RealLagBuffer(this::sendDelayedPacket);

    public final SliderSetting direction;
    public final SliderSetting delay;
    public final SliderSetting throttle;
    public final ButtonSetting safePackets;
    public final ButtonSetting combatOnly;
    public final SliderSetting combatRange;

    private NetHandlerPlayClient connection;
    private WorldClient world;
    private long resumeAt = Long.MIN_VALUE;
    private long lastCombatTime = 0L;
    private boolean flushing;
    private boolean replayingOutbound;

    public RealLag() {
        super("RealLag", category.combat, 0, "idle.miau.module.impl.player.RealLag");
        setDescription("FakeLag but Real !?!?!?!?!?");
        registerSetting(direction = new SliderSetting("Direction", 0, DIRECTION_MODES));
        registerSetting(delay = new SliderSetting("Delay", "ms", 150.0, 0.0, 1000.0, 10.0));
        registerSetting(throttle = new SliderSetting("Throttle", "ms", 0.0, 0.0, 1000.0, 25.0));
        registerSetting(safePackets = new ButtonSetting("Safe packets", true));
        registerSetting(combatOnly = new ButtonSetting("Combat only", true));
        registerSetting(combatRange = new SliderSetting("Combat range", "m", 12.0, 3.0, 30.0, 1.0));
    }

    @Override
    public synchronized void onEnable() {
        clear();
    }

    @Subscribe(priority = Priority.LOWEST)
    public synchronized void onSendPacket(SendPacketEvent event) {
        if (!event.isCanceled() && delayOutbound(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    public synchronized boolean handleNoEventPacket(Packet<?> packet) {
        return isEnabled() && delayOutbound(packet);
    }

    private boolean delayOutbound(Packet<?> packet) {
        if (flushing || replayingOutbound || !bindSession()) {
            return false;
        }
        if (packet == null || LagPacketPolicy.isConnectionCritical(packet)) {
            return false;
        }
        if (isNonCombatInteraction(packet)) {
            return false;
        }
        if (packet instanceof C02PacketUseEntity useEntity && useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
            Entity target = mc.theWorld != null ? useEntity.getEntityFromWorld(mc.theWorld) : null;
            if (target == null || !isBotOrNpc(target)) {
                lastCombatTime = System.currentTimeMillis();
            }
        }
        if (!isInCombat()) {
            outbound.flush();
            return false;
        }
        long now = System.nanoTime();
        if ((int) direction.getInput() == 1 || now < resumeAt || !mc.thePlayer.isEntityAlive()) {
            outbound.flush();
            return false;
        }
        return enqueue(outbound, packet, now);
    }

    private void sendDelayedPacket(Packet<?> packet) {
        replayingOutbound = true;
        try {
            PacketUtils.sendPacketNoEvent(packet);
        } finally {
            replayingOutbound = false;
        }
    }

    @Subscribe(priority = Priority.HIGHEST, receiveCanceled = true)
    public synchronized void onReceiveBoundary(ReceivePacketEvent event) {
        if (flushing || !bindSession()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (isLocalCombatEvent(packet)) {
            lastCombatTime = System.currentTimeMillis();
        }
        if (safePackets.isToggled() && affectsLocalMovement(packet)) {
            recover();
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public synchronized void onReceivePacket(ReceivePacketEvent event) {
        if (flushing || event.isCanceled() || !bindSession()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet == null || LagPacketPolicy.isConnectionCritical(packet)) {
            return;
        }
        if (!isInCombat()) {
            inbound.flush();
            return;
        }
        long now = System.nanoTime();
        if ((int) direction.getInput() == 0 || now < resumeAt || !mc.thePlayer.isEntityAlive()) {
            inbound.flush();
            return;
        }
        if (enqueue(inbound, packet, now)) {
            event.setCanceled(true);
        }
    }

    private boolean enqueue(RealLagBuffer buffer, Packet<?> packet, long now) {
        long pause = throttle.getInput() > 0.0 && random.nextInt(4) == 0
                ? TimeUnit.MILLISECONDS.toNanos((long) throttle.getInput()) : 0L;
        return buffer.enqueue(packet, now, TimeUnit.MILLISECONDS.toNanos((long) delay.getInput()), pause);
    }

    public boolean isBotOrNpc(Entity entity) {
        if (entity == null || (mc != null && mc.thePlayer != null && entity == mc.thePlayer)) {
            return false;
        }
        return ModuleManager.antiBot != null && ModuleManager.antiBot.isEnabled() && AntiBot.isBot(entity);
    }

    public boolean isInCombat() {
        if (!combatOnly.isToggled()) {
            return true;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        if (mc.thePlayer.hurtTime > 0) {
            lastCombatTime = System.currentTimeMillis();
            return true;
        }
        if (System.currentTimeMillis() - lastCombatTime < 3000L) {
            return true;
        }
        return isEnemyNearby();
    }

    private boolean isEnemyNearby() {
        if (mc.theWorld == null || mc.thePlayer == null || mc.theWorld.playerEntities == null) {
            return false;
        }
        double range = combatRange.getInput();
        double rangeSq = range * range;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) {
                continue;
            }
            if (player.isDead || player.getHealth() <= 0.0F) {
                continue;
            }
            if (mc.thePlayer.getDistanceSqToEntity(player) <= rangeSq) {
                if (!isBotOrNpc(player) && !Utils.isTeammate(player) && !Utils.isFriended(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isNonCombatInteraction(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity useEntity) {
            if (useEntity.getAction() == C02PacketUseEntity.Action.INTERACT
                    || useEntity.getAction() == C02PacketUseEntity.Action.INTERACT_AT) {
                return true;
            }
            if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK && mc != null && mc.theWorld != null) {
                Entity target = useEntity.getEntityFromWorld(mc.theWorld);
                if (target != null && isBotOrNpc(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLocalCombatEvent(Packet<?> packet) {
        return affectsLocalMovement(packet)
                || packet instanceof S06PacketUpdateHealth
                || (packet instanceof S19PacketEntityStatus status
                && mc.theWorld != null
                && status.getEntity(mc.theWorld) == mc.thePlayer
                && status.getOpCode() == 2);
    }

    private boolean affectsLocalMovement(Packet<?> packet) {
        return packet instanceof S12PacketEntityVelocity velocity
                && velocity.getEntityID() == mc.thePlayer.getEntityId()
                || packet instanceof S27PacketExplosion
                || packet instanceof S06PacketUpdateHealth health && health.getHealth() <= 0.0F;
    }

    @Override
    public synchronized void onUpdate() {
        if (!bindSession()) {
            return;
        }
        long now = System.nanoTime();
        if (!mc.thePlayer.isEntityAlive()) {
            flush();
            return;
        }
        if (!isInCombat()) {
            flush();
            return;
        }
        if ((int) direction.getInput() == 1) {
            outbound.flush();
        } else {
            outbound.release(now);
        }
        if ((int) direction.getInput() == 0) {
            inbound.flush();
        } else {
            inbound.release(now);
        }
    }

    private boolean bindSession() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null
                || mc.getNetHandler() == null
                || !mc.getNetHandler().getNetworkManager().isChannelOpen()
                || !mc.getNetHandler().isDoneLoadingTerrain()) {
            clear();
            return false;
        }
        if (connection != mc.getNetHandler() || world != mc.theWorld) {
            clear();
            connection = mc.getNetHandler();
            world = mc.theWorld;
        }
        return true;
    }

    private void recover() {
        resumeAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250L);
        flush();
    }

    public synchronized void handleServerCorrection() {
        if (isEnabled() && bindSession()) {
            outbound.discardObsoleteMovement();
            recover();
        } else {
            clear();
        }
    }

    public synchronized void clearConnection() {
        clear();
    }

    private void flush() {
        flushing = true;
        try {
            outbound.flush();
            inbound.flush();
        } finally {
            flushing = false;
        }
    }

    @Subscribe
    public synchronized void onWorld(WorldEvent event) {
        clear();
    }

    @Override
    public synchronized void onDisable() {
        if (bindSession()) {
            flush();
        }
        clear();
    }

    private void clear() {
        inbound.clear();
        outbound.clear();
        connection = null;
        world = null;
        resumeAt = Long.MIN_VALUE;
        lastCombatTime = 0L;
    }

    @Override
    public String getInfo() {
        int index = Math.max(0, Math.min((int) direction.getInput(), DIRECTION_MODES.length - 1));
        return DIRECTION_MODES[index] + " " + (int) delay.getInput() + "ms";
    }
}
