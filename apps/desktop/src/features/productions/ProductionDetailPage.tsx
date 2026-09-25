import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Boxes,
  CalendarDays,
  MapPin,
  Plus,
  Users,
} from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, api, json } from "../../lib/api";
import { financeApi, financeAmount } from "../finance/finance.api";
import type {
  Employee,
  Priority,
  Production,
  ProductionStatus,
  WorkTask,
} from "../../types/domain";
import {
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
const next: Partial<Record<ProductionStatus, ProductionStatus>> = {
  DRAFT: "PLANNING",
  PLANNING: "PRE_PRODUCTION",
  PRE_PRODUCTION: "PRODUCTION",
  PRODUCTION: "POST_PRODUCTION",
  POST_PRODUCTION: "REVIEW",
  REVIEW: "DELIVERED",
};
export function ProductionDetailPage() {
  const { id } = useParams(),
    navigate = useNavigate(),
    client = useQueryClient();
  const [tab, setTab] = useState("overview"),
    [crewOpen, setCrewOpen] = useState(false),
    [editOpen, setEditOpen] = useState(false),
    [edit, setEdit] = useState({
      title: "",
      clientName: "",
      description: "",
      eventDate: "",
      startTime: "",
      endTime: "",
      venueName: "",
      venueAddress: "",
      priority: "NORMAL" as Priority,
      progressPercent: 0,
    }),
    [employeeId, setEmployeeId] = useState(""),
    [role, setRole] = useState("Crew"),
    [conflict, setConflict] = useState<ApiError | null>(null);
  const production = useQuery({
    queryKey: ["production", id],
    queryFn: () => api<Production>(`/productions/${id}`),
    enabled: !!id,
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const tasks = useQuery({
    queryKey: ["tasks", "production", id],
    queryFn: () => api<WorkTask[]>(`/tasks?production=${id}`),
    enabled: !!id,
  });
  const activity = useQuery({
    queryKey: ["audit", "production", id],
    queryFn: () =>
      api<
        Array<{
          id: string;
          action: string;
          createdAt: string;
          actorId: string;
        }>
      >(`/audit?entityType=PRODUCTION&entityId=${id}`),
    enabled: !!id && tab === "activity",
  });
  const finance = useQuery({ queryKey: ["finance", "production", id], queryFn: () => financeApi.production(id!), enabled: !!id && tab === "finance" });
  const transition = useMutation({
    mutationFn: (status: ProductionStatus) =>
      api<Production>(`/productions/${id}/transition`, {
        method: "POST",
        ...json({ status }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
  const assign = useMutation({
    mutationFn: (override: boolean) =>
      api<Production>(`/productions/${id}/members`, {
        method: "POST",
        ...json({
          employeeId,
          productionRole: role,
          attendanceRequired: true,
          assignmentStatus: "PENDING",
          overrideConflict: override,
          overrideReason: override
            ? "Owner approved overlapping commitment"
            : null,
        }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["calendar"] });
      setCrewOpen(false);
      setConflict(null);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === "SCHEDULING_CONFLICT")
        setConflict(e);
    },
  });
  const save = useMutation({
    mutationFn: () =>
      api<Production>(`/productions/${id}`, {
        method: "PATCH",
        ...json(edit),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["calendar"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setEditOpen(false);
    },
  });
  const memberStatus = useMutation({
    mutationFn: ({
      employeeId,
      assignmentStatus,
    }: {
      employeeId: string;
      assignmentStatus: "CONFIRMED" | "DECLINED";
    }) =>
      api<Production>(`/productions/${id}/members/${employeeId}`, {
        method: "PATCH",
        ...json({ assignmentStatus }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["calendar"] });
    },
  });
  if (production.isPending) return <SkeletonCard />;
  if (!production.data || production.isError)
    return (
      <EmptyState
        title="Production unavailable"
        description="This production could not be loaded."
      />
    );
  const p = production.data;
  return (
    <>
      <button className="back-link" onClick={() => navigate("/productions")}>
        <ArrowLeft size={15} />
        Productions
      </button>
      <SABentoCard className="production-hero">
        <div>
          <span className="eyebrow">{p.clientName}</span>
          <h1>{p.title}</h1>
          <p>
            <CalendarDays size={14} />
            {longDate(p.eventDate)} · {p.startTime.slice(0, 5)}{" "}
            <MapPin size={14} />
            {p.venueName}
          </p>
        </div>
        <div className="hero-progress">
          <StatusBadge tone={p.priority === "URGENT" ? "danger" : "warning"}>
            {p.status.replaceAll("_", " ")}
          </StatusBadge>
          <strong>{p.progressPercent}%</strong>
          <SAProgress value={p.progressPercent} />
          {next[p.status] && (
            <SAButton
              variant="primary"
              onClick={() => transition.mutate(next[p.status]!)}
            >
              Move to {next[p.status]!.replaceAll("_", " ")}
            </SAButton>
          )}
          {!(["DELIVERED", "CANCELLED"] as ProductionStatus[]).includes(
            p.status,
          ) && (
            <SAButton
              onClick={() => {
                setEdit({
                  title: p.title,
                  clientName: p.clientName,
                  description: p.description ?? "",
                  eventDate: p.eventDate,
                  startTime: p.startTime.slice(0, 5),
                  endTime: p.endTime.slice(0, 5),
                  venueName: p.venueName,
                  venueAddress: p.venueAddress ?? "",
                  priority: p.priority,
                  progressPercent: p.progressPercent,
                });
                setEditOpen(true);
              }}
            >
              Edit production
            </SAButton>
          )}
        </div>
      </SABentoCard>
      <SATabs
        value={tab}
        onValueChange={setTab}
        tabs={[
          "overview",
          "crew",
          "tasks",
          "schedule",
          "equipment",
          "finance",
          "notes",
          "activity",
        ].map((value) => ({
          value,
          label: value[0].toUpperCase() + value.slice(1),
        }))}
      >
        <SATabContent value="overview">
          <SABentoGrid className="production-detail-grid">
            <SABentoCard>
              <h3>Operational brief</h3>
              <p>{p.description || "No production notes yet."}</p>
              <dl>
                <div>
                  <dt>Venue</dt>
                  <dd>{p.venueName}</dd>
                </div>
                <div>
                  <dt>Priority</dt>
                  <dd>{p.priority}</dd>
                </div>
              </dl>
            </SABentoCard>
            <SABentoCard>
              <strong className="metric">{p.members.length}</strong>
              <span className="muted">crew assigned</span>
            </SABentoCard>
            <SABentoCard>
              <strong className="metric">{p.unfinishedTaskCount}</strong>
              <span className="muted">unfinished tasks</span>
            </SABentoCard>
          </SABentoGrid>
        </SATabContent>
        <SATabContent value="crew">
          <div className="section-heading">
            <div>
              <h2>Crew</h2>
              <p>Assignments share the canonical calendar conflict surface.</p>
            </div>
            <SAButton variant="primary" onClick={() => setCrewOpen(true)}>
              <Plus size={15} />
              Assign crew
            </SAButton>
          </div>
          <div className="operations-list">
            {p.members.map((m) => (
              <SABentoCard key={m.id}>
                <div>
                  <strong>{m.employeeName}</strong>
                  <span>{m.productionRole}</span>
                </div>
                <StatusBadge
                  tone={
                    m.conflictOverridden
                      ? "warning"
                      : m.assignmentStatus === "CONFIRMED"
                        ? "success"
                        : "neutral"
                  }
                >
                  {m.conflictOverridden
                    ? "Conflict overridden"
                    : m.assignmentStatus}
                </StatusBadge>
                {m.assignmentStatus === "PENDING" && (
                  <div className="compact-actions">
                    <SAButton
                      size="sm"
                      onClick={() =>
                        memberStatus.mutate({
                          employeeId: m.employeeId,
                          assignmentStatus: "CONFIRMED",
                        })
                      }
                    >
                      Confirm
                    </SAButton>
                    <SAButton
                      size="sm"
                      onClick={() =>
                        memberStatus.mutate({
                          employeeId: m.employeeId,
                          assignmentStatus: "DECLINED",
                        })
                      }
                    >
                      Decline
                    </SAButton>
                  </div>
                )}
              </SABentoCard>
            ))}
          </div>
        </SATabContent>
        <SATabContent value="tasks">
          <div className="operations-list">
            {tasks.data?.map((t) => (
              <SABentoCard key={t.id}>
                <div>
                  <strong>{t.title}</strong>
                  <span>
                    {t.assigneeName ?? "Unassigned"} ·{" "}
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
        </SATabContent>
        <SATabContent value="equipment">
          <SABentoCard className="production-equipment-entry">
            <div>
              <Boxes size={20} />
              <h3>Headquarters equipment plan</h3>
              <p>
                Review reservations, dispatches, venue custody, returns and
                unresolved equipment for this production.
              </p>
            </div>
            <SAButton
              variant="primary"
              onClick={() => navigate(`/headquarters?production=${p.id}`)}
            >
              Open Headquarters
            </SAButton>
          </SABentoCard>
        </SATabContent>
        <SATabContent value="finance">
          {finance.isPending ? <SkeletonCard /> : finance.isError || !finance.data ? <EmptyState title="Finance unavailable" description="This production's financial summary could not be loaded." /> : <SABentoGrid className="finance-metrics">
            <SABentoCard><span className="eyebrow">Contracted</span><strong className="metric">{financeAmount(finance.data.production.contracted)}</strong></SABentoCard>
            <SABentoCard><span className="eyebrow">Received</span><strong className="metric">{financeAmount(finance.data.received)}</strong><span className="muted">Outstanding {financeAmount(finance.data.outstanding)}</span></SABentoCard>
            <SABentoCard><span className="eyebrow">Realized margin</span><strong className="metric">{financeAmount(finance.data.realizedMargin)}</strong><span className="muted">Contracted margin {financeAmount(finance.data.contractedMargin)}</span><SAButton onClick={() => navigate(`/finance?production=${p.id}`)}>Open Finance</SAButton></SABentoCard>
          </SABentoGrid>}
        </SATabContent>
        <SATabContent value="schedule">
          <SABentoCard>
            <h3>Linked calendar event</h3>
            <p>
              {longDate(p.eventDate)} · {p.startTime.slice(0, 5)}–
              {p.endTime.slice(0, 5)} · {p.venueName}
            </p>
          </SABentoCard>
        </SATabContent>
        <SATabContent value="notes">
          <SABentoCard>
            <h3>Production notes</h3>
            <p>{p.description || "No notes have been added."}</p>
          </SABentoCard>
        </SATabContent>
        <SATabContent value="activity">
          <div className="activity-list">
            {activity.data?.map((a) => (
              <div key={a.id}>
                <strong>{a.action.replaceAll("_", " ")}</strong>
                <span>{new Date(a.createdAt).toLocaleString()}</span>
              </div>
            ))}
          </div>
        </SATabContent>
      </SATabs>
      <SAModal
        open={editOpen}
        onOpenChange={setEditOpen}
        title="Edit production"
        description="Schedule changes update the linked calendar event and audit history."
      >
        <div className="form-grid">
          <FormField label="Title">
            <input
              aria-label="Edit production title"
              value={edit.title}
              onChange={(e) => setEdit({ ...edit, title: e.target.value })}
            />
          </FormField>
          <FormField label="Client">
            <input
              aria-label="Edit client name"
              value={edit.clientName}
              onChange={(e) => setEdit({ ...edit, clientName: e.target.value })}
            />
          </FormField>
          <FormField label="Date">
            <input
              aria-label="Edit event date"
              type="date"
              value={edit.eventDate}
              onChange={(e) => setEdit({ ...edit, eventDate: e.target.value })}
            />
          </FormField>
          <FormField label="Priority">
            <select
              aria-label="Edit production priority"
              value={edit.priority}
              onChange={(e) =>
                setEdit({ ...edit, priority: e.target.value as Priority })
              }
            >
              {["LOW", "NORMAL", "HIGH", "URGENT"].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </FormField>
          <FormField label="Start">
            <input
              aria-label="Edit start time"
              type="time"
              value={edit.startTime}
              onChange={(e) => setEdit({ ...edit, startTime: e.target.value })}
            />
          </FormField>
          <FormField label="End">
            <input
              aria-label="Edit end time"
              type="time"
              value={edit.endTime}
              onChange={(e) => setEdit({ ...edit, endTime: e.target.value })}
            />
          </FormField>
          <FormField label="Venue">
            <input
              aria-label="Edit venue name"
              value={edit.venueName}
              onChange={(e) => setEdit({ ...edit, venueName: e.target.value })}
            />
          </FormField>
          <FormField label="Progress">
            <input
              aria-label="Edit production progress"
              type="number"
              min="0"
              max="100"
              value={edit.progressPercent}
              onChange={(e) =>
                setEdit({ ...edit, progressPercent: Number(e.target.value) })
              }
            />
          </FormField>
        </div>
        {save.error && <p className="form-error">{save.error.message}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setEditOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              save.isPending ||
              !edit.title ||
              !edit.clientName ||
              !edit.venueName
            }
            onClick={() => save.mutate()}
          >
            Save production
          </SAButton>
        </div>
      </SAModal>
      <SAModal
        open={crewOpen}
        onOpenChange={setCrewOpen}
        title="Assign crew"
        description="Conflicts are checked against production, meeting and calendar commitments."
      >
        <FormField label="Employee">
          <select
            aria-label="Crew employee"
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value)}
          >
            <option value="">Choose employee</option>
            {employees.data
              ?.filter((e) => !p.members.some((m) => m.employeeId === e.id))
              .map((e) => (
                <option key={e.id} value={e.id}>
                  {e.displayName}
                </option>
              ))}
          </select>
        </FormField>
        <FormField label="Production role">
          <input
            aria-label="Production role"
            value={role}
            onChange={(e) => setRole(e.target.value)}
          />
        </FormField>
        {assign.error && !conflict && (
          <p className="form-error">{assign.error.message}</p>
        )}
        <div className="modal-actions">
          <SAButton onClick={() => setCrewOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={!employeeId || assign.isPending}
            onClick={() => assign.mutate(false)}
          >
            Assign
          </SAButton>
        </div>
      </SAModal>
      <SAModal
        open={!!conflict}
        onOpenChange={(v) => !v && setConflict(null)}
        title="Scheduling conflict"
        description={
          conflict
            ? `${conflict.fields.title} · ${new Date(conflict.fields.startsAt).toLocaleString()}–${new Date(conflict.fields.endsAt).toLocaleTimeString()}`
            : undefined
        }
      >
        <p>
          The employee already has an overlapping commitment. Assigning anyway
          will be audited.
        </p>
        <div className="modal-actions">
          <SAButton onClick={() => setConflict(null)}>Cancel</SAButton>
          <SAButton variant="primary" onClick={() => assign.mutate(true)}>
            Assign anyway
          </SAButton>
        </div>
      </SAModal>
    </>
  );
}
const longDate = (v: string) =>
  new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(`${v}T00:00:00`));
