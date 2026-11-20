package idle.miau.utility.spotify;

public final class MusicTrack {
    public static final MusicTrack STOPPED =
            new MusicTrack(
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    false,
                    false,
                    "off",
                    0L,
                    0L,
                    System.currentTimeMillis());
    private final boolean playing, explicit, shuffle;
    private final String trackName,
            artistName,
            albumName,
            albumUrl,
            trackId,
            contextName,
            externalUrl,
            deviceName,
            repeatMode;
    private final long progressMs, durationMs, fetchedAt;

    public MusicTrack(
            boolean playing, String trackName, String artistName, String albumUrl, String trackId) {
        this(
                playing,
                trackName,
                artistName,
                "",
                albumUrl,
                trackId,
                "",
                "",
                false,
                false,
                "off",
                0L,
                0L,
                System.currentTimeMillis());
    }

    public MusicTrack(
            boolean playing,
            String trackName,
            String artistName,
            String albumName,
            String albumUrl,
            String trackId,
            String contextName,
            String externalUrl,
            boolean explicit,
            boolean shuffle,
            String repeatMode,
            long progressMs,
            long durationMs,
            long fetchedAt) {
        this.playing = playing;
        this.trackName = safe(trackName);
        this.artistName = safe(artistName);
        this.albumName = safe(albumName);
        this.albumUrl = safe(albumUrl);
        this.trackId = safe(trackId);
        this.contextName = safe(contextName);
        this.externalUrl = safe(externalUrl);
        this.deviceName = "";
        this.explicit = explicit;
        this.shuffle = shuffle;
        this.repeatMode = safe(repeatMode);
        this.progressMs = Math.max(0, progressMs);
        this.durationMs = Math.max(0, durationMs);
        this.fetchedAt = Math.max(0, fetchedAt);
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    public boolean isPlaying() {
        return playing;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumUrl() {
        return albumUrl;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getContextName() {
        return contextName;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public String getRepeatMode() {
        return repeatMode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }

    public long getDisplayProgressMs(long now) {
        long value = progressMs + (playing ? Math.max(0, now - fetchedAt) : 0);
        return durationMs > 0 ? Math.min(durationMs, value) : value;
    }
}
