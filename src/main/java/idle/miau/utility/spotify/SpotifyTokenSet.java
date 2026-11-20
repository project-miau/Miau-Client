package idle.miau.utility.spotify;

public final class SpotifyTokenSet {
    public static final SpotifyTokenSet EMPTY = new SpotifyTokenSet("", "", 0L);
    private final String accessToken, refreshToken;
    private final long expiresAt;

    public SpotifyTokenSet(String accessToken, String refreshToken, long expiresAt) {
        this.accessToken = accessToken == null ? "" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.expiresAt = Math.max(0L, expiresAt);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean hasAccessToken() {
        return !accessToken.isEmpty();
    }

    public boolean hasRefreshToken() {
        return !refreshToken.isEmpty();
    }

    public boolean shouldRefresh(long now) {
        return hasRefreshToken() && (!hasAccessToken() || expiresAt <= now + 60000L);
    }
}
