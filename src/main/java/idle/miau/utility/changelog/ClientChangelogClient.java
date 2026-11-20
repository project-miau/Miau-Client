package idle.miau.utility.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import idle.miau.utility.api.MiauAPI;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ClientChangelogClient {
    public static final int PAGE_LIMIT = 20;
    static final int MAX_NOTES_LENGTH = 64 * 1024;
    private static final int MAX_RESPONSE_LENGTH = 2 * 1024 * 1024;
    private static final Pattern RELEASE_ID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern CURSOR = Pattern.compile("^[A-Za-z0-9_-]{1,512}$");

    public ClientChangelogPage loadPage(String cursor) throws IOException {
        String normalizedCursor = normalizeCursor(cursor);
        String path = "changelogs/client?limit=" + PAGE_LIMIT;
        if (normalizedCursor != null) {
            path += "&cursor=" + MiauAPI.encodePathSegment(normalizedCursor);
        }
        return parse(MiauAPI.getApiV1(path));
    }

    static ClientChangelogPage parse(String json) throws IOException {
        JsonObject response = parseResponse(json);
        if (!"success".equals(requiredString(response, "status", 32))) {
            throw invalidResponse();
        }
        JsonElement changelogsValue = response.get("changelogs");
        if (changelogsValue == null || !changelogsValue.isJsonArray()) {
            throw invalidResponse();
        }
        JsonArray values = changelogsValue.getAsJsonArray();
        if (values.size() > PAGE_LIMIT) {
            throw invalidResponse();
        }

        List<ClientChangelogEntry> entries = new ArrayList<ClientChangelogEntry>();
        Set<String> releaseIds = new HashSet<String>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw invalidResponse();
            }
            ClientChangelogEntry entry = parseEntry(value.getAsJsonObject());
            if (!releaseIds.add(entry.getReleaseId())) {
                throw invalidResponse();
            }
            entries.add(entry);
        }
        Collections.sort(entries, new Comparator<ClientChangelogEntry>() {
            @Override
            public int compare(ClientChangelogEntry left, ClientChangelogEntry right) {
                int dateOrder = Long.compare(
                        right.getPublishedAtEpochMillis(), left.getPublishedAtEpochMillis());
                return dateOrder != 0
                        ? dateOrder : right.getReleaseId().compareTo(left.getReleaseId());
            }
        });
        return new ClientChangelogPage(entries, parseNextCursor(response.get("nextCursor")));
    }

    private static ClientChangelogEntry parseEntry(JsonObject value) throws IOException {
        String releaseId = requiredString(value, "releaseId", 32);
        if (!RELEASE_ID.matcher(releaseId).matches()) {
            throw invalidResponse();
        }
        String displayName = optionalString(value, "displayName", 64);
        String version = optionalString(value, "version", 64);
        if (displayName == null || displayName.isEmpty()) {
            if (version == null || version.isEmpty()) {
                throw invalidResponse();
            }
            displayName = version;
        }
        if (version == null || version.isEmpty()) {
            version = displayName;
        }
        if (containsControlCharacter(displayName) || containsControlCharacter(version)) {
            throw invalidResponse();
        }
        String publishedAt = requiredString(value, "publishedAt", 64);
        long timestamp;
        try {
            timestamp = Instant.parse(publishedAt).toEpochMilli();
        }
        catch (DateTimeParseException exception) {
            throw invalidResponse();
        }
        String notes = requiredString(value, "notes", MAX_NOTES_LENGTH, false);
        return new ClientChangelogEntry(
                releaseId,
                displayName,
                version,
                publishedAt,
                timestamp,
                notes,
                optionalBoolean(value, "current"));
    }

    private static JsonObject parseResponse(String json) throws IOException {
        if (json == null || json.length() > MAX_RESPONSE_LENGTH) {
            throw invalidResponse();
        }
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                throw invalidResponse();
            }
            return parsed.getAsJsonObject();
        }
        catch (IOException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw invalidResponse();
        }
    }

    private static String parseNextCursor(JsonElement value) throws IOException {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalidResponse();
        }
        return normalizeCursor(value.getAsString());
    }

    private static String normalizeCursor(String value) throws IOException {
        if (value == null) {
            return null;
        }
        String cursor = value.trim();
        if (cursor.isEmpty() || !CURSOR.matcher(cursor).matches()) {
            throw invalidResponse();
        }
        return cursor;
    }

    private static String requiredString(
            JsonObject value, String key, int maxLength) throws IOException {
        return requiredString(value, key, maxLength, true);
    }

    private static String requiredString(
            JsonObject value, String key, int maxLength, boolean requireContent)
            throws IOException {
        String text = optionalString(value, key, maxLength);
        if (text == null || requireContent && text.isEmpty()) {
            throw invalidResponse();
        }
        return text;
    }

    private static String optionalString(
            JsonObject value, String key, int maxLength) throws IOException {
        JsonElement element = value.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalidResponse();
        }
        String text = element.getAsString().trim();
        if (text.length() > maxLength) {
            throw invalidResponse();
        }
        return text;
    }

    private static boolean optionalBoolean(JsonObject value, String key) throws IOException {
        JsonElement element = value.get(key);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (!element.isJsonPrimitive()) {
            throw invalidResponse();
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw invalidResponse();
        }
        return primitive.getAsBoolean();
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                return true;
            }
        }
        return false;
    }

    private static IOException invalidResponse() {
        return new IOException("The changelog service returned an invalid response");
    }
}
