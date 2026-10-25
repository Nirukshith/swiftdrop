# SwiftDrop Dispatch & Courier Workload Architecture Design

**Author:** Member M4 (Dispatcher, Assignment, Workload & User Management)  
**Target Service:** `operations-service` (Port 8083, Database: `ops_db`)  
**Package:** `com.swiftdrop.operations.dispatch`  
**Related Contract:** `contracts/openapi/operations-dispatch.yaml`

---

## 1. Overview & Responsibilities

Member M4 owns the dispatch domain within `operations-service` and admin user management within `auth-service`. The dispatch package is responsible for:
1. **Unassigned Queue & Assignment:** Maintaining real-time visibility over unassigned parcels, assigning parcels to available couriers, supporting bulk assignment, and preventing duplicate assignments (returning `409 Conflict`).
2. **Courier Workload Tracking:** Maintaining an accurate, live count of active deliveries per courier via asynchronous Kafka event consumption.
3. **Re-queue Management:** Re-queueing failed deliveries back to the dispatch queue for re-assignment.
4. **Courier Availability:** Interfacing with M9's `CourierDirectory` to verify courier availability before assigning parcels and providing dispatcher availability overrides.

---

## 2. Database Schema (`ops_db`)

### 2.1 `courier_workload` Table
The `courier_workload` table stores the cached aggregate of active deliveries for each courier. This table enables immediate response times on the Dispatcher Workload Board without expensive aggregation queries across delivery history.

```sql
CREATE TABLE IF NOT EXISTS courier_workload (
    courier_id VARCHAR(36) PRIMARY KEY,
    active_count INT NOT NULL DEFAULT 0,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    last_event_id VARCHAR(36),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_active_count_non_negative CHECK (active_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_courier_workload_active_count ON courier_workload(active_count);
```

### 2.2 `processed_event` Table (Idempotency)
To guarantee at-least-once Kafka consumption does not corrupt workload counters, every processed event is recorded in `processed_event`:

```sql
CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(36),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_event_parcel ON processed_event(parcel_id);
```

---

## 3. Workload Counter Event Rules

Workload counts are mutated strictly in response to Kafka domain events published to `parcel.assigned.v1` and `parcel.status-changed.v1` using the canonical `EventEnvelope`:

```text
EventEnvelope fields:
- eventId (UUID)
- eventType (String)
- occurredAt (Instant)
- sequence (Long)
- parcelId (UUID)
- trackingNumber (String)
- fromStatus (String)
- toStatus (String)
- courierId (UUID)
- previousCourierId (UUID)
- reason (String)
```

### Transition Matrix & Counting Logic

| Event / Action | Condition | Workload Mutation |
| :--- | :--- | :--- |
| **`parcel.assigned.v1` (Initial Assignment)** | `previousCourierId` is null | Increment `courierId`: `active_count = active_count + 1` |
| **`parcel.assigned.v1` (Reassignment)** | `previousCourierId` is present and distinct | Decrement `previousCourierId`: `active_count = active_count - 1`<br>Increment `courierId`: `active_count = active_count + 1` |
| **`parcel.status-changed.v1`** | `toStatus == 'DELIVERED'` | Decrement `courierId`: `active_count = active_count - 1` |
| **`parcel.status-changed.v1`** | `toStatus == 'FAILED'` | Decrement `courierId`: `active_count = active_count - 1` |
| **`parcel.status-changed.v1`** | `toStatus == 'CANCELLED'` | If parcel was assigned to a courier (`courierId != null`), decrement `courierId`: `active_count = active_count - 1` |
| **Re-queue Action (`POST /api/v1/dispatch/parcels/{id}/requeue`)** | Dispatched via REST | Clears courier on parcel and sets status back to `CREATED`. The courier's count was already decremented when the parcel entered `FAILED`, so re-queuing makes no additional mutation to the previous courier's count. |

*Safety Guard:* Active counts are constrained to `active_count >= 0`. Any decrement below 0 is clamped to 0 and logged as a warning.

---

## 4. Idempotency & Out-of-Order Event Handling

### 4.1 Idempotent Consumption by `eventId`
1. When an event is received from Kafka, the consumer checks `processed_event` for `eventId`.
2. If `eventId` exists, the message is an exact duplicate (e.g. from Kafka consumer group rebalance or retry); it is acknowledged and immediately skipped.
3. If not present, the workload update and `processed_event` insertion are executed within a single database transaction.

### 4.2 Out-of-Order Handling with `sequence`
Network delays or multi-partition setups can cause events to arrive out of chronological order:
* Each `EventEnvelope` carries a monotonically increasing `sequence` number emitted by `parcel-service`.
* When updating workload for a parcel, the consumer verifies that `event.sequence() > current_parcel_sequence`.
* If an event arrives with a `sequence` smaller than the highest observed sequence for that parcel (e.g., an `ASSIGNED` event arriving *after* a `DELIVERED` event), the counter mutation for that stale event is rejected to prevent counter skew.

---

## 5. Interface Contract with M9 (`CourierDirectory`)

The dispatch package in `operations-service` does not own the courier profile database tables (owned by M9 in `com.swiftdrop.operations.courier.profile`). Per architecture section 8.2, cross-package collaboration uses an in-process interface:

### 5.1 Required Interface: `CourierDirectory`
Package: `com.swiftdrop.operations.courier.profile`

```java
package com.swiftdrop.operations.courier.profile;

import java.util.List;
import java.util.Optional;

public interface CourierDirectory {

    /**
     * Find courier details and active availability.
     */
    Optional<CourierSummary> findCourierById(String courierId);

    /**
     * List couriers assigned to a specific delivery zone.
     * @param zone Zone code (e.g. "ZONE-COLOMBO-NORTH")
     * @param availableOnly If true, returns only couriers currently marked available
     */
    List<CourierSummary> listCouriersByZone(String zone, boolean availableOnly);

    /**
     * List all registered couriers in the system.
     */
    List<CourierSummary> listAllCouriers();

    /**
     * Fast check whether a courier is available for assignment.
     */
    boolean isCourierAvailable(String courierId);

    /**
     * Update courier availability (called by dispatcher override).
     */
    void updateAvailability(String courierId, boolean available);
}
```

### 5.2 Required DTO: `CourierSummary`

```java
package com.swiftdrop.operations.courier.profile;

public record CourierSummary(
    String courierId,
    String name,
    String phoneNumber,
    String zone,
    boolean available,
    String vehicleType
) {}
```

---

## 6. Integration Contract with M1 (`parcel-service`)

When a dispatcher triggers assignment:
1. M4 calls M9's `CourierDirectory.isCourierAvailable(courierId)`. If false, returns `400 Bad Request` ("Courier is currently unavailable").
2. M4 calls M1's `PUT /internal/v1/parcels/{id}/assignment` with payload `{"courierId": "..."}`.
3. If M1 returns `409 Conflict`, M4 surfaces `409 Conflict` ("Parcel has already been assigned") to the caller.
4. M1 updates parcel state and publishes `parcel.assigned.v1` to Kafka, which M4's consumer catches to update `courier_workload`.
