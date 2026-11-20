package idle.miau.module;

import idle.miau.Miau;
import idle.miau.event.ModuleStateChangeEvent;
import idle.miau.helper.MouseHelper;

import idle.miau.module.impl.client.Notifications;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.config.ConfigIdentity;
import net.minecraft.client.Minecraft;
import idle.miau.mcp.McpRuntime;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Module {
    protected ArrayList<Setting> settings;
    private String moduleName;
    private DescriptionSetting description;
    private final String configId;
    private Module.category moduleCategory;
    private volatile boolean enabled;
    private int keycode;
    private boolean holdBind;
    protected static Minecraft mc;
    private boolean isToggled = false;
    public boolean canBeEnabled = true;
    public boolean canBeDisabled = true;
    public keystrokesmod.script.Script script;
    public boolean ignoreOnSave = false;
    public boolean hidden = false;
    public boolean closetModule = false;
    public boolean alwaysOn = false;
    public String lastInfo;
    public static boolean sort;
    public static List<String> categoriesString = new ArrayList<>();
    private final Map<Setting, String> settingConfigIds = new IdentityHashMap<Setting, String>();
    private final Map<Setting, String> explicitSettingConfigIds = new IdentityHashMap<Setting, String>();
    private boolean settingConfigIdsInitialized;

    static {
        for (category cat : category.values()) {
            categoriesString.add(cat.name());
        }
    }

    public Module(String moduleName, Module.category moduleCategory, int keycode) {
        this(moduleName, moduleCategory, keycode, null);
    }

    protected Module(String moduleName, Module.category moduleCategory, int keycode, String configId) {
        this.moduleName = moduleName;
        this.configId = configId == null || configId.trim().isEmpty()
                ? ConfigIdentity.className(getClass()) : configId;
        this.moduleCategory = moduleCategory;
        this.keycode = keycode;
        this.enabled = false;
        mc = Minecraft.getMinecraft();
        this.settings = new ArrayList();
    }

    public static Module getModule(Class<? extends Module> a) {
        return ModuleManager.getModule(a);
    }

    public Module(String name, Module.category moduleCategory) {
        this(name, moduleCategory, 0);
    }

    public Module(keystrokesmod.script.Script script) {
        this.enabled = false;
        this.moduleName = script.name;
        this.configId = "script:" + script.name;
        this.script = script;
        this.keycode = 0;
        this.moduleCategory = category.scripts;
        this.settings = new ArrayList<>();
        mc = Minecraft.getMinecraft();
    }

    public void onKeyBind() {
        if (this.keycode != 0) {
            try {
                if (!this.isToggled && (this.keycode >= 1000 ? ((this.keycode == 1069 || this.keycode == 1070) ? MouseHelper.isScrollDown(this.keycode) : Mouse.isButtonDown(this.keycode - 1000)) : Keyboard.isKeyDown(this.keycode))) {
                    this.toggle();
                    this.isToggled = true;
                }
                else if ((this.keycode >= 1000 ? ((this.keycode == 1069 || this.keycode == 1070) ? !MouseHelper.isScrollDown(this.keycode) : !Mouse.isButtonDown(this.keycode - 1000)) : !Keyboard.isKeyDown(this.keycode))) {
                    this.isToggled = false;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                Utils.sendMessage("&cFailed to check keybinding. Setting to none");
                this.keycode = 0;
            }
        }
    }

    public void syncKeyBindState() {
        if (this.keycode == 0) {
            return;
        }

        try {
            this.isToggled = this.keycode >= 1000
                    ? ((this.keycode == 1069 || this.keycode == 1070) ? MouseHelper.isScrollDown(this.keycode) : Mouse.isButtonDown(this.keycode - 1000))
                    : Keyboard.isKeyDown(this.keycode);
        }
        catch (Exception e) {
            e.printStackTrace();
            Utils.sendMessage("&cFailed to check keybinding. Setting to none");
            this.keycode = 0;
            this.isToggled = false;
        }
    }

    public boolean canBeEnabled() {
        if (this.script != null && this.script.error) {
            return false;
        }
        return this.canBeEnabled;
    }

    public boolean canBeDisabled() {
        return this.canBeDisabled;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void enable() {
        if (!this.canBeEnabled() || this.isEnabled()) {
            return;
        }

        this.setEnabled(true);
        idle.miau.diagnostic.MiauCrashDiagnostics.recordModuleToggle(this.getName(), true);
        boolean organized = false;
        boolean registered = false;
        try {
            ModuleManager.organizedModules.add(this);
            organized = true;
            if (ModuleManager.arraylist != null && ModuleManager.arraylist.isEnabled()) {
                ModuleManager.sort();
            }

            if (this.script != null) {
                if (idle.miau.Miau.scriptManager != null) {
                    idle.miau.Miau.scriptManager.onEnable(this.script);
                }
            }
            else {
                if (!alwaysOn) {
                    McpRuntime.EVENTS.register(this);
                    registered = true;
                }
                this.onEnable();
            }
            if (this.isEnabled()) {
                McpRuntime.EVENTS.post(new ModuleStateChangeEvent(this, true));
            }
            if (Utils.nullCheck() && Notifications.isChatEnabled()) {
                Utils.sendMessage("&7[&b" + this.getName() + "&7] &aEnabled");
            }
        }
        catch (RuntimeException exception) {
            rollbackEnable(organized, registered);
            Utils.log.error("Module {} failed during enable", this.getName(), exception);
            throw exception;
        }
        catch (Error error) {
            rollbackEnable(organized, registered);
            Utils.log.error("Module {} failed during enable", this.getName(), error);
            throw error;
        }
    }

    public void disable() {
        if (!this.isEnabled() || !this.canBeDisabled()) {
            return;
        }

        this.setEnabled(false);
        idle.miau.diagnostic.MiauCrashDiagnostics.recordModuleToggle(this.getName(), false);
        try {
            ModuleManager.organizedModules.remove(this);
            if (this.script != null) {
                if (idle.miau.Miau.scriptManager != null) {
                    idle.miau.Miau.scriptManager.onDisable(this.script);
                }
            }
            else {
                if (!alwaysOn) {
                    McpRuntime.EVENTS.unregister(this);
                }
                this.onDisable();
            }
            if (!this.isEnabled()) {
                McpRuntime.EVENTS.post(new ModuleStateChangeEvent(this, false));
            }
            if (Utils.nullCheck() && Notifications.isChatEnabled()) {
                Utils.sendMessage("&7[&b" + this.getName() + "&7] &cDisabled");
            }
        }
        catch (RuntimeException exception) {
            Utils.log.error("Module {} failed during disable", this.getName(), exception);
            throw exception;
        }
        catch (Error error) {
            Utils.log.error("Module {} failed during disable", this.getName(), error);
            throw error;
        }
    }

    private void rollbackEnable(boolean organized, boolean registered) {
        if (registered && !alwaysOn) {
            McpRuntime.EVENTS.unregister(this);
        }
        if (organized) {
            ModuleManager.organizedModules.remove(this);
        }
        this.setEnabled(false);
    }

    public String getInfo() {
        if (this.script != null) {
            String suffix = this.script.getString("getSuffix");
            return suffix == null ? "" : suffix;
        }
        return "";
    }

    public String getHudInfo() {
        for (Setting setting : settings) {
            if (setting instanceof MultiSelectSetting && "Mode".equalsIgnoreCase(setting.getName())) {
                return ((MultiSelectSetting) setting).getSelectedCount() + " active";
            }
        }
        return getInfo();
    }

    public String getInfoUpdate() {
        String info = getHudInfo();
        if (!Objects.equals(info, lastInfo)) {
            sort = true;
        }
        lastInfo = info;
        return info;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return this.moduleName;
    }

    public String getDisplayName() {
        return getName();
    }

    public final String getConfigId() {
        return configId;
    }

    public final String getSettingConfigId(Setting setting) {
        initializeSettingConfigIds();
        String configKey = settingConfigIds.get(setting);
        if (configKey == null) {
            throw new IllegalArgumentException("Setting is not registered by module " + configId);
        }
        return configKey;
    }

    protected final void renameModule(String name) {
        if (name != null && !name.isEmpty()) {
            this.moduleName = name;
        }
    }

    public String getNameInHud() {
        return getDisplayName();
    }

    public ArrayList<Setting> getSettings() {
        return this.settings;
    }

    public String getDescription() {
        if (description != null) {
            return description.getDesc();
        }
        if (moduleCategory == category.configs) {
            return "Loads and manages this saved configuration.";
        }
        if (moduleCategory == category.official_config) {
            return "Downloads and loads this official configuration.";
        }
        for (Setting setting : settings) {
            if (setting instanceof DescriptionSetting) {
                String description = ((DescriptionSetting) setting).getDesc();
                if (description != null && !description.trim().isEmpty()) {
                    return description;
                }
            }
        }
        return script == null ? "" : "Runs a custom client script.";
    }

    protected final void setDescription(String description) {
        this.description = new DescriptionSetting(Objects.requireNonNull(description));
    }

    public List<Setting> getDisplaySettings() {
        List<Setting> displaySettings = new ArrayList<>(settings);
        String description = getDescription();
        if (!description.isEmpty()) {
            displaySettings.removeIf(setting -> setting instanceof DescriptionSetting
                    && description.equals(((DescriptionSetting) setting).getDesc()));
            displaySettings.add(0, this.description == null
                    ? new DescriptionSetting(description) : this.description);
        }
        return displaySettings;
    }

    public void registerSetting(Setting setting) {
        this.settings.add(setting);
        this.settingConfigIdsInitialized = false;
    }

    protected void registerSetting(Setting setting, String configId) {
        this.settings.add(setting);
        this.explicitSettingConfigIds.put(setting, Objects.requireNonNull(configId));
        this.settingConfigIdsInitialized = false;
    }

    public Module.category moduleCategory() {
        return this.moduleCategory;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void toggle() {
        if (this.isEnabled()) {
            if (!this.canBeDisabled()) {
                return;
            }
            this.disable();
        } else {
            this.enable();
        }
        Miau.markCurrentConfigUnsaved();
    }

    public void onUpdate() {}

    public void guiUpdate() {}

    public void guiButtonToggled(ButtonSetting b) {
        Miau.markCurrentConfigUnsaved();
    }

    public void onSlide(SliderSetting setting) {}

    public void onCommandSettingChanged(Setting setting) {}

    public int getKeycode() {
        return this.keycode;
    }

    public void setBind(int keybind) {
        this.keycode = keybind;
        if (keybind == 0) {
            this.holdBind = false;
        }
    }

    public boolean isHoldBind() {
        return this.holdBind;
    }

    public void setHoldBind(boolean holdBind) {
        this.holdBind = this.keycode != 0 && holdBind;
    }

    public static enum category {
        combat,
        movement,
        player,
        world,
        render,
        minigames,
        bedwar_utils,
        fun,
        other,
        configs,
        official_config,
        scripts;
    }

    private void initializeSettingConfigIds() {
        synchronized (settingConfigIds) {
            if (settingConfigIdsInitialized) {
                return;
            }

            List<Field> settingFields = new ArrayList<Field>();
            for (Class<?> type = getClass(); type != null && Module.class.isAssignableFrom(type);
                 type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Setting.class.isAssignableFrom(field.getType())) {
                        settingFields.add(field);
                    }
                }
            }
            settingFields.sort(Comparator.comparing(
                    field -> ConfigIdentity.className(field.getDeclaringClass()) + "#" + ConfigIdentity.fieldName(field)));

            Map<Setting, Field> fieldsBySetting = new IdentityHashMap<Setting, Field>();
            for (Field field : settingFields) {
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object value = field.get(Modifier.isStatic(field.getModifiers()) ? null : this);
                    if (value instanceof Setting && settings.contains(value)) {
                        fieldsBySetting.putIfAbsent((Setting) value, field);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }

            settingConfigIds.clear();
            Set<String> usedIds = new HashSet<String>();
            for (int index = 0; index < settings.size(); index++) {
                Setting setting = settings.get(index);
                Field field = fieldsBySetting.get(setting);
                String explicitId = explicitSettingConfigIds.get(setting);
                String id = explicitId != null
                        ? explicitId
                        : field == null
                                ? String.format("setting.%03d", index)
                                : "setting." + ConfigIdentity.fieldName(field);
                if (!usedIds.add(id)) {
                    if (explicitId != null) {
                        throw new IllegalStateException("Duplicate setting config ID: " + id);
                    }
                    if (field != null) {
                        id = "setting." + ConfigIdentity.simpleName(field.getDeclaringClass()) + "." + ConfigIdentity.fieldName(field);
                        usedIds.add(id);
                    }
                }
                settingConfigIds.put(setting, id);
            }
            settingConfigIdsInitialized = true;
        }
    }
}
