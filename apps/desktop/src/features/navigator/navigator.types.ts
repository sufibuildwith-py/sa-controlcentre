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
  productionId?: string;
  productionTitle?: string;
  teamName?: string;
  productionRole?: string;
};
export type TeamMember = {
  employeeId: string;
  employeeName: string;
  productionRole?: string;
  assignmentStatus?: string;
};
export type ProductionTeam = {
  productionId: string;
  productionTitle: string;
  teamName: string;
  memberCount: number;
  members: TeamMember[];
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

export const VARANASI_HQ: [number, number] = [82.9739, 25.3176];

export const isValidCoordinate = (lat?: number, lng?: number): boolean =>
  typeof lat === "number" &&
  typeof lng === "number" &&
  !isNaN(lat) &&
  !isNaN(lng) &&
  lat >= -90 &&
  lat <= 90 &&
  lng >= -180 &&
  lng <= 180 &&
  !(lat === 0 && lng === 0);

export const resolveInitialMapCenter = (
  items: NavigatorItem[],
): [number, number] => {
  const valid = items.filter((i) => isValidCoordinate(i.latitude, i.longitude));
  if (!valid.length) return VARANASI_HQ;

  const timestampOf = (i: NavigatorItem): number => {
    const raw = i.recordedAt || i.receivedAt;
    if (!raw) return 0;
    const t = new Date(raw).getTime();
    return isNaN(t) ? 0 : t;
  };

  // Priority 1: Latest valid location of a LIVE employee
  const live = valid.filter((i) => i.state === "LIVE");
  if (live.length > 0) {
    live.sort((a, b) => timestampOf(b) - timestampOf(a));
    const bestLive = live[0];
    return [bestLive.longitude!, bestLive.latitude!];
  }

  // Priority 2: Latest valid location among all employees (historical)
  valid.sort((a, b) => timestampOf(b) - timestampOf(a));
  const bestHistorical = valid[0];
  return [bestHistorical.longitude!, bestHistorical.latitude!];
};

