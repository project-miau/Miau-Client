package idle.miau.event;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class PreEntityVelocityEvent extends Event {
    public S12PacketEntityVelocity packet;

    public PreEntityVelocityEvent(S12PacketEntityVelocity packet) {
        this.packet = packet;
    }
}
