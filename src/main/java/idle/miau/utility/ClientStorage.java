package idle.miau.utility;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ClientStorage {
    public static final String DIRECTORY_NAME = "miau";
    private static final String LEGACY_DIRECTORY_NAME = "Roven";

    private ClientStorage() {
    }

    public static File getDirectory(File gameDirectory) {
        return new File(gameDirectory, DIRECTORY_NAME);
    }

    public static File getLegacyDirectory(File gameDirectory) {
        return new File(gameDirectory, LEGACY_DIRECTORY_NAME);
    }

    public static File ensureDirectory(File gameDirectory) {
        File directory = getDirectory(gameDirectory);
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create Miau data directory: " + directory);
        }
        return directory;
    }

    public static int migrateJsonFiles(File legacyDirectory, File targetDirectory) throws IOException {
        if (!legacyDirectory.isDirectory()) {
            return 0;
        }
        ensureChildDirectory(targetDirectory);
        File[] files = legacyDirectory.listFiles((directory, name) ->
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return 0;
        }
        int migrated = 0;
        for (File source : files) {
            File target = new File(targetDirectory, source.getName());
            if (copyIfMissing(source, target)) {
                migrated++;
            }
        }
        return migrated;
    }

    public static boolean copyIfMissing(File source, File target) throws IOException {
        if (!source.isFile() || target.exists()) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            ensureChildDirectory(parent);
        }
        File temporary = new File(parent, target.getName() + ".migration.tmp");
        Files.copy(source.toPath(), temporary.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), target.toPath());
            }
        }
        finally {
            Files.deleteIfExists(temporary.toPath());
        }
        return true;
    }

    private static void ensureChildDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create directory: " + directory);
        }
    }
}
