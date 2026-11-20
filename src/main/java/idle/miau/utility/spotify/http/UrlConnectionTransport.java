package idle.miau.utility.spotify.http;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UrlConnectionTransport implements HttpTransport {
    private static final int MAX_BODY = 1024 * 1024;

    @Override
    public HttpResponse execute(HttpRequest request) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(request.getUrl()).openConnection();
        c.setRequestMethod(request.getMethod());
        c.setConnectTimeout(request.getConnectTimeout());
        c.setReadTimeout(request.getReadTimeout());
        c.setRequestProperty("User-Agent", "Miau-Nextgen/1.8.9");
        for (java.util.Map.Entry<String, String> e : request.getHeaders().entrySet())
            c.setRequestProperty(e.getKey(), e.getValue());
        try {
            if (!request.getBody().isEmpty()) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(request.getBody().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream raw = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new HttpResponse(status, readBounded(raw), c.getHeaderFields());
        } finally {
            c.disconnect();
        }
    }

    private String readBounded(InputStream raw) throws IOException {
        if (raw == null) return "";
        try (InputStream in = raw;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096];
            int total = 0, n;
            while ((n = in.read(b)) != -1) {
                total += n;
                if (total > MAX_BODY) throw new IOException("Response body exceeded safe limit");
                out.write(b, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
