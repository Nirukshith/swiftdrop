# SwiftDrop Shared Authentication Library (`shared-auth`)

**Package:** `@swiftdrop/shared-auth`  
**Owner:** Member M10  
**Angular Version:** 21 / Standalone Components  

---

## 1. Overview

`shared-auth` is the centralized Angular authentication library for the SwiftDrop multi-portal frontend. It implements:
- In-memory JWT access token management complying with **ADR-004** (zero storage in `localStorage` or `sessionStorage`).
- Seamless session restoration and refresh rotation via `HttpOnly` cookie exchange with `auth-service`.
- Functional HTTP interceptor (`authInterceptor`) injecting Bearer authorization tokens with cross-origin leakage defense.
- Functional route guard (`authGuard`) enforcing role-based client-side authorization.
- Reusable UI components for login, customer registration, Google OAuth 2.0 callback, forgot password, and password reset.

---

## 2. Components & Architecture

### 2.1 Reusable UI Components
- **`LoginComponent` (`/auth/login`):** Email and password authentication with validation, error toast integration, and "Continue with Google" initiation.
- **`RegisterComponent` (`/auth/register`):** Customer self-service onboarding with client-side validation.
- **`CallbackComponent` (`/auth/callback`):** Handles Google OAuth 2.0 session handoff. Calls `AuthService.restoreSession()` upon landing, retrieves the in-memory access token, and navigates to the authorized dashboard.
- **`ForgotPasswordComponent` (`/auth/forgot-password`):** Self-service password recovery initiation (owned by M3).
- **`ResetPasswordComponent` (`/auth/reset-password`):** Recovery token validation and password reset form (owned by M3).

### 2.2 Core Services
- **`AuthService`:** Manages authentication signals (`currentUser`, `isAuthenticated`, `accessToken`), executes login/register/logout/refresh requests, and provides `hasRole(role)` and `hasAnyRole(roles)` authorization utilities.
- **`authGuard`:** Route guard inspecting `authService.currentUser()`. Redirects unauthenticated users to `/auth/login` and unauthorized roles to their respective portal root.
- **`authInterceptor`:** Attaches `Authorization: Bearer <token>` to outbound HTTP requests matching the application origin or gateway. Strictly prevents token injection to external third-party URLs.

---

## 3. ADR-004 Token Storage & Security Design

Per **ADR-004 (Token Storage and Session Management)**:
1. **Access Token:** Stored exclusively in TypeScript memory (`AuthService._accessToken` signal). Tokens are lost upon full page reload and automatically re-acquired via background refresh.
2. **Refresh Token:** Stored in an `HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth` cookie issued by `auth-service`. Inaccessible to browser JavaScript, mitigating XSS token theft.
3. **Session Restoration:** When the frontend initializes or reloads, `AuthService.restoreSession()` calls `POST /api/v1/auth/refresh` with `withCredentials: true`. The backend verifies the refresh cookie, rotates the token, and returns a fresh in-memory access token.

---

## 4. Verification & Testing

```bash
# Run unit tests
npm test -- --watch=false --browsers=ChromeHeadless

# Build shared-auth library
ng build shared-auth
```
