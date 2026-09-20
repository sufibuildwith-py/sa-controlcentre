import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Clock3, Lock, Plus, Search, WalletCards } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../../lib/api";
import type {
  PayrollAdjustment,
  PayrollItem,
  PayrollPaymentMethod,
  PayrollPeriod,
  PayrollPeriodSummary,
} from "../../types/domain";
import {
  ConfirmAction,
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SADrawer,
  SAModal,
  SASegmentedControl,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";

type Filter =
  | "ALL"
  | "UNPAID"
  | "PARTIALLY_PAID"
  | "PAID"
  | "BONUS"
  | "ADVANCE"
  | "BALANCE";
const nowLocal = () => {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

export function PayrollPage() {
  const { id } = useParams(),
    location = useLocation(),
    navigate = useNavigate(),
    client = useQueryClient();
  const [filter, setFilter] = useState<Filter>(
    new URLSearchParams(location.search).get("paymentStatus") === "OPEN"
      ? "BALANCE"
      : "ALL",
  );
  const [search, setSearch] = useState("");
  const [detailItem, setDetailItem] = useState<PayrollItem | null>(null);
  const [adjustItem, setAdjustItem] = useState<PayrollItem | null>(null);
  const [payItem, setPayItem] = useState<PayrollItem | null>(null);
  const [adjustmentOpen, setAdjustmentOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [adjustment, setAdjustment] = useState({
    type: "" as "" | PayrollAdjustment["type"],
    amount: "",
    reason: "",
  });
  const [payment, setPayment] = useState({
    requestId: "",
    amount: "",
    paidAt: nowLocal(),
    paymentMethod: "BANK_TRANSFER" as PayrollPaymentMethod,
    reference: "",
    note: "",
  });
  const [lockOpen, setLockOpen] = useState(false);
  const periods = useQuery({
    queryKey: ["payroll"],
    queryFn: () => api<PayrollPeriodSummary[]>("/payroll"),
  });
  const detail = useQuery({
    queryKey: ["payroll", id],
    queryFn: () => api<PayrollPeriod>(`/payroll/${id}`),
    enabled: !!id,
  });
  const selected = detail.data;
  const ledger = useQuery({
    queryKey: ["payroll-ledger", selected?.id, detailItem?.id],
    queryFn: () =>
      api<PayrollItem>(
        `/payroll/${selected?.id}/items/${detailItem?.id}/payments`,
      ),
    enabled: !!selected && !!detailItem,
  });
  const invalidate = (data?: PayrollPeriod) => {
    client.invalidateQueries({ queryKey: ["payroll"] });
    client.invalidateQueries({ queryKey: ["payroll-ledger"] });
    client.invalidateQueries({ queryKey: ["dashboard"] });
    client.invalidateQueries({ queryKey: ["employee-operations"] });
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
    mutationFn: (name: "approve" | "lock") =>
      api<PayrollPeriod>(`/payroll/${id}/${name}`, { method: "POST" }),
    onSuccess: (data) => {
      invalidate(data);
      setLockOpen(false);
    },
  });
  const adjust = useMutation({
    mutationFn: () =>
      api<PayrollPeriod>(`/payroll/${selected?.id}/adjustments`, {
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
      setAdjustmentOpen(false);
      setAdjustItem(null);
      setDetailItem(data.items.find((x) => x.id === detailItem?.id) ?? null);
    },
  });
  const recordPayment = useMutation({
    mutationFn: () =>
      api<PayrollPeriod>(
        `/payroll/${selected?.id}/items/${payItem?.id}/payments`,
        {
          method: "POST",
          ...json({
            requestId: payment.requestId,
            amountMinor: Math.round(Number(payment.amount) * 100),
            paidAt: new Date(payment.paidAt).toISOString(),
            paymentMethod: payment.paymentMethod,
            reference: payment.reference || null,
            note: payment.note || null,
          }),
        },
      ),
    onSuccess: (data) => {
      invalidate(data);
      setPaymentOpen(false);
      setPayItem(null);
      setDetailItem(data.items.find((x) => x.id === detailItem?.id) ?? null);
    },
  });

  const openAdjustment = (item: PayrollItem | null = null) => {
    setAdjustItem(item);
    setAdjustment({ type: "", amount: "", reason: "" });
    setAdjustmentOpen(true);
  };
  const openPayment = (item: PayrollItem | null = null) => {
    setPayItem(item);
    setPayment({
      requestId: crypto.randomUUID(),
      amount: "",
      paidAt: nowLocal(),
      paymentMethod: "BANK_TRANSFER",
      reference: "",
      note: "",
    });
    setPaymentOpen(true);
  };
  useEffect(() => {
    if (!id && periods.data?.[0])
      navigate(`/payroll/${periods.data[0].id}`, { replace: true });
  }, [id, navigate, periods.data]);
  useEffect(() => {
    if (new URLSearchParams(location.search).get("adjust") !== "1" || !selected)
      return;
    if (!id) navigate(`/payroll/${selected.id}?adjust=1`, { replace: true });
    else if (selected.status === "CALCULATED") openAdjustment();
  }, [id, location.search, navigate, selected]);
  useEffect(() => {
    if (!selected || !detailItem) return;
    setDetailItem(
      selected.items.find((item) => item.id === detailItem.id) ?? null,
    );
  }, [selected]);
  const rows = useMemo(
    () =>
      (selected?.items ?? []).filter((item) => {
        const text = `${item.employeeName} ${item.employeeRole}`.toLowerCase();
        if (!text.includes(search.toLowerCase())) return false;
        if (filter === "BONUS") return item.bonusMinor > 0;
        if (filter === "ADVANCE") return item.advanceDeductionMinor > 0;
        if (filter === "BALANCE") return item.remaining > 0;
        return filter === "ALL" || item.paymentStatus === filter;
      }),
    [selected, filter, search],
  );

  if (periods.isPending || !id || detail.isPending) return <SkeletonCard />;
  if (periods.isError || detail.isError)
    return (
      <EmptyState
        title="Payroll could not be loaded"
        description="Check the API and try again."
      />
    );
  if (!selected)
    return (
      <EmptyState
        title="No payroll calculated"
        description="Calculate the current period to snapshot employee compensation."
        action={
          <SAButton variant="primary" onClick={() => calculate.mutate()}>
            Calculate payroll
          </SAButton>
        }
      />
    );
  const mutableAdjustments = selected.status === "CALCULATED",
    mutablePayments = selected.status === "APPROVED";

  return (
    <>
      <div className="page-title payroll-title">
        <div>
          <span className="eyebrow">Payroll ledger</span>
          <h1>
            {month(selected.month)} {selected.year}
          </h1>
          <p>
            Salary snapshots, adjustments and every payment in one auditable
            record.
          </p>
        </div>
        <div className="payroll-header-actions">
          {mutableAdjustments && (
            <SAButton onClick={() => openAdjustment()}>
              <Plus size={15} />
              Add adjustment
            </SAButton>
          )}
          {mutablePayments && (
            <SAButton variant="primary" onClick={() => openPayment()}>
              <WalletCards size={15} />
              Record payment
            </SAButton>
          )}
          {selected.status === "PAID" && (
            <SAButton variant="primary" onClick={() => setLockOpen(true)}>
              <Lock size={15} />
              Lock payroll
            </SAButton>
          )}
        </div>
      </div>
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
          <small>{selected.items.length} employees</small>
        </SABentoCard>
        <SABentoCard>
          <span>Paid</span>
          <strong>{money(selected.paidMinor)}</strong>
          <small>{selected.paidCount} fully paid</small>
        </SABentoCard>
        <SABentoCard>
          <span>Remaining</span>
          <strong>{money(selected.remainingMinor)}</strong>
          <small>
            {selected.partiallyPaidCount} partial · {selected.unpaidCount}{" "}
            unpaid
          </small>
        </SABentoCard>
        <SABentoCard>
          <span>Payroll state</span>
          <strong className="policy-name">
            {selected.status.replaceAll("_", " ")}
          </strong>
          <small>{selected.policy.replaceAll("_", " ").toLowerCase()}</small>
        </SABentoCard>
      </div>
      <div className="payroll-actions">
        {mutableAdjustments && (
          <>
            <SAButton onClick={() => calculate.mutate()}>Recalculate</SAButton>
            <SAButton
              variant="primary"
              onClick={() => action.mutate("approve")}
            >
              Approve payroll
            </SAButton>
          </>
        )}
        {selected.status === "LOCKED" && (
          <StatusBadge tone="success">Immutable financial history</StatusBadge>
        )}
      </div>
      <div className="payroll-toolbar">
        <SASegmentedControl
          value={filter}
          onChange={setFilter}
          label="Payroll filter"
          items={[
            { value: "ALL", label: "All" },
            { value: "UNPAID", label: "Unpaid" },
            { value: "PARTIALLY_PAID", label: "Partially paid" },
            { value: "PAID", label: "Paid" },
          ]}
        />
        <div className="payroll-secondary-filters">
          {(["BONUS", "ADVANCE", "BALANCE"] as Filter[]).map((value) => (
            <button
              key={value}
              className={filter === value ? "active" : ""}
              onClick={() => setFilter(value)}
            >
              {value === "BALANCE"
                ? "Has balance"
                : value === "ADVANCE"
                  ? "Took advance"
                  : "Received bonus"}
            </button>
          ))}
        </div>
        <label className="payroll-search">
          <Search size={14} />
          <input
            aria-label="Search payroll employees"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search employees"
          />
        </label>
      </div>
      <div className="payroll-list">
        {rows.length === 0 ? (
          <EmptyState
            title="No matching employees"
            description="Change the filter or search to see payroll records."
          />
        ) : (
          rows.map((item) => (
            <SABentoCard
              key={item.id}
              className="payroll-row"
              interactive
              onClick={() => setDetailItem(item)}
            >
              <div className="payroll-person">
                <strong>{item.employeeName}</strong>
                <span>{item.employeeRole}</span>
                <StatusBadge tone={statusTone(item.paymentStatus)}>
                  {item.netPayable === 0
                    ? "NO PAYMENT DUE"
                    : item.paymentStatus.replaceAll("_", " ")}
                </StatusBadge>
              </div>
              <div className="payroll-components">
                <span>
                  Base <b>{money(item.baseSalaryMinor)}</b>
                </span>
                {item.bonusMinor > 0 && (
                  <span className="money-positive">
                    Bonus <b>+{money(item.bonusMinor)}</b>
                  </span>
                )}
                {item.advanceDeductionMinor > 0 && (
                  <span className="money-negative">
                    Advance <b>−{money(item.advanceDeductionMinor)}</b>
                  </span>
                )}
              </div>
              <div className="payroll-owed">
                <span>Net payable</span>
                <strong>{money(item.netPayable)}</strong>
                <small>
                  Paid {money(item.totalPaid)} · Remaining{" "}
                  {money(item.remaining)}
                </small>
              </div>
              <div className="payroll-row-action">
                {item.lastPayment && (
                  <small>
                    Last payment
                    <br />
                    <b>
                      {money(item.lastPayment.amountMinor)} ·{" "}
                      {shortDate(item.lastPayment.paidAt)} ·{" "}
                      {method(item.lastPayment.paymentMethod)}
                    </b>
                  </small>
                )}
                <div>
                  <SAButton
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      setDetailItem(item);
                    }}
                  >
                    View details
                  </SAButton>
                  {mutablePayments && item.remaining > 0 && (
                    <SAButton
                      size="sm"
                      variant="primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        openPayment(item);
                      }}
                    >
                      Record payment
                    </SAButton>
                  )}
                </div>
              </div>
            </SABentoCard>
          ))
        )}
      </div>

      <SAModal
        open={adjustmentOpen}
        onOpenChange={(v) => !v && setAdjustmentOpen(false)}
        title={`Adjust ${adjustItem?.employeeName ?? "payroll"}`}
        description="An adjustment changes what the employee is owed and keeps its reason and creator."
      >
        <div className="form-grid">
          <FormField label="Employee">
            <select
              aria-label="Adjustment employee"
              value={adjustItem?.id ?? ""}
              onChange={(e) =>
                setAdjustItem(
                  selected.items.find((x) => x.id === e.target.value) ?? null,
                )
              }
            >
              <option value="">Select employee…</option>
              {selected.items.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.employeeName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Type">
            <select
              aria-label="Adjustment type"
              value={adjustment.type}
              onChange={(e) =>
                setAdjustment({
                  ...adjustment,
                  type: e.target.value as "" | PayrollAdjustment["type"],
                })
              }
            >
              <option value="">Select type…</option>
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
          <FormField
            label="Amount (₹)"
            hint={
              adjustment.type === "ADVANCE"
                ? "Advances reduce the salary still owed."
                : undefined
            }
          >
            <input
              aria-label="Adjustment amount"
              type="number"
              min="0.01"
              step="0.01"
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
        </div>
        {adjust.error && <p className="form-error">{adjust.error.message}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setAdjustmentOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              !adjustItem ||
              !adjustment.type ||
              !adjustment.reason.trim() ||
              Number(adjustment.amount) <= 0 ||
              adjust.isPending
            }
            onClick={() => adjust.mutate()}
          >
            Add adjustment
          </SAButton>
        </div>
      </SAModal>

      <SAModal
        open={paymentOpen}
        onOpenChange={(v) => !v && setPaymentOpen(false)}
        title={`Record payment${payItem ? ` · ${payItem.employeeName}` : ""}`}
        description={`Remaining balance ${money(payItem?.remaining ?? 0)}. Partial payments are supported.`}
      >
        <div className="form-grid">
          <FormField label="Employee">
            <select
              aria-label="Payment employee"
              value={payItem?.id ?? ""}
              onChange={(e) =>
                setPayItem(
                  selected.items.find((x) => x.id === e.target.value) ?? null,
                )
              }
            >
              <option value="">Select employee…</option>
              {selected.items
                .filter((x) => x.remaining > 0)
                .map((item) => (
                  <option value={item.id} key={item.id}>
                    {item.employeeName}
                  </option>
                ))}
            </select>
          </FormField>
          <FormField label="Amount (₹)">
            <input
              aria-label="Payment amount"
              type="number"
              min="0.01"
              max={(payItem?.remaining ?? 0) / 100}
              step="0.01"
              value={payment.amount}
              onChange={(e) =>
                setPayment({ ...payment, amount: e.target.value })
              }
            />
          </FormField>
          <FormField label="Date and time">
            <input
              aria-label="Payment date and time"
              type="datetime-local"
              max={nowLocal()}
              value={payment.paidAt}
              onChange={(e) =>
                setPayment({ ...payment, paidAt: e.target.value })
              }
            />
          </FormField>
          <FormField label="Payment method">
            <select
              aria-label="Payment method"
              value={payment.paymentMethod}
              onChange={(e) =>
                setPayment({
                  ...payment,
                  paymentMethod: e.target.value as PayrollPaymentMethod,
                })
              }
            >
              {["CASH", "BANK_TRANSFER", "UPI", "CHEQUE", "OTHER"].map((x) => (
                <option key={x} value={x}>
                  {method(x as PayrollPaymentMethod)}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Reference">
            <input
              aria-label="Payment reference"
              value={payment.reference}
              onChange={(e) =>
                setPayment({ ...payment, reference: e.target.value })
              }
              placeholder="Optional transaction reference"
            />
          </FormField>
          <FormField label="Note">
            <input
              aria-label="Payment note"
              value={payment.note}
              onChange={(e) => setPayment({ ...payment, note: e.target.value })}
              placeholder="Optional note"
            />
          </FormField>
        </div>
        {recordPayment.error && (
          <p className="form-error">{recordPayment.error.message}</p>
        )}
        <div className="modal-actions">
          <SAButton onClick={() => setPaymentOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              !payItem ||
              !payment.paidAt ||
              Number(payment.amount) <= 0 ||
              Math.round(Number(payment.amount) * 100) >
                (payItem?.remaining ?? 0) ||
              recordPayment.isPending
            }
            onClick={() => recordPayment.mutate()}
          >
            Record payment
          </SAButton>
        </div>
      </SAModal>

      <SADrawer
        open={!!detailItem}
        onOpenChange={(v) => !v && setDetailItem(null)}
        title={detailItem?.employeeName ?? "Payroll details"}
        description={`${month(selected.month)} ${selected.year} · immutable salary snapshot`}
      >
        {ledger.isPending ? (
          <SkeletonCard />
        ) : (
          <PayrollDrawer
            item={ledger.data ?? detailItem}
            canAdjust={mutableAdjustments}
            canPay={mutablePayments}
            onAdjust={() => detailItem && openAdjustment(detailItem)}
            onPay={() => detailItem && openPayment(detailItem)}
          />
        )}
      </SADrawer>
      <ConfirmAction
        open={lockOpen}
        onOpenChange={setLockOpen}
        title={`Lock ${month(selected.month)} payroll?`}
        description="This paid period will become immutable. Salary snapshots, adjustments, and payment history will remain preserved."
        confirmLabel="Lock payroll"
        onConfirm={() => action.mutate("lock")}
        pending={action.isPending}
      />
    </>
  );
}

function PayrollDrawer({
  item,
  canAdjust,
  canPay,
  onAdjust,
  onPay,
}: {
  item: PayrollItem | null;
  canAdjust: boolean;
  canPay: boolean;
  onAdjust: () => void;
  onPay: () => void;
}) {
  if (!item) return null;
  return (
    <div className="payroll-drawer">
      <section>
        <span className="eyebrow">Pay breakdown</span>
        <Breakdown label="Base salary" value={item.baseSalaryMinor} />
        <Breakdown
          label="Attendance deduction"
          value={-item.attendanceDeductionMinor}
        />
        <Breakdown label="Bonus" value={item.bonusMinor} />
        <Breakdown label="Overtime" value={item.overtimeMinor} />
        <Breakdown
          label="Advance · already received"
          value={-item.advanceDeductionMinor}
        />
        <Breakdown
          label="Other deductions / corrections"
          value={item.manualAdjustmentMinor}
        />
        <div className="drawer-net">
          <span>Net payable</span>
          <strong>{money(item.netPayable)}</strong>
        </div>
      </section>
      <section>
        <span className="eyebrow">Payment summary</span>
        <div className="drawer-summary">
          <div>
            <span>Total paid</span>
            <b>{money(item.totalPaid)}</b>
          </div>
          <div>
            <span>Remaining</span>
            <b>{money(item.remaining)}</b>
          </div>
          <StatusBadge tone={statusTone(item.paymentStatus)}>
            {item.netPayable === 0
              ? "NO PAYMENT DUE"
              : item.paymentStatus.replaceAll("_", " ")}
          </StatusBadge>
        </div>
      </section>
      <section>
        <span className="eyebrow">Payment history</span>
        {item.payments.length ? (
          <div className="payment-timeline">
            {item.payments.map((p) => (
              <div key={p.id}>
                <span className="timeline-node" />
                <div>
                  <time>{fullDate(p.paidAt)}</time>
                  <strong>{money(p.amountMinor)}</strong>
                  <small>
                    {method(p.paymentMethod)}
                    {p.reference ? ` · ${p.reference}` : ""}
                  </small>
                  {p.note && <p>{p.note}</p>}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="drawer-empty">No payments recorded.</p>
        )}
      </section>
      <section>
        <span className="eyebrow">Adjustment history</span>
        {item.adjustments.length ? (
          <div className="adjustment-history">
            {item.adjustments.map((a) => (
              <div key={a.id}>
                <StatusBadge tone={adjustmentTone(a.type)}>
                  {a.type}
                </StatusBadge>
                <div>
                  <b>{signedAdjustment(a)}</b>
                  <span>{a.reason}</span>
                  <time>{fullDate(a.createdAt)}</time>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="drawer-empty">No adjustments recorded.</p>
        )}
      </section>
      {(canAdjust || (canPay && item.remaining > 0)) && (
        <footer>
          {canAdjust && <SAButton onClick={onAdjust}>Add adjustment</SAButton>}
          {canPay && item.remaining > 0 && (
            <SAButton variant="primary" onClick={onPay}>
              Record payment
            </SAButton>
          )}
        </footer>
      )}
    </div>
  );
}
function Breakdown({ label, value }: { label: string; value: number }) {
  return (
    <div className="breakdown-line">
      <span>{label}</span>
      <b
        className={
          value > 0 ? "money-positive" : value < 0 ? "money-negative" : ""
        }
      >
        {value > 0 ? "+" : value < 0 ? "−" : ""}
        {money(Math.abs(value))}
      </b>
    </div>
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
const shortDate = (value: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(
    new Date(value),
  );
const fullDate = (value: string) =>
  new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
const method = (value: PayrollPaymentMethod) =>
  value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
const statusTone = (
  value: PayrollItem["paymentStatus"],
): "success" | "warning" | "neutral" =>
  value === "PAID"
    ? "success"
    : value === "PARTIALLY_PAID"
      ? "warning"
      : "neutral";
const adjustmentTone = (
  value: PayrollAdjustment["type"],
): "success" | "warning" | "neutral" =>
  ["BONUS", "OVERTIME"].includes(value)
    ? "success"
    : ["DEDUCTION", "ADVANCE"].includes(value)
      ? "warning"
      : "neutral";
const signedAdjustment = (a: PayrollAdjustment) =>
  `${["DEDUCTION", "ADVANCE"].includes(a.type) ? "−" : a.amountMinor > 0 ? "+" : "−"}${money(Math.abs(a.amountMinor))}`;
