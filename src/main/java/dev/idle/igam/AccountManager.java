package dev.idle.igam;

import com.google.gson.*;
import dev.idle.igam.auth.Account;
import dev.idle.igam.auth.SessionManager;
import dev.idle.igam.utils.Nan0EventRegister;
import dev.idle.igam.utils.SSLUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import idle.miau.mcp.McpRuntime;
import idle.miau.utility.spotify.security.TokenProtector;
import idle.miau.utility.spotify.security.TokenProtectorFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class AccountManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final File file = new File(mc.mcDataDir, "openmiau.accounts.json");
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final Logger LOGGER = Logger.getLogger(AccountManager.class.getName());
  private static final TokenProtector tokenProtector = TokenProtectorFactory.create();
  private static final Object STORAGE_LOCK = new Object();
  private static long lastPlayTimeUpdate = System.currentTimeMillis();
  private static long lastPlayTimeSave = lastPlayTimeUpdate;
  private static boolean playTimeDirty;

  public static final List<Account> accounts = new CopyOnWriteArrayList<Account>();

  public static void init() {
    SSLUtils.getSSLContext();
    Nan0EventRegister.register(McpRuntime.EVENTS, new Events());
    SessionManager.captureLaunchSession();

    if (!file.exists()) {
      try {
        if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
          if (file.createNewFile()) {
            LOGGER.info("Successfully created openmiau.accounts.json");
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Couldn't create openmiau.accounts.json", e);
      }
    }
    load();
    recordSession(SessionManager.get());
  }

  public static Account store(Account incoming) {
    if (incoming == null) {
      throw new IllegalArgumentException("incoming");
    }
    synchronized (STORAGE_LOCK) {
      Account stored = AccountStore.merge(accounts, incoming);
      saveLocked();
      return stored;
    }
  }

  public static void storeAll(List<Account> incomingAccounts) {
    if (incomingAccounts == null || incomingAccounts.isEmpty()) {
      return;
    }
    synchronized (STORAGE_LOCK) {
      for (Account incoming : incomingAccounts) {
        if (incoming != null) {
          AccountStore.merge(accounts, incoming);
        }
      }
      saveLocked();
    }
  }

  public static void load() {
    synchronized (STORAGE_LOCK) {
      if (playTimeDirty) {
        saveLocked();
      }
      accounts.clear();
      boolean migratePlainTokens = false;
      try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
        JsonElement json = new JsonParser().parse(reader);
        if (json != null && json.isJsonArray()) {
          for (JsonElement element : json.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String refreshToken = "";
            String accessToken = "";
            String authData = "";

            if (object.has("credentials") && object.get("credentials").isJsonObject()) {
              try {
                JsonObject envelope = object.getAsJsonObject("credentials");
                String decrypted = tokenProtector.unprotect(string(envelope, "payload"));
                JsonObject credentials = new JsonParser().parse(decrypted).getAsJsonObject();
                refreshToken = string(credentials, "refreshToken");
                accessToken = string(credentials, "accessToken");
                authData = string(credentials, "authData");
              } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Couldn't decrypt credentials for saved account", e);
              }
            } else {
              refreshToken = optionalString(object, "refreshToken");
              accessToken = optionalString(object, "accessToken");
              migratePlainTokens |= !refreshToken.isEmpty() || !accessToken.isEmpty();
            }

            accounts.add(
                new Account(
                    refreshToken,
                    accessToken,
                    optionalString(object, "username"),
                    Optional.ofNullable(object.get("unban")).map(JsonElement::getAsLong).orElse(0L),
                    optionalString(object, "clientId"),
                    optionalString(object, "scope"),
                    Optional.ofNullable(object.get("type"))
                        .map(JsonElement::getAsString)
                        .orElse(Account.TYPE_PREMIUM),
                    optionalString(object, "uid"),
                    optionalLong(object, "lastLogin"),
                    optionalLong(object, "playTime"),
                    authData));
          }
        }
      } catch (FileNotFoundException e) {
        LOGGER.log(Level.WARNING, "Couldn't find openmiau.accounts.json", e);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Couldn't load openmiau.accounts.json", e);
      }

      if (migratePlainTokens) {
        saveLocked();
      }
      playTimeDirty = false;
      lastPlayTimeUpdate = System.currentTimeMillis();
      lastPlayTimeSave = lastPlayTimeUpdate;
    }
  }

  public static void save() {
    synchronized (STORAGE_LOCK) {
      saveLocked();
    }
  }

  public static void recordSession(Session session) {
    if (session == null) return;
    synchronized (STORAGE_LOCK) {
      Account account = findAccount(session);
      if (account == null) return;
      account.setUsername(session.getUsername());
      account.setUid(session.getPlayerID());
      account.setLastLogin(System.currentTimeMillis());
      lastPlayTimeUpdate = System.currentTimeMillis();
      saveLocked();
    }
  }

  public static void updatePlayTime() {
    synchronized (STORAGE_LOCK) {
      long now = System.currentTimeMillis();
      long elapsed = Math.max(0L, Math.min(5000L, now - lastPlayTimeUpdate));
      lastPlayTimeUpdate = now;
      if (mc.theWorld == null || mc.thePlayer == null || elapsed == 0L) return;
      Account account = findAccount(SessionManager.get());
      if (account == null) return;
      account.addPlayTime(elapsed);
      playTimeDirty = true;
      if (now - lastPlayTimeSave >= 30000L) {
        saveLocked();
        lastPlayTimeSave = now;
      }
    }
  }

  private static Account findAccount(Session session) {
    String token = session.getToken();
    if (token != null && !token.isEmpty() && !"0".equals(token)) {
      for (Account account : accounts) {
        if (token.equals(account.getAccessToken())) return account;
      }
    }
    String username = session.getUsername();
    for (Account account : accounts) {
      if (username != null && username.equalsIgnoreCase(account.getUsername())) return account;
    }
    return null;
  }

  private static void saveLocked() {
    try {
      JsonArray array = new JsonArray();
      for (Account account : new ArrayList<Account>(accounts)) {
        JsonObject object = new JsonObject();
        if (!account.getRefreshToken().isEmpty()
            || !account.getAccessToken().isEmpty()
            || !account.getAuthData().isEmpty()) {
          JsonObject credentials = new JsonObject();
          credentials.addProperty("refreshToken", account.getRefreshToken());
          credentials.addProperty("accessToken", account.getAccessToken());
          credentials.addProperty("authData", account.getAuthData());
          JsonObject envelope = new JsonObject();
          envelope.addProperty("version", 1);
          envelope.addProperty("mode", tokenProtector.mode());
          envelope.addProperty("payload", tokenProtector.protect(gson.toJson(credentials)));
          object.add("credentials", envelope);
        }
        object.addProperty("username", account.getUsername());
        object.addProperty("unban", account.getUnban());
        object.addProperty("clientId", account.getClientId());
        object.addProperty("scope", account.getScope());
        object.addProperty("type", account.getType());
        object.addProperty("uid", account.getUid());
        object.addProperty("lastLogin", account.getLastLogin());
        object.addProperty("playTime", account.getPlayTime());
        array.add(object);
      }

      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      File temporary = new File(file.getPath() + ".tmp");
      File backup = new File(file.getPath() + ".bak");
      try (Writer writer =
          new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
        gson.toJson(array, writer);
      }
      if (file.isFile() && file.length() > 0L) {
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      try {
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      playTimeDirty = false;
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Couldn't save openmiau.accounts.json", e);
    }
  }

  private static String optionalString(JsonObject object, String key) {
    return Optional.ofNullable(object.get(key)).map(JsonElement::getAsString).orElse("");
  }

  private static long optionalLong(JsonObject object, String key) {
    return Optional.ofNullable(object.get(key)).map(JsonElement::getAsLong).orElse(0L);
  }

  private static String string(JsonObject object, String key) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
  }
}
