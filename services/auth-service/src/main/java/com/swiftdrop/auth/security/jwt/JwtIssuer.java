package com.swiftdrop.auth.security.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues RS256-signed JWT access tokens and refresh-token correlation values
 * for SwiftDrop auth-service.
 *
 * <p>Claims produced match the shared contract in
 * {@code com.swiftdrop.common.security.JwtClaims} and
 * are compatible with {@code DevJwtTokenMinter} (same kid, same claim names).
 *
 * <p>Token lifetimes (ADR-004):
 * <ul>
 *   <li>Access token  – 15 minutes ({@link #ACCESS_TOKEN_TTL})</li>
 *   <li>Client-credentials token – 1 hour ({@link #CLIENT_CREDENTIALS_TTL})</li>
 * </ul>
 *
 * <p>Owner: M10 (com.swiftdrop.auth.security.jwt).
 * Does NOT clash with M7's {@code com.swiftdrop.auth.security} package.
 */
@Component
public class JwtIssuer {

    /** 15-minute lifetime for browser access tokens (ADR-004). */
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    /** 1-hour lifetime for OAuth2 client-credentials (machine-to-machine). */
    public static final Duration CLIENT_CREDENTIALS_TTL = Duration.ofHours(1);

    /** Standard issuer URI – matches DevJwtTokenMinter.DEV_ISSUER in common. */
    public static final String ISSUER = "https://auth.swiftdrop.local";

    private final JwtKeyProvider keyProvider;

    public JwtIssuer(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Issues a 15-minute RS256 access token for a regular user.
     *
     * @param userId     the user's unique identifier (becomes {@code sub})
     * @param email      user email
     * @param role       one of {@link Roles} constants
     * @param merchantId optional; include when role == MERCHANT
     * @return serialized compact JWT string
     */
    public String issueAccessToken(String userId, String email, String role, String merchantId) {
        return buildToken(userId, email, role, merchantId, ACCESS_TOKEN_TTL);
    }

    /**
     * Issues a client-credentials (M2M) access token.
     *
     * @param clientId   the OAuth2 client identifier (becomes {@code sub})
     * @param merchantId associated merchant UUID
     * @param scopes     granted scopes (e.g. ["parcels:create", "parcels:read"])
     * @return serialized compact JWT string
     */
    public String issueClientCredentialsToken(String clientId, String merchantId, List<String> scopes) {
        Instant now = Instant.now();
        Instant exp = now.plus(CLIENT_CREDENTIALS_TTL);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(clientId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim(JwtClaims.ROLE, Roles.MERCHANT)
                .claim(JwtClaims.ROLES, List.of("ROLE_MERCHANT"))
                .claim(JwtClaims.MERCHANT_ID, merchantId)
                .claim(JwtClaims.SCOPE, String.join(" ", scopes))
                .build();

        return sign(claims);
    }

    // ----------------------------------------------------------------
    // Internal
    // ----------------------------------------------------------------

    String buildToken(String userId, String email, String role, String merchantId, Duration ttl) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim(JwtClaims.EMAIL, email)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.ROLES, List.of("ROLE_" + role));

        if (merchantId != null && !merchantId.isBlank()) {
            builder.claim(JwtClaims.MERCHANT_ID, merchantId);
        }

        return sign(builder.build());
    }

    private String sign(JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyProvider.getRsaKey().getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keyProvider.getPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }
}
