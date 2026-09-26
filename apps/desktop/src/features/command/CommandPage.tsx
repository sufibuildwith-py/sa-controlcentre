import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type HTMLMotionProps } from "motion/react";
import {
  AlertCircle,
  ArrowRight,
  ArrowUpRight,
  Calendar,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clapperboard,
  Clock,
  CreditCard,
  FileText,
  PieChart,
  Plus,
  RefreshCw,
  ShieldAlert,
  Users,
  Wallet,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import { EmptyState, SkeletonCard, Tooltip, SAProgress } from "../../components/ui/sa";
import { commandApi, type CommandDashboardView } from "./command.api";
import { CardSpotlight } from "./components/CardSpotlight";
import { FollowingPointer } from "./components/FollowingPointer";

export function CommandPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const reducedMotion = useReducedMotion();

  const [dateStr, setDateStr] = useState<string>("");
  const [activeOpTab, setActiveOpTab] = useState<"productions" | "tasks" | "attendance">("productions");

  const dashboardQuery = useQuery({
    queryKey: ["command-dashboard", dateStr],
    queryFn: () => commandApi.dashboard(dateStr || undefined),
    staleTime: 20000,
  });

  const handleRefresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["command-dashboard"] });
  };

  const shiftDate = (days: number) => {
    const baseDate = dateStr ? new Date(dateStr) : new Date();
    baseDate.setDate(baseDate.getDate() + days);
    setDateStr(baseDate.toISOString().slice(0, 10));
  };

  const resetToToday = () => {
    setDateStr("");
  };

  const ownerName =
    import.meta.env.VITE_DESKTOP_RELEASE === "true" ? "Azeem" : "Owner";

  if (dashboardQuery.isPending) {
    return (
      <div className="command-dashboard-shell">
        <WorkspaceHeader
          title={`${greeting()}, ${ownerName}.`}
          subtitle="Loading the operating picture…"
        />
        <div style={{ display: "grid", gap: "16px" }}>
          <SkeletonCard />
          <SkeletonCard />
        </div>
      </div>
    );
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <div className="command-dashboard-shell">
        <WorkspaceHeader
          title={`${greeting()}, ${ownerName}.`}
          subtitle="Operating picture unavailable"
        />
        <EmptyState
          title="Command telemetry unavailable"
          description="Could not load the unified owner dashboard. Please verify connection and retry."
          action={
            <button
              type="button"
              className="command-btn-icon-subtle"
              style={{ width: "auto", padding: "8px 16px" }}
              onClick={handleRefresh}
            >
              <RefreshCw size={14} style={{ marginRight: 6 }} />
              Retry
            </button>
          }
        />
      </div>
    );
  }

  const d = dashboardQuery.data;
  const isTodayView = !dateStr || dateStr === new Date().toISOString().slice(0, 10);
  const formattedDate = new Date(`${d.date}T00:00:00`).toLocaleDateString("en-IN", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const anim = (delay: number): HTMLMotionProps<"div"> =>
    reducedMotion
      ? {}
      : {
          initial: { opacity: 0, y: 8 },
          animate: { opacity: 1, y: 0 },
          transition: { duration: 0.22, delay, ease: "easeOut" },
        };

  return (
    <div className="command-dashboard-shell">
      {/* 1. Header & Date Control Bar */}
      <motion.div {...anim(0)}>
        <WorkspaceHeader
          title={`${greeting()}, ${ownerName}.`}
          subtitle="Executive operating console · Authoritative canonical telemetry"
        />
        <div className="command-header-bar">
          <div className="command-header-left">
            <span className="command-system-status">
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: "50%",
                  backgroundColor:
                    d.money.reconciliationStatus === "BROKEN"
                      ? "#ef4444"
                      : d.money.reconciliationStatus === "WARNING"
                        ? "#f59e0b"
                        : "#10b981",
                }}
              />
              {d.money.reconciliationStatus === "BROKEN"
                ? "Reconciliation Issue"
                : d.money.reconciliationStatus === "WARNING"
                  ? "Reconciliation Warning"
                  : "Finance Reconciled"}
            </span>
            <span style={{ fontSize: "12px", color: "var(--text-3)" }}>
              Viewing {isTodayView ? "Today" : d.date}
            </span>
          </div>

          <div className="command-header-date-controls">
            <button
              type="button"
              className="command-btn-icon-subtle"
              onClick={() => shiftDate(-1)}
              aria-label="Previous day"
              title="Previous day"
            >
              <ChevronLeft size={16} />
            </button>

            <button
              type="button"
              className="command-btn-icon-subtle"
              style={{
                width: "auto",
                padding: "0 12px",
                fontWeight: isTodayView ? 600 : 400,
                color: isTodayView ? "var(--text-1)" : "var(--text-2)",
              }}
              onClick={resetToToday}
            >
              Today
            </button>

            <button
              type="button"
              className="command-btn-icon-subtle"
              onClick={() => shiftDate(1)}
              aria-label="Next day"
              title="Next day"
            >
              <ChevronRight size={16} />
            </button>

            <div className="command-date-pill">
              <Calendar size={14} style={{ color: "var(--text-3)" }} />
              <input
                type="date"
                className="command-date-input"
                value={dateStr || d.date}
                onChange={(e) => setDateStr(e.target.value)}
                aria-label="Select custom business date"
              />
              <span style={{ color: "var(--text-2)", marginLeft: 2 }}>{formattedDate}</span>
            </div>

            <button
              type="button"
              className="command-btn-icon-subtle"
              onClick={handleRefresh}
              aria-label="Refresh dashboard telemetry"
              title="Refresh telemetry"
            >
              <RefreshCw
                size={14}
                className={dashboardQuery.isFetching ? "spin" : ""}
              />
            </button>
          </div>
        </div>
      </motion.div>

      {/* 2. Today Summary Row (Command Strip) */}
      <motion.div className="command-today-strip" {...anim(0.05)}>
        {/* Productions */}
        <div
          className="command-today-metric-card"
          onClick={() => navigate("/productions")}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === "Enter" && navigate("/productions")}
        >
          <div className="command-today-metric-top">
            <span className="command-today-metric-label">Productions</span>
            <Clapperboard size={15} style={{ color: "var(--text-3)" }} />
          </div>
          <strong className="command-today-metric-value">{d.today.productions}</strong>
          <div className="command-today-metric-footer">
            <span>
              {d.today.productions > 0 ? "Scheduled commitments today" : "No productions today"}
            </span>
            <ArrowUpRight size={13} style={{ marginLeft: "auto", color: "var(--text-3)" }} />
          </div>
        </div>

        {/* Tasks */}
        <div
          className="command-today-metric-card"
          onClick={() => navigate("/work")}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === "Enter" && navigate("/work")}
        >
          <div className="command-today-metric-top">
            <span className="command-today-metric-label">Tasks Due</span>
            <Clock size={15} style={{ color: "var(--text-3)" }} />
          </div>
          <strong className="command-today-metric-value">{d.today.tasks}</strong>
          <div className="command-today-metric-footer">
            <span>{d.today.tasks > 0 ? "Operational tasks due today" : "Schedule is clear"}</span>
            <ArrowUpRight size={13} style={{ marginLeft: "auto", color: "var(--text-3)" }} />
          </div>
        </div>

        {/* Attendance */}
        <div
          className="command-today-metric-card"
          onClick={() => navigate("/attendance")}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === "Enter" && navigate("/attendance")}
        >
          <div className="command-today-metric-top">
            <span className="command-today-metric-label">Attendance</span>
            <Users size={15} style={{ color: "var(--text-3)" }} />
          </div>
          <strong className="command-today-metric-value">{d.today.attendanceExceptions}</strong>
          <div className="command-today-metric-footer">
            <span>
              {d.today.attendanceExceptions > 0
                ? "Exceptions need review"
                : "All members accounted for"}
            </span>
            <ArrowUpRight size={13} style={{ marginLeft: "auto", color: "var(--text-3)" }} />
          </div>
        </div>

        {/* Money Today */}
        <div
          className="command-today-metric-card"
          onClick={() => navigate("/finance")}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === "Enter" && navigate("/finance")}
        >
          <div className="command-today-metric-top">
            <span className="command-today-metric-label">Money Today</span>
            <CreditCard size={15} style={{ color: "var(--text-3)" }} />
          </div>
          <strong className="command-today-metric-value">
            {formatInr(d.today.moneyMovement.received)}
          </strong>
          <div className="command-today-metric-footer">
            <span>
              Out: {formatInr(d.today.moneyMovement.disbursed)} ({d.today.moneyMovement.transactionCount} txs)
            </span>
            <ArrowUpRight size={13} style={{ marginLeft: "auto", color: "var(--text-3)" }} />
          </div>
        </div>
      </motion.div>

      {/* 3. Primary Grid: Business Position + Attention */}
      <motion.div className="command-primary-grid" {...anim(0.1)}>
        {/* Business Position Hero Card with Spotlight */}
        <FollowingPointer
          content={
            <div>
              <strong>Canonical Finance Core</strong>
              <p style={{ margin: "2px 0 0", fontSize: "11px", color: "var(--text-3)" }}>
                Click to explore ledger journals & account balances.
              </p>
            </div>
          }
        >
          <CardSpotlight interactive onClick={() => navigate("/finance")}>
            <div className="command-money-hero-header">
              <div className="command-money-title-group">
                <h2>Business Position</h2>
                <div
                  className={`command-money-hero-amount ${
                    d.money.businessPosition >= 0 ? "positive" : "negative"
                  }`}
                >
                  {formatInr(d.money.businessPosition)}
                </div>
              </div>

              <Tooltip content="Reconciliation Control: Compares total journal postings to physical owner balances. Click to open control plane.">
                <div
                  className={`command-money-badge status-${d.money.reconciliationStatus.toLowerCase()}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/finance");
                  }}
                  role="button"
                  tabIndex={0}
                >
                  <span
                    style={{
                      width: 6,
                      height: 6,
                      borderRadius: "50%",
                      backgroundColor: "currentColor",
                    }}
                  />
                  {d.money.reconciliationStatus}
                </div>
              </Tooltip>
            </div>

            {/* Owner Account Positions */}
            <div className="command-owner-positions-grid">
              <Tooltip content="Azeem (AZ-2): Current balance of owner capital. Negative position indicates capital drawn or funded beyond current balance, not third-party debt.">
                <div
                  className="command-position-tile"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/finance");
                  }}
                >
                  <div className="tile-label">
                    <span>Azeem (AZ-2)</span>
                    <ArrowUpRight size={13} />
                  </div>
                  <strong className="tile-value">{formatInr(d.money.azeemPosition)}</strong>
                  <span className="tile-hint">Owner capital position</span>
                </div>
              </Tooltip>

              <Tooltip content="Akash (AK-2): Current balance of owner capital associated with AK-2 account.">
                <div
                  className="command-position-tile"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/finance");
                  }}
                >
                  <div className="tile-label">
                    <span>Akash (AK-2)</span>
                    <ArrowUpRight size={13} />
                  </div>
                  <strong className="tile-value">{formatInr(d.money.akashPosition)}</strong>
                  <span className="tile-hint">Owner capital position</span>
                </div>
              </Tooltip>
            </div>

            {/* Receivables & Payables Breakdown */}
            <div className="command-receivables-breakdown">
              <Tooltip content="Direct counterparty charges outstanding on the direct ledger track.">
                <div
                  className="command-sub-metric"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/finance");
                  }}
                >
                  <span>Party Charges</span>
                  <strong>{formatInr(d.money.customerReceivable)}</strong>
                </div>
              </Tooltip>

              <Tooltip content="Formal GST / standard invoices pending payment on the invoice track.">
                <div
                  className="command-sub-metric"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/finance");
                  }}
                >
                  <span>Formal Invoices</span>
                  <strong>{formatInr(d.money.invoiceReceivable)}</strong>
                </div>
              </Tooltip>

              <Tooltip content="Pending salary and manual earning obligations owed to employees.">
                <div
                  className="command-sub-metric"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate("/payroll");
                  }}
                >
                  <span>Crew Payable</span>
                  <strong>{formatInr(d.money.employeePayable)}</strong>
                </div>
              </Tooltip>
            </div>
          </CardSpotlight>
        </FollowingPointer>

        {/* Attention Center Panel */}
        <div className="command-panel-card command-attention-panel">
          <div className="command-attention-header">
            <h3>Attention Queue ({d.attention.length})</h3>
            <span style={{ fontSize: "11.5px", color: "var(--text-3)" }}>
              {d.attention.length ? "Deterministic actions" : "Clear"}
            </span>
          </div>

          <div className="command-attention-list">
            {d.attention.length > 0 ? (
              d.attention.map((item, idx) => (
                <div
                  key={`${item.type}-${idx}`}
                  className="command-attention-card"
                  onClick={() => navigate(item.route)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => e.key === "Enter" && navigate(item.route)}
                >
                  <div className="command-attention-left">
                    <span
                      className={`command-severity-indicator severity-${item.severity.toLowerCase()}`}
                    />
                    <div className="command-attention-text">
                      <strong>{item.title}</strong>
                      <p>{item.description}</p>
                    </div>
                  </div>
                  <div className="command-attention-action">
                    <span>Resolve</span>
                    <ArrowRight size={14} />
                  </div>
                </div>
              ))
            ) : (
              <div className="command-attention-clear">
                <CheckCircle2 size={28} style={{ color: "#10b981", strokeWidth: 1.5 }} />
                <strong>You’re clear</strong>
                <p>No overdue invoices, salary arrears, or reconciliation alerts.</p>
              </div>
            )}
          </div>
        </div>
      </motion.div>

      {/* 4. Secondary Grid: Operations & Activity Timeline */}
      <motion.div className="command-secondary-grid" {...anim(0.15)}>
        {/* Operations Panel with Tabs */}
        <div className="command-panel-card">
          <div className="command-panel-header">
            <h3>Operations</h3>
            <div className="command-operations-tabs">
              <button
                type="button"
                className={`command-tab-btn ${activeOpTab === "productions" ? "active" : ""}`}
                onClick={() => setActiveOpTab("productions")}
              >
                Productions ({d.operations.upcomingProductions.length})
              </button>
              <button
                type="button"
                className={`command-tab-btn ${activeOpTab === "tasks" ? "active" : ""}`}
                onClick={() => setActiveOpTab("tasks")}
              >
                Tasks ({d.operations.pendingWork.length})
              </button>
              <button
                type="button"
                className={`command-tab-btn ${activeOpTab === "attendance" ? "active" : ""}`}
                onClick={() => setActiveOpTab("attendance")}
              >
                Exceptions ({d.operations.attendanceExceptions.length})
              </button>
            </div>
          </div>

          <div className="command-operations-list">
            {activeOpTab === "productions" && (
              <>
                {d.operations.upcomingProductions.length > 0 ? (
                  d.operations.upcomingProductions.map((p) => (
                    <div
                      key={p.id}
                      className="command-op-item"
                      onClick={() => navigate(`/productions/${p.id}`)}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="command-op-info">
                        <strong>{p.title}</strong>
                        <span>
                          {p.clientName} · {p.venueName} ·{" "}
                          {new Date(`${p.eventDate}T00:00:00`).toLocaleDateString("en-IN", {
                            day: "2-digit",
                            month: "short",
                          })}
                        </span>
                      </div>
                      <div style={{ width: 80, textAlign: "right" }}>
                        <span style={{ fontSize: "11px", fontWeight: 600 }}>
                          {p.progressPercent}%
                        </span>
                        <SAProgress value={p.progressPercent} />
                      </div>
                    </div>
                  ))
                ) : (
                  <p style={{ fontSize: "12.5px", color: "var(--text-3)", padding: "16px 0" }}>
                    No upcoming productions scheduled.
                  </p>
                )}
              </>
            )}

            {activeOpTab === "tasks" && (
              <>
                {d.operations.pendingWork.length > 0 ? (
                  d.operations.pendingWork.map((t) => (
                    <div
                      key={t.id}
                      className="command-op-item"
                      onClick={() => navigate("/work")}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="command-op-info">
                        <strong>{t.title}</strong>
                        <span>
                          {t.assignedEmployeeName ?? "Unassigned"}
                          {t.productionTitle ? ` · ${t.productionTitle}` : ""}
                        </span>
                      </div>
                      <span
                        style={{
                          fontSize: "11px",
                          fontWeight: 560,
                          color: t.priority === "URGENT" || t.priority === "HIGH" ? "#ef4444" : "var(--text-3)",
                        }}
                      >
                        {t.priority}
                      </span>
                    </div>
                  ))
                ) : (
                  <p style={{ fontSize: "12.5px", color: "var(--text-3)", padding: "16px 0" }}>
                    All operational tasks completed.
                  </p>
                )}
              </>
            )}

            {activeOpTab === "attendance" && (
              <>
                {d.operations.attendanceExceptions.length > 0 ? (
                  d.operations.attendanceExceptions.map((ex) => (
                    <div
                      key={ex.employeeId}
                      className="command-op-item"
                      onClick={() => navigate("/attendance")}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="command-op-info">
                        <strong>{ex.employeeName}</strong>
                        <span>
                          {ex.status === "LATE" && ex.minutesLate
                            ? `Late by ${ex.minutesLate} mins`
                            : ex.status === "ABSENT"
                              ? "Marked absent today"
                              : "No check-in recorded"}
                        </span>
                      </div>
                      <span
                        style={{
                          fontSize: "11px",
                          fontWeight: 560,
                          color: ex.status === "ABSENT" ? "#ef4444" : "#f59e0b",
                        }}
                      >
                        {ex.status}
                      </span>
                    </div>
                  ))
                ) : (
                  <p style={{ fontSize: "12.5px", color: "var(--text-3)", padding: "16px 0" }}>
                    Full team attendance accounted for today.
                  </p>
                )}
              </>
            )}
          </div>
        </div>

        {/* Recent Financial Movement */}
        <div className="command-panel-card">
          <div className="command-panel-header">
            <h3>Recent Financial Activity</h3>
            <span
              style={{
                fontSize: "11.5px",
                color: "var(--text-3)",
                cursor: "pointer",
              }}
              onClick={() => navigate("/finance")}
            >
              View ledger →
            </span>
          </div>

          <div className="command-timeline-list">
            {d.recentFinancialActivity.length > 0 ? (
              d.recentFinancialActivity.map((tx) => {
                const isPositive =
                  tx.type.includes("RECEIPT") || tx.type === "INVOICE_PAYMENT";
                const entity =
                  tx.counterpartyName || tx.employeeName || tx.productionTitle || "Operating";
                return (
                  <div
                    key={tx.id}
                    className="command-timeline-row"
                    onClick={() => navigate("/finance")}
                    role="button"
                    tabIndex={0}
                  >
                    <div className="command-timeline-left">
                      <span className="command-timeline-badge">{formatTxType(tx.type)}</span>
                      <div className="command-timeline-desc">
                        <strong>{entity}</strong>
                        <span>{tx.description}</span>
                      </div>
                    </div>
                    <strong
                      className={`command-timeline-amount ${
                        isPositive ? "positive" : "neutral"
                      }`}
                    >
                      {isPositive ? "+" : "-"}
                      {formatInr(tx.amount)}
                    </strong>
                  </div>
                );
              })
            ) : (
              <p style={{ fontSize: "12.5px", color: "var(--text-3)", padding: "16px 0" }}>
                No recent financial transactions posted.
              </p>
            )}
          </div>
        </div>
      </motion.div>

      {/* 5. Quick Actions Dock */}
      <motion.div className="command-quick-actions-bar" {...anim(0.2)}>
        <span style={{ fontSize: "11px", fontWeight: 600, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.06em", marginRight: 4 }}>
          Quick Actions
        </span>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/productions")}
        >
          <Clapperboard size={14} />
          New Production
        </button>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/finance")}
        >
          <ArrowRight size={14} />
          Record Receipt
        </button>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/finance")}
        >
          <ArrowUpRight size={14} />
          Log Expense
        </button>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/payroll")}
        >
          <Wallet size={14} />
          Disburse Salary
        </button>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/billing")}
        >
          <FileText size={14} />
          Create Bill
        </button>

        <button
          type="button"
          className="command-quick-action-btn"
          onClick={() => navigate("/finance")}
        >
          <PieChart size={14} />
          Finance Console
        </button>
      </motion.div>
    </div>
  );
}

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

function formatInr(val?: number | null) {
  if (val == null || isNaN(val)) return "₹0";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(val);
}

function formatTxType(type: string) {
  switch (type) {
    case "PRODUCTION_RECEIPT":
    case "COUNTERPARTY_RECEIPT":
      return "Receipt";
    case "INVOICE_PAYMENT":
      return "Invoice Paid";
    case "PRODUCTION_EXPENSE":
    case "GENERAL_EXPENSE":
      return "Expense";
    case "EMPLOYEE_PAYMENT":
      return "Payroll";
    case "EMPLOYEE_EARNING":
    case "MONTHLY_SALARY_ACCRUAL":
      return "Accrual";
    default:
      return type.replace(/_/g, " ").toLowerCase();
  }
}
