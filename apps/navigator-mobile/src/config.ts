import Constants from "expo-constants";
export const gatewayUrl=process.env.EXPO_PUBLIC_NAVIGATOR_GATEWAY_URL??(Constants.expoConfig?.extra?.gatewayUrl as string|undefined)??"https://navigator-demo.saproduction.in";
export const CONSENT_VERSION="n1-2026-09";
export const LOCATION_TASK="sa-navigator-active-location";
