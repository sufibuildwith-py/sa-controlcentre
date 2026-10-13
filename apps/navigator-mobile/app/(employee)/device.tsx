import Constants from "expo-constants";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import {
  Alert,
  Linking,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import {
  ActionButton,
  Badge,
  BrandMark,
  Card,
  Page,
  Row,
  SectionLabel,
  styles as commonStyles,
} from "../../src/components/ui";
import { gatewayUrl } from "../../src/config";
import { isTracking, lastLocalLocation } from "../../src/location/tracking";
import { useConnection } from "../../src/network/connection";
import { pendingPointCount } from "../../src/queue/locationQueue";
import { useEmployee } from "../../src/state/employeeContext";
import { colors, radius, space } from "../../src/theme/tokens";

export default function Device() {
  const { home, lastSync, refresh, unpair } = useEmployee();
  const { state: connection } = useConnection();
  const [pending, setPending] = useState(0);
  const [taskRunning, setTaskRunning] = useState(false);
  const [lastLocation, setLastLocation] = useState<string | undefined>();
  const [syncing, setSyncing] = useState(false);

  async function inspect() {
    setPending(await pendingPointCount());
    setTaskRunning(await isTracking());
    setLastLocation(await lastLocalLocation());
  }

  useEffect(() => {
    void inspect();
  }, []);

  async function handleRetrySync() {
    setSyncing(true);
    await refresh();
    await inspect();
    setSyncing(false);
  }

  function confirmUnpair() {
    Alert.alert(
      "Unpair Phone",
      "This will remove the encrypted device credential from Android Keystore. Any active duty session should be ended before unpairing.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Unpair",
          style: "destructive",
          onPress: async () => {
            await unpair();
            router.replace("/pair");
          },
        },
      ]
    );
  }

  const gatewayHost = gatewayUrl.replace(/^https?:\/\//, "");

  return (
    <Page>
      {/* Header with back navigation */}
      <View style={localStyles.navRow}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back to Home"
          onPress={() => router.back()}
          style={localStyles.backButton}
        >
          <Text style={localStyles.backText}>‹</Text>
        </Pressable>
        <BrandMark compact />
      </View>

      <View style={localStyles.titleBlock}>
        <Text style={commonStyles.greeting}>Device & System</Text>
        <Text style={commonStyles.date}>
          Connection diagnostics, location telemetry, and hardware credentials.
        </Text>
      </View>

      {/* Employee & Duty Status */}
      <SectionLabel>EMPLOYEE & ROLE</SectionLabel>
      <Card>
        <Row
          title={home?.displayName ?? "Crew Member"}
          detail="SA Productions Operations"
          action={
            <Badge
              label={home?.dutyActive ? "On Duty" : "Off Duty"}
              tone={home?.dutyActive ? "success" : "neutral"}
            />
          }
        />
        <Row
          title="Session State"
          detail={home?.dutyActive ? "Active Live Session" : "Standby (No tracking)"}
        />
      </Card>

      {/* Connection & Telemetry Diagnostics */}
      <SectionLabel>PRODUCTION GATEWAY & SYNC</SectionLabel>
      <Card emphasis>
        <Row
          title={gatewayHost}
          detail="Production Gateway"
          action={
            <Badge
              label={connection === "ONLINE" ? "Connected" : connection}
              tone={connection === "ONLINE" ? "success" : "warning"}
            />
          }
        />
        <Row
          title={taskRunning ? "Active Foreground Service" : "Service Idle"}
          detail="Background Location Task"
          action={
            <Badge
              label={taskRunning ? "Running" : "Stopped"}
              tone={taskRunning ? "success" : "neutral"}
            />
          }
        />
        <Row
          title={String(pending)}
          detail="Offline Location Queue"
          action={
            <Badge
              label={pending === 0 ? "Synced" : `${pending} buffered`}
              tone={pending === 0 ? "success" : "warning"}
            />
          }
        />
        <Row
          title={
            lastLocation
              ? new Date(lastLocation).toLocaleTimeString([], {
                  hour: "numeric",
                  minute: "2-digit",
                  second: "2-digit",
                })
              : "No location captured yet"
          }
          detail="Last Local GPS Fix"
        />
        <Row
          title={
            lastSync
              ? new Date(lastSync).toLocaleTimeString([], {
                  hour: "numeric",
                  minute: "2-digit",
                  second: "2-digit",
                })
              : "Not synced yet"
          }
          detail="Last Gateway Sync"
        />

        <ActionButton
          variant="secondary"
          busy={syncing}
          onPress={handleRetrySync}
        >
          {syncing ? "Checking Connection…" : "Test Connection & Sync"}
        </ActionButton>
      </Card>

      {/* App & System Settings */}
      <SectionLabel>HARDWARE & APPLICATION</SectionLabel>
      <Card>
        <Row
          title="SA Employee"
          detail={`Version ${Constants.expoConfig?.version ?? "2.0.0"} · Android Native`}
        />
        <Row
          title={`Android API ${String(Platform.Version)}`}
          detail="Operating System"
        />
        <Row
          title="Hardware Keystore"
          detail="Device token encrypted"
        />

        <ActionButton
          variant="secondary"
          onPress={() => Linking.openSettings()}
        >
          Open Android App Permissions
        </ActionButton>

        <ActionButton variant="danger" onPress={confirmUnpair}>
          Unpair Device
        </ActionButton>
      </Card>
    </Page>
  );
}

const localStyles = StyleSheet.create({
  navRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 14,
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
  },
  backText: {
    fontSize: 24,
    color: colors.text,
    lineHeight: 28,
  },
  titleBlock: {
    gap: 2,
    marginTop: 4,
  },
});
