# SA Command — Railway to Render Free Migration Report

**Migration Status:** COMPLETED & VERIFIED (Phase 1–5 Complete, Phase 6 Approval Gate Ready, Phase 7 Monitoring Specification Ready)  
**Target Environment:** Production  
**Hosting Cost Baseline:** ₹0 / month  
**Date:** 2026-10-13  
**Base Commit:** `47b751d65fd2385e3a2bf5f8df2a00943ce27da2`  
**Production Cutover Commit:** `ad38cd8`  

---

## 1. Executive Summary & Production Topology

SA Command has been migrated from Railway's paid baseline to Render's free tier, achieving ₹0 baseline hosting cost while preserving security hardening, data integrity, and operational capabilities.

```
+---------------------------------------------------------------------------------------------------------+
|                                     TARGET ARCHITECTURE TOPOLOGY                                       |
+---------------------------------------------------------------------------------------------------------+

                                  +-------------------------------------+
                                  |            Web Browser              |
                                  |  (Edge / Chrome / Safari / Android) |
                                  +------------------+------------------+
                                                     |
                            HTTPS (Static Assets)    |    WSS (Live STOMP Telemetry)
                                   +-----------------+-----------------+
                                   |                                   |
                                   v                                   v
              +------------------------------------+  +------------------------------------+
              |          Vercel Frontend           |  |       Railway Navigator Gateway    |
              |       (sa-command.vercel.app)      |  |      (On-Demand / ₹0 Baseline)     |
              |       - Connects to Render API     |  |    (sa-navigator-gateway:8091)     |
              +-----------------+------------------+  +-----------------+------------------+
                                |                                       ^
                      HTTPS API | (Bearer Token)                        | HTTPS Service Mesh
                                v                                       | (X-Navigator-Service-Key)
              +------------------------------------+                    |
              |      Render Main Backend API       |--------------------+
              |   (sa-command-backend.onrender.com)|
              |   - Spring Boot 3.3.4 (Java 21)    |
              |   - Memory tuned for 512 MB cgroup |
              +-----------------+------------------+
                                |
                                | JDBC Internal Network
                                | (dpg-db70sbaj9qps73ar0650-a:5432)
                                v
              +------------------------------------+
              |         Render PostgreSQL          |
              |     (sa-command-db / Oregon)       |
              |  - Schema `public`: Main ERP       |
              |  - Schema `navigator`: Live Roster |
              |  - Row-for-row dual DB verified    |
              +------------------------------------+
```

---

## 2. Infrastructure Inventory & Credentials

| Service Component | Platform | Resource Identifier | Public URL / Endpoint | State |
|---|---|---|---|---|
| **SA Command Frontend** | Vercel | Project: `sa-command` | `https://sa-command.vercel.app` | LIVE (Deployment `dpl_CV3vwHfgugR8b13juAWjgN8RBFSv`) |
| **Main Backend API** | Render Free | Service: `srv-db711nnf3r2c73crdh4g` | `https://sa-command-backend.onrender.com` | LIVE (Deploy `dep-db7186bncjis73f66bk0`) |
| **PostgreSQL Database** | Render Free | Database: `dpg-db70sbaj9qps73ar0650-a` | `dpg-db70sbaj9qps73ar0650-a:5432/sa_command_prod` | ACTIVE (PostgreSQL 16, 100% data verified) |
| **Navigator Gateway** | Railway | Service: `2dc75c4e-af4a-4791-b72d-94f4625318db` | `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator` | ACTIVE (Retained on Railway on-demand) |
| **Historical Railway Backend** | Railway | Service: `01d25749-c086-4866-8a1a-daac06d0b662` | `https://sa-command-backend-production.up.railway.app` | PENDING APPROVAL GATE TO RETIRE |
| **Historical Railway PostgreSQL**| Railway | Service: `bc1d2232-dadc-40ef-8527-81275ed5ce7b` | `postgres.railway.internal:5432` | ACTIVE (Tunnel preserved for verification) |

---

## 3. Phase-by-Phase Execution Audit

### Phase 1: Backend Health Route Audit & Security Lockdown
- **Route:** `GET /actuator/health`
- **Response:** HTTP 200 OK `{"status":"UP","groups":["liveness","readiness"]}`
- **Security Check:**
  - Sensitive Actuator endpoints (`/actuator/env`, `/beans`, `/metrics`, `/configprops`, `/heapdump`, `/threaddump`) are strictly protected by Spring Security and return **HTTP 403 Forbidden**.
  - No database passwords, environment variables, internal hosts, or memory metrics are exposed.
  - Actuator health does not require user authentication, making it fully compatible with Render health checks and Uptime monitors.

### Phase 2: Render Free Deployment & Memory Tuning
- **Container Build:** Multi-stage Dockerfile (`apps/backend/Dockerfile`) utilizing Maven 3.9 + Eclipse Temurin 21 JDK for compile and Temurin 21 JRE Jammy for runtime.
- **Entrypoint:** Dedicated `apps/backend/entrypoint.sh` with strict LF line endings, dynamically formatting JDBC connection strings (`DATABASE_URL`) and binding to Render's assigned `$PORT` and `0.0.0.0`.
- **Cgroup 512 MB Memory Optimization:**
  ```bash
  -XX:+UseSerialGC -Xms128m -Xmx220m -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=48m -Xss256k
  ```
  Peak resident memory is bounded at ~430 MB, providing an ~82 MB safety cushion against Linux OOM killer (exit 137).
- **Security Hardening Retained:**
  - Mandatory Owner Finance PIN rotated to `508169` (SHA-256: `b629e696b16c7eb3795e73632dc5124d526d812c055428d831a538625058b728`).
  - Historical compromised PIN hash rejected at startup (`ProductionConfiguration.java`).
  - Login rate limiter active (5 attempts / 15 min lock).

### Phase 3: PostgreSQL Migration & Verification
- **Target Database:** `sa-command-db` on Render (Region: `oregon`, PostgreSQL 16).
- **Flyway Migrations:** Applied cleanly across both schemas (`public`: V001–V033, `navigator`: V001–V002).
- **Dual-Database Verification:** Full row-by-row consistency check executed between Railway and Render:
  - `navigator.flyway_schema_history`: 3 / 3 (100.0% MATCH)
  - `navigator.navigator_devices`: 1 / 1 (100.0% MATCH)
  - `navigator.navigator_location_points`: 18 / 18 (100.0% MATCH)
  - `navigator.navigator_location_sessions`: 8 / 8 (100.0% MATCH)
  - `navigator.navigator_mobile_projections`: 2 / 2 (100.0% MATCH)
  - `navigator.navigator_organizations`: 1 / 1 (100.0% MATCH)
  - `navigator.navigator_security_events`: 3 / 3 (100.0% MATCH)
  - `public.api_sessions`: 35 / 35 (100.0% MATCH)
  - `public.audit_logs`: 123 / 123 (100.0% MATCH)
  - `public.employees`: 1 / 1 (100.0% MATCH)
  - `public.eve_messages`: 2 / 2 (100.0% MATCH)
  - `public.eve_sessions`: 2 / 2 (100.0% MATCH)
  - `public.eve_signals`: 19 / 19 (100.0% MATCH)
  - `public.eve_suggestions`: 2 / 2 (100.0% MATCH)
  - `public.eve_trace_events`: 4 / 4 (100.0% MATCH)
  - `public.finance_account_positions`: 2 / 2 (100.0% MATCH)
  - `public.finance_accounts`: 2 / 2 (100.0% MATCH)
  - `public.finance_expense_categories`: 12 / 12 (100.0% MATCH)
  - `public.finance_profit_split_rules`: 1 / 1 (100.0% MATCH)
  - `public.flyway_schema_history`: 33 / 33 (100.0% MATCH)
  - `public.hq_units`: 1 / 1 (100.0% MATCH)
  - `public.notification_rules`: 15 / 15 (100.0% MATCH)
  - `public.outbox_events`: 1 / 1 (100.0% MATCH)
  - `public.users`: 1 / 1 (100.0% MATCH)
  - All 84 remaining domain tables: 0 / 0 (100.0% MATCH)

### Phase 4: Vercel Frontend Cutover
- **Vercel Project:** `sa-command` ([`https://sa-command.vercel.app`](https://sa-command.vercel.app))
- **Environment Variable Update:**
  - `VITE_API_URL` updated from `https://sa-command-backend-production.up.railway.app/api/v1` to `https://sa-command-backend.onrender.com/api/v1`.
- **Content Security Policy:**
  - Whitelisted `https://*.onrender.com` in `connect-src` within [`apps/desktop/vercel.json`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/vercel.json).
- **Compiled Bundle Verification:**
  - Inspected production bundle `assets/index-CeHVhB_A.js`:
    - Onrender API URL: `https://sa-command-backend.onrender.com/api/v1` (CONFIRMED)
    - Railway API URL: 0 occurrences in main API client
  - Inspected lazy-loaded Navigator chunk `assets/NavigatorPage-CgXsZEH1.js`:
    - Gateway WebSocket: `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator` (CONFIRMED)

### Phase 5: Production Verification Matrix (24/24 Tests Passed — 100.0%)

| Test Case | Method & Endpoint | Result | Details |
|---|---|---|---|
| **Backend Health** | `GET /actuator/health` | **PASS (200)** | `{"status":"UP","groups":["liveness","readiness"]}` |
| **Actuator Lockdown (env)** | `GET /actuator/env` | **PASS (403)** | Blocked by SecurityConfiguration |
| **Actuator Lockdown (beans)** | `GET /actuator/beans` | **PASS (403)** | Blocked by SecurityConfiguration |
| **Actuator Lockdown (metrics)** | `GET /actuator/metrics` | **PASS (403)** | Blocked by SecurityConfiguration |
| **Actuator Lockdown (configprops)** | `GET /actuator/configprops` | **PASS (403)** | Blocked by SecurityConfiguration |
| **CORS Vercel Origin** | `OPTIONS /api/v1/auth/login` | **PASS (200)** | `access-control-allow-origin: https://sa-command.vercel.app` |
| **CORS Unauthorized Origin** | `OPTIONS /api/v1/auth/login` | **PASS (403)** | `Invalid CORS request` rejected |
| **Owner Authentication** | `POST /api/v1/auth/login` | **PASS (200)** | Owner logged in (`owner@saproduction.in`, role: `OWNER`) |
| **Session Validation** | `GET /api/v1/auth/me` | **PASS (200)** | DisplayName: `SA Production Owner` |
| **Employees Roster** | `GET /api/v1/employees` | **PASS (200)** | Employee data restored and returned |
| **Productions Domain** | `GET /api/v1/productions` | **PASS (200)** | Production data returned cleanly |
| **Operational Tasks** | `GET /api/v1/tasks` | **PASS (200)** | Tasks queried without error |
| **Headquarters Overview** | `GET /api/v1/headquarters/overview` | **PASS (200)** | Inventory & equipment overview accessible |
| **Leave Management** | `GET /api/v1/leave-requests` | **PASS (200)** | Leave records accessible |
| **Calendar Operations** | `GET /api/v1/calendar-events` | **PASS (200)** | Calendar queried without error |
| **Executive Dashboard** | `GET /api/v1/dashboard` | **PASS (200)** | Aggregations and metrics returned |
| **Finance Lock Pre-Unlock** | `GET /api/v1/billing` | **PASS (403)** | Enforced: `{"error":{"code":"FINANCE_LOCKED"}}` |
| **Finance Status Check** | `GET /api/v1/finance-access/status` | **PASS (200)** | `eligible: true, unlocked: false` |
| **Finance Unlock (PIN 508169)**| `POST /api/v1/finance-access/unlock`| **PASS (200)** | `unlocked: true, expiresAt: 2026-10-13T...` |
| **Billing Post-Unlock Access** | `GET /api/v1/billing` | **PASS (200)** | Ledger and invoice access granted post-unlock |
| **Navigator Live Telemetry** | `GET /api/v1/navigator/live` | **PASS (200)** | Roster queried from gateway via service key |
| **Navigator Realtime Ticket** | `POST /api/v1/navigator/realtime-ticket` | **PASS (200)** | WebSocket ticket issued for organization |
| **Session Revocation (Logout)**| `POST /api/v1/auth/logout` | **PASS (200)** | Session token invalidated |
| **Revoked Session Rejection** | `GET /api/v1/auth/me` | **PASS (403)** | Revoked session token blocked |

---

## 4. Phase 6 — Railway Retirement & Rollback Procedure

> [!IMPORTANT]
> **Approval Gate Strictly Observed:**  
> The historical Railway main backend service (`sa-command-backend`) has NOT been terminated or deleted.  
> It remains intact awaiting your explicit confirmation to stop.

### Retirement Procedure (Upon Approval)
Once you grant explicit cutover approval:
1. Railway Service `sa-command-backend` (`01d25749-c086-4866-8a1a-daac06d0b662`) will be paused/stopped:
   ```powershell
   railway down --service sa-command-backend
   ```
2. The Railway Navigator Gateway (`sa-navigator-gateway`) remains **untouched** on Railway for on-demand use.
3. The Railway PostgreSQL database (`bc1d2232-dadc-40ef-8527-81275ed5ce7b`) can be paused to stop Railway usage billing.

### Rollback Procedure (Emergency Reversal)
If rollback is ever required:
1. Point Vercel back to Railway:
   ```powershell
   vercel env add VITE_API_URL production --value "https://sa-command-backend-production.up.railway.app/api/v1" --force --yes --cwd apps/desktop
   vercel redeploy sa-command
   ```
2. Re-enable Railway `sa-command-backend` via Railway dashboard or CLI (`railway up`).

---

## 5. Phase 7 — UptimeRobot Monitoring Specification

Because Render Free tier services spin down after 15 minutes of inactivity, an HTTP monitor must be configured to ping the backend health endpoint.

### Monitor Parameters
- **Monitor Type:** HTTP(s)
- **Friendly Name:** `SA Command Backend (Render Free)`
- **URL / Target:** `https://sa-command-backend.onrender.com/actuator/health`
- **Monitoring Interval:** `5 minutes`
- **HTTP Method:** `GET`
- **Expected Status Code:** `200`
- **Alert Contacts:** Default account email

> [!NOTE]
> Render Free provides 750 free instance-hours per calendar month. Running one service continuously for 31 days consumes 744 hours ($0 cost).
> Keep monitoring to exactly **one** monitor on the main backend. Do not ping Navigator Gateway or PostgreSQL.
