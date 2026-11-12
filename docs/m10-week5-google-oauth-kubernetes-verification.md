# M10 Week 5 — Issue #182: Google OAuth Integration & Kubernetes Deployment Verification

## Scope

This verification covers M10's responsibilities for SwiftDrop Week 5:
1. **Google OAuth 2.0 Integration**:
   - Backend initiation (`GET /api/v1/auth/oauth2/google`) with CSRF state cookie (10m TTL, `HttpOnly`, `SameSite=Lax`).
   - Callback handler (`GET /api/v1/auth/oauth2/callback/google`) with ID token validation (issuer, audience, expiry, verified email).
   - Account provisioning: auto-provisions new users as `CUSTOMER`; preserves existing roles (`ADMIN`, `MERCHANT`, `COURIER`, `DISPATCHER`).
   - Rejection of disabled accounts and tampered/missing CSRF states.
   - Clean frontend redirection to `/auth/callback` without credentials, secrets, or tokens in URL query params.
   - Issuance of 7-day `refresh_token` cookie (`HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`).
   - Angular `shared-auth` callback flow with role-based routing matrix (`CUSTOMER` → `/customer`, `MERCHANT` → `/merchant`, `COURIER` → `/courier`, `DISPATCHER` → `/dispatcher`, `ADMIN` → `/admin`).
   - LocalStorage token sanitization check.
2. **Kubernetes Deployment (Minikube)**:
   - Full declarative manifests in `deploy/k8s/` covering namespace, configmap, secrets template, statefulsets with PVCs, services, deployments, and gateway.

No other member's package or code was altered.

---

## Automated Verification

### 1. Auth Service Unit & OAuth Verification Tests

```bash
mvn -B test -pl services/auth-service -Dtest=GoogleOAuthTest,GoogleOAuthExtendedTest,JwtIssuerTest,AuthSecurityVerificationTest --no-transfer-progress
```

**Result:**
```text
JwtIssuerTest:                  17 passed
AuthSecurityVerificationTest:   19 passed
GoogleOAuthTest:                 9 passed
GoogleOAuthExtendedTest:         9 passed
----------------------------------------
Total:                          54 passed, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
```

Test coverage includes:
- `GoogleOAuthTest`: Initiation redirect URL, client ID, scope, CSRF state cookie, callback user auto-provisioning, role preservation, disabled account redirect, error query param handling.
- `GoogleOAuthExtendedTest`:
  - Tampered state CSRF rejection (`400 Bad Request`).
  - Missing state cookie rejection (`400 Bad Request`).
  - Missing authorization code rejection (`400 Bad Request`).
  - Unverified email rejection (`400 Bad Request`).
  - `ADMIN` and `DISPATCHER` role preservation on OAuth sign-in.
  - Refresh cookie security attributes (`HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`).
  - URL hygiene: assertion that `Location` header never contains `token=`, `code=`, or `secret=`.
  - State cookie Max-Age 600s (10 min) validation.

### 2. Angular Frontend Tests

```bash
cd frontend && npm test -- --ci
```

**Result:**
```text
Test Suites: 62 passed, 62 total
Tests:       434 passed, 434 total
Snapshots:   0 total
Time:        3.738 s
```

`CallbackComponent` tests (`callback.component.spec.ts`):
- Session initialization and clean navigation.
- 5-role navigation matrix:
  - `CUSTOMER` → `/customer`
  - `MERCHANT` → `/merchant`
  - `COURIER` → `/courier`
  - `DISPATCHER` → `/dispatcher`
  - `ADMIN` → `/admin`
- Error parameter handling (`access_denied`, `account_disabled`, generic).
- Assertion that no access token or refresh token is written to `localStorage`.

### 3. Angular Production Build

```bash
cd frontend && npm run build
```

**Result:**
```text
Application bundle generation complete. [2.451 seconds]
Output location: dist/web
Build succeeded with 0 errors.
```

### 4. Kubernetes Manifests Validation

```bash
ruby -ryaml -e "Dir.glob('deploy/k8s/*.yaml').each { |f| YAML.load_stream(File.read(f)); puts \"OK: #{File.basename(f)}\" }"
```

**Result:**
```text
OK: postgres-statefulset.yaml
OK: secrets.yaml
OK: mailpit-deployment.yaml
OK: integration-service-deployment.yaml
OK: namespace.yaml
OK: kafka-statefulset.yaml
OK: auth-service-deployment.yaml
OK: nginx-deployment.yaml
OK: configmap.yaml
OK: parcel-service-deployment.yaml
OK: operations-service-deployment.yaml
All 11 manifests valid.
```

---

## Live Provider Status

- **Mocked Provider Tests:** Fully automated and passing (18 tests covering positive and negative OAuth flows).
- **Live Google OAuth Verification:** Real end-to-end browser login against Google's live identity servers requires a registered OAuth Client ID and secret from Google Cloud Console. Because live third-party production credentials cannot be committed, this remains validated via mocked provider contract tests.
