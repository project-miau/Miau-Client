package idle.miau.module.impl.player;

import idle.miau.mcp.accessor.IAccessorEntityLivingBase;
import idle.miau.mcp.accessor.IAccessorMinecraft;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;

public class DelayRemover extends Module {
    public ButtonSetting oldReg, removeJumpTicks;

    public DelayRemover() {
        super("DelayRemover", category.player, 0);
        setDescription("Removes attack and optional jump delays.");
        this.registerSetting(oldReg = new ButtonSetting("1.7 hitreg", true));
        this.registerSetting(removeJumpTicks = new ButtonSetting("Remove jump ticks", false));
        this.closetModule = true;
    }

    public boolean shouldRemoveJumpTicks() {
        return isEnabled() && removeJumpTicks.isToggled();
    }

    @Subscribe
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !mc.inGameHasFocus || !Utils.nullCheck()) {
            return;
        }
        if (oldReg.isToggled()) {
            ((IAccessorMinecraft) mc).setLeftClickCounter(0);
        }
        if (shouldRemoveJumpTicks()) {
            ((IAccessorEntityLivingBase) mc.thePlayer).setJumpTicks(0);
        }
    }
}
