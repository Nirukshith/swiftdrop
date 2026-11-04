# SwiftDrop — AI Agent Guidelines

Welcome, AI coding assistant. This repository follows strict pair-programming and shared engineering rules across 10 members.

## Mandatory Instructions

1. **Read Shared Rules First:** Before writing or proposing any code, read the centralized shared development workflow in [`docs/sharedrule.md`](docs/sharedrule.md).
2. **Follow Authoritative Sources:**
   - Architecture: Approved ADRs in [`docs/decisions/`](docs/decisions/).
   - APIs & Events: Merged specifications in [`contracts/openapi/`](contracts/openapi/) and [`contracts/events/`](contracts/events/).
   - Requirements: System Requirements Specification in [`docs/srs.md`](docs/srs.md).
   - Tasks: The exact assigned GitHub issue.
3. **Branching & Git:**
   - Always branch from the latest `development` branch (`git fetch origin && git switch development && git pull`).
   - Never commit or push directly to `development` or `main`.
   - Every PR must target `development`.
   - Never merge PRs automatically.
4. **Member Boundaries:**
   - Touch only the packages, frontend folders, and database tables assigned to the active member scope.
   - Do not modify files outside the assigned issue's scope.
5. **Seek Confirmation:**
   - Present your implementation plan, affected files, and dependency analysis.
   - Wait for explicit user confirmation before writing code.
6. **Testing & Quality:**
   - Backend: `mvn -B verify -pl <module> -am` (Java 21).
   - Frontend: `npm ci && npm test -- --ci && npm run build` (Node 22).
   - Never skip, delete, or weaken failing tests.
7. **Commit & Attribution:**
   - Use Conventional Commits.
   - Never add AI author or co-author attribution trailers.
8. **Failure Escalation:**
   - If any command fails twice consecutively, stop and ask the user.

For complete rules, member ownership mappings, and conflict resolution hierarchies, refer directly to [`docs/sharedrule.md`](docs/sharedrule.md).
