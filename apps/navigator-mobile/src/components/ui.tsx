import type { PropsWithChildren, ReactNode } from "react";
import {
  ActivityIndicator,
  Animated,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type PressableProps,
} from "react-native";
import { colors, radius, space } from "../theme/tokens";
import type { ConnectionState } from "../network/connection";

export function Page({
  children,
  scroll = true,
  refreshControl,
}: {
  children: ReactNode;
  scroll?: boolean;
  refreshControl?: ReactNode;
}) {
  const content = (
    <View style={styles.page}>
      {children}
      <View style={{ height: 110 }} />
    </View>
  );

  return (
    <SafeAreaView style={styles.safe}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={styles.scroll}
          showsVerticalScrollIndicator={false}
          refreshControl={refreshControl as never}
        >
          {content}
        </ScrollView>
      ) : (
        content
      )}
    </SafeAreaView>
  );
}

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <View style={styles.brandRow}>
      <View style={styles.mark}>
        <Text style={styles.markText}>S</Text>
        <View style={styles.markDot} />
      </View>
      <View>
        <Text style={styles.company}>SA PRODUCTIONS</Text>
        {!compact && <Text style={styles.brand}>Navigator</Text>}
      </View>
    </View>
  );
}

export function SectionLabel({ children }: PropsWithChildren) {
  return <Text style={styles.eyebrow}>{children}</Text>;
}

export function Card({
  children,
  emphasis = false,
  highlight = false,
}: PropsWithChildren<{ emphasis?: boolean; highlight?: boolean }>) {
  return (
    <View
      style={[
        styles.card,
        emphasis && styles.cardEmphasis,
        highlight && styles.cardHighlight,
      ]}
    >
      {children}
    </View>
  );
}

export function LivePulse({ active = true, tone = "success" }: { active?: boolean; tone?: "success" | "warning" | "danger" | "info" }) {
  const colorMap = {
    success: colors.success,
    warning: colors.warning,
    danger: colors.danger,
    info: colors.info,
  };
  const color = colorMap[tone];

  return (
    <View style={styles.pulseContainer}>
      <View style={[styles.pulseRing, { borderColor: color, opacity: active ? 0.35 : 0.15 }]} />
      <View style={[styles.pulseDot, { backgroundColor: color }]} />
    </View>
  );
}

export function Badge({
  label,
  tone = "neutral",
}: {
  label: string;
  tone?: "neutral" | "success" | "warning" | "danger" | "info";
}) {
  const bg = {
    neutral: colors.surfaceSoft,
    success: colors.successSoft,
    warning: colors.warningSoft,
    danger: colors.dangerSoft,
    info: colors.infoSoft,
  }[tone];

  const border = {
    neutral: colors.borderSoft,
    success: colors.successBorder,
    warning: colors.warningBorder,
    danger: colors.dangerBorder,
    info: colors.infoBorder,
  }[tone];

  const text = {
    neutral: colors.textSecondary,
    success: colors.success,
    warning: colors.warning,
    danger: colors.danger,
    info: colors.info,
  }[tone];

  return (
    <View style={[styles.badge, { backgroundColor: bg, borderColor: border }]}>
      <Text style={[styles.badgeText, { color: text }]}>{label}</Text>
    </View>
  );
}

export function ActionButton({
  children,
  variant = "primary",
  busy,
  ...props
}: PropsWithChildren<
  PressableProps & {
    variant?: "primary" | "danger" | "secondary" | "ghost";
    busy?: boolean;
  }
>) {
  const isDanger = variant === "danger";
  const isSecondary = variant === "secondary";
  const isGhost = variant === "ghost";

  const buttonStyle = [
    styles.button,
    isDanger && styles.buttonDanger,
    isSecondary && styles.buttonSecondary,
    isGhost && styles.buttonGhost,
    (props.disabled || busy) && styles.disabled,
  ];

  const textStyle = [
    styles.buttonText,
    isSecondary && styles.buttonTextSecondary,
    isGhost && styles.buttonTextGhost,
  ];

  const spinnerColor = isSecondary || isGhost ? colors.text : colors.background;

  return (
    <Pressable
      accessibilityRole="button"
      {...props}
      disabled={props.disabled || busy}
      style={({ pressed }) => [...buttonStyle, pressed && styles.pressed]}
    >
      {busy ? (
        <ActivityIndicator color={spinnerColor} size="small" />
      ) : (
        <Text style={textStyle}>{children}</Text>
      )}
    </Pressable>
  );
}

export function Row({
  title,
  detail,
  action,
}: {
  title: string;
  detail?: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.row}>
      <View style={{ flex: 1, gap: 3 }}>
        <Text style={styles.rowTitle}>{title}</Text>
        {detail ? <Text style={styles.meta}>{detail}</Text> : null}
      </View>
      {action}
    </View>
  );
}

export function MetricTile({
  label,
  value,
  subvalue,
}: {
  label: string;
  value: string;
  subvalue?: string;
}) {
  return (
    <View style={styles.metricTile}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>{value}</Text>
      {subvalue ? <Text style={styles.metricSub}>{subvalue}</Text> : null}
    </View>
  );
}

export function EmptyState({
  title,
  body,
  glyph = "○",
}: {
  title: string;
  body: string;
  glyph?: string;
}) {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyGlyph}>{glyph}</Text>
      <Text style={styles.rowTitle}>{title}</Text>
      <Text style={styles.body}>{body}</Text>
    </View>
  );
}

export function ConnectionBanner({ state }: { state: ConnectionState }) {
  if (state === "ONLINE") return null;

  const text = {
    CONNECTING: "Connecting to SA Productions…",
    DEGRADED: "Connection is unstable · local queue is active",
    OFFLINE: "Offline mode · locations will sync when network returns",
    AUTH_REQUIRED: "Device session expired · please pair again",
    SERVICE_UNAVAILABLE: "Production gateway is temporarily unreachable",
  }[state];

  const isOffline = state === "OFFLINE";
  const isAuth = state === "AUTH_REQUIRED";

  return (
    <View
      style={[
        styles.banner,
        isOffline && styles.bannerOffline,
        isAuth && styles.bannerDanger,
      ]}
    >
      <View style={styles.bannerDot} />
      <Text style={styles.bannerText}>{text}</Text>
    </View>
  );
}

export function Skeleton() {
  return (
    <View style={styles.skeleton}>
      <View style={[styles.skeletonLine, { width: "42%" }]} />
      <View style={[styles.skeletonLine, { width: "88%" }]} />
      <View style={[styles.skeletonLine, { width: "65%" }]} />
    </View>
  );
}

export const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scroll: {
    flexGrow: 1,
  },
  page: {
    paddingHorizontal: 20,
    paddingTop: 16,
    gap: 16,
  },
  brandRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  mark: {
    width: 40,
    height: 40,
    borderRadius: 14,
    backgroundColor: colors.surfaceRaised,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
  },
  markText: {
    fontSize: 20,
    fontWeight: "900",
    fontStyle: "italic",
    color: colors.text,
  },
  markDot: {
    position: "absolute",
    bottom: 6,
    right: 6,
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: colors.accent,
  },
  company: {
    fontSize: 10,
    letterSpacing: 2,
    fontWeight: "800",
    color: colors.textTertiary,
  },
  brand: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.text,
    letterSpacing: 0.3,
    marginTop: 1,
  },
  greeting: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: "700",
    color: colors.text,
    letterSpacing: -0.4,
  },
  date: {
    fontSize: 13,
    color: colors.textTertiary,
    marginTop: 4,
    letterSpacing: 0.2,
  },
  eyebrow: {
    fontSize: 10,
    letterSpacing: 1.8,
    fontWeight: "800",
    color: colors.textTertiary,
    textTransform: "uppercase",
    marginTop: 4,
  },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    borderRadius: radius.card,
    padding: 20,
    gap: 14,
  },
  cardEmphasis: {
    backgroundColor: colors.surfaceRaised,
    borderColor: colors.border,
  },
  cardHighlight: {
    backgroundColor: colors.surfaceHighlight,
    borderColor: colors.borderStrong,
  },
  title: {
    fontSize: 22,
    lineHeight: 28,
    fontWeight: "700",
    color: colors.text,
    letterSpacing: -0.3,
  },
  body: {
    fontSize: 14,
    lineHeight: 21,
    color: colors.textSecondary,
  },
  meta: {
    fontSize: 12,
    lineHeight: 16,
    color: colors.textTertiary,
  },
  badge: {
    alignSelf: "flex-start",
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingVertical: 5,
    paddingHorizontal: 10,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: "800",
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  button: {
    minHeight: 52,
    borderRadius: radius.button,
    backgroundColor: colors.text,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20,
  },
  buttonDanger: {
    backgroundColor: colors.danger,
  },
  buttonSecondary: {
    backgroundColor: colors.surfaceSoft,
    borderWidth: 1,
    borderColor: colors.border,
  },
  buttonGhost: {
    backgroundColor: "transparent",
    borderWidth: 0,
  },
  buttonText: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.background,
    letterSpacing: 0.2,
  },
  buttonTextSecondary: {
    color: colors.text,
  },
  buttonTextGhost: {
    color: colors.textSecondary,
  },
  pressed: {
    transform: [{ scale: 0.985 }],
    opacity: 0.88,
  },
  disabled: {
    opacity: 0.4,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 4,
  },
  rowTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.text,
  },
  empty: {
    alignItems: "center",
    paddingVertical: 24,
    gap: 8,
  },
  emptyGlyph: {
    fontSize: 26,
    color: colors.textTertiary,
    marginBottom: 4,
  },
  banner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: radius.sm,
    backgroundColor: colors.warningSoft,
    borderWidth: 1,
    borderColor: colors.warningBorder,
    paddingVertical: 9,
    paddingHorizontal: 12,
  },
  bannerOffline: {
    backgroundColor: "rgba(100, 110, 125, 0.16)",
    borderColor: "rgba(100, 110, 125, 0.3)",
  },
  bannerDanger: {
    backgroundColor: colors.dangerSoft,
    borderColor: colors.dangerBorder,
  },
  bannerDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.warning,
  },
  bannerText: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.textSecondary,
    flex: 1,
  },
  pulseContainer: {
    width: 18,
    height: 18,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
  },
  pulseRing: {
    position: "absolute",
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
  },
  pulseDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  metricTile: {
    flex: 1,
    backgroundColor: colors.surfaceSoft,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.borderSubtle,
    padding: 12,
    gap: 4,
  },
  metricLabel: {
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 0.8,
    textTransform: "uppercase",
    color: colors.textTertiary,
  },
  metricValue: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.text,
  },
  metricSub: {
    fontSize: 11,
    color: colors.textTertiary,
  },
  skeleton: {
    gap: 10,
  },
  skeletonLine: {
    height: 14,
    borderRadius: 7,
    backgroundColor: colors.surfaceSoft,
  },
});
