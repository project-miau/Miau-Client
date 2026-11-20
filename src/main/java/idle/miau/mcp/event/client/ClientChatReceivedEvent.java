package idle.miau.mcp.event.client;

import net.minecraft.util.IChatComponent;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class ClientChatReceivedEvent extends Event {
    public final byte type;
    public IChatComponent message;

    public ClientChatReceivedEvent(byte type, IChatComponent message) {
        this.type = type;
        this.message = message;
    }
}
