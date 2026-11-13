# M9 Week 7 Deployment and Reliability Verification

**Issue:** #236
**Branch:** `test/m9-week7-deployment-reliability`
**Initial inspected base:** `f1b0f5ed477f552d54e137ed47539eaa5f76aea4`
**Verification date:** 2026-11-13

## Scope and environment

Verification was performed in the isolated worktree `D:\swiftdrop\m9-week7-deployment-reliability` and Compose project `swiftdrop-m9-week7`. The existing Week 6 checkout and its containers were left untouched. No Docker volumes were deleted. The temporary host-port override used for coexistence with Week 6 is outside the repository and is not part of the change set.

Docker Desktop reported Compose v5.6.1 and Docker Server 29.9.2. Because this checkout did not contain a local `deploy/.env`, the tracked `deploy/.env.example` was used for configuration validation and local verification; no secret file was created.

## Results

| Area | Status | Evidence |
|---|---|---|
| Compose `infra` profile | PASS | `docker compose ... --profile infra config` exit 0 |
| Compose `core` profile | PASS | `docker compose ... --profile core config` exit 0 |
| Compose `full` profile | PASS | `docker compose ... --profile full config` exit 0 |
| Backend/frontend image build | PASS | Full Compose build completed for all four Spring Boot images and frontend |
| Isolated full-stack startup | PASS | PostgreSQL, Kafka, Mailpit, frontend, four services, and gateway reached healthy status |
| Nginx syntax | PASS | `nginx -t` passed inside the isolated Compose network |
| Gateway and frontend smoke tests | PASS | Gateway `/health` and `/` returned 200; frontend direct `/health` returned 200 |
| Backend gateway routes | PASS | Auth, parcels, courier, and notifications reached services and returned expected unauthenticated 401 responses |
| Internal route protection | PASS | Gateway `/internal/health` returned 404 |
| CORS preflight | PASS | Auth preflight returned 200 with origin and method headers |
| PostgreSQL persistence | PASS | Six non-template databases remained after controlled PostgreSQL stop/start on the same named volume |
| Operations restart/recovery | PASS | Controlled restart and graceful stop/start returned to healthy; actuator returned 200 |
| Dependency interruption | PASS | Integration remained healthy while Mailpit was stopped and after restoration |
| Maven operations verification | PASS | `mvn -B verify -pl services/operations-service -am -DtrimStackTrace=false`; 206 tests, 0 failures, 0 errors, exit 0 |
| Frontend verification | PASS | `npm ci`; 65 suites/508 tests passed; production build passed; exit 0 |
| Authenticated courier E2E | BLOCKED | No approved disposable courier account, JWT, and assigned parcel fixture were available |
| Current remote CI | BLOCKED | Latest `development` run has one unrelated auth-service test failure; Docker job was skipped as a dependency |

## Compose and image verification

The following configuration checks passed with `deploy/.env.example`:

```text
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile infra config
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile core config
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example --profile full config
```

The full image build was rerun after the Week 6 BuildKit/Bake report:

```text
docker compose -p swiftdrop-m9-week7 -f deploy/docker-compose.yml \
  --env-file deploy/.env.example --profile full build
```

The build completed successfully for `auth-service`, `parcel-service`, `operations-service`, `integration-service`, and `frontend`. The earlier image-build blocker did not reproduce.

The isolated runtime used an external, untracked override only to avoid fixed host ports and container names owned by the existing Week 6 stack. The repository Compose configuration itself was not changed.

## Gateway verification

The repository Nginx configuration had three upstreams using `host.docker.internal`. That would route an isolated gateway to another host stack instead of the Compose services. The M9 platform correction changes these upstreams to Compose DNS:

```text
parcel-service:8082
operations-service:8083
integration-service:8084
```

Auth and frontend already used Compose DNS. The gateway configuration passed:

```text
docker run --rm --network swiftdrop-m9-week7_default \
  -v deploy/nginx/nginx.conf:/etc/nginx/nginx.conf:ro \
  nginx:1.27-alpine nginx -t
```

Observed public-entry-point results through `http://127.0.0.1:18080`:

```text
GET /health                         200
GET /                                200
GET /.well-known/jwks.json          200
POST /api/v1/auth/login with {}     400
GET /api/v1/parcels                  401
GET /api/v1/courier/me               401
GET /api/v1/notifications            401
GET /internal/health                 404
GET /not-a-real-route                200 (frontend fallback)
OPTIONS /api/v1/auth/login           200
```

The four backend actuator endpoints returned 200 directly on their isolated host mappings. Docker DNS resolution from the gateway resolved `auth-service`, `parcel-service`, `operations-service`, `integration-service`, and `frontend` to the isolated network addresses.

## Health, dependency, and persistence checks

The Compose health-gated startup completed with all application services and infrastructure healthy. Kafka initialization exited successfully and the expected event, retry, and DLT topics were present. Kafka health probes briefly timed out during startup but subsequently became healthy; no persistent Kafka failure was observed.

Direct actuator checks:

```text
auth-service         /actuator/health  200
parcel-service       /actuator/health  200
operations-service   /actuator/health  200
integration-service  /actuator/health  200
```

PostgreSQL reported six non-template databases before and after a controlled stop/start. The named volume was retained. No volume deletion or destructive database cleanup was performed.

Operations-service was restarted, stopped gracefully, and started again. Its final container health was `healthy` and `/actuator/health` returned 200. Mailpit was stopped for a controlled dependency interruption; integration remained healthy during the interruption and after Mailpit was restored.

## Tests and CI

Local operations verification:

```text
mvn -B verify -pl services/operations-service -am -DtrimStackTrace=false
BUILD SUCCESS
Tests run: 206, Failures: 0, Errors: 0, Skipped: 0
```

Local frontend verification ran under the repository Node 22 configuration:

```text
npm ci
npm test -- --ci
npm run build
65 test suites passed; 508 tests passed; production build passed; exit 0
```

The latest observed remote `development` CI run was for commit `2dd7a0c914d6efaae1ce28faa3aeb8f64e323dbe`:

- Frontend CI: PASS.
- Backend CI: FAIL, one auth-service test: `JwtIssuerTest.tokenWithTamperedSignature_isRejected`.
- Docker image builds: SKIPPED because the backend job failed.

This is an M1/auth-owned cross-member failure. No auth code or tests were modified. The preceding base commit `f1b0f5ed477f552d54e137ed47539eaa5f76aea4` had a successful CI run before the later development commit.

## Authenticated E2E prerequisite

Authenticated courier workflow verification is **BLOCKED**. No approved disposable courier account, valid JWT, and assigned parcel fixture were supplied or discoverable in the approved repository documentation. No credentials were invented and authentication was not bypassed. Unauthenticated authorization and gateway protection checks were still performed.

## Acceptance checklist

- PASS — Compose profiles parse and render.
- PASS — Relevant images build locally, including the previously reported BuildKit/Bake path.
- PASS — Isolated full-stack startup and service health checks.
- PASS — Nginx syntax, service DNS, public routes, error propagation, CORS preflight, and internal-route blocking.
- PASS — PostgreSQL persistence and controlled service restart/graceful shutdown.
- PASS — Operations Maven verification and frontend tests/build.
- BLOCKED — Authenticated courier E2E: approved account/JWT/assigned parcel fixture unavailable.
- BLOCKED — Full CI green status: current development has an unrelated auth-service failure and consequently skipped Docker CI.
- NOT RUN — Destructive volume recreation or load testing; excluded to preserve shared/local data and scope.

Because required acceptance items remain blocked, the eventual PR must use `Refs #236`, not `Closes #236`.
