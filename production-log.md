# SA Command — Production Deployment & Architecture Log

**Document Version:** 1.0.0  
**Target Environment:** Production  
**Main Production Frontend:** [https://sa-command.vercel.app](https://sa-command.vercel.app)  
**Main Production Backend API:** [https://sa-command-backend-production.up.railway.app](https://sa-command-backend-production.up.railway.app)  
**Navigator Realtime Gateway:** [https://sa-navigator-gateway-production.up.railway.app](https://sa-navigator-gateway-production.up.railway.app)  
**Navigator Realtime WebSocket:** `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator`  

---

## 1. Executive Summary & Architecture Topology

SA Command is deployed as a distributed, high-security operations platform for SA Production. It consists of three containerized backend services hosted on **Railway**, backed by a dedicated PostgreSQL cluster with private mesh networking, and a statically compiled Single Page Application (SPA) frontend hosted on **Vercel**.

```
+---------------------------------------------------------------------------------------------------------+
|                                     PRODUCTION ARCHITECTURE OVERVIEW                                    |
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
             |       (sa-command.vercel.app)      |  |    (sa-navigator-gateway:8091)     |
             +-----------------+------------------+  +-----------------+------------------+
                               |                                       ^
                     HTTPS API | (Bearer Token)                        | Private Mesh
                               v                                       | (X-Navigator-Service-Key)
             +------------------------------------+                    |
             |      Railway Main Backend API      |--------------------+
             |    (sa-command-backend:8080)       |
             +-----------------+------------------+
                               |
                               | JDBC over Private Mesh
                               | (postgres.railway.internal:5432)
                               v
             +------------------------------------+
             |         Railway PostgreSQL         |
             |       (PostgreSQL 18.6 DB)         |
             |  - Schema `public`: Main ERP       |
             |  - Schema `navigator`: Live Roster |
             +------------------------------------+
```

---

## 2. Source Control & Release Authority

All production deployments across Railway and Vercel are connected to a single canonical GitHub repository branch:

- **Repository**: [`https://github.com/delusional-deployments/sa-panel`](https://github.com/delusional-deployments/sa-panel)
- **Branch**: `production`
- **Initial Release SHA**: `fc8d5888fd492b7a439e1b7bccbc458030edc52a`
- **Production Hotfix SHAs**:
  - `211a5c3`: Bundle MapLibre web worker and shared module assets into `public/assets/`
  - `47b751d`: Add `apps/desktop/vercel.json` SPA rewrite rules for client-side routing
- **Current Production HEAD**: `47b751d65fd2385e3a2bf5f8df2a00943ce27da2`

---

## 3. Railway Infrastructure Setup

Railway hosts the data layer and both Spring Boot Java microservices within a single private network project.

### Project & Environment Identifiers
- **Railway Workspace / Team**: `delusional-deployments's Projects` (`cd21481f-3c25-40a8-acc6-3019880c5ce8`)
- **Railway Project Name**: `SA Command Production`
- **Railway Project ID**: `c4f90fff-0e85-43e3-935c-bea1cee2682d`
- **Railway Environment**: `production`
- **Railway Environment ID**: `13678d7e-bfef-42ee-b9ba-f4d52137eab0`

---

### Service 1: PostgreSQL Database (`Postgres`)

- **Service ID**: `bc1d2232-dadc-40ef-8527-81275ed5ce7b`
- **Image**: PostgreSQL 18.6
- **Storage**: Persistent volume mounted at `/var/lib/postgresql/data/pgdata`
- **Private Host**: `postgres.railway.internal`
- **Private Port**: `5432`
- **Public Access**: **Disabled** (Database is not reachable from the public internet)

#### Multi-Schema Isolation Strategy
To prevent collisions and maintain modular domain isolation while sharing the single database cluster:
1. **`public` schema**: Dedicated to `sa-command-backend`. Migrated sequentially from `V001` through `V033` using Flyway. Manages core ERP tables: `users`, `employees`, `attendance_records`, `productions`, `tasks`, `meetings`, `bills`, `finance_ledger`, and `api_sessions`.
2. **`navigator` schema**: Dedicated to `sa-navigator-gateway`. Configured with `SPRING_FLYWAY_SCHEMAS=navigator` and `SPRING_FLYWAY_CREATE_SCHEMAS=true`. Migrated via `V001__navigator_core.sql` and `V002__navigator_mobile_duty_projection.sql`. Manages device registrations, duty sessions, paired devices, and location breadcrumbs.

---

### Service 2: Main ERP Backend (`sa-command-backend`)

- **Service ID**: `01d25749-c086-4866-8a1a-daac06d0b662`
- **GitHub Connection**: `delusional-deployments/sa-panel` (Branch: `production`)
- **Root Directory**: `apps/backend`
- **Build Engine**: Railpack (JDK 21.0.2, Maven 3.9.16)
- **Build Command**: `mvn -B clean package -DskipTests`
- **Artifact**: `target/sa-command-backend-1.2.0.jar`
- **Start Command**: `java -jar target/sa-command-backend-1.2.0.jar`
- **Internal Port**: `8080` (mapped via `${{PORT}}`)
- **Public Domain**: `https://sa-command-backend-production.up.railway.app`
- **Health Check**: `GET /actuator/health` → `{"status":"UP"}`

#### Configured Production Environment Variables

| Variable | Value / Expression | Purpose |
| :--- | :--- | :--- |
| `APP_MODE` | `production` | Enforces production security, validates HTTPS CORS origins, disables synthetic data seeders and table truncation controllers. |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Standard operating timezone for attendance, scheduling, and audit timestamps. |
| `SERVER_PORT` | `${{PORT}}` | Dynamically assigned container listen port. |
| `JAVA_VERSION` | `21` | Toolchain enforcement. |
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Internal mesh JDBC connection string. |
| `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` | Database username (`postgres`). |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` | Database password reference from PostgreSQL service. |
| `DESKTOP_ORIGINS` | `https://sa-command.vercel.app,https://sa-command-backend-production.up.railway.app,tauri://localhost,http://tauri.localhost` | Whitelist of allowed CORS origins. Wildcards are strictly rejected. |
| `MESSAGING_PROVIDER` | `unconfigured` | Blocks console-based message logging in production. |
| `EVE_MODEL_PROVIDER` | `TEST` | Operates EVE in safe, deterministic, grammar-based mode without unverified cloud AI leaks. |
| `FINANCE_UNLOCK_CODE_HASH` | `dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47` | Constant-time SHA-256 hash of the 6-digit owner finance unlock PIN (`153011`). |
| `FINANCE_ACCESS_TTL_MINUTES` | `30` | Duration of unlocked financial access window. |
| `BOOTSTRAP_OWNER_EMAIL` | `owner@saproduction.in` | First-boot clean install owner bootstrap email. |
| `BOOTSTRAP_OWNER_PASSWORD` | `[High-Entropy Secret]` | First-boot clean install owner bootstrap password. |
| `BOOTSTRAP_OWNER_NAME` | `SA Production Owner` | Owner display name. |
| `NAVIGATOR_ENABLED` | `true` | Enables Navigator API routes on the main backend. |
| `NAVIGATOR_GATEWAY_URL` | `http://sa-navigator-gateway.railway.internal:8091` | Direct internal mesh routing to the gateway. |
| `NAVIGATOR_SERVICE_KEY` | `[High-Entropy Shared Secret]` | Shared authentication secret for inter-service ticket generation. |
| `NAVIGATOR_ORGANIZATION_PUBLIC_ID` | `6be51f4b-e5bd-4e91-8d60-4e41a72ad86f` | Canonical tenant organization UUID. |
| `NAVIGATOR_SIMULATOR_ENABLED` | `false` | Disables mock/fake location data generation in production. |

---

### Service 3: Navigator Realtime Gateway (`sa-navigator-gateway`)

- **Service ID**: `2dc75c4e-af4a-4791-b72d-94f4625318db`
- **GitHub Connection**: `delusional-deployments/sa-panel` (Branch: `production`)
- **Root Directory**: `apps/navigator-gateway`
- **Build Engine**: Railpack (JDK 21.0.2, Maven 3.9.16)
- **Build Command**: `mvn -B clean package -DskipTests`
- **Artifact**: `target/sa-navigator-gateway-1.0.0-N1.jar`
- **Start Command**: `java -jar target/sa-navigator-gateway-1.0.0-N1.jar`
- **Internal Port**: `8091` (mapped via `${{PORT}}`)
- **Internal Private Host**: `sa-navigator-gateway.railway.internal:8091`
- **Public Domain**: `https://sa-navigator-gateway-production.up.railway.app`
- **WebSocket Endpoint**: `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator`
- **Health Check**: `GET /actuator/health` → `{"status":"UP"}`

#### Configured Production Environment Variables

| Variable | Value / Expression | Purpose |
| :--- | :--- | :--- |
| `PORT` | `8091` | Gateway HTTP and WebSocket port. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}?currentSchema=navigator` | Dedicated database connection scoped to `navigator` schema. |
| `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` | Database user (`postgres`). |
| `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` | Database password reference. |
| `SPRING_FLYWAY_SCHEMAS` | `navigator` | Tells Flyway to isolate schema migrations to `navigator`. |
| `SPRING_FLYWAY_CREATE_SCHEMAS`| `true` | Creates schema `navigator` if not already present. |
| `NAVIGATOR_SERVICE_KEY` | `[High-Entropy Shared Secret]` | Verifies internal requests from `sa-command-backend` when minting realtime tickets. |
| `NAVIGATOR_TICKET_TTL_SECONDS`| `90` | Lifetime of browser WebSocket connection tickets. |
| `CORS_ALLOWED_ORIGINS` | `https://sa-command.vercel.app,https://sa-command-backend-production.up.railway.app` | Whitelist for HTTP and WebSocket upgrades. |

---

## 4. Vercel Web Frontend Deployment

The web client is deployed to **Vercel** as a high-performance static SPA using Vite and React 19.

### Vercel Project Configuration
- **Project Name**: `sa-command`
- **Project ID**: `prj_G9axujMwfb3iNCsvC5hqy7LlXvTn`
- **Vercel Team / Org**: `delusuion-deployements` (`team_Qf0upMoqUVPKxbTLqi7Blved`)
- **GitHub Connection**: `delusional-deployments/sa-panel` (Branch: `production`)
- **Framework Preset**: `Vite`
- **Root Directory**: `apps/desktop`
- **Node.js Version**: `22.x`
- **Build Command**: `npm run build:web` (`tsc -b && vite build --mode web`)
- **Output Directory**: `dist`
- **Live Production URL**: [https://sa-command.vercel.app](https://sa-command.vercel.app)

#### Configured Environment Variables on Vercel

| Variable | Production Value | Purpose |
| :--- | :--- | :--- |
| `VITE_API_URL` | `https://sa-command-backend-production.up.railway.app/api/v1` | Public backend API URL consumed by `src/lib/api.ts`. |
| `VITE_APP_MODE` | `production` | Prevents demo login pre-fill and enforces strict token verification. |
| `VITE_BUILD_TARGET` | `web` | Directs Vite to bypass native Tauri security blocks during bundling. |
| `VITE_NAVIGATOR_ENABLED` | `true` | Conditionally compiles the Navigator top domain navigation and code-split lazy chunk. |
| `VITE_NAVIGATOR_GATEWAY_URL` | `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator` | Target WSS URL for STOMP live location tracking. |

---

## 5. End-to-End Inter-Service Communication Flow

### A. Authentication & Session Flow
1. User enters owner credentials at `https://sa-command.vercel.app/`.
2. Browser issues `POST /api/v1/auth/login` to `https://sa-command-backend-production.up.railway.app`.
3. Backend validates credentials against BCrypt(12) hash in PostgreSQL `users` table.
4. Backend creates a 12-hour cryptographic bearer session in `api_sessions` and returns it.
5. Frontend stores token in browser `sessionStorage` (`sa-command-session`), protecting it across tab reloads while isolating it from cross-tab leakage.

### B. Navigator Realtime Handshake Flow
```
Browser                       Main Backend API                  Navigator Gateway
   |                                 |                                  |
   |-- 1. POST /navigator/ticket --->|                                  |
   |   (Bearer session token)        |-- 2. POST /admin/realtime-ticket>|
   |                                 |   (X-Navigator-Service-Key)      |
   |                                 |<-- 3. Return 90s ticket ---------|
   |<-- 4. Return ticket to browser -|                                  |
   |                                                                    |
   |=== 5. WSS Handshake (wss://.../ws/navigator) =====================>|
   |--- 6. STOMP CONNECT (passcode: ticket) --------------------------->|
   |<-- 7. STOMP CONNECTED ---------------------------------------------|
   |--- 8. SUBSCRIBE /topic/organizations/{orgId}/locations ----------->|
   |<-- 9. Stream live location updates --------------------------------|
```

---

## 6. Critical Production Fixes & Hardening Post-Deployment

During post-deployment verification, two production-only regressions were diagnosed, solved, and verified live:

### 1. MapLibre GL Web Worker 404 Fix
- **Symptom**: In local development, the Navigator map worked; in production on Vercel, the map showed zoom controls and attribution but the tile canvas remained blank cream.
- **Root Cause**: `maplibre-gl` resolves worker scripts dynamically via `new URL('./' + workerName, import.meta.url).href`. Rollup/Vite did not bundle this dynamic import into `dist/assets/`. The browser requested `https://sa-command.vercel.app/assets/maplibre-gl-worker.mjs`, which returned `404 Not Found`. Without the worker, `.pbf` vector tiles from OpenFreeMap were never requested or parsed.
- **Solution**:
  1. Placed `maplibre-gl-worker.mjs` and `maplibre-gl-shared.mjs` in `apps/desktop/public/assets/`.
  2. Implemented `maplibreWorkerPlugin` in `apps/desktop/vite.config.ts` to automatically copy and sync worker files from `node_modules/maplibre-gl/dist` to `public/assets` on every build.
  3. Added explicit worker URL initialization in `NavigatorMap.tsx`:
     ```ts
     if (typeof window !== "undefined" && !maplibregl.getWorkerUrl()) {
       maplibregl.setWorkerUrl("/assets/maplibre-gl-worker.mjs");
     }
     ```
  4. Pushed commit `211a5c3`.

### 2. Vercel Single-Page Application (SPA) Routing Fix
- **Symptom**: Navigating to `https://sa-command.vercel.app/` worked, but refreshing or directly navigating to `https://sa-command.vercel.app/navigator` returned Vercel `404 NOT_FOUND`.
- **Root Cause**: Vercel looked for static file `dist/navigator.html` which did not exist.
- **Solution**:
  1. Created `apps/desktop/vercel.json` with client-side SPA rewrites:
     ```json
     {
       "rewrites": [
         {
           "source": "/(.*)",
           "destination": "/index.html"
         }
       ]
     }
     ```
  2. Pushed commit `47b751d`.

---

## 7. Verification & Smoke Test Matrix

| # | Check / Test Case | Target / Endpoint | Result | Verification Detail |
| :-: | :--- | :--- | :-: | :--- |
| **1** | **Backend Health** | `https://sa-command-backend-production.up.railway.app/actuator/health` | **PASS** | HTTP 200, `{"status":"UP"}` |
| **2** | **Gateway Health** | `https://sa-navigator-gateway-production.up.railway.app/actuator/health` | **PASS** | HTTP 200, `{"status":"UP"}` |
| **3** | **PostgreSQL Private Mesh** | `postgres.railway.internal:5432` | **PASS** | Internal latency < 2ms, zero public internet exposure |
| **4** | **Flyway Backend Migration** | PostgreSQL `public` schema | **PASS** | Migrations `V001` through `V033` applied cleanly |
| **5** | **Flyway Gateway Migration** | PostgreSQL `navigator` schema | **PASS** | Migrations `V001` and `V002` applied cleanly |
| **6** | **Owner Bootstrap & Login** | `POST /api/v1/auth/login` | **PASS** | HTTP 200, returns `ROLE_OWNER` and 12h Bearer token |
| **7** | **Session Revocation** | `POST /api/v1/auth/logout` | **PASS** | Token invalidated immediately in DB; next request returns 401 |
| **8** | **Finance Privacy Veil** | `GET /api/v1/finance/overview` | **PASS** | Blocked with `403 FINANCE_LOCKED` until unlocked with code `153011` |
| **9** | **Realtime Ticket Minting** | `POST /api/v1/navigator/realtime-ticket` | **PASS** | Inter-service mesh authentication succeeded, 90s ticket issued |
| **10** | **Gateway Service Auth** | `POST /api/v1/admin/realtime-ticket` | **PASS** | Rejects missing/invalid keys with 401; accepts valid internal key |
| **11** | **STOMP WebSocket WSS** | `wss://.../ws/navigator` | **PASS** | WSS upgrade succeeded, authenticated via ticket, topic subscribed |
| **12** | **SPA Direct Routing** | `https://sa-command.vercel.app/navigator` | **PASS** | HTTP 200 via `vercel.json` rewrite to `index.html` |
| **13** | **Worker Asset Serving** | `https://sa-command.vercel.app/assets/maplibre-gl-worker.mjs` | **PASS** | HTTP 200, Content-Type: `application/javascript` |
| **14** | **Shared Asset Serving** | `https://sa-command.vercel.app/assets/maplibre-gl-shared.mjs` | **PASS** | HTTP 200, Content-Type: `application/javascript` |
| **15** | **Live Vector Tile Rendering** | OpenFreeMap Liberty Style (`.pbf` tiles) | **PASS** | WebGL canvas rendered with live streets, labels, and markers |

---

## 8. Summary of Active Live Endpoints

| Component | Host / Provider | Protocol | Production URL |
| :--- | :--- | :--- | :--- |
| **Web Frontend** | Vercel | HTTPS | [https://sa-command.vercel.app](https://sa-command.vercel.app) |
| **Main REST API** | Railway | HTTPS | [https://sa-command-backend-production.up.railway.app](https://sa-command-backend-production.up.railway.app) |
| **Navigator REST** | Railway | HTTPS | [https://sa-navigator-gateway-production.up.railway.app](https://sa-navigator-gateway-production.up.railway.app) |
| **Navigator WSS** | Railway | WSS | `wss://sa-navigator-gateway-production.up.railway.app/ws/navigator` |
| **PostgreSQL DB** | Railway | TCP (Private Mesh) | `postgres.railway.internal:5432` |
