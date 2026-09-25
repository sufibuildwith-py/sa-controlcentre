import { useEffect } from "react";
import { Client } from "@stomp/stompjs";
import type { QueryObserverResult } from "@tanstack/react-query";
import { useQueryClient } from "@tanstack/react-query";
import { navigatorApi } from "./navigator.api";
import type { NavigatorEvent, NavigatorSnapshot } from "./navigator.types";
export function applyNavigatorEvent(
  old: NavigatorSnapshot,
  event: NavigatorEvent,
): NavigatorSnapshot {
  return {
    ...old,
    syncedAt: new Date().toISOString(),
    items: old.items.map((item) =>
      item.employeeRef !== event.employeeRef
        ? item
        : {
            ...item,
            deviceId: event.deviceId,
            sessionId: event.sessionId,
            state:
              event.type === "TRACKING_STOPPED"
                ? "OFF_DUTY"
                : event.type === "DEVICE_REVOKED"
                  ? "UNPAIRED"
                  : event.type === "DEVICE_OFFLINE"
                    ? "OFFLINE"
                    : "LIVE",
            latitude: event.latitude ?? item.latitude,
            longitude: event.longitude ?? item.longitude,
            accuracyMeters: event.accuracyMeters ?? item.accuracyMeters,
            recordedAt: event.recordedAt ?? item.recordedAt,
            receivedAt: event.receivedAt ?? item.receivedAt,
          },
    ),
  };
}
export function useNavigatorRealtime(
  query: QueryObserverResult<NavigatorSnapshot, Error>,
) {
  const cache = useQueryClient();
  useEffect(() => {
    let client: Client | undefined,
      cancelled = false;
    navigatorApi
      .ticket()
      .then((grant) => {
        if (cancelled) return;
        client = new Client({
          brokerURL:
            import.meta.env.VITE_NAVIGATOR_GATEWAY_URL ??
            "ws://localhost:8091/ws/navigator",
          connectHeaders: { "X-Navigator-Ticket": grant.ticket },
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          reconnectDelay: Math.min(10000, 4000),
          onConnect: () => {
            query.refetch();
            client?.subscribe(
              `/topic/organizations/${grant.organizationPublicId}/locations`,
              (message) => {
                const event = JSON.parse(message.body) as NavigatorEvent;
                cache.setQueryData<NavigatorSnapshot>(
                  ["navigator", "live"],
                  (old) => (old ? applyNavigatorEvent(old, event) : old),
                );
              },
            );
          },
        });
        client.activate();
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      client?.deactivate();
    };
  }, [cache, query.refetch]);
}
