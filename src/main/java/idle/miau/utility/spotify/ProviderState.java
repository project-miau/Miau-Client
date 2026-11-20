package idle.miau.utility.spotify;

public final class ProviderState {
    public static final ProviderState DISCONNECTED =
            new ProviderState(MusicProviderStatus.DISCONNECTED, "Disconnected", "", 0L);
    private final MusicProviderStatus status;
    private final String detail;
    private final String source;
    private final long retryAt;

    public ProviderState(MusicProviderStatus status, String detail, String source, long retryAt) {
        this.status = status == null ? MusicProviderStatus.DISCONNECTED : status;
        this.detail = detail == null ? "" : detail;
        this.source = source == null ? "" : source;
        this.retryAt = Math.max(0L, retryAt);
    }

    public MusicProviderStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getSource() {
        return source;
    }

    public long getRetryAt() {
        return retryAt;
    }

    public boolean isHealthy() {
        return status == MusicProviderStatus.CONNECTED
                || status == MusicProviderStatus.PAUSED
                || status == MusicProviderStatus.FALLBACK_ACTIVE;
    }
}
