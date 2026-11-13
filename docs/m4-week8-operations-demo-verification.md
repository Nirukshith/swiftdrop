# SwiftDrop — Week 8 M4 Final Operations & Management Demo Verification

**Assigned Member:** M4 — @Agnes-Ostina  
**Issue:** [#251 — Finalize Admin and Dispatcher Workflows, Operational Integration, and Management Demo](https://github.com/Nirukshith/swiftdrop/issues/251)  
**Base Branch:** `development`  
**Feature Branch:** `chore/m4-week8-final-ops-demo`  

---

## 1. Executive Summary

This document details the final operational integration, release-readiness verification, and repeatable management demonstration script for Member M4 during Week 8 of the SwiftDrop project.

Building upon the completed and hardened implementations from Weeks 3–7 (PR #198, PR #259, and PR #265), M4 has finalized all assigned capabilities:
- **AU-05:** Administrative staff management (provisioning `COURIER` and `DISPATCHER` accounts, status toggle, BCrypt hashing, and role filtering).
- **DI-01:** Dispatch unassigned parcel queue with date and delivery zone filtering.
- **DI-02:** Single parcel assignment and pre-pickup courier reassignment with `reassign=true` validation.
- **DI-03:** Real-time courier workload monitoring integrated with M9's `CourierDirectory` adapter.
- **DI-06:** Bulk parcel assignment with batch reporting and courier availability toggling (`isAvailable: true/false`).
- **DI-07:** FAILED parcel recovery and re-queuing (`FAILED -> CREATED`, attempt count increment, courier clearing, workload counter invariance).
- **ADR 005:** Monotonic event envelope sequence tracking, idempotency deduplication, and dead-letter queue resilience.

All automated test suites in `auth-service`, `operations-service`, and `frontend/projects/web` pass with zero failures and zero skipped tests.

---

## 2. Requirements & Contract Traceability Matrix

| Requirement | Module / Service | OpenAPI / Event Contract | HTTP Method & Path | Implemented Scope & Access Control | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AU-05** | `auth-service` | `contracts/openapi/auth-admin.yaml` | `GET /api/v1/admin/users` | List users with pagination, role filter (`COURIER`, `DISPATCHER`, `ADMIN`, `CUSTOMER`, `MERCHANT`), and status filter. Restricted strictly to `ROLE_ADMIN`. | **VERIFIED** |
| **AU-05** | `auth-service` | `contracts/openapi/auth-admin.yaml`<br>`contracts/events/user-registered.schema.json` | `POST /api/v1/admin/users` | Provision staff users (`COURIER` or `DISPATCHER`), BCrypt hashing, default disabled, triggers `user.registered.v1`. Restricted to `ROLE_ADMIN`. | **VERIFIED** |
| **AU-05** | `auth-service` | `contracts/openapi/auth-admin.yaml` | `PATCH /api/v1/admin/users/{id}` | Toggle account status (`enabled: true/false`). Restricted to `ROLE_ADMIN`. | **VERIFIED** |
| **DI-01** | `operations-service` | `contracts/openapi/operations-dispatch.yaml` | `GET /api/v1/dispatch/parcels` | List unassigned parcels awaiting assignment, filter by `zone` and `date`. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-02** | `operations-service` | `contracts/openapi/operations-dispatch.yaml`<br>`contracts/openapi/parcel.yaml` | `POST /api/v1/dispatch/assignments`<br>`PUT /api/v1/dispatch/parcels/{id}/assignment` | Single courier assignment and pre-pickup reassignment (`reassign=true`). Calls internal parcel client. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-03** | `operations-service` | `contracts/openapi/operations-dispatch.yaml` | `GET /api/v1/dispatch/couriers` | View active delivery counts per courier, aggregated live from Kafka consumer. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-06** | `operations-service` | `contracts/openapi/operations-dispatch.yaml` | `PATCH /api/v1/dispatch/couriers/{id}/availability` | Toggle courier availability (`isAvailable: true/false`). Restricts assignment when unavailable. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-06** | `operations-service` | `contracts/openapi/operations-dispatch.yaml` | `POST /api/v1/dispatch/assignments` (batch) | Bulk assignment of parcels to selected courier. Detects batch duplicates, validates state. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-07** | `operations-service` | `contracts/openapi/operations-dispatch.yaml`<br>`contracts/events/parcel-status-changed.schema.json` | `GET /api/v1/dispatch/parcels/failed` | List failed delivery parcels eligible for re-queueing. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **DI-07** | `operations-service` | `contracts/openapi/operations-dispatch.yaml` | `POST /api/v1/dispatch/parcels/{id}/requeue` | Re-queue failed parcel to `CREATED`, increment attempt counter, clear assigned courier. Restricted to `ROLE_DISPATCHER`. | **VERIFIED** |
| **ADR 001** | `common`, `auth`, `operations` | `docs/decisions/001-role-model.md` | All endpoints | Enforces strict role separation: `ROLE_ADMIN` vs `ROLE_DISPATCHER`. Forbidden roles receive `403 Forbidden`. | **VERIFIED** |
| **ADR 005** | `operations-service` | `docs/decisions/005-event-envelope-and-workload.md`<br>`contracts/events/parcel-assigned.schema.json`<br>`contracts/events/parcel-status-changed.schema.json` | Kafka topics: `parcel.assigned.v1`, `parcel.status-changed.v1` | Monotonic per-parcel `sequence` check, `previousCourierId` handoff decrement, terminal status decrements (`DELIVERED`, `FAILED`, `CANCELLED`), re-queue counter invariance. | **VERIFIED** |

---

## 3. Environment & Configuration Reference

### 3.1 Environment Variables (`deploy/.env.example`)
The deployment configuration specifies ports and credentials across containers:

```ini
# PostgreSQL Configuration
POSTGRES_USER=swiftdrop
POSTGRES_PASSWORD=swiftdrop_password
POSTGRES_PORT=5434

# Frontend Port
FRONTEND_PORT=4200

# Gateway Routing
GATEWAY_PORT=8080

# Messaging
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Service Ports (Direct internal access)
AUTH_SERVICE_PORT=8081
PARCEL_SERVICE_PORT=8082
OPERATIONS_SERVICE_PORT=8083
INTEGRATION_SERVICE_PORT=8084
```

### 3.2 Gateway Route Mapping (`deploy/nginx/nginx.conf`)
The Nginx reverse proxy routes client traffic while blocking internal service APIs:

| External Path Pattern | Gateway Target | Permitted Caller Roles | Description |
| :--- | :--- | :--- | :--- |
| `/api/v1/admin/users/**` | `http://auth-service:8081` | `ROLE_ADMIN` | Staff provisioning & user account lifecycle |
| `/api/v1/dispatch/**` | `http://operations-service:8083` | `ROLE_DISPATCHER` | Parcel queue, courier assignment, workload & recovery |
| `/api/v1/courier/**` | `http://operations-service:8083` | `ROLE_COURIER` | Courier execution & profile |
| `/internal/**` | **BLOCKED (404)** | None (Service-to-service internal only) | Blocked from external exposure |
| `/*` | `http://frontend:80` | Public / Role-guarded | Angular Web Portal (`/admin/users`, `/dispatch`) |

---

## 4. Step-by-Step Environment Setup & Deployment

### 4.1 Running with Docker Compose (`full` Profile)

1. **Verify configuration and environment:**
   ```powershell
   Copy-Item deploy/.env.example deploy/.env -Force
   docker compose -f deploy/docker-compose.yml --profile full config --quiet
   ```

2. **Start the complete platform stack:**
   ```powershell
   docker compose -f deploy/docker-compose.yml --profile full up -d --build
   ```

3. **Verify container health:**
   ```powershell
   docker compose -f deploy/docker-compose.yml --profile full ps
   ```
   All services (`postgres`, `kafka`, `kafka-init`, `mailpit`, `auth-service`, `parcel-service`, `operations-service`, `integration-service`, `frontend`, `gateway`) should report `healthy` or `running`.

4. **Shutdown and resource cleanup:**
   ```powershell
   docker compose -f deploy/docker-compose.yml --profile full down
   ```

### 4.2 Running Services Locally for Development

1. **Start Infrastructure Services:**
   ```powershell
   docker compose -f deploy/docker-compose.yml --profile infra up -d
   ```

2. **Start Auth Service:**
   ```powershell
   mvn spring-boot:run -pl services/auth-service
   ```

3. **Start Operations Service:**
   ```powershell
   mvn spring-boot:run -pl services/operations-service
   ```

4. **Start Frontend Portal:**
   ```powershell
   cd frontend
   npm run start
   ```
   Navigate to `http://localhost:4200`.

---

## 5. Repeatable Management Demonstration Script

This end-to-end operational script validates all M4 capabilities in a live or simulated environment.

### 5.1 Test Accounts & Roles

| Role | Username / Email | Password | Allowed Access | Forbidden Access |
| :--- | :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@swiftdrop.lk` | `AdminPass123!` | `/admin/users`, `/admin/audit`, `/admin/reports` | `/dispatch/**` |
| **DISPATCHER** | `dispatcher@swiftdrop.lk` | `DispatcherPass123!` | `/dispatch`, `/api/v1/dispatch/**` | `/admin/users` |
| **COURIER** | `courier1@swiftdrop.lk` | `CourierPass123!` | `/courier`, mobile stops | `/admin/users`, `/dispatch` |

---

### 5.2 Scenario A: Admin Staff Provisioning & Lifecycle (AU-05)

**Goal:** Prove an administrator can list, filter, provision new courier/dispatcher staff, and manage account activation status.

1. **Authentication:**
   - Log in to the web portal as `admin@swiftdrop.lk`.
   - Confirm redirect to `/admin`.
2. **Access User Management:**
   - Navigate to `/admin/users`.
   - Verify table displays existing accounts with columns: `Name`, `Email`, `Role`, `Status`, `Created At`, `Actions`.
3. **Filter System Users:**
   - Set Role dropdown filter to `COURIER`.
   - Verify only courier accounts are displayed.
   - Set Status dropdown filter to `DISABLED`.
   - Verify only disabled accounts are displayed. Reset filters to `ALL`.
4. **Provision Staff Account:**
   - Click **"+ Add Staff Member"** button to open the modal.
   - Enter Full Name: `Kasun Bandara`.
   - Enter Email: `kasun.courier@swiftdrop.lk`.
   - Select Role: `COURIER` (Observe that only `COURIER` and `DISPATCHER` are selectable options per AU-05).
   - Enter Temporary Password: `TempPassword123!`.
   - Click **"Create Staff Account"**.
   - **Verification:** Modal closes, success toast appears, `POST /api/v1/admin/users` returns `201 Created`, user appears in table with `Status = DISABLED`, and `user.registered.v1` event is published to Kafka.
5. **Account Activation Toggle:**
   - Locate `kasun.courier@swiftdrop.lk` in the table.
   - Click the toggle switch in the `Actions` column to enable the account.
   - **Verification:** `PATCH /api/v1/admin/users/{id}` returns `200 OK`. Status badge updates immediately from `DISABLED` to `ENABLED`.
6. **Authorization Negative Test (ADR 001):**
   - Attempt to call `GET /api/v1/admin/users` with a `DISPATCHER` or `COURIER` token.
   - **Verification:** Response is `403 Forbidden`.

---

### 5.3 Scenario B: Dispatcher Queue & Zone Filtering (DI-01)

**Goal:** Prove a dispatcher can view parcels waiting for courier assignment, filtered by delivery zone and assignment date.

1. **Authentication:**
   - Log in as `dispatcher@swiftdrop.lk`.
   - Confirm dispatcher navigation includes **"Dispatch Board"** (`/dispatch`).
2. **Queue Inspection:**
   - Open `/dispatch`.
   - Verify the "Unassigned Parcels" queue renders with details: Tracking Number, Destination Address, Zone, Weight, Status (`CREATED`), and Creation Timestamp.
3. **Zone Filtering:**
   - Select Zone `ZONE-COLOMBO-NORTH` from the filter dropdown.
   - Verify that only parcels belonging to `ZONE-COLOMBO-NORTH` are visible.
   - Select Zone `ZONE-COLOMBO-SOUTH` to view other regional queues.
4. **Date Filtering:**
   - Select today's date in the date picker.
   - Verify the list reflects parcels submitted for delivery today.

---

### 5.4 Scenario C: Courier Workload Monitoring & Assignment (DI-02, DI-03, DI-06)

**Goal:** Prove a dispatcher can monitor live courier delivery loads and execute single or bulk assignments.

1. **Workload Board Inspection:**
   - Locate the "Courier Workload" pane on the right-hand side of `/dispatch`.
   - View active couriers (e.g., `usr-courier-01`, `usr-courier-02`).
   - Verify each card shows: Courier Name, Current Workload (`Active Deliveries: N`), Max Capacity, and Availability Badge.
2. **Single Assignment Execution:**
   - On unassigned parcel `PCL-8401` in `ZONE-COLOMBO-NORTH`, click **"Assign"**.
   - Select Courier `usr-courier-01` from the active courier selector.
   - Click **"Confirm Assignment"**.
   - **Verification:**
     - `POST /api/v1/dispatch/assignments` returns `200 OK`.
     - Parcel `PCL-8401` disappears from the unassigned queue.
     - Courier `usr-courier-01` active workload increments by 1.
3. **Bulk Assignment Execution (DI-06):**
   - Check the selection boxes on 3 unassigned parcels (`PCL-8402`, `PCL-8403`, `PCL-8404`).
   - Observe the top action bar: "3 parcels selected". Click **"Bulk Assign"**.
   - Choose Courier `usr-courier-02` and confirm.
   - **Verification:**
     - Batch response returns `totalCount: 3, successCount: 3, failureCount: 0`.
     - All 3 parcels disappear from the queue.
     - Courier `usr-courier-02` active workload increments by 3.

---

### 5.5 Scenario D: Pre-Pickup Reassignment & Workload Adjustment (DI-02, ADR 005)

**Goal:** Prove pre-pickup reassignment moves the delivery to a new courier and accurately decrements the previous courier's workload counter.

1. **Trigger Reassignment:**
   - Locate parcel `PCL-8401` (currently assigned to `usr-courier-01`, status `CREATED`).
   - Click **"Reassign Courier"**.
   - Select Courier `usr-courier-02`.
   - Submit the reassignment (`reassign=true`).
2. **Verification & Workload Verification:**
   - `PUT /api/v1/dispatch/parcels/{id}/assignment` returns `200 OK`.
   - `parcel.assigned.v1` is published with `previousCourierId = "usr-courier-01"` and `courierId = "usr-courier-02"`.
   - Courier `usr-courier-01` workload decrements by 1.
   - Courier `usr-courier-02` workload increments by 1.
   - Workload conservation holds: total active parcels across couriers remains constant.

---

### 5.6 Scenario E: Courier Availability Safeguard (DI-06)

**Goal:** Prove dispatchers can toggle courier availability and the system rejects assignment to unavailable couriers.

1. **Toggle Availability:**
   - On Courier `usr-courier-02`'s card, toggle the switch from `Available` to `Unavailable`.
   - `PATCH /api/v1/dispatch/couriers/usr-courier-02/availability` returns `200 OK` with `isAvailable: false`.
   - The courier badge changes to `UNAVAILABLE` (red/gray).
2. **Assignment Attempt:**
   - Select an unassigned parcel `PCL-8405` and attempt to assign to `usr-courier-02`.
   - **Verification:**
     - Assignment is rejected with HTTP `400 Bad Request`.
     - UI displays validation error toast: *"Selected courier is currently unavailable for dispatch"*.
     - Parcel remains safely in the unassigned queue.
3. **Restoration:**
   - Toggle Courier `usr-courier-02` back to `Available`.
   - Repeat assignment; operation succeeds.

---

### 5.7 Scenario F: Failed Delivery Recovery & Re-queuing (DI-07, ADR 005)

**Goal:** Prove a failed delivery can be recovered and re-queued into the unassigned queue without corrupting workload counters.

1. **Review Failed Deliveries:**
   - In `/dispatch`, click on the **"Failed Deliveries"** tab (`/dispatch?tab=failed`).
   - View failed parcels with: Tracking Number, Original Courier, Failure Reason (e.g., *"Customer unavailable at delivery address"*), Attempt Count (`1`), and Failed Timestamp.
2. **Re-queue Execution:**
   - On parcel `PCL-7701`, click **"Re-queue Parcel"**.
   - A dialog opens prompting for redelivery instructions or dispatch notes.
   - Enter: *"Customer contacted; requested re-attempt between 2 PM - 5 PM"*.
   - Click **"Confirm Re-queue"**.
3. **Verification & Invariance Checks:**
   - `POST /api/v1/dispatch/parcels/{id}/requeue` returns `200 OK`.
   - Status updates: `FAILED -> CREATED`.
   - Attempt count increments: `1 -> 2`.
   - Assigned courier is cleared (`courierId = null`).
   - Parcel leaves "Failed Deliveries" and reappears at the top of the "Unassigned Parcels" queue.
   - **Workload Counter Invariance:** The courier's workload counter does NOT decrement again (because the terminal `FAILED` status already decremented it upon delivery failure). The courier workload remains accurate.

---

## 6. Cross-Service Integration & Test Harness Analysis

### 6.1 Inter-Service Interaction Summary
- **Auth Service & Operations Service:** Share JWT validation mechanism and role definitions (`ROLE_ADMIN`, `ROLE_DISPATCHER`, `ROLE_COURIER`) codified in `common`.
- **Operations Service & Parcel Service:**
  - Operations service calls parcel service internal REST APIs (`/internal/v1/parcels/**`) for assignment and re-queueing.
  - Parcel service publishes domain events (`parcel.assigned.v1`, `parcel.status-changed.v1`) consumed by operations service Kafka listener to maintain `courier_workload` tables.
- **Operations Service & Integration Service:** Integration service consumes `parcel.status-changed.v1` and `user.registered.v1` for notification alerts.

### 6.2 Test Harness Design & Limitations
- **Integration Test Class:** `DispatchIntegrationTest.java` spins up PostgreSQL and Kafka Testcontainers.
- **Harness Scope:** It verifies Spring Context, Hibernate JPA mappings, database transactions, idempotency tables (`processed_events`, `parcel_sequence`), and dead-letter queue behavior by directly invoking `dispatchEventListener.onEvent(payload)`.
- **Platform Verification:** End-to-end broker network socket delivery (from `parcel-service` producer partition over the Kafka protocol to `operations-service` consumer group) is independently verified in the full-stack Docker Compose environment (`deploy/docker-compose.yml`), where Nginx, parcel-service, operations-service, and Kafka communicate across real virtual container networks.

---

## 7. Automated Test Verification Results

### 7.1 Auth Service Tests
- **Command:** `mvn test -pl services/auth-service "-Dtest=AdminUserControllerTest,AdminUserServiceTest,AdminUserSecurityTest"`
- **Result:** **BUILD SUCCESS**
- **Summary:** 33 tests run, 0 failures, 0 errors, 0 skipped.
  - `AdminUserControllerTest`: 6 tests passing (Endpoints, validation, duplicate email handling)
  - `AdminUserServiceTest`: 9 tests passing (BCrypt hashing, role filters, event publishing)
  - `AdminUserSecurityTest`: 18 tests passing (Role-based access enforcement per ADR 001)

### 7.2 Operations Service Tests
- **Command:** `mvn test -pl services/operations-service "-Dtest=DispatchControllerTest,DispatchEventListenerTest,DispatchSecurityTest,WorkloadCounterTest,DispatchServiceTest"`
- **Result:** **BUILD SUCCESS**
- **Summary:** 69 tests run, 0 failures, 0 errors, 0 skipped.
  - `DispatchControllerTest`: 14 tests passing (Queue, single/bulk assign, reassignment, requeue, availability)
  - `DispatchEventListenerTest`: 10 tests passing (Kafka event processing, idempotency, sequence checks, DLT resilience)
  - `DispatchSecurityTest`: 24 tests passing (Strict role separation for DISPATCHER vs others)
  - `DispatchServiceTest`: 11 tests passing (Zone filters, availability validation, batch duplicate detection)
  - `WorkloadCounterTest`: 10 tests passing (Pure-Java domain counter, re-queue invariance, zero clamping)

### 7.3 Frontend Web Portal Tests
- **Command:** `npx jest --testPathPatterns="admin-users|dispatch"`
- **Result:** **PASS** (6 test suites, 73 tests passing).
  - `admin-users.component.spec.ts` & `admin-users.service.spec.ts`: Table rendering, staff creation modal, toggle status, error recovery.
  - `dispatch.component.spec.ts` & `dispatch.service.spec.ts`: Queue display, single/bulk assignment, failed re-queue, availability toggle, polling lifecycle.

### 7.4 Frontend Production Build
- **Command:** `npm run build`
- **Result:** **SUCCESS** (`dist/web` generated, 0 compilation or template errors).

---

## 8. Cross-Member Collaboration & Sign-off

- **Member M1 (Parcel Service):** Verified `parcel-service` internal assignment and re-queue endpoints match contracts. Sequence numbering in `EventEnvelope` and `previousCourierId` propagation confirmed.
- **Member M9 (Platform & Gateway):** Verified `deploy/nginx/nginx.conf` correctly proxies `/api/v1/admin/users/**` to `auth-service:8081` and `/api/v1/dispatch/**` to `operations-service:8083`. Compose `full` profile configuration validated.
- **Member M10 (Auth & Security):** Verified `ROLE_ADMIN` and `ROLE_DISPATCHER` JWT generation and claims alignment with ADR 001.

---

## 9. Final Acceptance Checklist (Issue #251)

- [x] AU-05 Admin staff management (list, filter, create courier/dispatcher, enable/disable) fully operational.
- [x] DI-01 Dispatch unassigned parcel queue with zone and date filtering fully operational.
- [x] DI-02 Courier assignment and pre-pickup reassignment fully operational.
- [x] DI-03 Courier workload monitoring with real-time active delivery counts fully operational.
- [x] DI-06 Bulk parcel assignment and courier availability toggling fully operational.
- [x] DI-07 FAILED parcel recovery and re-queuing with attempt increment fully operational.
- [x] ADR 001 strict role separation (`ROLE_ADMIN` vs `ROLE_DISPATCHER`) verified across all endpoints.
- [x] ADR 005 event envelope sequence tracking, handoff decrement, and re-queue invariance verified.
- [x] Repeatable management demo script authored with clear steps, credentials, and expected results.
- [x] Docker Compose environment setup and gateway routing documented.
- [x] Documented test harness constraints and platform integration verification.
- [x] All backend unit/integration tests and frontend tests pass cleanly (175+ automated tests passing).
- [x] Zero code regressions and zero breaking changes to existing contracts.

