# SA Command — Google Drive Backup Integration Report

**Target Environment:** SA Command Production  
**Document Authority:** Disaster Recovery & Off-Site Data Protection  
**Date:** October 13, 2026  
**Status:** Implementation Complete — Awaiting Owner Browser OAuth Approval  

---

## 1. Existing Implementation Findings

- **Baseline Data Protection:** SA Command's PostgreSQL backup and renewal foundation was previously committed to `production` (`22334ee`, `facfa43`).
- **Target Database Verification:** Render Free PostgreSQL 16 (`sa-command-db`, ID: `dpg-db70sbaj9qps73ar0650-a`, Oregon region) hosts 108 tables (98 `public`, 10 `navigator`), 33 public Flyway migrations, and 3 navigator Flyway migrations.
- **R2 Deprecation Rationale:** Cloudflare R2 enforces credit-card/payment method activation even for free-tier utilization. To maintain a strict **₹0 baseline hosting cost without billing accounts**, Google Drive has been selected and integrated as the primary off-site backup destination.
- **Credential Scrubbing Audit:** All hardcoded API keys and passphrases were completely purged from the codebase. The previously exposed Render API key was revoked by the owner and rotated in GitHub Repository Secrets.

---

## 2. Google Drive Integration Architecture

```
Render PostgreSQL 16 (sa-command-db)
       │
       ▼  (pg_dump -Fc custom format / Python extractor fallback)
Local / CI Runner Temp Storage
       │
       ▼  (AES-256-CBC with PBKDF2 100k iter + SHA-256 integrity digest)
Encrypted Archive (.dump.enc) + Metadata Manifest (.meta.json)
       │
       ▼  (rclone authenticated via Desktop OAuth Client)
Google Drive (`SA_COMMAND_DB_BACKUPS`)
       ├── daily/             (7-day rolling window of verified encrypted backups)
       ├── pre-migration/     (Permanent pre-migration baseline archives)
       └── manifests/         (Matching JSON metadata manifests with checksums)
```

- **Transport Tool:** `rclone` (native Go binary, static linking, zero Python S3 dependencies).
- **Privacy & Security Boundaries:** The backup folder `SA_COMMAND_DB_BACKUPS` remains strictly private. No public web links or anonymous sharing are permitted.
- **Restricted Scope & Isolation:** When `rclone` is configured, it operates exclusively on the designated backup folder hierarchy (`daily/`, `pre-migration/`, `manifests/`).

---

## 3. OAuth & rclone Authentication Status

### OAuth Client Configuration
- **Google Cloud Project:** `SA Command Backups`
- **Application Name:** `SA Command rclone`
- **Client Type:** Desktop App (Installed Application)
- **API Enabled:** Google Drive API

### Testing vs Production Lifecycle Investigation

> [!WARNING]
> **Critical Google OAuth Policy on "Testing" Status:**
> 1. **User Whitelist:** When an OAuth consent screen is set to **Testing**, only Google accounts explicitly listed under **OAuth consent screen > Test users** can authenticate.
> 2. **7-Day Refresh Token Expiry:** Google Cloud strictly limits refresh token lifespans to **7 days** for external OAuth apps in "Testing" status.
> 3. **Permanent Resolution for Unattended CI/CD Backups:**
>    - In Google Cloud Console -> **APIs & Services > OAuth consent screen**, click **"Publish App"** to transition Publishing Status from *Testing* to *In production*.
>    - Verification by Google is **NOT** required for internal/personal account access.
>    - During initial browser consent, Google will display an unverified app banner. Clicking **Advanced > Go to SA Command Backups (unsafe)** grants permanent consent.
>    - Once published, refresh tokens remain valid indefinitely without 7-day expiration.

---

## 4. Required GitHub Secrets (Names Only)

To operate the automated backup and renewal system in GitHub Actions, the following secrets are configured under **Settings > Secrets and variables > Actions**:

| Secret Name | Purpose | Current Status |
|---|---|---|
| `RENDER_API_KEY` | Render API Bearer token for database metadata and renewal | **Configured** |
| `RENDER_DATABASE_ID` | Render PostgreSQL Resource ID (`dpg-...`) | **Configured / Defaulted** |
| `BACKUP_ENCRYPTION_KEY` | High-entropy AES-256 client-side encryption passphrase | **Configured** |
| `GDRIVE_RCLONE_CONFIG` | Complete authenticated `rclone.conf` contents | **Pending Owner Browser Auth** |

*(Obsolete secrets `BACKUP_S3_*` have been removed from the workflow).*

---

## 5. Backup Schedule & Retention Policy

- **Schedule:** Automated daily execution at `02:00 UTC` (`07:30 IST`) via `.github/workflows/db-backup.yml`. Manual on-demand execution available via `workflow_dispatch`.
- **Retention Rules:**
  - `SA_COMMAND_DB_BACKUPS/daily/`: Minimum **7 verified daily backups** retained.
  - `SA_COMMAND_DB_BACKUPS/pre-migration/`: Permanent baseline backups (`sa_command_baseline_*.dump.enc`) are **never pruned**.
  - `SA_COMMAND_DB_BACKUPS/manifests/`: Matching manifests retained for each active archive.
- **Conservative Deletion Safety Guarantee:**
  - Pruning only triggers after successful upload and byte-parity verification of the new backup.
  - If an upload or verification fails, **zero files are deleted**.
  - The last known-good backup is strictly preserved under all circumstances.

---

## 6. Actual Backup & Restore Test Results

### 1. Isolated PostgreSQL 16 Restore Test
- **Archive:** `sa_command_baseline_20261013_121819.dump.enc`
- **Result:** **100.0% PASS**
- **Table Count Verification:** 108 tables restored (98 `public`, 10 `navigator`).
- **Flyway History:**
  - `public.flyway_schema_history`: 33 migrations (latest: `033`) — verified.
  - `navigator.flyway_schema_history`: 3 migrations (latest: `002`) — verified.
- **Critical Record Row Parity:**
  - `public.users`: 1 row (PASS)
  - `public.employees`: 1 row (PASS)
  - `public.audit_logs`: 152 rows (PASS)
  - `public.api_sessions`: 47 rows (PASS)
  - `public.finance_accounts`: 2 rows (PASS)
  - `public.finance_account_positions`: 2 rows (PASS)
  - `public.finance_expense_categories`: 12 rows (PASS)
  - `public.eve_signals`: 19 rows (PASS)
  - `navigator.navigator_devices`: 1 row (PASS)
  - `navigator.navigator_location_sessions`: 8 rows (PASS)
  - `navigator.navigator_location_points`: 18 rows (PASS)
- **Live Database Integrity:** Render production database was not restarted or modified during verification.

---

## 7. Renewal Notification Status

- **Workflow:** `.github/workflows/db-renewal-reminder.yml` scheduled daily at `04:00 UTC` (`09:30 IST`).
- **Delivery Guarantee:** Engine creates/updates GitHub Issues mentioning `@sufibuildwith-py`. GitHub forwards notifications directly to the owner's primary email and mobile application.
- **Escalation Schedule:** Day 25 (Initial reminder), Day 27 (Escalation), Day 29 (Urgent escalation).
- **Anti-Duplication:** Verified via `.db_renewal_state.json`.

---

## 8. Security Boundaries & Invariants

- **No Public Sharing:** All Google Drive backups and folders remain private.
- **Zero Secrets in Git:** OAuth refresh tokens, client secrets, and `rclone.conf` are strictly excluded from git.
- **Safe Tooling:** `rclone` binary v1.68.2 installed locally in `c:\Users\xtrar\tools\rclone\` and added to user PATH.
- **Production Uptime:** Web frontend (`sa-command.vercel.app`), backend (`sa-command-backend.onrender.com`), and Navigator Gateway remain unaffected.

---

## 9. Remaining Manual Actions for Owner

To complete the Google Drive authorization and activate automated CI/CD backups:

1. **Publish OAuth App in Google Cloud Console:**
   - Open **APIs & Services > OAuth consent screen**.
   - Under **Publishing status**, click **"Publish App"** (prevents the 7-day token expiration policy).
2. **Run Interactive Authorization (30 seconds):**
   - Open a PowerShell terminal in the repository and run:
     ```powershell
     powershell -ExecutionPolicy Bypass -File scripts\setup_gdrive_auth.ps1
     ```
   - Enter your Client ID and Client Secret (when prompted, or pass as parameters).
   - Your browser will open Google's login window. Click **Allow**.
   - The script will automatically verify the `SA_COMMAND_DB_BACKUPS` folder and copy the entire `rclone.conf` configuration directly to your clipboard.
3. **Save Secret in GitHub:**
   - Go to: `https://github.com/sufibuildwith-py/sa-controlcentre/settings/secrets/actions`
   - Click **"New repository secret"**.
   - Name: `GDRIVE_RCLONE_CONFIG`
   - Value: Press `Ctrl+V` (paste your clipboard).
   - Click **"Add secret"**.

---

## 10. Commit & Deployment Status

- **Workflows Updated:** `.github/workflows/db-backup.yml`
- **Core Scripts Updated:** `scripts/backup_database.py`, `scripts/setup_gdrive_auth.ps1`
- **Runbooks Updated:** `SA_COMMAND_DATABASE_RECOVERY.md`
- **Branch:** `production`
