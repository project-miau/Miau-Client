package idle.miau.notifications;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGui;
import idle.miau.clickgui.modern.MiauClickGui;
import idle.miau.event.ModuleStateChangeEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.client.Notifications;
import idle.miau.utility.Utils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MiauNotificationService {
    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_ACCENT = 1;
    public static final int TYPE_UNLOAD = 2;
    public static final int TYPE_ANTICHEAT = 3;
    public static final int CAPACITY = 10;
    public static final float LIFETIME_SECONDS = 5.0F;
    public static final float FADE_SECONDS = 0.2F;

    private final NativeBackend backend;
    private final ConcurrentLinkedQueue<NativeCommand> commands =
            new ConcurrentLinkedQueue<NativeCommand>();
    private final Object stateLock = new Object();
    private final AtomicBoolean onboardingShown = new AtomicBoolean();
    private final AtomicBoolean failureReported = new AtomicBoolean();

    private boolean booting = true;
    private int configRestoreDepth;
    private int pendingEnabled;
    private int pendingDisabled;
    private String latestEnabled = "";
    private String latestDisabled = "";
    private int logicalQueueSize;
    private long previousRenderNanos;
    private volatile boolean nativeFailed;

    public MiauNotificationService() {
        this(new NativeNotificationRenderer());
    }

    MiauNotificationService(NativeBackend backend) {
        if (backend == null) throw new NullPointerException("backend");
        this.backend = backend;
    }

    @Subscribe(priority = Priority.NORMAL)
    public void onModuleStateChanged(ModuleStateChangeEvent event) {
        if (event == null || event.getModule() == null || nativeFailed) {
            return;
        }

        Module module = event.getModule();
        if (module == ModuleManager.notifications || module == ModuleManager.clickGui) {
            return;
        }

        synchronized (stateLock) {
            if (booting || configRestoreDepth != 0
                    || !Notifications.isOverlayEnabled()
                    || !acceptsTransition(
                    module,
                    Notifications.hideHoldNotifications != null
                            && Notifications.hideHoldNotifications.isToggled(),
                    Notifications.showCombat == null || Notifications.showCombat.isToggled(),
                    Notifications.showVisual == null || Notifications.showVisual.isToggled(),
                    Notifications.showUtility == null || Notifications.showUtility.isToggled())) {
                return;
            }

            if (event.isEnabled()) {
                pendingEnabled++;
                latestEnabled = module.getName();
            } else {
                pendingDisabled++;
                latestDisabled = module.getName();
            }
        }
    }

    public void finishBoot() {
        synchronized (stateLock) {
            booting = false;
        }
        if (!Notifications.isOverlayEnabled()) {
            clear();
        }
    }

    public void onNotificationsEnabled() {
        synchronized (stateLock) {
            if (booting || configRestoreDepth != 0 || nativeFailed
                    || !Notifications.isOverlayEnabled()) {
                return;
            }
        }
        if (onboardingShown.compareAndSet(false, true)) {
            enqueue("Press RSHIFT to open GUI", TYPE_ACCENT);
        }
    }

    public void beginConfigRestore() {
        synchronized (stateLock) {
            configRestoreDepth++;
        }
    }

    public void endConfigRestore() {
        boolean shouldShowOnboarding;
        synchronized (stateLock) {
            if (configRestoreDepth > 0) {
                configRestoreDepth--;
            }
            shouldShowOnboarding = configRestoreDepth == 0 && !booting
                    && Notifications.isOverlayEnabled();
        }
        if (shouldShowOnboarding) {
            onNotificationsEnabled();
        } else {
            clear();
        }
    }

    public boolean isAvailable() {
        return !nativeFailed;
    }

    public boolean enqueue(String message) {
        return enqueue(message, TYPE_ACCENT);
    }

    public boolean enqueue(String message, int type) {
        return enqueue(message, null, type);
    }

    public boolean enqueue(String message, String badge, int type) {
        if (message == null || nativeFailed) {
            return false;
        }
        byte[] encoded = ModifiedUtf8.encodeCString(message);
        if (badge == null || badge.isEmpty()) {
            commands.add(new EnqueueCommand(encoded, type));
        } else {
            commands.add(new BadgedEnqueueCommand(
                    encoded, ModifiedUtf8.encodeCString(badge), type));
        }
        synchronized (stateLock) {
            logicalQueueSize = Math.min(CAPACITY, logicalQueueSize + 1);
        }
        return true;
    }

    public void clear() {
        commands.clear();
        synchronized (stateLock) {
            pendingEnabled = 0;
            pendingDisabled = 0;
            logicalQueueSize = 0;
            previousRenderNanos = 0L;
        }
        if (!nativeFailed) {
            try {
                if (backend.isInitialized()) {
                    commands.add(ClearCommand.INSTANCE);
                }
            } catch (Throwable failure) {
                failNative("state check", failure);
            }
        }
    }

    public int size() {
        synchronized (stateLock) {
            return logicalQueueSize;
        }
    }

    public void renderFinalOverlay(int width, int height) {
        if (nativeFailed) {
            return;
        }

        if (hiddenBySetting()) {
            synchronized (stateLock) {
                pendingEnabled = 0;
                pendingDisabled = 0;
            }
        } else if (Notifications.isOverlayEnabled()) {
            flushPendingTransitions();
        }

        final boolean initialized;
        try {
            initialized = backend.isInitialized();
        } catch (Throwable failure) {
            failNative("state check", failure);
            return;
        }

        if (!initialized) {
            if (commands.isEmpty()) {
                return;
            }
            final boolean initializationSucceeded;
            try {
                initializationSucceeded = backend.initialize();
            } catch (Throwable failure) {
                failNative("initialize", failure);
                return;
            }
            if (!initializationSucceeded) {
                failNative("initialize", null);
                return;
            }
        }

        NativeCommand command;
        while ((command = commands.poll()) != null) {
            final boolean applied;
            try {
                applied = command.apply(backend);
            } catch (Throwable failure) {
                failNative(command.operation(), failure);
                return;
            }
            if (!applied) {
                failNative(command.operation(), null);
                return;
            }
        }

        long now = System.nanoTime();
        float deltaSeconds;
        synchronized (stateLock) {
            deltaSeconds = previousRenderNanos == 0L
                    ? 0.0F : (now - previousRenderNanos) / 1_000_000_000.0F;
            previousRenderNanos = now;
        }
        final boolean rendered;
        try {
            rendered = backend.render(
                    width,
                    height,
                    deltaSeconds,
                    Notifications.accentRgb(),
                    Notifications.scaleFactor(),
                    Notifications.animationMode());
        } catch (Throwable failure) {
            failNative("render", failure);
            return;
        }
        if (!rendered) {
            failNative("render", null);
        }
    }

    public void shutdown() {
        commands.clear();
        synchronized (stateLock) {
            pendingEnabled = 0;
            pendingDisabled = 0;
            logicalQueueSize = 0;
            previousRenderNanos = 0L;
        }
        try {
            backend.shutdown();
        } catch (Throwable failure) {
            failNative("shutdown", failure, false);
        }
    }

    public int nativeBackend() {
        if (nativeFailed) {
            return 0;
        }
        try {
            return backend.backend();
        } catch (Throwable failure) {
            failNative("backend query", failure);
            return 0;
        }
    }

    private void flushPendingTransitions() {
        String enabledMessage = null;
        String disabledMessage = null;
        synchronized (stateLock) {
            if (pendingEnabled != 0) {
                enabledMessage = pendingEnabled == 1
                        ? "Enabled " + latestEnabled
                        : "Enabled " + pendingEnabled + " mods";
                pendingEnabled = 0;
            }
            if (pendingDisabled != 0) {
                disabledMessage = pendingDisabled == 1
                        ? "Disabled " + latestDisabled
                        : "Disabled " + pendingDisabled + " mods";
                pendingDisabled = 0;
            }
        }
        if (enabledMessage != null) enqueue(enabledMessage, TYPE_ACCENT);
        if (disabledMessage != null) enqueue(disabledMessage, TYPE_ACCENT);
    }

    private boolean hiddenBySetting() {
        if (Notifications.hideInGame == null || !Notifications.hideInGame.isToggled()) {
            return false;
        }
        return Miau.mc == null
                || !(Miau.mc.currentScreen instanceof ClickGui)
                && !(Miau.mc.currentScreen instanceof MiauClickGui);
    }

    private void failNative(String operation, Throwable failure) {
        failNative(operation, failure, true);
    }

    private void failNative(String operation, Throwable failure, boolean attemptShutdown) {
        synchronized (stateLock) {
            if (nativeFailed) {
                return;
            }
            nativeFailed = true;
            pendingEnabled = 0;
            pendingDisabled = 0;
            logicalQueueSize = 0;
            previousRenderNanos = 0L;
        }
        commands.clear();

        String detail = failure == null ? "" : throwableMessage(failure);
        try {
            String backendDetail = backend.lastError();
            if (backendDetail != null && !backendDetail.trim().isEmpty()
                    && !backendDetail.equals(detail)) {
                detail = detail.isEmpty() ? backendDetail : detail + "; " + backendDetail;
            }
        } catch (Throwable errorReadFailure) {
            if (detail.isEmpty()) {
                detail = throwableMessage(errorReadFailure);
            }
        }
        if (detail == null || detail.trim().isEmpty()) {
            detail = "unknown native renderer error";
        }
        if (attemptShutdown) {
            try {
                backend.shutdown();
            } catch (Throwable ignored) {
            }
        }

        if (failureReported.compareAndSet(false, true)) {
            String diagnostic = "Notifications native " + operation + " failed: " + detail;
            try {
                Utils.log.error(diagnostic, failure);
                if (Utils.nullCheck()) {
                    Utils.sendMessage("&c" + diagnostic);
                }
            } catch (Throwable ignored) {
            }
        }

        if (ModuleManager.notifications != null && ModuleManager.notifications.isEnabled()) {
            try {
                ModuleManager.notifications.disable();
            } catch (Throwable disableFailure) {
                try {
                    Utils.log.error("Could not disable Notifications after native failure",
                            disableFailure);
                } catch (Throwable ignored) {
                }
                ModuleManager.notifications.setEnabled(false);
            }
        }
    }

    private static String throwableMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.trim().isEmpty() ? "" : ": " + message);
    }

    public static boolean acceptsTransition(
            Module module,
            boolean holdSuppressed,
            boolean combat,
            boolean visual,
            boolean utility) {
        if (module == null || holdSuppressed && module.isHoldBind()) {
            return false;
        }
        switch (module.moduleCategory()) {
            case combat:
                return combat;
            case render:
                return visual;
            default:
                return utility;
        }
    }

    public static float opacity(float age) {
        if (age < FADE_SECONDS) {
            return smoothstep(age / FADE_SECONDS);
        }
        if (age > LIFETIME_SECONDS - FADE_SECONDS) {
            return smoothstep((LIFETIME_SECONDS - age) / FADE_SECONDS);
        }
        return 1.0F;
    }

    public static boolean expired(float age) {
        return age > LIFETIME_SECONDS;
    }

    private static float smoothstep(float value) {
        return (3.0F - 2.0F * value) * value * value;
    }

    interface NativeBackend {
        boolean initialize();

        boolean isInitialized();

        boolean enqueue(byte[] message, int type);

        default boolean enqueueEx(byte[] message, byte[] badge, int type) {
            return enqueue(message, type);
        }

        void clear();

        boolean render(
                int width,
                int height,
                float deltaSeconds,
                int accentRgb,
                float scale,
                int animationMode);

        int backend();

        String lastError();

        void shutdown();
    }

    private interface NativeCommand {
        boolean apply(NativeBackend backend);

        String operation();
    }

    private static final class EnqueueCommand implements NativeCommand {
        private final byte[] message;
        private final int type;

        private EnqueueCommand(byte[] message, int type) {
            this.message = message;
            this.type = type;
        }

        @Override
        public boolean apply(NativeBackend backend) {
            return backend.enqueue(message, type);
        }

        @Override
        public String operation() {
            return "enqueue";
        }
    }

    private static final class BadgedEnqueueCommand implements NativeCommand {
        private final byte[] message;
        private final byte[] badge;
        private final int type;

        private BadgedEnqueueCommand(byte[] message, byte[] badge, int type) {
            this.message = message;
            this.badge = badge;
            this.type = type;
        }

        @Override
        public boolean apply(NativeBackend backend) {
            return backend.enqueueEx(message, badge, type);
        }

        @Override
        public String operation() {
            return "enqueue";
        }
    }

    private enum ClearCommand implements NativeCommand {
        INSTANCE;

        @Override
        public boolean apply(NativeBackend backend) {
            backend.clear();
            return true;
        }

        @Override
        public String operation() {
            return "clear";
        }
    }
}
