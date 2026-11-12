# M6 Week 6 completion — #223

Implementation is split by the existing tracked issues. All PRs target development
and remain unmerged for owner/maintainer review:

1. [#261 recovery contract](https://github.com/Nirukshith/swiftdrop/pull/261).
2. [#266 onboarding recovery](https://github.com/Nirukshith/swiftdrop/pull/266),
   implementing #207 against that contract.
3. [#260 merchant bulk/pricing integration](https://github.com/Nirukshith/swiftdrop/pull/260),
   consuming recovery and completing #223. Merge in this order after review.

The user explicitly confirmed M10/M5 owner agreement and approved the cross-member
implementation and same-client ADMIN-confirmed credential replacement policy.
The original checkout's uncommitted work was preserved; isolated worktrees were used.
The final combined development baseline was `cc37191`.

## Implemented scope

| Requirement | Final behaviour/evidence |
| --- | --- |
| ME-01 onboarding | Public registration remains PENDING; ADMIN approval/rejection retains isolation and validates provider associations. |
| ME-02 credential handoff/recovery | Stable operation committed before auth; atomic provider provisioning and non-secret replay; explicit replacement on the same client after a lost handoff. No duplicate user/client and no raw-secret persistence. |
| ME-03 secured order API | Approved merchant JWT association, request/key validation, real pricing and real M1 creation/replay/conflict are verified. |
| ME-06 M6 bulk slice | Template, UTF-8 CSV preview, row validation, independent partial results, upload limits, stable upload/row keys, durable original quote and explicit safe reconciliation. M7 webhook slices are unchanged. |
| Authorization and failures | ADMIN-only recovery, service-caller restrictions, merchant state/tenant guards, invalid inputs, unknown outcomes, concurrent retries and changed-key/file conflicts are tested. |
| Dependencies | M1 #205 replay (#216) and merged M10 audience support (#262/#264) are consumed. #207 has its own PR #266 rather than being silently bundled/replaced. |

New bulk attempts persist their accepted quote independently before the parcel
request. Replaying the same confirmed file/key can resume WAITING rows and recover
unknown commits with M1's stable downstream identity. Completed rows do not repeat
writes; successful rows survive other row failures. The provider's current parcel
status is retained when reconstructing a lost mapping, including PICKED_UP.

Recovery never retrieves a stored raw secret. Normal onboarding retries reconcile
identifiers; ADMIN confirmation is required to replace an unknown secret on that
same client. A replacement replay does not rotate twice. Another lost one-time
replacement requires another explicit confirmation with a new key. Old secrets
cannot obtain new tokens; already-issued stateless access tokens retain normal expiry.
The admin UI offers Pending/Approved selection, recovery confirmation, transient
handoff display/clearing and protection against overlapping handoffs.

## Configuration and manual workflow

Privately configure the integration-service pricing identity, provisioned by M10
with only pricing:read scope:

| Variable | Purpose |
| --- | --- |
| MERCHANT_PRICING_CLIENT_ID | Dedicated machine client ID; ordinary merchant clients are not granted pricing access. |
| MERCHANT_PRICING_CLIENT_SECRET | Dedicated client secret; never commit its value. |
| SERVICES_AUTH_BASE_URL | Existing auth base URL; default http://localhost:8081. |
| SERVICES_OPERATIONS_BASE_URL | Existing operations base URL; default http://localhost:8083. |
| SERVICES_PARCEL_BASE_URL | Existing parcel base URL; default http://localhost:8082. |
| INTERNAL_MERCHANT_SERVICE_TOKEN | Existing internal caller credential, matching auth and integration. |

The M6 pricing client exchanges credentials through the merged JSON token endpoint
`/api/v1/auth/token`, caches bearer tokens only in memory, refreshes before expiry,
and invalidates rejected tokens without automatic write retry. Missing credentials
or failed pricing fails before parcel creation. Existing role/scope/audience
validation and gateway internal-route restrictions remain in force. No deployment
or shared-auth code is changed by these PRs.

For a manual demo, register the business, approve it as ADMIN and acknowledge the
one-time API credential handoff. An approved merchant without a browser password
can use the existing Forgot password/email reset flow before signing in to the
merchant portal; the OAuth client secret is an API credential, not a browser
password. Open `/merchant/bulk`, download the template, preview valid/invalid rows,
confirm creation, then check/replay using the retained upload reference. For a
lost credential handoff, the ADMIN selects the Pending or Approved list and
explicitly confirms **Recover credentials**. Browser steps are provided for
reproduction; an interactive browser session is not claimed as executed evidence.

## Exact verification commands

Java 21.0.13, Node 22.23.4 and Docker. A local verification checkout combined the
separate PR branches; no GitHub PR was merged.

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
mvn -B verify -pl services/auth-service -am
cd frontend
npm ci
npm test -- --ci
npm run build
cd ..
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s services/integration-service/src/test/resources/merchant -p test_fault_proxy.py -v
python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

| Check | Result |
| --- | --- |
| Combined full Maven reactor | BUILD SUCCESS; 836 tests; zero failures/errors/skips |
| common / auth / parcel / operations / integration in that run | 13 / 218 / 218 / 137 / 250 |
| Final auth check after operation-collision guard | BUILD SUCCESS; common 13 + auth 219; zero failures/errors/skips |
| Latest module evidence combined | 837 tests: common 13, auth 219, parcel 218, operations 137, integration 250 |
| Node 22 npm ci | Passed; lockfile unchanged |
| Frontend tests | 65 suites / 489 tests passed |
| Production build | Passed |
| Fault-proxy real HTTP framing tests | 2 passed |
| Isolated real-service HTTP workflow | 146 checks passed; exit 0; disposable fixtures cleaned up |

The last auth guard was checked in the complete auth module after the full reactor.
The runtime used that verified auth JAR; source equality with the combined checkout
was checked. Other service JARs came from the passing full reactor. The 837 figure
is latest module evidence, not a claim that the full reactor was rerun after that
one guard. No test was skipped, deleted or weakened. Existing validation/denial
cases were adapted to the new atomic provisioning contract.

The initial combined build caught newly merged audience overlap and then a newly
merged standalone test's constructor setup. Work stopped after two failures; the
user authorized correction and continuation. M10's merged audience implementation
and the security test assertions were retained. Corrected full verification passed.
The initial runtime exposed chunked-body forwarding missing from the test proxy.
After a real HTTP framing test/fix, the complete workflow passed. The framing
suite required loopback socket permission; its initial sandbox bind was blocked
before tests could run. No application policy was weakened to make tests pass.

## Real workflow and fault evidence

The runner starts real auth, operations, parcel and integration JVMs with disposable
PostgreSQL/Kafka/Mailpit. Dedicated machine credentials and DISPATCHER pricing data
are provisioned through existing authorized APIs. The seeded verification price is
500 + 250 + 100 = 850 LKR; these are isolated test data, not new production pricing
requirements. Successful pricing, provisioning and parcel creation are real.

Loopback-only fault proxies discard selected SUCCESSFUL provider responses after
commit and return a gateway error. They never fabricate a successful business
response. The test proxy handles Java chunked request bodies. Private credentials
stay in memory/private fixture environment and are never printed.

Sanitized terminal evidence:

```text
PASS real scoped pricing quote: HTTP 200
PASS ordinary merchant token cannot read internal pricing: HTTP 403
PASS injected lost auth provisioning response after commit: HTTP 502
PASS normal retry reconciles without silently rotating credentials: HTTP 409
PASS same replacement key does not rotate or reveal a secret again: HTTP 409
PASS explicit new confirmation recovers credential handoff: HTTP 200
PASS recovery retains the original client and approves the merchant
PASS replaced old secret cannot obtain another token: HTTP 401
PASS bulk records lost real parcel response: HTTP 200
PASS exactly one real parcel committed before retry
PASS change real pricing after ambiguous parcel commit: HTTP 200
PASS recovery pins 850 LKR instead of requoting at the new price
PASS recovery respects the provider current lifecycle state
PASS lost-response recovery created no duplicate parcel
ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED
Isolated runtime fixtures cleaned up
```

The 146 checks also cover registration/approval/rejection, credential exchange,
input validation and failure correction, tenant-isolated order lists, M1 downstream
replay/conflict, cancellation lifecycle/ownership, malformed CSV, row errors,
partial results, concurrent/repeated uploads and authorization. A real PostgreSQL
trigger separately verifies a local M6 persistence failure after provider completion.

## Operational limits

- Legacy ambiguous bulk attempts created BEFORE quote persistence stay reserved
  when the original quote cannot be recovered. Never guess it or delete a guard
  to force another creation. New version-1 attempts support the verified replay.
- Ambiguous multiple legacy auth clients are denied for controlled operator
  reconciliation; no arbitrary client is selected and no secret is read from storage.
- Operation/upload checkpoints have no automatic deletion/expiry job. Existing
  stateless access tokens expire normally after a secret replacement.
- M10's audience support is reused from development. This feature does not claim
  to close every separately tracked #208 consumer task or modify M1 customer code.

Implementation and verification are complete for #223's merchant scope, with the
above migration/operational safeguards documented. Release requires maintainer
review and the stated PR merge order; no PR was merged by the agent.
