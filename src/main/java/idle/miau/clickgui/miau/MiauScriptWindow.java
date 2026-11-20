package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.module.Module;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.font.MiauFontRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MiauScriptWindow {
    private static final int COLUMN_WIDTH = 300;
    private static final int BUTTON_Y = 32;
    private static final int BUTTON_HEIGHT = 16;
    private static final int NAME_FIELD_Y = 52;
    private static final int NAME_FIELD_HEIGHT = 14;
    private static final int LIST_TOP = 72;
    private static final int LIST_BOTTOM_MARGIN = 56;
    private static final int HEADER_HEIGHT = 20;
    private static final String HINT = "Click to toggle | Right click for settings";

    private final List<MiauModuleRow> rows = new ArrayList<MiauModuleRow>();
    private final MiauTextField nameField = new MiauTextField("Type a script name...", 32);
    private String status;
    private int x;
    private int width;
    private int listTop = LIST_TOP;
    private int listBottom = LIST_TOP;
    private int contentHeight;
    private float scroll;

    void open(int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        syncModules();
    }

    void close() {
    }

    void draw(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        MiauFontRenderer font = MiauStyle.settingFont();

        drawFunctionButtons(mouseX, mouseY, font);
        nameField.draw(x, NAME_FIELD_Y, width);

        contentHeight = calculateContentHeight();
        clampScroll();
        int viewportHeight = listBottom - listTop;
        RenderUtils.scissorPushGui(x, listTop, width, viewportHeight);
        int rowY = listTop - Math.round(scroll);
        for (MiauModuleRow row : rows) {
            row.setBounds(x, rowY, width);
            row.draw(mouseX, mouseY);
            rowY += row.getHeight();
        }
        RenderUtils.scissorPop();

        String hint = rows.isEmpty() ? "No scripts found" : (status != null ? status : HINT);
        font.drawString(MiauStyle.trim(hint, font, width), x + (width - font.getStringWidth(hint)) / 2,
                listBottom + 8, MiauStyle.MUTED, true);
    }

    private void drawFunctionButtons(int mouseX, int mouseY, MiauFontRenderer font) {
        String[] labels = {"Create", "Edit", "Load", "Folder", "Docs"};
        int gap = 4;
        int buttonWidth = (width - gap * (labels.length - 1)) / labels.length;
        int buttonY = BUTTON_Y;
        int buttonHeight = BUTTON_HEIGHT;
        for (int index = 0; index < labels.length; index++) {
            int buttonX = x + index * (buttonWidth + gap);
            boolean hovered = MiauStyle.hovered(buttonX, buttonY, buttonWidth, buttonHeight,
                    mouseX, mouseY);
            RenderUtils.drawRoundedRectangle(buttonX, buttonY,
                    buttonX + buttonWidth, buttonY + buttonHeight, 3.0F,
                    hovered ? MiauStyle.HOVER : MiauStyle.withAlpha(MiauStyle.DARK, 235));
            int labelColor = hovered ? MiauStyle.WHITE : MiauStyle.MUTED;
            font.drawString(labels[index],
                    buttonX + (buttonWidth - font.getStringWidth(labels[index])) / 2,
                    MiauStyle.centeredTextY(buttonY, buttonHeight, font), labelColor, true);
        }
    }

    private boolean functionButtonClicked(int mouseX, int mouseY) {
        String[] labels = {"Create", "Edit", "Load", "Folder", "Docs"};
        int gap = 4;
        int buttonWidth = (width - gap * (labels.length - 1)) / labels.length;
        int buttonY = BUTTON_Y;
        int buttonHeight = BUTTON_HEIGHT;
        if (!MiauStyle.hovered(x, buttonY, width, buttonHeight, mouseX, mouseY)) {
            return false;
        }
        for (int index = 0; index < labels.length; index++) {
            int buttonX = x + index * (buttonWidth + gap);
            if (MiauStyle.hovered(buttonX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY)) {
                runFunction(labels[index]);
                return true;
            }
        }
        return true;
    }

    private void runFunction(String function) {
        if ("Edit".equals(function)) {
            String requested = nameField.getText().trim();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(mc.currentScreen, requested.isEmpty() ? null : requested));
            return;
        }
        keystrokesmod.script.Manager manager = Miau.moduleManager != null
                ? Miau.moduleManager.scriptManager : null;
        if (manager == null) {
            status = "Script manager unavailable";
            return;
        }
        if ("Create".equals(function)) {
            String requested = nameField.getText().trim();
            if (requested.isEmpty()) {
                status = "Type a script name first";
                return;
            }
            manager.createScript(requested);
            nameField.setText("");
            syncModules();
            status = "Created script: " + requested;
            return;
        }
        if ("Load".equals(function)) {
            manager.loadScriptsNow();
            syncModules();
            status = "Scripts loaded";
            return;
        }
        if ("Folder".equals(function)) {
            manager.openFolder();
            return;
        }
        if ("Docs".equals(function)) {
            manager.viewDocumentation();
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!MiauStyle.hovered(x, BUTTON_Y, width, listBottom - BUTTON_Y, mouseX, mouseY)) {
            return false;
        }
        if (mouseY < listTop) {
            if (button != 0) {
                return true;
            }
            if (MiauStyle.hovered(x, NAME_FIELD_Y, width, NAME_FIELD_HEIGHT, mouseX, mouseY)) {
                nameField.focus();
            } else {
                nameField.unfocus();
            }
            functionButtonClicked(mouseX, mouseY);
            return true;
        }
        for (MiauModuleRow row : rows) {
            if (row.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        clearTextFocus();
        return true;
    }

    boolean mouseReleased(int mouseX, int mouseY, int button) {
        for (MiauModuleRow row : rows) {
            row.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (nameField.isFocused() && keyCode == org.lwjgl.input.Keyboard.KEY_RETURN) {
            if (!nameField.getText().trim().isEmpty()) {
                runFunction("Create");
            }
            nameField.unfocus();
            return true;
        }
        if (nameField.keyTyped(typedChar, keyCode)) {
            return true;
        }
        for (MiauModuleRow row : rows) {
            if (row.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || !MiauStyle.hovered(x, listTop, width, listBottom - listTop, mouseX, mouseY)) {
            return false;
        }
        for (MiauModuleRow row : rows) {
            if (row.mouseWheel(mouseX, mouseY, wheel)) {
                return true;
            }
        }
        scroll -= ClickGuiInput.scrollPixels(wheel, MiauStyle.ROW_HEIGHT);
        clampScroll();
        return true;
    }

    void clearTextFocus() {
        nameField.unfocus();
        for (MiauModuleRow row : rows) {
            row.clearTextFocus();
        }
    }

    void onGuiClosed() {
        nameField.unfocus();
        for (MiauModuleRow row : rows) {
            row.onGuiClosed();
        }
    }

    void syncModules() {
        List<Module> current = currentScriptModules();
        current.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, MiauModuleRow> previous = new HashMap<String, MiauModuleRow>();
        for (MiauModuleRow row : rows) {
            previous.put(row.getModule().getConfigId() + "\u0000" + row.getModule().getName(), row);
            previous.put(row.getModule().getName(), row);
        }
        rows.clear();
        for (Module module : current) {
            MiauModuleRow old = previous.get(module.getConfigId() + "\u0000" + module.getName());
            if (old == null) {
                old = previous.get(module.getName());
            }
            MiauModuleRow row = new MiauModuleRow(module);
            if (old != null) {
                row.copyUiState(old);
            }
            rows.add(row);
        }
        contentHeight = calculateContentHeight();
        clampScroll();
    }

    private List<Module> currentScriptModules() {
        List<Module> current = new ArrayList<Module>();
        if (Miau.moduleManager == null) {
            return current;
        }
        for (Module module : Miau.moduleManager.inCategory(Module.category.scripts)) {
            if (module instanceof keystrokesmod.script.Manager) {
                continue;
            }
            current.add(module);
        }
        return current;
    }

    private int calculateContentHeight() {
        int total = 0;
        for (MiauModuleRow row : rows) {
            total += row.getHeight();
        }
        return total;
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = Math.max(0.0F, Math.min(scroll, max));
    }

    private void layout(int screenWidth, int screenHeight) {
        width = Math.min(COLUMN_WIDTH, Math.max(160, screenWidth - 80));
        x = (screenWidth - width) / 2;
        listTop = LIST_TOP;
        listBottom = Math.max(listTop + 40, screenHeight - LIST_BOTTOM_MARGIN);
    }
}
