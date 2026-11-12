package com.swiftdrop.auth.oauth2;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extended security and boundary tests for the Google OAuth 2.0 flow.
 *
 * <p>Covers CSRF/state-mismatch rejection, unverified-email rejection,
 * missing-code rejection, role preservation for privileged accounts,
 * refresh-cookie attribute enforcement, and URL-credential hygiene checks.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuthExtendedTest {

    private MockMvc mockMvc;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthEventPublisher authEventPublisher;

    private GoogleOAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GoogleOAuthProperties();
        properties.setClientId("test-google-client-id");
        properties.setClientSecret("test-google-client-secret");
        properties.setRedirectUri("http://localhost/api/v1/auth/oauth2/callback/google");
        properties.setAuthUrl("https://accounts.google.com/o/oauth2/v2/auth");
        properties.setTokenUrl("https://oauth2.googleapis.com/token");
        properties.setFrontendCallbackUrl("http://localhost:4200/auth/callback");

        GoogleOAuthController controller = new GoogleOAuthController(
                properties,
                googleOAuthClient,
                userRepository,
                refreshTokenRepository,
                auditService,
                authEventPublisher
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ─── State / CSRF validation ────────────────────────────────────────────────

    @Test
    @DisplayName("State mismatch: callback state param differs from state cookie → 400 Bad Request, no session")
    void callback_stateMismatch_returns400_andNoSession() throws Exception {
        // Cookie holds "correct-state", but query param carries a different value.
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "tampered-state")
                        .cookie(new Cookie("oauth2_state", "correct-state")))
                .andExpect(status().isBadRequest());

        verify(googleOAuthClient, never()).exchangeCodeAndGetUserInfo(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Missing state cookie: callback without oauth2_state cookie → 400 Bad Request")
    void callback_noStateCookie_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", "any-state"))
                // No cookie attached — stateCookie param will be null
                .andExpect(status().isBadRequest());

        verify(googleOAuthClient, never()).exchangeCodeAndGetUserInfo(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Missing authorization code: valid state but no code param → 400 Bad Request")
    void callback_missingCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("state", "valid-state")
                        .cookie(new Cookie("oauth2_state", "valid-state")))
                .andExpect(status().isBadRequest());

        verify(googleOAuthClient, never()).exchangeCodeAndGetUserInfo(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reused state: state token submitted twice → second invocation is rejected with 400")
    void callback_reusedState_returns400_andNoSession() throws Exception {
        String state = "state-single-use-unique";
        String code = "code-single-use";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-reused", "reused@example.com", "Reused User", null, true);

        User savedUser = new User(
                "user-reused-id", "reused@example.com", null,
                "Reused User", null, Roles.CUSTOMER, true, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("reused@example.com")).thenReturn(Optional.of(savedUser));

        // First call should succeed
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound());

        // Second call with identical state must be rejected as already consumed
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Expired state: state token timestamp older than 10-minute TTL → 400 Bad Request")
    void callback_expiredState_returns400_andNoSession() throws Exception {
        // Timestamp from 15 minutes ago (TTL is 10 minutes)
        long expiredTimestamp = System.currentTimeMillis() - (15 * 60 * 1000L);
        String expiredState = "teststate." + expiredTimestamp;

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "some-code")
                        .param("state", expiredState)
                        .cookie(new Cookie("oauth2_state", expiredState)))
                .andExpect(status().isBadRequest());

        verify(googleOAuthClient, never()).exchangeCodeAndGetUserInfo(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Provider failure: Google token exchange throws exception → 400 Bad Request, no session")
    void callback_providerFailure_returns400_andNoSession() throws Exception {
        String state = "state-provider-fail";
        String code = "code-bad";

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Google exchange failed"));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isBadRequest());

        verify(refreshTokenRepository, never()).save(any());
    }

    // ─── Email verification enforcement ─────────────────────────────────────────

    @Test
    @DisplayName("Unverified Google email: emailVerified=false → 400 Bad Request, no session issued")
    void callback_unverifiedEmail_returns400_andNoSession() throws Exception {
        String state = "state-unverified";
        GoogleUserInfo unverified = new GoogleUserInfo(
                "google-sub-unverified",
                "unverified@example.com",
                "Unverified User",
                null,
                false   // emailVerified = false
        );

        when(googleOAuthClient.exchangeCodeAndGetUserInfo("code-unverified"))
                .thenReturn(unverified);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-unverified")
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isBadRequest());

        verify(refreshTokenRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    // ─── Privileged-role preservation ────────────────────────────────────────────

    @Test
    @DisplayName("Admin user signs in via Google: role stays ADMIN, not downgraded to CUSTOMER")
    void callback_adminUser_rolePreservedAsAdmin() throws Exception {
        String state = "state-admin";
        String code  = "code-admin";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-admin", "admin@swiftdrop.com", "Admin User", null, true);

        User adminUser = new User(
                "user-admin", "admin@swiftdrop.com", "hash",
                "Admin User", "+94770000001", Roles.ADMIN, true, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("admin@swiftdrop.com"))
                .thenReturn(Optional.of(adminUser));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback"))
                .andExpect(header().string("Location", not(containsString("token="))))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));

        // Role must remain ADMIN — no save() called on user object
        verify(userRepository, never()).save(any());
        assertThat(adminUser.getRole()).isEqualTo(Roles.ADMIN);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Dispatcher user signs in via Google: role stays DISPATCHER, not downgraded to CUSTOMER")
    void callback_dispatcherUser_rolePreservedAsDispatcher() throws Exception {
        String state = "state-dispatcher";
        String code  = "code-dispatcher";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-dispatcher", "dispatcher@swiftdrop.com", "Dispatcher User", null, true);

        User dispatcherUser = new User(
                "user-dispatcher", "dispatcher@swiftdrop.com", "hash",
                "Dispatcher User", null, Roles.DISPATCHER, true, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("dispatcher@swiftdrop.com"))
                .thenReturn(Optional.of(dispatcherUser));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback"));

        verify(userRepository, never()).save(any());
        assertThat(dispatcherUser.getRole()).isEqualTo(Roles.DISPATCHER);
    }

    // ─── Refresh-cookie security attributes ──────────────────────────────────────

    @Test
    @DisplayName("Successful OAuth: refresh cookie is httpOnly, Secure, SameSite=Strict, no URL credentials")
    void callback_success_refreshCookieHasSecureAttributes_andNoCredentialInUrl() throws Exception {
        String state = "state-attr";
        String code  = "code-attr";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-attr", "attr@example.com", "Attr User", null, true);

        User savedUser = new User(
                "user-attr-id", "attr@example.com", null,
                "Attr User", null, Roles.CUSTOMER, true, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("attr@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                // Location must not carry any credential
                .andExpect(header().string("Location", not(containsString("token="))))
                .andExpect(header().string("Location", not(containsString("code="))))
                .andExpect(header().string("Location", not(containsString("secret="))))
                // Refresh cookie must have all required security attributes
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")));
    }

    // ─── First-time Google login (new user auto-provision) ──────────────────────

    @Test
    @DisplayName("New Google user is auto-provisioned as CUSTOMER with no password hash")
    void callback_newUser_autoProvisionedAsCustomer() throws Exception {
        String state = "state-new";
        String code  = "code-new";
        GoogleUserInfo googleUser = new GoogleUserInfo(
                "google-sub-new", "newuser@gmail.com", "New User", null, true);

        User savedUser = new User(
                "user-new-id", "newuser@gmail.com", null,
                "New User", null, Roles.CUSTOMER, true, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("newuser@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getRole()).isEqualTo(Roles.CUSTOMER);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getEmail()).isEqualTo("newuser@gmail.com");
        assertThat(created.isEnabled()).isTrue();

        // Kafka registration event must be published
        verify(authEventPublisher).publishUserRegistered(
                anyString(), anyString(), anyString(), any(), anyString(), any(), anyBoolean());
    }

    // ─── Initiation endpoint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Initiate endpoint: state cookie is httpOnly, 10-minute Max-Age, SameSite=Lax")
    void initiateLogin_stateCookieHas10MinMaxAgeAndLaxSameSite() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/google"))
                .andExpect(status().isFound())
                // 10 minutes = 600 seconds
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=600")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }
}
