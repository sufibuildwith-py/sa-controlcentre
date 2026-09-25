import * as SQLite from "expo-sqlite";
import type { MobileHome } from "../api/navigatorApi";

let database: Promise<SQLite.SQLiteDatabase> | undefined;
async function db() {
  database ??= SQLite.openDatabaseAsync("sa-employee.db");
  const value = await database;
  await value.execAsync(`
    CREATE TABLE IF NOT EXISTS app_cache(key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS sync_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
  `);
  return value;
}

export async function initializeCache() { await db(); }
export async function readHomeCache() {
  const row = await (await db()).getFirstAsync<{ value: string }>("SELECT value FROM app_cache WHERE key='home'");
  if (!row) return undefined;
  try { return JSON.parse(row.value) as MobileHome; } catch { return undefined; }
}
export async function writeHomeCache(home: MobileHome) {
  await (await db()).runAsync("INSERT OR REPLACE INTO app_cache(key,value,updated_at) VALUES('home',?,?)", JSON.stringify(home), new Date().toISOString());
  await setMetadata("last_sync", new Date().toISOString());
}
export async function getMetadata(key: string) {
  return (await (await db()).getFirstAsync<{ value: string }>("SELECT value FROM sync_metadata WHERE key=?", key))?.value;
}
export async function setMetadata(key: string, value: string) {
  await (await db()).runAsync("INSERT OR REPLACE INTO sync_metadata(key,value) VALUES(?,?)", key, value);
}
export async function clearEmployeeCache() { await (await db()).runAsync("DELETE FROM app_cache"); }
