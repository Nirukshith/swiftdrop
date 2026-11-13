# M2 Week 7 — Issue #239 Tracking, SSE, History and Receipt Hardening Verification

#229 is a duplicate of #239 with the same title.

## Scope

Testing, hardening and defect fixing in M2's area:

- `parcel-service` `tracking` package: public lookup, SSE stream, history, rating, and the
  status-event consumer
- `receipt` package: PDF receipts
- the M2 frontend pages, which had no changes this week

Real integration paths are tested with Testcontainers and the Docker stack, not only mocks.
Cross-member defects are documented with a fix for the owner, not changed in their files.

## Defects found

| # | Defect | Owner | Status |
| :--- | :--- | :--- | :--- |
| 1 | **Receipts dropped Sinhala and Tamil text.** Helvetica only covers Latin, so a recipient like சுனில் குமார் or an address like මහනුවර printed as blank space. | M2 | **Fixed** (commit 2) |
| 2 | **All public-tracking visitors share one rate limit behind the gateway.** nginx sends no `X-Forwarded-For` on `/api/v1/tracking`, and the gateway isn't a trusted proxy, so parcel-service counts everyone as the gateway: 30 per minute, burst 10, for the whole site. | M9 (`nginx.conf`, `docker-compose.yml`) | **Documented with a tested fix** (`docs/tracking-design.md` 6.1) |
| 3 | **Empty 404 for an unknown parcel's receipt** once merged with M1's #280. `ParcelExceptionHandler` (highest priority) now also handles `ResponseStatusException` without a content type, so a request with `Accept: application/pdf`, as the frontend sends, got a 404 with no JSON body. | M2 | **Fixed** (commit 2): `ReceiptService` maps it to `ReceiptParcelNotFoundException`, which the receipt handler always sends as JSON. Verified on this branch and merged with the latest `development`. |

### 1. Sinhala and Tamil receipts (fixed)

Noto Sans Sinhala and Noto Sans Tamil (Regular and Bold, SIL Open Font License, about 0.5 MB, with
`OFL.txt`) are bundled in `com/swiftdrop/parcel/receipt/fonts/`.

- `ReceiptFonts` loads them once and turns on OpenPDF's `LayoutProcessor`, so the text is shaped:
  vowel signs are reordered and combined using the JDK's text layout. It also writes each run's
  original text as `ActualText`, so copy, paste and search work in PDF viewers.
- Value cells pick a font per character: Helvetica for Latin, Noto for Sinhala and Tamil. The Noto
  fonts are embedded as subsets, so a Latin-only receipt doesn't grow and stays under 5 KB.

Before the fix, the same renderer setup produced `Recipient: ` with nothing after it for Tamil
and Sinhala names; Latin names, including accents, were fine.

### 2. Shared rate limit behind the gateway (M9)

Evidence from the live run below (step 10), before the fix:

```text
visitor 1, 12 lookups: 404 404 404 404 404 404 404 404 404 404 429 429
visitor 2 (container swiftdrop-frontend), first lookup: 429
FAIL  visitor 2 has their own limit: expected 404, got 429
```

The fix was applied locally only (a copy of `nginx.conf` and a compose override, neither
committed). With it, visitor 2 gets `404` and all 31 checks pass:

```nginx
location ^~ /api/v1/tracking {
    proxy_pass http://parcel_service;
    proxy_set_header Host $host;                                   # must be repeated
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    ...
}
```

```yaml
parcel-service:
  environment:
    SWIFTDROP_TRACKING_RATE_LIMIT_TRUSTED_PROXIES: 172.16.0.0/12
```

The `Host` line matters. The first local attempt added only `X-Forwarded-For`. nginx then dropped
the inherited `proxy_set_header Host $host`, sent `Host: parcel_service`, and Tomcat answered
**400 on every tracking request** ("The character [_] is never valid in a domain name").

## Automated verification

Backend command:

```text
mvn -B verify -pl services/parcel-service -am
```

Result:

```text
common:          Tests run: 13,  Failures: 0, Errors: 0, Skipped: 0
parcel-service:  Tests run: 240, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

New and updated tests (parcel-service 222 → 240):

- **`TrackingRateLimitGatewayIntegrationTest`** (5 tests), with the gateway as a trusted proxy:
  - each visitor has their own limit when the gateway sends `X-Forwarded-For`
  - every visitor shares the gateway's limit when it doesn't
  - a direct caller can't dodge the limit with a fake header, and fake entries on the left of the
    chain are ignored
  - `429` with `Retry-After` and the shared error body, on the lookup and on the stream
- **`ReceiptMultilingualTest`** (7 tests), reading the PDF's `ActualText` runs, embedded fonts and
  glyph ids:
  - Tamil and Sinhala names and addresses appear in both directions, in the matching Noto fonts
  - **shaping:** the Tamil e-sign and the Sinhala kombuva are drawn before the consonant
  - a long Sinhala address wraps on one page and keeps every word
  - a Latin-only receipt embeds no Noto font and stays under 5 KB
  - bold values use the bold Noto font
- **`ReceiptIntegrationTest`** (+1, and one case extended):
  - a Tamil recipient and Sinhala city stored in Postgres appear on the receipt downloaded from the
    endpoint
  - an unknown parcel requested with `Accept: application/pdf` still gets the JSON 404 body
- The receipt tests also pass with this branch merged with the latest `development`, which includes
  M1's #280: `Tests run: 30, Failures: 0`.
- **`TrackingAccessMatrixIntegrationTest`** (4 tests): every M2 endpoint × 11 callers with real
  signed tokens. The callers are owner and other customer, assigned and other courier, dispatcher,
  admin, merchant, no token, expired token, wrongly signed token, and a non-JWT. Results:
  - history: owner, assigned courier, dispatcher and admin
  - receipt and reading a rating: owner and admin
  - submitting a rating: owner only, and refused calls store nothing
  - bad tokens: 401
  - public lookup and stream: open to everyone, and they ignore any token

The Week 6 tests for retry and the dead-letter topic, the 2-second live updates, reconnect and
stale events still pass.

Frontend command (Node 22.23.4, no frontend changes):

```text
cd frontend && npm ci && npm test -- --ci && npm run build
Test Suites: 65 passed, 65 total
Tests:       492 passed, 492 total
Application bundle generation complete.
```

## Live end-to-end run (`docs/m2-e2e/run.sh`)

The script is now in the repo, so anyone can rerun it:

```text
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d
bash docs/m2-e2e/run.sh
```

It needs bash, curl, node and docker. It prints `ok` or `FAIL` for each of 31 checks and exits
with the number of failures.

Run on 2026-11-13 against the stack built from this branch (parcel-service image rebuilt with
`docker build -f deploy/dockerfiles/parcel-service.Dockerfile -t deploy-parcel-service .`). The
customer created the parcel through the API with a **Tamil recipient and a Sinhala city**. Nothing
was inserted into `parcel_db`.

```text
### 2. Customer A creates a parcel with a Tamil recipient and a Sinhala city
ok    POST /api/v1/parcels (201)
parcel id=0261cf14-5362-4ce6-b796-4abbb918a361 trackingNumber=SD-20261113-10V9VY fee=LKR 700

### 3. Public lookup without login is masked
ok    public status (CREATED)
ok    recipient masked (Tamil initials) (U+0B9A*** U+0B95***)
ok    no address, phone, notes or fee (none)

### 4. Live stream through the gateway, timed (NF-01: under 2 s)
ok    assign courier (200)
PICKED_UP         sequence 3: 145 ms
IN_TRANSIT        sequence 4: 108 ms
OUT_FOR_DELIVERY  sequence 5: 130 ms
DELIVERED         sequence 6: 131 ms
ok    every status change reached the stream (0)
ok    slowest update under 2000 ms (yes)
ok    event data has exactly the 4 public fields (4)

### 5. Reconnect with Last-Event-ID 3 replays only the missed changes
ok    replayed ids (4 5 6)

### 6. History is owner-only
ok    A's list contains the parcel (true)
ok    B's list does not (false)
ok    A /history (200)
ok    B /history (403)
ok    no token /history (401)
ok    timeline (CREATED PICKED_UP IN_TRANSIT OUT_FOR_DELIVERY DELIVERED)

### 7. PDF receipt (Tamil recipient, Sinhala city)
ok    A receipt (200)
ok    receipt is a PDF (%PDF-)
ok    Tamil font embedded (yes)
ok    Sinhala font embedded (yes)
ok    B receipt (403)
ok    no token receipt (401)

### 8. Delivery rating
ok    B rates (403)
ok    A rates 7 (400)
ok    A rates 5 (201)
ok    A rates again (409)
ok    A reads rating (5)

### 9. Every change stored for replay by the Kafka consumer
ok    tracking_event rows (3 4 5 6)

### 10. Public rate limit per visitor behind the gateway (burst 10)
visitor 1, 12 lookups: 404 404 404 404 404 404 404 404 404 404 429 429
ok    visitor 1 is limited after the burst (429)
visitor 2 (container swiftdrop-frontend), first lookup: 429
FAIL  visitor 2 has their own limit: expected 404, got 429

### Result: 1 failed check(s)
```

With M9's fix applied locally: `visitor 2 (container swiftdrop-frontend), first lookup: 404`, then
`Result: 0 failed check(s)`, and live updates arrived in 111–138 ms.

Two problems found while writing the script were in the script, not the app:

- **Tamil text in a command-line argument.** On Windows, curl received it as `?` characters, which
  were stored as `??????`. Sent as a UTF-8 file, the same text is stored correctly (hex
  `e0ae9a…`) and masked as `ச*** க***`. The script now writes the request body with node.
- A bad output path in the latency step.

The browser UI was not driven. The frontend tests cover the pages.

## Environment notes

- Docker Compose v5.6.1 here still refuses `--build` (see the Week 6 doc), so images are built one
  by one with `docker build`. One parcel-service image build failed once in the Maven step and
  succeeded unchanged on the retry, so it was most likely a temporary dependency download problem.
- One Testcontainers run stalled for 11 minutes inside Hibernate startup, before any test code.
  The same class started in 39 s on the next run.

## Remaining blockers

| Item | Owner |
| :--- | :--- |
| Gateway `X-Forwarded-For` and `Host` lines, plus parcel-service trusted proxies (defect 2) | M9 |
| History filters (Issue #219): the contract (#275) and M1's endpoint (#280) are merged; the M2 history page UI is a separate follow-up for #219 | M2 |

## Acceptance checklist (Issue #239)

- [x] Relevant unit, integration and end-to-end tests are added or updated.
- [x] Relevant authorization, validation, error, retry and failure cases are tested.
- [x] Confirmed defects in owned code are fixed (receipt fonts, JSON 404 for receipts).
- [x] Required module and frontend verification commands and results are recorded.
- [x] Cross-service integration and remaining blockers are documented with reproducible evidence (`docs/m2-e2e/run.sh`; the gateway rate-limit defect needs M9).
- [x] A focused PR targeting `development` is opened and linked.
