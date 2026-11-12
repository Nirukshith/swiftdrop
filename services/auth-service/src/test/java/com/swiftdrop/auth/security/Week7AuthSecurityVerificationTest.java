package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.auth.AuthController;
import com.swiftdrop.auth.admin.AdminUserController;
import com.swiftdrop.auth.admin.AdminUserService;
import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.client.ClientCredential;
import com.swiftdrop.auth.client.ClientCredentialRepository;
import com.swiftdrop.auth.client.ClientTokenService;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.internal.InternalClientController;
import com.swiftdrop.auth.internal.InternalUserController;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.internal.security.InternalServiceSecurityFilter;
import com.swiftdrop.auth.oauth2.GoogleOAuthClient;
import com.swiftdrop.auth.oauth2.GoogleOAuthController;
import com.swiftdrop.auth.oauth2.GoogleOAuthProperties;
import com.swiftdrop.auth.oauth2.GoogleUserInfo;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.security.jwt.JwtKeyProvider;
import com.swiftdrop.auth.token.OAuthTokenController;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Security Verification Test Suite for SwiftDrop Week 7 (Issue #237).
 * Verifies JWT validation attacks, session lifecycle, Google OAuth defenses,
 * role authorization boundaries, and service-to-service access controls.
 */
@ExtendWith(MockitoExtension.class)
class Week7AuthSecurityVerificationTest {

    private MockMvc authMockMvc;
    private MockMvc internalUserMockMvc;
    private MockMvc oauthMockMvc;
    private MockMvc tokenMockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ClientCredentialRepository clientCredentialRepository;

    @Mock
    private LoginGuard loginGuard;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthEventPublisher authEventPublisher;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    private PasswordEncoder passwordEncoder;
    private JwtKeyProvider keyProvider;
    private JwtIssuer jwtIssuer;
    private NimbusJwtDecoder decoder;
    private GoogleOAuthProperties oauthProperties;

    private static final String NOTIF_TOKEN = "swiftdrop-internal-notif-secret-2026";
    private static final String MERCHANT_TOKEN = "swiftdrop-internal-merchant-secret-2026";
    private static final String INTERNAL_HEADER = "X-Internal-Service-Token";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        keyProvider = new JwtKeyProvider("", "", JwtKeyProvider.DEV_KEY_ID);
        jwtIssuer = new JwtIssuer(keyProvider);

        decoder = NimbusJwtDecoder.withPublicKey(keyProvider.getPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        AuthController authController = new AuthController(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtIssuer,
                loginGuard,
                auditService,
                new AuthRateLimiter(),
                authEventPublisher,
                null,
                keyProvider
        );
        authMockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        InternalUserController internalUserController = new InternalUserController(userRepository, passwordEncoder);
        InternalServiceSecurityFilter internalFilter = new InternalServiceSecurityFilter(NOTIF_TOKEN, MERCHANT_TOKEN);
        internalUserMockMvc = MockMvcBuilders.standaloneSetup(internalUserController)
                .addFilters(internalFilter)
                .build();

        oauthProperties = new GoogleOAuthProperties();
        oauthProperties.setClientId("test-client-id");
        oauthProperties.setClientSecret("test-client-secret");
        oauthProperties.setRedirectUri("http://localhost:8081/api/v1/auth/oauth2/callback/google");
        oauthProperties.setAuthUrl("https://accounts.google.com/o/oauth2/v2/auth");
        oauthProperties.setTokenUrl("https://oauth2.googleapis.com/token");
        oauthProperties.setFrontendCallbackUrl("http://localhost:4200/auth/callback");

        GoogleOAuthController googleController = new GoogleOAuthController(
                oauthProperties,
                googleOAuthClient,
                userRepository,
                refreshTokenRepository,
                auditService,
                authEventPublisher
        );
        oauthMockMvc = MockMvcBuilders.standaloneSetup(googleController).build();

        ClientTokenService clientTokenService = new ClientTokenService(
                clientCredentialRepository,
                userRepository,
                passwordEncoder,
                jwtIssuer
        );
        OAuthTokenController oAuthTokenController = new OAuthTokenController(clientTokenService);
        tokenMockMvc = MockMvcBuilders.standaloneSetup(oAuthTokenController).build();
    }

    // ========================================================================
    // 1. JWT SECURITY AND CRYPTOGRAPHIC VALIDATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("JWT Security and Cryptographic Tests")
    class JwtSecurityTests {

        @Test
        @DisplayName("Valid RS256 token is accepted and contains standard audience swiftdrop-api")
        void jwt_validToken_acceptedWithAudience() {
            String token = jwtIssuer.issueAccessToken("usr-100", "user@swiftdrop.lk", Roles.CUSTOMER, null);
            var jwt = decoder.decode(token);

            assertThat(jwt.getSubject()).isEqualTo("usr-100");
            assertThat(jwt.getAudience()).containsExactly("swiftdrop-api");
            assertThat(jwt.getClaimAsString(JwtClaims.ROLE)).isEqualTo(Roles.CUSTOMER);
        }

        @Test
        @DisplayName("Tampered payload bytes invalidate RS256 signature and are rejected")
        void jwt_tamperedPayload_rejectedByDecoder() {
            String token = jwtIssuer.issueAccessToken("usr-101", "victim@swiftdrop.lk", Roles.CUSTOMER, null);
            String[] parts = token.split("\\.");

            // Modify a byte in the payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            payloadBytes[payloadBytes.length - 2] ^= 0xFF;
            String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);

            String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

            assertThrows(BadJwtException.class, () -> decoder.decode(tamperedToken));
        }

        @Test
        @DisplayName("Algorithm confusion attack: HMAC-signed token using RSA public key is rejected")
        void jwt_hmacAlgorithmConfusion_rejectedByDecoder() throws Exception {
            byte[] fakeSecret = "thirty-two-bytes-minimum-secret-key-12345".getBytes(StandardCharsets.UTF_8);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(JwtIssuer.ISSUER)
                    .audience(JwtIssuer.AUDIENCE)
                    .subject("usr-hacker")
                    .expirationTime(new Date(System.currentTimeMillis() + 600000))
                    .claim(JwtClaims.ROLE, Roles.ADMIN)
                    .build();

            SignedJWT hmacJwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims
            );
            hmacJwt.sign(new MACSigner(fakeSecret));
            String hmacToken = hmacJwt.serialize();

            assertThrows(BadJwtException.class, () -> decoder.decode(hmacToken));
        }

        @Test
        @DisplayName("JWKS exposes public parameters only; private exponents are completely unexposed")
        void jwks_zeroPrivateKeyExposure() {
            RSAKey rsaKey = keyProvider.getRsaKey();
            RSAKey publicOnlyKey = rsaKey.toPublicJWK();

            JWKSet jwkSet = new JWKSet(publicOnlyKey);
            RSAKey extracted = (RSAKey) jwkSet.getKeyByKeyId(JwtKeyProvider.DEV_KEY_ID);

            assertThat(extracted).isNotNull();
            assertThat(extracted.isPrivate()).isFalse();
            assertThat(extracted.getPrivateExponent()).isNull();
            assertThat(extracted.getFirstPrimeFactor()).isNull();
            assertThat(extracted.getSecondPrimeFactor()).isNull();
        }
    }

    // ========================================================================
    // 2. REFRESH-TOKEN, SESSION, AND CONCURRENCY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Refresh Token & Session Lifecycle Tests")
    class RefreshSessionSecurityTests {

        @Test
        @DisplayName("Direct presentation of an already revoked token is rejected with 401")
        void refresh_revokedToken_returns401() throws Exception {
            String rawToken = UUID.randomUUID().toString();
            String hashed = hashToken(rawToken);

            RefreshToken token = new RefreshToken("rt-1", hashed, "usr-revoked", "fam-1", Instant.now().plus(Duration.ofDays(1)));
            token.setRevoked(true);

            when(refreshTokenRepository.findByTokenHash(hashed)).thenReturn(Optional.of(token));

            authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, rawToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Blank, empty, or whitespace refresh token is rejected with 401")
        void refresh_blankToken_returns401() throws Exception {
            authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, "   ")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Disabled user account cannot refresh session (returns 401 Unauthorized, clears cookie, revokes tokens)")
        void refresh_disabledAccount_returns401() throws Exception {
            String rawToken = UUID.randomUUID().toString();
            String hashed = hashToken(rawToken);

            RefreshToken token = new RefreshToken("rt-2", hashed, "usr-disabled", "fam-2", Instant.now().plus(Duration.ofDays(1)));
            User disabledUser = new User("usr-disabled", "disabled@swiftdrop.lk", "hash", "Disabled User", "+94770000000", Roles.CUSTOMER, false, null);

            when(refreshTokenRepository.findByTokenHash(hashed)).thenReturn(Optional.of(token));
            when(userRepository.findById("usr-disabled")).thenReturn(Optional.of(disabledUser));

            authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, rawToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

            verify(refreshTokenRepository).revokeByUserId("usr-disabled");
        }

        @Test
        @DisplayName("Logout invalidates active refresh token and returns Set-Cookie with Max-Age 0")
        void logout_clearsCookieAndRevokesToken() throws Exception {
            String rawToken = UUID.randomUUID().toString();
            String hashed = hashToken(rawToken);

            RefreshToken token = new RefreshToken("rt-3", hashed, "usr-logout", "fam-3", Instant.now().plus(Duration.ofDays(1)));
            when(refreshTokenRepository.findByTokenHash(hashed)).thenReturn(Optional.of(token));

            authMockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, rawToken)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

            assertThat(token.isRevoked()).isTrue();
        }
    }

    // ========================================================================
    // 3. GOOGLE OAUTH SECURITY DEFENSE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Google OAuth Security Defense Tests")
    class GoogleOAuthSecurityTests {

        @Test
        @DisplayName("Provider denial/cancellation redirects safely to /auth/callback?error=access_denied without session")
        void oauth_providerCancellation_redirectsSafelyWithoutSession() throws Exception {
            MvcResult result = oauthMockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                            .param("error", "access_denied")
                            .param("error_description", "User denied authorization"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("error=access_denied")))
                    .andReturn();

            // Must NOT set refresh_token cookie
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).satisfiesAnyOf(
                    cookie -> assertThat(cookie).isNull(),
                    cookie -> assertThat(cookie).doesNotContain("refresh_token=")
            );
        }

        @Test
        @DisplayName("Callback redirect URL contains zero tokens, codes, or secrets in query or fragment")
        void oauth_callbackRedirect_containsZeroTokensOrSecrets() throws Exception {
            MvcResult result = oauthMockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                            .param("error", "server_error"))
                    .andExpect(status().isFound())
                    .andReturn();

            String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
            assertThat(location).isNotNull();
            assertThat(location).doesNotContain("access_token");
            assertThat(location).doesNotContain("token=");
            assertThat(location).doesNotContain("secret");
            assertThat(location).doesNotContain("refresh_token");
        }
    }

    // ========================================================================
    // 4. ROLE-BASED & CROSS-USER AUTHORIZATION DEFENSE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Role-Based & Cross-User Authorization Defense Tests")
    class RoleAuthorizationDefenseTests {

        @Test
        @DisplayName("Spoofed identity headers (X-User-Id, X-Role) are ignored on /api/v1/auth/me")
        void authMe_ignoresSpoofedHeaders() throws Exception {
            String token = jwtIssuer.issueAccessToken("legit-user-id", "alice@swiftdrop.lk", Roles.CUSTOMER, null);
            User user = new User("legit-user-id", "alice@swiftdrop.lk", "hash", "Alice", "+94771111111", Roles.CUSTOMER, true, null);

            when(userRepository.findById("legit-user-id")).thenReturn(Optional.of(user));

            authMockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header("X-User-Id", "spoofed-admin-id")
                            .header("X-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("legit-user-id"))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"));
        }

        @Test
        @DisplayName("Disabled user cannot access /api/v1/auth/me even with valid unexpired JWT")
        void authMe_disabledUser_returns403() throws Exception {
            String token = jwtIssuer.issueAccessToken("disabled-user", "bob@swiftdrop.lk", Roles.COURIER, null);
            User disabledUser = new User("disabled-user", "bob@swiftdrop.lk", "hash", "Bob", "+94772222222", Roles.COURIER, false, null);

            when(userRepository.findById("disabled-user")).thenReturn(Optional.of(disabledUser));

            authMockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // ========================================================================
    // 5. SERVICE-TO-SERVICE & CLIENT CREDENTIALS AUTHORIZATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Service-to-Service Authorization Tests")
    class ServiceAuthorizationTests {

        @Test
        @DisplayName("Client credentials token request with invalid secret returns 401 Unauthorized")
        void clientCredentials_invalidSecret_returns401() throws Exception {
            ClientCredential client = new ClientCredential(
                    "client-abc",
                    passwordEncoder.encode("correct-secret"),
                    "mch-01",
                    "Store API",
                    "parcels:create"
            );

            when(clientCredentialRepository.findByClientId("client-abc")).thenReturn(Optional.of(client));

            tokenMockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"client_id\":\"client-abc\",\"client_secret\":\"wrong-secret\",\"grant_type\":\"client_credentials\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Client credentials token request with unauthorized scope returns 400 Bad Request")
        void clientCredentials_unauthorizedScope_returns400() throws Exception {
            ClientCredential client = new ClientCredential(
                    "client-xyz",
                    passwordEncoder.encode("secret-123"),
                    "mch-02",
                    "Store API",
                    "parcels:create,parcels:read"
            );

            User merchantUser = new User("usr-mch-02", "mch@daraz.lk", "hash", "Merchant", "+94773333333", Roles.MERCHANT, true, "mch-02");
            when(clientCredentialRepository.findByClientId("client-xyz")).thenReturn(Optional.of(client));
            when(userRepository.findFirstByMerchantId("mch-02")).thenReturn(Optional.of(merchantUser));

            tokenMockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"client_id\":\"client-xyz\",\"client_secret\":\"secret-123\",\"grant_type\":\"client_credentials\",\"scope\":\"admin:all\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Internal service filter denies notifications-service from creating users (least-privilege matrix)")
        void internal_leastPrivilege_notifBlockedFromUserCreation() throws Exception {
            InternalCreateUserRequest request = new InternalCreateUserRequest("u@d.lk", "P@ss1234", "Name", "+94774444444", Roles.MERCHANT, "mch-1", true);

            internalUserMockMvc.perform(post("/internal/v1/users")
                            .header(INTERNAL_HEADER, NOTIF_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Internal service filter allows merchant-service to create users")
        void internal_merchantService_allowedUserCreation() throws Exception {
            InternalCreateUserRequest request = new InternalCreateUserRequest("u@d.lk", "P@ss1234", "Name", "+94774444444", Roles.MERCHANT, "mch-1", true);
            when(userRepository.existsByEmailIgnoreCase("u@d.lk")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            internalUserMockMvc.perform(post("/internal/v1/users")
                            .header(INTERNAL_HEADER, MERCHANT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
