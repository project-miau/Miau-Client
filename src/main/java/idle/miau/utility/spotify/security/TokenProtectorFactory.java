package idle.miau.utility.spotify.security;

public final class TokenProtectorFactory {
    private TokenProtectorFactory() {}

    public static TokenProtector create() {
        if (WindowsDpapiTokenProtector.supported()) {
            try {
                return new WindowsDpapiTokenProtector();
            } catch (Throwable ignored) {
            }
        }
        return new FallbackTokenProtector();
    }
}
