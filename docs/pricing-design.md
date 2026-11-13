# Pricing design

## Contract boundary

`operations-service` owns zones, pricing rules, and report projections. The
internal quote endpoint is:

```text
GET /internal/v1/pricing/quote
```

It accepts pickup district, drop-off district, weight in kilograms, and parcel
size. It returns an LKR total and a named breakdown. The pickup district is
validated against an active served zone. The drop-off district selects the zone
base fee.

The baseline monetary values and final size taxonomy are still open questions
and must be agreed before persistence migrations or production seed data are
added. The current district catalog is the 25 administrative districts already
defined by operations validation and the OpenAPI contract:

`Ampara`, `Anuradhapura`, `Badulla`, `Batticaloa`, `Colombo`, `Galle`,
`Gampaha`, `Hambantota`, `Jaffna`, `Kalutara`, `Kandy`, `Kegalle`,
`Kilinochchi`, `Kurunegala`, `Mannar`, `Matale`, `Matara`, `Monaragala`,
`Mullaitivu`, `Nuwara Eliya`, `Polonnaruwa`, `Puttalam`, `Ratnapura`,
`Trincomalee`, `Vavuniya`.

Zone configuration accepts only these district names. No baseline zones,
weight-band charges, or size charges are seeded because the approved monetary
values have not been recorded.

## Strategy composition

The calculator uses three pure strategies:

1. **Zone strategy** validates pickup serviceability and returns the drop-off
   zone base fee.
2. **Weight strategy** selects the matching weight-band charge.
3. **Size strategy** selects the matching size charge.

Per decision 003, the result is:

```text
fee = drop-off zone base fee + weight-band charge + size charge
```

The strategies are composed by a quote calculator. They do not depend on Spring,
JPA, Kafka, or a database, which keeps boundary and rounding tests deterministic.
Unknown districts, unavailable pickup zones, missing rules, and invalid weights
are rejected rather than silently assigned a default price.

Money is calculated with decimal arithmetic and rounded to two decimal places
using the agreed monetary rounding mode at the quote boundary. The precise
rounding mode remains an implementation-level convention to keep consistent
across services until the team records it in the pricing decision.

## Price immutability

Pricing rules are used to calculate a quote before a parcel is confirmed. Once
the parcel is created, its finalized fee is stored on the parcel. Later rule
changes therefore affect future quotes only; they never recalculate parcels
that already exist or change their receipts.

## Reports and events

`operations-service` consumes parcel events and maintains a daily report
projection. The current API reports terminal delivery attempts: `DELIVERED`
increments successful deliveries and `FAILED` increments failed deliveries.
`PARCEL_CREATED` events are accepted for sequence and idempotency tracking but
do not increment the exposed delivery counters. A created-delivery metric is
described in this document's historical design text but is not defined by the
current OpenAPI response, so it remains unresolved rather than being inferred.
The projection stores processed event IDs and rejects stale per-parcel
sequences. It must not join another service's database.

Report filters are calendar dates in `Asia/Colombo`. Event timestamps are
stored as UTC instants; the report date is derived by converting the event
instant to `Asia/Colombo` before grouping. The JSON report and CSV export use
the same projection and date semantics.

CSV exports use the columns
`reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate`,
return `text/csv`, and use the attachment filename `daily-report.csv`.

Zone-level analytics are not available from the current parcel event envelope:
it contains parcel and status fields but no pickup/drop-off district or zone.
Adding those metrics requires an approved event-contract change and
coordination with the parcel-event owner; M5 does not derive zones from
unrelated fields.

## Audit

Administrative zone and pricing-rule changes are recorded in an audit table
with the actor, action, target type and ID, changed values or summary, and UTC
timestamp. The audit record supports traceability of rule changes; it does not
rewrite historical parcel fees.

The exact audit schema and ownership are open questions because the SRS assigns
the auth-service audit package to M8 while M5 owns operations pricing-rule
changes. The implementation PR must agree whether operations stores a local
pricing audit projection or publishes audit events to the shared audit owner.
