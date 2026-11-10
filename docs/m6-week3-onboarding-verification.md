# M6-01 merchant onboarding and credential handoff

Issue: #110 — SwiftDrop Week 3 M6-01 — Complete merchant approval, auth provisioning, and one-time API credential handoff.

## Agreed workflow

Registration posts to `/api/v1/merchants/register` and displays the server-issued `PENDING` result. The ADMIN-only merchant approval page uses the existing list/approve/reject contracts. Approval synchronously calls M10 user creation, activation, and client provisioning, then returns `APPROVED` with the one-time client credentials.

The administrator securely hands the credentials to the merchant before acknowledging the secret. The approval page retains them only in component memory; acknowledgement and navigation clear them, including cancellation of an in-flight approval subscription. Refreshing the pending list preserves an active handoff, and a subsequent approval cannot overwrite it. Reopening either page never fetches or recreates the secret. The merchant credentials tab explains the administrator handoff instead of displaying mock credentials or asserting an invented approval status. The approval response has `Cache-Control: no-store`, and its diagnostic `toString()` redacts the secret to prevent Spring DEBUG logging from exposing it.

## Local verification — 2026-11-10

Java: Temurin 21. Node: 22.23.4.

```text
mvn -B verify -pl services/integration-service -am
common:              Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
integration-service: Tests run: 109, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

npm ci
added 908 packages

npm test -- --ci --runInBand
Test Suites: 62 passed, 62 total
Tests:       399 passed, 399 total

npm run build
Application bundle generation complete.
Initial total: 466.76 kB
```

PostgreSQL Testcontainers tests cover pending persistence, duplicate registration, ADMIN-only operations, successful provisioning, repeat-approval rejection, secret omission from lists, and failed provisioning remaining pending with retry reusing the existing auth user. HTTP client tests cover the internal provisioning endpoints and caller header. Frontend tests cover the HTTP registration boundary, pending/approved rendering, transient handoff, acknowledgement, navigation, late response cancellation, no-secret responses, and visible approval errors.

The backend run logged existing local Kafka hostname/reconnect warnings but completed all tests successfully. Frontend tests logged existing ts-jest decorator metadata warnings. No tests were skipped or weakened. The sandboxed build initially aborted; the production build succeeded outside the sandbox.

## Coordination and verification limits

- M5-02 (#107), owner `Ashmi13`: the task requester explicitly approved extending this fix into the existing admin approval files to implement the one-time ADMIN handoff. Preserve the approval response only for that active view; do not add secrets to merchant lists, browser storage, URLs, or shared service caches.
- M10-02 (#120), owner `nirsanth23`: user creation, activation, client provisioning, and public `/api/v1/auth/token` remain owned by auth-service. No auth endpoint or payload changes are introduced. Provisioning integration tests use an auth client mock, with separate HTTP-boundary tests; this is not evidence of a live multi-service token exchange.
- Peer review and GitHub CI are tracked on the PR. This document records local evidence, not reviewer approval or CI completion.

## Reviewer runtime recipe (not executed as part of the local automated checks)

1. Submit a new business at `/merchant-registration`; verify a persisted pending row appears at `/admin/merchants` for an ADMIN.
2. Approve it and securely hand the displayed client ID/secret to the merchant. Verify `APPROVED` feedback and removal from the pending list.
3. Refresh the list and verify the active handoff remains visible; verify another approval cannot replace it.
4. Acknowledge the secret; verify it disappears. Reopen the page and verify it is not recovered.
5. Exchange the saved credentials using M10's public `/api/v1/auth/token` contract. Verify the merchant token and rejection of invalid credentials with M10-02.
6. Register another merchant and reject it with a reason; verify the pending list updates and invalid/non-ADMIN actions are rejected.
