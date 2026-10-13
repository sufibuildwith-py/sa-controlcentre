import Constants from "expo-constants";

export const PRODUCTION_GATEWAY_URL = "https://sa-navigator-gateway-production.up.railway.app";
export const PRODUCTION_WS_URL = "wss://sa-navigator-gateway-production.up.railway.app/ws/navigator";

export const gatewayUrl =
  process.env.EXPO_PUBLIC_NAVIGATOR_GATEWAY_URL ??
  (Constants.expoConfig?.extra?.gatewayUrl as string | undefined) ??
  PRODUCTION_GATEWAY_URL;

export const CONSENT_VERSION = "n1-2026-09";
export const LOCATION_TASK = "sa-navigator-active-location";
