# SwiftDrop Authentication & Identity Architecture Design

**Author:** Member M10 (Authentication & Platform)  
**Service:** `auth-service` (Port 8081, Database `auth_db`)  
**Status:** Approved / Active Design  
**Alignment:** Architecture Decisions ADR 001 (Role Model), ADR 004 (Token Storage), and course SRS.

---

## 1. Overview & Architecture Boundary

`auth-service` is the central identity provider for the SwiftDrop microservice ecosystem. It provides:
1. **User Identity & Lifecycle**: Registration, password management, BCrypt hashing, and internal provisioning for merchant onboarding (`M6`) and admin user management (`M4`).
2. **Token Issuance**: Asymmetric RS256 signing of JWT access tokens and management of secure refresh tokens.
3. **Public JWKS Verification**: Serving `/.well-known/jwks.json` to allow downstream resource servers (`parcel-service`, `operations-service`, `integration-service`) and the Nginx Gateway to independently verify bearer tokens without synchronous RPCs back to `auth-service`.
4. **Social Authentication**: Google OAuth 2.0 authorization code flow for customer login.
5. **Machine-to-Machine (M2M)**: OAuth 2.0 client credentials token issuance for merchant backend integrations.
6. **Kafka Event Publishing**: Emitting `user.registered.v1` events to drive welcome emails (`M3`) and courier profile creation (`M9`).

---

## 2. JWT Claims Schema & JWKS Endpoint

### 2.1 Token Specification & Algorithm
- **Signature Algorithm:** `RS256` (RSA Signature with SHA-256), adhering to RFC 7518.
- **Key Size:** RSA 2048-bit.
- **Header Structure:**
  ```json
  {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "swiftdrop-dev-key"
  }
  ```

### 2.2 JWT Claims Schema
The JWT claims strictly adhere to `com.swiftdrop.common.security.JwtClaims` and `com.swiftdrop.common.Roles`:

| Claim | Type | Required | Description & Standard | Example |
| :--- | :--- | :--- | :--- | :--- |
| `iss` | String | Yes | Issuer URI | `https://auth.swiftdrop.local` |
| `sub` | String (UUID) | Yes | Subject: Unique user identifier | `usr-7f8e3b21` |
| `email` | String | Yes | User primary email address | `customer@example.com` |
| `role` | String | Yes | Single authorized role (ADR 001) | `CUSTOMER` |
| `roles` | Array[String]| Yes | Array of roles for Spring Security compatibility | `["ROLE_CUSTOMER"]` |
| `merchantId`| String (UUID)| Conditional| Associated merchant ID (only present when role is `MERCHANT`) | `mch-9902a1` |
| `iat` | Integer | Yes | Issued At timestamp (epoch seconds) | `1729864800` |
| `exp` | Integer | Yes | Expiration timestamp (epoch seconds) | `1729865700` |

### 2.3 JWKS Verification Endpoint
- **URL:** `http://localhost:8081/.well-known/jwks.json` (also mapped via Gateway if exposed).
- **Format (RFC 7517):**
  ```json
  {
    "keys": [
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "sig",
        "kid": "swiftdrop-dev-key",
        "alg": "RS256",
        "n": "u1wE7...public_modulus..."
      }
    ]
  }
  ```
- Downstream Spring Boot resource servers configure:
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            jwk-set-uri: http://auth-service:8081/.well-known/jwks.json
  ```

---

## 3. Token Lifetimes, Storage & Security (ADR 004)

### 3.1 Token Lifetimes
1. **Access Token:**
   - **Duration:** **15 minutes** (`900 seconds`).
   - Short lifespan minimizes exposure window if intercepted in transit or memory.
2. **Refresh Token:**
   - **Duration:** **7 days** (`604,800 seconds`).
   - Stored in `auth_db` (hashed with SHA-256) alongside user ID, expiration, rotation family ID, and revocation flag.
3. **Client-Credentials Token (M2M):**
   - **Proposed Duration:** **1 hour** (`3600 seconds`).
   - *Rationale:* Server-to-server merchant API calls (`integration-service` order API) do not use browser cookies; 1 hour provides high caching efficiency and low issuance overhead while ensuring prompt enforcement if merchant credentials are revoked.

### 3.2 Token Storage Architecture (ADR 004)
To eliminate Cross-Site Scripting (XSS) attack vectors:
- **Browser SPA (Angular):**
  - **Access Token:** Stored **in-memory only** within the Angular application state (Signal store in `shared-auth` library). **Never stored** in `localStorage`, `sessionStorage`, or IndexedDB.
  - **Refresh Token:** Stored in an **`httpOnly`, `Secure`, `SameSite=Strict`** cookie:
    ```http
    Set-Cookie: refresh_token=b82a01f9-3d12...; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800
    ```
- **Nginx Gateway Forwarding:**
  - Nginx configuration explicitly preserves and forwards `Cookie` and `Set-Cookie` headers for `/api/v1/auth/**` routes.

---

## 4. Refresh Token Rotation & Account State Lifecycle

### 4.1 Token Rotation Protocol
Every request to `POST /api/v1/auth/refresh` follows strict one-time-use rotation:
1. Client makes request with the current `refresh_token` cookie.
2. `auth-service` validates the token against `auth_db`:
   - Checks expiration (`exp > now`).
   - Checks revocation status (`revoked == false`).
3. If valid:
   - The used refresh token is immediately marked as **consumed/revoked**.
   - A newly generated refresh token is saved to the database.
   - A new 15-minute RS256 access token is returned in the response body.
   - The new refresh token is set in the response `Set-Cookie` header.
4. **Reuse Detection (Theft Protection):**
   - If an already-consumed refresh token is presented, `auth-service` detects token reuse.
   - It immediately revokes the **entire token family** for that user, invalidating all sessions and forcing re-authentication.

### 4.2 Behavior When User is Disabled or Suspended
When an administrator disables a user (via M4's `PATCH /api/v1/admin/users/{id}`):
1. User account `enabled` flag is set to `false` in `auth_db`.
2. All active refresh tokens belonging to that `userId` are immediately marked `revoked = true`.
3. Subsequent calls to `/api/v1/auth/refresh` perform an active database check:
   - `user.isEnabled()` returns `false` $\rightarrow$ returns **HTTP 401 Unauthorized**.
   - Refresh cookie is cleared (`Max-Age=0`).
4. Any active 15-minute access token will naturally expire within $\le 15$ minutes without any possibility of renewal.

---

## 5. Key Handling: Dev Profile vs. Production Environment

### 5.1 Dev Profile
- Aligned with `com.swiftdrop.common.security.dev.DevJwtTokenMinter`.
- **Key ID:** `swiftdrop-dev-key`.
- Uses an in-memory, deterministic or static RSA 2048-bit key pair when active profile includes `dev` or `local`.
- Zero configuration required by team members running locally.

### 5.2 Production & Staging Environment
- When `dev` profile is inactive, keys are loaded strictly from the environment or secrets store:
  - `SWIFTDROP_AUTH_JWT_PRIVATE_KEY`: Base64-encoded PKCS#8 PEM private key.
  - `SWIFTDROP_AUTH_JWT_PUBLIC_KEY`: Base64-encoded X.509 PEM public key.
  - `SWIFTDROP_AUTH_JWT_KEY_ID`: Identifier for key rotation (e.g. `swiftdrop-prod-2026-v1`).
- Fails startup fast with an explicit configuration error if environment keys are missing or unparseable.
- Never prints or logs private key material.

---

## 6. Google OAuth 2.0 SPA Session Handoff

```
[Browser / SPA]  ── (1) Click "Sign in with Google" ──►  [Gateway / Nginx]
      │                                                         │
      │◄── (2) 302 Redirect to Google Consent ──────────────────┘
      │
[Google Auth] ──── (3) User consents & redirects to Callback ────► [auth-service]
                                                                        │ (4) Exchange code
                                                                        │     Fetch profile
                                                                        │ (5) Find/create user
                                                                        │     Issue refresh cookie
                                                                        │
[Browser / SPA]  ◄── (6) 302 to /auth/callback?token=<jwt> ─────────────┘
  (/auth/callback)
      │
      ├── (7) Extract JWT access token from URL fragment/query
      ├── (8) Store access token in memory (Signal store)
      └── (9) Clean URL with window.history.replaceState()
```

### Handoff Details
1. **Initiation:** User clicks "Sign in with Google" in the Angular SPA. Browser calls `GET /api/v1/auth/oauth2/google`.
2. **State & Redirect:** `auth-service` issues a secure, signed `state` cookie (containing CSRF token and return URL) and redirects the browser to `https://accounts.google.com/o/oauth2/v2/auth`.
3. **User Consent:** User authenticates on Google and consents to basic profile and email scopes (`openid email profile`).
4. **Callback:** Google redirects back to `GET /api/v1/auth/oauth2/callback/google?code=...&state=...`.
5. **Token Exchange & Account Resolution:**
   - `auth-service` verifies `state`.
   - Makes a back-channel POST request to Google's token endpoint to exchange the authorization code for an ID token and access token.
   - Verifies Google ID token and extracts `email`, `name`, and Google subject ID.
   - Checks `auth_db`:
     - If user exists, retrieves user details.
     - If user does not exist, registers a new user with role `CUSTOMER` and publishes `user.registered.v1`.
6. **Session Handoff to SPA:**
   - `auth-service` creates a 7-day refresh token and sets the `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
   - Generates a 15-minute RS256 access token.
   - Returns a 302 redirect to the Angular SPA:
     `Location: http://localhost:4200/auth/callback?token=<access_token>`
7. **SPA Reception:**
   - Angular route `/auth/callback` activates.
   - Extracts the access token parameter into the in-memory Signal store.
   - Cleans the browser history using `history.replaceState()` so the access token does not remain in the browser address bar.
   - Navigates the customer directly to their dashboard.

---

## 7. Service-to-Service Internal Endpoints (Gateway Blocked)

In compliance with the project architecture, all internal service-to-service endpoints are under `/internal/v1/**` and return **HTTP 404** when accessed through the Nginx gateway:

1. **`GET /internal/v1/users/{id}/contact` (Consumed by M3 `integration-service`):**
   - Retrieves recipient name, email, phone number, and role for dispatching status notifications.
2. **`POST /internal/v1/users` (Consumed by M6 `integration-service`):**
   - Provisioning merchant accounts during merchant onboarding.
3. **`POST /internal/v1/users/{id}/activate` (Consumed by M6 `integration-service`):**
   - Activating merchant users upon admin approval.
4. **`POST /internal/v1/clients` (Consumed by M6 `integration-service`):**
   - Generating OAuth 2.0 client credentials (`clientId`, `clientSecret`) for merchant server-to-server order ingestion.
