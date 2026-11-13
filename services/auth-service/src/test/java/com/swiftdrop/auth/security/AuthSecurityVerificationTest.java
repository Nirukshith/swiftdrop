package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.AuthController;
import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.dto.LoginRequest;
import com.swiftdrop.auth.dto.RegisterRequest;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.internal.InternalUserController;
import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.internal.security.InternalServiceSecurityFilter;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.security.jwt.JwtKeyProvider;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security verification and hardening test suite for M10 Week 4 (Issue #172).
 * Verifies authentication lifecycle, session restoration, token rotation, reuse detection,
 * role authorization, spoofed header rejection, and internal service access control.
 */
@ExtendWith(MockitoExtension.class)
class AuthSecurityVerificationTest {

    private MockMvc authMockMvc;
    private MockMvc internalMockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginGuard loginGuard;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthEventPublisher authEventPublisher;

    private PasswordEncoder passwordEncoder;
    private JwtKeyProvider keyProvider;
    private JwtIssuer jwtIssuer;
    private AuthRateLimiter rateLimiter;
    private ObjectMapper objectMapper;

    private static final String NOTIF_TOKEN = "swiftdrop-internal-notif-secret-2026";
    private static final String MERCHANT_TOKEN = "swiftdrop-internal-merchant-secret-2026";
    private static final String INTERNAL_HEADER = "X-Internal-Service-Token";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        keyProvider = new JwtKeyProvider("", "", JwtKeyProvider.DEV_KEY_ID);
        jwtIssuer = new JwtIssuer(keyProvider);
        rateLimiter = new AuthRateLimiter();
        objectMapper = new ObjectMapper();

        AuthController authController = new AuthController(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtIssuer,
                loginGuard,
                auditService,
                rateLimiter,
                authEventPublisher,
                null,
                keyProvider
        );

        authMockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        InternalUserController internalUserController = new InternalUserController(userRepository, passwordEncoder,
                org.mockito.Mockito.mock(com.swiftdrop.auth.internal.MerchantProvisioningGuard.class));
        InternalServiceSecurityFilter internalFilter = new InternalServiceSecurityFilter(NOTIF_TOKEN, MERCHANT_TOKEN);
        internalMockMvc = MockMvcBuilders.standaloneSetup(internalUserController)
                .addFilters(internalFilter)
                .build();
    }

    // ========================================================================
    // 1. REGISTRATION LIFECYCLE & PASSWORD POLICY
    // ========================================================================

    @Nested
    @DisplayName("Registration Lifecycle Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Valid customer registration succeeds with hashed password, RS256 token and httpOnly cookie")
        void registration_success() throws Exception {
            when(userRepository.existsByEmailIgnoreCase("newuser@swiftdrop.lk")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RegisterRequest request = new RegisterRequest("newuser@swiftdrop.lk", "SecurePass1!", "Alice Walker", "+94771234567");

            MvcResult result = authMockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.email").value("newuser@swiftdrop.lk"))
                    .andExpect(jsonPath("$.user.role").value(Roles.CUSTOMER))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                    .andReturn();

            assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Path=/api/v1/auth");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(authEventPublisher).publishUserRegistered(anyString(), eq("newuser@swiftdrop.lk"), anyString(), anyString(), eq(Roles.CUSTOMER), any(), eq(true));
        }

        @Test
        @DisplayName("Weak password violating policy (< 8 chars or missing digits) is rejected with 400")
        void registration_weakPassword_rejected() throws Exception {
            RegisterRequest shortPass = new RegisterRequest("user@swiftdrop.lk", "Short1!", "User", null);
            authMockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shortPass)))
                    .andExpect(status().isBadRequest());

            RegisterRequest noDigitPass = new RegisterRequest("user@swiftdrop.lk", "PasswordOnly!", "User", null);
            authMockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(noDigitPass)))
                    .andExpect(status().isBadRequest());

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate email registration is rejected with 409 Conflict")
        void registration_duplicateEmail_rejected() throws Exception {
            when(userRepository.existsByEmailIgnoreCase("existing@swiftdrop.lk")).thenReturn(true);

            RegisterRequest request = new RegisterRequest("existing@swiftdrop.lk", "SecurePass1!", "User", null);
            authMockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(userRepository, never()).save(any());
        }
    }

    // ========================================================================
    // 2. LOGIN AUTHENTICATION & LOCKOUT
    // ========================================================================

    @Nested
    @DisplayName("Login Authentication Tests")
    class LoginTests {

        @Test
        @DisplayName("Valid credentials succeed and issue 15-minute access JWT with refresh cookie")
        void login_validCredentials_succeeds() throws Exception {
            User user = new User("usr-1", "user@swiftdrop.lk", passwordEncoder.encode("Secret123!"),
                    "Test User", null, Roles.CUSTOMER, true, null);
            when(userRepository.findByEmailIgnoreCase("user@swiftdrop.lk")).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest("user@swiftdrop.lk", "Secret123!");
            authMockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.role").value(Roles.CUSTOMER))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));

            verify(loginGuard).recordSuccess("user@swiftdrop.lk", "127.0.0.1");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Invalid password returns 401 and records failure in LoginGuard")
        void login_invalidPassword_returns401() throws Exception {
            User user = new User("usr-1", "user@swiftdrop.lk", passwordEncoder.encode("Secret123!"),
                    "Test User", null, Roles.CUSTOMER, true, null);
            when(userRepository.findByEmailIgnoreCase("user@swiftdrop.lk")).thenReturn(Optional.of(user));

            LoginRequest request = new LoginRequest("user@swiftdrop.lk", "WrongPass!");
            authMockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(loginGuard).recordFailure("user@swiftdrop.lk", "127.0.0.1");
        }

        @Test
        @DisplayName("Disabled user account returns 403 Forbidden")
        void login_disabledAccount_returns403() throws Exception {
            User disabledUser = new User("usr-disabled", "disabled@swiftdrop.lk", passwordEncoder.encode("Secret123!"),
                    "Disabled", null, Roles.CUSTOMER, false, null);
            when(userRepository.findByEmailIgnoreCase("disabled@swiftdrop.lk")).thenReturn(Optional.of(disabledUser));

            LoginRequest request = new LoginRequest("disabled@swiftdrop.lk", "Secret123!");
            authMockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Account locked by LoginGuard returns 423 Locked")
        void login_lockedAccount_returns423() throws Exception {
            doThrow(new AccountLockedException("Account temporarily locked"))
                    .when(loginGuard).checkLoginAllowed("locked@swiftdrop.lk", "127.0.0.1");

            LoginRequest request = new LoginRequest("locked@swiftdrop.lk", "Secret123!");
            authMockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        }
    }

    // ========================================================================
    // 3. REFRESH TOKEN LIFECYCLE, ROTATION & REUSE ATTACK DETECTION
    // ========================================================================

    @Nested
    @DisplayName("Refresh Token Lifecycle & Rotation Tests")
    class RefreshTokenLifecycleTests {

        @Test
        @DisplayName("Valid refresh rotates cookie and issues new access token")
        void refresh_validCookie_rotatesToken() throws Exception {
            String rawToken = "raw-refresh-token-1234";
            String tokenHash = hashToken(rawToken);

            User user = new User("usr-1", "user@swiftdrop.lk", "hash", "User", null, Roles.CUSTOMER, true, null);
            RefreshToken activeToken = new RefreshToken("tok-1", tokenHash, "usr-1", "fam-1", Instant.now().plus(Duration.ofDays(7)));

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activeToken));
            when(userRepository.findById("usr-1")).thenReturn(Optional.of(user));

            authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", rawToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("user@swiftdrop.lk"))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));

            assertThat(activeToken.isRevoked()).isTrue();
            assertThat(activeToken.getConsumedAt()).isNotNull();
            verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Reusing an already consumed refresh token triggers family revocation and returns 401")
        void refresh_reuseDetection_revokesFamily() throws Exception {
            String rawToken = "reused-token-5678";
            String tokenHash = hashToken(rawToken);

            RefreshToken alreadyConsumed = new RefreshToken("tok-old", tokenHash, "usr-1", "fam-compromised", Instant.now().plus(Duration.ofDays(7)));
            alreadyConsumed.setRevoked(true);
            alreadyConsumed.setConsumedAt(Instant.now().minusSeconds(60));

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(alreadyConsumed));

            MvcResult result = authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", rawToken)))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            verify(refreshTokenRepository).revokeByFamilyId("fam-compromised");
            assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        }

        @Test
        @DisplayName("Expired refresh token is revoked, cookie cleared, and returns 401")
        void refresh_expiredToken_returns401() throws Exception {
            String rawToken = "expired-token-9999";
            String tokenHash = hashToken(rawToken);

            RefreshToken expired = new RefreshToken("tok-exp", tokenHash, "usr-1", "fam-1", Instant.now().minusSeconds(10));
            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

            MvcResult result = authMockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", rawToken)))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertThat(expired.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(expired);
            assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        }
    }

    // ========================================================================
    // 4. LOGOUT & SESSION INVALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Logout invalidates active refresh token and clears cookie")
        void logout_invalidatesTokenAndClearsCookie() throws Exception {
            String rawToken = "logout-token-1111";
            String tokenHash = hashToken(rawToken);

            RefreshToken activeToken = new RefreshToken("tok-active", tokenHash, "usr-1", "fam-1", Instant.now().plus(Duration.ofDays(7)));
            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activeToken));

            MvcResult result = authMockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refresh_token", rawToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out successfully"))
                    .andReturn();

            assertThat(activeToken.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(activeToken);
            assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        }
    }

    // ========================================================================
    // 5. CURRENT USER PROFILE (/me) & SPOOFED HEADER INTEGRITY
    // ========================================================================

    @Nested
    @DisplayName("Current User Identity & Header Spoofing Protection")
    class CurrentUserTests {

        @Test
        @DisplayName("GET /me extracts authenticated identity from JWT sub and ignores spoofed X-User-Id header")
        void getCurrentUser_ignoresSpoofedHeaders() throws Exception {
            User legitimateUser = new User("usr-legit", "legit@swiftdrop.lk", "hash", "Legit User", null, Roles.CUSTOMER, true, null);
            when(userRepository.findById("usr-legit")).thenReturn(Optional.of(legitimateUser));

            String token = jwtIssuer.issueAccessToken("usr-legit", "legit@swiftdrop.lk", Roles.CUSTOMER, null);

            authMockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + token)
                            .header("X-User-Id", "usr-attacker-injected")
                            .header("X-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("usr-legit"))
                    .andExpect(jsonPath("$.email").value("legit@swiftdrop.lk"))
                    .andExpect(jsonPath("$.role").value(Roles.CUSTOMER));

            verify(userRepository).findById("usr-legit");
            verify(userRepository, never()).findById("usr-attacker-injected");
        }

        @Test
        @DisplayName("GET /me with disabled user account returns 403 Forbidden")
        void getCurrentUser_disabledUser_returns403() throws Exception {
            User disabledUser = new User("usr-disabled", "disabled@swiftdrop.lk", "hash", "Disabled", null, Roles.CUSTOMER, false, null);
            when(userRepository.findById("usr-disabled")).thenReturn(Optional.of(disabledUser));

            String token = jwtIssuer.issueAccessToken("usr-disabled", "disabled@swiftdrop.lk", Roles.CUSTOMER, null);

            authMockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /me without Bearer header returns 401 Unauthorized")
        void getCurrentUser_missingToken_returns401() throws Exception {
            authMockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // 6. INTERNAL SERVICE AUTHENTICATION & LEAST PRIVILEGE
    // ========================================================================

    @Nested
    @DisplayName("Internal Service Security Tests")
    class InternalServiceSecurityTests {

        @Test
        @DisplayName("Anonymous request to internal endpoint returns 401 Unauthorized")
        void internal_anonymous_returns401() throws Exception {
            internalMockMvc.perform(post("/internal/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Invalid internal service token returns 401 Unauthorized")
        void internal_invalidToken_returns401() throws Exception {
            internalMockMvc.perform(post("/internal/v1/users")
                            .header(INTERNAL_HEADER, "invalid-secret-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Ordinary user Bearer JWT cannot access internal service endpoints")
        void internal_userJwt_returns401() throws Exception {
            String token = jwtIssuer.issueAccessToken("usr-admin", "admin@swiftdrop.lk", Roles.ADMIN, null);
            internalMockMvc.perform(post("/internal/v1/users")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Notifications-service token is rejected for POST /internal/v1/users (least privilege)")
        void internal_leastPrivilege_notifCannotCreateUser() throws Exception {
            internalMockMvc.perform(post("/internal/v1/users")
                            .header(INTERNAL_HEADER, NOTIF_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Merchant-service token successfully provisions user on POST /internal/v1/users")
        void internal_merchantService_canCreateUser() throws Exception {
            when(userRepository.existsByEmailIgnoreCase("newmerchant@store.lk")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            InternalCreateUserRequest request = new InternalCreateUserRequest(
                    "newmerchant@store.lk",
                    "InitialPass1!",
                    "Store Owner",
                    "+94778888888",
                    Roles.MERCHANT,
                    "mch-8888",
                    true
            );

            internalMockMvc.perform(post("/internal/v1/users")
                            .header(INTERNAL_HEADER, MERCHANT_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("newmerchant@store.lk"))
                    .andExpect(jsonPath("$.role").value(Roles.MERCHANT))
                    .andExpect(jsonPath("$.merchantId").value("mch-8888"));
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
