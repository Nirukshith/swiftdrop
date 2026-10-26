# Customer Delivery Creation Sequence

This sequence diagram illustrates the customer parcel creation lifecycle from the Angular web application through the Nginx gateway, `parcel-service`, synchronous fee calculation via `operations-service`, transactional database persistence, and asynchronous domain event emission to Kafka.

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer (Browser UI)
    participant Gateway as Nginx Gateway (Port 80)
    participant ParcelService as parcel-service (8082)
    participant OpsService as operations-service (8083)
    participant DB as PostgreSQL (parcel_db)
    participant Kafka as Kafka Broker
    participant Consumers as Event Consumers (M3 / M5)

    Customer->>Gateway: POST /api/v1/parcels<br/>Authorization: Bearer <JWT><br/>Payload: pickup/dropoff address (districts), weight, size
    Gateway->>Gateway: Enforce path routing & rate limiting
    Gateway->>ParcelService: Forward POST /api/v1/parcels
    
    ParcelService->>ParcelService: Validate JWT claims (CUSTOMER role)<br/>Validate address districts against ADR 002 allow-list

    critical Obtain Fee Calculation
        ParcelService->>OpsService: GET /internal/v1/pricing/quote?<br/>pickupDistrict=...&dropoffDistrict=...&weightKg=...&size=...
        OpsService->>OpsService: Calculate fee (Zone + Weight + Size per ADR 003)
        OpsService-->>ParcelService: 200 OK { totalFee: 750.00, currency: "LKR", breakdown: {...} }
    end

    critical Database Transaction
        ParcelService->>ParcelService: Generate tracking number (SD-YYYYMMDD-XXXXXX)<br/>Set status = CREATED, sequence = 1, attemptCount = 0
        ParcelService->>DB: INSERT INTO parcel (...)
        DB-->>ParcelService: Commit Transaction
    end

    ParcelService->>Kafka: Publish parcel.created.v1 (AFTER_COMMIT)<br/>Key: parcelId, Payload: EventEnvelope
    
    ParcelService-->>Gateway: 201 Created (ParcelResponse)
    Gateway-->>Customer: 201 Created (trackingNumber, status: CREATED, fee: 750.00)

    par Asynchronous Event Handling
        Kafka->>Consumers: Consume parcel.created.v1 (integration-service)
        Consumers->>Consumers: Send confirmation email (Mailpit)
    and
        Kafka->>Consumers: Consume parcel.created.v1 (operations-service)
        Consumers->>Consumers: Update DailyStat projection
    end
```

### Notes & References
- **Gateway Boundary:** Nginx blocks any external traffic attempting to hit `/internal/**` directly.
- **District Validation (ADR 002):** Pickup and drop-off districts are validated against the 25 fixed administrative districts of Sri Lanka.
- **Price Immutability (ADR 003):** The fee returned by `operations-service` is locked onto the created parcel record and will not change even if pricing rules are modified later.
- **Reliable Publishing (ADR 005):** `parcel.created.v1` is published strictly using `@TransactionalEventListener(phase = AFTER_COMMIT)` to prevent phantom Kafka events if database persistence fails.
