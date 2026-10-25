# Google OAuth 2.0 Login Sequence Diagram

This diagram specifies the Google OAuth 2.0 authorization code flow, token exchange, session issuance, and SPA handoff implemented by `auth-service` (port 8081).

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer (Browser SPA)
    participant Gateway as Nginx Gateway (:80)
    participant AS as auth-service (:8081)
    participant Google as Google Identity Provider
    participant AuthDB as auth_db (PostgreSQL)
    participant Kafka as Kafka Broker (KRaft)
    participant Audit as AuditService (M8)

    %% Step 1: Initiate Flow
    Customer->>Gateway: GET /api/v1/auth/oauth2/google
    Gateway->>AS: Forward request
    AS->>AS: Generate CSRF state token & nonce
    AS-->>Customer: 302 Found (Location: accounts.google.com/o/oauth2/v2/auth...)<br/>Set-Cookie: oauth2_state=<state>; HttpOnly; Secure; SameSite=Lax

    %% Step 2: Google Consent
    Customer->>Google: Navigate to Google Consent Page
    Google-->>Customer: Render Google sign-in & permission prompt
    Customer->>Google: Grant Consent (openid, email, profile)
    Google-->>Customer: 302 Redirect to /api/v1/auth/oauth2/callback/google?code=<code>&state=<state>

    %% Step 3: Callback Processing
    Customer->>Gateway: GET /api/v1/auth/oauth2/callback/google?code=<code>&state=<state><br/>Cookie: oauth2_state=<state>
    Gateway->>AS: Forward request with cookies
    AS->>AS: Verify state matches oauth2_state cookie (CSRF protection)

    alt State mismatch or expired
        AS-->>Customer: 400 Bad Request { code: "INVALID_OAUTH_STATE" }
    else Valid State
        %% Step 4: Token Exchange with Google
        AS->>Google: POST https://oauth2.googleapis.com/token { code, client_id, client_secret, redirect_uri }
        Google-->>AS: 200 OK { id_token, access_token }
        AS->>AS: Parse and verify Google ID Token (iss, aud, exp)
        AS->>AS: Extract claims (google_sub, email, name, picture)

        %% Step 5: User Account Resolution
        AS->>AuthDB: SELECT * FROM users WHERE email = :email
        alt User account does not exist
            AS->>AuthDB: INSERT INTO users (id, email, full_name, role: 'CUSTOMER', enabled: true, provider: 'GOOGLE')
            AS->>Kafka: Publish user.registered.v1 { userId, email, fullName, role: 'CUSTOMER' }
            AS->>Audit: Log USER_REGISTERED (provider: GOOGLE)
        else User exists
            AS->>AuthDB: UPDATE users SET last_login = NOW() WHERE id = :id
            AS->>Audit: Log LOGIN_SUCCESS (provider: GOOGLE)
        end

        %% Step 6: Session Issuance & SPA Handoff
        AS->>AS: Generate 7-day Refresh Token (UUID)
        AS->>AuthDB: INSERT INTO refresh_tokens (user_id, token_hash, expires_at: +7d)
        AS->>AS: Generate 15-minute RS256 JWT Access Token (claims: sub, email, role, roles)
        AS-->>Customer: 302 Found (Location: http://localhost:4200/auth/callback?token=<accessToken>)<br/>Set-Cookie: refresh_token=<refreshToken>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800

        %% Step 7: SPA Finalization
        Customer->>Customer: Angular Route /auth/callback receives query token
        Customer->>Customer: Store Access Token in-memory (Signal Store)
        Customer->>Customer: Clean browser address bar (history.replaceState)
        Customer->>Customer: Redirect to Customer Dashboard (/customer)
    end
```

---

## Technical Notes

1. **CSRF Protection**: The `state` parameter generated at initiation is signed and matched against an `HttpOnly` cookie upon callback arrival to prevent OAuth CSRF injection.
2. **First-Time Users**: Google login auto-provisions a `CUSTOMER` account in `auth_db` and publishes `user.registered.v1` to Kafka, ensuring `integration-service` dispatches a welcome email.
3. **Token Storage (ADR 004)**: The 7-day refresh token is written as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie bound to `/api/v1/auth`. The 15-minute access token is held purely in-memory in the Angular client.
