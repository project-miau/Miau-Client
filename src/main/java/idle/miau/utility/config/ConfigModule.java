package idle.miau.utility.config;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGui;
import idle.miau.clickgui.modern.MiauClickGui;
import idle.miau.clickgui.panel.MiauPanelClickGui;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.Utils;

public class ConfigModule extends Module {
    private final Config config;
    private final TextSetting configNameSetting;
    private String displayName;
    public boolean saved = true;

    public ConfigModule(Config config, String name, int bind) {
        super(name, category.configs, bind);
        this.config = config;
        this.displayName = name;
        this.registerSetting(configNameSetting = new TextSetting("Config name", name, "Type a new config name...", 32, this::renameConfig));
        this.registerSetting(new ButtonSetting("Save config", () -> {
            if (Miau.configManager.saveConfig(this.config)) {
                Utils.sendMessage("&7Saved config: &b" + getName());
                saved = true;
            }
            else {
                saved = false;
                Utils.sendMessage("&cFailed to save config: &b" + getName());
            }
        }));
        this.registerSetting(new ButtonSetting("Remove config", () -> {
            String configName = getName();
            if (Miau.configManager.deleteConfig(configName)) {
                Utils.sendMessage("&7Removed config: &b" + configName);
            }
        }));
    }

    @Override
    public void toggle() {
        if (mc.currentScreen instanceof ClickGui
                || mc.currentScreen instanceof MiauClickGui || mc.currentScreen instanceof MiauPanelClickGui
                || mc.currentScreen == null) {
            if (!Miau.configManager.loadConfig(this.getName())) {
                saved = false;
                return;
            }

            Miau.currentConfig = config;

            Utils.sendMessage("&7Enabled config: &b" + this.getName());
            saved = true;
        }
    }

    @Override
    public boolean isEnabled() {
        if (Miau.currentConfig == null) {
            return false;
        }
        return Miau.currentConfig.getModule() == this;
    }

    @Override
    public String getName() {
        return displayName;
    }

    public void setConfigName(String configName) {
        this.displayName = configName;
        configNameSetting.setText(configName);
    }

    private void renameConfig() {
        if (Miau.configManager == null) {
            return;
        }

        String oldName = getName();
        if (Miau.configManager.renameConfig(config, configNameSetting.getText())) {
            configNameSetting.setText(config.getName());
            if (!oldName.equals(config.getName())) {
                Utils.sendMessage("&7Renamed config: &b" + oldName + " &7to &b" + config.getName());
            }
        }
    }
}
