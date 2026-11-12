package com.swiftdrop.auth.audit;

import com.swiftdrop.auth.audit.dto.AuditLogPageResponse;
import com.swiftdrop.auth.audit.dto.AuditRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AuditPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditService auditService;

    @Autowired
    private AdminAuditService adminAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify JpaAuditService is the active AuditService primary bean")
    void activeAuditService_isJpaAuditService() {
        assertThat(auditService).isInstanceOf(JpaAuditService.class);
    }

    @Test
    @DisplayName("End-to-end: Record audit event, persist to PostgreSQL, and query through AdminAuditService")
    void recordAndRetrieveAuditLog_success() {
        auditService.record(
            "admin-uuid-001",
            "ADMIN",
            "MERCHANT_APPROVED",
            "MERCHANT",
            "merchant-uuid-99",
            Map.of("businessName", "Colombo Tech Ltd", "approvedCreditLimit", 50000)
        );

        AuditLogPageResponse page = adminAuditService.getAuditLogs(
            "MERCHANT_APPROVED", null, "MERCHANT", null, null, 0, 20
        );

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);

        AuditRecordResponse record = page.content().get(0);
        assertThat(record.id()).isNotNull();
        assertThat(record.actorId()).isEqualTo("admin-uuid-001");
        assertThat(record.actorRole()).isEqualTo("ADMIN");
        assertThat(record.action()).isEqualTo("MERCHANT_APPROVED");
        assertThat(record.targetType()).isEqualTo("MERCHANT");
        assertThat(record.targetId()).isEqualTo("merchant-uuid-99");
        assertThat(record.details()).containsEntry("businessName", "Colombo Tech Ltd");
        assertThat(record.details()).containsEntry("approvedCreditLimit", 50000);

        Optional<AuditLogEntity> persisted = auditLogRepository.findById(record.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getAction()).isEqualTo("MERCHANT_APPROVED");
    }

    @Test
    @DisplayName("End-to-end: Filtering by action, actorId, and date range in PostgreSQL")
    void filterAuditLogs_byMultipleCriteria() {
        Instant now = Instant.now();

        AuditEvent event1 = new AuditEvent(
            null,
            now.minus(2, ChronoUnit.HOURS),
            "admin-1",
            "ADMIN",
            "USER_REGISTERED",
            "USER",
            "usr-1",
            "127.0.0.1",
            "corr-1",
            Map.of("email", "courier1@swiftdrop.lk")
        );

        AuditEvent event2 = new AuditEvent(
            null,
            now.minus(1, ChronoUnit.HOURS),
            "dispatcher-1",
            "DISPATCHER",
            "PARCEL_ASSIGNED",
            "PARCEL",
            "pcl-1",
            "127.0.0.1",
            "corr-2",
            Map.of("courierId", "courier-1")
        );

        auditService.record(event1);
        auditService.record(event2);

        // Filter by action
        AuditLogPageResponse userEvents = adminAuditService.getAuditLogs(
            "USER_REGISTERED", null, null, null, null, 0, 10
        );
        assertThat(userEvents.totalElements()).isEqualTo(1);
        assertThat(userEvents.content().get(0).action()).isEqualTo("USER_REGISTERED");

        // Filter by actorId
        AuditLogPageResponse dispatcherEvents = adminAuditService.getAuditLogs(
            null, "dispatcher-1", null, null, null, 0, 10
        );
        assertThat(dispatcherEvents.totalElements()).isEqualTo(1);
        assertThat(dispatcherEvents.content().get(0).actorId()).isEqualTo("dispatcher-1");

        // Filter by date range (between 90 mins ago and 30 mins ago -> should only match event2)
        AuditLogPageResponse dateRangeEvents = adminAuditService.getAuditLogs(
            null, null, null, now.minus(90, ChronoUnit.MINUTES), now.minus(30, ChronoUnit.MINUTES), 0, 10
        );
        assertThat(dateRangeEvents.totalElements()).isEqualTo(1);
        assertThat(dateRangeEvents.content().get(0).action()).isEqualTo("PARCEL_ASSIGNED");
    }

    @Test
    @DisplayName("End-to-end: Audit persistence redacts sensitive credentials in details")
    void record_redactsSensitiveFieldsInPostgres() {
        auditService.record(
            "usr-99",
            "CUSTOMER",
            "USER_LOGIN_FAILED",
            "USER",
            "usr-99",
            Map.of(
                "password", "AttemptedSecret123",
                "refreshToken", "refresh.jwt.token",
                "attemptCount", 2
            )
        );

        AuditLogPageResponse page = adminAuditService.getAuditLogs(
            "USER_LOGIN_FAILED", null, null, null, null, 0, 10
        );

        assertThat(page.totalElements()).isEqualTo(1);
        Map<String, Object> details = page.content().get(0).details();
        assertThat(details.get("password")).isEqualTo("[REDACTED]");
        assertThat(details.get("refreshToken")).isEqualTo("[REDACTED]");
        assertThat(details.get("attemptCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("Courier authentication events (success & failure) persist to PostgreSQL and survive across service re-query")
    void courierAuthenticationAuditPersistence_successAndFailure() {
        // 1. Record successful courier login
        auditService.record(
            "courier-it-001",
            "COURIER",
            "USER_LOGIN_SUCCESS",
            "USER",
            "courier-it-001",
            Map.of("email", "courier@swiftdrop.lk", "authMethod", "PASSWORD")
        );

        // 2. Record failed courier login attempt
        auditService.record(
            "ANONYMOUS",
            "ANONYMOUS",
            "USER_LOGIN_FAILED",
            "USER",
            "courier@swiftdrop.lk",
            Map.of("email", "courier@swiftdrop.lk", "reason", "INVALID_CREDENTIALS", "password", "SecretPass123!")
        );

        // 3. Query PostgreSQL via AdminAuditService
        AuditLogPageResponse successLogs = adminAuditService.getAuditLogs(
            "USER_LOGIN_SUCCESS", "courier-it-001", "USER", null, null, 0, 10
        );
        assertThat(successLogs.totalElements()).isEqualTo(1);
        AuditRecordResponse successRecord = successLogs.content().get(0);
        assertThat(successRecord.actorRole()).isEqualTo("COURIER");
        assertThat(successRecord.details()).containsEntry("email", "courier@swiftdrop.lk");

        AuditLogPageResponse failedLogs = adminAuditService.getAuditLogs(
            "USER_LOGIN_FAILED", "ANONYMOUS", "USER", null, null, 0, 10
        );
        assertThat(failedLogs.totalElements()).isEqualTo(1);
        AuditRecordResponse failedRecord = failedLogs.content().get(0);
        assertThat(failedRecord.details()).containsEntry("reason", "INVALID_CREDENTIALS");
        // Ensure sensitive password was redacted
        assertThat(failedRecord.details().get("password")).isEqualTo("[REDACTED]");
    }
}
