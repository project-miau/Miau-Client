package idle.miau.event;

import net.minecraft.network.Packet;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class SendPacketEvent extends Event {
    private Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
