# EVE Phase 1 — Implementation Map & Architectural References

## 1. Actual Repository Implementation Map

This map documents real classes, methods, endpoints, database entities, and frontend components discovered during reconnaissance.

### Frontend (`apps/desktop/`)

- **Router & Application Root**:
  - [`apps/desktop/src/app/App.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/app/App.tsx)
  - Navigation route mount: `<Route path="/eve" element={<EvePage />} />`
  - Segmented top switcher: [`FloatingDomainSwitcher`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/components/layout/AppShell.tsx) in `AppShell.tsx`, positioned immediately after `Billing` (`{ label: "Eve", to: "/eve" }`).
- **Feature Folder**:
  - `apps/desktop/src/features/eve/`
  - `EvePage.tsx`: Main command console workspace.
  - `EvePage.test.tsx`: Vitest tests for the command console.
  - `eve.api.ts`: API client functions using `api<T>(path, init)`.
  - `eve.types.ts`: TypeScript contracts mirroring backend DTOs.
- **Shared UI Primitives**:
  - [`apps/desktop/src/components/ui/sa.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/components/ui/sa.tsx)
  - Bento architecture: `SABentoGrid`, `SABentoCard`, `CardHeader`, `MetricCard`.
  - Buttons & Inputs: `SAButton`, `SAIconButton`, `SearchField`.
  - States & Feedback: `SkeletonCard`, `EmptyState`, `Tooltip`, `useReducedMotion`, `appSpring`.
- **API Client & Networking**:
  - [`apps/desktop/src/lib/api.ts`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/lib/api.ts)
  - Resolves `API_URL` (`http://localhost:8080/api/v1` or Tauri IPC).
  - Automatically unwraps `ApiEnvelope.data`.
- **Query & UI State**:
  - TanStack Query v5 (`useQuery`, `useMutation`, `useQueryClient`).
  - Zustand (`useUiStore` in `apps/desktop/src/app/store/ui.ts`).

### Backend (`apps/backend/`)

- **Package Root**: `com.saproduction.command`
- **Eve Target Package**: `com.saproduction.command.eve`
- **Security & Authorization**:
  - [`SecurityConfig.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/config/SecurityConfig.java)
  - Stateless session, `BearerSessionFilter`, `/api/v1/eve/**` secured under `authenticated()`.
- **Shared Envelope & Exceptions**:
  - `com.saproduction.command.shared.ApiEnvelope<T>` (`ApiEnvelope.of(data)`).
  - `com.saproduction.command.shared.ApiException`.
- **Employee Finance Read Integration Point**:
  - `com.saproduction.command.finance.FinanceReadService.employee(UUID id)`: Authoritative source for `earned`, `paid`, `outstanding`, and `obligations`.
  - `com.saproduction.command.employee.Employee360Service.get360(UUID id)`: Full 360 view aggregating employee identity, finance, operations, and today's status.
  - `com.saproduction.command.employee.EmployeeService.list(String search, Status status)`: Bounded search for employee candidates.
  - `com.saproduction.command.employee.EmployeeRepository`: `findByEmployeeCodeIgnoreCase(String code)`, `findAllByStatusNotOrderByDisplayName(...)`.
- **Database Migrations**:
  - Tool: Flyway (`apps/backend/src/main/resources/db/migration/`).
  - Latest existing: `V027__production_optional_schedule.sql`.
  - Eve Phase 1 migration: `V028__eve_foundation.sql` (creating `eve_sessions`, `eve_messages`, `eve_trace_events` strictly for Eve metadata).

---

## 2. External Architectural References & How They Are Adapted

We analyzed two primary open-source references as instructed:

### 1. AnythingLLM (`Mintplex-Labs/anything-llm`)

- **Inspiration**:
  - **Workspace & Session Boundaries**: Chat context and state are strictly contained within sessions.
  - **Model Provider Abstraction**: A single internal interface decouples the application from model provider implementations (`LocalModelProvider`, `TestModelProvider`, Cloud).
  - **Local-First & Privacy**: Credentials, DB connection strings, and sensitive system state are never dumped into model context.
- **SA Command Adaptation**:
  - Implemented as Java `EveModelProvider` interface with a deterministic `TestModelProvider` for unit/integration tests.
  - Session and message metadata are persisted in PostgreSQL via `eve_sessions` and `eve_messages`.
  - No foreign runtime (e.g. Node/Python microservice) or vector database is added; existing PostgreSQL and Spring Boot architecture is strictly preserved.

### 2. DocMind AI (`BjornMelin/docmind-ai-llm`)

- **Inspiration**:
  - **Retrieval Routing**: Tiered deterministic retrieval pipeline before invoking semantic assistance (`Exact ID → Normalized match → Bounded DB search → Candidate set resolution`).
  - **Deterministic Test Fixtures & Evaluation**: Tests run against pre-recorded, deterministic evaluation fixtures with zero dependence on a live LLM or external network.
  - **Observable Telemetry & Trace**: Distinct lifecycle steps (`STARTED`, `INTERPRETING`, `SEARCHING`, `MATCHED`, `VALIDATING`, `PLANNING`, `COMPLETED`, `BLOCKED`, `FAILED`).
- **SA Command Adaptation**:
  - Implemented in `EveRetrievalService` with deterministic resolution and provenance capture (`spokenValue`, `canonicalId`, `canonicalName`, `matchMethod`, `confidence`).
  - Implemented in `EveService` with truthful trace events emitted and persisted to `eve_trace_events`.
  - Business data (employee notes, task descriptions) is treated strictly as untrusted DATA, mitigating prompt injection.
