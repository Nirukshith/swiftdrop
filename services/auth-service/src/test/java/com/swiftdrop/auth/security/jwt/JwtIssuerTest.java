package com.swiftdrop.auth.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtIssuer}.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>Tokens can be decoded and verified against the JWKS public key exposed by
 *       {@link JwtKeyProvider#getPublicKey()} (same key pair used for signing).</li>
 *   <li>Issued access tokens carry the correct claim values as agreed in the shared
 *       {@link JwtClaims} schema and {@link com.swiftdrop.common.security.dev.DevJwtTokenMinter}.</li>
 *   <li>Client-credentials tokens carry scope and merchantId claims.</li>
 *   <li>The JWKS endpoint would expose exactly the public portion of the signing key.</li>
 * </ol>
 *
 * <p>These tests run without Spring context (pure unit tests) – fast and no Docker required.
 *
 * <p>Owner: M10
 */
class JwtIssuerTest {

    private JwtKeyProvider keyProvider;
    private JwtIssuer jwtIssuer;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        // Use dev (in-memory) key – no env vars needed
        keyProvider = new JwtKeyProvider("", "", JwtKeyProvider.DEV_KEY_ID);
        jwtIssuer = new JwtIssuer(keyProvider);

        // Build a decoder backed by the same public key (simulates what resource servers do)
        RSAPublicKey publicKey = keyProvider.getPublicKey();
        decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    // ----------------------------------------------------------------
    // Access token – happy path
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Access token verifies against the JWKS public key")
    void accessToken_verifiesAgainstPublicKey() {
        String token = jwtIssuer.issueAccessToken("usr-001", "alice@example.com", Roles.CUSTOMER, null);

        // Must not throw – verifies RS256 signature and expiry
        var jwt = decoder.decode(token);
        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("usr-001");
    }

    @Test
    @DisplayName("Access token carries sub, email, role, roles claims")
    void accessToken_carriesRequiredClaims() throws ParseException {
        String token = jwtIssuer.issueAccessToken("usr-002", "bob@example.com", Roles.COURIER, null);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("usr-002");
        assertThat(claims.getIssuer()).isEqualTo(JwtIssuer.ISSUER);
        assertThat(claims.getStringClaim(JwtClaims.EMAIL)).isEqualTo("bob@example.com");
        assertThat(claims.getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.COURIER);
        assertThat(claims.getStringListClaim(JwtClaims.ROLES)).containsExactly("ROLE_COURIER");
        assertThat(claims.getStringClaim(JwtClaims.MERCHANT_ID)).isNull();
    }

    @Test
    @DisplayName("Access token for MERCHANT role includes merchantId claim")
    void accessToken_merchantRoleIncludesMerchantId() throws ParseException {
        String token = jwtIssuer.issueAccessToken("usr-003", "shop@daraz.lk", Roles.MERCHANT, "mch-9901");

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.MERCHANT);
        assertThat(claims.getStringClaim(JwtClaims.MERCHANT_ID)).isEqualTo("mch-9901");
    }

    @Test
    @DisplayName("Access token uses RS256 algorithm and correct kid in header")
    void accessToken_usesRS256WithCorrectKid() throws ParseException {
        String token = jwtIssuer.issueAccessToken("usr-004", "d@example.com", Roles.DISPATCHER, null);

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(JwtKeyProvider.DEV_KEY_ID);
    }

    @Test
    @DisplayName("Access token lifetime is 15 minutes")
    void accessToken_lifetimeIs15Minutes() throws ParseException {
        String token = jwtIssuer.issueAccessToken("usr-005", "e@example.com", Roles.CUSTOMER, null);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
        // JWT timestamps are truncated to whole seconds, so compare only the delta
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;

        assertThat(ttlSeconds).isEqualTo(JwtIssuer.ACCESS_TOKEN_TTL.getSeconds());
    }

    // ----------------------------------------------------------------
    // Client-credentials token
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Client-credentials token verifies against the JWKS public key")
    void clientCredentials_verifiesAgainstPublicKey() {
        String token = jwtIssuer.issueClientCredentialsToken(
                "sd_live_client_abc", "mch-5501", List.of("parcels:create", "parcels:read"));

        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("sd_live_client_abc");
    }

    @Test
    @DisplayName("Client-credentials token carries merchantId and scope claims")
    void clientCredentials_carriesRequiredClaims() throws ParseException {
        List<String> scopes = List.of("parcels:create", "parcels:read");
        String token = jwtIssuer.issueClientCredentialsToken("sd_client_xyz", "mch-7701", scopes);

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("sd_client_xyz");
        assertThat(claims.getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.MERCHANT);
        assertThat(claims.getStringClaim(JwtClaims.MERCHANT_ID)).isEqualTo("mch-7701");
        assertThat(claims.getStringClaim(JwtClaims.SCOPE)).isEqualTo("parcels:create parcels:read");
    }

    @Test
    @DisplayName("Client-credentials token lifetime is 1 hour")
    void clientCredentials_lifetimeIsOneHour() throws ParseException {
        String token = jwtIssuer.issueClientCredentialsToken("client-1", "mch-001", List.of("parcels:read"));

        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;

        assertThat(ttlSeconds).isEqualTo(JwtIssuer.CLIENT_CREDENTIALS_TTL.getSeconds());
    }

    // ----------------------------------------------------------------
    // JWKS public key exposure
    // ----------------------------------------------------------------

    @Test
    @DisplayName("JWKS contains only public key – no private key material exposed")
    void jwks_exposesOnlyPublicKey() {
        RSAKey publicJwk = keyProvider.getRsaKey().toPublicJWK();
        JWKSet jwkSet = new JWKSet(publicJwk);

        assertThat(jwkSet.getKeys()).hasSize(1);
        RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(JwtKeyProvider.DEV_KEY_ID);
        assertThat(key).isNotNull();
        assertThat(key.isPrivate()).isFalse();
        assertThat(key.getModulus()).isNotNull();
        assertThat(key.getPublicExponent()).isNotNull();
    }

    @Test
    @DisplayName("Dev key ID matches common DevJwtTokenMinter.DEV_KEY_ID")
    void keyId_matchesCommonDevKeyId() {
        // Ensures tokens minted by DevJwtTokenMinter can be verified using JwtKeyProvider
        assertThat(keyProvider.getRsaKey().getKeyID())
                .isEqualTo(com.swiftdrop.common.security.dev.DevJwtTokenMinter.DEV_KEY_ID);
    }
}
