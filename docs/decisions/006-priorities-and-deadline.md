# 006 - Priorities and Deadline

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
With a fixed project submission deadline, the team requires an explicit prioritization framework to manage development time across all 10 members. Establishing clear boundaries between critical functionality, secondary enhancements, and bonus items ensures the core system is delivered reliably on time with a clear de-scoping protocol if schedule risks arise.

## Decision
- **Final Submission Deadline**: November 10, 2026.
- **Must-have**: Core requirements delivering end-to-end functionality:
  - CU-01 to CU-06, CU-08
  - DI-01 to DI-05
  - ME-01 to ME-05, ME-07
  - CO-01 to CO-05
  - AU-01 to AU-05
  - PL-01, PL-03
- **Should-have (+)**: Scope-balancing features to drop first if time runs short:
  - Saved addresses, cancel parcel before pickup, rate delivery
  - Courier availability toggle, CSV export of reports, re-queue failed parcel
  - Merchant webhook log with retry, rotate API credentials, bulk CSV order upload
  - Courier Google Maps deep link & reattempt badge
  - Password reset, account lockout after failed logins, audit log
- **Bonus**:
  - Kubernetes deployment & Helm chart (PL-02)
  - AI Delivery Assistant with MCP server (CU-09, CU-10)

## Consequences
- **M1–M10**: All members must implement and test their respective Must-have requirements before spending time on Should-have (+) or Bonus features.
- **M1**: Focuses on core parcel lifecycle, state machine, and fee quote integration first; defers saved addresses and pre-pickup cancellation if constrained.
- **M2**: Prioritizes live tracking stream, history table, and PDF receipts; defers delivery rating and treats Helm chart as bonus work.
- **M3**: Focuses on core Kafka notification consumers (email via Mailpit, mock SMS); notification bell and password reset are treated as Should-have (+); AI assistant and MCP tools are prioritized as bonus.
- **M4**: Implements core unassigned queue and assignment first; defers bulk assignment, courier availability toggle, and failed parcel re-queuing.
- **M5**: Completes zone lookup, pricing strategy, quote endpoint, and basic report stats first; defers CSV export.
- **M6**: Completes merchant registration, approval endpoints, and order API first; defers bulk CSV upload.
- **M7**: Focuses on merchant dashboard, signed webhook delivery, and retry topics first; defers webhook delivery retry log and login lockout.
- **M8**: Prioritizes courier delivery execution cards, stepper, and proof photo upload; defers the audit log package.
- **M9**: Prioritizes failure logging, courier profile consumer, daily summary, docker-compose setup, and CI; defers Google Maps links and reattempt badges.
- **M10**: Prioritizes customer/courier/merchant authentication, JWT/JWKS, and shared frontend auth library; treats Kubernetes manifests as bonus.

## Open items
- None.
