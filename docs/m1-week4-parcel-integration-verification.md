# M1 Week 4 — Issue #163 Parcel Integration Verification

## Scope

This verification covers the existing `parcel-service` parcel lifecycle integration:
customer and merchant creation, courier assignment and reassignment, status
transitions, optimistic concurrency, and post-commit Kafka events.

No other member's package, shared contract, ADR, gateway, or database was changed.

## Automated verification

Command:

```text
mvn -B verify -pl services/parcel-service -am
```

Result:

```text
common:          13 tests passed
parcel-service:  188 tests passed
Total:           201 tests passed, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
```

The integration suite uses Testcontainers with PostgreSQL and Kafka and verifies:

- Customer parcel creation, pricing response, persistence, tracking number, and
  initial `CREATED` state.
- Merchant parcel creation through the internal contract.
- Assignment, reassignment, duplicate assignment, and
  `previousCourierId`.
- Valid lifecycle transitions, illegal transitions, and failed-transition
  validation.
- Expected-current-status conflicts, including stale idempotent retries.
- Monotonic per-parcel event sequences and `parcelId` Kafka keys.
- Post-commit `parcel.created.v1`, `parcel.assigned.v1`, and
  `parcel.status-changed.v1` event payloads.
- Re-queue behavior and courier clearing.

## Cross-service verification and known gaps

- Pricing, authentication, merchant ordering, parcel persistence, and lifecycle
  execution have existing real-service evidence in
  [M6 real parcel verification](./m6-week3-real-parcel-verification.md).
- M4 dispatch, M8 courier execution, and M9 courier profile consumers were not
  modified. Their consumer-side behavior remains covered by their owning
  services and contracts.
- A complete fresh customer-login-to-gateway run was not reproduced in this
  parcel-service-only verification. The parcel-service Testcontainers suite
  verifies the authenticated customer boundary with Spring Security test
  principals and verifies internal lifecycle calls through the real HTTP
  controller.
- The parcel service does not independently validate courier eligibility; that
  responsibility remains with the operations-service owner, as required by the
  service boundary.

## Acceptance checklist

- [x] Parcel creation uses the agreed pricing and parcel APIs.
- [x] Assignment and reassignment persist correctly.
- [x] Legal status transitions succeed.
- [x] Invalid and stale transitions return conflict responses.
- [x] Required Kafka events contain the agreed envelope fields and sequence.
- [x] Failed operations do not publish successful transition events.
- [x] Integration tests pass with real PostgreSQL and Kafka containers.
- [x] Cross-service dependencies and remaining verification boundaries are documented.
