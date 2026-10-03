import { useCallback, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Radio, RefreshCw, UserPlus, Users, UsersRound, WifiOff } from "lucide-react";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SkeletonCard,
} from "../../components/ui/sa";
import { navigatorApi } from "./navigator.api";
import { NavigatorMap } from "./NavigatorMap";
import { NavigatorRoster, type Filter, type FocusedTeam } from "./NavigatorRoster";
import { NavigatorEmployeeCard } from "./NavigatorEmployeeCard";
import { AddEmployeeModal } from "./AddEmployeeModal";
import { MakeTeamModal } from "./MakeTeamModal";
import { useNavigatorRealtime } from "./useNavigatorRealtime";

export function NavigatorPage() {
  const [selected, setSelected] = useState<string>();
  const [filter, setFilter] = useState<Filter>("ALL");
  const [focusedTeam, setFocusedTeam] = useState<FocusedTeam | null>(null);
  const [addEmployeeOpen, setAddEmployeeOpen] = useState(false);
  const [makeTeamOpen, setMakeTeamOpen] = useState(false);

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

  const toggleFilter = (target: Filter) => {
    setFilter((prev) => (prev === target ? "ALL" : target));
    setFocusedTeam(null);
  };

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
          action={
            <div className="navigator-empty-actions">
              <SAButton onClick={() => setAddEmployeeOpen(true)}>
                <UserPlus size={14} />
                Add Employee
              </SAButton>
              <SAButton
                variant="secondary"
                onClick={() => setMakeTeamOpen(true)}
              >
                <Users size={14} />
                Make Team
              </SAButton>
            </div>
          }
        />
      ) : (
        <div className="navigator-page">
          <div className="navigator-summary">
            <Metric
              icon={UsersRound}
              label="Paired"
              value={totals.paired}
              active={filter === "ALL"}
              onClick={() => {
                setFilter("ALL");
                setFocusedTeam(null);
              }}
            />
            <Metric
              icon={Radio}
              label="Live"
              value={totals.live}
              active={filter === "LIVE"}
              onClick={() => toggleFilter("LIVE")}
            />
            <Metric
              icon={WifiOff}
              label="Stale / offline"
              value={totals.stale}
              active={filter === "ATTENTION"}
              onClick={() => toggleFilter("ATTENTION")}
            />
            <Metric
              icon={Radio}
              label="Off duty"
              value={totals.paused}
              active={filter === "OFF_DUTY"}
              onClick={() => toggleFilter("OFF_DUTY")}
            />
          </div>
          <div className="navigator-layout">
            <section>
              <NavigatorMap
                items={items}
                selected={selected}
                focusedTeam={focusedTeam}
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
              filter={filter}
              onFilterChange={setFilter}
              focusedTeam={focusedTeam}
              onFocusTeam={setFocusedTeam}
              onSelect={select}
              onOpenAddEmployee={() => setAddEmployeeOpen(true)}
              onOpenMakeTeam={() => setMakeTeamOpen(true)}
            />
          </div>
        </div>
      )}

      <AddEmployeeModal
        open={addEmployeeOpen}
        onOpenChange={setAddEmployeeOpen}
        pairedItems={items}
      />

      <MakeTeamModal
        open={makeTeamOpen}
        onOpenChange={setMakeTeamOpen}
      />
    </>
  );
}

function Metric({
  icon: Icon,
  label,
  value,
  active,
  onClick,
}: {
  icon: typeof Radio;
  label: string;
  value: number;
  active?: boolean;
  onClick?: () => void;
}) {
  return (
    <SABentoCard
      interactive={!!onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-pressed={active}
      onClick={onClick}
      onKeyDown={(e) => {
        if (onClick && (e.key === "Enter" || e.key === " ")) {
          e.preventDefault();
          onClick();
        }
      }}
      className={`navigator-metric-card ${active ? "active" : ""}`}
    >
      <Icon size={16} />
      <strong>{value}</strong>
      <span>{label}</span>
    </SABentoCard>
  );
}
