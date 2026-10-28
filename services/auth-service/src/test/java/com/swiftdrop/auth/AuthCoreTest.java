package com.swiftdrop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.auth.dto.LoginRequest;
import com.swiftdrop.auth.dto.RefreshTokenRequest;
import com.swiftdrop.auth.dto.RegisterRequest;
import com.swiftdrop.auth.security.AccountLockedException;
import com.swiftdrop.auth.security.AuthRateLimiter;
import com.swiftdrop.auth.security.LoginGuard;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthCoreTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @MockitoBean
    private LoginGuard loginGuard;

    @MockitoBean
    private com.swiftdrop.auth.event.AuthEventPublisher authEventPublisher;

    @BeforeEach
    void setUp() {
        authRateLimiter.reset();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(loginGuard).checkLoginAllowed(anyString());
        doNothing().when(loginGuard).checkLoginAllowed(anyString(), anyString());
        doNothing().when(loginGuard).recordSuccess(anyString());
        doNothing().when(loginGuard).recordSuccess(anyString(), anyString());
        doNothing().when(loginGuard).recordFailure(anyString());
        doNothing().when(loginGuard).recordFailure(anyString(), anyString());
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Successful customer registration returns 201, JWT access token, and httpOnly cookie")
        void successfulRegistration() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Customer@SwiftDrop.com",
                    "StrongP@ss123",
                    "Jane Doe",
                    "+94770001122"
            );

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.expiresIn", is(900)))
                    .andExpect(jsonPath("$.user.email", is("customer@swiftdrop.com")))
                    .andExpect(jsonPath("$.user.fullName", is("Jane Doe")))
                    .andExpect(jsonPath("$.user.role", is(Roles.CUSTOMER)))
                    .andReturn();

            User user = userRepository.findByEmailIgnoreCase("customer@swiftdrop.com").orElseThrow();
            assertThat(user.isEnabled()).isTrue();
            assertThat(passwordEncoder.matches("StrongP@ss123", user.getPasswordHash())).isTrue();
            verify(authEventPublisher).publishUserRegistered(
                    org.mockito.ArgumentMatchers.eq(user.getId()),
                    org.mockito.ArgumentMatchers.eq("customer@swiftdrop.com"),
                    org.mockito.ArgumentMatchers.eq("Jane Doe"),
                    org.mockito.ArgumentMatchers.eq("+94770001122"),
                    org.mockito.ArgumentMatchers.eq(Roles.CUSTOMER),
                    org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq(true)
            );
        }

        @Test
        @DisplayName("Rejects weak password during registration")
        void rejectsWeakPassword() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "customer@swiftdrop.com",
                    "weak",
                    "Jane Doe",
                    null
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Duplicate email registration returns 409 Conflict")
        void duplicateEmailReturns409() throws Exception {
            User existing = new User("usr-1", "test@swiftdrop.com", "hash", "Test", null, Roles.CUSTOMER, true, null);
            userRepository.save(existing);

            RegisterRequest request = new RegisterRequest(
                    "TEST@swiftdrop.com",
                    "StrongP@ss123",
                    "Another Name",
                    null
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Successful login returns access token, user details, sets refresh cookie and records success in LoginGuard")
        void successfulLogin() throws Exception {
            User user = new User("usr-login-1", "user@swiftdrop.com", passwordEncoder.encode("Secret123!"),
                    "Test User", null, Roles.CUSTOMER, true, null);
            userRepository.save(user);

            LoginRequest request = new LoginRequest("user@swiftdrop.com", "Secret123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.user.id", is("usr-login-1")))
                    .andExpect(jsonPath("$.user.email", is("user@swiftdrop.com")));

            verify(loginGuard).recordSuccess(anyString(), anyString());
        }

        @Test
        @DisplayName("Failed login with invalid password returns 401 and records failure in LoginGuard")
        void failedLoginWrongPassword() throws Exception {
            User user = new User("usr-login-2", "user2@swiftdrop.com", passwordEncoder.encode("Correct123!"),
                    "Test User", null, Roles.CUSTOMER, true, null);
            userRepository.save(user);

            LoginRequest request = new LoginRequest("user2@swiftdrop.com", "WrongPassword123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(loginGuard).recordFailure(anyString(), anyString());
        }

        @Test
        @DisplayName("Account locked by LoginGuard returns 423 Locked")
        void accountLockedReturns423() throws Exception {
            doThrow(new AccountLockedException("Account is temporarily locked"))
                    .when(loginGuard).checkLoginAllowed(anyString(), anyString());

            LoginRequest request = new LoginRequest("locked@swiftdrop.com", "Password123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is(423))
                    .andExpect(jsonPath("$.code", is("ACCOUNT_LOCKED")));
        }

        @Test
        @DisplayName("Disabled user cannot log in and returns 403 Forbidden")
        void disabledUserLoginReturns403() throws Exception {
            User disabled = new User("usr-disabled", "disabled@swiftdrop.com", passwordEncoder.encode("Secret123!"),
                    "Disabled User", null, Roles.CUSTOMER, false, null);
            userRepository.save(disabled);

            LoginRequest request = new LoginRequest("disabled@swiftdrop.com", "Secret123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Refresh Token Rotation and Reuse Detection Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Refresh token rotation issues new access token, rotates refresh cookie, and marks previous token consumed")
        void refreshRotation() throws Exception {
            // First register to get a valid refresh cookie
            RegisterRequest reg = new RegisterRequest("rotate@swiftdrop.com", "Rotate123!", "Rotate User", null);
            MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reg)))
                    .andExpect(status().isCreated())
                    .andReturn();

            Cookie cookie = regResult.getResponse().getCookie("refresh_token");
            assertThat(cookie).isNotNull();

            // Refresh token with cookie
            MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andReturn();

            Cookie newCookie = refreshResult.getResponse().getCookie("refresh_token");
            assertThat(newCookie).isNotNull();
            assertThat(newCookie.getValue()).isNotEqualTo(cookie.getValue());

            // Reuse detection: attempt to refresh AGAIN with the old cookie
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie))
                    .andExpect(status().isUnauthorized());

            // After reuse detection, the entire family is invalidated, so even the new token is now rejected
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(newCookie))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Disabled user cannot refresh access token")
        void disabledUserCannotRefresh() throws Exception {
            RegisterRequest reg = new RegisterRequest("disableme@swiftdrop.com", "Secret123!", "Disable Me", null);
            MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reg)))
                    .andExpect(status().isCreated())
                    .andReturn();

            Cookie cookie = regResult.getResponse().getCookie("refresh_token");

            // Disable the user in the database
            User user = userRepository.findByEmailIgnoreCase("disableme@swiftdrop.com").orElseThrow();
            user.setEnabled(false);
            userRepository.save(user);

            // Attempt to refresh
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("JWKS Verification and Claims")
    class JwksAndClaimsTests {

        @Test
        @DisplayName("JWKS endpoint verifies the signature and claims of issued access token")
        void jwksVerifiesIssuedToken() throws Exception {
            RegisterRequest reg = new RegisterRequest("claims@swiftdrop.com", "Secret123!", "Claims User", null);
            MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reg)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String tokenResponse = regResult.getResponse().getContentAsString();
            String jwt = objectMapper.readTree(tokenResponse).get("accessToken").asText();

            // Fetch JWKS
            MvcResult jwksResult = mockMvc.perform(get("/.well-known/jwks.json"))
                    .andExpect(status().isOk())
                    .andReturn();

            String jwksJson = jwksResult.getResponse().getContentAsString();
            JWKSet jwkSet = JWKSet.parse(jwksJson);
            RSAKey rsaKey = (RSAKey) jwkSet.getKeyByKeyId("swiftdrop-dev-key");
            assertThat(rsaKey).isNotNull();

            // Verify JWT signature using JWKS public key
            SignedJWT signedJWT = SignedJWT.parse(jwt);
            JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            assertThat(signedJWT.verify(verifier)).isTrue();

            // Verify standard claims
            var claims = signedJWT.getJWTClaimsSet();
            assertThat(claims.getIssuer()).isEqualTo(JwtIssuer.ISSUER);
            assertThat(claims.getStringClaim(JwtClaims.EMAIL)).isEqualTo("claims@swiftdrop.com");
            assertThat(claims.getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.CUSTOMER);
            assertThat(claims.getStringListClaim(JwtClaims.ROLES)).contains("ROLE_CUSTOMER");
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Exceeding login rate limit returns 429 Too Many Requests")
        void exceedingLoginRateLimit() throws Exception {
            authRateLimiter.setLoginLimit(3);

            LoginRequest request = new LoginRequest("rate@swiftdrop.com", "Secret123!");

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));
            }

            // 4th request exceeds rate limit
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Exceeding register rate limit returns 429 Too Many Requests")
        void exceedingRegisterRateLimit() throws Exception {
            authRateLimiter.setRegisterLimit(2);

            RegisterRequest request = new RegisterRequest("r1@swiftdrop.com", "Secret123!", "User 1", null);
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            RegisterRequest request2 = new RegisterRequest("r2@swiftdrop.com", "Secret123!", "User 2", null);
            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request2)));

            // 3rd attempt exceeds limit
            RegisterRequest request3 = new RegisterRequest("r3@swiftdrop.com", "Secret123!", "User 3", null);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request3)))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Logout revokes active refresh token and clears cookie")
        void logoutRevokesToken() throws Exception {
            RegisterRequest reg = new RegisterRequest("logout@swiftdrop.com", "Secret123!", "Logout User", null);
            MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reg)))
                    .andExpect(status().isCreated())
                    .andReturn();

            Cookie cookie = regResult.getResponse().getCookie("refresh_token");

            // Perform logout
            MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(cookie))
                    .andExpect(status().isOk())
                    .andReturn();

            Cookie clearedCookie = logoutResult.getResponse().getCookie("refresh_token");
            assertThat(clearedCookie).isNotNull();
            assertThat(clearedCookie.getMaxAge()).isEqualTo(0);

            // Attempting to refresh with revoked token should fail
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie))
                    .andExpect(status().isUnauthorized());
        }
    }
}
