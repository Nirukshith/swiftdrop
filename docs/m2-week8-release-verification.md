# M2 Week 8 — Issue #249 Release Verification: Tracking, History, Receipts, Frontend Integration

## Scope

Final integration and release preparation of M2's area:

- **parcel-service** `tracking` and `receipt` packages: public tracking and SSE, history timeline,
  ratings, PDF receipts, and the status-event consumer
- **M2's web pages**: public tracking, dashboard, My parcels with filters, and the parcel detail
  page with live timeline, receipt and rating

Verified on 2026-11-13 against the latest `development` (`2dd7a0c`, after #288), with every service
image rebuilt from it.

This issue adds no new features. It adds:

- a real-browser walkthrough
- configuration and operations documentation (`docs/m2-configuration.md`)
- a demo guide with screenshots (`docs/demo/m2-week8-tracking-demo.md`)
- this verification

No production code, contract or other member's file was changed.

## Integration with dependent services

| Dependency | Owner | Verified by |
| :--- | :--- | :--- |
| auth-service login and JWT (JWKS) | M10 | Browser login through the UI; signed tokens in tests; 401 without a token |
| Parcel creation and pricing | M1, M5, M10 | `POST /api/v1/parcels` through the gateway returns 201 with fee LKR 700 |
| Courier assignment and status workflow | M1 | Internal status endpoint drives CREATED → DELIVERED |
| `parcel.status-changed.v1` on Kafka | M1 publishes, M2 consumes | Changes reach the browser in 58–171 ms; all stored in `tracking_event` |
| History list with filters | M1 (#280) | Browser filter, API filter checks, consumer tests |
| nginx gateway (SSE, API and web app routes) | M9 | Everything above runs through `http://localhost:8080` |
| Frontend container | M9 | Serves the app at `/` through the gateway |

## Automated verification

```text
mvn -B verify -pl services/parcel-service -am
common:          Tests run: 13,  Failures: 0, Errors: 0, Skipped: 0
parcel-service:  Tests run: 252, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

cd frontend && npm ci && npm test -- --ci && npm run build      (Node 22.23.4)
Test Suites: 65 passed, 65 total
Tests:       525 passed, 525 total
Application bundle generation complete.
```

## Real-browser walkthrough (`node docs/m2-e2e/browser-walkthrough.mjs`)

The script drives the real web app in headless Edge 157 over the DevTools protocol, with no
dependencies beyond Node 22. **Result: 32 ok, 0 failed.** The screenshots are in
`docs/demo/m2-week8/`.

```text
### 1. Setup through the APIs: pricing data, customers A and B, A's parcel (Tamil recipient, Sinhala city)
ok    customer A creates a parcel (API) (201)
      parcel SD-20261113-7S0WT7 (9401231c-f673-425d-b66a-d6adb6cfe4f2), fee LKR 700
### 2. Customer A signs in through the UI
ok    login redirects away from /login (true)
### 3. My parcels: list, then filter by tracking number
ok    history shows the parcel (true)
ok    history says newest first (true)
ok    filter keeps the parcel (true)
ok    URL carries the filter (true)
ok    no match shows the filtered empty state (true)
### 4. Parcel detail: the timeline updates live while the status changes (no reload)
ok    detail shows the tracking number (true)
ok    stream connects (Live) (true)
ok    Picked up appears on the open page within 2 s (true)        163 ms
ok    In transit appears on the open page within 2 s (true)       58 ms
ok    Out for delivery appears on the open page within 2 s (true) 171 ms
ok    Delivered appears on the open page within 2 s (true)        147 ms
ok    receipt button appears once delivered (true)
ok    rating card appears once delivered (true)
### 5. Download the PDF receipt from the page
ok    receipt file downloaded (receipt-SD-20261113-7S0WT7.pdf)
ok    receipt is a PDF (%PDF-)
ok    receipt embeds the Tamil and Sinhala fonts (true)
### 6. Rate the delivery, then reload: the rating is still there
ok    thank-you message after rating (true)
ok    rating still shown after reload (true)
### 7. Public tracking page (no login): masked details and the full timeline
ok    /track shows the search box (true)
ok    search opens the parcel (true)
ok    recipient is masked (true)
ok    no address, phone or notes on the public page (false)
ok    public timeline ends with Delivered (Delivered)
ok    a badly formed number gets the format hint (true)
### 8. Customer B cannot see A's parcel
ok    customer B signs in (true)
ok    B gets the no-access message (true)
ok    no details leak to B (false)
### 9. Phone width (360 px, NF-04)
ok    09-mobile-public-track: M2 page fits 360 px (true)
      whole page width 418 px (wider than the screen because of the shared header: a "Daily summary", a "Track a parcel")
ok    10-mobile-history: M2 page fits 360 px (true)
      whole page width 418 px (wider than the screen because of the shared header: a "Daily summary", a "Track a parcel")
ok    history uses cards on a phone (true)
```

The first version of the phone check measured the whole page and failed: 418 px instead of 360.
The screenshots showed the overflow is the shared app header's nav row, not an M2 page, so the
check now measures the M2 page itself and reports the header separately (limitation 3 below).

## API end-to-end check (`bash docs/m2-e2e/run.sh`)

**Result: 34 ok, 1 failed.** The failure is the known gateway rate-limit defect (limitation 1).

```text
ok    POST /api/v1/parcels (201)            trackingNumber=SD-20261113-I9134R fee=LKR 700
ok    recipient masked (Tamil initials)     ok    no address, phone, notes or fee
PICKED_UP 94 ms   IN_TRANSIT 104 ms   OUT_FOR_DELIVERY 87 ms   DELIVERED 86 ms
ok    every status change reached the stream     ok    event data has exactly the 4 public fields
ok    replayed ids (4 5 6)
ok    A /history (200)   ok    B /history (403)   ok    no token /history (401)
ok    q=i9134r (lower case) finds the parcel     ok    from=to=2026-11-13 finds the parcel
ok    to=2026-01-01 does not                     ok    from after to (400)
ok    A receipt (200), PDF, Tamil and Sinhala fonts embedded; B 403; no token 401
ok    B rates (403)   ok    A rates 7 (400)   ok    A rates 5 (201)   ok    A rates again (409)
ok    tracking_event rows (3 4 5 6)
visitor 1, 12 lookups: 404 404 404 404 404 404 404 404 404 404 429 429
visitor 2 (container swiftdrop-frontend), first lookup: 429
FAIL  visitor 2 has their own limit: expected 404, got 429
```

## Configuration check

The environment-variable names in `docs/m2-configuration.md` were checked against a running
parcel-service:

- `SWIFTDROP_TRACKING_RATE_LIMIT_BURST=2` made the third direct lookup return `429`.
- `SWIFTDROP_TRACKING_STREAM_RECONNECT_DELAY=7s` changed the stream's `retry:` to `7000`.

The default configuration was restored afterwards.

## Known limitations, unresolved defects and workarounds

| # | Item | Status | Workaround or owner |
| :--- | :--- | :--- | :--- |
| 1 | Behind the Docker gateway, all public-tracking visitors share one rate limit (30/min, burst 10), because nginx sends no `X-Forwarded-For` on `/api/v1/tracking` | **Unresolved; not M2's file** | M9: the fix tested in Week 7 (`docs/tracking-design.md` 6.1). For the demo, keep public lookups under about 10 a minute, or locally set `SWIFTDROP_TRACKING_RATE_LIMIT_ENABLED=false` |
| 2 | Docker Compose 5.x refuses `up --build` (`--allow=fs.read`) | Environment | Build images one by one, then `up --no-build` (`m2-configuration.md` §4) |
| 3 | The shared app header's nav row doesn't wrap at 360 px (page 418 px wide) | Open; not M2's file | App shell owner (`app.html`); the M2 pages themselves fit |
| 4 | `%` and `_` in the history search act as wildcards | Harmless (own parcels only) | M1 could escape them |
| 5 | History entries have `sequence: null` (M1's `status_history` has no sequence) | As the contract allows | None needed |
| 6 | No admin user is seeded, so there is no pricing on a fresh database | Environment | Both scripts insert demo pricing for Colombo and Kandy |
| 7 | M2 Kubernetes Ingress / Helm chart | Not delivered | Bonus work for M2 (ADR 006) |

There are no known defects in M2's own code.

## Acceptance checklist (Issue #249)

- [x] The owned feature is integrated with the latest `development` and the services it depends on (table above).
- [x] The final user workflow is tested and demonstrable: real-browser walkthrough 32/32, demo guide with screenshots.
- [x] Relevant automated tests and build commands pass, with results recorded (parcel-service 252, common 13, frontend 525, build OK).
- [x] Environment variables, setup and run steps, and operational requirements are documented without secrets (`docs/m2-configuration.md`).
- [x] Known limitations, unresolved defects and workarounds are documented (above, and `m2-configuration.md` §5).
- [x] Demo steps and evidence are prepared (`docs/demo/m2-week8-tracking-demo.md`, `docs/demo/m2-week8/`).
- [x] A focused PR targeting `development` is opened and linked.
