# SwiftDrop Kafka Topics

| Topic | Key | Producer | Consumers | Description |
| :--- | :--- | :--- | :--- | :--- |
| `parcel.created.v1` | `parcelId` | `parcel-service` | `operations-service`, `integration-service` | Emitted when a new parcel order is submitted and validated. |
| `parcel.assigned.v1` | `parcelId` | `operations-service` | `parcel-service`, `integration-service` | Emitted when a parcel is assigned to a courier for pickup/delivery. |
| `parcel.status-changed.v1` | `parcelId` | `parcel-service` | `operations-service`, `integration-service` | Emitted whenever parcel transition state updates (e.g. IN_TRANSIT, DELIVERED). |
| `user.registered.v1` | `userId` | `auth-service` | `parcel-service`, `operations-service`, `integration-service` | Emitted when a customer, merchant, or courier account is created. |
