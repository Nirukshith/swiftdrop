package com.swiftdrop.common.security.dev;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

/**
 * Dev-only test JWT minter strictly forbidden in production.
 * Enabled only when the active Spring profile includes 'dev'.
 */
@Component
@Profile("dev")
public class DevJwtTokenMinter {

    public static final String DEV_KEY_ID = "swiftdrop-dev-key";
    public static final String DEV_ISSUER = "https://auth.swiftdrop.local";

    private final RSAKey rsaKey;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final JWSSigner signer;

    public DevJwtTokenMinter(Environment environment) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new IllegalStateException("FATAL: DevJwtTokenMinter cannot be loaded outside the 'dev' profile!");
        }
        try {
            this.rsaKey = new RSAKeyGenerator(2048)
                .keyID(DEV_KEY_ID)
                .generate();
            this.privateKey = rsaKey.toRSAPrivateKey();
            this.publicKey = rsaKey.toRSAPublicKey();
            this.signer = new RSASSASigner(privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize dev RSA key pair", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAKey getRsaKey() {
        return rsaKey;
    }

    public String mintToken(String userId, String email, String role, String merchantId, Duration duration) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(duration != null ? duration : Duration.ofHours(1));

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(DEV_ISSUER)
                .subject(userId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp));

            if (email != null && !email.isBlank()) {
                builder.claim(JwtClaims.EMAIL, email);
            }
            if (role != null && !role.isBlank()) {
                builder.claim(JwtClaims.ROLE, role);
            }
            if (merchantId != null && !merchantId.isBlank()) {
                builder.claim(JwtClaims.MERCHANT_ID, merchantId);
            }

            SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(DEV_KEY_ID)
                    .type(JOSEObjectType.JWT)
                    .build(),
                builder.build()
            );

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mint dev JWT token", e);
        }
    }

    public String mintToken(String userId, String role) {
        return mintToken(userId, userId + "@example.com", role, null, Duration.ofHours(1));
    }

    public String mintCourierToken(String courierId) {
        return mintToken(courierId, courierId + "@courier.swiftdrop.local", Roles.COURIER, null, Duration.ofHours(2));
    }

    public String mintCustomerToken(String customerId) {
        return mintToken(customerId, customerId + "@customer.swiftdrop.local", Roles.CUSTOMER, null, Duration.ofHours(2));
    }

    public String mintMerchantToken(String merchantId, String userId) {
        return mintToken(userId, userId + "@merchant.swiftdrop.local", Roles.MERCHANT, merchantId, Duration.ofHours(2));
    }

    public String mintDispatcherToken(String dispatcherId) {
        return mintToken(dispatcherId, dispatcherId + "@ops.swiftdrop.local", Roles.DISPATCHER, null, Duration.ofHours(2));
    }

    public String mintAdminToken(String adminId) {
        return mintToken(adminId, adminId + "@admin.swiftdrop.local", Roles.ADMIN, null, Duration.ofHours(2));
    }
}
