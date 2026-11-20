package idle.miau.event;

import idle.miau.mcp.event.Event;

public class ClientLookEvent extends Event {
    public float yaw;
    public float pitch;

    public ClientLookEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
