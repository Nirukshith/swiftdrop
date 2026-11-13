# M2 Configuration and Operations — Tracking, History, Receipts, Ratings

What M2's part of SwiftDrop needs to run, how to configure it, and how to check that it works.
Design background is in [`tracking-design.md`](tracking-design.md); the demo is in
[`demo/m2-week8-tracking-demo.md`](demo/m2-week8-tracking-demo.md).

No secrets are listed here. Where a shared credential is involved, only the variable name is given;
its value comes from `deploy/.env` (copy of `deploy/.env.example`), which is not committed.

## 1. What M2 runs

All backend parts run inside **parcel-service** (port 8082, database `parcel_db`). There is no
separate M2 service.

| Part | Where | Endpoints and inputs |
| :--- | :--- | :--- |
| Public tracking | `tracking` package | `GET /api/v1/tracking/{trackingNo}` and `GET /api/v1/tracking/{trackingNo}/stream` (SSE), no login |
| Parcel history (timeline) | `tracking` package | `GET /api/v1/parcels/{id}/history` (owner customer, assigned courier, DISPATCHER, ADMIN) |
| Delivery rating | `tracking` package | `POST` and `GET /api/v1/parcels/{id}/rating` (owner submits; owner and ADMIN read) |
| PDF receipt | `receipt` package | `GET /api/v1/parcels/{id}/receipt` (owner customer and ADMIN, `DELIVERED` only) |
| Status-event consumer | `tracking` package | Kafka topic `parcel.status-changed.v1` (published by M1) |
| History list | M1's `GET /api/v1/parcels` | Used by M2's history page (filters: `status`, `from`, `to`, `q`) |

Frontend (Angular, `frontend/projects/web`):

| Page | Route | Owner folder |
| :--- | :--- | :--- |
| Public tracking (search) | `/track` | `pages/track.component.ts` |
| Public tracking (one parcel, live) | `/track/:trackingNo` | `pages/track.component.ts` |
| Customer dashboard | `/customer/dashboard` | `pages/customer-history/` |
| My parcels (history, filters) | `/customer/parcels` | `pages/customer-history/` |
| Parcel detail (live timeline, receipt, rating) | `/customer/parcels/:id` | `pages/tracking/` |

Contracts: `contracts/openapi/parcel-read.yaml` (M2) and `contracts/openapi/parcel.yaml` (M1, history list).

## 2. Settings

All M2 settings have working defaults, so a standard `docker compose ... up` needs no extra M2
configuration. Spring Boot reads each setting from `application.yml` or from an environment
variable (dots and dashes become underscores, upper case).

### Public tracking rate limit (`swiftdrop.tracking.rate-limit.*`)

| Setting | Environment variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `enabled` | `SWIFTDROP_TRACKING_RATE_LIMIT_ENABLED` | `true` | Per-IP limit on the public lookup and stream |
| `requests-per-minute` | `SWIFTDROP_TRACKING_RATE_LIMIT_REQUESTS_PER_MINUTE` | `30` | Steady rate per client IP |
| `burst` | `SWIFTDROP_TRACKING_RATE_LIMIT_BURST` | `10` | Requests allowed at once before the steady rate applies |
| `max-tracked-clients` | `SWIFTDROP_TRACKING_RATE_LIMIT_MAX_TRACKED_CLIENTS` | `10000` | Cap on remembered client IPs (bounded memory) |
| `trusted-proxies` | `SWIFTDROP_TRACKING_RATE_LIMIT_TRUSTED_PROXIES` | `127.0.0.1/32, ::1/128` | IPs or CIDR ranges whose `X-Forwarded-For` is trusted (the gateway) |

Over the limit, the caller gets `429` with `Retry-After` and the standard JSON error body.
**Behind the Docker gateway this needs the gateway's address in `trusted-proxies` and
`X-Forwarded-For` from nginx.** Until then all visitors share one limit; see §5.

### Live stream (`swiftdrop.tracking.stream.*`)

| Setting | Environment variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `heartbeat` | `SWIFTDROP_TRACKING_STREAM_HEARTBEAT` | `15s` | Interval of the `:heartbeat` comment that keeps proxies from closing idle streams |
| `timeout` | `SWIFTDROP_TRACKING_STREAM_TIMEOUT` | `30m` | Stream lifetime; the browser then reconnects with `Last-Event-ID` |
| `reconnect-delay` | `SWIFTDROP_TRACKING_STREAM_RECONNECT_DELAY` | `5s` | The `retry:` value sent at connect |
| `max-open-streams` | `SWIFTDROP_TRACKING_STREAM_MAX_OPEN_STREAMS` | `500` | Open streams per instance; above it new streams get `503` |

### Kafka consumers

| Setting | Environment variable | Default | Meaning |
| :--- | :--- | :--- | :--- |
| `swiftdrop.tracking.kafka.enabled` | `SWIFTDROP_TRACKING_KAFKA_ENABLED` | on when `spring.kafka.bootstrap-servers` is set | Starts the two tracking consumers |
| `swiftdrop.tracking.broadcast-group-id` | `SWIFTDROP_TRACKING_BROADCAST_GROUP_ID` | `parcel-tracking-sse-<HOSTNAME>` | Consumer group of the live broadcaster; must be unique per instance |

The consumers:

| Consumer | Group | Starts at | On failure |
| :--- | :--- | :--- | :--- |
| History writer: stores each change in `tracking_event` for replay | `parcel-tracking` (shared by all instances) | `earliest` | 5 attempts with 1, 2, 4 and 8 s back-off, then `parcel.status-changed.v1.dlt`; unusable messages go to the DLT at once |
| Live broadcaster: pushes changes to this instance's open streams | one group per instance | `latest` | Skips unusable messages; a missed push is recovered by `Last-Event-ID` replay |

### Shared settings parcel-service needs (owned by other members)

M2 relies on these, but they're not M2's to change. Names only:

| Variable | What for | Owner |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | `parcel_db` | M9 / M1 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Status events (`kafka:9092` in Compose) | M9 |
| `JWKS_URL` | Validating login tokens from auth-service | M10 |
| `SWIFTDROP_PRICING_QUOTE_URL`, `INTERNAL_PRICING_SERVICE_TOKEN` | Pricing for parcel creation (M1's flow; needed to create parcels in a demo) | M1 / M5 / M10 |

### Receipts

No settings. The fonts for Sinhala and Tamil text (Noto Sans Sinhala and Noto Sans Tamil, Regular
and Bold, SIL Open Font License) are bundled in
`services/parcel-service/src/main/resources/com/swiftdrop/parcel/receipt/fonts/` with `OFL.txt`.
At the first receipt they're copied to temporary files, because OpenPDF's text shaping reads fonts
from a file path. The temporary directory must be writable, which it is in the default container.
Receipts are generated per request and never stored.

### Database

| Table | Migration | Written by | Notes |
| :--- | :--- | :--- | :--- |
| `tracking_event` | `V3__create_tracking_event.sql` | History writer (Kafka) | `event_id` primary key, so redelivered events are stored once |
| `parcel_rating` | `V4__create_parcel_rating.sql` | Rating endpoint | `parcel_id` primary key: one rating per parcel, also under races; `score` 1–5 enforced by the database |

Flyway runs the migrations when parcel-service starts. M2 reads parcels only through M1's
`ParcelQueryService`, never from M1's tables directly.

## 3. Gateway requirements (nginx, owned by M9)

The Docker gateway (`deploy/nginx/nginx.conf`) must route:

- `/api/v1/tracking/**` to parcel-service with **buffering off** and a long read timeout (1 h), so
  SSE events are flushed at once. This is in place.
- `/api/v1/parcels/**` to parcel-service. In place.
- `/` to the frontend container, which serves the Angular app. In place.

Still needed for the per-visitor rate limit (§5, item 1): in `location ^~ /api/v1/tracking`,
`proxy_set_header Host $host;` and `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`.
**Both lines are needed**; with only the second, Tomcat returns 400. parcel-service also needs
`SWIFTDROP_TRACKING_RATE_LIMIT_TRUSTED_PROXIES` set to the gateway's address or network.

## 4. Run and check

```bash
cp deploy/.env.example deploy/.env      # once; holds local credentials, never committed
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d --build
```

If `--build` fails with `additional privileges requested: pass "--allow=fs.read=..."` (Docker
Compose 5.x), build the images one by one, then start without building:

```bash
for svc in parcel-service auth-service operations-service integration-service frontend; do
  docker build -f deploy/dockerfiles/$svc.Dockerfile -t deploy-$svc .
done
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d --no-build
```

Health and quick checks:

```bash
curl http://localhost:8080/health                                   # gateway: ok
curl http://localhost:8082/actuator/health                          # parcel-service: {"status":"UP",...}
curl http://localhost:8080/api/v1/tracking/SD-20991231-NOPE00       # 404 RESOURCE_NOT_FOUND JSON
```

Full checks, against the running stack:

```bash
bash docs/m2-e2e/run.sh                     # 35 API checks through the gateway (bash, curl, node, docker)
node docs/m2-e2e/browser-walkthrough.mjs    # 32 checks in a real browser (Edge or Chrome; set BROWSER=... if not found)
```

Automated tests:

```bash
mvn -B verify -pl services/parcel-service -am                        # Java 21, Docker for Testcontainers
cd frontend && npm ci && npm test -- --ci && npm run build           # Node 22
```

Frontend on its own (`ng serve`): `frontend/proxy.conf.json` forwards `/api/v1/tracking`,
`/api/v1/parcels` and the other APIs to the local services. `ng serve --configuration mock` runs
the pages without a backend (MSW mocks); it is never used for evidence.

### Operating notes

- **Logs to watch** (`docker logs deploy-parcel-service-1`):
  - `Ignored unusable status-changed event`: a bad message on the topic; live tracking continues.
  - `Receipt PDF generation failed`: the receipt endpoint returned `500 RECEIPT_GENERATION_FAILED`.
    Only the exception type is logged, never parcel data.
  - Rating logs show the parcel id and score only, never the comment.
- **Dead-letter topic:** messages on `parcel.status-changed.v1.dlt` were not stored for replay.
  M3's notification consumer uses the same DLT (topics.md); the `kafka_dlt-original-consumer-group`
  header tells whose message it is.
- **Scaling:** each parcel-service instance holds its own streams (at most 500). Every instance
  receives every status change through its own broadcaster group, so any instance can serve any
  stream. Replays come from the shared `tracking_event` table.

## 5. Known limitations

| # | Limitation | Effect | Workaround or owner |
| :--- | :--- | :--- | :--- |
| 1 | The gateway sends no `X-Forwarded-For` on `/api/v1/tracking`, and the gateway isn't a trusted proxy | In Docker, all public-tracking visitors share one rate limit (30/min, burst 10) | Fix in §3 (M9). Step 10 of `run.sh` shows whether it's fixed. For a demo, keep public lookups under 10 a minute, or set `SWIFTDROP_TRACKING_RATE_LIMIT_ENABLED=false` locally |
| 2 | Docker Compose 5.x refuses `--build` on this setup | `up --build` fails | Build images one by one (§4) |
| 3 | The shared app header's nav row doesn't wrap | At 360 px the whole page is 418 px wide (sideways scroll); the M2 page content itself fits | App shell owner (`app.html`) |
| 4 | `%` and `_` in the history search act as wildcards | A search for `%` matches all of the customer's own parcels | Harmless (own parcels only); M1 could escape them |
| 5 | `status_history` (M1) stores no event sequence | `/history` entries have `sequence: null` (the contract allows it) | None needed; the stream sends the current sequence at connect |
| 6 | No admin user is seeded, so zones and pricing rules can't be created through the API on a fresh database | Customer parcel creation fails until pricing exists | The demo scripts insert demo pricing rows for Colombo and Kandy into `ops_db` |
| 7 | Kubernetes Ingress / Helm chart for M2 | Not delivered | Bonus work for M2 under ADR 006 |
