package idle.miau.utility.inventory;

import idle.miau.helper.MouseHelper;
import idle.miau.module.ModuleManager;
import idle.miau.utility.Utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public final class InventoryActions {
    private InventoryActions() {}

    public static boolean inputReady() {
        return Utils.nullCheck() && Minecraft.getMinecraft().inGameHasFocus;
    }

    public static boolean bindingPressed(int code) {
        if (code == 0) {
            return false;
        }
        if (code == 1069 || code == 1070) {
            return MouseHelper.isScrollDown(code);
        }
        return code >= 1000 ? Mouse.isButtonDown(code - 1000) : Keyboard.isKeyDown(code);
    }

    public static int type(ItemStack stack) {
        return stack != null && stack.getItem() != null ? Item.getIdFromItem(stack.getItem()) : 0;
    }

    public static void prepareItemUse() {
        Minecraft mc = Minecraft.getMinecraft();
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled()) {
            ModuleManager.killAura.suspendForItemUse();
        }
        if (mc.thePlayer.isUsingItem()) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
        }
    }

    public static void press(KeyBinding key) {
        key.incrementPressTime();
    }

    public static void pressCount(KeyBinding key, int count) {
        key.setPressTime(count);
    }

    public static void click(GuiContainer screen, int slot, int button, int mode) {
        screen.clickSlot(slot, button, mode);
    }

    public static void escape(GuiScreen screen) {
        try {
            screen.pressKey((char) 0, 1);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot close inventory", exception);
        }
    }

    public static void drainInput() {
        while (Mouse.next()) {}
        while (Keyboard.next()) {}
    }
}
