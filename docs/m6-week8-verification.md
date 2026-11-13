# M6 Week 8 verification evidence — #253

Issue: <https://github.com/Nirukshith/swiftdrop/issues/253>

Verified baseline: merged development `6355345`, including Week 7 PR #286 and
M1's merged parcel hardening. See [release/demo runbook](m6-week8-release-demo.md)
for prerequisites, environment variables, live demo steps, cleanup and limitations.
Only M6 onboarding UI, merchant verification fixtures and documentation change.
No provider implementation, shared proxy, deployment or contract is modified.

## Automated verification on 2026-11-13

Java 21.0.13, Node 22.23.4, Python 3.9, Docker with PostgreSQL 16, Kafka 3.8.1
and Mailpit 1.32.0. Exact verification commands:

```sh
mvn -B verify -pl services/auth-service,services/parcel-service,services/operations-service,services/integration-service -am
cd frontend
npm ci && npm test -- --ci && npm run build
cd ..
PYTHONPYCACHEPREFIX=/private/tmp/m6-week8-pycache python3 -m unittest discover -s services/integration-service/src/test/resources/merchant -p 'test_*.py' -v
M6_VERIFY_FRONTEND=1 M6_VERIFY_PRICING_MODE=oauth python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
M6_VERIFY_FRONTEND=1 M6_VERIFY_PRICING_MODE=internal M6_DEMO_HOLD_SECONDS=2 python3 services/integration-service/src/test/resources/merchant/run_isolated_verification.py
```

| Check | Result |
| --- | --- |
| Full Maven verify | BUILD SUCCESS; 971 tests; zero failures/errors/skips |
| common / auth / parcel / operations / integration | 13 / 231 / 247 / 206 / 274 |
| Node 22 frontend | 65 suites / 506 tests passed; production build passed |
| Python reset-message/fault-fixture checks | 6 tests; OK |
| Real services through Angular, OAuth pricing | 187 checks passed; exit 0; fixtures cleaned |
| Real services through Angular, internal pricing + 2-second live demo | 190 checks passed; exit 0; fixtures cleaned |

The internal run includes three extra checks for mode 0600/0700 permissions and
actual ADMIN/MERCHANT authentication from the private live-demo login file. The
two-second hold completed, that file was removed, and all 11 demo ports were
successfully rebound afterward. The earlier OAuth rehearsal also passed 187
checks; the final run uses the finished same-origin/cleanup implementation.

Sanitized final runtime output:

```text
PASS Angular serves merchant deep link without API proxy interception
PASS real SMTP delivered the intended merchant reset email
PASS merchant sets a separate sign-in password: HTTP 200
PASS reset token is single-use: HTTP 400
PASS merchant signs in after email setup: HTTP 200
PASS human sign-in retains the approved merchant association
PASS merchant sign-in does not grant ADMIN approval: HTTP 403
PASS signed-in merchant submits the documented CSV template: HTTP 200
PASS portal-created order remains isolated from another merchant
PASS demo CSV reports one valid order and one invalid row
PASS demo CSV replay preserves partial success without another order
ALL REAL-SERVICE MERCHANT ORDER CHECKS PASSED
PASS live demo login file and directory have private permissions
PASS private live demo admin login authenticates through Angular
PASS private live demo merchant login authenticates through Angular
Private demo login file removed
Isolated runtime fixtures cleaned up
```

The frontend audit reports 37 existing findings (3 low, 33 moderate, 1 high).
Dependency versions and the lockfile are unchanged by this release task.

The registration component test now checks the actual password-setup link and
guidance after registration. Unit checks select the intended reset-email recipient
and subject, parse the opaque token without following the URL, reject external or
ambiguous links, and ensure failure text does not expose another merchant's token.
Real SMTP delivery, reset, login and order processing are verified separately over
HTTP; these unit checks are not substitutes for that workflow.

## Shared default proxy reproduction and scoped workaround

An independent Angular dev server was started with the existing default config:

```sh
cd frontend
npm start -- --host 127.0.0.1 --port 19179
```

Anonymous GET observations in this environment (normal host services were absent):

```text
/merchant/bulk                 500 text/plain  Angular HTML: false
/admin/merchants               500 text/plain  Angular HTML: false
/api/v1/merchant/orders        200 text/html   Angular HTML: true
```

The first two routes were forwarded as APIs instead of serving the application;
the actual merchant API route fell through to the Angular HTML page. This explains
why component tests alone do not prove the default local demo setup works. The
M6-specific [proxy configuration](m6-demo/proxy.conf.json) preserves page routes
and forwards the actual API prefixes. The isolated runner uses that exact file
with only target ports changed to its disposable services. It checks the Angular
deep link and sends the full public workflow through the running dev server with
its same-origin Origin header. The temporary reproduction server is stopped.

M9 owns a shared-proxy correction and the separate Compose/gateway topology
handoff listed in the runbook. The local M6 demo workaround does not change the
shared platform configuration or its route/security policy.

## Release evidence boundaries

Real provider faults and pricing/lifecycle recovery are inherited from merged
Week 7 tests. Week 8 adds the missing merchant password-setup-to-upload path,
actual Angular proxy integration, official/mixed-validity CSV fixtures and an
optional bounded live demo. Private fixture credentials remain outside the
repository; demo login files are mode 0600 and removed on cleanup. Access tokens
and reset/API secrets never appear in the sanitized PASS output.

Interactive browser steps are prepared in the runbook. No executed browser
recording, screenshot, shared deployment certification or production fee is
claimed. Legacy reconciliation limits, browser session deployment requirements
and shared platform handoffs remain explicit.
