# M9 Week 4 Full-Stack Docker and Gateway Verification

Status: verified on 2026-11-12 (Asia/Colombo)

## Scope and baseline

- Issue: #171
- Branch: `feature/m9-week4-fullstack-docker-integration`
- Base: `3c6e1dc563333b1ccb7a78456002bcc50036873b`
- Environment: Windows PowerShell, Docker Compose, Java 21 images
- Gateway: `http://localhost:8080`

The existing `development` deployment structure is preserved. The only
configuration correction in this change is the parcel pricing destination:
the approved pricing contract is `GET /internal/v1/pricing/quote`, implemented
by `operations-service` on port 8083. The parcel container now uses
`http://operations-service:8083/internal/v1/pricing/quote`.

## Compose profiles

The current repository does not define an Angular container. Therefore `full`
contains the same backend and infrastructure services as `core`; it does not
implicitly add the frontend.

| Profile | Expected services |
|---|---|
| `infra` | `postgres`, `kafka`, `kafka-init`, `mailpit` |
| `core` | Infrastructure plus `auth-service`, `parcel-service`, `operations-service`, `integration-service`, `gateway` |
| `full` | Same services as `core` in the current repository |

Configuration commands:

```powershell
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile infra config --quiet
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile core config --quiet
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile full config --quiet
```

## Startup and shutdown

Use a unique Compose project name for disposable verification resources. Do not
remove the normal `postgres_data` or `kafka_data` volumes.

```powershell
$project = "swiftdrop-m9-week4-$([guid]::NewGuid().ToString('N').Substring(0,8))"
docker compose -p $project -f deploy/docker-compose.yml --env-file deploy/.env --profile core up -d --build
docker compose -p $project -f deploy/docker-compose.yml --env-file deploy/.env --profile core ps
docker compose -p $project -f deploy/docker-compose.yml --env-file deploy/.env --profile core down
```

The Compose file deliberately does not set fixed container names, so the
project name namespaces containers, networks, and named volumes. Existing
containers and volumes are not removed by this procedure.

## Health semantics

| Check | Meaning | Limitation |
|---|---|---|
| `GET /health` on port 8080 | Nginx liveness | Does not prove downstream readiness |
| `GET /actuator/health` on ports 8081-8084 | Spring Boot application readiness, including configured health contributors | Direct service ports are internal deployment surfaces, not public gateway routes |
| PostgreSQL Compose healthcheck | PostgreSQL accepts readiness probes | Does not prove every Flyway migration or query succeeds |
| Kafka Compose healthcheck | Kafka broker API is available | Does not prove application consumer processing |
| `kafka-init` completion | Required base topics were created idempotently | Does not prove every consumer is subscribed |
| Mailpit Compose healthcheck | Mailpit readiness endpoint responds | Does not prove a particular message was delivered |

Actuator details remain private; the public gateway exposes only its Nginx
liveness endpoint.

## Gateway route matrix

The gateway preserves the request URI and forwards the `Authorization` header.
The following matrix is verified against controller mappings and merged
OpenAPI contracts; method-specific smoke results are recorded below.

| Service | Contract/controller route families | Upstream |
|---|---|---|
| Auth | `/.well-known/jwks.json`, `/api/v1/auth/**`, `/api/v1/admin/users/**` | auth-service:8081 |
| Parcel | `/api/v1/parcels/**`, `/api/v1/tracking/**`, `/api/v1/addresses/**` | parcel-service:8082 |
| Operations | `/api/v1/dispatch/**`, `/api/v1/courier/**`, `/api/v1/admin/zones/**`, `/api/v1/admin/pricing-rules/**`, `/api/v1/admin/reports/**` | operations-service:8083 |
| Integration | `/api/v1/notifications/**`, `/api/v1/merchant/**`, `/api/v1/merchants/**`, `/api/v1/admin/merchants/**`, `/api/v1/assistant/**` | integration-service:8084 |

`/internal/**` is rejected by Nginx with HTTP 404. Tracking routes retain HTTP
1.1 and buffering-disabled settings for SSE.

## Verification evidence

Results are recorded only after execution. Each item is marked `PASS`, `FAIL`,
or `BLOCKED`; expected authorization failures such as 401/403 are valid
results and are not replaced with synthetic 200 responses.

| Check | Expected | Observed | Result |
|---|---|---|---|
| Compose `infra` config | exit 0 | exit 0; services: postgres, kafka, kafka-init, mailpit | PASS |
| Compose `core` config | exit 0 | exit 0; infrastructure, four services, gateway | PASS |
| Compose `full` config | exit 0 | exit 0; same service set as `core` | PASS |
| Nginx syntax | successful test | syntax is ok; test is successful | PASS |
| Four backend image builds | successful builds | auth, parcel, operations, integration images built successfully | PASS |
| Gateway `/health` | HTTP 200 | HTTP 200 with `ok` body | PASS |
| Auth route through gateway | contract-valid response | registration HTTP 201; login HTTP 200; JWKS HTTP 200 | PASS |
| Parcel route through gateway | contract-valid/auth response | authenticated `/api/v1/parcels` and `/api/v1/addresses` HTTP 200; public unknown tracking HTTP 404 | PASS |
| Operations route through gateway | contract-valid/auth response | unauthenticated HTTP 401; CUSTOMER token HTTP 403 on dispatch, courier, and zones | PASS |
| Integration route through gateway | contract-valid/auth response | authenticated notifications HTTP 200; unauthenticated assistant/merchant routes HTTP 401 | PASS |
| `/internal/*` through gateway | HTTP 404 | `/internal/v1/users` HTTP 404 | PASS |
| Required Kafka topics | all four present | all four present after startup and restart | PASS |
| Maven module verification | relevant verification completes | `mvn -B verify -pl services/parcel-service,services/operations-service -am -DtrimStackTrace=false` was interrupted after host-side Testcontainers Kafka warnings repeatedly failed to resolve `kafka` / connect to the dynamically mapped broker port | BLOCKED |

### Runtime commands and observations

The disposable project was `swiftdrop-m9-week4-runtime`. It created only
project-namespaced containers, network, and named volumes. The existing local
SwiftDrop worktree and data were not used or changed.

```text
docker compose ... --profile core up -d --build     PASS
docker compose ... --profile core ps                PASS: 8 services healthy/running; kafka-init exited 0
GET http://localhost:8080/health                     PASS: 200
GET http://localhost:8081/actuator/health            PASS: 200, UP, PostgreSQL and Mailpit UP where configured
GET http://localhost:8082/actuator/health            PASS: 200, UP, PostgreSQL UP
GET http://localhost:8083/actuator/health            PASS: 200, UP, PostgreSQL UP
GET http://localhost:8084/actuator/health            PASS: 200, UP, PostgreSQL and Mailpit UP
GET http://localhost:8025/readyz                     PASS: 200
docker compose ... stop; ... start                   PASS: all services recovered; gateway health 200
```

The four required topics were present after both initial startup and restart:
`parcel.created.v1`, `parcel.assigned.v1`, `parcel.status-changed.v1`, and
`user.registered.v1`. Spring Kafka also created the expected retry/DLT topics.

Authentication verification used a disposable contract-valid CUSTOMER account;
the password and access token were not recorded. Gateway and direct-service
comparisons matched for `/api/v1/addresses` (HTTP 200) and
`/api/v1/dispatch/parcels` (HTTP 403). A CUSTOMER token was rejected by
dispatcher, courier, and zone authorization. A report request with the
required `from` and `to` parameters returned HTTP 403 as required for its
ADMIN-only controller.

The running parcel container reported:

```text
SWIFTDROP_PRICING_QUOTE_URL=http://operations-service:8083/internal/v1/pricing/quote
```

This confirms Docker DNS and the corrected operations pricing destination are
bound without exposing credentials.

The four backend Docker image builds completed successfully with tests skipped
inside the image build. The separate Maven verification command was not claimed
as passing: the host-side parcel integration test run hung while its Kafka
producer/consumer attempted to resolve the Docker-only hostname `kafka` and
was interrupted. This is an existing test-environment dependency rather than a
Compose runtime failure; it remains outside the M9 deployment-only file scope.

## Remaining dependencies

No application business logic is changed by this M9 deployment fix. The
gateway `/health` endpoint remains an Nginx liveness check only; downstream
readiness is verified separately through private Actuator and infrastructure
checks. No unresolved runtime blocker remains within the M9 deployment scope.
Overall automated Maven verification remains `BLOCKED` by the host-side
Testcontainers/Kafka hostname issue described above, so the issue should not be
closed until that validation dependency is resolved or explicitly waived by
the owning test/infrastructure member.
