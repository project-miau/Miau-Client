package idle.miau.utility.spotify;

import idle.miau.utility.spotify.http.*;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class SpotifyAPI implements MusicProvider {
    private final MusicCredentialsStore store;
    private final SpotifyOAuthClient oauth;
    private final SpotifyPlaybackClient playback;
    private final SpotifyCallbackServer callback = new SpotifyCallbackServer();
    private final AtomicReference<MusicTrack> snapshot = new AtomicReference<>(MusicTrack.STOPPED);
    private final AtomicReference<ProviderState> state =
            new AtomicReference<>(ProviderState.DISCONNECTED);
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor scheduler = createScheduler();
    private final Random jitter = new Random();
    private volatile SpotifyTokenSet tokens;
    private volatile boolean running, userDisconnected;
    private volatile ScheduledFuture<?> pollTask;
    private final AtomicLong generation = new AtomicLong();
    private long backoff = 2000L;

    private static ScheduledThreadPoolExecutor createScheduler() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(
                        1,
                        r -> {
                            Thread t = new Thread(r, "Miau-Spotify");
                            t.setDaemon(true);
                            return t;
                        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(5, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public SpotifyAPI(String clientId, MusicCredentialsStore store) {
        this(clientId, store, new UrlConnectionTransport());
    }

    SpotifyAPI(String clientId, MusicCredentialsStore store, HttpTransport http) {
        this.store = store;
        this.oauth = new SpotifyOAuthClient(clientId, http);
        this.playback = new SpotifyPlaybackClient(http);
        this.tokens = store.loadSpotifyTokens();
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        userDisconnected = false;
        long g = generation.incrementAndGet();
        if (!tokens.hasAccessToken() && !tokens.hasRefreshToken()) connect();
        else schedule(0, g);
    }

    @Override
    public synchronized void stop() {
        running = false;
        generation.incrementAndGet();
        if (pollTask != null) pollTask.cancel(true);
        pollTask = null;
        callback.stop();
        snapshot.set(MusicTrack.STOPPED);
        state.set(ProviderState.DISCONNECTED);
    }

    public synchronized void disconnect() {
        userDisconnected = true;
        stop();
    }

    public boolean wasUserDisconnected() {
        return userDisconnected;
    }

    public synchronized void connect() {
        if (!running) {
            running = true;
            generation.incrementAndGet();
        }
        long g = generation.get();
        try {
            state.set(
                    new ProviderState(
                            MusicProviderStatus.AUTHORIZING,
                            "Opening Spotify authorization",
                            "Spotify",
                            0));
            String authUrl = oauth.createAuthorizationUrl(callback.start());
            state.set(
                    new ProviderState(
                            MusicProviderStatus.WAITING_FOR_CALLBACK,
                            "Waiting for browser authorization",
                            "Spotify",
                            0));
            callback.getFuture()
                    .whenComplete(
                            (result, error) ->
                                    scheduler.execute(() -> handleCallback(g, result, error)));
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(authUrl));
            else throw new IOException("Desktop browser integration is unavailable");
        } catch (Exception e) {
            state.set(
                    new ProviderState(MusicProviderStatus.CONNECTION_ERROR, safe(e), "Spotify", 0));
        }
    }

    public synchronized void reconnect() {
        try {
            store.clearSpotifyTokens();
        } catch (IOException ignored) {
        }
        tokens = SpotifyTokenSet.EMPTY;
        callback.stop();
        connect();
    }

    public synchronized void clearAuthorization() {
        try {
            store.clearSpotifyTokens();
        } catch (IOException ignored) {
        }
        tokens = SpotifyTokenSet.EMPTY;
        disconnect();
    }

    private void handleCallback(long g, SpotifyCallbackServer.Result result, Throwable error) {
        if (!valid(g)) return;
        if (error != null || result == null) {
            state.set(
                    new ProviderState(
                            MusicProviderStatus.AUTH_TIMEOUT,
                            "Authorization timed out",
                            "Spotify",
                            0));
            return;
        }
        if (!result.getError().isEmpty()) {
            MusicProviderStatus s =
                    result.getError().toLowerCase().contains("denied")
                            ? MusicProviderStatus.AUTH_DENIED
                            : MusicProviderStatus.CONNECTION_ERROR;
            state.set(new ProviderState(s, result.getError(), "Spotify", 0));
            return;
        }
        try {
            state.set(
                    new ProviderState(
                            MusicProviderStatus.EXCHANGING_TOKEN,
                            "Completing authorization",
                            "Spotify",
                            0));
            tokens = oauth.exchange(result.getCode());
            store.saveSpotifyTokens(tokens);
            schedule(0, g);
        } catch (Exception e) {
            state.set(
                    new ProviderState(
                            MusicProviderStatus.AUTH_EXPIRED,
                            "Token exchange failed",
                            "Spotify",
                            0));
        }
    }

    private synchronized void schedule(long delay, long g) {
        if (!valid(g)) return;
        if (pollTask != null) pollTask.cancel(false);
        pollTask = scheduler.schedule(() -> poll(g), Math.max(0, delay), TimeUnit.MILLISECONDS);
    }

    private void poll(long g) {
        if (!valid(g)) return;
        try {
            ensureToken();
            HttpResponse r = playback.current(tokens.getAccessToken());
            if (r.getStatus() == 401) {
                refresh();
                schedule(0, g);
                return;
            }
            if (r.getStatus() == 429) {
                long delay = r.retryAfterMillis(15000);
                state.set(
                        new ProviderState(
                                MusicProviderStatus.RATE_LIMITED,
                                "Spotify rate limit",
                                "Spotify",
                                System.currentTimeMillis() + delay));
                schedule(delay, g);
                return;
            }
            if (r.getStatus() == 204) {
                snapshot.set(MusicTrack.STOPPED);
                state.set(
                        new ProviderState(
                                MusicProviderStatus.PAUSED, "Nothing is playing", "Spotify", 0));
                backoff = 2000;
                schedule(5000, g);
                return;
            }
            if (r.getStatus() != 200) throw new IOException("Playback HTTP " + r.getStatus());
            MusicTrack t = playback.parse(r);
            snapshot.set(t);
            state.set(
                    new ProviderState(
                            t.isPlaying()
                                    ? MusicProviderStatus.CONNECTED
                                    : MusicProviderStatus.PAUSED,
                            t.isPlaying() ? "Playing" : "Paused",
                            "Spotify",
                            0));
            backoff = 2000;
            schedule(t.isPlaying() ? 2000 : 5000, g);
        } catch (Exception e) {
            snapshot.set(MusicTrack.STOPPED);
            long delay = Math.min(30000, backoff) + (jitter.nextInt(501));
            backoff = Math.min(30000, backoff * 2);
            state.set(
                    new ProviderState(
                            MusicProviderStatus.CONNECTION_ERROR,
                            safe(e),
                            "Spotify",
                            System.currentTimeMillis() + delay));
            schedule(delay, g);
        }
    }

    private void ensureToken() throws Exception {
        if (tokens.shouldRefresh(System.currentTimeMillis())) refresh();
        if (!tokens.hasAccessToken()) throw new IOException("Spotify authorization required");
    }

    private void refresh() throws Exception {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            tokens = oauth.refresh(tokens.getRefreshToken());
            store.saveSpotifyTokens(tokens);
        } finally {
            refreshing.set(false);
        }
    }

    public void pause() {
        control(0, 0);
    }

    public void resume() {
        control(1, 0);
    }

    public void next() {
        control(2, 0);
    }

    public void previous() {
        control(3, 0);
    }

    public void volume(int value) {
        control(4, value);
    }

    private void control(int type, int value) {
        scheduler.execute(
                () -> {
                    try {
                        ensureToken();
                        HttpResponse r =
                                type == 0
                                        ? playback.pause(tokens.getAccessToken())
                                        : type == 1
                                                ? playback.resume(tokens.getAccessToken())
                                                : type == 2
                                                        ? playback.next(tokens.getAccessToken())
                                                        : type == 3
                                                                ? playback.previous(
                                                                        tokens.getAccessToken())
                                                                : playback.volume(
                                                                        tokens.getAccessToken(),
                                                                        value);
                        if (r.getStatus() == 403)
                            state.set(
                                    new ProviderState(
                                            MusicProviderStatus.AUTH_DENIED,
                                            "Spotify Premium/control permission required",
                                            "Spotify",
                                            0));
                        else if (r.getStatus() == 404)
                            state.set(
                                    new ProviderState(
                                            MusicProviderStatus.NO_ACTIVE_DEVICE,
                                            "No active Spotify device",
                                            "Spotify",
                                            0));
                        else schedule(300, generation.get());
                    } catch (Exception e) {
                        state.set(
                                new ProviderState(
                                        MusicProviderStatus.CONNECTION_ERROR,
                                        safe(e),
                                        "Spotify",
                                        0));
                    }
                });
    }

    private boolean valid(long g) {
        return running && generation.get() == g;
    }

    private static String safe(Throwable e) {
        String m = e == null ? "Connection error" : e.getMessage();
        return m == null || m.length() > 160 ? "Connection error" : m;
    }

    @Override
    public MusicTrack getSnapshot() {
        return snapshot.get();
    }

    @Override
    public ProviderState getProviderState() {
        return state.get();
    }

    @Override
    public String getProviderName() {
        return "Spotify";
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
