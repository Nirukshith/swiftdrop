# SwiftDrop Week 8 M5 — Pricing, Zones, and Reports Demonstration Guide

**Assigned Member:** M5
**Issue:** [#252 — Finalize Pricing, Analytics, Reporting Dashboard, and Business Reporting Documentation](https://github.com/Nirukshith/swiftdrop/issues/252)
**Primary Focus:** Delivery zone configuration, dynamic fee calculation, administrative reports dashboard, and CSV export.

---

## 1. Executive Summary & Demo Flow

This demonstration guide provides a reproducible, step-by-step walkthrough of Member M5's capabilities in SwiftDrop:
1. **Service Readiness & Health Verification**: Verifying gateway and operations microservice status.
2. **Delivery Zone Management (`/dispatch/zones`)**: Inspecting active coverage boundaries, served status, and drop-off base fees.
3. **Dynamic Pricing Rules (`/dispatch/pricing`)**: Configuring weight tiers and size charges.
4. **Internal Quote Execution (`GET /internal/v1/pricing/quote`)**: Exercising the deterministic pricing formula ($\text{Fee} = \text{drop-off base fee} + \text{weight-band charge} + \text{size charge}$).
5. **Event Ingestion Simulation**: Simulating Kafka parcel status events (`DELIVERED`, `FAILED`) to trigger real-time report projections.
6. **Reporting Dashboard (`/admin/reports`)**: Viewing KPI summary cards, volume trend charts, and date range filters in `Asia/Colombo` time.
7. **CSV Report Export**: Downloading and verifying `daily-report.csv`.

---

## 2. Prerequisites & Environment Setup

### 2.1 Starting the System

Start the full stack using Docker Compose:

```powershell
# 1. Ensure environment variables are initialized
Copy-Item deploy/.env.example deploy/.env -Force

# 2. Build and launch all containers
docker compose -f deploy/docker-compose.yml --profile full up -d --build

# 3. Verify gateway health
curl.exe -s http://localhost:8080/health
# Expected: ok
```

### 2.2 Approved Demo Seed Data Setup

On a fresh database, `ops_db` starts without baseline monetary values (per ADR 003). To run the standard demonstration, execute the approved E2E demo seed (identical to `docs/m2-e2e/run.sh` and `docs/m2-e2e/browser-walkthrough.mjs`):

```powershell
# Execute seed against PostgreSQL ops_db container
$pg = docker ps --format "{{.Names}}" | Select-String "postgres" | Select-Object -First 1

docker exec -i $pg psql -q -U swiftdrop -d ops_db -c @"
INSERT INTO zones (name, served, base_fee_lkr) VALUES ('E2E Western', true, 300), ('E2E Central', true, 400) ON CONFLICT (name) DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Colombo' FROM zones WHERE name = 'E2E Western' ON CONFLICT DO NOTHING;
INSERT INTO zone_districts (zone_id, district) SELECT id, 'Kandy' FROM zones WHERE name = 'E2E Central' ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, minimum_weight_kg, maximum_weight_kg, charge_lkr) VALUES ('WEIGHT_BAND', 'E2E_0_10', 0, 10, 200) ON CONFLICT DO NOTHING;
INSERT INTO pricing_rules (kind, rule_key, charge_lkr) VALUES ('SIZE', 'MEDIUM', 100) ON CONFLICT DO NOTHING;
"@
```

> **Notice:** These values (`LKR 300`, `LKR 400`, `LKR 200`, `LKR 100`) are for demo and testing workflows only. They are not production baseline prices.

---

## 3. Step-by-Step Management Demonstration

### Step 1: Sign In as Administrator & Open Admin Shell

1. Open your browser and navigate to `http://localhost:8080/login`.
2. Sign in with an account having `ROLE_ADMIN` (e.g. `admin@swiftdrop.lk`).
3. You will be directed to the **Admin Shell** (`/admin`).
4. Notice the navigation tabs:
   - **Overview** (`/admin`)
   - **Staff management** (`/admin/users`)
   - **Merchant approval** (`/admin/merchants`)
   - **Reports** (`/admin/reports`)
5. Click **View reports →** or navigate to `/admin/reports`.

---

### Step 2: Manage Delivery Zones (`/dispatch/zones`)

1. As a `DISPATCHER` or `ADMIN`, open `http://localhost:8080/dispatch/zones`.
2. **Inspect Existing Zones**:
   - Verify `E2E Western` covering `Colombo` with base fee `LKR 300.00` and `Served: Yes`.
   - Verify `E2E Central` covering `Kandy` with base fee `LKR 400.00` and `Served: Yes`.
3. **Verify via REST API**:
   ```powershell
   curl.exe -s http://localhost:8080/api/v1/admin/zones -H "Authorization: Bearer <TOKEN>"
   ```
   **Expected JSON Response:**
   ```json
   [
     {
       "name": "E2E Western",
       "districts": ["Colombo"],
       "served": true,
       "baseFeeLkr": 300.00
     },
     {
       "name": "E2E Central",
       "districts": ["Kandy"],
       "served": true,
       "baseFeeLkr": 400.00
     }
   ]
   ```
4. **Validation Check**: Attempting to create a zone with an invalid district name (e.g. `"Atlantis"`) returns `400 Bad Request` with message `"Unknown district(s): [Atlantis]"`.

---

### Step 3: Manage Pricing Rules (`/dispatch/pricing`)

1. Navigate to `http://localhost:8080/dispatch/pricing`.
2. Inspect the configured rules:
   - **Weight Band**: Key `E2E_0_10`, bounds `0.000` to `10.000` kg, charge `LKR 200.00`.
   - **Size Tier**: Key `MEDIUM`, charge `LKR 100.00`.
3. **Verify via REST API**:
   ```powershell
   curl.exe -s http://localhost:8080/api/v1/admin/pricing-rules -H "Authorization: Bearer <TOKEN>"
   ```
   **Expected JSON Response:**
   ```json
   [
     {
       "kind": "WEIGHT_BAND",
       "key": "E2E_0_10",
       "minimumWeightKg": 0.000,
       "maximumWeightKg": 10.000,
       "chargeLkr": 200.00,
       "active": true
     },
     {
       "kind": "SIZE",
       "key": "MEDIUM",
       "chargeLkr": 100.00,
       "active": true
     }
   ]
   ```

---

### Step 4: Validate Dynamic Quote Calculation

Test the internal pricing quote calculation directly on `operations-service` (port `8083`) using the service token header:

```powershell
curl.exe -s "http://localhost:8083/internal/v1/pricing/quote?pickupDistrict=Colombo&dropoffDistrict=Kandy&weightKg=2.5&size=MEDIUM" -H "X-Internal-Service-Token: <INTERNAL_PRICING_SERVICE_TOKEN>"
```

**Expected JSON Response:**
```json
{
  "currency": "LKR",
  "totalFee": 700.00,
  "breakdown": {
    "dropoffZoneBaseFee": 400.00,
    "weightBandCharge": 200.00,
    "sizeCharge": 100.00
  },
  "pickupZone": "E2E Western",
  "dropoffZone": "E2E Central"
}
```

#### Calculation Verification:
$$\text{Total Fee} = 400.00 (\text{Dropoff: Kandy}) + 200.00 (\text{Weight: 2.5 kg}) + 100.00 (\text{Size: MEDIUM}) = \text{LKR } 700.00$$

* **Pickup Serviceability Check**: If `pickupDistrict` is in an unserved zone (`served: false`), the endpoint immediately rejects the request with `422 Unprocessable Entity` (`PICKUP_DISTRICT_UNSERVED`).
* **Gateway Route Protection**: Attempting `GET http://localhost:8080/internal/v1/pricing/quote` through the Nginx gateway returns HTTP `404 Not Found` (gateway blocks `/internal/**`).

---

### Step 5: Simulate Delivery Events & Real-Time Projections

Simulate delivery completion events on Kafka topic `parcel.status-changed.v1`:

```powershell
# Simulate DELIVERED event
$deliveredEvent = @{
  eventId = [guid]::NewGuid().ToString()
  eventType = "PARCEL_STATUS_CHANGED"
  occurredAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  sequence = 4
  parcelId = "pcl-demo-001"
  trackingNumber = "SD-20261114-DEMO01"
  fromStatus = "OUT_FOR_DELIVERY"
  toStatus = "DELIVERED"
  courierId = "usr-courier-01"
} | ConvertTo-Json -Compress

# Simulate FAILED event
$failedEvent = @{
  eventId = [guid]::NewGuid().ToString()
  eventType = "PARCEL_STATUS_CHANGED"
  occurredAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  sequence = 4
  parcelId = "pcl-demo-002"
  trackingNumber = "SD-20261114-DEMO02"
  fromStatus = "OUT_FOR_DELIVERY"
  toStatus = "FAILED"
  courierId = "usr-courier-02"
  reason = "Customer unavailable"
} | ConvertTo-Json -Compress

$kafka = (docker ps --format "{{.Names}}" | Where-Object { $_ -like "*kafka*" })[0]
$deliveredEvent | docker exec -i $kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic parcel.status-changed.v1
$failedEvent | docker exec -i $kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic parcel.status-changed.v1
```

The `operations-reports` Kafka consumer will ingest both events, check sequence monotonically, deduplicate on `eventId`, convert the UTC timestamp to `Asia/Colombo` date, and update `daily_report_aggregate`.

---

### Step 6: Inspect Daily Delivery Reports Dashboard (`/admin/reports`)

1. Open `http://localhost:8080/admin/reports` in your browser.
2. Select a date range covering today's date:
   - **From**: `2026-11-08`
   - **To**: `2026-11-14`
3. Click **Apply**.
4. **Inspect KPI Cards**:
   - **Total deliveries**: Displays aggregate count of terminal delivery attempts.
   - **Successful**: Count of `DELIVERED` parcels.
   - **Failed**: Count of `FAILED` parcels.
   - **Success rate**: Calculated percentage ($\text{Successful} / \text{Total}$).
5. **Inspect Trend Charts**:
   - **Daily delivery trend**: Visual bar showing volume relative to peak daily total.
   - **Success versus failure**: Segmented bar displaying emerald (success) vs rose (failed) distribution.
6. **Notice on Zone Data**:
   - Observe the informational card text: *"Zone analytics are unavailable because the approved parcel event contract does not include zone or district data."*

---

### Step 7: Export and Verify CSV Report

1. On the reports page (`/admin/reports`), click **Export CSV**.
2. The browser downloads `daily-report.csv`.
3. Open or inspect the downloaded file:
   ```csv
   reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate
   2026-11-08,0,0,0,0.0
   2026-11-09,0,0,0,0.0
   2026-11-10,0,0,0,0.0
   2026-11-11,0,0,0,0.0
   2026-11-12,0,0,0,0.0
   2026-11-13,0,0,0,0.0
   2026-11-14,2,1,1,0.5
   ```
4. Verify via direct REST call:
   ```powershell
   curl.exe -i "http://localhost:8080/api/v1/admin/reports/daily.csv?from=2026-11-08&to=2026-11-14" -H "Authorization: Bearer <ADMIN_TOKEN>"
   ```
   **Expected Headers & Content:**
   - `Content-Type: text/csv`
   - `Content-Disposition: attachment; filename="daily-report.csv"`
   - Contract header: `reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate`

---

## 4. Security & Role Boundary Verification Check

1. **Dispatcher Role Access Check**:
   - Log in as a user with `ROLE_DISPATCHER`.
   - Attempt to open `http://localhost:8080/admin/reports`.
   - Result: Frontend `roleGuard` prevents navigation and redirects away.
   - Direct API request:
     ```powershell
     curl.exe -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/admin/reports/daily?from=2026-11-08&to=2026-11-14" -H "Authorization: Bearer <DISPATCHER_TOKEN>"
     # Output: 403
     ```
   - Confirms that reports are strictly restricted to `ROLE_ADMIN`.

---

## 5. Demonstration Evidence Checklist

| Checkpoint | Verification Description | Expected Outcome | Status |
| :--- | :--- | :--- | :--- |
| **Zone List** | Open `/dispatch/zones` | Shows `E2E Western` and `E2E Central` with correct base fees | Verified |
| **Zone District Validation** | Save zone with unknown district | HTTP `400 Bad Request` with district error message | Verified |
| **Pricing Rules** | Open `/dispatch/pricing` | Shows weight-band and size rule entries | Verified |
| **Price Quote** | Execute `GET /internal/v1/pricing/quote` | Returns `LKR 700.00` total with accurate breakdown | Verified |
| **Pickup Validation** | Quote with unserved pickup zone | Returns `422 Unprocessable Entity` (`PICKUP_DISTRICT_UNSERVED`) | Verified |
| **Reports Display** | Open `/admin/reports` with active range | Displays summary cards, daily bar chart, and success vs failure bar | Verified |
| **Reports Empty State** | Open `/admin/reports` with empty range | Shows "No delivery report data is available for this date range." | Verified |
| **CSV Export** | Click **Export CSV** | Downloads `daily-report.csv` with contract headers | Verified |
| **Role Restriction** | Query `/api/v1/admin/reports/**` as Dispatcher | Returns HTTP `403 Forbidden` | Verified |
