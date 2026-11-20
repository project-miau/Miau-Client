package idle.miau.event;

import net.minecraft.network.Packet;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class ReceivePacketEvent extends Event {
    private Packet<?> packet;
    private boolean protectedCancellationAllowed;

    public ReceivePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void allowProtectedCancellation() {
        protectedCancellationAllowed = true;
    }

    public boolean isProtectedCancellationAllowed() {
        return protectedCancellationAllowed;
    }
}
