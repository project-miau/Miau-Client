package idle.miau.module.impl.movement.fly.modes;

import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import idle.miau.utility.Utils;

public final class VanillaFly extends FlyMode {
    private boolean wasFlying;

    public VanillaFly(FlyModule owner) {
        super("Vanilla", owner);
    }

    @Override
    public boolean usesSpeedSettings() {
        return true;
    }

    @Override
    public void onEnable() {
        if (mc != null && mc.thePlayer != null) {
            wasFlying = mc.thePlayer.capabilities.isFlying;
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.capabilities.setFlySpeed(
                (float) (0.05000000074505806D * owner.getHorizontalSpeed()));
        mc.thePlayer.capabilities.isFlying = true;
    }

    @Override
    public void onDisable() {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.capabilities.isFlying = mc.thePlayer.capabilities.allowFlying && wasFlying;
        mc.thePlayer.capabilities.setFlySpeed(0.05F);
        wasFlying = false;
    }
}
