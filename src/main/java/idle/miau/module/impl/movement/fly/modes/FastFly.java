package idle.miau.module.impl.movement.fly.modes;

import idle.miau.module.impl.movement.fly.FlyMode;
import idle.miau.module.impl.movement.fly.FlyModule;
import idle.miau.utility.Utils;

public final class FastFly extends FlyMode {
    private boolean wasFlying;

    public FastFly(FlyModule owner) {
        super("Fast", owner);
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
        mc.thePlayer.onGround = true;
        if (mc.currentScreen == null) {
            if (Utils.jumpDown()) {
                mc.thePlayer.motionY = 0.3D * owner.getVerticalSpeed();
            } else if (Utils.isBindDown(mc.gameSettings.keyBindSneak)) {
                mc.thePlayer.motionY = -0.3D * owner.getVerticalSpeed();
            } else {
                mc.thePlayer.motionY = 0.0D;
            }
        } else {
            mc.thePlayer.motionY = 0.0D;
        }
        mc.thePlayer.capabilities.setFlySpeed(0.2F);
        mc.thePlayer.capabilities.isFlying = true;
        setSpeed(0.85D * owner.getHorizontalSpeed());
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
