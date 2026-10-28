package com.swiftdrop.operations.zones;

import com.swiftdrop.common.Roles;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZoneAuditService {

    private final AuditLogRepository repository;

    public ZoneAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String targetId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "SYSTEM" : authentication.getName();
        String role = authentication == null ? Roles.DISPATCHER : authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(Roles.DISPATCHER);
        repository.save(new AuditLog(actor, role, action, "ZONE_OR_PRICING_RULE", targetId, Map.of()));
    }

    @Entity
    @Table(name = "audit_log")
    static class AuditLog {
        @Id private UUID id = UUID.randomUUID();
        @Column(name = "occurred_at", nullable = false) private Instant occurredAt = Instant.now();
        @Column(name = "actor_id", nullable = false) private String actorId;
        @Column(name = "actor_role", nullable = false) private String actorRole;
        @Column(nullable = false) private String action;
        @Column(name = "target_type", nullable = false) private String targetType;
        @Column(name = "target_id") private String targetId;
        @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> details;
        protected AuditLog() {}
        AuditLog(String actorId, String actorRole, String action, String targetType, String targetId, Map<String, Object> details) {
            this.actorId = actorId; this.actorRole = actorRole; this.action = action;
            this.targetType = targetType; this.targetId = targetId; this.details = details;
        }
    }

}
