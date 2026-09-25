import { useCallback, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Radio, RefreshCw, UsersRound, WifiOff } from "lucide-react";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SkeletonCard,
} from "../../components/ui/sa";
import { navigatorApi } from "./navigator.api";
import { NavigatorMap } from "./NavigatorMap";
import { NavigatorRoster } from "./NavigatorRoster";
import { NavigatorEmployeeCard } from "./NavigatorEmployeeCard";
import { useNavigatorRealtime } from "./useNavigatorRealtime";
export function NavigatorPage() {
  const [selected, setSelected] = useState<string>();
  const query = useQuery({
    queryKey: ["navigator", "live"],
    queryFn: navigatorApi.live,
    refetchInterval: false,
    retry: 2,
  });
  useNavigatorRealtime(query);
  const items = query.data?.items ?? [];
  const select = useCallback((id: string) => setSelected(id), []);
  const current = items.find((i) => i.employeeRef === selected);
  const totals = useMemo(
    () => ({
      paired: items.filter((i) => i.state !== "UNPAIRED").length,
      live: items.filter((i) => i.state === "LIVE").length,
      stale: items.filter((i) => ["STALE", "OFFLINE"].includes(i.state)).length,
      paused: items.filter((i) => i.state === "OFF_DUTY").length,
    }),
    [items],
  );
  return (
    <>
      <WorkspaceHeader
        title="Navigator"
        subtitle="Consent-based live field operations for SA Productions."
      />
      {query.isPending ? (
        <SkeletonCard />
      ) : query.isError ? (
        <EmptyState
          title="Navigator connection unavailable"
          description="The live roster cannot be refreshed. No marker is being claimed as live."
          action={
            <SAButton onClick={() => query.refetch()}>
              <RefreshCw size={14} />
              Retry
            </SAButton>
          }
        />
      ) : !items.length ? (
        <EmptyState
          title="Navigator is ready"
          description="Pair an employee phone to start live location sharing."
        />
      ) : (
        <div className="navigator-page">
          <div className="navigator-summary">
            <Metric icon={UsersRound} label="Paired" value={totals.paired} />
            <Metric icon={Radio} label="Live" value={totals.live} />
            <Metric
              icon={WifiOff}
              label="Stale / offline"
              value={totals.stale}
            />
            <Metric icon={Radio} label="Off duty" value={totals.paused} />
          </div>
          <div className="navigator-layout">
            <section>
              <NavigatorMap
                items={items}
                selected={selected}
                onSelect={select}
              />
              {current && <NavigatorEmployeeCard item={current} />}
              <small className="navigator-sync">
                Last sync{" "}
                {new Date(query.data!.syncedAt).toLocaleTimeString("en-IN")}
              </small>
            </section>
            <NavigatorRoster
              items={items}
              selected={selected}
              onSelect={select}
            />
          </div>
        </div>
      )}
    </>
  );
}
function Metric({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Radio;
  label: string;
  value: number;
}) {
  return (
    <SABentoCard>
      <Icon size={16} />
      <strong>{value}</strong>
      <span>{label}</span>
    </SABentoCard>
  );
}
