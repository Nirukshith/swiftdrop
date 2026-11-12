package com.swiftdrop.auth.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;

class NoOpAuditServiceTest {

    private final NoOpAuditService noOpAuditService = new NoOpAuditService();

    @Test
    @DisplayName("NoOpAuditService.record with sensitive details sanitizes before logging without exception")
    void record_sanitizesDetailsSafely() {
        Map<String, Object> details = Map.of(
            "password", "SecretPass!",
            "token", "bearer.token.value",
            "nested", Map.of("clientSecret", "super_secret_123")
        );

        assertThatNoException().isThrownBy(() ->
            noOpAuditService.record("usr-1", "CUSTOMER", "USER_LOGIN_FAILED", "USER", "usr-1", details)
        );
    }

    @Test
    @DisplayName("NoOpAuditService.record(AuditEvent) handles null and sensitive events safely")
    void record_withAuditEvent_isSafe() {
        assertThatNoException().isThrownBy(() -> noOpAuditService.record(null));

        AuditEvent event = new AuditEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            "admin-1",
            "ADMIN",
            "CREDENTIALS_UPDATED",
            "USER",
            "usr-2",
            "127.0.0.1",
            "corr-99",
            Map.of("rawPassword", "NewPassword123!", "authHeader", "Bearer xyz")
        );

        assertThatNoException().isThrownBy(() -> noOpAuditService.record(event));
    }
}

