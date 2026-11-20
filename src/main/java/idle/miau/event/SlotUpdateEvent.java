package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class SlotUpdateEvent extends Event {
    public int slot;

    public SlotUpdateEvent(int slot) {
        this.slot = slot;
    }
}
