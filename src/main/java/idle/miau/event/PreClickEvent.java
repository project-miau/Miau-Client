package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class PreClickEvent extends Event {
    public static final int LEFT_CLICK = 0;
    public static final int RIGHT_CLICK = 1;

    private final int button;

    public PreClickEvent(int button) {
        this.button = button;
    }

    public int getButton() {
        return button;
    }

    public boolean isLeftClick() {
        return button == LEFT_CLICK;
    }

    public boolean isRightClick() {
        return button == RIGHT_CLICK;
    }
}
