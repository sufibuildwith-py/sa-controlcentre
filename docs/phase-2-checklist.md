# Phase 2 closure checklist

## Operations

- [x] Production CRUD, filtered list, enforced lifecycle and audit
- [x] Crew assignment, duplicate prevention, overlap conflict and audited override
- [x] Persisted tasks, progress history, overdue/DONE/BLOCKED behavior
- [x] Month, week and agenda calendar with production, meeting and task-deadline links
- [x] Meetings, participants, notes, response state and normal-task action items
- [x] Payroll snapshots, policy, adjustments, approval, payment, lock and immutability
- [x] Real Command aggregates and employee Work, Payroll and Performance evidence
- [x] Deterministic repeat-safe Phase 2 demo fixtures
- [x] Communications remains a Phase 3 placeholder

## Demo reset and verification

The seed runs only in demo mode and inserts Phase 2 fixtures when no productions exist. It creates four productions, 25 tasks across all states, four meetings, linked schedule/deadline events, a deterministic overlap, the current calculated period and a prior locked period. Re-running startup does not duplicate the fixture set.

Run PostgreSQL with `docker compose up -d postgres`. On current Docker Desktop for Windows, expose its active engine to Maven with `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`; `docker-java.properties` supplies API 1.44 compatibility. Then run `mvn -f apps/backend/pom.xml verify`, `npm --prefix apps/desktop test -- --run`, `npm --prefix apps/desktop run build`, and `npm --prefix apps/desktop run test:e2e`.

The browser harness is deterministic and mocked at the HTTP boundary. PostgreSQL integration tests independently exercise real migrations, production/crew/calendar conflict behavior, task/deadline history, meetings/actions, payroll arithmetic and locked snapshot immutability.
