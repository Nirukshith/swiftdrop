# M6-02 real parcel verification

Issue #111: SwiftDrop Week 3 M6-02 — Verify merchant order creation, idempotency, and cancellation against the real parcel service.

## Change

Cancellation previously looked up an idempotency response using only the merchant, operation, key, and request body before checking the requested order. Reusing a successful cancellation key against a different order with the same reason could return the first order's response.

The service now checks tenant ownership before replay and rejects a stored response whose order ID differs from the requested target. Existing request hashes and stored responses remain compatible; no idempotency migration or contract change is needed. A replay for the original order still succeeds after its state becomes `CANCELLED`.

The real HTTP parcel client retains the merged POST creation and PATCH status contracts, including the derived creation header and cancellation `idempotencyKey` field. Its additional constructor allows HTTP-boundary tests without replacing the production client in runtime verification. Each downstream request also relays only the already authenticated JWT from Spring Security; no incoming identity header, arbitrary authentication credentials, or shared cached token is used. This keeps the caller authenticated during parcel-service error dispatch so lifecycle conflicts can retain their original HTTP status.

## Automated checks

PostgreSQL integration tests cover creation replay, conflicting payloads, merchant key isolation, cancellation replay, changed reasons, target-order conflicts, ownership before replay, stale parcel state, local picked-up state, failed cancellation retry, pending merchants, and canonical numeric JSON. HTTP-boundary tests assert the exact creation/cancellation payloads and preserve upstream 409 responses.

Commands (Java 21 / Node 22):

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci
npm test -- --ci --runInBand
npm run build
```

Initial full-reactor verification passed: common 13, auth 140, parcel 199, operations 85, integration 119 tests; zero failures/errors/skips. Frontend verification passed 62 suites / 399 tests and the production build.

The first real-service run exposed a pickup cancellation returning 502 instead of 409. JWT-relay regression tests were added, and final M6 verification was rerun. An intervening full integration run failed in the unchanged M7 webhook test because `api.testmerchant.com` could not resolve; the hostname was subsequently verified to resolve and the full suite rerun without skipping or modifying that test. Final M6 verification passed with all assertions unchanged:

```text
mvn -B verify -pl services/integration-service -am
common:              Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
integration-service: Tests run: 121, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Combined final module evidence is 558 backend tests: common 13, auth 140, parcel 199, operations 85, integration 121. Unchanged frontend: 62 suites / 399 tests passed and production build passed. Real runtime verification completed with **50 passing checks**. The initial pickup failure was traced to the anonymous parcel request's error dispatch returning 401; relaying the validated JWT preserves the original 409. The strict expected-409 assertions were retained.

```text
PASS real client-credentials token first: HTTP 200
PASS real parcel creation: HTTP 201
PASS concurrent requests share one original response
PASS replays and conflicts caused no duplicate parcel side effects
PASS cancellation key cannot replay for another owned order: HTTP 409
PASS ownership checked before replay: HTTP 404
DIAGNOSTIC anonymous parcel conflict response: HTTP 401
PASS authenticated parcel call preserves original lifecycle conflict: HTTP 409
PASS real lifecycle rejects cancellation in PICKED_UP: HTTP 409
PASS real lifecycle rejects cancellation in IN_TRANSIT: HTTP 409
PASS real lifecycle rejects cancellation in OUT_FOR_DELIVERY: HTTP 409
PASS real lifecycle rejects cancellation in DELIVERED: HTTP 409
ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED
Isolated runtime fixtures cleaned up
```

## Reproduce the real-service check

The standard-library Python runners live in `services/integration-service/src/test/resources/merchant/`. With Java 21 selected through `JAVA_HOME`, Docker running, all service JARs built by the command above, and Python 3 available:

```sh
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

The runner creates disposable PostgreSQL, Kafka, and Mailpit containers, isolated databases for each service, and real auth, parcel, operations, and integration JVMs. It binds only loopback ports 19181–19185, 19192, 19194, and 19125; occupied ports abort setup rather than replacing existing services. Credentials are randomly generated, passed in memory/environment, and never printed. Private JVM logs are stored in a generated temporary directory. The runner terminates its JVMs and removes only its own containers and anonymous volumes on completion or an exception.

`verify_real_parcel.py` logs in to a prepared ADMIN account, registers and approves two merchants through real integration/auth calls, exchanges their real client credentials for signed tokens, and exercises merchant writes through the production `HttpParcelClient` into the real parcel service. It checks full response equality on replay, concurrent same-key calls, absence of duplicate parcel side effects, merchant isolation, cancellation replay/conflicts, foreign and missing orders, and cancellation rejection in `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, and `DELIVERED` states. It also rejects invalid client secrets, invalid bearer tokens, anonymous callers, and non-merchant roles.

For an already prepared isolated environment, `verify_real_parcel.py` accepts `M6_VERIFY_AUTH_URL`, `M6_VERIFY_INTEGRATION_URL`, `M6_VERIFY_PARCEL_URL`, `M6_VERIFY_ADMIN_EMAIL`, and `M6_VERIFY_ADMIN_PASSWORD`. URLs must use loopback hosts. Its quote fixture must return LKR 850 for the documented test addresses, SMALL size, and 1.5 kg. Optional `M6_VERIFY_PRICING_PROVIDER_URL` checks the current real provider's authorization mismatch.

## Pricing dependency and owner handoffs

The merged `operations-pricing.yaml` quote operation has no operation security requirement, while the current operations implementation requires an authenticated `DISPATCHER` on `/internal/v1/pricing/quote`. M6's `HttpPricingQuoteClient` has no agreed service credential for that route. A merchant token must not be elevated to DISPATCHER to work around this.

Following the shared rule for unready dependencies, this verification uses a small HTTP fixture implementing the merged quote request/response contract. Auth provisioning, JWT verification, merchant ordering, parcel persistence, and parcel lifecycle execution remain real. The separately launched real operations provider is checked for its current 401/403 behavior. This is not evidence that the complete real pricing chain works.

- M1 (`SharanenS`): confirm the unchanged internal creation/status contracts and expected-current-status cancellation behavior. The parcel creation controller currently does not consume the advertised `Idempotency-Key`; integration's stored key prevents duplicate effects on successful/concurrent replays, but crash recovery after a parcel succeeds and before the integration transaction commits remains a separate downstream idempotency handoff.
- M10-02 #120 (`nirsanth23`): review actual client-credentials issuance and signed-token verification. No auth source changes are introduced.
- M5/M10 follow-up: agree least-privilege service authentication for the quote route, record it in a small contract PR, and align provider/consumer implementation. No M5 security checks are relaxed by this PR.

Peer review and GitHub CI must pass before the issue is marked complete. The PR targets `development` and must not be auto-merged.

Current verifier note: Week 4 adds an inactive-account security fixture. Standalone `verify_real_parcel.py` now also requires `M6_VERIFY_INTERNAL_TOKEN`; the isolated runner supplies a generated token automatically. See [Week 4 verification](m6-week4-merchant-integration-verification.md) for the current environment requirements. The results above remain the historical Week 3 run.
