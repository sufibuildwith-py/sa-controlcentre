import { beforeEach, describe, expect, it, vi } from "vitest";
import { transition } from "./state/mobileState";
import { MAX_BATCH, MAX_POINTS, oldestBatch, retryDelayMs } from "./queue/queuePolicy";
import { dutyLabel, locationHealth } from "./duty/dutyState";
import { PRODUCTION_GATEWAY_URL, PRODUCTION_WS_URL, gatewayUrl } from "./config";

vi.mock("expo-constants", () => ({
  default: {
    expoConfig: {
      extra: {
        gatewayUrl: "https://sa-navigator-gateway-production.up.railway.app",
      },
    },
  },
}));

const secure = new Map<string, string>();
vi.mock("expo-secure-store", () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: 1,
  getItemAsync: vi.fn((key: string) => Promise.resolve(secure.get(key) ?? null)),
  setItemAsync: vi.fn((key: string, value: string) => {
    secure.set(key, value);
    return Promise.resolve();
  }),
  deleteItemAsync: vi.fn((key: string) => {
    secure.delete(key);
    return Promise.resolve();
  }),
}));

describe("SA Navigator Mobile & Production Integration", () => {
  beforeEach(() => secure.clear());

  it("stores the device credential only through SecureStore", async () => {
    const { credentials } = await import("./storage/credentials");
    await credentials.set("secret");
    expect(await credentials.get()).toBe("secret");
  });

  it("clears a revoked device credential", async () => {
    const { credentials } = await import("./storage/credentials");
    await credentials.set("secret");
    await credentials.clear();
    expect(await credentials.get()).toBeNull();
  });

  it("points to the live production gateway by default", () => {
    expect(PRODUCTION_GATEWAY_URL).toBe("https://sa-navigator-gateway-production.up.railway.app");
    expect(PRODUCTION_WS_URL).toBe("wss://sa-navigator-gateway-production.up.railway.app/ws/navigator");
    expect(gatewayUrl).toBe(PRODUCTION_GATEWAY_URL);
    expect(gatewayUrl).not.toContain("demo");
    expect(gatewayUrl).not.toContain("localhost");
    expect(gatewayUrl).not.toContain("127.0.0.1");
  });

  it("requires explicit start after pairing", () => {
    expect(transition("UNPAIRED", "PAIRED")).toBe("PAIRED_IDLE");
  });

  it("passes through permission check before tracking", () => {
    expect(transition("PAIRED_IDLE", "START")).toBe("PERMISSION_CHECK");
  });

  it("stops active and offline sessions", () => {
    expect(transition("TRACKING_ACTIVE", "STOP")).toBe("PAIRED_IDLE");
    expect(transition("TRACKING_OFFLINE_QUEUE", "STOP")).toBe("PAIRED_IDLE");
  });

  it("moves active sharing into and out of offline queue", () => {
    expect(transition(transition("TRACKING_ACTIVE", "OFFLINE"), "ONLINE")).toBe("TRACKING_ACTIVE");
  });

  it("uses bounded exponential backoff", () => {
    expect(retryDelayMs(0)).toBe(5000);
    expect(retryDelayMs(20)).toBe(300000);
  });

  it("replays oldest valid points in batches of at most 25", () => {
    const now = Date.now();
    const points = Array.from({ length: 40 }, (_, i) => ({ id: i, createdAt: now - i * 1000 }));
    const batch = oldestBatch(points, now);
    expect(batch).toHaveLength(MAX_BATCH);
    expect(batch[0].id).toBe(39);
  });

  it("drops points older than the 24 hour policy", () => {
    expect(oldestBatch([{ createdAt: Date.now() - 25 * 60 * 60 * 1000 }])).toHaveLength(0);
  });

  it("documents the hard queue cap", () => {
    expect(MAX_POINTS).toBe(5000);
  });

  it("transitions any connected state to revoked", () => {
    expect(transition("TRACKING_ACTIVE", "REVOKE")).toBe("REVOKED");
  });

  it("does not report live before the location task starts", () => {
    expect(locationHealth(true, false, new Date().toISOString(), true)).toBe("TASK_STOPPED");
  });

  it("reports acquiring until the first usable point exists", () => {
    expect(locationHealth(true, true, undefined, true)).toBe("ACQUIRING");
  });

  it("reports live when active and recent location point exists", () => {
    const recent = new Date(Date.now() - 30_000).toISOString();
    expect(locationHealth(true, true, recent, true)).toBe("LIVE");
  });

  it("reports stale when active location point is older than 90 seconds", () => {
    const stale = new Date(Date.now() - 100_000).toISOString();
    expect(locationHealth(true, true, stale, true)).toBe("STALE");
  });

  it("reports queued offline tracking separately from duty", () => {
    expect(locationHealth(true, true, new Date().toISOString(), false)).toBe("QUEUING_OFFLINE");
  });

  it("uses honest duty labels", () => {
    expect(dutyLabel("OFF_DUTY")).toBe("Off duty");
    expect(dutyLabel("STARTING")).toBe("Starting duty");
    expect(dutyLabel("ACQUIRING_LOCATION")).toBe("Acquiring location");
    expect(dutyLabel("ON_DUTY_TRACKING")).toBe("On duty · Live");
    expect(dutyLabel("ON_DUTY_DEGRADED")).toBe("On duty · Attention");
    expect(dutyLabel("ENDING")).toBe("Ending duty");
    expect(dutyLabel("END_PENDING_SYNC")).toBe("Off duty · Sync pending");
    expect(dutyLabel("ERROR")).toBe("Duty unavailable");
  });
});
