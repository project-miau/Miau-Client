package idle.miau.utility.spotify.http;

import java.util.*;

public final class HttpResponse {
    private final int status;
    private final String body;
    private final Map<String, List<String>> headers;

    public HttpResponse(int status, String body, Map<String, List<String>> headers) {
        this.status = status;
        this.body = body == null ? "" : body;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public String getHeader(String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet())
            if (e.getKey() != null
                    && e.getKey().equalsIgnoreCase(name)
                    && e.getValue() != null
                    && !e.getValue().isEmpty()) return e.getValue().get(0);
        return "";
    }

    public long retryAfterMillis(long fallback) {
        try {
            return Math.max(1000L, Long.parseLong(getHeader("Retry-After").trim()) * 1000L);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
