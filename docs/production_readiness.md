# SA Command — Production Readiness Specification

Date: October 2026  
Status: Production-Grade Hardening & Operational Guide  
Application: SA Command (Operations Platform for SA Production)

---

## 1. System Architecture

SA Command is built as an enterprise-grade private operations platform:

- **Backend**: Java 21 / Spring Boot 3.4.5 with Spring Security, Spring Session JDBC, Flyway migration management, and Hibernate JPA (`ddl-auto: validate`).
- **Database**: PostgreSQL 15+ with append-only ledger tables, strict domain constraints, foreign keys, and currency minor-unit representation.
- **Frontend**: React 19 / TypeScript / Vite with TanStack Query 5, Zustand (UI state only), Tailwind CSS, Schedule-X calendar, and MapLibre GL. Supports dual targets: native Tauri desktop shell or pure web application.
- **Security Boundary**: Stateless Bearer token sessions (`api_sessions`), BCrypt(12) passwords, explicit HTTPS CORS origins, and server-side Finance Privacy Veil access gates.
- **EVE Cognitive Engine**: Governed assistant with deterministic grammar parsing, strict typed tool invocation (calling canonical Spring services), and required human plan confirmation for mutations.

---

## 2. Required Environment Variables

| Variable | Required? | Default / Example | Purpose |
| :--- | :---: | :--- | :--- |
| `APP_MODE` | **YES** | `production` | Enforces production security, origin validation, and disables demo seeders/controllers. |
| `DATABASE_URL` | **YES** | `jdbc:postgresql://<host>:5432/<db>` | PostgreSQL JDBC connection URL. |
| `DATABASE_USERNAME` | **YES** | `sa_command` | Database user. |
| `DATABASE_PASSWORD` | **YES** | *(Secret)* | Must NOT equal `sa_command_dev`. |
| `DESKTOP_ORIGINS` | **YES** | `https://panel.saproduction.in` | Exact allowed HTTPS origins (comma-separated). Wildcards rejected. |
| `MESSAGING_PROVIDER` | **YES** | `unconfigured` or `meta` | Outbound message transport. `console` is rejected in production. |
| `APP_TIME_ZONE` | Optional | `Asia/Kolkata` | Operational reporting and calendar timezone. |
| `BOOTSTRAP_OWNER_EMAIL` | Clean Install | `owner@saproduction.in` | Initial owner email on clean database boot. |
| `BOOTSTRAP_OWNER_PASSWORD` | Clean Install | *(Secret, min 8 chars)* | Initial owner password on clean database boot. |
| `BOOTSTRAP_OWNER_NAME` | Optional | `Azeem Khan` | Display name of the initial owner. |
| `FINANCE_UNLOCK_CODE_HASH` | Optional | `dd7a0bb1db3c404...` | SHA-256 hash of the 6-digit finance unlock code (`153011`). |
| `FINANCE_ACCESS_TTL_MINUTES`| Optional | `30` | Duration of unlocked finance session in minutes. |
| `VITE_API_URL` | Frontend | `https://panel.saproduction.in/api/v1` | Public API base URL accessed by browser client. |
| `VITE_BUILD_TARGET` | Frontend | `web` | Directs Vite to bundle for standard web browser deployment. |

---

## 3. Database Setup & Clean Installation

Flyway automates schema creation across 33 sequential migrations (`V001__...` through `V033__...`).

### First-Run Invariant
1. Target an empty database.
2. Spring Boot runs migrations from zero.
3. All tables, check constraints, foreign keys, and indexes are created.
4. **Zero demo business records** (no sample employees, productions, tasks, clients, fake invoices) are inserted.
5. Structural reference data only is populated:
   - System notification rules (`V009`)
   - SA Production owner accounts (`AZ-2` Azeem, `AK-2` Akash) and expense categories (`V016`)
   - Default measurement units (`V031`)

---

## 4. First-Run Owner Bootstrap

When SA Command boots against an empty database with 0 users:
- If `BOOTSTRAP_OWNER_EMAIL` and `BOOTSTRAP_OWNER_PASSWORD` are supplied in the environment, `ProductionOwnerBootstrap` creates the initial `ROLE_OWNER` user.
- The password is encrypted with BCrypt (strength 12).
- An immutable audit log (`AUTH` / `OWNER_BOOTSTRAPPED`) is recorded.
- Subsequent application restarts detect `users.count() > 0` and ignore bootstrap variables.

---

## 5. Authentication & Session Governance

- **Transport**: Stateless Bearer token (`Authorization: Bearer <token>`).
- **Storage**: Stored in `api_sessions` table with cryptographic UUID tokens.
- **Expiration**: 12 hours timeout; invalid after expiry.
- **Logout**: Explicit POST `/api/v1/auth/logout` invalidates the database session immediately.
- **Client Persistence**:
  - Desktop: Tauri native secure token store (`store_session_token`).
  - Web Browser: `sessionStorage` (`sa-command-session`), isolating credentials per browser tab session.

---

## 6. Finance Privacy Veil

The Finance Privacy Veil protects all company financial records:
- **Default State**: Locked upon login. Financial endpoints (`/api/v1/finance/**`, `/api/v1/billing/**`, `/api/v1/payroll/**`, etc.) reject requests with `403 FORBIDDEN` when locked.
- **Unlock Procedure**:
  - Eligible only to authenticated users with `ROLE_OWNER`.
  - Owner initiates unlock via the Developer Settings surface.
  - Submits the 6-digit authorization code (`153011`).
  - Server verifies code against `FINANCE_UNLOCK_CODE_HASH` using constant-time comparison.
  - A time-bound grant (`financeExpiresAt`) is issued on the active `api_sessions` record (default: 30 minutes).
- **Protection Lifecycle**:
  - Grant expires automatically after TTL or upon user logout.
  - Rate-limited via `FinanceAccessThrottle` (max 5 failed attempts per 15 minutes per IP/user) to prevent brute-force attacks.
  - Unlock PIN is never logged and never bundled in frontend JavaScript.

---

## 7. EVE Intelligence Engine

- **Architecture**: Decoupled via `EveModelProvider`.
- **System of Record**: Domain services remain authoritative; EVE performs no direct SQL or ledger modifications.
- **Production Mode**: Defaults to `EVE_MODEL_PROVIDER=TEST` (pure deterministic grammar routing).
- **External Model Server**: For LLM reasoning in production, set `EVE_MODEL_PROVIDER=LOCAL_QWEN_THINKING` and point `EVE_LOCAL_MODEL_URL` to an external inference container (e.g. `vLLM` or `llama.cpp` on a GPU host).
- **Graceful Failure**: If the model service is unreachable, EVE returns an explicit `EVE_MODEL_FAILED` error code rather than crashing.

---

## 8. Storage & File Handling

- **PDF Invoices**: Generated on the fly via OpenPDF into in-memory byte streams and returned as Base64 JSON envelopes.
- **Excel Ledgers**: Generated in-memory via Apache POI into byte streams and delivered as Base64 JSON envelopes.
- **Browser Download**: Client converts Base64 to `Blob` and triggers download via standard DOM anchor links (`URL.createObjectURL`).
- **Disk Independence**: The backend writes zero operational files to the local container disk. It is fully compatible with ephemeral container filesystems.

---

## 9. Browser / Web Requirements

- Modern browser (Chrome 110+, Edge 110+, Firefox 110+, Safari 16+).
- Responsive across all viewports from 320px mobile phones up to 1920px+ desktop monitors.
- Build target: `npm run build:web` in `apps/desktop`.

---

## 10. Operational Risks & Mitigations

| Risk | Mitigation |
| :--- | :--- |
| Accidental demo data wipe | `DemoResetController` has `matchIfMissing=false` and requires explicit `app.mode=demo`. |
| Weak owner credentials | `ProductionOwnerBootstrap` requires minimum 8-character passwords; `ProductionConfiguration` validates non-default passwords. |
| Insecure CORS | `ProductionConfiguration` validates exact HTTPS origins; wildcards (`*`) cause startup failure. |
| Leaked stack traces | `server.error.include-stacktrace: never` and `GlobalExceptionHandler` returns sanitized error envelopes. |
