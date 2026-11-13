# SwiftDrop Week 8 M9 Release-Readiness Verification

**Issue:** #256
**Branch:** `fix/m9-week8-release-readiness`
**Base commit:** `6901299456ff8d78667df332daadb9fcdc4c57ad`
**Verification date:** 2026-11-13 (Asia/Colombo)
**Environment:** Windows PowerShell, Docker Desktop 4.96.3, Docker Engine 29.9.2, Compose v5.6.1, Java 21.0.9, Node 22.18.0

## Scope

This report records final deployment and release-readiness verification for the
merged M9 platform work. PRs #292 and #295 are ancestors of the base commit.
No previous Week 4–7 implementation was rebuilt or duplicated.

The original checkout at `D:\swiftdrop\swiftdrop` was preserved with its
existing uncommitted frontend changes and untracked architecture guide. All
runtime resources used here belonged to the isolated Compose project
`swiftdrop-m9-week8`; existing containers, images, networks, and volumes were
not removed.

## Prerequisites and configuration

- Docker Desktop with Linux containers and at least 6 GB available memory.
- Java 21 and Maven for backend verification.
- Node 22 for frontend verification.
- GitHub CLI authenticated as `kavishanthn16-svg`.
- Copy `deploy/.env.example` to a local environment file only when local
  overrides are required. Do not commit secrets.

The repository Compose file uses the approved dedicated `172.30.0.0/24`
network, gateway address `172.30.0.254`, and parcel trusted-proxy value
`172.30.0.254/32`. The broad `172.16.0.0/12` trust range is not present.

The existing Docker host already had a `172.30.0.0/24` network, so the
isolated verification used an external, untracked override with subnet
`172.31.0.0/24`, gateway `172.31.0.254`, and matching trusted proxy
`172.31.0.254/32`. This temporary override is not part of the PR.

## Reproducible deployment commands

```powershell
$project = "swiftdrop-m9-week8"
$override = "D:\swiftdrop\m9-week7-compose.override.yml"

docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile infra config --quiet
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile core config --quiet
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile full config --quiet

docker compose -p $project -f deploy/docker-compose.yml -f $override `
  --env-file deploy/.env.example --profile full build

docker compose -p $project -f deploy/docker-compose.yml -f $override `
  --env-file deploy/.env.example --profile full up -d
```

All three repository profile configuration checks passed. The isolated full
image build passed for `auth-service`, `parcel-service`, `operations-service`,
`integration-service`, and `frontend`.

## Runtime results

The full profile started nine required runtime services:

- PostgreSQL 16 — healthy
- Kafka 3.8.1 — healthy
- Kafka topic initializer — exited 0
- Mailpit — healthy
- Angular frontend — healthy
- auth-service — healthy
- parcel-service — healthy
- operations-service — healthy
- integration-service — healthy
- Nginx gateway — healthy

Kafka topics included the four required base topics:

```text
parcel.created.v1
parcel.assigned.v1
parcel.status-changed.v1
user.registered.v1
```

Retry and DLT topics were also present for the event consumers.

## Gateway and security verification

Nginx syntax was verified inside the running gateway container:

```text
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

Observed gateway responses:

| Request | Status | Result |
|---|---:|---|
| `GET /health` | 200 | gateway health |
| `GET /` | 200 | Angular frontend |
| `GET /.well-known/jwks.json` | 200 | auth/JWKS routing |
| `POST /api/v1/auth/login` with `{}` | 400 | contract validation preserved |
| `OPTIONS /api/v1/auth/login` | 200 | CORS preflight |
| `GET /api/v1/parcels` | 401 | authorization preserved |
| `GET /api/v1/tracking/{unknown}` | 404 | public tracking reaches parcel-service |
| `GET /api/v1/tracking/{unknown}/stream` | 404 | SSE route reaches parcel-service; no fixture available |
| `GET /api/v1/addresses` | 401 | parcel routing |
| `GET /api/v1/dispatch/parcels` | 401 | dispatch routing |
| `GET /api/v1/admin/zones` | 401 | zones routing |
| `GET /api/v1/admin/pricing-rules` | 401 | pricing routing |
| `GET /api/v1/admin/reports` | 401 | reporting routing |
| `GET /api/v1/courier/me` | 401 | courier routing |
| `GET /api/v1/merchants/register` | 401 | merchant routing |
| `GET /api/v1/merchant/orders` | 401 | merchant order routing |
| `GET /api/v1/admin/merchants` | 401 | merchant administration routing |
| `GET /api/v1/notifications` | 401 | notification routing |
| `GET /api/v1/merchant/webhooks/settings` | 401 | webhook routing |
| `GET /api/v1/assistant/chat` | 401 | assistant routing |
| `GET /internal/v1/users` | 404 | internal routes blocked |

The running gateway resolved all service upstreams through Docker DNS. The
parcel container reported:

```text
SWIFTDROP_PRICING_QUOTE_URL=http://operations-service:8083/internal/v1/pricing/quote
SWIFTDROP_TRACKING_RATE_LIMIT_TRUSTED_PROXIES=172.31.0.254/32
```

Tracking preserves `Host` and `X-Forwarded-For`, uses HTTP/1.1, disables proxy
buffering, and has long read/send timeouts. No authorization bypass was used.

## Persistence and recovery

PostgreSQL reported the following databases before and after controlled
PostgreSQL restart:

```text
auth_db
integration_db
ops_db
parcel_db
postgres
swiftdrop
```

`operations-service` and PostgreSQL were restarted without deleting volumes.
After the normal startup interval, all four actuator endpoints returned HTTP
200 and the gateway returned HTTP 200. All nine Compose services were healthy.

Mailpit readiness returned HTTP 200 at `/readyz`. No destructive cleanup,
volume recreation, or existing deployment shutdown was performed.

## Automated verification

Backend:

```text
mvn -B verify -pl services/operations-service -am -DtrimStackTrace=false
BUILD SUCCESS
Tests run: 208, Failures: 0, Errors: 0, Skipped: 0
```

Frontend under Node 22.18.0:

```text
npm ci                         PASS
npm test -- --ci               PASS — 65 suites, 526 tests
npm run build                  PASS
```

The frontend install reported existing npm audit findings (37 vulnerabilities:
3 low, 33 moderate, 1 high). No dependency changes were made in this platform
verification PR.

## CI status

The completed GitHub Actions run for base commit
`6901299456ff8d78667df332daadb9fcdc4c57ad` was successful:

- Backend CI: success
- Frontend CI: success
- Docker image builds: success

The newer development push run was still in progress at the time of inspection
and is not used as evidence for this branch.

## Authenticated workflow status

Unauthenticated gateway, authorization, routing, health, persistence, and
security checks passed. Approved disposable credentials and fixtures for
customer, dispatcher, courier, merchant, and integration business workflows
were not available in the repository or supplied for this verification.

Accordingly, the following remain **BLOCKED**, not passed:

- authenticated customer parcel creation and tracking workflow;
- dispatcher courier availability and assignment workflow;
- courier profile, assigned deliveries, and delivery-action workflow;
- merchant API workflow;
- authenticated notification, webhook, and assistant workflow;
- end-to-end Kafka event-to-notification/email demonstration using business data.

Responsible owners: M10/auth for approved authentication fixtures and the
relevant feature owners M1–M8 for disposable business data. No fabricated JWT,
mock success response, or authorization bypass was used.

## Release checklist

- PASS — latest `development` integrated at `6901299456ff8d78667df332daadb9fcdc4c57ad`
- PASS — Compose `infra`, `core`, and `full` profiles validate
- PASS — all required images build
- PASS — full stack starts and reaches healthy status
- PASS — gateway routes and frontend are operational
- PASS — internal routes are blocked
- PASS — authentication/JWKS and authorization behavior preserved
- PASS — Kafka and Mailpit operational
- PASS — PostgreSQL persistence and controlled recovery verified
- PASS — operations Maven verification passes
- PASS — frontend tests and production build pass
- BLOCKED — authenticated customer/dispatcher/courier/merchant/integration workflows
- BLOCKED — complete business-level final demonstration pending approved fixtures

Because authenticated acceptance items remain unavailable, the PR should use
`Refs #256`, not `Closes #256`, and Issue #256 should remain open.
