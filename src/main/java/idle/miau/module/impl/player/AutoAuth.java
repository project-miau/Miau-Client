package idle.miau.module.impl.player;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.ClientStorage;
import net.minecraft.network.play.server.S02PacketChat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public final class AutoAuth extends Module {
    public static final String FILE_NAME = "autoauth.json";

    private static final String[] REGISTER_KEYWORDS = {
            "/register",
            "/registrar",
            "/rejestracja",
            "/cadastrar",
            "/enregistrer",
            "/зарег",
            "/kayit",
            "/reg"
    };
    private static final String[] LOGIN_KEYWORDS = {
            "/login",
            "/logar",
            "/entrar",
            "/ingresar",
            "/einloggen",
            "/seconnecter",
            "/connexion",
            "/zaloguj",
            "/войти",
            "/giriş",
            "/giris",
            "/log"
    };

    private final TextSetting password;
    private final ButtonSetting doublePassword;
    private boolean registerSent;
    private boolean loginSent;
    private boolean loadingStorage;

    public AutoAuth() {
        super("AutoAuth", category.player, 0);
        setDescription("Automatically login and register on Cracked servers.");
        registerSetting(password = new TextSetting(
                "Password",
                "miauclient67",
                "Type an authentication password...",
                64,
                this::savePasswordStorage
        ) {
            @Override
            public void setText(String text) {
                super.setText(text);
                if (!loadingStorage) {
                    savePasswordStorage();
                }
            }
        }.setSensitive(true));
        registerSetting(doublePassword = new ButtonSetting("Double password", true));
        loadPasswordStorage();
    }

    public static File getStorageFile() {
        if (mc == null || mc.mcDataDir == null) {
            return null;
        }
        File target = new File(ClientStorage.ensureDirectory(mc.mcDataDir), FILE_NAME);
        if (!target.exists()) {
            File legacy = new File(ClientStorage.getLegacyDirectory(mc.mcDataDir), FILE_NAME);
            try {
                ClientStorage.copyIfMissing(legacy, target);
            } catch (Exception ignored) {
            }
        }
        return target;
    }

    public void loadPasswordStorage() {
        File file = getStorageFile();
        if (file != null) {
            loadPasswordStorage(file);
        }
    }

    public void loadPasswordStorage(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }
        loadingStorage = true;
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(content);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("password") && obj.get("password").isJsonPrimitive()) {
                    password.setText(obj.get("password").getAsString());
                }
                if (obj.has("doublePassword") && obj.get("doublePassword").isJsonPrimitive()) {
                    doublePassword.setEnabled(obj.get("doublePassword").getAsBoolean());
                }
            }
        } catch (Exception ignored) {
        } finally {
            loadingStorage = false;
        }
    }

    public void savePasswordStorage() {
        File file = getStorageFile();
        if (file != null) {
            savePasswordStorage(file);
        }
    }

    public void savePasswordStorage(File file) {
        if (file == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("password", password.getText());
            obj.addProperty("doublePassword", doublePassword.isToggled());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(obj);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onEnable() {
        resetSession();
        loadPasswordStorage();
    }

    @Override
    public void onDisable() {
        resetSession();
        savePasswordStorage();
    }

    @Subscribe(receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.thePlayer == null || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.getType() == 2 || packet.getChatComponent() == null) {
            return;
        }

        String command = resolveAuthCommand(packet.getChatComponent().getUnformattedText());
        if (command != null) {
            mc.thePlayer.sendChatMessage(command);
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetSession();
    }

    String resolveAuthCommand(String message) {
        String value = normalizePassword(password.getText());
        if (value == null) {
            return null;
        }

        String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String registerCommand = findKeyword(normalizedMessage, REGISTER_KEYWORDS);
        if (registerCommand != null) {
            if (registerSent) {
                return null;
            }
            registerSent = true;
            return registerCommand + " " + value
                    + (doublePassword.isToggled() ? " " + value : "");
        }

        String loginCommand = findKeyword(normalizedMessage, LOGIN_KEYWORDS);
        if (loginCommand == null || loginSent) {
            return null;
        }
        loginSent = true;
        return loginCommand + " " + value;
    }

    void resetSession() {
        registerSent = false;
        loginSent = false;
    }

    private static String findKeyword(String message, String[] keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private static String normalizePassword(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) {
                return null;
            }
        }
        return normalized;
    }
}
