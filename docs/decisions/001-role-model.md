# 001 - Role Model

Status: decided
Date: 2026-10-24
Decided by: the team

## Context
The system previously had overlapping boundaries between Dispatcher and Admin roles, with earlier drafts noting that dispatchers also held admin rights. A clear separation of administrative governance and operational fulfillment was required to establish explicit role-based access control (RBAC) across endpoints and UI portals.

## Decision
ADMIN and DISPATCHER are separated into distinct roles:
- **ADMIN**: Manages user accounts (create, enable, disable), merchant approval and rejection, and system audit logs.
- **DISPATCHER**: Handles parcel assignment, courier workload monitoring, delivery zones, and pricing rules.

## Consequences
- **M10**: Enforces distinct `ROLE_ADMIN` and `ROLE_DISPATCHER` roles in token generation, JWT claims, and endpoint security within `auth-service`.
- **M4**: Secures user management endpoints (`/api/v1/admin/users`) strictly for `ADMIN`; restricts assignment, workload, and re-queue endpoints to `DISPATCHER`.
- **M5**: Restricts merchant approval and audit viewers in the admin portal shell to `ADMIN`; restricts zone and pricing configuration forms and quote calculator to `DISPATCHER`.
- **M8**: Secures audit log retrieval (`/api/v1/admin/audit`) strictly for `ADMIN`.
- **M1, M2, M3, M6, M7, M9**: Gateway and route guards must check for the explicit required role (`ADMIN` vs `DISPATCHER`) rather than treating them interchangeably.

## Open items
- None.
