import { api } from "../../lib/api";
import type { NavigatorSnapshot } from "./navigator.types";
export const navigatorApi = {
  live: () => api<NavigatorSnapshot>("/navigator/live"),
  pairing: (employeeId: string) =>
    api<{ id: string; code: string; expiresAt: string }>(
      `/navigator/employees/${employeeId}/pairing`,
      { method: "POST" },
    ),
  revoke: (deviceId: string) =>
    api<{ revoked: boolean }>(`/navigator/devices/${deviceId}/revoke`, {
      method: "POST",
    }),
  mobileMessage: (employeeId: string, title: string, body: string) =>
    api<{ id: string }>(`/navigator/employees/${employeeId}/mobile-message`, {
      method: "POST",
      body: JSON.stringify({ title, body }),
    }),
  ticket: () =>
    api<{ ticket: string; organizationPublicId: string; expiresAt: string }>(
      "/navigator/realtime-ticket",
      { method: "POST" },
    ),
  simulator: (action: "start" | "stop") =>
    api<{ running: boolean }>(`/navigator/simulator/${action}`, {
      method: "POST",
    }),
};
