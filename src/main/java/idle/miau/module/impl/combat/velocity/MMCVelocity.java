package idle.miau.module.impl.combat.velocity;

import idle.miau.mcp.event.entity.living.LivingEvent;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;

public final class MMCVelocity extends VelocityMode {
    private final SliderSetting value;

    public MMCVelocity(VelocityModule owner) {
        super("MMC", owner);
        register(value = new SliderSetting("MMC Value", 0.8D, 0.75D, 1.0D, 0.01D));
    }

    @Override
    public void updateVisibility() {
        value.setVisible(owner.isMode("MMC"), owner);
    }

    @Override
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!Utils.nullCheck() || isSuppressed() || event.entity != mc.thePlayer
                || mc.thePlayer.hurtTime <= 1) {
            return;
        }
        mc.thePlayer.motionX *= value.getInput();
        mc.thePlayer.motionZ *= value.getInput();
        if (mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.jump();
        }
    }
}
