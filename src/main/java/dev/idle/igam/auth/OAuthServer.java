package dev.idle.igam.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class OAuthServer {
  private static final String AUTH_URL =
      "https://login.live.com/oauth20_authorize.srf?client_id=<client_id>&redirect_uri=<redirect_uri>&response_type=code&display=touch&scope=<scope>&prompt=select_account";

  private final OAuthHandler handler;
  private final String clientId;
  private final String redirectUri;
  private final String scope;
  private HttpServer httpServer;
  private ThreadPoolExecutor threadPoolExecutor;

  public OAuthServer(OAuthHandler handler, String clientId, String redirectUri, String scope)
      throws IOException {
    this.handler = handler;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.scope = scope;
    this.httpServer = HttpServer.create(new InetSocketAddress("localhost", 21919), 0);
    AtomicInteger workerId = new AtomicInteger();
    this.threadPoolExecutor =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                2,
                runnable -> {
                  Thread thread = new Thread(runnable, "Mimeow-OAuth-" + workerId.incrementAndGet());
                  thread.setDaemon(true);
                  return thread;
                });
  }

  public void start() {
    httpServer.createContext("/login", new OAuthHttpHandler(this));
    httpServer.setExecutor(threadPoolExecutor);
    httpServer.start();
    String url =
        AUTH_URL
            .replace("<client_id>", clientId)
            .replace("<redirect_uri>", redirectUri)
            .replace("<scope>", scope);
    handler.openUrl(url);
  }

  public synchronized void stop(boolean isInterrupt) {
    if (httpServer != null) {
      httpServer.stop(0);
      httpServer = null;
    }
    if (threadPoolExecutor != null) {
      threadPoolExecutor.shutdownNow();
      threadPoolExecutor = null;
    }
    if (isInterrupt) {
      handler.authError("Has been interrupted");
    }
  }

  private static class OAuthHttpHandler implements HttpHandler {
    private final OAuthServer server;

    OAuthHttpHandler(OAuthServer server) {
      this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      int status = 500;
      String response = "No code in the query";
      try {
        Map<String, String> query = getQueryParams(exchange.getRequestURI().getRawQuery());
        if (query.containsKey("code")) {
          server.handler.authResult(query.get("code"), server.clientId, server.scope);
          status = 200;
          response = "Login Success";
        }
        else {
          server.handler.authError("No code in the query");
        }
      }
      catch (Exception exception) {
        server.handler.authError(exception.toString());
        response = "Error: " + exception;
      }
      finally {
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        try {
          exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
          exchange.sendResponseHeaders(status, body.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
          }
        }
        finally {
          exchange.close();
          server.stop(false);
        }
      }
    }
  }

  static Map<String, String> getQueryParams(String query) {
    Map<String, String> params = new HashMap<>();
    if (query != null) {
      for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        if (pair.length == 2) {
          params.put(
              URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
              URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
      }
    }
    return params;
  }
}
