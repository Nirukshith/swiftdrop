# SwiftDrop Week 6 M8 — Courier Delivery Workflows, Proof Storage & Audit Verification

**Repository:** https://github.com/Nirukshith/swiftdrop  
**GitHub Issue:** [#225](https://github.com/Nirukshith/swiftdrop/issues/225)  
**Related Issue:** [#180](https://github.com/Nirukshith/swiftdrop/issues/180)  
**Related PRs:** [#192](https://github.com/Nirukshith/swiftdrop/pull/192), [#211](https://github.com/Nirukshith/swiftdrop/pull/211), [#214](https://github.com/Nirukshith/swiftdrop/pull/214)  
**Member:** M8 — Harsha Kumarasamy  
**Base Branch:** `development`  
**Feature Branch:** `feature/m8-week6-delivery-workflows`

---

## 1. Summary of Completed Deliverables

This deliverable fulfills all requirements and acceptance criteria for **Issue #225 (Week 6 M8)**:

### A. Courier Delivery Execution Workflows
- **Queue Retrieval:** `GET /api/v1/courier/deliveries` returns today's assigned deliveries for the authenticated courier with date and status filters, Google Maps link generation, attempt count, and sequence ordering.
- **Delivery Detail:** `GET /api/v1/courier/deliveries/{parcelId}` retrieves full parcel details and enforces courier assignment ownership.
- **State Transition Machine:** `PATCH /api/v1/courier/deliveries/{parcelId}/status` enforces deterministic lifecycle progression:
  $$\text{CREATED} \longrightarrow \text{PICKED\_UP} \longrightarrow \text{IN\_TRANSIT} \longrightarrow \text{OUT\_FOR\_DELIVERY} \longrightarrow \text{DELIVERED or FAILED}$$
  - Idempotent status repeats return `200 OK` without duplicate side-effects.
  - Illegal status transitions/leaps are rejected with `409 Conflict`.
  - Terminal `DELIVERED` requires recipient name or prior uploaded photo proof.
  - Terminal `FAILED` requires a non-blank failure reason.
- **Inter-Service Resilience & Fault Handling:**
  - `RestParcelServiceClient` propagates the incoming request `X-Correlation-Id` header from `CorrelationIdFilter` on all outgoing calls to `parcel-service`.
  - Outages or timeouts connecting to `parcel-service` (`ResourceAccessException`) are gracefully wrapped into `503 Service Unavailable` responses.

### B. Delivery Proof Functionality
- **Content-Based Magic Byte Validation:** `ContentTypeDetector` inspects raw file bytes to enforce valid image formats:
  - JPEG: `FF D8 FF`
  - PNG: `89 50 4E 47 0D 0A 1A 0A`
  - WEBP: `RIFF...WEBP`
- **Payload & Path Security:**
  - File size limit enforced at 5 MB (returns `413 Payload Too Large` on violation).
  - File paths strictly confined to configured `storageDir` (preventing directory traversal attacks).
- **Restart Persistence:** Proof files are stored in persistent volume `/data/proofs/{parcelId}-{timestamp}.{ext}`.
- **Authorization Enforcement:** `GET /api/v1/courier/proofs/{id}` restricts access to the assigned courier, dispatchers, and administrators. Foreign couriers receive `403 Forbidden`.

### C. AuditService Integration
- **Delivery Action Auditing:** `DeliveryProofAuditService` persists events in `ops_db.audit_log`:
  - `DELIVERY_PROOF_UPLOADED`: Records uploaded proof ID, size, MIME type, and courier ID.
  - `DELIVERY_PROOF_REJECTED`: Records rejections on magic bytes, file size, unassigned parcels, or unauthorized access.
  - `DELIVERY_STATUS_UPDATED`: Records successful lifecycle status transitions, previous status, current status, recipient, reason, and idempotency key.
- **Authentication & Security Auditing:** `AuditService` in `auth-service` persists events in `auth_db.audit_log`:
  - `USER_LOGIN_SUCCESS` and `USER_LOGIN_FAILED` for courier accounts.
  - Sensitive credentials (`password`, `refreshToken`) recursively redacted (`[REDACTED]`).

---

## 2. Test Verification Evidence

### 2.1 Backend Tests (Maven - Java 21)

#### `operations-service` Verification:
```bash
mvn -B verify -pl services/operations-service -am
```
- **Results:**
  - `swiftdrop`: SUCCESS
  - `common`: 13 tests run, 0 failures, 0 errors
  - `operations-service`: 126 tests run, 0 failures, 0 errors (includes `CourierExecutionServiceTest`, `CourierExecutionControllerTest`, `CourierExecutionIntegrationTest`, and `RestParcelServiceClientTest`)
  - **Total Reactor Status:** BUILD SUCCESS (01:09 min)

#### `auth-service` Verification:
```bash
mvn -B verify -pl services/auth-service -am
```
- **Results:**
  - `swiftdrop`: SUCCESS
  - `common`: 13 tests run, 0 failures, 0 errors
  - `auth-service`: 185 tests run, 0 failures, 0 errors (includes `AuditPersistenceIntegrationTest`, `AdminAuditSecurityTest`, `AuditServiceCurrentUserTest`)
  - **Total Reactor Status:** BUILD SUCCESS (01:22 min)

### 2.2 Frontend Tests (Jest - Node 24 / Angular 21)
```bash
npm test -- courier.component.spec.ts courier.service.spec.ts
```
- **Results:**
  - `PASS projects/web/src/app/pages/courier/courier.service.spec.ts`
  - `PASS projects/web/src/app/pages/courier.component.spec.ts`
  - **Test Suites:** 2 passed, 2 total
  - **Tests:** 32 passed, 32 total

---

## 3. Acceptance Criteria Checklist (Issue #225)

- [x] All requirements in the linked SRS and this issue's assigned scope are implemented.
- [x] Authorization, validation, error handling, and failure/retry behaviour are covered where relevant.
- [x] Relevant backend and frontend tests pass, with results recorded.
- [x] Integration with dependent services is verified or blockers are clearly documented.
- [x] Documentation and evidence are updated.
- [x] A focused PR targeting `development` is opened and linked to Issue #225.
