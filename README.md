# SA Command

The operating system for SA Production.

SA Command is owner-facing desktop operations software. V1 combines people, attendance, productions, scheduling, work, meetings, payroll and closed-loop WhatsApp communications in one audited system.

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

`apps/backend/src/test/resources/docker-java.properties` selects Docker API 1.44 for Docker Engine 29 compatibility. Browser tests use a stateful API harness for deterministic interaction and screenshots; PostgreSQL/Testcontainers suites separately verify migrations, real persistence, linked-domain behavior and authenticated HTTP.

See the [architecture](docs/architecture.md), [data model](docs/data-model.md), [payroll ledger](docs/payroll.md), [WhatsApp operations](docs/whatsapp.md), [security guide](docs/security.md), [release guide](docs/release.md), [Phase 3 checklist](docs/phase-3-checklist.md), and [third-party provenance](docs/THIRD_PARTY.md).

## Demo reset

With the API running in demo mode, run `powershell -ExecutionPolicy Bypass -File scripts/reset-demo.ps1`. This authenticated reset recreates deterministic Phase 1–3 business data, notification rules, outbox records, messages and response state.
