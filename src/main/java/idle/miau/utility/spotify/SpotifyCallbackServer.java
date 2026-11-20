package idle.miau.utility.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.*;

public final class SpotifyCallbackServer {
    public static final class Result {
        private final String code, error;

        Result(String code, String error) {
            this.code = code == null ? "" : code;
            this.error = error == null ? "" : error;
        }

        public String getCode() {
            return code;
        }

        public String getError() {
            return error;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private HttpServer server;
    private ExecutorService executor;
    private ScheduledExecutorService timeout;
    private CompletableFuture<Result> future;
    private String state = "";

    public synchronized String start() throws IOException {
        stop();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = server.getAddress().getPort();
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        state =
                "miau1."
                        + port
                        + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        future = new CompletableFuture<>();
        server.createContext("/callback", this::handle);
        executor = Executors.newSingleThreadExecutor(daemon("Miau-Spotify-Callback"));
        timeout = Executors.newSingleThreadScheduledExecutor(daemon("Miau-Spotify-Timeout"));
        server.setExecutor(executor);
        server.start();
        timeout.schedule(
                () -> complete(new Result("", "Authorization timed out")), 180, TimeUnit.SECONDS);
        return state;
    }

    public synchronized CompletableFuture<Result> getFuture() {
        return future;
    }

    public synchronized int getPort() {
        return server == null ? 0 : server.getAddress().getPort();
    }

    private void handle(HttpExchange x) throws IOException {
        MapQuery q = new MapQuery(x.getRequestURI().getRawQuery());
        boolean method = "GET".equalsIgnoreCase(x.getRequestMethod());
        boolean valid = method && constantTime(state, q.get("state"));
        String error =
                !method
                        ? "Invalid callback method"
                        : !valid ? "Invalid authorization state" : q.get("error");
        String code = valid ? q.get("code") : "";
        boolean ok = valid && !code.isEmpty() && error.isEmpty();
        byte[] body =
                page(
                                ok,
                                ok ? "Spotify connected" : "Spotify authorization failed",
                                ok ? "You can close this tab." : error)
                        .getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(ok ? 200 : 400, body.length);
        try (OutputStream out = x.getResponseBody()) {
            out.write(body);
        }
        complete(new Result(code, error.isEmpty() && !ok ? "Missing authorization code" : error));
    }

    private synchronized void complete(Result r) {
        if (future != null && !future.isDone()) future.complete(r);
        closeServer();
    }

    public synchronized void stop() {
        if (future != null && !future.isDone()) future.cancel(true);
        closeServer();
    }

    private void closeServer() {
        if (server != null) server.stop(0);
        server = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        if (timeout != null) timeout.shutdownNow();
        timeout = null;
        state = "";
    }

    private static boolean constantTime(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static String page(boolean ok, String title, String msg) {
        return "<!doctype html><html><body style='background:#0d0914;color:#fff;font:16px"
                + " system-ui;display:grid;place-items:center;min-height:100vh'><main><h1"
                + " style='color:"
                + (ok ? "#c489ff" : "#ff8fba")
                + "'>Miau Spotify</h1><h2>"
                + title
                + "</h2><p>"
                + escape(msg)
                + "</p></main></body></html>";
    }

    private static String escape(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class MapQuery {
        private final java.util.Map<String, String> m = new java.util.HashMap<>();

        MapQuery(String raw) {
            if (raw != null)
                for (String p : raw.split("&")) {
                    String[] v = p.split("=", 2);
                    try {
                        m.put(
                                URLDecoder.decode(v[0], "UTF-8"),
                                v.length > 1 ? URLDecoder.decode(v[1], "UTF-8") : "");
                    } catch (Exception ignored) {
                    }
                }
        }

        String get(String k) {
            String v = m.get(k);
            return v == null ? "" : v;
        }
    }
}
