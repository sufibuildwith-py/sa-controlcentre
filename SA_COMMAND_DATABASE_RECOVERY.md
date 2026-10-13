# SA Command — PostgreSQL Automated Backup & Renewal Runbook

**Document Authority:** Production Operations & Disaster Recovery  
**Target Database:** Render Free PostgreSQL 16 (`sa-command-db`)  
**Database ID:** `dpg-db70sbaj9qps73ar0650-a`  
**Creation Timestamp:** `2026-10-13T10:55:41Z`  
**Current Expiration Timestamp:** `2026-11-12T10:55:41Z` (30-day lifecycle)  
**Next Renewal Window (Day 25):** `2026-11-07`  

---

## 1. Executive Summary & Lifecycle Context

Render Free PostgreSQL instances are strictly bounded to a **30-day lifecycle**. At the end of 30 days, Render deletes the free database instance. 

To maintain uninterrupted **₹0 baseline hosting cost**, this runbook establishes:
1. **Automated Daily Backups:** Exported via `pg_dump` (custom format), verified via TOC manifest, encrypted client-side with AES-256-CBC, and uploaded off-site.
2. **Automated Restore Verification:** Tested automatically inside an isolated Docker PostgreSQL 16 container.
3. **Tiered Expiration Reminders:** Triggered on Days 25, 27, and 29 based on Render's actual API timestamps.
4. **Assisted Replacement Procedure:** A controlled, zero-data-loss cutover runbook designed specifically around Render's single-free-database-per-workspace quota.

---

## 2. Infrastructure & Quota Finding

During live API probing, Render returned:
```json
Status: 400
{
  "message": "cannot have more than one active free tier database"
}
```

> [!IMPORTANT]
> **Render Platform Restriction:**  
> A Render workspace allows at most **one active Free PostgreSQL database** at any given moment. You cannot provision a replacement free database within the same workspace while the old one is still running.

This requires one of two renewal architectures:
- **Strategy A (Standard Single Workspace):** Controlled ~2-minute maintenance-window cutover (verified backup -> delete old DB -> provision new DB -> restore data -> update backend env vars).
- **Strategy B (Zero-Downtime Multi-Workspace):** Provision the replacement database in a secondary free Render workspace/account in advance, restore data, cutover backend connection, then retire the old database.

---

## 3. Automated Daily Backup Pipeline

### Workflow: `.github/workflows/db-backup.yml`
- **Schedule:** Daily at `02:00 UTC` (`07:30 IST`) via GitHub Actions cron.
- **Manual Trigger:** `workflow_dispatch` with toggle for isolated restore verification.
- **Concurrency Control:** `db-backup-production` with `cancel-in-progress: false` to prevent overlapping runs.
- **Export Format:** `pg_dump --format=custom --no-owner --no-acl --verbose` (captures `public` and `navigator` schemas, tables, sequences, constraints, and Flyway history).
- **Encryption:** OpenSSL / Python AES-256-CBC with PBKDF2 (100,000 iterations). Plaintext dumps are securely shredded from runner disk immediately after encryption.

### Independent Off-Site Storage: Google Drive (rclone)

| Component | Specification | Details |
|---|---|---|
| **Storage Destination** | **Google Drive** | Primary dedicated folder: `SA_COMMAND_DB_BACKUPS` |
| **Transport Utility** | **rclone** | Native Go binary for authenticated, encrypted cloud transfers |
| **Billing Requirement** | **₹0 ($0.00)** | Zero-cost baseline; no payment method or credit card required |
| **Authentication** | Dedicated OAuth Client | Installed Desktop OAuth app in Google Cloud (`SA Command Backups`) |
| **Directory Layout** | Dedicated subfolders | `daily/`, `pre-migration/`, `manifests/` |

### Retention Policy
- **Daily Retention:** Minimum **7 recent daily backups** retained in Google Drive under `SA_COMMAND_DB_BACKUPS/daily/`.
- **Permanent Baseline:** Pre-migration baseline (`sa_command_baseline_*.dump.enc`) is stored in `SA_COMMAND_DB_BACKUPS/pre-migration/` and never pruned.
- **Automated Pruning:** Older daily backups beyond the 7 most recent are safely pruned only after successful upload verification.
- **Fail-Safe Guarantee:** If an upload fails, cleanup is aborted. The last known-good backup is never deleted.

### Required GitHub Secrets

To activate automated off-site archival, configure these secrets under **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Status / Source |
|---|---|---|
| `RENDER_API_KEY` | Render API Bearer token | Configured in GitHub Secrets |
| `RENDER_DATABASE_ID` | Render PostgreSQL Resource ID | Configured (`dpg-db70sbaj9qps73ar0650-a`) |
| `BACKUP_ENCRYPTION_KEY` | High-entropy AES-256 passphrase | Configured in GitHub Secrets |
| `GDRIVE_RCLONE_CONFIG` | Complete rclone config file contents | Generated via `scripts/setup_gdrive_auth.ps1` |

---

## 4. Automated Tiered Renewal Reminders

### Workflow: `.github/workflows/db-renewal-reminder.yml`
- **Schedule:** Daily at `04:00 UTC` (`09:30 IST`).
- **Data Source:** Dynamic query to Render API (`GET /postgres/{db_id}`), inspecting real `createdAt` and `expiresAt` timestamps. Never uses hardcoded calendar days.

### Escalation Matrix

| Day in Lifecycle | Days Remaining | Tier | Level | Action Taken |
|---|---|---|---|---|
| **Days 1–24** | > 5.0 days | Tier 0 | `NORMAL` | Normal operation. No alert dispatched. |
| **Day 25** | <= 5.0 days | Tier 1 | `INITIAL_REMINDER` | Opens GitHub Issue: *Replacement window open*. Mentions owner. |
| **Day 27** | <= 3.0 days | Tier 2 | `ESCALATION` | Comments on existing issue: *Action required in 3 days*. |
| **Day 29** | <= 1.0 day | Tier 3 | `URGENT_ESCALATION` | High-priority alert: *Database expires within 24–48 hours!* |
| **Day 30+** | <= 0.0 days | Tier 4 | `EXPIRED` | Critical alert: *Database expiration imminent or past!* |

### Anti-Duplicate Guarantee
The reminder engine records notification state (`.db_renewal_state.json`) and inspects existing open GitHub Issues. It updates the existing issue comment thread rather than creating duplicate issues for the same tier.

### Verified Simulation Test Traces

The renewal scheduler was tested against simulated dates for days 24, 25, 27, 29, and 30:

```text
Day 24 (Age: 24.0d, Remaining: 6.0d): [NORMAL] (Tier 0) -> Within normal lifespan.
Day 25 (Age: 25.0d, Remaining: 5.0d): [INITIAL_REMINDER] (Tier 1) -> Replacement window open.
Day 27 (Age: 27.0d, Remaining: 3.0d): [ESCALATION] (Tier 2) -> Renewal incomplete with ~3.0d remaining.
Day 29 (Age: 29.0d, Remaining: 1.0d): [URGENT_ESCALATION] (Tier 3) -> Expires within 24-48 hours!
Day 30 (Age: 30.0d, Remaining: 0.0d): [EXPIRED] (Tier 4) -> Expiration reached!

Deduplication Verification:
Call 1 (Fresh Day 25): Dispatched: True (State recorded at Tier 1)
Call 2 (Duplicate Day 25): Suppressing duplicate: Dispatched: False
```

---

## 5. Assisted Database Replacement Runbook

When Day 25 reminder fires, execute the replacement using [`scripts/renew_database.py`](file:///c:/Users/xtrar/Desktop/ERP/scripts/renew_database.py).

> [!CAUTION]
> **Explicit Operator Confirmation Required:**  
> No automatic deletion, production database replacement, or credential cutover will execute without explicit operator authorization.

### Expected Maintenance Window & Downtime Specification

Due to Render's policy permitting **at most one active Free PostgreSQL database per workspace**, Option A involves a brief planned maintenance window:

| Phase | Duration | Service State | Activity |
|---|---|---|---|
| **Phase 1: Write Freeze** | 15 seconds | Read-only / Veil | Stop inbound mutations; flush active connections; take final pre-cutover sync dump. |
| **Phase 2: Retirement** | 5 seconds | Offline | Delete expiring database via Render API (`DELETE /postgres/{id}`). |
| **Phase 3: Provisioning** | 60–90 seconds | Offline | Render spins up fresh PostgreSQL 16 instance until status is `available`. |
| **Phase 4: Restoration** | 20–30 seconds | Offline | Decrypt dump and restore all 108 tables across `public` and `navigator` schemas. |
| **Phase 5: Cutover & Restart** | 30–45 seconds | Recovering | Update backend `DATABASE_URL` environment variables; backend reboots and runs health check. |
| **Total Expected Downtime** | **~2 to 3 minutes** | Planned Window | End-to-end cutover completed with 0 data loss. |

---

### Step-by-Step Maintenance-Window Cutover Procedure (Option A)

#### Step 1: Pre-Flight Audit & Quota Check
```bash
python scripts/renew_database.py --dry-run
```
Verifies current database ID, creation/expiration dates, and ensures all tools (`pg_dump`, `openssl`, Python dependencies) are ready.

#### Step 2: Inbound Write Freeze
To guarantee zero transaction loss between the final backup and cutover:
1. Temporarily pause or restrict inbound writes by scaling backend to maintenance mode or notifying active users.
2. In the Render Dashboard (`sa-command-backend`), set environment variable:
   `MAINTENANCE_MODE=true` (or suspend inbound write requests).
3. Confirm no active write transactions are pending in PostgreSQL:
   ```sql
   SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND query NOT ILIKE '%pg_stat_activity%';
   ```

#### Step 3: Final Pre-Cutover Backup & Verification
```bash
python scripts/backup_database.py --baseline
```
- Exports full database (`public` + `navigator`, Flyway, records).
- Generates encrypted archive `sa_command_baseline_<timestamp>.dump.enc`.
- Verifies SHA-256 decryption round-trip.
- Uploads to Cloudflare R2 off-site bucket.

#### Step 4: Delete Expiring Database (Maintenance Window Begins)
```bash
curl -X DELETE "https://api.render.com/v1/postgres/dpg-db70sbaj9qps73ar0650-a" \
  -H "Authorization: Bearer $RENDER_API_KEY"
```

#### Step 5: Provision Replacement Instance
```bash
curl -X POST "https://api.render.com/v1/postgres" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sa-command-db",
    "plan": "free",
    "version": "16",
    "databaseName": "sa_command_prod",
    "databaseUser": "sa_command_user",
    "region": "oregon",
    "ownerId": "tea-db6vgcd9fdbs73eh5a2g",
    "ipAllowList": [{"cidrBlock": "0.0.0.0/0", "description": "Everywhere"}]
  }'
```
Record the new database ID (e.g. `dpg-new...`). Poll `GET /postgres/{new_id}` until `status` is `"available"` (~60s).

#### Step 6: Decrypt and Restore Data
Retrieve external connection URL:
```bash
curl "https://api.render.com/v1/postgres/<new_id>/connection-info" \
  -H "Authorization: Bearer $RENDER_API_KEY"
```
Execute restore using `verify_real_restore.py` or `pg_restore`:
```bash
python scratch/verify_real_restore.py
```
Validates:
- All 108 tables restored (98 `public`, 10 `navigator`)
- `public.flyway_schema_history` == 33 (latest `033`)
- `navigator.flyway_schema_history` == 3 (latest `002`)
- Critical record row counts match exactly.

#### Step 7: Update Backend Connection Strings
In Render Web Service `sa-command-backend` (`srv-db711nnf3r2c73crdh4g`), update:
- `DATABASE_URL`: `jdbc:postgresql://<new_host>:5432/sa_command_prod`
- `DATABASE_USERNAME`: `sa_command_user`
- `DATABASE_PASSWORD`: `<new_password>`
- Remove `MAINTENANCE_MODE=true`

#### Step 8: Post-Cutover Verification
```bash
curl -i https://sa-command-backend.onrender.com/actuator/health
```
Verify `{"status":"UP"}`. Test login, Navigator ticket, and live employee locations.

---

### Rollback & Emergency Contingency Procedures

If any failure occurs during the renewal window:

#### Scenario 1: Provisioning Fails (Render API Error / Quota Exhaustion)
- **Immediate Action:** The final backup archive (`sa_command_baseline_*.dump.enc`) is safely stored locally and in Cloudflare R2.
- **Rollback Option 1 (Railway Hot-Spare):** Railway PostgreSQL is currently online and fully functional as a hot backup. Re-point Render backend `DATABASE_URL` directly to Railway PostgreSQL connection string (`postgresql://postgres:<password>@<railway-host>:5432/railway`). Restores service in < 60 seconds.
- **Rollback Option 2 (Secondary Render Workspace):** Provision a free database in a secondary Render account/workspace and restore the verified dump.

#### Scenario 2: Data Restore Errors or Corrupted Dump
- **Immediate Action:** Do not point backend to partially restored database.
- **Resolution:** Decrypt and restore from the verified daily backup archive on Cloudflare R2 (`backups/sa-command-db/YYYY-MM-DD/`) or local pre-migration baseline.
- **Integrity Validation:** Run `python scratch/verify_real_restore.py` against the target database before directing traffic. All 108 tables must verify 100% PASS.

---

## 6. Emergency Disaster Recovery (Worst-Case Scenario)

If Render PostgreSQL disappears unexpectedly or is dropped prematurely:

1. **Locate Latest Verified Encrypted Backup:**
   From Google Drive:
   ```bash
   rclone copyto gdrive:SA_COMMAND_DB_BACKUPS/daily/<latest_file>.dump.enc ./latest_backup.dump.enc
   ```
   Or from local archive (`backups/archive/`).
2. **Provision Fresh Database:**
   Follow Step 4 above to provision a new Render Free PostgreSQL 16 instance.
3. **Decrypt and Restore:**
   ```bash
   openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
     -in <backup_file>.dump.enc \
     -out restore.dump \
     -pass pass:$BACKUP_ENCRYPTION_KEY
   
   pg_restore --no-owner --no-acl -d "<new_external_connection_string>" restore.dump
   rm restore.dump
   ```
4. **Reconnect Backend:**
   Point `sa-command-backend` to the new database in Render service settings.
5. **Verify Application:**
   Run verification suite:
   ```bash
   python scratch/full_production_verification.py
   ```
   All 24 tests must return `PASS`.
