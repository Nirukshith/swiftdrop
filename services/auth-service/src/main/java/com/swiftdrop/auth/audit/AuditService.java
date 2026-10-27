package com.swiftdrop.auth.audit;

import java.util.Map;

/**
 * In-process audit logging service interface.
 * Aligned with docs/audit-design.md (M8).
 */
public interface AuditService {

    /**
     * Records an audit event with full explicit actor and target details.
     *
     * @param actorId    ID of the user performing the action (or "SYSTEM")
     * @param actorRole  Role of the actor (e.g. "ADMIN", "DISPATCHER", "ANONYMOUS")
     * @param action     Action code (e.g. "USER_LOCKED", "MERCHANT_APPROVED")
     * @param targetType Type of entity affected (e.g. "USER", "MERCHANT", "PARCEL")
     * @param targetId   Identifier of the target entity
     * @param details    Structured metadata or changed field values
     */
    void record(String actorId,
                String actorRole,
                String action,
                String targetType,
                String targetId,
                Map<String, Object> details);

    /**
     * Convenience method extracting actorId and actorRole automatically from CurrentUser.
     */
    default void record(String action,
                        String targetType,
                        String targetId,
                        Map<String, Object> details) {
        // Default fallback
        record("SYSTEM", "SYSTEM", action, targetType, targetId, details);
    }

    /**
     * Records a pre-built AuditEvent object.
     */
    void record(AuditEvent event);
}
