package idle.miau.utility.spotify;

import com.google.gson.*;

import idle.miau.utility.spotify.http.*;

import java.io.IOException;

public final class SpotifyPlaybackClient {
    private static final String API = "https://api.spotify.com/v1";
    private final HttpTransport http;

    public SpotifyPlaybackClient(HttpTransport http) {
        this.http = http;
    }

    public HttpResponse current(String token) throws IOException {
        return request("GET", "/me/player/currently-playing", token, "");
    }

    public MusicTrack parse(HttpResponse r) {
        if (r.getStatus() == 204 || r.getBody().isEmpty()) return MusicTrack.STOPPED;
        JsonObject root = new JsonParser().parse(r.getBody()).getAsJsonObject();
        if (!root.has("item") || root.get("item").isJsonNull()) return MusicTrack.STOPPED;
        JsonObject item = root.getAsJsonObject("item"),
                album = item.has("album") ? item.getAsJsonObject("album") : new JsonObject();
        String artist = "", art = "";
        if (item.has("artists") && item.getAsJsonArray("artists").size() > 0)
            artist = str(item.getAsJsonArray("artists").get(0).getAsJsonObject(), "name");
        if (album.has("images") && album.getAsJsonArray("images").size() > 0)
            art = str(album.getAsJsonArray("images").get(0).getAsJsonObject(), "url");
        String url =
                item.has("external_urls")
                        ? str(item.getAsJsonObject("external_urls"), "spotify")
                        : "";
        return new MusicTrack(
                bool(root, "is_playing"),
                str(item, "name"),
                artist,
                str(album, "name"),
                art,
                str(item, "id"),
                "",
                url,
                bool(item, "explicit"),
                false,
                "off",
                number(root, "progress_ms"),
                number(item, "duration_ms"),
                System.currentTimeMillis());
    }

    public HttpResponse pause(String t) throws IOException {
        return request("PUT", "/me/player/pause", t, "");
    }

    public HttpResponse resume(String t) throws IOException {
        return request("PUT", "/me/player/play", t, "");
    }

    public HttpResponse next(String t) throws IOException {
        return request("POST", "/me/player/next", t, "");
    }

    public HttpResponse previous(String t) throws IOException {
        return request("POST", "/me/player/previous", t, "");
    }

    public HttpResponse volume(String t, int value) throws IOException {
        return request(
                "PUT",
                "/me/player/volume?volume_percent=" + Math.max(0, Math.min(100, value)),
                t,
                "");
    }

    private HttpResponse request(String method, String path, String token, String body)
            throws IOException {
        return http.execute(
                HttpRequest.builder(method, API + path)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build());
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    private static long number(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0L;
    }
}
