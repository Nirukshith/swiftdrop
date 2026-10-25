# SwiftDrop Parcel Delivery & Courier Management System: SRS & Project Plan

**Date:** Oct 21, 2026

---

## 1. Introduction

This document specifies the SwiftDrop Parcel Delivery & Courier Management System and splits the work equally across 10 team members, with a low-resource architecture that runs on one laptop.

* **Purpose:** Define what the system must do, how it is built, and who builds which part, so every member can work in parallel with clear interfaces.
* **Scope:** A web platform with four portals (Customer, Merchant, Courier, Dispatcher/Admin), a shared authentication service, a Kafka-based event backbone, a notification service, merchant webhooks, and an AI Delivery Assistant. Deployment is by docker-compose, with Kubernetes as the bonus target.
* **References:** The course assignment guideline (Parcel Delivery & Courier Management System, 20 marks, group of 10).

### Terminology

| Term | Meaning |
| :--- | :--- |
| **Parcel** | One delivery order with a public tracking number |
| **Merchant** | Partner online store that creates orders through the API |
| **Dispatcher** | Staff user who assigns parcels to couriers (also holds Admin rights) |
| **Event** | A Kafka message announcing something that already happened (e.g. status changed) |
| **BFF / Gateway** | A single entry point that routes browser calls to the backend services |
| **SSE** | Server-Sent Events, a one-way stream from server to browser for live tracking |

---

## 2. Overall Description

The assignment already holds enough work for 10 people, but some portals were thin, so this plan adds small, low-risk features marked (+) to even out the scope.

### 2.1 User Classes

| Role | Portal | Main Goals |
| :--- | :--- | :--- |
| **Customer** | Customer portal + public tracking page | Send parcels, see the fee, track live, download receipts |
| **Merchant** | Merchant portal + order API | Create orders from their store, receive webhooks |
| **Courier** | Courier portal (mobile-first) | See today's deliveries, update status with proof |
| **Dispatcher / Admin** | Admin portal | Assign parcels, watch workloads, manage zones, pricing, users, merchants |

### 2.2 Assumptions and Constraints

* The whole system must run on one developer laptop (target: 8 GB RAM free), so backends are kept to four and infrastructure to one Kafka node and one PostgreSQL instance.
* Email is mocked with Mailpit and SMS with a log-only mock; no paid providers.
* Maps are optional: addresses are text plus a Google Maps deep link, so no map API key is needed.
* The AI assistant uses one hosted LLM API with tool calling; its key comes from an environment variable.
* Only one status-change authority exists: the parcel service. All other services ask it to change status.

### 2.3 Features Added to Balance Scope (+)

| Area | Added Feature | Why It Is Safe |
| :--- | :--- | :--- |
| **Customer** | Saved addresses, cancel parcel before pickup, rate the delivery | Simple CRUD on the parcel service |
| **Dispatcher** | Filter queue by zone, courier availability toggle, CSV export of reports | Reads existing data |
| **Merchant** | Webhook delivery log with manual retry, rotate API credentials, bulk CSV order upload | Builds on the webhook and API features |
| **Courier** | Google Maps link per stop, daily summary, reattempt count | Reads data already stored |
| **Authentication** | Password reset (mock email), account lockout after failed logins, audit log | Standard security hardening |
| **AI Assistant** | Tools for tracking, fee estimate and recent parcels, also exposed as MCP tools | Calls existing REST APIs |

* **Priority Rule:** Every feature without (+) is Must-have. Features marked (+) are Should-have and are dropped first if time runs short.

---

## 3. Architecture

Four Spring Boot services sit behind one gateway and talk through Kafka, which keeps the project truly microservice-based while needing only four backend processes on one laptop.

### 3.1 Why This Shape
* **Client-server in three layers:** Angular is the presentation layer, the Spring services are the business logic layer, and PostgreSQL with Kafka form the data layer.
* **Four backends, not seven:** Services follow business capability (identity, parcel lifecycle, operations, external integration) instead of one per portal. Fewer JVMs means less RAM and fewer things to start for a demo.
* **Nginx as the gateway:** It does routing and blocks `/internal` paths without adding a fifth Java process.
* **One writer of parcel status:** Courier and dispatcher actions go through `parcel-service`, so two services can never disagree about a parcel's state.
* **Kafka between services:** Slow consumers (email, webhooks, reports) never delay a courier's update.
* **Easy to split later:** Operations and integration use separate packages and tables, so either can become two services if the team wants more later.

### 3.2 Design Patterns and Principles

| Pattern or Principle | Where It Is Applied |
| :--- | :--- |
| **Repository** | Spring Data repositories hide database access in every service |
| **Strategy** | Fee calculation by weight, size and zone (M5) |
| **State** | Parcel status transitions and their allowed moves (M1) |
| **Observer (Pub/Sub)** | Kafka events for notifications, webhooks, reports and live tracking |
| **Adapter** | `NotificationChannel` for email and SMS, and LLM client behind one interface (M3) |
| **DTO and Mapper** | API objects separate from database entities, so no internal fields leak |
| **Single Responsibility, DRY** | One package per capability; shared error format and validation helpers, no copied logic |
| **Dependency Inversion** | Services depend on interfaces, so tests can use fakes for Kafka, mail and LLM |

---

## 4. Finalized Tech Stack

* **Frontend Framework:** Angular 20 (standalone components, signals) + TypeScript + Angular CLI.
* **Frontend Libraries:** Angular Router, RxJS and signals, HttpClient with interceptors, Reactive Forms, Angular CDK, Tailwind CSS, ng2-charts (Chart.js).
* **Frontend Tests:** Jest (`jest-preset-angular`) + Angular Testing Library, MSW for mock APIs.
* **Gateway:** Nginx 1.27 (`docker-compose`); `ingress-nginx` (Kubernetes). Path-based routing, blocks `/internal/**`.
* **Backend Language & Build:** Java 21, Spring Boot 3.3/3.4, Maven.
* **Backend Libraries:** Spring Web, Spring Security (OAuth2 resource server), Spring Data JPA, Bean Validation, Spring Kafka, Spring Mail, Flyway, springdoc-openapi, MapStruct, Lombok.
* **API Style:** REST + JSON, versioned `/api/v1`, standard error format from shared `common` module.
* **Authentication:** JWT signed RS256 (access 15 min, refresh 7 days), JWKS endpoint, BCrypt, Google OAuth 2.0 authorization code flow, OAuth 2.0 client credentials for merchants.
* **Messaging:** Apache Kafka in KRaft mode, one broker, JSON messages, `@RetryableTopic` retry and dead-letter.
* **Database:** PostgreSQL 16, one instance, one database per service (`auth_db`, `parcel_db`, `ops_db`, `integration_db`) with Flyway migrations.
* **Real Time:** Server-Sent Events (`SseEmitter`) from `parcel-service`.
* **File Storage:** Local volume `/data/proofs` for delivery proof photos.
* **PDF Receipts:** OpenPDF in `parcel-service`.
* **Notifications:** Mailpit (mock SMTP) and log-only mock SMS channel.
* **AI Assistant:** Claude API with tool use; tools also exposed as MCP server; provider hidden behind `LlmClient` interface (`LLM_API_KEY`).
* **Backend Tests:** JUnit 5, Mockito, Spring Security Test, Testcontainers (PostgreSQL, Kafka), WireMock, JaCoCo (70% line coverage target).
* **Containers:** Docker, `docker-compose` with profiles `infra`, `core`, `full`.

---

## 5. Kafka Event-Driven Design

### 5.1 Topics

| Topic | Key | Producer | Consumers (Consumer Group) |
| :--- | :--- | :--- | :--- |
| `parcel.created.v1` | `parcelId` | `parcel-service` | `integration-service` (notifications), `operations-service` (reports) |
| `parcel.assigned.v1` | `parcelId` | `parcel-service` | `integration-service` (notifications), `operations-service` (workload) |
| `parcel.status-changed.v1` | `parcelId` | `parcel-service` | `parcel-service` (live tracking), `integration-service` (notifications, webhooks), `operations-service` (reports) |
| `user.registered.v1` | `userId` | `auth-service` | `integration-service` (welcome & reset emails), `operations-service` (creates courier profile) |
| `*.retry` and `*.dlt` | same | Spring Kafka | Retry and dead-letter handling for webhooks and notifications |

### 5.2 Event Envelope (JSON)

Every event carries:
* `eventId` (UUID)
* `eventType` (String)
* `occurredAt` (Instant / ISO timestamp)
* `parcelId` (UUID)
* `trackingNumber` (String)
* `fromStatus` (String)
* `toStatus` (String)
* `courierId` (UUID, nullable)
* `merchantId` (UUID, nullable)
* `customerId` (UUID, nullable)
* `reason` (String, optional)

Consumers store `eventId` for idempotency (repeated message does nothing).

### 5.3 Parcel Status Lifecycle

```text
CREATED ──► PICKED_UP ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED
   │                                                    └──► FAILED
   └──► CANCELLED (Customer or Merchant before pickup)
```

* **Assignment:** Sets courier on a `CREATED` parcel without changing status.
* **Re-queue:** A dispatcher may re-queue a `FAILED` parcel, returning it to `CREATED` with attempt count incremented.
* The parcel service rejects any other jump (returns `409 Conflict`).

### 5.4 Integration Patterns

* **Publish/Subscribe:** Status events to notifications, webhooks, reports, live tracking.
* **Request/Reply (REST):** `parcel-service` asks `operations-service` for price quote; `operations-service` asks `parcel-service` to assign or change status.
* **Retry + DLT:** Webhook delivery with exponential backoff, 5 attempts.
* **Competing Consumers:** Each service reads the same topic with its own consumer group.
* **Event-Carried State:** Reports and workload tables built from events (no cross-service DB joins).
* **SSE:** Live parcel tracking pushed to browser.

---

## 6. Functional Requirements by Portal

### 6.1 Customer Portal (M1, M2, M3)
* `CU-01` [M]: Sign up, log in, Google login.
* `CU-02` [M]: Create delivery request (pickup, drop-off, size, weight).
* `CU-03` [M]: Show estimated fee before confirmation.
* `CU-04` [M]: Live tracking via SSE.
* `CU-05` [M]: Delivery history with filters, PDF receipt download.
* `CU-06` [M]: Public tracking page by tracking number (no login).
* `CU-07` [S]: Saved addresses, cancel before pickup, rate delivery (+).
* `CU-08` [M]: Email / SMS notification on status changes.
* `CU-09` [M bonus]: AI assistant chat widget.
* `CU-10` [S]: Assistant tools exposed as MCP server (+).

### 6.2 Dispatcher / Admin Portal (M4, M5)
* `DI-01` [M]: List unassigned parcels, filter by zone and date.
* `DI-02` [M]: Assign a parcel to a courier; reassign before pickup.
* `DI-03` [M]: Courier workload view: active deliveries per courier, live.
* `DI-04` [M]: Manage delivery zones and pricing rules.
* `DI-05` [M]: Reports: deliveries per day, success and failure rate charts.
* `DI-06` [S]: Courier availability toggle, bulk assign, CSV export of reports (+).
* `DI-07` [S]: Re-queue a FAILED parcel for another attempt (+).
* User management (list, enable, disable) is built by M4; merchant approval by M5.

### 6.3 Merchant Portal (M6, M7)
* `ME-01` [M]: Register merchant account (stays `PENDING` until admin approves).
* `ME-02` [M]: Generate OAuth 2.0 client credentials.
* `ME-03` [M]: Secured order API under `/api/v1`.
* `ME-04` [M]: Webhook URL setting with HMAC-SHA256 signature and retry.
* `ME-05` [M]: Dashboard listing all orders and current states.
* `ME-06` [S]: Webhook delivery log with manual retry, credential rotation, bulk CSV upload (+).
* `ME-07` [M]: Admin page to list pending merchants and approve/reject.

### 6.4 Courier Portal (M8, M9)
* `CO-01` [M]: Login (role `COURIER` only).
* `CO-02` [M]: List today's assigned deliveries (mobile cards).
* `CO-03` [M]: Update status step by step with proof (recipient name or photo upload).
* `CO-04` [M]: Mark delivery FAILED with required reason.
* `CO-05` [M]: A courier can only see and update their own deliveries.
* `CO-06` [S]: Google Maps link per stop, daily summary, reattempt count (+).

### 6.5 Authentication and Platform (M10, M3, M4, M7, M8)
* `AU-01` [M]: Register and log in with BCrypt passwords, short-lived JWT, refresh token.
* `AU-02` [M]: Google login through OAuth 2.0 authorization code flow.
* `AU-03` [M]: Roles `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`, `ADMIN` carried in JWT; JWKS endpoint.
* `AU-04` [M]: OAuth 2.0 client-credentials token endpoint for merchants.
* `AU-05` [M]: Admin user management API and page: list, create courier or dispatcher, enable, disable.
* `AU-06` [S]: Password reset, account lockout after repeated failures, audit log (+).
* `PL-01` [M]: docker-compose with profiles (`infra`, `core`, `full`) and `.env` template.
* `PL-02` [Bonus]: Kubernetes manifests and Helm chart.
* `PL-03` [M]: Shared frontend auth library: login pages, route guards, HTTP interceptor.

---

## 7. Non-Functional Requirements & Security

* `NF-01` Performance: API calls under 500 ms locally; live tracking update within 2s.
* `NF-02` Resource use: Full stack under 4 GB RAM.
* `NF-03` Reliability: Failed webhooks retried 5 times with backoff, then DLT.
* `NF-04` Usability: Responsive UI, public tracking and courier portal usable at 360px width.
* `NF-05` Maintainability: Layered code, central error handler, environment variable configs.
* `NF-06` Observability: `/actuator/health` on every service; correlation ID per request.
* **Security:** BCrypt password hashing; role-based access control; JWT verification; no secrets in logs or repo; HMAC webhook signing; internal endpoints `/internal/**` blocked by Nginx.

---

## 8. Scope Division for 10 Members

### 8.1 Summary Table

| Member | Feature Area | Frontend Slice | Backend Slice | Platform / Shared | Points |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **M1** | Customer: parcel creation & core domain | Shared UI kit, delivery wizard, fee preview, address book (+), cancel (+) | `parcel-service`: core, state machine, addresses, internal API, Kafka producer | — | 21.5 |
| **M2** | Customer: tracking, history & receipts | Dashboard, parcel detail with SSE timeline, public tracking, history table, receipt download | `parcel-service`: tracking, history, PDF receipt, SSE stream consumer | Ingress & Helm chart | 21.5 |
| **M3** | Customer: notifications, AI assistant, pwd reset | Chat widget, notification bell (+), forgot/reset password pages | `integration-service`: notifications, assistant, MCP; `auth-service`: reset package | — | 22.0 |
| **M4** | Dispatcher: assignment, workload, user management | Unassigned queue, assign dialog, bulk assign (+), workload board, failed list (+), user management page | `operations-service`: `dispatch` package; `auth-service`: `admin` package | — | 22.0 |
| **M5** | Dispatcher: zones, pricing, reports, admin shell | Admin shell, zone & pricing forms, reports with charts (+), merchant approval page | `operations-service`: `zones`, `pricing`, `reports` packages | — | 22.0 |
| **M6** | Merchant: onboarding & order API | Registration form, API credentials page, bulk CSV upload (+) | `integration-service`: `merchant` package, order API | — | 20.5 |
| **M7** | Merchant: dashboard, webhooks, login lockout | Orders dashboard, webhook settings & delivery log (+), test event | `integration-service`: `webhook` package; `auth-service`: `security` package (lockout) | — | 21.0 |
| **M8** | Courier: delivery execution | Today's deliveries cards, status stepper, delivered form with photo proof | `operations-service`: `courier.execution`; `auth-service`: `audit` package | `common` library module | 21.0 |
| **M9** | Courier: failures, profile, summary & platform | Failed form, daily summary, availability toggle, profile page | `operations-service`: `courier.profile` package, `CourierDirectory` | Docker, Compose, Nginx, CI pipeline | 21.5 |
| **M10** | Authentication & Kubernetes | Login/signup, route guards, HTTP interceptor with refresh | `auth-service`: core, JWT, OAuth2, user registration event | Kubernetes manifests | 22.0 |

### 8.2 Backend Service Ownership & Package Isolation

* **`auth-service` (8081, `auth_db`):**
  * M10: core (login, register, JWT, JWKS, Google OAuth, client credentials)
  * M3: `reset` package
  * M4: `admin` package (user management API)
  * M7: `security` package (`LoginGuard`, lockout)
  * M8: `audit` package (`AuditService`, audit log)
* **`parcel-service` (8082, `parcel_db`):**
  * M1: `parcel`, `address`, `internal` packages
  * M2: `tracking`, `receipt` packages
* **`operations-service` (8083, `ops_db`):**
  * M4: `dispatch` package (unassigned list, assign, bulk assign, re-queue, workload)
  * M5: `zones`, `pricing`, `reports` packages
  * M8: `courier.execution` package
  * M9: `courier.profile` package (`CourierDirectory`)
* **`integration-service` (8084, `integration_db`):**
  * M3: `notification`, `assistant` packages
  * M6: `merchant` package
  * M7: `webhook` package
* **`common` (shared module):**
  * M8: roles, error response, event envelope, correlation ID filter

### 8.3 Inter-Service Contracts

| Contract / Endpoint | Provider | Consumer | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| `PUT /internal/v1/parcels/{id}/assignment` | M1 (`parcel-service`) | M4 (`operations-service`) | REST | Assign courier to parcel (returns 409 if already assigned) |
| `GET /internal/v1/parcels?unassigned=true&zone=` | M1 (`parcel-service`) | M4 (`operations-service`) | REST | Query unassigned parcels queue |
| `PATCH /internal/v1/parcels/{id}/status` | M1 (`parcel-service`) | M8, M9 (`operations-service`) | REST | Update parcel status |
| `parcel.assigned.v1` | M1 (`parcel-service`) | M4 (`operations-service`) | Kafka | Increment courier active workload |
| `parcel.status-changed.v1` | M1 (`parcel-service`) | M4 (`operations-service`) | Kafka | Decrement on `DELIVERED`, `FAILED`, `CANCELLED` |
| `CourierDirectory` (interface) | M9 (`operations-service`) | M4 (`operations-service`) | In-process | Query courier profile and availability |
| `GET /api/v1/admin/users`<br>`POST /api/v1/admin/users`<br>`PATCH /api/v1/admin/users/{id}` | M4 (`auth-service`) | M4 (Admin UI) | REST | List users, create courier/dispatcher, enable/disable |
| `user.registered.v1` | M10 & M4 (`auth-service`) | M3, M9 (`operations-service`) | Kafka | Published when admin creates courier or dispatcher |

---

## 9. Plan, Risks and Submission Checklist

* **Week 1: Foundations:** Team design diagrams (use cases, ER, sequence, architecture), OpenAPI specs and event schemas in `contracts/`, `infra` profile up.
* **Weeks 2–3: Vertical Slices:** Members build individual slices against stubbed interfaces.
* **Week 4: First Integration:** End-to-end event flow across services.
* **Weeks 5–6: Feature Completion:** Real-time tracking, receipts, reports, proof upload, webhooks, AI assistant, (+) features.
* **Week 7: Hardening:** Security tests, 70% JaCoCo coverage, Kubernetes deployment, cleanup.
* **Week 8: Demo Preparation:** Rehearsal on single laptop.
