# SwiftDrop — Week 6 M4 Workflow Completion & Verification Guide

**Assigned Member:** M4 — @Agnes-Ostina  
**Issue:** [#221 — Complete Admin User Management, Dispatcher Controls, Failed Parcel Re-queuing, and Workload Management](https://github.com/Nirukshith/swiftdrop/issues/221)  
**Base Branch:** `development`  
**Feature Branch:** `feature/m4-week6-workflow-completion`

---

## 1. Executive Summary

This document serves as the comprehensive verification record and architecture handoff for Member M4's Week 6 responsibilities on the SwiftDrop distributed logistics platform.

All assigned functionalities have been verified end-to-end across backend microservices and the Angular web application:
1. **Admin Staff User Management (AU-05):** Server-side paginated staff user listing, strict `COURIER` and `DISPATCHER` account creation, duplicate email rejection (`409 Conflict`), BCrypt password hashing, and account enable/disable mutation (`PATCH /api/v1/admin/users/{id}`).
2. **Dispatcher Controls (DI-01, DI-02, DI-03, DI-06):** Unassigned parcel queue with zone and date filtering, real courier directory integration via M9's primary adapter, single parcel assignment and pre-pickup reassignment, bulk assignment with duplicate detection, and courier availability toggling.
3. **Failed Parcel Re-queuing (DI-07):** Querying `FAILED` delivery parcels, performing the authoritative `FAILED -> CREATED` lifecycle transition via M1's `parcel-service`, clearing courier assignment, incrementing delivery attempt count exactly once, and handling optimistic concurrency conflicts with automatic queue refresh.
4. **Courier Workload Management (ADR 005):** Pure-Java domain workload tracking, Kafka event consumption (`parcel.assigned.v1` and `parcel.status-changed.v1`), persistent per-parcel sequence tracking, database-level event deduplication, and verified re-queue invariance (ensuring re-queued parcels do not corrupt or double-decrement active delivery counts).
5. **Security & Role-Based Access Control (ADR 001):** Strict enforcement where `/api/v1/admin/users/**` is accessible only by `ROLE_ADMIN`, and `/api/v1/dispatch/**` is accessible only by `ROLE_DISPATCHER`. All other roles (`CUSTOMER`, `MERCHANT`, `COURIER`, unauthenticated) are explicitly rejected with `401 Unauthorized` or `403 Forbidden`.

---

## 2. Requirement Traceability Matrix

| Requirement ID | Description | Owning Service / Path | Frontend Component | Contract / ADR Reference | Verification Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AU-05** | Admin user management (list, filter, create staff, enable/disable) | `auth-service`<br>`com.swiftdrop.auth.admin` | `AdminUsersComponent`<br>`/admin/users` | `contracts/openapi/auth-admin.yaml`<br>ADR 001 | **VERIFIED** |
| **DI-01** | List unassigned parcels filtered by zone & date | `operations-service`<br>`com.swiftdrop.operations.dispatch` | `DispatchComponent`<br>`/dispatch` | `contracts/openapi/operations-dispatch.yaml` | **VERIFIED** |
| **DI-02** | Assign parcel to courier; reassign before pickup | `operations-service`<br>`com.swiftdrop.operations.dispatch` | `DispatchComponent`<br>`/dispatch` | `contracts/openapi/operations-dispatch.yaml`<br>ADR 005 | **VERIFIED** |
| **DI-03** | Courier workload board (active delivery counts & availability) | `operations-service`<br>`com.swiftdrop.operations.dispatch` | `DispatchComponent`<br>`/dispatch` | `contracts/openapi/operations-dispatch.yaml`<br>ADR 005 | **VERIFIED** |
| **DI-06** | Courier availability toggle & bulk parcel assignment | `operations-service`<br>`com.swiftdrop.operations.dispatch` | `DispatchComponent`<br>`/dispatch` | `contracts/openapi/operations-dispatch.yaml` | **VERIFIED** |
| **DI-07** | Re-queue FAILED parcel for another delivery attempt | `operations-service`<br>`com.swiftdrop.operations.dispatch` | `DispatchComponent`<br>`/dispatch` | `contracts/openapi/operations-dispatch.yaml`<br>ADR 005 | **VERIFIED** |

---

## 3. Security & Role Authorization Matrix (ADR 001)

Spring Security filters and Angular route guards enforce strict role boundaries across the application:

| Endpoint Pattern | Allowed Roles | Denied Roles (`403 Forbidden` / `401 Unauthorized`) | Test Verification Class |
| :--- | :--- | :--- | :--- |
| `/api/v1/admin/users/**` | `ROLE_ADMIN` | `ROLE_DISPATCHER`, `ROLE_COURIER`, `ROLE_CUSTOMER`, `ROLE_MERCHANT`, Unauthenticated | `AdminUserSecurityTest` |
| `/api/v1/dispatch/**` | `ROLE_DISPATCHER` | `ROLE_ADMIN`, `ROLE_COURIER`, `ROLE_CUSTOMER`, `ROLE_MERCHANT`, Unauthenticated | `DispatchSecurityTest` |
| Route `/admin/users` | `ADMIN` | Non-admin users redirected by `roleGuard(['ADMIN'])` | `app.routes.ts` |
| Route `/dispatch` | `DISPATCHER` | Non-dispatcher users redirected by `roleGuard(['DISPATCHER'])` | `app.routes.ts` |

---

## 4. Workload State Machine & Event Lifecycle (ADR 005)

The courier workload is dynamically managed by `DispatchEventListener` consuming Kafka topics:

| Event Topic | Event Type | State Transition | Workload Action | Replay / Sequence Protection |
| :--- | :--- | :--- | :--- | :--- |
| `parcel.assigned.v1` | `PARCEL_ASSIGNED` | Initial assignment | `increment(courierId)` | Checked against `processed_event` table and `parcel_sequence` table. |
| `parcel.assigned.v1` | `PARCEL_ASSIGNED` | Reassignment (`previousCourierId` != `null`) | `decrement(previousCourierId)`<br>`increment(courierId)` | Balanced transaction; no-op if same courier reassigned. |
| `parcel.status-changed.v1` | `PARCEL_STATUS_CHANGED` | Transition to `DELIVERED`, `FAILED`, or `CANCELLED` | `decrement(courierId)` | Terminal states decrement count (bounded at 0). |
| `parcel.status-changed.v1` | `PARCEL_STATUS_CHANGED` | Transition from `FAILED` to `CREATED` (Re-queue) | **NO-OP (0 change)** | Non-terminal transition. Courier reference is null; previous failure already decremented count. |
| Any | Any | Duplicate `eventId` | **IGNORED** | Idempotently dropped via `processedEventRepository.existsByEventId()`. |
| Any | Any | `sequence <= lastSequence` | **IGNORED** | Dropped as stale/out-of-order via `parcelSequenceRepository`. |

---

## 5. Automated Test Evidence

### 5.1 Backend Verification

1. **`services/auth-service` (Admin Management & Security):**
   - Command: `mvn test -pl services/auth-service "-Dtest=AdminUserControllerTest,AdminUserServiceTest,AdminUserSecurityTest"`
   - Output: **BUILD SUCCESS**
   - Results: 21 tests run, 0 failures, 0 errors.
   - Verified:
     - Rejection of non-admin roles (`CUSTOMER`, `MERCHANT`, `DISPATCHER`, `COURIER`).
     - Validation and BCrypt password encoding.
     - Role restriction to `COURIER` and `DISPATCHER` only.
     - Duplicate email rejection with HTTP `409 Conflict`.
     - `user.registered.v1` event publication on creation.

2. **`services/operations-service` (Dispatch, Security, & Workload):**
   - Command: `mvn test -pl services/operations-service "-Dtest=DispatchControllerTest,DispatchServiceTest,DispatchEventListenerTest,WorkloadCounterTest,DispatchSecurityTest"`
   - Output: **BUILD SUCCESS**
   - Results: 51 tests run, 0 failures, 0 errors.
   - Verified:
     - Rejection of non-dispatcher roles (`ADMIN`, `CUSTOMER`, `MERCHANT`, `COURIER`, unauthenticated).
     - Querying unassigned parcels and failed parcels with zone and date filters.
     - Courier availability checks preventing assignment to unavailable couriers.
     - Single and bulk parcel assignment with duplicate batch detection.
     - Failed parcel re-queuing returning `RequeueResponse` with attempt count increment.
     - Workload increments, decrements, reassignment balance, and lower-bound clamping at 0.
     - Workload re-queue invariance (ensuring no second decrement on re-queue to `CREATED`).
     - Persistent event deduplication and out-of-order sequence rejection.

### 5.2 Frontend Verification

- Command: `npx jest --testPathPatterns="admin-users|dispatch"`
- Output:
  ```text
  PASS projects/web/src/app/pages/admin/admin-users.service.spec.ts
  PASS projects/web/src/app/pages/dispatch/dispatch.service.spec.ts
  PASS projects/web/src/app/pages/dispatch/dispatch-zones.component.spec.ts
  PASS projects/web/src/app/pages/dispatch/dispatch-pricing.component.spec.ts
  PASS projects/web/src/app/pages/admin/admin-users.component.spec.ts
  PASS projects/web/src/app/pages/dispatch/dispatch.component.spec.ts

  Test Suites: 6 passed, 6 total
  Tests:       73 passed, 73 total
  Snapshots:   0 total
  ```

---

## 6. Cross-Member Boundaries & Coordination

1. **M1 (`parcel-service`):**
   - **Interface:** Internal assignment (`PUT /internal/v1/parcels/{id}/assignment`) and status update (`PATCH /internal/v1/parcels/{id}/status`).
   - **Contract:** M1 owns the underlying parcel database table and state machine. M4 interacts strictly via REST client (`RestParcelServiceClient.java`) and Kafka events.
2. **M9 (`courier.profile` & DevOps):**
   - **Interface:** `CourierDirectory` interface implemented by M9's `@Primary` `M4CourierDirectoryAdapter`.
   - **Contract:** M4 delegates courier listing, zone lookup, and availability updates directly through M9's service adapter. M9 also provisions shared Testcontainers Kafka infrastructure for test verification.
3. **M10 (`auth.security`):**
   - **Interface:** Spring Security JWT decoder and role claims (`roles`).
   - **Contract:** M4 enforces `ROLE_ADMIN` on `/api/v1/admin/users/**` and honors disabled account flags in authentication sessions.

---

## 7. Acceptance Criteria Checklist (Issue #221)

- [x] Relevant M4 SRS requirements and issue #221 scope are satisfied (AU-05, DI-01, DI-02, DI-03, DI-06, DI-07).
- [x] Existing completed features from Weeks 3–5 are reused rather than rewritten.
- [x] Admin staff-management authorization and validation are covered (including all 5 role checks).
- [x] Dispatcher controls use real authoritative data via M1 and M9.
- [x] Failed parcel re-queuing follows the approved lifecycle (`FAILED -> CREATED`).
- [x] Courier assignment clearing and attempt count increments are verified.
- [x] Workload changes, replay/idempotency, and re-queue invariance are fully covered.
- [x] Relevant frontend and backend tests pass (73 frontend tests, 51 operations tests, 21 auth tests).
- [x] Documentation and reproducible evidence are completed.
- [x] Focused PR branch prepared for `development`.
