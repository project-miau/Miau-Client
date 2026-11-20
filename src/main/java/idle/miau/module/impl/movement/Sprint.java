package idle.miau.module.impl.movement;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.utility.Utils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;

public class Sprint extends Module {

    private final GroupSetting condition = new GroupSetting("Condition");
    private final ButtonSetting allowUsingItem;
    private final ButtonSetting allowBackwards;
    private final ButtonSetting allowSideways;
    private final ButtonSetting allowInInventory;

    public Sprint() {
        super("Sprint", category.movement, 0);
        setDescription("Automatically sprints when movement allows it.");
        this.registerSetting(new DescriptionSetting("Allow while"));
        this.registerSetting(condition);
        this.registerSetting(allowUsingItem = new ButtonSetting(condition, "Using item", false));
        this.registerSetting(allowBackwards = new ButtonSetting(condition, "Backwards", false));
        this.registerSetting(allowSideways = new ButtonSetting(condition, "Sideways", false));
        this.registerSetting(allowInInventory = new ButtonSetting(condition, "In inventory", false));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        if (Utils.nullCheck()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }
        boolean inGame = mc.inGameHasFocus;
        boolean inInv = allowInInventory.isToggled() && (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest);
        if (!inGame && !inInv) {
            return;
        }
        if (ModuleManager.scaffold != null && !ModuleManager.scaffold.allowsSprint()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            mc.thePlayer.resetSprintToggleTimer();
            mc.thePlayer.setSprinting(false);
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
    }

    public boolean allowWhileUsingItem() {
        return this.isEnabled() && allowUsingItem.isToggled();
    }

    public boolean allowWhileBackwards() {
        return this.isEnabled() && allowBackwards.isToggled();
    }

    public boolean allowWhileSideways() {
        return this.isEnabled() && allowSideways.isToggled();
    }
}
