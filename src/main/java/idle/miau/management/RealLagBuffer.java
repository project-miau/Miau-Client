package idle.miau.management;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0CPacketInput;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public final class RealLagBuffer {
    private static final int MAX_QUEUE_SIZE = 1000;
    private final Deque<DelayedPacket> packets = new ArrayDeque<>();
    private final Consumer<Packet<?>> replay;
    private long throttleUntil = Long.MIN_VALUE;

    public RealLagBuffer(Consumer<Packet<?>> replay) {
        this.replay = replay;
    }

    public boolean enqueue(Packet<?> packet, long now, long delay, long pause) {
        release(now);
        if (now >= throttleUntil && pause > 0L) {
            throttleUntil = now + pause;
        }
        long releaseAt = Math.max(now + delay, throttleUntil);
        DelayedPacket tail = packets.peekLast();
        if (tail != null) {
            releaseAt = Math.max(releaseAt, tail.releaseAt);
        } else if (releaseAt <= now) {
            return false;
        }
        if (packets.size() >= MAX_QUEUE_SIZE) {
            replay.accept(packets.removeFirst().packet);
        }
        packets.addLast(new DelayedPacket(packet, releaseAt));
        return true;
    }

    public void release(long now) {
        while (!packets.isEmpty() && packets.peekFirst().releaseAt <= now) {
            replay.accept(packets.removeFirst().packet);
        }
    }

    public void flush() {
        throttleUntil = Long.MIN_VALUE;
        while (!packets.isEmpty()) {
            replay.accept(packets.removeFirst().packet);
        }
    }

    public void discardObsoleteMovement() {
        packets.removeIf(queued -> queued.packet instanceof C03PacketPlayer
                || queued.packet instanceof C02PacketUseEntity
                || queued.packet instanceof C07PacketPlayerDigging digging
                && (digging.getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                || digging.getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK
                || digging.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK)
                || queued.packet instanceof C08PacketPlayerBlockPlacement placement
                && placement.getPlacedBlockDirection() != 255
                || queued.packet instanceof C0APacketAnimation
                || queued.packet instanceof C0CPacketInput);
    }

    public void clear() {
        packets.clear();
        throttleUntil = Long.MIN_VALUE;
    }

    private record DelayedPacket(Packet<?> packet, long releaseAt) {
    }
}
