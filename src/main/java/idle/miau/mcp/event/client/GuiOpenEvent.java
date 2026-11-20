package idle.miau.mcp.event.client;

import net.minecraft.client.gui.GuiScreen;
import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class GuiOpenEvent extends Event {
    public GuiScreen gui;

    public GuiOpenEvent(GuiScreen gui) {
        this.gui = gui;
    }
}
