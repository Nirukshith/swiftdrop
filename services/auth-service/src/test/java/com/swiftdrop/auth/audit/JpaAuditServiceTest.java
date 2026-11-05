package com.swiftdrop.auth.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private JpaAuditService jpaAuditService;

    @Test
    @DisplayName("record() converts arguments to AuditLogEntity and persists via repository")
    void record_withExplicitArgs_persistsSuccessfully() {
        Map<String, Object> details = Map.of("email", "customer@example.com", "attemptCount", 3);

        jpaAuditService.record("usr-123", "CUSTOMER", "USER_LOGIN_SUCCESS", "USER", "usr-123", details);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity entity = captor.getValue();
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getOccurredAt()).isNotNull();
        assertThat(entity.getActorId()).isEqualTo("usr-123");
        assertThat(entity.getActorRole()).isEqualTo("CUSTOMER");
        assertThat(entity.getAction()).isEqualTo("USER_LOGIN_SUCCESS");
        assertThat(entity.getTargetType()).isEqualTo("USER");
        assertThat(entity.getTargetId()).isEqualTo("usr-123");
        assertThat(entity.getDetails()).containsEntry("email", "customer@example.com");
        assertThat(entity.getDetails()).containsEntry("attemptCount", 3);
    }

    @Test
    @DisplayName("record(AuditEvent) preserves existing eventId and timestamps")
    void record_withAuditEvent_preservesEventId() {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-25T14:22:10Z");

        AuditEvent event = new AuditEvent(
            eventId.toString(),
            timestamp,
            "admin-1",
            "ADMIN",
            "MERCHANT_APPROVED",
            "MERCHANT",
            "m-99",
            "192.168.1.50",
            "corr-123",
            Map.of("businessName", "Colombo Tech")
        );

        jpaAuditService.record(event);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(eventId);
        assertThat(entity.getOccurredAt()).isEqualTo(timestamp);
        assertThat(entity.getActorId()).isEqualTo("admin-1");
        assertThat(entity.getActorRole()).isEqualTo("ADMIN");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.50");
        assertThat(entity.getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    @DisplayName("sanitizeDetails redacts sensitive credentials and secrets")
    void sanitizeDetails_redactsSecrets() {
        Map<String, Object> input = Map.of(
            "password", "PlaintextSecret123!",
            "passwordHash", "$2a$10$abcdefgh...",
            "refreshToken", "jwt.refresh.token.value",
            "clientSecret", "secret_key_456",
            "safeField", "hello_world"
        );

        Map<String, Object> sanitized = JpaAuditService.sanitizeDetails(input);

        assertThat(sanitized.get("password")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("passwordHash")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("refreshToken")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("clientSecret")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("safeField")).isEqualTo("hello_world");
    }

    @Test
    @DisplayName("record() rethrows repository exceptions for visibility")
    void record_rethrowsPersistenceExceptions() {
        when(auditLogRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new RuntimeException("Database connection timeout"));

        assertThatThrownBy(() -> jpaAuditService.record("usr-1", "CUSTOMER", "ACTION", "USER", "usr-1", Map.of()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database connection timeout");
    }
}
