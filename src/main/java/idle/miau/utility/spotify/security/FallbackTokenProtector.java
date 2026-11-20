package idle.miau.utility.spotify.security;

import net.minecraft.client.Minecraft;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;

public final class FallbackTokenProtector implements TokenProtector {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public FallbackTokenProtector() {
        this(true);
    }

    FallbackTokenProtector(boolean persistent) {
        try {
            key = persistent ? loadOrCreateKey() : legacyKey();
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize token protection", e);
        }
    }

    @Override
    public String protect(String value) throws Exception {
        return encrypt(value, key);
    }

    @Override
    public String unprotect(String value) throws Exception {
        try {
            return decrypt(value, key);
        } catch (Exception currentFailure) {
            return unprotectLegacy(value);
        }
    }

    String unprotectLegacy(String value) throws Exception {
        return decrypt(value, legacyKey());
    }

    @Override
    public String mode() {
        return "aes-gcm-random-install-key";
    }

    private static String encrypt(String value, byte[] secret) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, iv));
        return Base64.getEncoder().encodeToString(iv)
                + "."
                + Base64.getEncoder().encodeToString(
                        cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decrypt(String value, byte[] secret) throws Exception {
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid protected payload");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(secret, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
        return new String(
                cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }

    private static byte[] loadOrCreateKey() throws IOException {
        Path directory = Minecraft.getMinecraft().mcDataDir.toPath();
        Files.createDirectories(directory);
        Path file = directory.resolve(".miau-token-key");
        if (Files.isRegularFile(file)) {
            byte[] encoded = Files.readAllBytes(file);
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length == 32) return decoded;
            throw new IOException("Invalid Miau token key");
        }

        byte[] generated = new byte[32];
        RANDOM.nextBytes(generated);
        try {
            Files.write(
                    file,
                    Base64.getEncoder().encode(generated),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException race) {
            byte[] decoded = Base64.getDecoder().decode(Files.readAllBytes(file));
            if (decoded.length == 32) return decoded;
            throw new IOException("Invalid Miau token key", race);
        }
        try {
            Files.setPosixFilePermissions(
                    file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
        return generated;
    }

    private static byte[] legacyKey() throws Exception {
        String seed =
                System.getProperty("user.name", "")
                        + "|"
                        + System.getProperty("os.name", "")
                        + "|"
                        + System.getenv("COMPUTERNAME");
        return MessageDigest.getInstance("SHA-256")
                .digest(seed.getBytes(StandardCharsets.UTF_8));
    }
}
