package com.swiftdrop.auth.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.auth.audit.AuditLogRepository;
import com.swiftdrop.auth.reset.dto.ForgotPasswordResponse;
import com.swiftdrop.auth.reset.dto.ResetPasswordResponse;
import com.swiftdrop.auth.token.RefreshToken;
import com.swiftdrop.auth.token.RefreshTokenRepository;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PasswordResetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private RestClient mailpitClient;

    @BeforeEach
    void setUp() {
        mailpitClient = RestClient.builder()
                .baseUrl("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025))
                .build();

        try {
            mailpitClient.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
        } catch (Exception ignored) {}

        tokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    /** The reset email is sent asynchronously after commit, so poll Mailpit until it arrives. */
    private JsonNode awaitMailpitMessages(int expectedTotal) {
        long deadline = System.currentTimeMillis() + 10_000;
        JsonNode messages = getMailpitMessages();
        while (messages.get("total").asInt() < expectedTotal && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            messages = getMailpitMessages();
        }
        return messages;
    }

    private JsonNode getMailpitMessages() {
        return mailpitClient.get()
                .uri("/api/v1/messages")
                .retrieve()
                .body(JsonNode.class);
    }

    @Test
    @DisplayName("End-to-end: forgot password sends email to Mailpit, reset updates hash and invalidates sessions")
    void endToEnd_forgotAndResetPassword_withMailpit() {
        // 1. Create existing user
        String userId = "usr-" + UUID.randomUUID();
        String email = "recovery.user@example.com";
        String oldPassword = "OldSecureP@ssw0rd1!";
        User user = new User(userId, email, passwordEncoder.encode(oldPassword), "Recovery User", "+94770001122", "CUSTOMER", true, null);
        userRepository.save(user);

        // Create an active refresh token session for user
        RefreshToken refreshToken = new RefreshToken("rt-1", "hash-1", userId, "fam-1", Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        // 2. Request password reset
        ForgotPasswordResponse forgotResponse = passwordResetService.requestPasswordReset(email, "192.168.1.10", "corr-test");
        assertThat(forgotResponse.message()).isEqualTo(ForgotPasswordResponse.DEFAULT_MESSAGE);

        // 3. Verify email arrived in Mailpit
        JsonNode messages = awaitMailpitMessages(1);
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        JsonNode firstMessage = messages.get("messages").get(0);
        assertThat(firstMessage.get("To").get(0).get("Address").asText()).isEqualTo(email);
        assertThat(firstMessage.get("Subject").asText()).isEqualTo("Reset your SwiftDrop password");

        // 4. Retrieve full email body and extract the six-digit code
        String messageId = firstMessage.get("ID").asText();
        JsonNode fullMsg = mailpitClient.get().uri("/api/v1/message/" + messageId).retrieve().body(JsonNode.class);
        String textBody = fullMsg.get("Text").asText();
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\d{6})\\s*$").matcher(textBody);
        assertThat(matcher.find()).isTrue();
        String extractedToken = matcher.group(1);

        // 5. Complete password reset with new password
        String newPassword = "BrandNewP@ssw0rd2026!";
        PasswordResetResult resetResult = passwordResetService.resetPassword(
                email, extractedToken, newPassword, "192.168.1.10", "corr-test");
        assertThat(resetResult.response().message()).isEqualTo(ResetPasswordResponse.DEFAULT_MESSAGE);

        // 6. Verify password hash updated in DB
        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, updatedUser.getPasswordHash())).isFalse();

        // 7. Verify refresh token session revoked
        RefreshToken updatedRt = refreshTokenRepository.findById("rt-1").orElseThrow();
        assertThat(updatedRt.isRevoked()).isTrue();

        // 8. Verify reset token cannot be reused
        assertThatThrownBy(() -> passwordResetService.resetPassword(
                email, extractedToken, "AnotherPassword!", "192.168.1.10", "corr-test"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    @DisplayName("Password reset code expires after 10 minutes")
    void resetPassword_expiresAfter10Minutes() {
        String userId = "usr-" + UUID.randomUUID();
        String email = "timeout.user@example.com";
        User user = new User(userId, email, passwordEncoder.encode("InitialPassword123!"), "Timeout User", null, "CUSTOMER", true, null);
        userRepository.save(user);

        Instant t0 = Instant.parse("2026-11-07T12:00:00Z");
        passwordResetService.setClock(Clock.fixed(t0, ZoneOffset.UTC));

        passwordResetService.requestPasswordReset(email, "127.0.0.1", "corr-timeout");

        JsonNode messages = awaitMailpitMessages(1);
        String messageId = messages.get("messages").get(0).get("ID").asText();
        JsonNode fullMsg = mailpitClient.get().uri("/api/v1/message/" + messageId).retrieve().body(JsonNode.class);
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\d{6})\\s*$").matcher(fullMsg.get("Text").asText());
        assertThat(matcher.find()).isTrue();
        String token = matcher.group(1);

        // Advance clock by 11 minutes
        Instant t31 = t0.plus(11, ChronoUnit.MINUTES);
        passwordResetService.setClock(Clock.fixed(t31, ZoneOffset.UTC));

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                email, token, "NewPassword12345!", "127.0.0.1", "corr-timeout"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid, expired, or already-used reset token");

        // Reset clock
        passwordResetService.setClock(Clock.systemUTC());
    }

    @Test
    @DisplayName("Five wrong codes invalidate the reset code, so the correct code is then rejected")
    void resetPassword_fiveWrongCodes_locksCode() {
        String userId = "usr-" + UUID.randomUUID();
        String email = "bruteforce.target@example.com";
        String originalHash = passwordEncoder.encode("OriginalPassword123!");
        userRepository.save(new User(userId, email, originalHash, "Target User", null, "CUSTOMER", true, null));

        passwordResetService.requestPasswordReset(email, "10.0.0.5", "corr-brute");
        JsonNode messages = awaitMailpitMessages(1);
        String messageId = messages.get("messages").get(0).get("ID").asText();
        JsonNode fullMsg = mailpitClient.get().uri("/api/v1/message/" + messageId).retrieve().body(JsonNode.class);
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\d{6})\\s*$").matcher(fullMsg.get("Text").asText());
        assertThat(matcher.find()).isTrue();
        String realCode = matcher.group(1);
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= PasswordResetService.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> passwordResetService.verifyResetCode(email, wrongCode, "10.0.0.5", "corr-brute"))
                    .isInstanceOf(InvalidResetTokenException.class);
        }

        // Failed attempts were committed despite the exception, and the code is now locked.
        PasswordResetToken stored = tokenRepository.findByUserId(userId).get(0);
        assertThat(stored.getFailedAttempts()).isEqualTo(PasswordResetService.MAX_FAILED_ATTEMPTS);
        assertThat(stored.isConsumed()).isTrue();

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                email, realCode, "AttackerPassword123!", "10.0.0.5", "corr-brute"))
                .isInstanceOf(InvalidResetTokenException.class);
        assertThat(userRepository.findById(userId).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }
}
