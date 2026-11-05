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
