package idle.miau.utility.spotify;

import com.google.gson.*;

import net.minecraft.client.Minecraft;

import idle.miau.utility.spotify.security.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class MusicCredentialsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file = new File(Minecraft.getMinecraft().mcDataDir, "Miau_Music.json");
    private JsonObject data = new JsonObject();
    private final TokenProtector protector;

    public MusicCredentialsStore() {
        protector = TokenProtectorFactory.create();
    }

    public synchronized void load() {
        data = read(file);
        if (data.entrySet().isEmpty()) migrateLegacy();
        migratePlainTokens();
    }

    public synchronized String get(String key) {
        return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : "";
    }

    public synchronized void put(String key, String value) {
        data.addProperty(key, value == null ? "" : value);
    }

    public synchronized SpotifyTokenSet loadSpotifyTokens() {
        try {
            if (!data.has("spotify_tokens") || !data.get("spotify_tokens").isJsonObject())
                return SpotifyTokenSet.EMPTY;
            JsonObject e = data.getAsJsonObject("spotify_tokens");
            String raw = protector.unprotect(string(e, "payload"));
            JsonObject j = new JsonParser().parse(raw).getAsJsonObject();
            return new SpotifyTokenSet(
                    string(j, "access"),
                    string(j, "refresh"),
                    j.has("expires_at") ? j.get("expires_at").getAsLong() : 0L);
        } catch (Exception ignored) {
            return SpotifyTokenSet.EMPTY;
        }
    }

    public synchronized void saveSpotifyTokens(SpotifyTokenSet tokens) throws IOException {
        try {
            JsonObject raw = new JsonObject();
            raw.addProperty("access", tokens.getAccessToken());
            raw.addProperty("refresh", tokens.getRefreshToken());
            raw.addProperty("expires_at", tokens.getExpiresAt());
            JsonObject env = new JsonObject();
            env.addProperty("version", 1);
            env.addProperty("mode", protector.mode());
            env.addProperty("payload", protector.protect(GSON.toJson(raw)));
            data.add("spotify_tokens", env);
            data.remove("spotify_access_token");
            data.remove("spotify_refresh_token");
            save();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not protect Spotify tokens");
        }
    }

    public synchronized void clearSpotifyTokens() throws IOException {
        data.remove("spotify_tokens");
        data.remove("spotify_access_token");
        data.remove("spotify_refresh_token");
        save();
    }

    public synchronized boolean hasSpotifyAuthorization() {
        SpotifyTokenSet t = loadSpotifyTokens();
        return t.hasAccessToken() || t.hasRefreshToken();
    }

    public synchronized void clearAll() throws IOException {
        data = new JsonObject();
        save();
    }

    public synchronized void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(file.getPath() + ".tmp"), bak = new File(file.getPath() + ".bak");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            GSON.toJson(data, w);
        }
        if (file.isFile())
            Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonObject read(File source) {
        JsonObject result = readOne(source);
        if (result.entrySet().isEmpty()) result = readOne(new File(source.getPath() + ".bak"));
        return result;
    }

    private JsonObject readOne(File source) {
        if (!source.isFile() || source.length() > 1024 * 1024) return new JsonObject();
        try (Reader r =
                new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
            JsonElement e = new JsonParser().parse(r);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private void migrateLegacy() {
        JsonObject l = readOne(new File(file.getParentFile(), "Miau_LastFm.json"));
        copyAny(l, "lastfm_user", "lastfm_user", "user", "username");
        copyAny(l, "lastfm_key", "lastfm_key", "key", "api_key");
        copyAny(l, "spotify_client_id", "spotify_client_id", "client_id");
        JsonObject s = readOne(new File(file.getParentFile(), "Miau_Spotify.json"));
        copyAny(s, "spotify_client_id", "spotify_client_id", "client_id");
        copyAny(s, "spotify_access_token", "spotify_access_token", "access_token");
        copyAny(s, "spotify_refresh_token", "spotify_refresh_token", "refresh_token");
        try {
            save();
        } catch (IOException ignored) {
        }
    }

    private void migratePlainTokens() {
        String access = get("spotify_access_token"), refresh = get("spotify_refresh_token");
        if (access.isEmpty() && refresh.isEmpty()) return;
        try {
            saveSpotifyTokens(new SpotifyTokenSet(access, refresh, 0L));
        } catch (IOException ignored) {
        }
    }

    private void copyAny(JsonObject source, String target, String... candidates) {
        for (String key : candidates)
            if (source.has(key) && !source.get(key).isJsonNull()) {
                data.addProperty(target, source.get(key).getAsString());
                return;
            }
    }

    private static String string(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
