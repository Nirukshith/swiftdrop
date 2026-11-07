package com.swiftdrop.auth.reset;

import com.swiftdrop.auth.audit.AuditService;
import com.swiftdrop.auth.reset.dto.ForgotPasswordResponse;
import com.swiftdrop.auth.reset.dto.ResetPasswordResponse;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender mailSender;
    private final AuditService auditService;
    private Clock clock = Clock.systemUTC();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetMailSender mailSender,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.auditService = auditService;
    }

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Transactional
    public ForgotPasswordResponse requestPasswordReset(String email, String ipAddress, String correlationId) {
        if (email == null || email.isBlank()) {
            return new ForgotPasswordResponse();
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOpt.isPresent() && userOpt.get().isEnabled()) {
            User user = userOpt.get();
            Instant now = clock.instant();

            // Invalidate existing active reset tokens for this user
            tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

            // Generate cryptographically secure random token
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);

            Instant expiresAt = now.plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);
            PasswordResetToken token = new PasswordResetToken(
                    UUID.randomUUID().toString(),
                    user.getId(),
                    tokenHash,
                    expiresAt
            );
            tokenRepository.save(token);

            // Send email to recipient via Mailpit SMTP
            mailSender.sendResetEmail(user.getEmail(), rawToken);

            // Record audit log
            auditService.record(
                    user.getId(),
                    user.getRole(),
                    "PASSWORD_RESET_REQUESTED",
                    "USER",
                    user.getId(),
                    Map.of("ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
            );
        } else {
            // User does not exist or is disabled - record generic audit without leaking existence
            auditService.record(
                    "ANONYMOUS",
                    "ANONYMOUS",
                    "PASSWORD_RESET_REQUEST_FAILED",
                    "USER",
                    normalizedEmail,
                    Map.of("reason", "USER_NOT_FOUND_OR_DISABLED", "ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
            );
        }

        return new ForgotPasswordResponse();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(String rawToken, String newPassword, String ipAddress, String correlationId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException("Invalid, expired, or already-used reset token.");
        }

        Instant now = clock.instant();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    auditService.record(
                            "ANONYMOUS",
                            "ANONYMOUS",
                            "PASSWORD_RESET_REJECTED",
                            "TOKEN",
                            "UNKNOWN",
                            Map.of("reason", "TOKEN_NOT_FOUND", "ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
                    );
                    return new InvalidResetTokenException("Invalid, expired, or already-used reset token.");
                });

        if (token.isConsumed()) {
            auditService.record(
                    token.getUserId(),
                    "ANONYMOUS",
                    "PASSWORD_RESET_REJECTED",
                    "USER",
                    token.getUserId(),
                    Map.of("reason", "TOKEN_ALREADY_USED", "ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
            );
            throw new InvalidResetTokenException("Invalid, expired, or already-used reset token.");
        }

        if (token.isExpired(now)) {
            auditService.record(
                    token.getUserId(),
                    "ANONYMOUS",
                    "PASSWORD_RESET_REJECTED",
                    "USER",
                    token.getUserId(),
                    Map.of("reason", "TOKEN_EXPIRED", "ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
            );
            throw new InvalidResetTokenException("Invalid, expired, or already-used reset token.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException("User associated with reset token not found."));

        // Atomically consume the token
        token.setConsumedAt(now);
        tokenRepository.save(token);

        // Update password using BCrypt
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Invalidate active refresh token sessions
        refreshTokenRepository.revokeByUserId(user.getId());

        // Audit log success
        auditService.record(
                user.getId(),
                user.getRole(),
                "PASSWORD_RESET_COMPLETED",
                "USER",
                user.getId(),
                Map.of("ipAddress", ipAddress != null ? ipAddress : "UNKNOWN")
        );

        return new ResetPasswordResponse();
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

