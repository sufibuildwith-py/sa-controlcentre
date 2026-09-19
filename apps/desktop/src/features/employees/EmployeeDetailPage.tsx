import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Edit3,
  MessageCircle,
  Phone,
  WalletCards,
} from "lucide-react";
import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useUiStore } from "../../app/store/ui";
import {
  ConfirmAction,
  EmptyState,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SAProgress,
  SATabContent,
  SATabs,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api } from "../../lib/api";
import type {
  AttendanceMonth,
  Employee,
  EmployeeOperations,
} from "../../types/domain";
import { initials } from "./PeoplePage";
export function EmployeeDetailPage() {
  const { id } = useParams(),
    location = useLocation(),
    navigate = useNavigate(),
    client = useQueryClient(),
    openForm = useUiStore((s) => s.openEmployeeForm);
  const requestedTab = new URLSearchParams(location.search).get("tab"),
    [tab, setTab] = useState(
      ["attendance", "work", "payroll", "performance"].includes(
        requestedTab ?? "",
      )
        ? requestedTab!
        : "overview",
    ),
    [confirm, setConfirm] = useState(false);
  const employee = useQuery({
    queryKey: ["employee", id],
    queryFn: () => api<Employee>(`/employees/${id}`),
    enabled: !!id,
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
  const deactivate = useMutation({
    mutationFn: () =>
      api<Employee>(`/employees/${id}/deactivate`, { method: "POST" }),
    onSuccess: (data) => {
      client.setQueryData(["employee", id], data);
      client.invalidateQueries({ queryKey: ["employees"] });
      setConfirm(false);
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
          <SAButton disabled title="Available in Phase 3">
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
          { value: "performance", label: "Performance" },
          { value: "communication", label: "Communication" },
        ]}
      >
        <SATabContent value="overview">
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
                  <SABentoCard key={p.id}>
                    <div>
                      <strong>
                        {month(p.month)} {p.year}
                      </strong>
                      <span>
                        {money(item.baseSalaryMinor, item.salaryCurrency)} base
                        · {p.status}
                      </span>
                    </div>
                    <div>
                      <strong>
                        {money(item.netSalaryMinor, item.salaryCurrency)}
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
          <EmptyState
            title="Communication arrives in Phase 3"
            description="No messages or acknowledgement states are fabricated in Phase 2."
          />
        </SATabContent>
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
