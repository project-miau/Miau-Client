package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class KeyPressEvent extends Event {
    public char typedChar;
    public int keyCode;

    public KeyPressEvent(char typedChar, int keyCode) {
        this.typedChar = typedChar;
        this.keyCode = keyCode;
    }
}
