import { useQuery } from "@tanstack/react-query";
import {
  ArrowUpRight,
  CalendarClock,
  MessageCircle,
  Users,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  CardHeader,
  EmptyState,
  SABentoCard,
  SABentoGrid,
  SAProgress,
  SARadialMetric,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api } from "../../lib/api";
import type { Dashboard } from "../../types/domain";
export function CommandPage() {
  const navigate = useNavigate();
  const dashboard = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api<Dashboard>("/dashboard"),
  });
  const now = new Date();
  if (dashboard.isPending)
    return (
      <>
        <WorkspaceHeader
          title={`${greeting()}, Owner.`}
          subtitle="Loading the operating picture…"
        />
        <SkeletonCard />
      </>
    );
  if (dashboard.isError || !dashboard.data)
    return (
      <EmptyState
        title="Command unavailable"
        description="Operational aggregates could not be loaded."
      />
    );
  const d = dashboard.data,
    total = d.team.employees,
    present = d.team.present + d.team.late,
    attendancePct = total
      ? Math.round(((total - d.team.incomplete) / total) * 100)
      : 0;
  const load = d.workload.reduce((sum, x) => sum + x.active, 0),
    overloaded = d.workload.filter(
      (x) => x.active >= 4 || x.overdue > 0,
    ).length;
  return (
    <>
      <WorkspaceHeader
        title={`${greeting()}, Owner.`}
        subtitle="Here’s the live operating picture for today."
      />
      <SABentoGrid className="command-grid">
        <SABentoCard className="time-card">
          <span className="eyebrow">Local time</span>
          <strong className="hero-time">
            {now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </strong>
          <p>
            {now.toLocaleDateString([], {
              weekday: "long",
              month: "long",
              day: "numeric",
            })}
          </p>
          <div className="time-marker">
            <CalendarClock size={15} />
            {d.today.length ? `Next: ${d.today[0].title}` : "Schedule is clear"}
          </div>
        </SABentoCard>
        <SABentoCard
          interactive
          className="today-card"
          onClick={() => navigate("/calendar")}
        >
          <CardHeader
            eyebrow="Live schedule"
            title="Today"
            action={<ArrowUpRight size={16} />}
          />
          <div className="timeline">
            {d.today.length ? (
              d.today.slice(0, 3).map((item) => (
                <div key={item.id}>
                  <time>
                    {new Date(item.startsAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </time>
                  <span />
                  <div>
                    <strong>{item.title}</strong>
                    <small>{item.type}</small>
                  </div>
                </div>
              ))
            ) : (
              <p className="muted">No scheduled commitments today.</p>
            )}
          </div>
        </SABentoCard>
        <SABentoCard className="team-card">
          <CardHeader eyebrow="Live attendance" title="Team status" />
          <div className="team-content">
            <SARadialMetric
              value={attendancePct}
              label={`${total - d.team.incomplete} of ${total} accounted`}
            />
            <div className="team-breakdown">
              <div>
                <span>Present</span>
                <strong>{d.team.present}</strong>
              </div>
              <div>
                <span>Late</span>
                <strong>{d.team.late}</strong>
              </div>
              <div>
                <span>Absent</span>
                <strong>{d.team.absent}</strong>
              </div>
              <div>
                <span>Leave</span>
                <strong>{d.team.leave}</strong>
              </div>
            </div>
          </div>
        </SABentoCard>
        <SABentoCard
          interactive
          className="attendance-card"
          onClick={() => navigate("/attendance")}
        >
          <CardHeader eyebrow="Attendance" title="Today" />
          <strong className="compact-metric">{present}</strong>
          <span className="muted">present or checked in</span>
          <div className="mini-footer">
            <Users size={15} />
            {total} active people
          </div>
        </SABentoCard>
        <SABentoCard className="comms-card">
          <CardHeader eyebrow="Phase 3 boundary" title="Communications" />
          <div className="donut-mini">
            <MessageCircle size={19} />
            <strong>—</strong>
            <span>provider unavailable</span>
          </div>
          <div className="inline-stats">
            <span>No fabricated delivery metrics</span>
          </div>
        </SABentoCard>
        <SABentoCard
          interactive
          className="productions-card"
          onClick={() => navigate("/productions")}
        >
          <CardHeader
            eyebrow="Live operations"
            title="Active productions"
            action={<StatusBadge tone="success">Real data</StatusBadge>}
          />
          <div className="production-list">
            {d.productions.slice(0, 2).map((p) => (
              <div key={p.id}>
                <div>
                  <strong>{p.title}</strong>
                  <span>
                    {p.status.replaceAll("_", " ")} ·{" "}
                    {new Date(`${p.eventDate}T00:00`).toLocaleDateString(
                      "en-IN",
                      { day: "2-digit", month: "short" },
                    )}
                  </span>
                </div>
                <div>
                  <b>{p.progressPercent}%</b>
                  <SAProgress value={p.progressPercent} />
                </div>
              </div>
            ))}
          </div>
        </SABentoCard>
        <SABentoCard
          interactive
          className="workload-card"
          onClick={() => navigate("/work")}
        >
          <CardHeader eyebrow="Live tasks" title="Workload" />
          <div className="workload-ring">
            <SARadialMetric
              value={Math.min(
                100,
                Math.round((load / Math.max(total * 4, 1)) * 100),
              )}
              label={`${load} active tasks`}
            />
          </div>
          <p>
            <strong>{overloaded}</strong> crew members need review
          </p>
        </SABentoCard>
        <SABentoCard
          interactive
          className="payroll-card"
          onClick={() => navigate("/payroll")}
        >
          <CardHeader eyebrow="Financial record" title="Payroll" />
          <strong className="compact-metric">
            {d.payroll ? money(d.payroll.totalMinor) : "Not calculated"}
          </strong>
          <span className="muted">
            {d.payroll
              ? `${month(d.payroll.month)} ${d.payroll.year}`
              : "Current period"}
          </span>
          <StatusBadge
            tone={d.payroll?.status === "LOCKED" ? "success" : "neutral"}
          >
            {d.payroll?.status ?? "ACTION NEEDED"}
          </StatusBadge>
        </SABentoCard>
        <SABentoCard className="attention-card">
          <CardHeader eyebrow="Owner queue" title="Needs attention" />
          <div className="attention-list">
            {d.attention.slice(0, 3).map((item) => (
              <div key={item.code} onClick={() => navigate(item.href)}>
                <StatusBadge tone={item.tone}>
                  {item.tone === "danger" ? "Urgent" : "Review"}
                </StatusBadge>
                <div>
                  <strong>{item.title}</strong>
                  <span>{item.detail}</span>
                </div>
                <ArrowUpRight size={15} />
              </div>
            ))}
          </div>
        </SABentoCard>
      </SABentoGrid>
    </>
  );
}
function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
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
