# Merchant bulk CSV upload and recovery — #178 / #223

Sign in as a MERCHANT and open **Bulk CSV orders** from `/merchant` or
`/merchant/bulk`. Download the template, select a UTF-8 CSV, review row errors,
then confirm valid rows. Preview and removing invalid rows create no parcels.
Correct the source file and upload again before confirmation; inline cell editing
is not provided. Results retain original source row numbers after invalid rows
are removed. Backend authorization/validation remains authoritative.

## Format and limits

[Template](templates/merchant-orders.csv):

```csv
externalReference,recipientName,recipientPhone,pickupLine1,pickupLine2,pickupCity,pickupDistrict,pickupPostalCode,dropoffLine1,dropoffLine2,dropoffCity,dropoffDistrict,dropoffPostalCode,size,weightKg
```

- UTF-8 with optional BOM, LF/CRLF, quoted commas, escaped quotes and quoted line
  breaks. Blank lines are ignored. Invalid UTF-8, NUL, malformed quotes or headers
  reject the entire new file before any parcel call. Short data records get row errors.
- Maximum 5 MiB and 100 records. Five new uploads per authenticated client subject
  per merchant in a rolling minute. Replays do not consume another allowance;
  429 includes `Retry-After: 60`.
- All columns except pickupLine2/dropoffLine2 are required. Reference length is
  at most 128; contact/address columns at most 255. Size is SMALL/MEDIUM/LARGE;
  weight is at least 0.10 kg, at most 12 significant digits and six decimal places
  (including negative scale for exponents). Postal codes are nonblank.
- Districts use M1's 25 Sri Lankan names; case is normalized and Nuwara_Eliya is
  accepted as Nuwara Eliya. Duplicated references within the file are all invalid.
  Existing/reserved references conflict per merchant; another merchant may use
  the same reference independently.

## API and isolation

`POST /api/v1/merchant/orders/bulk`, Content-Type `text/csv`, a valid merchant JWT
and a 1–128 character `Idempotency-Key` are required. The service checks the JWT
merchant association and APPROVED state. CSV fields and spoofed identity headers
cannot select another merchant. The browser uses the existing shared-auth token
in memory and MERCHANT route guard.

200 returns totalRows, acceptedRows, rejectedRows and per-row results with source
rowNumber, accepted, order or errors. Invalid/failed/unresolved rows count as
rejected. 400 rejects malformed new files/keys, 401/403 enforce authentication and
state, 409 rejects changed bytes for an existing key, 413 enforces file limits,
and 429 enforces the allowance. Existing keys are compared before parsing changed
content. Successful reports use `Cache-Control: no-store`.

## Durable quote, independent rows and explicit replay

M6 persists upload/file hash, per-row request hash, row outcome, reference
reservation and original quote in integration_db. Raw CSV, a second copy of
addresses/recipient details, bearer tokens and client secrets are not stored in
these bulk tables. Each row uses the existing order command and M1 internal
creation API with a stable merchant/upload/row-derived idempotency key.

The accepted quote commits in a separate transaction BEFORE the parcel request.
The order transaction always reads that original quote; a retry cannot substitute
new pricing. Successful rows remain committed when another row fails. Completed
responses replay without another parcel write. Explicit same-file/key replay can
resume unattempted rows and reconcile ambiguous writes using the stable provider
identity, even after a concurrent submission or a process interruption.

If M1 committed but its response/local mapping was lost, M1's merged durable replay
returns the existing parcel. M6 rebuilds its mapping using that parcel's current
lifecycle state and original fee. It does not assume replay returns a historical
CREATED state. A later real pricing change does not change the pinned request.
Concurrency remains serialized locally and protected by M1's merchant/key replay.

Pricing failures before a parcel request are retryable with the same upload key
and exact bytes. Explicit 400/422 parcel validation failures release the reference;
correct the data in a NEW upload because changing the original bytes conflicts.
Timeouts, provider/server errors and local commit failures retain reservations.
Never retry an uncertain row with a new reference/key. The UI freezes the confirmed
payload/key together and retries only on explicit **Check / replay this upload**.

Migration V8 marks new attempts with recovery_version=1. Older ambiguous attempts
without a durable original quote stay reserved for controlled operator/M1
reconciliation; the server never guesses their fee or blindly repeats them.
Unattempted legacy WAITING rows can enter the new protocol safely. Upload reports,
quotes and ambiguous reservations have no automatic expiry/deletion. Do not delete
checkpoints to bypass a conflict. This is an explicit legacy-data safety limit,
not an automatic reconciliation job.

## Providers, configuration and evidence

M1 durable merchant creation/replay is already merged (#205 / PR #216). M10's
API audience support is already merged (#262/#264); no duplicate #208 provider
implementation is introduced. M6 preserves both merged pricing authentication modes. Explicit
`MERCHANT_PRICING_CLIENT_ID`/`MERCHANT_PRICING_CLIENT_SECRET` selects pricing:read
OAuth; incomplete/failed OAuth does not silently fall back. With OAuth unset,
the existing `INTERNAL_PRICING_SERVICE_TOKEN` mode from M10 #267 is retained and
must match operations. Only one credential type is sent; neither mode grants a
human staff role or ordinary merchant pricing privileges. Configure credentials
privately; base URLs use existing `SERVICES_AUTH_BASE_URL`/`SERVICES_OPERATIONS_BASE_URL`.

Contract PR #261 and the separately tracked #207 implementation in PR #266 are
now merged into development. The ADMIN must
explicitly confirm replacing a lost secret on the same client; ordinary retries
never silently rotate or read an old secret from storage.

See [Week 6 verification](m6-week6-merchant-completion-verification.md) for exact
commands/results and merge dependencies. The isolated runner now uses real auth,
operations pricing, parcel and integration services with disposable dependencies.
Its fault proxies only discard selected successful auth/parcel responses AFTER
real provider commit; they do not fake successful pricing or parcel creation.
UI/router tests and actual HTTP integration are recorded separately; an interactive
browser login session is not claimed.
