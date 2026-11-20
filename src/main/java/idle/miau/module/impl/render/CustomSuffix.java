package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.TextSetting;

public final class CustomSuffix extends Module {
    private static final int FULL_MODE = 1;
    private static final String[] MODES = {"Semi", "Full"};

    private final SliderSetting mode;
    private final TextSetting suffix;

    public CustomSuffix() {
        super("CustomSuffix", category.render, 0);
        setDescription("Customizes the information shown beside modules.");
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        registerSetting(suffix = new TextSetting(
                "Suffix",
                "CustomSuffix",
                "Type a custom suffix...",
                64,
                this::refreshHud
        ));
    }

    @Override
    public String getInfo() {
        return MODES[getModeIndex()];
    }

    @Override
    public void onEnable() {
        refreshHud();
    }

    @Override
    public void onDisable() {
        refreshHud();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            refreshHud();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode || setting == suffix) {
            refreshHud();
        }
    }

    static String customizeHudInfo(Module module, String originalInfo) {
        CustomSuffix customSuffix = ModuleManager.customSuffix;
        if (customSuffix == null || !customSuffix.isEnabled() || module == customSuffix) {
            return originalInfo;
        }
        return customSuffix.applySuffix(originalInfo);
    }

    String applySuffix(String originalInfo) {
        String original = originalInfo == null ? "" : originalInfo;
        String custom = suffix.getText().trim().replace('&', '\u00a7');
        if (getModeIndex() == FULL_MODE) {
            return custom;
        }
        if (custom.isEmpty()) {
            return original;
        }
        return original.isEmpty() ? custom : custom + " " + original;
    }

    private int getModeIndex() {
        return Math.max(0, Math.min((int) mode.getInput(), MODES.length - 1));
    }

    private void refreshHud() {
        Module.sort = true;
        ModuleManager.sort();
    }
}
