import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  motion,
  useReducedMotion,
  AnimatePresence,
  type HTMLMotionProps,
} from "motion/react";
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  Building2,
  Calendar,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Clapperboard,
  Clock,
  CreditCard,
  DollarSign,
  ExternalLink,
  FileText,
  PieChart,
  ReceiptText,
  RefreshCw,
  ShieldAlert,
  Users,
  Wallet,
  Zap,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import { EmptyState, Tooltip, SAProgress } from "../../components/ui/sa";
import {
  commandApi,
  type CommandAttentionItem,
  type CommandDashboardView,
  type DashboardProduction,
  type DashboardTask,
} from "./command.api";
import { CardSpotlight } from "./components/CardSpotlight";
import { FollowingPointer } from "./components/FollowingPointer";
import { DirectionAwareHover } from "./components/DirectionAwareHover";
import { UpcomingProductions } from "./components/UpcomingProductions";

export function CommandPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const reducedMotion = useReducedMotion();

  const [dateStr, setDateStr] = useState<string>("");
  const [activeOpTab, setActiveOpTab] = useState<"tasks" | "attendance">(
    "tasks",
  );
  const [taskFilter, setTaskFilter] = useState<
    "ALL" | "OVERDUE" | "DUE_TODAY" | "PENDING"
  >("ALL");

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
        <div className="command-skeleton-today">
          <div className="command-skeleton-card" />
          <div className="command-skeleton-card" />
          <div className="command-skeleton-card" />
          <div className="command-skeleton-card" />
        </div>
        <div className="command-skeleton-grid">
          <div className="command-skeleton-col">
            <div className="command-skeleton-hero" />
            <div className="command-skeleton-upcoming" />
          </div>
          <div className="command-skeleton-hero" style={{ height: "100%" }} />
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
          title="Command data unavailable"
          description="Could not load the unified owner dashboard telemetry. Please verify connection and retry."
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
  const isTodayView =
    !dateStr || dateStr === new Date().toISOString().slice(0, 10);
  const formattedDate = new Date(`${d.date}T00:00:00`).toLocaleDateString(
    "en-IN",
    {
      weekday: "short",
      month: "short",
      day: "numeric",
      year: "numeric",
    },
  );

  const anim = (delay: number): HTMLMotionProps<"div"> =>
    reducedMotion
      ? {}
      : {
          initial: { opacity: 0, y: 8 },
          animate: { opacity: 1, y: 0 },
          transition: { duration: 0.22, delay, ease: "easeOut" },
        };

  // Filter tasks if subfilter selected
  const filteredTasks = d.operations.pendingWork.filter((t) => {
    if (taskFilter === "ALL") return true;
    if (taskFilter === "OVERDUE") return t.isOverdue;
    if (taskFilter === "DUE_TODAY") return t.bucket === "DUE_TODAY";
    if (taskFilter === "PENDING") return t.bucket === "PENDING" && !t.isOverdue;
    return true;
  });

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
            {d.money && (
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
            )}
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
              <span style={{ color: "var(--text-2)", marginLeft: 2 }}>
                {formattedDate}
              </span>
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
          <strong className="command-today-metric-value">
            {d.today.productions}
          </strong>
          <div className="command-today-metric-footer">
            <span>
              {d.today.productions > 0
                ? "Scheduled commitments today"
                : "No productions today"}
            </span>
            <ArrowUpRight
              size={13}
              style={{ marginLeft: "auto", color: "var(--text-3)" }}
            />
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
          <strong className="command-today-metric-value">
            {d.today.tasks}
          </strong>
          <div className="command-today-metric-footer">
            <span>
              {d.today.tasks > 0
                ? "Operational tasks due today"
                : "Schedule is clear"}
            </span>
            <ArrowUpRight
              size={13}
              style={{ marginLeft: "auto", color: "var(--text-3)" }}
            />
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
          <strong className="command-today-metric-value">
            {d.today.attendanceExceptions}
          </strong>
          <div className="command-today-metric-footer">
            <span>
              {d.today.attendanceExceptions > 0
                ? "Exceptions need review"
                : "All members accounted for"}
            </span>
            <ArrowUpRight
              size={13}
              style={{ marginLeft: "auto", color: "var(--text-3)" }}
            />
          </div>
        </div>

        {/* Money Today */}
        {d.money && (
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
                Out: {formatInr(d.today.moneyMovement.disbursed)} (
                {d.today.moneyMovement.transactionCount} txs)
              </span>
              <ArrowUpRight
                size={13}
                style={{ marginLeft: "auto", color: "var(--text-3)" }}
              />
            </div>
          </div>
        )}
      </motion.div>

      {/* 3. Primary Grid: Business Position + Upcoming Productions (Left) & Attention Queue (Right) */}
      <motion.div className="command-primary-grid" {...anim(0.1)}>
        <div className="command-primary-left-col">
          {/* Business Position Hero Card with Spotlight */}
          {d.money && (
            <FollowingPointer
              content={
                <div>
                  <strong>Canonical Finance Core</strong>
                  <p
                    style={{
                      margin: "2px 0 0",
                      fontSize: "11px",
                      color: "var(--text-3)",
                    }}
                  >
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
                        navigate("/finance?tab=RECONCILIATION");
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
                        navigate("/finance?tab=OWNERS");
                      }}
                    >
                      <div className="tile-label">
                        <span>Azeem (AZ-2)</span>
                        <ArrowUpRight size={13} />
                      </div>
                      <strong className="tile-value">
                        {formatInr(d.money.azeemPosition)}
                      </strong>
                      <span className="tile-hint">Owner capital position</span>
                    </div>
                  </Tooltip>

                  <Tooltip content="Akash (AK-2): Current balance of owner capital associated with AK-2 account.">
                    <div
                      className="command-position-tile"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate("/finance?tab=OWNERS");
                      }}
                    >
                      <div className="tile-label">
                        <span>Akash (AK-2)</span>
                        <ArrowUpRight size={13} />
                      </div>
                      <strong className="tile-value">
                        {formatInr(d.money.akashPosition)}
                      </strong>
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
                        navigate("/finance?tab=PARTIES");
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
                        navigate("/finance?tab=INVOICES");
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
          )}

          {/* Dedicated Upcoming Productions Section */}
          <UpcomingProductions
            productions={d.operations.upcomingProductions}
            formatInr={formatInr}
          />
        </div>

        {/* Attention Center Panel (Phase 3 Centerpiece — Actionable Queue) */}
        <div className="command-panel-card command-attention-panel">
          <div className="command-attention-header">
            <h3>Attention Queue ({d.attention.length})</h3>
            <span style={{ fontSize: "11.5px", color: "var(--text-3)" }}>
              {d.attention.length ? "Deterministic actions" : "Clear"}
            </span>
          </div>

          <div className="command-attention-list">
            {d.attention.length > 0 ? (
              d.attention.map((item, i) => (
                <motion.div
                  key={item.id}
                  initial={
                    reducedMotion ? { opacity: 0 } : { opacity: 0, y: 6 }
                  }
                  animate={{ opacity: 1, y: 0 }}
                  transition={{
                    duration: 0.18,
                    delay: reducedMotion ? 0 : i * 0.05,
                    ease: "easeOut",
                  }}
                >
                  <DirectionAwareHover
                    onClick={() => navigate(item.route)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === "Enter" && navigate(item.route)}
                    aria-label={`${item.severity} alert: ${item.title}`}
                  >
                    <div className="command-attention-card">
                      {/* Top Row: Severity + Category + Amount */}
                      <div className="command-attention-top">
                        <div className="command-attention-badge-group">
                          <span
                            className={`command-severity-pill severity-${item.severity.toLowerCase()}`}
                          >
                            <span
                              style={{
                                width: 5,
                                height: 5,
                                borderRadius: "50%",
                                backgroundColor: "currentColor",
                              }}
                            />
                            {item.severity}
                          </span>
                          <span className="command-category-pill">
                            {item.category}
                          </span>
                        </div>

                        {item.amount != null && item.amount > 0 && (
                          <span className="command-attention-amount-pill">
                            {formatInr(item.amount)}
                          </span>
                        )}
                      </div>

                      {/* Headline and Description */}
                      <div className="command-attention-body">
                        <strong>{item.title}</strong>
                        <p>{item.description}</p>
                      </div>

                      {/* Rationale / Why this matters */}
                      {item.reason && (
                        <div className="command-attention-reason">
                          <span>Why:</span>
                          <em>{item.reason}</em>
                        </div>
                      )}

                      {/* Phase 3: Named action footer — specific to item type */}
                      <div className="command-attention-footer">
                        <span className="command-attention-action-label">
                          {attentionActionLabel(item)}
                        </span>
                        <ExternalLink size={12} />
                      </div>
                    </div>
                  </DirectionAwareHover>
                </motion.div>
              ))
            ) : (
              <div className="command-attention-clear">
                <CheckCircle2
                  size={30}
                  style={{ color: "#10b981", strokeWidth: 1.5 }}
                />
                <strong>Everything is clear</strong>
                <p>
                  No operational or financial exceptions need attention right
                  now.
                </p>
              </div>
            )}
          </div>
        </div>
      </motion.div>

      {/* 4. Secondary Grid: Operations & Activity Timeline */}
      <motion.div className="command-secondary-grid" {...anim(0.15)}>
        {/* Operations Panel with Animated Tabs */}
        <div className="command-panel-card">
          <div className="command-panel-header">
            <h3>Operations</h3>
            <div className="command-operations-tabs" role="tablist">
              {(
                [
                  {
                    id: "tasks",
                    label: `Tasks (${d.operations.pendingWork.length})`,
                  },
                  {
                    id: "attendance",
                    label: `Exceptions (${d.operations.attendanceExceptions.length})`,
                  },
                ] as const
              ).map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  role="tab"
                  aria-selected={activeOpTab === tab.id}
                  className={`command-tab-btn ${activeOpTab === tab.id ? "active" : ""}`}
                  onClick={() => setActiveOpTab(tab.id)}
                >
                  <span style={{ position: "relative", zIndex: 2 }}>
                    {tab.label}
                  </span>
                  {activeOpTab === tab.id && !reducedMotion && (
                    <motion.div
                      layoutId="command-ops-indicator"
                      className="command-tab-indicator"
                      transition={{
                        type: "spring",
                        stiffness: 380,
                        damping: 30,
                      }}
                      style={{ inset: 0, zIndex: 1 }}
                    />
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="command-operations-list">
            {/* TASKS TAB */}
            {activeOpTab === "tasks" && (
              <>
                <div
                  className="command-task-subfilters"
                  role="group"
                  aria-label="Task category filter"
                >
                  {(
                    [
                      { id: "ALL", label: "All" },
                      { id: "OVERDUE", label: "Overdue" },
                      { id: "DUE_TODAY", label: "Due Today" },
                      { id: "PENDING", label: "Pending" },
                    ] as const
                  ).map((f) => (
                    <button
                      key={f.id}
                      type="button"
                      className={`command-task-subfilter-btn ${taskFilter === f.id ? "active" : ""}`}
                      onClick={() => setTaskFilter(f.id)}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>

                {filteredTasks.length > 0 ? (
                  filteredTasks.map((t) => (
                    <div
                      key={t.id}
                      className="command-task-item"
                      onClick={() =>
                        navigate(t.isOverdue ? "/work?view=OVERDUE" : "/work")
                      }
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === "Enter" && navigate("/work")}
                    >
                      <div className="command-op-info">
                        <strong>{t.title}</strong>
                        <span>
                          {t.assignedEmployeeName ?? "Unassigned"}
                          {t.productionTitle ? ` · ${t.productionTitle}` : ""}
                        </span>
                      </div>

                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                        }}
                      >
                        <span
                          className={`command-task-bucket-badge bucket-${t.bucket.toLowerCase()}`}
                        >
                          {t.bucket === "DUE_TODAY"
                            ? "Today"
                            : t.bucket === "OVERDUE"
                              ? "Overdue"
                              : "Pending"}
                        </span>
                        <span
                          style={{
                            fontSize: "11px",
                            fontWeight: 560,
                            color:
                              t.priority === "URGENT" || t.priority === "HIGH"
                                ? "#ef4444"
                                : "var(--text-3)",
                          }}
                        >
                          {t.priority}
                        </span>
                      </div>
                    </div>
                  ))
                ) : (
                  <p
                    style={{
                      fontSize: "12.5px",
                      color: "var(--text-3)",
                      padding: "16px 0",
                    }}
                  >
                    No tasks matching this filter.
                  </p>
                )}
              </>
            )}

            {/* ATTENDANCE TAB */}
            {activeOpTab === "attendance" && (
              <>
                {d.operations.attendanceExceptions.length > 0 ? (
                  d.operations.attendanceExceptions.map((ex) => (
                    <div
                      key={ex.employeeId}
                      className="command-attendance-row"
                      onClick={() => navigate("/attendance")}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) =>
                        e.key === "Enter" && navigate("/attendance")
                      }
                    >
                      <div className="command-op-info">
                        <strong>{ex.employeeName}</strong>
                        <span>
                          {ex.status === "LATE" && ex.minutesLate
                            ? `Late by ${ex.minutesLate} mins`
                            : ex.status === "ABSENT"
                              ? "Marked absent today"
                              : "No check-in recorded"}
                          {ex.notes ? ` · ${ex.notes}` : ""}
                        </span>
                      </div>

                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                        }}
                      >
                        <span
                          style={{
                            fontSize: "11px",
                            fontWeight: 600,
                            padding: "2px 7px",
                            borderRadius: 6,
                            color:
                              ex.status === "ABSENT" ? "#dc2626" : "#d97706",
                            background:
                              ex.status === "ABSENT"
                                ? "rgba(239, 68, 68, 0.12)"
                                : "rgba(245, 158, 11, 0.12)",
                          }}
                        >
                          {ex.status === "UNRECORDED" ? "Missing" : ex.status}
                        </span>
                        <ArrowUpRight
                          size={13}
                          style={{ color: "var(--text-3)" }}
                        />
                      </div>
                    </div>
                  ))
                ) : (
                  <p
                    style={{
                      fontSize: "12.5px",
                      color: "var(--text-3)",
                      padding: "16px 0",
                    }}
                  >
                    Full team attendance accounted for today.
                  </p>
                )}
              </>
            )}
          </div>
        </div>

        {/* Recent Financial Movement */}
        {d.money && (
          <div className="command-panel-card">
            <div className="command-panel-header">
              <h3>Recent Financial Activity</h3>
              <span
                style={{
                  fontSize: "11.5px",
                  color: "var(--text-3)",
                  cursor: "pointer",
                }}
                role="button"
                tabIndex={0}
                onClick={() => navigate("/finance?tab=TRANSACTIONS")}
                onKeyDown={(e) =>
                  e.key === "Enter" && navigate("/finance?tab=TRANSACTIONS")
                }
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
                    tx.counterpartyName ||
                    tx.employeeName ||
                    tx.productionTitle ||
                    "Operating";
                  const txRoute = activityRoute(tx.type);
                  return (
                    <div
                      key={tx.id}
                      className="command-timeline-row"
                      onClick={() => navigate(txRoute)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === "Enter" && navigate(txRoute)}
                      aria-label={`${formatTxType(tx.type)}: ${entity} — ${formatInr(tx.amount)}`}
                    >
                      <div className="command-timeline-left">
                        <span className="command-timeline-badge">
                          {formatTxType(tx.type)}
                        </span>
                        <div className="command-timeline-desc">
                          <strong>{entity}</strong>
                          <span>{tx.description}</span>
                        </div>
                      </div>
                      <div className="command-timeline-right">
                        <strong
                          className={`command-timeline-amount ${
                            isPositive ? "positive" : "neutral"
                          }`}
                        >
                          {isPositive ? "+" : "-"}
                          {formatInr(tx.amount)}
                        </strong>
                        <span className="command-timeline-date">{tx.date}</span>
                      </div>
                    </div>
                  );
                })
              ) : (
                <p
                  style={{
                    fontSize: "12.5px",
                    color: "var(--text-3)",
                    padding: "16px 0",
                  }}
                >
                  No recent financial transactions posted.
                </p>
              )}
            </div>
          </div>
        )}
      </motion.div>

      {/* 5. Quick Actions Dock — Phase 3 polished command surface */}
      <motion.div className="command-quick-actions-bar" {...anim(0.2)}>
        <div className="command-dock-label">
          <Zap size={12} style={{ color: "var(--text-3)" }} />
          <span>Quick Actions</span>
        </div>

        <div className="command-dock-separator" aria-hidden="true" />

        {(
          [
            {
              id: "new-production",
              label: "New Production",
              hint: "Productions",
              icon: <Clapperboard size={14} />,
              route: "/productions?create=production",
            },
            ...(d.money
              ? [
                  {
                    id: "record-receipt",
                    label: "Record Receipt",
                    hint: "Finance · Production Receipt",
                    icon: <ArrowDownLeft size={14} />,
                    route: "/finance?tab=PRODUCTIONS",
                  },
                  {
                    id: "log-expense",
                    label: "Log Expense",
                    hint: "Finance overview",
                    icon: <ReceiptText size={14} />,
                    route: "/finance",
                  },
                  {
                    id: "disburse-salary",
                    label: "Disburse Salary",
                    hint: "Payroll",
                    icon: <Wallet size={14} />,
                    route: "/payroll",
                  },
                  {
                    id: "create-bill",
                    label: "Create Bill",
                    hint: "Billing",
                    icon: <FileText size={14} />,
                    route: "/billing",
                  },
                  {
                    id: "finance-console",
                    label: "Finance Console",
                    hint: "Finance overview",
                    icon: <PieChart size={14} />,
                    route: "/finance",
                  },
                ]
              : []),
          ]
        ).map((action, i) => (
          <motion.div
            key={action.id}
            initial={reducedMotion ? { opacity: 0 } : { opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              duration: 0.16,
              delay: reducedMotion ? 0 : 0.22 + i * 0.04,
              ease: "easeOut",
            }}
          >
            <Tooltip content={action.hint}>
              <button
                type="button"
                className="command-quick-action-btn"
                onClick={() => navigate(action.route)}
                aria-label={action.label}
              >
                {action.icon}
                {action.label}
              </button>
            </Tooltip>
          </motion.div>
        ))}
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

/**
 * Phase 3: Maps financial activity transaction type to the most relevant
 * Finance tab deep-link. This is a presentation-only helper — it does not
 * change any financial data. The canonical Finance page remains authoritative.
 */
function activityRoute(type: string): string {
  if (type === "COUNTERPARTY_RECEIPT" || type === "COUNTERPARTY_CHARGE") {
    return "/finance?tab=PARTIES";
  }
  if (type === "INVOICE_PAYMENT") {
    return "/finance?tab=INVOICES";
  }
  if (type === "PRODUCTION_RECEIPT" || type === "PRODUCTION_EXPENSE") {
    return "/finance?tab=PRODUCTIONS";
  }
  if (
    type === "EMPLOYEE_PAYMENT" ||
    type === "EMPLOYEE_EARNING" ||
    type === "MONTHLY_SALARY_ACCRUAL"
  ) {
    return "/finance?tab=EMPLOYEES";
  }
  if (type === "EQUIPMENT_PURCHASE" || type === "EQUIPMENT_PAYMENT") {
    return "/finance?tab=EQUIPMENT";
  }
  if (
    type === "OWNER_CREDIT" ||
    type === "OWNER_DEBIT" ||
    type === "TRANSFER"
  ) {
    return "/finance?tab=OWNERS";
  }
  return "/finance?tab=TRANSACTIONS";
}

/**
 * Phase 3: Returns a specific named action label for each attention item type,
 * replacing the generic "Investigate →" text. The action describes where the
 * owner will go and what they can do there. No new routes are invented.
 */
function attentionActionLabel(item: CommandAttentionItem): string {
  switch (item.type) {
    case "RECONCILIATION_BROKEN":
      return "Open Reconciliation Control";
    case "RECONCILIATION_WARNING":
      return "Review Reconciliation";
    case "OVERDUE_INVOICES":
      return "Review Invoices";
    case "UNPAID_SALARY":
      return "Process Payroll";
    case "OVERDUE_TASKS":
      return "View Overdue Tasks";
    case "ATTENDANCE_INCOMPLETE":
      return "Mark Attendance";
    default:
      return "Investigate";
  }
}
