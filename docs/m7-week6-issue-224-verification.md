# M7 Week 6 - Issue #224 Verification

## Scope

Issue #224 completes the merchant order dashboard, webhook delivery visibility and
retry controls, and login protection. The existing M7 implementation is preserved;
this completion hardens webhook delivery-log query validation while retaining the
established immediate manual-retry behavior.

Merchant APIs derive the merchant identifier from the validated JWT claim. Order
and delivery repositories scope reads and retries by that identifier, while
non-merchant callers and spoofed identity headers are rejected. Webhook payloads
and response bodies remain protected from sensitive diagnostic leakage.

## Verification

| Command | Result |
| --- | --- |
| `mvn -B -pl services/integration-service -am -Dtest='WebhookControllerTest,WebhookDispatcherTest,WebhookCryptoServiceTest,WebhookSignerTest,WebhookDeliveryPolicyTest' -Dsurefire.failIfNoSpecifiedTests=false test` | PASS - 31 tests |
| `mvn -B verify -pl services/auth-service -am` | PASS |
| `cd frontend && npm ci` | PASS |
| `cd frontend && npm test -- --ci` | PASS - 65 suites, 490 tests |
| `cd frontend && npm run build` | PASS |
| `mvn -B verify -pl services/integration-service -am` | BLOCKED - Testcontainers could not find a Docker environment; the run was stopped after repeated Docker startup attempts |

The container-backed integration tests remain required for CI or a host with Docker
available. The focused controller, webhook policy, signing, crypto, and dispatcher
tests pass without Docker.
