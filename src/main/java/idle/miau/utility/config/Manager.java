package idle.miau.utility.config;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.Utils;

import java.awt.*;
import java.io.IOException;

public class Manager extends Module {
    private final TextSetting createConfigName;
    private ButtonSetting loadConfigs, openFolder, createConfig;

    public Manager() {
        super("Manager", category.configs);
        this.registerSetting(createConfigName = new TextSetting("Config name", "", "Type a config name...", 32, this::createConfig));
        this.registerSetting(createConfig = new ButtonSetting("Create config", () -> {
            createConfig();
        }));
        this.registerSetting(loadConfigs = new ButtonSetting("Load configs", () -> {
            if (Utils.nullCheck() && Miau.configManager != null) {
                Miau.configManager.loadConfigs();
            }
        }));
        this.registerSetting(openFolder = new ButtonSetting("Open folder", () -> {
            try {
                Desktop.getDesktop().open(Miau.configManager.directory);
            }
            catch (IOException ex) {
                Miau.configManager.directory.mkdirs();
                Utils.sendMessage("&cError locating folder, recreated.");
            }
        }));
        ignoreOnSave = true;
        canBeEnabled = false;
    }

    private void createConfig() {
        if (!Utils.nullCheck() || Miau.configManager == null) {
            return;
        }

        Config config = Miau.configManager.createConfig(createConfigName.getText(), 0);
        if (config != null) {
            createConfigName.setText("");
            Utils.sendMessage("&7Created config: &b" + config.getName());
        }
    }
}
