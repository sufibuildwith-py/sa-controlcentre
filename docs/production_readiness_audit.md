# SA Command — Production Readiness Audit (Phase 0)

Date: October 2026  
Status: In-Depth Architecture & Working Tree Audit  
Reference Product: SA Command (Internal Operations Platform for SA Production)

---

## 1. Executive Summary

This audit assesses the readiness of SA Command for production deployment to `https://github.com/delusional-deployments/sa-panel`. The existing desktop design, mobile responsiveness layer, and core domain features are preserved.

The application contains robust enterprise architectures (stateless bearer token sessions, immutable double-entry finance ledger, in-memory PDF/Excel streaming, and governed cognitive workflows). However, several critical production blockers and configuration gaps exist:
1. **Demo data triggers**: `DemoResetController` had `matchIfMissing = true` on `app.mode=demo`, creating an accidental data-wipe risk on unconfigured deployments. `SyntheticDataSeedController` was unguarded.
2. **First-run owner bootstrap**: With `app.demo-seed=false` and an empty database, no mechanism existed to securely bootstrap an owner account without desktop setup mode.
3. **Frontend session persistence in browser mode**: In `apps/desktop/src/lib/api.ts`, web session storage was restricted to `VITE_APP_MODE === "demo"`, preventing session persistence across reloads in browser production mode.
4. **Vite production build constraints**: `vite.config.ts` enforced Tauri desktop CSP checks unconditionally for non-demo builds, blocking pure web-targeted builds.
5. **Local AI dependencies**: EVE's `LocalQwenModelProvider` relies on a Windows x64 `llama-server.exe` binary and local 2.7GB GGUF weights, which cannot run in cloud container environments without an external model service.

---

## 2. Inventory & Component Classification

| Component / Subsystem | Primary Location | Classification | Evidence & Findings |
| :--- | :--- | :--- | :--- |
| **Spring Boot Backend** | `apps/backend/` | **PRODUCTION-READY** | Spring Boot 3.4.5, Java 21, stateless architecture, JPA + Hibernate with `ddl-auto: validate`. Runs cleanly on server or desktop. |
| **React/TypeScript Frontend** | `apps/desktop/` | **IMPLEMENTED** / **PARTIAL** | React 19, React Router 7, TanStack Query 5, Tailwind CSS. Desktop UI complete; mobile responsiveness pass complete (320px–1024px). Browser session persistence needed fix. |
| **Vite Configuration** | `apps/desktop/vite.config.ts` | **PARTIAL** / **NEEDS CONFIGURATION** | Throws error if production API origin is not in `tauri.conf.json` CSP. Must support pure web application builds. |
| **Tauri Native Code** | `apps/desktop/src-tauri/` | **PRODUCTION-READY** | Encapsulated in `src-tauri`. Frontend gracefully detects browser vs Tauri via `__TAURI_INTERNALS__`. |
| **PostgreSQL & Flyway** | `apps/backend/src/main/resources/db/migration/` | **PRODUCTION-READY** | Migrations V001 through V033 create full schema deterministically from zero. Zero demo business records inserted. V016 creates required business chart of accounts and profit split rules. |
| **Authentication & Sessions** | `com.saproduction.command.auth` | **PRODUCTION-READY** | BCrypt(12) passwords, UUID bearer tokens in DB (`api_sessions`), 12h expiration, immediate revocation on logout. |
| **First-Run Owner Bootstrap** | `com.saproduction.command.auth` | **BLOCKING** | Currently only `OwnerSetupController` exists (requires desktop secret) or demo seeder. Clean production startup requires secure environment variable bootstrap (`BOOTSTRAP_OWNER_EMAIL`/`BOOTSTRAP_OWNER_PASSWORD`). |
| **Finance Engine** | `com.saproduction.command.finance` | **PRODUCTION-READY** | Append-only double-entry ledger, dual owner current accounts (Azeem & Akash), profit splits, invoice tracking, reconciliation snapshots. |
| **Finance Privacy Veil** | `com.saproduction.command.finance.access` | **PRODUCTION-READY** | Session-scoped grants, SHA-256 code verification, IP rate-limiting, server-side filter denying financial data when veil is locked. |
| **Billing** | `com.saproduction.command.billing` | **PRODUCTION-READY** | Draft/issue lifecycle, GST tax calculations, Apache POI Excel export, OpenPDF PDF generation. All delivered in-memory via Base64 JSON. |
| **File / Document Handling** | Backend & Desktop | **PRODUCTION-READY** | In-memory generation (`ByteArrayOutputStream`); browser download via DOM `Blob` and `URL.createObjectURL`. Zero persistent local disk writes. |
| **EVE Cognitive Runtime** | `com.saproduction.command.eve` | **NEEDS EXTERNAL SERVICE** | Governed cognitive layer. System of record remains Postgres. Local Qwen provider requires local Windows executable; production requires external inference endpoint (`EVE_LOCAL_MODEL_URL`) or test provider. |
| **Navigator Service** | `apps/navigator-gateway`, `apps/navigator-mobile` | **NEEDS EXTERNAL SERVICE** / **NEEDS CONFIGURATION** | Microservice on port 8091. Safely disabled by default in production backend (`NAVIGATOR_ENABLED=false`). |
| **Demo / Seed Loaders** | `com.saproduction.command.config` | **BLOCKING** | `DemoResetController` had `matchIfMissing=true`. `SyntheticDataSeedController` had no condition. Must be disabled/scoped. |
| **Environment Configuration** | `.env.example`, `application.yml` | **NEEDS CONFIGURATION** | Root `.env.example` defaulted to demo settings. Must provide clean production template. |
| **Docker Configuration** | `docker-compose.yml` | **PRODUCTION-READY** | Postgres 17 containers for local development and integration testing. |
| **CI/CD Workflows** | `.github/workflows/` | **PRODUCTION-READY** | Workflows for backend verification, frontend tests/linting, CodeQL, and installers. |

---

## 3. Frontend Entry & Web Build Target

- **Entry Point**: `apps/desktop/index.html` mounts `apps/desktop/src/main.tsx`.
- **Router**: `BrowserRouter` from `react-router-dom` handles all routes.
- **API Transport**: Standard `fetch()` calls against `VITE_API_URL`.
- **Browser Adaptation**:
  1. `api.ts`: When `!isTauri()`, persist session token in `sessionStorage` (`sa-command-session`).
  2. `vite.config.ts`: Allow `mode === 'web'` or `VITE_BUILD_TARGET === 'web'` to bypass native Tauri CSP checks so standard web bundles compile smoothly.
  3. `SettingsPage.tsx`: Native settings (`ReleaseSettings`) are already gated behind `VITE_DESKTOP_RELEASE === 'true'`.

---

## 4. Production Blockers & Remediation Plan

### Blocker 1: Demo Controller Security (`DemoResetController`)
- **Issue**: `@ConditionalOnProperty(name = "app.mode", havingValue = "demo", matchIfMissing = true)` allows table truncation if `app.mode` is missing.
- **Fix**: Remove `matchIfMissing = true`. Require explicit `app.mode=demo`.

### Blocker 2: Unguarded Synthetic Seed Controller (`SyntheticDataSeedController`)
- **Issue**: `/api/v1/admin/seed/synthetic` had no conditional annotation.
- **Fix**: Add `@ConditionalOnProperty(name = "app.seed-synthetic.enabled", havingValue = "true")`.

### Blocker 3: Missing Clean Production Owner Bootstrap
- **Issue**: Starting on an empty production database without demo data left zero users.
- **Fix**: Add `ProductionOwnerBootstrap` that inspects `BOOTSTRAP_OWNER_EMAIL` and `BOOTSTRAP_OWNER_PASSWORD`. If `users.count() == 0`, securely creates the owner account with BCrypt password hashing.

### Blocker 4: Web Browser Session Token Loss
- **Issue**: In `apps/desktop/src/lib/api.ts`, non-Tauri tokens were only saved to `sessionStorage` if `VITE_APP_MODE === "demo"`.
- **Fix**: Store and restore tokens in `sessionStorage` for all non-Tauri environments.

### Blocker 5: Outdated Root `.env.example`
- **Issue**: `.env.example` had demo presets.
- **Fix**: Overhaul `.env.example` with clear sections: REQUIRED, OPTIONAL, and DEVELOPMENT-ONLY.
