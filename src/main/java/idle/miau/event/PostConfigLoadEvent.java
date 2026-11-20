package idle.miau.event;

import idle.miau.mcp.event.Event;

public class PostConfigLoadEvent extends Event {
    public String configName;

    public PostConfigLoadEvent(String configName) {
        this.configName = configName;
    }
}
