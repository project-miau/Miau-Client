package idle.miau.utility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import idle.miau.utility.ClientStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MainMenuPreferences {
    static final String FILE_NAME = "client-preferences.json";
    static final String LOGO_MODE_KEY = logoModeKey();
    private static final long MAX_FILE_SIZE = 64L * 1024L;
    private static final Object FILE_LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogManager.getLogger(MainMenuPreferences.class);

    private final File file;

    private MainMenuPreferences(File file) {
        this.file = file;
    }

    private static String logoModeKey() {
        return "mainMenuLogoMode";
    }

    public static MainMenuPreferences forGameDirectory(File gameDirectory) {
        return new MainMenuPreferences(
                new File(ClientStorage.getDirectory(gameDirectory), FILE_NAME));
    }

    static MainMenuPreferences forFile(File file) {
        return new MainMenuPreferences(file);
    }

    public LogoMode loadLogoMode() {
        synchronized (FILE_LOCK) {
            JsonObject preferences = readPreferences();
            JsonElement value = preferences.get(LOGO_MODE_KEY);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                return LogoMode.TEXT;
            }
            try {
                return LogoMode.fromSerialized(value.getAsString());
            }
            catch (RuntimeException ignored) {
                return LogoMode.TEXT;
            }
        }
    }

    public boolean saveLogoMode(LogoMode mode) {
        synchronized (FILE_LOCK) {
            LogoMode safeMode = mode == null ? LogoMode.TEXT : mode;
            JsonObject preferences = readPreferences();
            preferences.addProperty(LOGO_MODE_KEY, safeMode.serializedName);

            Path temporary = null;
            try {
                Path target = file.toPath().toAbsolutePath();
                Path directory = target.getParent();
                if (directory == null) {
                    throw new IOException("Main-menu preference file has no parent directory");
                }
                Files.createDirectories(directory);
                temporary = Files.createTempFile(directory, FILE_NAME + ".", ".tmp");
                try (BufferedWriter writer = Files.newBufferedWriter(
                        temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(preferences, writer);
                }
                ConfigManager.replaceConfigFile(temporary, target);
                temporary = null;
                return true;
            }
            catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to save the main-menu logo preference", exception);
                return false;
            }
            finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    }
                    catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private JsonObject readPreferences() {
        try {
            if (!file.isFile() || file.length() > MAX_FILE_SIZE) {
                return new JsonObject();
            }
            try (BufferedReader reader = Files.newBufferedReader(
                    file.toPath(), StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                return parsed != null && parsed.isJsonObject()
                        ? parsed.getAsJsonObject()
                        : new JsonObject();
            }
        }
        catch (IOException | RuntimeException ignored) {
            return new JsonObject();
        }
    }

    public enum LogoMode {
        TEXT("text"),
        IMAGE("image");

        private final String serializedName;

        LogoMode(String serializedName) {
            this.serializedName = serializedName;
        }

        static LogoMode fromSerialized(String value) {
            if (value != null) {
                String normalized = value.trim();
                for (LogoMode mode : values()) {
                    if (mode.serializedName.equalsIgnoreCase(normalized)) {
                        return mode;
                    }
                }
            }
            return TEXT;
        }
    }
}
