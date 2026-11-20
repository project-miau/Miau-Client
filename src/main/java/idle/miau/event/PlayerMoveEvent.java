package idle.miau.event;

import idle.miau.mcp.event.Event;

public class PlayerMoveEvent extends Event {
    public double x;
    public double y;
    public double z;

    public PlayerMoveEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

}
