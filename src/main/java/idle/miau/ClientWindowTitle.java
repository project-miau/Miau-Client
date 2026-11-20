package idle.miau;

public final class ClientWindowTitle {
    public static final String VERSION_PROPERTY = "miau.client.version";
    public static final String DISPLAY_NAME_PROPERTY = "miau.client.displayName";

    private ClientWindowTitle() {
    }

    public static String current() {
        return format(BuildVersion.VERSION);
    }

    public static String format(String version) {
        return format(version, "");
    }

    public static String format(String version, String displayName) {
        String normalizedVersion = normalize(version, BuildVersion.VERSION);
        return "Miau " + normalizedVersion;
    }

    private static String normalize(String value, String fallback) {
        String input = value == null ? "" : value;
        StringBuilder output = new StringBuilder(Math.min(input.length(), 64));
        for (int index = 0; index < input.length() && output.length() < 64; index++) {
            char character = input.charAt(index);
            output.append(character < 32 || character == 127 ? ' ' : character);
        }
        String normalized = output.toString().trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
