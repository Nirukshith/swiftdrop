# M6 Week 6 merchant completion — #223

Base: `development` commit `5a2b107`. Scope is M6's merchant package, bulk UI,
corresponding tests/runtime fixtures, and merchant documentation. Existing work
in the original checkout was preserved in place; this branch uses a fresh worktree.

## Changes

The merchant pricing adapter now uses the merged OAuth client-credentials
contract (`POST /api/v1/auth/token`, JSON request) to request only `pricing:read`.
It sends that dedicated bearer token to the real pricing URL, never the incoming
merchant bearer token. Tokens are cached only in memory, refreshed before expiry,
and invalidated after a quote 401. There is no automatic HTTP retry. Missing
credentials, failed exchange, invalid token metadata, and missing scope fail
before a parcel is requested. Exchange errors do not propagate upstream response
bodies that could contain credentials.

Bulk confirmation freezes its upload key alongside the submitted CSV. Explicit
check/replay keeps both original values even if the editable signal changes.
Existing row validation, per-merchant reservations, partial results, and unknown
outcome guards remain in use.

## Configuration

Set these in the integration-service process environment through the deployment's
secret configuration; do not commit their values:

| Variable | Purpose |
| --- | --- |
| `MERCHANT_PRICING_CLIENT_ID` | Dedicated client provisioned by M10 for the integration service |
| `MERCHANT_PRICING_CLIENT_SECRET` | That client's secret; used only for token exchange |
| `SERVICES_AUTH_BASE_URL` | Auth-service base URL; defaults to `http://localhost:8081` |
| `SERVICES_OPERATIONS_BASE_URL` | Operations-service base URL; defaults to `http://localhost:8083` |

M10 must provision a client allowed to request `pricing:read`. Do not grant that
scope to ordinary merchant clients or grant a human DISPATCHER/ADMIN role to the
service. The current auth API ties clients to an enabled user/merchant association;
the isolated runner creates a disposable dedicated association through those
existing APIs. Production identity provisioning and audience agreement belong to
M10/M5. Missing configuration fails closed with the merchant API's existing 502.
No deployment/platform or provider source is changed in this PR.

## Reproducible verification

Use Java 21 and Node 22. The frontend commands run from `frontend/`.

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci
npm test -- --ci
npm run build
cd ..
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

The runner creates private, disposable PostgreSQL/Kafka/Mailpit fixtures and real
auth, parcel, operations, and integration JVMs. It generates credentials in memory,
uses private temporary service logs, checks actual token issuance and real pricing
authorization, then verifies registration, approval/rejection, order validation,
M1 creation/replay/conflict, cancellation, CSV partial outcomes, tenant isolation,
and duplicate prevention. Pricing for successful order creation remains an
explicit fixture; it now requires a bearer header. This is HTTP integration
evidence, not an interactive browser session or a fully real pricing chain.

Recorded automated results (Java 21.0.13 / Node 22.23.4):

| Check | Result |
| --- | --- |
| Full Maven reactor above | BUILD SUCCESS; 791 tests; zero failures/errors/skips |
| common | 13 tests passed |
| auth-service | 187 tests passed |
| parcel-service | 218 tests passed |
| operations-service | 131 tests passed |
| integration-service in full reactor | 242 tests passed, including 14 new pricing consumer tests |
| `npm ci` | Passed; lockfile unchanged |
| `npm test -- --ci` | 65 suites / 483 tests passed |
| `npm run build` | Production build passed |

The new consumer tests cover exact credential-exchange payload, token caching,
expiry refresh, rejected-token invalidation, missing configuration, sanitized
exchange errors, invalid/insufficient token metadata, dedicated bearer forwarding,
401/403 without automatic retry, and invalid quote responses. Existing onboarding,
CSV parser/API/preview, tenant isolation, concurrency, reservation/recovery, and
parcel lifecycle suites all passed. No tests were skipped, deleted, or weakened.
A temporary default-port Kafka test broker was removed after Maven verification.

The final contract review additionally covered auth's optional response `scope`:
an omitted scope is accepted for the explicitly requested `pricing:read`; an
explicit response lacking that scope is rejected. The pricing provider remains
responsible for validating the signed token and its actual permissions.

After that refinement, `mvn -B verify -pl services/integration-service -am`
passed again: **common 13 + integration 243 tests**, zero failures/errors/skips,
including **15 pricing consumer tests**. The full reactor and runtime results
were recorded before that compatibility refinement; unchanged provider modules
were not rerun. The final module run also used a temporary default-port Kafka
broker for existing Spring test contexts; it was removed afterward.

Runtime result: **109 checks passed**, exit 0, with all isolated fixtures cleaned
up. Sanitized evidence:

```text
PASS real dedicated pricing service token: HTTP 200
PASS dedicated pricing scope without dispatcher elevation
PASS documented M10/M5 blocker: issued token has no audience
PASS documented M10/M5 blocker: real scoped token rejected by pricing audience validation: HTTP 401
PASS real M1 downstream creation replay: HTTP 201
PASS M1 downstream replay preserves the original parcel
PASS M1 downstream key rejects changed payload: HTTP 409
PASS bulk replay, duplicates and malformed files added no extra parcels
ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED
Isolated runtime fixtures cleaned up
```

The 109 checks establish the fixture-qualified merchant workflow and the actual
provider blocker, not full real-pricing success or onboarding crash recovery.
The M1 HTTP check replays an existing committed creation with M6's exact key and
payload; it does not inject a network failure or kill a process after commit.

## Remaining work and ownership

- [#207](https://github.com/Nirukshith/swiftdrop/issues/207), M6/M10: safe recovery
  after auth activation/client provisioning partially commits or loses its response.
  The merged onboarding flow retains the existing pending state/retry tests;
  this issue does not replace or silently implement the separate recovery contract.
- [#208](https://github.com/Nirukshith/swiftdrop/issues/208), M5/M10: align the
  token issuer with operations' required audience. PR #217 merged scoped access
  but auth's `JwtIssuer.issueClientCredentialsToken` still emits no `aud` claim.
  Operations `SecurityConfig` requires `swiftdrop-api`. Do not disable validation
  or mint a fake successful service token as end-to-end evidence.
- [#205](https://github.com/Nirukshith/swiftdrop/issues/205), M1: PR #216's durable
  creation replay is consumed and verified; no provider code is duplicated here.
  M6's ambiguous bulk writes remain reserved until controlled reconciliation can
  preserve the original quote/payload and respect current provider lifecycle state.

Keep `Refs #223` in the PR while recovery and full real pricing are incomplete.
Do not close #223 or mark all acceptance criteria satisfied based on fixture tests.
