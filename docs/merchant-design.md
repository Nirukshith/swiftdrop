# Merchant Onboarding and Orders (M6)

**Status:** Week 1 contract proposal. API details marked **proposed** require team review where the SRS/ADRs do not decide the shape.

## Scope and ownership

M6 owns the `merchant` package in `integration-service` (8084), merchant registration, approval coordination, and merchant order creation/cancellation/bulk upload contracts. M5 owns the admin approval page and calls the admin merchant endpoints. M10 owns auth users, activation, and OAuth client credentials. M1 owns parcel creation and parcel status; integration-service must call M1 rather than write parcel state itself. M5 owns pricing and the quote endpoint. Bulk upload is a Should-have and is explicitly lower priority than the Must-have registration, approval, and single-order flow.

The OpenAPI proposal is in `contracts/openapi/integration-merchant.yaml`.

## Onboarding and credential provisioning

1. A merchant submits registration. Integration-service validates and stores the merchant profile as `PENDING`; it does not issue ordering credentials.
2. M5's ADMIN-only approval page uses `GET /api/v1/admin/merchants`, then approves or rejects a pending profile.
3. On approval, integration-service coordinates with M10's auth-service using the endpoints specified by the task: `POST /internal/v1/users`, `POST /internal/v1/users/{id}/activate`, then `POST /internal/v1/clients`. These paths are given by the task; request/response fields, call order guarantees, and client-to-merchant association shape are not established by the SRS or existing auth contracts and remain questions below.
4. The merchant becomes `APPROVED` only when user activation and client provisioning succeed (exact transaction/compensation behavior needs agreement; services cannot share a database transaction).
5. Auth-service must store only a hash of the client secret. The raw secret may be displayed once, never logged, and never returned by later list/read calls. The exact secure one-time delivery path is not settled. A client secret is distinct from the OAuth access token.
6. The merchant obtains an OAuth 2.0 client-credentials access token from auth-service and sends it as `Authorization: Bearer <token>` to merchant APIs. This bearer-token pattern follows ADR 004. The exact token URL and client-credentials request/response contract are not in the available auth OpenAPI contract.

## Merchant states and transitions

| Current state | Event | Next state | Notes |
| --- | --- | --- | --- |
| `PENDING` | ADMIN approves and auth provisioning succeeds | `APPROVED` | Enables merchant order API access after client credentials are ready. |
| `PENDING` | ADMIN rejects | `REJECTED` | Rejection reason is included in the proposal; confirm if it is required/persisted. |
| `APPROVED` | Suspension action | `SUSPENDED` | **Open question:** no suspension endpoint or owner workflow is currently specified. |

No re-approval, rejection reversal, or reactivation transition is defined. `SUSPENDED` is a recognized proposed state only; do not assume it can be entered until an endpoint and authorization policy are agreed. The administrator operations are restricted to `ADMIN` per decision 001.

## Merchant order API

The contract proposes:

- `POST /api/v1/merchant/orders` for one order.
- `POST /api/v1/merchant/orders/{orderId}/cancel` for cancellation before pickup only, reflecting the parcel lifecycle in the SRS.
- `POST /api/v1/merchant/orders/bulk` for CSV upload (Should-have).
- All three operations require `Idempotency-Key`, scoped to the authenticated merchant and operation. Exact route names and cancel HTTP method are proposals for M1/M7 review.

For order creation, integration-service validates the merchant token, structured pickup/drop-off addresses (ADR 002 requires district), and parcel fields; obtains the M5 price quote (ADR 003); then calls M1's `POST /internal/v1/parcels`. Parcel-service remains the authority for parcel lifecycle. On success, integration-service stores its merchant-order mapping and returns order and tracking identifiers. Do not infer M1's request/response schema from this design; the internal parcel contract is still an open question.

Cancellation must be forwarded to the parcel service's approved cancellation operation, not applied by directly changing status in integration-service. The SRS says cancellation is before pickup; its inter-service endpoint and request shape are unspecified.

## Idempotency design (proposal)

For each write, persist `(merchantId, operation, idempotencyKey)`, a canonical request hash, processing state, original HTTP status/body, and creation/expiry timestamps. Proposed key retention is **24 hours** after first acceptance. Calculate the hash over a canonical representation of the authenticated operation and payload (for CSV, the uploaded bytes after transport decoding); do not include bearer credentials.

- First request reserves the key, performs the operation, then stores the complete result.
- Same key and same hash returns the stored status/body without repeating downstream effects.
- Same key and different hash returns `409 Conflict`.
- Concurrent requests for one scoped key must serialize or use a unique constraint so only one parcel side effect occurs.
- Define recovery for a process crash after parcel creation but before storing the response using a stable downstream reference or reconciliation; do not claim exactly-once behavior until M1's idempotency support is agreed.
- Expired keys may be removed after the retention window. The retention period is a proposal and must be approved before implementation.

## Bulk CSV proposal

`text/csv`, UTF-8, one header row. Proposed columns (header spellings are case-sensitive in this draft):

`externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg`

`pickupDistrict` and `dropoffDistrict` are required and must be values from the team-maintained district list (ADR 002 notes that list is still open). The proposed response gives totals and a result per source row, with row number, accepted flag, created order on success, and validation/downstream errors on failure. Proposed processing is independent per row with partial success; each successful row must still have its own stable idempotency identity derived from the upload key and row number, so retrying the upload cannot duplicate successful parcels. File-level malformed CSV rejects the whole request. This format and partial-success/retry policy are proposals, not established requirements.

## Rate limits (proposal)

No numeric limits appear in the SRS. Initial local-development proposal, keyed by OAuth client ID:

- Registration: 5 requests per source IP per hour.
- Single-order create/cancel: 60 requests per client per minute.
- Bulk CSV: 5 uploads per client per minute, proposed maximum 5 MiB and 100 rows per file.
- Return `429` with `Retry-After`; do not log access tokens or client secrets.

Team/security review must set production values and decide whether registration needs IP-based throttling behind the gateway.

## `MerchantOrderQuery` contract for M7

M7's dashboard needs tenant-scoped order listing and status updates from the existing parcel lifecycle events. Proposed Java repository/service query methods (UUID IDs, Spring `Page`/`Pageable`, and `Instant` timestamps; confirm actual domain types before coding):

```java
Page<MerchantOrder> findByMerchantId(UUID merchantId, MerchantOrderStatus status, Pageable pageable);
Optional<MerchantOrder> findByIdAndMerchantId(UUID orderId, UUID merchantId);
Optional<MerchantOrder> findByParcelId(UUID parcelId);
boolean existsByMerchantIdAndExternalReference(UUID merchantId, String externalReference);
int updateParcelStatusByParcelId(UUID parcelId, ParcelStatus status, Instant occurredAt);
```

`status` may be nullable to mean no status filter. Every merchant-facing lookup must scope by the merchant ID from the authenticated token; never trust a merchant ID supplied by the caller. The event updater is intended for M7's `parcel.status-changed.v1` consumer and must de-duplicate events using `eventId` per the event contract. M7 should confirm these exact method names/types before implementation.

## Open questions for the PR reviewers

1. M10: What are the exact request/response schemas and service-auth mechanism for `POST /internal/v1/users`, `POST /internal/v1/users/{id}/activate`, `POST /internal/v1/clients`, and the OAuth client-credentials token endpoint? How is the client associated with the merchant?
2. M10/M5: Which workflow displays the raw client secret exactly once to the merchant, given approval is performed through M5's admin page? Should approval provision credentials synchronously or use a retryable saga?
3. M1: What is the exact `POST /internal/v1/parcels` schema, cancellation endpoint, downstream idempotency support, and parcel/order ID relationship?
4. M5: What is the exact quote request/response contract and district allow-list source?
5. M7: Confirm the proposed `MerchantOrderQuery` method signatures, order/status fields needed by the dashboard, and event-to-order update ownership.
6. M5/M10: Confirm registration and admin approval/rejection payloads, whether rejection reason is required, and pagination/filter semantics.
7. Team: Confirm order routes/methods, CSV header/size/row limits, partial-success semantics, idempotency key retention, and proposed rate limits.
8. Team: `SUSPENDED` has no defined endpoint or transition workflow yet. Who can suspend/reactivate merchants, and what happens to already-issued OAuth clients?
