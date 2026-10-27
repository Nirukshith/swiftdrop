# SwiftDrop Integration Overview

**Author:** Member M2  
**Sources:** `deploy/nginx/nginx.conf`, `contracts/events/topics.md`, `contracts/openapi/*.yaml`, SRS sections 5 and 8.3

Solid arrows are **synchronous** HTTP calls (the caller waits for the answer). Dashed arrows are **asynchronous** Kafka events (the producer does not wait). Labels on Kafka edges are consumer group ids from `topics.md`.

```mermaid
flowchart LR
    browser["Angular portals<br/>(customer, merchant, courier, admin,<br/>public /track page)"]
    merchantsys["Merchant store systems"]
    merchanthook["Merchant webhook URLs"]

    subgraph edge["Gateway"]
        gateway["Nginx :8080<br/>blocks /internal/**"]
    end

    subgraph services["Spring Boot services"]
        auth["auth-service :8081<br/>M10 core, M3 reset, M4 admin,<br/>M7 security, M8 audit"]
        parcel["parcel-service :8082<br/>M1 parcel, address, internal<br/>M2 tracking, receipt"]
        ops["operations-service :8083<br/>M4 dispatch, M5 zones/pricing/reports,<br/>M8 courier.execution, M9 courier.profile"]
        integ["integration-service :8084<br/>M3 notification, assistant<br/>M6 merchant, M7 webhook"]
    end

    subgraph kafka["Kafka (KRaft, 1 broker)"]
        tCreated[["parcel.created.v1"]]
        tAssigned[["parcel.assigned.v1"]]
        tStatus[["parcel.status-changed.v1"]]
        tUser[["user.registered.v1"]]
    end

    mailpit["Mailpit SMTP<br/>(mock email)"]
    llm["Claude API"]

    %% ---------- Browser and gateway (synchronous) ----------
    browser -->|"HTTPS + Bearer JWT<br/>refresh cookie (ADR 004)"| gateway
    merchantsys -->|"client-credentials token<br/>/api/v1/merchant/**"| gateway
    gateway -->|"/api/v1/auth/**<br/>/api/v1/admin/users"| auth
    gateway -->|"/api/v1/parcels/**, /api/v1/addresses<br/>/api/v1/tracking/** (incl. SSE stream)"| parcel
    gateway -->|"/api/v1/dispatch/**, /api/v1/courier/**<br/>/api/v1/admin/zones, pricing-rules, reports"| ops
    gateway -->|"/api/v1/merchants/**, /api/v1/merchant/**<br/>/api/v1/admin/merchants, /api/v1/assistant/**<br/>/api/v1/notifications/**"| integ
    parcel -.->|"SSE text/event-stream<br/>(server push, long-lived)"| gateway

    %% ---------- Internal REST (synchronous, never through the gateway) ----------
    parcel -->|"POST /internal/v1/pricing/quote"| ops
    ops -->|"PUT /internal/v1/parcels/{id}/assignment<br/>PATCH /internal/v1/parcels/{id}/status<br/>GET /internal/v1/parcels"| parcel
    integ -->|"POST /internal/v1/parcels<br/>(merchant orders)"| parcel
    integ -->|"GET /internal/v1/users/{id}/contact<br/>POST /internal/v1/users, /internal/v1/clients"| auth
    parcel & ops & integ -->|"GET JWKS<br/>(JWT verification, cached)"| auth

    %% ---------- Outbound (synchronous) ----------
    integ -->|"SMTP"| mailpit
    integ -->|"tool-use API"| llm
    integ -->|"signed webhook POST<br/>(HMAC-SHA256, retried)"| merchanthook

    %% ---------- Kafka events (asynchronous) ----------
    parcel -.->|publish| tCreated
    parcel -.->|publish| tAssigned
    parcel -.->|publish| tStatus
    auth -.->|publish| tUser

    tCreated -.->|"integration-notification"| integ
    tCreated -.->|"operations-reports"| ops
    tAssigned -.->|"integration-notification"| integ
    tAssigned -.->|"operations-workload"| ops
    tStatus -.->|"parcel-tracking (history)<br/>parcel-tracking-sse-{instance} (live)"| parcel
    tStatus -.->|"integration-notification<br/>integration-webhook"| integ
    tStatus -.->|"operations-reports<br/>operations-workload"| ops
    tUser -.->|"integration-notification"| integ
    tUser -.->|"operations-courier"| ops
```

## Synchronous calls

| Caller | Callee | Call | Purpose |
| :--- | :--- | :--- | :--- |
| Browser | Nginx | `/api/v1/**` | Every portal call goes through the gateway |
| Nginx | parcel-service | `GET /api/v1/tracking/{trackingNo}/stream` | Long-lived SSE response (server push over one HTTP request) |
| parcel-service | operations-service | `POST /internal/v1/pricing/quote` | Fee before a parcel is created (CU-03) |
| operations-service | parcel-service | `PUT /internal/v1/parcels/{id}/assignment` | Dispatcher assigns a courier (M4) |
| operations-service | parcel-service | `PATCH /internal/v1/parcels/{id}/status` | Courier status steps (M8, M9) and re-queue (M4) |
| operations-service | parcel-service | `GET /internal/v1/parcels` | Unassigned queue and courier's daily list |
| integration-service | parcel-service | `POST /internal/v1/parcels` | Merchant order creation (M6) |
| integration-service | auth-service | `GET /internal/v1/users/{id}/contact`, `POST /internal/v1/users`, `POST /internal/v1/users/{id}/activate`, `POST /internal/v1/clients` | Notification contact lookup (M3); merchant user, activation and credentials (M6) |
| all resource servers | auth-service | JWKS | Verify JWT signatures (cached keys) |
| integration-service | Mailpit, Claude API, merchant URLs | SMTP, HTTPS | Email, assistant, webhooks |

## Asynchronous events

| Topic | Producer | Consumer groups |
| :--- | :--- | :--- |
| `parcel.created.v1` | parcel-service | `integration-notification`, `operations-reports` |
| `parcel.assigned.v1` | parcel-service | `integration-notification`, `operations-workload` |
| `parcel.status-changed.v1` | parcel-service | `parcel-tracking`, `parcel-tracking-sse-<instance>` (proposed, see `docs/tracking-design.md` 4.2), `integration-notification`, `integration-webhook`, `operations-reports`, `operations-workload` |
| `user.registered.v1` | auth-service | `integration-notification`, `operations-courier` |

Each topic also has `.retry` and `.dlt` topics for the consumers that use `@RetryableTopic` (`topics.md` section 2).

## Notes

- `operations-workload` on `parcel.status-changed.v1` comes from SRS 8.3 and ADR 005 (decrement on `DELIVERED`, `FAILED`, `CANCELLED`); `topics.md` lists only `operations-reports` for that topic.
- The gateway routes are drawn as the contracts define them (`/api/v1/...`). Today's `deploy/nginx/nginx.conf` still uses some unprefixed paths (`/tracking`, `/addresses`, `/courier`, `/admin/...`, `/merchant`, `/assistant`, `/notifications`); aligning them is M9's call (see `docs/tracking-design.md` section 5).
- The internal operations-service to operations-service `CourierDirectory` call (M4 to M9) is in-process, so it is not drawn; see `docs/diagrams/architecture.md`.
