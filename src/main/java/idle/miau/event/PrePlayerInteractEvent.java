package idle.miau.event;

import idle.miau.mcp.event.Event;

public class PrePlayerInteractEvent extends Event {
    private boolean suppressAttack;
    private boolean suppressUse;
    private boolean suppressItemRelease;
    private boolean suppressBlockHit;

    public void suppressAll() {
        suppressAttack = true;
        suppressUse = true;
        suppressItemRelease = true;
        suppressBlockHit = true;
    }

    public void suppressAttack() {
        suppressAttack = true;
    }

    public void suppressItemRelease() {
        suppressItemRelease = true;
    }

    public void suppressBlockHit() {
        suppressBlockHit = true;
    }

    public boolean shouldSuppressAttack() {
        return suppressAttack;
    }

    public boolean shouldSuppressUse() {
        return suppressUse;
    }

    public boolean shouldSuppressItemRelease() {
        return suppressItemRelease;
    }

    public boolean shouldSuppressBlockHit() {
        return suppressBlockHit;
    }
}
