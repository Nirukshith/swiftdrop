package com.swiftdrop.auth.audit;

import com.swiftdrop.auth.audit.dto.AuditLogPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminAuditService adminAuditService;

    @Test
    @DisplayName("getAuditLogs returns paginated response when valid params provided")
    void getAuditLogs_returnsPaginatedResponse() {
        AuditLogEntity entity = new AuditLogEntity(
            UUID.randomUUID(),
            Instant.now(),
            "admin-1",
            "ADMIN",
            "USER_REGISTERED",
            "USER",
            "usr-1",
            "127.0.0.1",
            "corr-1",
            Map.of("email", "test@swiftdrop.lk")
        );

        Page<AuditLogEntity> pageResult = new PageImpl<>(List.of(entity));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(pageResult);

        AuditLogPageResponse response = adminAuditService.getAuditLogs(
            "USER_REGISTERED", "admin-1", "USER", null, null, 0, 20
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).action()).isEqualTo("USER_REGISTERED");
    }

    @Test
    @DisplayName("getAuditLogs rejects negative page index")
    void getAuditLogs_rejectsNegativePage() {
        assertThatThrownBy(() -> adminAuditService.getAuditLogs(null, null, null, null, null, -1, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page index must not be negative");
    }

    @Test
    @DisplayName("getAuditLogs rejects size zero or greater than 100")
    void getAuditLogs_rejectsInvalidSize() {
        assertThatThrownBy(() -> adminAuditService.getAuditLogs(null, null, null, null, null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page size must be between 1 and 100");

        assertThatThrownBy(() -> adminAuditService.getAuditLogs(null, null, null, null, null, 0, 101))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page size must be between 1 and 100");
    }

    @Test
    @DisplayName("getAuditLogs rejects fromDate after toDate")
    void getAuditLogs_rejectsInvalidDateRange() {
        Instant from = Instant.parse("2026-11-05T10:00:00Z");
        Instant to = Instant.parse("2026-11-04T10:00:00Z");

        assertThatThrownBy(() -> adminAuditService.getAuditLogs(null, null, null, from, to, 0, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fromDate must not be after toDate");
    }
}
