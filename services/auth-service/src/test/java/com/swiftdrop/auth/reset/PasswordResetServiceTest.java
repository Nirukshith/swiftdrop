package com.swiftdrop.auth.reset;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.reset.dto.ForgotPasswordResponse;
import com.swiftdrop.auth.reset.dto.ResetPasswordResponse;
import com.swiftdrop.auth.security.jwt.JwtIssuer;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetMailSender mailSender;

    @Mock
    private AuditService auditService;

    @Mock
    private JwtIssuer jwtIssuer;

    private PasswordResetService resetService;

    private final Instant fixedNow = Instant.parse("2026-11-07T10:00:00Z");
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        resetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                mailSender,
                auditService,
                jwtIssuer
        );
        resetService.setClock(clock);
    }

    @Test
    @DisplayName("Registered active user produces a hashed 6-digit code with 10-min expiry and sends a generic response")
    void requestPasswordReset_registeredUser_succeeds() {
        User user = new User("usr-123", "alice@example.com", "hashed_old", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = resetService.requestPasswordReset("Alice@Example.com ", "127.0.0.1", "corr-1");

        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);

        // Verify old tokens invalidated
        verify(tokenRepository).invalidateActiveTokensForUser(eq("usr-123"), eq(fixedNow));

        // Verify token saved with 10-min expiration and hashed
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUserId()).isEqualTo("usr-123");
        assertThat(savedToken.getExpiresAt()).isEqualTo(fixedNow.plus(10, ChronoUnit.MINUTES));
        assertThat(savedToken.getTokenHash()).matches("^[a-f0-9]{64}$");

        // Verify email dispatched with raw token
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendResetEmail(eq("alice@example.com"), rawTokenCaptor.capture());
        String rawToken = rawTokenCaptor.getValue();
        assertThat(rawToken).matches("^\\d{6}$");
        assertThat(resetService.hashToken(rawToken)).isEqualTo(savedToken.getTokenHash());

        // Verify audit log has no raw token or password
        verify(auditService).record(
                eq("usr-123"),
                eq("CUSTOMER"),
                eq("PASSWORD_RESET_REQUESTED"),
                eq("USER"),
                eq("usr-123"),
                argThat(details -> "127.0.0.1".equals(details.get("ipAddress")) && !details.containsKey("token"))
        );
    }

    @Test
    @DisplayName("Unregistered email returns the generic response without sending a code (no account enumeration)")
    void requestPasswordReset_unregisteredUser_returnsGenericResponse() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response =
                resetService.requestPasswordReset("unknown@example.com", "127.0.0.1", "corr-2");

        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);
        verify(mailSender, never()).sendResetEmail(any(), any());
        verify(tokenRepository, never()).save(any());

        verify(auditService).record(
                eq("ANONYMOUS"),
                eq("ANONYMOUS"),
                eq("PASSWORD_RESET_REQUEST_FAILED"),
                eq("USER"),
                eq("unknown@example.com"),
                any()
        );
    }

    @Test
    @DisplayName("Disabled user returns the generic response without sending a code")
    void requestPasswordReset_disabledUser_returnsGenericResponse() {
        User user = new User("usr-disabled", "disabled@example.com", "hashed", "Disabled", null, "CUSTOMER", false, null);
        when(userRepository.findByEmailIgnoreCase("disabled@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response =
                resetService.requestPasswordReset("disabled@example.com", "127.0.0.1", "corr-3");

        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);
        verify(mailSender, never()).sendResetEmail(any(), any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid reset token updates password hash, marks token consumed, revokes refresh sessions, and logs audit")
    void resetPassword_validToken_succeeds() {
        String rawToken = "123456";
        String tokenHash = resetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken("tok-1", "usr-123", tokenHash, fixedNow.plus(15, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);

        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.of(token));
        when(userRepository.findById("usr-123")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new_bcrypt_hash");

        when(jwtIssuer.issueAccessToken("usr-123", "alice@example.com", "CUSTOMER", null))
                .thenReturn("access-token");

        PasswordResetResult result = resetService.resetPassword(
                "alice@example.com", rawToken, "NewSecret123!", "127.0.0.1", "corr-4");
        ResetPasswordResponse response = result.response();

        assertThat(response.message()).isEqualTo(ResetPasswordResponse.DEFAULT_MESSAGE);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNotBlank();

        // Verify token consumed
        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(fixedNow);
        verify(tokenRepository).save(token);
        verify(tokenRepository, never()).incrementFailedAttempts(any());

        // Verify user password updated
        assertThat(user.getPasswordHash()).isEqualTo("new_bcrypt_hash");
        verify(userRepository).save(user);

        // Verify active refresh token sessions revoked
        verify(refreshTokenRepository).revokeByUserId("usr-123");

        // Verify audit record contains no secrets
        verify(auditService).record(
                eq("usr-123"),
                eq("CUSTOMER"),
                eq("PASSWORD_RESET_COMPLETED"),
                eq("USER"),
                eq("usr-123"),
                argThat(details -> !details.containsKey("token") && !details.containsKey("password") && !details.containsKey("hash"))
        );
    }

    @Test
    @DisplayName("Valid code is verified only for the matching email and remains usable until reset")
    void verifyResetCode_validCodeAndEmail_succeeds() {
        String rawToken = "123456";
        PasswordResetToken token = new PasswordResetToken(
                "tok-verify", "usr-123", resetService.hashToken(rawToken), fixedNow.plus(10, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.of(token));

        resetService.verifyResetCode("alice@example.com", rawToken, "127.0.0.1", "corr-verify");

        assertThat(token.isConsumed()).isFalse();
        verify(tokenRepository, never()).incrementFailedAttempts(any());
    }

    @Test
    @DisplayName("Code issued to another email is rejected and counted as a failed attempt")
    void verifyResetCode_codeFromAnotherAccount_throwsException() {
        String aliceCode = "123456";
        PasswordResetToken bobToken = new PasswordResetToken(
                "tok-bob", "usr-bob", resetService.hashToken("999999"), fixedNow.plus(10, ChronoUnit.MINUTES));
        User bob = new User("usr-bob", "bob@example.com", "hash", "Bob", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(bob));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-bob"))
                .thenReturn(Optional.of(bobToken));

        assertThatThrownBy(() -> resetService.verifyResetCode(
                "bob@example.com", aliceCode, "127.0.0.1", "corr-mismatch"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(tokenRepository).incrementFailedAttempts("tok-bob");
    }

    @Test
    @DisplayName("Unknown email is rejected with the generic invalid-code error")
    void resetPassword_unknownEmail_throwsException() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetService.resetPassword(
                "ghost@example.com", "654321", "NewSecret123!", "127.0.0.1", "corr-ghost"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("No active code (never issued, already used, or locked) throws InvalidResetTokenException")
    void resetPassword_noActiveToken_throwsException() {
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetService.resetPassword(
                "alice@example.com", "654321", "NewSecret123!", "127.0.0.1", "corr-5"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("Expired token (10 min past creation) throws InvalidResetTokenException using controllable clock")
    void resetPassword_expiredToken_throwsException() {
        String rawToken = "123456";
        String tokenHash = resetService.hashToken(rawToken);
        // Token expired 1 minute ago
        PasswordResetToken token = new PasswordResetToken("tok-1", "usr-123", tokenHash, fixedNow.minus(1, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.resetPassword(
                "alice@example.com", rawToken, "NewSecret123!", "127.0.0.1", "corr-7"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("Wrong code below the attempt limit records a failed attempt without locking the code")
    void resetPassword_wrongCode_recordsFailedAttempt() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "usr-123", resetService.hashToken("123456"), fixedNow.plus(10, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.of(token));
        when(tokenRepository.lockIfAttemptsExhausted("tok-1", PasswordResetService.MAX_FAILED_ATTEMPTS, fixedNow))
                .thenReturn(0);

        assertThatThrownBy(() -> resetService.resetPassword(
                "alice@example.com", "000000", "NewSecret123!", "127.0.0.1", "corr-wrong"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(tokenRepository).incrementFailedAttempts("tok-1");
        verify(userRepository, never()).save(any());
        verify(auditService).record(
                eq("usr-123"), eq("ANONYMOUS"), eq("PASSWORD_RESET_REJECTED"), eq("TOKEN"), eq("usr-123"),
                argThat(details -> "TOKEN_MISMATCH".equals(details.get("reason"))));
    }

    @Test
    @DisplayName("Wrong code that reaches the attempt limit locks the code")
    void resetPassword_wrongCodeAtLimit_locksCode() {
        PasswordResetToken token = new PasswordResetToken(
                "tok-1", "usr-123", resetService.hashToken("123456"), fixedNow.plus(10, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("usr-123"))
                .thenReturn(Optional.of(token));
        when(tokenRepository.lockIfAttemptsExhausted("tok-1", PasswordResetService.MAX_FAILED_ATTEMPTS, fixedNow))
                .thenReturn(1);

        assertThatThrownBy(() -> resetService.verifyResetCode(
                "alice@example.com", "000000", "127.0.0.1", "corr-lock"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(auditService).record(
                eq("usr-123"), eq("ANONYMOUS"), eq("PASSWORD_RESET_REJECTED"), eq("TOKEN"), eq("usr-123"),
                argThat(details -> "TOKEN_LOCKED".equals(details.get("reason"))));
    }
}
