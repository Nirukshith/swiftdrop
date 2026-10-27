package com.swiftdrop.integration.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class WebhookCryptoService {

    private static final Logger log = LoggerFactory.getLogger(WebhookCryptoService.class);

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final int GCM_IV_LENGTH = 12; // in bytes
    private static final String SECRET_PREFIX = "whsec_";

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookCryptoService(@Value("${swiftdrop.webhook.encryption-key:}") String configuredKey) {
        String envKey = System.getenv("WEBHOOK_ENCRYPTION_KEY");
        String effectiveKey = (envKey != null && !envKey.isBlank()) ? envKey : configuredKey;

        if (effectiveKey == null || effectiveKey.isBlank()) {
            log.warn("No WEBHOOK_ENCRYPTION_KEY or swiftdrop.webhook.encryption-key provided. Using local development fallback key.");
            effectiveKey = "swiftdrop-default-webhook-dev-secret-key-32b";
        }

        byte[] keyBytes;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(effectiveKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }

        this.masterKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Generates a new cryptographically secure webhook signing secret.
     */
    public String generateSecret() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return SECRET_PREFIX + HexFormat.of().formatHex(randomBytes);
    }

    /**
     * Creates a masked preview of the secret (e.g. whsec_••••••••••••••••3f8a).
     */
    public String generatePreview(String rawSecret) {
        if (rawSecret == null || rawSecret.length() < 4) {
            return SECRET_PREFIX + "••••••••";
        }
        String last4 = rawSecret.substring(rawSecret.length() - 4);
        return SECRET_PREFIX + "••••••••••••••••" + last4;
    }

    /**
     * Encrypts a plaintext secret using AES-GCM.
     */
    public String encrypt(String plainSecret) {
        if (plainSecret == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt webhook secret", e);
        }
    }

    /**
     * Decrypts an encrypted webhook secret.
     */
    public String decrypt(String encryptedSecretBase64) {
        if (encryptedSecretBase64 == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedSecretBase64);
            if (decoded.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted secret length");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt webhook secret", e);
        }
    }
}
