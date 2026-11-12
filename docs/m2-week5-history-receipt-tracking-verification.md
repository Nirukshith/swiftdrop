# M2 Week 5 — Issue #174 History, Receipt and Public Tracking Verification

## Scope

This verification covers the customer parcel history, the PDF receipt and the public tracking
page (CU-05, CU-06).

- The history list is M1's `GET /api/v1/parcels` (`parcel.yaml`). M2 builds the page and reads
  the API; it adds no second list endpoint.
- Receipts and tracking are M2's `receipt` and `tracking` packages, reading parcels through
  `ParcelQueryService`.

Backend changes in this issue are tests only. Frontend changes are in M2's `customer-history/`,
`tracking/` and `track.component.ts`, plus:

- one added route in `app.routes.ts` (`/track`)
- the header link in `app.html`, which pointed to an invalid sample number

## What changed

**Public tracking (`track.component.ts`)**

- A search box on the page, and a new `/track` route with no number that shows only the box. The
  header link "Track Sample" (`/track/SWIFT-12345`, which can never exist) is now
  "Track a parcel" (`/track`).
- Pasted numbers are cleaned (spaces removed, upper-cased) and checked against
  `SD-YYYYMMDD-XXXXXX` before anything is sent.
- A badly formed number, typed or in a link, shows a format hint. It sends no API request and
  opens no stream.
- The masked card and live timeline are unchanged.

**Customer history (`customer-history/`)**

- On phones (below the `sm` breakpoint) the history shows one card per parcel instead of the
  7-column table. Each card has the tracking number, status, recipient, destination, date, fee,
  a link to the detail page, and the receipt button for delivered parcels. The cards have their
  own loading and empty states.
- The page number and status filter are kept in the URL (`?page=2&status=DELIVERED`). Going Back
  from a parcel returns to the same page and filter, and invalid values fall back to the defaults.
- "Newest first" / "Recent parcels" wording was removed, because `GET /api/v1/parcels` has no
  defined sort order (see Known gaps).

## Automated verification

Backend command:

```text
mvn -B verify -pl services/parcel-service -am
```

Result:

```text
common:          13 tests passed
parcel-service:  214 tests passed (0 failures, 0 errors, 0 skipped)
BUILD SUCCESS
```

New `CustomerHistoryIntegrationTest` (Testcontainers PostgreSQL, bearer tokens signed with the
dev key, through parcel-service's security chain):

- **Pagination.** Three pages of 10 have the correct `totalElements`, `totalPages`, `page` and
  `size`. Every one of the customer's 23 parcels appears exactly once, and no one else's parcel
  appears. A page past the end is empty.
- **Status filter.** `status=DELIVERED` returns exactly the caller's delivered parcels.
- **Ownership comes from the token.** Another customer sees only their own 5 parcels, even with
  `customerId=<owner>` in the query. A courier token lists none.
- **No token or an invalid token:** 401.
- **Receipt with signed tokens:**
  - The owner gets `application/pdf` named `receipt-<trackingNumber>.pdf`. It has one page with
    the stored tracking number, id, addresses, recipient, size, weight and fee, and the delivery
    time from the stored DELIVERED history row. It has no phone, notes or courier id.
  - ADMIN gets 200.
  - Another customer and the courier get 403.
  - A parcel that isn't delivered gets 409 `PARCEL_NOT_DELIVERED`.
  - No token gets 401.

Existing tests still cover:

- receipt eligibility for every status, content and layout (`ReceiptServiceTest`,
  `ReceiptPdfRendererTest`, `ReceiptIntegrationTest`)
- public masking (`TrackingMapperTest`, `TrackingIntegrationTest`,
  `TrackingEndToEndIntegrationTest`)

Frontend command:

```text
cd frontend && npm ci && npm test -- --ci && npm run build
```

Result:

```text
Test Suites: 62 passed, 62 total
Tests:       442 passed, 442 total
Application bundle generation complete.
```

New frontend tests:

- **Tracking:**
  - `/track` shows the search prompt with no request
  - a pasted lower-case number with spaces opens that parcel
  - a bad or empty number shows the hint and is never sent
  - a link with a badly formed number makes no request and opens no stream
  - searching another number closes the old stream
- **History:**
  - phone cards: 10 cards, the table hidden on small screens, card text and link, the receipt
    button only on delivered cards, and the loading and empty states
  - page and filter written to and read from the URL, with invalid URL values ignored

## Live check (Docker, through the gateway)

Date: 2026-11-12. The core stack was built from this branch:

```text
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d --build
```

Customer parcel creation still returns 401 in Docker (see Known gaps). So 12 parcels for
customer A and 3 for customer B were inserted into `parcel_db` in the state the create endpoint
stores (`CREATED`, a creation history row), owned by each customer's real user id from the
token. One of A's parcels was then delivered through M1's real internal status workflow. Logins,
history, receipt and tracking all used the real services through the gateway.

```text
### 2. Customer parcel creation (POST /api/v1/parcels)
{"timestamp":"2026-11-12T13:12:31.452+00:00","status":401,"error":"Unauthorized","path":"/api/v1/parcels"}
-> HTTP 401

### 3. Deliver one of A's parcels through M1's real status workflow
assign -> 200
CREATED -> PICKED_UP: 200
PICKED_UP -> IN_TRANSIT: 200
IN_TRANSIT -> OUT_FOR_DELIVERY: 200
OUT_FOR_DELIVERY -> DELIVERED: 200
delivered parcel: cb636939-18da-4157-a053-af9ae4b152de SD-20261112-50FD38

### 4. History pages through the gateway (size 5)
page 0: 5 items, totalElements 12, totalPages 3, all owned by A: true
page 1: 5 items, totalElements 12, totalPages 3, all owned by A: true
page 2: 2 items, totalElements 12, totalPages 3, all owned by A: true
status=DELIVERED: 1 -> SD-20261112-50FD38 DELIVERED
customer B sees 3 parcels, any not owned by B: false
no token -> HTTP 401

### 5. Receipt through the gateway
owner A -> HTTP 200, 1776 bytes
Content-Type: application/pdf
Content-Disposition: attachment; filename="receipt-SD-20261112-50FD38.pdf"
file starts with: %PDF-2.0
customer B -> HTTP 403
{"code":"PARCEL_NOT_DELIVERED","message":"A receipt is only available for delivered parcels; current status is CREATED", ...}
undelivered parcel -> HTTP 409
no token -> HTTP 401

### 6. Public tracking through the gateway
{"trackingNumber":"SD-20261112-50FD38","status":"DELIVERED","recipientNameMasked":"S*** S***","dropoffDistrict":"Kandy","lastUpdatedAt":"2026-11-12T13:12:42.668849Z","timeline":[{"status":"CREATED","occurredAt":"2026-11-12T13:12:32.048207Z"},{"status":"PICKED_UP","occurredAt":"2026-11-12T13:12:42.364871Z"},{"status":"IN_TRANSIT","occurredAt":"2026-11-12T13:12:42.467964Z"},{"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-12T13:12:42.568514Z"},{"status":"DELIVERED","occurredAt":"2026-11-12T13:12:42.668849Z"}]}
unknown number -> HTTP 404
badly formed number -> HTTP 404
```

Text of the downloaded receipt, read back with OpenPDF:

```text
pages: 1
SwiftDrop
 Delivery receipt
 ...
 Tracking numberSD-20261112-50FD38
 Parcel IDcb636939-18da-4157-a053-af9ae4b152de
 Status Delivered
 Created 12 Nov 2026, 18:42
 Delivered 12 Nov 2026, 18:42
 Pickup address45 Galle Road, Colombo 03, Colombo, 00300
 Drop-off address12 Temple Road, Flat 2, Kandy, Kandy, 20000
 Recipient Sunil Silva
 Size Medium
 Weight 2.5 kg
 Delivery feeLKR 1,000.00
```

The stored phone number (`+94771234567`) and notes (`Gate code 4321`) are not in the receipt
or the public view.

The browser UI was not driven in this run. The page behaviour is covered by the frontend tests
above.

## Known gaps (outside M2)

| Gap | Effect | Owner |
| :--- | :--- | :--- |
| `GET /api/v1/parcels` has no defined sort order: no default sort, and `parcel.yaml` documents none | History pages can't promise "newest first"; the order is whatever Postgres returns | M1 |
| History has only a status filter; CU-05 "history with filters" may want date range or search (Q2 in `docs/tracking-design.md`) | No date or search filter on the history page | M1 (contract) |
| operations-service requires authentication on `/internal/v1/pricing/quote`, and parcel-service's pricing call sends none | `POST /api/v1/parcels` returns 401 in Docker for every customer | M5 / M1 |

## Acceptance checklist (Issue #174)

- [x] An authenticated customer can view paginated parcel history with ownership checks.
- [x] Delivered parcels can generate and download a correct PDF receipt using persisted parcel details.
- [x] Public tracking exposes only approved masked fields and a clear status timeline.
- [x] The UI handles empty, loading and error states and is responsive (phone card layout, search box).
- [x] Tests cover pagination, receipt eligibility and content, masking, and authorization.
- [x] Parcel reads go through M1's agreed interfaces (`GET /api/v1/parcels`, `ParcelQueryService`); no parcel domain ownership is duplicated.
