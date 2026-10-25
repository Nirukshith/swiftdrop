# SwiftDrop Authentication Service (`auth-service`)

**Port:** `8081`  
**Database:** `auth_db` (PostgreSQL 16, port 5432 / local 5434)  
**Primary Owner:** Member M10  
**Contributing Members:** M3 (Password Reset), M4 (Admin User Management), M7 (Security & Lockout), M8 (Audit)

---

## 1. Overview

`auth-service` manages identity, authentication, authorization credentials, and token issuance for the SwiftDrop microservices ecosystem. It provides asymmetric RS256 token signing, serves the public JWKS verification key set (`/.well-known/jwks.json`), manages refresh token lifecycles with secure cookies, orchestrates Google OAuth 2.0 login, and provisions internal service-to-service users and clients.

---

## 2. Package Architecture & Team Ownership

To prevent merge conflicts across members collaborating within this service, code is partitioned into strict packages by team capability:

| Package | Owner | Primary Responsibility |
| :--- | :--- | :--- |
| `com.swiftdrop.auth.core` | **M10** | Core registration, login, refresh rotation, RS256 `JwtIssuer`, `/.well-known/jwks.json` controller, Google OAuth 2.0 flow, and OAuth 2.0 client credentials. |
| `com.swiftdrop.auth.reset` | **M3** | Self-service password recovery (`/api/v1/auth/password/forgot`, `/api/v1/auth/password/reset`), email token dispatch via Mailpit mock SMTP. |
| `com.swiftdrop.auth.admin` | **M4** | Administrative user management (`/api/v1/admin/users`), staff account provisioning (`COURIER`, `DISPATCHER`), and account enable/disable toggling. |
| `com.swiftdrop.auth.security` | **M7** | Web security configuration, rate-limiting, brute-force defense, and the `LoginGuard` interface. |
| `com.swiftdrop.auth.audit` | **M8** | System security auditing, audit log table migrations, audit retrieval API, and the `AuditService` interface. |

---

## 3. In-Process Service Interfaces

Different feature packages within `auth-service` communicate via decoupled, in-process Java interfaces defined in their respective packages.

### 3.1 `LoginGuard` (Owned by M7)
- **Package:** `com.swiftdrop.auth.security.LoginGuard`
- **Purpose:** Protects the login endpoint against credential stuffing and brute-force attacks by tracking consecutive failures per email/IP and enforcing temporary lockout.
- **Contract:**
  ```java
  package com.swiftdrop.auth.security;

  public interface LoginGuard {

      /**
       * Checks whether the account or IP is currently locked out.
       * @param email user email attempting authentication
       * @throws AccountLockedException if maximum failed attempts exceeded
       */
      void checkLoginAllowed(String email);

      /**
       * Records a successful login, resetting failure counters for the identifier.
       * @param email user email
       */
      void recordSuccess(String email);

      /**
       * Records a failed login attempt, incrementing counters and locking if threshold reached.
       * @param email user email
       */
      void recordFailure(String email);
  }
  ```
- **Consumer:** Invoked by M10's login handler in `com.swiftdrop.auth.core`.

---

### 3.2 `AuditService` (Owned by M8)
- **Package:** `com.swiftdrop.auth.audit.AuditService`
- **Purpose:** Records asynchronous security and compliance audit logs into `auth_db.audit_logs`.
- **Contract:**
  ```java
  package com.swiftdrop.auth.audit;

  import java.time.Instant;

  public interface AuditService {

      /**
       * Records an audit log entry for authentication and administrative actions.
       * @param eventType type of event (e.g. LOGIN_SUCCESS, LOGIN_FAILURE, USER_LOCKED, USER_CREATED, ROLE_CHANGED)
       * @param userId ID of the affected user (if known)
       * @param actorId ID of the actor initiating the action (or system/anonymous)
       * @param ipAddress client remote IP address
       * @param details contextual metadata or error summary
       */
      void recordEvent(String eventType, String userId, String actorId, String ipAddress, String details);
  }
  ```
- **Consumer:** Invoked across `com.swiftdrop.auth.core` (login, register), `com.swiftdrop.auth.reset` (password reset), `com.swiftdrop.auth.admin` (staff creation, disable), and `com.swiftdrop.auth.security` (lockout triggered).

---

## 4. Configuration & Profiles

- **`local`**: Connects to infrastructure running via `docker-compose --profile infra` (PostgreSQL on port `5434`, Kafka on `localhost:29092`).
- **`dev`**: Activates fixed development RSA signing keys (`swiftdrop-dev-key`) matching `DevJwtTokenMinter`.
- **`docker`**: Used inside container network behind the Nginx gateway (`host.docker.internal:8081`).

---

## 5. Build & Verification

```zsh
# Verify auth-service and its dependencies
mvn -B verify -pl services/auth-service -am

# Run locally
mvn spring-boot:run -pl services/auth-service -Dspring-boot.run.profiles=local
```
