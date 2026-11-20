package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

public final class NoBreakParticles extends Module {
    public NoBreakParticles() {
        super("NoBreakParticles", category.render);
        setDescription("Removes particles produced by breaking blocks.");
    }

    public static boolean isActive() {
        return ModuleManager.noBreakParticles != null && ModuleManager.noBreakParticles.isEnabled();
    }
}
