import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Lock, Plus, WalletCards } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../../lib/api";
import type {
  PayrollAdjustment,
  PayrollItem,
  PayrollPeriod,
} from "../../types/domain";
import {
  ConfirmAction,
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SAModal,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
export function PayrollPage() {
  const { id } = useParams(),
    location = useLocation(),
    navigate = useNavigate(),
    client = useQueryClient();
  const [adjustItem, setAdjustItem] = useState<PayrollItem | null>(null),
    [adjustment, setAdjustment] = useState({
      type: "BONUS" as PayrollAdjustment["type"],
      amount: "3000",
      reason: "Performance bonus",
    }),
    [lockOpen, setLockOpen] = useState(false);
  const periods = useQuery({
    queryKey: ["payroll"],
    queryFn: () => api<PayrollPeriod[]>("/payroll"),
  });
  const detail = useQuery({
    queryKey: ["payroll", id],
    queryFn: () => api<PayrollPeriod>(`/payroll/${id}`),
    enabled: !!id,
  });
  const invalidate = (data?: PayrollPeriod) => {
    client.invalidateQueries({ queryKey: ["payroll"] });
    client.invalidateQueries({ queryKey: ["dashboard"] });
    if (data) client.setQueryData(["payroll", data.id], data);
  };
  const current = new Date();
  const calculate = useMutation({
    mutationFn: () =>
      api<PayrollPeriod>(
        `/payroll/${current.getFullYear()}/${current.getMonth() + 1}/calculate`,
        { method: "POST" },
      ),
    onSuccess: (data) => {
      invalidate(data);
      navigate(`/payroll/${data.id}`);
    },
  });
  const action = useMutation({
    mutationFn: (name: "approve" | "mark-all-paid" | "lock") =>
      api<PayrollPeriod>(`/payroll/${id}/${name}`, { method: "POST" }),
    onSuccess: (data) => {
      invalidate(data);
      setLockOpen(false);
    },
  });
  const adjust = useMutation({
    mutationFn: () =>
      api<PayrollPeriod>(`/payroll/${id}/adjustments`, {
        method: "POST",
        ...json({
          itemId: adjustItem?.id,
          type: adjustment.type,
          amountMinor: Math.round(Number(adjustment.amount) * 100),
          reason: adjustment.reason,
        }),
      }),
    onSuccess: (data) => {
      invalidate(data);
      setAdjustItem(null);
    },
  });
  const payItem = useMutation({
    mutationFn: (itemId: string) =>
      api<PayrollPeriod>(`/payroll/${id}/items/${itemId}/mark-paid`, {
        method: "POST",
      }),
    onSuccess: invalidate,
  });
  const selected = detail.data ?? (!id ? periods.data?.[0] : undefined);
  useEffect(() => {
    if (new URLSearchParams(location.search).get("adjust") !== "1" || !selected)
      return;
    if (!id) navigate(`/payroll/${selected.id}?adjust=1`, { replace: true });
    else if (selected.status === "CALCULATED" && selected.items[0])
      setAdjustItem(selected.items[0]);
  }, [id, location.search, navigate, selected]);
  if (periods.isPending || (!!id && detail.isPending)) return <SkeletonCard />;
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Payroll</h1>
          <p>Immutable salary snapshots, adjustments and payment records.</p>
        </div>
        <SAButton
          variant="primary"
          disabled={calculate.isPending}
          onClick={() => calculate.mutate()}
        >
          <WalletCards size={16} />
          {selected?.status === "CALCULATED"
            ? "Recalculate"
            : "Calculate payroll"}
        </SAButton>
      </div>
      {periods.isError ? (
        <EmptyState
          title="Payroll could not be loaded"
          description="Check the API and try again."
        />
      ) : !selected ? (
        <EmptyState
          title="No payroll calculated"
          description="Calculate the current period to snapshot employee compensation."
          action={
            <SAButton variant="primary" onClick={() => calculate.mutate()}>
              Calculate payroll
            </SAButton>
          }
        />
      ) : (
        <>
          <div className="payroll-periods">
            {periods.data?.map((p) => (
              <button
                key={p.id}
                className={selected.id === p.id ? "active" : ""}
                onClick={() => navigate(`/payroll/${p.id}`)}
              >
                <span>
                  {month(p.month)} {p.year}
                </span>
                <StatusBadge
                  tone={
                    p.status === "LOCKED"
                      ? "success"
                      : p.status === "APPROVED"
                        ? "info"
                        : "neutral"
                  }
                >
                  {p.status}
                </StatusBadge>
              </button>
            ))}
          </div>
          <div className="payroll-summary">
            <SABentoCard>
              <span>Total payroll</span>
              <strong>{money(selected.totalMinor)}</strong>
            </SABentoCard>
            <SABentoCard>
              <span>Paid</span>
              <strong>{money(selected.paidMinor)}</strong>
            </SABentoCard>
            <SABentoCard>
              <span>Pending</span>
              <strong>{money(selected.pendingMinor)}</strong>
            </SABentoCard>
            <SABentoCard>
              <span>Policy</span>
              <strong className="policy-name">
                {selected.policy.replaceAll("_", " ")}
              </strong>
            </SABentoCard>
          </div>
          <div className="payroll-actions">
            {selected.status === "CALCULATED" && (
              <>
                <SAButton onClick={() => calculate.mutate()}>
                  Recalculate
                </SAButton>
                <SAButton
                  variant="primary"
                  onClick={() => action.mutate("approve")}
                >
                  Approve payroll
                </SAButton>
              </>
            )}
            {selected.status === "APPROVED" && (
              <SAButton
                variant="primary"
                onClick={() => action.mutate("mark-all-paid")}
              >
                Mark all paid
              </SAButton>
            )}
            {selected.status === "PAID" && (
              <SAButton variant="primary" onClick={() => setLockOpen(true)}>
                <Lock size={15} />
                Lock payroll
              </SAButton>
            )}
            {selected.status === "LOCKED" && (
              <StatusBadge tone="success">
                Immutable financial history
              </StatusBadge>
            )}
          </div>
          <div className="payroll-list">
            {selected.items.map((item) => (
              <SABentoCard key={item.id} className="payroll-row">
                <div>
                  <strong>{item.employeeName}</strong>
                  <span>
                    {money(item.baseSalaryMinor)} base{" "}
                    {item.bonusMinor
                      ? `· +${money(item.bonusMinor)} bonus`
                      : ""}
                  </span>
                </div>
                <div className="payroll-breakdown">
                  <span>
                    Attendance <b>−{money(item.attendanceDeductionMinor)}</b>
                  </span>
                  <span>
                    Adjustments{" "}
                    <b>
                      {money(
                        item.overtimeMinor +
                          item.bonusMinor -
                          item.advanceDeductionMinor +
                          item.manualAdjustmentMinor,
                      )}
                    </b>
                  </span>
                </div>
                <div className="payroll-net">
                  <strong>{money(item.netSalaryMinor)}</strong>
                  <StatusBadge
                    tone={item.paymentStatus === "PAID" ? "success" : "warning"}
                  >
                    {item.paymentStatus}
                  </StatusBadge>
                </div>
                {selected.status === "CALCULATED" && (
                  <SAButton size="sm" onClick={() => setAdjustItem(item)}>
                    <Plus size={14} />
                    Adjust
                  </SAButton>
                )}
                {selected.status === "APPROVED" &&
                  item.paymentStatus === "PENDING" && (
                    <SAButton
                      size="sm"
                      disabled={payItem.isPending}
                      onClick={() => payItem.mutate(item.id)}
                    >
                      Mark paid
                    </SAButton>
                  )}
              </SABentoCard>
            ))}
          </div>
        </>
      )}
      <SAModal
        open={!!adjustItem}
        onOpenChange={(v) => !v && setAdjustItem(null)}
        title={`Adjust ${adjustItem?.employeeName ?? "payroll"}`}
        description="Every financial adjustment keeps its reason and creator."
      >
        <FormField label="Type">
          <select
            aria-label="Adjustment type"
            value={adjustment.type}
            onChange={(e) =>
              setAdjustment({
                ...adjustment,
                type: e.target.value as PayrollAdjustment["type"],
              })
            }
          >
            {[
              "BONUS",
              "DEDUCTION",
              "OVERTIME",
              "ADVANCE",
              "CORRECTION",
              "OTHER",
            ].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Amount (₹)">
          <input
            aria-label="Adjustment amount"
            type="number"
            value={adjustment.amount}
            onChange={(e) =>
              setAdjustment({ ...adjustment, amount: e.target.value })
            }
          />
        </FormField>
        <FormField label="Reason">
          <input
            aria-label="Adjustment reason"
            value={adjustment.reason}
            onChange={(e) =>
              setAdjustment({ ...adjustment, reason: e.target.value })
            }
          />
        </FormField>
        {adjust.error && <p className="form-error">{adjust.error.message}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setAdjustItem(null)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              !adjustment.reason || !adjustment.amount || adjust.isPending
            }
            onClick={() => adjust.mutate()}
          >
            Add adjustment
          </SAButton>
        </div>
      </SAModal>
      <ConfirmAction
        open={lockOpen}
        onOpenChange={setLockOpen}
        title={`Lock ${selected ? month(selected.month) : ""} payroll?`}
        description="This period will be preserved as financial history. Future attendance or salary changes will not alter it."
        confirmLabel="Lock payroll"
        onConfirm={() => action.mutate("lock")}
        pending={action.isPending}
      />
    </>
  );
}
const money = (minor: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(minor / 100);
const month = (n: number) =>
  new Intl.DateTimeFormat("en-IN", { month: "long" }).format(
    new Date(2026, n - 1, 1),
  );
