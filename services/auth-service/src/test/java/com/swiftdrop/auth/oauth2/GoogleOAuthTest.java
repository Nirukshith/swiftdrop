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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthTest {

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

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google sets state cookie and redirects to Google auth URL")
    void initiateGoogleLogin_setsStateCookieAndRedirects() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/google"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", containsString("client_id=test-google-client-id")))
                .andExpect(header().string("Location", containsString("scope=openid%20email%20profile")))
                .andExpect(header().string("Location", containsString("response_type=code")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Set-Cookie", containsString("oauth2_state=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth/oauth2")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    @DisplayName("Callback with error parameter from Google redirects to frontend with error and clears state")
    void callback_withGoogleError_redirectsWithCleanCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback?error=access_denied"))
                .andExpect(header().string("Set-Cookie", containsString("oauth2_state=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("Callback with missing state cookie returns 400 Bad Request")
    void callback_missingStateCookie_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "valid-code")
                        .param("state", "some-state"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Callback with mismatched state parameter returns 400 Bad Request")
    void callback_mismatchedState_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "valid-code")
                        .param("state", "state-A")
                        .cookie(new Cookie("oauth2_state", "state-B")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Callback with missing authorization code returns 400 Bad Request")
    void callback_missingCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("state", "matching-state")
                        .cookie(new Cookie("oauth2_state", "matching-state")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Callback with unverified email returns 400 Bad Request")
    void callback_unverifiedEmail_returns400() throws Exception {
        String state = "test-state-123";
        when(googleOAuthClient.exchangeCodeAndGetUserInfo("unverified-code"))
                .thenReturn(new GoogleUserInfo("sub123", "unverified@gmail.com", "Test User", null, false));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "unverified-code")
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("First-time Google user creates CUSTOMER account, sets refresh cookie, and redirects with NO credentials in URL")
    void callback_firstTimeUser_createsCustomerAndRedirectsSecurely() throws Exception {
        String state = "valid-state-abc";
        String code = "valid-code-xyz";
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-1", "john.doe@gmail.com", "John Doe", null, true);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("john.doe@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback"))
                // Crucial security check: NO tokens, codes, or secrets in URL
                .andExpect(header().string("Location", not(containsString("token="))))
                .andExpect(header().string("Location", not(containsString("code="))))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")));

        // Verify user creation with CUSTOMER role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("john.doe@gmail.com");
        assertThat(createdUser.getFullName()).isEqualTo("John Doe");
        assertThat(createdUser.getRole()).isEqualTo(Roles.CUSTOMER);
        assertThat(createdUser.isEnabled()).isTrue();
        assertThat(createdUser.getPasswordHash()).isNull();

        // Verify Kafka event published
        verify(authEventPublisher).publishUserRegistered(
                eq(createdUser.getId()),
                eq("john.doe@gmail.com"),
                eq("John Doe"),
                eq(null),
                eq(Roles.CUSTOMER),
                eq(null),
                eq(true)
        );

        // Verify refresh token saved
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Existing user preserves role, issues refresh cookie, and redirects with clean URL")
    void callback_existingUser_preservesRoleAndRedirects() throws Exception {
        String state = "state-999";
        String code = "code-999";
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-2", "existing@swiftdrop.com", "Existing Merchant", null, true);

        User existingUser = new User("user-100", "existing@swiftdrop.com", "hash", "Existing Merchant",
                "+94770001122", Roles.MERCHANT, true, "m-123");

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("existing@swiftdrop.com")).thenReturn(Optional.of(existingUser));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback"))
                .andExpect(header().string("Location", not(containsString("token="))))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));

        // User role is NOT modified or downgraded
        verify(userRepository, never()).save(any());
        assertThat(existingUser.getRole()).isEqualTo(Roles.MERCHANT);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Disabled user is rejected and redirected with error parameter without session cookie")
    void callback_disabledUser_redirectsWithError() throws Exception {
        String state = "state-dis";
        String code = "code-dis";
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-3", "disabled@swiftdrop.com", "Disabled User", null, true);

        User disabledUser = new User("user-dis", "disabled@swiftdrop.com", null, "Disabled User",
                null, Roles.CUSTOMER, false, null);

        when(googleOAuthClient.exchangeCodeAndGetUserInfo(code)).thenReturn(googleUser);
        when(userRepository.findByEmailIgnoreCase("disabled@swiftdrop.com")).thenReturn(Optional.of(disabledUser));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", code)
                        .param("state", state)
                        .cookie(new Cookie("oauth2_state", state)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:4200/auth/callback?error=account_disabled"))
                .andExpect(header().string("Set-Cookie", containsString("oauth2_state=;")));

        verify(refreshTokenRepository, never()).save(any());
    }
}

