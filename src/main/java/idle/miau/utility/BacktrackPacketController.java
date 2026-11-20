package idle.miau.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.event.GameTickEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.RunGameLoopEvent;
import idle.miau.mcp.accessor.IAccessorS14PacketEntity;
import idle.miau.mcp.accessor.IBacktrackEntityState;
import idle.miau.module.ModuleManager;
import idle.miau.utility.PacketUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BacktrackPacketController {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<ReplayPacket> replayQueue = new ConcurrentLinkedQueue<>();
    private long lastFrameTime = System.currentTimeMillis();
    private double frameInterpolationDelta;

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (BacktrackPacketPolicy.isSessionBoundaryPacket(event.getPacket())
                || BacktrackPacketPolicy.isTimelineResetPacket(event.getPacket())) {
            replayQueue.clear();
        }
        if (!isTrackingActive() || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        updateTruePosition(event.getPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onGameTick(GameTickEvent event) {
        if (!isTrackingActive() || mc.theWorld == null) {
            return;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase) {
                IBacktrackEntityState state = state(entity);
                if (!state.hasBacktrackTruePosition()) {
                    setTruePosition(state, entity.getPositionVector(), false);
                }
            }
        }
    }

    @Subscribe(priority = Priority.LOW)
    public void onRunGameLoop(RunGameLoopEvent event) {
        long now = System.currentTimeMillis();
        long deltaTime = Math.max(0L, now - lastFrameTime);
        lastFrameTime = now;
        if (!isTrackingActive() && replayQueue.isEmpty()) {
            return;
        }
        frameInterpolationDelta = Math.min(deltaTime / 60.0D, 1.0D);
        drainReplayQueue();
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        replayQueue.clear();
    }

    public void scheduleReplay(Packet<?> packet) {
        NetHandlerPlayClient handler = mc.getNetHandler();
        WorldClient world = mc.theWorld;
        if (packet != null && handler != null && world != null
                && handler.isDoneLoadingTerrain()
                && !BacktrackPacketPolicy.isSessionBoundaryPacket(packet)
                && !BacktrackPacketPolicy.isTimelineResetPacket(packet)) {
            replayQueue.offer(new ReplayPacket(packet, handler, world));
        }
    }

    public boolean isReplayQueueEmpty() {
        return replayQueue.isEmpty();
    }

    public void clearReplayQueue() {
        replayQueue.clear();
    }

    private boolean isTrackingActive() {
        return ModuleManager.backtrack != null && ModuleManager.backtrack.isEnabled();
    }

    public Vec3 getTruePosition(Entity entity) {
        IBacktrackEntityState state = state(entity);
        return state.hasBacktrackTruePosition()
                ? new Vec3(state.getBacktrackTrueX(), state.getBacktrackTrueY(), state.getBacktrackTrueZ())
                : entity.getPositionVector();
    }

    public Vec3 getInterpolatedPosition(Entity entity) {
        IBacktrackEntityState state = state(entity);
        return state.hasBacktrackTruePosition()
                ? new Vec3(state.getBacktrackLerpX(), state.getBacktrackLerpY(), state.getBacktrackLerpZ())
                : entity.getPositionVector();
    }

    public boolean hasTruePosition(Entity entity) {
        return entity != null && state(entity).hasBacktrackTruePosition();
    }

    private void updateTruePosition(Packet<?> packet) {
        Entity entity = null;
        Vec3 position = null;
        boolean ignoreInterpolation = false;

        if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer spawn = (S0CPacketSpawnPlayer) packet;
            entity = mc.theWorld.getEntityByID(spawn.getEntityID());
            position = new Vec3(spawn.getX() / 32.0D, spawn.getY() / 32.0D, spawn.getZ() / 32.0D);
        } else if (packet instanceof S0FPacketSpawnMob) {
            S0FPacketSpawnMob spawn = (S0FPacketSpawnMob) packet;
            entity = mc.theWorld.getEntityByID(spawn.getEntityID());
            position = new Vec3(spawn.getX() / 32.0D, spawn.getY() / 32.0D, spawn.getZ() / 32.0D);
        } else if (packet instanceof S14PacketEntity) {
            entity = mc.theWorld.getEntityByID(((IAccessorS14PacketEntity) packet).getEntityId());
            if (entity != null) {
                IBacktrackEntityState state = state(entity);
                if (!state.hasBacktrackTruePosition()) {
                    setTruePosition(state, entity.getPositionVector(), false);
                }
                S14PacketEntity move = (S14PacketEntity) packet;
                position = new Vec3(
                        state.getBacktrackTrueX() + move.func_149062_c() / 32.0D,
                        state.getBacktrackTrueY() + move.func_149061_d() / 32.0D,
                        state.getBacktrackTrueZ() + move.func_149064_e() / 32.0D);
                setTrueCoordinates(state, position);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            entity = mc.theWorld.getEntityByID(teleport.getEntityId());
            position = new Vec3(teleport.getX() / 32.0D, teleport.getY() / 32.0D, teleport.getZ() / 32.0D);
            ignoreInterpolation = true;
        }

        if (entity instanceof EntityLivingBase && position != null && !(packet instanceof S14PacketEntity)) {
            setTruePosition(state(entity), position, ignoreInterpolation);
        }
    }

    public void interpolatePosition(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        IBacktrackEntityState state = state(entity);
        if (!state.hasBacktrackTruePosition()) {
            return;
        }
        state.setBacktrackLerpX(state.getBacktrackLerpX()
                + (state.getBacktrackTrueX() - state.getBacktrackLerpX()) * frameInterpolationDelta);
        state.setBacktrackLerpY(state.getBacktrackLerpY()
                + (state.getBacktrackTrueY() - state.getBacktrackLerpY()) * frameInterpolationDelta);
        state.setBacktrackLerpZ(state.getBacktrackLerpZ()
                + (state.getBacktrackTrueZ() - state.getBacktrackLerpZ()) * frameInterpolationDelta);
    }

    private void drainReplayQueue() {
        NetHandlerPlayClient handler = mc.getNetHandler();
        WorldClient world = mc.theWorld;
        if (handler == null || world == null || !handler.isDoneLoadingTerrain()) {
            replayQueue.clear();
            return;
        }
        ReplayPacket replay;
        while ((replay = replayQueue.poll()) != null) {
            if (replay.handler != handler || replay.world != world
                    || BacktrackPacketPolicy.isSessionBoundaryPacket(replay.packet)
                    || BacktrackPacketPolicy.isTimelineResetPacket(replay.packet)) {
                continue;
            }
            PacketUtils.receivePacketNoEvent(replay.packet);
        }
    }

    private static final class ReplayPacket {
        private final Packet<?> packet;
        private final NetHandlerPlayClient handler;
        private final WorldClient world;

        private ReplayPacket(Packet<?> packet, NetHandlerPlayClient handler, WorldClient world) {
            this.packet = packet;
            this.handler = handler;
            this.world = world;
        }
    }

    private static IBacktrackEntityState state(Entity entity) {
        return (IBacktrackEntityState) entity;
    }

    private static void setTruePosition(IBacktrackEntityState state, Vec3 position, boolean ignoreInterpolation) {
        setTrueCoordinates(state, position);
        if (!ignoreInterpolation) {
            state.setBacktrackLerpX(position.xCoord);
            state.setBacktrackLerpY(position.yCoord);
            state.setBacktrackLerpZ(position.zCoord);
        }
        state.setBacktrackTruePosition(true);
    }

    private static void setTrueCoordinates(IBacktrackEntityState state, Vec3 position) {
        state.setBacktrackTrueX(position.xCoord);
        state.setBacktrackTrueY(position.yCoord);
        state.setBacktrackTrueZ(position.zCoord);
        state.setBacktrackTruePosition(true);
    }
}
