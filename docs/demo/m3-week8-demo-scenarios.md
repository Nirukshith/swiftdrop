# SwiftDrop Week 8 M3 — Customer Demo Scenarios

**Member:** M3 — Nirukshith  
**Issue:** [#250](https://github.com/Nirukshith/swiftdrop/issues/250)  
**Date:** 2026-11-13  
**Environment:** Local (`http://localhost` via Nginx gateway port 80)  
**Mailpit UI:** `http://localhost:8025`

---

## Demo Accounts

> [!IMPORTANT]
> Use these demo accounts only. Never use real customer credentials or expose real personal data during the presentation.

| Role | Email | Notes |
|---|---|---|
| Customer A | Register via `/register` | Use for primary demo customer |
| Customer B | Register a second account | Use to demonstrate ownership isolation |
| Admin | Created via `POST /api/v1/admin/users` by an ADMIN | For parcel/event setup |

> Obtain JWT tokens for API calls:
> ```bash
> curl -s -X POST http://localhost/api/v1/auth/login \
>   -H "Content-Type: application/json" \
>   -d '{"email":"customerA@demo.local","password":"Demo1234!"}'
> ```

---

## Scenario 1 — Customer Receives a Notification

**What this demonstrates:** End-to-end Kafka → notification consumer → IN_APP notification bell

### Preconditions
- Customer A is registered and logged in
- A parcel exists for Customer A (created via the Customer Delivery Portal)
- `integration-service` is running and connected to Kafka

### Steps

| # | Action | Expected Result |
|---|---|---|
| 1 | Log in as Customer A at `http://localhost/login` | Redirected to `/customer` |
| 2 | Note the 🔔 bell in the top-right navigation bar | Bell shows current unread count (initially 0 or N) |
| 3 | Using dispatcher login, assign the parcel to a courier | `parcel.assigned.v1` event published to Kafka |
| 4 | Wait ~5 seconds | `integration-service` consumes the event |
| 5 | Refresh the page as Customer A | Bell badge shows unread count incremented by 1 |
| 6 | Click the 🔔 bell | Dropdown opens showing "Your parcel has been assigned" notification |
| 7 | Click the notification item | Notification marked as read |
| 8 | Verify unread count decremented | Badge shows N-1 unread |

### Expected Result
✅ Notification appears in bell within seconds of the Kafka event being published.

### Evidence to Capture
- Screenshot of bell badge with unread count before and after
- Screenshot of notification dropdown showing notification text

---

## Scenario 2 — Customer Receives an Email

**What this demonstrates:** Kafka → email consumer → Mailpit email delivery

### Preconditions
- Customer A is registered with a valid email address
- Mailpit SMTP is running on port 1025 (included in `docker-compose.yml`)
- Mailpit UI is accessible at `http://localhost:8025`

### Steps

| # | Action | Expected Result |
|---|---|---|
| 1 | Log in as Customer A | Authenticated session |
| 2 | Create a new parcel via Customer Delivery Portal | `parcel.created.v1` event published to Kafka |
| 3 | Wait ~5 seconds | `integration-service` consumes the event |
| 4 | Open Mailpit at `http://localhost:8025` | Mailpit web UI loads |
| 5 | Find the email addressed to Customer A | Email subject: "Your parcel has been created" |
| 6 | Open the email | Shows parcel tracking number, status, and customer name |

### Alternative trigger (status change):
```bash
# Trigger a status change event via dispatcher (or admin curl):
curl -s -X PATCH http://localhost/api/v1/dispatch/assignments \
  -H "Authorization: Bearer <DISPATCHER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"parcelId":"<PARCEL_ID>","status":"OUT_FOR_DELIVERY"}'
# → parcel.status-changed.v1 published → email "Your parcel is out for delivery"
```

### Expected Result
✅ Email appears in Mailpit within seconds. Recipient, subject, and parcel information are correct.

### Evidence to Capture
- Screenshot of Mailpit inbox showing the email
- Screenshot of email body showing tracking number and status

---

## Scenario 3 — Customer Uses the AI Assistant

**What this demonstrates:** Angular AI Assistant UI → `/api/v1/assistant/chat` → real parcel data retrieval

### Preconditions
- Customer A is logged in and has at least one parcel
- Note Customer A's parcel tracking number (e.g. `SD-20261113-ABC123`)
- `integration-service` running (with `ASSISTANT_PROVIDER=fake` for demo, or `claude` for live LLM)

### Steps

| # | Action | Expected Result |
|---|---|---|
| 1 | Log in as Customer A | Authenticated session |
| 2 | Click **AI Assistant** in the navigation bar | Navigates to `/customer/assistant` |
| 3 | Click suggestion chip: "Where is my parcel?" | Message sent to backend |
| 4 | Assistant responds | Shows list of recent parcels with statuses |
| 5 | Type: "What is the status of parcel SD-20261113-ABC123?" | Sends message with tracking number |
| 6 | Assistant responds | Shows current parcel status, tracking number, and details |
| 7 | Type: "Show me the tracking history for SD-20261113-ABC123" | Sends message |
| 8 | Assistant responds | Shows status timeline events |
| 9 | Type: "Who is delivering my parcel?" | Sends message |
| 10 | Assistant responds | Shows courier assignment info or "unavailable" if unassigned |
| 11 | Type: "What is the weather today?" | Sends unsupported question |
| 12 | Assistant responds | "I can help with SwiftDrop parcels…" (safe rejection) |

### Expected Result
✅ Assistant retrieves real parcel data for Customer A's parcels and responds accurately. Unsupported questions are safely rejected.

### Evidence to Capture
- Screenshot of AI Assistant page with conversation showing parcel status
- Screenshot of safe rejection for unsupported topic

---

## Scenario 4 — Secure Parcel Lookup (Multi-Tenant Isolation)

**What this demonstrates:** Customer A cannot access Customer B's parcel data via the assistant or MCP

### Preconditions
- Customer A and Customer B are both registered
- Customer B has at least one parcel with a known tracking number

### Steps

| # | Action | Expected Result |
|---|---|---|
| 1 | Log in as Customer A | Authenticated as A |
| 2 | Navigate to `/customer/assistant` | AI Assistant page |
| 3 | Type: "What is the status of parcel SD-XXXXXXXX-YYYYYY?" (Customer B's tracking number) | Sends message |
| 4 | Assistant responds | "I could not find that parcel among your SwiftDrop parcels." |
| 5 | Confirm Customer B's parcel data is not shown | ✅ Access denied |
| 6 | Log out and log in as Customer B | Authenticated as B |
| 7 | Type the same query with Customer B's own tracking number | Sends message |
| 8 | Assistant responds | Shows Customer B's actual parcel status ✅ |

**MCP direct test (via curl):**
```bash
# Using Customer A's JWT, attempt to retrieve Customer B's parcel
curl -s -X POST http://localhost/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <CUSTOMER_A_JWT>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_parcel_by_tracking_number",
      "arguments": { "trackingNumber": "SD-XXXXXXXX-YYYYYY" }
    }
  }'
# Expected: { "error": { "code": -32004, "message": "Parcel not found or not owned by this customer" } }
```

### Expected Result
✅ Customer A receives a "not found" response for Customer B's parcel. Customer B can retrieve their own parcel. The backend enforces ownership — the frontend cannot bypass it.

### Evidence to Capture
- Screenshot of assistant rejection message when querying another customer's parcel
- Curl output showing JSON-RPC `-32004` error

---

## Scenario 5 — MCP Tool Demonstration

**What this demonstrates:** MCP server startup, tool discovery, tool execution, auth enforcement, and error handling

### Preconditions
- `integration-service` is running
- Customer A has a valid JWT

### Steps

| # | Action | Expected Result |
|---|---|---|
| 1 | Initialize MCP session | `{"result": {"protocolVersion": "2024-11-05", "serverInfo": {"name": "swiftdrop-assistant"}}}` |
| 2 | List available tools | 6 tools returned: `get_parcel_by_tracking_number`, `get_customer_parcels`, `get_tracking_history`, `get_delivery_information`, `get_parcel_summary`, `estimate_delivery_fee` |
| 3 | Call `get_parcel_summary` with Customer A's JWT | Returns count breakdown of parcels by status |
| 4 | Call `get_customer_parcels` with `{"page": 0, "size": 5}` | Returns first page of Customer A's parcels |
| 5 | Call `estimate_delivery_fee` with valid districts and weight | Returns calculated fee in LKR |
| 6 | Call `get_parcel_by_tracking_number` without auth | `{"error": {"code": -32001, "message": "Authenticated customer token is required"}}` |
| 7 | Call `get_parcel_by_tracking_number` with malformed tracking number | `{"error": {"code": -32602, "message": "..."}}` |
| 8 | Call unknown method `"get_secret_data"` | `{"error": {"code": -32601, "message": "..."}}` |

**Step 1 curl:**
```bash
curl -s -X POST http://localhost/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <CUSTOMER_A_JWT>" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

**Step 2 curl:**
```bash
curl -s -X POST http://localhost/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <CUSTOMER_A_JWT>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

**Step 5 curl (fee estimate):**
```bash
curl -s -X POST http://localhost/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <CUSTOMER_A_JWT>" \
  -d '{
    "jsonrpc": "2.0", "id": 5, "method": "tools/call",
    "params": {
      "name": "estimate_delivery_fee",
      "arguments": {
        "pickupDistrict": "Colombo",
        "destinationDistrict": "Kandy",
        "weightKg": 2.5,
        "sizeCategory": "MEDIUM"
      }
    }
  }'
```

### Expected Result
✅ All 6 tools discoverable. Valid calls return parcel data for the authenticated customer. Auth enforcement and input validation return correct JSON-RPC error codes.

### Evidence to Capture
- Terminal output of `tools/list` showing all 6 tools
- Terminal output of a successful `get_parcel_summary` call
- Terminal output of `-32001` (no auth) and `-32004` (wrong parcel) error responses

---

## Quick Demo Setup Checklist

```bash
# 1. Start the full stack
docker compose -f deploy/docker-compose.yml --profile core up -d

# 2. Confirm all services healthy
kubectl get pods -n bookdrop   # (or docker compose ps)

# 3. Open the app
open http://localhost

# 4. Open Mailpit
open http://localhost:8025

# 5. Register two customer accounts
#    Customer A: customerA@demo.local / Demo1234!
#    Customer B: customerB@demo.local / Demo1234!

# 6. Get Customer A JWT for API calls
TOKEN=$(curl -s -X POST http://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customerA@demo.local","password":"Demo1234!"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# 7. Test MCP
curl -s -X POST http://localhost/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

