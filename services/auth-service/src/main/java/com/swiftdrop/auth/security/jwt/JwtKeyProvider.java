package com.swiftdrop.auth.security.jwt;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provides the RSA key pair used by {@link JwtIssuer} to sign tokens.
 *
 * <p>Strategy:
 * <ul>
 *   <li><b>Dev / local profile</b>: generates an in-memory 2048-bit RSA key on startup,
 *       using the well-known {@code swiftdrop-dev-key} kid so that
 *       {@code common.security.dev.DevJwtTokenMinter} tokens are interchangeable in tests.</li>
 *   <li><b>Production</b>: loads private key PEM from the {@code SWIFTDROP_JWT_PRIVATE_KEY_PEM}
 *       environment variable and public key PEM from {@code SWIFTDROP_JWT_PUBLIC_KEY_PEM}.</li>
 * </ul>
 *
 * <p>Owner: M10 (com.swiftdrop.auth.security.jwt sub-package).
 */
@Component
public class JwtKeyProvider {

    /** Kid used by the dev in-memory key – matches DevJwtTokenMinter.DEV_KEY_ID in common. */
    public static final String DEV_KEY_ID = "swiftdrop-dev-key";
    public static final String DEV_ISSUER = "https://auth.swiftdrop.local";

    private final RSAKey rsaKey;

    public JwtKeyProvider(
            @Value("${swiftdrop.jwt.private-key-pem:}") String privateKeyPem,
            @Value("${swiftdrop.jwt.public-key-pem:}") String publicKeyPem,
            @Value("${swiftdrop.jwt.key-id:" + DEV_KEY_ID + "}") String keyId) {

        if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
            // Production path: load from PEM env vars
            this.rsaKey = loadFromPem(privateKeyPem.trim(), publicKeyPem.trim(), keyId);
        } else {
            // Dev/local path: generate in-memory key with the fixed dev kid
            this.rsaKey = generateDevKey();
        }
    }

    public RSAKey getRsaKey() {
        return rsaKey;
    }

    public RSAPublicKey getPublicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract RSA public key", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        try {
            return rsaKey.toRSAPrivateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract RSA private key", e);
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private static RSAKey generateDevKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(DEV_KEY_ID)
                    .generate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate dev RSA key pair", e);
        }
    }

    private static RSAKey loadFromPem(String privateKeyPem, String publicKeyPem, String keyId) {
        try {
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");

            // Parse private key (PKCS#8 PEM)
            String privBase64 = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] privBytes = java.util.Base64.getDecoder().decode(privBase64);
            RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(
                    new java.security.spec.PKCS8EncodedKeySpec(privBytes));

            // Parse public key (X.509 PEM)
            String pubBase64 = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] pubBytes = java.util.Base64.getDecoder().decode(pubBase64);
            RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(pubBytes));

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "FATAL: Failed to load RSA keys from environment variables. " +
                    "Ensure SWIFTDROP_JWT_PRIVATE_KEY_PEM and SWIFTDROP_JWT_PUBLIC_KEY_PEM are set correctly.", e);
        }
    }
}
