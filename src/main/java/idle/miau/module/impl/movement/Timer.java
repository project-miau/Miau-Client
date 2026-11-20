package idle.miau.module.impl.movement;

import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;

public class Timer extends Module {
    private SliderSetting speed;

    public Timer() {
        super("Timer", category.movement);
        setDescription("Changes the speed at which your game runs.");
        this.registerSetting(speed = new SliderSetting("Speed", 1.0D, 0.0D, 10.0D, 0.01D));
    }

    @Override
    public String getInfo() {
        return Utils.asWholeNum(speed.getInput());
    }

    public float getConfiguredSpeed() {
        return (float) speed.getInput();
    }

    @Override
    public void onEnable() {

        if (Utils.nullCheck() && speed.getInput() <= 0.0D) {
            Utils.resetTimer();
        }
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }

        float configuredSpeed = (float) speed.getInput();
        if (configuredSpeed > 0.0F) {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = configuredSpeed;
        } else {

            Utils.resetTimer();
        }
    }

    public static int consumeExtraLocalUpdatesForBaseTick() {
        return 0;
    }

    public static boolean shouldSkipBaseLocalUpdate() {
        Timer timer = idle.miau.module.ModuleManager.timer;
        if (timer == null || !timer.isEnabled()) {
            return false;
        }
        if (!Utils.nullCheck()) {
            return false;
        }
        return timer.speed.getInput() <= 0.0D;
    }
}
