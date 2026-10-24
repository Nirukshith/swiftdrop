# SwiftDrop

SwiftDrop is a distributed parcel delivery and courier management system built by a 10-person student engineering team. The platform coordinates parcel lifecycles, courier dispatching, real-time tracking, merchant integrations, and automated notifications through event-driven Spring Boot microservices and an Angular standalone web client.

---

## Prerequisites

Before running SwiftDrop locally, ensure the following developer tooling is installed:

| Tool | Recommended Version | Verification Command |
| :--- | :--- | :--- |
| **Java (JDK)** | Temurin JDK 21 | `java -version` |
| **Maven** | 3.9+ | `mvn -v` |
| **Node.js** | 20 LTS or 22 LTS | `node -v` |
| **npm** | 10+ | `npm -v` |
| **Docker Engine & Compose** | Docker v24+ / Compose v2+ | `docker compose version` |

---

## Service Port Allocation Table

| Service / Component | Host Port | Internal / Container Port | Protocol / Description |
| :--- | :--- | :--- | :--- |
| **`auth-service`** | `8081` | `8081` | HTTP / REST & Actuator (Auth, RBAC, JWT, Users) |
| **`parcel-service`** | `8082` | `8082` | HTTP / REST & Actuator (Parcels, Addresses, Status, Tracking) |
| **`operations-service`** | `8083` | `8083` | HTTP / REST & Actuator (Dispatch, Zones, Pricing, Couriers) |
| **`integration-service`** | `8084` | `8084` | HTTP / REST & Actuator (Merchants, Assistant, Webhooks, Mail) |
| **PostgreSQL 16** | `5434` | `5432` | Database (`auth_db`, `parcel_db`, `ops_db`, `integration_db`) |
| **Apache Kafka (KRaft)** | `29092` | `9092` (internal) / `29092` (ext) | KRaft broker & event streaming bus |
| **Mailpit (SMTP)** | `1025` | `1025` | Mock SMTP server for transactional email |
| **Mailpit (UI)** | `8025` | `8025` | Web inbox to inspect dispatched notification emails |
| **Angular Web App** | `4200` | `4200` | Single Page Application (`web` application) |

> **Note on PostgreSQL Port**: Host port `5434` is mapped to container port `5432` via `POSTGRES_PORT=5434` in `deploy/.env` to avoid collisions with any local PostgreSQL instance installed on macOS or Linux.

---

## Repository Layout

```text
swiftdrop/
├── .github/
│   ├── workflows/ci.yml             # GitHub Actions CI (backend + frontend jobs)
│   └── pull_request_template.md     # Standard PR checklist & description template
├── common/                          # Shared library (com.swiftdrop:common)
│   ├── pom.xml                      # Plain JAR (no spring-boot-maven-plugin)
│   └── src/main/java/com/swiftdrop/common/
│       ├── Roles.java               # Role constants (CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN)
│       ├── ErrorResponse.java       # Standard error envelope record
│       ├── GlobalExceptionHandler.java # @RestControllerAdvice suppressing stack traces
│       ├── CorrelationIdFilter.java # Filter managing X-Correlation-Id in MDC
│       └── EventEnvelope.java       # Canonical Kafka event wrapper record
├── services/                        # Spring Boot microservices (Java 21)
│   ├── auth-service/                # Port 8081 (DB: auth_db)
│   ├── parcel-service/              # Port 8082 (DB: parcel_db)
│   ├── operations-service/          # Port 8083 (DB: ops_db)
│   └── integration-service/         # Port 8084 (DB: integration_db)
├── frontend/                        # Angular multi-project workspace
│   ├── projects/
│   │   ├── web/                     # Web application (standalone, Tailwind CSS, Jest)
│   │   ├── ui/                      # Shared reusable UI component library
│   │   └── shared-auth/             # Shared auth and state library
│   ├── proxy.conf.json              # Dev proxy routing API traffic to ports 8081-8084
│   └── tailwind.config.js           # Tailwind CSS configuration
├── deploy/                          # Infrastructure & container configs
│   ├── docker-compose.yml           # Postgres, Kafka KRaft, init-topics, Mailpit
│   ├── .env.example                 # Template for environment variables
│   └── postgres/init.sql            # Creates auth_db, parcel_db, ops_db, integration_db
├── contracts/                       # Schemas & API specifications
│   ├── events/                      # event-envelope.schema.json & topics.md
│   └── openapi/                     # auth.yaml, parcel.yaml, operations.yaml, integration.yaml
├── docs/                            # Architectural documentation
│   └── decisions/                   # Architectural Decision Records (001 - 006)
└── pom.xml                          # Root Maven parent POM (Spring Boot 3.4.3)
```

---

## Clone-to-Running Walkthrough

Follow these steps in order to start the infrastructure, build and test the backend microservices, and run the frontend application.

### Step 1: Start Infrastructure (Docker Compose)

Prepare the environment configuration and boot PostgreSQL, Kafka (KRaft), and Mailpit:

```bash
# 1. Copy the environment configuration template
cp deploy/.env.example deploy/.env

# 2. Start the infrastructure containers using profile 'infra'
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile infra up -d

# 3. Verify that all infrastructure containers are running and healthy
docker compose -f deploy/docker-compose.yml ps

# 4. (Optional) Confirm the required Kafka topics were initialized
docker exec swiftdrop-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Expected topics:
- `parcel.created.v1`
- `parcel.assigned.v1`
- `parcel.status-changed.v1`
- `user.registered.v1`

---

### Step 2: Build and Verify Backend Microservices

Build all Maven modules (shared library + 4 services) and run the unit and integration tests (including Testcontainers tests):

```bash
# From repository root:
mvn -B verify
```

To run individual microservices locally with the `local` Spring profile (which connects to localhost PostgreSQL on port 5434 and Kafka on port 29092):

```bash
# Run auth-service (Port 8081)
mvn -pl services/auth-service spring-boot:run -Dspring-boot.run.profiles=local

# Run parcel-service (Port 8082)
mvn -pl services/parcel-service spring-boot:run -Dspring-boot.run.profiles=local

# Run operations-service (Port 8083)
mvn -pl services/operations-service spring-boot:run -Dspring-boot.run.profiles=local

# Run integration-service (Port 8084)
mvn -pl services/integration-service spring-boot:run -Dspring-boot.run.profiles=local
```

Verify service health via Actuator:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
# Each endpoint responds with: {"status":"UP"}
```

---

### Step 3: Contracts & Architectural Decisions

- **Event Contracts**: Check [contracts/events/topics.md](file:///Users/nirukshithpremkumar/swiftdrop/contracts/events/topics.md) and [contracts/events/event-envelope.schema.json](file:///Users/nirukshithpremkumar/swiftdrop/contracts/events/event-envelope.schema.json).
- **OpenAPI Stubs**: Located in [contracts/openapi/](file:///Users/nirukshithpremkumar/swiftdrop/contracts/openapi/) for each microservice.
- **Architectural Decision Records (ADRs)**: Review [docs/decisions/README.md](file:///Users/nirukshithpremkumar/swiftdrop/docs/decisions/README.md) and ADR stubs `001` through `006`.

---

### Step 4: Run Frontend Client (Angular, Tailwind, Jest)

Navigate to the `frontend/` directory to install dependencies, run tests, build, and serve the application:

```bash
cd frontend

# 1. Install dependencies
npm ci

# 2. Run Jest unit test suites
npm test -- --ci

# 3. Build production web bundle
npm run build

# 4. Start Angular development server with backend proxying
ng serve web --proxy-config proxy.conf.json
# or simply:
npm start
```

Open the web client in your browser:
- `http://localhost:4200/customer` - Customer Portal placeholder
- `http://localhost:4200/merchant` - Merchant Portal placeholder
- `http://localhost:4200/courier` - Courier Portal placeholder
- `http://localhost:4200/admin` - Admin Portal placeholder
- `http://localhost:4200/track/SWIFT-DEMO-001` - Public Parcel Tracking placeholder

---

## Development & Branch Rules

To keep the 10-person engineering team aligned and prevent divergence:

1. **Short-Lived Branches**:
   - Create branches off `main` for individual tasks: `feat/<feature-name>`, `fix/<bug-name>`, or `chore/<task-name>`.
   - Keep branch lifetimes under 24–48 hours.
2. **One Peer Review Required**:
   - Every Pull Request requires at least one approving review before merging.
   - Use the `.github/pull_request_template.md` checklist when filing PRs.
3. **Daily Merges to `main`**:
   - Integrate progress into `main` daily to minimize merge conflicts.
4. **CI Green Gate**:
   - All GitHub Actions CI checks (`backend` and `frontend` jobs) must pass cleanly before any PR can be merged.
