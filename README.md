# SA Command

Owner-facing desktop operations software for SA Production. This repository implements Phase 1: the final visual shell, owner authentication, People, attendance, leave and audit foundations.

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

- Backend: `mvn -f apps/backend/pom.xml "-Dapi.version=1.44" verify`
- Frontend: `npm --prefix apps/desktop test -- --run`
- Type/build: `npm --prefix apps/desktop run build`
- E2E: `npm --prefix apps/desktop run test:e2e`

Testcontainers and end-to-end tests require Docker and installed Playwright browsers respectively.

The Docker API override supports this host's Docker 29 engine with the pinned Testcontainers version. Browser tests use a stateful mock API; the backend PostgreSQL application test separately verifies real authenticated HTTP and persistence workflows. See [Phase 1 closure evidence](docs/phase-1-closure.md) for the approved visual checks, exact results and native packaging prerequisites.

## Scope boundary

Productions, Work, Calendar, Finance and Communications are visible only as intentional later-phase destinations. Their business domains are not implemented in Phase 1.
