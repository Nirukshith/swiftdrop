# Merchant bulk CSV upload — #178

## Workflow

Sign in as a MERCHANT and open **Bulk CSV orders** from `/merchant`, or navigate
to `/merchant/bulk`. Download the template, select a UTF-8 CSV, review validation,
and confirm the valid rows. Preview and removing invalid rows create no parcels.
Correct invalid data in the source file and select it again before confirmation;
inline cell editing is not provided. Results preserve the original source row
numbers even when invalid rows were removed before submission.

The browser uses shared-auth's existing in-memory bearer token and MERCHANT
route guard. The backend independently validates the JWT merchant association
and requires the merchant record to be APPROVED. Neither headers supplied as
identity nor CSV fields can select another merchant.

## CSV format and limits

[Download the template](templates/merchant-orders.csv). Header spelling and order
must match exactly:

```csv
externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg
```

- UTF-8, with optional BOM; LF or CRLF. Quoted commas, escaped double quotes and
  quoted line breaks are supported. Blank lines are ignored. NUL, invalid UTF-8,
  unmatched/stray quotes and invalid headers reject the entire new file before
  any parcel call. An incorrect short data record receives a row error.
- Maximum 5 MiB and 100 data records. Five new uploads per authenticated client
  subject per merchant in a rolling minute; 429 includes `Retry-After: 60`.
  Replaying an existing key does not consume another upload allowance.
- Every field except `pickupLine2` and `dropoffLine2` is required.
  `externalReference` is at most 128 characters; address/contact columns are at
  most 255. Recipient name and phone must be nonblank, as in the merchant API.
- Size is exactly `SMALL`, `MEDIUM` or `LARGE`. Weight is a decimal of at least
  0.10 kg, at most 12 significant digits and six decimal places (including
  negative scale for exponents). Postal codes must be nonblank. These align the
  CSV boundary with the current M1 creation requirements.
- Districts use M1's 25 Sri Lankan district names. Case is normalized; `Nuwara_Eliya`
  is accepted as `Nuwara Eliya`.
- All occurrences of a duplicate reference within a file are invalid. Existing
  or reserved references are rejected per merchant; another merchant may use
  the same reference independently.

Backend validation is authoritative, including requests that bypass the preview.
No new third-party CSV dependency is used.

## HTTP contract

The implementation uses the existing
[merchant OpenAPI](../contracts/openapi/integration-merchant.yaml):

```http
POST /api/v1/merchant/orders/bulk
Authorization: Bearer <merchant JWT>
Idempotency-Key: <1-128 character upload reference>
Content-Type: text/csv
```

200 returns `totalRows`, `acceptedRows`, `rejectedRows`, and `results`. Each result
contains `rowNumber`, `accepted`, an order on success, and `errors` otherwise.
The API row number is the physical starting line in the submitted CSV, including
the header. Failed/unresolved rows count as rejected; errors distinguish invalid
data, unavailable pricing, duplicate references and unknown outcomes. The UI maps
these numbers back to the original preview when filtering rows.

400 rejects malformed new files or invalid keys; 401/403 protect authentication,
role and merchant state; 409 rejects reuse of a key with different file bytes,
even if the replacement file is malformed; 413 enforces bytes/records; 429 enforces
the upload allowance. Successful responses use `Cache-Control: no-store`.

## Durable submission, retries and partial failure

M6 stores upload metadata, the SHA-256 file hash, per-row request hashes, outcomes
and external-reference reservations in integration_db. Raw CSV, addresses,
recipient details, access tokens and client secrets are not stored in these new
tables. Existing single-order mappings and their original responses are reused.

Each valid row commits a merchant/reference reservation and ATTEMPTING marker
before the external write. Rows invoke the existing order command separately:
each receives its own pricing quote and M1 internal parcel call with a stable
merchant/upload/row-derived idempotency key. One failed row does not roll back
successful rows. The merchant write lock and reference guard also prevent a
single-order request from bypassing an uncertain bulk reservation.

Use the same upload key and exact submitted bytes to check/replay an upload.
Completed results are replayed without repeating parcel writes. A concurrent
replay can return the current stored processing/unresolved rows; check the same
upload after the original request finishes. The browser freezes the confirmed
payload and key for retries and never retries automatically.

If pricing fails before any parcel request, the row reservation is released. A
corrected new upload may retry that reference. M1's explicit 400/422 validation
rejections also release the reference. Timeout, transport/server errors,
unexpected responses, conflicts with uncertain provider state, or local commit
failure retain the reservation and report an unknown outcome. **Unknown writes
are never automatically posted again.** If the local order transaction committed,
M6 can recover its exact original response using merchant, row key and request
hash, then durably save that recovered result in the bulk report.

If a process stops before later rows are attempted, replay reports them as not
completed. No automatic resume job exists. Upload reports and uncertain
reservations do not expire automatically; deleting them blindly would permit
duplicate parcels. Controlled reconciliation/cleanup depends on the provider
contract described below. This conservative behavior prioritizes avoiding a
duplicate write over automatically retrying an ambiguous outcome.

## Provider handoffs and completion limits

[Owner coordination](https://github.com/Nirukshith/swiftdrop/issues/178#issuecomment-6827127456)
tracks M1/M5/M10 dependencies. Their packages and shared-auth are unchanged.

1. **Pricing:** current real `/internal/v1/pricing/quote` requires DISPATCHER;
   M6 has no agreed least-privilege service credential. Merchant tokens must not
   be elevated. The real-service runner uses an explicit pricing fixture and
   separately checks the provider's 401/403 policy. Full real pricing-chain
   acceptance remains open.
2. **M1 recovery:** the internal parcel controller still ignores its advertised
   Idempotency-Key. The durable M6 guard prevents blind repeat writes, but cannot
   identify a parcel that committed remotely while its response/local mapping
   was lost. That outcome needs an agreed M1 replay/reconciliation operation.
3. **M10 endpoint:** the supplied integration note names
   `/api/v1/auth/oauth2/token`; the merged auth contract/controller expose
   `/api/v1/auth/token`. Verification follows the merged endpoint. The baseline
   user/client APIs and JWT agreement do not resolve pricing authorization or
   partial onboarding recovery (#207).

## Acceptance and verification

- [x] Documented template, file upload, validation and preview before confirmation.
- [x] Row errors and duplicate detection; invalid rows create no parcels.
- [x] Independent M1 row commands, tenant-scoped durable replay and partial results.
- [x] JWT/merchant-state isolation and byte/record/upload-rate limits.
- [x] Automated malformed-file, partial-failure, replay/conflict, crash, concurrent,
  authorization and UI scenarios.
- [ ] Full real pricing authentication and provider flow verified.
- [ ] Provider-supported reconciliation of unknown downstream commits.

Run Java 21 `mvn -B verify -pl services/integration-service -am`; build the other
service JARs before `python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py`.
For Node 22 run `npm ci`, `npm test -- --ci --runInBand`, and `npm run build`.
The runtime script verifies real auth/parcel creation, row mappings/fees, counts,
bulk replay, duplicate references, malformed files, concurrency and tenant/role
isolation. Its pricing fixture must remain explicit in reported evidence.

Final command results are recorded in the implementation PR. Use `Refs #178`
while the provider-dependent acceptance criteria remain open.

### Recorded verification on development base `97db190`

| Check | Result |
| --- | --- |
| Full Java 21 reactor | BUILD SUCCESS |
| Final `mvn -B verify -pl services/integration-service -am` | BUILD SUCCESS; common 13 + integration 228 tests |
| Latest combined module evidence | 738 tests: common 13, auth 176, parcel 214, operations 107, integration 228; zero failures/errors/skips |
| Focused CSV/bulk/existing-order suite | 54 tests passed |
| Node 22 `npm ci` | Passed with unchanged lockfile |
| Frontend tests | 65 suites / 481 tests passed |
| Production build | Passed |
| Isolated real-service HTTP verification | 102 checks passed; fixtures cleaned up |

The full reactor passed before the final M6 replay/recovery refinements. The
complete integration/common module suite passed again after those changes;
provider sources were unchanged. A dedicated temporary Kafka broker supplied
default-port test dependencies and was removed after verification.

| CSV scenario | Evidence |
| --- | --- |
| UTF-8/BOM, CRLF, escaped quotes, commas and multiline fields | Java parser and browser preview/file-reader tests passed |
| Invalid encoding/header/quotes, empty files, missing fields | Parser/API/UI tests passed; malformed files created no parcels |
| Duplicate rows, previously used/reserved references | API/UI tests passed; real replay and new-key duplicate attempts created no extra parcels |
| Weight/size/district/postal validation | Row errors passed; real bulk invalid-weight row created no parcel |
| 5 MiB, 100 rows, five uploads/minute | Parser/API/UI limit tests passed; replay exempt; 429 Retry-After verified |
| Partial validation and parcel/pricing failures | API tests passed; successful rows remained committed |
| Concurrent submissions, timeout/crash and lost local result | Durable reservation/replay/recovery tests passed; no blind repeat write |
| Tenant, pending merchant, wrong role and anonymous access | API and actual route-guard tests passed; real signed-token isolation passed |

Sanitized real-service evidence:

```text
PASS real bulk CSV submission: HTTP 200
PASS invalid bulk row created no parcel
PASS bulk parcels have real M1 mappings, correct tenant and configured quote
PASS bulk replay preserves every original row result
PASS bulk conflicting file reuse: HTTP 409
PASS bulk tenant replay cannot expose another merchant parcel
PASS bulk replay, duplicates and malformed files added no extra parcels
PASS bulk non-merchant role denied: HTTP 403
PASS bulk anonymous request denied: HTTP 401
PASS bulk invalid bearer token denied: HTTP 401
```

The bulk scenarios created exactly **five real M1 parcels**: two valid rows for
each of two merchants and one additional concurrently submitted row. Invalid,
malformed, replayed and duplicate rows added none. Returned parcel IDs were
looked up in real M1 responses and checked for the expected merchant and 850 LKR
fixture quote. Real pricing separately returned 401 without authentication and
403 for a merchant token. UI/router tests and real HTTP tests are separate;
an interactive browser login session is not claimed.

The first runtime attempt caught PostgreSQL's temporary initialization server
before its restart. The runner now waits for TCP readiness; the retry passed all
102 checks. The initial focused test fixtures were corrected for Mockito stub
overrides and real JWT security configuration while retaining the anonymous-401
assertion. No tests were skipped, deleted or weakened.
