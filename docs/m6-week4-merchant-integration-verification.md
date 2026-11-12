# M6 Week 4 — Issue #168 merchant onboarding and order integration

Base: development `c4ff87a`. Branch: `feature/m6-week4-merchant-integration`.

## Scope and contracts

Only M6 merchant backend commands, their tests/runtime fixtures, and this document change. M1 parcel, M5 approval/pricing, M10 auth, M7 webhook/dashboard, shared libraries, contracts, and deployment files are not modified.

The existing merged merchant workflow is preserved: public registration persists `PENDING`; ADMIN approval calls M10 to create an initially disabled MERCHANT user, activate that user, and create the one-time OAuth client credentials. Rejection persists `REJECTED` and its trimmed reason without creating or activating credentials. The approval/list/rejection response shapes used by M5 are unchanged. This change does not move credential issuance to public registration.

M6 now checks the provisioned/activated user ID, merchant association, role, normalized email, and expected enabled state. It validates nonblank client ID/secret and the client's merchant association before approving or exposing credentials. Invalid upstream responses leave the merchant pending; a validated saved auth user ID remains available for retry. Client creation is never attempted after an invalid/failed activation response. Error responses never include the raw client secret.

Order creation now rejects missing/malformed parcel identifiers, blank tracking numbers, wrong/missing merchant associations, unknown statuses, missing creation times, and negative fees before persisting the merchant mapping or completing its idempotency key. Failed creation rolls back the local key so a corrected/retried request can proceed. Existing successful replay, conflict, and tenant ownership behavior is preserved. M1 HTTP 400/422 validation errors map to the merchant contract’s 422 response, M1 conflicts map to 409, and transport/server failures remain 502; pricing failures retain their separate gateway-error handling.

## Coverage

- Public registration stays pending, normalizes the email, and performs no premature auth provisioning.
- Approval provisions a disabled user, activates it, and issues credentials in the agreed order.
- Invalid created users, activation identities/states, blank credentials, and foreign-merchant credentials fail closed.
- Activation/client failures remain pending; retries reuse the validated auth user.
- ADMIN-only listing/actions, rejection, duplicate transitions, and one-time/no-store credential responses remain covered.
- Pending/rejected merchant order access, validation, required idempotency keys, successful/concurrent replay, conflicting payloads, and tenant isolation remain covered.
- Invalid/foreign parcel responses, quote failure, parcel failure, retry after rollback, and absence of invalid persisted mappings are tested against PostgreSQL.
- HTTP-boundary tests assert the exact M10 user/client payloads, empty activation body, service authentication header, and 201/200 responses.

## Verification commands

Java 21 and Node 22 are required.

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci
npm test -- --ci --runInBand
npm run build
```

The existing M5 frontend is checked through its unchanged tests and API compatibility. No interactive browser approval session is claimed by these automated checks.

### Isolated real-service verification

After building the service JARs with the command above:

```sh
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

The runner creates its own PostgreSQL databases, Kafka, Mailpit, and real auth/parcel/operations/integration JVMs on loopback. It generates credentials in memory, stores only private JVM logs, and cleans up its own processes, containers, and anonymous volumes. Existing development services and data are not replaced.

The verification performs real registration/approval/client-credentials exchange, pending/approved/rejected list/action checks matching M5's requests, inactive-account token rejection, tenant-isolated order listings, successful/concurrent idempotent creation, and a real M1 creation failure followed by corrected retry and replay. The inactive client is deliberately created through M10's internal API as a negative test fixture, not by public merchant registration.

M6's merged weight contract allows positive values while M1 requires at least 0.10 kg. The 0.01 kg probe is intentionally valid at the M6 boundary and invalid at M1; M6 maps that upstream validation failure to its documented 422 response. The check verifies no failed merchant mapping or parcel side effect and then retries with the corrected weight and rolled-back key. No input contract is silently tightened.

For standalone `verify_real_parcel.py`, provide loopback `M6_VERIFY_AUTH_URL`, `M6_VERIFY_INTEGRATION_URL`, `M6_VERIFY_PARCEL_URL`, prepared `M6_VERIFY_ADMIN_EMAIL`/`M6_VERIFY_ADMIN_PASSWORD`, and `M6_VERIFY_INTERNAL_TOKEN`. The isolated runner supplies all of these. An optional `M6_VERIFY_PRICING_PROVIDER_URL` diagnoses the current real provider's authorization policy.

## Provider handoffs and completion limits

Coordination is recorded at [issue #168](https://github.com/Nirukshith/swiftdrop/issues/168#issuecomment-6813364884) for M1 (`SharanenS`), M5 (`Ashmi13`), and M10 (`nirsanth23`).

1. **M5/M10 pricing:** the current real quote endpoint requires DISPATCHER, but its merged contract does not specify service authentication and M6 has no agreed service credential for it. The existing contract pricing fixture is used; the real provider's 401/403 behavior is diagnosed separately. No authentication rule is relaxed and no merchant token is elevated. This is not a fully real pricing-chain verification.
2. **M10 partial approval recovery:** if activation succeeds and client provisioning fails/loses its response, M6 remains pending and denies ordering. The auth account may already be active. No merged deactivation/compensation or idempotent credential-recovery API exists, so full cross-service rollback/activation consistency requires an owner-agreed contract. This PR does not invent an endpoint or claim that compensation is solved.
3. **M1 crash recovery:** the creation controller still does not consume the advertised downstream Idempotency-Key. Successful/concurrent M6 replay is verified, but recovery after a parcel commit and before local integration commit is not claimed to be exactly once.

The implementation PR uses `Refs #168`; unresolved provider handoffs, review, and CI must be tracked before the whole integration issue is marked complete. The PR targets development and must not be auto-merged.

## Terminal results

Final evidence (Java 21 / Node 22.23.4):

```text
Initial full reactor: BUILD SUCCESS
Final mvn -B verify -pl services/integration-service -am: BUILD SUCCESS
common:              13 tests, 0 failures/errors/skips
auth-service:        163 tests, 0 failures/errors/skips
parcel-service:      208 tests, 0 failures/errors/skips
operations-service:  90 tests, 0 failures/errors/skips
integration-service: 192 tests, 0 failures/errors/skips
Combined latest module evidence: 666 backend tests passed
npm ci: passed
npm test -- --ci --runInBand: 62 suites / 425 tests passed
npm run build: passed
Real-service verification: 78 checks passed
```

The full reactor passed before the final M1 error-status mapping addition; the final M6 module and its common dependency were rerun successfully after that change. The other service sources were unchanged.

Selected sanitized runtime output:

```text
PASS real client-credentials token first: HTTP 200
PASS pending inactive account cannot obtain a token: HTTP 403
PASS M5 rejection action contract: HTTP 200
PASS rejected inactive account remains unable to obtain a token: HTTP 403
PASS first merchant listing excludes the second tenant
PASS second merchant listing excludes the first tenant
PASS real M1 validation failure uses the merchant contract without an order: HTTP 422
PASS failed creation produced no parcel side effect
PASS failed creation produced no merchant mapping
PASS retry after M1 validation failure can reuse the rolled-back key: HTTP 201
ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED
Isolated runtime fixtures cleaned up
```
 The first backend attempt could not find a running Docker daemon; Docker Desktop was started and readiness checked before rerunning the full suite. No tests were skipped, deleted, or weakened.
