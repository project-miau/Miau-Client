package idle.miau.utility.irc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import idle.miau.BuildVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

final class IrcWebSocketClient {
    private static final Pattern MESSAGE_ID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern CURSOR = Pattern.compile("^\\d{13}-[a-f0-9]{32}$");
    private static final Pattern UUID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MAX_PACKET_LENGTH = 16 * 1024;
    private static final int MAX_PRESENCE = 500;
    private final Object lock = new Object();
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Events events;
    private WebSocket webSocket;
    private long generation;
    private boolean authenticated;
    private volatile long lastPacketAt;
    private volatile boolean presenceHidden;
    private volatile boolean hiddenSupported = true;
    private volatile boolean lastAuthHadHidden;

    IrcWebSocketClient(Events events) throws IOException {
        this(
                resolveEndpoint(),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8L))
                        .build(),
                events
        );
    }

    IrcWebSocketClient(URI endpoint, HttpClient httpClient, Events events) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.events = events;
    }

    CompletableFuture<Void> connect(
            String token,
            String after,
            String server) throws IOException {
        String sessionToken = requiredText(token, 4096);
        String cursor = after == null ? "" : after.trim().toLowerCase(Locale.ROOT);
        if (!cursor.isEmpty() && !CURSOR.matcher(cursor).matches()) {
            throw new IOException("The IRC cursor is invalid.");
        }
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "auth");
        packet.addProperty("token", sessionToken);
        packet.addProperty("after", cursor);
        packet.addProperty("server", normalizeServer(server));
        lastAuthHadHidden = presenceHidden && hiddenSupported;
        if (lastAuthHadHidden) {
            packet.addProperty("hidden", true);
        }
        WebSocket previous;
        long connectionGeneration;
        synchronized (lock) {
            previous = webSocket;
            webSocket = null;
            authenticated = false;
            connectionGeneration = ++generation;
        }
        if (previous != null) {
            previous.abort();
        }
        SocketListener listener = new SocketListener(
                connectionGeneration,
                packet.toString()
        );
        return httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8L))
                .buildAsync(endpoint, listener)
                .thenApply(ignored -> null);
    }

    boolean sendChat(String message) {
        return sendAuthenticated(createChatPacket(message));
    }

    boolean sendPresence(String server) {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "presence");
        packet.addProperty("server", normalizeServer(server));
        if (presenceHidden && hiddenSupported) {
            packet.addProperty("hidden", true);
        }
        return sendAuthenticated(packet);
    }

    void setPresenceHidden(boolean hidden) {
        presenceHidden = hidden;
    }

    boolean sendPing() {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "ping");
        return sendAuthenticated(packet);
    }

    boolean isAuthenticated() {
        synchronized (lock) {
            return authenticated && webSocket != null;
        }
    }

    boolean isOpen() {
        synchronized (lock) {
            return webSocket != null;
        }
    }

    long getLastPacketAt() {
        return lastPacketAt;
    }

    void abort() {
        WebSocket target;
        boolean notify;
        synchronized (lock) {
            target = webSocket;
            notify = target != null;
            webSocket = null;
            authenticated = false;
            generation += 1L;
        }
        if (target != null) {
            target.abort();
        }
        if (notify) {
            events.onDisconnected();
        }
    }

    private boolean sendAuthenticated(JsonObject packet) {
        WebSocket target;
        long connectionGeneration;
        synchronized (lock) {
            if (!authenticated || webSocket == null) {
                return false;
            }
            target = webSocket;
            connectionGeneration = generation;
        }
        send(connectionGeneration, target, packet.toString());
        return true;
    }

    private void send(long connectionGeneration, WebSocket target, String packet) {
        target.sendText(packet, true).whenComplete((ignored, error) -> {
            if (error != null) {
                disconnect(connectionGeneration, target);
            }
        });
    }

    private boolean attach(long connectionGeneration, WebSocket target) {
        synchronized (lock) {
            if (generation != connectionGeneration || webSocket != null) {
                return false;
            }
            webSocket = target;
            long now = System.currentTimeMillis();
            lastPacketAt = now;
            return true;
        }
    }

    private boolean authenticate(long connectionGeneration, WebSocket target) {
        synchronized (lock) {
            if (generation != connectionGeneration || webSocket != target) {
                return false;
            }
            authenticated = true;
            long now = System.currentTimeMillis();
            lastPacketAt = now;
            return true;
        }
    }

    private boolean isCurrent(long connectionGeneration, WebSocket target) {
        synchronized (lock) {
            return generation == connectionGeneration && webSocket == target;
        }
    }

    private void disconnect(long connectionGeneration, WebSocket target) {
        boolean notify = false;
        synchronized (lock) {
            if (generation == connectionGeneration && webSocket == target) {
                webSocket = null;
                authenticated = false;
                generation += 1L;
                notify = true;
            }
        }
        if (notify) {
            target.abort();
            events.onDisconnected();
        }
    }

    private void handlePacket(
            long connectionGeneration,
            WebSocket target,
            String raw) throws IOException {
        if (!isCurrent(connectionGeneration, target)) {
            return;
        }
        lastPacketAt = System.currentTimeMillis();
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(raw);
        }
        catch (Exception exception) {
            throw invalidPacket();
        }
        if (!parsed.isJsonObject()) {
            throw invalidPacket();
        }
        JsonObject packet = parsed.getAsJsonObject();
        String type = requiredString(packet, "type", 32);
        if ("auth_success".equals(type)) {
            String username = requiredString(packet, "username", 32);
            String cursor = optionalString(packet, "cursor", 64);
            if (cursor == null) cursor = "";
            if (!cursor.isEmpty() && !CURSOR.matcher(cursor).matches()) {
                throw invalidPacket();
            }
            if (authenticate(connectionGeneration, target)) {
                events.onAuthenticated(username, cursor);
            }
            return;
        }
        if ("auth_failed".equals(type)) {
            String reason = requiredString(packet, "reason", 64);
            if (lastAuthHadHidden && "unexpected_field".equals(reason)) {
                hiddenSupported = false;
            }
            events.onAuthenticationFailed(reason);
            disconnect(connectionGeneration, target);
            return;
        }
        if ("chat".equals(type)) {
            events.onMessage(parseMessage(packet));
            return;
        }
        if ("presence".equals(type)) {
            PresenceUpdate update = parsePresence(packet);
            events.onPresence(update.players, update.online);
            return;
        }
        if ("error".equals(type)) {
            events.onServiceError(requiredString(packet, "message", 256));
            return;
        }
        if ("pong".equals(type)) {
            return;
        }
        throw invalidPacket();
    }

    static IrcMessage parseMessage(JsonObject packet) throws IOException {
        String id = requiredString(packet, "id", 32);
        String cursor = requiredString(packet, "cursor", 64);
        String source = requiredString(packet, "source", 16);
        String username = requiredString(packet, "username", 32);
        String role = optionalString(packet, "role", 16);
        String message = requiredString(packet, "message", 256);
        long timestamp = requiredLong(packet, "timestamp");
        if (!MESSAGE_ID.matcher(id).matches()
                || !CURSOR.matcher(cursor).matches()
                || !("client".equals(source)
                    || "dashboard".equals(source)
                    || "discord".equals(source))
                || !isRole(role)
                || containsUnsupportedCharacter(username)
                || containsUnsupportedCharacter(message)) {
            throw invalidPacket();
        }
        return new IrcMessage(
                id,
                cursor,
                username,
                role,
                source,
                message,
                timestamp
        );
    }

    static JsonObject createChatPacket(String message) {
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "chat");
        packet.addProperty("message", message);
        return packet;
    }

    static PresenceUpdate parsePresence(JsonObject packet) throws IOException {
        long onlineValue = requiredLong(packet, "online");
        JsonElement playersElement = packet.get("players");
        if (onlineValue > 1_000_000L
                || playersElement == null
                || !playersElement.isJsonArray()) {
            throw invalidPacket();
        }
        JsonArray values = playersElement.getAsJsonArray();
        if (values.size() > MAX_PRESENCE) {
            throw invalidPacket();
        }
        List<IrcPresence> players = new ArrayList<IrcPresence>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) {
                throw invalidPacket();
            }
            JsonObject player = element.getAsJsonObject();
            String uuid = requiredString(player, "uuid", 32);
            String username = requiredString(player, "username", 16);
            if (!UUID.matcher(uuid).matches()
                    || !MINECRAFT_USERNAME.matcher(username).matches()
                    || containsUnsupportedCharacter(username)) {
                throw invalidPacket();
            }
            players.add(new IrcPresence(uuid, username));
        }
        return new PresenceUpdate(
                Collections.unmodifiableList(players),
                (int) onlineValue
        );
    }

    static URI resolveEndpoint() throws IOException {
        String configured = System.getProperty("miau.irc.websocketUrl", "").trim();
        if (configured.isEmpty()) {
            String environment = System.getenv("MIAU_IRC_WS_URL");
            if (environment != null) configured = environment.trim();
        }
        if (configured.isEmpty()) configured = BuildVersion.IRC_WEBSOCKET_URL;
        try {
            URI uri = URI.create(configured);
            String scheme = uri.getScheme();
            if (!("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !"/ws".equals(uri.getPath())) {
                throw invalidEndpoint();
            }
            return uri;
        }
        catch (IllegalArgumentException exception) {
            throw invalidEndpoint();
        }
    }

    private static String normalizeServer(String value) {
        String server = value == null ? "" : value.trim();
        if (server.length() > 128) server = server.substring(0, 128);
        StringBuilder clean = new StringBuilder(server.length());
        for (int index = 0; index < server.length(); index++) {
            char character = server.charAt(index);
            if (character != '\u00a7' && !Character.isISOControl(character)) {
                clean.append(character);
            }
        }
        return clean.length() == 0 ? "Main Menu" : clean.toString();
    }

    private static String requiredText(String value, int maxLength) throws IOException {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IOException("The IRC request contains an invalid value.");
        }
        return text;
    }

    private static String requiredString(
            JsonObject packet,
            String key,
            int maxLength) throws IOException {
        String value = optionalString(packet, key, maxLength);
        if (value == null || value.isEmpty()) {
            throw invalidPacket();
        }
        return value;
    }

    private static String optionalString(
            JsonObject packet,
            String key,
            int maxLength) throws IOException {
        JsonElement element = packet.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalidPacket();
        }
        String value = element.getAsString();
        if (value.length() > maxLength) {
            throw invalidPacket();
        }
        return value;
    }

    private static long requiredLong(JsonObject packet, String key) throws IOException {
        JsonElement element = packet.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw invalidPacket();
        }
        try {
            String raw = element.getAsString();
            if (!raw.matches("0|[1-9][0-9]*")) {
                throw invalidPacket();
            }
            long value = Long.parseLong(raw);
            if (value < 0L) throw invalidPacket();
            return value;
        }
        catch (NumberFormatException exception) {
            throw invalidPacket();
        }
    }

    private static boolean isRole(String role) {
        return role == null
                || "owner".equals(role)
                || "dev".equals(role)
                || "manager".equals(role)
                || "helper".equals(role)
                || "media".equals(role)
                || "friends".equals(role);
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

    private static IOException invalidPacket() {
        return new IOException("The IRC service returned an invalid packet.");
    }

    private static IOException invalidEndpoint() {
        return new IOException("The IRC WebSocket endpoint is invalid.");
    }

    interface Events {
        void onAuthenticated(String username, String cursor);

        void onAuthenticationFailed(String reason);

        void onMessage(IrcMessage message);

        void onPresence(List<IrcPresence> players, int online);

        void onServiceError(String message);

        void onDisconnected();
    }

    static final class PresenceUpdate {
        private final List<IrcPresence> players;
        private final int online;

        private PresenceUpdate(List<IrcPresence> players, int online) {
            this.players = players;
            this.online = online;
        }

        List<IrcPresence> getPlayers() {
            return players;
        }

        int getOnline() {
            return online;
        }
    }

    private final class SocketListener implements WebSocket.Listener {
        private final long connectionGeneration;
        private final StringBuilder frame = new StringBuilder();
        private String authPacket;

        private SocketListener(long connectionGeneration, String authPacket) {
            this.connectionGeneration = connectionGeneration;
            this.authPacket = authPacket;
        }

        @Override
        public void onOpen(WebSocket target) {
            if (!attach(connectionGeneration, target)) {
                target.abort();
                return;
            }
            String packet = authPacket;
            authPacket = "";
            send(connectionGeneration, target, packet);
            target.request(1L);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket target,
                CharSequence data,
                boolean last) {
            frame.append(data);
            if (frame.length() > MAX_PACKET_LENGTH) {
                events.onServiceError("The IRC service returned an invalid packet.");
                disconnect(connectionGeneration, target);
                return null;
            }
            if (last) {
                String packet = frame.toString();
                frame.setLength(0);
                try {
                    handlePacket(connectionGeneration, target, packet);
                }
                catch (IOException exception) {
                    events.onServiceError(exception.getMessage());
                    disconnect(connectionGeneration, target);
                    return null;
                }
            }
            target.request(1L);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket target,
                int statusCode,
                String reason) {
            disconnect(connectionGeneration, target);
            return null;
        }

        @Override
        public void onError(WebSocket target, Throwable error) {
            disconnect(connectionGeneration, target);
        }
    }
}
