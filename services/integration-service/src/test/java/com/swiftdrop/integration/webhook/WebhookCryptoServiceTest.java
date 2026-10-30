package com.swiftdrop.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookCryptoServiceTest {

    private WebhookCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new WebhookCryptoService("test-encryption-key-for-unit-testing-32b");
    }

    @Test
    @DisplayName("Generated secret starts with whsec_ and has sufficient entropy")
    void generateSecret_format() {
        String secret = cryptoService.generateSecret();
        assertThat(secret).startsWith("whsec_");
        assertThat(secret.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("Preview masks secret correctly preserving last 4 characters")
    void generatePreview_masked() {
        String secret = "whsec_0123456789abcdef3f8a";
        String preview = cryptoService.generatePreview(secret);

        assertThat(preview).isEqualTo("whsec_••••••••••••••••3f8a");
    }

    @Test
    @DisplayName("AES-GCM encryption and decryption round-trip succeeds")
    void encryptDecrypt_roundTrip() {
        String plain = "whsec_my_super_secret_signing_key_123456789";
        String encrypted = cryptoService.encrypt(plain);

        assertThat(encrypted).isNotNull().isNotEqualTo(plain);

        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("Corrupted ciphertext fails decryption")
    void decrypt_corruptedCiphertext_throwsException() {
        String plain = "whsec_some_secret";
        String encrypted = cryptoService.encrypt(plain);

        // Corrupt by replacing middle char
        char[] chars = encrypted.toCharArray();
        chars[chars.length / 2] = (chars[chars.length / 2] == 'A') ? 'B' : 'A';
        String corrupted = new String(chars);

        assertThatThrownBy(() -> cryptoService.decrypt(corrupted))
                .isInstanceOf(IllegalStateException.class);
    }
}
