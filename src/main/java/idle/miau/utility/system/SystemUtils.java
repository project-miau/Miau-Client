package idle.miau.utility.system;

public class SystemUtils {
    public static void addToClipboard(String string) {
        ClipboardBridge.setString(string);
    }
}
