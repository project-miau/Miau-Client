package idle.miau.module.setting;

import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.PlayerListSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.module.setting.impl.TextSetting;

import java.util.List;

public final class SettingGroups {
    private SettingGroups() {}

    public static GroupSetting parentOf(Setting setting, List<Setting> settings) {
        GroupSetting parent = null;
        if (setting instanceof ButtonSetting value) parent = value.group;
        else if (setting instanceof SliderSetting value) parent = value.groupSetting;
        else if (setting instanceof RangeSetting value) parent = value.groupSetting;
        else if (setting instanceof ColorSetting value) parent = value.groupSetting;
        else if (setting instanceof MultiSelectSetting value) parent = value.groupSetting;
        else if (setting instanceof TextSetting value) parent = value.group;
        else if (setting instanceof KeySetting value) parent = value.group;
        else if (setting instanceof BlockListSetting value) parent = value.group;
        else if (setting instanceof StringListSetting value) parent = value.group;
        else if (setting instanceof PlayerListSetting value) parent = value.group;
        if (parent != null) return parent;
        for (Setting candidate : settings) {
            if (candidate instanceof GroupSetting group && candidate != setting && group.getSettings().contains(setting)) {
                return group;
            }
        }
        return null;
    }
}
