# SwiftDrop entity relationship draft

This is a week-one contract draft. Tables marked `TODO` require confirmation
from the owning member before migrations are treated as final.

```mermaid
erDiagram
    USER {
        uuid id PK
        string role
        string email
    }

    PARCEL {
        uuid id PK
        string tracking_number
        uuid customer_id FK
        uuid merchant_id FK
        string pickup_district
        string dropoff_district
        decimal finalized_fee_lkr
        string status
    }

    ADDRESS {
        uuid id PK
        uuid customer_id FK
        string district
        string address_text
    }

    DELIVERY_EVENT {
        uuid event_id PK
        uuid parcel_id FK
        string event_type
        datetime occurred_at_utc
    }

    ZONE {
        uuid id PK
        string name
        boolean served
        decimal base_fee_lkr
    }

    ZONE_DISTRICT {
        uuid zone_id FK
        string district PK
    }

    PRICING_RULE {
        uuid id PK
        string kind
        string key
        decimal charge_lkr
        boolean active
    }

    DAILY_STAT {
        date report_date PK
        int total_deliveries
        int successful_deliveries
        int failed_deliveries
    }

    PRICING_AUDIT {
        uuid id PK
        uuid actor_id FK
        string action
        string target_type
        uuid target_id
        datetime occurred_at_utc
    }

    TODO_AUTH_TABLE {
        string note "M10/M4: confirm auth schema"
    }

    TODO_COURIER_TABLE {
        string note "M8/M9: confirm courier ownership"
    }

    TODO_MERCHANT_TABLE {
        string note "M6: confirm merchant schema"
    }

    USER ||--o{ ADDRESS : owns
    USER ||--o{ PARCEL : creates
    PARCEL ||--o{ DELIVERY_EVENT : emits
    ZONE ||--o{ ZONE_DISTRICT : contains
    ZONE ||--o{ PARCEL : serves_dropoff
    PRICING_RULE ||--o{ PARCEL : prices
    USER ||--o{ PRICING_AUDIT : performs
    DAILY_STAT }o--o{ DELIVERY_EVENT : projects
    TODO_AUTH_TABLE ||--o{ USER : owns
    TODO_COURIER_TABLE ||--o{ PARCEL : assigned_to
    TODO_MERCHANT_TABLE ||--o{ PARCEL : submits
```

## Ownership questions

- M1: confirm parcel, address, and finalized-fee columns.
- M4: confirm dispatch and courier assignment tables.
- M6: confirm merchant references.
- M8/M9: confirm courier tables and ownership.
- M10/M4/M8: confirm the audit-table owner and whether operations keeps a
  local pricing audit table or emits audit events.
