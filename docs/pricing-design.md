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

The baseline monetary values, predefined district catalog, and final size
taxonomy are still open questions and must be agreed before persistence
migrations or production seed data are added.

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

`operations-service` consumes parcel events and maintains a `DailyStat`
projection. At minimum, the projection aggregates created, delivered, and
failed deliveries, and stores processed event IDs for idempotency. It must not
join another service's database.

Report filters are calendar dates in `Asia/Colombo`. Event timestamps are
stored as UTC instants; the report date is derived by converting the event
instant to `Asia/Colombo` before grouping. The JSON report and CSV export use
the same projection and date semantics.

## Audit

Administrative zone and pricing-rule changes are recorded in an audit table
with the actor, action, target type and ID, changed values or summary, and UTC
timestamp. The audit record supports traceability of rule changes; it does not
rewrite historical parcel fees.

The exact audit schema and ownership are open questions because the SRS assigns
the auth-service audit package to M8 while M5 owns operations pricing-rule
changes. The implementation PR must agree whether operations stores a local
pricing audit projection or publishes audit events to the shared audit owner.
