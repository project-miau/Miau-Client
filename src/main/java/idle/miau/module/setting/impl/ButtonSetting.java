package idle.miau.module.setting.impl;

import com.google.gson.JsonObject;
import idle.miau.module.setting.Setting;

public class ButtonSetting extends Setting {
    private String name;
    private boolean isEnabled;
    public boolean isMethodButton;
    private Runnable method;
    public GroupSetting group;
    private final String[] legacyConfigKeys;

    public ButtonSetting(String name, boolean isEnabled) {
        super(name);
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.legacyConfigKeys = new String[0];
    }

    public ButtonSetting(String name, boolean isEnabled, String... legacyConfigKeys) {
        super(name);
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public ButtonSetting(GroupSetting group, String name, boolean isEnabled) {
        super(name);
        this.group = group;
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.legacyConfigKeys = new String[0];
    }

    public ButtonSetting(GroupSetting group, String name, boolean isEnabled, String... legacyConfigKeys) {
        super(name);
        this.group = group;
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public ButtonSetting(String name, Runnable method) {
        super(name);
        this.name = name;
        this.isEnabled = false;
        this.isMethodButton = true;
        this.method = method;
        this.legacyConfigKeys = new String[0];
    }

    public void runMethod() {
        if (method != null) {
            method.run();
        }
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String getConfigKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    public boolean isToggled() {
        return this.isEnabled;
    }

    public void toggle() {
        this.isEnabled = !this.isEnabled;
    }

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }

    public void setEnabled(boolean b) {
        this.isEnabled = b;
    }

    @Override
    public void loadConfig(JsonObject data) {
        String configKey = getConfigKey();
        String legacyKey = getName();
        String key = null;
        if (data.has(configKey)) {
            key = configKey;
        }
        else if (data.has(legacyKey)) {
            key = legacyKey;
        }
        else {
            for (String legacyConfigKey : legacyConfigKeys) {
                if (data.has(legacyConfigKey)) {
                    key = legacyConfigKey;
                    break;
                }
            }
        }
        if (key != null && data.get(key).isJsonPrimitive() && !this.isMethodButton) {
            boolean booleanValue = isEnabled;
            try {
                booleanValue = data.getAsJsonPrimitive(key).getAsBoolean();
            }
            catch (Exception e) {}
            setEnabled(booleanValue);
        }
    }
}
