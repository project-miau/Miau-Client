package idle.miau.utility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

final class ConfigFileCodec {
    private static final byte[] MAGIC = {'M', 'I', 'A', 'U', 'C', 'F', 'G'};
    private static final byte VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final byte[] KEY = {
            (byte) 0xA5, (byte) 0x16, (byte) 0xC8, (byte) 0x3F,
            (byte) 0x71, (byte) 0xD4, (byte) 0x09, (byte) 0xBE,
            (byte) 0x52, (byte) 0xE7, (byte) 0x68, (byte) 0x1A,
            (byte) 0x90, (byte) 0x4B, (byte) 0xF3, (byte) 0x2D
    };
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigFileCodec() {
    }

    static JsonObject read(Path file) throws IOException {
        byte[] stored = Files.readAllBytes(file);
        boolean encrypted = hasMagic(stored);
        byte[] plain = encrypted ? decrypt(stored) : stored;
        JsonObject config = parse(plain);
        if (!encrypted) {
            writeBytes(file, plain);
        }
        return config;
    }

    static void write(Path file, JsonObject config) throws IOException {
        writeBytes(file, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject parse(byte[] plain) throws IOException {
        try {
            JsonElement parsed = new JsonParser().parse(new InputStreamReader(
                    new ByteArrayInputStream(plain), StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("Invalid config data");
            }
            JsonObject config = parsed.getAsJsonObject();
            if (!config.has("modules") || !config.get("modules").isJsonArray()) {
                throw new IOException("Invalid config modules");
            }
            return config;
        }
        catch (RuntimeException exception) {
            throw new IOException("Invalid config data", exception);
        }
    }

    private static boolean hasMagic(byte[] stored) {
        if (stored.length < MAGIC.length) {
            return false;
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (stored[index] != MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] decrypt(byte[] stored) throws IOException {
        if (stored.length < MAGIC.length + 1 + NONCE_LENGTH + TAG_LENGTH / 8
                || stored[MAGIC.length] != VERSION) {
            throw new IOException("Unsupported or incomplete config data");
        }
        int nonceOffset = MAGIC.length + 1;
        byte[] nonce = Arrays.copyOfRange(stored, nonceOffset, nonceOffset + NONCE_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(TAG_LENGTH, nonce));
            cipher.updateAAD(stored, 0, nonceOffset);
            return cipher.doFinal(stored, nonceOffset + NONCE_LENGTH,
                    stored.length - nonceOffset - NONCE_LENGTH);
        }
        catch (GeneralSecurityException exception) {
            throw new IOException("Unable to decrypt config", exception);
        }
    }

    private static byte[] encrypt(byte[] plain) throws IOException {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        byte[] header = Arrays.copyOf(MAGIC, MAGIC.length + 1 + NONCE_LENGTH);
        header[MAGIC.length] = VERSION;
        System.arraycopy(nonce, 0, header, MAGIC.length + 1, NONCE_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(TAG_LENGTH, nonce));
            cipher.updateAAD(header, 0, MAGIC.length + 1);
            byte[] ciphertext = cipher.doFinal(plain);
            byte[] stored = Arrays.copyOf(header, header.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, stored, header.length, ciphertext.length);
            return stored;
        }
        catch (GeneralSecurityException exception) {
            throw new IOException("Unable to encrypt config", exception);
        }
    }

    private static void writeBytes(Path file, byte[] plain) throws IOException {
        byte[] stored = encrypt(plain);
        Path temporary = Files.createTempFile(
                file.getParent(), file.getFileName().toString() + ".config-", ".tmp");
        try {
            Files.write(temporary, stored);
            ConfigManager.replaceConfigFile(temporary, file);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }
}