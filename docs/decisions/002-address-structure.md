# 002 - Address Structure

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
Delivery pricing, zone assignment, and courier routing require reliable geographical classification without relying on external paid map APIs. Freeform address text would create ambiguity in zone mapping and make automated fee calculation error-prone.

## Decision
Addresses consist of fixed structured fields, with `district` strictly required from a predefined fixed list. Delivery zones are looked up directly by district.

## Consequences
- **M1**: Implements the structured Address model in `parcel-service` and validates `district` against the fixed list in delivery creation and customer address book forms.
- **M5**: Maps delivery zones to districts in `operations-service` (`zones` package) and resolves zone pricing based on the pickup and drop-off districts.
- **M6**: Validates the structured address format and required `district` in merchant single and bulk CSV order creation endpoints.
- **M8, M9**: Courier views display the fixed address fields and construct Google Maps navigation links from the structured address text.
- **M2, M3**: Customer parcel views display structured addresses; assistant `estimate_fee` tool accepts district inputs to resolve pricing.
- **M4, M7, M10**: No direct impact, though any mock user or merchant contact data must align with the structured address schema.

## Open items
- Predefined list of districts to be codified in shared validation rules or `contracts/`.
