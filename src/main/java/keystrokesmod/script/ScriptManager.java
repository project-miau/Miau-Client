package keystrokesmod.script;

import idle.miau.Miau;
import idle.miau.clickgui.components.impl.CategoryComponent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.utility.ClientStorage;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import idle.miau.mcp.McpRuntime;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

public class ScriptManager {
    private static final char[] INVALID_SCRIPT_NAME_CHARS = new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'};
    private Minecraft mc = Minecraft.getMinecraft();
    public LinkedHashMap<Script, Module> scripts = new LinkedHashMap<>();
    public JavaCompiler compiler = resolveJavaCompiler();
    public boolean deleteTempFiles = true;
    public File directory;
    public List<String> imports = Arrays.asList(Color.class.getName(), Collections.class.getName(), List.class.getName(), ArrayList.class.getName(), Arrays.class.getName(), Map.class.getName(), Set.class.getName(), HashMap.class.getName(), HashSet.class.getName(), ConcurrentHashMap.class.getName(), LinkedHashMap.class.getName(), LinkedHashSet.class.getName(), Iterator.class.getName(), Comparator.class.getName(), AtomicInteger.class.getName(), AtomicLong.class.getName(), AtomicBoolean.class.getName(), Random.class.getName(), Matcher.class.getName());
    public String COMPILED_DIR;
    public String jarPath = compilerClasspath();
    private Map<String, String> loadedHashes = new HashMap<>();
    private final Map<Module, Script> callbacks = new ConcurrentHashMap<>();
    private boolean loading;

    public static JavaCompiler resolveJavaCompiler() {
        try {
            JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
            if (systemCompiler != null) {
                return systemCompiler;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (JavaCompiler c : ServiceLoader.load(JavaCompiler.class, ClassLoader.getPlatformClassLoader())) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (JavaCompiler c : ServiceLoader.load(JavaCompiler.class, JavaCompiler.class.getClassLoader())) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (JavaCompiler c : ServiceLoader.load(JavaCompiler.class, Thread.currentThread().getContextClassLoader())) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public ScriptManager() {
        this(defaultGameDirectory());
    }

    public ScriptManager(File gameDirectory) {
        if (gameDirectory == null) {
            gameDirectory = defaultGameDirectory();
        }
        directory = new File(ClientStorage.getDirectory(gameDirectory), "scripts");
        try {
            COMPILED_DIR = Files.createTempDirectory("miau-scripts-").toString();
            new File(COMPILED_DIR).deleteOnExit();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to create scripting compiler directory", exception);
        }
    }

    private static File defaultGameDirectory() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.mcDataDir != null) {
            return minecraft.mcDataDir;
        }
        return new File(System.getProperty("user.dir", "."));
    }

    public static String compilerClasspath() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        try {
            URL location = ScriptManager.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                try {
                    entries.add(new File(location.toURI()).getPath());
                } catch (Exception e) {
                    entries.add(new File(location.getPath()).getPath());
                }
            }
        } catch (Exception exception) {
            Utils.log.warn("Unable to resolve scripting API location", exception);
        }
        for (String entry : System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        for (ClassLoader loader = ScriptManager.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader) {
                for (java.net.URL url : ((java.net.URLClassLoader) loader).getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        try {
                            entries.add(new File(url.toURI()).getPath());
                        } catch (Exception ignored) {
                            try {
                                entries.add(new File(url.getPath()).getPath());
                            } catch (Exception ignored2) {
                            }
                        }
                    }
                }
            }
        }
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader instanceof java.net.URLClassLoader) {
                for (java.net.URL url : ((java.net.URLClassLoader) contextLoader).getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        try {
                            entries.add(new File(url.toURI()).getPath());
                        } catch (Exception ignored) {
                            try {
                                entries.add(new File(url.getPath()).getPath());
                            } catch (Exception ignored2) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return String.join(File.pathSeparator, entries);
    }

    public void onEnable(Script script) {
        if (script.event == null) {
            script.event = new ScriptEvents(getModule(script));
            McpRuntime.EVENTS.register(script.event);
        }
        script.invoke("onEnable");
    }

    public Module getModule(Script script) {
        for (Map.Entry<Script, Module> entry : this.scripts.entrySet()) {
            if (entry.getKey().equals(script)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public synchronized void shutdown() {
        for (Module module : new ArrayList<Module>(scripts.values())) {
            try {
                if (module.isEnabled()) {
                    module.disable();
                }
            }
            catch (Throwable throwable) {
                Utils.log.error("Unable to disable script module {}", module.getName(), throwable);
            }
            if (Miau.moduleManager != null) {
                Miau.moduleManager.removeModule(module);
            }
        }
        scripts.clear();
        callbacks.clear();
        loadedHashes.clear();
        ScriptDefaults.reloadModules();
        refreshScriptModules();
    }

    public String createScript(String requestedName) {
        String scriptName = normalizeScriptName(requestedName);
        String validationError = validateScriptName(scriptName, null);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return null;
        }

        try {
            if (!directory.exists() && !directory.mkdirs()) {
                Utils.sendMessage("&cFailed to create scripts folder.");
                return null;
            }

            Files.write(new File(directory, scriptName + ".java").toPath(), buildDefaultScriptTemplate().getBytes(StandardCharsets.UTF_8));
            loadScripts();
            return scriptName;
        }
        catch (Exception e) {
            Utils.sendMessage("&cFailed to create script: &b" + scriptName);
            e.printStackTrace();
            return null;
        }
    }

    public void loadScripts() {
        if (loading) {
            return;
        }
        loading = true;
        try {
            Map<String, Boolean> previousEnabled = new HashMap<>();
            Map<String, String> previousCustomNames = new HashMap<>();
            for (Module module : this.scripts.values()) {
                previousEnabled.put(module.getName(), module.isEnabled());
                if (module instanceof ScriptModule) {
                    previousCustomNames.put(module.getName(), ((ScriptModule) module).getCustomName());
                }
            }

            if (deleteTempFiles) {
                deleteTempFiles = false;
                File tempDirectory = new File(COMPILED_DIR);
                if (tempDirectory.exists() && tempDirectory.isDirectory()) {
                    File[] tempFiles = tempDirectory.listFiles();
                    if (tempFiles != null) {
                        for (File tempFile : tempFiles) {
                            if (isGeneratedClass(tempFile) && !tempFile.delete()) {
                                System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                            }
                        }
                    }
                }
            }
            else {
                if (!this.scripts.isEmpty()) {
                    Iterator<Map.Entry<Script, Module>> iterator = scripts.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<Script, Module> entry = iterator.next();
                        if (entry.getKey().file == null || !entry.getKey().file.exists()) {
                            if (entry.getValue().isEnabled()) {
                                entry.getValue().disable();
                            }
                            entry.getKey().delete();
                            Miau.moduleManager.removeModule(entry.getValue());
                            callbacks.remove(entry.getValue());
                            iterator.remove();
                            loadedHashes.remove(entry.getKey().file != null ? entry.getKey().file.getName() : "");
                            continue;
                        }
                        String fileName = entry.getKey().file.getName();
                        String hash = calculateHash(entry.getKey().file);

                        String cachedHash = loadedHashes.get(fileName);
                        if (cachedHash != null && cachedHash.equals(hash) && !entry.getKey().error && !entry.getKey().usesLoadString) {
                            continue;
                        }
                        if (entry.getValue().isEnabled()) {
                            entry.getValue().disable();
                        }
                        entry.getKey().delete();
                        Miau.moduleManager.removeModule(entry.getValue());
                        callbacks.remove(entry.getValue());
                        iterator.remove();
                        loadedHashes.remove(fileName);
                    }
                }
                else {
                    loadedHashes.clear();
                }
            }

            File scriptDirectory = directory;
            if (scriptDirectory.exists() && scriptDirectory.isDirectory()) {
                File[] scriptFiles = scriptDirectory.listFiles();
                if (scriptFiles != null) {
                    Arrays.sort(scriptFiles, Comparator.comparing(File::getName));
                    for (File scriptFile : scriptFiles) {
                        if (scriptFile.isFile() && scriptFile.getName().endsWith(".java")) {
                            String fileName = scriptFile.getName();
                            String hash = calculateHash(scriptFile);

                            String cachedHash = loadedHashes.get(fileName);
                            if (cachedHash != null && cachedHash.equals(hash)) {
                                continue;
                            }
                            parseFile(scriptFile, previousEnabled);
                            Script loadedScript = getScriptByFile(scriptFile);
                            Module loadedModule = loadedScript == null ? null : getModule(loadedScript);
                            if (loadedModule instanceof ScriptModule) {
                                String customName = previousCustomNames.get(loadedModule.getName());
                                if (customName != null) {
                                    ((ScriptModule) loadedModule).setCustomName(customName);
                                }
                            }
                            loadedHashes.put(scriptFile.getName(), hash);
                        }
                    }
                }
            }
            else {
                if (scriptDirectory.mkdirs()) {
                    System.out.println("Created script directory: " + scriptDirectory.getAbsolutePath());
                }
                else {
                    System.err.println("Failed to create script directory: " + scriptDirectory.getAbsolutePath());
                }
            }

            refreshScriptModules();

            ScriptDefaults.reloadModules();

            File tempDirectory = new File(COMPILED_DIR);
            if (tempDirectory.exists() && tempDirectory.isDirectory()) {
                File[] tempFiles = tempDirectory.listFiles();
                if (tempFiles != null) {
                    for (File tempFile : tempFiles) {
                        if (isGeneratedClass(tempFile) && !tempFile.delete()) {
                            System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                        }
                    }
                }
            }
            if (Miau.commandManager != null) {
                Miau.commandManager.refreshScriptCommands();
            }
        } finally {
            loading = false;
        }
    }

    private boolean parseFile(File file) {
        return parseFile(file, Collections.emptyMap());
    }

    private boolean parseFile(File file, Map<String, Boolean> previousEnabled) {
        if (file.getName().startsWith("_") || !file.getName().endsWith(".java")) {
            return false;
        }
        String scriptName = file.getName().substring(0, file.getName().length() - ".java".length());
        if (scriptName.isEmpty()) {
            return false;
        }
        for (Script loaded : this.scripts.keySet()) {
            if (loaded.name.equalsIgnoreCase(scriptName) && (loaded.file == null || !loaded.file.equals(file))) {
                Utils.sendMessage("&cDuplicate script name: &b" + scriptName);
                return false;
            }
        }
        StringBuilder scriptContents = new StringBuilder();
        try (BufferedReader bufferedReader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                scriptContents.append(line).append("\n");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (scriptContents.length() == 0) {
            return false;
        }

        String source = normalizeLineSeparators(scriptContents.toString());
        List<String> topLevelLines = Utils.getTopLevelLines(source);
        boolean usesLoadString = false;
        for (String line : topLevelLines) {
            if (line.startsWith("load - \"") && line.endsWith("\"")) {
                usesLoadString = true;
                int loadIndex = source.indexOf(line);
                if (loadIndex != -1) {
                    source = source.substring(0, loadIndex)
                            + source.substring(loadIndex + line.length());
                }

                if (!Manager.enableHttpRequests.isToggled()) {
                    Utils.sendMessage("&7Blocked &cload string&7 in &b" + scriptName + "&7, http requests are not enabled.");
                    continue;
                }
                String url = line.substring("load - \"".length(), line.length() - 1);
                String externalContents = normalizeLineSeparators(
                        ScriptHttp.load(url)
                );
                if (externalContents.isEmpty()) {
                    break;
                }

                if (loadIndex != -1) {
                    source = source.substring(0, loadIndex)
                            + externalContents
                            + source.substring(loadIndex);
                }
            }
        }

        Script script = new Script(scriptName);
        script.file = file;
        script.usesLoadString = usesLoadString;
        script.setCode(source);
        script.run();
        Module module = new ScriptModule(script);
        Miau.scriptManager.scripts.put(script, module);
        callbacks.put(module, script);
        Miau.moduleManager.addModule(module);
        ScriptDefaults.reloadModules();
        Miau.scriptManager.invoke("onLoad", module);
        if (previousEnabled != null && Boolean.TRUE.equals(previousEnabled.get(scriptName)) && module.canBeEnabled()) {
            module.enable();
        }
        return !script.error;
    }

    private String normalizeLineSeparators(String source) {
        if (source == null) {
            return "";
        }

        return source.replace('\u2028', '\n').replace('\u2029', '\n').replace('\u0085', '\n');
    }

    public void onDisable(Script script) {
        if (script.event != null) {
            McpRuntime.EVENTS.unregister(script.event);
            script.event = null;
        }
        script.invoke("onDisable");
    }

    public void invoke(String methodName, Module module, Object... args) {
        Script script = callbacks.get(module);
        if (script != null && ((module.canBeEnabled() && module.isEnabled()) || methodName.equals("onLoad"))) {
            script.invoke(methodName, args);
        }
    }

    public int invokeBoolean(String methodName, Module module, Object... args) {
        Script script = callbacks.get(module);
        return script != null && module.canBeEnabled() && module.isEnabled() ? script.getBoolean(methodName, args) : -1;
    }

    public Float[] invokeFloatArray(String method, Module module, Object... args) {
        Script script = callbacks.get(module);
        return script != null && module.canBeEnabled() && module.isEnabled() ? script.getFloatArray(method, args) : null;
    }

    private String calculateHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(Paths.get(file.getPath()));
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        }
        catch (Exception e) {
            return "";
        }
    }

    public boolean renameScript(Script script, String requestedName) {
        if (script == null || script.file == null) {
            Utils.sendMessage("&cFailed to rename script.");
            return false;
        }

        String oldName = script.name;
        String newName = normalizeScriptName(requestedName);
        String validationError = validateScriptName(newName, oldName);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return false;
        }

        if (oldName.equals(newName)) {
            return true;
        }

        File newFile = new File(directory, newName + ".java");
        try {
            Files.move(script.file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadScripts();
            return true;
        }
        catch (Exception e) {
            Utils.sendMessage("&cFailed to rename script: &b" + oldName);
            e.printStackTrace();
            return false;
        }
    }

    private void refreshScriptModules() {
        if (Miau.clickGui == null) {
            return;
        }
        for (CategoryComponent categoryComponent : Miau.clickGui.categories) {
            if (categoryComponent.category == Module.category.scripts) {
                categoryComponent.reloadModules();
                break;
            }
        }
        if (Miau.miauClickGui != null) {
            Miau.miauClickGui.refreshScriptModules();
        }
    }

    private boolean isGeneratedClass(File file) {
        return file.isFile() && file.getName().startsWith("sc_") && file.getName().endsWith(".class");
    }

    public Script getScript(String name) {
        if (name == null) {
            return null;
        }
        for (Script script : this.scripts.keySet()) {
            if (script.name.equalsIgnoreCase(name)) {
                return script;
            }
        }
        return null;
    }

    public Script getScriptByFile(File file) {
        if (file == null) {
            return null;
        }
        for (Script script : this.scripts.keySet()) {
            if (script.file != null && script.file.equals(file)) {
                return script;
            }
        }
        return null;
    }

    private String validateScriptName(String scriptName, String currentName) {
        if (scriptName.isEmpty()) {
            return "Script name cannot be empty.";
        }
        if (scriptName.endsWith(".") || scriptName.endsWith(" ")) {
            return "Script name cannot end with a space or period.";
        }
        for (char c : scriptName.toCharArray()) {
            if (c < 32 || containsInvalidScriptChar(c)) {
                return "Script name contains invalid characters.";
            }
        }
        File scriptFile = new File(directory, scriptName + ".java");
        if ((currentName == null || !currentName.equalsIgnoreCase(scriptName)) && scriptFile.exists()) {
            return "Script already exists: " + scriptName;
        }
        return null;
    }

    private boolean containsInvalidScriptChar(char c) {
        for (char invalidChar : INVALID_SCRIPT_NAME_CHARS) {
            if (invalidChar == c) {
                return true;
            }
        }
        return false;
    }

    private String normalizeScriptName(String name) {
        return name == null ? "" : name.trim();
    }

    private String buildDefaultScriptTemplate() {
        return "// New Raven script\n"
            + "void onLoad() {\n"
            + "}\n\n"
            + "void onEnable() {\n"
            + "}\n\n"
            + "void onDisable() {\n"
            + "}\n";
    }
}
