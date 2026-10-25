# SwiftDrop Kafka Topics & Event Contracts

This document specifies the Kafka topics, consumer groups, message contracts, retry strategies, and idempotency guarantees across SwiftDrop microservices, in alignment with Architecture Decision Record **005** and `common.EventEnvelope`.

---

## 1. Topic Registry

| Topic | Partition Key | Producer | Primary Consumers (Group ID) | Description |
| :--- | :--- | :--- | :--- | :--- |
| `parcel.created.v1` | `parcelId` | `parcel-service` | `integration-notification` (`integration-service`),<br>`operations-reports` (`operations-service`) | Emitted when a new parcel order is created and validated. Triggers initial customer confirmation email. |
| `parcel.assigned.v1` | `parcelId` | `parcel-service` | `integration-notification` (`integration-service`),<br>`operations-workload` (`operations-service`) | Emitted when a parcel is assigned or reassigned to a courier. Triggers courier assignment notification and updates courier workload. |
| `parcel.status-changed.v1` | `parcelId` | `parcel-service` | `parcel-tracking` (`parcel-service`),<br>`integration-notification` (`integration-service`),<br>`integration-webhook` (`integration-service`),<br>`operations-reports` (`operations-service`) | Emitted on every state transition (`PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`, `CANCELLED`). Drives live tracking SSE, notifications, webhooks, and daily statistics. |
| `user.registered.v1` | `userId` | `auth-service` | `integration-notification` (`integration-service`),<br>`operations-courier` (`operations-service`) | Emitted when a new user registers or is created by an admin. Triggers welcome email or courier profile provisioning. |

---

## 2. Retry and Dead Letter Topics (DLT)

To ensure resilient message processing without blocking broker partitions, consumers configure non-blocking retry topics via Spring Kafka `@RetryableTopic`:

| Base Topic | Retry Topic | Dead Letter Topic (DLT) | Target Consumer Service |
| :--- | :--- | :--- | :--- |
| `parcel.created.v1` | `parcel.created.v1.retry` | `parcel.created.v1.dlt` | `integration-service` |
| `parcel.assigned.v1` | `parcel.assigned.v1.retry` | `parcel.assigned.v1.dlt` | `integration-service`, `operations-service` |
| `parcel.status-changed.v1` | `parcel.status-changed.v1.retry` | `parcel.status-changed.v1.dlt` | `integration-service`, `operations-service` |
| `user.registered.v1` | `user.registered.v1.retry` | `user.registered.v1.dlt` | `integration-service`, `operations-service` |

### Retry Policy
- **Maximum Attempts**: 5 attempts.
- **Backoff Strategy**: Exponential backoff with initial interval `1000ms`, multiplier `2.0`, max delay `16000ms` (1s, 2s, 4s, 8s, 16s).
- **Dead Letter Behavior**: Messages failing after 5 attempts are committed and forwarded to the corresponding `.dlt` topic with failure headers (`x-exception-message`, `x-exception-stacktrace`, `x-original-topic`) for operational alerting and manual inspection.

---

## 3. Shared Event Envelope Contract

All Kafka events share the standard JSON envelope defined in `com.swiftdrop.common.EventEnvelope` and `contracts/events/event-envelope.schema.json`:

```json
{
  "eventId": "e9b207e4-2395-46f1-a18a-986cf194ad53",
  "eventType": "PARCEL_STATUS_CHANGED",
  "occurredAt": "2026-10-24T18:45:00Z",
  "sequence": 4,
  "parcelId": "pcl-109283",
  "trackingNumber": "SD-20261024-891023",
  "fromStatus": "IN_TRANSIT",
  "toStatus": "OUT_FOR_DELIVERY",
  "courierId": "usr-courier-42",
  "previousCourierId": null,
  "merchantId": null,
  "customerId": "usr-cust-901",
  "reason": null
}
```

### Field Definitions (ADR 005 Alignment)
- **`eventId`** (*string, UUID, required*): Globally unique event identifier, used as the primary deduplication key.
- **`eventType`** (*string, required*): Event name (`PARCEL_CREATED`, `PARCEL_ASSIGNED`, `PARCEL_STATUS_CHANGED`, `USER_REGISTERED`).
- **`occurredAt`** (*string, ISO-8601 UTC timestamp, required*): Time when the state change occurred in the producer database.
- **`sequence`** (*integer, int64, required*): Monotonically increasing sequence number per parcel (1 for creation, 2 for assignment, etc.) ensuring downstream consumers detect and ignore out-of-order deliveries.
- **`parcelId`** (*string, required*): Internal database ID of the parcel.
- **`trackingNumber`** (*string, required*): Public alphanumeric tracking number (`SD-YYYYMMDD-XXXXXX`).
- **`fromStatus`** (*string, required*): State before this transition (e.g. `IN_TRANSIT`).
- **`toStatus`** (*string, required*): State after this transition (e.g. `OUT_FOR_DELIVERY`).
- **`courierId`** (*string, nullable*): ID of currently assigned courier.
- **`previousCourierId`** (*string, nullable*): Populated during reassignment to allow `operations-service` to decrement workload from the previous courier and increment the new courier.
- **`merchantId`** (*string, nullable*): Associated merchant ID if parcel originates from merchant order API.
- **`customerId`** (*string, nullable*): Owner customer user ID.
- **`reason`** (*string, nullable*): Failure explanation or cancellation note.

---

## 4. Consumer Requirements & Idempotency Guarantees

1. **Partitioning**: Messages on parcel topics use `parcelId` as partition key, guaranteeing in-order delivery of all events for a single parcel within a partition.
2. **Post-Commit Publishing**: Producers publish events strictly after database transaction commit (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`) to eliminate phantom events.
3. **Consumer Idempotency**: Each consumer maintains an internal `processed_events` or `notification_log` table storing `eventId`. If a message with an already-recorded `eventId` arrives, processing is skipped immediately and the offset is committed.
4. **Header Propagation**: The tracing header `X-Correlation-Id` is propagated via Kafka Record Headers across producers and consumers to correlate logs.
