package keystrokesmod.script;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.TextSetting;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.Image;
import keystrokesmod.script.model.NetworkPlayer;
import idle.miau.utility.Utils;
import org.lwjgl.Sys;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Manager extends Module {
    public static ButtonSetting enableHttpRequests;
    public static ButtonSetting enableWebSockets;
    private final TextSetting createScriptName;

    public final String DOCUMENTATION_URL = "https://blowsy.gitbook.io/raven";
    private final String CONFIG_DIR = new File(idle.miau.utility.ClientStorage.getDirectory(mc != null ? mc.mcDataDir : null), "script_settings.txt").getPath();
    private final String SEPARATOR = ":";
    private final String SEPARATOR_FULL = SEPARATOR + " ";

    private long lastLoad = 0;

    public Manager() {
        super("Manager", category.scripts);
        setDescription("Creates, edits and loads custom client scripts.");
        this.registerSetting(createScriptName = new TextSetting("Script name", "", "Type a script name...", 32, this::createScript));
        this.registerSetting(new ButtonSetting("Create script", this::createScript));
        this.registerSetting(new ButtonSetting("Script Editor", this::openEditor));
        this.registerSetting(new ButtonSetting("Load scripts", this::loadScriptsNow));
        this.registerSetting(new ButtonSetting("Open folder", this::openFolder));
        this.registerSetting(new ButtonSetting("View documentation", this::viewDocumentation));
        this.registerSetting(new DescriptionSetting("Privacy"));
        this.registerSetting(enableHttpRequests = new ButtonSetting("Enable http requests", true));
        this.registerSetting(enableWebSockets = new ButtonSetting("Enable websockets", true));
        this.canBeEnabled = false;
        this.ignoreOnSave = true;

        retrieveSettings();
    }

    @Override
    public void guiButtonToggled(ButtonSetting s) {
        updateSettingFile();
    }

    private boolean updateSettingFile() {
        return set("enable-http-requests", String.valueOf(enableHttpRequests.isToggled())) & set("enable-websockets", String.valueOf(enableWebSockets.isToggled()));
    }

    public void loadScripts(long ms) {
        Miau.scriptManager.loadScripts();
        if (Miau.scriptManager.scripts.isEmpty()) {
            Utils.sendMessage("&7No scripts found.");
        }
        else {
            double timeTaken = Utils.round((System.currentTimeMillis() - ms) / 1000.0, 1);
            Utils.sendMessage("&7Loaded &b" + Miau.scriptManager.scripts.size() + " &7script" + ((Miau.scriptManager.scripts.size() == 1) ? "" : "s") + " in &b" + Utils.asWholeNum(timeTaken) + "&7s.");
        }
        Entity.clearCache();
        NetworkPlayer.clearCache();
        Image.clearCache();
        ScriptDefaults.reloadModules();
        if (Miau.currentConfig != null && Miau.currentConfig.getModule() != null) {
            Miau.currentConfig.getModule().saved = false;
        }
    }

    private void ensureConfigFileExists() throws IOException {
        final Path configPath = Paths.get(CONFIG_DIR);
        if (Files.notExists(configPath)) {
            Files.createDirectories(configPath.getParent());
            Files.createFile(configPath);
        }
    }

    private boolean set(String key, String value) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        key = key.replace(SEPARATOR, "");
        final String entry = key + SEPARATOR_FULL + value;
        try {
            ensureConfigFileExists();
            final Path configPath = new File(CONFIG_DIR).toPath();
            final List<String> lines = new ArrayList<>(Files.readAllLines(configPath));
            boolean keyExists = false;
            for (int i = 0; i < lines.size(); ++i) {
                final String line = lines.get(i);
                if (line.startsWith(key + SEPARATOR_FULL)) {
                    lines.set(i, entry);
                    keyExists = true;
                    break;
                }
            }
            if (!keyExists) {
                lines.add(entry);
            }
            Files.write(configPath, lines);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }

    private void retrieveSettings() {
        String requestState = retrieveSetting("enable-http-requests");
        String webSocketsState = retrieveSetting("enable-websockets");
        if (requestState != null) {
            enableHttpRequests.setEnabled(parseBoolean(requestState, true));
        }
        if (webSocketsState != null) {
            enableWebSockets.setEnabled(parseBoolean(webSocketsState, true));
        }
    }

    private boolean parseBoolean(String parse, boolean defaultVal) {
        try {
            return Boolean.parseBoolean(parse);
        }
        catch (Exception e) {
            return defaultVal;
        }
    }

    private String retrieveSetting(String key) {
        try {
            ensureConfigFileExists();
            final Path configPath = new File(CONFIG_DIR).toPath();
            final List<String> lines = Files.readAllLines(configPath);
            for (final String line : lines) {
                if (line.startsWith(key + SEPARATOR_FULL)) {
                    return line.substring((key + SEPARATOR_FULL).length());
                }
            }
        }
        catch (IOException ex) {}
        return null;
    }

    private void createScript() {
        createScript(createScriptName.getText());
        if (!createScriptName.getText().isEmpty()) {
            createScriptName.setText("");
        }
    }

    public void createScript(String requestedName) {
        if (Miau.scriptManager == null || requestedName == null || requestedName.trim().isEmpty()) {
            return;
        }

        String scriptName = Miau.scriptManager.createScript(requestedName.trim());
        if (scriptName != null) {
            Utils.sendMessage("&7Created script: &b" + scriptName);
        }
    }

    public void loadScriptsNow() {
        if (Miau.scriptManager.compiler == null) {
            Utils.sendMessage("&cCompiler error, JDK not found");
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (Utils.timeBetween(this.lastLoad, currentTimeMillis) > 1500) {
            this.lastLoad = currentTimeMillis;
            this.loadScripts(currentTimeMillis);
        }
        else {
            Utils.sendMessage("&cYou are on cooldown.");
        }
    }

    public void openFolder() {
        try {
            Files.createDirectories(Miau.scriptManager.directory.toPath());
            Desktop.getDesktop().open(Miau.scriptManager.directory);
        }
        catch (Exception ex) {
            Utils.sendMessage("&7Scripts folder: &b" + Miau.scriptManager.directory.getAbsolutePath());
        }
    }

    public void viewDocumentation() {
        try {
            Desktop.getDesktop().browse(new URI(DOCUMENTATION_URL));
        }
        catch (Throwable t) {
            Sys.openURL(DOCUMENTATION_URL);
        }
    }

    public void openEditor() {
        if (mc.isCallingFromMinecraftThread()) {
            mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(mc.currentScreen, null));
        } else {
            mc.addScheduledTask(() -> mc.displayGuiScreen(new idle.miau.gui.GuiScriptEditor(mc.currentScreen, null)));
        }
    }
}
