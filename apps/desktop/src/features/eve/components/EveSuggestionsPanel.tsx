import React, { useState } from "react";
import { ChevronDown, ChevronUp, Sparkles, RefreshCw, X, ArrowUpRight } from "lucide-react";
import type { EveSuggestion } from "../eve.types";

interface EveSuggestionsPanelProps {
  suggestions: EveSuggestion[];
  loading?: boolean;
  onDismiss: (id: string) => void;
  onInvestigate: (prompt: string) => void;
  onRefresh?: () => void;
}

export const EveSuggestionsPanel: React.FC<EveSuggestionsPanelProps> = ({
  suggestions,
  loading = false,
  onDismiss,
  onInvestigate,
  onRefresh,
}) => {
  const [expandedEvidence, setExpandedEvidence] = useState<Record<string, boolean>>({});
  const [collapsed, setCollapsed] = useState(false);

  const toggleEvidence = (id: string) => {
    setExpandedEvidence((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  };

  const getInvestigationPrompt = (s: EveSuggestion): string => {
    if (s.type === "OUTSTANDING_EMPLOYEE_PAYMENT") {
      return `Review payment for ${s.canonicalEntityName ?? "employee"}`;
    }
    if (s.type === "APPROACHING_PRODUCTION_OPEN_TASKS") {
      return `Check open tasks for ${s.canonicalEntityName ?? "production"}`;
    }
    if (s.type === "OVERDUE_TASK") {
      return `Inspect overdue task ${s.canonicalEntityName ?? "task"}`;
    }
    return `Investigate ${s.title}`;
  };

  const activeSuggestions = suggestions.filter((s) => s.status === "ACTIVE");

  return (
    <div
      data-testid="eve-suggestions-panel"
      className="mb-4 rounded-2xl border p-4 transition-all"
      style={{
        backgroundColor: "var(--surface)",
        borderColor: "var(--border)",
      }}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div
            className="flex h-7 w-7 items-center justify-center rounded-lg"
            style={{
              backgroundColor: "var(--surface-soft)",
              color: "var(--text-1)",
            }}
          >
            <Sparkles className="h-4 w-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: "var(--text-1)" }}>
                Continuous Intelligence
              </span>
              <span
                className="rounded-full px-2 py-0.5 text-[10px] font-semibold"
                style={{
                  backgroundColor: "var(--surface-soft)",
                  color: "var(--text-2)",
                  border: "1px solid var(--border)",
                }}
              >
                {activeSuggestions.length}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1">
          {onRefresh && (
            <button
              type="button"
              onClick={onRefresh}
              disabled={loading}
              title="Refresh suggestions"
              className="rounded-lg p-1.5 transition-colors hover:opacity-80 disabled:opacity-40"
              style={{
                color: "var(--text-2)",
                backgroundColor: "transparent",
              }}
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            </button>
          )}
          <button
            type="button"
            onClick={() => setCollapsed(!collapsed)}
            className="rounded-lg p-1.5 transition-colors hover:opacity-80"
            style={{
              color: "var(--text-2)",
              backgroundColor: "transparent",
            }}
            title={collapsed ? "Expand panel" : "Collapse panel"}
          >
            {collapsed ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronUp className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {!collapsed && (
        <div className="mt-3 space-y-2.5">
          {activeSuggestions.length === 0 ? (
            <div
              className="rounded-xl border border-dashed py-4 text-center text-xs"
              style={{
                borderColor: "var(--border)",
                color: "var(--text-2)",
              }}
            >
              No proactive suggestions at this time. EVE is observing canonical system mutations.
            </div>
          ) : (
            activeSuggestions.map((s) => {
              const isHigh = s.priority === "HIGH";
              const isEvidenceOpen = !!expandedEvidence[s.id];

              return (
                <div
                  key={s.id}
                  data-testid={`suggestion-card-${s.id}`}
                  className="rounded-xl border p-3.5 transition-colors"
                  style={{
                    backgroundColor: "var(--surface-soft)",
                    borderColor: isHigh ? "var(--danger, #ef4444)" : "var(--border)",
                  }}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 space-y-1">
                      <div className="flex items-center gap-2">
                        <span
                          className="rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider"
                          style={{
                            backgroundColor: isHigh ? "rgba(239, 68, 68, 0.15)" : "var(--surface)",
                            color: isHigh ? "var(--danger, #ef4444)" : "var(--text-2)",
                            border: "1px solid var(--border)",
                          }}
                        >
                          {s.priority}
                        </span>
                        <span className="text-xs font-semibold" style={{ color: "var(--text-1)" }}>
                          {s.title}
                        </span>
                      </div>
                      <p className="text-xs leading-relaxed" style={{ color: "var(--text-2)" }}>
                        {s.summary}
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() => onDismiss(s.id)}
                      title="Dismiss suggestion"
                      className="rounded-lg p-1 transition-colors hover:opacity-80"
                      style={{
                        color: "var(--text-2)",
                        backgroundColor: "var(--surface)",
                        border: "1px solid var(--border)",
                      }}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>

                  {/* Evidence Section */}
                  {s.evidence && s.evidence.length > 0 && (
                    <div className="mt-2.5">
                      <button
                        type="button"
                        onClick={() => toggleEvidence(s.id)}
                        className="flex items-center gap-1 text-[11px] font-medium transition-colors hover:opacity-80"
                        style={{ color: "var(--text-2)" }}
                      >
                        <span>Evidence ({s.evidence.length})</span>
                        {isEvidenceOpen ? (
                          <ChevronUp className="h-3 w-3" />
                        ) : (
                          <ChevronDown className="h-3 w-3" />
                        )}
                      </button>

                      {isEvidenceOpen && (
                        <div
                          className="mt-2 rounded-lg border p-2.5 space-y-1.5"
                          style={{
                            backgroundColor: "var(--surface)",
                            borderColor: "var(--border)",
                          }}
                        >
                          {s.evidence.map((item, idx) => (
                            <div key={idx} className="flex items-center justify-between text-[11px]">
                              <span style={{ color: "var(--text-2)" }}>{item.label}</span>
                              <span className="font-medium font-mono" style={{ color: "var(--text-1)" }}>
                                {item.value}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Actions */}
                  <div className="mt-3 flex items-center justify-end gap-2 border-t pt-2.5" style={{ borderColor: "var(--border)" }}>
                    <button
                      type="button"
                      onClick={() => onDismiss(s.id)}
                      className="rounded-lg px-2.5 py-1 text-xs font-medium transition-colors hover:opacity-80"
                      style={{
                        color: "var(--text-2)",
                        backgroundColor: "transparent",
                      }}
                    >
                      Dismiss
                    </button>
                    <button
                      type="button"
                      onClick={() => onInvestigate(getInvestigationPrompt(s))}
                      className="flex items-center gap-1 rounded-lg px-3 py-1 text-xs font-semibold transition-opacity hover:opacity-90"
                      style={{
                        backgroundColor: "var(--surface)",
                        color: "var(--text-1)",
                        border: "1px solid var(--border)",
                      }}
                    >
                      <span>Investigate in EVE</span>
                      <ArrowUpRight className="h-3 w-3" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
};
