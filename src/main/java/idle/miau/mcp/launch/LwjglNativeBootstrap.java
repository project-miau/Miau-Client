package idle.miau.mcp.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LwjglNativeBootstrap {
    private static final String LWJGL_VERSION = "3.3.6";
    private static final String LIBRARY_PATH_PROPERTY = "org.lwjgl.librarypath";
    private static final String CACHE_PATH_PROPERTY = "miau.nativeCacheDir";

    private LwjglNativeBootstrap() {
    }

    public static void prepare() {
        Platform platform = Platform.detect(
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", "")
        );
        if (platform == null) {
            return;
        }
        Path cacheRoot = configuredCacheRoot();
        try {
            prepareFor(platform, cacheRoot, LwjglNativeBootstrap.class.getClassLoader());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not prepare bundled LWJGL " + LWJGL_VERSION
                            + " natives for " + platform.id,
                    exception
            );
        }
    }

    static Path prepareFor(
            String osName,
            String architecture,
            Path cacheRoot,
            ClassLoader loader
    ) throws IOException {
        Platform platform = Platform.detect(osName, architecture);
        if (platform == null) {
            throw new IOException(
                    "Bundled LWJGL natives do not support " + osName + " / " + architecture
            );
        }
        return prepareFor(platform, cacheRoot, loader);
    }

    private static Path prepareFor(
            Platform platform,
            Path cacheRoot,
            ClassLoader loader
    ) throws IOException {
        if (cacheRoot == null) {
            throw new IOException("Native cache root is unavailable");
        }
        if (loader == null) {
            throw new IOException("Client class loader is not available");
        }

        List<Payload> payloads = loadPayloads(platform, loader);
        MessageDigest bundleDigest = sha256();
        for (Payload payload : payloads) {
            bundleDigest.update(payload.resource.getBytes(StandardCharsets.UTF_8));
            bundleDigest.update((byte) 0);
            bundleDigest.update(payload.bytes);
        }
        String fingerprint = HexFormat.of().formatHex(bundleDigest.digest(), 0, 8);
        Path normalizedRoot = cacheRoot.toAbsolutePath().normalize();
        Path directory = normalizedRoot.resolve(
                "lwjgl-" + LWJGL_VERSION + "-" + platform.id + "-" + fingerprint
        );
        Files.createDirectories(directory);
        rejectSymbolicLink(directory, "native cache directory");

        for (Payload payload : payloads) {
            install(directory, payload);
        }
        System.setProperty(LIBRARY_PATH_PROPERTY, directory.toString());
        return directory;
    }

    private static List<Payload> loadPayloads(
            Platform platform,
            ClassLoader loader
    ) throws IOException {
        List<Payload> payloads = new ArrayList<>(platform.libraries.size());
        for (Library library : platform.libraries) {
            String resource = platform.resourceRoot + library.resourceSuffix;
            byte[] bytes;
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Bundled native resource is missing: " + resource);
                }
                bytes = input.readAllBytes();
            }
            if (bytes.length < 4_096) {
                throw new IOException("Bundled native resource is unexpectedly small: " + resource);
            }
            payloads.add(new Payload(resource, library.fileName, bytes, digest(bytes)));
        }
        return payloads;
    }

    private static void install(Path directory, Payload payload) throws IOException {
        Path target = directory.resolve(payload.fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IOException("Invalid native output path: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(target, "native library");
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(target) == payload.bytes.length
                    && MessageDigest.isEqual(digest(Files.readAllBytes(target)), payload.digest)) {
                return;
            }
        }

        Path temporary = Files.createTempFile(directory, payload.fileName + ".", ".tmp");
        try {
            Files.write(temporary, payload.bytes);
            setOwnerOnlyPermissions(temporary);
            if (!MessageDigest.isEqual(digest(Files.readAllBytes(temporary)), payload.digest)) {
                throw new IOException("Native extraction checksum mismatch: " + payload.resource);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyPermissions(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path configuredCacheRoot() {
        String configured = System.getProperty(CACHE_PATH_PROPERTY, "").trim();
        if (!configured.isEmpty()) {
            return Path.of(configured);
        }
        String userHome = System.getProperty("user.home", "").trim();
        if (userHome.isEmpty()) {
            throw new IllegalStateException("user.home is unavailable");
        }
        return Path.of(userHome, ".miau-launcher", "runtime", "natives");
    }

    private static void rejectSymbolicLink(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing symbolic-link " + label + ": " + path);
        }
    }

    private static void setOwnerOnlyPermissions(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static byte[] digest(byte[] bytes) throws IOException {
        return sha256().digest(bytes);
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private enum Platform {
        WINDOWS_X64(
                "windows-x64",
                "windows/x64/org/lwjgl/",
                List.of(
                        new Library("lwjgl.dll", "lwjgl.dll"),
                        new Library("glfw/glfw.dll", "glfw.dll"),
                        new Library("opengl/lwjgl_opengl.dll", "lwjgl_opengl.dll"),
                        new Library("openal/OpenAL.dll", "OpenAL.dll")
                )
        ),
        LINUX_X64(
                "linux-x64",
                "linux/x64/org/lwjgl/",
                List.of(
                        new Library("liblwjgl.so", "liblwjgl.so"),
                        new Library("glfw/libglfw.so", "libglfw.so"),
                        new Library("opengl/liblwjgl_opengl.so", "liblwjgl_opengl.so"),
                        new Library("openal/libopenal.so", "libopenal.so")
                )
        );

        private final String id;
        private final String resourceRoot;
        private final List<Library> libraries;

        Platform(String id, String resourceRoot, List<Library> libraries) {
            this.id = id;
            this.resourceRoot = resourceRoot;
            this.libraries = libraries;
        }

        private static Platform detect(String osName, String architecture) {
            String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
            String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
            boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
            if (!x64) {
                return null;
            }
            if (os.contains("win")) {
                return WINDOWS_X64;
            }
            if (os.contains("linux")) {
                return LINUX_X64;
            }
            return null;
        }
    }

    private record Library(String resourceSuffix, String fileName) {
    }

    private record Payload(String resource, String fileName, byte[] bytes, byte[] digest) {
    }
}
