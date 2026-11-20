package idle.miau.lag.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import idle.miau.management.LagPacketPolicy;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.event.SendPacketEvent;
import idle.miau.utility.PacketUtils;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class LagRangePacketController {
    private enum DelayMode {
        TICK,
        MILLISECOND
    }

    private static final class DelayedPacket {
        private final Packet<?> packet;
        private final long enqueueTimeMs;
        private int ageTicks;

        private DelayedPacket(Packet<?> packet) {
            this.packet = packet;
            this.enqueueTimeMs = System.currentTimeMillis();
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Deque<DelayedPacket> delayQueue = new ConcurrentLinkedDeque<DelayedPacket>();

    private DelayMode delayMode = DelayMode.TICK;
    private int tickDelay;
    private long millisecondDelay;
    private boolean flushing;
    private Vec3 lastServerPosition;

    @Subscribe(priority = Priority.NORMAL)
    public synchronized void onSendPacket(SendPacketEvent event) {
        if (event.isCanceled() || flushing) {
            return;
        }
        if (mc.getNetHandler() == null) {
            clearWithoutSending();
            return;
        }

        if (handlePacket(event.getPacket())) {
            event.setCanceled(true);
        }
    }

    public synchronized boolean handleNoEventPacket(Packet<?> packet) {
        return !flushing && handlePacket(packet);
    }

    public synchronized boolean isFlushing() {
        return flushing;
    }

    private boolean handlePacket(Packet<?> packet) {
        releaseEligibleDelayPackets();
        if (!LagPacketPolicy.followsMovementTimeline(packet)) {
            return false;
        }

        boolean shouldDelay = delayMode == DelayMode.MILLISECOND
                ? millisecondDelay > 0L
                : tickDelay > 0;
        if (shouldDelay) {
            delayQueue.offer(new DelayedPacket(packet));
            return true;
        } else {
            updateLastServerPosition(packet);
        }
        return false;
    }

    public synchronized void setTickDelay(int ticks) {
        delayMode = DelayMode.TICK;
        tickDelay = Math.max(0, ticks);
        millisecondDelay = 0L;
    }

    public synchronized void setMillisecondDelay(long milliseconds) {
        delayMode = DelayMode.MILLISECOND;
        millisecondDelay = Math.max(0L, milliseconds);
        tickDelay = 0;
    }

    public synchronized void resetDelay() {
        delayMode = DelayMode.TICK;
        tickDelay = 0;
        millisecondDelay = 0L;
    }

    public synchronized void tickPost() {
        if (mc.getNetHandler() == null) {
            clearWithoutSending();
            return;
        }
        if (delayMode != DelayMode.MILLISECOND) {
            for (DelayedPacket packet : delayQueue) {
                packet.ageTicks++;
            }
        }
        releaseEligibleDelayPackets();
    }

    public synchronized Vec3 getLastServerPosition() {
        return lastServerPosition;
    }

    public synchronized void handleServerCorrection() {
        resetDelay();
        delayQueue.clear();
        lastServerPosition = null;
    }

    public synchronized void resetAll(boolean flushPackets) {
        resetDelay();
        if (flushPackets && mc.getNetHandler() != null) {
            releaseAllDelayPackets();
        } else {
            clearWithoutSending();
        }
    }

    private void releaseEligibleDelayPackets() {
        long now = System.currentTimeMillis();
        flushing = true;
        try {
            DelayedPacket queued;
            while ((queued = delayQueue.peek()) != null) {
                boolean canRelease = delayMode == DelayMode.MILLISECOND
                        ? millisecondDelay <= 0L || now - queued.enqueueTimeMs >= millisecondDelay
                        : tickDelay <= 0 || queued.ageTicks > tickDelay;
                if (!canRelease) {
                    break;
                }
                delayQueue.poll();
                PacketUtils.sendPacketNoEvent(queued.packet);
                updateLastServerPosition(queued.packet);
            }
        } finally {
            flushing = false;
        }
    }

    private void releaseAllDelayPackets() {
        flushing = true;
        try {
            DelayedPacket queued;
            while ((queued = delayQueue.poll()) != null) {
                PacketUtils.sendPacketNoEvent(queued.packet);
                updateLastServerPosition(queued.packet);
            }
        } finally {
            flushing = false;
        }
    }

    private void updateLastServerPosition(Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        if (movement.isMoving()) {
            lastServerPosition = new Vec3(
                    movement.getPositionX(),
                    movement.getPositionY(),
                    movement.getPositionZ());
        }
    }

    private void clearWithoutSending() {
        resetDelay();
        delayQueue.clear();
        flushing = false;
        lastServerPosition = null;
    }
}
