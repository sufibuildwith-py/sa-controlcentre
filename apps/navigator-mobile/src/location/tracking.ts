import * as Location from "expo-location";
import * as TaskManager from "expo-task-manager";
import { LOCATION_TASK } from "../config";
import { enqueue } from "../queue/locationQueue";
import { flushQueue } from "../api/navigatorApi";
import { activeSession } from "../storage/credentials";
import { getMetadata, setMetadata } from "../storage/cache";
import { locationPolicy } from "./policy";

let sequence = 0;

TaskManager.defineTask(LOCATION_TASK, async ({ data, error }) => {
  const sessionId = await activeSession.get();
  if (error || !data || !sessionId) return;
  const locations = (data as { locations: Location.LocationObject[] }).locations;
  for (const point of locations) {
    sequence = Math.max(sequence + 1, Date.now());
    const recordedAt = new Date(point.timestamp).toISOString();
    await enqueue({
      sessionId,
      sequenceNo: sequence,
      latitude: point.coords.latitude,
      longitude: point.coords.longitude,
      accuracyMeters: point.coords.accuracy,
      recordedAt,
    });
    await setMetadata("last_local_location", recordedAt);
  }
  await flushQueue().catch(() => {});
});

export async function requestForeground(): Promise<boolean> {
  const { status } = await Location.requestForegroundPermissionsAsync();
  return status === "granted";
}

export async function requestBackground(): Promise<boolean> {
  const { status } = await Location.requestBackgroundPermissionsAsync();
  return status === "granted";
}

export async function checkForegroundPermission(): Promise<boolean> {
  const { status } = await Location.getForegroundPermissionsAsync();
  return status === "granted";
}

export async function checkBackgroundPermission(): Promise<boolean> {
  const { status } = await Location.getBackgroundPermissionsAsync();
  return status === "granted";
}

export async function locationServicesEnabled(): Promise<boolean> {
  return Location.hasServicesEnabledAsync();
}

export async function isTracking(): Promise<boolean> {
  return Location.hasStartedLocationUpdatesAsync(LOCATION_TASK).catch(() => false);
}

export async function lastLocalLocation(): Promise<string | undefined> {
  return getMetadata("last_local_location");
}

export async function captureImmediateLocation(sessionId: string): Promise<void> {
  try {
    const point = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });
    sequence = Math.max(sequence + 1, Date.now());
    const recordedAt = new Date(point.timestamp).toISOString();
    await enqueue({
      sessionId,
      sequenceNo: sequence,
      latitude: point.coords.latitude,
      longitude: point.coords.longitude,
      accuracyMeters: point.coords.accuracy,
      recordedAt,
    });
    await setMetadata("last_local_location", recordedAt);
    await flushQueue().catch(() => {});
  } catch (error) {
    if (__DEV__) {
      console.warn("[SA Employee] immediate location capture bypassed", error);
    }
  }
}

export async function startTracking(id: string): Promise<void> {
  await activeSession.set(id);
  sequence = Date.now();
  if (!(await locationServicesEnabled())) throw new Error("LOCATION_SERVICES_DISABLED");
  if (await isTracking()) return;

  try {
    await Location.startLocationUpdatesAsync(LOCATION_TASK, {
      accuracy: Location.Accuracy.High,
      timeInterval: locationPolicy.timeInterval,
      distanceInterval: locationPolicy.distanceInterval,
      deferredUpdatesInterval: locationPolicy.deferredUpdatesInterval,
      pausesUpdatesAutomatically: false,
      showsBackgroundLocationIndicator: true,
      foregroundService: {
        notificationTitle: "SA Productions · On Duty",
        notificationBody: "Live crew location sharing is active for your shift",
        notificationColor: "#D94B35",
        killServiceOnDestroy: false,
      },
    });
  } catch (cause) {
    if (__DEV__) {
      console.warn("[SA Employee duty] location task start failed", {
        name: cause instanceof Error ? cause.name : "Unknown",
        message: cause instanceof Error ? cause.message : "Unknown",
      });
    }
    throw new Error("LOCATION_TRACKING_START_FAILED");
  }

  if (!(await isTracking())) {
    throw new Error("LOCATION_TASK_NOT_STARTED");
  }
}

export async function stopTracking(): Promise<void> {
  if (await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK)) {
    await Location.stopLocationUpdatesAsync(LOCATION_TASK);
  }
  await activeSession.clear();
}
