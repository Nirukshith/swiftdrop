# SwiftDrop Design Diagram Tracking Matrix

**Coordinator & PDF Assembler:** Member M8  
**Scope:** Design Document & Final Architecture PDF Assembly  
**Target Delivery:** Week 1 / Week 2 Milestone  

---

## 1. Overview

This document tracks all required design diagrams across the 10 SwiftDrop team members (M1–M10). As the final design PDF assembler, Member M8 monitors submission status, verifies notation standards, and compiles the diagrams into the unified course submission document.

---

## 2. Master Diagram Status Matrix

| ID | Diagram Title | Type | Owner | Service / Scope | File Path | Status | Notes |
|:---|:---|:---|:---|:---|:---|:---:|:---|
| **D-01** | Core Use Case Catalog | Use Case | **M1** | System-wide / All Portals | [`docs/diagrams/use-cases.md`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/use-cases.md) | **Completed** | Full actor and use case specs for all 4 portals |
| **D-02** | High-Level System Architecture | Component / C4 | **M9** | Infrastructure & Platform | [`docs/diagrams/architecture.md`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/architecture.md) | **Completed** | Nginx, 4 Spring Boot microservices, Kafka, Postgres |
| **D-03** | OAuth2 Login & Token Flow | Sequence | **M10** | `auth-service` | [`docs/diagrams/seq-oauth-login.md`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/seq-oauth-login.md) | **Completed** | Google OAuth2 code grant, JWT issuance, cookie rotation |
| **D-04** | Notification Channel Delivery | Sequence | **M3** | `integration-service` | [`docs/diagrams/notification-sequence.mmd`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/notification-sequence.mmd) | **Completed** | Email (Mailpit) & SMS mock dispatching |
| **D-05** | Secure Password Reset Flow | Sequence | **M3** | `auth-service` / `integration-service` | [`docs/diagrams/password-reset-sequence.mmd`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/password-reset-sequence.mmd) | **Completed** | Token generation, email link, password update |
| **D-06** | AI Delivery Assistant & MCP | Sequence | **M3** | `integration-service` | [`docs/diagrams/assistant-sequence.mmd`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/assistant-sequence.mmd) | **Completed** | Claude LLM tool calls & MCP server interactions |
| **D-07** | Merchant Onboarding & Order API | Sequence | **M6** | `integration-service` | [`docs/diagrams/seq-merchant-order.md`](file:///c:/Users/prade/Desktop/swiftdrop/swiftdrop/docs/diagrams/seq-merchant-order.md) | **Completed** | OAuth2 client credentials, bulk upload |
| **D-08** | Courier Delivery Execution & Proof | Sequence | **M8** | `operations-service` | `docs/diagrams/courier-execution-sequence.puml` | **Drafted** | Delivery list, step transitions, proof photo upload |
| **D-09** | Audit Trail Recording & Query | Sequence | **M8** | `auth-service` | `docs/diagrams/audit-log-sequence.puml` | **Drafted** | Non-repudiation logging, Admin query endpoint |
| **D-10** | Parcel State Lifecycle Machine | State Machine | **M1** | `parcel-service` | `docs/diagrams/parcel-lifecycle-state.puml` | *Pending* | States CREATED through DELIVERED/FAILED |
| **D-11** | Live Tracking & SSE Stream | Sequence | **M2** | `parcel-service` | `docs/diagrams/tracking-sse-sequence.puml` | *Pending* | Browser SSE connection, Kafka status listener |
| **D-12** | Dispatch Queue & Workload Assign | Sequence | **M4** | `operations-service` | `docs/diagrams/dispatch-workload-sequence.puml` | *Pending* | Unassigned queue, assignment, workload counts |
| **D-13** | Dynamic Pricing & Quote Strategy | Sequence | **M5** | `operations-service` | `docs/diagrams/pricing-quote-sequence.puml` | *Pending* | Weight, volume, zone calculation rules |
| **D-14** | Webhook Dispatch & Exponential Retry | Sequence | **M7** | `integration-service` | `docs/diagrams/webhook-delivery-sequence.puml` | *Pending* | HMAC signature, 5x retry, dead-letter topic |
| **D-15** | Courier Failure & Daily Summary | Sequence | **M9** | `operations-service` | `docs/diagrams/courier-summary-sequence.puml` | *Pending* | Reattempt counter, daily summary cards |
| **D-16** | Global Domain Entity-Relationship (ER) | ER Diagram | **M1 / M10** | All Databases | `docs/diagrams/domain-er-diagram.puml` | *Pending* | Cross-service schema relationships |

---

## 3. Submission & Formatting Guidelines

All team members must adhere to the following standards when committing diagram files:

1. **Format Standards**:
   - Primary: PlantUML (`.puml`) or Mermaid (`.mmd` / markdown fenced blocks).
   - Clean UTF-8 text with no binary image check-ins directly in git.
2. **PlantUML Styling Convention**:
   ```plantuml
   @startuml
   skinparam monochrome false
   skinparam shadowing false
   skinparam defaultFontName "Helvetica,Arial,sans-serif"
   skinparam roundcorner 8
   skinparam ParticipantPadding 20
   skinparam BoxPadding 10
   autonumber "<b>[00]</b>"
   ...
   @enduml
   ```
3. **Diagram Assembly Workflow**:
   - Member M8 renders diagrams to vector SVG/PDF using `plantuml` or `@mermaid-js/mermaid-cli`.
   - Diagrams are incorporated with markdown design chapters and compiled into the final project design PDF.

---

## 4. Pending Deliverables Before Week 2

To complete the design PDF assembly, Member M8 requires the following from peers before the start of Week 2:
1. **From M1**: Final internal parcel assignment contract (`PUT /internal/v1/parcels/{id}/assignment`) and state machine diagram (**D-10**).
2. **From M9**: Courier proof retrieval integration endpoint and summary cards contract.
3. **From M10**: Finalized claims schema for `auth-service` and domain ER diagram contribution (**D-16**).
