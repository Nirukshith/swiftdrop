# M6 Week 7 merchant hardening — #243

Issue: <https://github.com/Nirukshith/swiftdrop/issues/243>

Base: `origin/development` at `cd1913e`. This work tests and hardens the merged
Week 6 implementation; it does not reimplement #223, #207 or #208. The applicable
sources are SRS §6.3, decided ADRs 001–006, and the merged merchant, recovery,
auth, parcel and operations pricing contracts.

## Confirmed defects and corrections

The initial `MerchantOrderIntegrationTest` regression run executed 38 tests:
three failed, with zero errors/skips. A new key could create another parcel after
an ambiguous single-order response (expected 409, received 201). Cancellation
accepted foreign/missing merchant responses (expected 502, received 200).

* V9 and `MerchantOrderAttemptStore` commit the single-order request hash,
  reference and original fee before the parcel request. A timeout, malformed
  response or local transaction failure retains that intent. Same-key retries
  reuse the original downstream identity and fee. Changed bodies and competing
  single/bulk references conflict before another parcel write. The intent is
  removed atomically with the completed order and its response; definite provider
  400/422 validation failures release it for corrected input. Pricing failures
  commit no intent and make no parcel call.
* Cancellation requires the returned merchant and parcel to match the local
  order, in addition to the CANCELLED state. Invalid responses leave the order
  and replay key unchanged so the same cancellation can retry.
* Reset clears the browser's file control, allowing the same corrected CSV to
  be selected again. A late read from a replaced file cannot overwrite its preview.

No public request/response or event contract changed. Changes remain in M6's
merchant backend, merchant bulk frontend, associated migration, tests and docs.
M1 owns parcel replay; M10 owns provisioning/token issuance; M5 owns real pricing.
Those providers are exercised without modifying their code or adding privileges.

## Coverage and verification

PostgreSQL Testcontainers exercise real migrations and transactions. Provider
mocks in module tests isolate association checks and specific error responses;
they are not the evidence for the real end-to-end workflow.

New regressions cover unknown single-order outcomes, changed pricing/keys/bodies,
single-to-bulk reference conflicts, explicit validation correction, five malformed
cancellation variants, four invalid recovery responses followed by safe recovery,
same-file UI reset and stale file reads. Existing suites also cover CSV limits,
malformed UTF-8/quoting, tenant authorization, pending/rejected merchants, throttling,
concurrent replays, partial success, onboarding secret loss and retry failures.

Commands (Java 21; Docker available; Kafka on loopback 9092 for Spring test contexts):

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B verify -pl services/integration-service -am
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
```

Frontend, from `frontend`, using Node 22.23.4:

```sh
export PATH=/Users/denushathavaruban/.nvm/versions/node/v22.23.4/bin:$PATH
npm ci && npm test -- --ci && npm run build
```

Real HTTP integration, from the repository root after the reactor build:

```sh
python3 -m unittest discover -s services/integration-service/src/test/resources/merchant -p test_fault_proxy.py
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
M6_VERIFY_PRICING_MODE=internal python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

The runner creates disposable PostgreSQL, Kafka and SMTP containers plus real
auth, operations, parcel and integration JVMs. Credentials remain in private
temporary logs/memory. Both pricing authentication modes use real pricing rules.
Fault proxies discard selected successful responses only **after real provider
commit**. In addition to onboarding and bulk recovery, the Week 7 scenario loses
a single-order response, rejects another key and a competing CSV row, advances
the actual parcel to PICKED_UP, changes pricing from 1350 to 1850 LKR, and recovers
the same parcel at its pinned 1350 LKR fee. It checks stable replay and unchanged
parcel count. The runner cleans up its own processes and containers.

Results are recorded below after verification completes.

## Boundaries and operational limits

V9 protects new single-order attempts; it cannot infer pre-migration remote writes
whose response and local state were both lost. Investigate any such historical
incident with M1 using its original merchant/downstream key before resubmitting.
Do not delete an unknown intent to bypass a conflict. Like existing bulk unknown
reservations, unknown single-order intents deliberately have no automatic expiry.
Completed response retention remains the existing 24-hour policy; local order
reference uniqueness remains permanent. No automatic historical reconciliation
or provider-owned change is introduced.

The existing legacy bulk recovery_version=0 limit is unchanged: unknown rows
without a pinned fee require controlled M1/operator reconciliation. Frontend
component/router tests and actual service HTTP verification are separate evidence;
an interactive browser login session is not claimed. Dependency installation
reports existing audit findings; this issue does not change dependency versions.
