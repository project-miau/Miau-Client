package idle.miau.utility.changelog;

import idle.miau.BuildVersion;
import idle.miau.Miau;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClientChangelogOverview {
    private static final int PREVIEW_LINE_LIMIT = 4;
    private static final Pattern NUMERIC_VERSION =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)(?:[^0-9].*)?$");
    private static final Object CACHE_LOCK = new Object();

    private static volatile Snapshot cached = Snapshot.checking();
    private static boolean loading;
    private static boolean loaded;

    private ClientChangelogOverview() {
    }

    public static Snapshot get() {
        return cached;
    }

    public static void ensureLoaded() {
        synchronized (CACHE_LOCK) {
            if (loading || loaded) {
                return;
            }
            loading = true;
            cached = Snapshot.checking();
        }

        try {
            Miau.getCachedExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClientChangelogClient client = new ClientChangelogClient();
                        publish(load(client::loadPage, BuildVersion.VERSION), true);
                    }
                    catch (Exception exception) {
                        publish(Snapshot.unavailable(), false);
                    }
                }
            });
        }
        catch (RuntimeException exception) {
            publish(Snapshot.unavailable(), false);
        }
    }

    static Snapshot load(ClientChangelogHistory.PageLoader loader, String localVersion)
            throws IOException {
        return summarize(ClientChangelogHistory.load(loader, localVersion), localVersion);
    }

    static Snapshot summarize(List<ClientChangelogEntry> history, String localVersion) {
        List<ClientChangelogEntry> sorted = ClientChangelogHistory.sort(
                history == null
                        ? Collections.<ClientChangelogEntry>emptyList()
                        : history);
        ClientChangelogEntry previewRelease = null;
        List<ClientChangelogLine> previewLines = Collections.emptyList();

        for (ClientChangelogEntry entry : sorted) {
            List<ClientChangelogLine> parsed = ClientChangelogFormatter.parse(entry.getNotes());
            if (!parsed.isEmpty()) {
                previewRelease = entry;
                previewLines = firstLines(parsed, PREVIEW_LINE_LIMIT);
                break;
            }
        }
        if (previewRelease == null && !sorted.isEmpty()) {
            previewRelease = sorted.get(0);
        }

        int localIndex = findVersion(sorted, localVersion);
        Integer versionsBehind = localIndex >= 0
                ? Integer.valueOf(localIndex)
                : inferVersionsBehind(sorted, localVersion);
        String updateMessage = versionsBehind == null
                ? "Unable to determine whether this version of Miau is up to date."
                : formatUpdateMessage(versionsBehind.intValue());
        return new Snapshot(
                updateMessage,
                previewRelease,
                previewLines,
                versionsBehind,
                false,
                false);
    }

    static String formatUpdateMessage(int versionsBehind) {
        if (versionsBehind <= 0) {
            return "You are running the latest version of Miau!";
        }
        return "You are running " + versionsBehind + " "
                + (versionsBehind == 1 ? "version" : "versions")
                + " behind the latest version of Miau.";
    }

    private static void publish(Snapshot snapshot, boolean successful) {
        synchronized (CACHE_LOCK) {
            cached = snapshot;
            loaded = successful;
            loading = false;
        }
    }

    private static int findVersion(List<ClientChangelogEntry> history, String localVersion) {
        for (int index = 0; index < history.size(); index++) {
            if (ClientChangelogHistory.matchesVersion(history.get(index), localVersion)) {
                return index;
            }
        }
        return -1;
    }

    private static Integer inferVersionsBehind(
            List<ClientChangelogEntry> history, String localVersion) {
        if (history.isEmpty()) {
            return null;
        }
        Set<String> releasedVersions = releasedVersions(history);
        Integer latestComparison = compareVersions(
                history.get(0).getVersion(), localVersion, releasedVersions);
        if (latestComparison == null) {
            return null;
        }
        if (latestComparison.intValue() <= 0) {
            return Integer.valueOf(0);
        }

        int newer = 0;
        for (ClientChangelogEntry entry : history) {
            Integer comparison = compareVersions(
                    entry.getVersion(), localVersion, releasedVersions);
            if (comparison != null && comparison.intValue() > 0) {
                newer++;
            }
        }
        return newer == 0 ? null : Integer.valueOf(newer);
    }

    static Integer compareVersions(String left, String right) {
        return compareVersions(
                left, right, Collections.<String>emptySet());
    }

    private static Integer compareVersions(
            String left, String right, Set<String> releasedVersions) {
        List<BigInteger> leftNumbers = parseVersion(left, releasedVersions);
        List<BigInteger> rightNumbers = parseVersion(right, releasedVersions);
        if (leftNumbers == null || rightNumbers == null) {
            return null;
        }
        int length = Math.max(leftNumbers.size(), rightNumbers.size());
        for (int index = 0; index < length; index++) {
            BigInteger leftNumber = index < leftNumbers.size()
                    ? leftNumbers.get(index) : BigInteger.ZERO;
            BigInteger rightNumber = index < rightNumbers.size()
                    ? rightNumbers.get(index) : BigInteger.ZERO;
            int compared = leftNumber.compareTo(rightNumber);
            if (compared != 0) {
                return Integer.valueOf(compared);
            }
        }
        return Integer.valueOf(0);
    }

    private static List<BigInteger> parseVersion(
            String value, Set<String> releasedVersions) {
        String normalized = ClientChangelogHistory.normalizeVersion(value);
        Matcher matcher = NUMERIC_VERSION.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        String[] parts = matcher.group(1).split("\\.");
        List<BigInteger> numbers = new ArrayList<BigInteger>();
        for (int index = 0; index < parts.length - 1; index++) {
            numbers.add(new BigInteger(parts[index]));
        }

        String lastPart = parts[parts.length - 1];
        int baseLength = compactBaseLength(parts, lastPart, releasedVersions);
        if (baseLength <= 0) {
            numbers.add(new BigInteger(lastPart));
            return numbers;
        }

        numbers.add(new BigInteger(lastPart.substring(0, baseLength)));
        for (int index = baseLength; index < lastPart.length(); index++) {
            String part = String.valueOf(lastPart.charAt(index));
            numbers.add(new BigInteger(part));
        }
        return numbers;
    }

    private static int compactBaseLength(
            String[] parts, String lastPart, Set<String> releasedVersions) {
        if (lastPart.length() < 2 || releasedVersions.isEmpty()) {
            return -1;
        }
        StringBuilder prefix = new StringBuilder();
        for (int index = 0; index < parts.length - 1; index++) {
            if (prefix.length() > 0) {
                prefix.append('.');
            }
            prefix.append(parts[index]);
        }
        if (prefix.length() > 0) {
            prefix.append('.');
        }

        for (int length = lastPart.length() - 1; length >= 1; length--) {
            if (releasedVersions.contains(prefix.toString()
                    + lastPart.substring(0, length))) {
                return length;
            }
        }
        return -1;
    }

    private static Set<String> releasedVersions(List<ClientChangelogEntry> history) {
        Set<String> versions = new HashSet<String>();
        for (ClientChangelogEntry entry : history) {
            String version = ClientChangelogHistory.normalizeVersion(entry.getVersion());
            if (!version.isEmpty()) {
                versions.add(version);
            }
        }
        return versions;
    }

    private static List<ClientChangelogLine> firstLines(
            List<ClientChangelogLine> lines, int maximum) {
        int count = Math.min(maximum, lines.size());
        return Collections.unmodifiableList(
                new ArrayList<ClientChangelogLine>(lines.subList(0, count)));
    }

    public static final class Snapshot {
        private final String updateMessage;
        private final ClientChangelogEntry previewRelease;
        private final List<ClientChangelogLine> previewLines;
        private final Integer versionsBehind;
        private final boolean checking;
        private final boolean unavailable;

        private Snapshot(
                String updateMessage,
                ClientChangelogEntry previewRelease,
                List<ClientChangelogLine> previewLines,
                Integer versionsBehind,
                boolean checking,
                boolean unavailable) {
            this.updateMessage = updateMessage;
            this.previewRelease = previewRelease;
            this.previewLines = Collections.unmodifiableList(
                    new ArrayList<ClientChangelogLine>(previewLines));
            this.versionsBehind = versionsBehind;
            this.checking = checking;
            this.unavailable = unavailable;
        }

        private static Snapshot checking() {
            return new Snapshot(
                    "Checking for Miau updates...",
                    null,
                    Collections.<ClientChangelogLine>emptyList(),
                    null,
                    true,
                    false);
        }

        static Snapshot unavailable() {
            return new Snapshot(
                    "Unable to check for Miau updates.",
                    null,
                    Collections.<ClientChangelogLine>emptyList(),
                    null,
                    false,
                    true);
        }

        public String getUpdateMessage() {
            return updateMessage;
        }

        public ClientChangelogEntry getPreviewRelease() {
            return previewRelease;
        }

        public List<ClientChangelogLine> getPreviewLines() {
            return previewLines;
        }

        public Integer getVersionsBehind() {
            return versionsBehind;
        }

        public boolean isChecking() {
            return checking;
        }

        public boolean isUnavailable() {
            return unavailable;
        }
    }
}
