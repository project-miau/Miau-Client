package idle.miau.utility.config.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import idle.miau.utility.api.MiauAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineConfigClient {
    private static final long LIST_CACHE_TTL_MS = 60000L;
    private static final long CONFIG_CACHE_TTL_MS = 60000L;

    private static final Map<OnlineConfigSource, CacheEntry<List<OnlineConfigEntry>>> LIST_CACHE =
            new ConcurrentHashMap<OnlineConfigSource, CacheEntry<List<OnlineConfigEntry>>>();
    private static final Map<String, CacheEntry<String>> CONFIG_CACHE =
            new ConcurrentHashMap<String, CacheEntry<String>>();

    private static final class CacheEntry<T> {
        final T data;
        final long expiresAt;

        CacheEntry(T data, long expiresAt) {
            this.data = data;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    public static void clearCache() {
        LIST_CACHE.clear();
        CONFIG_CACHE.clear();
    }

    public List<OnlineConfigEntry> list(OnlineConfigSource source) throws Exception {
        return list(source, false);
    }

    public List<OnlineConfigEntry> list(OnlineConfigSource source, boolean forceRefresh) throws Exception {
        if (!forceRefresh) {
            CacheEntry<List<OnlineConfigEntry>> cached = LIST_CACHE.get(source);
            if (cached != null && cached.isValid()) {
                return cached.data;
            }
        }

        List<OnlineConfigEntry> entries;
        try {
            entries = parseList(MiauAPI.getClient("configs/" + source.getPath()));
        }
        catch (MiauAPI.MiauApiException exception) {
            if (exception.getStatusCode() != 404) {
                throw exception;
            }
            entries = parseList(MiauAPI.getClient(legacyPath(source)));
        }

        LIST_CACHE.put(source, new CacheEntry<List<OnlineConfigEntry>>(entries, System.currentTimeMillis() + LIST_CACHE_TTL_MS));
        return entries;
    }

    public String load(OnlineConfigSource source, String configId) throws Exception {
        return load(source, configId, false);
    }

    public String load(OnlineConfigSource source, String configId, boolean forceRefresh) throws Exception {
        String id = configId == null ? "" : configId.trim();
        if (id.isEmpty()) {
            throw new Exception("Invalid config id");
        }
        String cacheKey = (source == null ? "OFFICIAL" : source.name()) + ":" + id;
        if (!forceRefresh) {
            CacheEntry<String> cached = CONFIG_CACHE.get(cacheKey);
            if (cached != null && cached.isValid()) {
                return cached.data;
            }
        }

        String content;
        try {
            content = MiauAPI.getClient(
                    "configs/" + source.getPath() + "/" + MiauAPI.encodePathSegment(id));
        }
        catch (MiauAPI.MiauApiException exception) {
            if (exception.getStatusCode() != 404) {
                throw exception;
            }
            content = MiauAPI.getClient(
                    legacyPath(source) + "/" + MiauAPI.encodePathSegment(id));
        }

        CONFIG_CACHE.put(cacheKey, new CacheEntry<String>(content, System.currentTimeMillis() + CONFIG_CACHE_TTL_MS));
        return content;
    }

    static List<OnlineConfigEntry> parseList(String json) {
        JsonElement parsed = new JsonParser().parse(json);
        JsonArray entries;
        if (parsed.isJsonArray()) {
            entries = parsed.getAsJsonArray();
        }
        else if (parsed.isJsonObject()) {
            JsonObject response = parsed.getAsJsonObject();
            JsonElement configs = response.get("configs");
            if (configs == null || !configs.isJsonArray()) {
                return Collections.emptyList();
            }
            entries = configs.getAsJsonArray();
        }
        else {
            return Collections.emptyList();
        }

        List<OnlineConfigEntry> result = new ArrayList<OnlineConfigEntry>();
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                continue;
            }
            OnlineConfigEntry value = OnlineConfigEntry.fromJson(entry.getAsJsonObject());
            if (value != null && !value.getId().isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String legacyPath(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? "user-configs" : "settings";
    }
}
