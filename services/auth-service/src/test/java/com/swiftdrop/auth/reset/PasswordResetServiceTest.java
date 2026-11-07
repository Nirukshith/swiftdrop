package com.swiftdrop.auth.reset;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.reset.dto.ForgotPasswordResponse;
import com.swiftdrop.auth.reset.dto.ResetPasswordResponse;
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
                auditService
        );
        resetService.setClock(clock);
    }

    @Test
    @DisplayName("Registered active user produces token hashed with 30-min expiry, sends email, and returns HTTP 202 generic response")
    void requestPasswordReset_registeredUser_succeeds() {
        User user = new User("usr-123", "alice@example.com", "hashed_old", "Alice", null, "CUSTOMER", true, null);
        when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = resetService.requestPasswordReset("Alice@Example.com ", "127.0.0.1", "corr-1");

        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);

        // Verify old tokens invalidated
        verify(tokenRepository).invalidateActiveTokensForUser(eq("usr-123"), eq(fixedNow));

        // Verify token saved with 30-min expiration and hashed
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUserId()).isEqualTo("usr-123");
        assertThat(savedToken.getExpiresAt()).isEqualTo(fixedNow.plus(30, ChronoUnit.MINUTES));
        assertThat(savedToken.getTokenHash()).matches("^[a-f0-9]{64}$");

        // Verify email dispatched with raw token
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendResetEmail(eq("alice@example.com"), rawTokenCaptor.capture());
        String rawToken = rawTokenCaptor.getValue();
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
    @DisplayName("Unregistered email returns identical generic response without email dispatch to prevent account enumeration")
    void requestPasswordReset_unregisteredUser_returnsGenericResponseWithoutEmail() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = resetService.requestPasswordReset("unknown@example.com", "127.0.0.1", "corr-2");

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
    @DisplayName("Disabled user returns identical generic response without sending reset email")
    void requestPasswordReset_disabledUser_returnsGenericResponseWithoutEmail() {
        User user = new User("usr-disabled", "disabled@example.com", "hashed", "Disabled", null, "CUSTOMER", false, null);
        when(userRepository.findByEmailIgnoreCase("disabled@example.com")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = resetService.requestPasswordReset("disabled@example.com", "127.0.0.1", "corr-3");

        assertThat(response.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);
        verify(mailSender, never()).sendResetEmail(any(), any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid reset token updates password hash, marks token consumed, revokes refresh sessions, and logs audit")
    void resetPassword_validToken_succeeds() {
        String rawToken = "my-secret-token";
        String tokenHash = resetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken("tok-1", "usr-123", tokenHash, fixedNow.plus(15, ChronoUnit.MINUTES));
        User user = new User("usr-123", "alice@example.com", "old_hash", "Alice", null, "CUSTOMER", true, null);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findById("usr-123")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new_bcrypt_hash");

        ResetPasswordResponse response = resetService.resetPassword(rawToken, "NewSecret123!", "127.0.0.1", "corr-4");

        assertThat(response.message()).isEqualTo(ResetPasswordResponse.DEFAULT_MESSAGE);

        // Verify token consumed
        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(fixedNow);
        verify(tokenRepository).save(token);

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
    @DisplayName("Non-existent token throws InvalidResetTokenException")
    void resetPassword_nonExistentToken_throwsException() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetService.resetPassword("non-existent", "NewSecret123!", "127.0.0.1", "corr-5"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("Already consumed token throws InvalidResetTokenException")
    void resetPassword_alreadyConsumedToken_throwsException() {
        String rawToken = "used-token";
        String tokenHash = resetService.hashToken(rawToken);
        PasswordResetToken token = new PasswordResetToken("tok-1", "usr-123", tokenHash, fixedNow.plus(15, ChronoUnit.MINUTES));
        token.setConsumedAt(fixedNow.minus(5, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.resetPassword(rawToken, "NewSecret123!", "127.0.0.1", "corr-6"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("Expired token (30 min past creation) throws InvalidResetTokenException using controllable clock")
    void resetPassword_expiredToken_throwsException() {
        String rawToken = "expired-token";
        String tokenHash = resetService.hashToken(rawToken);
        // Token expired 1 minute ago
        PasswordResetToken token = new PasswordResetToken("tok-1", "usr-123", tokenHash, fixedNow.minus(1, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.resetPassword(rawToken, "NewSecret123!", "127.0.0.1", "corr-7"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeByUserId(any());
    }
}

