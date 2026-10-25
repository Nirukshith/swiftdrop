# 003 - Pricing Formula

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
The system requires a clear, deterministic fee calculation mechanism for upfront quotes and order processing. Costs vary based on destination zone, parcel weight, and package dimensions, while service availability must be validated before an order can be accepted.

## Decision
The delivery fee is calculated as:
`Fee = drop-off zone base fee + weight-band charge + size charge`

The pickup zone only validates that the pickup location is an active, served area. If unserved, the quote or delivery request is rejected.

## Consequences
- **M5**: Implements the `PricingStrategy` in `operations-service` using this formula; stores zone base fees, weight bands, and size charges; implements `GET /internal/v1/pricing/quote` and validates pickup serviceability.
- **M1**: Calls `GET /internal/v1/pricing/quote` from `parcel-service` to show the customer fee preview and store the finalized fee on the parcel; handles errors if pickup is not served.
- **M3**: Assistant tool `estimate_fee(weightKg, size, destination)` calls the pricing quote API and returns this computed fee.
- **M6**: Uses the fee quote endpoint during merchant order creation to calculate order totals.
- **M2**: Displays the finalized fee on the parcel details and generates the receipt PDF.
- **M4, M7, M8, M9, M10**: Read the calculated fee from the parcel entity as needed without modifying pricing logic.

## Open items
- Baseline monetary values for drop-off base fees, weight bands, and size charges to be seeded in Flyway migrations for `operations-service`.
