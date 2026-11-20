package idle.miau.utility.irc;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import idle.miau.Miau;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.client.IRC;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Session;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class MiauIRC {
    private static final MiauIRC INSTANCE = new MiauIRC(new IrcApiClient());
    private static final long MAINTENANCE_INTERVAL_SECONDS = 20L;
    private static final long PACKET_TIMEOUT_MS = 60_000L;
    private static final long AUTH_RETRY_DELAY_MS = 10_000L;
    private static final long SESSION_REFRESH_MARGIN_MS = 60_000L;
    private static final long SERVICE_STATUS_INTERVAL_MS = 20_000L;
    private static final long MAX_RECONNECT_DELAY_MS = 30_000L;
    private static final int MAX_SEEN_MESSAGES = 512;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final IrcApiClient api;
    private final IrcWebSocketClient realtime;
    private final Object authenticationLock = new Object();
    private final Set<String> seenMessageIds = new LinkedHashSet<String>();
    private volatile ScheduledFuture<?> reconnectTask;
    private volatile ScheduledFuture<?> maintenanceTask;
    private volatile boolean running;
    private volatile boolean connecting;
    private volatile long lifecycleGeneration;
    private volatile boolean chatEnabled = true;
    private volatile boolean serviceEnabled = true;
    private volatile boolean reconnectRequested;
    private volatile boolean immediateReconnectRequested;
    private volatile int reconnectAttempt;
    private volatile int online;
    private volatile String token = "";
    private volatile String tokenUserId = "";
    private volatile String tokenUsername = "";
    private volatile long tokenExpiresAt;
    private volatile long lastAuthenticationFailureAt;
    private volatile long lastServiceStatusAt;
    private volatile String lastAuthenticationFailure = "IRC authentication failed.";
    private volatile String cursor = "";
    private volatile String observedSessionIdentity = "";

    private static final class SessionChangedException extends IOException {
        private SessionChangedException() {
            super("Minecraft session changed.");
        }
    }

    private MiauIRC(IrcApiClient api) {
        this.api = api;
        try {
            this.realtime = new IrcWebSocketClient(new RealtimeEvents());
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    public static MiauIRC getInstance() {
        return INSTANCE;
    }

    public void onMinecraftSessionChanged() {
        String currentIdentity = sessionIdentity(mc.getSession());
        synchronized (this) {
            if (!running || !serviceEnabled) {
                observedSessionIdentity = currentIdentity;
                return;
            }
            if (currentIdentity.equals(observedSessionIdentity)
                    && (connecting || realtime.isAuthenticated())) {
                return;
            }
            observedSessionIdentity = currentIdentity;
            reconnectRequested = true;
            immediateReconnectRequested = true;
            cancelReconnectTask();
        }
        clearSession();
        realtime.abort();
        synchronized (this) {
            if (!running) {
                return;
            }
            cancelReconnectTask();
            if (!connecting && !realtime.isOpen()) {
                scheduleReconnect(0L);
            }
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        lifecycleGeneration++;
        running = true;
        reconnectRequested = false;
        immediateReconnectRequested = false;
        reconnectAttempt = 0;
        serviceEnabled = true;
        lastServiceStatusAt = 0L;
        observedSessionIdentity = sessionIdentity(mc.getSession());
        maintenanceTask = Miau.getScheduledExecutor().scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        maintainConnection();
                    }
                },
                MAINTENANCE_INTERVAL_SECONDS,
                MAINTENANCE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        scheduleReconnect(0L);
    }

    public synchronized void shutdown() {
        lifecycleGeneration++;
        running = false;
        reconnectRequested = false;
        immediateReconnectRequested = false;
        connecting = false;
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
            reconnectTask = null;
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel(true);
            maintenanceTask = null;
        }
        realtime.abort();
        online = 0;
        clearSession();
    }

    public void setChatEnabled(boolean enabled) {
        chatEnabled = enabled;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public boolean handleChatInput(String input) {
        if (!running || !chatEnabled) {
            return false;
        }
        IrcCommand command = IrcCommand.parse(input);
        if (!command.isHandled()) {
            return false;
        }
        if (!serviceEnabled) {
            displaySystem("IRC is disabled globally.", true);
            return true;
        }
        if (command.getType() == IrcCommand.Type.ONLINE) {
            requestOnline();
            return true;
        }
        String content = command.getContent();
        if (content.isEmpty()) {
            displaySystem("Use # <message> to chat or #online to view online users.", true);
            return true;
        }
        if (content.length() > 256) {
            displaySystem("IRC messages cannot exceed 256 characters.", true);
            return true;
        }
        if (!realtime.sendChat(content)) {
            if (!connecting) {
                scheduleReconnect(0L);
            }
            displaySystem("IRC is reconnecting. Try again in a moment.", true);
        }
        return true;
    }

    private synchronized void scheduleReconnect(long delayMs) {
        if (!running || !serviceEnabled || connecting || realtime.isOpen()) {
            return;
        }
        if (reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }
        reconnectTask = Miau.getScheduledExecutor().schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        launchConnectionAttempt();
                    }
                },
                Math.max(0L, delayMs),
                TimeUnit.MILLISECONDS
        );
    }

    private synchronized void cancelReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void launchConnectionAttempt() {
        long attemptGeneration;
        synchronized (this) {
            reconnectTask = null;
            if (!running || !serviceEnabled || connecting || realtime.isOpen()) {
                return;
            }
            connecting = true;
            reconnectRequested = false;
            attemptGeneration = lifecycleGeneration;
        }
        Miau.getCachedExecutor().execute(new Runnable() {
            @Override
            public void run() {
                connect(attemptGeneration);
            }
        });
    }

    private void connect(long attemptGeneration) {
        try {
            if (!refreshServiceStatus(true)) {
                finishConnectionAttempt(attemptGeneration, null);
                return;
            }
            String expectedIdentity = sessionIdentity(mc.getSession());
            String sessionToken = ensureSession();
            synchronized (this) {
                if (!running || lifecycleGeneration != attemptGeneration) {
                    return;
                }
                if (!expectedIdentity.equals(sessionIdentity(mc.getSession()))) {
                    reconnectRequested = true;
                    immediateReconnectRequested = true;
                    finishConnectionAttempt(attemptGeneration, null);
                    return;
                }
                realtime.setPresenceHidden(presenceHiddenSetting());
                realtime.connect(
                        sessionToken,
                        cursor,
                        currentServer()
                ).whenComplete((ignored, error) -> finishConnectionAttempt(attemptGeneration, error));
            }
        }
        catch (Exception exception) {
            finishConnectionAttempt(attemptGeneration, exception);
        }
    }

    private void finishConnectionAttempt(long attemptGeneration, Throwable error) {
        boolean reconnect;
        boolean immediateReconnect;
        synchronized (this) {
            if (lifecycleGeneration != attemptGeneration) {
                return;
            }
            connecting = false;
            reconnect = reconnectRequested;
            immediateReconnect = immediateReconnectRequested;
            reconnectRequested = false;
            immediateReconnectRequested = false;
        }
        if (serviceEnabled && (error != null || reconnect)) {
            scheduleReconnect(immediateReconnect ? 0L : nextReconnectDelay());
        }
    }

    private void maintainConnection() {
        if (!running) {
            return;
        }
        if (!refreshServiceStatus(false)) {
            return;
        }
        if (hasMinecraftSessionChanged()) {
            onMinecraftSessionChanged();
            return;
        }
        if (!realtime.isAuthenticated()) {
            if (!connecting && !realtime.isOpen()) {
                scheduleReconnect(nextReconnectDelay());
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (tokenExpiresAt <= now + SESSION_REFRESH_MARGIN_MS
                || now - realtime.getLastPacketAt() > PACKET_TIMEOUT_MS) {
            if (tokenExpiresAt <= now + SESSION_REFRESH_MARGIN_MS) {
                clearSession();
            }
            realtime.abort();
            return;
        }
        realtime.setPresenceHidden(presenceHiddenSetting());
        if (!realtime.sendPresence(currentServer()) || !realtime.sendPing()) {
            realtime.abort();
        }
    }

    private boolean presenceHiddenSetting() {
        IRC module = (IRC) ModuleManager.getModule(IRC.class);
        return module != null && module.isPresenceHidden();
    }

    private void requestOnline() {
        if (!serviceEnabled) {
            displaySystem("IRC is disabled globally.", true);
            return;
        }
        if (!realtime.isAuthenticated()) {
            displaySystem("IRC is reconnecting.", true);
            return;
        }
        int currentOnline = online;
        displaySystem(
                currentOnline + (currentOnline == 1 ? " user is" : " users are") + " online.",
                false
        );
    }

    private String ensureSession() throws IOException, AuthenticationException {
        Session minecraftSession = mc.getSession();
        String username = minecraftSession == null ? "" : minecraftSession.getUsername();
        String uuid = minecraftSession == null
                ? ""
                : normalizeUuid(minecraftSession.getPlayerID());
        long now = System.currentTimeMillis();
        if (isSessionValid(username, uuid, now)) {
            return token;
        }
        synchronized (authenticationLock) {
            now = System.currentTimeMillis();
            if (isSessionValid(username, uuid, now)) {
                return token;
            }
            if (now - lastAuthenticationFailureAt < AUTH_RETRY_DELAY_MS) {
                throw new IOException(lastAuthenticationFailure);
            }
            try {
                IrcApiClient.Session ircSession = authenticate(
                        minecraftSession,
                        username,
                        uuid
                );
                String authenticatedIdentity = sessionIdentity(minecraftSession);
                synchronized (this) {
                    if (!authenticatedIdentity.equals(sessionIdentity(mc.getSession()))) {
                        throw new SessionChangedException();
                    }
                    token = ircSession.getToken();
                    tokenExpiresAt = ircSession.getExpiresAt();
                    tokenUsername = username;
                    tokenUserId = uuid;
                    lastAuthenticationFailureAt = 0L;
                    lastAuthenticationFailure = "IRC authentication failed.";
                    observedSessionIdentity = authenticatedIdentity;
                }
                return token;
            }
            catch (SessionChangedException exception) {
                throw exception;
            }
            catch (IOException exception) {
                rememberAuthenticationFailure(exception);
                throw exception;
            }
            catch (AuthenticationException exception) {
                rememberAuthenticationFailure(exception);
                throw exception;
            }
        }
    }

    private IrcApiClient.Session authenticate(
            Session minecraftSession,
            String username,
            String uuid) throws IOException, AuthenticationException {
        if (minecraftSession == null
                || IrcApiClient.isOfflineAccessToken(minecraftSession.getToken())) {
            return api.requestOfflineSession(username);
        }
        IrcApiClient.Challenge challenge = api.requestChallenge(username, uuid);
        try {
            mc.getSessionService().joinServer(
                    minecraftSession.getProfile(),
                    minecraftSession.getToken(),
                    challenge.getServerId()
            );
        }
        catch (InvalidCredentialsException exception) {
            return api.requestOfflineSession(username);
        }
        return api.exchangeChallenge(challenge.getChallenge());
    }

    private boolean isSessionValid(String username, String uuid, long now) {
        return !token.isEmpty()
                && tokenExpiresAt > now + SESSION_REFRESH_MARGIN_MS
                && tokenUsername.equals(username)
                && tokenUserId.equals(uuid);
    }

    private void rememberAuthenticationFailure(Exception exception) {
        lastAuthenticationFailureAt = System.currentTimeMillis();
        String message = exception.getMessage();
        lastAuthenticationFailure = message == null || message.trim().isEmpty()
                ? "IRC authentication failed."
                : message.trim();
    }

    private synchronized long nextReconnectDelay() {
        int exponent = Math.min(reconnectAttempt, 5);
        reconnectAttempt = Math.min(reconnectAttempt + 1, 30);
        long base = Math.min(MAX_RECONNECT_DELAY_MS, 1_000L << exponent);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, base / 4L));
        return Math.min(MAX_RECONNECT_DELAY_MS, base + jitter);
    }

    private synchronized boolean remember(String messageId) {
        if (!seenMessageIds.add(messageId)) {
            return false;
        }
        while (seenMessageIds.size() > MAX_SEEN_MESSAGES) {
            Iterator<String> iterator = seenMessageIds.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    private void displayMessage(IrcMessage message) {
        displayRaw(IrcMessageFormatter.format(message));
    }

    private void displaySystem(String message, boolean error) {
        displayRaw("\u00a77[\u00a7dIRC\u00a77] " + (error ? "\u00a7c" : "\u00a7f") + message);
    }

    private void displayRaw(String message) {
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (Utils.nullCheck()) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(message));
                }
            }
        });
    }

    private String currentServer() {
        ServerData server = mc.getCurrentServerData();
        if (server != null && server.serverIP != null && !server.serverIP.trim().isEmpty()) {
            return server.serverIP.trim();
        }
        return mc.isSingleplayer() ? "Singleplayer" : "Main Menu";
    }

    private static String normalizeUuid(String value) {
        return value == null ? "" : value.replace("-", "").trim().toLowerCase();
    }

    private boolean hasMinecraftSessionChanged() {
        return !observedSessionIdentity.equals(sessionIdentity(mc.getSession()));
    }

    static String sessionIdentity(Session session) {
        if (session == null) {
            return "";
        }
        return safe(session.getUsername())
                + "\u0000"
                + normalizeUuid(session.getPlayerID())
                + "\u0000"
                + safe(session.getToken());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private synchronized void clearSession() {
        token = "";
        tokenUserId = "";
        tokenUsername = "";
        tokenExpiresAt = 0L;
        MiauIngamePresence.clear();
    }

    private boolean refreshServiceStatus(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastServiceStatusAt < SERVICE_STATUS_INTERVAL_MS) {
            return serviceEnabled;
        }
        try {
            boolean enabled = api.isEnabled();
            lastServiceStatusAt = now;
            if (!enabled) {
                disableService();
                return false;
            }
            boolean wasDisabled = !serviceEnabled;
            serviceEnabled = true;
            if (wasDisabled) {
                reconnectAttempt = 0;
                lastAuthenticationFailureAt = 0L;
                synchronized (this) {
                    if (running && !connecting && !realtime.isOpen()) {
                        scheduleReconnect(0L);
                    }
                }
            }
            return true;
        }
        catch (IOException exception) {
            lastServiceStatusAt = now;
            return serviceEnabled;
        }
    }

    private void disableService() {
        boolean notify;
        synchronized (this) {
            notify = serviceEnabled;
            serviceEnabled = false;
            reconnectRequested = false;
            immediateReconnectRequested = false;
            cancelReconnectTask();
        }
        clearSession();
        online = 0;
        MiauIngamePresence.clear();
        realtime.abort();
        if (notify && running) {
            displaySystem("IRC is disabled globally.", true);
        }
    }

    private final class RealtimeEvents implements IrcWebSocketClient.Events {
        @Override
        public void onAuthenticated(String username, String resumeCursor) {
            if (!running || !serviceEnabled) {
                return;
            }
            reconnectAttempt = 0;
            if (!resumeCursor.isEmpty()) {
                cursor = resumeCursor;
            }
        }

        @Override
        public void onAuthenticationFailed(String reason) {
            if (!running) {
                return;
            }
            clearSession();
            lastAuthenticationFailureAt = System.currentTimeMillis();
            lastAuthenticationFailure = "IRC authentication failed: " + reason;
        }

        @Override
        public void onMessage(IrcMessage message) {
            if (!running || !serviceEnabled) {
                return;
            }
            if (!message.getCursor().isEmpty()
                    && (cursor.isEmpty() || message.getCursor().compareTo(cursor) > 0)) {
                cursor = message.getCursor();
            }
            if (chatEnabled && remember(message.getId())) {
                displayMessage(message);
            }
        }

        @Override
        public void onPresence(List<IrcPresence> players, int onlineUsers) {
            if (!running || !serviceEnabled) {
                return;
            }
            online = onlineUsers;
            MiauIngamePresence.update(players);
        }

        @Override
        public void onServiceError(String message) {
            if (running && serviceEnabled && chatEnabled && Utils.nullCheck()) {
                displaySystem(message, true);
            }
        }

        @Override
        public void onDisconnected() {
            online = 0;
            MiauIngamePresence.clear();
            synchronized (MiauIRC.this) {
                if (!serviceEnabled) {
                    reconnectRequested = false;
                    immediateReconnectRequested = false;
                    return;
                }
                if (connecting) {
                    reconnectRequested = true;
                    return;
                }
            }
            scheduleReconnect(nextReconnectDelay());
        }
    }
}
