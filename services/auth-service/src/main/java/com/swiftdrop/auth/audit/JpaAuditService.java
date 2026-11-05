package com.swiftdrop.auth.audit;

import com.swiftdrop.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.*;

/**
 * Persistent AuditService implementation saving audit records to auth_db.
 * Owned by M8.
 */
@Service
@Primary
public class JpaAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditService.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password",
        "password_hash",
        "passwordhash",
        "secret",
        "client_secret",
        "clientsecret",
        "token",
        "access_token",
        "accesstoken",
        "refresh_token",
        "refreshtoken",
        "rawrefreshtoken",
        "raw_refresh_token",
        "code",
        "authorization_code",
        "authorizationcode",
        "credentials",
        "private_key",
        "privatekey",
        "session"
    );

    private final AuditLogRepository auditLogRepository;

    public JpaAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void record(String actorId, String actorRole, String action,
                       String targetType, String targetId, Map<String, Object> details) {
        AuditEvent event = new AuditEvent(
            null,
            Instant.now(),
            actorId != null ? actorId : "ANONYMOUS",
            actorRole != null ? actorRole : "ANONYMOUS",
            action,
            targetType,
            targetId,
            null,
            null,
            details
        );
        record(event);
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        if (event == null) {
            log.warn("Attempted to record null AuditEvent");
            return;
        }

        try {
            UUID id = parseOrGenerateUuid(event.eventId());
            Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
            String actorId = (event.actorId() != null && !event.actorId().isBlank()) ? event.actorId() : "ANONYMOUS";
            String actorRole = (event.actorRole() != null && !event.actorRole().isBlank()) ? event.actorRole() : "ANONYMOUS";
            String action = event.action() != null ? event.action() : "UNKNOWN";
            String targetType = event.targetType() != null ? event.targetType() : "SYSTEM";
            String targetId = event.targetId();
            String ipAddress = resolveIpAddress(event.ipAddress(), event.details());
            String correlationId = resolveCorrelationId(event.correlationId());
            Map<String, Object> sanitizedDetails = sanitizeDetails(event.details());

            AuditLogEntity entity = new AuditLogEntity(
                id,
                occurredAt,
                actorId,
                actorRole,
                action,
                targetType,
                targetId,
                ipAddress,
                correlationId,
                sanitizedDetails
            );

            auditLogRepository.save(entity);
            log.debug("Persisted audit event id={} action={} actor={}:{}", id, action, actorRole, actorId);
        } catch (Exception ex) {
            log.error("Failed to persist audit event action={} actorId={}: {}",
                event.action(), event.actorId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private UUID parseOrGenerateUuid(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }

    private String resolveCorrelationId(String existingCorrelationId) {
        if (existingCorrelationId != null && !existingCorrelationId.isBlank()) {
            return existingCorrelationId;
        }
        String mdcCorrelationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (mdcCorrelationId != null && !mdcCorrelationId.isBlank()) {
            return mdcCorrelationId;
        }
        RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
        if (attribs instanceof ServletRequestAttributes servletAttribs) {
            HttpServletRequest request = servletAttribs.getRequest();
            String header = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return null;
    }

    private String resolveIpAddress(String existingIp, Map<String, Object> details) {
        if (existingIp != null && !existingIp.isBlank()) {
            return existingIp;
        }
        if (details != null && details.get("ipAddress") != null) {
            return String.valueOf(details.get("ipAddress"));
        }
        RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
        if (attribs instanceof ServletRequestAttributes servletAttribs) {
            HttpServletRequest request = servletAttribs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    public static Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String lowerKey = key.trim().toLowerCase();
            boolean isSensitive = SENSITIVE_KEYS.contains(lowerKey)
                || lowerKey.endsWith("password")
                || lowerKey.endsWith("secret")
                || lowerKey.endsWith("token");
            if (isSensitive) {
                sanitized.put(key, "[REDACTED]");
            } else {
                sanitized.put(key, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
