# SwiftDrop Week 7 M8 — Test Courier Authorization, Delivery Proof Persistence, Parcel Completion, and Audit Logging Verification

**Repository:** `https://github.com/Nirukshith/swiftdrop`  
**Base Branch:** `development` (at `1670de9`)  
**Feature Branch:** `test/m8-week7-courier-verification`  
**Assigned Member:** M8 — Harsha Kumarasamy (`harshakumarasamy22@gmail.com`)  
**Linked GitHub Issues:** Closes #245, Closes #235  
**Related Historical Issues & PRs:** Refs #180, Refs #192, Refs #214, Refs #225, Refs #263  

---

## 1. Executive Summary

This document verifies the Week 7 deliverables for Member M8, focusing on:
1. **Courier Authorization:** Verification of HTTP boundary security, role checks (`ROLE_COURIER`, `ROLE_DISPATCHER`, `ROLE_ADMIN`), resource ownership validation, and rejection of unauthorized or unauthenticated requests.
2. **Delivery Proof Persistence:** Multi-format validation (JPEG, PNG, WEBP), magic byte verification, 5MB file size limit enforcement, path traversal protection, PostgreSQL metadata persistence, and restart persistence with SHA-256 integrity checks.
3. **Parcel Completion Workflows:** Full lifecycle status transitions (`OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED`, `FAILED`, `RETURNED_TO_HUB`), proof/reason prerequisite enforcement, idempotency, and downstream resilience with `X-Correlation-Id` propagation.
4. **Audit Logging:** End-to-end audit recording across `ops_db.audit_log` and `auth_db.audit_log`, transaction isolation (`Propagation.REQUIRES_NEW`) ensuring rejection events persist even when business operations fail, and zero sensitive credential leakage.

---

## 2. ADR Compliance

Mandatory status check executed against `origin/development`:
```bash
git grep -n "^Status" origin/development -- docs/decisions
```
- All six ADRs (001–006) are confirmed in `decided` status (none `open` or `rejected`).

---

## 3. Defect Discovery and Correction

### Defect: Audit Rollback on Operation Rejection
- **Root Cause:** When `uploadProof` or `updateDeliveryStatus` encountered validation failures (e.g., file size exceeded, invalid magic bytes, or courier ownership mismatch), the service invoked `deliveryProofAuditService.record(...)` and then threw `ResponseStatusException`. Because the enclosing method was marked `@Transactional`, throwing an uncaught exception triggered Spring's transaction rollback, silently discarding the recorded audit rejection entry.
- **Fix:** Configured `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `DeliveryProofAuditService.record(...)`. This creates an independent transaction that commits the audit log immediately, ensuring audit events for rejected operations persist in `ops_db.audit_log` regardless of business transaction rollback.
- **Verification:** Verified via `CourierExecutionIntegrationTest` (`testProofUploadWithInvalidMagicBytesThrowsBadRequestAndAuditsRejection`, `testProofUploadExceeding5MbThrowsPayloadTooLargeAndAuditsRejection`, and `testStatusUpdateByMismatchedCourierAuditsUnauthorizedAttempt`).

---

## 4. Verification Evidence Across the 4 Focus Areas

### 4.1 Courier Authorization
- **HTTP/Controller Boundary Security:** Implemented `CourierSecurityTest` testing Spring Security filters with `CourierSecurityConfig`:
  - `GET /api/v1/courier/deliveries` $\rightarrow$ `401 Unauthorized` (unauthenticated)
  - `GET /api/v1/courier/deliveries/{id}` $\rightarrow$ `401 Unauthorized` (unauthenticated)
  - `PATCH /api/v1/courier/deliveries/{id}/status` $\rightarrow$ `401 Unauthorized` (unauthenticated)
  - `POST /api/v1/courier/deliveries/{id}/proof` $\rightarrow$ `401 Unauthorized` (unauthenticated)
  - `GET /api/v1/courier/proofs/{id}` $\rightarrow$ `401 Unauthorized` (unauthenticated)
  - Rejection of unauthorized roles (`CUSTOMER`, `MERCHANT`, `DISPATCHER`) attempting delivery execution with `403 Forbidden`.
  - Allowance of `COURIER` role for delivery endpoints (`200 OK` / `201 Created`).
  - Proof retrieval `/api/v1/courier/proofs/{id}` allows `COURIER`, `DISPATCHER`, and `ADMIN`.
- **Resource Ownership:** Rejection with `403 Forbidden` if a courier attempts to view, update, or upload proof for a parcel assigned to another courier.

### 4.2 Delivery Proof Persistence
- **Magic Byte Validation:** Validated real file headers for JPEG (`FF D8 FF`), PNG (`89 50 4E 47 0D 0A 1A 0A`), and WEBP (`RIFF...WEBP`). Executables, scripts, and corrupted bytes are rejected with `400 Bad Request`.
- **Size Limits:** Enforced $5\text{MB}$ limit ($5{,}242{,}880$ bytes). Oversized uploads are rejected with `413 Payload Too Large`.
- **Path Traversal Protection:** Target path is normalized and checked against base storage directory.
- **Restart Persistence:** Testcontainers integration tests verify that files saved to disk and metadata in PostgreSQL survive simulated service restarts, with matching SHA-256 content hashes.

### 4.3 Parcel Completion Workflows
- **Completion Prerequisites:**
  - Transition to `DELIVERED` requires either prior photo proof or `recipientName`; missing both returns `400 Bad Request`.
  - Transition to `FAILED` requires non-blank failure `reason`; missing reason returns `400 Bad Request`.
- **State Machine Compliance:** Valid transitions (`OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED`, `FAILED`, `RETURNED_TO_HUB`) succeed; invalid state leaps throw `409 Conflict`.
- **Idempotency:** Repeated status updates with the same status return current state idempotently without re-triggering transitions.
- **Fault Resilience:** Outgoing calls to `parcel-service` propagate `X-Correlation-Id` and wrap connection failures or network partitions into `503 Service Unavailable`.

### 4.4 Audit Logging
- **`ops_db.audit_log` Events:**
  - `DELIVERY_STATUS_UPDATED`: records previous/current status, recipient, reason, and idempotency key.
  - `DELIVERY_PROOF_UPLOADED`: records parcel ID, MIME type, file size, and proof URL.
  - `DELIVERY_PROOF_REJECTED`: records rejection reason (`FILE_SIZE_EXCEEDED`, `INVALID_FILE_TYPE`, `PARCEL_NOT_FOUND`, `OWNERSHIP_MISMATCH_OR_UNASSIGNED`).
  - `UNAUTHORIZED_COURIER_ACCESS_ATTEMPT`: records unauthorized access attempt with courier ID and parcel ID.
- **`auth_db.audit_log` Events:**
  - `USER_LOGIN_SUCCESS`, `USER_LOGIN_FAILED`, `USER_LOGOUT` for courier accounts.
  - Sensitive data (passwords, tokens, refresh tokens) are strictly redacted to `[REDACTED]`.

---

## 5. Automated Test Results

### 5.1 Operations Service Unit & Security Tests
```bash
mvn test "-Dtest=CourierSecurityTest,CourierExecutionControllerTest,CourierExecutionServiceTest,RestParcelServiceClientTest" -pl services/operations-service
```
- `CourierSecurityTest`: 16/16 passed
- `CourierExecutionControllerTest`: 4/4 passed
- `CourierExecutionServiceTest`: 21/21 passed
- `RestParcelServiceClientTest`: 4/4 passed
- **Subtotal:** 45/45 tests passed.

### 5.2 Operations Service Integration Tests (Testcontainers)
```bash
mvn test -Dtest=CourierExecutionIntegrationTest -pl services/operations-service
```
- `CourierExecutionIntegrationTest`: 11/11 passed (PostgreSQL 16 + Apache Kafka 3.8.1).

### 5.3 Full Module Verifications
```bash
# Operations Service (M8 & Operations)
mvn -B verify -pl services/operations-service -am
# Tests run: 185, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (01:12 min)

# Auth Service (Audit Service & Security)
mvn -B verify -pl services/auth-service -am
# Tests run: 231, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (01:44 min)

# Common Module
# Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### 5.4 Frontend Verification
```bash
cd frontend && npm test -- courier.component.spec.ts courier.service.spec.ts
```
- Test Suites: 2 passed, 2 total
- Tests: 32 passed, 32 total (Time: 9.815 s)

---

## 6. Acceptance Criteria Checklist

- [x] Relevant unit tests are added or updated (`CourierSecurityTest`, `RestParcelServiceClientTest`, `CourierExecutionServiceTest`).
- [x] Relevant integration tests are added or updated (`CourierExecutionIntegrationTest` with 11 Testcontainers scenarios).
- [x] Relevant end-to-end tests or reproducible verification procedures are provided.
- [x] Courier authorization is tested (unauthenticated 401, role 403, courier 200, ownership validation).
- [x] Delivery proof persistence is tested (PostgreSQL, SHA-256 disk verification, restart persistence).
- [x] Parcel completion workflow is tested (`OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED`/`FAILED`/`RETURNED_TO_HUB`, proof requirements, idempotency).
- [x] Audit logging is tested (`DELIVERY_STATUS_UPDATED`, `DELIVERY_PROOF_UPLOADED`, `DELIVERY_PROOF_REJECTED`, credential redaction).
- [x] Relevant validation and error cases are covered (size limit, magic bytes, missing reason/proof, invalid transitions).
- [x] Relevant failure and retry behavior is covered (inter-service outage 503, idempotent retries).
- [x] Confirmed defects within M8 ownership are fixed (`Propagation.REQUIRES_NEW` on audit service).
- [x] Required Maven verification passes (`operations-service`: 185 tests, `auth-service`: 231 tests).
- [x] Frontend verification passes (`32/32 passed`).
- [x] Exact commands and results are recorded.
- [x] Cross-service integration evidence is documented.
- [x] Remaining blockers are documented (none).
- [x] Documentation is updated (`docs/m8-week7-courier-verification.md`).
- [x] A focused PR targeting `development` is opened and linked.
