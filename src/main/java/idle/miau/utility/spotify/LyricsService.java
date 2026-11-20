package idle.miau.utility.spotify;

import com.google.gson.*;

import idle.miau.utility.api.MiauAPI;
import idle.miau.utility.spotify.http.*;

import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class LyricsService {
    public static final class Line {
        private final long timeMs;
        private final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        public long getTimeMs() {
            return timeMs;
        }

        public String getText() {
            return text;
        }
    }

    public static final class Snapshot {
        public static final Snapshot EMPTY =
                new Snapshot("", Collections.<Line>emptyList(), false, false);
        private final String trackId;
        private final List<Line> lines;
        private final boolean instrumental, loading;

        Snapshot(String trackId, List<Line> lines, boolean instrumental, boolean loading) {
            this.trackId = trackId;
            this.lines = Collections.unmodifiableList(lines);
            this.instrumental = instrumental;
            this.loading = loading;
        }

        public String getTrackId() {
            return trackId;
        }

        public List<Line> getLines() {
            return lines;
        }

        public boolean isInstrumental() {
            return instrumental;
        }

        public boolean isLoading() {
            return loading;
        }

        public int activeIndex(long progress) {
            int result = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs > progress) break;
                result = i;
            }
            return result;
        }
    }

    private final HttpTransport http = new UrlConnectionTransport();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "Miau-Lyrics");
                        t.setDaemon(true);
                        return t;
                    });
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);
    private final AtomicLong generation = new AtomicLong();
    private volatile String requested = "";

    public void request(MusicTrack track) {
        if (track == null || track.getTrackId().isEmpty() || track.getTrackId().equals(requested))
            return;
        requested = track.getTrackId();
        long g = generation.incrementAndGet();
        snapshot.set(new Snapshot(requested, Collections.<Line>emptyList(), false, true));
        executor.execute(() -> load(track, g));
    }

    public Snapshot getSnapshot() {
        return snapshot.get();
    }

    public void clear() {
        requested = "";
        generation.incrementAndGet();
        snapshot.set(Snapshot.EMPTY);
    }

    private void load(MusicTrack t, long g) {
        try {
            String url =
                    MiauAPI.url("api/spotify/lyrics")
                            + "?track="
                            + enc(t.getTrackName())
                            + "&artist="
                            + enc(t.getArtistName())
                            + "&album="
                            + enc(t.getAlbumName())
                            + "&duration="
                            + (t.getDurationMs() / 1000);
            HttpResponse r =
                    http.execute(HttpRequest.builder("GET", url).timeouts(5000, 7000).build());
            if (r.getStatus() != 200) throw new java.io.IOException("Lyrics HTTP " + r.getStatus());
            JsonObject root = new JsonParser().parse(r.getBody()).getAsJsonObject();
            boolean instrumental =
                    root.has("instrumental") && root.get("instrumental").getAsBoolean();
            String synced = str(root, "syncedLyrics"), plain = str(root, "plainLyrics");
            List<Line> lines = !synced.isEmpty() ? parseSynced(synced) : parsePlain(plain);
            if (generation.get() == g)
                snapshot.set(new Snapshot(t.getTrackId(), lines, instrumental, false));
        } catch (Exception ignored) {
            if (generation.get() == g)
                snapshot.set(
                        new Snapshot(t.getTrackId(), Collections.<Line>emptyList(), false, false));
        }
    }

    private static List<Line> parseSynced(String value) {
        List<Line> out = new ArrayList<>();
        for (String row : value.split("\\r?\\n")) {
            int close = row.indexOf(']');
            if (row.startsWith("[") && close > 1) {
                String[] time = row.substring(1, close).split(":", 2);
                try {
                    long ms =
                            (Long.parseLong(time[0]) * 60_000L)
                                    + (long) (Double.parseDouble(time[1]) * 1000);
                    String text = row.substring(close + 1).trim();
                    if (!text.isEmpty()) out.add(new Line(ms, text));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private static List<Line> parsePlain(String value) {
        List<Line> out = new ArrayList<>();
        for (String row : value.split("\\r?\\n")) {
            String text = row.trim();
            if (!text.isEmpty()) out.add(new Line(-1, text));
            if (out.size() >= 64) break;
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static String enc(String v) throws Exception {
        return URLEncoder.encode(v == null ? "" : v, "UTF-8");
    }
}
