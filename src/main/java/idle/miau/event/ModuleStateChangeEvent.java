package idle.miau.event;

import idle.miau.mcp.event.Event;
import idle.miau.module.Module;

public final class ModuleStateChangeEvent extends Event {
    private final Module module;
    private final boolean enabled;

    public ModuleStateChangeEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public Module getModule() {
        return module;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
