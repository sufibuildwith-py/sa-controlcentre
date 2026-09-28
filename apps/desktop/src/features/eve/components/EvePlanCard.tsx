import React from "react";
import {
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Loader2,
  ArrowRight,
  Landmark,
  FileCheck,
} from "lucide-react";
import type { EvePlan } from "../eve.types";

interface EvePlanCardProps {
  plan: EvePlan;
  onConfirm: (plan: EvePlan) => void;
  onCancel: (plan: EvePlan) => void;
  isConfirming?: boolean;
  isCancelling?: boolean;
}

export function EvePlanCard({
  plan,
  onConfirm,
  onCancel,
  isConfirming = false,
  isCancelling = false,
}: EvePlanCardProps) {
  const isProposed = plan.status === "PROPOSED";
  const isExecuting = plan.status === "EXECUTING" || isConfirming;
  const isCompleted = plan.status === "COMPLETED";
  const isStale = plan.status === "STALE_PLAN";
  const isCancelled = plan.status === "CANCELLED";

  const riskBadgeStyles: Record<string, string> = {
    FINANCIAL_WRITE: "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20",
    SAFE_OPERATION: "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20",
    READ: "bg-[var(--surface-soft)] text-[var(--text-2)] border-[var(--border)]",
    BLOCKED: "bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20",
  };

  return (
    <div
      data-testid="eve-plan-card"
      className="mt-4 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5 shadow-lg transition-all duration-200"
    >
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border-soft)] pb-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber-500/15 text-amber-600 dark:text-amber-400 border border-amber-500/20">
            <Landmark className="h-4 w-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-[var(--text-3)]">
                Governed Execution Plan
              </span>
              <span className="font-mono text-[10px] text-[var(--text-3)]" title={plan.planHash}>
                #{plan.planHash.substring(0, 10)}
              </span>
            </div>
            <h4 className="text-sm font-medium text-[var(--text-1)]">{plan.summary}</h4>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Risk Badge */}
          <span
            className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${
              riskBadgeStyles[plan.riskTier] || riskBadgeStyles.READ
            }`}
          >
            <ShieldAlert className="h-3 w-3" />
            {plan.riskTier.replace("_", " ")}
          </span>

          {/* Status Badge */}
          {isCompleted && (
            <span className="inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-0.5 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
              <CheckCircle2 className="h-3 w-3" />
              Verified & Posted
            </span>
          )}
          {isStale && (
            <span className="inline-flex items-center gap-1 rounded-full border border-rose-500/20 bg-rose-500/10 px-2.5 py-0.5 text-[11px] font-medium text-rose-600 dark:text-rose-400">
              <AlertTriangle className="h-3 w-3" />
              Stale Plan
            </span>
          )}
          {isCancelled && (
            <span className="inline-flex items-center gap-1 rounded-full border border-[var(--border)] bg-[var(--surface-soft)] px-2.5 py-0.5 text-[11px] font-medium text-[var(--text-3)]">
              <XCircle className="h-3 w-3" />
              Cancelled
            </span>
          )}
          {isExecuting && (
            <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/20 bg-amber-500/10 px-2.5 py-0.5 text-[11px] font-medium text-amber-600 dark:text-amber-300">
              <Loader2 className="h-3 w-3 animate-spin" />
              Executing & Verifying
            </span>
          )}
        </div>
      </div>

      {/* Action Breakdown */}
      <div className="mt-4 space-y-3">
        {plan.actions.map((act) => {
          const params = act.parameters || {};
          const amount = params.amount ? `₹${params.amount}` : null;
          const payer = params.payerAccount ? String(params.payerAccount) : null;

          return (
            <div
              key={act.actionId}
              className="rounded-xl border border-[var(--border-soft)] bg-[var(--surface-soft)] p-3 text-xs"
            >
              <div className="flex items-center justify-between text-[var(--text-3)]">
                <span className="font-mono text-[11px] text-[var(--text-3)]">Action {act.seq}</span>
                <span className="font-mono text-[11px] uppercase tracking-wide text-[var(--text-2)]">
                  {act.commandType}
                </span>
              </div>

              <div className="mt-2 grid grid-cols-2 gap-2 text-[var(--text-2)] sm:grid-cols-4">
                <div>
                  <span className="block text-[10px] text-[var(--text-3)]">Target Entity</span>
                  <span className="font-medium text-[var(--text-1)]">
                    {act.targetEntityName || "N/A"}
                  </span>
                </div>
                {amount && (
                  <div>
                    <span className="block text-[10px] text-[var(--text-3)]">Amount</span>
                    <span className="font-mono font-medium text-amber-600 dark:text-amber-400">{amount}</span>
                  </div>
                )}
                {payer && (
                  <div>
                    <span className="block text-[10px] text-[var(--text-3)]">Payer Account</span>
                    <span className="font-medium text-[var(--text-1)]">
                      {payer} {payer === "AZ-2" ? "(Azeem Khan)" : payer === "AK-2" ? "(Akash)" : ""}
                    </span>
                  </div>
                )}
                <div>
                  <span className="block text-[10px] text-[var(--text-3)]">Permission</span>
                  <span className="font-mono text-[var(--text-2)]">{act.requiredPermission}</span>
                </div>
              </div>

              {act.estimatedEffect && (
                <div className="mt-2.5 flex items-start gap-1.5 rounded-lg bg-[var(--surface)] p-2 text-[11px] text-[var(--text-2)] border border-[var(--border-soft)]">
                  <ArrowRight className="mt-0.5 h-3 w-3 shrink-0 text-amber-500" />
                  <span>{act.estimatedEffect}</span>
                </div>
              )}

              {/* Action Verification Status */}
              {act.status === "VERIFIED" && (
                <div className="mt-2 flex items-center gap-1.5 text-[11px] text-emerald-600 dark:text-emerald-400">
                  <FileCheck className="h-3.5 w-3.5" />
                  <span>Verified in PostgreSQL (double-entry allocations & audit confirmed)</span>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Stale Plan Alert */}
      {isStale && (
        <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-700 dark:text-rose-300">
          <AlertTriangle className="h-4 w-4 shrink-0 text-rose-500" />
          <span>
            This plan is stale. The underlying canonical database records changed after this plan
            was proposed. Please submit a new query to calculate a fresh plan.
          </span>
        </div>
      )}

      {/* Actions */}
      {isProposed && (
        <div className="mt-4 flex flex-wrap items-center justify-end gap-2.5 pt-2">
          <button
            type="button"
            onClick={() => onCancel(plan)}
            disabled={isConfirming || isCancelling}
            className="rounded-xl border border-[var(--border)] bg-[var(--surface-soft)] hover:bg-[var(--surface-raised)] px-4 py-2 text-xs font-medium text-[var(--text-2)] hover:text-[var(--text-1)] transition-colors disabled:opacity-50"
          >
            {isCancelling ? "Cancelling..." : "Cancel"}
          </button>
          <button
            type="button"
            onClick={() => onConfirm(plan)}
            disabled={isConfirming || isCancelling}
            data-testid="confirm-plan-btn"
            className="inline-flex items-center gap-2 rounded-xl bg-amber-500 hover:bg-amber-400 px-5 py-2 text-xs font-medium text-neutral-950 transition-transform active:scale-[0.98] disabled:opacity-50 shadow-sm"
          >
            {isConfirming ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                Executing & Verifying...
              </>
            ) : (
              <>
                <CheckCircle2 className="h-3.5 w-3.5" />
                Confirm & Post Payment
              </>
            )}
          </button>
        </div>
      )}
    </div>
  );
}
