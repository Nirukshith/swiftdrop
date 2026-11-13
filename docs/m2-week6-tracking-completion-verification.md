# M2 Week 6 — Issue #219 Tracking, History, Receipts and Real-Time Updates Verification

## Scope

M2's SRS scope (row M2 and CU-04, CU-05, CU-06, NF-01, NF-04):

- dashboard
- parcel detail with the live SSE timeline
- public tracking
- history table
- PDF receipt download
- the `parcel-service` `tracking` and `receipt` packages and the status-event consumer

This issue completes and verifies that scope:

- adds tests for the remaining failure and retry behaviour and the 2-second live-update
  requirement
- records the first fully real end-to-end run, now that customer parcel creation works in Docker
  (M10's #267 fixed the pricing 401)
- proposes the history filters CU-05 still needs to M1

No production code changed. The other members' code, the contracts and the gateway are untouched.
No separately tracked additional-fix issue is assigned to M2.

## Requirement status

| Requirement | Where | Status |
| :--- | :--- | :--- |
| CU-04 live tracking via SSE | `tracking` package, parcel detail and public pages | Done; verified live below, including reconnect |
| CU-05 history, PDF receipt download | History page, `receipt` package | Done, except history **filters** beyond status (needs M1, see below) |
| CU-06 public tracking page, no login | `/track`, `/track/:trackingNo` | Done; masked fields only |
| CU-07 rate delivery (+) | Rating card, `POST/GET /rating` | Done; verified live below |
| NF-01 live update within 2 s | Kafka → tracking → SSE | Done: automated check, and 120–160 ms measured through the gateway |
| NF-04 usable at 360 px | Public page, history cards | Done (Week 5); covered by frontend tests |
| Failure and retry behaviour | History writer retry and DLT, SSE reconnect, receipt and rating errors | Done; new retry and DLT test below |
| Ingress & Helm chart | — | Not done: bonus work for M2 by ADR 006 |

## Automated verification

Backend command:

```text
mvn -B verify -pl services/parcel-service -am
```

Result:

```text
common:          13 tests passed
parcel-service:  222 tests passed (0 failures, 0 errors, 0 skipped)
BUILD SUCCESS
```

New in this issue:

- **`TrackingRetryIntegrationTest`** (Testcontainers Kafka and PostgreSQL; the store is a spy that
  fails for one event):
  - A short database outage (2 failures) is retried, and the change is stored exactly once. Nothing
    reaches `parcel.status-changed.v1.dlt`.
  - A failure that doesn't clear is tried 5 times with 1, 2, 4 and 8 s back-off (at least 14 s).
    Then the original message, with its key unchanged, lands on the DLT. The headers carry the
    original topic and `DataAccessResourceFailureException`, and nothing is stored.
  - An unusable message goes to the DLT at once with `InvalidTrackingEventException`, without
    back-off. The next valid event is still stored, so the consumer isn't stuck.
- **`TrackingEndToEndIntegrationTest.liveUpdateWithinTwoSeconds`** (NF-01): four real status
  changes, PICKED_UP through DELIVERED. Each goes through M1's status workflow, Kafka and the
  tracking consumers, and must reach an open SSE client within 2 seconds of the request.

Frontend command (Node 22.23.4, no frontend changes in this issue):

```text
cd frontend && npm ci && npm test -- --ci && npm run build
```

Result:

```text
Test Suites: 65 passed, 65 total
Tests:       489 passed, 489 total
Application bundle generation complete.
```

## Live end-to-end run (Docker, through the gateway)

Date: 2026-11-13. Images built from this branch with plain `docker build`, one per service, then
started with `docker compose ... --profile core up -d --no-build`. See "Environment note".

Nothing was inserted into `parcel_db`. The only local setup was pricing zones and rules for
Colombo and Kandy in `ops_db`, because no admin user is seeded to create them through the API.

```text
### 2. Customer A creates a parcel (POST /api/v1/parcels via gateway, real pricing)
HTTP 201  id=ee2cf1d8-d9cf-42fa-93d7-4d5eef6e997b trackingNumber=SD-20261113-9OX5T3 status=CREATED fee=LKR 700

### 3. Public lookup without login (masked)
{"trackingNumber":"SD-20261113-9OX5T3","status":"CREATED","recipientNameMasked":"S*** S***","dropoffDistrict":"Kandy","lastUpdatedAt":"2026-11-13T01:19:45.488279Z","timeline":[{"status":"CREATED","occurredAt":"2026-11-13T01:19:45.488279Z"}]}

### 4. Live stream through the gateway, timed
assign courier -> HTTP 200
PICKED_UP sequence=3
IN_TRANSIT sequence=4
OUT_FOR_DELIVERY sequence=5
DELIVERED sequence=6
--- raw stream (arrival ms + line) ---
1794532786722 HTTP 200 text/event-stream
1794532786725 retry:5000
1794532786726 id:1
1794532786726 event:status
1794532786726 data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":1,"status":"CREATED","occurredAt":"2026-11-13T01:19:45.488279Z"}
1794532788966 id:3
1794532788966 event:status
1794532788966 data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":3,"status":"PICKED_UP","occurredAt":"2026-11-13T01:19:48.926332096Z"}
1794532790255 id:4
1794532790256 event:status
1794532790258 data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":4,"status":"IN_TRANSIT","occurredAt":"2026-11-13T01:19:50.238625017Z"}
1794532791627 id:5
1794532791627 event:status
1794532791628 data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":5,"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-13T01:19:51.608283312Z"}
1794532792945 id:6
1794532792946 event:status
1794532792946 data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":6,"status":"DELIVERED","occurredAt":"2026-11-13T01:19:52.929308819Z"}
1794532792970 END
--- status change -> event on the client, through the gateway (NF-01: under 2000 ms) ---
PICKED_UP         sequence 3: 151 ms
IN_TRANSIT        sequence 4: 133 ms
OUT_FOR_DELIVERY  sequence 5: 160 ms
DELIVERED         sequence 6: 120 ms
slowest: 160 ms -> within 2 s

### 5. Reconnect with Last-Event-ID 3 after delivery (replay of missed changes, then the stream ends)
HTTP 200 text/event-stream
retry:5000
id:4
event:status
data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":4,"status":"IN_TRANSIT","occurredAt":"2026-11-13T01:19:50.238625Z"}
id:5
event:status
data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":5,"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-13T01:19:51.608283Z"}
id:6
event:status
data:{"trackingNumber":"SD-20261113-9OX5T3","sequence":6,"status":"DELIVERED","occurredAt":"2026-11-13T01:19:52.929309Z"}
END

### 6. History list and timeline (owner only)
A list: totalElements 1, contains SD-20261113-9OX5T3: true, status DELIVERED
B list: totalElements 0, contains A parcel: false
A /history -> HTTP 200
  timeline: CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
B /history -> HTTP 403
no token /history -> HTTP 401

### 7. PDF receipt
A -> HTTP 200, 1777 bytes
Content-Type: application/pdf
Content-Disposition: attachment; filename="receipt-SD-20261113-9OX5T3.pdf"
B -> HTTP 403
no token -> HTTP 401

### 8. Delivery rating
A rates 5:        {"parcelId":"ee2cf1d8-...","score":5,"comment":"Courier was on time","createdAt":"2026-11-13T01:19:57.590406Z"} -> HTTP 201
A rates again:    {"code":"RATING_ALREADY_EXISTS","message":"Parcel SD-20261113-9OX5T3 has already been rated",...} -> HTTP 409
B rates:          {"code":"ACCESS_DENIED","message":"Only the customer who sent this parcel can rate it",...} -> HTTP 403
A score 7:        {"code":"VALIDATION_FAILED","message":"score must be between 1 and 5",...} -> HTTP 400
{"parcelId":"ee2cf1d8-...","score":5,"comment":"Courier was on time","createdAt":"2026-11-13T01:19:57.590406Z"} -> HTTP 200

### 9. Stored for replay (parcel_db.tracking_event, written by the Kafka consumer)
 sequence |   from_status    |    to_status     |          occurred_at
----------+------------------+------------------+-------------------------------
        3 | CREATED          | PICKED_UP        | 2026-11-13 01:19:48.926332+00
        4 | PICKED_UP        | IN_TRANSIT       | 2026-11-13 01:19:50.238625+00
        5 | IN_TRANSIT       | OUT_FOR_DELIVERY | 2026-11-13 01:19:51.608283+00
        6 | OUT_FOR_DELIVERY | DELIVERED        | 2026-11-13 01:19:52.929309+00
(4 rows)

### 10. Frontend served by the stack
GET :4200/ -> HTTP 200  (<app-root)
GET :4200/track -> HTTP 200  (<app-root)
GET :4200/track/SD-20261113-9OX5T3 -> HTTP 200  (<app-root)
GET :4200/customer/parcels -> HTTP 200  (<app-root)
```

Text of the downloaded receipt, read back with OpenPDF:

```text
pages: 1
SwiftDrop
 Delivery receipt
 ...
 Tracking numberSD-20261113-9OX5T3
 Parcel IDee2cf1d8-d9cf-42fa-93d7-4d5eef6e997b
 Status Delivered
 Created 13 Nov 2026, 06:49
 Delivered 13 Nov 2026, 06:49
 Pickup address45 Galle Road, Colombo 03, Colombo, 00300
 Drop-off address12 Temple Road, Flat 2, Kandy, Kandy, 20000
 Recipient Sunil Silva
 Size Medium
 Weight 2.5 kg
 Delivery feeLKR 700.00
```

What the run shows:

- **Real parcel and pricing.** The parcel was created by the customer through the real API with
  real pricing, and the receipt's fee (LKR 700) is the stored, priced fee.
- **Live updates through the gateway arrived in 120–160 ms,** well within NF-01's 2 s.
- **The courier assignment** (sequence 2) moved the sequence but added no event or timeline row.
- **Reconnect.** Reconnecting with `Last-Event-ID: 3` replayed exactly 4, 5 and 6, then the stream
  ended because the parcel is delivered.
- **Owner-only access.** History, receipt and rating were refused for another customer (403) and
  without a token (401). Ratings also rejected a duplicate (409) and an invalid score (400).
- **Masking.** The phone number and notes appear in neither the public view nor the receipt.

The browser UI was not driven. The frontend container serves the app shell for each route. Page
behaviour is covered by the frontend tests.

## Remaining work

| Item | Why | Owner |
| :--- | :--- | :--- |
| History filters beyond status (date range, tracking-number search) and a defined newest-first order (CU-05) | `GET /api/v1/parcels` is M1's endpoint and contract. Proposal in `docs/tracking-design.md` section 12, waiting for M1's agreement | M1, then M2 for the UI |
| Ingress & Helm chart | Bonus work for M2 (ADR 006) | M2 (bonus) |

## Environment note

Docker Compose v5.6.1 on this machine builds through bake and refused to read
`deploy/dockerfiles/*.Dockerfile` without an explicit `--allow=fs.read=...`. That happened with
the default builder and again with `COMPOSE_BAKE=false`. Building each image with
`docker build -f deploy/dockerfiles/<service>.Dockerfile -t deploy-<service> .` and starting with
`up -d --no-build` works. M9 may want to know in case others see the same message.

## Acceptance checklist (Issue #219)

- [ ] All SRS requirements in M2's scope are implemented. **Not complete:** CU-05 history filters
  beyond status need M1's contract change (proposal in section 12). Helm chart is bonus.
- [x] Authorization, validation, error handling and failure/retry behaviour are covered.
- [x] Relevant backend and frontend tests pass, with results recorded.
- [x] Integration with dependent services (auth, M1 parcel workflow, pricing, Kafka, gateway) is verified.
- [x] Documentation and evidence are updated.
- [x] A focused PR targeting `development` is opened and linked here.
