package dev.idle.igam.auth;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class Account {
  public static final String TYPE_PREMIUM = "premium";
  public static final String TYPE_CRACKED = "cracked";
  public static final String TYPE_COOKIE = "cookie";
  public static final String TYPE_TOKEN = "token";
  public static final String TYPE_SESSION = "session";
  public static final String TYPE_REFRESH = "refresh";

  private String refreshToken;
  private String accessToken;
  private String username;
  private long unban;
  private String clientId;
  private String scope;
  private String type;
  private String uid;
  private long lastLogin;
  private long playTime;
  private String authData;

  public Account(
      String refreshToken, String accessToken, String username, String clientId, String scope) {
    this(refreshToken, accessToken, username, 0L, clientId, scope, TYPE_PREMIUM);
  }

  public Account(
      String refreshToken,
      String accessToken,
      String username,
      long unban,
      String clientId,
      String scope) {
    this(refreshToken, accessToken, username, unban, clientId, scope, TYPE_PREMIUM);
  }

  public Account(
      String refreshToken,
      String accessToken,
      String username,
      long unban,
      String clientId,
      String scope,
      String type) {
    this(refreshToken, accessToken, username, unban, clientId, scope, type, "", 0L, 0L);
  }

  public Account(
      String refreshToken,
      String accessToken,
      String username,
      long unban,
      String clientId,
      String scope,
      String type,
      String uid,
      long lastLogin,
      long playTime) {
    this(
        refreshToken,
        accessToken,
        username,
        unban,
        clientId,
        scope,
        type,
        uid,
        lastLogin,
        playTime,
        "");
  }

  public Account(
      String refreshToken,
      String accessToken,
      String username,
      long unban,
      String clientId,
      String scope,
      String type,
      String uid,
      long lastLogin,
      long playTime,
      String authData) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.username = username;
    this.unban = unban;
    this.clientId = clientId;
    this.scope = scope;
    this.type = type == null || type.isEmpty() ? TYPE_PREMIUM : type;
    this.uid = uid == null ? "" : uid;
    this.lastLogin = Math.max(0L, lastLogin);
    this.playTime = Math.max(0L, playTime);
    this.authData = authData == null ? "" : authData;
  }

  public static Account cracked(String username) {
    return new Account("", "", username, 0L, "", "", TYPE_CRACKED);
  }

  public String getClientId() {
    return clientId;
  }

  public String getScope() {
    return scope;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getUsername() {
    return username;
  }

  public long getUnban() {
    return unban;
  }

  public String getType() {
    return type;
  }

  public String getUid() {
    return uid;
  }

  public long getLastLogin() {
    return lastLogin;
  }

  public long getPlayTime() {
    return playTime;
  }

  public String getAuthData() {
    return authData;
  }

  public boolean isCracked() {
    return TYPE_CRACKED.equals(type);
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setUnban(long unban) {
    this.unban = unban;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public void setType(String type) {
    this.type = type == null || type.isEmpty() ? TYPE_PREMIUM : type;
  }

  public void setUid(String uid) {
    this.uid = uid == null ? "" : uid;
  }

  public void setLastLogin(long lastLogin) {
    this.lastLogin = Math.max(0L, lastLogin);
  }

  public void setPlayTime(long playTime) {
    this.playTime = Math.max(0L, playTime);
  }

  public void setAuthData(String authData) {
    this.authData = authData == null ? "" : authData;
  }

  public void addPlayTime(long duration) {
    if (duration > 0L) {
      playTime += duration;
    }
  }
}
