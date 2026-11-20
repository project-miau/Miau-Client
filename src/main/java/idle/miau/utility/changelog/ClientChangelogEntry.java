package idle.miau.utility.changelog;

public final class ClientChangelogEntry {
    private final String releaseId;
    private final String displayName;
    private final String version;
    private final String publishedAt;
    private final long publishedAtEpochMillis;
    private final String notes;
    private final boolean current;

    ClientChangelogEntry(
            String releaseId,
            String displayName,
            String version,
            String publishedAt,
            long publishedAtEpochMillis,
            String notes,
            boolean current) {
        this.releaseId = releaseId;
        this.displayName = displayName;
        this.version = version;
        this.publishedAt = publishedAt;
        this.publishedAtEpochMillis = publishedAtEpochMillis;
        this.notes = notes;
        this.current = current;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersion() {
        return version;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public long getPublishedAtEpochMillis() {
        return publishedAtEpochMillis;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isCurrent() {
        return current;
    }
}
