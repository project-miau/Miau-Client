package idle.miau.utility.config.online;

public enum OnlineConfigSource {
    OFFICIAL("official"),
    USER("user");

    private final String path;

    OnlineConfigSource(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
