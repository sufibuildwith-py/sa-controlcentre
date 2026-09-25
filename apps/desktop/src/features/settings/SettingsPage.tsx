import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Beaker,
  Database,
  MessageCircle,
  MapPinned,
  RefreshCw,
  ShieldCheck,
  Wifi,
  Zap,
} from "lucide-react";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { ThemeSwitch } from "../../components/layout/ThemeSwitch";
import { ReleaseSettings } from "./ReleaseSettings";
import { api, json } from "../../lib/api";
import type {
  CommunicationCentre,
  NotificationRule,
  OutboundMessage,
} from "../../types/domain";
import { navigatorEnabled } from "../navigator/navigator.types";
import { navigatorApi } from "../navigator/navigator.api";
type Diagnostics = {
  api: string;
  database: string;
  appMode: string;
  messagingProvider: string;
};
export function SettingsPage() {
  const health = useQuery({
    queryKey: ["system", "diagnostics"],
    queryFn: () => api<Diagnostics>("/system/diagnostics"),
    refetchInterval: 30000,
  });
  return (
    <>
      <WorkspaceHeader
        title="Settings"
        subtitle="Appearance, notification automation and system health."
      />
      <div className="settings-stack">
        <SABentoCard>
          <ThemeSwitch />
        </SABentoCard>
        <SABentoCard>
          <div className="settings-section-title">
            <span>
              <ShieldCheck size={17} />
              <div>
                <strong>System health</strong>
                <small>
                  Authenticated live diagnostics. No secrets are exposed.
                </small>
              </div>
            </span>
            <SAButton size="sm" onClick={() => health.refetch()}>
              <RefreshCw size={13} />
              Refresh
            </SAButton>
          </div>
          {health.isPending ? (
            <SkeletonCard />
          ) : health.isError ? (
            <EmptyState
              title="Diagnostics unavailable"
              description="The authenticated health check could not be completed."
            />
          ) : (
            <div className="health-grid">
              <Health icon={Wifi} label="API" value={health.data.api} />
              <Health
                icon={Database}
                label="Database"
                value={health.data.database}
              />
              <Health
                icon={ShieldCheck}
                label="App mode"
                value={health.data.appMode}
              />
              <Health
                icon={MessageCircle}
                label="Messaging"
                value={health.data.messagingProvider}
              />
            </div>
          )}
        </SABentoCard>
        <NotificationRules />
        {import.meta.env.VITE_DESKTOP_RELEASE === "true" && <ReleaseSettings />}
        {import.meta.env.VITE_DESKTOP_RELEASE !== "true" && <Simulator />}
        {navigatorEnabled && import.meta.env.VITE_APP_MODE === "demo" && <NavigatorSimulator />}
      </div>
    </>
  );
}
function Health({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Wifi;
  label: string;
  value: string;
}) {
  const good =
    value.toUpperCase() === "UP" ||
    !["DOWN", "FAILED"].includes(value.toUpperCase());
  return (
    <div className="settings-row">
      <span>
        <Icon size={17} />
        <div>
          <strong>{label}</strong>
          <small>{value === "unconfigured" ? "Not configured" : value}</small>
        </div>
      </span>
      <StatusBadge tone={good ? "success" : "danger"}>
        {good ? "Healthy" : "Unavailable"}
      </StatusBadge>
    </div>
  );
}
function NotificationRules() {
  const client = useQueryClient(),
    rules = useQuery({
      queryKey: ["notification-rules"],
      queryFn: () => api<NotificationRule[]>("/notification-rules"),
    });
  const update = useMutation({
    mutationFn: ({
      rule,
      enabled,
      delayMinutes,
    }: {
      rule: NotificationRule;
      enabled: boolean;
      delayMinutes: number;
    }) =>
      api<NotificationRule>(`/notification-rules/${rule.id}`, {
        method: "PATCH",
        ...json({ enabled, delayMinutes }),
      }),
    onSuccess: () =>
      client.invalidateQueries({ queryKey: ["notification-rules"] }),
  });
  return (
    <SABentoCard>
      <div className="settings-section-title">
        <span>
          <Zap size={17} />
          <div>
            <strong>Notification automation</strong>
            <small>
              Rules are evaluated before any outbound message is created.
            </small>
          </div>
        </span>
      </div>
      {rules.isPending ? (
        <SkeletonCard />
      ) : rules.isError ? (
        <EmptyState
          title="Rules unavailable"
          description="Notification policy could not be loaded."
        />
      ) : (
        <div className="automation-list">
          {rules.data?.map((rule) => (
            <div key={rule.id}>
              <div>
                <strong>{label(rule.eventType)}</strong>
                <small>{rule.templateKey} · WhatsApp</small>
              </div>
              <label className="delay-field">
                Delay
                <input
                  type="number"
                  min="0"
                  defaultValue={rule.delayMinutes}
                  onBlur={(e) =>
                    update.mutate({
                      rule,
                      enabled: rule.enabled,
                      delayMinutes: Number(e.target.value),
                    })
                  }
                />
                <span>min</span>
              </label>
              <button
                className={`rule-switch ${rule.enabled ? "on" : ""}`}
                role="switch"
                aria-checked={rule.enabled}
                onClick={() =>
                  update.mutate({
                    rule,
                    enabled: !rule.enabled,
                    delayMinutes: rule.delayMinutes,
                  })
                }
              >
                <span />
              </button>
            </div>
          ))}
        </div>
      )}
    </SABentoCard>
  );
}
function Simulator() {
  const client = useQueryClient(),
    messages = useQuery({
      queryKey: ["demo", "messages"],
      queryFn: () => api<CommunicationCentre>("/demo/messages"),
      retry: false,
    });
  const action = useMutation({
    mutationFn: ({ id, path }: { id: string; path: string }) =>
      api<OutboundMessage>(`/demo/messages/${id}/${path}`, { method: "POST" }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["demo", "messages"] });
      client.invalidateQueries({ queryKey: ["communications"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
  if (messages.isError) return null;
  return (
    <SABentoCard>
      <div className="settings-section-title">
        <span>
          <Beaker size={17} />
          <div>
            <strong>Messaging simulator</strong>
            <small>
              Demo only · uses the same inbound service as Meta callbacks.
            </small>
          </div>
        </span>
        <SAButton size="sm" onClick={() => messages.refetch()}>
          <RefreshCw size={13} />
          Refresh
        </SAButton>
      </div>
      {messages.isPending ? (
        <SkeletonCard />
      ) : (
        <div className="simulator-list">
          {messages.data?.messages.slice(0, 6).map((m) => (
            <div key={m.id}>
              <div>
                <strong>{m.employeeName}</strong>
                <small>{m.bodyPreview}</small>
              </div>
              <div>
                {(["SENT", "DELIVERED", "READ", "FAILED"] as const).map((s) => (
                  <button
                    key={s}
                    onClick={() => action.mutate({ id: m.id, path: s })}
                  >
                    {s}
                  </button>
                ))}
                {m.requiresResponse && (
                  <>
                    <button
                      onClick={() =>
                        action.mutate({ id: m.id, path: "respond/CONFIRM" })
                      }
                    >
                      CONFIRM
                    </button>
                    <button
                      onClick={() =>
                        action.mutate({ id: m.id, path: "respond/DECLINE" })
                      }
                    >
                      DECLINE
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </SABentoCard>
  );
}
function NavigatorSimulator(){const client=useQueryClient(),[running,setRunning]=useState(false);const action=useMutation({mutationFn:(next:boolean)=>navigatorApi.simulator(next?"start":"stop"),onSuccess:(_,next)=>{setRunning(next);client.invalidateQueries({queryKey:["navigator","live"]});}});return <SABentoCard><div className="settings-section-title"><span><MapPinned size={17}/><div><strong>Navigator Simulator</strong><small>Demo only · routes movement through the Navigator gateway ingestion service.</small></div></span><StatusBadge tone={running?"success":"neutral"}>{running?"RUNNING":"STOPPED"}</StatusBadge></div><p className="muted">Sharma Wedding · Amaan moving, Rehan live, Farhan stale, Sarah paused.</p><SAButton variant={running?"danger":"primary"} onClick={()=>action.mutate(!running)} disabled={action.isPending}>{running?"Stop simulation":"Start simulation"}</SAButton></SABentoCard>}
const label = (value: string) =>
  value
    .toLowerCase()
    .split("_")
    .map((x) => x[0].toUpperCase() + x.slice(1))
    .join(" ");
