package idle.miau.module.impl.world;

import idle.miau.baritone.BaritoneBridge;
import idle.miau.module.Module;

public final class Baritone extends Module {
    public Baritone() {
        super("Baritone", category.other);
        setDescription("Baritone but in Miau.");
    }
    @Override
    public void onEnable() { BaritoneBridge.initialize(); }
    @Override
    public void onDisable() { BaritoneBridge.disable(); }
    @Override
    public String getInfo() { return BaritoneBridge.status(); }
}
