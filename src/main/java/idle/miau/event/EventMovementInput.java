package idle.miau.event;

import idle.miau.mcp.event.Cancelable;
import idle.miau.mcp.event.Event;

@Cancelable
public final class EventMovementInput extends Event {
    private float speed;
    private float strafe;
    private boolean jump;

    public EventMovementInput(float speed, float strafe, boolean jump) {
        this.speed = speed;
        this.strafe = strafe;
        this.jump = jump;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public float getSpeed() {
        return speed;
    }

    public float getStrafe() {
        return strafe;
    }

    public boolean isJumping() {
        return jump;
    }
}
