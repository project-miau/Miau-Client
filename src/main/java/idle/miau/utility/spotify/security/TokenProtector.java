package idle.miau.utility.spotify.security;

public interface TokenProtector {
    String protect(String value) throws Exception;

    String unprotect(String value) throws Exception;

    String mode();
}
