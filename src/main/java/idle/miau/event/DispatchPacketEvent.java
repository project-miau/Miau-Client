package idle.miau.event;

import net.minecraft.network.Packet;
import idle.miau.mcp.event.Event;

public class DispatchPacketEvent extends Event {
    private Packet<?> packet;

    public DispatchPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
