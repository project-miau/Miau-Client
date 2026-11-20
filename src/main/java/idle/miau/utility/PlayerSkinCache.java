package idle.miau.utility;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import idle.miau.Miau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.net.Proxy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerSkinCache {
    private static final long PROFILE_CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final long FAILURE_RETRY_MS = 5L * 60L * 1000L;
    private static final int MAX_CACHE_ENTRIES = 512;

    private static final class CachedProfile {
        private final GameProfile profile;
        private final long expiresAtMs;

        private CachedProfile(GameProfile profile, long expiresAtMs) {
            this.profile = profile;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired(long now) {
            return now >= expiresAtMs;
        }
    }

    private static final Map<String, ResourceLocation> SKINS = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Map<String, UUID> UUIDS = new ConcurrentHashMap<String, UUID>();
    private static final Map<String, CachedProfile> PROFILES = new ConcurrentHashMap<String, CachedProfile>();
    private static final Set<String> LOOKUP_IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> SKIN_LOAD_IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, Long> FAILED_UNTIL = new ConcurrentHashMap<String, Long>();
    private static final GameProfileRepository PROFILE_REPOSITORY = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString()).createProfileRepository();
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Miau-SkinLookup-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private PlayerSkinCache() {
    }

    public static ResourceLocation getSkin(String username, NetworkPlayerInfo playerInfo) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkin(EntityPlayer.getOfflineUUID("Steve"));
        }

        if (playerInfo != null && playerInfo.getGameProfile() != null) {
            GameProfile profile = playerInfo.getGameProfile();
            UUID uuid = profile.getId();
            if (uuid != null) {
                UUIDS.put(normalized, uuid);
            }
            if (profile.getName() != null && Miau.playerRelationsManager != null) {
                Miau.playerRelationsManager.refreshDisplayName(profile.getName());
            }
            ResourceLocation location = playerInfo.getLocationSkin();
            if (location != null) {
                SKINS.put(normalized, location);
                FAILED_UNTIL.remove(normalized);
                trimCaches(normalized);
                return location;
            }
        }

        ResourceLocation cached = SKINS.get(normalized);
        if (cached != null) {
            return cached;
        }

        long now = System.currentTimeMillis();
        Long failedUntil = FAILED_UNTIL.get(normalized);
        if (failedUntil == null || now >= failedUntil) {
            if (failedUntil != null) {
                FAILED_UNTIL.remove(normalized, failedUntil);
            }
            requestSkin(normalized, username);
        }

        UUID fallbackUuid = UUIDS.get(normalized);
        if (fallbackUuid == null) {
            fallbackUuid = EntityPlayer.getOfflineUUID(username);
            UUIDS.put(normalized, fallbackUuid);
            trimCaches(normalized);
        }
        return DefaultPlayerSkin.getDefaultSkin(fallbackUuid);
    }

    private static void requestSkin(final String normalized, final String username) {
        long now = System.currentTimeMillis();
        CachedProfile cachedProfile = PROFILES.get(normalized);
        if (cachedProfile != null && !cachedProfile.isExpired(now)) {
            if (cachedProfile.profile == null) {
                markFailure(normalized);
                return;
            }
            resolveSkinFromProfile(normalized, cachedProfile.profile);
            return;
        }
        if (cachedProfile != null) {
            PROFILES.remove(normalized, cachedProfile);
        }

        if (!LOOKUP_IN_FLIGHT.add(normalized)) {
            return;
        }

        LOOKUP_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final GameProfile[] holder = new GameProfile[1];
                    PROFILE_REPOSITORY.findProfilesByNames(new String[]{username}, Agent.MINECRAFT, new ProfileLookupCallback() {
                        @Override
                        public void onProfileLookupSucceeded(GameProfile profile) {
                            holder[0] = profile;
                        }

                        @Override
                        public void onProfileLookupFailed(GameProfile profile, Exception exception) {
                        }
                    });

                    GameProfile profile = holder[0];
                    if (profile == null) {
                        cacheProfile(normalized, null);
                        markFailure(normalized);
                        return;
                    }

                    Minecraft minecraft = Minecraft.getMinecraft();
                    GameProfile filledProfile = minecraft.getSessionService().fillProfileProperties(profile, false);
                    if (filledProfile == null) {
                        cacheProfile(normalized, null);
                        markFailure(normalized);
                        return;
                    }

                    cacheProfile(normalized, filledProfile);
                    resolveSkinFromProfile(normalized, filledProfile);
                }
                catch (Exception ignored) {
                    cacheProfile(normalized, null);
                    markFailure(normalized);
                }
                finally {
                    LOOKUP_IN_FLIGHT.remove(normalized);
                }
            }
        });
    }

    private static void resolveSkinFromProfile(final String normalized, final GameProfile profile) {
        if (profile == null) {
            markFailure(normalized);
            return;
        }
        if (!SKIN_LOAD_IN_FLIGHT.add(normalized)) {
            return;
        }

        try {
            if (profile.getId() != null) {
                UUIDS.put(normalized, profile.getId());
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            Map<Type, MinecraftProfileTexture> textures = minecraft.getSkinManager().loadSkinFromCache(profile);
            final MinecraftProfileTexture skinTexture = textures == null ? null : textures.get(Type.SKIN);
            if (skinTexture == null) {
                markFailure(normalized);
                SKIN_LOAD_IN_FLIGHT.remove(normalized);
                return;
            }

            minecraft.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (profile.getName() != null && Miau.playerRelationsManager != null) {
                            Miau.playerRelationsManager.refreshDisplayName(profile.getName());
                        }
                        ResourceLocation location = Minecraft.getMinecraft().getSkinManager().loadSkin(skinTexture, Type.SKIN);
                        if (location != null) {
                            SKINS.put(normalized, location);
                            FAILED_UNTIL.remove(normalized);
                            trimCaches(normalized);
                        }
                        else {
                            markFailure(normalized);
                        }
                    }
                    finally {
                        SKIN_LOAD_IN_FLIGHT.remove(normalized);
                    }
                }
            });
        }
        catch (Exception ignored) {
            SKIN_LOAD_IN_FLIGHT.remove(normalized);
            markFailure(normalized);
        }
    }

    private static void cacheProfile(String normalized, GameProfile profile) {
        long ttl = profile == null ? FAILURE_RETRY_MS : PROFILE_CACHE_TTL_MS;
        PROFILES.put(normalized, new CachedProfile(profile, System.currentTimeMillis() + ttl));
        trimCaches(normalized);
    }

    private static void markFailure(String normalized) {
        FAILED_UNTIL.put(normalized, System.currentTimeMillis() + FAILURE_RETRY_MS);
        trimCaches(normalized);
    }

    private static void trimCaches(String keepKey) {
        trimMap(SKINS, keepKey);
        trimMap(UUIDS, keepKey);
        trimMap(PROFILES, keepKey);
        trimMap(FAILED_UNTIL, keepKey);
    }

    private static <T> void trimMap(Map<String, T> cache, String keepKey) {
        if (cache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        for (String key : cache.keySet()) {
            if (!key.equals(keepKey)) {
                cache.remove(key);
                if (cache.size() <= MAX_CACHE_ENTRIES) {
                    return;
                }
            }
        }
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
