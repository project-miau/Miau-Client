package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class PreSlotScrollEvent extends Event {
    public int slot;
    public int previousSlot;

    public PreSlotScrollEvent(int slot, int previousSlot) {
        this.slot = slot;
        this.previousSlot = previousSlot;
    }
}
