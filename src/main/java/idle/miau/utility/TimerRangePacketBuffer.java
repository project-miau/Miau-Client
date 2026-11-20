package idle.miau.utility;

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

public final class TimerRangePacketBuffer {
    private static final int RECOVERY_THRESHOLD = 2048;
    private final Deque<Packet<?>> packets = new ArrayDeque<>();
    private final Consumer<Packet<?>> replay;

    public TimerRangePacketBuffer(Consumer<Packet<?>> replay) {
        this.replay = replay;
    }

    public void add(Packet<?> packet) {
        packets.addLast(packet);
    }

    public boolean needsRecovery() {
        return packets.size() >= RECOVERY_THRESHOLD;
    }

    public void flush() {
        while (!packets.isEmpty()) {
            replay.accept(packets.removeFirst());
        }
    }

    public void discardObsoleteMovement() {
        packets.removeIf(packet -> packet instanceof C03PacketPlayer
                || packet instanceof C02PacketUseEntity
                || packet instanceof C0APacketAnimation
                || packet instanceof C0CPacketInput
                || packet instanceof C07PacketPlayerDigging digging
                && digging.getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
                || packet instanceof C08PacketPlayerBlockPlacement placement
                && placement.getPlacedBlockDirection() != 255);
    }

    public void clear() {
        packets.clear();
    }
}
