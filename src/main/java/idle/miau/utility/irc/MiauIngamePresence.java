package idle.miau.utility.irc;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MiauIngamePresence {
    private static final long CACHE_TTL_NANOS = 60_000_000_000L;
    private static final Snapshot EMPTY = new Snapshot(
            Collections.<UUID>emptySet(),
            Collections.<String>emptySet(),
            0L
    );
    private static volatile Snapshot snapshot = EMPTY;

    private MiauIngamePresence() {
    }

    public static void update(List<IrcPresence> players) {
        Set<UUID> uuids = new HashSet<UUID>();
        Set<String> offlineUsernames = new HashSet<String>();
        if (players != null) {
            for (IrcPresence player : players) {
                if (player == null) {
                    continue;
                }
                UUID uuid = parseUuid(player.getUuid());
                if (uuid != null) {
                    uuids.add(uuid);
                }
                if (isOfflineIdentity(player)) {
                    offlineUsernames.add(normalizeUsername(player.getUsername()));
                }
            }
        }
        snapshot = new Snapshot(
                Collections.unmodifiableSet(uuids),
                Collections.unmodifiableSet(offlineUsernames),
                System.nanoTime()
        );
    }

    public static boolean isPresent(UUID uuid) {
        return isPresent(uuid, null);
    }

    public static boolean isPresent(UUID uuid, String username) {
        Snapshot current = snapshot;
        if (System.nanoTime() - current.updatedAt > CACHE_TTL_NANOS) {
            return false;
        }
        return (uuid != null && current.uuids.contains(uuid))
                || (username != null && current.offlineUsernames.contains(normalizeUsername(username)));
    }

    public static void clear() {
        snapshot = EMPTY;
    }

    private static boolean isOfflineIdentity(IrcPresence player) {
        try {
            return IrcApiClient.createOfflineUuid(player.getUsername()).equals(player.getUuid());
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        String uuid = value.replace("-", "").trim();
        if (uuid.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(
                    uuid.substring(0, 8) + "-"
                            + uuid.substring(8, 12) + "-"
                            + uuid.substring(12, 16) + "-"
                            + uuid.substring(16, 20) + "-"
                            + uuid.substring(20)
            );
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Snapshot {
        private final Set<UUID> uuids;
        private final Set<String> offlineUsernames;
        private final long updatedAt;

        private Snapshot(Set<UUID> uuids, Set<String> offlineUsernames, long updatedAt) {
            this.uuids = uuids;
            this.offlineUsernames = offlineUsernames;
            this.updatedAt = updatedAt;
        }
    }
}
