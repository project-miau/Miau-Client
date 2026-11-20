package idle.miau.lag.handler;

import idle.miau.event.GameTickEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.queue.BiTrackLagNodeQueue;
import idle.miau.management.LagPacketPolicy;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.utility.BlinkUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@SuppressWarnings("DuplicatedCode")
public final class UnifiedLagHandler extends AbstractFastTrackProvider {

    private final @NotNull BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    private final @NotNull Set<Packet<?>> packetFastTrack = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );
    private volatile @Nullable Vec3 serverPosition;

    public void requestLag(final @NotNull LagRequest request) {
        queue.requestLag(request);
    }

    public int pollRequest(final @NotNull EnumLagDirection direction, final @NotNull LagRequest request) {
        return queue.pollRequest(direction, request);
    }

    public boolean releaseRequestIfDrained(final @NotNull EnumLagDirection direction,
                                           final @NotNull LagRequest request) {
        return queue.releaseRequestIfDrained(direction, request);
    }

    public void releaseExpiredPackets(
            final @NotNull EnumLagDirection direction,
            final @NotNull LagRequest owner,
            long maxAgeMs
    ) {
        queue.releaseExpiredPackets(direction, owner, maxAgeMs);
    }

    public void releaseLag(final @NotNull LagRequest owner) {
        queue.release(owner);
    }

    public int getQueuedOutboundPacketCount() {
        return queue.getQueuedPacketCount(EnumLagDirection.OUTBOUND);
    }

    public @Nullable Vec3 getLastReleasedServerPosition() {
        return serverPosition;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onSendPacket(final @NotNull SendPacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();
        final boolean fastTracked = consumeFastTrack(packet);

        if (event.isCanceled()) {
            return;
        }

        if (fastTracked) {
            updateServerPosition(packet);
            return;
        }

        if (LagPacketPolicy.isConnectionCritical(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.OUTBOUND)) {
            event.setCanceled(true);
            return;
        }

        updateServerPosition(packet);
    }

    @Subscribe
    public void onReceivePacket(final @NotNull ReceivePacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();
        final boolean fastTracked = consumeFastTrack(packet);

        if (event.isCanceled()) {
            return;
        }

        if (fastTracked) {
            return;
        }

        if (LagPacketPolicy.isConnectionCritical(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.INBOUND)) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onGameTick(final @NotNull GameTickEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        queue.tick(null, null);
    }

    @Subscribe
    public void onWorldUnload(final @NotNull WorldEvent.Unload event) {
        queue.clear();
        packetFastTrack.clear();
        clearServerPositions();
        BlinkUtils.discardPackets();
    }

    @Override
    public void forPacket(final @NotNull Packet<?> packet) {
        packetFastTrack.add(packet);
    }

    private boolean consumeFastTrack(final @NotNull Packet<?> packet) {
        return packetFastTrack.remove(packet);
    }

    private void updateServerPosition(final @NotNull Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }

        C03PacketPlayer movementPacket = (C03PacketPlayer) packet;
        if (!movementPacket.isMoving()) {
            return;
        }

        serverPosition = new Vec3(
                movementPacket.getPositionX(),
                movementPacket.getPositionY(),
                movementPacket.getPositionZ()
        );
    }

    private void clearServerPositions() {
        serverPosition = null;
    }

}
