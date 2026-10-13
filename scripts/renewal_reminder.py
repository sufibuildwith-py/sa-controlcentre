#!/usr/bin/env python3
"""
SA Command — PostgreSQL Database Renewal Checker & Reminder Engine
Reads creation/expiration timestamps from Render API and issues tiered reminders
for Render Free tier's 30-day database lifespan.
"""

import os
import sys
import json
import argparse
from datetime import datetime, timezone, timedelta
import urllib.request
import urllib.error

# Add scratch to path for Render API client if available
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCRATCH_DIR = os.path.join(os.path.expanduser("~"), ".gemini", "antigravity", "brain", "1be62e58-c99c-4d1a-abaa-651f14f94244", "scratch")
if os.path.exists(SCRATCH_DIR) and SCRATCH_DIR not in sys.path:
    sys.path.append(SCRATCH_DIR)

DEFAULT_DB_ID = "dpg-db70sbaj9qps73ar0650-a"
RENDER_API_BASE = "https://api.render.com/v1"
STATE_FILE = os.path.join(BASE_DIR, ".db_renewal_state.json")

def get_render_api_key():
    key = os.environ.get("RENDER_API_KEY")
    if not key:
        raise RuntimeError("RENDER_API_KEY environment variable is required but not set")
    return key

def fetch_render_db_info(db_id, api_key):
    url = f"{RENDER_API_BASE}/postgres/{db_id}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Accept": "application/json",
            "Content-Type": "application/json"
        }
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def parse_iso(ts_str):
    if not ts_str:
        return None
    # Python 3.11+ handles Z and fractional seconds
    ts_str = ts_str.replace("Z", "+00:00")
    return datetime.fromisoformat(ts_str)

def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, "r") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_state(state):
    try:
        with open(STATE_FILE, "w") as f:
            json.dump(state, f, indent=2)
    except Exception as e:
        print(f"[WARN] Failed to write state file: {e}", file=sys.stderr)

def evaluate_renewal(created_at, expires_at, now=None, simulated_day=None):
    if now is None:
        now = datetime.now(timezone.utc)
    
    if simulated_day is not None:
        # Simulate that today is day N of the 30-day cycle
        total_lifespan = timedelta(days=30)
        created_at = now - timedelta(days=simulated_day)
        expires_at = created_at + total_lifespan

    age = now - created_at
    remaining = expires_at - now
    
    age_days = age.total_seconds() / 86400.0
    remaining_days = remaining.total_seconds() / 86400.0
    
    # Determine alert tier
    # Day 25: Initial replacement reminder (age >= 25 days or remaining <= 5 days)
    # Day 27: Escalation reminder (age >= 27 days or remaining <= 3 days)
    # Day 29: Urgent escalation (age >= 29 days or remaining <= 1 day)
    # Day 30+: Expiration imminent / expired
    tier = 0
    level_name = "NORMAL"
    message = "Database is within normal operating lifespan."
    
    if age_days >= 30 or remaining_days <= 0:
        tier = 4
        level_name = "EXPIRED"
        message = f"CRITICAL DANGER: Database has reached or passed expiration! ({remaining_days:.1f} days remaining)"
    elif age_days >= 29 or remaining_days <= 1.0:
        tier = 3
        level_name = "URGENT_ESCALATION"
        message = f"LEVEL 3 URGENT ESCALATION (Day {int(age_days)}): Database expires within 24-48 hours! Action required immediately."
    elif age_days >= 27 or remaining_days <= 3.0:
        tier = 2
        level_name = "ESCALATION"
        message = f"LEVEL 2 ESCALATION (Day {int(age_days)}): Database renewal remains incomplete with ~{remaining_days:.1f} days remaining. Plan maintenance window."
    elif age_days >= 25 or remaining_days <= 5.0:
        tier = 1
        level_name = "INITIAL_REMINDER"
        message = f"LEVEL 1 REMINDER (Day {int(age_days)}): Replacement window open. ~{remaining_days:.1f} days remaining on Render Free tier."
        
    return {
        "created_at": created_at.isoformat(),
        "expires_at": expires_at.isoformat(),
        "evaluated_at": now.isoformat(),
        "age_days": round(age_days, 2),
        "remaining_days": round(remaining_days, 2),
        "tier": tier,
        "level_name": level_name,
        "message": message,
        "requires_action": tier > 0
    }

def dispatch_github_issue(level_name, tier, db_name, db_id, eval_result):
    token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "delusional-deployments/sa-panel")
    if not token:
        print("[INFO] GITHUB_TOKEN not set; skipping GitHub Issue creation.")
        return False
        
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json"
    }
    
    # Check for existing open renewal issue
    url_list = f"https://api.github.com/repos/{repo}/issues?labels=database-renewal&state=open"
    req_list = urllib.request.Request(url_list, headers=headers)
    existing_issue = None
    try:
        with urllib.request.urlopen(req_list) as resp:
            issues = json.loads(resp.read().decode("utf-8"))
            if issues:
                existing_issue = issues[0]
    except Exception as e:
        print(f"[WARN] Failed to query existing issues: {e}")
        
    body_text = f"""### ⚠️ Database Renewal Alert: {level_name} (Tier {tier})

cc @sufibuildwith-py

- **Database:** `{db_name}` (`{db_id}`)
- **Lifespan Age:** {eval_result['age_days']} days (of 30)
- **Time Remaining:** {eval_result['remaining_days']} days
- **Expiration Date:** `{eval_result['expires_at']}`
- **Action Required:** {eval_result['message']}

> [!IMPORTANT]
> Render Free PostgreSQL instances are strictly limited to a 30-day lifecycle.
> Refer to [`SA_COMMAND_DATABASE_RECOVERY.md`](https://github.com/{repo}/blob/production/SA_COMMAND_DATABASE_RECOVERY.md) for the replacement runbook.
"""

    if existing_issue:
        issue_num = existing_issue["number"]
        comment_url = f"https://api.github.com/repos/{repo}/issues/{issue_num}/comments"
        payload = json.dumps({"body": body_text}).encode("utf-8")
        req_post = urllib.request.Request(comment_url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req_post) as resp:
                print(f"[INFO] Successfully added escalation comment to GitHub Issue #{issue_num}.")
                return True
        except Exception as e:
            print(f"[WARN] Failed to post comment to issue #{issue_num}: {e}")
            return False
    else:
        create_url = f"https://api.github.com/repos/{repo}/issues"
        payload = json.dumps({
            "title": f"🚨 ACTION REQUIRED: Render PostgreSQL Renewal Window Open (Day {int(eval_result['age_days'])})",
            "body": body_text,
            "labels": ["database-renewal", "urgent"]
        }).encode("utf-8")
        req_post = urllib.request.Request(create_url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req_post) as resp:
                created = json.loads(resp.read().decode("utf-8"))
                print(f"[INFO] Successfully opened GitHub Issue #{created.get('number')} notifying @sufibuildwith-py.")
                return True
        except Exception as e:
            print(f"[WARN] Failed to create GitHub Issue: {e}")
            return False

def dispatch_webhook(eval_result, db_name, db_id):
    webhook_url = os.environ.get("NOTIFICATION_WEBHOOK_URL")
    if not webhook_url:
        return
    msg = f"🚨 **SA Command Database Renewal Alert [{eval_result['level_name']}]**: {eval_result['remaining_days']} days remaining for `{db_name}` ({db_id}). Expiration: `{eval_result['expires_at']}`."
    req = urllib.request.Request(
        webhook_url,
        data=json.dumps({"content": msg, "text": msg}).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req) as resp:
            print("[INFO] Webhook notification dispatched successfully.")
    except Exception as e:
        print(f"[WARN] Webhook dispatch failed: {e}")

def dispatch_notification(eval_result, db_info, is_simulation=False):
    tier = eval_result["tier"]
    level_name = eval_result["level_name"]
    db_id = db_info.get("id", "unknown")
    db_name = db_info.get("name", "sa-command-db")
    
    print("\n" + "="*70)
    print(f"DATABASE RENEWAL ALERT: [{level_name}] (Tier {tier})")
    print(f"Database: {db_name} (ID: {db_id})")
    print(f"Created At: {eval_result['created_at']}")
    print(f"Expires At: {eval_result['expires_at']}")
    print(f"Age: {eval_result['age_days']} days | Remaining: {eval_result['remaining_days']} days")
    print(f"Status: {eval_result['message']}")
    print("="*70)
    
    if is_simulation:
        print("[SIMULATION] Notification rendered but external dispatch skipped.")
        return True

    # Check state to prevent duplicate notifications for the same tier & db_id
    state = load_state()
    db_state = state.get(db_id, {})
    last_notified_tier = db_state.get("last_notified_tier", 0)
    last_notified_at = db_state.get("last_notified_at")
    
    if tier == 0:
        # Normal state, no notification required
        return True
        
    if tier <= last_notified_tier:
        print(f"[INFO] Tier {tier} already notified at {last_notified_at} for instance {db_id}. Suppressing duplicate.")
        return False
        
    # In GitHub Actions, write to Step Summary and create/comment on GitHub Issue
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file and os.path.exists(os.path.dirname(summary_file)):
        with open(summary_file, "a", encoding="utf-8") as sf:
            sf.write(f"\n### ⚠️ Database Renewal Alert: {level_name}\n\n")
            sf.write(f"- **Database:** `{db_name}` (`{db_id}`)\n")
            sf.write(f"- **Lifespan Age:** {eval_result['age_days']} days (of 30)\n")
            sf.write(f"- **Time Remaining:** {eval_result['remaining_days']} days\n")
            sf.write(f"- **Expiration Date:** `{eval_result['expires_at']}`\n")
            sf.write(f"- **Action Required:** {eval_result['message']}\n\n")
            sf.write(f"Refer to [`SA_COMMAND_DATABASE_RECOVERY.md`](../SA_COMMAND_DATABASE_RECOVERY.md) for renewal procedure.\n")

    # Dispatch GitHub Issue to trigger direct email and mobile notifications to repository owner
    dispatch_github_issue(level_name, tier, db_name, db_id, eval_result)
    dispatch_webhook(eval_result, db_name, db_id)
            
    # Update state
    db_state["last_notified_tier"] = tier
    db_state["last_notified_level"] = level_name
    db_state["last_notified_at"] = datetime.now(timezone.utc).isoformat()
    db_state["db_id"] = db_id
    state[db_id] = db_state
    save_state(state)
    print(f"[INFO] Notification dispatched and recorded in state for {db_id} at Tier {tier}.")
    return True

def main():
    parser = argparse.ArgumentParser(description="Render PostgreSQL Renewal Checker")
    parser.add_argument("--db-id", default=os.environ.get("RENDER_DATABASE_ID", DEFAULT_DB_ID), help="Render database ID")
    parser.add_argument("--simulate-day", type=float, help="Simulate execution on day N of 30-day lifecycle (e.g. 24, 25, 27, 29, 30)")
    parser.add_argument("--force", action="store_true", help="Force notification dispatch even if tier has not changed")
    args = parser.parse_args()

    if args.simulate_day is not None:
        db_info = {
            "id": args.db_id,
            "name": "sa-command-db (SIMULATION)",
            "plan": "free",
            "region": "oregon"
        }
        res = evaluate_renewal(None, None, simulated_day=args.simulate_day)
        dispatch_notification(res, db_info, is_simulation=True)
        return

    api_key = get_render_api_key()
    print(f"[INFO] Checking renewal status for database {args.db_id}...")

    try:
        db_info = fetch_render_db_info(args.db_id, api_key)
    except Exception as e:
        print(f"[ERROR] Failed to fetch database info from Render API: {e}", file=sys.stderr)
        sys.exit(1)

    created_at = parse_iso(db_info.get("createdAt"))
    expires_at = parse_iso(db_info.get("expiresAt"))
    
    if not created_at or not expires_at:
        print("[ERROR] Database response missing createdAt or expiresAt timestamps", file=sys.stderr)
        sys.exit(1)
        
    eval_result = evaluate_renewal(created_at, expires_at)
    dispatch_notification(eval_result, db_info, is_simulation=False)
    
    if eval_result["tier"] >= 3:
        # Exit with warning code if urgent
        sys.exit(2)
    elif eval_result["tier"] > 0:
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
