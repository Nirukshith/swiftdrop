# SwiftDrop MCP & AI Assistant Integration Guide

**Owner:** M3 — Nirukshith  
**Service:** `integration-service` (Port 8084)  
**Last Updated:** 2026-11-13

---

## 1. What Is MCP and How SwiftDrop Uses It

**Model Context Protocol (MCP)** is an open standard that lets AI agents call structured tools in a backend application using a JSON-RPC 2.0 protocol. SwiftDrop implements a lightweight MCP server inside `integration-service` to expose six read-only customer tools that the AI Delivery Assistant can call, and that external AI agents (Claude Desktop, Cursor, etc.) can connect to directly.

The MCP server enforces the same multi-tenant, customer-scoped authorization as every other SwiftDrop API. It cannot be used to retrieve another customer's data.

---

## 2. Architecture Overview

```
  ┌────────────────────────────────────────────────────────────┐
  │                      Angular Frontend                      │
  │   Customer logs in → JWT issued → AI Assistant page       │
  │   POST /api/v1/assistant/chat  (Bearer <JWT>)              │
  └────────────────┬───────────────────────────────────────────┘
                   │ HTTP (via Nginx gateway :8080)
                   ▼
  ┌────────────────────────────────────────────────────────────┐
  │            integration-service  :8084                      │
  │                                                            │
  │  ┌─────────────────┐    intent routing    ┌─────────────┐ │
  │  │ AssistantService│ ─────────────────────▶ AssistantTools│ │
  │  └────────┬────────┘                      └──────┬──────┘ │
  │           │ prompt + facts                        │        │
  │           ▼                                       │        │
  │  ┌─────────────────┐              ┌───────────────▼──────┐ │
  │  │  LlmClient      │              │ ParcelServiceClient   │ │
  │  │  (Claude/Fake)  │              │ GET /api/v1/parcels   │ │
  │  └─────────────────┘              └──────────────────────┘ │
  │                                                            │
  │  POST /mcp  (MCP JSON-RPC 2.0 endpoint)                   │
  │  ┌────────────────────────────────────────────┐           │
  │  │          McpServerController               │           │
  │  │  tools/list · tools/call · initialize      │           │
  │  └────────────────────────────────────────────┘           │
  └────────────────────────────────────────────────────────────┘
                   │
                   ▼
  ┌────────────────────────────────────────────────────────────┐
  │            parcel-service  :8082                           │
  │  GET /api/v1/parcels  (customer-scoped by JWT sub)         │
  │  GET /api/v1/parcels/{id}/tracking-events                  │
  │  GET /api/v1/parcels/{id}/delivery-info                    │
  │  GET /api/v1/pricing/quotes                                │
  └────────────────────────────────────────────────────────────┘
```

**Key security principle:** Every MCP tool call passes the authenticated customer's JWT to `ParcelServiceClient`. The parcel service filters results by the JWT `sub` claim. This ensures Customer A can never retrieve Customer B's parcel, regardless of what tracking number is supplied to a tool.

---

## 3. MCP Server Configuration

The MCP server is a standard Spring Boot `@RestController` mapped to `POST /mcp`.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `ASSISTANT_PROVIDER` | No | `fake` | LLM backend: `fake` (demo) or `claude` (production) |
| `LLM_API_KEY` | Only if `claude` | — | Anthropic Claude API key — **never commit this value** |
| `LLM_MODEL` | No | `claude-3-5-haiku-latest` | Claude model name |
| `LLM_PROVIDER_URL` | No | `https://api.anthropic.com` | LLM provider base URL |
| `LLM_PROVIDER_TIMEOUT` | No | `PT10S` | Timeout for LLM calls (ISO-8601 duration) |
| `ASSISTANT_UPSTREAM_TIMEOUT` | No | `PT3S` | Timeout for backend parcel-service calls |

> [!IMPORTANT]
> Never commit `LLM_API_KEY` to version control. Set it as a Kubernetes Secret or Docker environment variable. The service starts and operates in demo mode without it when `ASSISTANT_PROVIDER=fake`.

### Local Run (Demo Mode — No API Key Required)

```bash
# From the project root (requires docker-compose stack running)
cd services/integration-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The service starts with `ASSISTANT_PROVIDER=fake` by default. The `FakeLlmClient` returns deterministic responses that include the actual backend parcel data — suitable for demos without an Anthropic API key.

### Production Mode (Real Claude LLM)

```bash
export ASSISTANT_PROVIDER=claude
export LLM_API_KEY=sk-ant-...   # your Anthropic key
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 4. Available MCP Tools

All tools require a valid customer JWT (`Authorization: Bearer <token>`). Tools are read-only and scoped to the authenticated customer's parcels.

### Tool 1: `get_parcel_by_tracking_number`

Retrieves the current status of a specific parcel owned by the customer.

**Input schema:**

| Parameter | Type | Required | Pattern |
|---|---|---|---|
| `trackingNumber` | string | ✅ | `^SD-\d{8}-[A-Z0-9]{6}$` |

**Example request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_parcel_by_tracking_number",
    "arguments": { "trackingNumber": "SD-20261113-ABC123" }
  }
}
```

**Example success response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{ "type": "text", "text": "ParcelStatusResult[...]" }],
    "structuredContent": { "trackingNumber": "SD-20261113-ABC123", "status": "OUT_FOR_DELIVERY", ... }
  }
}
```

**Error — parcel not owned by customer:**
```json
{ "jsonrpc": "2.0", "id": 1, "error": { "code": -32004, "message": "Parcel not found or not owned by this customer" } }
```

---

### Tool 2: `get_customer_parcels`

Lists all parcels belonging to the authenticated customer with optional status filter and pagination.

**Input schema:**

| Parameter | Type | Required | Constraints |
|---|---|---|---|
| `status` | string | ❌ | Parcel status filter (e.g. `CREATED`, `DELIVERED`) |
| `page` | integer | ❌ | Page number, min 0 |
| `size` | integer | ❌ | Page size, min 1, max 50 |

---

### Tool 3: `get_tracking_history`

Returns the full status timeline / tracking events for an owned parcel.

**Input schema:**

| Parameter | Type | Required |
|---|---|---|
| `trackingNumber` | string | ✅ |

---

### Tool 4: `get_delivery_information`

Returns courier assignment and delivery information for an owned parcel.

**Input schema:**

| Parameter | Type | Required |
|---|---|---|
| `trackingNumber` | string | ✅ |

---

### Tool 5: `get_parcel_summary`

Summarizes the authenticated customer's parcel statuses (counts by status). Takes no parameters.

---

### Tool 6: `estimate_delivery_fee`

Calculates a delivery fee quote based on pickup district, destination district, weight, and parcel size.

**Input schema:**

| Parameter | Type | Required | Constraints |
|---|---|---|---|
| `pickupDistrict` | string | ✅ | Sri Lanka district name |
| `destinationDistrict` | string | ✅ | Sri Lanka district name |
| `weightKg` | number | ✅ | 0.1 – 50 kg |
| `sizeCategory` | string | ✅ | `SMALL`, `MEDIUM`, or `LARGE` |

---

## 5. MCP Protocol Usage

### Initialize

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT>" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

### Discover Tools

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

### Call a Tool

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_parcel_by_tracking_number",
      "arguments": { "trackingNumber": "SD-20261113-ABC123" }
    }
  }'
```

---

## 6. AI Assistant REST API

The assistant also exposes a higher-level conversational API consumed by the Angular UI.

**Endpoint:** `POST /api/v1/assistant/chat`  
**Auth:** Bearer JWT (CUSTOMER role required)  
**Content-Type:** `application/json`

**Request:**
```json
{
  "message": "Where is my parcel SD-20261113-ABC123?",
  "conversationId": "conv-abc123"
}
```

**Response:**
```json
{
  "reply": "Here is the SwiftDrop information I found: ...",
  "conversationId": "conv-abc123"
}
```

**Validation:**
- `message` must not be blank → `400 Bad Request`
- `message` must not exceed 500 characters → `400 Bad Request`
- Unauthenticated → `401 Unauthorized`
- Non-CUSTOMER role → `403 Forbidden`

---

## 7. Authentication and Authorization

| Scenario | Behaviour |
|---|---|
| No `Authorization` header | `401 Unauthorized` |
| Invalid / expired JWT | `401 Unauthorized` |
| Valid JWT, role ≠ `CUSTOMER` | `403 Forbidden` |
| Valid CUSTOMER JWT, owns parcel | Tool returns parcel data |
| Valid CUSTOMER JWT, does **not** own requested parcel | JSON-RPC error `-32004` |

---

## 8. Error Code Reference

| JSON-RPC Code | Meaning |
|---|---|
| `-32600` | Invalid JSON-RPC request (malformed structure) |
| `-32601` | Method not found (unknown tool name) |
| `-32602` | Invalid parameters (missing required arg, bad type, out-of-range value) |
| `-32001` | Missing authentication token |
| `-32004` | Parcel not found or not owned by this customer |

---

## 9. Security Considerations

1. **Multi-tenant isolation is enforced server-side.** The frontend cannot bypass it by supplying a different customer's tracking number.
2. **Internal backend tokens are never exposed** in MCP responses. `AssistantTools` uses internal service tokens only for backend-to-backend calls; these never reach the client.
3. **No prompt injection protection is claimed** for the LLM layer — the assistant is scoped to SwiftDrop parcel data only. Unsupported topics (passwords, SQL, shell commands) are rejected before reaching the LLM.
4. **LLM API keys are never logged** or included in error responses.
5. **The `FakeLlmClient` is safe for demos** — it does not call any external service and returns actual backend data.

---

## 10. Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `401` on `/mcp` or `/api/v1/assistant/chat` | JWT missing, expired, or malformed | Re-login and use fresh token |
| `403` on `/api/v1/assistant/chat` | Logged in as non-CUSTOMER role | Switch to a CUSTOMER account |
| JSON-RPC `-32004` | Parcel not owned by this customer | Use a tracking number from your own account |
| `"LLM provider is unavailable"` | `ASSISTANT_PROVIDER=claude` but `LLM_API_KEY` not set | Set `LLM_API_KEY` or switch to `ASSISTANT_PROVIDER=fake` |
| Assistant returns `"temporarily unavailable"` | Upstream parcel-service is down | Restart parcel-service, check Docker/K8s pod health |
| MCP `tools/list` returns empty | Request reached wrong endpoint | Confirm `POST /mcp`, not `GET /mcp` |

