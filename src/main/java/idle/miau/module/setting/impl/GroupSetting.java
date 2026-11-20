package idle.miau.module.setting.impl;

import com.google.gson.JsonObject;
import idle.miau.module.setting.Setting;

import java.util.List;
import java.util.ArrayList;

public class GroupSetting extends Setting {
    private final List<Setting> settings = new ArrayList<Setting>();
    private boolean opened;

    public GroupSetting(String name) {
        super(name);
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public void addSetting(Setting setting) {
        settings.add(setting);
    }

    public void removeSetting(Setting setting) {
        settings.remove(setting);
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    @Override
    public void loadConfig(JsonObject data) {}
}
