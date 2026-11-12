package com.swiftdrop.auth.audit;

import com.swiftdrop.common.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceCurrentUserTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Convenience record resolves actorId and role from CurrentUser when authenticated")
    void record_resolvesActorFromCurrentUser() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "admin-uuid-456",
            "N/A",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<String> capturedActorId = new AtomicReference<>();
        AtomicReference<String> capturedActorRole = new AtomicReference<>();

        AuditService testService = new AuditService() {
            @Override
            public void record(String actorId, String actorRole, String action, String targetType, String targetId, Map<String, Object> details) {
                capturedActorId.set(actorId);
                capturedActorRole.set(actorRole);
            }

            @Override
            public void record(AuditEvent event) {}
        };

        testService.record("STAFF_USER_CREATED", "USER", "new-staff-1", Map.of("role", "COURIER"));

        assertThat(capturedActorId.get()).isEqualTo("admin-uuid-456");
        assertThat(capturedActorRole.get()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Convenience record falls back to SYSTEM when no security context exists")
    void record_fallsBackToSystemWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        AtomicReference<String> capturedActorId = new AtomicReference<>();
        AtomicReference<String> capturedActorRole = new AtomicReference<>();

        AuditService testService = new AuditService() {
            @Override
            public void record(String actorId, String actorRole, String action, String targetType, String targetId, Map<String, Object> details) {
                capturedActorId.set(actorId);
                capturedActorRole.set(actorRole);
            }

            @Override
            public void record(AuditEvent event) {}
        };

        testService.record("SYSTEM_JOB_TRIGGERED", "SYSTEM", "job-1", Map.of("status", "SUCCESS"));

        assertThat(capturedActorId.get()).isEqualTo("SYSTEM");
        assertThat(capturedActorRole.get()).isEqualTo("SYSTEM");
    }
}

