package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ProfileTextureLoader {
    private static final Logger logger = LogManager.getLogger();
    private final MinecraftSessionService sessionService;
    private final PublicKey publicKey;
    private final LoadingCache<GameProfile, Map<Type, MinecraftProfileTexture>> refreshedTextures;

    ProfileTextureLoader(MinecraftSessionService sessionService) {
        this(sessionService, loadPublicKey());
    }

    ProfileTextureLoader(MinecraftSessionService sessionService, PublicKey publicKey) {
        this.sessionService = sessionService;
        this.publicKey = publicKey;
        this.refreshedTextures = CacheBuilder.newBuilder()
                .maximumSize(1024)
                .expireAfterWrite(1L, TimeUnit.MINUTES)
                .build(new CacheLoader<GameProfile, Map<Type, MinecraftProfileTexture>>() {
                    @Override
                    public Map<Type, MinecraftProfileTexture> load(GameProfile profile) {
                        return refreshSecureTextures(profile);
                    }
                });
    }

    Map<Type, MinecraftProfileTexture> load(GameProfile profile, boolean requireSecure) {
        if (profile == null) {
            return Collections.emptyMap();
        }
        try {
            return requireSecure ? loadSecureTextures(profile)
                    : this.sessionService.getTextures(profile, false);
        } catch (RuntimeException exception) {
            logger.debug("Could not load profile textures for " + profile.getId(), exception);
            return Collections.emptyMap();
        }
    }

    private Map<Type, MinecraftProfileTexture> loadSecureTextures(GameProfile profile) {
        GameProfile candidate = new GameProfile(profile.getId(), profile.getName());
        candidate.getProperties().putAll(profile.getProperties());
        if (candidate.getProperties().get("textures").isEmpty()) {
            return Collections.emptyMap();
        }
        if (!hasValidSignature(candidate)) {
            if (profile.getId() == null) {
                return Collections.emptyMap();
            }
            return this.refreshedTextures.getUnchecked(profile);
        }
        return this.sessionService.getTextures(candidate, true);
    }

    private Map<Type, MinecraftProfileTexture> refreshSecureTextures(GameProfile profile) {
        GameProfile candidate = this.sessionService.fillProfileProperties(
                new GameProfile(profile.getId(), profile.getName()), true);
        if (candidate == null || !profile.getId().equals(candidate.getId())
                || !hasValidSignature(candidate)) {
            return Collections.emptyMap();
        }
        return this.sessionService.getTextures(candidate, true);
    }

    private boolean hasValidSignature(GameProfile profile) {
        for (Property property : profile.getProperties().get("textures")) {
            if (!property.hasSignature()) {
                return false;
            }
            try {
                Signature verifier = Signature.getInstance("SHA1withRSA");
                verifier.initVerify(this.publicKey);
                verifier.update(property.getValue().getBytes(StandardCharsets.UTF_8));
                return verifier.verify(Base64.getDecoder().decode(property.getSignature()));
            } catch (GeneralSecurityException | IllegalArgumentException exception) {
                return false;
            }
        }
        return false;
    }

    private static PublicKey loadPublicKey() {
        try (InputStream input = YggdrasilMinecraftSessionService.class
                .getResourceAsStream("/yggdrasil_session_pubkey.der")) {
            if (input == null) {
                throw new IllegalStateException("Missing Yggdrasil texture public key");
            }
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(input.readAllBytes()));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Yggdrasil texture public key", exception);
        }
    }
}
