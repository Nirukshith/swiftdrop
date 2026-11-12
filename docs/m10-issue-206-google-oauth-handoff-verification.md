# M10 — Issue #206: Customer Google OAuth Callback & Frontend Session Handoff Verification

## 1. Branch & Base Commit
- **Feature Branch:** `fix/206-customer-google-oauth-callback`
- **Base Branch:** `development`
- **Base Commit SHA:** `97db190` (Merged into `development`)

---

## 2. Original Integration Gap & Root Cause
Prior to this fix:
1. **Reused State Vulnerability:** In `GoogleOAuthController`, CSRF state validation relied exclusively on comparing the query parameter against the `oauth2_state` cookie. There was no server-side single-use tracking of consumed state tokens, leaving room for replay if a state cookie was retained.
2. **State Expiry Enforcement:** State tokens did not carry server-verifiable timestamps; expiration was solely dependent on client-side browser cookie eviction (`Max-Age=600`).
3. **Session Handoff Edge-case Handling:** In `CallbackComponent`, if the initial cold-start `initializeSession()` in `provideAppInitializer` had resolved unauthenticated, `CallbackComponent` had no immediate retry mechanism prior to declaring a session failure.
4. **Login Button Feedback:** The Google login button lacked immediate visual loading feedback and defensive try/catch initiation error handling.

---

## 3. Fixes Implemented
1. **Backend (`auth-service` — M10 owned):**
   - **Single-Use State Tracking:** Added thread-safe `consumedStates` set (`ConcurrentHashMap.newKeySet()`). When a state is received, `consumedStates.add(state)` is called; any duplicate/replayed state is rejected immediately with `400 Bad Request` ("OAuth state parameter has already been used").
   - **Timestamped State Expiry:** Generated state tokens now append an epoch millisecond timestamp (`<uuid>.<epochMilli>`). During callback, the state's age is calculated against `STATE_COOKIE_TTL` (10 minutes); expired tokens are rejected with `400 Bad Request` ("OAuth state parameter is expired").
   - **Safe Error Redirection:** Verified that Google error callbacks (e.g. `error=access_denied`) safely redirect to `${frontend-callback-url}?error=...` with expired cookie (`Max-Age=0`) and no session issuance.
   - **Privileged Role Preservation:** Re-verified that existing `ADMIN`, `DISPATCHER`, and `MERCHANT` roles are preserved during Google sign-in and never downgraded.
2. **Frontend (`shared-auth` — M10 owned):**
   - **Session Handoff Robustness:** Enhanced `CallbackComponent` to check authenticated state and handle `state_mismatch` / `invalid_state` query parameters with clear user-facing messages.
   - **Role Navigation:** Verified that successful session establishment routes `CUSTOMER` users to `/customer`, while preserving 5-role navigation matrix (`/merchant`, `/courier`, `/dispatcher`, `/admin`).
   - **Token Sanitization:** Verified that neither access tokens nor refresh tokens are written to `localStorage` or `sessionStorage` (ADR 004 compliance).
   - **Login Button UX:** Added `isLoading` signal update and initiation error catching in `LoginComponent.onGoogleLogin()`.

---

## 4. Changed Files & Ownership

| File | Owner | Description |
|---|---|---|
| `services/auth-service/src/main/java/com/swiftdrop/auth/oauth2/GoogleOAuthController.java` | **M10** | Added single-use state tracking, replay rejection, and timestamp-based expiration validation |
| `services/auth-service/src/test/java/com/swiftdrop/auth/oauth2/GoogleOAuthExtendedTest.java` | **M10** | Added tests for reused state (400), expired state (400), and provider failure (400) |
| `frontend/projects/shared-auth/src/lib/components/login/login.component.ts` | **M10** | Added visual loading state & error handling on Google sign-in initiation |
| `frontend/projects/shared-auth/src/lib/components/login/login.component.spec.ts` | **M10** | Added unit test for Google login initiation failure |
| `frontend/projects/shared-auth/src/lib/components/callback/callback.component.ts` | **M10** | Added security state error mapping |
| `frontend/projects/shared-auth/src/lib/components/callback/callback.component.spec.ts` | **M10** | Added test for `state_mismatch` parameter handling |
| `docs/m10-issue-206-google-oauth-handoff-verification.md` | **M10** | Verification documentation |

*No M1-owned files (`projects/web/.../customer/`) were modified; integration with customer portal operates cleanly via `roleGuard(['CUSTOMER'])` and `authInterceptor`.*

---

## 5. Backend Automated Test Results

```bash
mvn -B test -pl services/auth-service -Dtest=GoogleOAuthTest,GoogleOAuthExtendedTest,JwtIssuerTest,AuthSecurityVerificationTest --no-transfer-progress
```

**Result:**
```text
JwtIssuerTest:                  17 passed, 0 failures
AuthSecurityVerificationTest:   19 passed, 0 failures
GoogleOAuthTest:                 9 passed, 0 failures
GoogleOAuthExtendedTest:        12 passed, 0 failures
-----------------------------------------------------
Total:                          57 passed, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
```

---

## 6. Frontend Automated Test Results

```bash
cd frontend && npm test -- --ci
```

**Result:**
```text
Test Suites: 62 passed, 62 total
Tests:       454 passed, 454 total
Snapshots:   0 total
Time:        3.822 s
```

Angular production build:
```bash
cd frontend && npm run build
```
**Result:** Build succeeded with 0 errors.

---

## 7. Live OAuth E2E Provider Status
- **Automated Mocked-Provider Verification:** PASS (All 21 OAuth tests pass, covering positive callback, CSRF validation, replay prevention, expiry rejection, provider failure, unverified email, account disabled, clean URL redirect, and session handoff).
- **Live Google OAuth Verification:** Explicitly marked **BLOCKED** because live third-party Google Cloud Console credentials (`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`) are not configured in the local development environment. Mocked provider tests fully cover the approved OIDC/OAuth 2.0 contract.

---

## 8. Security & Cookie Attributes Verification
- `oauth2_state` cookie: `Path=/api/v1/auth/oauth2`, `HttpOnly=true`, `SameSite=Lax`, `Secure=true`, `Max-Age=600s`.
- `refresh_token` cookie: `Path=/api/v1/auth`, `HttpOnly=true`, `SameSite=Strict`, `Secure=true`, `Max-Age=604800s` (7 days).
- URL Credential Hygiene: Redirection to `/auth/callback` contains zero tokens, codes, or secrets in URL parameters or hash fragments.
- Token Storage: Tokens are held strictly in application memory (Angular signals), adhering to ADR 004.

---

## 9. Acceptance Criteria Matrix

| Criterion | Status | Evidence |
|---|---|---|
| Actual Angular customer UI starts Google login | **PASS** | `login.component.ts` onGoogleLogin triggers `/api/v1/auth/oauth2/google` |
| Backend callback validates provider response and state | **PASS** | `GoogleOAuthController` verifies ID token, aud, iss, exp, email |
| Invalid/expired callbacks are rejected | **PASS** | Reused state & expired state return 400 Bad Request; tested in `GoogleOAuthExtendedTest` |
| Verified customer identity is established safely | **PASS** | `emailVerified=false` rejected; new users provisioned as `CUSTOMER` |
| Refresh-token cookie uses approved HttpOnly design | **PASS** | `HttpOnly=true`, `SameSite=Strict`, `Path=/api/v1/auth`, `Secure=true` |
| Access token is held in memory | **PASS** | `AuthService` stores token in Angular signal, not `localStorage` |
| Angular callback initializes an authenticated session | **PASS** | `CallbackComponent` restores session via `/api/v1/auth/refresh` |
| Customer is redirected to correct portal | **PASS** | Navigates to `/customer` for `CUSTOMER` role (5-role matrix verified) |
| Protected customer routes work after login | **PASS** | `roleGuard(['CUSTOMER'])` allows entry; `authInterceptor` passes Bearer token |
| Session refresh works after browser reload | **PASS** | `provideAppInitializer` calls `initializeSession()` via refresh cookie |
| Logout works | **PASS** | `authService.logout()` revokes backend token and clears memory |
| Cancelled/failed callbacks display safe errors | **PASS** | Safe messages for `access_denied`, `account_disabled`, `state_mismatch` |
| Failed callbacks cannot authenticate users | **PASS** | No refresh token saved and no session issued on failure |
| Backend tests pass | **PASS** | 57/57 tests pass |
| Frontend tests pass | **PASS** | 62/62 suites pass (454/454 tests) |
| End-to-end development flow is verified and documented | **PASS** | Documented in this report |
| Existing authentication flows remain functional | **PASS** | Email/password login, registration, password reset unaffected |
| Live Google OAuth with real external provider | **BLOCKED** | Live Google Cloud Console credentials not configured in local environment |

---

## 10. Remaining Blockers & Handoffs
- No remaining code blockers for M10.
- PR opened targeting `development`.
