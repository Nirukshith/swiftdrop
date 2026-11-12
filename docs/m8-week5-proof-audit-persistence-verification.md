# SwiftDrop — Delivery Proof Storage & Audit Persistence Verification Guide (Week 5)

**Author:** M8 — Harsha Kumarasamy  
**Scope:** `operations-service` (delivery proof storage & audit log) and `auth-service` (authentication audit log)  
**Related Issue:** [#180](https://github.com/Nirukshith/swiftdrop/issues/180)  
**Related PR:** [#192](https://github.com/Nirukshith/swiftdrop/pull/192)

---

## 1. Overview

This document specifies the persistence architecture, Docker Compose volume configuration, container restart safety, and step-by-step verification procedures for:
1. **Delivery Proof Files:** Ensuring uploaded delivery proof photos are saved to a dedicated persistent volume and survive container restarts without data loss or checksum degradation.
2. **Delivery Proof Metadata & Audit Records:** Ensuring delivery proof metadata (`delivery_proofs`) and delivery proof audit logs (`audit_log` with actions `DELIVERY_PROOF_UPLOADED` and `DELIVERY_PROOF_REJECTED`) persist safely in PostgreSQL across service restarts.
3. **Courier Authentication Audit Records:** Ensuring courier authentication audit logs (`USER_LOGIN_SUCCESS`, `USER_LOGIN_FAILED`) persist in `auth_db` across authentication service restarts.

---

## 2. Configuration & Environment Variables

### 2.1 Operations Service Environment Variables

The following environment variables control delivery proof storage in `operations-service`:

| Variable | Default Value | Production / Docker Compose Recommended | Description |
|---|---|---|---|
| `SWIFTDROP_PROOFS_STORAGE_DIR` | `/tmp/swiftdrop/proofs` | `/data/proofs` | Absolute directory path where delivery proof image files are stored on disk. |
| `SWIFTDROP_PROOFS_MAX_SIZE_BYTES` | `5242880` (5 MB) | `5242880` (5 MB) | Maximum permitted size for uploaded delivery proof files (in bytes). |

### 2.2 Docker Compose Persistent Volume Configuration

To ensure delivery proof files survive container restarts and redeployments, the `operations-service` container in `deploy/docker-compose.yml` must mount a named Docker volume to `/data/proofs`.

```yaml
services:
  operations-service:
    image: swiftdrop/operations-service:latest
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SWIFTDROP_PROOFS_STORAGE_DIR=/data/proofs
      - SWIFTDROP_PROOFS_MAX_SIZE_BYTES=5242880
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-ops:5432/ops_db
    volumes:
      - proofs_data:/data/proofs
    depends_on:
      - postgres-ops

volumes:
  proofs_data:
    driver: local
```

> **IMPORTANT:**
> - The named volume `proofs_data` decouples uploaded binary image storage from the container filesystem lifecycle.
> - When `operations-service` container stops, restarts, or is rebuilt, all files in `/data/proofs` remain intact.

---

## 3. Safe Container Restart Guidelines

To restart the `operations-service` or `auth-service` without losing persistent data:

### Safe Commands:
```bash
# Graceful restart of the operations service
docker compose restart operations-service

# Stop and recreate container without touching volumes
docker compose down
docker compose up -d operations-service
```

### Prohibited Commands:
```bash
# WARNING: NEVER run docker compose down with the -v flag in test/staging/production!
# The -v flag will delete named volumes including proofs_data and database volumes.
docker compose down -v # DO NOT RUN
```

---

## 4. End-to-End Verification Procedure

Follow this verification procedure to validate delivery proof storage, SHA-256 integrity, restart persistence, and audit logging.

### Step 1: Upload Delivery Proof File
Send a multipart POST request with courier credentials:
```bash
curl -X POST http://localhost:8080/api/v1/courier/parcels/11111111-2222-3333-4444-555555555555/proof \
  -H "Authorization: Bearer <COURIER_JWT_TOKEN>" \
  -F "file=@delivery-proof.jpg;type=image/jpeg" \
  -F "recipientName=Sunil Shantha" \
  -F "deliveryNotes=Delivered to reception"
```

Expected response: `201 Created` with payload:
```json
{
  "proofId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "parcelId": "11111111-2222-3333-4444-555555555555",
  "proofType": "PHOTO",
  "proofUrl": "/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "recipientName": "Sunil Shantha",
  "uploadedAt": "2026-11-12T14:30:00Z"
}
```

### Step 2: Record Original File SHA-256 Checksum
```bash
sha256sum delivery-proof.jpg
# Output: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  delivery-proof.jpg
```

### Step 3: Inspect Database Records & Audit Logs in `ops_db`
Connect to PostgreSQL `ops_db`:
```sql
-- 1. Check delivery proof metadata
SELECT id, parcel_id, courier_id, file_path, file_size_bytes, mime_type
FROM delivery_proofs
WHERE id = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

-- 2. Check delivery proof audit log record
SELECT id, occurred_at, actor_id, actor_role, action, target_type, target_id, details
FROM audit_log
WHERE target_id = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
```
Expected audit row:
- `action`: `DELIVERY_PROOF_UPLOADED`
- `actor_id`: `<courier_id>`
- `actor_role`: `COURIER`
- `target_type`: `DELIVERY_PROOF`
- `target_id`: `3fa85f64-5717-4562-b3fc-2c963f66afa6`
- `details`: `{"parcelId": "...", "fileSizeBytes": 45020, "mimeType": "image/jpeg"}`

### Step 4: Perform Container Restart
```bash
docker compose restart operations-service
```
Wait for healthy status:
```bash
docker compose ps operations-service
```

### Step 5: Retrieve Proof File & Validate Integrity Post-Restart
Retrieve the proof through the authorized API:
```bash
curl -X GET http://localhost:8080/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
  -H "Authorization: Bearer <COURIER_JWT_TOKEN>" \
  -o retrieved-proof.jpg
```

Compare SHA-256 checksums:
```bash
sha256sum retrieved-proof.jpg
# Verify output matches original hash exactly!
```

### Step 6: Verify Authorization Enforcement Post-Restart
Attempt to retrieve proof with an unauthorized courier token:
```bash
curl -X GET http://localhost:8080/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
  -H "Authorization: Bearer <OTHER_COURIER_JWT_TOKEN>"
```
Expected response: `403 Forbidden`.

Attempt to retrieve proof without authentication:
```bash
curl -X GET http://localhost:8080/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6
```
Expected response: `401 Unauthorized`.

---

## 5. Automated Tests

The verification behavior is automated in CI/CD through:
- `services/operations-service/.../courier/execution/CourierExecutionIntegrationTest.java`:
  - `testUploadProofAndCompleteDelivery`: Tests upload, metadata persistence, delivery status transition, and retrieval.
  - `testDeliveryProofAndAuditRestartPersistence`: Verifies SHA-256 checksum byte equality, metadata persistence, audit record persistence, and retrieval across simulated restarts.
  - `testUnauthorizedProofRetrievalIsRejected`: Tests 403 Forbidden enforcement on foreign courier access.
- `services/auth-service/.../audit/AuditPersistenceIntegrationTest.java`:
  - `courierAuthenticationAuditPersistence_successAndFailure`: Tests courier login success and failure audit logging, credential redaction, and persistence in PostgreSQL.

---

## 6. Troubleshooting

| Issue | Cause | Resolution |
|---|---|---|
| Upload fails with `500 Internal Server Error` (file write error) | Directory not writable or does not exist. | Check `SWIFTDROP_PROOFS_STORAGE_DIR` permissions inside the container (`chmod 755 /data/proofs`). |
| Upload fails with `400 Bad Request` | File is not a valid JPEG/PNG image or exceeds 5 MB. | Verify image magic bytes (`FF D8 FF` for JPEG, `89 50 4E 47` for PNG) and size <= 5 MB. |
| Retrieved file is missing after container restart | Directory `/data/proofs` was not mounted to a persistent volume. | Ensure `volumes: - proofs_data:/data/proofs` is in `deploy/docker-compose.yml`. |
| Retrieval returns `403 Forbidden` | The requesting courier is not the courier assigned to the parcel, or user lacks `DISPATCHER` or `ADMIN` role. | Verify JWT identity matches `courier_id` in `delivery_proofs` record or has elevated role. |
