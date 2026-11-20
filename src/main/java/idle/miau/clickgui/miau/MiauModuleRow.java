package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.utility.RenderUtils;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

class MiauModuleRow {
    private static final long EXPAND_ANIMATION_MS = 220L;

    private final Module module;
    private final List<MiauSettingComponent> settings = new ArrayList<MiauSettingComponent>();
    private final MiauFloatAnimation heightAnimation = new MiauFloatAnimation(MiauStyle.ROW_HEIGHT);
    private int x;
    private int y;
    private int width;
    private int stripeIndex;
    private boolean opened;
    private boolean binding;

    MiauModuleRow(Module module) {
        this.module = module;
        rebuildSettings();
    }

    void draw(int mouseX, int mouseY) {
        boolean hovered = MiauStyle.hovered(x, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY);
        int rowAccent = MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D);
        int baseColor = module.isEnabled()
                ? MiauStyle.FRAME : MiauStyle.moduleRowColor(stripeIndex);
        Gui.drawRect(x, y, x + width, y + MiauStyle.ROW_HEIGHT, baseColor);
        if (module.isEnabled()) {
            Gui.drawRect(
                    x,
                    y,
                    x + width,
                    y + MiauStyle.ROW_HEIGHT,
                    MiauStyle.withAlpha(rowAccent, hovered ? 130 : 90));
            Gui.drawRect(x, y, x + 2, y + MiauStyle.ROW_HEIGHT, rowAccent);
        }
        else if (hovered) {
            Gui.drawRect(x, y, x + width, y + MiauStyle.ROW_HEIGHT, MiauStyle.HOVER);
        }

        boolean controlDown = isControlDown();
        String text = module.getDisplayName();
        if (binding) {
            text += " [Binding...]";
        }
        else if (controlDown && module.isHidden()) {
            text += " [Hidden]";
        }
        else if (!controlDown && module.getKeycode() != 0) {
            text += " [" + keyName(module.getKeycode()) + "]";
        }
        text = MiauStyle.trim(text, MiauStyle.moduleFont(), width - (hasVisibleSettings() ? 18 : 6));
        MiauStyle.moduleFont().drawString(text, x + 5, MiauStyle.centeredTextY(y, MiauStyle.ROW_HEIGHT, MiauStyle.moduleFont()),
                MiauStyle.WHITE, true);

        if (hasVisibleSettings()) {
            drawExpandIcon(opened ? rowAccent : (module.isEnabled() ? rowAccent : MiauStyle.MUTED));
        }
        float animatedHeight = getAnimatedHeight();
        float revealedContent = Math.max(0.0F, animatedHeight - MiauStyle.ROW_HEIGHT);
        if (revealedContent <= 0.0F) {
            return;
        }
        RenderUtils.scissorPushGui(x, y + MiauStyle.ROW_HEIGHT, width, revealedContent);
        drawSettingBackground(rowAccent);
        int settingY = y + MiauStyle.ROW_HEIGHT;
        for (MiauSettingComponent component : settings) {
            if (!component.isVisible()) {
                continue;
            }
            component.setBounds(x + 3, settingY, width - 3);
            component.draw(mouseX, mouseY);
            settingY += component.getHeight();
        }
        RenderUtils.scissorPop();
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (MiauStyle.hovered(x, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY)) {
            clearTextFocus();
            if (binding && button > 1) {
                module.setBind(button + 1000);
                binding = false;
                markChanged();
                return true;
            }
            if (button == 0 && module.canBeEnabled() && !(module instanceof idle.miau.module.impl.client.ClickGui)) {
                module.toggle();
            }
            else if (button == 1 && hasVisibleSettings()) {
                opened = !opened;
                if (!opened) {
                    clearTextFocus();
                }
            }
            else if (button == 2 && module.moduleCategory() != Module.category.configs) {
                if (module.moduleCategory() == Module.category.scripts && !(module instanceof keystrokesmod.script.Manager)) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                    mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(mc.currentScreen, module.getName() + ".java"));
                    return true;
                }
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                    module.setHidden(!module.isHidden());
                    markChanged();
                }
                else if (module.canBeEnabled()) {
                    binding = true;
                }
            }
            return true;
        }
        if (!opened || !MiauStyle.hovered(
                x,
                y + MiauStyle.ROW_HEIGHT,
                width,
                Math.max(0.0F, getAnimatedHeight() - MiauStyle.ROW_HEIGHT),
                mouseX,
                mouseY)) {
            return false;
        }
        for (MiauSettingComponent component : settings) {
            if (component.isVisible() && component.mouseClicked(mouseX, mouseY, button)) {
                for (MiauSettingComponent other : settings) {
                    if (other != component) {
                        other.clearTextFocus();
                    }
                }
                return true;
            }
        }
        return false;
    }

    void mouseReleased(int mouseX, int mouseY, int button) {
        for (MiauSettingComponent component : settings) {
            component.mouseReleased(mouseX, mouseY, button);
        }
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (binding) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_0) {
                module.setBind(module instanceof idle.miau.module.impl.client.ClickGui ? 54 : 0);
            }
            else {
                module.setBind(keyCode);
            }
            binding = false;
            markChanged();
            return true;
        }
        if (!opened) {
            return false;
        }
        for (MiauSettingComponent component : settings) {
            if (component.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (binding) {
            module.setBind(wheel > 0 ? 1069 : 1070);
            binding = false;
            markChanged();
            return true;
        }
        if (!opened) {
            return false;
        }
        for (MiauSettingComponent component : settings) {
            if (component.isVisible() && component.mouseWheel(mouseX, mouseY, wheel)) {
                return true;
            }
        }
        return false;
    }

    void clearTextFocus() {
        for (MiauSettingComponent component : settings) {
            component.clearTextFocus();
        }
    }

    void onGuiClosed() {
        binding = false;
        for (MiauSettingComponent component : settings) {
            component.onGuiClosed();
        }
        heightAnimation.snap(getTargetHeight());
    }

    int getHeight() {
        return Math.max(MiauStyle.ROW_HEIGHT, Math.round(getAnimatedHeight()));
    }

    void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    void setStripeIndex(int stripeIndex) {
        this.stripeIndex = stripeIndex;
    }

    Module getModule() {
        return module;
    }

    void copyUiState(MiauModuleRow other) {
        opened = other.opened;
        heightAnimation.snap(getTargetHeight());
    }

    private void rebuildSettings() {
        settings.clear();
        for (Setting setting : module.getDisplaySettings()) {
            if (MiauSettingComponent.groupOf(setting) == null) {
                settings.add(MiauSettingComponent.create(setting, module, module.getSettings()));
            }
        }
    }

    private boolean hasVisibleSettings() {
        for (MiauSettingComponent component : settings) {
            if (component.isVisible()) {
                return true;
            }
        }
        return false;
    }

    float getAnimatedHeight() {
        heightAnimation.animateTo(getTargetHeight(), EXPAND_ANIMATION_MS);
        return heightAnimation.get();
    }

    private float getTargetHeight() {
        float height = MiauStyle.ROW_HEIGHT;
        if (!opened) {
            return height;
        }
        for (MiauSettingComponent component : settings) {
            if (component.isVisible()) {
                height += component.getHeight();
            }
        }
        return height;
    }

    private void drawExpandIcon(int color) {
        if (!MiauStyle.drawTexture(opened ? "expand" : "collapse", x + width - 12, y + 5, 8, 5, color)) {
            MiauStyle.moduleFont().drawString(opened ? "-" : "+", x + width - 10, y + 3, color, false);
        }
    }

    private void drawSettingBackground(int rowAccent) {
        int top = y + MiauStyle.ROW_HEIGHT;
        int contentHeight = Math.max(0, Math.round(getTargetHeight() - MiauStyle.ROW_HEIGHT));
        Gui.drawRect(x, top, x + width, top + contentHeight, MiauStyle.SETTING_BACKGROUND);
        for (int offset = 0, row = 0; offset < contentHeight; offset += MiauStyle.ROW_HEIGHT, row++) {
            int bottom = Math.min(top + contentHeight, top + offset + MiauStyle.ROW_HEIGHT);
            Gui.drawRect(x + 2, top + offset, x + width, bottom, MiauStyle.settingRowColor(row));
        }
        int leftBarColor = module.isEnabled() ? rowAccent : 0xFF3C3C3C;
        Gui.drawRect(x, top, x + 2, top + contentHeight, leftBarColor);
        Gui.drawRect(x, top, x + width, top + 1, MiauStyle.SEPARATOR);
        Gui.drawRect(x, top + contentHeight - 1, x + width, top + contentHeight, MiauStyle.SEPARATOR);
    }

    private void markChanged() {
        if (Miau.currentConfig != null) {
            Miau.currentConfig.getModule().saved = false;
        }
    }

    static String keyName(int key) {
        if (key == 1069) {
            return "MScrollUp";
        }
        if (key == 1070) {
            return "MScrollDown";
        }
        if (key >= 1000) {
            return "M" + (key - 1000);
        }
        String name = Keyboard.getKeyName(key);
        return name == null ? "None" : name;
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }
}
