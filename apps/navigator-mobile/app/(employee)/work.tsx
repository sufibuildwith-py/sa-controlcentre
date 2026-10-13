import { RefreshControl, StyleSheet, Text, View } from "react-native";
import { useState } from "react";
import {
  Badge,
  BrandMark,
  Card,
  EmptyState,
  MetricTile,
  Page,
  Row,
  SectionLabel,
  styles as commonStyles,
} from "../../src/components/ui";
import { useEmployee } from "../../src/state/employeeContext";
import { colors, radius, space } from "../../src/theme/tokens";

const formatDateTime = (value?: string) =>
  value
    ? new Date(value).toLocaleString([], {
        weekday: "short",
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      })
    : "Not scheduled";

const formatTimeOnly = (value?: string) =>
  value
    ? new Date(value).toLocaleTimeString([], {
        hour: "numeric",
        minute: "2-digit",
      })
    : "";

export default function Work() {
  const { home, refresh } = useEmployee();
  const [refreshing, setRefreshing] = useState(false);

  async function reload() {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
  }

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
      <BrandMark compact />

      <View style={localStyles.header}>
        <Text style={commonStyles.greeting}>Production Call</Text>
        <Text style={commonStyles.date}>
          Operational shift assignments, call times, and locations.
        </Text>
      </View>

      <SectionLabel>ACTIVE PRODUCTION ASSIGNMENT</SectionLabel>
      {home?.productionTitle ? (
        <Card emphasis>
          <View style={localStyles.badgeRow}>
            <Badge label="Scheduled Shift" tone="info" />
            {home.dutyActive && <Badge label="Live On Duty" tone="success" />}
          </View>

          <Text style={commonStyles.title}>{home.productionTitle}</Text>

          <View style={localStyles.metricRow}>
            <MetricTile
              label="Call Time"
              value={formatTimeOnly(home.startsAt) || "TBD"}
              subvalue={home.startsAt ? new Date(home.startsAt).toLocaleDateString([], { month: "short", day: "numeric" }) : undefined}
            />
            <MetricTile
              label="Wrap Time"
              value={formatTimeOnly(home.endsAt) || "Open"}
              subvalue={home.endsAt ? "Scheduled end" : "Wrap at discretion"}
            />
          </View>

          <Row
            title={home.locationName ?? "Location to be confirmed"}
            detail="Venue / Shoot Location"
          />

          <Row
            title={formatDateTime(home.startsAt)}
            detail="Shift Start Timestamp"
          />
        </Card>
      ) : (
        <Card>
          <EmptyState
            title="No Production Scheduled Today"
            body="Your upcoming call sheet and venue assignments will appear here once published in SA Command."
          />
        </Card>
      )}

      <SectionLabel>TASKS & DIRECTIVES</SectionLabel>
      <Card>
        <EmptyState
          title="No Directives Pending"
          body="Operational tasks and department instructions will be displayed when attached to this production."
        />
      </Card>

      <SectionLabel>OPERATIONAL NOTES</SectionLabel>
      <Card>
        <Text style={commonStyles.body}>
          Ensure your mobile phone maintains sufficient battery during production hours. Location tracking runs strictly while your duty shift is active.
        </Text>
      </Card>
    </Page>
  );
}

const localStyles = StyleSheet.create({
  header: {
    gap: 2,
    marginTop: 4,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
  },
  metricRow: {
    flexDirection: "row",
    gap: 8,
    marginVertical: 4,
  },
});
