# SwiftDrop Parcel Lifecycle State Machine Design

**Owner:** Member M1 (Customer Parcel Creation, Core Domain & Internal Parcel API)  
**Service:** `parcel-service` (Port 8082, Database: `parcel_db`)  
**Package:** `com.swiftdrop.parcel`  
**OpenAPI Contract:** [`contracts/openapi/parcel.yaml`](file:///c:/Users/sshar/OneDrive/Desktop/swiftdrop/contracts/openapi/parcel.yaml)  
**Related Decisions:** [ADR 002 (Address Structure)](file:///c:/Users/sshar/OneDrive/Desktop/swiftdrop/docs/decisions/002-address-structure.md), [ADR 005 (Event Envelope & Workload)](file:///c:/Users/sshar/OneDrive/Desktop/swiftdrop/docs/decisions/005-event-envelope-and-workload.md)

---

## 1. Lifecycle Overview & States

`parcel-service` is the **single authority of record** for parcel state within SwiftDrop. All other microservices (`operations-service`, `integration-service`) request transitions through internal REST APIs.

### Canonical States (`ParcelStatus`)

```text
               ┌─────────────┐
               │   CREATED   │ ◄──────────────────────┐
               └──────┬──────┘                        │
                      │                               │ Re-queue (Dispatcher)
         ┌────────────┼────────────┐                  │ (clears courier,
         │            │            │                  │  attemptCount + 1)
         ▼            ▼            │                  │
   ┌───────────┐ ┌─────────┐       │                  │
   │ CANCELLED │ │PICKED_UP│       │                  │
   └───────────┘ └────┬────┘       │                  │
   (Customer/         │            │                  │
    Merchant)         ▼            │                  │
                 ┌──────────┐      │                  │
                 │IN_TRANSIT│      │                  │
                 └────┬─────┘      │                  │
                      │            │                  │
                      ▼            │                  │
              ┌────────────────┐   │                  │
              │OUT_FOR_DELIVERY│   │                  │
              └───────┬────────┘   │                  │
                      │            │                  │
          ┌───────────┴───────────┐│                  │
          ▼                       ▼▼                  │
   ┌─────────────┐         ┌─────────────┐            │
   │  DELIVERED  │         │   FAILED    │ ───────────┘
   └─────────────┘         └─────────────┘
  (Terminal Success)      (Terminal Attempt)
```

1. **`CREATED`**: Delivery order created and validated. Courier assignment occurs in this state without altering the status.
2. **`PICKED_UP`**: Assigned courier has physically collected the parcel from the sender/merchant and verified the package.
3. **`IN_TRANSIT`**: Parcel is moving between sorting hubs or transported towards the destination zone.
4. **`OUT_FOR_DELIVERY`**: Parcel is loaded onto the courier's delivery vehicle for final-mile handover.
5. **`DELIVERED`**: Parcel handed over to recipient. **Terminal success state**.
6. **`FAILED`**: Delivery attempt failed (e.g. recipient absent, incorrect address). **Terminal attempt state**.
7. **`CANCELLED`**: Cancelled by customer or merchant prior to pickup. **Terminal cancellation state**.

---

## 2. State Transition Matrix & Authority

| From State | Allowed To State | Triggered By | API / Operation | Preconditions & Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| *(None)* | `CREATED` | Customer / Merchant | `POST /api/v1/parcels`<br>`POST /internal/v1/parcels` | Valid structured addresses per ADR 002; valid weight & size; calculated fee. Sequence initialized to 1. |
| `CREATED` | `CREATED` *(Assignment)* | Dispatcher (M4) | `PUT /internal/v1/parcels/{id}/assignment` | Parcel must be in `CREATED`. Sets `courierId`. Sequence incremented. Publishes `parcel.assigned.v1`. |
| `CREATED` | `PICKED_UP` | Assigned Courier (M8) | `PATCH /internal/v1/parcels/{id}/status` | Must be assigned to calling courier (`courierId != null`). Verification barcode confirmed. |
| `PICKED_UP` | `IN_TRANSIT` | Courier / Hub | `PATCH /internal/v1/parcels/{id}/status` | Courier has checked in parcel at distribution hub or transit vehicle. |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY` | Assigned Courier (M8) | `PATCH /internal/v1/parcels/{id}/status` | Parcel scanned onto final delivery route vehicle. |
| `OUT_FOR_DELIVERY` | `DELIVERED` | Assigned Courier (M8) | `PATCH /internal/v1/parcels/{id}/status` | **Terminal success**. Requires delivery proof: recipient name AND/OR photo proof upload path. |
| `OUT_FOR_DELIVERY` | `FAILED` | Assigned Courier (M8/M9) | `PATCH /internal/v1/parcels/{id}/status` | **Terminal attempt**. Requires valid failure reason (`RECIPIENT_UNAVAILABLE`, `INCORRECT_ADDRESS`, `CUSTOMER_REJECTED`, `ACCESS_RESTRICTED`). |
| `CREATED` | `CANCELLED` | Customer (M1) / Merchant (M6) | `POST /api/v1/parcels/{id}/cancel` | Allowed **only before pickup** (`status == CREATED`). If already assigned, courier workload is decremented. |
| `FAILED` | `CREATED` *(Re-queue)* | Dispatcher (M4) | `PATCH /internal/v1/parcels/{id}/status` | Allowed **only from FAILED**. Clears courier (`courierId = null`) and increments `attemptCount`. |

---

## 3. Core Business & Invariant Rules

### 3.1 Cancellation Invariants
- Cancellation is permitted **only** when the parcel is in `CREATED` status.
- Once a parcel transitions to `PICKED_UP`, cancellation is permanently forbidden. Requests return `409 Conflict` (`ILLEGAL_STATUS_TRANSITION`).
- If the parcel had already been allocated to a courier prior to cancellation, the emitted `parcel.status-changed.v1` event carries the assigned `courierId`, instructing `operations-service` to decrement that courier's active workload.

### 3.2 Failure & Re-queue Rules (ADR 005)
- When a delivery attempt fails (`OUT_FOR_DELIVERY` &rarr; `FAILED`), a failure reason is mandatory.
- The `parcel.status-changed.v1` event with `toStatus: FAILED` causes `operations-service` to immediately decrement the courier's `active_count`.
- When a dispatcher re-queues a failed parcel (`FAILED` &rarr; `CREATED`):
  1. The parcel status returns to `CREATED`.
  2. **The courier is cleared (`courierId = null`)**, placing the parcel back into the unassigned dispatch queue.
  3. `attemptCount` is incremented by 1.
  4. The previous courier's workload counter is **not** decremented on re-queue, because it was already decremented upon reaching `FAILED`.

### 3.3 Courier Assignment & Reassignment (ADR 005)
- Assignment is performed via `PUT /internal/v1/parcels/{id}/assignment`.
- If a parcel is already assigned to a courier, subsequent assignment without explicit reassignment intent returns `409 Conflict` (`PARCEL_ALREADY_ASSIGNED`).
- When a reassignment occurs:
  - The emitted event is `parcel.assigned.v1`.
  - It carries `courierId` (the new courier) AND `previousCourierId` (the prior courier).
  - This allows `operations-service` to atomically decrement `previousCourierId` and increment `courierId`.

---

## 4. Concurrency Control & Safe Retries

Mobile couriers and external callers operate in intermittent connectivity environments. To prevent duplicate status triggers, race conditions, or stale updates:

### 4.1 Expected Status Checking
The status update contract (`PATCH /internal/v1/parcels/{id}/status`) requires:
```json
{
  "status": "OUT_FOR_DELIVERY",
  "expectedCurrentStatus": "IN_TRANSIT"
}
```
1. **Match**: If `parcel.status == expectedCurrentStatus`, transition executes and publishes event.
2. **Idempotent Retry**: If network timeout caused client retry and `parcel.status == status` (already reached target status), the service detects idempotent repetition, returns `200 OK` with existing details, and suppresses duplicate Kafka events.
3. **Collision / Stale Jump**: If `parcel.status != expectedCurrentStatus` and `parcel.status != status`, returns `409 Conflict` (`STATUS_CONFLICT`).

### 4.2 Optimistic Database Locking
The `parcel` table contains a `@Version Long version` column:
- Concurrent updates to the same parcel result in Spring Data JPA throwing `OptimisticLockingFailureException`.
- Caught by `GlobalExceptionHandler` and translated to standard `ErrorResponse` with HTTP `409 Conflict`.

---

## 5. Kafka Event Publishing Rules (ADR 005)

### 5.1 Shared Envelope (`common.EventEnvelope`)
All events conform to `com.swiftdrop.common.EventEnvelope`:
- `eventId`: UUIDv4 primary deduplication key.
- `eventType`: `PARCEL_CREATED`, `PARCEL_ASSIGNED`, `PARCEL_STATUS_CHANGED`.
- `occurredAt`: UTC Instant of state change.
- `sequence`: Monotonically increasing number per parcel (1 for creation, 2 for assignment, etc.).
- `parcelId`, `trackingNumber`, `fromStatus`, `toStatus`, `courierId`, `previousCourierId`, `merchantId`, `customerId`, `reason`.

### 5.2 Transactional Post-Commit Publishing
Events are published strictly after database transaction commit:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleParcelEvent(ParcelDomainEvent event) {
    kafkaTemplate.send(event.getTopic(), event.getParcelId(), event.getEnvelope());
}
```
This guarantees that uncommitted or rolled-back state changes never leak phantom Kafka events downstream.
