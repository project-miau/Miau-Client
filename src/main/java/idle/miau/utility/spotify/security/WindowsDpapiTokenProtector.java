package idle.miau.utility.spotify.security;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class WindowsDpapiTokenProtector implements TokenProtector {
    private final FallbackTokenProtector legacy = new FallbackTokenProtector(false);

    @Override
    public String protect(String value) {
        byte[] encrypted = Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String unprotect(String value) throws Exception {
        try {
            byte[] encrypted = Base64.getDecoder().decode(value);
            return new String(Crypt32Util.cryptUnprotectData(encrypted), StandardCharsets.UTF_8);
        } catch (Throwable dpapiFailure) {
            return legacy.unprotectLegacy(value);
        }
    }

    @Override
    public String mode() {
        return "windows-dpapi-user";
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
