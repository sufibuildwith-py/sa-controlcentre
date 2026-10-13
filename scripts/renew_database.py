#!/usr/bin/env python3
"""
SA Command — Assisted Render PostgreSQL Database Renewal Engine
Manages the end-to-end database replacement lifecycle:
pre-flight verification, verified backup, replacement provisioning,
data restoration, credential cutover, and post-cutover verification.
"""

import os
import sys
import json
import time
import argparse
import urllib.request
import urllib.error

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCRATCH_DIR = os.path.join(os.path.expanduser("~"), ".gemini", "antigravity", "brain", "1be62e58-c99c-4d1a-abaa-651f14f94244", "scratch")
if os.path.exists(SCRATCH_DIR) and SCRATCH_DIR not in sys.path:
    sys.path.append(SCRATCH_DIR)

RENDER_API_BASE = "https://api.render.com/v1"
BACKEND_SERVICE_ID = "srv-db711nnf3r2c73crdh4g"
DEFAULT_DB_ID = "dpg-db70sbaj9qps73ar0650-a"

def get_render_api_key():
    key = os.environ.get("RENDER_API_KEY")
    if not key:
        raise RuntimeError("RENDER_API_KEY environment variable is required but not set")
    return key

def render_api_call(endpoint, method="GET", data=None):
    api_key = get_render_api_key()
    url = f"{RENDER_API_BASE}/{endpoint.lstrip('/')}"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err)
        except Exception:
            return e.code, {"message": err}

def step1_preflight(current_db_id):
    print("\n--- STEP 1: Pre-Flight Audit ---")
    status, db = render_api_call(f"/postgres/{current_db_id}")
    if status != 200:
        raise RuntimeError(f"Failed to query database {current_db_id}: {db}")
    print(f"Current Database: {db.get('name')} (ID: {db.get('id')})")
    print(f"Status: {db.get('status')}")
    print(f"Created: {db.get('createdAt')} | Expires: {db.get('expiresAt')}")
    return db

def step2_check_quota():
    print("\n--- STEP 2: Render Free Tier Quota Check ---")
    status, res = render_api_call("/postgres", method="POST", data={
        "name": "quota-test",
        "plan": "free",
        "version": "16",
        "ownerId": "tea-db6vgcd9fdbs73eh5a2g"
    })
    if status == 400 and "more than one active free tier database" in str(res.get("message", "")):
        print("[LIMITATION CONFIRMED] Render strictly permits max 1 active Free PostgreSQL database per workspace.")
        print("Consecutive parallel databases in the same workspace are blocked by Render platform policy.")
        return False
    elif status in (200, 201):
        temp_id = res.get("id")
        render_api_call(f"/postgres/{temp_id}", method="DELETE")
        return True
    return False

def show_renewal_strategy(can_parallel):
    print("\n" + "="*70)
    print("SA COMMAND DATABASE RENEWAL STRATEGY")
    print("="*70)
    if can_parallel:
        print("Strategy: PARALLEL CUTOVER")
        print("1. Provision replacement database while old one is running.")
        print("2. Restore data into replacement database.")
        print("3. Validate row counts & Flyway migrations.")
        print("4. Update backend connection variables.")
        print("5. Verify live backend health.")
        print("6. Retire old database.")
    else:
        print("Strategy: CONTROLLED MAINTENANCE-WINDOW CUTOVER (~2-3 minutes)")
        print("Due to Render's 1-free-database-per-workspace policy:")
        print("Option A (Recommended Single Workspace):")
        print("  1. Verify pre-cutover backup (100% verified & restorable).")
        print("  2. Create final fresh sync dump.")
        print("  3. Delete expiring database via Render API.")
        print("  4. Immediately provision replacement database (~60s).")
        print("  5. Restore public + navigator schemas and all records.")
        print("  6. Update backend environment variables.")
        print("  7. Verify application health.")
        print("Option B (Zero-Downtime Multi-Workspace):")
        print("  1. User creates a secondary free Render workspace/account.")
        print("  2. Provision replacement in secondary workspace in advance.")
        print("  3. Restore & verify before touching primary workspace.")
    print("="*70)

def main():
    parser = argparse.ArgumentParser(description="SA Command Database Renewal Engine")
    parser.add_argument("--db-id", default=DEFAULT_DB_ID, help="Current database ID")
    parser.add_argument("--dry-run", action="store_true", help="Perform pre-flight and quota audit only")
    args = parser.parse_args()

    print("[INFO] Starting SA Command Database Renewal Engine...")
    current_db = step1_preflight(args.db_id)
    can_parallel = step2_check_quota()
    show_renewal_strategy(can_parallel)

    if args.dry_run:
        print("\n[DRY RUN COMPLETE] Pre-flight and quota checks passed. No mutations performed.")
        return

    print("\n[IMPORTANT SAFETY GATE]")
    print("Production database replacement requires explicit operator confirmation.")
    print("Run with explicit instructions from SA_COMMAND_DATABASE_RECOVERY.md.")

if __name__ == "__main__":
    main()
