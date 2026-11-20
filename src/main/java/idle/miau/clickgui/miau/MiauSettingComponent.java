package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.InventoryItemListSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.PlayerListSetting;
import idle.miau.module.setting.impl.PotionListSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.module.setting.impl.TextSetting;

import java.util.List;

abstract class MiauSettingComponent {
    final Setting setting;
    final Module owner;
    float x;
    float y;
    float width;

    MiauSettingComponent(Setting setting, Module owner) {
        this.setting = setting;
        this.owner = owner;
    }

    static MiauSettingComponent create(Setting setting, Module owner, List<Setting> ownerSettings) {
        if (setting instanceof GroupSetting) {
            return new MiauGroupComponent((GroupSetting) setting, owner, ownerSettings);
        }
        if (setting instanceof ButtonSetting) {
            return new MiauButtonComponent((ButtonSetting) setting, owner);
        }
        if (setting instanceof MultiSelectSetting) {
            return new MiauDropdownComponent((MultiSelectSetting) setting, owner);
        }
        if (setting instanceof SliderSetting) {
            SliderSetting slider = (SliderSetting) setting;
            return slider.isString
                    ? new MiauDropdownComponent(slider, owner)
                    : new MiauSliderComponent(slider, owner);
        }
        if (setting instanceof RangeSetting) {
            return new MiauRangeComponent((RangeSetting) setting, owner);
        }
        if (setting instanceof ColorSetting) {
            return new MiauColorComponent((ColorSetting) setting, owner);
        }
        if (setting instanceof TextSetting) {
            return new MiauTextComponent((TextSetting) setting, owner);
        }
        if (setting instanceof KeySetting) {
            return new MiauKeyComponent((KeySetting) setting, owner);
        }
        if (setting instanceof PlayerListSetting) {
            return new MiauListComponent((PlayerListSetting) setting, owner);
        }
        if (setting instanceof InventoryItemListSetting) {
            return new MiauListComponent((InventoryItemListSetting) setting, owner);
        }
        if (setting instanceof PotionListSetting) {
            return new MiauListComponent((PotionListSetting) setting, owner);
        }
        if (setting instanceof ItemListSetting) {
            return new MiauListComponent((ItemListSetting) setting, owner);
        }
        if (setting instanceof BlockListSetting) {
            return new MiauListComponent((BlockListSetting) setting, owner);
        }
        if (setting instanceof StringListSetting) {
            return new MiauListComponent((StringListSetting) setting, owner);
        }
        if (setting instanceof DescriptionSetting) {
            return new MiauDescriptionComponent((DescriptionSetting) setting, owner);
        }
        return new MiauDescriptionComponent(setting, owner);
    }

    static GroupSetting groupOf(Setting setting) {
        if (setting instanceof ButtonSetting) {
            return ((ButtonSetting) setting).group;
        }
        if (setting instanceof SliderSetting) {
            return ((SliderSetting) setting).groupSetting;
        }
        if (setting instanceof RangeSetting) {
            return ((RangeSetting) setting).groupSetting;
        }
        if (setting instanceof MultiSelectSetting) {
            return ((MultiSelectSetting) setting).groupSetting;
        }
        if (setting instanceof ColorSetting) {
            return ((ColorSetting) setting).groupSetting;
        }
        if (setting instanceof KeySetting) {
            return ((KeySetting) setting).group;
        }
        if (setting instanceof TextSetting) {
            return ((TextSetting) setting).group;
        }
        if (setting instanceof StringListSetting) {
            return ((StringListSetting) setting).group;
        }
        if (setting instanceof BlockListSetting) {
            return ((BlockListSetting) setting).group;
        }
        if (setting instanceof PlayerListSetting) {
            return ((PlayerListSetting) setting).group;
        }
        return null;
    }

    void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    boolean isVisible() {
        return setting.visible;
    }

    int getHeight() {
        return MiauStyle.ROW_HEIGHT;
    }

    abstract void draw(int mouseX, int mouseY);

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    void mouseReleased(int mouseX, int mouseY, int button) {
    }

    boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        return false;
    }

    void clearTextFocus() {
    }

    void onGuiClosed() {
        clearTextFocus();
    }

    boolean hovered(int mouseX, int mouseY) {
        return MiauStyle.hovered(x - 3, y, width, getHeight(), mouseX, mouseY);
    }

    void markChanged() {
        if (Miau.currentConfig != null) {
            Miau.currentConfig.getModule().saved = false;
        }
    }
}
