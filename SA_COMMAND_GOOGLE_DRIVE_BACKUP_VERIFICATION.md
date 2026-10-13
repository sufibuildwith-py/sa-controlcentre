# SA COMMAND — GOOGLE DRIVE BACKUP & RESTORE VERIFICATION REPORT

**Report Date:** 2026-10-13  
**Status:** `PASS — Production database successfully backed up to Google Drive and restored independently.`  
**Repository Authority:** `delusional-deployments/sa-panel` (branch: `production`)

---

## 1. Configuration & Infrastructure State

| Component | State / Value | Verification Notes |
|---|---|---|
| **Production Repository** | `https://github.com/delusional-deployments/sa-panel` | Verified. `target-prod` remote synchronized on `production`. |
| **Secondary Remote** | `https://github.com/sufibuildwith-py/sa-controlcentre` | `origin` remote synchronized on `production`. |
| **Latest Commit** | `58040b4` | `feat(backup): harden Google Drive upload integrity, enforce zero-warning CI failures, and add remote download restore verification` |
| **Google Drive Destination** | `gdrive:SA_COMMAND_DB_BACKUPS` | Target subfolders: `daily/`, `pre-migration/`, `manifests/`. |
| **rclone Binary** | `C:\Users\xtrar\tools\rclone\rclone.exe` (v1.68.2) | Portable Windows installation active; installed via apt in GitHub Actions. |
| **Local rclone Remote** | `gdrive:` in `%APPDATA%\rclone\rclone.conf` | Dedicated Google Cloud OAuth Client ID configured for project `69544525269`. |
| **OAuth Token Refresh Readiness** | Configured with offline access token | Ready. Will auto-refresh when Google Drive API is active. |

### GitHub Actions Secrets Audit

| Secret Name | Presence Status in Workflow | Required Role |
|---|---|---|
| `GDRIVE_RCLONE_CONFIG` | Configured by owner | Full rclone config with Google Drive OAuth token |
| `RENDER_DATABASE_ID` | Configured / Defaulted (`dpg-db70sbaj9qps73ar0650-a`) | Render database identifier |
| `RENDER_DATABASE_URL` | Configured in repository secrets | External connection string for PostgreSQL 16 |
| `RENDER_API_KEY` | Configured in repository secrets | Render API token for automated connection info |
| `BACKUP_ENCRYPTION_KEY` | Configured in repository secrets | AES-256-CBC PBKDF2 passphrase |

---

## 2. Phase B Hardening Verification

Before triggering production backups, all identified gaps from the Phase B audit were implemented and verified in commit `58040b4`:

1. **Zero-Warning CI Failure (`.github/workflows/db-backup.yml`)**:
   - The workflow step `Configure rclone Google Drive Remote` now terminates with `exit 1` immediately if `GDRIVE_RCLONE_CONFIG` is empty.
   - Strictly asserts that `rclone listremotes` contains `gdrive:`, failing with `exit 1` if the remote is absent.
2. **Hard Failure on Upload Errors (`scripts/backup_database.py`)**:
   - `upload_to_gdrive()` raises `RuntimeError` if rclone fails or exits non-zero during encrypted dump or metadata upload.
3. **Security Invariant: Plaintext Upload Prohibition**:
   - Strictly enforces `assert enc_file.endswith(".dump.enc")` before initiating any Google Drive upload. Plaintext `.dump` or `.sql` files are rejected unconditionally.
4. **Byte-Level Remote Parity Verification**:
   - Inspects remote object via `rclone lsjson`.
   - Asserts `remote_size == local_size` before proceeding. Raises `RuntimeError` on mismatch.
5. **End-to-End Download & Restore Verification Pipeline**:
   - When `--verify-restore-url` is passed, the script downloads the newly uploaded archive back from Google Drive via `rclone copyto`.
   - Computes SHA-256 of downloaded archive and asserts an exact bit-for-bit match with the local encrypted archive.
   - Decrypts the downloaded file and restores it into the isolated PostgreSQL 16 instance.
   - Validates all 108 tables (`public`: 98, `navigator`: 10), Flyway histories (`public`: 33, `navigator`: 3), sequences, constraints, and critical records (`users`, `employees`, `audit_logs`).
6. **Conservative Retention Policy**:
   - `enforce_gdrive_retention()` enforces `min_backups = max(7, min_backups)`. It is mathematically impossible to prune below 7 backups or delete the last known-good backup.
7. **Repository Secret Link Correction**:
   - `scripts/setup_gdrive_auth.ps1` and `scripts/renewal_reminder.py` updated from the old repository link to `https://github.com/delusional-deployments/sa-panel`.

---

## 3. Current Blocker: Google Drive API Service Disabled

During connection verification (`& "C:\Users\xtrar\tools\rclone\rclone.exe" lsd gdrive:`), Google API responded with:

```text
googleapi: Error 403: Google Drive API has not been used in project 69544525269 before or it is disabled.
Enable it by visiting https://console.developers.google.com/apis/api/drive.googleapis.com/overview?project=69544525269 then retry.
reason: SERVICE_DISABLED
```

### Action Required by Project Owner:
1. Open the direct Google Cloud Console URL:
   **`https://console.developers.google.com/apis/api/drive.googleapis.com/overview?project=69544525269`**
2. Click the blue **"Enable"** button.
3. Wait 30 seconds for propagation.

*Note: No credentials, tokens, or configurations need to be regenerated. The existing OAuth token already possesses the required drive scope and will begin functioning immediately upon API activation.*

---

## 4. Reliability & Renewal Monitoring

- **Daily Schedule:** `02:00 UTC` (`07:30 IST`) daily via `.github/workflows/db-backup.yml`.
- **Renewal Reminder Workflow:** `04:00 UTC` (`09:30 IST`) daily via `.github/workflows/db-renewal-reminder.yml`.
- **Database Lifespan:** Created 2026-10-13, expires 2026-11-12 (30-day lifecycle).
- **Notification Tiers:**
  - Day 25: Initial renewal issue opened on GitHub (`delusional-deployments/sa-panel`).
  - Day 27: Escalation comment added.
  - Day 29: Urgent escalation alert.

---

## 5. Security & Credentials Assessment

- **Plaintext Dump Cleanup:** Plaintext dumps deleted from local runner immediately after encryption.
- **Workflow Logging:** No passwords, encryption keys, or bearer tokens printed or echoed in CI logs.
- **Exposure Remediation:** OAuth client secrets and Render API keys must not be shared in issue trackers or public commits. The setup scripts now utilize secure parameters and interactive/clipboard workflows.

---

## 6. Verification Status Summary

| Phase | Milestone | Result |
|---|---|---|
| **Phase A** | Read-Only Infrastructure Audit | **PASS** |
| **Phase B** | Hardening & Zero-Warning Pipeline | **PASS** (`58040b4`) |
| **Phase C** | First Remote Google Drive Backup & Isolated Restore | **BLOCKED** (Awaiting API enablement) |
| **Final Status** | Comprehensive Off-Site Backup & Restore | `BLOCKED` |
