package idle.miau.clickgui.modern;

import idle.miau.Miau;
import idle.miau.gui.GuiScriptEditor;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import keystrokesmod.script.Manager;
import net.minecraft.client.Minecraft;

import java.util.List;

final class MiauScriptPanel extends MiauPanel {
    private static final String[] ACTIONS = {"Create", "Edit", "Load", "Folder", "Docs"};
    private final MiauInput name;
    private final MiauModuleList scripts;
    private String status = "Left click to toggle | Right click for settings | Middle click to bind";
    private int count;

    MiauScriptPanel(MiauClickGui gui) {
        super(gui);
        name = new MiauInput("Script name", "Type a script name...", gui, () -> runAction("Create"));
        scripts = new MiauModuleList(gui);
    }

    @Override
    void open() { refresh(); }

    void refresh() {
        List<Module> modules = Miau.moduleManager == null ? List.of()
                : Miau.moduleManager.inCategory(Module.category.scripts).stream()
                .filter(module -> !(module instanceof Manager)).toList();
        count = modules.size();
        scripts.setModules(modules);
    }

    @Override
    void drawContent(MiauRenderer renderer, float mouseX, float mouseY) {
        heading(renderer, "Script Manager", "\ue86f", count + (count == 1 ? " script" : " scripts"));
        float buttonWidth = (width - 20 - 4 * (ACTIONS.length - 1)) / ACTIONS.length;
        for (int index = 0; index < ACTIONS.length; index++) {
            button(renderer, ACTIONS[index], x + 10 + index * (buttonWidth + 4), y + 34,
                    buttonWidth, 20, mouseX, mouseY, index == 0);
        }
        name.drawAt(renderer, x + 10, y + 62, width - 20, mouseX, mouseY);
        float listTop = y + 98;
        if (manager() != null) {
            float permissionWidth = (width - 24) / 2;
            drawPermission(renderer, "HTTP requests", Manager.enableHttpRequests, x + 10, permissionWidth, mouseX, mouseY);
            drawPermission(renderer, "WebSockets", Manager.enableWebSockets, x + 14 + permissionWidth, permissionWidth, mouseX, mouseY);
            listTop += 26;
        }
        scripts.draw(renderer, x + 10, listTop, width - 20, y + height - 26 - listTop, mouseX, mouseY);
        footer(renderer, status);
    }

    private void drawPermission(MiauRenderer renderer, String label, ButtonSetting setting,
                                float left, float permissionWidth, float mouseX, float mouseY) {
        button(renderer, label + (setting.isToggled() ? ": On" : ": Off"), left, y + 96,
                permissionWidth, 18, mouseX, mouseY, setting.isToggled());
    }

    @Override
    void click(float mouseX, float mouseY, int mouseButton) {
        if (mouseButton == 0) {
            float buttonWidth = (width - 20 - 4 * (ACTIONS.length - 1)) / ACTIONS.length;
            for (int index = 0; index < ACTIONS.length; index++) {
                if (MiauSetting.inside(mouseX, mouseY, x + 10 + index * (buttonWidth + 4), y + 34, buttonWidth, 20)) {
                    runAction(ACTIONS[index]);
                    return;
                }
            }
            if (name.hit(mouseX, mouseY)) {
                name.click(mouseX, mouseY, mouseButton);
                return;
            }
            Manager manager = manager();
            if (manager != null) {
                float permissionWidth = (width - 24) / 2;
                ButtonSetting[] permissions = {Manager.enableHttpRequests, Manager.enableWebSockets};
                for (int index = 0; index < permissions.length; index++) {
                    if (MiauSetting.inside(mouseX, mouseY, x + 10 + index * (permissionWidth + 4), y + 96, permissionWidth, 18)) {
                        permissions[index].toggle();
                        manager.guiButtonToggled(permissions[index]);
                        return;
                    }
                }
            }
        }
        scripts.click(mouseX, mouseY, mouseButton);
    }

    private Manager manager() { return Miau.moduleManager == null ? null : Miau.moduleManager.scriptManager; }

    private void runAction(String action) {
        if ("Edit".equals(action)) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiScriptEditor(gui, name.text().isEmpty() ? null : name.text()));
            return;
        }
        Manager manager = manager();
        if (manager == null || Miau.scriptManager == null) {
            status = "Script manager unavailable";
            return;
        }
        switch (action) {
            case "Create" -> {
                if (name.text().isEmpty()) {
                    status = "Type a script name first";
                    return;
                }
                String created = Miau.scriptManager.createScript(name.text());
                status = created == null ? "Failed to create script" : "Created script: " + created;
                if (created != null) {
                    name.clear();
                    refresh();
                }
            }
            case "Load" -> {
                manager.loadScriptsNow();
                refresh();
                status = "Found " + count + (count == 1 ? " script" : " scripts");
            }
            case "Folder" -> manager.openFolder();
            case "Docs" -> manager.viewDocumentation();
        }
    }

    @Override
    void scroll(float mouseX, float mouseY, float steps) { scripts.scroll(mouseX, mouseY, steps); }

    @Override
    void cancel() {
        name.cancel();
        scripts.cancel();
    }
}
