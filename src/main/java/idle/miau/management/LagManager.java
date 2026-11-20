package idle.miau.management;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.utility.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class LagManager {
    private final Minecraft mc = Minecraft.getMinecraft();
    public final Deque<LagPacket> packetQueue = new ConcurrentLinkedDeque<LagPacket>();
    private int tickDelay;
    private boolean flushing;
    private Vec3 lastPosition = new Vec3(0.0D, 0.0D, 0.0D);

    private synchronized void flushQueue() {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null || netHandler.getNetworkManager() == null
                || !netHandler.getNetworkManager().isChannelOpen()) {
            packetQueue.clear();
            return;
        }

        flushing = true;
        try {
            while (!packetQueue.isEmpty()) {
                LagPacket lagPacket = packetQueue.peek();
                if (!shouldRelease(tickDelay, lagPacket.delay)) {
                    break;
                }
                packetQueue.poll();
                PacketUtils.sendPacketNoEvent(lagPacket.packet);
                updateLastPosition(lagPacket.packet);
            }
        } finally {
            flushing = false;
        }
    }

    private void incrementDelays() {
        for (LagPacket lagPacket : packetQueue) {
            lagPacket.delay++;
        }
    }

    public synchronized boolean handlePacket(Packet<?> packet) {
        flushQueue();
        if (!LagPacketPolicy.followsMovementTimeline(packet)) {
            return false;
        }
        if (tickDelay > 0) {
            packetQueue.offer(new LagPacket(packet));
            return true;
        }
        updateLastPosition(packet);
        return false;
    }

    public synchronized void setDelay(int delay) {
        tickDelay = delay;
    }

    public synchronized int getDelay() {
        return tickDelay;
    }

    public synchronized boolean hasDelayedPackets() {
        return !packetQueue.isEmpty();
    }

    public synchronized Vec3 getLastPosition() {
        return lastPosition;
    }

    public synchronized boolean isFlushing() {
        return flushing;
    }

    public synchronized void clearConnection() {
        tickDelay = 0;
        flushing = false;
        packetQueue.clear();
        lastPosition = mc.thePlayer == null
                ? new Vec3(0.0D, 0.0D, 0.0D)
                : new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    }

    @Subscribe
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            clearConnection();
            return;
        }
        if (mc.thePlayer.isDead) {
            setDelay(0);
        }
        synchronized (this) {
            incrementDelays();
            flushQueue();
        }
    }

    private void updateLastPosition(Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        if (movement.isMoving()) {
            lastPosition = new Vec3(
                    movement.getPositionX(),
                    movement.getPositionY(),
                    movement.getPositionZ());
        }
    }

    static boolean shouldRelease(int tickDelay, int packetDelay) {
        return tickDelay <= 0 || packetDelay > tickDelay;
    }

    public static final class LagPacket {
        public final Packet<?> packet;
        public int delay;

        public LagPacket(Packet<?> packet) {
            this.packet = packet;
        }
    }
}
