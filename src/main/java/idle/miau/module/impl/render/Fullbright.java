package idle.miau.module.impl.render;

import idle.miau.module.Module;

public class Fullbright extends Module {
    private float originalGamma;

    public Fullbright() {
        super("Fullbright", category.render);
    }

    @Override
    public void onEnable() {
        if (mc.gameSettings != null) {
            originalGamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 1000.0F;
        }
    }

    @Override
    public void onDisable() {
        if (mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = originalGamma;
        }
    }
}
