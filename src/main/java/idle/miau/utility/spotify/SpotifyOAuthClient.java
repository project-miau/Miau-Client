package idle.miau.utility.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import idle.miau.utility.api.MiauAPI;
import idle.miau.utility.spotify.http.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class SpotifyOAuthClient {
    public static final String REDIRECT_URI = MiauAPI.url("api/spotify/callback");
    private final String clientId;
    private final HttpTransport http;
    private final SecureRandom random = new SecureRandom();
    private String verifier = "";

    public SpotifyOAuthClient(String clientId, HttpTransport http) {
        this.clientId = clientId;
        this.http = http;
    }

    public String createAuthorizationUrl(String state) throws Exception {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String challenge = challenge(verifier);
        return "https://accounts.spotify.com/authorize?client_id="
                + enc(clientId)
                + "&response_type=code&redirect_uri="
                + enc(REDIRECT_URI)
                + "&scope="
                + enc(
                        "user-read-currently-playing user-read-playback-state"
                            + " user-modify-playback-state")
                + "&state="
                + enc(state)
                + "&code_challenge_method=S256&code_challenge="
                + enc(challenge);
    }

    public SpotifyTokenSet exchange(String code) throws IOException {
        return request(
                "grant_type=authorization_code&client_id="
                        + enc(clientId)
                        + "&code="
                        + enc(code)
                        + "&redirect_uri="
                        + enc(REDIRECT_URI)
                        + "&code_verifier="
                        + enc(verifier),
                "");
    }

    public SpotifyTokenSet refresh(String refreshToken) throws IOException {
        return request(
                "grant_type=refresh_token&client_id="
                        + enc(clientId)
                        + "&refresh_token="
                        + enc(refreshToken),
                refreshToken);
    }

    private SpotifyTokenSet request(String body, String priorRefresh) throws IOException {
        HttpResponse r =
                http.execute(
                        HttpRequest.builder("POST", "https://accounts.spotify.com/api/token")
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .body(body)
                                .build());
        if (r.getStatus() != 200)
            throw new IOException("Spotify token endpoint returned HTTP " + r.getStatus());
        JsonObject j = new JsonParser().parse(r.getBody()).getAsJsonObject();
        String access = str(j, "access_token"), refresh = str(j, "refresh_token");
        if (refresh.isEmpty()) refresh = priorRefresh;
        long expires = j.has("expires_in") ? j.get("expires_in").getAsLong() : 3600L;
        return new SpotifyTokenSet(
                access, refresh, System.currentTimeMillis() + Math.max(60L, expires) * 1000L);
    }

    public static String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256")
                                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private static String str(JsonObject j, String k) {
        return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : "";
    }
}
