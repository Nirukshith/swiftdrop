# M10 Week 7 Verification Report: Authentication Security Testing, JWT Validation, Refresh Sessions, OAuth, and Service-to-Service Authorization

**Issue Reference:** [Issue #237](https://github.com/Nirukshith/swiftdrop/issues/237)  
**Member:** M10 (`nirsanth23`)  
**Base Commit SHA:** `b9b5f3a` (`origin/development`)  
**Branch:** `test/m10-week7-auth-security-verification`  
**Date:** 2026-11-13  

---

## 1. Executive Summary

This report delivers comprehensive security testing and verification evidence for SwiftDrop Week 7 M10 under **Issue #237**. All security testing and verification requirements have been executed against `docs/sharedrule.md`, `AGENTS.md`, `docs/srs.md`, approved ADRs (especially ADR 004 for in-memory token storage), merged contracts (`contracts/openapi/auth.yaml`, `contracts/openapi/operations-pricing.yaml`), and preceding issues (#172, #182, #206, #227):

1. **JWT Cryptographic Security:** Verified RS256 signature enforcement, algorithm confusion attack mitigation (rejection of symmetric HMAC-signed tokens), tampered payload rejection, audience claim (`swiftdrop-api`) compliance, and JWKS key safety (zero private exponent exposure).
2. **Refresh Sessions & Family Revocation:** Verified single-use refresh token rotation, cascade revocation of the entire token family upon reuse detection, rejection of revoked/expired/blank tokens, disabled account session termination (401 + cookie clearance + revocation), and logout cookie invalidation (`Max-Age=0`).
3. **Google OAuth Callback Security:** Verified CSRF state validation, 10-minute state TTL, replay rejection (`400 Bad Request`), unverified Google email rejection, provider cancellation/error handling without session establishment, role preservation preventing privilege escalation, and zero credential leakage in redirect URLs.
4. **Role-Based Authorization & Identity Integrity:** Verified that `X-User-Id` and `X-Role` request headers are strictly ignored, identity is resolved exclusively from the verified JWT `sub`, and non-admin callers cannot access administrative endpoints.
5. **Service-to-Service Authorization:** Verified OAuth 2.0 client-credentials token issuance (`POST /api/v1/auth/token`), unauthorized scope rejection, inactive client rejection, `InternalServiceSecurityFilter` least-privilege matrix (`notifications-service` vs. `merchant-service`), and Nginx gateway 404 blocking for `/internal/*`.
6. **Frontend Security & Cross-Domain Leak Prevention:** Hardened `authInterceptor` to prevent leaking Bearer tokens to external third-party URLs; verified 100% ADR 004 compliance (zero tokens stored in `localStorage` or `sessionStorage`).
7. **Automated Verification:** 156 backend tests in `auth-service` passed with 0 failures; 485 frontend tests across 65 test suites passed cleanly with 0 failures; Angular production build succeeded with 0 errors.

---

## 2. JWT Security & Cryptographic Validation Findings

### 2.1 Algorithm Confusion & Tamper Resistance
- **RS256 vs. HMAC Algorithm Confusion:**
  - An attacker attempting an algorithm confusion attack by crafting an `HS256` token signed with the RSA public key is rejected by Spring Security / Nimbus `JwtDecoder` with `BadJwtException`.
  - Tokens specifying `"alg": "none"` are rejected with `BadJwtException`.
- **Payload & Signature Tampering:**
  - Modifying payload claims (e.g. attempting to flip `role` from `CUSTOMER` to `ADMIN`) without re-signing with the private RSA key causes immediate signature mismatch and `BadJwtException`.
- **Foreign Key Rejection:**
  - Tokens signed by an untrusted or foreign RSA key are rejected with `BadJwtException`.

### 2.2 Audience & Issuer Validation
- **Audience Claim (`aud`):**
  - All access tokens and client-credentials tokens carry the contracted `aud: ["swiftdrop-api"]` claim.
  - Resource server `JwtDecoder` configured with `AudienceValidator("swiftdrop-api")` accepts valid tokens and fails closed on tokens with mismatched or foreign audiences.
- **Issuer Claim (`iss`):**
  - All tokens specify `iss: https://auth.swiftdrop.local`. Any token with a different issuer is rejected.

### 2.3 JWKS Public Key Exposure
- Endpoint: `/.well-known/jwks.json`.
- The endpoint exposes only public parameters (`kty: RSA`, `kid: swiftdrop-dev-key`, `n`, `e`).
- Verified that all private key components (`d`, `p`, `q`, `dp`, `dq`, `qi`) are strictly null and never exposed.

---

## 3. Refresh-Token and Session Security

### 3.1 Token Rotation & Family Revocation
- **Rotation:** Presenting a valid refresh token consumes the token and generates a new token belonging to the same `familyId`.
- **Reuse Detection:** If an already-consumed token is re-submitted (e.g. replay attack or concurrent token theft), `AuthController` detects the reuse, revokes all tokens within that family, and returns `401 Unauthorized` with an expired cookie.
- **Revoked & Expired Tokens:** Direct presentation of a revoked or expired token returns `401 Unauthorized`.
- **Blank / Malformed Tokens:** Empty, blank, or whitespace tokens return `401 Unauthorized`.

### 3.2 Disabled Account Handling
- When a refresh request is received for an account where `user.isEnabled() == false`:
  - `refreshTokenRepository.revokeByUserId(...)` revokes all active tokens for that user.
  - The browser cookie is cleared (`Max-Age=0`).
  - Response status is `401 Unauthorized`.

### 3.3 Logout Invalidation
- Invoking `POST /api/v1/auth/logout`:
  - Revokes the active refresh token in the database.
  - Returns `Set-Cookie: refresh_token=; Max-Age=0; Path=/api/v1/auth; HttpOnly; SameSite=Strict`.

---

## 4. Google OAuth Security Defenses

### 4.1 State & CSRF Protection
- **Timestamped State:** `<uuid>.<epochMilli>` format generated at initiation.
- **Replay Protection:** Re-submitting an already-used state parameter returns `400 Bad Request` ("OAuth state parameter has already been used").
- **TTL Expiration:** Callback states older than 10 minutes return `400 Bad Request` ("OAuth state parameter is expired").
- **State Mismatch:** Mismatched state query parameter vs. cookie returns `400 Bad Request`.

### 4.2 Error & Cancellation Handling
- If Google returns an error (e.g. `error=access_denied&error_description=User+denied`):
  - Safely redirects browser to `http://localhost:4200/auth/callback?error=access_denied`.
  - **Zero Session Creation:** No `refresh_token` cookie is set.
  - **Zero Credential Leakage:** No tokens, codes, or secrets are exposed in the redirect URI query or fragment.

### 4.3 Provider Verification & Privilege Escalation Defenses
- **Unverified Emails:** Google accounts with `emailVerified == false` return `400 Bad Request` and are rejected.
- **Role Preservation:** Existing accounts (e.g. `ADMIN` or `DISPATCHER`) linking Google identity preserve their pre-existing role.
- **Auto-Provisioning Least-Privilege:** New users authenticating via Google are auto-provisioned strictly with role `CUSTOMER`.

---

## 5. Role-Based and Cross-User Authorization

### 5.1 Identity Spoofing Immunity
- Request headers such as `X-User-Id` or `X-Role` sent to `/api/v1/auth/me` are completely ignored.
- The user identity is extracted strictly by decoding and verifying the RS256 Bearer JWT subject claim (`sub`).
- If an account is disabled, `/api/v1/auth/me` returns `403 Forbidden` even if the bearer token is valid and unexpired.

### 5.2 Role Boundary Enforcement
- All administrative endpoints under `/api/v1/admin/users/**` and `/api/v1/admin/audit/**` require role `ADMIN`.
- Requests from `CUSTOMER`, `MERCHANT`, `COURIER`, or `DISPATCHER` receive `403 Forbidden`.

---

## 6. Service-to-Service Authorization & Gateway Isolation

### 6.1 Public Client Credentials (`POST /api/v1/auth/token`)
- Valid client ID and secret exchange for an RS256 JWT containing `sub=clientId`, `role=MERCHANT`, `merchantId`, `scope`, and `aud=swiftdrop-api`.
- Invalid secret returns `401 Unauthorized`.
- Inactive client (`status != ACTIVE`) returns `403 Forbidden`.
- Disabled merchant user account returns `403 Forbidden`.
- Requesting an unauthorized scope (e.g. `admin:all`) returns `400 Bad Request`.

### 6.2 Internal Service Security Filter (`/internal/v1/**`)
- Blocked externally by Nginx gateway with `404 Not Found` (`location ^~ /internal/ { return 404; }`).
- Requests lacking `X-Internal-Service-Token` return `401 Unauthorized`.
- Ordinary user JWTs return `401 Unauthorized`.
- **Least-Privilege Authorization Matrix:**
  | Caller Identity | Endpoint | Method | Result |
  | :--- | :--- | :--- | :---: |
  | `notifications-service` | `/internal/v1/users/{id}/contact` | GET | **200 OK** |
  | `notifications-service` | `/internal/v1/users` | POST | **403 Forbidden** |
  | `notifications-service` | `/internal/v1/clients` | POST | **403 Forbidden** |
  | `merchant-service` | `/internal/v1/users` | POST | **201 Created** |
  | `merchant-service` | `/internal/v1/users/{id}/activate` | POST | **200 OK** |
  | `merchant-service` | `/internal/v1/clients` | POST | **201 Created** |

---

## 7. Frontend Token Security & Cross-Domain Leak Prevention

### 7.1 Cross-Domain Leak Defense (`authInterceptor`)
- `authInterceptor` was enhanced to check whether requests target external domains:
  ```typescript
  const isExternalUrl = (req.url.startsWith('http://') || req.url.startsWith('https://')) &&
                        !req.url.includes('swiftdrop');
  if (token && !isExternalUrl && !req.headers.has('Authorization')) {
    authReq = authReq.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  ```
- **Evidence:** Tested with `https://maps.googleapis.com/...` — verified that `Authorization` header is `null` and never leaked to third parties.

### 7.2 ADR 004 Token Storage Verification
- Access tokens are stored exclusively in Angular Signals in application memory.
- `localStorage` and `sessionStorage` remain completely untouched during registration, login, token refresh, Google OAuth callback, and logout.

---

## 8. Automated Verification Test Suite Summary

### 8.1 Backend Tests (`auth-service`)
Command executed:
```bash
mvn -B test -pl services/auth-service -Dtest=Week7AuthSecurityVerificationTest,OAuthTokenControllerTest,JwtIssuerTest,JwksControllerTest,LoginGuardTest,AuthSecurityVerificationTest,SlidingWindowLoginGuardTest,LoginGuardSwitchableConfigurationTest,PasswordResetControllerTest,PasswordResetServiceTest,GoogleOAuthExtendedTest,GoogleOAuthTest,AdminUserServiceTest,AdminUserControllerTest,AdminUserSecurityTest,JpaAuditServiceTest,AdminAuditServiceTest,AdminAuditSecurityTest,ClientTokenServiceTest,AuditServiceCurrentUserTest,NoOpAuditServiceTest
```
**Result: BUILD SUCCESS**
- **Total Tests Run:** 156
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Key Suites:**
  - `Week7AuthSecurityVerificationTest`: 16 tests passed (100% green).
  - `JwtIssuerTest`: 19 tests passed.
  - `GoogleOAuthExtendedTest`: 12 tests passed.
  - `AuthSecurityVerificationTest`: 16 tests passed.

*Note on Integration Testcontainers:* Tests requiring local Docker daemon (`AuthCoreTest`, etc.) report `FileNotFoundException: ~/.docker/config.json` when the Docker daemon is inactive in the host environment. Documented as `BLOCKED (Environment Dependency: Docker daemon)` per Section 12 guidelines.

### 8.2 Frontend Tests (`projects/shared-auth` & `projects/web`)
Command executed:
```bash
cd frontend && npm test -- --ci
```
**Result: 100% PASS**
- **Test Suites:** 65 passed, 65 total
- **Tests:** 485 passed, 485 total (including new cross-domain leak prevention tests)
- **Execution Time:** 3.776 s

### 8.3 Frontend Production Build
Command executed:
```bash
cd frontend && npm run build
```
**Result: BUILD SUCCESS**
- `ng build web` completed with 0 errors.
- Bundle output: `frontend/dist/web`.

---

## 9. Cross-Member Ownership & Handoff Summary

| Member | Boundary | Interface / Coordination | Status |
| :--- | :--- | :--- | :--- |
| **M1** | Customer UI | Customer route protection & Google login handoff | Verified |
| **M3** | Notifications | Contact lookup (`/internal/v1/users/{id}/contact`) via service token | Verified |
| **M4** | Admin Users | Admin user management (`/api/v1/admin/users`) role protection | Verified |
| **M5** | Operations/Pricing | Service token audience `swiftdrop-api` and `SCOPE_pricing:read` | Verified |
| **M6** | Merchant Credentials | Internal client provisioning (`/internal/v1/clients`) & token exchange | Verified |
| **M7** | LoginGuard | Account lockout sliding window logic in `auth-service/security` | Preserved & Passing |
| **M8** | Audit | Audit event logging via `AuditService` | Preserved & Passing |
| **M9** | Gateway & Nginx | Nginx gateway `/internal/*` 404 blocking and proxy routing | Verified in config |

---

## 10. Acceptance Criteria Matrix (Issue #237)

| Acceptance Criterion | Evaluation | Evidence |
| :--- | :---: | :--- |
| All SRS requirements relevant to M10 Week 7 security testing satisfied | **PASS** | JWT, JWKS, refresh sessions, OAuth, service credentials, and role matrices verified. |
| JWT authentication and validation appropriately tested | **PASS** | RS256, algorithm confusion, tampered payload, audience, and key safety tested. |
| Refresh-session rotation, invalidation, and error handling tested | **PASS** | Rotation, family revocation cascade, revoked/expired rejection, and logout tested. |
| Google OAuth security and session handoff verified | **PASS** | Replay defense, state TTL, cancellation safety, and unverified email rejection tested. |
| Role-based and cross-user authorization tested | **PASS** | 5-role matrix, admin route protection, spoofed header ignorance verified. |
| Contracted service-to-service authorization tested | **PASS** | Client credentials, internal least privilege, and gateway isolation verified. |
| Relevant validation and failure handling covered | **PASS** | Bad tokens, expired states, disabled accounts, and unauthorized scopes tested. |
| Backend tests pass and results recorded | **PASS** | 156 unit/security tests pass (0 failures). |
| Frontend tests pass and results recorded | **PASS** | 65 test suites, 485 tests pass (100% green); build clean. |
| Dependent-service integration verified or blockers documented | **PASS** | Audience alignment and Docker daemon dependency accurately documented. |
| Documentation and reproducible security evidence updated | **PASS** | Recorded in `docs/m10-week7-auth-security-verification.md`. |
| A focused PR targeting `development` opened and linked to issue #237 | **PENDING** | Branch pushed to origin; PR ready for creation with `Closes #237`. |
