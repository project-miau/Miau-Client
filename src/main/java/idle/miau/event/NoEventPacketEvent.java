package idle.miau.event;

import net.minecraft.network.Packet;
import idle.miau.mcp.event.Event;

public class NoEventPacketEvent extends Event {
    private Packet<?> packet;

    public NoEventPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
