package idle.miau.utility.discord;

import idle.miau.BuildVersion;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class DiscordPresence {
    private static final Logger LOGGER = LogManager.getLogger("Mimeow Discord RPC");
    private static final DiscordPresence INSTANCE = new DiscordPresence();
    private static final Pattern APPLICATION_ID_PATTERN = Pattern.compile("[0-9]{17,20}");
    private static final long REFRESH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15L);
    private static final String VERSION = "Version: " + BuildVersion.VERSION;
    private static final int MAX_SERVER_LENGTH = 100;

    private final Object lock = new Object();
    private ScheduledExecutorService callbackExecutor;
    private DiscordEventHandlers handlers;
    private String lastDetails;
    private long nextRefreshAt;
    private long startedAt;
    private boolean active;

    private DiscordPresence() {
    }

    public static DiscordPresence getInstance() {
        return INSTANCE;
    }

    public void start(Minecraft minecraft) {
        String applicationId = readSetting(
                "mimeow.discord.applicationId",
                "MIMEOW_DISCORD_APPLICATION_ID",
                BuildVersion.DISCORD_APPLICATION_ID
        );
        if (!APPLICATION_ID_PATTERN.matcher(applicationId).matches()) {
            LOGGER.warn("Discord Rich Presence is disabled because its application ID is missing or invalid");
            return;
        }

        synchronized (lock) {
            if (active) {
                return;
            }

            handlers = new DiscordEventHandlers.Builder().build();
            try {
                DiscordRPC.discordInitialize(applicationId, handlers, false);
                startedAt = System.currentTimeMillis() / 1000L;
                active = true;
                callbackExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "Mimeow-Discord-RPC");
                    thread.setDaemon(true);
                    return thread;
                });
                callbackExecutor.scheduleWithFixedDelay(this::runCallbacks, 0L, 2L, TimeUnit.SECONDS);
            } catch (Throwable error) {
                ScheduledExecutorService executor = callbackExecutor;
                callbackExecutor = null;
                active = false;
                handlers = null;
                if (executor != null) {
                    executor.shutdownNow();
                }
                try {
                    DiscordRPC.discordShutdown();
                } catch (Throwable ignored) {
                }
                LOGGER.warn("Unable to start Discord Rich Presence", error);
                return;
            }
        }

        update(minecraft, true);
    }

    public void update(Minecraft minecraft) {
        update(minecraft, false);
    }

    public void shutdown() {
        stop(null, true);
    }

    private void update(Minecraft minecraft, boolean force) {
        String details = "Playing on " + resolveServer(minecraft);
        long now = System.nanoTime();
        Throwable failure = null;

        synchronized (lock) {
            if (!active || !force && Objects.equals(lastDetails, details) && now < nextRefreshAt) {
                return;
            }

            DiscordRichPresence presence = new DiscordRichPresence.Builder(VERSION)
                    .setDetails(details)
                    .setStartTimestamps(startedAt)
                    .build();

            try {
                DiscordRPC.discordUpdatePresence(presence);
                lastDetails = details;
                nextRefreshAt = now + REFRESH_INTERVAL_NANOS;
            } catch (Throwable error) {
                failure = error;
            }
        }

        if (failure != null) {
            stop(failure, false);
        }
    }

    private void runCallbacks() {
        Throwable failure = null;

        synchronized (lock) {
            if (!active) {
                return;
            }
            try {
                DiscordRPC.discordRunCallbacks();
            } catch (Throwable error) {
                failure = error;
            }
        }

        if (failure != null) {
            stop(failure, false);
        }
    }

    private void stop(Throwable failure, boolean clearPresence) {
        ScheduledExecutorService executor;

        synchronized (lock) {
            if (!active && callbackExecutor == null) {
                return;
            }

            active = false;
            executor = callbackExecutor;
            callbackExecutor = null;
            try {
                if (clearPresence) {
                    DiscordRPC.discordClearPresence();
                }
                DiscordRPC.discordShutdown();
            } catch (Throwable ignored) {
            }
            handlers = null;
            lastDetails = null;
        }

        if (executor != null) {
            executor.shutdownNow();
        }
        if (failure != null) {
            LOGGER.warn("Discord Rich Presence stopped after an RPC error", failure);
        }
    }

    private static String resolveServer(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServerData();
        if (server != null && server.serverIP != null) {
            String address = server.serverIP.trim().replace('\r', ' ').replace('\n', ' ');
            if (!address.isEmpty()) {
                return address.length() <= MAX_SERVER_LENGTH
                        ? address
                        : address.substring(0, MAX_SERVER_LENGTH);
            }
        }
        return minecraft.isSingleplayer() ? "Singleplayer" : "Main Menu";
    }

    private static String readSetting(String propertyName, String environmentName, String fallback) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(environmentName);
        }
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
