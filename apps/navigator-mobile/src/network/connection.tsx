import NetInfo, { type NetInfoState } from "@react-native-community/netinfo";
import { createContext, type PropsWithChildren, useContext, useEffect, useMemo, useState } from "react";

export type ConnectionState = "CONNECTING" | "ONLINE" | "DEGRADED" | "OFFLINE" | "AUTH_REQUIRED" | "SERVICE_UNAVAILABLE";

export function classifyNetwork(state: Pick<NetInfoState, "isConnected" | "isInternetReachable">): ConnectionState {
  if (state.isConnected === false) return "OFFLINE";
  if (state.isConnected == null || state.isInternetReachable == null) return "CONNECTING";
  return state.isInternetReachable ? "ONLINE" : "DEGRADED";
}

const ConnectionContext = createContext<{ state: ConnectionState; setServiceState: (state: ConnectionState) => void }>({
  state: "CONNECTING",
  setServiceState: () => undefined,
});

export function ConnectionProvider({ children }: PropsWithChildren) {
  const [network, setNetwork] = useState<ConnectionState>("CONNECTING");
  const [service, setService] = useState<ConnectionState>();
  useEffect(() => NetInfo.addEventListener((value) => setNetwork(classifyNetwork(value))), []);
  const state = service && network === "ONLINE" ? service : network;
  const value = useMemo(() => ({ state, setServiceState: setService }), [state]);
  return <ConnectionContext.Provider value={value}>{children}</ConnectionContext.Provider>;
}

export const useConnection = () => useContext(ConnectionContext);
