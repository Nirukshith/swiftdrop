package com.swiftdrop.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fallback audit logging service logging structured audit events via SLF4J.
 * Aligned with docs/audit-design.md (M8).
 */
@Service
@ConditionalOnMissingBean(type = "com.swiftdrop.auth.audit.JpaAuditService")
public class NoOpAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(NoOpAuditService.class);

    @Override
    public void record(String actorId, String actorRole, String action,
                       String targetType, String targetId, Map<String, Object> details) {
        log.info("[AUDIT] actor={} role={} action={} target={}:{} details={}",
            actorId, actorRole, action, targetType, targetId, details);
    }

    @Override
    public void record(AuditEvent event) {
        log.info("[AUDIT-EVENT] {}", event);
    }
}
