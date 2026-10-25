# Merchant Order Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as Merchant System
    participant Auth as auth-service (8081)
    participant IS as integration-service (8084)
    participant Pricing as operations-service (8083)
    participant Parcel as parcel-service (8082)
    participant Kafka as Kafka

    Merchant->>Auth: OAuth client_credentials request
    Auth-->>Merchant: access token (Bearer)
    Merchant->>IS: POST /api/v1/merchant/orders<br/>Authorization: Bearer token<br/>Idempotency-Key: key + order payload
    IS->>IS: Validate token, APPROVED merchant, key and request hash
    alt Existing key with same request hash
        IS-->>Merchant: Replay stored status and response
    else Existing key with different request hash
        IS-->>Merchant: 409 Conflict
    else New request
        IS->>IS: Reserve idempotency key
        IS->>Pricing: Request quote for pickup/drop-off districts, size and weight
        Pricing-->>IS: Quote response (contract to confirm)
        IS->>Parcel: POST /internal/v1/parcels<br/>Parcel request + merchant association (schema to confirm)
        Parcel->>Parcel: Validate request and persist parcel
        Parcel-->>IS: Created parcel response (schema to confirm)
        Parcel->>Kafka: Publish parcel.created.v1 after commit
        IS->>IS: Persist merchant-order mapping and replay response
        IS-->>Merchant: 201 Created<br/>orderId, parcelId, trackingNumber, status
    end
```

See [Merchant onboarding and orders](../merchant-design.md) for assumptions and open contract questions. Cancellation is also idempotent and must be accepted only before pickup; its downstream parcel-service endpoint remains to be agreed.
