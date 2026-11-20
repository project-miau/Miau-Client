package idle.miau.utility.irc;

public final class IrcPresence {
    private final String uuid;
    private final String username;

    public IrcPresence(String uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }
}
