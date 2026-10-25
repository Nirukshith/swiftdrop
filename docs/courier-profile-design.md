# Courier Profile and CourierDirectory Design

**Owner:** M9  
**Service:** `operations-service` (port 8083)  
**Package:** `courier.profile`  
**Related contract:** `contracts/openapi/operations-courier-profile.yaml`

## Responsibility

The `courier.profile` package owns the operations-side courier profile and
availability state. It does not own parcel status transitions, parcel
assignment, or delivery execution. Those responsibilities remain with
`parcel-service`, M4's `dispatch` package, and M8's `courier.execution`
package respectively.

The profile contains the user identifier, display name, optional phone, zone,
availability, and vehicle value. The profile is used by M4's dispatch and
workload flows through `CourierDirectory`.

## Profile creation from `user.registered.v1`

`operations-service` consumes `user.registered.v1`. When the event role is
`COURIER`, the consumer creates or updates the corresponding courier profile
using the event's `userId`, `fullName`, and optional `phoneNumber`. Events for
other roles do not create courier profiles.

The consumer stores the event's `eventId` in the service's processed-event
record before considering the event handled. Re-delivery of the same
`eventId` is therefore a no-op: it must not create a second profile or apply a
second side effect. A later event with a different `eventId` for the same
`userId` must reconcile with the existing profile rather than create a
duplicate.

The event is expected to follow the shared `user-registered.schema.json`
contract. Profile creation is performed only after the event's database work
has a reliable post-commit publication guarantee. The choice between
`@TransactionalEventListener(phase = AFTER_COMMIT)` and a transactional
outbox is intentionally not made here; it is an open implementation question
owned by M1/the team, as recorded below.

## Availability

The authenticated courier can set their availability through
`PUT /api/v1/courier/availability`. The courier identity comes from the JWT,
never from a request field. M4's dispatcher-facing availability operation
remains in `operations-dispatch.yaml`; both views read the same profile state.

An unavailable courier must not be selected for a new assignment. Availability
does not change the parcel status or active workload count.

## Failed delivery flow

M8's courier execution flow owns the normal delivery update and proof capture.
M9's failure operation accepts one of the fixed reason codes in the OpenAPI
contract and an optional note. It verifies courier ownership, records the
failure context, and asks `parcel-service` to perform the authoritative
transition to `FAILED`.

The operations service must not write parcel status directly. Once the status
event is published, M4's workload consumer removes the active workload for the
courier. A failed parcel can later be re-queued by the dispatcher. Under
Decision 005, re-queue clears the courier assignment, returns the parcel to
`CREATED`, and increments the attempt count. The next assignment then adds
work only to the newly selected courier.

## Decision 005 event and workload rules

The profile and summary read the event-carried state according to
`docs/decisions/005-event-envelope-and-workload.md`:

- `sequence` is monotonic per parcel; consumers may ignore stale,
  out-of-order events after de-duplication by `eventId`.
- `previousCourierId` is populated on reassignment. Workload processing
  decrements the previous courier and increments the new courier.
- Assignment increments the assigned courier's active workload.
- `DELIVERED`, `FAILED`, and `CANCELLED` decrement active workload.
- Re-queue clears the courier, returns the parcel to `CREATED`, and increments
  the attempt count.

The daily summary and courier delivery list must reflect those rules rather
than counting only the most recent assignment record. In particular, a
reassigned parcel must no longer appear as active for the previous courier; a
failed parcel must no longer count as active; and a re-queued parcel must not
remain attached to the courier who recorded the failure.

## CourierDirectory for M4

M9 provides the in-process `CourierDirectory` capability to M4's dispatch
package. The agreed capabilities are:

1. List couriers for dispatch and workload views.
2. Expose each courier's current availability so assignment can reject
   unavailable couriers.

The directory is a package boundary, not a cross-service REST call: both
packages run in `operations-service` and use the operations database through
their own repositories. The directory must return profile data and
availability without exposing persistence entities or allowing dispatch code to
change parcel status.

## Open questions for M4, M8, and M10

These details are not specified by the SRS or project plan and remain
deliberately unresolved for team coordination:

- Final Java method signatures, filter/pagination rules, DTO names, and error
  semantics for `CourierDirectory`.
- The complete controlled vocabulary for failure reason codes beyond the three
  named in the plan.
- Exact request/response representation for the daily summary, including date
  timezone and whether it returns delivery IDs or expanded delivery records.
- Exact proof response shape, photo streaming/download behavior, and storage
  authorization rules.
- Whether availability is represented as `available` or a richer state in the
  final shared DTO.
- The post-commit event publication mechanism:
  `@TransactionalEventListener(phase = AFTER_COMMIT)` versus a transactional
  outbox. This is owned by M1/the team and is not selected or implemented by
  this PR.

M4, M8, and M10 should resolve the relevant questions before implementation
depends on the contract. This PR intentionally avoids adding undocumented
fields or behavior.
