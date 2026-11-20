package idle.miau.utility;

import net.minecraft.network.Packet;

public final class QueuedBacktrackPacket {
    private final Packet<?> packet;
    private final long timestamp;
    private final long sequence;

    public QueuedBacktrackPacket(Packet<?> packet, long timestamp, long sequence) {
        this.packet = packet;
        this.timestamp = timestamp;
        this.sequence = sequence;
    }

    public Packet<?> getPacket() { return packet; }
    public long getTimestamp() { return timestamp; }
    public long getSequence() { return sequence; }
}
