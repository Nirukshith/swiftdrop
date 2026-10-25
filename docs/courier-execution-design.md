# Courier Delivery Execution & Proof Design

**Owner:** Member M8  
**Service:** `operations-service` (`com.swiftdrop.operations.courier.execution`)  
**Port:** `8083`  
**OpenAPI Contract:** [`contracts/openapi/operations-courier.yaml`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/contracts/openapi/operations-courier.yaml)  

---

## 1. Overview & Context

Courier delivery execution encompasses the mobile-first operational workflow through which assigned couriers view their daily delivery queue, advance parcels step-by-step through their physical lifecycle, and record verifiable proof upon handover.

Per the architectural boundary established in SRS §3.1 and §8.2, `parcel-service` (M1) remains the single authority of record for parcel state changes. The `operations-service` acts as the operational orchestrator:
1. Validates courier identity and ownership rights using the shared `common` module's `CurrentUser` and `OwnershipCheck`.
2. Enforces client-facing state machine rules and idempotency.
3. Validates and stores delivery proof artifacts (photo / signature).
4. Synchronizes status updates with `parcel-service` via internal REST contracts.

---

## 2. Allowed Courier State Transitions

### 2.1 State Progression Model

Once a parcel is assigned by a dispatcher, it resides in status `CREATED` with a non-null `courierId`. The courier is authorized to advance the parcel through the following deterministic lifecycle:

```text
               ┌─────────────┐
               │   CREATED   │ (Assigned to Courier)
               └──────┬──────┘
                      │
                      ▼
               ┌─────────────┐
               │  PICKED_UP  │ (Courier receives parcel at merchant/hub)
               └──────┬──────┘
                      │
                      ▼
               ┌─────────────┐
               │ IN_TRANSIT  │ (En route between hubs or to destination zone)
               └──────┬──────┘
                      │
                      ▼
            ┌───────────────────┐
            │ OUT_FOR_DELIVERY  │ (On courier vehicle for final-mile dropoff)
            └─────────┬─────────┘
                      │
         ┌────────────┴────────────┐
         │                         │
         ▼                         ▼
  ┌─────────────┐           ┌─────────────┐
  │  DELIVERED  │           │   FAILED    │
  └─────────────┘           └─────────────┘
  (Proof Required)          (Reason Required)
```

### 2.2 Transition Rules & Invariants

| From Status | Allowed Next Status | Preconditions & Requirements |
|:---|:---|:---|
| `CREATED` | `PICKED_UP` | Parcel must be assigned to calling courier (`OwnershipCheck.isCourierOwner`). |
| `PICKED_UP` | `IN_TRANSIT` | Courier has scanned/verified parcel barcode at pickup point. |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY` | Parcel is loaded onto local delivery vehicle. |
| `OUT_FOR_DELIVERY` | `DELIVERED` | **Terminal success**. Requires delivery proof: recipient name AND/OR uploaded photo proof. |
| `OUT_FOR_DELIVERY` | `FAILED` | **Terminal failure attempt**. Requires non-blank failure reason (`RECIPIENT_UNAVAILABLE`, `INCORRECT_ADDRESS`, `CUSTOMER_REJECTED`, `ACCESS_RESTRICTED`). Decrements courier workload per ADR 005. |

### 2.3 Invalid Jumps & Rejection

Any attempt to skip intermediate states (e.g., jumping directly from `CREATED` to `DELIVERED` or `OUT_FOR_DELIVERY`) is rejected immediately:
* HTTP Status: `409 Conflict` (or `400 Bad Request` if payload violates preconditions).
* Error Code: `ILLEGAL_STATUS_TRANSITION`.
* Response includes `allowedNextStatuses` to guide mobile UI state disabling.

---

## 3. Idempotent Status Updates

Mobile network connections in delivery vehicles are frequently intermittent. To prevent duplicate status triggers, failed retries, or race conditions:

1. **Idempotency Key**:
   - Clients may supply an optional `idempotencyKey` in the request header or JSON body (`CourierStatusUpdateRequest`).
2. **Current State Match**:
   - If an update requests transitioning a parcel to a state it has **already reached** (e.g. network timeout caused client retry of `OUT_FOR_DELIVERY` when server already processed it), the service detects `currentStatus == requestedStatus`.
   - It returns `200 OK` with the existing status details rather than throwing `409 Conflict`.
   - No duplicate Kafka events (`parcel.status-changed.v1`) are triggered downstream.
3. **Strict Sequencing**:
   - State emissions to Kafka carry the monotonic `sequence` number defined in ADR 005. Downstream consumers ignore stale out-of-order events.

---

## 4. Delivery Proof Upload Rules

When delivering high-value or signature-required parcels, the courier captures proof (recipient full name + delivery photo).

### 4.1 Content-Based File Validation
To prevent security exploits (e.g., uploading malicious executables disguised as `.jpg`), file type is verified by inspecting **content magic bytes**, not just client-supplied MIME headers or file extensions:

| Format | Magic Bytes (Hex) | Accepted MIME Types |
|:---|:---|:---|
| **JPEG** | `FF D8 FF` | `image/jpeg` |
| **PNG** | `89 50 4E 47 0D 0A 1A 0A` | `image/png` |
| **WEBP** | `52 49 46 46` ... `57 45 42 50` | `image/webp` |

Files failing magic byte inspection are rejected with `400 Bad Request` (`INVALID_FILE_TYPE`).

### 4.2 Size Limits
* **Maximum File Size**: 5 MB (`5,242,880 bytes`).
* Uploads exceeding 5 MB are rejected with `413 Payload Too Large` (`FILE_SIZE_EXCEEDED`).
* Mobile client performs client-side compression (canvas resize) prior to upload to keep average payloads around 500 KB – 1 MB.

### 4.3 Storage Location & Path Schema
* Proof photos are stored in a dedicated persistent volume mounted at:
  ```text
  /data/proofs/{parcelId}-{timestamp}.{ext}
  ```
* Example: `/data/proofs/7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11-1729864200.jpg`
* `operations-service` exposes read access to authorized consumers (M2 customer tracking receipts and M9 courier summaries) via `GET /api/v1/courier/proofs/{proofId}`.

---

## 5. Inter-Service Integration Contracts

`operations-service` integrates with `parcel-service` (owned by M1) using internal service-to-service REST calls protected from external access by Nginx routing:

```text
[Courier Mobile Portal]
         │
         │ (1) GET /api/v1/courier/deliveries?date=2026-10-25
         ▼
[operations-service:8083]
         │
         │ (2) GET /internal/v1/parcels?courierId={courierId}&date={date}
         ▼
[parcel-service:8082] ──► (Returns assigned parcels)
```

```text
[Courier Mobile Portal]
         │
         │ (1) PATCH /api/v1/courier/deliveries/{id}/status { "status": "PICKED_UP" }
         ▼
[operations-service:8083]
         │
         │ (2) PATCH /internal/v1/parcels/{id}/status { "status": "PICKED_UP", "courierId": ... }
         ▼
[parcel-service:8082] ──► (Updates parcel_db & publishes parcel.status-changed.v1)
```

### 5.1 Error Handling on Inter-Service Delegation
* If `parcel-service` returns `404 Not Found`, `operations-service` returns `404 Not Found`.
* If `parcel-service` returns `409 Conflict`, `operations-service` returns `409 Conflict` with the underlying reason.
* Network timeouts are caught and reported as `503 Service Unavailable` with `X-Correlation-Id` for tracing.
