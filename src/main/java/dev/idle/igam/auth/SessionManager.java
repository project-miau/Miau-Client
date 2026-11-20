package dev.idle.igam.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import dev.idle.igam.AccountManager;
import idle.miau.utility.irc.MiauIRC;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SessionManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static Session launchSession;

  public static void captureLaunchSession() {
    if (launchSession == null && mc != null && mc.getSession() != null) {
      launchSession = copy(mc.getSession());
    }
  }

  public static Session getLaunchSession() {
    return launchSession == null ? null : copy(launchSession);
  }

  public static boolean isUsingLaunchSession() {
    return sameSession(launchSession, get());
  }

  public static boolean restoreLaunchSession() {
    if (launchSession == null) {
      return false;
    }
    set(copy(launchSession));
    return true;
  }

  public static Session get() {
    return mc.getSession();
  }

  public static Session offline(String username) {
    String uuid =
        UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "");
    return new Session(username, uuid, "0", Session.Type.LEGACY.toString());
  }

  public static void set(Session session) {
    if (session == null) {
      throw new IllegalArgumentException("session");
    }
    mc.setSession(session);
    AccountManager.recordSession(session);
    MiauIRC.getInstance().onMinecraftSessionChanged();
  }

  static boolean sameSession(Session first, Session second) {
    if (first == null || second == null) {
      return first == second;
    }
    return safe(first.getUsername()).equals(safe(second.getUsername()))
        && safe(first.getPlayerID()).equals(safe(second.getPlayerID()))
        && safe(first.getToken()).equals(safe(second.getToken()));
  }

  private static Session copy(Session session) {
    Session.Type sessionType = session.getSessionType();
    return new Session(
        safe(session.getUsername()),
        safe(session.getPlayerID()),
        safe(session.getToken()),
        sessionType == null ? Session.Type.LEGACY.toString() : sessionType.toString());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
