# Merchant provisioning recovery — #207

The cross-member implementation and explicit same-client replacement policy were
approved by the user confirming M10/M5 owner agreement. This implementation
consumes the additive [recovery contract PR #261](https://github.com/Nirukshith/swiftdrop/pull/261).
Merge that contract before this implementation; no PR is merged automatically.

## Behaviour

M6 commits a merchant-scoped operation UUID before its first auth request. It
then serializes approval, rejection and recovery per merchant. Auth serializes
both operation identity and merchant association and atomically commits the
MERCHANT user activation, OAuth client and operation fingerprint. Repeating the
same operation/profile returns the same identifiers without another account or
client. Changed profiles, foreign email/merchant associations, mismatched saved
user IDs and ambiguous legacy clients conflict instead of being silently adopted.
Legacy internal create endpoints cannot bypass an established operation.

No cross-database transaction or query is used by either service. Auth owns
`merchant_provisioning`, `merchant_credential_replacements` and
`merchant_provisioning_events` in auth_db. M6 owns the durable operation column and
`merchant_provisioning_audit` in integration_db. Both use additive V7 migrations;
do not delete these checkpoints to retry an unknown outcome.

The raw secret is returned only when generated, held in memory and never saved
in the provisioning/audit tables. Auth stores its BCrypt hash in the existing
client row. Provider status and ordinary retries expose no previous secret.
M6 validates the operation, merchant, user identity/role/enabled state/email and
client association before approval or handoff. Provider response string rendering
redacts secret/contact data; downstream failure bodies are not exposed by M6.

## Lost responses and explicit recovery

If auth committed but M6 lost its response or failed its own local transaction,
retry reuses the committed operation. When the original one-time secret cannot
be returned, M6 remains PENDING and ordinary approval returns a conflict directing
the ADMIN to recovery. Pending/rejected merchants still cannot place orders.
M6 refuses rejection after provisioning started so that an active orphan account
is not silently left behind.

In the ADMIN merchant approval page, refresh the list and choose **Recover
credentials**, or select **Approved** if approval committed before the browser
lost its response. Explicitly confirm replacing the lost secret. Recovery uses
the same OAuth client ID and a separately identified replacement operation.
Retries retain that replacement key; auth never rotates again for the same key.
If the replacement's one-time response is also lost, close the dialog and make a
new explicit confirmation. This creates a new secret on the same client, not a
second client. The old secret can no longer obtain new tokens; already-issued
stateless access tokens retain their normal expiry. No automatic rotation or
general secret-read API exists.

The successful approval/recovery response uses `Cache-Control: no-store` and the
existing approval response shape. The admin UI holds the secret transiently,
clears it on acknowledgement/navigation, and blocks overlapping handoffs. Lists
only add `credentialRecoveryAvailable`; they never return a secret. ADMIN actors
come from validated authentication in M6. Auth accepts audit actor metadata only
from the authenticated merchant-service caller; notifications and ordinary user
tokens cannot invoke the internal provisioning/replacement endpoints.

## Setup and checks

Use Java 21, Node 22 and Docker. Existing `INTERNAL_MERCHANT_SERVICE_TOKEN` must
match between auth and integration services. Recovery requires no new plaintext
secret store or deployment key. Keep gateway `/internal/**` protection in place.
Dedicated pricing credentials belong to the separate M6 pricing consumer; M10's
already merged audience support (#262/#264) is consumed without duplicating #208.

```sh
mvn -B verify -pl services/auth-service,services/integration-service -am
cd frontend
npm ci
npm test -- --ci
npm run build
```

Focused evidence before the final combined run: auth provisioning suite 13 tests
passed; M6 onboarding suite 32 tests passed, including a real PostgreSQL trigger
that forces the local APPROVED update to fail after the provider returned. The
retry retained its operation and required controlled secret recovery. Existing
invalid-user/activation/credential association cases were adapted to the atomic
contract; their denial and non-disclosure assertions remain. Legacy HTTP client
tests remain, and new provisioning/status/replacement wire checks and response
redaction checks were added. The final cross-merchant operation-collision guard
adds another provider regression test.

Frontend recovery verification: all 65 suites / 487 tests passed on this branch,
and the production build passed. The first frontend run caught an actionable
409-message regression; the application message was corrected while retaining
the original assertion. No test was skipped, deleted or weakened. Latest combined
development frontend verification also passed: 65 suites / 489 tests and build.

The complete #223 real-service runner combines this branch with the approved
contract and merchant bulk branch. It discards selected successful responses
only after the real auth/parcel provider commits, then verifies reconciliation,
same-client explicit replacement, old-secret denial, real pricing and no duplicate
parcel after a price change. Final aggregate results are recorded on the related
PRs and in the Week 6 merchant completion document.

## Final recorded results

Development baseline `cc37191`, Java 21.0.13 / Node 22.23.4:

- Combined full reactor: **BUILD SUCCESS, 836 tests**, zero failures/errors/skips
  (common 13, auth 218, parcel 218, operations 137, integration 250).
- After the final operation-collision guard,
  `mvn -B verify -pl services/auth-service -am`: **BUILD SUCCESS**, common 13 +
  auth **219 tests**, zero failures/errors/skips. This includes 14 provider recovery
  tests. The runtime used that verified final auth artifact, with source equality
  checked against the combined checkout. Other service artifacts came from the
  passing full reactor.
- Combined frontend: `npm ci`, `npm test -- --ci`, `npm run build` passed;
  **65 suites / 489 tests**.
- `python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py`:
  **146 checks passed**, exit 0, all private fixtures cleaned up. Real pricing
  succeeds; no successful pricing/provisioning/parcel result is mocked.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s services/integration-service/src/test/resources/merchant -p test_fault_proxy.py -v`:
  **2 framing/fault tests passed** with loopback socket permission.

The initial combined verification caught an overlap with newly merged M10
audience support, then a newly merged standalone security test's constructor
setup. Work stopped after the second failure as required; the user authorized
the compatibility correction and continuation. M10's implementation was retained,
and the test received the guard dependency without changing its assertions.
The corrected full reactor passed. The first runtime run exposed missing chunked
request forwarding in the test proxy; framing was fixed and independently tested,
then the complete 146-check run passed. No application authorization or assertions
were relaxed to obtain these results.

The GitHub PRs remain unmerged. Merge contract #261, then the #207 implementation,
then #223/#260, after the requested owner reviews. The audience dependency is
already on development via M10 #262/#264; this work does not claim to close #208
or replace its other consumers' work.
