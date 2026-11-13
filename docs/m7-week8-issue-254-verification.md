# SwiftDrop Week 8 M7 - Issue #254 Verification

## Scope

The M7 merchant workflow is integrated against the authenticated merchant APIs:

- The dashboard loads merchant-scoped order pages and status counts, with loading,
  empty, error, filtering, pagination, and refresh states.
- Webhook settings use SSRF validation, encrypted HMAC secrets, one-time secret
  rotation responses, signed test deliveries, and protected merchant ownership.
- Delivery logs expose paginated status, event, and date filtering, detail
  inspection, retry history, and authorized retry of failed deliveries only.
- OpenAPI documentation describes authentication, merchant isolation, order
  summaries, webhook signatures, retry behavior, delivery diagnostics, and
  error responses.

No credentials, bearer tokens, or webhook secrets are stored in the repository.

## Demo flow

1. Sign in with an approved `MERCHANT` account and open `/merchant`.
2. Confirm order totals and lifecycle rows load, then exercise status, tracking,
   date, and pagination filters.
3. Open **Webhook Settings**, save a public HTTPS endpoint, and rotate the
   secret. Copy the one-time secret from the response; subsequent reads show
   only the masked preview.
4. Send a test event and verify the displayed status, latency, timestamp, and
   signature header.
5. Open **Delivery Logs**, filter by status/event/date, inspect a delivery, and
   retry a failed delivery.
6. Confirm a second merchant cannot see the first merchant's orders, settings,
   or delivery IDs.

## Verification results

| Command | Result |
| --- | --- |
| `mvn -B -pl services/integration-service -am -Dtest=WebhookControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS - 14 tests |
| `mvn -B verify -pl services/integration-service -am` | PASS |
| `cd frontend && npm ci && npm test -- --ci && npm run build` | PASS - 65 suites, 493 tests, production build |

`npm ci` reports existing dependency-audit findings (37 packages: 3 low,
33 moderate, 1 high); no dependency changes were made for this issue.

## Known limitations

- Full end-to-end webhook delivery still requires Kafka, PostgreSQL, and a
  publicly reachable HTTPS test endpoint.
- Manual retry uses the currently configured endpoint and signing secret while
  retaining the delivery's prior attempt history.
