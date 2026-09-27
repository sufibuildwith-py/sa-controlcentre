import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  DollarSign,
  Edit3,
  ExternalLink,
  MessageCircle,
  Phone,
  Users,
  WalletCards,
} from "lucide-react";
import { useReducedMotion, type HTMLMotionProps } from "motion/react";
import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useUiStore } from "../../app/store/ui";
import {
  ConfirmAction,
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SAModal,
  SAProgress,
  SATabContent,
  SATabs,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api, ApiError } from "../../lib/api";
import type {
  AttendanceMonth,
  CommunicationCentre,
  Employee,
  Employee360View,
  EmployeeOperations,
} from "../../types/domain";
import { initials } from "./PeoplePage";
import { navigatorEnabled } from "../navigator/navigator.types";
import { EmployeeNavigatorPanel } from "../navigator/EmployeeNavigatorPanel";
import { financeApi, financeAmount } from "../finance/finance.api";

function SpotlightCard({
  children,
  className = "",
  style,
  onClick,
}: {
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
  onClick?: () => void;
}) {
  const reducedMotion = useReducedMotion();
  const [coords, setCoords] = useState<{ x: number; y: number } | null>(null);

  return (
    <SABentoCard
      className={`spotlight-card ${className}`}
      style={style}
      onClick={onClick}
      onMouseMove={(e) => {
        if (reducedMotion) return;
        const rect = e.currentTarget.getBoundingClientRect();
        setCoords({ x: e.clientX - rect.left, y: e.clientY - rect.top });
      }}
      onMouseLeave={() => setCoords(null)}
    >
      {!reducedMotion && (
        <div
          className="spotlight-layer"
          style={{
            opacity: coords ? 1 : 0,
            background: coords
              ? `radial-gradient(circle 240px at ${coords.x}px ${coords.y}px, rgba(255, 255, 255, 0.08), transparent 80%)`
              : "transparent",
          }}
        />
      )}
      {children}
    </SABentoCard>
  );
}

export function EmployeeDetailPage() {
  const { id } = useParams(),
    location = useLocation(),
    navigate = useNavigate(),
    client = useQueryClient(),
    openForm = useUiStore((s) => s.openEmployeeForm);
  const requestedTab = new URLSearchParams(location.search).get("tab"),
    [tab, setTab] = useState(
      [
        "attendance",
        "work",
        "payroll",
        "finance",
        "performance",
        "communication",
        ...(navigatorEnabled ? ["navigator"] : []),
      ].includes(requestedTab ?? "")
        ? requestedTab!
        : "overview",
    ),
    [confirm, setConfirm] = useState(false);
  const employee = useQuery({
    queryKey: ["employee", id],
    queryFn: () => api<Employee>(`/employees/${id}`),
    enabled: !!id,
  });
  const e360 = useQuery({
    queryKey: ["employee", id, "360"],
    queryFn: () => api<Employee360View>(`/employees/${id}/360`),
    enabled: !!id && tab === "overview",
  });
  const attendance = useQuery({
    queryKey: ["employee", id, "attendance"],
    queryFn: () => api<AttendanceMonth>(`/employees/${id}/attendance`),
    enabled: !!id && tab === "attendance",
  });
  const operations = useQuery({
    queryKey: ["employee", id, "operations"],
    queryFn: () => api<EmployeeOperations>(`/employees/${id}/operations`),
    enabled: !!id && ["work", "payroll", "performance"].includes(tab),
  });
  const finance = useQuery({
    queryKey: ["finance", "employee", id],
    queryFn: () => financeApi.employee(id!),
    enabled: !!id && tab === "finance",
  });
  const communications = useQuery({
    queryKey: ["employee", id, "communications"],
    queryFn: () => api<CommunicationCentre>(`/messages?employeeId=${id}`),
    enabled: !!id && tab === "communication",
  });
  const deactivate = useMutation({
    mutationFn: () =>
      api<Employee>(`/employees/${id}/deactivate`, { method: "POST" }),
    onSuccess: (data) => {
      client.setQueryData(["employee", id], data);
      client.invalidateQueries({ queryKey: ["employee", id, "360"] });
      client.invalidateQueries({ queryKey: ["employees"] });
      setConfirm(false);
    },
  });

  const [earningOpen, setEarningOpen] = useState(false);
  const [payoutOpen, setPayoutOpen] = useState(false);
  const [earningRequestKey, setEarningRequestKey] = useState(generateKey());
  const [payoutRequestKey, setPayoutRequestKey] = useState(generateKey());
  const [earningError, setEarningError] = useState("");
  const [payoutError, setPayoutError] = useState("");
  const [earningForm, setEarningForm] = useState({
    amount: "",
    date: new Date().toISOString().slice(0, 10),
    description: "",
    productionId: "",
  });
  const [payoutForm, setPayoutForm] = useState({
    amount: "",
    date: new Date().toISOString().slice(0, 10),
    description: "",
    payerAccount: "" as "" | "AZ-2" | "AK-2",
  });

  const productions = useQuery({
    queryKey: ["productions"],
    queryFn: () => api<Array<{ id: string; title: string }>>("/productions"),
    enabled: !!id && earningOpen,
  });

  const addEarningMutation = useMutation({
    mutationFn: () => {
      const amt = Number(earningForm.amount);
      return financeApi.addEarning({
        idempotencyKey: earningRequestKey,
        employeeId: id!,
        amount: amt,
        date: earningForm.date,
        description: earningForm.description.trim(),
        productionId: earningForm.productionId || undefined,
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "employee", id] });
      client.invalidateQueries({ queryKey: ["employee", id] });
      client.invalidateQueries({ queryKey: ["employee", id, "360"] });
      client.invalidateQueries({ queryKey: ["finance"] });
      setEarningOpen(false);
      setEarningError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setEarningError(err.message);
      } else if (err instanceof Error) {
        setEarningError(err.message);
      }
    },
  });

  const recordPayoutMutation = useMutation({
    mutationFn: () => {
      const amt = Number(payoutForm.amount);
      return financeApi.recordEmployeePayment({
        idempotencyKey: payoutRequestKey,
        employeeId: id!,
        amount: amt,
        date: payoutForm.date,
        description: payoutForm.description.trim(),
        payerAccount: payoutForm.payerAccount,
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "employee", id] });
      client.invalidateQueries({ queryKey: ["employee", id] });
      client.invalidateQueries({ queryKey: ["employee", id, "360"] });
      client.invalidateQueries({ queryKey: ["finance"] });
      setPayoutOpen(false);
      setPayoutError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setPayoutError(err.message);
      } else if (err instanceof Error) {
        setPayoutError(err.message);
      }
    },
  });
  if (employee.isPending) return <SkeletonCard />;
  if (employee.isError || !employee.data)
    return (
      <EmptyState
        title="Employee unavailable"
        description="This employee could not be found."
        action={
          <SAButton onClick={() => navigate("/people")}>
            Back to People
          </SAButton>
        }
      />
    );
  const e = employee.data;

  const openPayoutModal = (defaultAmt?: number) => {
    setPayoutRequestKey(generateKey());
    setPayoutForm({
      amount:
        defaultAmt !== undefined && defaultAmt > 0
          ? defaultAmt.toFixed(2)
          : finance.data && finance.data.outstanding > 0
            ? finance.data.outstanding.toFixed(2)
            : e360.data && e360.data.money.outstanding > 0
              ? e360.data.money.outstanding.toFixed(2)
              : "",
      date: new Date().toISOString().slice(0, 10),
      description: `Disbursement to ${e.displayName}`,
      payerAccount: "",
    });
    setPayoutError("");
    setPayoutOpen(true);
  };

  const openEarningModal = () => {
    setEarningRequestKey(generateKey());
    setEarningForm({
      amount: "",
      date: new Date().toISOString().slice(0, 10),
      description: `Shift labor for ${e.displayName}`,
      productionId: "",
    });
    setEarningError("");
    setEarningOpen(true);
  };
  return (
    <>
      <button className="back-link" onClick={() => navigate("/people")}>
        <ArrowLeft size={15} />
        People
      </button>
      <SABentoCard className="employee-hero">
        <div className="employee-hero__avatar">{initials(e.displayName)}</div>
        <div className="employee-hero__identity">
          <h1>{e.displayName}</h1>
          <p>{e.roleTitle}</p>
          <div>
            <StatusBadge
              tone={
                e.status === "ACTIVE"
                  ? "success"
                  : e.status === "ON_LEAVE"
                    ? "warning"
                    : "neutral"
              }
            >
              {e.status.replace("_", " ")}
            </StatusBadge>
          </div>
        </div>
        <div className="employee-hero__actions">
          <SAButton
            onClick={() =>
              navigate(`/communications?compose=1&employeeId=${e.id}`)
            }
          >
            <MessageCircle size={15} />
            Message
          </SAButton>
          <SAButton variant="primary" onClick={() => openForm(e.id)}>
            <Edit3 size={15} />
            Edit
          </SAButton>
        </div>
      </SABentoCard>
      <SATabs
        value={tab}
        onValueChange={setTab}
        tabs={[
          { value: "overview", label: "Overview" },
          { value: "attendance", label: "Attendance" },
          { value: "work", label: "Work" },
          { value: "payroll", label: "Payroll" },
          { value: "finance", label: "Finance" },
          { value: "performance", label: "Performance" },
          { value: "communication", label: "Communication" },
          ...(navigatorEnabled
            ? [{ value: "navigator", label: "Navigator" }]
            : []),
        ]}
      >
        <SATabContent value="overview">
          {e360.isPending ? (
            <SkeletonCard />
          ) : e360.isError || !e360.data ? (
            <SABentoGrid className="detail-grid">
              <SABentoCard className="detail-main">
                <h3>Employee information</h3>
                <dl>
                  <Info label="Employee code" value={e.employeeCode} />
                  <Info label="Department" value={e.department} />
                  <Info label="Joining date" value={dateLabel(e.joiningDate)} />
                  <Info
                    label="Employment"
                    value={e.employmentType.replace("_", " ")}
                  />
                  <Info label="Phone" value={e.phone} />
                  <Info
                    label="WhatsApp"
                    value={e.whatsappPhone ?? "Not provided"}
                  />
                  <Info label="Email" value={e.email ?? "Not provided"} />
                  <Info label="Status" value={e.status.replace("_", " ")} />
                </dl>
              </SABentoCard>
              <SABentoCard className="salary-card">
                <WalletCards size={18} />
                <span>Salary basis</span>
                <strong>{money(e.baseSalaryMinor, e.salaryCurrency)}</strong>
                <small>Monthly · integer minor units</small>
              </SABentoCard>
              <SABentoCard className="contact-card">
                <Phone size={18} />
                <span>Primary contact</span>
                <strong>{e.phone}</strong>
              </SABentoCard>
              <SABentoCard className="notes-card">
                <h3>Owner notes</h3>
                <p>{e.notes || "No internal notes for this employee."}</p>
              </SABentoCard>
              <div className="danger-zone">
                <div>
                  <strong>Deactivate employee</strong>
                  <span>Keeps historical operational and financial records.</span>
                </div>
                <SAButton
                  variant="danger"
                  disabled={e.status === "INACTIVE"}
                  onClick={() => setConfirm(true)}
                >
                  Deactivate
                </SAButton>
              </div>
            </SABentoGrid>
          ) : (
            <div className="e360-overview" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {/* Quick Action Strip */}
              <div className="e360-action-strip">
                <SAButton
                  variant="primary"
                  onClick={openEarningModal}
                >
                  <DollarSign size={15} />
                  Record Shift Earning
                </SAButton>
                <SAButton
                  variant="secondary"
                  disabled={e360.data.money.outstanding <= 0}
                  onClick={() => openPayoutModal(e360.data?.money.outstanding)}
                >
                  <WalletCards size={15} />
                  Record Disbursement
                </SAButton>
                <SAButton
                  onClick={() => navigate(`/communications?compose=1&employeeId=${e.id}`)}
                >
                  <MessageCircle size={15} />
                  Send Message
                </SAButton>
                <div style={{ marginLeft: "auto", display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <SAButton variant="ghost" onClick={() => setTab("attendance")}>
                    Attendance
                    <ArrowUpRight size={13} />
                  </SAButton>
                  <SAButton variant="ghost" onClick={() => setTab("work")}>
                    Work Tasks
                    <ArrowUpRight size={13} />
                  </SAButton>
                  <SAButton variant="ghost" onClick={() => setTab("finance")}>
                    Finance Ledger
                    <ArrowUpRight size={13} />
                  </SAButton>
                </div>
              </div>

              {/* 360 Grid */}
              <div className="e360-grid">
                {/* Pillar 1: Today Status */}
                <SpotlightCard className="e360-col-4">
                  <div className="e360-card-header">
                    <h3>Today's Status</h3>
                    <SAButton variant="ghost" size="sm" onClick={() => setTab("attendance")}>
                      Details
                      <ArrowUpRight size={12} />
                    </SAButton>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
                    <StatusBadge
                      tone={
                        e360.data.today.attendance?.status === "PRESENT"
                          ? "success"
                          : e360.data.today.attendance?.status === "LATE" || e360.data.today.attendance?.status === "HALF_DAY" || e360.data.today.attendance?.status === "LEAVE"
                            ? "warning"
                            : e360.data.today.attendance?.status === "ABSENT"
                              ? "danger"
                              : "neutral"
                      }
                    >
                      {e360.data.today.attendance?.status?.replace("_", " ") ?? "NOT MARKED"}
                    </StatusBadge>
                    {e360.data.today.attendance && e360.data.today.attendance.minutesLate > 0 && (
                      <StatusBadge tone="warning">
                        {e360.data.today.attendance.minutesLate}m late
                      </StatusBadge>
                    )}
                  </div>
                  <div className="e360-metrics-row">
                    <div className="e360-metric-box">
                      <span>Check In</span>
                      <strong>{e360.data.today.attendance?.checkInTime?.slice(0, 5) ?? "—"}</strong>
                    </div>
                    <div className="e360-metric-box">
                      <span>Check Out</span>
                      <strong>{e360.data.today.attendance?.checkOutTime?.slice(0, 5) ?? "—"}</strong>
                    </div>
                  </div>
                  {e360.data.today.activeOrPendingLeave && (
                    <div
                      style={{
                        marginTop: 12,
                        padding: "8px 12px",
                        background: "var(--warning-soft, rgba(234, 179, 8, 0.1))",
                        border: "1px solid var(--warning-border, rgba(234, 179, 8, 0.2))",
                        borderRadius: 8,
                        fontSize: 12,
                      }}
                    >
                      <strong>Active Leave: </strong>
                      {e360.data.today.activeOrPendingLeave.leaveType} ({e360.data.today.activeOrPendingLeave.startDate} to {e360.data.today.activeOrPendingLeave.endDate})
                    </div>
                  )}
                </SpotlightCard>

                {/* Pillar 2: Canonical Money */}
                <SpotlightCard className="e360-col-8">
                  <div className="e360-card-header">
                    <div>
                      <h3>Canonical Compensation & Ledger</h3>
                      <span>Zero-risk integer minor units</span>
                    </div>
                    <SAButton variant="ghost" size="sm" onClick={() => setTab("finance")}>
                      Ledger
                      <ArrowUpRight size={12} />
                    </SAButton>
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 12, marginBottom: 14 }}>
                    <div className="e360-metric-box">
                      <span>Base Salary</span>
                      <strong>{money(e360.data.money.baseSalaryMinor, e360.data.money.salaryCurrency)}</strong>
                    </div>
                    <div className="e360-metric-box">
                      <span>Total Earned</span>
                      <strong>{financeAmount(e360.data.money.earned)}</strong>
                    </div>
                    <div className="e360-metric-box">
                      <span>Total Paid</span>
                      <strong>{financeAmount(e360.data.money.paid)}</strong>
                    </div>
                    <div className="e360-metric-box" style={{ background: e360.data.money.outstanding > 0 ? "var(--warning-soft, rgba(234, 179, 8, 0.08))" : undefined }}>
                      <span>Net Outstanding</span>
                      <strong style={{ color: e360.data.money.outstanding > 0 ? "var(--warning, #f59e0b)" : undefined }}>
                        {financeAmount(e360.data.money.outstanding)}
                      </strong>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", paddingTop: 10, borderTop: "1px solid var(--border-soft, var(--border))", fontSize: 12, flexWrap: "wrap", gap: 8 }}>
                    <div>
                      {e360.data.money.latestPayrollPeriod ? (
                        <span>
                          Latest Payroll: <strong>{e360.data.money.latestPayrollPeriod}</strong> ·{" "}
                          <StatusBadge tone={e360.data.money.currentPayrollStatus === "PAID" ? "success" : "warning"}>
                            {e360.data.money.currentPayrollStatus ?? "DRAFT"}
                          </StatusBadge>
                        </span>
                      ) : (
                        <span className="muted">No finalized payroll runs on record.</span>
                      )}
                    </div>
                    {e360.data.money.outstanding > 0 ? (
                      <SAButton size="sm" variant="secondary" onClick={() => openPayoutModal(e360.data?.money.outstanding)}>
                        Disburse {financeAmount(e360.data.money.outstanding)}
                      </SAButton>
                    ) : (
                      <span style={{ color: "var(--success, #10b981)", display: "flex", alignItems: "center", gap: 4 }}>
                        <CheckCircle2 size={13} />
                        Ledger balanced
                      </span>
                    )}
                  </div>
                </SpotlightCard>

                {/* Pillar 3: Operations & Tasks */}
                <SpotlightCard className="e360-col-6">
                  <div className="e360-card-header">
                    <h3>Operations & Tasks</h3>
                    <SAButton variant="ghost" size="sm" onClick={() => setTab("work")}>
                      Tasks
                      <ArrowUpRight size={12} />
                    </SAButton>
                  </div>
                  <div className="e360-metrics-row">
                    <div className="e360-metric-box">
                      <span>Assigned Tasks</span>
                      <strong>{e360.data.operations.tasksAssigned}</strong>
                    </div>
                    <div className="e360-metric-box" style={{ background: e360.data.operations.overdueTasks > 0 ? "var(--danger-soft, rgba(239, 68, 68, 0.08))" : undefined }}>
                      <span>Overdue Tasks</span>
                      <strong style={{ color: e360.data.operations.overdueTasks > 0 ? "var(--danger, #ef4444)" : undefined }}>
                        {e360.data.operations.overdueTasks}
                      </strong>
                    </div>
                  </div>
                  <div style={{ marginTop: 12 }}>
                    <span style={{ fontSize: 11, textTransform: "uppercase", color: "var(--text-3)", letterSpacing: "0.04em" }}>
                      Active Productions ({e360.data.operations.activeProductions.length})
                    </span>
                    {e360.data.operations.activeProductions.length > 0 ? (
                      <div className="e360-production-list">
                        {e360.data.operations.activeProductions.map((p) => (
                          <div key={p.id} className="e360-production-row">
                            <div>
                              <strong>{p.title}</strong>
                              {p.role && <span className="muted" style={{ marginLeft: 6 }}>· {p.role}</span>}
                            </div>
                            <StatusBadge tone="neutral">{p.status}</StatusBadge>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="muted" style={{ fontSize: 12, marginTop: 6 }}>
                        No active production assignments.
                      </p>
                    )}
                  </div>
                </SpotlightCard>

                {/* Pillar 4: Performance & Comms */}
                <SpotlightCard className="e360-col-6">
                  <div className="e360-card-header">
                    <h3>Performance & Device Sync</h3>
                    <SAButton variant="ghost" size="sm" onClick={() => setTab("performance")}>
                      Review
                      <ArrowUpRight size={12} />
                    </SAButton>
                  </div>
                  <div className="e360-metrics-row">
                    <div className="e360-metric-box">
                      <span>Attendance Rate</span>
                      <strong>
                        {e360.data.performance.attendanceRate != null
                          ? `${e360.data.performance.attendanceRate}%`
                          : "—"}
                      </strong>
                    </div>
                    <div className="e360-metric-box">
                      <span>Total Messages</span>
                      <strong>{e360.data.communication.totalMessages}</strong>
                    </div>
                  </div>
                  <div style={{ marginTop: 14, fontSize: 12, display: "flex", flexDirection: "column", gap: 6 }}>
                    <div>
                      <span className="muted">On-time completion: </span>
                      <strong>{e360.data.performance.onTimeCompletionRate != null ? `${e360.data.performance.onTimeCompletionRate}%` : "—"}</strong>
                    </div>
                    <div>
                      <span className="muted">Attended shifts: </span>
                      <strong>{e360.data.performance.attended} / {e360.data.performance.attendanceRecords} days</strong>
                    </div>
                    <div>
                      <span className="muted">Last contact: </span>
                      <span>{e360.data.communication.lastContactAt ? dateLabel(e360.data.communication.lastContactAt.slice(0, 10)) : "Never"}</span>
                    </div>
                    {navigatorEnabled && (
                      <div style={{ marginTop: 4, paddingTop: 8, borderTop: "1px solid var(--border-soft, var(--border))" }}>
                        <span className="muted">Navigator device: </span>
                        <strong>{e360.data.navigator.deviceStatus}</strong>
                        <span className="muted" style={{ marginLeft: 8 }}>({e360.data.navigator.isPaired ? "Paired" : "Unpaired"})</span>
                      </div>
                    )}
                  </div>
                </SpotlightCard>

                {/* Identity & Details Card */}
                <SABentoCard className="e360-col-8">
                  <h3>Employee Information</h3>
                  <dl className="detail-info-list" style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "10px 16px", marginTop: 12 }}>
                    <Info label="Employee code" value={e.employeeCode} />
                    <Info label="Department" value={e.department} />
                    <Info label="Joining date" value={dateLabel(e.joiningDate)} />
                    <Info label="Employment" value={e.employmentType.replace("_", " ")} />
                    <Info label="Phone" value={e.phone} />
                    <Info label="WhatsApp" value={e.whatsappPhone ?? "Not provided"} />
                    <Info label="Email" value={e.email ?? "Not provided"} />
                    <Info label="Status" value={e.status.replace("_", " ")} />
                  </dl>
                </SABentoCard>

                {/* Owner Notes Card */}
                <SABentoCard className="e360-col-4">
                  <h3>Owner Notes</h3>
                  <p style={{ marginTop: 10, fontSize: 13, lineHeight: 1.5 }}>
                    {e.notes || "No internal notes recorded for this employee."}
                  </p>
                </SABentoCard>

                {/* Deactivate Zone */}
                <div className="danger-zone e360-col-12" style={{ marginTop: 8 }}>
                  <div>
                    <strong>Deactivate employee</strong>
                    <span>Keeps historical operational and financial records.</span>
                  </div>
                  <SAButton
                    variant="danger"
                    disabled={e.status === "INACTIVE"}
                    onClick={() => setConfirm(true)}
                  >
                    Deactivate
                  </SAButton>
                </div>
              </div>
            </div>
          )}
        </SATabContent>
        <SATabContent value="finance">
          {finance.isPending ? (
            <SkeletonCard />
          ) : finance.isError || !finance.data ? (
            <EmptyState
              title="Finance unavailable"
              description="This employee's financial ledger could not be loaded."
            />
          ) : (
            <div className="employee-finance-pane">
              <div
                className="section-actions"
                style={{
                  marginBottom: "1rem",
                  display: "flex",
                  gap: "0.75rem",
                  flexWrap: "wrap",
                  alignItems: "center",
                }}
              >
                <SAButton
                  variant="primary"
                  onClick={() => {
                    setEarningRequestKey(generateKey());
                    setEarningForm({
                      amount: "",
                      date: new Date().toISOString().slice(0, 10),
                      description: `Shift labor for ${e.displayName}`,
                      productionId: "",
                    });
                    setEarningError("");
                    setEarningOpen(true);
                  }}
                >
                  Add Shift Earning
                </SAButton>
                <SAButton
                  disabled={finance.data.outstanding <= 0}
                  onClick={() => {
                    setPayoutRequestKey(generateKey());
                    setPayoutForm({
                      amount:
                        finance.data && finance.data.outstanding > 0
                          ? finance.data.outstanding.toFixed(2)
                          : "",
                      date: new Date().toISOString().slice(0, 10),
                      description: `Disbursement to ${e.displayName}`,
                      payerAccount: "",
                    });
                    setPayoutError("");
                    setPayoutOpen(true);
                  }}
                >
                  Record Payout
                </SAButton>
                <SAButton onClick={() => navigate(`/finance?employee=${e.id}`)}>
                  Open Finance ledger
                </SAButton>
              </div>

              <SABentoGrid className="finance-metrics">
                <SABentoCard>
                  <span className="eyebrow">Earned</span>
                  <strong className="metric">
                    {financeAmount(finance.data.earned)}
                  </strong>
                </SABentoCard>
                <SABentoCard>
                  <span className="eyebrow">Paid</span>
                  <strong className="metric">
                    {financeAmount(finance.data.paid)}
                  </strong>
                </SABentoCard>
                <SABentoCard>
                  <span className="eyebrow">Outstanding</span>
                  <strong className="metric">
                    {financeAmount(finance.data.outstanding)}
                  </strong>
                </SABentoCard>
              </SABentoGrid>

              <SABentoCard style={{ marginTop: "1.25rem" }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: "0.75rem",
                  }}
                >
                  <div>
                    <h3
                      style={{
                        margin: 0,
                        fontSize: "1rem",
                        fontWeight: 600,
                      }}
                    >
                      Earned Obligations
                    </h3>
                    <p
                      style={{
                        margin: "0.25rem 0 0",
                        fontSize: "0.8125rem",
                        color: "var(--muted)",
                      }}
                    >
                      Accrued payroll and shift earnings linked to canonical
                      Finance.
                    </p>
                  </div>
                  <span className="status-badge status-badge--neutral">
                    {finance.data.obligations.length} obligation
                    {finance.data.obligations.length === 1 ? "" : "s"}
                  </span>
                </div>
                {finance.data.obligations.length === 0 ? (
                  <p
                    style={{
                      color: "var(--muted)",
                      fontSize: "0.875rem",
                      margin: "1rem 0",
                    }}
                  >
                    No recorded earnings or salary obligations yet.
                  </p>
                ) : (
                  <div className="operations-list">
                    {finance.data.obligations.map((ob) => (
                      <div
                        key={ob.id}
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                          padding: "0.625rem 0.75rem",
                          borderBottom: "1px solid var(--border)",
                        }}
                      >
                        <div>
                          <strong>{ob.description || "Obligation"}</strong>
                          <div
                            style={{
                              fontSize: "0.8125rem",
                              color: "var(--muted)",
                            }}
                          >
                            {ob.date} ·{" "}
                            <span className="status-badge status-badge--neutral">
                              {ob.type === "FIXED_SALARY"
                                ? "Fixed Salary"
                                : ob.type === "WORK_EARNING"
                                  ? "Shift Earning"
                                  : ob.type}
                            </span>
                          </div>
                        </div>
                        <strong style={{ fontSize: "0.9375rem" }}>
                          {financeAmount(ob.amount)}
                        </strong>
                      </div>
                    ))}
                  </div>
                )}
              </SABentoCard>
            </div>
          )}
        </SATabContent>
        <SATabContent value="attendance">
          {attendance.isPending ? (
            <SkeletonCard />
          ) : attendance.isError ? (
            <EmptyState
              title="Attendance unavailable"
              description="Monthly attendance could not be loaded."
            />
          ) : (
            <AttendanceHistory data={attendance.data!} />
          )}
        </SATabContent>
        <SATabContent value="work">
          <OperationsLoading query={operations} />
          {operations.data && (
            <div className="operations-list">
              {operations.data.work.map((t) => (
                <SABentoCard key={t.id}>
                  <div>
                    <strong>{t.title}</strong>
                    <span>
                      {t.productionTitle ?? "Internal"} ·{" "}
                      {t.status.replaceAll("_", " ")}
                    </span>
                  </div>
                  <div className="compact-progress">
                    <b>{t.progressPercent}%</b>
                    <SAProgress value={t.progressPercent} />
                  </div>
                </SABentoCard>
              ))}
            </div>
          )}
        </SATabContent>
        <SATabContent value="payroll">
          <OperationsLoading query={operations} />
          {operations.data && (
            <div className="operations-list">
              {operations.data.payroll.map((p) => {
                const item = p.items[0];
                return (
                  <SABentoCard
                    key={p.id}
                    interactive
                    onClick={() => navigate(`/payroll/${p.id}`)}
                  >
                    <div>
                      <strong>
                        {month(p.month)} {p.year}
                      </strong>
                      <span>
                        {money(item.baseSalaryMinor, item.salaryCurrency)} base
                        · {money(item.totalPaid, item.salaryCurrency)} paid
                      </span>
                    </div>
                    <div>
                      <strong>
                        {money(item.remaining, item.salaryCurrency)} remaining
                      </strong>
                      <StatusBadge
                        tone={
                          item.paymentStatus === "PAID" ? "success" : "warning"
                        }
                      >
                        {item.paymentStatus}
                      </StatusBadge>
                    </div>
                  </SABentoCard>
                );
              })}
            </div>
          )}
        </SATabContent>
        <SATabContent value="performance">
          <OperationsLoading query={operations} />
          {operations.data && <Performance data={operations.data} />}
        </SATabContent>
        <SATabContent value="communication">
          {communications.isPending ? (
            <SkeletonCard />
          ) : communications.isError ? (
            <EmptyState
              title="Messages unavailable"
              description="Communication history could not be loaded."
            />
          ) : communications.data?.messages.length ? (
            <div className="operations-list">
              {communications.data.messages.map((m) => (
                <SABentoCard
                  key={m.id}
                  interactive
                  onClick={() => navigate(`/communications?employeeId=${e.id}`)}
                >
                  <div>
                    <strong>{m.bodyPreview}</strong>
                    <span>
                      {new Date(m.queuedAt).toLocaleString("en-IN")} ·{" "}
                      {m.category}
                    </span>
                  </div>
                  <StatusBadge
                    tone={
                      m.status === "FAILED"
                        ? "danger"
                        : m.status === "READ" || m.status === "DELIVERED"
                          ? "success"
                          : "neutral"
                    }
                  >
                    {m.status}
                  </StatusBadge>
                </SABentoCard>
              ))}
            </div>
          ) : (
            <EmptyState
              title="No messages yet"
              description="This employee has no outbound communication history."
              action={
                <SAButton
                  onClick={() =>
                    navigate(`/communications?compose=1&employeeId=${e.id}`)
                  }
                >
                  Send message
                </SAButton>
              }
            />
          )}
        </SATabContent>
        {navigatorEnabled && (
          <SATabContent value="navigator">
            <EmployeeNavigatorPanel employeeId={e.id} />
          </SATabContent>
        )}
      </SATabs>
      <ConfirmAction
        open={confirm}
        onOpenChange={setConfirm}
        title="Deactivate employee?"
        description="The profile and all historical operational records remain available."
        confirmLabel="Deactivate"
        danger
        pending={deactivate.isPending}
        onConfirm={() => deactivate.mutate()}
      />

      <SAModal
        open={earningOpen}
        onOpenChange={setEarningOpen}
        title="Add Shift Earning"
        description={`Record labor cost obligation for ${e.displayName}. Accrues an employee payable obligation in canonical Finance.`}
      >
        <div className="form-grid">
          <FormField label="Earning Amount (₹)" error={earningError}>
            <input
              aria-label="Shift earning amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 3500"
              value={earningForm.amount}
              onChange={(e) => {
                setEarningError("");
                setEarningForm({ ...earningForm, amount: e.target.value });
              }}
            />
          </FormField>
          <FormField label="Shift Date">
            <input
              aria-label="Shift earning date"
              type="date"
              required
              value={earningForm.date}
              onChange={(e) =>
                setEarningForm({ ...earningForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Production (Optional)">
            <select
              aria-label="Shift production"
              value={earningForm.productionId}
              onChange={(e) =>
                setEarningForm({ ...earningForm, productionId: e.target.value })
              }
            >
              <option value="">None / Independent Shift</option>
              {productions.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Description / Shift Notes">
            <input
              aria-label="Shift earning description"
              required
              placeholder="e.g. On-site sound engineering shift"
              value={earningForm.description}
              onChange={(e) =>
                setEarningForm({ ...earningForm, description: e.target.value })
              }
            />
          </FormField>
        </div>
        {earningError && <p className="form-error">{earningError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setEarningOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              addEarningMutation.isPending ||
              !earningForm.amount ||
              Number(earningForm.amount) <= 0 ||
              !earningForm.date ||
              !earningForm.description.trim()
            }
            onClick={() => {
              if (addEarningMutation.isPending) return;
              const amt = Number(earningForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setEarningError("Amount must be a positive number.");
                return;
              }
              addEarningMutation.mutate();
            }}
          >
            {addEarningMutation.isPending ? "Adding…" : "Add Earning"}
          </SAButton>
        </div>
      </SAModal>

      <SAModal
        open={payoutOpen}
        onOpenChange={setPayoutOpen}
        title="Record Employee Payout"
        description={`Disburse payment to ${e.displayName} from an owner cash account. Reduces open obligations via FIFO allocation.`}
      >
        <div className="form-grid">
          <FormField label="Payout Amount (₹)" error={payoutError}>
            <input
              aria-label="Payout amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 5000"
              value={payoutForm.amount}
              onChange={(e) => {
                setPayoutError("");
                setPayoutForm({ ...payoutForm, amount: e.target.value });
              }}
            />
          </FormField>
          <FormField label="Payer Account (Owner Position)">
            <select
              aria-label="Payout payer account"
              required
              value={payoutForm.payerAccount}
              onChange={(e) =>
                setPayoutForm({
                  ...payoutForm,
                  payerAccount: e.target.value as "" | "AZ-2" | "AK-2",
                })
              }
            >
              <option value="">Select owner account...</option>
              <option value="AZ-2">AZ-2 (Azeem)</option>
              <option value="AK-2">AK-2 (Akash)</option>
            </select>
          </FormField>
          <FormField label="Payment Date">
            <input
              aria-label="Payout date"
              type="date"
              required
              value={payoutForm.date}
              onChange={(e) =>
                setPayoutForm({ ...payoutForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Description / Reference">
            <input
              aria-label="Payout description"
              required
              placeholder="e.g. Bank transfer payment"
              value={payoutForm.description}
              onChange={(e) =>
                setPayoutForm({ ...payoutForm, description: e.target.value })
              }
            />
          </FormField>
        </div>
        {payoutError && <p className="form-error">{payoutError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setPayoutOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              recordPayoutMutation.isPending ||
              !payoutForm.amount ||
              Number(payoutForm.amount) <= 0 ||
              !payoutForm.date ||
              !payoutForm.description.trim() ||
              !payoutForm.payerAccount
            }
            onClick={() => {
              if (recordPayoutMutation.isPending) return;
              const amt = Number(payoutForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setPayoutError("Amount must be a positive number.");
                return;
              }
              if (finance.data && amt > finance.data.outstanding) {
                setPayoutError(
                  `Amount cannot exceed outstanding balance (${financeAmount(finance.data.outstanding)}).`,
                );
                return;
              }
              recordPayoutMutation.mutate();
            }}
          >
            {recordPayoutMutation.isPending ? "Recording…" : "Record Payout"}
          </SAButton>
        </div>
      </SAModal>
    </>
  );
}
function OperationsLoading({
  query,
}: {
  query: { isPending: boolean; isError: boolean };
}) {
  return query.isPending ? (
    <SkeletonCard />
  ) : query.isError ? (
    <EmptyState
      title="Operational data unavailable"
      description="Work, payroll and evidence could not be loaded."
    />
  ) : null;
}
function Performance({ data }: { data: EmployeeOperations }) {
  const p = data.performance;
  return (
    <SABentoGrid className="performance-grid">
      <Metric
        label="Attendance rate"
        value={p.attendanceRate == null ? "—" : `${p.attendanceRate}%`}
      />
      <Metric label="Late records" value={p.lateCount} />
      <Metric
        label="Tasks completed"
        value={`${p.tasksCompleted}/${p.tasksAssigned}`}
      />
      <Metric label="Overdue tasks" value={p.overdueTasks} />
      <Metric label="Active productions" value={p.activeProductions} />
      <Metric label="Delivered productions" value={p.completedProductions} />
      <SABentoCard className="evidence-note">
        <h3>Evidence, not a score</h3>
        <p>
          These facts come from attendance, tasks and crew assignments. SA
          Command does not infer personality or intelligence.
        </p>
      </SABentoCard>
    </SABentoGrid>
  );
}
function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <SABentoCard>
      <strong className="metric">{value}</strong>
      <span className="muted">{label}</span>
    </SABentoCard>
  );
}
function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
function AttendanceHistory({ data }: { data: AttendanceMonth }) {
  return (
    <SABentoGrid className="history-grid">
      <Metric label="Present days" value={data.summary.PRESENT ?? 0} />
      <Metric label="Late days" value={data.summary.LATE ?? 0} />
      <Metric label="Leave days" value={data.summary.LEAVE ?? 0} />
      <SABentoCard className="history-list">
        <h3>Monthly history</h3>
        {data.records.length ? (
          data.records.map((r) => (
            <div key={r.id}>
              <time>{dateLabel(r.date)}</time>
              <StatusBadge
                tone={
                  r.status === "PRESENT"
                    ? "success"
                    : r.status === "LATE"
                      ? "warning"
                      : r.status === "ABSENT"
                        ? "danger"
                        : "neutral"
                }
              >
                {r.status.replace("_", " ")}
              </StatusBadge>
              <span>{r.checkInTime?.slice(0, 5) ?? "—"}</span>
            </div>
          ))
        ) : (
          <p className="muted">No records this month.</p>
        )}
      </SABentoCard>
    </SABentoGrid>
  );
}
const dateLabel = (date: string) =>
  new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${date}T00:00:00`));
const money = (minor: number, currency = "INR") =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(minor / 100);
const month = (n: number) =>
  new Intl.DateTimeFormat("en-IN", { month: "long" }).format(
    new Date(2026, n - 1, 1),
  );
const generateKey = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (c) =>
        (
          +c ^
          (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (+c / 4)))
        ).toString(16),
      );
