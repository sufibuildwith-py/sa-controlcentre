import { Search, UserPlus, Users, X } from "lucide-react";
import { useMemo, useState } from "react";
import { SAButton, StatusBadge } from "../../components/ui/sa";
import { initials } from "../employees/PeoplePage";
import { ageLabel, type NavigatorItem } from "./navigator.types";
export type Filter = "ALL" | "LIVE" | "ATTENTION" | "OFF_DUTY" | "PRODUCTION";

export interface FocusedTeam {
  teamName: string;
  employeeRefs: string[];
}

export function NavigatorRoster({
  items,
  selected,
  filter: controlledFilter,
  onFilterChange,
  focusedTeam = null,
  onFocusTeam = () => {},
  onSelect,
  onOpenAddEmployee,
  onOpenMakeTeam,
}: {
  items: NavigatorItem[];
  selected?: string;
  filter?: Filter;
  onFilterChange?: (f: Filter) => void;
  focusedTeam?: FocusedTeam | null;
  onFocusTeam?: (t: FocusedTeam | null) => void;
  onSelect: (id: string) => void;
  onOpenAddEmployee?: () => void;
  onOpenMakeTeam?: () => void;
}) {
  const [search, setSearch] = useState("");
  const [internalFilter, setInternalFilter] = useState<Filter>("ALL");
  const filter = controlledFilter ?? internalFilter;
  const handleFilterChange = (f: Filter) => {
    if (onFilterChange) onFilterChange(f);
    else setInternalFilter(f);
  };

  // 1. Filter items based on active status filter, team focus, and search
  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((i) => {
      // Search match
      const matchesSearch =
        !q ||
        (i.employeeName ?? "").toLowerCase().includes(q) ||
        (i.roleTitle ?? "").toLowerCase().includes(q) ||
        (i.teamName ?? "").toLowerCase().includes(q) ||
        (i.productionTitle ?? "").toLowerCase().includes(q);

      if (!matchesSearch) return false;

      // Team focus match
      if (focusedTeam && !focusedTeam.employeeRefs.includes(i.employeeRef)) {
        return false;
      }

      // Status filter match
      if (filter === "LIVE") return i.state === "LIVE";
      if (filter === "ATTENTION")
        return ["STALE", "OFFLINE", "UNPAIRED"].includes(i.state);
      if (filter === "OFF_DUTY") return i.state === "OFF_DUTY";
      if (filter === "PRODUCTION") return !!i.productionTitle;

      return true;
    });
  }, [filter, focusedTeam, items, search]);

  // 2. Group visible items by Production -> Team
  const grouped = useMemo(() => {
    const prodMap = new Map<
      string,
      {
        productionTitle: string;
        productionId?: string;
        teams: Map<string, NavigatorItem[]>;
      }
    >();

    const unassignedItems: NavigatorItem[] = [];

    for (const item of visible) {
      if (item.productionTitle) {
        if (!prodMap.has(item.productionTitle)) {
          prodMap.set(item.productionTitle, {
            productionTitle: item.productionTitle,
            productionId: item.productionId,
            teams: new Map(),
          });
        }
        const prod = prodMap.get(item.productionTitle)!;
        const teamKey = item.teamName || "General Crew";
        if (!prod.teams.has(teamKey)) {
          prod.teams.set(teamKey, []);
        }
        prod.teams.get(teamKey)!.push(item);
      } else {
        unassignedItems.push(item);
      }
    }

    return {
      productions: Array.from(prodMap.values()).map((p) => ({
        productionTitle: p.productionTitle,
        productionId: p.productionId,
        teams: Array.from(p.teams.entries()).map(([teamName, members]) => ({
          teamName,
          members,
        })),
      })),
      unassigned: unassignedItems,
    };
  }, [visible]);

  // Counts for filter pills
  const counts = useMemo(
    () => ({
      ALL: items.length,
      LIVE: items.filter((i) => i.state === "LIVE").length,
      ATTENTION: items.filter((i) =>
        ["STALE", "OFFLINE", "UNPAIRED"].includes(i.state),
      ).length,
      OFF_DUTY: items.filter((i) => i.state === "OFF_DUTY").length,
      PRODUCTION: items.filter((i) => !!i.productionTitle).length,
    }),
    [items],
  );

  const handleTeamClick = (teamName: string, members: NavigatorItem[]) => {
    if (focusedTeam?.teamName === teamName) {
      onFocusTeam(null);
    } else {
      onFocusTeam({
        teamName,
        employeeRefs: members.map((m) => m.employeeRef),
      });
    }
  };

  return (
    <>
      <aside className="navigator-roster" aria-label="Navigator team roster">
        <div className="navigator-roster-head">
          <div>
            <h2>Team</h2>
            <span className="navigator-roster-count">{items.length} paired</span>
          </div>
          <div className="navigator-roster-actions">
            <SAButton
              size="sm"
              variant="secondary"
              onClick={onOpenAddEmployee}
              aria-label="Add Employee to Navigator"
            >
              <UserPlus size={13} />
              Add Employee
            </SAButton>
            <SAButton
              size="sm"
              variant="secondary"
              onClick={onOpenMakeTeam}
              aria-label="Make Team in Production"
            >
              <Users size={13} />
              Make Team
            </SAButton>
          </div>
        </div>

        <div className="navigator-roster-search">
          <label>
            <Search size={14} />
            <input
              aria-label="Search Navigator team"
              placeholder="Search people or teams..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
        </div>

        <div className="navigator-filters">
          {(
            [
              ["ALL", "All"],
              ["LIVE", "Live"],
              ["ATTENTION", "Needs attention"],
              ["OFF_DUTY", "Off duty"],
              ["PRODUCTION", "Production"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              aria-label={label}
              className={filter === value ? "active" : ""}
              onClick={() => {
                handleFilterChange(value);
                if (focusedTeam) onFocusTeam(null);
              }}
            >
              {label} ({counts[value]})
            </button>
          ))}
        </div>

        {focusedTeam && (
          <div className="navigator-focused-team-banner">
            <span>
              Focusing <strong>{focusedTeam.teamName}</strong> (
              {focusedTeam.employeeRefs.length} members)
            </span>
            <button
              type="button"
              className="navigator-clear-team-btn"
              onClick={() => onFocusTeam(null)}
              aria-label="Clear team filter"
            >
              <X size={12} />
              Clear
            </button>
          </div>
        )}

        <div className="navigator-roster-list" role="list">
          {grouped.productions.map((prod) => (
            <div key={prod.productionTitle} className="navigator-production-group">
              <div className="navigator-production-title">
                <span>{prod.productionTitle.toUpperCase()}</span>
              </div>

              {prod.teams.map((team) => {
                const isTeamFocused = focusedTeam?.teamName === team.teamName;
                return (
                  <div key={team.teamName} className="navigator-team-section">
                    <button
                      type="button"
                      className={`navigator-team-header-btn ${isTeamFocused ? "focused" : ""}`}
                      onClick={() => handleTeamClick(team.teamName, team.members)}
                      title={`Click to ${isTeamFocused ? "unfocus" : "focus"} ${team.teamName} on map`}
                    >
                      <Users size={13} />
                      <strong>{team.teamName}</strong>
                      <span className="navigator-team-count">
                        {team.members.length} {team.members.length === 1 ? "member" : "members"}
                      </span>
                    </button>

                    <div className="navigator-team-members">
                      {team.members.map((item) => (
                        <EmployeeRow
                          key={`${item.employeeRef}:${item.deviceId}`}
                          item={item}
                          isSelected={selected === item.employeeRef}
                          onSelect={onSelect}
                        />
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          ))}

          {grouped.unassigned.length > 0 && (
            <div className="navigator-production-group">
              <div className="navigator-production-title">
                <span>GENERAL FIELD TEAM / UNASSIGNED</span>
              </div>
              <div className="navigator-team-members">
                {grouped.unassigned.map((item) => (
                  <EmployeeRow
                    key={`${item.employeeRef}:${item.deviceId}`}
                    item={item}
                    isSelected={selected === item.employeeRef}
                    onSelect={onSelect}
                  />
                ))}
              </div>
            </div>
          )}

          {!visible.length && (
            <div className="navigator-roster-empty">
              <p className="muted">No team members match this view.</p>
              {search && (
                <SAButton size="sm" onClick={() => setSearch("")}>
                  Clear search
                </SAButton>
              )}
            </div>
          )}
        </div>
      </aside>
    </>
  );
}

function EmployeeRow({
  item,
  isSelected,
  onSelect,
}: {
  item: NavigatorItem;
  isSelected: boolean;
  onSelect: (id: string) => void;
}) {
  return (
    <button
      key={`${item.employeeRef}:${item.deviceId}`}
      className={`navigator-employee-row ${isSelected ? "active" : ""}`}
      onClick={() => onSelect(item.employeeRef)}
      role="listitem"
    >
      <span className="navigator-avatar">
        {initials(item.employeeName ?? "Employee")}
      </span>
      <span className="navigator-employee-details">
        <strong className="navigator-employee-name">
          {item.employeeName ?? "Employee"}
        </strong>
        <small className="navigator-employee-sub">
          {item.roleTitle ?? "Field team"}
          {item.teamName ? ` · ${item.teamName}` : ""} · {ageLabel(item.recordedAt)}
        </small>
      </span>
      <StatusBadge
        tone={
          item.state === "LIVE"
            ? "success"
            : item.state === "STALE"
              ? "warning"
              : item.state === "OFFLINE"
                ? "danger"
                : "neutral"
        }
      >
        {item.state === "OFF_DUTY" ? "Off duty" : item.state}
      </StatusBadge>
    </button>
  );
}
