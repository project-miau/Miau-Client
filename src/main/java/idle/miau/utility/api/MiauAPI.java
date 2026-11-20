package idle.miau.utility.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import idle.miau.BuildVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class MiauAPI {
    public static final String BASE_URL = BuildVersion.API_BASE_URL;
    public static String CLIENT_BRANCH = "myau";
    private static final String API_V1_PATH = "/api/v1";
    private static final String CLIENT_PATH = API_V1_PATH + "/client/" + CLIENT_BRANCH;
    private static String userAgent() {
        return "Miau/" + CLIENT_BRANCH;
    }
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private MiauAPI() {
    }

    public static String url(String path) {
        return BASE_URL + normalizePath(path);
    }

    public static String apiV1Url(String path) {
        return BASE_URL + API_V1_PATH + normalizePath(path);
    }

    public static String clientUrl(String path) {
        return BASE_URL + CLIENT_PATH + normalizePath(path);
    }

    public static String encodePathSegment(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String get(String path) throws IOException {
        return requestUrl("GET", url(path), null, Collections.<String, String>emptyMap());
    }

    public static String getApiV1(String path) throws IOException {
        return requestUrl("GET", apiV1Url(path), null, Collections.<String, String>emptyMap());
    }

    public static String getClient(String path) throws IOException {
        return requestUrl("GET", clientUrl(path), null, Collections.<String, String>emptyMap());
    }

    public static String request(
            String method, String path, String body, Map<String, String> headers)
            throws IOException {
        return requestUrl(method, url(path), body, headers);
    }

    public static String requestApiV1(
            String method, String path, String body, Map<String, String> headers)
            throws IOException {
        return requestUrl(method, apiV1Url(path), body, headers);
    }

    public static String requestClient(
            String method, String path, String body, Map<String, String> headers)
            throws IOException {
        return requestUrl(method, clientUrl(path), body, headers);
    }

    private static String requestUrl(
            String method, String address, String body, Map<String, String> headers)
            throws IOException {
        String requestMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (requestMethod.isEmpty()) {
            throw new IllegalArgumentException("HTTP method is required");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setRequestMethod(requestMethod);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", userAgent());

            Map<String, String> requestHeaders = headers == null
                    ? Collections.<String, String>emptyMap() : headers;
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                if (header.getKey() != null && header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            if (body != null) {
                connection.setDoOutput(true);
                if (connection.getRequestProperty("Content-Type") == null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int statusCode = connection.getResponseCode();
            String response = read(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new MiauApiException(statusCode, errorMessage(statusCode, response));
            }
            return response;
        }
        finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Miau API response is too large");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String errorMessage(int statusCode, String response) {
        try {
            JsonElement parsed = new JsonParser().parse(response);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("message")) {
                String message = parsed.getAsJsonObject().get("message").getAsString().trim();
                if (!message.isEmpty()) {
                    return "HTTP " + statusCode + ": " + message;
                }
            }
        }
        catch (Exception ignored) {
        }
        return "HTTP " + statusCode;
    }

    private static String normalizePath(String path) {
        String value = path == null ? "" : path.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("//") || lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new IllegalArgumentException("Miau API path must be relative");
        }
        if (value.isEmpty()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    public static final class MiauApiException extends IOException {
        private final int statusCode;

        private MiauApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
