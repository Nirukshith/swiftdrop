# M2 Week 4 — Issue #164 Tracking Integration Verification

## Scope

This verification covers M2's live tracking against real parcel status events:

- M1's status workflow publishing `parcel.status-changed.v1` after commit
- the `tracking` package consuming it, storing changes in `tracking_event`, and pushing them over SSE
  (`parcel-read.yaml` `/api/v1/tracking/{trackingNo}/stream`)
- the public masked lookup and the owner-protected history

Code changes are limited to the M2 `tracking` package and its tests. M1's `ParcelQueryService`
interface, the contracts, ADRs, gateway and other members' code are unchanged.

## Change found by the integration

Since #188, M1 writes one `status_history` row per courier assignment or reassignment:
`CREATED -> CREATED`, with the courier ids in the reason. `TrackingMapper` treated every
`CREATED -> CREATED` row as the parcel's creation. As a result:

- the public timeline and the customer's timeline showed "Created" twice
- the customer's history showed the internal courier ids

`TrackingMapper.statusChanges` now keeps the first `CREATED -> CREATED` row as the creation. Later
rows that don't change the status are left out of both views. Real changes are kept, including a
re-queue (`FAILED -> CREATED`). The live stream was not affected, because its events carry only
status and time.

## Automated verification

Command:

```text
mvn -B verify -pl services/parcel-service -am
```

Result:

```text
common:          13 tests passed
parcel-service:  208 tests passed (0 failures, 0 errors, 0 skipped)
BUILD SUCCESS
```

`TrackingEndToEndIntegrationTest` is new. It uses Testcontainers PostgreSQL and Kafka, real HTTP and
SSE clients, and bearer tokens signed with the dev key. Nothing between the status change and the
client is hand-built: M1's internal endpoint makes the change, and M1's listener publishes it after
commit. It covers:

- **Real changes reach the stream.** Assignment, then PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY and
  DELIVERED. Each arrives as a `status` event in the contract format: `id` equals the sequence, the
  data has exactly 4 fields, and no customer, courier or address data appears. The sequence
  increases with every change. The stream ends after DELIVERED, and every change is stored for
  replay.
- **Stale and repeated events.** Old or repeated events published after real changes never reach
  the client and never move it backwards. Only the next real change arrives.
- **Disconnect and reconnect.** Changes made while the client is gone are replayed once, in order,
  on reconnect with `Last-Event-ID`. Live updates continue afterwards.
- **Public lookup.** It shows the real status and stored times, masked. An expired or broken token
  sent by a signed-in browser is ignored, and the masked view is the same for everyone.
- **History with signed tokens.**

  | Caller | Result |
  | :--- | :--- |
  | Owner | 200 |
  | Assigned courier | 200 |
  | ADMIN | 200 |
  | Another customer | 403 |
  | Another courier | 403 |
  | No token | 401 |
  | Invalid token | 401 |

  There is one row per status change, and courier ids don't appear.

`TrackingMapperTest` adds a case for assignment and reassignment rows.

Frontend (unchanged code):

```text
cd frontend && npm ci && npm test -- --ci && npm run build
Test Suites: 62 passed, 62 total
Tests:       425 passed, 425 total
Application bundle generation complete.
```

## Live end-to-end run (Docker, real parcel status change)

Date: 2026-11-12. The Docker core stack was built from this branch:

```text
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d --build
```

Customer calls and SSE go through the gateway (`http://localhost:8080`). Status changes use
parcel-service's internal endpoint on port 8082, as couriers' and dispatchers' services do. The
gateway answers `404` for `/internal`.

Local-only setup (not committed):

- A compose override sets parcel-service's `SWIFTDROP_PRICING_QUOTE_URL` to
  `http://operations-service:8083/...`. Compose points it at `integration-service:8083`, but the
  quote endpoint is in operations-service. See "Known gaps".
- Demo zones and pricing rules were added to `ops_db`, because no admin user is seeded.
- **Customer parcel creation is blocked outside M2.** `POST /api/v1/parcels` with customer A's
  real token returned `401`, because operations-service rejects parcel-service's unauthenticated
  quote call. So customer A's parcel row was inserted into `parcel_db` in the state the create
  endpoint stores: `CREATED`, sequence 1, one creation history row, and owned by customer A's real
  user id (the token's `sub`).

Everything after that used the real system: registration and login in auth-service, M1's status
workflow, Kafka, the tracking consumers, SSE through nginx, and JWT validation via JWKS.

Recorded output (tokens shortened):

```text
### 1. Register and log in two customers (auth-service via gateway)
customer A token: eyJraWQiOiJzd2lm...  customer B token: eyJraWQiOiJzd2lm...

### 2. Customer A creates a parcel (POST /api/v1/parcels via gateway)
create response: {"timestamp":"2026-11-12T00:03:52.478+00:00","status":401,"error":"Unauthorized","path":"/api/v1/parcels"}
(blocked outside M2, see above; demo row inserted for customer A, user id usr-1eef2c2436)
parcel id=883293ea-63eb-4266-8250-959209d3f59b trackingNumber=SD-20261112-44B4E9

### 3. Public lookup without login (masked)
{"trackingNumber":"SD-20261112-44B4E9","status":"CREATED","recipientNameMasked":"S*** S***","dropoffDistrict":"Kandy","lastUpdatedAt":"2026-11-12T00:03:53.679564Z","timeline":[{"status":"CREATED","occurredAt":"2026-11-12T00:03:53.679564Z"}]}

### 5. Real status changes (M1 internal endpoint) while the stream is open
assign courier -> HTTP 200
PICKED_UP sequence=3
client disconnects
last event id seen by the client: 3
IN_TRANSIT sequence=4  (while disconnected)
OUT_FOR_DELIVERY sequence=5  (while disconnected)

### 6. Reconnect with Last-Event-ID: 3, then deliver
DELIVERED sequence=6
--- first connection (raw SSE) ---
retry:5000

id:1
event:status
data:{"trackingNumber":"SD-20261112-44B4E9","sequence":1,"status":"CREATED","occurredAt":"2026-11-12T00:03:53.679564Z"}

id:3
event:status
data:{"trackingNumber":"SD-20261112-44B4E9","sequence":3,"status":"PICKED_UP","occurredAt":"2026-11-12T00:03:56.274091474Z"}

--- after reconnect (raw SSE) ---
retry:5000

id:4
event:status
data:{"trackingNumber":"SD-20261112-44B4E9","sequence":4,"status":"IN_TRANSIT","occurredAt":"2026-11-12T00:03:58.766292Z"}

id:5
event:status
data:{"trackingNumber":"SD-20261112-44B4E9","sequence":5,"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-12T00:03:58.925055Z"}

id:6
event:status
data:{"trackingNumber":"SD-20261112-44B4E9","sequence":6,"status":"DELIVERED","occurredAt":"2026-11-12T00:04:03.166573074Z"}

### 7. Gateway blocks the internal endpoint
PATCH http://localhost:8080/internal/v1/parcels/{id}/status -> HTTP 404

### 8. Public lookup after delivery (masked, real times)
{"trackingNumber":"SD-20261112-44B4E9","status":"DELIVERED","recipientNameMasked":"S*** S***","dropoffDistrict":"Kandy","lastUpdatedAt":"2026-11-12T00:04:03.164624Z","timeline":[{"status":"CREATED","occurredAt":"2026-11-12T00:03:53.679564Z"},{"status":"PICKED_UP","occurredAt":"2026-11-12T00:03:56.272305Z"},{"status":"IN_TRANSIT","occurredAt":"2026-11-12T00:03:58.764473Z"},{"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-12T00:03:58.923920Z"},{"status":"DELIVERED","occurredAt":"2026-11-12T00:04:03.164624Z"}]}

### 9. Authenticated history access (GET /api/v1/parcels/{id}/history via gateway)
owner (customer A)  -> HTTP 200
other (customer B)  -> HTTP 403
no token            -> HTTP 401
broken token        -> HTTP 401
owner's history:
{"parcelId":"883293ea-63eb-4266-8250-959209d3f59b","trackingNumber":"SD-20261112-44B4E9","currentStatus":"DELIVERED","entries":[{"sequence":null,"fromStatus":null,"toStatus":"CREATED","occurredAt":"2026-11-12T00:03:53.679564Z","reason":"Parcel order created"},{"sequence":null,"fromStatus":"CREATED","toStatus":"PICKED_UP","occurredAt":"2026-11-12T00:03:56.272305Z","reason":null},{"sequence":null,"fromStatus":"PICKED_UP","toStatus":"IN_TRANSIT","occurredAt":"2026-11-12T00:03:58.764473Z","reason":null},{"sequence":null,"fromStatus":"IN_TRANSIT","toStatus":"OUT_FOR_DELIVERY","occurredAt":"2026-11-12T00:03:58.923920Z","reason":null},{"sequence":null,"fromStatus":"OUT_FOR_DELIVERY","toStatus":"DELIVERED","occurredAt":"2026-11-12T00:04:03.164624Z","reason":null}]}

### 10. Stored for replay (parcel_db.tracking_event, written by the Kafka consumer)
 sequence |   from_status    |    to_status     |          occurred_at
----------+------------------+------------------+-------------------------------
        3 | CREATED          | PICKED_UP        | 2026-11-12 00:03:56.274091+00
        4 | PICKED_UP        | IN_TRANSIT       | 2026-11-12 00:03:58.766292+00
        5 | IN_TRANSIT       | OUT_FOR_DELIVERY | 2026-11-12 00:03:58.925055+00
        6 | OUT_FOR_DELIVERY | DELIVERED        | 2026-11-12 00:04:03.166573+00
(4 rows)
```

What the run shows:

- **Event format.** Each status event has `id` equal to the sequence and exactly the four public
  fields.
- **The courier assignment** (sequence 2) moved the sequence but added no status event or timeline
  row.
- **Reconnect.** After the client disconnected with `Last-Event-ID: 3`, only the missed changes 4
  and 5 were replayed, then 6 arrived live.
- **Masking.** The public view never contained the address, phone, notes, fee, customer id or
  courier id.
- **History.** It matched the stored change times, with one row per status change.

## Known gaps (outside M2)

| Gap | Effect | Owner |
| :--- | :--- | :--- |
| operations-service requires authentication on `/internal/v1/pricing/quote`, but parcel-service calls it without credentials | `POST /api/v1/parcels` returns 401 in Docker for every customer | M5 / M1 |
| Compose sets parcel-service `SWIFTDROP_PRICING_QUOTE_URL` to `integration-service:8083`; the quote endpoint is in operations-service on 8083 | Pricing is unreachable even after the gap above is fixed | M9 / M1 |
| parcel-service's main security chain does not permit `/error` | An error from `/internal/v1/parcels/**` (for example 400 or 404) reaches callers as an empty `401` | M1 / M10 |
| `status_history` stores no ADR 005 sequence | `/history` entries keep `sequence: null`, as the contract allows. The stream sends the current state with its sequence at connect, so ordering is still correct | M1 |

The browser UI was not driven in this run. The pages read these same endpoints, the frontend uses
the real backend by default (mocks only run in the `mock` configuration), and the frontend tests
cover the timeline, stream states and reconnect.

## Acceptance checklist (Issue #164)

- [x] The tracking timeline shows real parcel status and times, not mock data (public lookup, history and stream in the run above).
- [x] SSE delivers status updates in the contract format and handles reconnect and disconnect safely.
- [x] Public tracking masks private customer and address data; authenticated history stays owner-protected.
- [x] Integration tests cover event-to-tracking updates, ordering and stale events, and access control.
- [x] An end-to-end run with a real parcel status change is recorded (customer creation blocked outside M2, see above).
- [x] M2 work stays in the tracking package; the parcel query interface is unchanged.
