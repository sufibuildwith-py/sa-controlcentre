import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Search, Users } from "lucide-react";
import { useMemo, useState } from "react";
import { FormField, SAButton, SAModal } from "../../components/ui/sa";
import { api } from "../../lib/api";
import { initials } from "../employees/PeoplePage";
import { navigatorApi } from "./navigator.api";
import type { Employee, Production } from "../../types/domain";

interface MakeTeamModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultProductionId?: string;
}

export function MakeTeamModal({
  open,
  onOpenChange,
  defaultProductionId,
}: MakeTeamModalProps) {
  const [productionId, setProductionId] = useState(defaultProductionId || "");
  const [teamName, setTeamName] = useState("");
  const [memberSearch, setMemberSearch] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");

  const client = useQueryClient();

  const productionsQuery = useQuery({
    queryKey: ["productions", "active-select"],
    queryFn: () => api<Production[]>("/productions"),
    enabled: open,
  });

  const employeesQuery = useQuery({
    queryKey: ["employees", "team-picker"],
    queryFn: () => api<Employee[]>("/employees?status=ACTIVE"),
    enabled: open,
  });

  // Filter out cancelled productions
  const availableProductions = useMemo(() => {
    return (productionsQuery.data ?? []).filter(
      (p) => p.status !== "CANCELLED" && p.status !== "DELIVERED",
    );
  }, [productionsQuery.data]);

  const effectiveProductionId =
    productionId || (availableProductions[0]?.id ?? "");

  const filteredEmployees = useMemo(() => {
    const list = employeesQuery.data ?? [];
    if (!memberSearch.trim()) return list;
    const q = memberSearch.toLowerCase();
    return list.filter(
      (e) =>
        (e.displayName ?? "").toLowerCase().includes(q) ||
        (e.roleTitle ?? "").toLowerCase().includes(q) ||
        (e.department ?? "").toLowerCase().includes(q),
    );
  }, [employeesQuery.data, memberSearch]);

  const toggleMember = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const createTeamMutation = useMutation({
    mutationFn: () => {
      const targetProdId = effectiveProductionId;
      if (!targetProdId) throw new Error("Please select a production.");
      if (!teamName.trim()) throw new Error("Please enter a team name.");
      if (selectedIds.size === 0)
        throw new Error("Please select at least one employee for the team.");
      return navigatorApi.createTeam(
        targetProdId,
        teamName.trim(),
        Array.from(selectedIds),
      );
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["navigator", "teams"] });
      client.invalidateQueries({ queryKey: ["navigator", "live"] });
      client.invalidateQueries({ queryKey: ["productions"] });
      handleClose(false);
    },
    onError: (err: any) => {
      if (err?.fields && Object.keys(err.fields).length > 0) {
        const fieldMsgs = Object.values(err.fields).join(". ");
        setError(fieldMsgs);
      } else if (err?.message) {
        setError(err.message);
      } else {
        setError("Failed to create team. Please try again.");
      }
    },
  });

  const handleClose = (v: boolean) => {
    if (!v) {
      setTeamName("");
      setMemberSearch("");
      setSelectedIds(new Set());
      setError("");
      createTeamMutation.reset();
    }
    onOpenChange(v);
  };

  return (
    <SAModal
      open={open}
      onOpenChange={handleClose}
      title="Create Production Team"
      description="Organize field crew into a dedicated operational team for a specific shoot or event."
    >
      <div className="navigator-make-team-form">
        <FormField label="Team Name *" error={!teamName.trim() && error ? "Team name is required" : undefined}>
          <input
            aria-label="Team Name"
            placeholder="e.g. Camera Crew, Sound Crew, Lighting Crew"
            value={teamName}
            onChange={(e) => {
              setTeamName(e.target.value);
              setError("");
            }}
            autoFocus
          />
        </FormField>

        <FormField label="Production *">
          <select
            aria-label="Select production"
            value={effectiveProductionId}
            onChange={(e) => {
              setProductionId(e.target.value);
              setError("");
            }}
          >
            {availableProductions.map((p) => {
              const dateStr = p.eventDate
                ? ` · ${new Date(p.eventDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}`
                : "";
              return (
                <option key={p.id} value={p.id}>
                  {p.title}
                  {dateStr} ({p.status})
                </option>
              );
            })}
          </select>
        </FormField>

        <div className="navigator-team-members-section">
          <div className="navigator-team-members-header">
            <label className="navigator-section-label">
              Team Members ({selectedIds.size} selected)
            </label>
            {selectedIds.size > 0 && (
              <button
                type="button"
                className="navigator-clear-btn"
                onClick={() => setSelectedIds(new Set())}
              >
                Clear all
              </button>
            )}
          </div>

          <div className="navigator-picker-search">
            <label>
              <Search size={14} />
              <input
                aria-label="Search members"
                placeholder="Search people to assign..."
                value={memberSearch}
                onChange={(e) => setMemberSearch(e.target.value)}
              />
            </label>
          </div>

          <div className="navigator-team-members-list" role="list">
            {employeesQuery.isPending ? (
              <p className="muted" style={{ padding: "12px" }}>Loading employees...</p>
            ) : !filteredEmployees.length ? (
              <p className="muted" style={{ padding: "12px" }}>No matching employees found.</p>
            ) : (
              filteredEmployees.map((emp) => {
                const isSelected = selectedIds.has(emp.id);
                return (
                  <label
                    key={emp.id}
                    className={`navigator-member-checkbox-row ${isSelected ? "selected" : ""}`}
                    role="listitem"
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleMember(emp.id)}
                      aria-label={`Select ${emp.displayName}`}
                    />
                    <span className="navigator-avatar">
                      {initials(emp.displayName)}
                    </span>
                    <div className="navigator-member-info">
                      <strong>{emp.displayName}</strong>
                      <small>
                        {emp.roleTitle || "Field team"}
                        {emp.department ? ` · ${emp.department}` : ""}
                      </small>
                    </div>
                    {isSelected && (
                      <Check size={16} className="navigator-selected-icon" />
                    )}
                  </label>
                );
              })
            )}
          </div>
        </div>

        {error && <p className="form-error">{error}</p>}

        <div className="modal-actions">
          <SAButton onClick={() => handleClose(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              createTeamMutation.isPending ||
              !teamName.trim() ||
              !effectiveProductionId ||
              selectedIds.size === 0
            }
            onClick={() => createTeamMutation.mutate()}
          >
            {createTeamMutation.isPending ? "Creating Team…" : "Create Team"}
          </SAButton>
        </div>
      </div>
    </SAModal>
  );
}
