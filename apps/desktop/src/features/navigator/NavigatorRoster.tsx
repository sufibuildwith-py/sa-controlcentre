import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { StatusBadge } from "../../components/ui/sa";
import { ageLabel, type NavigatorItem } from "./navigator.types";
type Filter = "ALL" | "LIVE" | "ATTENTION" | "PRODUCTION";
export function NavigatorRoster({
  items,
  selected,
  onSelect,
}: {
  items: NavigatorItem[];
  selected?: string;
  onSelect: (id: string) => void;
}) {
  const [search, setSearch] = useState(""),
    [filter, setFilter] = useState<Filter>("ALL");
  const visible = useMemo(
    () =>
      items.filter(
        (i) =>
          (!search ||
            (i.employeeName ?? "")
              .toLowerCase()
              .includes(search.toLowerCase())) &&
          (filter === "ALL" ||
            (filter === "LIVE" && i.state === "LIVE") ||
            (filter === "ATTENTION" &&
              ["STALE", "OFFLINE", "UNPAIRED"].includes(i.state)) ||
            (filter === "PRODUCTION" && !!i.productionTitle)),
      ),
    [filter, items, search],
  );
  return (
    <aside className="navigator-roster" aria-label="Navigator team roster">
      <div className="navigator-roster-head">
        <h2>Team</h2>
        <label>
          <Search size={14} />
          <input
            aria-label="Search Navigator team"
            placeholder="Search team"
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
            ["PRODUCTION", "Production"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            className={filter === value ? "active" : ""}
            onClick={() => setFilter(value)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="navigator-roster-list">
        {visible.map((item) => (
          <button
            key={`${item.employeeRef}:${item.deviceId}`}
            className={selected === item.employeeRef ? "active" : ""}
            onClick={() => onSelect(item.employeeRef)}
          >
            <span className="navigator-avatar">
              {(item.employeeName ?? "E")
                .split(/\s+/)
                .map((x) => x[0])
                .slice(0, 2)
                .join("")}
            </span>
            <span>
              <strong>{item.employeeName ?? "Employee"}</strong>
              <small>
                {item.roleTitle ?? "Field team"} · {ageLabel(item.recordedAt)}
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
        ))}
        {!visible.length && (
          <p className="muted">No employees match this view.</p>
        )}
      </div>
    </aside>
  );
}
