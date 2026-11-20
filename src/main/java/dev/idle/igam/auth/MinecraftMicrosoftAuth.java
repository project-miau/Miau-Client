package dev.idle.igam.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import idle.miau.BuildVersion;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.minecraft.util.Session;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.util.http.exception.ApiHttpRequestException;

public final class MinecraftMicrosoftAuth {
  private static final String USER_AGENT = "Miau/" + BuildVersion.VERSION;

  private MinecraftMicrosoftAuth() {
  }

  public static LoginResult login(Consumer<MsaDeviceCode> deviceCodeHandler)
      throws IOException, InterruptedException, TimeoutException {
    JavaAuthManager authManager = JavaAuthManager.create(MinecraftAuth.createHttpClient(USER_AGENT))
        .login(DeviceCodeMsaAuthService::new, deviceCodeHandler);
    return finish(authManager);
  }

  public static LoginResult restore(String authData) throws IOException {
    JsonElement json = new JsonParser().parse(authData);
    if (json == null || !json.isJsonObject()) {
      throw new IOException("Saved Microsoft login is invalid");
    }
    JavaAuthManager authManager = JavaAuthManager.fromJson(
        MinecraftAuth.createHttpClient(USER_AGENT), json.getAsJsonObject());
    return finish(authManager);
  }

  public static LoginResult migrate(String refreshToken) throws IOException {
    if (refreshToken == null || refreshToken.isEmpty()) {
      throw new IOException("Saved Microsoft login has expired. Add the account again.");
    }
    JavaAuthManager authManager = JavaAuthManager.create(MinecraftAuth.createHttpClient(USER_AGENT))
        .login(refreshToken);
    return finish(authManager);
  }

  public static String describe(Throwable error) {
    Throwable cause = unwrap(error);
    if (cause instanceof TimeoutException) {
      return "Microsoft login timed out";
    }
    if (cause instanceof InterruptedException
        || cause instanceof java.util.concurrent.CancellationException) {
      return "Microsoft login cancelled";
    }
    if (cause instanceof ApiHttpRequestException) {
      ApiHttpRequestException requestError = (ApiHttpRequestException) cause;
      String message = firstNonBlank(requestError.getErrorMessage(), requestError.getError());
      if (message != null) {
        return clean(message);
      }
    }
    if (cause instanceof HttpRequestException) {
      HttpRequestException requestError = (HttpRequestException) cause;
      if (requestError.getResponse() != null) {
        return "Microsoft login failed (HTTP "
            + requestError.getResponse().getStatusCode() + ")";
      }
    }
    String message = cause == null ? null : cause.getMessage();
    if (message == null || message.trim().isEmpty()) {
      return "Microsoft login failed";
    }
    return clean(message);
  }

  private static LoginResult finish(JavaAuthManager authManager) throws IOException {
    MinecraftToken token = authManager.getMinecraftToken().getUpToDate();
    MinecraftProfile profile = authManager.getMinecraftProfile().getUpToDate();
    String uid = profile.getId().toString().replace("-", "");
    Session session = new Session(
        profile.getName(), uid, token.getToken(), Session.Type.MOJANG.toString());
    return new LoginResult(
        session,
        JavaAuthManager.toJson(authManager).toString(),
        authManager.getMsaApplicationConfig().getClientId(),
        authManager.getMsaApplicationConfig().getScope());
  }

  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.trim().isEmpty()) {
      return first;
    }
    if (second != null && !second.trim().isEmpty()) {
      return second;
    }
    return null;
  }

  private static String clean(String message) {
    String cleaned = message.replace('\r', ' ').replace('\n', ' ').replace('&', ' ').trim();
    return cleaned.length() <= 300 ? cleaned : cleaned.substring(0, 297) + "...";
  }

  public static final class LoginResult {
    private final Session session;
    private final String authData;
    private final String clientId;
    private final String scope;

    private LoginResult(Session session, String authData, String clientId, String scope) {
      this.session = session;
      this.authData = authData;
      this.clientId = clientId;
      this.scope = scope;
    }

    public Session getSession() {
      return session;
    }

    public Account toAccount() {
      return new Account(
          "",
          session.getToken(),
          session.getUsername(),
          0L,
          clientId,
          scope,
          Account.TYPE_PREMIUM,
          session.getPlayerID(),
          System.currentTimeMillis(),
          0L,
          authData);
    }

    public void applyTo(Account account) {
      account.setRefreshToken("");
      account.setAccessToken(session.getToken());
      account.setUsername(session.getUsername());
      account.setClientId(clientId);
      account.setScope(scope);
      account.setType(Account.TYPE_PREMIUM);
      account.setUid(session.getPlayerID());
      account.setLastLogin(System.currentTimeMillis());
      account.setAuthData(authData);
    }
  }
}
