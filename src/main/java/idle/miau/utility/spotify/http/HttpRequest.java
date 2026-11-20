package idle.miau.utility.spotify.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpRequest {
    private final String method, url, body;
    private final Map<String, String> headers;
    private final int connectTimeout, readTimeout;

    private HttpRequest(Builder b) {
        method = b.method;
        url = b.url;
        body = b.body;
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
        connectTimeout = b.connectTimeout;
        readTimeout = b.readTimeout;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public static Builder builder(String method, String url) {
        return new Builder(method, url);
    }

    public static final class Builder {
        private final String method, url;
        private String body = "";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private int connectTimeout = 5000, readTimeout = 5000;

        private Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }

        public Builder header(String k, String v) {
            headers.put(k, v);
            return this;
        }

        public Builder body(String v) {
            body = v == null ? "" : v;
            return this;
        }

        public Builder timeouts(int c, int r) {
            connectTimeout = c;
            readTimeout = r;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
