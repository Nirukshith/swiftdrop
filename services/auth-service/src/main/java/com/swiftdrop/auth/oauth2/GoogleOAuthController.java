package com.swiftdrop.auth.oauth2;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
public class GoogleOAuthController {

    private static final String STATE_COOKIE_NAME = "oauth2_state";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);
    private static final Duration STATE_COOKIE_TTL = Duration.ofMinutes(10);

    private final java.util.Set<String> consumedStates = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final AuthEventPublisher authEventPublisher;

    @Autowired
    public GoogleOAuthController(GoogleOAuthProperties properties,
                                 @Autowired(required = false) GoogleOAuthClient googleOAuthClient,
                                 UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 AuditService auditService,
                                 AuthEventPublisher authEventPublisher) {
        this.properties = properties;
        this.googleOAuthClient = googleOAuthClient;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.authEventPublisher = authEventPublisher;
    }

    /**
     * Initiates Google OAuth 2.0 Authorization Code flow.
     * Generates CSRF state token, sets secure state cookie, and redirects user to Google.
     */
    @GetMapping("/google")
    public ResponseEntity<Void> initiateGoogleLogin() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google OAuth is not configured. Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, and GOOGLE_REDIRECT_URI."
            );
        }

        String state = UUID.randomUUID().toString().replace("-", "") + "." + Instant.now().toEpochMilli();

        ResponseCookie stateCookie = ResponseCookie.from(STATE_COOKIE_NAME, state)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth/oauth2")
                .maxAge(STATE_COOKIE_TTL)
                .build();

        String googleAuthUrl = UriComponentsBuilder.fromUriString(properties.getAuthUrl())
                .queryParam("client_id", properties.getClientId())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.LOCATION, googleAuthUrl)
                .build();
    }

    /**
     * Handles Google OAuth 2.0 callback with authorization code.
     * Validates CSRF state, exchanges code for verified identity, provisions or matches user,
     * issues refresh token cookie, and redirects to frontend without any credentials in the URL.
     */
    @GetMapping("/callback/google")
    @Transactional
    public ResponseEntity<Void> handleGoogleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE_NAME, required = false) String stateCookie) {

        ResponseCookie clearStateCookie = ResponseCookie.from(STATE_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth/oauth2")
                .maxAge(Duration.ZERO)
                .build();

        // Handle user cancellation / error from Google consent
        if (error != null && !error.isBlank()) {
            auditService.record("ANONYMOUS", "ANONYMOUS", "GOOGLE_OAUTH_DENIED", "OAUTH", "GOOGLE",
                    Map.of("error", error));
            String errorRedirect = UriComponentsBuilder.fromUriString(properties.getFrontendCallbackUrl())
                    .queryParam("error", error)
                    .build()
                    .toUriString();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, clearStateCookie.toString())
                    .header(HttpHeaders.LOCATION, errorRedirect)
                    .build();
        }

        // Validate state parameter against httpOnly cookie
        if (state == null || state.isBlank() || stateCookie == null || stateCookie.isBlank() ||
                !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), stateCookie.getBytes(StandardCharsets.UTF_8))) {
            auditService.record("ANONYMOUS", "ANONYMOUS", "OAUTH_STATE_MISMATCH", "OAUTH", "GOOGLE", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or missing OAuth state parameter");
        }

        // Single-use state verification: reject reused/replayed states
        if (!consumedStates.add(state)) {
            auditService.record("ANONYMOUS", "ANONYMOUS", "OAUTH_STATE_REUSED", "OAUTH", "GOOGLE", Map.of("state", state));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state parameter has already been used");
        }

        // Expiration check: if timestamp is embedded, verify state age against TTL
        int dotIdx = state.lastIndexOf('.');
        if (dotIdx > 0) {
            try {
                long timestamp = Long.parseLong(state.substring(dotIdx + 1));
                Instant stateInstant = Instant.ofEpochMilli(timestamp);
                if (Instant.now().isAfter(stateInstant.plus(STATE_COOKIE_TTL))) {
                    auditService.record("ANONYMOUS", "ANONYMOUS", "OAUTH_STATE_EXPIRED", "OAUTH", "GOOGLE", Map.of("state", state));
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state parameter is expired");
                }
            } catch (NumberFormatException ignored) {
                // If not numeric timestamp, rely on cookie TTL
            }
        }

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing authorization code");
        }

        if (googleOAuthClient == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Google OAuth client is not available");
        }

        // Exchange authorization code for verified Google identity
        GoogleUserInfo userInfo = googleOAuthClient.exchangeCodeAndGetUserInfo(code);
        if (userInfo == null || userInfo.email() == null || userInfo.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google account does not provide a valid email");
        }
        if (!userInfo.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google email address is not verified");
        }

        String normalizedEmail = userInfo.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            // First-time Google login: auto-provision CUSTOMER account
            String fullName = userInfo.name() != null && !userInfo.name().isBlank() ? userInfo.name() : "Google User";
            user = new User(
                    UUID.randomUUID().toString(),
                    normalizedEmail,
                    null,
                    fullName,
                    null,
                    Roles.CUSTOMER,
                    true,
                    null
            );
            user = userRepository.save(user);

            authEventPublisher.publishUserRegistered(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    null,
                    user.getRole(),
                    null,
                    true
            );

            auditService.record(user.getId(), user.getRole(), "USER_REGISTERED_GOOGLE", "USER", user.getId(),
                    Map.of("email", normalizedEmail, "googleSub", userInfo.sub()));
        } else {
            // Existing account check
            if (!user.isEnabled()) {
                auditService.record(user.getId(), user.getRole(), "USER_LOGIN_DISABLED", "USER", user.getId(),
                        Map.of("email", normalizedEmail));
                String disabledRedirect = UriComponentsBuilder.fromUriString(properties.getFrontendCallbackUrl())
                        .queryParam("error", "account_disabled")
                        .build()
                        .toUriString();

                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.SET_COOKIE, clearStateCookie.toString())
                        .header(HttpHeaders.LOCATION, disabledRedirect)
                        .build();
            }

            auditService.record(user.getId(), user.getRole(), "USER_LOGIN_GOOGLE", "USER", user.getId(),
                    Map.of("email", normalizedEmail, "googleSub", userInfo.sub()));
        }

        // Issue 7-day refresh token in auth_db
        String rawRefreshToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                hashToken(rawRefreshToken),
                user.getId(),
                familyId,
                Instant.now().plus(REFRESH_COOKIE_TTL)
        );
        refreshTokenRepository.save(refreshToken);

        ResponseCookie refreshCookie = buildRefreshCookie(rawRefreshToken, REFRESH_COOKIE_TTL);

        // Security requirement: ZERO credentials in callback URL!
        // Redirects to /auth/callback with Set-Cookie: refresh_token=...
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearStateCookie.toString())
                .header(HttpHeaders.LOCATION, properties.getFrontendCallbackUrl())
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
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
