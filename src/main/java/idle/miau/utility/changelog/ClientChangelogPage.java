package idle.miau.utility.changelog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientChangelogPage {
    private final List<ClientChangelogEntry> changelogs;
    private final String nextCursor;

    ClientChangelogPage(List<ClientChangelogEntry> changelogs, String nextCursor) {
        this.changelogs = Collections.unmodifiableList(
                new ArrayList<ClientChangelogEntry>(changelogs));
        this.nextCursor = nextCursor;
    }

    public List<ClientChangelogEntry> getChangelogs() {
        return changelogs;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
