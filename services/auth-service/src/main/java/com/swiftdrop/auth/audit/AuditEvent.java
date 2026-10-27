package com.swiftdrop.auth.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Value object representing an audit record.
 * Aligned with docs/audit-design.md (M8).
 */
public record AuditEvent(
    String eventId,
    Instant occurredAt,
    String actorId,
    String actorRole,
    String action,
    String targetType,
    String targetId,
    String ipAddress,
    String correlationId,
    Map<String, Object> details
) {}
