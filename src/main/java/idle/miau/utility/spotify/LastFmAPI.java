package idle.miau.utility.spotify;

import com.google.gson.*;

import idle.miau.utility.spotify.http.*;

import java.net.URLEncoder;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LastFmAPI implements MusicProvider {
    private final String user, apiKey;
    private final HttpTransport http;
    private final AtomicReference<MusicTrack> snapshot = new AtomicReference<>(MusicTrack.STOPPED);
    private final AtomicReference<ProviderState> state =
            new AtomicReference<>(ProviderState.DISCONNECTED);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "Miau-LastFm");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile boolean running;
    private ScheduledFuture<?> task;
    private long backoff = 3000;
    private final Random random = new Random();

    public LastFmAPI(String user, String apiKey) {
        this(user, apiKey, new UrlConnectionTransport());
    }

    LastFmAPI(String user, String apiKey, HttpTransport http) {
        this.user = user;
        this.apiKey = apiKey;
        this.http = http;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        state.set(new ProviderState(MusicProviderStatus.CONNECTING, "Connecting", "Last.fm", 0));
        schedule(0);
    }

    public synchronized void stop() {
        running = false;
        if (task != null) task.cancel(true);
        task = null;
        snapshot.set(MusicTrack.STOPPED);
        state.set(ProviderState.DISCONNECTED);
    }

    private synchronized void schedule(long d) {
        if (!running) return;
        if (task != null) task.cancel(false);
        task = scheduler.schedule(this::poll, Math.max(0, d), TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (!running) return;
        try {
            String url =
                    "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user="
                            + enc(user)
                            + "&api_key="
                            + enc(apiKey)
                            + "&format=json&limit=1";
            HttpResponse r = http.execute(HttpRequest.builder("GET", url).build());
            if (r.getStatus() != 200)
                throw new java.io.IOException("Last.fm HTTP " + r.getStatus());
            JsonObject root = new JsonParser().parse(r.getBody()).getAsJsonObject();
            JsonArray tracks =
                    root.has("recenttracks")
                            ? root.getAsJsonObject("recenttracks").getAsJsonArray("track")
                            : null;
            if (tracks == null || tracks.size() == 0) {
                snapshot.set(MusicTrack.STOPPED);
                state.set(
                        new ProviderState(MusicProviderStatus.PAUSED, "No playback", "Last.fm", 0));
                schedule(5000);
                return;
            }
            JsonObject t = tracks.get(0).getAsJsonObject();
            boolean playing =
                    t.has("@attr")
                            && "true"
                                    .equalsIgnoreCase(
                                            str(t.getAsJsonObject("@attr"), "nowplaying"));
            if (!playing) {
                snapshot.set(MusicTrack.STOPPED);
                state.set(new ProviderState(MusicProviderStatus.PAUSED, "Paused", "Last.fm", 0));
                schedule(5000);
                return;
            }
            String artist = t.has("artist") ? str(t.getAsJsonObject("artist"), "#text") : "",
                    id = !str(t, "url").isEmpty() ? str(t, "url") : str(t, "mbid");
            snapshot.set(new MusicTrack(true, str(t, "name"), artist, image(t), id));
            state.set(new ProviderState(MusicProviderStatus.CONNECTED, "Playing", "Last.fm", 0));
            backoff = 3000;
            schedule(3000);
        } catch (Exception e) {
            long d = Math.min(30000, backoff) + random.nextInt(501);
            backoff = Math.min(30000, backoff * 2);
            state.set(
                    new ProviderState(
                            MusicProviderStatus.CONNECTION_ERROR,
                            "Last.fm connection error",
                            "Last.fm",
                            System.currentTimeMillis() + d));
            schedule(d);
        }
    }

    private static String image(JsonObject t) {
        if (!t.has("image")) return "";
        String result = "";
        for (JsonElement e : t.getAsJsonArray("image")) {
            String u = str(e.getAsJsonObject(), "#text");
            if (!u.isEmpty()) result = u;
        }
        return result;
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    public MusicTrack getSnapshot() {
        return snapshot.get();
    }

    public ProviderState getProviderState() {
        return state.get();
    }

    public String getProviderName() {
        return "Last.fm";
    }

    public boolean isRunning() {
        return running;
    }
}
