# M6 final integration and merchant demo — #253

Issue: <https://github.com/Nirukshith/swiftdrop/issues/253>

Start from merged `development` at `6355345`. Week 6 PRs #260/#261/#266 and
Week 7 PR #286 are merged by the maintainer. This release work consumes those
implementations and the approved contracts; it adds no optional merchant feature
or provider-owned code. SRS ME-01/02/03, the M6 part of ME-06 and ADMIN onboarding
coordination are the demo scope. M7 owns dashboard/webhooks, M5 the admin UI and
pricing, M10 authentication, M3 password reset/email, and M9 shared deployment.

## Reproducible final verification

Requirements: Java 21, Maven, Node 22, Python 3.9+, Docker with PostgreSQL 16,
Kafka 3.8.1 and Mailpit images available, and sufficient memory for four JVMs,
the Angular dev server and disposable containers (allow approximately 4 GiB).
Commands below run from the repository root unless stated otherwise.

```sh
# Select your installed Java 21 and Node 22 before running these commands.
java -version
node --version
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci && npm test -- --ci && npm run build
cd ..
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s services/integration-service/src/test/resources/merchant -p 'test_*.py' -v
M6_VERIFY_FRONTEND=1 M6_VERIFY_PRICING_MODE=oauth python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
M6_VERIFY_FRONTEND=1 M6_VERIFY_PRICING_MODE=internal python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

Maven integration tests need Kafka on loopback 9092, as in CI. The isolated
workflow runner uses its own broker on 19192. Use the team infra profile or a
disposable test broker for Maven; do not replace an existing broker/container.
All four verified JARs and `frontend/node_modules` must exist before the runner.

The runner creates fresh, isolated databases, random fixture credentials,
authorized ADMIN/DISPATCHER accounts, two served pricing zones and weight/size
rules. No manual database changes or shared environment secrets are needed.
It serves the real Angular application and sends public auth/merchant requests
through its proxy, then exercises real auth, pricing, parcel and integration.
Internal fixture provisioning and provider inspection remain direct loopback calls.

| Loopback port | Disposable component |
| --- | --- |
| 19180 | Angular app/proxy, when M6_VERIFY_FRONTEND=1 |
| 19181 / 19182 / 19185 / 19184 | Auth / parcel / operations / integration |
| 19186 / 19187 | Parcel / auth response-loss fixtures |
| 19192 / 19194 | Kafka / PostgreSQL |
| 19125 / 19126 | Mailpit SMTP / UI and API |

All selected ports must be free. Preflight refuses occupied ports; it never kills
their owners. Fault proxies discard selected successful provider responses after
real commits. They do not replace pricing, token issuance or parcel creation.
Both modes also verify ordinary merchant tokens cannot access internal pricing.

The extra Week 8 workflow requests a reset for an approved merchant, reads its
real SMTP-delivered email, sets a separate password, rejects reset-token reuse
and a wrong password, then logs in with the correct MERCHANT association. That
human session uploads/replays both the official template and mixed-validity demo
CSV, checks partial results, and verifies another merchant cannot see its order.
Existing checks cover onboarding recovery, tenant guards, cancellation, pricing
changes, original-quote recovery, unknown commits and no duplicate parcels.

## Run the live demo

After the builds above, keep the verified stack running for a 15-minute demo:

```sh
M6_VERIFY_FRONTEND=1 M6_VERIFY_PRICING_MODE=internal M6_DEMO_HOLD_SECONDS=900 python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

Wait for `ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED` and the printed demo URLs.
Open <http://127.0.0.1:19180> and Mailpit at <http://127.0.0.1:19126>.
The runner prints the **path**, never the values, of a private `demo-accounts.json`
containing the disposable ADMIN and already-approved merchant sign-in accounts.
Its directory is mode 0700 and file mode 0600. Read it privately before recording;
do not copy it into the repository or submission artifacts. It is deleted on
normal/interrupt cleanup. Product API secrets remain one-time handoff values.

Use separate browser profiles/private windows for ADMIN and MERCHANT sessions.

| Step | Action | Expected result / safe evidence |
| --- | --- | --- |
| 1 | Open `/merchant-registration`; register a business with a new demo email and valid +94 phone. | PENDING confirmation; guidance separates API secret from sign-in password. |
| 2 | Sign in as fixture ADMIN; open `/admin/merchants`, Pending, approve the new business. | APPROVED; one-time credential handoff. Copy privately, acknowledge, and keep secrets out of screenshots/video. |
| 3 | In the merchant window use `/forgot-password` with that approved email. Open its message in isolated Mailpit, follow the reset link and set a password. | Reset succeeds; the API client secret is not used as a sign-in password. |
| 4 | Sign in as that merchant and open `/merchant/bulk`. | Merchant portal and Bulk CSV orders page load; ADMIN approval remains unavailable to this role. |
| 5 | Select [merchant-orders-demo.csv](m6-demo/merchant-orders-demo.csv). | Preview: one valid row, one invalid weight; preview creates no parcels. |
| 6 | Record the upload reference, then Confirm. | Only the valid preview row is submitted. One order/tracking number and fee appear. The invalid row remains a preview error. |
| 7 | Check / replay the upload with its retained reference. | Same order/tracking/fee; no second parcel. Preserve this reference if a response is uncertain. |
| 8 | Start another upload and select the same file. | File control resets and preview works. Use a fresh external reference for a genuinely new order; repeating an existing reference is rejected. |
| 9 | Return to `/merchant`. | Order listing is scoped to that merchant. M7 owns lifecycle/dashboard display details. |
| 10 | As ADMIN, select an approved merchant and explicitly confirm Recover credentials if demonstrating a lost handoff. | Same client, new one-time secret; old secret cannot obtain new tokens. Hide the handoff before recording. |

The invalid preview row is not submitted by the UI, so its final upload total is
one. Posting the raw two-row demo CSV directly yields totalRows=2, acceptedRows=1,
rejectedRows=1; the real HTTP suite separately verifies that server behaviour.
The initial verification fixtures change the demonstration price to 1850 LKR;
this is isolated seeded data, not a required production fee.

The hold accepts 0–1800 seconds (0 disables it) and requires the frontend option
when nonzero. It ends automatically; Ctrl+C also enters cleanup. The runner stops
its Angular process group and JVMs, removes its containers/volumes, and deletes
the private login file. Private service logs remain in the printed directory for
local diagnosis; redact them before sharing. A forced process kill or machine
shutdown cannot execute cleanup; identify only containers with the run's exact
`swiftdrop-m6-demo-<id>-` prefix when cleaning such an interrupted run.

## Environment and deployment requirements

The isolated runner configures these automatically. Existing host/service setup
must supply the corresponding values privately:

| Configuration | Requirement |
| --- | --- |
| JAVA_HOME / PATH | Java 21; Node 22 and npm on PATH for frontend verification. |
| SPRING_PROFILES_ACTIVE | `local` for the documented host fixture. |
| SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD | Each service uses its own auth_db, parcel_db, ops_db or integration_db; run Flyway, not manual table edits. |
| SPRING_KAFKA_BOOTSTRAP_SERVERS / KAFKA_BOOTSTRAP_SERVERS | Reachable broker; required parcel lifecycle topics. |
| JWKS_URL / SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI | Auth `/.well-known/jwks.json`; retain the configured issuer and swiftdrop-api audience checks. |
| SERVICES_AUTH_BASE_URL | Auth origin, default localhost:8081 on a host JVM. |
| SERVICES_PARCEL_BASE_URL | Parcel origin, default localhost:8082 on a host JVM. |
| SERVICES_OPERATIONS_BASE_URL | Pricing origin, default localhost:8083 on a host JVM. |
| INTERNAL_MERCHANT_SERVICE_TOKEN | Matching private service credential in auth and integration. |
| INTERNAL_PRICING_SERVICE_TOKEN | Matching private pricing credential in operations and consumers when using internal mode. |
| MERCHANT_PRICING_CLIENT_ID / MERCHANT_PRICING_CLIENT_SECRET | Both set selects explicit pricing:read OAuth; provision through the owning auth workflow. Partial/failed OAuth does not fall back to internal mode. Unset both for internal mode. |
| SPRING_MAIL_HOST / SPRING_MAIL_PORT | Reachable SMTP; the demo uses Mailpit so merchant password setup can complete. |
| FRONTEND_BASE_URL | Actual browser origin for auth reset-email links. |
| M6_VERIFY_FRONTEND | `1` launches the real Angular app and routes public workflow calls through its proxy; otherwise uses direct service HTTP. |
| M6_VERIFY_PRICING_MODE | `oauth` (default) or `internal`. |
| M6_DEMO_HOLD_SECONDS | Optional bounded live-demo hold after successful checks; not a product configuration. |

V9 is already in merged development. Preserve original-quote/reference intent
tables and provisioning audit/checkpoints across restarts. Back up service-owned
databases before migration; never reset a shared database to prepare a demo.

## Known limitations, workarounds and owner handoffs

* **Shared Angular proxy (M9):** `frontend/proxy.conf.json` maps `/merchant` and
  `/admin/merchants` as API prefixes but lacks the actual `/api/v1/merchant` and
  `/api/v1/admin/merchants` prefixes. This can intercept page deep links and miss
  merchant APIs. The verified demo uses [its own config](m6-demo/proxy.conf.json).
  For already-running host JVMs on normal ports, run from `frontend`:
  `npm start -- --proxy-config ../docs/m6-demo/proxy.conf.json`.
  This config is limited to the merchant demo; shared platform routes stay with M9.
* **Container networking (M9):** current integration Compose environment does not
  supply the three SERVICES_* origins, whose host defaults point at localhost
  inside its container. Gateway configuration also mixes container auth with
  host.docker.internal origins for the other services. A pure Compose release
  needs M9's topology/configuration agreement. This PR certifies the isolated
  host-JVM/Angular demo, not an unchanged `--profile full` deployment.
* **Credential handoff:** ADMIN replacement requires explicit confirmation and a
  fresh replacement key after a lost one-time replacement. There is no raw-secret
  readback. Existing issued access tokens retain their normal expiry.
* **Unknown orders:** replay the same original key/file. Unknown intents and bulk
  checkpoints do not auto-expire. Pre-V9 single-order incidents, legacy ambiguous
  bulk rows without a pinned quote, or ambiguous legacy auth associations require
  controlled operator/provider reconciliation. Do not delete a reservation or
  guess a fee to force a second creation.
* **Evidence boundary:** Angular component/router tests plus actual Angular-served
  HTTP integration are automated. The table above is a repeatable interactive
  walkthrough; an executed browser recording or screenshot is not claimed.
  Use the team's HTTPS setup for deployed browser session/cookie verification.

See [final verification evidence](m6-week8-verification.md) for exact commands,
results and the reproduction of the shared proxy limitation. The preceding
[Week 7 evidence](m6-week7-merchant-hardening-verification.md) remains historical.
