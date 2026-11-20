package dev.idle.igam.auth;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Session;

public final class RefreshTokenAuth {
  private static final String CLIENT_ID = "00000000402b5328";
  private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

  private RefreshTokenAuth() {
  }

  public static CompletableFuture<Account> authenticate(String refreshToken, Executor executor) {
    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      CompletableFuture<Account> failed = new CompletableFuture<>();
      failed.completeExceptionally(new IllegalArgumentException("Empty refresh token"));
      return failed;
    }

    MicrosoftAuth.CLIENT_ID = CLIENT_ID;
    MicrosoftAuth.SCOPE = SCOPE;
    AtomicReference<String> updatedRefreshToken = new AtomicReference<>(refreshToken.trim());
    AtomicReference<String> minecraftAccessToken = new AtomicReference<>("");

    return MicrosoftAuth.refreshMSAccessTokens(refreshToken.trim(), executor)
        .thenComposeAsync(tokens -> {
          String refreshed = tokens.get("refresh_token");
          if (refreshed != null && !refreshed.isEmpty()) {
            updatedRefreshToken.set(refreshed);
          }
          return MicrosoftAuth.acquireXboxAccessToken(required(tokens, "access_token"), executor);
        }, executor)
        .thenComposeAsync(
            xboxAccessToken -> MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor),
            executor)
        .thenComposeAsync(
            xboxXstsData -> MicrosoftAuth.acquireMCAccessToken(
                required(xboxXstsData, "Token"), required(xboxXstsData, "uhs"), executor),
            executor)
        .thenComposeAsync(accessToken -> {
          minecraftAccessToken.set(accessToken);
          return MicrosoftAuth.login(accessToken, executor);
        }, executor)
        .thenApply(session -> account(session, updatedRefreshToken.get(), minecraftAccessToken.get()));
  }

  private static Account account(Session session, String refreshToken, String accessToken) {
    return new Account(
        refreshToken,
        accessToken,
        session.getUsername(),
        0L,
        CLIENT_ID,
        SCOPE,
        Account.TYPE_REFRESH,
        session.getPlayerID(),
        0L,
        0L);
  }

  private static String required(Map<String, String> values, String key) {
    String value = values.get(key);
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException("Microsoft response is missing " + key);
    }
    return value;
  }
}
