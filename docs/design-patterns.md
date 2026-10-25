# SwiftDrop System-Wide Software Design Patterns & Architectural Idioms

**Author:** Member M7 (Design Patterns Note Lead)  
**Status:** Approved / Active Documentation  
**Alignment:** SRS Section 3.2 (Design Patterns and Principles), SRS Section 5 (Kafka Event-Driven Design), ADR 005 (Event Envelope & Monotonic Sequence).

---

## 1. Overview

To maintain code clarity, parallel developer independence, high testability, and resilience across our 10-person microservice team, the SwiftDrop platform consistently applies established object-oriented and distributed systems design patterns. This document catalogues where each pattern is implemented, the problem it solves, and the primary team owner.

---

## 2. Summary of Patterns Across Services

| Pattern | Category | Service / Layer | Primary Owner | Concrete Application |
| :--- | :--- | :--- | :--- | :--- |
| **Strategy** | Behavioral | `operations-service` | M5 | Dynamic parcel pricing calculation based on weight, volume, and delivery zone |
| **State / State Machine** | Behavioral | `parcel-service` | M1 | Parcel lifecycle state transitions and move validation |
| **Observer (Pub/Sub)** | Behavioral / Messaging | Kafka Backbone | M1, M3, M7, M10 | Event-driven notification, webhook delivery, and tracking broadcast |
| **Idempotent Consumer** | Messaging / Reliability | `integration-service`, `operations-service` | M2, M3, M5, M7 | Deduplication via `eventId` and out-of-order rejection using `sequence` |
| **Adapter** | Structural | `integration-service` | M3 | Unified interfaces for `LlmClient` (Claude API) and `NotificationChannel` (Email/SMS) |
| **Null Object / Default Interface** | Creational / Behavioral | `auth-service` | M7 | `NoOpLoginGuard` enabling M10 to develop auth controllers without waiting |
| **API Gateway / BFF** | Architectural | Nginx Gateway | M9 | Unified routing, SSL termination, and blocking of `/internal/**` routes |
| **Repository** | Data Access | All Spring Boot Services | All Members | Spring Data JPA repositories encapsulating relational queries |
| **DTO & Mapper** | Structural | All Services | All Members | MapStruct mappers isolating persistence entities from REST contracts |
| **Post-Commit Publisher** | Transactional / Messaging | `parcel-service`, `auth-service` | M1, M10 | Publishing Kafka events strictly post-commit to eliminate phantom messages |
| **Retry & Dead-Letter (DLT)** | Resilience | `integration-service`, Kafka | M3, M7 | Non-blocking exponential retry and DLT for webhook and notification delivery |

---

## 3. Detailed Pattern Applications

### 3.1 Strategy Pattern (Pricing Engine)
* **Location:** `com.swiftdrop.operations.pricing` (`operations-service`)
* **Owner:** Member M5
* **Problem:** Delivery fees depend on multiple dynamic factors: base cost, weight brackets, dimensional volume, and cross-zone tariffs (ADR 003). Hardcoding rules creates brittle, monolithic switch statements.
* **Implementation:**
  * Defines a `PricingStrategy` interface:
    ```java
    public interface PricingStrategy {
        BigDecimal calculateFee(ParcelDimension dimension, BigDecimal weightKg, Zone origin, Zone destination);
    }
    ```
  * Multiple interchangeable strategy implementations:
    * `StandardZonePricingStrategy`: Standard weight + zone distance calculation.
    * `ExpressSameDayPricingStrategy`: Multiplier-based urgent courier pricing.
    * `BulkyItemPricingStrategy`: Volumetric weight pricing.
  * A `PricingCalculatorService` dynamically resolves the applicable strategy at quote time.

---

### 3.2 State Pattern / Finite State Machine (Parcel Lifecycle)
* **Location:** `com.swiftdrop.parcel.core.state` (`parcel-service`)
* **Owner:** Member M1
* **Problem:** Parcels must follow a strictly controlled lifecycle (`CREATED` $\to$ `PICKED_UP` $\to$ `IN_TRANSIT` $\to$ `OUT_FOR_DELIVERY` $\to$ `DELIVERED` or `FAILED`/`CANCELLED`). Direct status mutation risks invalid jumps (e.g., jumping from `CREATED` directly to `DELIVERED`).
* **Implementation:**
  * Enforces state machine transitions centrally in `parcel-service`. Any illegal jump immediately throws an `IllegalStateTransitionException` returning HTTP `409 Conflict`.
  * Status changes emit `parcel.status-changed.v1` with `fromStatus` and `toStatus`.

---

### 3.3 Observer / Publish-Subscribe Pattern (Kafka Event Backbone)
* **Location:** Entire platform (Kafka Broker, Port 29092)
* **Owners:** M1, M3, M7, M10
* **Problem:** Synchronous REST calls between services for notifications, merchant webhooks, and analytics cause tight coupling, slow response times, and cascading failures.
* **Implementation:**
  * `parcel-service` simply announces domain facts (`parcel.created.v1`, `parcel.status-changed.v1`).
  * Multiple independent observers (consumers) subscribe via distinct consumer groups (`integration-notification`, `integration-webhook`, `operations-reports`) and process events asynchronously.

---

### 3.4 Idempotent Consumer Pattern (Duplicate & Out-of-Order Handling)
* **Location:** `integration-service` (Webhooks, Notifications), `operations-service` (Reports)
* **Owners:** M2, M3, M5, M7
* **Problem:** Network retransmissions and Kafka consumer rebalances cause duplicate messages. Concurrent partition rebalancing can also deliver messages out of chronological order.
* **Implementation:**
  * **Event ID Deduplication:** Consumers insert `eventId` into a local database table (`processed_events`). Repeated message IDs are recognized as duplicates and committed without re-processing.
  * **Sequence Guard (ADR 005):** Downstream consumers and merchant webhooks check the monotonic `sequence` integer. Events with `sequence <= lastProcessedSequence` are dropped to prevent stale state overwrites.

---

### 3.5 Adapter Pattern (LLM Client & Notification Channels)
* **Location:** `com.swiftdrop.integration.assistant` & `com.swiftdrop.integration.notification`
* **Owner:** Member M3
* **Problem:** Third-party APIs (Anthropic Claude API for AI Assistant, Mailpit for mock SMTP, log-only SMS) have specialized protocols and SDKs that should not pollute business logic.
* **Implementation:**
  * **`LlmClient` Interface:** Abstracts chat completions and tool invocations behind a uniform contract. Testing fakes can run without live API keys or network calls.
  * **`NotificationChannel` Interface:** Exposes `send(NotificationMessage message)`, implemented by `EmailNotificationChannel` (JavaMailSender / Mailpit) and `SmsNotificationChannel` (log mock).

---

### 3.6 Null Object / Default Interface Pattern (`LoginGuard`)
* **Location:** `com.swiftdrop.auth.security` (`auth-service`)
* **Owner:** Member M7
* **Problem:** M10 needs to implement and unit test the core authentication controller in Week 1, but M7's lockout caching and sliding-window logic is developed concurrently.
* **Implementation:**
  * M7 provides `NoOpLoginGuard` as a Spring `@Component` with `@ConditionalOnMissingBean`.
  * M10 injects `LoginGuard` without checking for null pointers or waiting for M7's production storage implementation.

---

### 3.7 API Gateway / Backend-For-Frontend (BFF) Pattern
* **Location:** `deploy/nginx/nginx.conf`
* **Owner:** Member M9
* **Problem:** Browser SPAs should not communicate directly with 4 distinct microservice backend ports (`8081`, `8082`, `8083`, `8084`), and internal service APIs (`/internal/**`) must never be accessible from the public internet.
* **Implementation:**
  * Single entry point on port `80` (or `443`). Routes `/api/v1/auth/**` to `auth-service`, `/api/v1/parcels/**` to `parcel-service`, `/api/v1/operations/**` to `operations-service`, and `/api/v1/integration/**` to `integration-service`.
  * Explicitly blocks all incoming traffic to `/internal/**` with HTTP `403/404`.

---

### 3.8 Post-Commit Publishing Pattern (Reliable Outbox)
* **Location:** `parcel-service`, `auth-service`
* **Owners:** M1, M10, M8
* **Problem:** Publishing a Kafka message inside a database transaction risks "phantom events" if the database transaction subsequently rolls back.
* **Implementation:**
  * Events are published using Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
  * Guarantees messages are dispatched to Kafka only when the local database transaction has durably committed.
