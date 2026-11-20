package idle.miau.module.setting;

import com.google.gson.JsonObject;
import idle.miau.Miau;
import idle.miau.clickgui.components.impl.CategoryComponent;
import idle.miau.clickgui.components.impl.ModuleComponent;
import idle.miau.module.Module;

public abstract class Setting {
    public String name;
    public boolean visible = true;

    public Setting(String name) {
        this.name = name;
    }

    public void setVisible(boolean visible, Module module) {
        if (visible == this.visible) {
            return;
        }
        this.visible = visible;
        if (Miau.clickGui == null || Miau.clickGui.categories == null) {
            return;
        }
        for (CategoryComponent categoryComponent : Miau.clickGui.categories) {
            if (categoryComponent.category == module.moduleCategory()) {
                for (ModuleComponent moduleComponent : categoryComponent.modules) {
                    if (moduleComponent.mod.getName().equals(module.getName())) {
                        moduleComponent.reloadSettings();
                        break;
                    }
                }
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public String getConfigKey() {
        return this.name;
    }

    public abstract void loadConfig(JsonObject data);
}
