package idle.miau.notifications;

import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

final class NativeNotificationRenderer implements MiauNotificationService.NativeBackend {
    private static final String MEDIUM_FONT = "assets/idle.miau/fonts/product_sans_medium.ttf";
    private static final String BOLD_FONT = "assets/idle.miau/fonts/product_sans_bold.ttf";
    private static final String MEDIUM_SHA256 =
            "68f690d4d66bfe861bada25318be1c9aa54b453250d22241d0615db686cb2ee3";
    private static final String BOLD_SHA256 =
            "8dbeee804c249634fd860cae932f54afe759de8c17c136995fcae57c24348cf9";

    private static final String INIT = "mimeow_vlkr_notifications_init";
    private static final String ENQUEUE = "mimeow_vlkr_notifications_enqueue";
    private static final String ENQUEUE_EX = "mimeow_vlkr_notifications_enqueue_ex";
    private static final String CLEAR = "mimeow_vlkr_notifications_clear";
    private static final String RENDER = "mimeow_vlkr_notifications_render";
    private static final String SHUTDOWN = "mimeow_vlkr_notifications_shutdown";
    private static final String BACKEND = "mimeow_vlkr_notifications_backend";
    private static final String ERROR = "mimeow_vlkr_notifications_error";

    private SharedLibrary library;
    private long initAddress;
    private long enqueueAddress;
    private long enqueueExAddress;
    private long clearAddress;
    private long renderAddress;
    private long shutdownAddress;
    private long backendAddress;
    private long errorAddress;
    private boolean initialized;
    private boolean attempted;
    private String lastError = "";

    @Override
    public synchronized boolean initialize() {
        if (initialized) {
            return true;
        }
        if (attempted) {
            return false;
        }
        attempted = true;

        ByteBuffer medium = null;
        ByteBuffer bold = null;
        try {
            Platform platform = Platform.detect(
                    System.getProperty("os.name", ""),
                    System.getProperty("os.arch", ""));
            if (platform == null) {
                throw new IOException("Notifications support only Linux x86-64 and Windows x86-64");
            }

            Path nativePath = extractNative(platform);
            library = Library.loadNative(
                    NativeNotificationRenderer.class,
                    "idle/miau/notifications",
                    nativePath.toAbsolutePath().toString());
            initAddress = requireSymbol(INIT);
            enqueueAddress = requireSymbol(ENQUEUE);
            enqueueExAddress = requireSymbol(ENQUEUE_EX);
            clearAddress = requireSymbol(CLEAR);
            renderAddress = requireSymbol(RENDER);
            shutdownAddress = requireSymbol(SHUTDOWN);
            backendAddress = requireSymbol(BACKEND);
            errorAddress = requireSymbol(ERROR);

            byte[] mediumBytes = loadVerifiedResource(MEDIUM_FONT, MEDIUM_SHA256);
            byte[] boldBytes = loadVerifiedResource(BOLD_FONT, BOLD_SHA256);
            medium = MemoryUtil.memAlloc(mediumBytes.length);
            bold = MemoryUtil.memAlloc(boldBytes.length);
            medium.put(mediumBytes).flip();
            bold.put(boldBytes).flip();

            int result = JNI.invokePPI(
                    MemoryUtil.memAddress(medium), medium.remaining(),
                    MemoryUtil.memAddress(bold), bold.remaining(),
                    initAddress);
            if (result == 0) {
                throw new IOException(nativeError("native renderer initialization failed"));
            }
            initialized = true;
            lastError = "";
            return true;
        } catch (Throwable failure) {
            lastError = message(failure);
            releaseLibrary();
            return false;
        } finally {
            if (medium != null) MemoryUtil.memFree(medium);
            if (bold != null) MemoryUtil.memFree(bold);
        }
    }

    @Override
    public synchronized boolean isInitialized() {
        return initialized;
    }

    @Override
    public synchronized boolean enqueue(byte[] message, int type) {
        if (!initialized || message == null) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer encoded = stack.malloc(message.length);
            encoded.put(message).flip();
            int result = JNI.invokePI(MemoryUtil.memAddress(encoded), type, enqueueAddress);
            if (result == 0) {
                lastError = nativeError("native notification enqueue failed");
            }
            return result != 0;
        }
    }

    @Override
    public synchronized boolean enqueueEx(byte[] message, byte[] badge, int type) {
        if (!initialized || message == null || badge == null) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer encoded = stack.malloc(message.length);
            encoded.put(message).flip();
            ByteBuffer badgeBuffer = stack.malloc(badge.length);
            badgeBuffer.put(badge).flip();
            int result = JNI.invokePPI(
                    MemoryUtil.memAddress(encoded),
                    MemoryUtil.memAddress(badgeBuffer),
                    type,
                    enqueueExAddress);
            if (result == 0) {
                lastError = nativeError("native notification enqueue failed");
            }
            return result != 0;
        }
    }

    @Override
    public synchronized void clear() {
        if (initialized) {
            JNI.invokeV(clearAddress);
        }
    }

    @Override
    public synchronized boolean render(
            int width,
            int height,
            float deltaSeconds,
            int accentRgb,
            float scale,
            int animationMode) {
        if (!initialized) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer frame = stack.calloc(24).order(ByteOrder.nativeOrder());
            frame.putInt(0, width);
            frame.putInt(4, height);
            frame.putInt(8, Float.floatToRawIntBits(deltaSeconds));
            frame.putInt(12, accentRgb & 0x00FFFFFF);
            frame.putInt(16, Float.floatToRawIntBits(scale));
            frame.putInt(20, animationMode);
            int result = JNI.invokePI(MemoryUtil.memAddress(frame), renderAddress);
            if (result == 0) {
                lastError = nativeError("native notification render failed");
            }
            return result != 0;
        }
    }

    @Override
    public synchronized int backend() {
        return initialized ? JNI.invokeI(backendAddress) : 0;
    }

    @Override
    public synchronized String lastError() {
        if (initialized && errorAddress != 0L) {
            String nativeMessage = nativeError("");
            if (!nativeMessage.isEmpty()) {
                return nativeMessage;
            }
        }
        return lastError;
    }

    @Override
    public synchronized void shutdown() {
        try {
            if (initialized && shutdownAddress != 0L) {
                JNI.invokeV(shutdownAddress);
            }
        } finally {
            initialized = false;
            releaseLibrary();
        }
    }

    private long requireSymbol(String name) throws IOException {
        long address = library.getFunctionAddress(name);
        if (address == 0L) {
            throw new IOException("Bundled notification native is missing export " + name);
        }
        return address;
    }

    private String nativeError(String fallback) {
        if (errorAddress == 0L) {
            return fallback;
        }
        long pointer = JNI.invokeP(errorAddress);
        if (pointer == 0L) {
            return fallback;
        }
        String value = MemoryUtil.memUTF8(pointer);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void releaseLibrary() {
        if (library != null) {
            try {
                library.free();
            } catch (Throwable ignored) {
            }
        }
        library = null;
        initAddress = 0L;
        enqueueAddress = 0L;
        enqueueExAddress = 0L;
        clearAddress = 0L;
        renderAddress = 0L;
        shutdownAddress = 0L;
        backendAddress = 0L;
        errorAddress = 0L;
    }

    private static byte[] loadVerifiedResource(String resource, String expectedHash)
            throws IOException {
        byte[] bytes = readResource(resource);
        String actual = HexFormat.of().formatHex(sha256(bytes));
        if (!expectedHash.equals(actual)) {
            throw new IOException("Bundled font checksum mismatch for " + resource);
        }
        return bytes;
    }

    private static Path extractNative(Platform platform) throws IOException {
        byte[] bytes = readResource(platform.resource);
        if (bytes.length < 4096) {
            throw new IOException("Bundled notification native is unexpectedly small: "
                    + platform.resource);
        }
        byte[] digest = sha256(bytes);
        String fingerprint = HexFormat.of().formatHex(digest);
        Path root = cacheRoot().toAbsolutePath().normalize();
        Path directory = root.resolve(
                "mimeow-notifications-" + platform.id + "-" + fingerprint);
        Files.createDirectories(directory);
        rejectSymbolicLink(directory, "notification native cache directory");

        Path target = directory.resolve(platform.fileName).normalize();
        if (!directory.equals(target.getParent())) {
            throw new IOException("Invalid notification native output path: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(target, "notification native library");
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(target) == bytes.length
                    && MessageDigest.isEqual(sha256(Files.readAllBytes(target)), digest)) {
                return target;
            }
        }

        Path temporary = Files.createTempFile(directory, platform.fileName + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            setOwnerOnlyPermissions(temporary, platform == Platform.LINUX_X64);
            if (!MessageDigest.isEqual(sha256(Files.readAllBytes(temporary)), digest)) {
                throw new IOException("Notification native extraction checksum mismatch");
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyPermissions(target, platform == Platform.LINUX_X64);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] readResource(String resource) throws IOException {
        ClassLoader loader = NativeNotificationRenderer.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input != null) {
                return input.readAllBytes();
            }
        }
        Path projectRoot = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] devFallbacks = new Path[]{
                projectRoot.resolve(Path.of("build", "resources", "main", resource)),
                projectRoot.resolve(Path.of("build", "generated", "notification-resources", "windows", resource)),
                projectRoot.resolve(Path.of("build", "generated", "notification-resources", "linux", resource)),
                projectRoot.resolve(Path.of("src", "main", "resources", resource)),
                projectRoot.resolve(resource)
        };
        for (Path fallback : devFallbacks) {
            if (Files.isRegularFile(fallback)) {
                return Files.readAllBytes(fallback);
            }
        }
        throw new IOException("Bundled notification resource is missing: " + resource);
    }

    private static Path cacheRoot() throws IOException {
        String configured = System.getProperty("miau.notificationNativeCacheDir", "").trim();
        if (!configured.isEmpty()) {
            return Path.of(configured);
        }
        String home = System.getProperty("user.home", "").trim();
        if (home.isEmpty()) {
            throw new IOException("user.home is unavailable");
        }
        return Path.of(home, ".miau-launcher", "runtime", "natives");
    }

    private static void rejectSymbolicLink(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing symbolic-link " + label + ": " + path);
        }
    }

    private static void setOwnerOnlyPermissions(Path path, boolean executable) throws IOException {
        try {
            Set<PosixFilePermission> permissions = executable
                    ? Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static byte[] sha256(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.trim().isEmpty()
                ? failure.getClass().getSimpleName() : value;
    }

    enum Platform {
        LINUX_X64(
                "linux-x64",
                "linux/x64/idle/miau/notifications/libmimeow_vlkr_notifications.so",
                "libmimeow_vlkr_notifications.so"),
        WINDOWS_X64(
                "windows-x64",
                "windows/x64/idle/miau/notifications/mimeow_vlkr_notifications.dll",
                "mimeow_vlkr_notifications.dll");

        private final String id;
        private final String resource;
        private final String fileName;

        Platform(String id, String resource, String fileName) {
            this.id = id;
            this.resource = resource;
            this.fileName = fileName;
        }

        static Platform detect(String osName, String architecture) {
            String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
            String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
            boolean x64 = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64");
            if (!x64) return null;
            if (os.contains("linux")) return LINUX_X64;
            if (os.contains("win")) return WINDOWS_X64;
            return null;
        }
    }
}
