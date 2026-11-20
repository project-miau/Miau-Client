package idle.miau.utility.irc;

public final class IrcCommand {
    public enum Type {
        NONE,
        MESSAGE,
        ONLINE
    }

    private static final IrcCommand NONE = new IrcCommand(Type.NONE, "");
    private final Type type;
    private final String content;

    private IrcCommand(Type type, String content) {
        this.type = type;
        this.content = content;
    }

    public static IrcCommand parse(String input) {
        if (input == null) {
            return NONE;
        }
        String trimmed = input.trim();
        if ("#online".equalsIgnoreCase(trimmed)) {
            return new IrcCommand(Type.ONLINE, "");
        }
        if ("#".equals(trimmed)) {
            return new IrcCommand(Type.MESSAGE, "");
        }
        if (input.startsWith("# ")) {
            return new IrcCommand(Type.MESSAGE, input.substring(2).trim());
        }
        return NONE;
    }

    public Type getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public boolean isHandled() {
        return type != Type.NONE;
    }
}
