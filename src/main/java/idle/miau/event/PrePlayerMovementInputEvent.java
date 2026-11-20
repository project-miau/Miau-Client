package idle.miau.event;

import idle.miau.mcp.event.Event;

public class PrePlayerMovementInputEvent extends Event {
    public float forward;
    public float strafe;

    public PrePlayerMovementInputEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }
}
