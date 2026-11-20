package idle.miau.utility.spotify;

public interface MusicProvider {
    void start();

    void stop();

    MusicTrack getSnapshot();

    ProviderState getProviderState();

    String getProviderName();

    boolean isRunning();
}
