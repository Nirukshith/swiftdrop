package com.swiftdrop.auth;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.dto.AuthResponse;
import com.swiftdrop.auth.dto.LoginRequest;
import com.swiftdrop.auth.dto.RefreshTokenRequest;
import com.swiftdrop.auth.dto.RegisterRequest;
import com.swiftdrop.auth.dto.TokenRefreshResponse;
import com.swiftdrop.auth.dto.UserSummary;
import com.swiftdrop.auth.event.AuthEventPublisher;
import com.swiftdrop.auth.security.AccountLockedException;
import com.swiftdrop.auth.security.AuthRateLimiter;
import com.swiftdrop.auth.security.LoginGuard;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import com.swiftdrop.common.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Public authentication endpoints for SwiftDrop identity platform.
 * Aligned with contracts/openapi/auth.yaml, docs/auth-design.md, ADR 004, and ADR 005.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    public static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final LoginGuard loginGuard;
    private final AuditService auditService;
    private final AuthRateLimiter authRateLimiter;
    private final AuthEventPublisher authEventPublisher;

    public AuthController(UserRepository userRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          PasswordEncoder passwordEncoder,
                          JwtIssuer jwtIssuer,
                          LoginGuard loginGuard,
                          AuditService auditService,
                          AuthRateLimiter authRateLimiter,
                          AuthEventPublisher authEventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.loginGuard = loginGuard;
        this.auditService = auditService;
        this.authRateLimiter = authRateLimiter;
        this.authEventPublisher = authEventPublisher;
    }

    /**
     * Self-service customer registration.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        if (!authRateLimiter.allowRegister(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Registration rate limit exceeded");
        }

        validatePasswordPolicy(request.password());

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address already registered: " + normalizedEmail);
        }

        String userId = "usr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                userId,
                normalizedEmail,
                passwordHash,
                request.fullName().trim(),
                request.phoneNumber() != null ? request.phoneNumber().trim() : null,
                Roles.CUSTOMER,
                true,
                null
        );

        User savedUser = userRepository.save(user);

        // Record audit and publish Kafka event
        auditService.record(savedUser.getId(), savedUser.getRole(), "USER_REGISTERED", "USER", savedUser.getId(),
                Map.of("email", savedUser.getEmail(), "ipAddress", clientIp));

        authEventPublisher.publishUserRegistered(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getPhoneNumber(),
                savedUser.getRole(),
                null,
                true
        );

        // Create refresh token
        String rawRefreshToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                hashToken(rawRefreshToken),
                savedUser.getId(),
                familyId,
                Instant.now().plus(REFRESH_COOKIE_TTL)
        );
        refreshTokenRepository.save(refreshToken);

        // Issue access token
        String accessToken = jwtIssuer.issueAccessToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole(), null);
        ResponseCookie cookie = buildRefreshCookie(rawRefreshToken, REFRESH_COOKIE_TTL);

        UserSummary summary = new UserSummary(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole(),
                null
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(accessToken, "Bearer", JwtIssuer.ACCESS_TOKEN_TTL.toSeconds(), summary));
    }

    /**
     * User authentication via email and password credentials.
     */
    @PostMapping("/login")
    @Transactional
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        String normalizedEmail = request.email().trim().toLowerCase();

        if (!authRateLimiter.allowLogin(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Login rate limit exceeded");
        }

        // LoginGuard pre-auth check
        loginGuard.checkLoginAllowed(normalizedEmail, clientIp);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null || user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginGuard.recordFailure(normalizedEmail, clientIp);
            auditService.record("ANONYMOUS", "ANONYMOUS", "USER_LOGIN_FAILED", "USER", normalizedEmail,
                    Map.of("email", normalizedEmail, "ipAddress", clientIp));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password credentials");
        }

        if (!user.isEnabled()) {
            auditService.record(user.getId(), user.getRole(), "USER_LOGIN_DISABLED", "USER", user.getId(),
                    Map.of("email", normalizedEmail, "ipAddress", clientIp));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled or pending merchant approval");
        }

        // Login success
        loginGuard.recordSuccess(normalizedEmail, clientIp);
        auditService.record(user.getId(), user.getRole(), "USER_LOGIN_SUCCESS", "USER", user.getId(),
                Map.of("email", normalizedEmail, "ipAddress", clientIp));

        // Create refresh token
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

        String accessToken = jwtIssuer.issueAccessToken(user.getId(), user.getEmail(), user.getRole(), user.getMerchantId());
        ResponseCookie cookie = buildRefreshCookie(rawRefreshToken, REFRESH_COOKIE_TTL);

        UserSummary summary = new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getMerchantId()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(accessToken, "Bearer", JwtIssuer.ACCESS_TOKEN_TTL.toSeconds(), summary));
    }

    /**
     * Refresh access token with rotation and reuse detection.
     */
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<TokenRefreshResponse> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest requestBody,
            HttpServletRequest httpRequest) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken.trim()
                : (requestBody != null && requestBody.refreshToken() != null ? requestBody.refreshToken().trim() : null);

        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        String tokenHash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // Reuse detection: already revoked or consumed token
        if (token.isRevoked() || token.getConsumedAt() != null) {
            refreshTokenRepository.revokeByFamilyId(token.getFamilyId());
            auditService.record(token.getUserId(), "USER", "TOKEN_REUSE_DETECTED", "TOKEN_FAMILY", token.getFamilyId(),
                    Map.of("userId", token.getUserId(), "familyId", token.getFamilyId()));

            ResponseCookie clearCookie = buildRefreshCookie("", Duration.ZERO);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        // Expiration check
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            ResponseCookie clearCookie = buildRefreshCookie("", Duration.ZERO);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        // User active check
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.isEnabled()) {
            refreshTokenRepository.revokeByUserId(user.getId());
            ResponseCookie clearCookie = buildRefreshCookie("", Duration.ZERO);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }

        // Token rotation: consume current token
        token.setRevoked(true);
        token.setConsumedAt(Instant.now());
        refreshTokenRepository.save(token);

        // Issue new refresh token in the same family
        String newRawToken = UUID.randomUUID().toString();
        RefreshToken newToken = new RefreshToken(
                UUID.randomUUID().toString(),
                hashToken(newRawToken),
                user.getId(),
                token.getFamilyId(),
                Instant.now().plus(REFRESH_COOKIE_TTL)
        );
        refreshTokenRepository.save(newToken);

        auditService.record(user.getId(), user.getRole(), "TOKEN_REFRESH_SUCCESS", "USER", user.getId(),
                Map.of("familyId", token.getFamilyId()));

        String accessToken = jwtIssuer.issueAccessToken(user.getId(), user.getEmail(), user.getRole(), user.getMerchantId());
        ResponseCookie newCookie = buildRefreshCookie(newRawToken, REFRESH_COOKIE_TTL);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(new TokenRefreshResponse(accessToken, "Bearer", JwtIssuer.ACCESS_TOKEN_TTL.toSeconds()));
    }

    /**
     * User logout: revokes active refresh token and clears cookie.
     */
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest requestBody) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken.trim()
                : (requestBody != null && requestBody.refreshToken() != null ? requestBody.refreshToken().trim() : null);

        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = hashToken(rawToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                auditService.record(token.getUserId(), "USER", "USER_LOGOUT", "USER", token.getUserId(), Map.of());
            });
        }

        ResponseCookie clearCookie = buildRefreshCookie("", Duration.ZERO);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        ErrorResponse error = new ErrorResponse(
                "ACCOUNT_LOCKED",
                ex.getMessage(),
                correlationId,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.LOCKED).body(error);
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

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 128 characters");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must contain at least one letter and one number");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return correlationId != null ? correlationId : "N/A";
    }
}
