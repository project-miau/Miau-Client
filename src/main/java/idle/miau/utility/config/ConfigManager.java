package idle.miau.utility.config;

import com.google.gson.*;
import idle.miau.BuildVersion;
import idle.miau.Miau;
import idle.miau.clickgui.ClickGui;
import idle.miau.clickgui.components.impl.CategoryComponent;
import idle.miau.event.PostConfigLoadEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.client.IRC;
import idle.miau.module.impl.client.Notifications;
import idle.miau.module.impl.combat.velocity.VelocityModule;
import idle.miau.module.impl.minigames.SpeedBuilders;
import idle.miau.module.impl.other.HideWindow;
import idle.miau.module.impl.player.Backtrack;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.module.impl.render.TargetHUD;
import idle.miau.module.impl.render.Watermark;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.ClientStorage;
import idle.miau.utility.Utils;
import idle.miau.mcp.McpRuntime;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.*;

public class ConfigManager implements IMinecraftInstance {
    private static final String DEFAULT_CONFIG_NAME = "default";
    private static final String CONFIG_DIRECTORY_NAME = "configs";
    private static final String LEGACY_CONFIG_DIRECTORY_NAME = "configs";
    private static final String ACTIVE_CONFIG_FILE_NAME = "active-config.txt";
    private static final int CONFIG_FORMAT_VERSION = 11;
    private static final int HUD_SCALE_REBASE_FORMAT_VERSION = 3;
    private static final int ANIMATIONS_MODE_REBASE_FORMAT_VERSION = 6;
    private static final int CRITICALS_MODE_REBASE_FORMAT_VERSION = 7;
    private static final int CLICK_GUI_MODE_REBASE_FORMAT_VERSION = 8;
    private static final int AUTOBLOCK_MODE_REBASE_FORMAT_VERSION = 10;
    private static final int SCAFFOLD_MODE_REBASE_FORMAT_VERSION = 11;
    private static final char[] INVALID_CONFIG_NAME_CHARS = new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'};

    private static final class SavedCategoryState {
        final float x, y;
        final boolean opened;

        SavedCategoryState(float x, float y, boolean opened) {
            this.x = x;
            this.y = y;
            this.opened = opened;
        }
    }

    private static final class RequestedModuleState {
        boolean enabled;
        int keybind;
        boolean hold;
        Boolean hidden;

        RequestedModuleState(boolean enabled, int keybind) {
            this.enabled = enabled;
            this.keybind = keybind;
            this.hold = false;
        }
    }

    public File directory;
    public List<Config> configs = new ArrayList<>();
    private final File activeConfigFile;

    public ConfigManager() {
        File miauDirectory = ClientStorage.ensureDirectory(mc.mcDataDir);
        activeConfigFile = new File(miauDirectory, ACTIVE_CONFIG_FILE_NAME);
        directory = new File(miauDirectory, CONFIG_DIRECTORY_NAME);
        File legacyDirectory = new File(
                ClientStorage.getLegacyDirectory(mc.mcDataDir), LEGACY_CONFIG_DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            System.out.println("There was an issue creating configs directory.");
            return;
        }
        migrateLegacyConfigs(legacyDirectory);
        if (getConfigFiles().isEmpty()) {
            saveConfig(new Config(DEFAULT_CONFIG_NAME, 0));
        }
    }

    private void migrateLegacyConfigs(File legacyDirectory) {
        try {
            ClientStorage.migrateJsonFiles(legacyDirectory, directory);
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public boolean saveConfig(Config config) {
        if (config == null) {
            return false;
        }
        config.getModule().saved = false;
        JsonObject jsonObject = new JsonObject();
        ConfigVersion.write(jsonObject);
        jsonObject.addProperty("formatVersion", CONFIG_FORMAT_VERSION);
        jsonObject.addProperty("keybind", config.getModule().getKeycode());
        JsonArray jsonArray = new JsonArray();
        for (Module module : Miau.moduleManager.getModules()) {
            if (module.ignoreOnSave) {
                continue;
            }
            JsonObject moduleInformation = module.ignoreOnSave ? getModuleStateObject(module) : getJsonObject(module);
            jsonArray.add(moduleInformation);
        }

        jsonObject.add("modules", jsonArray);
        JsonArray guiCategories = new JsonArray();
        for (CategoryComponent category : ClickGui.categories) {
            JsonObject categoryState = new JsonObject();
            categoryState.addProperty("name", category.category.name());
            categoryState.addProperty("x", category.getX());
            categoryState.addProperty("y", category.getY());
            categoryState.addProperty("opened", category.isOpened());
            guiCategories.add(categoryState);
        }
        jsonObject.add("guiCategories", guiCategories);
        Path targetFile = new File(directory, config.getName() + ".json").toPath();
        try {
            ConfigFileCodec.write(targetFile, jsonObject);
        }
        catch (Exception e) {
            failedMessage("save", config.getName());
            e.printStackTrace();
            return false;
        }
        config.getModule().saved = true;
        return true;
    }

    static void replaceConfigFile(Path temporaryFile, Path targetFile) throws IOException {
        FileSystemException lastFailure = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                clearReadOnly(targetFile);
                try {
                    Files.move(temporaryFile, targetFile,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                }
                catch (FileSystemException atomicFailure) {
                    Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            catch (FileSystemException failure) {
                lastFailure = failure;
                if (!(failure instanceof AccessDeniedException) && attempt > 0) {
                    throw failure;
                }
                if (attempt < 5) {
                    pauseBeforeReplaceRetry(attempt, failure);
                }
            }
        }
        throw lastFailure;
    }

    private static void clearReadOnly(Path targetFile) throws IOException {
        if (!Files.exists(targetFile, LinkOption.NOFOLLOW_LINKS)) return;
        DosFileAttributeView attributes = Files.getFileAttributeView(
                targetFile, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes != null && attributes.readAttributes().isReadOnly()) {
            attributes.setReadOnly(false);
        }
    }

    private static void pauseBeforeReplaceRetry(int attempt, IOException failure) throws IOException {
        try {
            Thread.sleep(25L * (attempt + 1L));
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            IOException exception = new IOException("Interrupted while replacing config file", interrupted);
            exception.addSuppressed(failure);
            throw exception;
        }
    }

    private static void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) return;
        try {
            Files.deleteIfExists(temporaryFile);
        }
        catch (IOException ignored) {
        }
    }

    public Config createConfig(String requestedName, int bind) {
        String configName = normalizeConfigName(requestedName);
        String validationError = validateConfigName(configName, null);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return null;
        }

        Config config = new Config(configName, bind);
        if (!saveConfig(config)) {
            return null;
        }
        configs.add(config);
        refreshConfigModules();
        return config;
    }

    public static JsonObject getJsonObject(Module module) {
        JsonObject moduleInformation = new JsonObject();
        moduleInformation.addProperty("name", (module.moduleCategory() == Module.category.scripts) ?  "sc-" + module.getName() :  module.getName());
        if (module.canBeEnabled) {
            moduleInformation.addProperty("enabled", module.isEnabled());
            moduleInformation.addProperty("hidden", module.isHidden());
            moduleInformation.addProperty("keybind", module.getKeycode());
            moduleInformation.addProperty("hold", module.isHoldBind());
        }
        if (module instanceof ArrayListModule) {
            ArrayListModule hud = (ArrayListModule) module;
            moduleInformation.addProperty("posX", ArrayListModule.posX);
            moduleInformation.addProperty("posY", ArrayListModule.posY);
            moduleInformation.addProperty("relPosX", ArrayListModule.getRelativePosX());
            moduleInformation.addProperty("relPosY", ArrayListModule.getRelativePosY());
            moduleInformation.addProperty("modernRelPosX", hud.getModernRelativePosX());
            moduleInformation.addProperty("modernRelPosY", hud.getModernRelativePosY());
            moduleInformation.addProperty("potionPosX", hud.getPotionPositionX());
            moduleInformation.addProperty("potionPosY", hud.getPotionPositionY());
        }
        else if (module instanceof Watermark) {
            Watermark watermark = (Watermark) module;
            moduleInformation.addProperty("posX", watermark.getPositionX());
            moduleInformation.addProperty("posY", watermark.getPositionY());
        }
        else if (module instanceof TargetHUD) {
            moduleInformation.addProperty("posX", ModuleManager.targetHUD.posX);
            moduleInformation.addProperty("posY", ModuleManager.targetHUD.posY);
        }
        else if (module instanceof SpeedBuilders) {
            SpeedBuilders speedBuilders = (SpeedBuilders) module;
            moduleInformation.addProperty("hudPosX", speedBuilders.getHudX());
            moduleInformation.addProperty("hudPosY", speedBuilders.getHudY());
        }
        else if (module instanceof HideWindow) {
            HideWindow hideWindow = (HideWindow) module;
            moduleInformation.addProperty("posX", hideWindow.getPosX());
            moduleInformation.addProperty("posY", hideWindow.getPosY());
            moduleInformation.addProperty("relPosX", hideWindow.getRelativePosX());
            moduleInformation.addProperty("relPosY", hideWindow.getRelativePosY());
        }

        JsonArray portableSettings = new JsonArray();
        for (Setting setting : module.getSettings()) {
            JsonElement value = serializeSetting(setting);
            if (value == null) {
                continue;
            }
            moduleInformation.add(module.getSettingConfigId(setting), value);
            JsonObject portableSetting = new JsonObject();
            portableSetting.addProperty("name", setting.getConfigKey());
            portableSetting.add("value", value);
            portableSettings.add(portableSetting);
        }
        if (portableSettings.size() > 0) {
            moduleInformation.add("portableSettings", portableSettings);
        }
        return moduleInformation;
    }

    private static JsonElement serializeSetting(Setting setting) {
        if (setting instanceof ButtonSetting && !((ButtonSetting) setting).isMethodButton) {
            return new JsonPrimitive(((ButtonSetting) setting).isToggled());
        }
        if (setting instanceof SliderSetting) {
            return new JsonPrimitive(((SliderSetting) setting).getInput());
        }
        if (setting instanceof RangeSetting) {
            return ((RangeSetting) setting).toJsonObject();
        }
        if (setting instanceof MultiSelectSetting) {
            return ((MultiSelectSetting) setting).toJsonArray();
        }
        if (setting instanceof KeySetting) {
            return new JsonPrimitive(((KeySetting) setting).getKey());
        }
        if (setting instanceof TextSetting) {
            TextSetting textSetting = (TextSetting) setting;
            return textSetting.isSensitive() ? null : new JsonPrimitive(textSetting.getText());
        }
        if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting;
            return new JsonPrimitive(color.getRed() + "," + color.getGreen() + ","
                    + color.getBlue() + "," + color.getAlpha());
        }
        if (setting instanceof BlockListSetting) {
            return ((BlockListSetting) setting).toJsonArray();
        }
        if (setting instanceof StringListSetting) {
            return ((StringListSetting) setting).toJsonArray();
        }
        return null;
    }

    static void applySpeedBuildersHudPosition(SpeedBuilders module, JsonObject moduleInformation) {
        float hudX = readFiniteFloat(moduleInformation, "hudPosX", module.getHudX());
        float hudY = readFiniteFloat(moduleInformation, "hudPosY", module.getHudY());
        module.setHudPosition(hudX, hudY);
    }

    static void applyWatermarkPosition(Watermark module, JsonObject moduleInformation) {
        double x = readFiniteDouble(moduleInformation, "posX",
                readFiniteDouble(moduleInformation, "setting.posX",
                        readFiniteDouble(moduleInformation, "Position X", module.getPositionX())));
        double y = readFiniteDouble(moduleInformation, "posY",
                readFiniteDouble(moduleInformation, "setting.posY",
                        readFiniteDouble(moduleInformation, "Position Y", module.getPositionY())));
        module.setPosition(x, y);
    }

    private static float readFiniteFloat(JsonObject object, String key, float fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isNumber()) {
            return fallback;
        }
        float value = object.getAsJsonPrimitive(key).getAsFloat();
        return Float.isFinite(value) ? value : fallback;
    }

    private static double readFiniteDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isNumber()) {
            return fallback;
        }
        double value = object.getAsJsonPrimitive(key).getAsDouble();
        return Double.isFinite(value) ? value : fallback;
    }

    private static JsonObject getModuleStateObject(Module module) {
        JsonObject moduleInformation = new JsonObject();
        moduleInformation.addProperty("name", module.getName());
        if (module.canBeEnabled) {
            moduleInformation.addProperty("enabled", module.isEnabled());
            moduleInformation.addProperty("hidden", module.isHidden());
            moduleInformation.addProperty("keybind", module.getKeycode());
            moduleInformation.addProperty("hold", module.isHoldBind());
        }
        return moduleInformation;
    }

    public String getLastSelectedConfigName() {
        if (!activeConfigFile.isFile()) {
            return DEFAULT_CONFIG_NAME;
        }
        try {
            String configName = new String(Files.readAllBytes(activeConfigFile.toPath()), StandardCharsets.UTF_8).trim();
            return configName.isEmpty() ? DEFAULT_CONFIG_NAME : configName;
        }
        catch (IOException exception) {
            Utils.log.warn("Unable to read selected config name", exception);
            return DEFAULT_CONFIG_NAME;
        }
    }

    private void rememberConfigSelection(String configName) {
        try {
            Files.write(activeConfigFile.toPath(), configName.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            Utils.log.error("Unable to save selected config name {}", configName, exception);
        }
    }

    public boolean loadConfig(String name) {
        return loadConfig(name, true);
    }

    private boolean loadConfig(String name, boolean rememberSelection) {
        if (Miau.notificationService != null) {
            Miau.notificationService.beginConfigRestore();
        }
        try {
        Config existingConfig = getConfig(name);
        String configName = existingConfig != null ? existingConfig.getName() : normalizeConfigName(name);
        boolean foundConfig = false;
        for (File file : getConfigFiles()) {
            if (!file.exists()) {
                failedMessage("load", configName);
                System.out.println("Failed to load " + configName);
                return false;
            }
            if (!file.getName().equals(configName + ".json")) {
                continue;
            }
            foundConfig = true;
            try {
                JsonObject configJson = ConfigFileCodec.read(file.toPath());
                JsonArray modules = configJson.getAsJsonArray("modules");
                String savedClientVersion = ConfigVersion.read(configJson);
                boolean olderClientVersion = ConfigVersion.isOlderThanClient(configJson);
                List<Module> loadableModules = getLoadableModules();
                Map<Module, RequestedModuleState> requestedModuleStates = createDefaultRequestedModuleStates(loadableModules);
                Map<Module, JsonObject> loadedModuleData = new LinkedHashMap<Module, JsonObject>();
                int savedHiddenStates = 0;
                boolean hasVisibleSavedModule = false;
                Map<String, SavedCategoryState> savedGuiCategoryState = readCategoryStates(configJson, "guiCategories");
                int formatVersion = configJson.has("formatVersion")
                        ? configJson.get("formatVersion").getAsInt() : 1;
                if (formatVersion < HUD_SCALE_REBASE_FORMAT_VERSION) {
                    migrateLegacyHudScales(modules);
                }
                if (formatVersion < ANIMATIONS_MODE_REBASE_FORMAT_VERSION) {
                    migrateLegacyAnimationsModes(modules);
                }
                for (JsonElement moduleJson : modules) {
                    JsonObject moduleInformation = moduleJson.getAsJsonObject();
                    String moduleName = moduleInformation.has("name")
                            ? moduleInformation.get("name").getAsString() : "";

                    if (moduleName == null || moduleName.isEmpty()) {
                        continue;
                    }

                    Module module = moduleInformation.has("id")
                            ? ModuleManager.getModuleByConfigId(moduleInformation.get("id").getAsString())
                            : null;
                    if (module == null) {
                        module = Miau.moduleManager.getModule(moduleName);
                    }

                    if (module == null) {
                        continue;
                    }

                    loadedModuleData.put(module, moduleInformation);

                    if (module.canBeEnabled()) {
                        RequestedModuleState requestedState = requestedModuleStates.get(module);
                        if (requestedState == null) {
                            requestedState = new RequestedModuleState(false, 0);
                            requestedModuleStates.put(module, requestedState);
                        }
                        if (moduleInformation.has("enabled")) {
                            requestedState.enabled = moduleInformation.get("enabled").getAsBoolean();
                        }
                        if (moduleInformation.has("hidden")) {
                            requestedState.hidden = moduleInformation.get("hidden").getAsBoolean();
                            savedHiddenStates++;
                            if (!requestedState.hidden) {
                                hasVisibleSavedModule = true;
                            }
                        }
                        if (moduleInformation.has("keybind")) {
                            requestedState.keybind = moduleInformation.get("keybind").getAsInt();
                        }
                        if (moduleInformation.has("hold")) {
                            requestedState.hold = moduleInformation.get("hold").getAsBoolean();
                        }
                    }
                }
                if (savedHiddenStates > 0 && !hasVisibleSavedModule) {
                    for (RequestedModuleState requestedState : requestedModuleStates.values()) {
                        if (requestedState.hidden != null) {
                            requestedState.hidden = false;
                        }
                    }
                }
                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    if (!requestedState.enabled && module.isEnabled()) {
                        module.disable();
                    }
                }

                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    module.setBind(requestedState.keybind);
                    module.setHoldBind(requestedState.hold);
                    if (requestedState.hidden != null) {
                        module.setHidden(requestedState.hidden);
                    }
                }

                for (Map.Entry<Module, JsonObject> entry : loadedModuleData.entrySet()) {
                    Module module = entry.getKey();
                    JsonObject moduleInformation = entry.getValue();

                    if (module instanceof ArrayListModule) {
                        ArrayListModule hud = (ArrayListModule) module;
                        if (!moduleInformation.has("setting.hudMode") && !moduleInformation.has("Mode")) {
                            ArrayListModule.hudMode.setValue(0);
                        }
                        if (moduleInformation.has("relPosX") && moduleInformation.has("relPosY")) {
                            ArrayListModule.setRelativePosition(
                                    moduleInformation.get("relPosX").getAsFloat(),
                                    moduleInformation.get("relPosY").getAsFloat()
                            );
                        }
                        else if (moduleInformation.has("posX") || moduleInformation.has("posY")) {
                            float hudX = moduleInformation.has("posX") ? moduleInformation.get("posX").getAsFloat() : ArrayListModule.posX;
                            float hudY = moduleInformation.has("posY") ? moduleInformation.get("posY").getAsFloat() : ArrayListModule.posY;
                            ArrayListModule.setAbsolutePosition(hudX, hudY);
                        }
                        hud.setPotionPosition(
                                readFiniteDouble(moduleInformation, "potionPosX", hud.getPotionPositionX()),
                                readFiniteDouble(moduleInformation, "potionPosY", hud.getPotionPositionY()));
                        if (moduleInformation.has("setting.waveAxis")
                                || moduleInformation.has("setting.alphabeticalSort")) {
                            migrateLegacyArrayListComponentSettings(moduleInformation);
                        }
                    }
                    else if (module instanceof Watermark) {
                        applyWatermarkPosition((Watermark) module, moduleInformation);
                    }
                    else if (module.getName().equals("TargetHUD")) {
                        migrateLegacyTargetHudMode(moduleInformation);
                        if (moduleInformation.has("posX")) {
                            int posX = moduleInformation.get("posX").getAsInt();
                            ModuleManager.targetHUD.posX = posX;
                        }
                        if (moduleInformation.has("posY")) {
                            int posY = moduleInformation.get("posY").getAsInt();
                            ModuleManager.targetHUD.posY = posY;
                        }
                    }
                    else if (module instanceof SpeedBuilders) {
                        applySpeedBuildersHudPosition((SpeedBuilders) module, moduleInformation);
                    }
                    else if (module instanceof HideWindow) {
                        HideWindow hideWindow = (HideWindow) module;
                        if (moduleInformation.has("relPosX") && moduleInformation.has("relPosY")) {
                            hideWindow.setRelativePosition(
                                    moduleInformation.get("relPosX").getAsFloat(),
                                    moduleInformation.get("relPosY").getAsFloat());
                        }
                        else if (moduleInformation.has("posX") || moduleInformation.has("posY")) {
                            float posX = moduleInformation.has("posX")
                                    ? moduleInformation.get("posX").getAsFloat()
                                    : hideWindow.getPosX();
                            float posY = moduleInformation.has("posY")
                                    ? moduleInformation.get("posY").getAsFloat()
                                    : hideWindow.getPosY();
                            hideWindow.setAbsolutePosition(posX, posY);
                        }
                    }

                    prepareSettingValues(module, moduleInformation);
                    if (formatVersion < 9) migrateLegacyFonts(module, moduleInformation);
                    if (formatVersion < CLICK_GUI_MODE_REBASE_FORMAT_VERSION
                            && module instanceof idle.miau.module.impl.client.ClickGui) {
                        migrateLegacyClickGuiMode(module, moduleInformation);
                    }
                    if (formatVersion < CRITICALS_MODE_REBASE_FORMAT_VERSION
                            && module instanceof idle.miau.module.impl.combat.Criticals) {
                        migrateLegacyCriticalsMode(moduleInformation);
                    }
                    if (formatVersion < AUTOBLOCK_MODE_REBASE_FORMAT_VERSION
                            && module instanceof idle.miau.module.impl.combat.Autoblock) {
                        migrateLegacyAutoblockMode(moduleInformation);
                    }
                    if (formatVersion < SCAFFOLD_MODE_REBASE_FORMAT_VERSION
                            && module instanceof Scaffold) {
                        migrateLegacyScaffoldMode(moduleInformation);
                    }
                    for (Setting setting : module.getSettings()) {
                        if (setting instanceof TextSetting && ((TextSetting) setting).isSensitive()) {
                            continue;
                        }
                        copyStableSettingValueToLegacyKey(module, setting, moduleInformation);
                        setting.loadConfig(moduleInformation);
                    }
                    if (module instanceof ArrayListModule
                            && moduleInformation.has("modernRelPosX") && moduleInformation.has("modernRelPosY")) {
                        ((ArrayListModule) module).setModernRelativePosition(
                                readFiniteFloat(moduleInformation, "modernRelPosX", Float.NaN),
                                readFiniteFloat(moduleInformation, "modernRelPosY", Float.NaN));
                    }
                }

                for (Module module : loadableModules) {
                    RequestedModuleState requestedState = requestedModuleStates.get(module);
                    if (requestedState == null) {
                        continue;
                    }

                    if (requestedState.enabled && !module.isEnabled()) {
                        module.enable();
                    }
                }

                Miau.currentConfig = getConfig(configName);

                Miau.clickGui.refreshAfterConfigLoad();
                for (CategoryComponent c : ClickGui.categories) {
                    SavedCategoryState state = savedGuiCategoryState.get(c.category.name());
                    if (state != null) {
                        c.applySavedState(state.x, state.y, state.opened, true);
                    }
                }
                McpRuntime.EVENTS.post(new PostConfigLoadEvent(Miau.currentConfig.getName()));
                if (olderClientVersion) {
                    Utils.sendMessage("&eConfig &b" + configName + " &ewas saved on client &b"
                            + savedClientVersion + "&e, older than &b" + BuildVersion.VERSION
                            + "&e. Some settings may not match.");
                }
                if (rememberSelection) {
                    rememberConfigSelection(Miau.currentConfig.getName());
                    Miau.currentConfig.getModule().saved = true;
                }
                return true;
            }
            catch (Exception e) {
                failedMessage("load", configName);
                e.printStackTrace();
                return false;
            }
        }
        if (!foundConfig) {
            failedMessage("load", configName);
        }
        return false;
        } finally {
            if (Miau.notificationService != null) {
                Miau.notificationService.endConfigRestore();
            }
        }
    }

    public boolean applyExternalConfig(String json) {
        JsonObject configJson;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (parsed == null || !parsed.isJsonObject()) {
                return false;
            }
            configJson = parsed.getAsJsonObject();
            if (!configJson.has("modules") || !configJson.get("modules").isJsonArray()) {
                return false;
            }
        }
        catch (Exception exception) {
            return false;
        }

        String transientName = "external-" + UUID.randomUUID().toString().replace("-", "");
        Path targetFile = new File(directory, transientName + ".json").toPath();
        Config previousConfig = Miau.currentConfig;
        Config transientConfig = new Config(transientName, 0);
        boolean registered = false;
        boolean loaded = false;
        try {
            ConfigFileCodec.write(targetFile, configJson);
            configs.add(transientConfig);
            registered = true;
            loaded = loadConfig(transientName, false);
            return loaded;
        }
        catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
        finally {
            if (registered) {
                configs.remove(transientConfig);
            }
            Miau.currentConfig = previousConfig;
            if (loaded && previousConfig != null) {
                Miau.markCurrentConfigUnsaved();
            }
            deleteTemporaryFile(targetFile);
            deleteExternalBackups(transientName);
        }
    }

    private void deleteExternalBackups(String transientName) {
        File[] files = directory.listFiles((parent, name) -> name.startsWith(transientName + ".json.v")
                && name.endsWith(".bak"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            deleteTemporaryFile(file.toPath());
        }
    }

    static void migrateLegacyHudScales(JsonArray modules) {
        if (modules == null) {
            return;
        }
        for (JsonElement element : modules) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject moduleData = element.getAsJsonObject();
            String moduleName = moduleData.has("name") ? moduleData.get("name").getAsString() : "";
            if ("HUD".equals(moduleName) || "ArrayList".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.fontSize", "Scale", 0.5D, 2.0D);
            }
            else if ("Nametags".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.scale", "Scale", 0.1D, 2.0D);
            }
            else if ("TNT Timer".equals(moduleName) || "TNTTimer".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.scale", "Scale", 0.5D, 3.0D);
            }
            else if ("BedESP".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.defenseScale", "Defense scale", 0.1D, 2.0D);
            }
            else if ("KeyStrokes".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.hudScale", "Scale", 0.5D, 2.0D);
            }
            else if ("TargetHUD".equals(moduleName)) {
                migrateLegacyScaleValue(moduleData, "setting.hudScale", "Scale", 0.5D, 2.0D);
            }
        }
    }

    static void migrateLegacyAnimationsModes(JsonArray modules) {
        if (modules == null) {
            return;
        }
        for (JsonElement element : modules) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject moduleData = element.getAsJsonObject();
            if (!"Animations".equals(moduleData.has("name")
                    ? moduleData.get("name").getAsString() : "")) {
                continue;
            }
            migrateLegacyAnimationMode(moduleData, "setting.mode");
            migrateLegacyAnimationMode(moduleData, "Mode");
            JsonElement portableSettingsElement = moduleData.get("portableSettings");
            if (portableSettingsElement == null || !portableSettingsElement.isJsonArray()) {
                continue;
            }
            JsonArray portableSettings = portableSettingsElement.getAsJsonArray();
            for (JsonElement settingElement : portableSettings) {
                if (settingElement == null || !settingElement.isJsonObject()) {
                    continue;
                }
                JsonObject settingData = settingElement.getAsJsonObject();
                if (settingData.has("name") && settingData.get("name").isJsonPrimitive()
                        && "Mode".equalsIgnoreCase(settingData.get("name").getAsString())) {
                    migrateLegacyAnimationMode(settingData, "value");
                }
            }
        }
    }

    private static void migrateLegacyAnimationMode(JsonObject moduleData, String key) {
        if (!moduleData.has(key) || !moduleData.get(key).isJsonPrimitive()
                || !moduleData.getAsJsonPrimitive(key).isNumber()) {
            return;
        }
        double value = moduleData.getAsJsonPrimitive(key).getAsDouble();
        int legacyMode = (int) value;
        if (legacyMode != value || legacyMode < 0 || legacyMode > 12) {
            return;
        }
        int migratedMode;
        if (legacyMode <= 2) {
            migratedMode = legacyMode;
        }
        else if (legacyMode == 4) {
            migratedMode = 3;
        }
        else if (legacyMode == 9) {
            migratedMode = 4;
        }
        else {
            migratedMode = 1;
        }
        moduleData.addProperty(key, migratedMode);
    }

    static void migrateLegacyFonts(Module module, JsonObject data) {
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof SliderSetting slider) || !slider.isString) continue;
            String[] options = slider.getOptions();
            if (options.length < 3 || !"Minecraft".equals(options[0]) || !"Sf-Bold".equals(options[1])
                    || !"ProductSans".equals(options[2])) continue;
            String stableKey = module.getSettingConfigId(setting);
            JsonElement value = data.has(stableKey) ? data.get(stableKey) : data.get(setting.getConfigKey());
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
            int migrated = idle.miau.utility.font.FontManager.migrateLegacyFontIndex(value.getAsInt());
            data.addProperty(stableKey, migrated);
            data.addProperty(setting.getConfigKey(), migrated);
        }
    }

    static void migrateLegacyClickGuiMode(Module module, JsonObject data) {
        Setting mode = idle.miau.module.impl.client.ClickGui.mode;
        String stableKey = module.getSettingConfigId(mode);
        JsonElement value = data.has(stableKey) ? data.get(stableKey) : data.get(mode.getConfigKey());
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return;
        }
        int previous = value.getAsInt();
        int migrated = previous == 0 ? 0 : previous == 2 ? 1 : 2;
        data.addProperty(stableKey, migrated);
        data.addProperty(mode.getConfigKey(), migrated);
    }

    static void migrateLegacyScaffoldMode(JsonObject moduleData) {
        for (String key : new String[]{"setting.mode", "Mode"}) {
            JsonElement value = moduleData.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    && value.getAsDouble() == 3.0D) {
                moduleData.addProperty(key, 4.0D);
            }
        }
    }

    static void migrateLegacyCriticalsMode(JsonObject moduleData) {
        for (String key : new String[] {"setting.mode", "Mode"}) {
            JsonElement value = moduleData.get(key);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            double legacyMode = value.getAsDouble();
            int migratedMode = legacyMode == 4 ? 2 : legacyMode == 5 ? 3 : legacyMode == 1 ? 1 : 0;
            moduleData.addProperty(key, migratedMode);
        }
    }

    static void migrateLegacyAutoblockMode(JsonObject data) {
        JsonElement value = hudSettingValue(data, "setting.mode", "Mode");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return;
        int previous = value.getAsInt();
        int selected = previous == 4 ? 2 : previous == 1 ? 1 : 0;
        data.addProperty("setting.mode", selected);
        data.addProperty("Mode", selected);
    }

    static void migrateLegacyTargetHudMode(JsonObject moduleData) {
        if (moduleData == null || moduleData.has("setting.ravenMode") || moduleData.has("Raven mode")) {
            return;
        }
        String key = moduleData.has("setting.mode") ? "setting.mode" : moduleData.has("Mode") ? "Mode" : null;
        if (key == null || !moduleData.get(key).isJsonPrimitive()) {
            return;
        }
        try {
            int previousMode = moduleData.get(key).getAsInt();
            moduleData.addProperty(key, previousMode >= 3 ? 1 : 0);
        }
        catch (RuntimeException ignored) {
        }
    }

    private static void migrateLegacyScaleValue(JsonObject moduleData, String stableKey, String legacyKey,
                                                double minimum, double maximum) {
        String key = moduleData.has(stableKey) ? stableKey : legacyKey;
        if (!moduleData.has(key) || !moduleData.get(key).isJsonPrimitive()
                || !moduleData.getAsJsonPrimitive(key).isNumber()) {
            return;
        }
        double value = moduleData.getAsJsonPrimitive(key).getAsDouble();
        moduleData.addProperty(key, idle.miau.utility.render.HudScale.migrateLegacyScale(value, minimum, maximum));
    }

    private static void migrateLegacyArrayListComponentSettings(JsonObject moduleInformation) {
        copyLegacySetting(moduleInformation, "setting.045", "Potion Lower case");
        copyLegacySetting(moduleInformation, "setting.046", "Potion Background Alpha");
    }

    private static void copyLegacySetting(JsonObject moduleInformation, String legacyKey, String settingKey) {
        if (!moduleInformation.has(settingKey) && moduleInformation.has(legacyKey)) {
            moduleInformation.add(settingKey, moduleInformation.get(legacyKey));
        }
    }

    public static void copyStableSettingValueToLegacyKey(
            Module module, Setting setting, JsonObject moduleInformation) {
        String stableKey = module.getSettingConfigId(setting);
        if (!moduleInformation.has(setting.getConfigKey()) && moduleInformation.has(stableKey)) {
            moduleInformation.add(setting.getConfigKey(), moduleInformation.get(stableKey));
        }
    }

    public static void prepareSettingValues(Module module, JsonObject moduleInformation) {
        if (module == null || moduleInformation == null) {
            return;
        }
        migrateLegacyVelocityDelay(module, moduleInformation);
        migrateBacktrackDelayBounds(module, moduleInformation);
        migrateHudSettingGroups(module, moduleInformation);
        applyPortableSettingValues(module, moduleInformation);
        applyObfuscatedSettingValues(module, moduleInformation);
        migrateHudControls(module, moduleInformation);
    }

    private static void migrateLegacyVelocityDelay(Module module, JsonObject data) {
        if (!(module instanceof VelocityModule)) {
            return;
        }
        JsonElement delay = legacyVelocitySetting(data, 6);
        if (delay == null || !delay.isJsonPrimitive() || !delay.getAsJsonPrimitive().isBoolean()) {
            return;
        }
        JsonElement[] previous = new JsonElement[37];
        for (int index = 0; index < previous.length; index++) {
            previous[index] = legacyVelocitySetting(data, index);
        }
        List<Setting> settings = getSerializedSettings(module);
        MultiSelectSetting mode = (MultiSelectSetting) settings.get(0);
        MultiSelectSetting selected = new MultiSelectSetting("Mode", mode.getOptions(), "Standard");
        JsonObject selection = new JsonObject();
        if (previous[0] != null) {
            selection.add("Mode", previous[0]);
            selected.loadConfig(selection);
        }
        JsonPrimitive enabled = new JsonPrimitive(true);
        boolean standardDelayed = selected.isSelected("Standard") && enabled.equals(previous[6]);
        boolean oldGrimDelayed = selected.isSelected("OldGrim") && enabled.equals(previous[19]);
        int delayStart = (oldGrimDelayed && !standardDelayed
                || !selected.isSelected("Standard") && selected.isSelected("OldGrim")) ? 20 : 7;
        if (standardDelayed || oldGrimDelayed) {
            selected.setSelected("Delayed", true);
        }
        for (int index = 1; index < previous.length; index++) {
            data.remove(String.format(Locale.ROOT, "setting.%03d", index));
        }
        data.remove("portableSettings");
        data.add(module.getSettingConfigId(mode), selected.toJsonArray());
        data.add(mode.getConfigKey(), selected.toJsonArray());
        int[] retainedIndices = {0, 1, 2, 3, 4, 5, 16, 17, 18, 29, 30, 31, 32, 33, 34, 35, 36};
        for (int index = 1; index < settings.size(); index++) {
            Setting setting = settings.get(index);
            int previousIndex = index < retainedIndices.length
                    ? retainedIndices[index] : delayStart + index - retainedIndices.length;
            JsonElement value = previous[previousIndex];
            if (isCompatibleSettingValue(setting, value)) {
                data.add(module.getSettingConfigId(setting), value);
            }
        }
    }

    private static JsonElement legacyVelocitySetting(JsonObject data, int index) {
        String key = index == 0 ? "setting.mode" : String.format(Locale.ROOT, "setting.%03d", index);
        if (data.has(key)) {
            return data.get(key);
        }
        if (index == 0 && data.has("Mode")) {
            return data.get("Mode");
        }
        JsonElement portable = data.get("portableSettings");
        if (portable != null && portable.isJsonArray() && index < portable.getAsJsonArray().size()) {
            JsonElement entry = portable.getAsJsonArray().get(index);
            if (entry.isJsonObject()) {
                return entry.getAsJsonObject().get("value");
            }
        }
        return null;
    }

    private static void migrateBacktrackDelayBounds(Module module, JsonObject data) {
        if (!(module instanceof Backtrack)) {
            return;
        }
        JsonElement legacy = data.get("setting.014");
        if (legacy == null) {
            legacy = backtrackDelayValue(data, "Maximum delay");
        }
        String[] ids = {"setting.normal.minDelay", "setting.014"};
        String[] names = {"Min Delay", "Max Delay"};
        for (int index = 0; index < ids.length; index++) {
            if (data.has(ids[index])) {
                continue;
            }
            JsonElement value = backtrackDelayValue(data, names[index]);
            if (value == null) {
                value = legacy;
            }
            if (value != null) {
                data.add(ids[index], value);
            }
        }
    }

    private static JsonElement backtrackDelayValue(JsonObject data, String name) {
        if (data.has(name)) {
            return data.get(name);
        }
        JsonElement portable = data.get("portableSettings");
        if (portable != null && portable.isJsonArray()) {
            for (JsonElement value : portable.getAsJsonArray()) {
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject entry = value.getAsJsonObject();
                JsonElement key = entry.get("name");
                if (key != null && key.isJsonPrimitive() && name.equals(key.getAsString())) {
                    return entry.get("value");
                }
            }
        }
        return null;
    }

    private static void migrateHudControls(Module module, JsonObject data) {
        if (module instanceof idle.miau.module.impl.render.Chat
                && hudSettingValue(data, "setting.shader", "Shader") == null) {
            JsonElement blur = hudSettingValue(data, "setting.blur", "Blur");
            JsonElement bloom = hudSettingValue(data, "setting.bloom", "Bloom");
            if (blur != null || bloom != null) {
                data.addProperty("setting.shader", enabledSetting(blur) || enabledSetting(bloom));
            }
        }
        if (module instanceof idle.miau.module.impl.render.Scoreboard
                && enabledSetting(hudSettingValue(data, "setting.bloom", "Bloom"))) {
            data.addProperty("setting.blur", true);
            data.addProperty("Shaders.Blur", true);
        }
        if (module instanceof ArrayListModule
                && enabledSetting(hudSettingValue(data, "setting.modern.hideRender", "Hide render modules"))) {
            for (Setting setting : module.getSettings()) {
                if (!(setting instanceof MultiSelectSetting categories) || !"Hidden categories".equals(setting.getName())) continue;
                MultiSelectSetting migrated = new MultiSelectSetting(setting.getName(), categories.getOptions());
                JsonElement value = hudSettingValue(data, module.getSettingConfigId(setting), setting.getName());
                if (value != null) {
                    JsonObject selections = new JsonObject();
                    selections.add(setting.getName(), value);
                    migrated.loadConfig(selections);
                }
                migrated.setSelected("Render", true);
                JsonArray selections = migrated.toJsonArray();
                data.add(module.getSettingConfigId(setting), selections);
                data.add(setting.getConfigKey(), selections);
                break;
            }
        }
    }

    private static boolean enabledSetting(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static JsonElement hudSettingValue(JsonObject data, String id, String name) {
        if (data.has(id)) return data.get(id);
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            if (name.equalsIgnoreCase(ungroupedSettingName(entry.getKey()))) return entry.getValue();
        }
        JsonElement portable = data.get("portableSettings");
        if (portable != null && portable.isJsonArray()) {
            for (JsonElement element : portable.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                JsonElement key = entry.get("name");
                if (key != null && key.isJsonPrimitive() && name.equalsIgnoreCase(ungroupedSettingName(key.getAsString()))) {
                    return entry.get("value");
                }
            }
        }
        return null;
    }

    private static void migrateHudSettingGroups(Module module, JsonObject data) {
        if (!(module instanceof ArrayListModule)) {
            return;
        }
        JsonElement portable = data.get("portableSettings");
        if (portable != null && portable.isJsonArray()) {
            for (JsonElement entry : portable.getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                JsonObject value = entry.getAsJsonObject();
                if (value.has("name") && "Modern outline".equalsIgnoreCase(ungroupedSettingName(value.get("name").getAsString()))) {
                    value.addProperty("name", "Outline settings.Outline");
                }
            }
        }
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(data.entrySet());
        for (Setting setting : getSerializedSettings(module)) {
            if (data.has(setting.getConfigKey()) || data.has(module.getSettingConfigId(setting))) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : entries) {
                if (setting.getName().equalsIgnoreCase(ungroupedSettingName(entry.getKey()))
                        && isCompatibleSettingValue(setting, entry.getValue())) {
                    data.add(setting.getConfigKey(), entry.getValue());
                    break;
                }
            }
        }
    }

    private static void applyPortableSettingValues(Module module, JsonObject moduleInformation) {
        JsonElement value = moduleInformation.get("portableSettings");
        if (value == null || !value.isJsonArray()) {
            return;
        }
        List<JsonObject> entries = new ArrayList<JsonObject>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (element != null && element.isJsonObject()
                    && element.getAsJsonObject().has("value")) {
                entries.add(element.getAsJsonObject());
            }
        }
        List<Setting> settings = getSerializedSettings(module);
        boolean[] used = new boolean[entries.size()];
        boolean ungroupedHudNames = module instanceof ArrayListModule
                || module instanceof idle.miau.module.impl.render.Chat
                || module instanceof idle.miau.module.impl.render.Scoreboard;
        for (int index = 0; index < settings.size(); index++) {
            Setting setting = settings.get(index);
            if (moduleInformation.has(module.getSettingConfigId(setting))) {
                continue;
            }
            int entryIndex = findPortableSetting(entries, used, setting.getConfigKey(), false);
            if (entryIndex < 0 && ungroupedHudNames) {
                entryIndex = findPortableSetting(entries, used, setting.getName(), true);
            }
            if (entryIndex < 0 && index < entries.size() && !used[index]
                    && !(entries.get(index).has("name") && hasSharedHudAppearance(module))
                    && !hasPortableSettingName(settings, entries.get(index), ungroupedHudNames)
                    && isCompatibleSettingValue(setting, entries.get(index).get("value"))) {
                entryIndex = index;
            }
            if (entryIndex < 0) {
                continue;
            }
            JsonElement settingValue = entries.get(entryIndex).get("value");
            if (!isCompatibleSettingValue(setting, settingValue)) {
                continue;
            }
            used[entryIndex] = true;
            moduleInformation.add(setting.getConfigKey(), settingValue);
        }
    }

    private static boolean hasSharedHudAppearance(Module module) {
        return module instanceof ArrayListModule
                || module instanceof idle.miau.module.impl.render.Chat
                || module instanceof idle.miau.module.impl.render.Scoreboard
                || module instanceof idle.miau.module.impl.render.Watermark
                || module instanceof idle.miau.module.impl.render.KeyStrokes
                || module instanceof idle.miau.module.impl.render.TargetHUD
                || module instanceof idle.miau.module.impl.render.SpotifyMod;
    }

    private static boolean hasPortableSettingName(List<Setting> settings, JsonObject entry, boolean ungroupedHudNames) {
        JsonElement name = entry.get("name");
        if (name == null || !name.isJsonPrimitive()) {
            return false;
        }
        String configKey = name.getAsString();
        for (Setting setting : settings) {
            if (setting.getConfigKey().equalsIgnoreCase(configKey)
                    || ungroupedHudNames && setting.getName().equalsIgnoreCase(ungroupedSettingName(configKey))) {
                return true;
            }
        }
        return false;
    }

    private static int findPortableSetting(
            List<JsonObject> entries, boolean[] used, String configKey, boolean ungrouped) {
        for (int index = 0; index < entries.size(); index++) {
            if (used[index]) {
                continue;
            }
            JsonElement name = entries.get(index).get("name");
            if (name != null && name.isJsonPrimitive()) {
                String key = name.getAsString();
                if (configKey.equalsIgnoreCase(ungrouped ? ungroupedSettingName(key) : key)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String ungroupedSettingName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static void applyObfuscatedSettingValues(Module module, JsonObject moduleInformation) {
        if (moduleInformation.has("portableSettings")) {
            return;
        }
        JsonElement idValue = moduleInformation.get("id");
        if (idValue == null || !idValue.isJsonPrimitive()) {
            return;
        }
        String sourceId = idValue.getAsString();
        if (sourceId.equals(module.getConfigId())
                || (!sourceId.startsWith("x.") && !sourceId.startsWith("x/"))) {
            return;
        }
        List<Map.Entry<String, JsonElement>> values = new ArrayList<Map.Entry<String, JsonElement>>();
        for (Map.Entry<String, JsonElement> entry : moduleInformation.entrySet()) {
            if (entry.getKey().startsWith("setting.")) {
                values.add(entry);
            }
        }
        List<Setting> settings = getSerializedSettings(module);
        int count = Math.min(settings.size(), values.size());
        for (int index = 0; index < count; index++) {
            Setting setting = settings.get(index);
            String stableKey = module.getSettingConfigId(setting);
            String configKey = setting.getConfigKey();
            if (moduleInformation.has(stableKey) || moduleInformation.has(configKey)) {
                continue;
            }
            Map.Entry<String, JsonElement> entry = values.get(index);
            if (!entry.getKey().startsWith("setting.q")
                    || !isCompatibleSettingValue(setting, entry.getValue())) {
                continue;
            }
            moduleInformation.add(configKey, entry.getValue());
        }
    }

    private static List<Setting> getSerializedSettings(Module module) {
        List<Setting> result = new ArrayList<Setting>();
        for (Setting setting : module.getSettings()) {
            if (isSerializedSetting(setting)) {
                result.add(setting);
            }
        }
        return result;
    }

    private static boolean isSerializedSetting(Setting setting) {
        if (setting instanceof ButtonSetting) {
            return !((ButtonSetting) setting).isMethodButton;
        }
        if (setting instanceof TextSetting) {
            return !((TextSetting) setting).isSensitive();
        }
        return setting instanceof SliderSetting
                || setting instanceof RangeSetting
                || setting instanceof MultiSelectSetting
                || setting instanceof KeySetting
                || setting instanceof ColorSetting
                || setting instanceof BlockListSetting
                || setting instanceof StringListSetting;
    }

    private static boolean isCompatibleSettingValue(Setting setting, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (setting instanceof ButtonSetting) {
            return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
        }
        if (setting instanceof SliderSetting || setting instanceof KeySetting) {
            return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
        }
        if (setting instanceof RangeSetting) {
            return value.isJsonObject()
                    || value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
        }
        if (setting instanceof MultiSelectSetting) {
            return value.isJsonArray() || value.isJsonPrimitive();
        }
        if (setting instanceof TextSetting || setting instanceof ColorSetting) {
            return value.isJsonPrimitive();
        }
        return value.isJsonArray();
    }

    private List<Module> getLoadableModules() {
        return new ArrayList<Module>(Miau.getModuleManager().getModules());
    }

    private Map<Module, RequestedModuleState> createDefaultRequestedModuleStates(List<Module> modules) {
        Map<Module, RequestedModuleState> requestedModuleStates = new HashMap<Module, RequestedModuleState>();
        for (Module module : modules) {
            if (module.canBeEnabled()) {
                requestedModuleStates.put(module, new RequestedModuleState(
                        module instanceof Notifications || module instanceof IRC || module instanceof Watermark, 0));
            }
        }
        return requestedModuleStates;
    }

    public boolean deleteConfig(String name) {
        Config removedConfig = getConfig(name);
        String configName = removedConfig != null ? removedConfig.getName() : normalizeConfigName(name);
        File configFile = new File(directory, configName + ".json");

        if (configFile.exists() && !(configFile.delete() || !configFile.exists())) {
            return false;
        }

        boolean wasCurrentConfig = removedConfig != null && Miau.currentConfig == removedConfig;
        if (removedConfig != null) {
            configs.remove(removedConfig);
        }
        if (wasCurrentConfig) {
            Miau.currentConfig = null;
        }

        if (configs.isEmpty()) {
            if (createConfig(DEFAULT_CONFIG_NAME, 0) == null) {
                return false;
            }
        }
        else {
            refreshConfigModules();
        }
        if (wasCurrentConfig) {
            Config fallbackConfig = getDefaultOrFirstConfig();
            if (fallbackConfig != null) {
                loadConfig(fallbackConfig.getName());
            }
        }
        return removedConfig != null;
    }

    public void loadConfigs() {
        String currentConfigName = Miau.currentConfig != null ? Miau.currentConfig.getName() : null;
        boolean currentConfigSaved = Miau.currentConfig == null || Miau.currentConfig.getModule().saved;
        configs.clear();
        if (!directory.exists() && !directory.mkdirs()) {
            Utils.sendMessage("&cFailed to load configs.");
            return;
        }

        List<File> configFiles = getConfigFiles();
        if (configFiles.isEmpty()) {
            saveConfig(new Config(DEFAULT_CONFIG_NAME, 0));
            configFiles = getConfigFiles();
        }

        for (File file : configFiles) {
            try {
                JsonObject configJson = ConfigFileCodec.read(file.toPath());
                String configName = file.getName().replace(".json", "");

                int keybind = 0;

                if (configJson.has("keybind")) {
                    keybind = configJson.get("keybind").getAsInt();
                }

                Config config = new Config(configName, keybind);
                configs.add(config);
            } catch (Exception e) {
                Utils.sendMessage("&cFailed to load configs.");
                e.printStackTrace();
            }
        }

        if (currentConfigName != null) {
            Miau.currentConfig = getConfig(currentConfigName);
            if (Miau.currentConfig != null) {
                Miau.currentConfig.getModule().saved = currentConfigSaved;
            }
        }
        refreshConfigModules();
        Utils.sendMessage("&b" + configFiles.size() + " &7configs loaded.");
    }

    public List<File> getConfigFiles() {
        List<File> configFiles = new ArrayList<>();
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files == null) {
                return configFiles;
            }
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".json")) {
                    continue;
                }
                configFiles.add(file);
            }
        }
        return configFiles;
    }

    public Config getConfig(String name) {
        for (Config config : configs) {
            if (config.getName().equalsIgnoreCase(name)) {
                return config;
            }
        }
        return null;
    }

    public void loadInitialConfig() {
        Miau.currentConfig = null;
    }

    public void failedMessage(String reason, String name) {
        Utils.sendMessage("&cFailed to " + reason + ": &b" + name);
    }

    public boolean renameConfig(Config config, String requestedName) {
        if (config == null) {
            Utils.sendMessage("&cFailed to rename config.");
            return false;
        }

        String oldName = config.getName();
        String newName = normalizeConfigName(requestedName);
        String validationError = validateConfigName(newName, oldName);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return false;
        }

        if (oldName.equals(newName)) {
            config.setName(newName);
            return true;
        }

        File oldFile = new File(directory, oldName + ".json");
        File newFile = new File(directory, newName + ".json");

        if (!oldFile.exists()) {
            failedMessage("rename", oldName);
            return false;
        }

        try {
            Files.move(oldFile.toPath(), newFile.toPath());
            config.setName(newName);
            if (Miau.currentConfig == config) {
                rememberConfigSelection(newName);
            }
            return true;
        }
        catch (Exception e) {
            failedMessage("rename", oldName);
            e.printStackTrace();
            return false;
        }
    }

    private void refreshConfigModules() {
        if (Miau.miauClickGui != null) {
            Miau.miauClickGui.refreshConfigModules();
        }
        if (Miau.clickGui != null && ClickGui.categories != null) {
            for (CategoryComponent category : ClickGui.categories) {
                if (category.category == Module.category.configs) {
                    category.reloadModules(true);
                    break;
                }
            }
        }
    }

    private Map<String, SavedCategoryState> readCategoryStates(JsonObject configJson, String key) {
        Map<String, SavedCategoryState> states = new HashMap<String, SavedCategoryState>();
        JsonArray categories = configJson.getAsJsonArray(key);
        if (categories == null) {
            return states;
        }
        for (JsonElement categoryElement : categories) {
            if (!categoryElement.isJsonObject()) {
                continue;
            }
            JsonObject category = categoryElement.getAsJsonObject();
            if (!category.has("name") || !category.has("x") || !category.has("y")) {
                continue;
            }
            float x = readFiniteFloat(category, "x", Float.NaN);
            float y = readFiniteFloat(category, "y", Float.NaN);
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                continue;
            }
            states.put(
                    category.get("name").getAsString(),
                    new SavedCategoryState(
                            x,
                            y,
                            !category.has("opened") || category.get("opened").getAsBoolean()));
        }
        return states;
    }

    private Config getDefaultOrFirstConfig() {
        Config defaultConfig = getConfig(DEFAULT_CONFIG_NAME);
        if (defaultConfig != null) {
            return defaultConfig;
        }
        return configs.isEmpty() ? null : configs.get(0);
    }

    private String validateConfigName(String configName, String currentName) {
        if (configName.isEmpty()) {
            return "Config name cannot be empty.";
        }
        if (configName.endsWith(".") || configName.endsWith(" ")) {
            return "Config name cannot end with a space or period.";
        }
        for (char c : configName.toCharArray()) {
            if (c < 32 || containsInvalidConfigChar(c)) {
                return "Config name contains invalid characters.";
            }
        }
        for (Config config : configs) {
            if (config.getName().equalsIgnoreCase(configName) && (currentName == null || !config.getName().equalsIgnoreCase(currentName))) {
                return "Config already exists: " + configName;
            }
        }
        return null;
    }

    private boolean containsInvalidConfigChar(char c) {
        for (char invalidChar : INVALID_CONFIG_NAME_CHARS) {
            if (invalidChar == c) {
                return true;
            }
        }
        return false;
    }

    private String normalizeConfigName(String name) {
        return name == null ? "" : name.trim();
    }
}
