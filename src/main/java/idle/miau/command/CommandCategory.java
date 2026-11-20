package idle.miau.command;

public enum CommandCategory {
    GENERAL("General"),
    MODULES("Modules"),
    CONFIG("Configs"),
    UTILITY("Utility");

    private final String displayName;

    CommandCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
