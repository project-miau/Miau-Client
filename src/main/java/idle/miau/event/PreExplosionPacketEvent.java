package idle.miau.event;

import net.minecraft.network.play.server.S27PacketExplosion;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class PreExplosionPacketEvent extends Event {
    public S27PacketExplosion packet;

    public PreExplosionPacketEvent(S27PacketExplosion packet) {
        this.packet = packet;
    }

}
