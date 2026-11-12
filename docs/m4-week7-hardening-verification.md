# SwiftDrop — Week 7 M4 Test & Hardening Verification Guide

**Assigned Member:** M4 — @Agnes-Ostina  
**Issue:** [#231 — Test and Harden Dispatcher Assignment, Admin Management, Courier Workload, and Failed Parcel Recovery](https://github.com/Nirukshith/swiftdrop/issues/231)  
**Base Branch:** `development`  
**Feature Branch:** `test/m4-week7-dispatch-admin-hardening`

---

## 1. Executive Summary

This document details the testing, hardening, security validation, and cross-service boundary verification completed for Member M4 during Week 7 of the SwiftDrop project.

Building upon the merged foundational workflows from Weeks 3–6 (PRs #198 and #259), the focus of Week 7 was hardening edge cases, verifying all 5 system roles across mutating and query endpoints, adding explicit error response coverage (404/409/400), testing Dead Letter Queue (DLT) consumer resilience, and explicitly documenting test harness constraints.

Key Accomplishments:
1. **Full Role Authorization Hardening (ADR 001):** Extended automated security tests to cover mutating operations (`POST /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}`, `POST /api/v1/dispatch/assignments`, `PATCH /api/v1/dispatch/couriers/{id}/availability`) across all 5 roles (`ADMIN`, `DISPATCHER`, `COURIER`, `CUSTOMER`, `MERCHANT`) and unauthenticated callers.
2. **Assignment & Reassignment Error Coverage:** Added explicit `404 Not Found` test cases in `DispatchControllerTest` for both missing parcel (`ParcelNotFoundException`) and missing courier (`CourierNotFoundException`).
3. **Kafka Consumer Resilience & Dead Letter Queue:** Added explicit test cases in `DispatchEventListenerTest` for `@DltHandler` error logging and safe rejection of malformed or blank event envelopes without state corruption.
4. **Harness Limitation Documentation:** Formally analyzed and documented the architectural limitation of `DispatchIntegrationTest` (which verifies end-to-end database transactions via direct listener invocation rather than external broker producer delivery).

---

## 2. Requirements & Hardening Coverage Matrix

| Requirement | Scope | Test Class | Total Tests | What Is Verified | Edge Cases & Hardening Added |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AU-05** Admin Management | `auth-service` | `AdminUserControllerTest` | 6 | Paginated user listing, staff creation (COURIER/DISPATCHER), duplicate email (409), status toggle (200/404) | Role validation, duplicate email handling |
| **AU-05** Admin Service Logic | `auth-service` | `AdminUserServiceTest` | 9 | Password hashing (BCrypt), role filtering, event publishing (`user.registered.v1`) | Rejection of unauthorized roles (`ADMIN`, `CUSTOMER`, `MERCHANT`) |
| **AU-05 / ADR 001** Admin Security | `auth-service` | `AdminUserSecurityTest` | 18 | Endpoint protection on `GET`, `POST`, and `PATCH` | **Hardened:** Verified `POST` and `PATCH` reject `DISPATCHER`, `COURIER`, `CUSTOMER`, `MERCHANT`, and unauthenticated requests |
| **DI-01 / DI-02** Dispatch Controller | `operations-service` | `DispatchControllerTest` | 14 | Listing unassigned parcels, assignment, pre-pickup reassignment, bulk assignment, availability toggle | **Hardened:** Added `404 Not Found` for missing parcel and missing courier |
| **DI-01 / DI-02** Dispatch Service | `operations-service` | `DispatchServiceTest` | 11 | Zone filtering, courier availability validation, bulk assignment batch duplicate detection | Partial success and duplicate parcel detection in batch requests |
| **DI-01 / DI-02 / ADR 001** Dispatch Security | `operations-service` | `DispatchSecurityTest` | 24 | Endpoint protection on `GET /parcels`, `GET /failed`, `POST /assignments`, `PATCH /availability` | **Hardened:** Verified mutations reject `ADMIN`, `COURIER`, `CUSTOMER`, `MERCHANT`, and unauthenticated requests |
| **ADR 005** Domain Workload Counter | `operations-service` | `WorkloadCounterTest` | 10 | Pure-Java event counter: initial assignment, reassignment, terminal status decrement, bounded at 0 | Re-queue invariance (`FAILED -> CREATED` causes 0 workload change) |
| **ADR 005** Kafka Event Listener | `operations-service` | `DispatchEventListenerTest` | 10 | DB transactions, idempotency check, persistent per-parcel sequence check | **Hardened:** Added DLT handler execution and null/blank envelope resilience |
| **Frontend UI** Admin Portal | `web` | `admin-users.component.spec.ts`, `admin-users.service.spec.ts` | 18 | Table rendering, role/status filtering, creation modal, password clearing, error retry | Signal reconciliation, error toasts, form validation |
| **Frontend UI** Dispatch Portal | `web` | `dispatch.component.spec.ts`, `dispatch.service.spec.ts`, zones, pricing | 55 | Queue display, single/bulk assignment, failed parcel re-queue, polling lifecycle, cleanup | Conflict refresh, button disabled during pending mutations, timer destruction |

---

## 3. Comprehensive Role Authorization Matrix (ADR 001)

Automated security tests enforce strict role separation:

| Endpoint | Method | `ROLE_ADMIN` | `ROLE_DISPATCHER` | `ROLE_COURIER` | `ROLE_CUSTOMER` | `ROLE_MERCHANT` | Unauthenticated |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/admin/users` | `GET` | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/admin/users` | `POST` | **201 Created** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/admin/users/{id}` | `PATCH` | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/dispatch/parcels` | `GET` | 403 Forbidden | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden |
| `/api/v1/dispatch/parcels/failed` | `GET` | 403 Forbidden | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden |
| `/api/v1/dispatch/assignments` | `POST` | 403 Forbidden | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden |
| `/api/v1/dispatch/couriers/{id}/availability` | `PATCH` | 403 Forbidden | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden |

---

## 4. Kafka Event Processing & Test Harness Analysis

### 4.1 Workload State Transitions (ADR 005)
- **Initial Assignment (`parcel.assigned.v1`):** Increments assigned courier workload by 1.
- **Reassignment (`parcel.assigned.v1` with `previousCourierId`):** Decrements previous courier by 1, increments new courier by 1.
- **Same-Courier Reassignment:** No counter mutation (prevents double counting).
- **Terminal Status (`DELIVERED`, `FAILED`, `CANCELLED`):** Decrements courier workload by 1 (clamped at 0).
- **Re-queue (`FAILED -> CREATED`):** Workload is unaffected (0 change), as the prior failure already decremented it.
- **Idempotency & Replay:** Stale events (`sequence <= lastSequence`) and duplicates (`existsByEventId`) are rejected before state changes.

### 4.2 Integration Test Limitation Analysis
In `DispatchIntegrationTest.java`:
- The test spins up PostgreSQL and Kafka Testcontainers and verifies full database transaction persistence in `courier_workload`, `parcel_sequence`, and `processed_event` tables.
- However, events are delivered via direct method invocation: `dispatchEventListener.onEvent(jsonPayload)`.
- **Finding:** While this verifies Spring context, JPA repositories, transactional boundaries, and domain logic, it does not independently test network serialization over the live Kafka wire from `parcel-service` producer to broker partition.
- **Mitigation & Real E2E Evidence:** Live producer-to-broker verification is performed at the platform level via the full Docker Compose stack (`deploy/docker-compose.yml`), where Nginx, parcel-service, operations-service, and Kafka communicate over real internal networking.

---

## 5. Automated Test Evidence

### 5.1 Auth Service Tests
- **Command:** `mvn test -pl services/auth-service "-Dtest=AdminUserControllerTest,AdminUserServiceTest,AdminUserSecurityTest"`
- **Result:** **BUILD SUCCESS**
- **Test Count:** 33 tests run, 0 failures, 0 errors, 0 skipped.
  - `AdminUserControllerTest`: 6 tests
  - `AdminUserServiceTest`: 9 tests
  - `AdminUserSecurityTest`: 18 tests

### 5.2 Operations Service Tests
- **Command:** `mvn test -pl services/operations-service "-Dtest=DispatchControllerTest,DispatchEventListenerTest,DispatchSecurityTest,WorkloadCounterTest,DispatchServiceTest"`
- **Result:** **BUILD SUCCESS**
- **Test Count:** 69 tests run, 0 failures, 0 errors, 0 skipped.
  - `DispatchControllerTest`: 14 tests
  - `DispatchEventListenerTest`: 10 tests
  - `DispatchSecurityTest`: 24 tests
  - `DispatchServiceTest`: 11 tests
  - `WorkloadCounterTest`: 10 tests

### 5.3 Frontend Tests & Build
- **Jest Test Suites:** `npx jest --testPathPatterns="admin-users|dispatch"`
- **Result:** **6 passed, 6 total suites (73/73 tests passing)**.
- **Production Build:** `npm run build`
- **Result:** **SUCCESS** (`dist/web` generated, 0 compilation or template errors).

---

## 6. Cross-Member Dependencies & Boundaries

1. **M1 (`parcel-service`):**
   - Authoritative parcel lifecycle (`FAILED -> CREATED` re-queue).
   - Assignment persistence via `/internal/v1/parcels/{id}/assignment`.
   - Production of `parcel.assigned.v1` and `parcel.status-changed.v1`.
2. **M9 (`courier.profile` & Platform):**
   - Courier directory lookup and profile persistence via `@Primary` `M4CourierDirectoryAdapter`.
   - Shared test infrastructure (`OperationsKafkaIntegrationTest`).
3. **M10 (`auth.security`):**
   - JWT authentication filter, JWKS, and `ROLE_ADMIN` / `ROLE_DISPATCHER` claims.

---

## 7. Acceptance Criteria Checklist (Issue #231)

- [x] Relevant M4 SRS and issue requirements tested (AU-05, DI-01, DI-02, DI-03, DI-06, DI-07).
- [x] Missing unit, security, and integration coverage added across all mutating operations.
- [x] Authorization and role boundaries verified across all 5 roles.
- [x] Validation and API error behavior verified (including 400, 404, 409).
- [x] Retry, failure, and DLT paths tested.
- [x] Backend Maven verification results recorded (33 auth tests, 69 operations tests).
- [x] Frontend tests and build results recorded (73 Jest tests, production build passing).
- [x] Real cross-service verification and test harness limitations documented.
- [x] Focused PR branch prepared for `development` referencing Issue #231 only.
