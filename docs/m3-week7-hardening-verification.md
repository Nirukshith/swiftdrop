# SwiftDrop — Week 7 M3 Test & Hardening Verification Guide

**Assigned Member:** M3 — @Nirukshith  
**Issue:** [#230 — Test and Secure Kafka Notifications, Email Delivery, AI Assistant, Parcel Lookup, and MCP](https://github.com/Nirukshith/swiftdrop/issues/230)  
**Base Branch:** `development`  
**Feature Branch:** `feature/m3-week7-notification-assistant-hardening`

---

## 1. Executive Summary

This document details the testing, hardening, security validation, and cross-service boundary verification completed for Member M3 during Week 7 of the SwiftDrop project.

Building upon the merged foundational implementations from Weeks 3–6 (Issues #165, #175, #197, #220), the focus of Week 7 was hardening edge cases, verifying multi-tenant parcel isolation, strengthening notification consumer resilience, and explicitly validating MCP and AI assistant security boundaries.

Key Accomplishments:

1. **Multi-Tenant Parcel Isolation (AI Assistant & MCP):** Automated security tests confirm that Customer B cannot retrieve Customer A's parcel data via `/api/v1/assistant/chat` or any MCP tool (`get_parcel_by_tracking_number`, `get_tracking_history`, `get_delivery_information`). Violations return JSON-RPC error `-32004` or HTTP 404 — never the other customer's data.
2. **Role Authorization Hardening (ADR 001):** Unauthenticated requests to `/api/v1/assistant/chat` and `/mcp` return `401 Unauthorized`. Non-customer roles (`DISPATCHER`, `ADMIN`) receive `403 Forbidden`.
3. **Prompt & MCP Input Validation:** Blank assistant messages return `400 Bad Request`. Messages exceeding 500 characters are rejected. Malformed tracking numbers, negative pagination parameters, oversized page sizes, missing required MCP arguments, unknown MCP methods, and invalid JSON-RPC structures each return the correct JSON-RPC error code (`-32600`, `-32601`, `-32602`).
4. **Notification Consumer Resilience:** Unit tests verify safe handling of null events, null event IDs, non-UUID event IDs, missing `customerId`, blank email recipients, unconfigured mail senders, `MailSendException` propagation (enabling Spring Kafka retry/DLT routing), and safe DLT handler execution without unhandled exceptions.
5. **Cross-Service Blocker Resolution:** Both previously reported blockers — Pricing API 401 (resolved by M10 in commit `5f6c865`, `InternalPricingServiceFilter`) and Webhook retry failure (resolved by M7 in PR #270 / #234) — were confirmed resolved on `development` prior to branching.

---

## 2. Requirements & Hardening Coverage Matrix

| Requirement | Scope | Test Class | Tests | What Is Verified | Hardening Added |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NF-01 / ADR 001** Assistant Auth | `integration-service` | `AssistantSecurityIntegrationTest` | 16 | Unauthenticated 401, non-customer 403, multi-tenant parcel isolation via chat and MCP tools | Full role matrix across `/assistant/chat` and `/mcp` |
| **NF-01** Prompt Validation | `integration-service` | `AssistantSecurityIntegrationTest` | 16 | Blank message → 400, length > 500 → 400, invalid tracking → -32602, negative page → -32602, size > 50 → -32602, missing args → -32602, unknown method → -32601, invalid JSON-RPC → -32600 | Input guard across all MCP tool entry points |
| **NF-01** MCP Multi-Tenant Isolation | `integration-service` | `AssistantSecurityIntegrationTest` | 16 | Customer B denied access to Customer A's tracking number via all three MCP tools; returns JSON-RPC -32004 | `findOwnedParcel()` enforced at tool layer |
| **KN-01 / ADR 002** Notification Consumer | `integration-service` | `NotificationConsumerHardeningTest` | 11 | Null event, null eventId, non-UUID eventId, missing customerId, blank email recipient, unconfigured mail sender, MailSendException propagation, DLT handler safety | Prevents silent message loss; enforces Kafka retry eligibility |

---

## 3. Role Authorization Matrix (ADR 001)

Automated security tests enforce strict role separation for M3-owned endpoints:

| Endpoint | Method | `ROLE_CUSTOMER` | `ROLE_DISPATCHER` | `ROLE_ADMIN` | `ROLE_COURIER` | `ROLE_MERCHANT` | Unauthenticated |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/v1/assistant/chat` | `POST` | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/mcp` | `POST` (initialize) | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/notifications` | `GET` | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/notifications/unread-count` | `GET` | **200 OK** | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |
| `/api/v1/notifications/{id}/read` | `PATCH` | **200 OK** (own) / 404 (other) | 403 Forbidden | 403 Forbidden | 403 Forbidden | 403 Forbidden | 401 Unauthorized |

---

## 4. AI Assistant & MCP Security Analysis

### 4.1 Multi-Tenant Parcel Ownership Mechanism

The `AssistantToolsImpl.findOwnedParcel()` method enforces customer parcel isolation:

1. It calls `parcelClient.list(null, ..., callerToken)` using the authenticated caller's JWT.
2. `parcel-service` filters the result to only the calling customer's parcels (via JWT `sub` claim).
3. `findOwnedParcel()` then matches the requested tracking number against the caller-owned list.
4. If no match is found (Customer B querying Customer A's tracking number), `ParcelNotFoundException` is thrown.
5. The MCP layer maps `ParcelNotFoundException` to JSON-RPC error `-32004` — no data from the other customer is ever exposed.

### 4.2 MCP Input Validation Coverage

| Scenario | Expected Error Code | Verified |
| :--- | :--- | :--- |
| Malformed / invalid tracking number | `-32602` (Invalid params) | ✅ |
| Negative page number | `-32602` (Invalid params) | ✅ |
| Page size > 50 | `-32602` (Invalid params) | ✅ |
| Missing required argument | `-32602` (Invalid params) | ✅ |
| Unknown MCP method name | `-32601` (Method not found) | ✅ |
| Malformed JSON-RPC payload | `-32600` (Parse/invalid request) | ✅ |

---

## 5. Kafka Notification Consumer Resilience

### 5.1 Retry & Dead Letter Configuration

The notification consumer is configured with:
```
@RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000))
```
Retry topics: `integration-notification.retry-1000`, `.retry-2000`, `.retry-4000`, `.retry-8000`  
Dead letter topic: `integration-notification.dlt`

### 5.2 Error Handling Decisions

| Scenario | Handling | Rationale |
| :--- | :--- | :--- |
| Null event envelope | Log + skip (no throw) | Prevents infinite retry loop for unrecoverable input |
| Null / non-UUID eventId | Log + skip (no throw) | Idempotency guard cannot be applied; safe skip |
| Missing `customerId` | Mark as processed + skip save | Prevents duplicate no-op retries |
| Blank email recipient | Skip `EmailChannel` send, log warning | No email sent; event still processed successfully |
| Unconfigured mail sender | Skip `EmailChannel`, log warning | Graceful degradation in environments without SMTP |
| `MailSendException` | **Propagated** to Kafka | Enables Spring Kafka retry/DLT routing for transient SMTP failures |
| DLT handler | Log only, no rethrow | Prevents DLT processing from causing cascading exceptions |

---

## 6. Automated Test Evidence

### 6.1 Integration Service Tests

- **Command:** `mvn -B verify -pl services/integration-service -am`
- **Result:** **BUILD SUCCESS**
- **Total Tests Run:** 288 tests, 0 failures, 0 errors, 0 skipped
- **Completed At:** 2026-11-13T19:16:56+05:30 (elapsed: 06:11 min)
- **New Test Classes Added:**
  - `AssistantSecurityIntegrationTest`: 16 tests
  - `NotificationConsumerHardeningTest`: 11 tests

### 6.2 Frontend Tests & Build

- **Jest Tests:** `npm test -- --ci`
- **Result:** **65 suites passed, 492 tests passed, 0 failures**
- **Production Build:** `npm run build`
- **Result:** **SUCCESS** (production bundle generated, 0 compilation or template errors)

---

## 7. Cross-Service Blocker Status

| Blocker | Status | Resolution |
| :--- | :--- | :--- |
| Pricing API 401 on `GET /api/v1/pricing/quotes` from integration-service | ✅ **Resolved** | M10 added `InternalPricingServiceFilter` in commit `5f6c865` on `development` |
| Webhook retry test failure (M7 delivery proof & retry) | ✅ **Resolved** | M7 merged PR #270 / Issue #234 fix on `development` |

---

## 8. Cross-Member Dependencies & Boundaries

1. **M1 (`parcel-service`):**
   - Authoritative parcel data source; `ParcelServiceClient` used by `AssistantToolsImpl`.
   - Customer-scoped parcel list filtering enforced inside `parcel-service` via JWT `sub` claim.
   - M3 owns the assistant/MCP layer only; parcel ownership is enforced at the M1 API boundary.
2. **M10 (`auth-service`):**
   - JWT authentication filter, JWKS, and `ROLE_CUSTOMER` / `ROLE_ADMIN` claims.
   - `InternalPricingServiceFilter` resolves the Pricing API 401 blocker.
3. **M7 (`integration-service` webhook):**
   - Webhook consumer co-located in `integration-service`; retry/DLT topology verified independently by M7 PR #270.

---

## 9. Acceptance Criteria Checklist (Issue #230)

- [x] Kafka notification event consumption verified (valid events, null events, missing fields, duplicate prevention).
- [x] Email delivery pipeline tested (consumer → channel selection → recipient resolution → `MailSendException` propagation).
- [x] Notification consumer resilience hardened (retry propagation, DLT safety, missing customerId, blank recipient).
- [x] AI assistant authentication and authorization tested (unauthenticated 401, non-customer 403).
- [x] AI assistant prompt validation tested (blank message, length > 500).
- [x] MCP tool multi-tenant parcel isolation verified (Customer B blocked from Customer A's data, `-32004`).
- [x] MCP input validation tested (all 6 error code scenarios: `-32600`, `-32601`, `-32602`, `-32004`).
- [x] Role authorization matrix enforced across all M3-owned endpoints (5 roles + unauthenticated).
- [x] Cross-service blockers (Pricing API 401, Webhook retry) confirmed resolved on `development`.
- [x] Integration service Maven build passing: 288 tests, 0 failures.
- [x] Frontend Jest tests and production build passing: 492 tests, 0 failures.
- [x] No M3-external files modified without approval.
- [x] Focused PR branch prepared for `development` referencing Issue #230 only.
