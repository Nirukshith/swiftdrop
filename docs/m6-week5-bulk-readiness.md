# M6 Week 5 — Bulk CSV readiness (#178)

This is the historical pre-implementation readiness snapshot from PR #195.
See [merchant-bulk-csv.md](merchant-bulk-csv.md) for the subsequent M6 CSV
implementation, verification procedure and remaining provider limitations.

## Decision

Bulk upload is deferred on development `927dfa2aa7c0e2040027f437857c381971ddec0f`.
Issue #178 explicitly prioritizes unfinished core onboarding/order integration over
bulk CSV. [Decided ADR 006](decisions/006-priorities-and-deadline.md) requires M6
to complete its Must-have flow first. This report records that gate; it does not
implement the bulk endpoint, preview, template, upload limits, or CSV tests.

The Week 4 M6 validation changes are present in this base. Successful local
idempotent replay and tenant isolation are already covered, but they do not
establish safe recovery from a committed downstream parcel with a lost response.

## Dependencies requiring owner agreement

| Owner | Current evidence | Required handoff and verification |
| --- | --- | --- |
| M5/M10 | `HttpPricingQuoteClient` sends no service credential. The real quote controller requires DISPATCHER. `operations-pricing.yaml` describes an internal consumer endpoint without defining its service authentication. | Agree least-privilege service authentication and merge provider support. Then configure the M6 client and verify real quote success, denied unauthorized callers, and quote failure without a parcel side effect. Do not elevate merchant tokens to DISPATCHER. |
| M1 | `parcel.yaml` advertises an optional `Idempotency-Key`, and M6 sends it. `InternalParcelController.createMerchantParcel` does not consume it; `ParcelService.createMerchantParcel` creates a fresh parcel. | Provide durable replay scoped to merchant/key and conflicting-payload rejection. Verify concurrent requests and retries after provider commit/lost response before claiming safe bulk retry. Local M6 rollback cannot roll back a committed M1 parcel. |
| M10 | Approval activates the auth user before creating one-time client credentials. Client provisioning failure leaves M6 pending, but activation has already happened. `auth.yaml` has no internal compensation or credential-recovery operation. | Agree compensation or recoverable/idempotent provisioning semantics. Verify activation consistency and lost-response recovery without persisting raw secrets in M6. |

No provider package, shared authentication code, database, or contract is changed
by this readiness report. Existing registration remains PENDING; approval remains
the one-time ADMIN credential handoff. Pending/rejected merchants remain denied
by the M6 order API.

Owner coordination is recorded on
[issue #178](https://github.com/Nirukshith/swiftdrop/issues/178#issuecomment-6821608115),
with the previous [Week 4 handoff](https://github.com/Nirukshith/swiftdrop/issues/168#issuecomment-6813364884)
linked for context. Posting a handoff does not establish owner agreement.

## Bulk implementation gate

After the core dependencies are resolved and the real single-order flow passes:

1. Agree the CSV columns, partial-success semantics, upload byte/row/rate limits,
   retention, and retry rules currently marked as proposals in
   [merchant-design.md](merchant-design.md). Clarify the merged merchant contract
   through a focused contract PR before implementing any changed API behavior.
2. Add the documented template and merchant preview. Preview must have no parcel
   side effects; a local preview does not require inventing a server endpoint.
3. Validate authorization, tenant state, file limits, CSV structure, and individual
   rows before creating parcels. Reuse the single-order command and M1 boundary.
4. Persist per-upload/per-row outcomes scoped to the authenticated merchant.
   Retried uploads must preserve successful rows, reject conflicting key reuse,
   and safely resume failed rows using the agreed M1 replay semantics.
5. Test malformed files, invalid rows with no side effects, partial downstream
   failures, repeat/concurrent submissions, limits, unauthorized callers, and
   cross-tenant access. Verify the actual provider path before closing #178.

The numeric values in the design proposal (5 MiB, 100 rows, five uploads per
client per minute, and 24-hour key retention) are not claimed as agreed or enforced.

## Verification

Commands use Java 21 and Node 22.23.4:

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci --offline --cache /private/tmp/swiftdrop-npm-cache
npm test -- --ci --runInBand
npm run build
```

Frontend results: clean installation passed (908 packages); 62 suites / 428 tests
passed; production build passed. The first sandboxed build aborted with an
esbuild subprocess deadlock (exit 134); the same build passed outside the sandbox
without source changes. No tests were skipped or weakened.

Full backend reactor: BUILD SUCCESS; 676 tests, zero failures/errors/skips.

| Module | Tests |
| --- | ---: |
| common | 13 |
| auth-service | 163 |
| parcel-service | 209 |
| operations-service | 99 |
| integration-service | 192 |

Docker Desktop was started before verification. Existing parcel tests also needed
a broker on their default loopback port 9092. A dedicated temporary Kafka 3.8.1
container was started after confirming the port was free, then removed with its
anonymous volumes after the reactor completed. No existing development broker
or data was replaced.

The isolated runtime check uses the existing runner after building all JARs:

```sh
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

Current-base result: **78 checks passed**, exit 0; isolated runtime fixtures were
cleaned up, and no M6 verification containers remained. This includes real
registration, ADMIN approval/rejection, client-credentials exchange, pending and
rejected account denial, tenant-isolated lists, replay/conflict/concurrency,
validation failure with no parcel/order side effect, corrected retry, and
cancellation lifecycle/ownership checks.

The real pricing provider returned 401 for the current unauthenticated M6 call
and 403 for a merchant token, confirming that core handoff remains open. M1's
latest internal error handling did preserve a lifecycle conflict as 409,
including the anonymous diagnostic; that improvement does not provide creation
idempotency.

The runner uses real auth, parcel, operations, and integration JVMs with disposable
dependencies; M6 pricing still uses a contract fixture. These results establish
neither a fully real pricing chain nor partial-provisioning compensation or
post-commit crash recovery. Existing single-order tests do not verify
bulk-specific acceptance criteria.
