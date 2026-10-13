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

### Independent Off-Site Storage Options

| Storage Provider | Monthly Cost | Free Storage Limit | Egress Fees | Protocol | Recommendation |
|---|---|---|---|---|---|
| **Cloudflare R2** | **₹0 ($0.00)** | **10 GB forever** | **₹0 ($0.00)** | S3-Compatible | **Recommended** |
| **Backblaze B2** | **₹0 ($0.00)** | **10 GB forever** | 1 GB / day free | S3-Compatible | Alternative |
| **AWS S3** | **₹0 ($0.00)** | 5 GB (12 months) | Paid after 100 GB | S3 Standard | 12-month limit |

### Retention Policy
- **Daily Retention:** Minimum **7 recent daily backups** retained in off-site storage prefix `backups/sa-command-db/YYYY-MM-DD/`.
- **Permanent Baseline:** Pre-migration baseline (`sa_command_baseline_*.dump.enc`) is protected by immutable retention rules and never pruned.
- **Automated Pruning:** Objects older than 7 days (excluding baseline archives) are pruned automatically.

### Required GitHub Secrets

To activate automated off-site archival, configure these secrets under **Settings > Secrets and variables > Actions**:

| Secret Name | Description | Example / Recommended Value |
|---|---|---|
| `RENDER_API_KEY` | Render API Bearer token | `rnd_fIlzHhA4w17bxCF2oRQIL5QPMlbJ` |
| `RENDER_DATABASE_ID` | Render PostgreSQL Resource ID | `dpg-db70sbaj9qps73ar0650-a` |
| `BACKUP_ENCRYPTION_KEY` | High-entropy AES-256 passphrase | User-generated 32+ character random secret |
| `BACKUP_S3_BUCKET` | Destination S3/R2 Bucket name | `sa-command-backups` |
| `BACKUP_S3_ENDPOINT` | S3-compatible API endpoint | `https://<account-id>.r2.cloudflarestorage.com` |
| `BACKUP_S3_ACCESS_KEY_ID` | Storage Access Key ID | Generated from Cloudflare R2 / AWS IAM |
| `BACKUP_S3_SECRET_ACCESS_KEY` | Storage Secret Access Key | Generated from Cloudflare R2 / AWS IAM |

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

### Standard Maintenance-Window Cutover Procedure (Option A)

1. **Pre-Flight Audit:**
   ```bash
   python scripts/renew_database.py --dry-run
   ```
2. **Execute Fresh Full Backup:**
   ```bash
   python scripts/backup_database.py --baseline
   ```
   Confirm output archive: `backups/archive/sa_command_baseline_*.dump.enc` exists, is non-empty, and decryption round-trip succeeds.
3. **Delete Expiring Instance (Maintenance Window Begins):**
   ```bash
   # Via Render Dashboard or API
   curl -X DELETE "https://api.render.com/v1/postgres/dpg-db70sbaj9qps73ar0650-a" \
     -H "Authorization: Bearer $RENDER_API_KEY"
   ```
4. **Provision Replacement Instance:**
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
   Capture the new database ID (e.g. `dpg-new...`).
5. **Restore Schemas and Data:**
   Wait ~60 seconds until database status is `available`.
   Retrieve connection info:
   ```bash
   curl "https://api.render.com/v1/postgres/<new_id>/connection-info" \
     -H "Authorization: Bearer $RENDER_API_KEY"
   ```
   Decrypt and restore into the new database:
   ```bash
   python -c "
   from scripts.backup_database import decrypt_dump
   decrypt_dump('backups/archive/sa_command_baseline_latest.dump.enc', 'restore.dump', '$BACKUP_ENCRYPTION_KEY')
   "
   pg_restore --clean --if-exists --no-owner --no-acl -d "<new_external_url>" restore.dump
   rm restore.dump
   ```
6. **Verify Row Counts & Flyway Migrations:**
   Run dual database audit or row-count check:
   - `public.flyway_schema_history` >= 33
   - `navigator.flyway_schema_history` >= 3
   - `public.users` >= 1
   - `public.employees` >= 1
   - `public.audit_logs` >= 123
7. **Update Backend Database Credentials:**
   Update Render Web Service `srv-db711nnf3r2c73crdh4g` environment variables via Render Dashboard or API:
   - `DATABASE_URL`: `jdbc:postgresql://<new_host>:5432/sa_command_prod`
   - `DATABASE_USERNAME`: `sa_command_user`
   - `DATABASE_PASSWORD`: `<new_password>`
8. **Verify Backend Health:**
   ```bash
   curl -i https://sa-command-backend.onrender.com/actuator/health
   ```
   Confirm HTTP 200 OK `{"status":"UP"}`. Test Owner login and Navigator ticket.

---

## 6. Emergency Disaster Recovery (Worst-Case Scenario)

If Render PostgreSQL disappears unexpectedly or is dropped prematurely:

1. **Locate Latest Verified Encrypted Backup:**
   From S3/R2 bucket (`s3://sa-command-backups/backups/sa-command-db/YYYY-MM-DD/`) or local archive (`backups/archive/`).
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
