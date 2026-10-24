# 004 - Token Storage

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
Storing authentication tokens in browser `localStorage` or `sessionStorage` exposes sensitive sessions to Cross-Site Scripting (XSS) attacks. A robust, browser-secure token handling strategy was needed to protect long-lived credentials while supporting short-lived access tokens and automatic rotation.

## Decision
- **Access token**: Stored in memory only (within the Angular client runtime/signal store).
- **Refresh token**: Stored in an `httpOnly`, `Secure`, `SameSite` cookie issued and managed by `auth-service`.

## Consequences
- **M10**: Configures `auth-service` endpoints (`/login`, `/refresh`, `/logout`) to issue the refresh token via an `httpOnly`, `Secure`, `SameSite` cookie and return the short-lived access token in the JSON response; builds the `shared-auth` HTTP interceptor to retain the access token in memory and seamlessly handle silent refresh on 401 responses.
- **M1, M2, M3, M4, M5, M6, M7, M8**: All frontend portals import and strictly use the `shared-auth` library; no application code may persist tokens in `localStorage` or `sessionStorage`.
- **M9**: Configures Nginx reverse proxy headers to ensure `Cookie` and `Set-Cookie` headers are properly forwarded between the client and `auth-service`.
- **M6, M7**: Merchant server-to-server integrations using OAuth 2.0 client credentials transmit bearer tokens via standard `Authorization: Bearer <token>` headers without cookie dependency.

## Open items
- None.
