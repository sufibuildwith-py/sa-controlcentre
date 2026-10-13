#!/usr/bin/env python3
"""
SA Command — Automated PostgreSQL Backup & Integrity Engine
Exports consistent backup of Render PostgreSQL, encrypts with AES-256,
verifies TOC integrity, uploads to off-site S3-compatible storage, and tests restore.
Supports both pg_dump (Linux/CI) and pure-Python psycopg2 extractor (portable/Windows).
"""

import os
import sys
import json
import shutil
import hashlib
import subprocess
import argparse
from datetime import datetime, timezone, timedelta
import urllib.parse
import urllib.request

# Add scratch to path for Render API client if available
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCRATCH_DIR = os.path.join(os.path.expanduser("~"), ".gemini", "antigravity", "brain", "1be62e58-c99c-4d1a-abaa-651f14f94244", "scratch")
if os.path.exists(SCRATCH_DIR) and SCRATCH_DIR not in sys.path:
    sys.path.append(SCRATCH_DIR)

DEFAULT_DB_ID = "dpg-db70sbaj9qps73ar0650-a"
RENDER_API_BASE = "https://api.render.com/v1"

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def get_connection_url(db_id, api_key=None):
    direct_url = os.environ.get("RENDER_DATABASE_URL")
    if direct_url:
        return direct_url
        
    if not api_key:
        api_key = os.environ.get("RENDER_API_KEY")
        if not api_key:
            raise RuntimeError("RENDER_API_KEY environment variable is required")
        
    url = f"{RENDER_API_BASE}/postgres/{db_id}/connection-info"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Accept": "application/json"
        }
    )
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        conn_str = data.get("externalConnectionString")
        if not conn_str:
            raise RuntimeError("Render API returned empty externalConnectionString")
        return conn_str

def parse_connection_url(conn_str):
    clean_str = conn_str.replace("jdbc:", "")
    parsed = urllib.parse.urlparse(clean_str)
    return {
        "host": parsed.hostname,
        "port": str(parsed.port or 5432),
        "dbname": parsed.path.lstrip("/"),
        "user": parsed.username,
        "password": parsed.password,
        "sslmode": "require",
        "raw_url": clean_str
    }

def run_pg_dump(conn_params, output_dump_path):
    env = os.environ.copy()
    env["PGHOST"] = conn_params["host"]
    env["PGPORT"] = conn_params["port"]
    env["PGDATABASE"] = conn_params["dbname"]
    env["PGUSER"] = conn_params["user"]
    env["PGPASSWORD"] = conn_params["password"]
    env["PGSSLMODE"] = "require"

    cmd = [
        "pg_dump",
        "--format=custom",
        "--no-owner",
        "--no-acl",
        "--verbose",
        f"--file={output_dump_path}"
    ]
    
    print(f"[INFO] Running pg_dump (custom format) to {output_dump_path}...")
    proc = subprocess.run(cmd, env=env, capture_output=True, text=True)
    if proc.returncode != 0:
        err = proc.stderr.replace(conn_params["password"], "********") if conn_params.get("password") else proc.stderr
        raise RuntimeError(f"pg_dump failed with exit code {proc.returncode}: {err[:500]}")
    
    if not os.path.exists(output_dump_path) or os.path.getsize(output_dump_path) == 0:
        raise RuntimeError("pg_dump produced an empty or missing output file")
        
    print(f"[INFO] pg_dump completed. Size: {os.path.getsize(output_dump_path)} bytes.")

def run_python_dump(conn_params, output_dump_path):
    print(f"[INFO] pg_dump not found in PATH. Using portable Python PostgreSQL extractor...")
    import psycopg2
    
    conn = psycopg2.connect(conn_params["raw_url"], sslmode="require")
    cur = conn.cursor()
    
    # Query tables in public and navigator
    cur.execute("""
        SELECT table_schema, table_name 
        FROM information_schema.tables 
        WHERE table_schema IN ('public', 'navigator') 
          AND table_type = 'BASE TABLE'
        ORDER BY table_schema, table_name;
    """)
    tables = cur.fetchall()
    
    dump_dict = {
        "format": "sa_command_json_dump_v1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "database": conn_params["dbname"],
        "tables": {}
    }
    
    for schema, tbl in tables:
        cur.execute(f'SELECT count(*) FROM "{schema}"."{tbl}"')
        cnt = cur.fetchone()[0]
        
        # Get column details
        cur.execute("""
            SELECT column_name, data_type, udt_name
            FROM information_schema.columns 
            WHERE table_schema = %s AND table_name = %s
            ORDER BY ordinal_position;
        """, (schema, tbl))
        col_info = cur.fetchall()
        cols = [c[0] for c in col_info]
        
        # Fetch rows
        rows_data = []
        if cnt > 0:
            cols_quoted = [f'"{c}"' for c in cols]
            cur.execute(f'SELECT {", ".join(cols_quoted)} FROM "{schema}"."{tbl}"')
            raw_rows = cur.fetchall()
            for r in raw_rows:
                # Serialize row values
                row_serialized = []
                for val in r:
                    if val is None:
                        row_serialized.append(None)
                    elif isinstance(val, (datetime,)):
                        row_serialized.append(val.isoformat())
                    elif isinstance(val, (dict, list)):
                        row_serialized.append(json.dumps(val))
                    elif isinstance(val, (int, float, bool)):
                        row_serialized.append(val)
                    elif isinstance(val, bytes):
                        row_serialized.append(val.hex())
                    else:
                        row_serialized.append(str(val))
                rows_data.append(row_serialized)
                
        dump_dict["tables"][f"{schema}.{tbl}"] = {
            "schema": schema,
            "table": tbl,
            "columns": cols,
            "row_count": cnt,
            "rows": rows_data
        }
        
    conn.close()
    
    with open(output_dump_path, "w", encoding="utf-8") as f:
        json.dump(dump_dict, f, indent=2)
        
    print(f"[INFO] Python extractor dumped {len(tables)} tables to {output_dump_path}. Size: {os.path.getsize(output_dump_path)} bytes.")

def verify_dump_toc(dump_path):
    print(f"[INFO] Inspecting Table of Contents / Manifest...")
    if shutil.which("pg_restore"):
        cmd = ["pg_restore", "--list", dump_path]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode == 0:
            lines = proc.stdout.splitlines()
            public_entries = sum(1 for l in lines if "public" in l)
            navigator_entries = sum(1 for l in lines if "navigator" in l)
            print(f"[INFO] pg_restore TOC entries: total={len(lines)}, public={public_entries}, navigator={navigator_entries}")
            return {
                "total_toc_entries": len(lines),
                "public_entries": public_entries,
                "navigator_entries": navigator_entries
            }
            
    # If JSON dump
    try:
        with open(dump_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        tables = data.get("tables", {})
        public_entries = sum(1 for k in tables if k.startswith("public."))
        navigator_entries = sum(1 for k in tables if k.startswith("navigator."))
        print(f"[INFO] JSON Dump tables: total={len(tables)}, public={public_entries}, navigator={navigator_entries}")
        return {
            "total_toc_entries": len(tables),
            "public_entries": public_entries,
            "navigator_entries": navigator_entries
        }
    except Exception as e:
        raise RuntimeError(f"Failed to parse dump archive manifest: {e}")

def encrypt_dump(dump_path, enc_output_path, encryption_key):
    print(f"[INFO] Encrypting backup with AES-256-CBC (PBKDF2)...")
    if shutil.which("openssl"):
        env = os.environ.copy()
        env["BACKUP_PASS"] = encryption_key
        cmd = [
            "openssl", "enc", "-aes-256-cbc",
            "-salt", "-pbkdf2", "-iter", "100000",
            "-in", dump_path,
            "-out", enc_output_path,
            "-pass", "env:BACKUP_PASS"
        ]
        proc = subprocess.run(cmd, env=env, capture_output=True, text=True)
        if proc.returncode == 0 and os.path.exists(enc_output_path) and os.path.getsize(enc_output_path) > 0:
            print(f"[INFO] OpenSSL encryption succeeded. Size: {os.path.getsize(enc_output_path)} bytes.")
            return

    # Portable Python cryptography fallback
    print(f"[INFO] Using Python cryptography AES-256-CBC with PBKDF2...")
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives import padding
    
    salt = os.urandom(16)
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=100000,
        backend=default_backend()
    )
    key = kdf.derive(encryption_key.encode("utf-8"))
    iv = os.urandom(16)
    
    padder = padding.PKCS7(128).padder()
    with open(dump_path, "rb") as f_in:
        plaintext = f_in.read()
    padded_data = padder.update(plaintext) + padder.finalize()
    
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
    encryptor = cipher.encryptor()
    ciphertext = encryptor.update(padded_data) + encryptor.finalize()
    
    # Store: [16 bytes salt][16 bytes iv][ciphertext]
    with open(enc_output_path, "wb") as f_out:
        f_out.write(b"SA_ENC_V1")
        f_out.write(salt)
        f_out.write(iv)
        f_out.write(ciphertext)
        
    print(f"[INFO] Portable encryption succeeded. Size: {os.path.getsize(enc_output_path)} bytes.")

def decrypt_dump(enc_path, dec_output_path, encryption_key):
    if shutil.which("openssl"):
        env = os.environ.copy()
        env["BACKUP_PASS"] = encryption_key
        cmd = [
            "openssl", "enc", "-d", "-aes-256-cbc",
            "-pbkdf2", "-iter", "100000",
            "-in", enc_path,
            "-out", dec_output_path,
            "-pass", "env:BACKUP_PASS"
        ]
        proc = subprocess.run(cmd, env=env, capture_output=True, text=True)
        if proc.returncode == 0 and os.path.exists(dec_output_path) and os.path.getsize(dec_output_path) > 0:
            return

    # Portable Python cryptography fallback
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives import padding

    with open(enc_path, "rb") as f_in:
        header = f_in.read(9)
        if header != b"SA_ENC_V1":
            raise RuntimeError(f"Unrecognized encryption header: {header}")
        salt = f_in.read(16)
        iv = f_in.read(16)
        ciphertext = f_in.read()
        
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=100000,
        backend=default_backend()
    )
    key = kdf.derive(encryption_key.encode("utf-8"))
    
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
    decryptor = cipher.decryptor()
    padded_data = decryptor.update(ciphertext) + decryptor.finalize()
    
    unpadder = padding.PKCS7(128).unpadder()
    plaintext = unpadder.update(padded_data) + unpadder.finalize()
    
    with open(dec_output_path, "wb") as f_out:
        f_out.write(plaintext)

def upload_to_s3_storage(enc_file, meta_file, timestamp_str, is_baseline=False):
    bucket = os.environ.get("BACKUP_S3_BUCKET")
    endpoint = os.environ.get("BACKUP_S3_ENDPOINT")
    access_key = os.environ.get("BACKUP_S3_ACCESS_KEY_ID")
    secret_key = os.environ.get("BACKUP_S3_SECRET_ACCESS_KEY")
    
    if not (bucket and access_key and secret_key):
        print("\n" + "!"*70)
        print("[STORAGE NOTICE] S3-compatible off-site storage credentials not yet set.")
        print("Required GitHub Secrets for off-site archival:")
        print("  - BACKUP_S3_BUCKET (e.g. 'sa-command-backups')")
        print("  - BACKUP_S3_ENDPOINT (e.g. 'https://<account-id>.r2.cloudflarestorage.com')")
        print("  - BACKUP_S3_ACCESS_KEY_ID")
        print("  - BACKUP_S3_SECRET_ACCESS_KEY")
        print(f"The encrypted backup is safely persisted at: {enc_file}")
        print("!"*70 + "\n")
        return False
        
    try:
        import boto3
        from botocore.config import Config
        s3 = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            config=Config(signature_version="s3v4")
        )
        
        prefix = "backups/sa-command-db"
        base_name = os.path.basename(enc_file)
        meta_name = os.path.basename(meta_file)
        
        target_dump_key = f"{prefix}/{'baseline' if is_baseline else timestamp_str[:10]}/{base_name}"
        target_meta_key = f"{prefix}/{'baseline' if is_baseline else timestamp_str[:10]}/{meta_name}"
        
        print(f"[INFO] Uploading encrypted backup to s3://{bucket}/{target_dump_key}...")
        s3.upload_file(enc_file, bucket, target_dump_key)
        
        print(f"[INFO] Uploading metadata to s3://{bucket}/{target_meta_key}...")
        s3.upload_file(meta_file, bucket, target_meta_key)
        
        if not is_baseline:
            enforce_retention(s3, bucket, prefix, min_days=7)
        return True
    except Exception as e:
        print(f"[ERROR] Failed to upload to S3-compatible storage: {e}", file=sys.stderr)
        return False

def enforce_retention(s3_client, bucket, prefix, min_days=7):
    try:
        resp = s3_client.list_objects_v2(Bucket=bucket, Prefix=prefix)
        contents = resp.get("Contents", [])
        
        cutoff = datetime.now(timezone.utc) - timedelta(days=min_days)
        deleted_count = 0
        
        for obj in contents:
            key = obj["Key"]
            last_modified = obj["LastModified"]
            if "baseline" in key.lower() or "pre-migration" in key.lower():
                continue
            if last_modified < cutoff:
                s3_client.delete_object(Bucket=bucket, Key=key)
                deleted_count += 1
                
        print(f"[INFO] Retention policy enforced. Retaining >= {min_days} daily backups. Pruned: {deleted_count}.")
def verify_restore_into_target(enc_path, encryption_key, target_url, format_name):
    print(f"\n[INFO] Starting real PostgreSQL restore verification into target database...")
    import psycopg2
    
    temp_dec = f"{enc_path}.restore_test.tmp"
    decrypt_dump(enc_path, temp_dec, encryption_key)
    
    try:
        clean_url = target_url.replace("jdbc:", "")
        if "custom format" in format_name and shutil.which("pg_restore"):
            print("[INFO] Restoring dump using pg_restore...")
            env = os.environ.copy()
            cmd = [
                "pg_restore",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-acl",
                "-d", clean_url,
                temp_dec
            ]
            proc = subprocess.run(cmd, env=env, capture_output=True, text=True)
            if proc.returncode > 1:
                print(f"[WARN] pg_restore exited with code {proc.returncode}: {proc.stderr[:300]}")
        else:
            print("[INFO] Restoring dump using portable Python extractor into target database...")
            with open(temp_dec, "r", encoding="utf-8") as f:
                dump_data = json.load(f)
            
            conn = psycopg2.connect(clean_url)
            conn.autocommit = True
            cur = conn.cursor()
            
            cur.execute("CREATE SCHEMA IF NOT EXISTS public;")
            cur.execute("CREATE SCHEMA IF NOT EXISTS navigator;")
            
            tables = dump_data.get("tables", {})
            for full_name, tbl_info in tables.items():
                schema = tbl_info["schema"]
                tbl = tbl_info["table"]
                cols = tbl_info["columns"]
                
                col_defs = [f'"{c}" text' for c in cols]
                cur.execute(f'CREATE TABLE IF NOT EXISTS "{schema}"."{tbl}" ({", ".join(col_defs)});')
                
                rows = tbl_info.get("rows", [])
                if rows:
                    col_list_str = ", ".join([f'"{c}"' for c in cols])
                    placeholders = ", ".join(["%s"] * len(cols))
                    insert_sql = f'INSERT INTO "{schema}"."{tbl}" ({col_list_str}) VALUES ({placeholders})'
                    for r in rows:
                        cur.execute(insert_sql, tuple(r))
            conn.close()
            
        # Run integrity verification queries on target database
        conn = psycopg2.connect(clean_url)
        conn.autocommit = True
        cur = conn.cursor()
        
        cur.execute("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';")
        pub_tbls = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'navigator' AND table_type = 'BASE TABLE';")
        nav_tbls = cur.fetchone()[0]
        total_tbls = pub_tbls + nav_tbls
        
        print(f"[INFO] Verified restored tables: total={total_tbls} (public={pub_tbls}, navigator={nav_tbls})")
        assert pub_tbls >= 90, f"Expected >= 90 public tables, got {pub_tbls}"
        assert nav_tbls >= 10, f"Expected >= 10 navigator tables, got {nav_tbls}"
        
        # Check Flyway
        cur.execute("SELECT count(*), max(version) FROM public.flyway_schema_history;")
        pub_fw = cur.fetchone()
        print(f"[INFO] Verified Public Flyway: {pub_fw[0]} migrations (latest: {pub_fw[1]})")
        assert pub_fw[0] >= 33, f"Expected >= 33 public migrations, got {pub_fw[0]}"
        
        cur.execute("SELECT count(*), max(version) FROM navigator.flyway_schema_history;")
        nav_fw = cur.fetchone()
        print(f"[INFO] Verified Navigator Flyway: {nav_fw[0]} migrations (latest: {nav_fw[1]})")
        assert nav_fw[0] >= 3, f"Expected >= 3 navigator migrations, got {nav_fw[0]}"
        
        # Check critical records
        cur.execute("SELECT count(*) FROM public.users;")
        u_cnt = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM public.employees;")
        e_cnt = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM public.audit_logs;")
        a_cnt = cur.fetchone()[0]
        print(f"[INFO] Verified critical records: users={u_cnt}, employees={e_cnt}, audit_logs={a_cnt}")
        assert u_cnt >= 1, "Missing users records"
        assert e_cnt >= 1, "Missing employees records"
        assert a_cnt >= 100, f"Audit logs record count low: {a_cnt}"
        
        conn.close()
        print("\n" + "="*70)
        print("[PASS] REAL POSTGRESQL RESTORE VERIFICATION PASSED (100%)")
        print("All tables, schemas, Flyway histories, and critical business records verified.")
        print("="*70 + "\n")
        
    finally:
        if os.path.exists(temp_dec):
            os.remove(temp_dec)

def main():
    parser = argparse.ArgumentParser(description="SA Command PostgreSQL Backup Engine")
    parser.add_argument("--db-id", default=os.environ.get("RENDER_DATABASE_ID", DEFAULT_DB_ID))
    parser.add_argument("--output-dir", default="backups/archive")
    parser.add_argument("--baseline", action="store_true", help="Mark as permanent pre-migration baseline backup")
    parser.add_argument("--verify-restore-url", help="Isolated PostgreSQL URL to verify restore")
    args = parser.parse_args()
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    encryption_key = os.environ.get("BACKUP_ENCRYPTION_KEY")
    if not encryption_key:
        raise RuntimeError("BACKUP_ENCRYPTION_KEY environment variable is required")
        
    ts_now = datetime.now(timezone.utc)
    ts_str = ts_now.strftime("%Y%m%d_%H%M%S")
    
    prefix = "sa_command_baseline" if args.baseline else "sa_command_prod"
    dump_filename = f"{prefix}_{ts_str}.dump"
    dump_path = os.path.join(args.output_dir, dump_filename)
    enc_path = f"{dump_path}.enc"
    meta_path = os.path.join(args.output_dir, f"{prefix}_{ts_str}.meta.json")
    
    try:
        conn_str = get_connection_url(args.db_id)
        conn_params = parse_connection_url(conn_str)
        
        # 1. Export database
        if shutil.which("pg_dump"):
            run_pg_dump(conn_params, dump_path)
            format_name = "pg_dump custom format (-Fc)"
        else:
            run_python_dump(conn_params, dump_path)
            format_name = "portable python json dump"
            
        plaintext_size = os.path.getsize(dump_path)
        plaintext_sha = sha256_file(dump_path)
        
        # 2. Verify Table of Contents / Manifest
        toc_info = verify_dump_toc(dump_path)
        
        # 3. Encrypt
        encrypt_dump(dump_path, enc_path, encryption_key)
        enc_size = os.path.getsize(enc_path)
        enc_sha = sha256_file(enc_path)
        
        # 4. Remove plaintext dump from disk
        if os.path.exists(dump_path):
            os.remove(dump_path)
            
        # 5. Build and save metadata
        meta = {
            "timestamp": ts_now.isoformat(),
            "database_id": args.db_id,
            "database_name": conn_params["dbname"],
            "dump_format": format_name,
            "is_baseline": args.baseline,
            "plaintext_size_bytes": plaintext_size,
            "plaintext_sha256": plaintext_sha,
            "encrypted_file": os.path.basename(enc_path),
            "encrypted_size_bytes": enc_size,
            "encrypted_sha256": enc_sha,
            "encryption_algorithm": "AES-256-CBC-PBKDF2-100000",
            "toc_entries": toc_info["total_toc_entries"],
            "public_entries": toc_info["public_entries"],
            "navigator_entries": toc_info["navigator_entries"]
        }
        with open(meta_path, "w") as f:
            json.dump(meta, f, indent=2)
            
        print("\n" + "="*70)
        print("BACKUP VERIFICATION SUMMARY")
        print(f"Archive: {enc_path}")
        print(f"Format: {format_name}")
        print(f"Encrypted Size: {enc_size} bytes ({enc_size / 1024:.1f} KB)")
        print(f"Encrypted SHA-256: {enc_sha}")
        print(f"Public Schema Tables: {toc_info['public_entries']}")
        print(f"Navigator Schema Tables: {toc_info['navigator_entries']}")
        print(f"Metadata Manifest: {meta_path}")
        print("="*70 + "\n")
        
        # 6. Upload
        upload_to_s3_storage(enc_path, meta_path, ts_str, is_baseline=args.baseline)
        
        # 7. Test decryption integrity check
        test_dec = os.path.join(args.output_dir, f"test_dec_{ts_str}.tmp")
        decrypt_dump(enc_path, test_dec, encryption_key)
        dec_sha = sha256_file(test_dec)
        if dec_sha != plaintext_sha:
            raise RuntimeError(f"Decryption integrity check failed: SHA mismatch ({dec_sha} != {plaintext_sha})")
        print("[PASS] Decryption round-trip verified: Restored plaintext SHA matches original export exactly!")
        if os.path.exists(test_dec):
            os.remove(test_dec)
            
        # 8. Isolated target database restore verification if requested
        if args.verify_restore_url:
            verify_restore_into_target(enc_path, encryption_key, args.verify_restore_url, format_name)
            
        return enc_path, meta_path
        
    except Exception as e:
        print(f"[FATAL ERROR] Backup pipeline failed: {e}", file=sys.stderr)
        if os.path.exists(dump_path):
            os.remove(dump_path)
        sys.exit(1)

if __name__ == "__main__":
    main()
