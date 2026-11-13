# SwiftDrop Week 7 M7 - Issue #234 Verification

Issue #234 is a security-focused verification pass over the existing M7
webhook, merchant API, and login-lockout implementations. Tests preserve the
approved contracts and verify the following:

- HMAC-SHA256 signatures bind the raw payload and timestamp, reject tampering,
  malformed or missing signatures, enforce the replay tolerance window, and
  compare digests in constant time.
- Webhook secrets are generated with secure randomness, encrypted at rest,
  masked in previews, and excluded from diagnostic response data.
- Webhook delivery tests cover successful dispatch, HTTP and transport
  failures, timeout handling, SSRF validation, five-attempt retry/DLT
  behavior, idempotency, and merchant-scoped delivery logs.
- Merchant order and webhook tests cover JWT role enforcement, missing claims,
  request validation, merchant isolation, and safe response handling.
- Login security tests cover account, source-IP, and composite lockout
  thresholds, sliding-window expiry, successful-login reset, and legitimate
  users remaining unaffected.

## Verification results

- `mvn -B -pl services/integration-service -am -Dtest='WebhookSignerTest,WebhookCryptoServiceTest,WebhookDispatcherTest,WebhookControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test` - passed, 32 tests.
- `mvn -B -pl services/auth-service -am -Dtest='SlidingWindowLoginGuardTest,LoginGuardTest,Week7AuthSecurityVerificationTest' -Dsurefire.failIfNoSpecifiedTests=false test` - passed.
- `mvn -B verify -pl services/integration-service -am` - passed, 258 tests.
- `mvn -B verify -pl services/auth-service -am` - passed.
- `cd frontend && npm ci && npm test -- --ci && npm run build` - passed, 65 suites and 490 tests; production build completed.
