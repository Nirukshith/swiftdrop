# M9-01 Docker Core Stack and Gateway Verification

Date: 2026-11-05  
Branch: `fix/m9-week3-core-gateway-verification`  
Base: `b73191dc0eda7f6438e70c08f7ee8caeb0cfc67f`  
Gateway: `http://localhost:8080`

## Runtime result

The documented core profile built and started successfully with the local
`deploy/.env`. PostgreSQL 16, Kafka 3.8.1, Mailpit, all four Spring Boot
services, and Nginx 1.27 are running. The gateway health check required an
IPv4 loopback target: the container listens on IPv4 port 80, while
`wget http://localhost/health` resolved to an unavailable IPv6 loopback.

Kafka initialization completed and the required topics were present:

- `parcel.created.v1`
- `parcel.assigned.v1`
- `parcel.status-changed.v1`
- `user.registered.v1`

The PostgreSQL instance contained the four isolated databases:
`auth_db`, `parcel_db`, `ops_db`, and `integration_db`.

| Component | Health | Gateway route | Result |
|---|---|---|---|
| auth-service | Actuator `200`, `UP`; Compose healthy | `POST /api/v1/auth/login` | PASS: route reached auth; registration `201`, login `200` |
| parcel-service | Actuator `200`, `UP`; DB `UP`; Compose healthy | `GET /api/v1/tracking/UNKNOWN`, `GET /api/v1/addresses` | PASS: contract paths reached parcel; expected `404`/auth response |
| operations-service | Actuator `200`, `UP`; DB `UP`; Compose healthy | `GET /api/v1/dispatch/parcels`, `GET /api/v1/admin/zones` | PASS: routes reached operations; auth responses returned |
| integration-service | Actuator `200`, `UP`; DB `UP`, Mailpit `mailpit:1025` `UP`; Compose healthy | `GET /api/v1/notifications` | PASS: route reached integration and returned its auth response |

## Gateway and security checks

| Request | Expected | Actual | Result |
|---|---:|---:|---|
| `GET /health` | 200 | 200 | PASS |
| `GET /internal/v1/users` | 404 at gateway | 404 | PASS |
| Valid CUSTOMER token to `/api/v1/dispatch/parcels` | 403 | 403 | PASS: role rejected |
| Invalid token to `/api/v1/dispatch/parcels` | 401 | 401 | PASS |
| Valid CUSTOMER token to operations direct and gateway | Same backend response | 403 / 403 | PASS: forwarding and Host handling consistent |
| Valid CUSTOMER token to parcel/integration direct and gateway | Same backend response | 403 / 403 | PASS: routing consistent; backend auth configuration is a separate M10 handoff |

The temporary verification customer was created through the public contract;
no credentials or token values are recorded here. No valid DISPATCHER test
user was present in the database, so successful dispatcher authorization is
not claimed. The existing issue #93 JWKS URL, `DISPATCHER`-only dispatch
authorization, and `proxy_set_header Host $host` behavior were preserved.

Nginx validation passed:

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

Compose configuration validation passed, all four service images built
successfully, and the final `docker compose ... ps` state reported every
service healthy, including the gateway.

Automated verification passed with:

```text
mvn -B verify -pl services/auth-service,services/operations-service -am
BUILD SUCCESS
common: 13 tests, 0 failures
operations-service: 56 tests, 0 failures
auth-service: completed successfully
```

The Compose file uses explicit startup dependencies and memory limits. The
services retain Docker's default restart policy; restart behavior was not
changed because no reproducible restart defect was observed during this
verification.

## Changes

- Changed the gateway health probe from `localhost` to `127.0.0.1`.
- Added contract-aligned gateway prefixes for tracking, addresses, operations
  admin routes, merchant routes, assistant, and notifications.
- Preserved upstream addresses, Host forwarding, bearer forwarding, and
  `/internal/` blocking.

## Remaining blocker / handoff

Parcel and integration services return `403` for the valid CUSTOMER JWT both
directly and through the gateway. This is not a gateway defect and was not
changed because M9 must not modify M10/shared-auth or other service code.
M10 should verify the resource-server/JWT configuration for those services.


## Follow-up: Dispatcher Verification & Operations Parcel Compose Fix (2026-11-09)

### 1. Verification Context
- **Base Commit:** `c44196ae89b3fa00cef34aec7f6a57a4cde074d7` (`origin/development`, post PR #134 through PR #152).
- **Branch:** `fix/m9-week3-operations-parcel-compose`.
- **Worktree:** `D:\swiftdrop-m9-compose`.

### 2. Diagnosis of Initial HTTP 500 on Dispatch Endpoint
- **Symptom:** Authenticated DISPATCHER requests to `GET /api/v1/dispatch/parcels` returned HTTP `500 Internal Server Error` both through the Nginx gateway (port 8080) and directly against `operations-service` (port 8083).
- **Root Cause:** In `operations-service`, `RestParcelServiceClient` injects `@Value("${swiftdrop.services.parcel-service.url:http://localhost:8082}")`. Inside Docker, `localhost:8082` refers to the `swiftdrop-operations` container itself rather than the `swiftdrop-parcel` container. The connection attempt failed with `ClosedChannelException` and Spring `ResourceAccessException`.
- **M4 Approval:** M4 explicitly approved resolving this through a Compose-only configuration update by supplying `SWIFTDROP_SERVICES_PARCEL_SERVICE_URL: http://parcel-service:8082` and adding `parcel-service: condition: service_healthy` to `operations-service.depends_on`.

### 3. Applied Compose Configuration Correction
In `deploy/docker-compose.yml`:
```yaml
  operations-service:
    environment:
      ...
      SWIFTDROP_SERVICES_PARCEL_SERVICE_URL: http://parcel-service:8082
    depends_on:
      postgres:
        condition: service_healthy
      kafka-init:
        condition: service_completed_successfully
      parcel-service:
        condition: service_healthy
```

### 4. Compose Validation & Runtime Health
- **Compose Validation:** `docker compose -f deploy/docker-compose.yml config --quiet` exited with status `0` (VALID).
- **Container Re-creation:** `docker compose -f deploy/docker-compose.yml up -d --no-deps operations-service` recreated and started `swiftdrop-operations`.
- **Environment Verification:** Confirmed `SWIFTDROP_SERVICES_PARCEL_SERVICE_URL=http://parcel-service:8082` bound into `swiftdrop-operations` without exposing sensitive variables.
- **Inter-service Connectivity:** Confirmed `swiftdrop-operations` successfully reaches `http://parcel-service:8082/actuator/health` via Docker DNS.
- **Docker Core Stack Health:** All 8 containers operational and healthy:
  - `swiftdrop-postgres` (PostgreSQL 16, healthy on port 5434->5432)
  - `swiftdrop-kafka` (Apache Kafka 3.8.1, healthy on ports 9092, 29092)
  - `swiftdrop-mailpit` (Mailpit v1.32.0, healthy on ports 1025, 8025)
  - `swiftdrop-auth` (port 8081, Actuator health HTTP 200 / UP)
  - `swiftdrop-parcel` (port 8082, Actuator health HTTP 200 / UP)
  - `swiftdrop-operations` (port 8083, Actuator health HTTP 200 / UP)
  - `swiftdrop-integration` (port 8084, Actuator health HTTP 200 / UP)
  - `swiftdrop-gateway` (Nginx 1.27, port 8080->80, `/health` HTTP 200)

### 5. Dispatcher Gateway Authorization Matrix
Contract-defined dispatch endpoints re-tested after Compose fix:

| Test Case | Expected Status | Gateway (Port 8080) | Direct Service (Port 8083) | Assessment | Notes |
|---|---|---|---|---|---|
| Missing JWT on `/api/v1/dispatch/parcels` | 401 | 401 | 401 | PASS | Spring Security rejects unauthenticated access |
| Invalid JWT on `/api/v1/dispatch/parcels` | 401 | 401 | 401 | PASS | Malformed or untrusted signature rejected |
| CUSTOMER JWT on `/api/v1/dispatch/parcels` | 403 | 403 | 403 | PASS | Previously verified; `ROLE_CUSTOMER` lacks dispatcher authority |
| DISPATCHER JWT on `GET /api/v1/dispatch/couriers` | 200 | 200 | 200 | PASS | Returns workload list; validates gateway routing, JWKS auth, and role mapping |
| DISPATCHER JWT on `GET /api/v1/dispatch/parcels` | 200 | 200 | 200 | PASS | Returns `{"content":[],"totalElements":0,...}`; downstream parcel query succeeds |

### 6. Security & Gateway Integrity
- **Gateway Health:** `GET http://localhost:8080/health` returned HTTP `200` (PASS).
- **Internal Route Protection:** `GET http://localhost:8080/internal/v1/users` returned HTTP `404` at public gateway (PASS).
- **Direct Auth Security:** `POST http://localhost:8081/internal/v1/users` without internal token returned HTTP `401 Unauthorized` (PASS).
- **Host & Auth Header Forwarding:** Issue #93 fix (`proxy_set_header Host $host`) preserved; `Authorization` header forwarded intact.

### 7. Remaining Owner Dependencies
- **M1 Parcel Image (PR #150):** The running `swiftdrop-parcel` container predates PR #150 and returns 403 instead of 401 for unauthenticated calls. An image rebuild will be included in the normal deployment lifecycle.
