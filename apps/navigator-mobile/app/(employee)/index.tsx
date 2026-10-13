import { router } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import {
  flushPendingDutyEnd,
  flushQueue,
  navigatorApi,
} from "../../src/api/navigatorApi";
import {
  ActionButton,
  Badge,
  BrandMark,
  Card,
  ConnectionBanner,
  EmptyState,
  LivePulse,
  MetricTile,
  Page,
  Row,
  SectionLabel,
  Skeleton,
  styles as commonStyles,
} from "../../src/components/ui";
import {
  dutyLabel,
  locationHealth,
  type DutyState,
  type LocationHealth,
} from "../../src/duty/dutyState";
import {
  captureImmediateLocation,
  isTracking,
  lastLocalLocation,
  locationServicesEnabled,
  requestBackground,
  requestForeground,
  startTracking,
  stopTracking,
} from "../../src/location/tracking";
import { useConnection } from "../../src/network/connection";
import { pendingPointCount, savePendingDutyEnd } from "../../src/queue/locationQueue";
import { useEmployee } from "../../src/state/employeeContext";
import { activeSession } from "../../src/storage/credentials";
import { ApiError } from "../../src/api/client";
import { colors, radius, space } from "../../src/theme/tokens";

const formatClock = (value?: string) =>
  value
    ? new Date(value).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
    : undefined;

const formatRelative = (value?: string) => {
  if (!value) return "No update yet";
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 5) return "Just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  return `${Math.round(minutes / 60)}h ago`;
};

const getGreeting = () => {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
};

export default function Home() {
  const { home, boot, refresh } = useEmployee();
  const { state: connection } = useConnection();

  const [duty, setDuty] = useState<DutyState>(
    home?.dutyActive ? "ON_DUTY_DEGRADED" : "OFF_DUTY"
  );
  const [health, setHealth] = useState<LocationHealth>("TASK_STOPPED");
  const [pending, setPending] = useState(0);
  const [note, setNote] = useState<string | undefined>();
  const [refreshing, setRefreshing] = useState(false);
  const [localLastPoint, setLocalLastPoint] = useState<string | undefined>();

  const inspect = useCallback(async () => {
    const running = await isTracking();
    const localLast = await lastLocalLocation();
    setLocalLastPoint(localLast);
    const count = await pendingPointCount();
    setPending(count);

    const active = Boolean(home?.dutyActive);
    const isOnline = connection === "ONLINE";
    const nextHealth = locationHealth(
      active,
      running,
      localLast ?? home?.serverLastLocationAt,
      isOnline
    );
    setHealth(nextHealth);

    if (active && duty !== "STARTING" && duty !== "ENDING") {
      setDuty(nextHealth === "LIVE" ? "ON_DUTY_TRACKING" : "ON_DUTY_DEGRADED");
    }
    if (!active && duty !== "ENDING") {
      setDuty("OFF_DUTY");
    }
  }, [home?.dutyActive, home?.serverLastLocationAt, connection, duty]);

  useEffect(() => {
    void flushPendingDutyEnd()
      .then((changed) => {
        if (changed) void refresh();
      })
      .catch(() => undefined);

    void inspect();
    const timer = setInterval(() => void inspect(), 5000);
    return () => clearInterval(timer);
  }, [inspect, refresh]);

  async function reload() {
    setRefreshing(true);
    await refresh();
    await flushQueue().catch(() => undefined);
    await inspect();
    setRefreshing(false);
  }

  async function startDuty() {
    setNote(undefined);
    setDuty("STARTING");
    try {
      const fgGranted = await requestForeground();
      if (!fgGranted) {
        setHealth("PERMISSION_BLOCKED");
        setDuty("ERROR");
        setNote("Location permission is required to start your duty shift.");
        return;
      }

      const servicesOn = await locationServicesEnabled();
      if (!servicesOn) {
        setHealth("SERVICE_DISABLED");
        setDuty("ERROR");
        setNote("Device location is turned off. Please turn on GPS and retry.");
        return;
      }

      const session = await navigatorApi.start();
      await activeSession.set(session.sessionId);

      const bgGranted = await requestBackground();
      if (!bgGranted) {
        setHealth("PERMISSION_BLOCKED");
        setDuty("ON_DUTY_DEGRADED");
        setNote(
          "Duty started, but background location is limited. Enable 'Allow all the time' in settings to track while phone is locked."
        );
      }

      await startTracking(session.sessionId);
      await captureImmediateLocation(session.sessionId);

      setDuty("ACQUIRING_LOCATION");
      setHealth("ACQUIRING");
      await refresh();
      await inspect();
    } catch (cause) {
      if (__DEV__) {
        console.warn("[SA Employee duty] start error", cause);
      }
      setDuty("ERROR");
      setHealth("ERROR");
      const message = cause instanceof Error ? cause.message : "";
      setNote(
        cause instanceof ApiError && cause.code === "NETWORK_UNAVAILABLE"
          ? "Unable to reach SA Productions. Check your connection."
          : cause instanceof ApiError && cause.code === "RATE_LIMITED"
          ? "Too many requests. Please wait a moment before trying again."
          : cause instanceof ApiError
          ? "Server could not start duty shift. Please retry."
          : message === "LOCATION_SERVICES_DISABLED"
          ? "Turn on device location and try again."
          : message.startsWith("LOCATION_TRACKING") ||
            message === "LOCATION_TASK_NOT_STARTED"
          ? "Location service could not be initiated on this device."
          : "Duty shift could not start. Please retry."
      );
    }
  }

  async function endDuty() {
    setDuty("ENDING");
    setNote(undefined);
    const sessionId = (await activeSession.get()) ?? home?.sessionId;
    await stopTracking();

    if (!sessionId) {
      setDuty("OFF_DUTY");
      await refresh();
      return;
    }

    const endedAt = new Date().toISOString();
    try {
      await flushQueue();
      await navigatorApi.end(sessionId, endedAt);
      setDuty("OFF_DUTY");
    } catch {
      await savePendingDutyEnd(sessionId, endedAt);
      setDuty("END_PENDING_SYNC");
      setNote("Tracking stopped. Duty closure recorded and will sync once online.");
    }
    await refresh();
    await inspect();
  }

  async function restartLocation() {
    const id = (await activeSession.get()) ?? home?.sessionId;
    if (!id) return;
    setDuty("STARTING");
    setNote(undefined);
    try {
      if (!(await requestForeground()) || !(await requestBackground())) {
        setHealth("PERMISSION_BLOCKED");
        setDuty("ON_DUTY_DEGRADED");
        return;
      }
      await startTracking(id);
      await captureImmediateLocation(id);
      setDuty("ACQUIRING_LOCATION");
      setHealth("ACQUIRING");
      await inspect();
    } catch {
      setHealth("ERROR");
      setDuty("ON_DUTY_DEGRADED");
      setNote("Location service could not restart.");
    }
  }

  const active =
    Boolean(home?.dutyActive) && duty !== "OFF_DUTY" && duty !== "END_PENDING_SYNC";

  const dutyTone: "success" | "warning" | "neutral" | "info" =
    health === "LIVE" ? "success" : health === "ACQUIRING" ? "info" : active ? "warning" : "neutral";

  const statusHeadline = active
    ? health === "LIVE"
      ? "Sharing Location"
      : health === "QUEUING_OFFLINE"
      ? "Offline · Queuing Updates"
      : health === "ACQUIRING"
      ? "Acquiring GPS Signal"
      : "Duty Session Active"
    : duty === "END_PENDING_SYNC"
    ? "Shift Ended · Sync Pending"
    : "Off Duty";

  const statusExplanation =
    health === "LIVE"
      ? `Live GPS updates active · Last ack ${formatRelative(
          home?.serverLastLocationAt ?? localLastPoint
        )}`
      : health === "QUEUING_OFFLINE"
      ? `No internet connection · ${pending} location update${pending === 1 ? "" : "s"} safely queued`
      : health === "ACQUIRING"
      ? "Establishing accurate satellite fix…"
      : health === "PERMISSION_BLOCKED"
      ? "Location permission restricted on this device"
      : health === "SERVICE_DISABLED"
      ? "Device location is turned off in Android settings"
      : health === "TASK_STOPPED" && active
      ? "Location background task interrupted · tap restart"
      : "Location is not being shared. Ready when you are.";

  const initials =
    home?.displayName
      ?.split(/\s+/)
      .map((x) => x[0])
      .slice(0, 2)
      .join("") ?? "SA";

  return (
    <Page
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={reload}
          tintColor={colors.text}
        />
      }
    >
      <ConnectionBanner state={connection} />

      {/* Top Header */}
      <View style={localStyles.topBar}>
        <BrandMark compact />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Open device settings"
          onPress={() => router.push("/(employee)/device")}
          style={localStyles.avatarBtn}
        >
          <Text style={localStyles.avatarText}>{initials}</Text>
        </Pressable>
      </View>

      {/* Greeting & Date */}
      <View style={localStyles.greetingBlock}>
        <Text style={commonStyles.greeting}>
          {getGreeting()}, {home?.displayName?.split(" ")[0] ?? "Crew"}
        </Text>
        <Text style={commonStyles.date}>
          {new Date().toLocaleDateString([], {
            weekday: "long",
            day: "numeric",
            month: "long",
          })}
        </Text>
      </View>

      {!home && boot === "READY" ? (
        <Card>
          <Skeleton />
        </Card>
      ) : (
        <>
          {/* Primary Tactical Duty Card */}
          <Card emphasis highlight={active && health === "LIVE"}>
            <View style={localStyles.dutyHeader}>
              <View style={localStyles.dutyStatusRow}>
                <LivePulse
                  active={active}
                  tone={health === "LIVE" ? "success" : active ? "warning" : "info"}
                />
                <Text style={localStyles.dutyHeadline}>{statusHeadline}</Text>
              </View>
              <Badge label={dutyLabel(duty)} tone={dutyTone} />
            </View>

            <Text style={commonStyles.body}>{statusExplanation}</Text>

            {/* Metrics Row */}
            <View style={localStyles.metricRow}>
              <MetricTile
                label="Duty Shift"
                value={home?.startedAt && active ? formatClock(home.startedAt) ?? "Active" : "Off Duty"}
                subvalue={active ? "Session active" : "Shift idle"}
              />
              <MetricTile
                label="Server Ack"
                value={formatRelative(home?.serverLastLocationAt ?? localLastPoint)}
                subvalue={connection === "ONLINE" ? "Gateway connected" : "Offline buffer"}
              />
              {pending > 0 && (
                <MetricTile
                  label="Queued"
                  value={String(pending)}
                  subvalue="Awaiting sync"
                />
              )}
            </View>

            {note ? (
              <View style={localStyles.noteBox}>
                <Text style={localStyles.noteText}>{note}</Text>
              </View>
            ) : null}

            {/* Action Trigger */}
            {active &&
            ["TASK_STOPPED", "ERROR", "PERMISSION_BLOCKED", "SERVICE_DISABLED"].includes(
              health
            ) ? (
              <ActionButton onPress={restartLocation}>
                Restart Location Sharing
              </ActionButton>
            ) : (
              <ActionButton
                variant={active ? "danger" : "primary"}
                busy={duty === "STARTING" || duty === "ENDING"}
                onPress={active ? endDuty : startDuty}
              >
                {duty === "STARTING"
                  ? "Starting Duty…"
                  : duty === "ENDING"
                  ? "Ending Duty…"
                  : active
                  ? "End Duty Shift"
                  : "Start Duty Shift"}
              </ActionButton>
            )}
          </Card>

          {/* Today's Assignment Bento */}
          <SectionLabel>TODAY'S PRODUCTION</SectionLabel>
          <Card>
            {home?.productionTitle ? (
              <>
                <View style={localStyles.assignmentHeader}>
                  <Badge label="Active Assignment" tone="info" />
                  {home.locationName && (
                    <Text numberOfLines={1} style={commonStyles.meta}>
                      {home.locationName}
                    </Text>
                  )}
                </View>
                <Text style={commonStyles.title}>{home.productionTitle}</Text>
                <Row
                  title={
                    [formatClock(home.startsAt), formatClock(home.endsAt)]
                      .filter(Boolean)
                      .join(" – ") || "Scheduled Call Time"
                  }
                  detail="Operational shift hours"
                />
                <ActionButton
                  variant="secondary"
                  onPress={() => router.push("/(employee)/work")}
                >
                  View Full Assignment
                </ActionButton>
              </>
            ) : (
              <EmptyState
                title="No Production Scheduled Today"
                body="You have no active call sheet for today. Upcoming assignments will appear automatically."
              />
            )}
          </Card>

          {/* Operational Messages Preview */}
          <SectionLabel>NOTICES & MESSAGES</SectionLabel>
          <Card>
            {home?.messages?.length ? (
              <>
                {home.messages.slice(0, 2).map((msg) => (
                  <View key={msg.id} style={localStyles.messageItem}>
                    <View style={localStyles.messageTitleRow}>
                      <Text style={commonStyles.rowTitle}>{msg.title}</Text>
                      {!msg.read && <View style={localStyles.unreadDot} />}
                    </View>
                    <Text numberOfLines={2} style={commonStyles.body}>
                      {msg.body}
                    </Text>
                    <Text style={commonStyles.meta}>{formatClock(msg.sentAt)}</Text>
                  </View>
                ))}
                <ActionButton
                  variant="secondary"
                  onPress={() => router.push("/(employee)/messages")}
                >
                  View All Messages ({home.messages.length})
                </ActionButton>
              </>
            ) : (
              <EmptyState
                title="No Messages"
                body="All operational communications are up to date."
              />
            )}
          </Card>
        </>
      )}
    </Page>
  );
}

const localStyles = StyleSheet.create({
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  avatarBtn: {
    width: 42,
    height: 42,
    borderRadius: 14,
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: "800",
  },
  greetingBlock: {
    gap: 2,
    marginTop: 4,
  },
  dutyHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  dutyStatusRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    flex: 1,
  },
  dutyHeadline: {
    fontSize: 19,
    fontWeight: "700",
    color: colors.text,
    letterSpacing: -0.2,
  },
  metricRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
  noteBox: {
    backgroundColor: colors.warningSoft,
    borderWidth: 1,
    borderColor: colors.warningBorder,
    borderRadius: radius.xs,
    paddingVertical: 9,
    paddingHorizontal: 12,
  },
  noteText: {
    fontSize: 13,
    lineHeight: 18,
    color: colors.warning,
    fontWeight: "500",
  },
  assignmentHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  messageItem: {
    gap: 4,
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderSubtle,
  },
  messageTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  unreadDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.accent,
  },
});
