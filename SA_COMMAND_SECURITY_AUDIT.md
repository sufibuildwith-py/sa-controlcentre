# SA COMMAND — PRODUCTION SECURITY AUDIT REPORT
**Target Systems**: Vercel (`https://sa-command.vercel.app/`), Railway Backend (`https://sa-command-backend-production.up.railway.app/`), Railway Gateway (`https://sa-navigator-gateway-production.up.railway.app/`), GitHub Repository (`https://github.com/delusional-deployments/sa-panel`)  
**Assessment Date**: October 2026  
**Auditor**: Application Security Engineering Review  
**Branch / Baseline**: `production` (`47b751d`)  
**Assessment Status**: **AUDIT COMPLETE**  
**Verdict**: **`VULNERABILITIES FOUND — REMEDIATION REQUIRED`**

---

## 1. Executive Summary

A comprehensive, evidence-driven security audit of the production-deployed **SA Command** application and its supporting infrastructure was conducted. The assessment covered the deployed Vercel frontend, Railway-hosted Spring Boot backend, Railway-hosted Navigator gateway/WebSocket service, PostgreSQL data access patterns, employee mobile application architecture, authentication/session management, authorization matrices, and EVE AI engine boundaries.

### Summary of Posture

1. **Production Hardening Wins**:
   - The core backend has been hardened against destructive demo behaviors: `DemoResetController` and table truncation endpoints are strictly disabled in production (`app.mode != demo`), returning HTTP 403.
   - `SyntheticDataSeedController` is deactivated via `@ConditionalOnProperty`.
   - `OwnerSetupController` is disabled in server deployments (`app.deployment=server`), returning HTTP 404.
   - The Meta WhatsApp webhook receiver (`/api/v1/integrations/whatsapp/webhook`) strictly enforces HMAC-SHA256 signature verification in production, rejecting unsigned and forged payloads with HTTP 401 Unauthorized.
   - SQL queries across all domain services utilize parameterized queries with `JdbcTemplate` and Spring Data JPA, eliminating raw SQL injection vectors.
   - Production source maps on Vercel are blocked (HTTP 403 Forbidden).
   - EVE's cognitive runtime enforces a strict AI-to-business boundary: mutations are bounded to 10 registered actions, validate domain preconditions, execute exclusively through canonical Spring domain services, and require explicit human confirmation.

2. **Identified Vulnerabilities & Risks Requiring Remediation**:
   - **High**: Spring Boot Actuator endpoints (`/actuator`, `/actuator/metrics`, `/actuator/info`) are publicly exposed and unauthenticated on the live production Navigator Gateway (`sa-navigator-gateway-production.up.railway.app`), leaking detailed JVM metrics, process internals, thread states, and Spring Security filter chains.
   - **Medium**: Missing HTTP Security Headers and Clickjacking vulnerability on Vercel (`sa-command.vercel.app`). The frontend lacks `Content-Security-Policy` (CSP), `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, and `Permissions-Policy`, while emitting an overly permissive `Access-Control-Allow-Origin: *` on root HTML documents.
   - **Medium**: Published Default Finance Unlock PIN Fallback (`153011`). The default fallback SHA-256 hash in `application.yml` corresponds to `153011`, which is also documented in repository documentation. If `FINANCE_UNLOCK_CODE_HASH` is omitted in production environment variables, the Finance Privacy Veil is trivially bypassable.
   - **Medium**: Unconfigured Reverse Proxy Forwarding Strategy (`server.forward-headers-strategy`). The Spring Boot backend runs behind Railway's Hikari edge proxy without `server.forward-headers-strategy` configured, causing `request.getRemoteAddr()` to evaluate the edge proxy's IP. This risks shared IP rate-limiting Denial-of-Service or ineffective brute-force throttling on `/api/v1/auth/login` and `/api/v1/finance-access/unlock`.
   - **Medium**: Permissive WebSocket STOMP Allowed Origin Patterns (`https://*`) on Navigator Gateway (`RealtimeConfig.java`), allowing arbitrary origins to initiate WebSocket handshakes.
   - **Low**: Untracked `.env.local` files in `.gitignore`. While `.env` is ignored, `.env.local` is only ignored via local `.git/info/exclude`, creating a risk of accidental credential leakage during fresh developer or CI checkouts.
   - **Low**: Supply-Chain Dependencies with Known CVEs in devDependencies (`vitest`, `tinypool`, `tailwindcss`, `metro`, `shell-quote`).

---

## 2. Architecture & Attack Surface Overview

```mermaid
flowchart TD
    subgraph PublicInternet["Public Internet / Client Tier"]
        Browser["Desktop / Web Browser<br/>(https://sa-command.vercel.app)"]
        Mobile["Navigator Mobile App<br/>(React Native / Expo)"]
        Attacker["Untrusted Actors / Probers"]
    end

    subgraph VercelEdge["Vercel Hosting Edge"]
        VercelCDN["Vercel Static Edge CDN<br/>SPA Rewrites (/index.html)"]
    end

    subgraph RailwayCloud["Railway Cloud (sin1 Region)"]
        Backend["SA Command Backend<br/>(Spring Boot 3.4.5 / Java 21)"]
        Gateway["Navigator Gateway<br/>(Spring Boot / STOMP WebSocket)"]
        PGCommand[(PostgreSQL<br/>sa_command)]
        PGNavigator[(PostgreSQL<br/>navigator)]
    end

    Browser -->|HTTPS Static Assets| VercelCDN
    Browser -->|REST API (Bearer Token)| Backend
    Browser -->|WSS Realtime Locations| Gateway
    Mobile -->|REST API (Device Bearer)| Gateway
    Mobile -->|WSS Telemetry| Gateway
    Backend -->|X-Navigator-Service-Key| Gateway
    Backend --> PGCommand
    Gateway --> PGNavigator
    Attacker -.->|Direct HTTPS Probe| Backend
    Attacker -.->|Public Actuator Probe| Gateway
```

### Entry Points and Boundaries
1. **Frontend (`https://sa-command.vercel.app`)**:
   - Single-Page Application (SPA) bundled via Vite.
   - Authenticated via session Bearer token stored in `sessionStorage` (`sa-command-session`).
   - Connects to backend via `VITE_API_URL`.

2. **Core Operations Backend (`https://sa-command-backend-production.up.railway.app`)**:
   - Manages Users, Employees, Productions, Attendance, Headquarters, Billing, Finance, and EVE.
   - Protected by `BearerSessionFilter`, `DesktopBoundaryFilter`, and `FinanceAccessFilter`.
   - Single tenant owner model (`ROLE_OWNER`).

3. **Navigator Gateway (`https://sa-navigator-gateway-production.up.railway.app`)**:
   - Handles device pairing redemption (`/api/v1/mobile/pairings/redeem`), mobile location batch ingestion (`/api/v1/mobile/locations/batch`), and STOMP WebSocket tracking (`/ws/navigator`).
   - Administrative endpoints protected by `X-Navigator-Service-Key`.
   - Mobile endpoints protected by device token SHA-256 hash lookup.

---

## 3. Prioritized Findings Table

| ID | Title | Component | Severity | Status | Impact |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **SEC-001** | Unauthenticated Public Actuator & Metrics Exposure | Navigator Gateway | **HIGH** | Confirmed | Infrastructure reconnaissance, memory/thread/request leakage |
| **SEC-002** | Missing HTTP Security Headers & Framing Protection | Vercel Frontend | **MEDIUM** | Confirmed | Clickjacking, MIME sniffing, absence of Content Security Policy |
| **SEC-003** | Fallback to Publicly Documented Default Finance PIN | Backend Config / Docs | **MEDIUM** | Confirmed | Financial Privacy Veil bypass if env var omitted |
| **SEC-004** | Unconfigured Proxy Forwarding Strategy in IP Throttling | Backend (`application.yml`) | **MEDIUM** | Confirmed | DoS via IP lockout / ineffective brute-force protection behind proxy |
| **SEC-005** | Overly Permissive WebSocket STOMP Origin Patterns | Navigator Gateway (`RealtimeConfig`) | **MEDIUM** | Confirmed | Cross-site WebSocket handshake initiation (`https://*`) |
| **SEC-006** | Incomplete Repository `.gitignore` for `.env.local` | Git Root Configuration | **LOW** | Confirmed | Risk of accidental secret commit in future checkouts |
| **SEC-007** | Known Vulnerabilities in Dev & Mobile Dependencies | NPM Packages (Desktop & Mobile) | **LOW** | Confirmed | Supply-chain risk in development / build tooling |

---

## 4. Detailed Findings & Evidence

### SEC-001: Unauthenticated Public Actuator & Metrics Exposure
- **Finding ID**: `SEC-001`
- **Severity**: **HIGH**
- **Affected Component**: `apps/navigator-gateway` (`GatewaySecurityConfig.java`, `application.yml`)
- **Status**: **CONFIRMED** on live production
- **Evidence**:
  ```http
  GET /actuator HTTP/1.1
  Host: sa-navigator-gateway-production.up.railway.app

  HTTP/1.1 200 OK
  Content-Type: application/vnd.spring-boot.actuator.v3+json
  {"_links":{"self":{"href":"http://sa-navigator-gateway-production.up.railway.app/actuator",...},"metrics":{"href":"http://sa-navigator-gateway-production.up.railway.app/actuator/metrics",...},"info":{"href":"http://sa-navigator-gateway-production.up.railway.app/actuator/info",...}}}
  ```
  ```http
  GET /actuator/metrics HTTP/1.1
  Host: sa-navigator-gateway-production.up.railway.app

  HTTP/1.1 200 OK
  {"names":["application.ready.time","disk.free","executor.pool.size","hikaricp.connections.active","http.server.requests","jvm.memory.used","process.uptime","spring.security.filterchains",...]}
  ```
- **Root Cause**:
  In `apps/navigator-gateway/src/main/java/com/saproduction/navigator/GatewaySecurity.java`:
  ```java
  .authorizeHttpRequests(a->a.requestMatchers("/api/v1/mobile/pairings/redeem","/actuator/health","/ws/navigator/**").permitAll().anyRequest().permitAll())
  ```
  The security filter chain designates `.anyRequest().permitAll()`, inadvertently making Actuator endpoints publicly accessible over the internet without authentication.
- **Impact**:
  External attackers can query runtime memory usage, database connection pool statistics, HTTP request patterns, and internal application performance, aiding precise attack reconnaissance.
- **Safe Reproduction**:
  Execute `curl -s https://sa-navigator-gateway-production.up.railway.app/actuator/metrics`. Returns HTTP 200 with complete metrics catalog.
- **Remediation**:
  1. Restrict Actuator exposure in `apps/navigator-gateway/src/main/resources/application.yml` to health only:
     ```yaml
     management:
       endpoints:
         web:
           exposure:
             include: health
     ```
  2. Update `GatewaySecurityConfig.java` to explicitly permit only `/actuator/health` and require admin authentication for any other administrative/actuator paths.
- **Regression Tests Needed**:
  Add an integration test verifying that `GET /actuator` and `GET /actuator/metrics` return 401 or 403 when unauthenticated.

---

### SEC-002: Missing HTTP Security Headers & Framing Protection (Clickjacking Risk)
- **Finding ID**: `SEC-002`
- **Severity**: **MEDIUM**
- **Affected Component**: `apps/desktop/vercel.json`, Vercel Deployment
- **Status**: **CONFIRMED** on live production
- **Evidence**:
  ```http
  GET / HTTP/1.1
  Host: sa-command.vercel.app

  HTTP/1.1 200 OK
  Access-Control-Allow-Origin: *
  Server: Vercel
  Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
  ```
- **Root Cause**:
  `apps/desktop/vercel.json` only specifies rewrite rules:
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
  No `headers` block is defined. As a result, the Vercel deployment omits:
  - `X-Frame-Options: DENY` or CSP `frame-ancestors 'none'`
  - `X-Content-Type-Options: nosniff`
  - `Content-Security-Policy`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  Additionally, Vercel emits `Access-Control-Allow-Origin: *` for static responses.
- **Impact**:
  - The application interface can be framed inside a malicious third-party site, creating a Clickjacking attack vector targeting authenticated owners.
  - Absence of a Content Security Policy allows execution of injected scripts if a DOM injection vector were ever introduced.
- **Remediation**:
  Configure security headers in `apps/desktop/vercel.json`:
  ```json
  {
    "rewrites": [
      { "source": "/(.*)", "destination": "/index.html" }
    ],
    "headers": [
      {
        "source": "/(.*)",
        "headers": [
          { "key": "X-Frame-Options", "value": "DENY" },
          { "key": "X-Content-Type-Options", "value": "nosniff" },
          { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
          { "key": "Permissions-Policy", "value": "camera=(), microphone=(), geolocation=(self)" },
          { "key": "Content-Security-Policy", "value": "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' https://sa-command-backend-production.up.railway.app https://sa-navigator-gateway-production.up.railway.app wss://sa-navigator-gateway-production.up.railway.app; img-src 'self' data: blob:; frame-ancestors 'none';" }
        ]
      }
    ]
  }
  ```
- **Regression Tests Needed**:
  Run HTTP header check assertion in end-to-end / web smoke tests.

---

### SEC-003: Fallback to Publicly Documented Default Finance PIN
- **Finding ID**: `SEC-003`
- **Severity**: **MEDIUM**
- **Affected Component**: Backend `application.yml`, `docs/production_readiness.md`
- **Status**: **CONFIRMED** in repository configuration
- **Evidence**:
  1. `apps/backend/src/main/resources/application.yml` (line 57):
     ```yaml
     app:
       finance:
         unlock-code-hash: ${FINANCE_UNLOCK_CODE_HASH:dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47}
     ```
  2. `docs/production_readiness.md` (lines 35 & 87):
     ```markdown
     | `FINANCE_UNLOCK_CODE_HASH` | Optional | `dd7a0bb1db3c404...` | SHA-256 hash of the 6-digit finance unlock code (`153011`). |
     ...
     Submits the 6-digit authorization code (`153011`).
     ```
- **Root Cause**:
  The default fallback SHA-256 hash `dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47` corresponds to the plain text PIN `153011`, which is explicitly published in the documentation.
- **Impact**:
  If a production administrator does not set the `FINANCE_UNLOCK_CODE_HASH` environment variable on Railway, the Finance Privacy Veil offers zero protection against any individual who knows or discovers the default PIN from the documentation or source repository.
- **Remediation**:
  1. In production, mandate that `FINANCE_UNLOCK_CODE_HASH` is set to a unique, secret SHA-256 hash generated by the business owner.
  2. In `ProductionConfiguration.java` (production validation bean), add an enforcement check: if `app.mode=production` and `FINANCE_UNLOCK_CODE_HASH` matches the known default hash `dd7a0bb1...`, fail application startup with an explicit configuration error.
- **Regression Tests Needed**:
  Add unit test ensuring `ProductionConfiguration` rejects default PIN hash in production mode.

---

### SEC-004: Unconfigured Proxy Forwarding Strategy in IP Throttling
- **Finding ID**: `SEC-004`
- **Severity**: **MEDIUM**
- **Affected Component**: `apps/backend/src/main/resources/application.yml`, `AuthController.java`, `FinanceAccessThrottle.java`
- **Status**: **CONFIRMED** in repository configuration
- **Evidence**:
  In `application.yml`:
  `server.forward-headers-strategy` is absent.
  In `AuthController.java`:
  ```java
  String ip = request.getRemoteAddr();
  throttle.check(input.email(), ip);
  ```
- **Root Cause**:
  Spring Boot behind reverse proxies (Railway's internal Hikari proxy router) reports the IP of the proxy container for `request.getRemoteAddr()` unless `server.forward-headers-strategy: framework` or `native` is configured.
- **Impact**:
  - An attacker making repeated failed login attempts from any location could trigger rate-limiting on the shared Railway proxy IP, causing a Denial of Service for legitimate users sharing that proxy IP.
  - Per-IP rate limiting cannot reliably distinguish between different external clients.
- **Remediation**:
  Add to `apps/backend/src/main/resources/application.yml`:
  ```yaml
  server:
    forward-headers-strategy: framework
  ```
  Ensure proxy headers (`X-Forwarded-For`) are trusted only from known upstream edge networks.
- **Regression Tests Needed**:
  Verify with MockMvc that `X-Forwarded-For` header is honored when rate limiting is triggered.

---

### SEC-005: Overly Permissive WebSocket STOMP Origin Patterns
- **Finding ID**: `SEC-005`
- **Severity**: **MEDIUM**
- **Affected Component**: `apps/navigator-gateway` (`RealtimeConfig.java`)
- **Status**: **CONFIRMED** in source code
- **Evidence**:
  In `apps/navigator-gateway/src/main/java/com/saproduction/navigator/RealtimeConfig.java` (lines 118-121):
  ```java
  registry
      .addEndpoint("/ws/navigator")
      .setAllowedOriginPatterns(
          "https://*",
          "http://localhost:*"
      );
  ```
- **Root Cause**:
  `setAllowedOriginPatterns("https://*")` accepts incoming WebSocket connections from any HTTPS domain on the web.
- **Impact**:
  Allows malicious web pages to open cross-site WebSocket handshakes. Although STOMP subscriptions require a valid ticket header (`X-Navigator-Ticket`), cross-site origin validation is an essential defense-in-depth barrier against Cross-Site WebSocket Hijacking (CSWSH) and connection exhaustion.
- **Remediation**:
  Restrict allowed origins to the authorized frontend domains:
  ```java
  registry
      .addEndpoint("/ws/navigator")
      .setAllowedOriginPatterns(
          "https://sa-command.vercel.app",
          "https://panel.saproduction.in",
          "http://localhost:*"
      );
  ```
  Or inject allowed origin patterns dynamically from `${navigator.allowed-origins}`.
- **Regression Tests Needed**:
  Unit test asserting that an unauthorized origin (e.g., `https://evil.com`) is rejected during the WebSocket handshake.

---

### SEC-006: Incomplete Repository `.gitignore` for `.env.local`
- **Finding ID**: `SEC-006`
- **Severity**: **LOW**
- **Affected Component**: `.gitignore`
- **Status**: **CONFIRMED** in working tree
- **Evidence**:
  `.gitignore` contains only `.env`. The file `.env.local` exists in the local workspace and is only ignored because of a local rule in `.git/info/exclude`.
- **Root Cause**:
  The tracked `.gitignore` lacks wildcards for environment configuration variants (`.env.*`, `.env.local`, `.env.*.local`).
- **Impact**:
  When a developer clones the repository on a new machine or a CI workflow creates a temporary `.env.local`, Git does not ignore it by default, creating a risk that developers might stage and commit credentials.
- **Remediation**:
  Update `.gitignore`:
  ```gitignore
  .env
  .env.*
  !.env.example
  ```
- **Regression Tests Needed**:
  Verify with `git check-ignore` that `.env.local` and `.env.production.local` are ignored.

---

### SEC-007: Known Vulnerabilities in Dev & Mobile Dependencies
- **Finding ID**: `SEC-007`
- **Severity**: **LOW**
- **Affected Component**: `apps/desktop/package.json`, `apps/navigator-mobile/package.json`
- **Status**: **CONFIRMED** via `npm audit`
- **Evidence**:
  - `apps/desktop`: 11 vulnerabilities (3 moderate, 6 high, 2 critical in `tinypool` and `braces` via `vitest` / `tailwindcss`).
  - `apps/navigator-mobile`: 48 vulnerabilities (in older Expo/Metro toolchain dependencies including `shell-quote`, `node-forge`, `image-size`).
- **Impact**:
  Because these vulnerabilities are in development and build-time tooling (not runtime production servers), the direct exploitability against deployed production services is minimal. However, they represent supply-chain hygiene debt.
- **Remediation**:
  Perform scheduled dependency upgrades for `vitest`, `tailwindcss`, and mobile Expo toolchains. Dependabot PRs are already open and should be reviewed and merged.
- **Regression Tests Needed**:
  Full CI test execution (`npm test`, `npm run build:web`) post-upgrade.

---

## 5. Scope & Assessment Boundaries

### Checks Performed
- [x] **Public Reconnaissance**: Active TLS, HTTP response headers, CORS preflight checks, static asset analysis on Vercel deployment (`https://sa-command.vercel.app/`).
- [x] **Source Map & Bundle Inspection**: Verification of production bundles (`index-bwvPsGLN.js`) and validation that `.map` source maps return 403 Forbidden.
- [x] **Actuator & Management Audit**: Testing `/actuator`, `/actuator/health`, `/actuator/metrics`, `/actuator/info` on both Railway backend and Navigator Gateway services.
- [x] **Public Route Access Controls**: Verification of `/api/v1/owner-setup`, `/api/v1/demo/reset`, `/api/v1/admin/seed/synthetic`, `/api/v1/integrations/whatsapp/webhook`, and `/api/v1/navigator/live` on live production endpoints.
- [x] **Authentication Architecture Review**: Analysis of BCrypt hashing, Bearer session generation, `api_sessions` token hashing (SHA-256), revocation on logout, and session lifecycle.
- [x] **Authorization Boundaries & IDOR**: Inspection of `SecurityConfig`, role mappings (`ROLE_OWNER`), and access controls across Employee, Production, Finance, Headquarters, and Navigator endpoints.
- [x] **Finance Privacy Veil Audit**: Verification of dual-layer defense (`FinanceAccessFilter`, `FinanceAccessThrottle`, constant-time hash checks).
- [x] **Navigator Location Privacy Audit**: Assessment of 6-digit pairing code entropy, 10-minute expiry, single-use redemption, bearer token hashing, duty session lifecycles, and automated 14-day location data retention jobs.
- [x] **EVE AI Safety & Guardrails**: Verification of `EveCommandGateway` bounded execution (max 10 actions), registered domain services, precondition validation, and human confirmation mechanisms.
- [x] **Code & Query Injection Analysis**: Inspection of SQL queries (`JdbcTemplate`, Hibernate) for parameterization and AST queries; verification of zero `dangerouslySetInnerHTML` / DOM injection in React components.
- [x] **Git Repository & Secret Scanning**: Inspection of `.gitignore`, `.github/workflows`, commit history, and release tools.
- [x] **Local Test Suite Execution**: Verification of desktop Vitest suite (134 tests passing, 0 failures).

### Checks NOT Performed (Strict Production Safety Preservation)
- [ ] **No Brute-Force Testing**: Did not launch automated password or PIN dictionary attacks against production authentication endpoints.
- [ ] **No Destructive Operations**: Did not execute database queries, table truncations, or state mutations on production PostgreSQL databases.
- [ ] **No Live Location Injection**: Did not submit synthetic GPS telemetry or activate simulated devices on production Navigator Gateway.
- [ ] **No Mass Data Export**: Did not dump or exfiltrate production employee records, financial ledgers, or customer data.
- [ ] **No Unauthorized Credential Testing**: Did not attempt to authenticate with real employee credentials.

---

## 6. Data Categories Potentially Exposed

| Data Category | Current Risk Level | Protection Controls in Place | Areas Requiring Hardening |
| :--- | :---: | :--- | :--- |
| **Financial Ledgers & Records** | **LOW-MEDIUM** | Server-side Privacy Veil, 403 when locked, rate-limited unlock | Rotate default PIN hash if unset in production (`SEC-003`) |
| **Employee GPS & Live Telemetry**| **LOW** | Device token auth, 10-minute pairing TTL, duty session lifecycle | Restrict WebSocket origin patterns (`SEC-005`) |
| **Employee PII & Bank Details** | **LOW** | Enforced Bearer session requirement, no unauthenticated access | None (access requires valid Owner session) |
| **Infrastructure / Runtime State**| **HIGH** | Backend actuator hardened (health only) | Close unauthenticated Actuator metrics on Gateway (`SEC-001`) |
| **Production Schedules & Assets** | **LOW** | Enforced Bearer session requirement, no public leak | None |

---

## 7. Recommended Remediation Plan

### Immediate Actions (Priority 1 — Within 24-48 Hours)
1. **Remediate SEC-001 (Gateway Actuator Exposure)**:
   - In `apps/navigator-gateway/src/main/resources/application.yml`, set `management.endpoints.web.exposure.include: health`.
   - In `GatewaySecurityConfig.java`, remove `anyRequest().permitAll()` and require admin credentials for any non-health administrative route.
   - Deploy update to Railway `sa-navigator-gateway-production`.

2. **Remediate SEC-002 (Vercel Security Headers & Clickjacking)**:
   - Add security `headers` to `apps/desktop/vercel.json` including `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and a scoped `Content-Security-Policy`.
   - Deploy update to Vercel `sa-command.vercel.app`.

3. **Verify SEC-003 (Production Finance PIN)**:
   - Check Railway environment variables for `sa-command-backend-production`: confirm `FINANCE_UNLOCK_CODE_HASH` is explicitly set to a custom SHA-256 hash. If unset, generate a fresh 6-digit PIN and configure its SHA-256 hash immediately.

### Short-Term Actions (Priority 2 — Within 1 Week)
4. **Remediate SEC-004 (Forwarded Headers Strategy)**:
   - Add `server.forward-headers-strategy: framework` to `apps/backend/src/main/resources/application.yml` so rate limiting accurately maps client IPs behind Railway Hikari edge.
5. **Remediate SEC-005 (WebSocket Origin Restriction)**:
   - Restrict STOMP allowed origins in `RealtimeConfig.java` to explicit trusted domains instead of `https://*`.
6. **Remediate SEC-006 (Update `.gitignore`)**:
   - Add `.env.*` to `.gitignore` to prevent future leaks of local environment files.

### Medium-Term Actions (Priority 3 — Next Sprint)
7. **Address SEC-007 (Dependency Updates)**:
   - Merge open Dependabot pull requests and update devDependencies across `apps/desktop` and `apps/navigator-mobile`.
8. **Extend CodeQL Workflow**:
   - Update `.github/workflows/codeql.yml` to trigger on `production` branch in addition to `main`.

---

## 8. Final Verdict

# `VULNERABILITIES FOUND — REMEDIATION REQUIRED`

### Assessment Statement
The core SA Command backend is well-engineered with robust domain-layer validation, parameterized SQL queries, BCrypt password hashing, stateless token management, and governed EVE AI execution boundaries. No unauthorized access to live financial records or employee PII was observed during testing.

However, the assessment identified **one High-severity vulnerability** (unauthenticated Spring Boot Actuator and JVM metrics exposure on the production Navigator Gateway) along with **several Medium-severity findings** (missing Vercel framing/clickjacking headers and CSP, a publicly documented default fallback finance PIN, and permissive WebSocket origin patterns). 

Remediation of findings **SEC-001**, **SEC-002**, and **SEC-003** should be executed promptly following the plan detailed above.
