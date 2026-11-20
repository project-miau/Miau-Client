package keystrokesmod.script;

import keystrokesmod.Raven;
import idle.miau.Miau;
import idle.miau.utility.Utils;

import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class Script {
    public String name;
    public Class clazz;
    public Object instance;
    public String scriptName;
    public String codeStr;
    public boolean error = false;
    public String lastError = null;
    public boolean usesLoadString = false;
    public int STARTING_LINE;
    public ScriptEvents event;
    public File file;

    public Script(String name) {
        this.name = name;
        this.scriptName = "sc_" + name.replaceAll("[^a-zA-Z0-9_$]", "_") + "_" + Utils.generateRandomString(12);
    }

    private ScriptManager manager() {
        return Miau.scriptManager != null ? Miau.scriptManager : Raven.scriptManager;
    }

    public boolean run() {
        try {
            if (this.scriptName == null || this.codeStr == null) {
                return false;
            }
            this.lastError = null;
            ScriptManager scriptManager = manager();
            if (scriptManager == null) {
                this.error = true;
                return false;
            }
            final File file = new File(scriptManager.COMPILED_DIR);
            if (!file.exists() || !file.isDirectory()) {
                java.nio.file.Files.createDirectories(file.toPath());
            }
            if (scriptManager.compiler == null) {
                this.error = true;
                this.lastError = "Launch client with JDK 17+ to compile scripts";
                Utils.sendMessage("&cCannot compile scripts: launch Miau with a JDK (Java 17 or newer).");
                return false;
            }
            final ScriptDiagnosticListener bp = new ScriptDiagnosticListener(this);
            try (final StandardJavaFileManager fileManager = scriptManager.compiler.getStandardFileManager(bp, null, java.nio.charset.StandardCharsets.UTF_8)) {
            final ArrayList<String> compilationOptions = new ArrayList<>();
            compilationOptions.add("-d");
            compilationOptions.add(scriptManager.COMPILED_DIR);
            compilationOptions.addAll(Arrays.asList("--release", "17", "-proc:none", "-encoding", "UTF-8", "-classpath", scriptManager.jarPath));
            boolean success = scriptManager.compiler.getTask(null, ContainerCompilation.fileManager(fileManager, Script.class.getClassLoader()), bp, compilationOptions, null, Arrays.asList(new JavaSourceFromString(this.scriptName, this.codeStr, this.STARTING_LINE))).call();
            if (!success) {
                this.error = true;
                return false;
            }
            try (final SecureClassLoader secureClassLoader = new SecureClassLoader(new URL[]{file.toURI().toURL()}, Script.class.getClassLoader())) {
                this.clazz = secureClassLoader.loadClass(this.scriptName);
                loadGeneratedNestedClasses(secureClassLoader, file);
                this.instance = this.clazz.getDeclaredConstructor().newInstance();
                this.clazz.getDeclaredMethods();
            }
            catch (Throwable e) {
                e.printStackTrace();
                Utils.sendMessage("&7Script &b" + this.name + " &cfailed to load: &7" + e.getClass().getSimpleName() + ": " + e.getMessage());
                this.error = true;
                return false;
            }
            return true;
            }
        }
        catch (Exception ex) {
            Utils.log.error("Unable to compile script {}", this.name, ex);
            this.error = true;
            return !error;
        }
    }

    public int getBoolean(final String s, final Object... array) {
        if (this.clazz == null || this.instance == null) {
            return -1;
        }
        Method method = null;
        for (final Method method2 : this.clazz.getDeclaredMethods()) {
            if (method2.getName().equalsIgnoreCase(s) && method2.getParameterCount() == array.length && method2.getReturnType().equals(Boolean.TYPE)) {
                method = method2;
                break;
            }
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                final Object invoke = method.invoke(this.instance, array);
                if (invoke instanceof Boolean) {
                    return ((boolean)invoke) ? 1 : 0;
                }
            }
            catch (Throwable ex) {
                Throwable er = ex;
                Utils.sendMessage("&7Runtime error during script &b" + this.name);
                if (er.getCause() == null) {
                    Utils.sendMessage(" &7err: &cThrowable");
                }
                else {
                    Utils.sendMessage(" &7err: &c" + describeCause(er.getCause()));
                    final StackTraceElement[] stArr = er.getCause().getStackTrace();
                    if (stArr.length > 0) {
                        StackTraceElement st = stArr[0];
                        for (final StackTraceElement element : er.getCause().getStackTrace()) {
                            if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                st = element;
                                break;
                            }
                        }
                        Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - STARTING_LINE));
                        Utils.sendMessage(" &7src: &c" + st.getMethodName());
                    }
                }
            }
        }
        return -1;
    }

    public String getString(final String s, final Object... array) {
        if (this.clazz == null || this.instance == null) {
            return null;
        }
        Method method = null;
        for (final Method method2 : this.clazz.getDeclaredMethods()) {
            if (method2.getName().equalsIgnoreCase(s) && method2.getParameterCount() == array.length && method2.getReturnType().equals(String.class)) {
                method = method2;
                break;
            }
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                final Object invoke = method.invoke(this.instance, array);
                if (invoke instanceof String) {
                    return (String) invoke;
                }
            }
            catch (Throwable ex) {
                Throwable er = ex;
                Utils.sendMessage("&7Runtime error during script &b" + this.name);
                if (er.getCause() == null) {
                    Utils.sendMessage(" &7err: &cThrowable");
                }
                else {
                    Utils.sendMessage(" &7err: &c" + describeCause(er.getCause()));
                    final StackTraceElement[] stArr = er.getCause().getStackTrace();
                    if (stArr.length > 0) {
                        StackTraceElement st = stArr[0];
                        for (final StackTraceElement element : er.getCause().getStackTrace()) {
                            if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                st = element;
                                break;
                            }
                        }
                        Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - STARTING_LINE));
                        Utils.sendMessage(" &7src: &c" + st.getMethodName());
                    }
                }
            }
        }
        return null;
    }

    public Float[] getFloatArray(String methodName, Object... args) {
        if (this.clazz == null || this.instance == null) {
            return null;
        }
        Method method = null;
        for (Method _method : this.clazz.getDeclaredMethods()) {
            if (_method.getName().equals(methodName) && _method.getReturnType().equals(Float[].class) && _method.getParameterCount() == args.length) {
                method = _method;
                break;
            }
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                Object result = method.invoke(this.instance, args);
                if (result instanceof Float[]) {
                    return (Float[])result;
                }
            }
            catch (Throwable ex) {
                Throwable er = ex;
                Utils.sendMessage("&7Runtime error during script &b" + this.name);
                if (er.getCause() == null) {
                    Utils.sendMessage(" &7err: &cThrowable");
                }
                else {
                    Utils.sendMessage(" &7err: &c" + describeCause(er.getCause()));
                    StackTraceElement[] stArr = er.getCause().getStackTrace();
                    if (stArr.length > 0) {
                        StackTraceElement st = stArr[0];
                        for (StackTraceElement element : er.getCause().getStackTrace()) {
                            if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                st = element;
                                break;
                            }
                        }
                        Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - STARTING_LINE));
                        Utils.sendMessage(" &7src: &c" + st.getMethodName());
                    }
                }
            }
        }
        return null;
    }

    public void delete() {
        this.clazz = null;
        this.instance = null;
        ScriptManager scriptManager = manager();
        if (scriptManager == null) {
            return;
        }
        final File file = new File(scriptManager.COMPILED_DIR + File.separator + this.scriptName + ".class");
        if (file.exists()) {
            file.delete();
        }
    }

    public void setCode(String code) {
        STARTING_LINE = 0;
        StringBuilder fileCodeContents = new StringBuilder();
        ScriptManager scriptManager = manager();
        if (scriptManager == null) {
            this.codeStr = code;
            return;
        }
        Iterator<String> iterator = scriptManager.imports.iterator();
        while (iterator.hasNext()) {
            STARTING_LINE++;
            fileCodeContents.append("import ").append(iterator.next()).append(";\n");
        }
        fileCodeContents.append("import keystrokesmod.script.model.*;\n");
        fileCodeContents.append("import keystrokesmod.script.packet.clientbound.*;\n");
        fileCodeContents.append("import keystrokesmod.script.packet.serverbound.*;\n");
        fileCodeContents.append("import keystrokesmod.script.ScriptDefaults.*;\n");
        fileCodeContents.append("import keystrokesmod.utility.*;\n");

        ScriptSourceTransformer.ProcessedScript processed = ScriptSourceTransformer.process(code, this.name);
        for (String customImport : processed.extractedImports) {
            STARTING_LINE++;
            fileCodeContents.append("import ").append(customImport).append(";\n");
        }

        String name = this.name.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        String transformedCode = processed.code;
        this.codeStr = fileCodeContents + "@SuppressWarnings(\"unchecked\")public class " + this.scriptName + " extends " + ScriptDefaults.class.getName() + " {public static final " + ScriptDefaults.modules.class.getName().replace("$", ".") + " modules = new " + ScriptDefaults.modules.class.getName().replace("$", ".") + "(\"" + name + "\");public static final String scriptName = \"" + name + "\";\n" + transformedCode + "\n}";
        STARTING_LINE += 5;
    }

    private void loadGeneratedNestedClasses(SecureClassLoader classLoader, File outputDirectory) throws ClassNotFoundException {
        File[] nestedClassFiles = outputDirectory.listFiles((directory, fileName) ->
                fileName.startsWith(this.scriptName + "$") && fileName.endsWith(".class"));
        if (nestedClassFiles == null || nestedClassFiles.length == 0) {
            return;
        }

        Arrays.sort(nestedClassFiles, Comparator
                .comparingInt((File classFile) -> nestedClassDepth(classFile.getName()))
                .thenComparing(File::getName));
        for (File nestedClassFile : nestedClassFiles) {
            String fileName = nestedClassFile.getName();
            classLoader.loadClass(fileName.substring(0, fileName.length() - ".class".length()));
        }
    }

    private int nestedClassDepth(String fileName) {
        int depth = 0;
        for (int i = 0; i < fileName.length(); i++) {
            if (fileName.charAt(i) == '$') {
                depth++;
            }
        }
        return depth;
    }

    public boolean invoke(final String s, final Object... array) {
        if (this.clazz == null || this.instance == null) {
            return false;
        }
        Method method = null;
        for (final Method method2 : this.clazz.getDeclaredMethods()) {
            if (method2.getName().equalsIgnoreCase(s) && method2.getParameterCount() == array.length && method2.getReturnType().equals(Void.TYPE)) {
                method = method2;
                break;
            }
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                method.invoke(this.instance, array);
                return true;
            }
            catch (Throwable ex) {
                Throwable er = ex;
                System.err.println("[script-runtime] " + this.name + " " + s + ": " + (er.getCause() != null ? er.getCause() : er));
                Utils.sendMessage("&7Runtime error during script &b" + this.name);
                if (er.getCause() == null) {
                    Utils.sendMessage(" &7err: &cThrowable");
                }
                else {
                    Utils.sendMessage(" &7err: &c" + describeCause(er.getCause()));
                    final StackTraceElement[] stArr = er.getCause().getStackTrace();
                    if (stArr.length > 0) {
                        StackTraceElement st = stArr[0];
                        for (final StackTraceElement element : er.getCause().getStackTrace()) {
                            if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                st = element;
                                break;
                            }
                        }
                        Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - STARTING_LINE));
                        Utils.sendMessage(" &7src: &c" + st.getMethodName());
                    }
                }
            }
        }
        return false;
    }

    private static String describeCause(Throwable cause) {
        if (cause == null) {
            return "Unknown";
        }
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        return message == null || message.isEmpty() || message.equals(name) ? name : name + ": " + message;
    }
}
