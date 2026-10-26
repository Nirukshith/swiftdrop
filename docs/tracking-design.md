# SwiftDrop Tracking, History, Receipt & Rating Design

**Author:** Member M2 (Customer: tracking, history & receipts)  
**Target Service:** `parcel-service` (Port 8082, Database: `parcel_db`)  
**Packages:** `com.swiftdrop.parcel.tracking`, `com.swiftdrop.parcel.receipt`  
**Related Contract:** `contracts/openapi/parcel-read.yaml`  
**Depends on:** M1 `contracts/openapi/parcel.yaml` and `docs/parcel-state-machine.md`, `contracts/events/topics.md`, ADR 002, 004, 005, 006  
**Requirements:** CU-04 (live tracking, SSE), CU-05 (history, PDF receipt), CU-06 (public tracking page), CU-07 rating (+), NF-01 (update within 2 s), NF-04 (usable at 360 px)

---

## 1. Overview & Responsibilities

M2 owns the read side of the parcel lifecycle inside `parcel-service`. M1 owns the parcel aggregate, its state machine and every write; M2 never changes parcel status.

| Capability | Endpoint | Priority (ADR 006) |
| :--- | :--- | :--- |
| Public masked tracking | `GET /api/v1/tracking/{trackingNo}` | Must |
| Live tracking stream (SSE) | `GET /api/v1/tracking/{trackingNo}/stream` | Must |
| Status history (timeline) | `GET /api/v1/parcels/{id}/history` | Must |
| PDF receipt | `GET /api/v1/parcels/{id}/receipt` | Must |
| Delivery rating | `POST` / `GET /api/v1/parcels/{id}/rating` | Should (+), deferred |

The customer's list of deliveries (the history table on the dashboard) uses M1's `GET /api/v1/parcels`; M2 builds the page, not a second list endpoint.

---

## 2. Public Page Masking

The public page needs no login, so anyone who knows or guesses a tracking number sees it. It shows only what helps someone waiting for a parcel.

| Field | Public page | Why |
| :--- | :--- | :--- |
| `trackingNumber` | Shown | The caller already has it |
| `status`, `lastUpdatedAt` | Shown | The purpose of the page |
| Timeline (`status`, `occurredAt` per change) | Shown | Progress is the purpose of the page |
| Recipient name | **Masked**: `S*** S***` | Lets the recipient recognise their parcel without revealing the name |
| Drop-off `district` | Shown | Coarse (one of 25 districts, ADR 002), confirms the destination |
| Recipient phone | **Never** | Personal contact data |
| Street (`line1`, `line2`), `city`, `postalCode` | **Never** | Locates a home |
| Pickup address, sender, customer or merchant identity | **Never** | Not needed by the recipient |
| Courier ID or name | **Never** | Staff privacy |
| Failure or cancellation `reason` | **Never** | Cancellation reasons are free text and may hold personal data (Q4) |
| Fee, weight, size, notes, proof, internal IDs, `sequence` in the JSON body | **Never** | Internal or commercial data |

**Masking rule (proposal, Q3):** split the name on spaces and replace each part with its first letter followed by exactly three `*`. A fixed `***` hides the name length. `Sunil Silva` becomes `S*** S***`; `Kavindu` becomes `K***`.

**Implementation rule:** masking is done by building a separate `PublicTrackingResponse` DTO from an allow-list of fields, never by nulling fields on the full parcel DTO. A field added to `ParcelResponse` later cannot leak by accident.

**Probing:** an unknown and a malformed tracking number both return the same `404` body, so responses never reveal which numbers exist. The number has 6 random base-36 characters per day (about 2.2 billion combinations), and gateway rate limiting (section 6) makes guessing impractical.

The authenticated parcel detail page shows the full addresses and phone number from M1's `GET /api/v1/parcels/{id}`; masking applies to the public views only.

---

## 3. Live Tracking with Server-Sent Events

### 3.1 Why SSE
Tracking is one-way (server to browser), so SSE fits better than WebSockets: it is plain HTTP through Nginx, the browser's `EventSource` reconnects by itself, and Spring MVC supports it with `SseEmitter` (SRS section 4).

### 3.2 Stream protocol
`GET /api/v1/tracking/{trackingNo}/stream` returns `text/event-stream`:

```text
retry: 5000                       <- once, at connect: reconnect after 5 s

id: 4                             <- ADR 005 per-parcel sequence
event: status
data: {"trackingNumber":"SD-20261026-A1B2C3","sequence":4,"status":"OUT_FOR_DELIVERY","occurredAt":"2026-10-26T09:30:00Z"}

: heartbeat                       <- comment every 15 s, ignored by EventSource
```

- **Event id = `sequence`.** ADR 005 makes `sequence` monotonic per parcel, so it is a natural SSE id. It is not contiguous (assignment also increments it, and assignment is not a status change), so the client never treats a gap as a lost event.
- **Initial state.** At connect the server sends one `status` event with the current state, so the page does not need a separate first fetch.
- **Heartbeats.** A `: heartbeat` comment every 15 seconds stops Nginx, the Kubernetes ingress and browsers from closing an idle connection, and shows the server that a dead client has gone (the write fails and the emitter is removed).
- **End of stream.** The server completes the stream when the parcel reaches `DELIVERED` or `CANCELLED`. `FAILED` keeps it open because a dispatcher can re-queue the parcel to `CREATED`. The emitter timeout is 30 minutes; the browser then reconnects automatically.
- **Payload.** Events use the same masked fields as the public lookup, so the stream needs no login and has no ADR 004 problem.

### 3.3 Reconnect with `Last-Event-ID`
When the connection drops, `EventSource` reconnects after `retry` ms and sends the `Last-Event-ID` header with the last id it received.

1. The server reads `Last-Event-ID` (missing or unparseable means "send current state").
2. It loads from `tracking_event` every row for the parcel with `sequence > Last-Event-ID`, oldest first, and sends each as a `status` event.
3. It then registers the emitter for live events.

Between steps 2 and 3, a live event can arrive that was also replayed. The client therefore keeps the highest `sequence` it has applied and ignores any event that is not greater. This also covers duplicates caused by the race in section 4.3.

### 3.4 Emitter registry (per instance)
`TrackingEmitterRegistry` holds `Map<String trackingNumber, Set<SseEmitter>>` in memory (`ConcurrentHashMap` plus a concurrent set). Emitters are removed on `onCompletion`, `onTimeout` and `onError`. One `@Scheduled` task sends the heartbeat comment to every emitter every 15 seconds. To protect a single laptop (NF-02), each instance caps open streams (proposal: 500 in total, enforced in the registry with `503`) on top of the per-IP gateway limit.

### 3.5 Signed-in pages use the same stream
`EventSource` cannot send an `Authorization` header, and ADR 004 forbids putting the access token in storage or in the URL. The signed-in parcel detail page therefore:
1. loads full details from M1's `GET /api/v1/parcels/{id}` and the timeline from `GET /api/v1/parcels/{id}/history` (bearer token through `shared-auth`), then
2. opens the **public** stream for its `trackingNumber`, which carries only status and time.

So no authenticated SSE endpoint is needed. If the team later wants private data in the stream, the options are a fetch-based SSE client that can send the bearer header, or a short-lived single-use stream ticket; this is open question Q7.

---

## 4. Kafka Consumers

The tracking package consumes `parcel.status-changed.v1` (key `parcelId`, envelope from `common.EventEnvelope`) with **two listeners that do different jobs**.

### 4.1 History writer: shared group `parcel-tracking`
- Group id `parcel-tracking`, exactly as listed in `contracts/events/topics.md`. With several instances, Kafka gives each partition to one instance, so each event is written **once**.
- Inserts a row into `tracking_event` and records `eventId` in `processed_event` in the same transaction; a repeated `eventId` is skipped (ADR 005, topics.md section 4).
- An event whose `sequence` is not greater than the stored maximum for that parcel is stored for audit but is not treated as the current status (out-of-order protection, ADR 005).
- Uses `@RetryableTopic` with the team policy (5 attempts, 1 s to 16 s, then `parcel.status-changed.v1.dlt`).

### 4.2 Live broadcaster: a UNIQUE group per instance
A shared group cannot feed SSE. Each instance would receive only some partitions, so a browser connected to instance A would miss events for parcels whose partition went to instance B. Every instance must see **every** event, so each instance joins its own group:

```yaml
swiftdrop:
  tracking:
    broadcast-group-id: parcel-tracking-sse-${HOSTNAME:${random.uuid}}
```

```java
@KafkaListener(topics = "parcel.status-changed.v1",
               groupId = "${swiftdrop.tracking.broadcast-group-id}",
               properties = "auto.offset.reset=latest")
void broadcast(EventEnvelope event) { registry.publish(event); }
```

- `HOSTNAME` is the pod name in Kubernetes and the container id in Docker, so it is unique per instance; the random UUID covers a plain `java -jar` run.
- `auto.offset.reset=latest`: a new instance only needs events from now on. Anything older is in `tracking_event` and is replayed on reconnect.
- No retry topic and no DLT: pushing to a browser is best effort, and a missed push is recovered by `Last-Event-ID` replay.
- Old per-instance groups hold no data and are removed by the broker after `offsets.retention.minutes` (7 days by default).
- In-memory only: the broadcaster writes nothing to the database, so receiving each event on every instance cannot duplicate history rows.

### 4.3 Ordering between the two listeners
The broadcaster can push an event before the history writer has stored it. A client that disconnects in that window and reconnects may get the event twice (live, then replayed); the client-side "ignore if `sequence` is not greater" rule in 3.3 handles this.

### 4.4 Tables (`parcel_db`, Flyway, tracking package)

```sql
CREATE TABLE IF NOT EXISTS tracking_event (
    event_id       VARCHAR(36) PRIMARY KEY,
    parcel_id      UUID        NOT NULL,
    tracking_number VARCHAR(32) NOT NULL,
    sequence       BIGINT      NOT NULL,
    from_status    VARCHAR(32),
    to_status      VARCHAR(32) NOT NULL,
    reason         VARCHAR(500),
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tracking_event_parcel_seq ON tracking_event(parcel_id, sequence);
CREATE INDEX IF NOT EXISTS idx_tracking_event_tracking_no ON tracking_event(tracking_number, sequence);

CREATE TABLE IF NOT EXISTS parcel_rating (
    parcel_id   UUID PRIMARY KEY,            -- one rating per parcel
    customer_id UUID NOT NULL,
    score       SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

`event_id` as the primary key makes the history insert idempotent by itself. Whether the team keeps one shared `processed_event` table in `parcel_db` is for M1 and M2 to agree (Q8).

---

## 5. Nginx Settings to Request from M9

The current `deploy/nginx/nginx.conf` proxies `/tracking` with SSE settings, but the API path is **`/api/v1/tracking/`**, which today matches no `location` block. Request to M9:

```nginx
# in http { }
limit_req_zone  $binary_remote_addr zone=tracking_lookup:10m rate=30r/m;
limit_conn_zone $binary_remote_addr zone=tracking_streams:10m;

# in server { }  - SSE stream (more specific regex wins over the prefix block below)
location ~ ^/api/v1/tracking/[^/]+/stream$ {
    proxy_pass http://parcel_service;
    proxy_http_version 1.1;
    proxy_set_header Connection "";     # keep the upstream connection open
    proxy_buffering off;                # flush each event immediately (NF-01: < 2 s)
    proxy_cache off;
    gzip off;                           # compression buffers events
    proxy_read_timeout 1h;              # longer than the 15 s heartbeat and 30 min emitter timeout
    proxy_send_timeout 1h;
    limit_conn tracking_streams 5;      # max open streams per IP
    limit_conn_status 429;
}

# public lookup
location /api/v1/tracking/ {
    proxy_pass http://parcel_service;
    limit_req zone=tracking_lookup burst=10 nodelay;
    limit_req_status 429;
}
```

Also requested:
1. Replace or remove the existing `location /tracking` block. `/track/{trackingNo}` is the Angular public page (it is the link M3's notification emails use), and an API-style `/tracking` route at the root will clash with frontend routes once the gateway serves the SPA.
2. Return the shared `ErrorResponse` JSON for `429` (an `error_page 429` with a small JSON body plus a `Retry-After` header), so the frontend handles it like any other API error.
3. FYI, not M2's: M1's contract uses `/api/v1/addresses`, but nginx.conf routes `/addresses`.

`parcel-service` also sets `X-Accel-Buffering: no` and `Cache-Control: no-cache` on the stream response, so buffering stays off even if a proxy setting is missed. The same rules go into M2's Kubernetes Ingress later (`nginx.ingress.kubernetes.io/proxy-buffering: "off"`, `proxy-read-timeout: "3600"`, `limit-rpm`, `limit-connections`).

---

## 6. Rate Limiting on the Public Endpoints

| Endpoint | Limit (proposal, Q9) | Where | On exceed |
| :--- | :--- | :--- | :--- |
| `GET /api/v1/tracking/{trackingNo}` | 30 requests/min per IP, burst 10 | Nginx `limit_req` | `429` + `Retry-After` |
| `GET /api/v1/tracking/{trackingNo}/stream` | 5 open streams per IP | Nginx `limit_conn` | `429` |
| All streams on one instance | 500 open emitters | `TrackingEmitterRegistry` | `503` |

Limiting at the gateway rejects floods before they reach the JVM, and it costs no extra process. 30 per minute is far above what a person refreshing the page needs and far below what enumeration needs. The authenticated endpoints are not rate limited beyond the JWT check.

---

## 7. `ParcelQueryService` Methods Needed from M1

The tracking and receipt packages must not read M1's tables or entities directly (package isolation, SRS 8.2). M1 exposes a read-only, in-process interface in the `parcel` package:

```java
package com.swiftdrop.parcel.parcel;

public interface ParcelQueryService {

    /** Public tracking and SSE. No access check: the caller masks the result. Empty if unknown. */
    Optional<ParcelView> findByTrackingNumber(String trackingNumber);

    /**
     * History, receipt and rating. Applies the same access rule as GET /api/v1/parcels/{id}
     * (owner customer, assigned courier, DISPATCHER, ADMIN).
     * Throws the common not-found / access-denied exceptions, mapped to 404 / 403 by the
     * shared GlobalExceptionHandler.
     */
    ParcelView getAccessibleParcel(UUID parcelId, Authentication caller);
}
```

`ParcelView` is an immutable record with the same fields as M1's `ParcelResponse` (`id`, `trackingNumber`, `status`, `customerId`, `merchantId`, `courierId`, `pickupAddress`, `dropoffAddress`, `weightKg`, `size`, `fee`, `attemptCount`, `failureReason`, `recipientName`, `recipientPhone`, `notes`, `sequence`, `createdAt`, `updatedAt`), and not the JPA entity. Reusing M1's access rule keeps one definition of who may see a parcel. Receipt and rating apply the stricter rules in sections 8 and 9 on top of it.

For local development before M1 merges the implementation, M2 codes against this interface with an in-memory fake.

---

## 8. PDF Receipt

**Library: OpenPDF** (already the SRS choice, section 4). Reasons:
- **Licence:** LGPL/MPL, free for this project. iText 7 is AGPL, so the whole service would have to be AGPL.
- **Small and pure Java:** a single jar with no native code and no headless browser, so it fits the 4 GB RAM target (NF-02) and works the same in Docker.
- **Enough for a receipt:** tables, fonts and a logo with a simple API. Apache PDFBox is lower level and would need hand-drawn tables; HTML-to-PDF libraries (openhtmltopdf, Flying Saucer) add a template engine for one page.

**Content:** SwiftDrop header, tracking number, parcel id, creation date, current status, pickup and drop-off address (owner only, so not masked), recipient name, size and weight, fee in LKR (M5's quote currency), and the generation time. No phone number, proof photo or courier data.

**Access:** owner customer and ADMIN (`403` otherwise). Generated on request and streamed, never stored, with `Content-Disposition: attachment; filename="receipt-<trackingNumber>.pdf"`.

---

## 9. Delivery Rating (+)

Should-have, deferred until the Must-haves are done (ADR 006).
- Only the owner customer (`ROLE_CUSTOMER` and `customerId` match) may rate. Merchant parcels have no `customerId`, so they cannot be rated by customers.
- The parcel must be `DELIVERED` at the time of the request, otherwise `409 PARCEL_NOT_DELIVERED`.
- Once per parcel: `parcel_rating.parcel_id` is the primary key, so a second attempt (including a race) fails with `409 RATING_ALREADY_EXISTS`.
- The body (`score` 1-5, optional `comment` up to 500 characters) is a proposal (Q6).

---

## 10. Frontend Pages (M2)

| Page | Route (proposal) | Data |
| :--- | :--- | :--- |
| Public tracking | `/track/:trackingNo` (no guard; matches the email link) | Public lookup, then public stream |
| Customer dashboard | `/customer` | M1 `GET /api/v1/parcels` (recent) |
| Parcel detail with live timeline | `/customer/parcels/:id` | M1 detail, M2 history, public stream |
| History table | `/customer/parcels` | M1 `GET /api/v1/parcels` |
| Receipt download | button on detail and history | `GET /api/v1/parcels/{id}/receipt` as a blob through `shared-auth` |

The public page must work at 360 px width (NF-04).

---

## 11. Open Questions

| # | Question | Who |
| :--- | :--- | :--- |
| Q1 | The SRS gives the shared UI kit to M1 (8.1). Confirm that M1 builds it. | Team |
| Q2 | CU-05 asks for "history with filters", but M1's `GET /api/v1/parcels` only filters by `status`. Add `from` / `to` date filters and a tracking-number search there? | M1 |
| Q3 | Recipient name mask format (`S*** S***` proposed). | Team |
| Q4 | Show anything for `FAILED` on the public page, e.g. a fixed reason code like `RECIPIENT_UNAVAILABLE`, never free text? | Team |
| Q5 | Which statuses allow a receipt download (all, or all except `CANCELLED`)? Can DISPATCHER download it too? | Team |
| Q6 | Rating body: is `score` 1-5 plus an optional comment enough? Does anyone (M5 reports) consume ratings? | Team, M5 |
| Q7 | Is a private (authenticated) SSE stream needed, or is the public stream enough for signed-in pages (3.5)? | Team, M10 |
| Q8 | One shared `processed_event` table in `parcel_db`, or separate idempotency per package? | M1 |
| Q9 | Rate-limit numbers in section 6. | M9 |
| Q10 | `topics.md` lists only `parcel-tracking` for parcel-service. Add the per-instance `parcel-tracking-sse-<instance>` group (4.2) there; I did not edit it because it is outside this PR. | Contracts owner |
| Q11 | IDs: `parcel.yaml` uses `format: uuid` but its examples use strings like `usr-cust-901`. This PR follows `format: uuid`. | M1 |
