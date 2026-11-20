package idle.miau.mcp.launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MimeowBootstrap {
    private static final String[][] DEFAULT_ARGS = {
            {"--version", "Miau"},
            {"--accessToken", "0"},
            {"--assetsDir", "assets"},
            {"--assetIndex", "1.8"},
            {"--userProperties", "{}"}
    };

    private MimeowBootstrap() {
    }

    public static void main(String[] args) {
        if (javaFeatureVersion() < 17) {
            throw new IllegalStateException("Mimeow requires Java 17 or newer");
        }
        idle.miau.diagnostic.MiauCrashDiagnostics.install();
        LwjglNativeBootstrap.prepare();
        net.minecraft.client.main.Main.main(withDefaults(args));
    }

    private static int javaFeatureVersion() {
        String value = System.getProperty("java.specification.version", "0");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        int dot = value.indexOf('.');
        if (dot >= 0) {
            value = value.substring(0, dot);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static String[] withDefaults(String[] args) {
        List<String> input = args == null ? Collections.emptyList() : Arrays.asList(args);
        List<String> output = new ArrayList<>(input);
        for (String[] entry : DEFAULT_ARGS) {
            if (input.stream().noneMatch(arg -> arg.equals(entry[0]) || arg.startsWith(entry[0] + "="))) {
                output.add(0, entry[1]);
                output.add(0, entry[0]);
            }
        }
        return output.toArray(new String[0]);
    }
}
