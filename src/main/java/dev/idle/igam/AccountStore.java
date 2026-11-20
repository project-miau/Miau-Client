package dev.idle.igam;

import dev.idle.igam.auth.Account;
import java.util.List;

final class AccountStore {
  private AccountStore() {
  }

  static Account merge(List<Account> storedAccounts, Account incoming) {
    Account existing = find(storedAccounts, incoming);
    if (existing == null) {
      storedAccounts.add(0, incoming);
      return incoming;
    }

    if (!incoming.getAuthData().isEmpty()) {
      existing.setAuthData(incoming.getAuthData());
      existing.setRefreshToken(incoming.getRefreshToken());
    } else if (!incoming.getRefreshToken().isEmpty()) {
      existing.setRefreshToken(incoming.getRefreshToken());
    }
    if (!incoming.getAccessToken().isEmpty()) existing.setAccessToken(incoming.getAccessToken());
    if (!incoming.getUsername().isEmpty()) existing.setUsername(incoming.getUsername());
    if (!incoming.getClientId().isEmpty()) existing.setClientId(incoming.getClientId());
    if (!incoming.getScope().isEmpty()) existing.setScope(incoming.getScope());
    if (!incoming.getType().isEmpty()) existing.setType(incoming.getType());
    if (!incoming.getUid().isEmpty()) existing.setUid(incoming.getUid());
    if (incoming.getUnban() != 0L) existing.setUnban(incoming.getUnban());
    existing.setLastLogin(Math.max(existing.getLastLogin(), incoming.getLastLogin()));
    existing.setPlayTime(Math.max(existing.getPlayTime(), incoming.getPlayTime()));
    return existing;
  }

  private static Account find(List<Account> storedAccounts, Account incoming) {
    for (Account stored : storedAccounts) {
      if (sameNonEmpty(stored.getUid(), incoming.getUid())
          || sameNonEmpty(stored.getRefreshToken(), incoming.getRefreshToken())
          || sameNonEmpty(stored.getAccessToken(), incoming.getAccessToken())) {
        return stored;
      }
      boolean sameUsername = !incoming.getUsername().isEmpty()
          && incoming.getUsername().equalsIgnoreCase(stored.getUsername());
      if (sameUsername && stored.isCracked() == incoming.isCracked()) {
        return stored;
      }
    }
    return null;
  }

  private static boolean sameNonEmpty(String first, String second) {
    return first != null && second != null && !first.isEmpty() && first.equals(second);
  }
}
