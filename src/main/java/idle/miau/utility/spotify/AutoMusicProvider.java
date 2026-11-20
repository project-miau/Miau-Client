package idle.miau.utility.spotify;

public final class AutoMusicProvider implements MusicProvider {
    private final SpotifyAPI spotify;
    private final LastFmAPI lastFm;
    private volatile boolean running;
    private volatile long spotifyHealthySince;

    public AutoMusicProvider(SpotifyAPI spotify, LastFmAPI lastFm) {
        this.spotify = spotify;
        this.lastFm = lastFm;
    }

    public void start() {
        if (running) return;
        running = true;
        spotify.start();
        lastFm.start();
    }

    public void stop() {
        running = false;
        spotify.stop();
        lastFm.stop();
    }

    private boolean useSpotify() {
        ProviderState s = spotify.getProviderState();
        if (s.isHealthy() && spotify.getSnapshot().isPlaying()) {
            if (spotifyHealthySince == 0) spotifyHealthySince = System.currentTimeMillis();
            return System.currentTimeMillis() - spotifyHealthySince > 3000;
        }
        spotifyHealthySince = 0;
        return false;
    }

    public MusicTrack getSnapshot() {
        return useSpotify() ? spotify.getSnapshot() : lastFm.getSnapshot();
    }

    public ProviderState getProviderState() {
        if (useSpotify()) return spotify.getProviderState();
        ProviderState l = lastFm.getProviderState();
        return new ProviderState(
                MusicProviderStatus.FALLBACK_ACTIVE,
                "Spotify unavailable; using Last.fm",
                l.getSource(),
                l.getRetryAt());
    }

    public String getProviderName() {
        return "Auto";
    }

    public boolean isRunning() {
        return running;
    }

    public SpotifyAPI getSpotify() {
        return spotify;
    }
}
