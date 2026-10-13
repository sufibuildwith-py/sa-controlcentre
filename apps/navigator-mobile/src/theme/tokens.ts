export const colors = {
  background: "#111213",
  backgroundSecondary: "#161719",
  workspace: "#18191B",
  surface: "#1E2023",
  surfaceRaised: "#25272B",
  surfaceSoft: "#191A1D",
  surfaceHighlight: "#2D3035",

  text: "#F7F7F2",
  textSecondary: "#B8B8B1",
  textTertiary: "#76766E",
  textMuted: "#52524C",

  borderSubtle: "rgba(255, 255, 255, 0.04)",
  borderSoft: "rgba(255, 255, 255, 0.07)",
  border: "rgba(255, 255, 255, 0.11)",
  borderStrong: "rgba(255, 255, 255, 0.18)",
  borderFocus: "rgba(255, 255, 255, 0.35)",

  accent: "#D94B35",
  accentSoft: "rgba(217, 75, 53, 0.15)",
  accentGlow: "rgba(217, 75, 53, 0.30)",

  success: "#3FB950",
  successSoft: "rgba(63, 185, 80, 0.14)",
  successBorder: "rgba(63, 185, 80, 0.28)",

  warning: "#D29922",
  warningSoft: "rgba(210, 153, 34, 0.14)",
  warningBorder: "rgba(210, 153, 34, 0.28)",

  danger: "#F85149",
  dangerSoft: "rgba(248, 81, 73, 0.14)",
  dangerBorder: "rgba(248, 81, 73, 0.28)",

  info: "#58A6FF",
  infoSoft: "rgba(88, 166, 255, 0.14)",
  infoBorder: "rgba(88, 166, 255, 0.28)",
} as const;

export const space = {
  xxs: 4,
  xs: 6,
  sm: 10,
  md: 16,
  lg: 22,
  xl: 28,
  xxl: 36,
} as const;

export const radius = {
  xs: 8,
  sm: 13,
  md: 18,
  button: 17,
  card: 22,
  lg: 24,
  nav: 26,
  xl: 30,
  pill: 9999,
} as const;
