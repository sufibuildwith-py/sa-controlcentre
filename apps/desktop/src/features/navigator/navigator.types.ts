export const navigatorEnabled =
  import.meta.env.VITE_NAVIGATOR_ENABLED === "true";
export type NavigatorState =
  "LIVE" | "STALE" | "OFFLINE" | "OFF_DUTY" | "UNPAIRED";
export type NavigatorItem = {
  employeeRef: string;
  deviceId: string;
  sessionId?: string;
  state: NavigatorState;
  latitude?: number;
  longitude?: number;
  accuracyMeters?: number;
  recordedAt?: string;
  receivedAt?: string;
  trackingStartedAt?: string;
  pairedAt?: string;
  deviceLabel?: string;
  employeeName?: string;
  roleTitle?: string;
  profilePhotoUrl?: string;
  productionTitle?: string;
};
export type NavigatorSnapshot = {
  items: NavigatorItem[];
  syncedAt: string;
  organizationPublicId: string;
};
export type NavigatorEvent = {
  type:
    | "LOCATION_UPDATED"
    | "TRACKING_STARTED"
    | "TRACKING_STOPPED"
    | "DEVICE_OFFLINE"
    | "DEVICE_REVOKED";
  employeeRef: string;
  deviceId: string;
  sessionId?: string;
  latitude?: number;
  longitude?: number;
  accuracyMeters?: number;
  recordedAt?: string;
  receivedAt?: string;
};
export const stateFor = (
  active: boolean,
  paired: boolean,
  recordedAt?: string,
  now = Date.now(),
): NavigatorState => {
  if (!paired) return "UNPAIRED";
  if (!active) return "OFF_DUTY";
  if (!recordedAt) return "OFFLINE";
  const age = now - new Date(recordedAt).getTime();
  return age <= 60_000 ? "LIVE" : age <= 300_000 ? "STALE" : "OFFLINE";
};
export const ageLabel = (value?: string) => {
  if (!value) return "No updates";
  const seconds = Math.max(
    0,
    Math.round((Date.now() - new Date(value).getTime()) / 1000),
  );
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  return minutes < 60 ? `${minutes}m ago` : `${Math.round(minutes / 60)}h ago`;
};
