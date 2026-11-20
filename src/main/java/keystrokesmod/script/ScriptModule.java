package keystrokesmod.script;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.TextSetting;

public final class ScriptModule extends Module {
    private final TextSetting customName;

    public ScriptModule(Script script) {
        super(script);
        registerSetting(customName = new TextSetting(
                "Custom name", "", script.name, 32, this::renameDisplayName), "setting.customName");
    }

    @Override
    public String getDisplayName() {
        String name = customName.getText().trim();
        return name.isEmpty() ? getName() : name;
    }

    public String getCustomName() {
        return customName.getText();
    }

    public void setCustomName(String name) {
        customName.setText(name == null ? "" : name.trim());
        Module.sort = true;
    }

    private void renameDisplayName() {
        setCustomName(customName.getText());
        Miau.markCurrentConfigUnsaved();
    }

    @Override
    public boolean canBeEnabled() {
        return super.canBeEnabled() && (script == null || !script.error);
    }

    @Override
    public void onEnable() {
        if (script != null && Miau.scriptManager != null) {
            Miau.scriptManager.onEnable(script);
        }
    }

    @Override
    public void onDisable() {
        if (script != null && Miau.scriptManager != null) {
            Miau.scriptManager.onDisable(script);
        }
    }

    @Override
    public String getInfo() {
        if (script != null) {
            String suffix = script.getString("getSuffix");
            return suffix == null ? "" : suffix;
        }
        return super.getInfo();
    }
}
