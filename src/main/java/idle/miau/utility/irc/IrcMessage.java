package idle.miau.utility.irc;

public final class IrcMessage {
    private final String id;
    private final String cursor;
    private final String username;
    private final String role;
    private final String source;
    private final String content;
    private final long createdAt;

    public IrcMessage(
            String id,
            String cursor,
            String username,
            String role,
            String content,
            long createdAt) {
        this(id, cursor, username, role, "client", content, createdAt);
    }

    public IrcMessage(
            String id,
            String cursor,
            String username,
            String role,
            String source,
            String content,
            long createdAt) {
        this.id = id;
        this.cursor = cursor;
        this.username = username;
        this.role = role;
        this.source = source;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCursor() {
        return cursor;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
