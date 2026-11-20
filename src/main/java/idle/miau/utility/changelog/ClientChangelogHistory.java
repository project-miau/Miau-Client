package idle.miau.utility.changelog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientChangelogHistory {
    public static final int MAX_PAGES = 64;

    private static final Comparator<ClientChangelogEntry> NEWEST_FIRST =
            new Comparator<ClientChangelogEntry>() {
                @Override
                public int compare(ClientChangelogEntry left, ClientChangelogEntry right) {
                    int dateOrder = Long.compare(
                            right.getPublishedAtEpochMillis(),
                            left.getPublishedAtEpochMillis());
                    return dateOrder != 0
                            ? dateOrder
                            : right.getReleaseId().compareTo(left.getReleaseId());
                }
            };

    private ClientChangelogHistory() {
    }

    @FunctionalInterface
    public interface PageLoader {
        ClientChangelogPage load(String cursor) throws IOException;
    }

    public static List<ClientChangelogEntry> load(PageLoader loader, String localVersion)
            throws IOException {
        if (loader == null) {
            throw new IllegalArgumentException("A changelog page loader is required");
        }

        Map<String, ClientChangelogEntry> merged =
                new LinkedHashMap<String, ClientChangelogEntry>();
        Set<String> cursors = new HashSet<String>();
        String cursor = null;
        boolean currentFound = false;
        boolean localFound = false;

        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            ClientChangelogPage page = loader.load(cursor);
            if (page == null) {
                throw new IOException("The changelog service returned an empty page");
            }

            for (ClientChangelogEntry entry : page.getChangelogs()) {
                if (entry == null) {
                    throw new IOException("The changelog service returned an invalid entry");
                }
                merged.put(entry.getReleaseId(), entry);
                currentFound |= entry.isCurrent();
                localFound |= matchesVersion(entry, localVersion);
            }

            String nextCursor = page.getNextCursor();
            if (nextCursor == null || currentFound && localFound) {
                return sort(merged.values());
            }
            if (!cursors.add(nextCursor)) {
                throw new IOException("The changelog service returned a cursor loop");
            }
            cursor = nextCursor;
        }

        throw new IOException("The changelog service returned too many pages");
    }

    static boolean matchesVersion(ClientChangelogEntry entry, String version) {
        if (entry == null || version == null) {
            return false;
        }
        String expected = normalizeVersion(version);
        if (expected.isEmpty()) {
            return false;
        }
        return expected.equalsIgnoreCase(normalizeVersion(entry.getVersion()));
    }

    static String normalizeVersion(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "miau ", 0, 5)) {
            normalized = normalized.substring(5).trim();
        }
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    static List<ClientChangelogEntry> sort(Iterable<ClientChangelogEntry> entries) {
        List<ClientChangelogEntry> sorted = new ArrayList<ClientChangelogEntry>();
        for (ClientChangelogEntry entry : entries) {
            if (entry != null) {
                sorted.add(entry);
            }
        }
        Collections.sort(sorted, NEWEST_FIRST);
        return Collections.unmodifiableList(sorted);
    }
}
