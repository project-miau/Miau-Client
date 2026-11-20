package idle.miau.module.impl.movement;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.DescriptionSetting;

public class MovementFix extends Module {
    private static volatile boolean scriptForced;

    public MovementFix() {
        super("MovementFix", category.movement);
        setDescription("Keeps movement aligned with modified rotations.");
    }

    public static boolean isActive() {
        return scriptForced || ModuleManager.movementFix != null && ModuleManager.movementFix.isEnabled();
    }

    public static void setScriptForced(boolean forced) {
        scriptForced = forced;
    }

    public static boolean shouldCorrect(boolean rotationActive) {
        return isActive() && rotationActive;
    }
}
