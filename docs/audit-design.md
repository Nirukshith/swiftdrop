# Audit Logging Architecture & Service Contract

**Owner:** Member M8  
**Service:** `auth-service` (`com.swiftdrop.auth.audit`)  
**Port:** `8081`  
**Database:** `auth_db` (PostgreSQL 16)  
**Governance:** ADR 001 (Audit viewer restricted exclusively to `ADMIN`)  

---

## 1. Overview & Purpose

The Audit Logging system provides non-repudiation, governance, and traceability across the SwiftDrop platform. It records identity, security, and administrative actions performed across portals and services, including:
* Account lockouts and failed logins (M7, M10)
* User account creation, role assignment, enable/disable toggles (M4)
* Merchant application approval or rejection (M5)
* Password reset requests and completions (M3)
* Parcel assignment, re-queue, and manual overrides (M4)

Per ADR 001, audit event inspection (`/api/v1/admin/audit`) is reserved strictly for users with the `ADMIN` role.

---

## 2. Java Service Contract

To enable teammates (M10, M3, M4, M7) to wire audit calls into their endpoints immediately without blocking on Week 2 database migrations, M8 provides the `AuditService` interface along with an active `NoOpAuditService` fallback.

### 2.1 `AuditService` Interface

```java
package com.swiftdrop.auth.audit;

import java.util.Map;

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
        // Implementation retrieves CurrentUser.get() or falls back to "SYSTEM" / "ANONYMOUS"
    }

    /**
     * Records a pre-built AuditEvent object.
     */
    void record(AuditEvent event);
}
```

### 2.2 `AuditEvent` Value Object

```java
package com.swiftdrop.auth.audit;

import java.time.Instant;
import java.util.Map;

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
```

### 2.3 `NoOpAuditService` Fallback

The `NoOpAuditService` implements `AuditService` by emitting structured SLF4J logs at `INFO` level. It is annotated with `@ConditionalOnMissingBean(AuditService.class)`:

```java
package com.swiftdrop.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;

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
```

Teammates (M3, M4, M10) can immediately inject and invoke `AuditService` in their controllers and event listeners. Tests run green without spinning up PostgreSQL or running Flyway audit migrations.

---

## 3. Database Persistence Schema (`auth_db`)

In Week 2, M8 will introduce Flyway migration `V2__create_audit_log.sql` in `auth-service`:

```sql
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id       VARCHAR(64) NOT NULL,
    actor_role     VARCHAR(32) NOT NULL,
    action         VARCHAR(64) NOT NULL,
    target_type    VARCHAR(32) NOT NULL,
    target_id      VARCHAR(64),
    ip_address     VARCHAR(45),
    correlation_id VARCHAR(64),
    details        JSONB
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor_id ON audit_log (actor_id);
CREATE INDEX idx_audit_log_action ON audit_log (action);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
```

---

## 4. Admin Query API Specification

* **Endpoint**: `GET /api/v1/admin/audit`
* **Access**: Restricted strictly to `ROLE_ADMIN` (ADR 001)
* **Query Parameters**:
  * `action`: Optional filter (e.g. `USER_LOCKED`, `MERCHANT_APPROVED`)
  * `actorId`: Optional filter by user ID
  * `targetType`: Optional filter (`USER`, `MERCHANT`, `PARCEL`)
  * `fromDate` / `toDate`: ISO date-time range filters
  * `page` (default `0`), `size` (default `20`, max `100`)
* **Response Payload (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": "f5b61a49-92c2-4cb6-a67b-117565eb0012",
        "occurredAt": "2026-10-25T14:22:10Z",
        "actorId": "admin-uuid-001",
        "actorRole": "ADMIN",
        "action": "MERCHANT_APPROVED",
        "targetType": "MERCHANT",
        "targetId": "merchant-uuid-99",
        "ipAddress": "192.168.1.15",
        "correlationId": "8f3b89e2-1e94-4d1a-8bb4-fae26610bca4",
        "details": {
          "businessName": "Colombo Tech Ltd",
          "approvedCreditLimit": 50000
        }
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
  ```

---

## 5. Teammate Integration Guide (M10, M3, M4, M7)

To record an audit event from any package in `auth-service`:

```java
@Autowired
private AuditService auditService;

public void lockAccount(String userId, String reason) {
    // Business logic...
    auditService.record(
        userId,
        "ANONYMOUS",
        "ACCOUNT_LOCKED",
        "USER",
        userId,
        Map.of("reason", reason, "attemptCount", 5)
    );
}
```
