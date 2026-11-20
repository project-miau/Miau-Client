package idle.miau.mcp.event.client;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public class MouseEvent extends Event {
    public int button;
    public boolean buttonstate;
    public int dwheel;
    public int dx;
    public int dy;
    public int x;
    public int y;
    public long nanoseconds;

    public int getButton() { return button; }
    public boolean isButtonstate() { return buttonstate; }
    public int getDwheel() { return dwheel; }
    public int getDx() { return dx; }
    public int getDy() { return dy; }
    public int getX() { return x; }
    public int getY() { return y; }
    public long getNanoseconds() { return nanoseconds; }
}
