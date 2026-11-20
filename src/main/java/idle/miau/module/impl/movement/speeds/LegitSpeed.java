package idle.miau.module.impl.movement.speeds;

import idle.miau.event.PreMotionEvent;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;

public final class LegitSpeed extends SpeedMode {
    private final ButtonSetting onlyForward;
    private final ButtonSetting onlyStrafe;

    public LegitSpeed(SpeedModule owner) {
        super("Legit", owner);
        register(onlyForward = new ButtonSetting("Only forward", false));
        register(onlyStrafe = new ButtonSetting("Only strafe", false));
    }

    @Override
    public void updateVisibility() {
        boolean active = owner.isMode("Legit");
        onlyForward.setVisible(active, owner);
        onlyStrafe.setVisible(active, owner);
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
        if (Utils.getHorizontalSpeed() == 0.0D
                || !mc.thePlayer.onGround
                || mc.thePlayer.capabilities.isFlying
                || mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime && mc.thePlayer.maxHurtTime > 0
                || Utils.jumpDown()
                || !settingsMet()) {
            return;
        }
        mc.thePlayer.jump();
    }

    private boolean settingsMet() {
        if (onlyForward.isToggled() && !Utils.isBindDown(mc.gameSettings.keyBindForward)) {
            return false;
        }
        return !onlyStrafe.isToggled() || mc.thePlayer.moveStrafing != 0.0F;
    }
}
