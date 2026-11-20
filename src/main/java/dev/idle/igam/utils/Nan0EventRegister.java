package dev.idle.igam.utils;

import idle.miau.mcp.event.EventBus;

public final class Nan0EventRegister {
    private Nan0EventRegister() {
    }

    public static void register(EventBus bus, Object target) {
        bus.register(target);
    }
}
