# SwiftDrop Common Library Module (`common`)

**Version:** 0.1.0-SNAPSHOT  
**Owner:** Member M8  
**Module Directory:** `common/`

---

## 1. Overview

The `common` module is a shared Java library depended on by all SwiftDrop backend microservices (`auth-service`, `parcel-service`, `operations-service`, `integration-service`). It provides standard cross-cutting models, event schemas, security configuration, and diagnostic utilities.

To keep service footprints lightweight and prevent transitive dependency bloat, `common` is packaged as a plain JAR with Spring Web and Spring Security dependencies declared as `<optional>true</optional>`. Services only activate web and security features when their own POMs include the corresponding starters.

---

## 2. Contents of the Module

### 2.1 Core Models & Contracts
* **[Roles](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/Roles.java)**: Constants for the 5 system roles (`CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`, `ADMIN`) adhering to ADR 001.
* **[ErrorResponse](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/ErrorResponse.java)**: Standardized API error payload containing `code`, `message`, `correlationId`, and `timestamp`.
* **[EventEnvelope](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/EventEnvelope.java)**: Shared Kafka message envelope matching JSON Schema `contracts/events/event-envelope.schema.json` and ADR 005. Includes monotonic `sequence` and `previousCourierId` for tracking courier handoffs and workload adjustments.

### 2.2 Web & Observability
* **[CorrelationIdFilter](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/CorrelationIdFilter.java)**: Highest precedence filter extracting or generating `X-Correlation-Id`, propagating it in MDC logs and response headers.
* **[GlobalExceptionHandler](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/GlobalExceptionHandler.java)**: Controller advice converting validation errors, bad arguments, and unexpected exceptions into structured `ErrorResponse` objects with correlation IDs.

### 2.3 Security & JWT Resource Server (`com.swiftdrop.common.security`)
* **[JwtClaims](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/JwtClaims.java)**: Standard claim name constants (`sub`, `email`, `role`, `roles`, `merchantId`, `scope`).
* **[SwiftdropJwtGrantedAuthoritiesConverter](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/SwiftdropJwtGrantedAuthoritiesConverter.java)**: Converts JWT `role` or `roles` claims into Spring Security `GrantedAuthority` objects with the required `ROLE_` prefix (e.g. `"COURIER"` $\to$ `"ROLE_COURIER"`).
* **[CurrentUser](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/CurrentUser.java)**: Record and static context accessor for retrieving authenticated user details from `SecurityContextHolder`:
  * `CurrentUser.get()`: Returns `Optional<CurrentUser>`.
  * `CurrentUser.require()`: Returns `CurrentUser` or throws `AccessDeniedException`.
  * Role predicates: `isCourier()`, `isMerchant()`, `isAdmin()`, `isDispatcher()`, `isCustomer()`, and `hasRole(String)`.
  * Context attributes: `userId()`, `email()`, `role()`, `roles()`, `merchantId()`, `authorities()`.
* **[OwnershipCheck](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/OwnershipCheck.java)**: Enforces resource ownership rules across portals:
  * `isOwner(userId)` / `checkOwner(userId)`
  * `isCourierOwner(courierId)` / `checkCourierOwner(courierId)`: Verifies courier role and assigned ID (satisfies SRS CO-05).
  * `isMerchantOwner(merchantId)` / `checkMerchantOwner(merchantId)`: Verifies merchant ID (with Admin override).
  * `isOwnerOrAdmin(userId)` / `checkOwnerOrAdmin(userId)`: Permits owner or users with `ADMIN`/`DISPATCHER` roles.
* **[SwiftdropSecurityConfig](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/SwiftdropSecurityConfig.java)**: Auto-configuration that reads `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (or fallback `swiftdrop.security.jwks-url`), configures `NimbusJwtDecoder`, wires `JwtAuthenticationConverter`, and provisions a stateless default `SecurityFilterChain`.

### 2.4 Dev Token Support (`com.swiftdrop.common.security.dev`)
* **[DevJwtTokenMinter](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/dev/DevJwtTokenMinter.java)**: Developer-only component that mints RS256 JWTs using an embedded 2048-bit RSA key pair.
* **[DevSecurityConfig](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/common/src/main/java/com/swiftdrop/common/security/dev/DevSecurityConfig.java)**: Provides a fallback `JwtDecoder` in dev mode when no remote JWKS URL is configured.
* **Guard Rail**: Strictly guarded by `@Profile("dev")` and runtime environment assertions. It is impossible to load or execute in non-dev profiles.

---

## 3. How to Depend on `common`

In your service's `pom.xml`, include:

```xml
<dependency>
    <groupId>com.swiftdrop</groupId>
    <artifactId>common</artifactId>
    <version>${project.version}</version>
</dependency>
```

If your service exposes secured web endpoints:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

`common` components are automatically discovered via Spring Boot 3 auto-configuration (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), or can be explicitly imported via `@EnableSwiftdropSecurity`.

---

## 4. Running with the Dev Token Profile

When working locally without the `auth-service` JWKS endpoint running, run your service with the `dev` profile:

```bash
# Via Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or environment variable
SPRING_PROFILES_ACTIVE=dev java -jar target/operations-service.jar
```

In `dev` profile:
1. `DevSecurityConfig` registers a local `JwtDecoder` wired to `DevJwtTokenMinter`'s public key.
2. You can inject `DevJwtTokenMinter` in tests or dev controllers to generate bearer tokens:

```java
@Autowired
private DevJwtTokenMinter minter;

// Generate token for testing
String courierToken = minter.mintCourierToken("courier-uuid-123");
String adminToken = minter.mintAdminToken("admin-uuid-456");
String merchantToken = minter.mintMerchantToken("merchant-shop-789", "user-uuid-99");
```

---

## 5. Transition to M10's Production JWKS

In staging, production, or full docker-compose environments:
* **The `dev` profile is NOT active.** `DevJwtTokenMinter` and `DevSecurityConfig` are not loaded.
* Each service specifies the JWKS URL in `application.yml`:
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            jwk-set-uri: ${JWKS_URL:http://auth-service:8081/oauth2/jwks}
  ```
* When M10 delivers the production `auth-service` JWKS endpoint in Week 2, all services will seamlessly validate real user tokens issued by `auth-service` without requiring any changes to the `common` module.
