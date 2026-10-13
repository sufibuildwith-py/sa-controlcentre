# SA Command — Production Security Remediation Plan (2026)

**Document Reference:** `SA-SEC-PLAN-2026-V1`  
**Governing Authority:** `PLAN.md` & `AGENTS.md`  
**Target Environment:** SA Command Production Ecosystem  
**Audit Source:** [`SA_COMMAND_SECURITY_AUDIT_2026.md`](file:///c:/Users/xtrar/Desktop/ERP/SA_COMMAND_SECURITY_AUDIT_2026.md)  
**Cost Constraint:** \$0.00 / Zero-Cost Hosting Preserved Across All Providers  
**Execution Status:** Awaiting Owner Review and Authorization  

---

## 1. Guiding Principles & Safety Invariants

1. **Strict Approval Gate:** No production mutations, code commits, deployments, or Git history rewrites will be initiated until the Owner explicitly reviews and authorizes this remediation plan.
2. **Zero Downtime:** All application and configuration updates will execute via rolling zero-downtime deployments on Vercel and Render.
3. **Zero Financial Cost:** All fixes operate strictly within current free-tier allocations:
   - Vercel (Hobby Free)
   - Render (Free Web Service + Free PostgreSQL 16)
   - Railway (Free Starter)
   - Google Drive (15 GB Personal Free)
   - GitHub Actions (2,000 Free CI/CD minutes/month)
4. **Defense-in-Depth & Fail Closed:** Every security boundary must default to denying access rather than falling back to permissive demo credentials or static PINs.
5. **No Regressions on Product Authority:** No modifications to UI shell, theme, TanStack Query server state, or business logic defined in `PLAN.md` and `AGENTS.md`.

---

## 2. Phased Remediation Roadmap

```
+---------------------------------------------------------------------------------+
|                               APPROVAL GATE                                     |
|               Owner reviews Audit Report & Remediation Plan                     |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| PHASE 1: Backend Hardening & Fail-Closed Defaults (Zero Downtime)               |
| - SEC-02: Remove default PIN '153011' hash & fail closed in FinanceAccessService |
| - SEC-03: Implement bounded cache & memory cap on LoginThrottle / FinanceThrottle|
| - SEC-08: Disable default simulator in Navigator Gateway application.yml        |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| PHASE 2: CI/CD Pipeline & Supply Chain Hardening                                |
| - SEC-05: Fix inline shell interpolation in db-renewal-reminder.yml             |
| - SEC-07: Extend CodeQL triggers to cover 'production' branch                   |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| PHASE 3: Frontend Defense & Transport Security (Vercel)                         |
| - SEC-04: Pin CSP connect-src to exact host; remove wildcard & stale origin     |
| - Add Strict-Transport-Security (HSTS) headers to vercel.json                   |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| PHASE 4: Git History Sanitization (Coordinated Maintenance)                     |
| - SEC-01: Scrub revoked Render API key from Git history using git-filter-repo   |
| - Verify clean Git object tree & synchronize both remotes                       |
+---------------------------------------------------------------------------------+
```

---

## 3. Phase Specifications & Implementation Details

### Phase 1: Backend Security Hardening & Fail-Closed Defaults

#### Objectives
1. Eliminate the hardcoded Finance Privacy Veil fallback PIN `153011`.
2. Ensure `FinanceAccessService` strictly fails closed if `FINANCE_UNLOCK_CODE_HASH` is absent.
3. Prevent in-memory throttle maps from growing unbounded on 512MB RAM instances.
4. Set Navigator Gateway simulator default to disabled (`false`).

#### Target Files & Code Changes

1. **[`apps/backend/src/main/resources/application.yml`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/resources/application.yml#L64)**:
   - *Current:*
     ```yaml
     finance:
       unlock-code-hash: ${FINANCE_UNLOCK_CODE_HASH:dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47}
     ```
   - *Target:*
     ```yaml
     finance:
       unlock-code-hash: ${FINANCE_UNLOCK_CODE_HASH:}
     ```

2. **[`apps/backend/src/main/java/com/saproduction/command/finance/access/FinanceAccessService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/finance/access/FinanceAccessService.java#L43)**:
   - *Target:* If `unlockCodeHash.isBlank()`, reject unlock attempts with `ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FINANCE_UNCONFIGURED", "Finance unlock code has not been configured by the system administrator.")`.

3. **[`apps/backend/src/main/java/com/saproduction/command/auth/LoginThrottle.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/auth/LoginThrottle.java)** & **[`FinanceAccessThrottle.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/finance/access/FinanceAccessThrottle.java)**:
   - *Target:* Cap map capacity to 2,000 entries and evict entries older than window TTL upon insertions to eliminate memory leakage.

4. **[`apps/navigator-gateway/src/main/resources/application.yml`](file:///c:/Users/xtrar/Desktop/ERP/apps/navigator-gateway/src/main/resources/application.yml#L35)**:
   - *Current:* `simulator-enabled: ${NAVIGATOR_SIMULATOR_ENABLED:true}`
   - *Target:* `simulator-enabled: ${NAVIGATOR_SIMULATOR_ENABLED:false}`

#### Verification Procedures
- Compile and run backend test suites:
  ```bash
  mvn -f apps/backend/pom.xml test
  mvn -f apps/navigator-gateway/pom.xml test
  ```
- Verify unlock fails closed without environment variable.

#### Rollback Plan
- Revert commit on branch `production`. Git revert creates a clean append-only commit.

---

### Phase 2: CI/CD Pipeline & Supply Chain Hardening

#### Objectives
1. Eliminate shell expression injection vulnerability in GitHub Actions.
2. Enable continuous CodeQL static vulnerability scanning on branch `production`.

#### Target Files & Code Changes

1. **[`.github/workflows/db-renewal-reminder.yml`](file:///c:/Users/xtrar/Desktop/ERP/.github/workflows/db-renewal-reminder.yml#L41-L46)**:
   - *Target:*
     ```yaml
     - name: Run Renewal Checker
       id: checker
       env:
         RENDER_API_KEY: ${{ secrets.RENDER_API_KEY }}
         RENDER_DATABASE_ID: ${{ secrets.RENDER_DATABASE_ID || 'dpg-db70sbaj9qps73ar0650-a' }}
         GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
         SIMULATE_DAY: ${{ github.event.inputs.simulate_day }}
       run: |
         if [ -n "$SIMULATE_DAY" ]; then
           python scripts/renewal_reminder.py --simulate-day "$SIMULATE_DAY"
         else
           python scripts/renewal_reminder.py
         fi
     ```

2. **[`.github/workflows/codeql.yml`](file:///c:/Users/xtrar/Desktop/ERP/.github/workflows/codeql.yml#L4-L6)**:
   - *Target:*
     ```yaml
     on:
       push:
         branches: [main, production]
       pull_request:
         branches: [main, production]
     ```

#### Verification Procedures
- Trigger `db-renewal-reminder.yml` via GitHub Actions `workflow_dispatch` with `--simulate-day 25` and confirm safe execution.

---

### Phase 3: Frontend Browser Defense & Transport Security

#### Objectives
1. Pin Content Security Policy `connect-src` to the exact backend host.
2. Eliminate broad wildcard `https://*.onrender.com` and stale Railway backend origin.
3. Add HTTP Strict Transport Security (HSTS) headers to Vercel responses.

#### Target Files & Code Changes

1. **[`apps/desktop/vercel.json`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/vercel.json#L30)**:
   - *Target CSP `connect-src`:*
     ```
     connect-src 'self' https://sa-command-backend.onrender.com https://sa-navigator-gateway-production.up.railway.app wss://sa-navigator-gateway-production.up.railway.app https://api.saproduction.in https://tiles.openfreemap.org;
     ```
   - *Add Header:*
     ```json
     {
       "key": "Strict-Transport-Security",
       "value": "max-age=63072000; includeSubDomains; preload"
     }
     ```

#### Verification Procedures
- Run desktop build and format checks:
  ```bash
  npm run build --prefix apps/desktop
  npm run format:check --prefix apps/desktop
  ```
- Deploy to Vercel and test live HTTP response headers using `curl -I https://sa-command.vercel.app`.

---

### Phase 4: Git History Sanitization (Low-Impact Maintenance Window)

#### Objectives
1. Permanently remove the revoked Render API key (`rnd_fIlzHhA4w17bxCF2oRQIL5QPMlbJ`) from Git object history.
2. Ensure neither `delusional-deployments/sa-panel` nor `sufibuildwith-py/sa-controlcentre` contains the cleartext string in commit history.

#### Execution Procedure
1. Create a local backup clone of the entire repository before history rewrite.
2. Use `git-filter-repo` to replace the literal key across all commits with a dummy string:
   ```bash
   git-filter-repo --replace-text <(echo "rnd_fIlzHhA4w17bxCF2oRQIL5QPMlbJ==>REDACTED_HISTORICAL_RENDER_KEY")
   ```
3. Re-run `python scratch/scan_git_secrets.py` to confirm zero credential findings.
4. Force push sanitized branches and tags to both remotes:
   ```bash
   git push target-prod production --force
   git push origin production --force
   ```

---

## 4. Verification & Testing Matrix

| Finding ID | Remediation Phase | Verification Command / Metric | Expected Outcome |
|---|---|---|---|
| **SEC-01** | Phase 4 | `python scratch/scan_git_secrets.py` | 0 credential matches found across entire Git history |
| **SEC-02** | Phase 1 | `curl -s -X POST https://sa-command-backend.onrender.com/api/v1/finance-access/unlock -d '{"code":"153011"}'` | `HTTP 503` (if unconfigured) or `HTTP 400 INVALID_PIN` (with custom PIN) |
| **SEC-03** | Phase 1 | Unit tests in `LoginThrottleTest.java` | 2,001st entry evicts oldest; memory usage capped under 2 MB |
| **SEC-04** | Phase 3 | `curl -s -I https://sa-command.vercel.app \| grep -i content-security-policy` | `connect-src` contains exact host `sa-command-backend.onrender.com`; no `*.onrender.com` wildcard |
| **SEC-05** | Phase 2 | GitHub Actions run on `db-renewal-reminder.yml` | Step executes using `$SIMULATE_DAY` environment variable without shell warnings |
| **SEC-06** | Phase 1 | Direct curl to gateway endpoints | Path matchers block unauthenticated requests before controller execution |
| **SEC-07** | Phase 2 | GitHub Actions CodeQL tab | Workflows trigger and report on `production` branch pushes |
| **SEC-08** | Phase 1 | `curl -s -X POST https://sa-navigator-gateway-production.up.railway.app/api/v1/admin/simulator/start` | Returns `SIMULATOR_DISABLED` (404) |

---

## 5. Owner Decision & Approval Request

To maintain operational integrity and avoid any unintended service disruption, **no remediations will be applied until you approve**.

Please review the proposed plan and indicate whether you approve proceeding with:
- **Option A (Full Remediation):** Execute Phases 1, 2, 3, and 4 in sequence.
- **Option B (Application & CI First):** Execute Phases 1, 2, and 3 immediately; defer Phase 4 (Git history force-push) to a separate maintenance session.
- **Option C (Custom Scope):** Provide specific adjustments or instructions.
