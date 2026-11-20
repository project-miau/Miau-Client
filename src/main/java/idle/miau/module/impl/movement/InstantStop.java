package idle.miau.module.impl.movement;

import idle.miau.event.PrePlayerInputEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;
import idle.miau.mcp.event.Subscribe;

public class InstantStop extends Module {

    private final ButtonSetting onlyOnGround;
    private final ButtonSetting disableWhenFlying;

    private float lastForward;
    private float lastStrafe;

    public InstantStop() {
        super("InstantStop", category.movement, 0);
        setDescription("Stops horizontal movement when you release keys.");
        this.registerSetting(onlyOnGround = new ButtonSetting("Only on ground", true));
        this.registerSetting(disableWhenFlying = new ButtonSetting("Disable when flying", true));
    }

    @Override
    public void onDisable() {
        this.lastForward = 0.0F;
        this.lastStrafe = 0.0F;
    }

    @Subscribe
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!this.isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (onlyOnGround.isToggled() && !mc.thePlayer.onGround) {
            this.lastForward = e.getForward();
            this.lastStrafe = e.getStrafe();
            return;
        }
        if (disableWhenFlying.isToggled() && mc.thePlayer.capabilities.isFlying) {
            this.lastForward = e.getForward();
            this.lastStrafe = e.getStrafe();
            return;
        }

        float rawF = e.getForward();
        float rawS = e.getStrafe();

        if (rawF == 0.0F && rawS == 0.0F && (this.lastForward != 0.0F || this.lastStrafe != 0.0F)) {
            e.setForward(Math.signum(-this.lastForward));
            e.setStrafe(Math.signum(-this.lastStrafe));
        }

        this.lastForward = rawF;
        this.lastStrafe = rawS;
    }
}
