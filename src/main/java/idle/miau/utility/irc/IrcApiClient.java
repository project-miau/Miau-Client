package idle.miau.utility.irc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import idle.miau.utility.api.MiauAPI;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class IrcApiClient {
    private static final String CLIENT_PROOF_VERSION = "MIAU-IRC-CLIENT-V1";
    private static final String CLIENT_PROOF_PATH = "/api/v1/irc/auth/challenge";
    private static final String CLIENT_OFFLINE_PROOF_PATH = "/api/v1/irc/auth/offline";
    private static final String OFFLINE_ID_VERSION = "MIAU-IRC-OFFLINE-V1";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern UUID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern MESSAGE_ID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern CURSOR = Pattern.compile("^\\d{13}-[a-f0-9]{32}$");
    private static final Pattern SERVER_ID = Pattern.compile("^[a-f0-9]{40}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_PRESENCE = 500;
    private final String clientSecret;
    private final Transport transport;

    public IrcApiClient() {
        this(resolveClientSecret(), new Transport() {
            @Override
            public String request(
                    String method,
                    String path,
                    String body,
                    Map<String, String> headers) throws IOException {
                return MiauAPI.requestApiV1(method, path, body, headers);
            }
        });
    }

    IrcApiClient(String clientSecret, Transport transport) {
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.transport = transport;
    }

    public Challenge requestChallenge(String usernameValue, String uuidValue) throws IOException {
        String username = normalizeUsername(usernameValue);
        String uuid = normalizeUuid(uuidValue);
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("uuid", uuid);
        body.addProperty("branch", MiauAPI.CLIENT_BRANCH);
        JsonObject response = success(transport.request(
                "POST",
                "irc/auth/challenge",
                body.toString(),
                proofHeaders(CLIENT_PROOF_PATH, username, uuid)
        ));
        String challenge = requiredString(response, "challenge", 4096);
        String serverId = requiredString(response, "serverId", 40);
        if (!SERVER_ID.matcher(serverId).matches()) {
            throw invalidResponse();
        }
        return new Challenge(challenge, serverId, requiredLong(response, "expiresAt"));
    }

    public Session requestOfflineSession(String usernameValue) throws IOException {
        String username = normalizeUsername(usernameValue);
        String uuid = createOfflineUuid(username);
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("uuid", uuid);
        body.addProperty("branch", MiauAPI.CLIENT_BRANCH);
        return parseSession(success(transport.request(
                "POST",
                "irc/auth/offline",
                body.toString(),
                proofHeaders(CLIENT_OFFLINE_PROOF_PATH, username, uuid)
        )));
    }

    public Session exchangeChallenge(String challenge) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("challenge", requiredValue(challenge, 4096));
        JsonObject response = success(transport.request(
                "POST",
                "irc/auth/session",
                body.toString(),
                Collections.<String, String>emptyMap()
        ));
        return parseSession(response);
    }

    public boolean isEnabled() throws IOException {
        return requiredBoolean(success(transport.request(
                "GET",
                "irc/status",
                null,
                Collections.<String, String>emptyMap()
        )), "enabled");
    }

    public SyncResult sync(
            String token,
            String after,
            String server,
            boolean heartbeat,
            boolean hidden) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("after", after == null ? "" : after);
        body.addProperty("server", server == null ? "" : server);
        body.addProperty("heartbeat", heartbeat);
        body.addProperty("hidden", hidden);
        JsonObject response = success(transport.request(
                "POST",
                "irc/sync",
                body.toString(),
                bearer(token)
        ));
        JsonElement messagesElement = response.get("messages");
        if (messagesElement == null || !messagesElement.isJsonArray()) {
            throw invalidResponse();
        }
        JsonArray values = messagesElement.getAsJsonArray();
        if (values.size() > MAX_MESSAGES) {
            throw invalidResponse();
        }
        List<IrcMessage> messages = new ArrayList<IrcMessage>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw invalidResponse();
            }
            messages.add(parseMessage(value.getAsJsonObject()));
        }
        String cursor = optionalString(response, "cursor", 64);
        if (cursor == null) {
            cursor = "";
        }
        if (!cursor.isEmpty() && !CURSOR.matcher(cursor).matches()) {
            throw invalidResponse();
        }
        JsonElement presenceElement = response.get("presence");
        List<IrcPresence> presence = new ArrayList<IrcPresence>();
        if (presenceElement != null && !presenceElement.isJsonNull()) {
            if (!presenceElement.isJsonArray()) {
                throw invalidResponse();
            }
            JsonArray presenceValues = presenceElement.getAsJsonArray();
            if (presenceValues.size() > MAX_PRESENCE) {
                throw invalidResponse();
            }
            for (JsonElement value : presenceValues) {
                if (!value.isJsonObject()) {
                    throw invalidResponse();
                }
                presence.add(parsePresence(value.getAsJsonObject()));
            }
        }
        return new SyncResult(
                Collections.unmodifiableList(messages),
                cursor,
                requiredOnline(response),
                Collections.unmodifiableList(presence)
        );
    }

    public IrcMessage send(String token, String content) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("content", requiredValue(content, 256));
        JsonObject response = success(transport.request(
                "POST",
                "irc/messages",
                body.toString(),
                bearer(token)
        ));
        JsonElement message = response.get("message");
        if (message == null || !message.isJsonObject()) {
            throw invalidResponse();
        }
        return parseMessage(message.getAsJsonObject());
    }

    public int online(String token, String server) throws IOException {
        return requiredOnline(success(transport.request(
                "GET",
                "irc/online?server=" + MiauAPI.encodePathSegment(
                        server == null ? "" : server
                ),
                null,
                bearer(token)
        )));
    }

    static String createClientSignature(
            String secret,
            String timestamp,
            String nonce,
            String username,
            String uuid) throws IOException {
        return createClientSignature(
                secret,
                CLIENT_PROOF_PATH,
                timestamp,
                nonce,
                username,
                uuid
        );
    }

    static String createOfflineClientSignature(
            String secret,
            String timestamp,
            String nonce,
            String username,
            String uuid) throws IOException {
        return createClientSignature(
                secret,
                CLIENT_OFFLINE_PROOF_PATH,
                timestamp,
                nonce,
                username,
                uuid
        );
    }

    private static String createClientSignature(
            String secret,
            String path,
            String timestamp,
            String nonce,
            String username,
            String uuid) throws IOException {
        String payload = CLIENT_PROOF_VERSION + "\nPOST\n" + path
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + username
                + "\n" + uuid
                + "\n" + MiauAPI.CLIENT_BRANCH;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (GeneralSecurityException exception) {
            throw new IOException("Unable to create the IRC client proof.", exception);
        }
    }

    private Map<String, String> proofHeaders(
            String path,
            String username,
            String uuid) throws IOException {
        if (!isValidClientSecret(clientSecret)) {
            throw new IOException("IRC is unavailable in this client build.");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        byte[] nonceBytes = new byte[24];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String signature = createClientSignature(
                clientSecret,
                path,
                timestamp,
                nonce,
                username,
                uuid
        );
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "MiauClient " + signature);
        headers.put("X-Miau-Timestamp", timestamp);
        headers.put("X-Miau-Nonce", nonce);
        return headers;
    }

    private static boolean isValidClientSecret(String value) {
        return value != null && !value.isEmpty() && value.length() <= 4096;
    }

    private static Session parseSession(JsonObject response) throws IOException {
        return new Session(
                requiredString(response, "token", 4096),
                requiredLong(response, "expiresAt")
        );
    }

    static String createOfflineUuid(String usernameValue) throws IOException {
        String username = normalizeUsername(usernameValue).toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (OFFLINE_ID_VERSION + "\n" + username)
                            .getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder uuid = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                int value = digest[index] & 0xff;
                uuid.append(Character.forDigit(value >>> 4, 16));
                uuid.append(Character.forDigit(value & 0x0f, 16));
            }
            return uuid.toString();
        }
        catch (GeneralSecurityException exception) {
            throw new IOException("Unable to create the offline IRC identity.", exception);
        }
    }

    static boolean isOfflineAccessToken(String value) {
        String token = value == null ? "" : value.trim();
        return token.isEmpty()
                || "0".equals(token)
                || "-".equals(token)
                || "null".equalsIgnoreCase(token)
                || "offline".equalsIgnoreCase(token);
    }

    private static IrcMessage parseMessage(JsonObject value) throws IOException {
        String id = requiredString(value, "id", 32);
        String cursor = requiredString(value, "cursor", 64);
        String username = requiredString(value, "username", 32);
        String role = requiredString(value, "role", 8);
        String content = requiredString(value, "content", 256);
        if (!MESSAGE_ID.matcher(id).matches()
                || !CURSOR.matcher(cursor).matches()
                || !("user".equals(role)
                    || "owner".equals(role)
                    || "dev".equals(role)
                    || "manager".equals(role)
                    || "helper".equals(role)
                    || "media".equals(role)
                    || "friends".equals(role))
                || containsUnsupportedCharacter(username)
                || containsUnsupportedCharacter(content)) {
            throw invalidResponse();
        }
        return new IrcMessage(
                id,
                cursor,
                username,
                role,
                content,
                requiredLong(value, "createdAt")
        );
    }

    private static IrcPresence parsePresence(JsonObject value) throws IOException {
        String uuid = requiredString(value, "uuid", 32);
        String username = requiredString(value, "username", 16);
        if (!UUID.matcher(uuid).matches()
                || !USERNAME.matcher(username).matches()
                || containsUnsupportedCharacter(username)) {
            throw invalidResponse();
        }
        return new IrcPresence(uuid, username);
    }

    private static JsonObject success(String json) throws IOException {
        if (json == null || json.length() > 2 * 1024 * 1024) {
            throw invalidResponse();
        }
        try {
            JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                throw invalidResponse();
            }
            JsonObject response = parsed.getAsJsonObject();
            if (!"success".equals(requiredString(response, "status", 32))) {
                throw invalidResponse();
            }
            return response;
        }
        catch (IOException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw invalidResponse();
        }
    }

    private static Map<String, String> bearer(String token) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + requiredValue(token, 4096));
        return headers;
    }

    private static String normalizeUsername(String value) throws IOException {
        String username = value == null ? "" : value.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IOException("The Minecraft username is invalid.");
        }
        return username;
    }

    private static String normalizeUuid(String value) throws IOException {
        String uuid = value == null ? "" : value.replace("-", "").trim().toLowerCase();
        if (!UUID.matcher(uuid).matches()) {
            throw new IOException("The Minecraft session UUID is invalid.");
        }
        return uuid;
    }

    private static int requiredOnline(JsonObject value) throws IOException {
        long online = requiredLong(value, "online");
        if (online < 0 || online > 1_000_000) {
            throw invalidResponse();
        }
        return (int) online;
    }

    private static boolean requiredBoolean(JsonObject value, String key) throws IOException {
        JsonElement element = value.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            throw invalidResponse();
        }
        return element.getAsBoolean();
    }

    private static long requiredLong(JsonObject value, String key) throws IOException {
        JsonElement element = value.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw invalidResponse();
        }
        long result;
        try {
            result = element.getAsLong();
        }
        catch (Exception exception) {
            throw invalidResponse();
        }
        if (result < 0) {
            throw invalidResponse();
        }
        return result;
    }

    private static String requiredString(
            JsonObject value,
            String key,
            int maxLength) throws IOException {
        String text = optionalString(value, key, maxLength);
        if (text == null || text.isEmpty()) {
            throw invalidResponse();
        }
        return text;
    }

    private static String optionalString(
            JsonObject value,
            String key,
            int maxLength) throws IOException {
        JsonElement element = value.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalidResponse();
        }
        String text = element.getAsString();
        if (text.length() > maxLength) {
            throw invalidResponse();
        }
        return text;
    }

    private static String requiredValue(String value, int maxLength) throws IOException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IOException("The IRC request contains an invalid value.");
        }
        return normalized;
    }

    private static boolean containsUnsupportedCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\u00a7' || Character.isISOControl(character)) {
                return true;
            }
        }
        return false;
    }

    private static IOException invalidResponse() {
        return new IOException("The IRC service returned an invalid response.");
    }

    static String resolveClientSecret() {
        String property = System.getProperty("miau.irc.clientSecret", "").trim();
        if (!property.isEmpty()) {
            return property;
        }
        String environment = System.getenv("MIAU_IRC_CLIENT_SECRET");
        if (environment != null && !environment.trim().isEmpty()) {
            return environment.trim();
        }
        String legacyEnvironment = System.getenv("MIAU_CLIENT_AUTH_SECRET");
        if (legacyEnvironment != null && !legacyEnvironment.trim().isEmpty()) {
            return legacyEnvironment.trim();
        }
        return "";
    }

    interface Transport {
        String request(
                String method,
                String path,
                String body,
                Map<String, String> headers) throws IOException;
    }

    public static final class Challenge {
        private final String challenge;
        private final String serverId;
        private final long expiresAt;

        private Challenge(String challenge, String serverId, long expiresAt) {
            this.challenge = challenge;
            this.serverId = serverId;
            this.expiresAt = expiresAt;
        }

        public String getChallenge() {
            return challenge;
        }

        public String getServerId() {
            return serverId;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    public static final class Session {
        private final String token;
        private final long expiresAt;

        private Session(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    public static final class SyncResult {
        private final List<IrcMessage> messages;
        private final String cursor;
        private final int online;
        private final List<IrcPresence> presence;

        private SyncResult(List<IrcMessage> messages, String cursor, int online, List<IrcPresence> presence) {
            this.messages = messages;
            this.cursor = cursor;
            this.online = online;
            this.presence = presence;
        }

        public List<IrcMessage> getMessages() {
            return messages;
        }

        public String getCursor() {
            return cursor;
        }

        public int getOnline() {
            return online;
        }

        public List<IrcPresence> getPresence() {
            return presence;
        }
    }
}
