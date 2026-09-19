# SA Command

Owner-facing desktop operations software for SA Production. Phase 2 adds persisted productions, crew scheduling, work, a unified calendar, meetings, payroll, operational employee views, and a real aggregate Command dashboard to the Phase 1 people and attendance foundation.

## Requirements

- Java 21 and Maven 3.9+
- Node.js 22+ and npm 10+
- Rust stable and the Tauri 2 platform prerequisites
- Docker with Compose for PostgreSQL

## Start locally

1. Copy `.env.example` to `.env` if you want to override defaults.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the API: `mvn -f apps/backend/pom.xml spring-boot:run`.
4. Install the desktop dependencies: `npm --prefix apps/desktop install`.
5. Start the web UI: `npm --prefix apps/desktop run dev`.
6. For the native window, use `npm --prefix apps/desktop run tauri dev` after installing Rust/Tauri prerequisites.

The demo account is documented in [docs/demo.md](docs/demo.md). The API defaults to `http://localhost:8080`; the Vite UI defaults to `http://localhost:1420`.

## Verification

- Backend: set `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` on current Docker Desktop for Windows, then run `mvn -f apps/backend/pom.xml verify`
- Frontend: `npm --prefix apps/desktop test -- --run`
- Type/build: `npm --prefix apps/desktop run build`
- E2E: `npm --prefix apps/desktop run test:e2e`

Testcontainers and end-to-end tests require Docker and installed Playwright browsers respectively.

`apps/backend/src/test/resources/docker-java.properties` selects Docker API 1.44 for Docker Engine 29 compatibility. Browser tests use a stateful API harness for deterministic interaction and screenshots; PostgreSQL/Testcontainers suites separately verify migrations, real persistence, linked-domain behavior and authenticated HTTP. See the [Phase 2 checklist](docs/phase-2-checklist.md), [payroll policy](docs/payroll.md), and [Phase 1 closure evidence](docs/phase-1-closure.md).

## Scope boundary

Communications remains an explicit Phase 3 boundary. Phase 2 does not implement WhatsApp, provider webhooks, an outbox, message simulation, or notification automation.
