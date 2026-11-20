package idle.miau.utility.changelog;

public final class ClientChangelogLine {
    public enum Type {
        ADDED(0xFF55FF55),
        IMPROVED(0xFF55FFFF),
        REFACTORED(0xFFCC88FF),
        FIXED(0xFFFFAA00),
        REMOVED(0xFFFF5555),
        DEFAULT(0xFFE5EEF1);

        private final int color;

        Type(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    private final String text;
    private final Type type;

    ClientChangelogLine(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public Type getType() {
        return type;
    }
}
