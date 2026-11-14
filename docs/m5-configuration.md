# SwiftDrop — M5 Operations, Pricing, and Reporting Configuration Guide

This document details the configuration, deployment, runtime parameters, and environment variables for Member M5's owned area: delivery zones, dynamic pricing calculation, and daily reporting dashboards within `operations-service` and the frontend web workspace.

---

## 1. Architectural Scope & Service Boundaries

Member M5 owns:
* **Backend (`operations-service`):**
  * `com.swiftdrop.operations.pricing`: Quote calculation strategies, internal quote API, pricing rule management.
  * `com.swiftdrop.operations.zones`: Delivery zone definitions, canonical Sri Lankan district validation.
  * `com.swiftdrop.operations.reports`: Daily report event consumers, idempotent aggregation projections, daily JSON and CSV report APIs.
* **Database (`ops_db`):**
  * Tables: `zones`, `zone_districts`, `pricing_rules`, `audit_log`, `daily_report_aggregate`, `report_processed_event`, `report_parcel_sequence`.
  * Flyway migrations: `V4__pricing_zones_audit.sql`, `V6__create_parcel_sequence.sql`, `V7__create_daily_reporting_tables.sql`.
* **Frontend (`projects/web`):**
  * Admin Reporting Dashboard: `/admin/reports` (`reports.component.ts`, `reports.component.html`).
  * Dispatcher Zone Management: `/dispatch/zones` (`dispatch-zones.component.ts`).
  * Dispatcher Pricing Management: `/dispatch/pricing` (`dispatch-pricing.component.ts`).
  * Admin Shell Integration: `/admin` (`admin.component.html`).

---

## 2. Ports and Network Topology

In both local development and Docker Compose environments, traffic routes according to the following topology:

| Service / Container | Internal Port | Host Port | Routing / Gateway Rule | M5 Capabilities Exposed |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway (Nginx)** | `80` | `8080` | Routes `/api/v1/admin/zones/**`, `/api/v1/admin/pricing-rules/**`, `/api/v1/admin/reports/**` to `operations-service`.<br>**Blocks `/internal/**` (returns 404).** | Public external endpoints for zones, pricing rules, and daily reports. |
| **`operations-service`** | `8083` | `8083` | Accessible directly on service network; internal endpoints consumed by sibling services. | Internal quote API `GET /internal/v1/pricing/quote`; Zone/Pricing/Report management. |
| **`parcel-service`** | `8082` | `8082` | Invokes `operations-service:8083` for quotes during parcel creation; emits Kafka events upon status changes. | Consumer of pricing quotes; producer of `parcel.created.v1` and `parcel.status-changed.v1`. |
| **`auth-service`** | `8081` | `8081` | Issues RS256 JWTs with `ROLE_ADMIN` and `ROLE_DISPATCHER`; exposes JWKS at `/.well-known/jwks.json`. | Identity provider and token issuer. |
| **PostgreSQL 16** | `5432` | `5434` (Compose) | Isolated logical database: `ops_db`. | Relational storage for zones, rules, and daily aggregate projections. |
| **Apache Kafka 3.8.1** | `9092` (Docker internal) | `29092` (Host external) / `9092` (Container) | Topics: `parcel.created.v1`, `parcel.status-changed.v1`. | Event streams driving the daily delivery reports projection. |
| **Web Frontend** | `80` (Docker) | `4200` (Dev) / `8080` (Gateway) | Serves Angular UI bundle through reverse proxy. | Delivery Zones (`/dispatch/zones`), Pricing Rules (`/dispatch/pricing`), Reports (`/admin/reports`). |

---

## 3. Environment Variable Reference

All configuration keys support environment variable overrides. Variables should be supplied via container environment, shell exports, or a local `.env` file (never committed to version control).

### 3.1 Service-to-Service Pricing Authentication

| Variable | Target Property | Default / Example Value | Description |
| :--- | :--- | :--- | :--- |
| `INTERNAL_PRICING_SERVICE_TOKEN` | `swiftdrop.security.internal.tokens.pricing-service` | *(Supplied securely)* | Shared secret token presented in the `X-Internal-Service-Token` HTTP header by internal services (e.g. `parcel-service`) when calling `GET /internal/v1/pricing/quote`. |
| `SWIFTDROP_PRICING_QUOTE_URL` | `swiftdrop.pricing.quote-url` | `http://operations-service:8083/internal/v1/pricing/quote` | Configured in `parcel-service` to specify the internal quote URL on `operations-service`. |

#### Safe Setup Procedure for Internal Pricing Authentication
1. Both `operations-service` (the provider) and `parcel-service` (the client) must be configured with the **exact same** token string.
2. The token is transmitted exclusively over the internal service network via the `X-Internal-Service-Token` header.
3. The Nginx reverse proxy blocks all external requests to `/internal/**` with HTTP `404 Not Found`, preventing exposure of internal endpoints to public clients.
4. **Security Rule:** Never commit the actual token string to source control. Set `INTERNAL_PRICING_SERVICE_TOKEN` as an environment variable in container manifests or deployment configurations.

### 3.2 Database Configuration (`ops_db`)

| Variable | Target Property | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5434/ops_db` (local)<br>`jdbc:postgresql://postgres:5432/ops_db` (Docker) | JDBC connection string to the dedicated operations database. |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `swiftdrop` | Database user with ownership of `ops_db`. |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `swiftdrop_password` (dev default) | Database user password. |
| `SPRING_FLYWAY_ENABLED` | `spring.flyway.enabled` | `true` | Enables automatic schema migration upon service startup. |

### 3.3 Message Broker & Kafka Topics

| Variable | Target Property | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `spring.kafka.bootstrap-servers` | `localhost:29092` (local host)<br>`kafka:9092` (Docker internal) | Kafka broker connection string (port 29092 is exposed to the host; port 9092 is Docker network internal). |
| `SWIFTDROP_KAFKA_TOPICS_PARCEL_CREATED` | `swiftdrop.kafka.topics.parcel-created` | `parcel.created.v1` | Kafka topic for parcel creation events. Consumed for sequence tracking and deduplication. |
| `SWIFTDROP_KAFKA_TOPICS_PARCEL_STATUS_CHANGED` | `swiftdrop.kafka.topics.parcel-status-changed` | `parcel.status-changed.v1` | Kafka topic for parcel status transitions. Consumed to aggregate terminal deliveries (`DELIVERED`, `FAILED`). |

### 3.4 Security & JWT Validation

| Variable | Target Property | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `http://localhost:8081/.well-known/jwks.json` | JWKS endpoint hosted by `auth-service` for validating RS256 JWT signatures. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `https://auth.swiftdrop.local` | Expected issuer claim (`iss`) in validated JWTs. |
| `SWIFTDROP_SECURITY_OAUTH2_AUDIENCE` | `swiftdrop.security.oauth2.audience` | `swiftdrop-api` | Expected audience claim (`aud`) in validated JWTs. |

---

## 4. Local Execution & Startup Guide

### 4.1 Running with Docker Compose (Recommended)

1. Ensure Docker Engine and Docker Compose are running.
2. Initialize environment variables from the template:
   ```powershell
   Copy-Item deploy/.env.example deploy/.env -Force
   ```
3. Start the entire platform stack (PostgreSQL, Kafka, Auth, Parcel, Operations, Integration, Nginx Gateway, Frontend):
   ```powershell
   docker compose -f deploy/docker-compose.yml --profile full up -d --build
   ```
4. Confirm health status:
   ```powershell
   docker compose -f deploy/docker-compose.yml ps
   curl.exe -s http://localhost:8080/health
   # Output: ok
   ```

### 4.2 Running `operations-service` Locally (Direct Host)

1. Ensure PostgreSQL is running on port `5434` (or configure `SPRING_DATASOURCE_URL`) and Kafka on host-accessible port `29092` (Docker Compose maps external Kafka listener to `29092`).
2. Start the service using Maven with the local profile active (loads `application-local.yml` containing datasource and port `8083`):
   ```powershell
   mvn spring-boot:run -pl services/operations-service -Dspring-boot.run.profiles=local
   ```
3. Verify Actuator health endpoint:
   ```powershell
   curl.exe -s http://localhost:8083/actuator/health
   # Output: {"status":"UP"}
   ```

### 4.3 Running the Angular Frontend Locally

1. Navigate to the frontend directory:
   ```powershell
   cd frontend
   npm ci
   npm start
   ```
2. The application will be available at `http://localhost:4200/`. When running against local services, ensure API proxying or gateway forwarding is enabled.

---

## 5. Cold-Start Database Initialization (Demo Seed)

On a freshly initialized database, `ops_db` contains table structures but zero seed rows (per ADR 003, baseline monetary values were not committed into Flyway migrations). Consequently, initial quote queries or parcel creations will return `PRICING_RULE_NOT_FOUND` or `UNKNOWN_DISTRICT`.

To populate the approved E2E demo pricing rules (used in `docs/m2-e2e/run.sh` and `docs/m2-e2e/browser-walkthrough.mjs`), execute the following SQL seed against `ops_db`:

```sql
-- 1. Insert Demo Zones
INSERT INTO zones (name, served, base_fee_lkr)
VALUES ('E2E Western', true, 300), ('E2E Central', true, 400)
ON CONFLICT (name) DO NOTHING;

-- 2. Associate Districts (Colombo -> E2E Western; Kandy -> E2E Central)
INSERT INTO zone_districts (zone_id, district)
SELECT id, 'Colombo' FROM zones WHERE name = 'E2E Western'
ON CONFLICT DO NOTHING;

INSERT INTO zone_districts (zone_id, district)
SELECT id, 'Kandy' FROM zones WHERE name = 'E2E Central'
ON CONFLICT DO NOTHING;

-- 3. Insert Weight-Band Rule (0-10 kg)
INSERT INTO pricing_rules (kind, rule_key, minimum_weight_kg, maximum_weight_kg, charge_lkr)
VALUES ('WEIGHT_BAND', 'E2E_0_10', 0, 10, 200)
ON CONFLICT DO NOTHING;

-- 4. Insert Parcel Size Charge (MEDIUM)
INSERT INTO pricing_rules (kind, rule_key, charge_lkr)
VALUES ('SIZE', 'MEDIUM', 100)
ON CONFLICT DO NOTHING;
```

> **Important Note:** These monetary values (`LKR 300`, `LKR 400`, `LKR 200`, `LKR 100`) are verified demo seed parameters for testing and demonstration workflows only. They must not be treated as production baseline prices or added to Flyway migration files.

---

## 6. Operational Constraints & Boundaries

1. **Role Access Control on Reports:**
   * `/api/v1/admin/reports/**` and the frontend page `/admin/reports` require `ROLE_ADMIN`.
   * Users with `ROLE_DISPATCHER` receive HTTP `403 Forbidden` and are redirected by route guards.
2. **Zone-Level Analytics Unavailable:**
   * Kafka's shared `EventEnvelope` (`parcel.status-changed.v1`) does not contain origin or destination zone/district data.
   * `operations-service` does not join `parcel_db` (strict database-per-service isolation).
   * As a result, delivery aggregates are grouped strictly by calendar date in `Asia/Colombo` time, not by delivery zone.
3. **District Catalog Constraint:**
   * Zone management accepts only the 25 official Sri Lankan administrative districts defined in `Districts.java`. Unrecognized strings are rejected with HTTP `400 Bad Request`.
