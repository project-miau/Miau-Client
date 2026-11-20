package idle.miau.event;

import idle.miau.mcp.event.Event;

public final class WindowClickEvent extends Event {
    public final int windowId;
    public final int slotId;
    public final int mouseButton;
    public final int mode;

    public WindowClickEvent(int windowId, int slotId, int mouseButton, int mode) {
        this.windowId = windowId;
        this.slotId = slotId;
        this.mouseButton = mouseButton;
        this.mode = mode;
    }
}
