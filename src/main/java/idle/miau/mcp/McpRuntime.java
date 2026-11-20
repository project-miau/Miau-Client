package idle.miau.mcp;

import idle.miau.mcp.event.EventBus;

public final class McpRuntime {
    public static final EventBus EVENTS = new EventBus();

    private McpRuntime() {
    }
}
