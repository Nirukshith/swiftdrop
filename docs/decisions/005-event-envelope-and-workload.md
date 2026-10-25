# 005 - Event Envelope and Workload

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
Asynchronous event communication between services requires deterministic ordering, tracking of courier handoffs during reassignment, and accurate real-time workload calculations. Without explicit envelope fields and reliable transaction-bound publishing, race conditions and missed decrements could corrupt operational courier workloads.

## Decision
- **Event Envelope**: The shared `EventEnvelope` includes `sequence` (monotonic per parcel) and `previousCourierId` (populated on courier reassignment).
- **Workload Management**: Courier workload increments upon assignment and decrements upon reaching `DELIVERED`, `FAILED`, `CANCELLED`, or upon reassignment (where `previousCourierId` is decremented and the new `courierId` is incremented).
- **Re-queue**: Re-queuing a failed parcel clears the courier (resets courier to unassigned) and returns the status to `CREATED` with an incremented attempt count.
- **Reliable Publishing**: Event publishing must occur strictly after transaction commit (using `@TransactionalEventListener(phase = AFTER_COMMIT)` or a transactional outbox) to prevent phantom events.

## Consequences
- **M8**: Codifies `sequence` and `previousCourierId` in `com.swiftdrop.common.EventEnvelope`.
- **M1**: Implements sequence numbering and sets `previousCourierId` on reassignment in `parcel-service`; implements post-commit event publishing (or outbox); ensures re-queue clears the courier.
- **M4**: Implements workload consumer logic in `operations-service` to adjust `CourierWorkload` accordingly (decrement on delivered, failed, cancelled, and handle `previousCourierId` on reassignment).
- **M2, M3, M5, M7**: Consumers safely de-duplicate on `eventId` and can use `sequence` to ignore out-of-order stale events.
- **M9**: Courier summary counts and delivery lists accurately reflect parcels removed due to re-queuing or reassignment.
- **M6, M10**: Adhere to the shared event publishing standards.

## Open items
- Determine whether `parcel-service` uses `@TransactionalEventListener` or a dedicated outbox table for Kafka events.
