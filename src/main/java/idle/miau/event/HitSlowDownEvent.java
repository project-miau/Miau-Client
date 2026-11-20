package idle.miau.event;

import idle.miau.mcp.event.Event;

public final class HitSlowDownEvent extends Event {
    private double slowDown;
    private boolean sprint;

    public HitSlowDownEvent(double slowDown, boolean sprint) {
        this.slowDown = slowDown;
        this.sprint = sprint;
    }

    public double getSlowDown() {
        return slowDown;
    }

    public void setSlowDown(double slowDown) {
        this.slowDown = slowDown;
    }

    public boolean isSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
