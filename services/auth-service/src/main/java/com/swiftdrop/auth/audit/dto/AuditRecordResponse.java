package com.swiftdrop.auth.audit.dto;

import com.swiftdrop.auth.audit.AuditLogEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit record representation in admin audit retrieval responses.
 * Aligned with OpenAPI contract and docs/audit-design.md (M8).
 */
public record AuditRecordResponse(
    UUID id,
    Instant occurredAt,
    String actorId,
    String actorRole,
    String action,
    String targetType,
    String targetId,
    String ipAddress,
    String correlationId,
    Map<String, Object> details
) {
    public static AuditRecordResponse from(AuditLogEntity entity) {
        return new AuditRecordResponse(
            entity.getId(),
            entity.getOccurredAt(),
            entity.getActorId(),
            entity.getActorRole(),
            entity.getAction(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getIpAddress(),
            entity.getCorrelationId(),
            entity.getDetails()
        );
    }
}
