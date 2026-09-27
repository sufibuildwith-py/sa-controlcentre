import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Boxes,
  CalendarDays,
  CheckSquare,
  Clock,
  Edit3,
  MapPin,
  Plus,
  Trash2,
  Users,
  X,
} from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, api, json } from "../../lib/api";
import { financeApi, financeAmount } from "../finance/finance.api";
import { headquartersApi } from "../headquarters/headquarters.api";
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

  const [contractOpen, setContractOpen] = useState(false);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [expenseOpen, setExpenseOpen] = useState(false);
  const [earningOpen, setEarningOpen] = useState(false);

  const [contractRequestKey, setContractRequestKey] = useState(generateKey());
  const [receiptRequestKey, setReceiptRequestKey] = useState(generateKey());
  const [expenseRequestKey, setExpenseRequestKey] = useState(generateKey());
  const [earningRequestKey, setEarningRequestKey] = useState(generateKey());

  const [contractError, setContractError] = useState("");
  const [receiptError, setReceiptError] = useState("");
  const [expenseError, setExpenseError] = useState("");
  const [earningError, setEarningError] = useState("");

  const [contractForm, setContractForm] = useState({
    amount: "",
    date: "",
    description: "",
  });
  const [receiptForm, setReceiptForm] = useState({
    amount: "",
    receiverAccount: "",
    date: "",
    description: "",
    counterpartyId: "",
    legacyType: "ADD",
  });
  const [expenseForm, setExpenseForm] = useState({
    amount: "",
    payerAccount: "",
    categoryCode: "TRANSPORT",
    date: "",
    description: "",
    counterpartyId: "",
    employeeId: "",
  });
  const [earningForm, setEarningForm] = useState({
    employeeId: "",
    amount: "",
    date: "",
    description: "",
  });

  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [taskForm, setTaskForm] = useState({
    title: "",
    priority: "NORMAL" as Priority,
    assignedEmployeeId: "",
  });

  const [equipmentModalOpen, setEquipmentModalOpen] = useState(false);
  const [equipmentForm, setEquipmentForm] = useState({
    equipmentId: "",
    quantity: "1",
  });
  const [equipmentError, setEquipmentError] = useState("");

  const [notesModalOpen, setNotesModalOpen] = useState(false);
  const [notesText, setNotesText] = useState("");

  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [scheduleForm, setScheduleForm] = useState({
    startTime: "",
    endTime: "",
  });

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
  const finance = useQuery({
    queryKey: ["finance", "production", id],
    queryFn: () => financeApi.production(id!),
    enabled: !!id,
  });
  const financeConfig = useQuery({
    queryKey: ["finance", "config"],
    queryFn: () => financeApi.config(),
  });
  const counterparties = useQuery({
    queryKey: ["counterparties"],
    queryFn: () => financeApi.counterparties(0),
  });

  const setContractMutation = useMutation({
    mutationFn: () => {
      const amt = Number(contractForm.amount);
      return financeApi.setContract({
        idempotencyKey: contractRequestKey,
        productionId: id!,
        amount: amt,
        date: contractForm.date,
        description: contractForm.description.trim(),
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "production", id] });
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["productions"] });
      setContractOpen(false);
      setContractError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setContractError(err.message);
      } else if (err instanceof Error) {
        setContractError(err.message);
      }
    },
  });

  const recordReceiptMutation = useMutation({
    mutationFn: () => {
      const amt = Number(receiptForm.amount);
      return financeApi.recordReceipt({
        idempotencyKey: receiptRequestKey,
        productionId: id!,
        amount: amt,
        date: receiptForm.date,
        description: receiptForm.description.trim(),
        receiverAccount: receiptForm.receiverAccount,
        counterpartyId: receiptForm.counterpartyId || undefined,
        legacyType: receiptForm.legacyType || undefined,
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "production", id] });
      client.invalidateQueries({ queryKey: ["finance"] });
      setReceiptOpen(false);
      setReceiptError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setReceiptError(err.message);
      } else if (err instanceof Error) {
        setReceiptError(err.message);
      }
    },
  });

  const logExpenseMutation = useMutation({
    mutationFn: () => {
      const amt = Number(expenseForm.amount);
      return financeApi.logExpense({
        idempotencyKey: expenseRequestKey,
        amount: amt,
        date: expenseForm.date,
        description: expenseForm.description.trim(),
        payerAccount: expenseForm.payerAccount,
        productionId: id!,
        counterpartyId: expenseForm.counterpartyId || undefined,
        employeeId: expenseForm.employeeId || undefined,
        categoryCode: expenseForm.categoryCode || undefined,
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "production", id] });
      client.invalidateQueries({ queryKey: ["finance"] });
      setExpenseOpen(false);
      setExpenseError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setExpenseError(err.message);
      } else if (err instanceof Error) {
        setExpenseError(err.message);
      }
    },
  });

  const addEarningMutation = useMutation({
    mutationFn: () => {
      const amt = Number(earningForm.amount);
      return financeApi.addEarning({
        idempotencyKey: earningRequestKey,
        employeeId: earningForm.employeeId,
        productionId: id!,
        amount: amt,
        date: earningForm.date,
        description: earningForm.description.trim(),
      });
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance", "production", id] });
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

  const equipmentCatalog = useQuery({
    queryKey: ["headquarters", "equipment", "catalog"],
    queryFn: () => headquartersApi.equipment(0, ""),
    enabled: equipmentModalOpen,
  });

  const selectedEquipment = equipmentCatalog.data?.items?.find(
    (item) => item.id === equipmentForm.equipmentId,
  );

  const taskToggleMutation = useMutation({
    mutationFn: ({ taskId, done }: { taskId: string; done: boolean }) =>
      api(`/tasks/${taskId}/updates`, {
        method: "POST",
        ...json({
          progressPercent: done ? 100 : 0,
          status: done ? "DONE" : "TODO",
          note: done ? "Completed from checklist" : "Reopened from checklist",
        }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["tasks", "production", id] });
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const addTaskMutation = useMutation({
    mutationFn: () =>
      api<WorkTask>("/tasks", {
        method: "POST",
        ...json({
          productionId: id,
          title: taskForm.title.trim(),
          assignedEmployeeId: taskForm.assignedEmployeeId || undefined,
          priority: taskForm.priority,
          status: "TODO",
          progressPercent: 0,
        }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["tasks", "production", id] });
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setTaskModalOpen(false);
      setTaskForm({ title: "", priority: "NORMAL", assignedEmployeeId: "" });
    },
  });

  const addEquipmentMutation = useMutation({
    mutationFn: () => {
      setEquipmentError("");
      return api<Production>(`/productions/${id}/equipment`, {
        method: "POST",
        ...json({
          equipmentId: equipmentForm.equipmentId,
          quantity: Math.max(1, Number(equipmentForm.quantity) || 1),
        }),
      });
    },
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["headquarters"] });
      setEquipmentModalOpen(false);
      setEquipmentForm({ equipmentId: "", quantity: "1" });
      setEquipmentError("");
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setEquipmentError(err.message);
      } else if (err instanceof Error) {
        setEquipmentError(err.message);
      } else {
        setEquipmentError("Failed to assign equipment. Please try again.");
      }
    },
  });

  const removeEquipmentMutation = useMutation({
    mutationFn: (equipmentId: string) =>
      api<Production>(`/productions/${id}/equipment/${equipmentId}`, {
        method: "DELETE",
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["headquarters"] });
    },
  });

  const removeMemberMutation = useMutation({
    mutationFn: (empId: string) =>
      api(`/productions/${id}/members/${empId}`, {
        method: "DELETE",
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["production", id] });
      client.invalidateQueries({ queryKey: ["calendar"] });
    },
  });

  const updateNotesMutation = useMutation({
    mutationFn: () =>
      api<Production>(`/productions/${id}`, {
        method: "PATCH",
        ...json({
          title: p.title,
          clientName: p.clientName,
          description: notesText.trim() || null,
          eventDate: p.eventDate,
          startTime: p.startTime || null,
          endTime: p.endTime || null,
          venueName: p.venueName,
          venueAddress: p.venueAddress,
          priority: p.priority,
          progressPercent: p.progressPercent,
        }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      setNotesModalOpen(false);
    },
  });

  const updateScheduleMutation = useMutation({
    mutationFn: () =>
      api<Production>(`/productions/${id}`, {
        method: "PATCH",
        ...json({
          title: p.title,
          clientName: p.clientName,
          description: p.description,
          eventDate: p.eventDate,
          startTime: scheduleForm.startTime.trim() || null,
          endTime: scheduleForm.endTime.trim() || null,
          venueName: p.venueName,
          venueAddress: p.venueAddress,
          priority: p.priority,
          progressPercent: p.progressPercent,
        }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["production", id], data);
      client.invalidateQueries({ queryKey: ["calendar"] });
      setScheduleModalOpen(false);
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
            {longDate(p.eventDate)} · {p.startTime ? p.startTime.slice(0, 5) : "TBD"}{" "}
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
                  startTime: p.startTime ? p.startTime.slice(0, 5) : "",
                  endTime: p.endTime ? p.endTime.slice(0, 5) : "",
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
          {finance.data && !finance.data.production.contracted && (
            <SAButton
              variant="primary"
              onClick={() => {
                setContractRequestKey(generateKey());
                setContractForm({
                  amount: "",
                  date: p.eventDate || new Date().toISOString().slice(0, 10),
                  description: `Contract for ${p.title}`,
                });
                setContractError("");
                setContractOpen(true);
              }}
            >
              Set Contract
            </SAButton>
          )}
          <SAButton onClick={() => navigate(`/billing?productionId=${p.id}`)}>
            Create Bill
          </SAButton>
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
          <div className="production-overview-sections">
            {/* Top Brief & Status Grid */}
            <SABentoGrid className="production-detail-grid">
              <SABentoCard>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <h3>Operational Brief</h3>
                    <p style={{ margin: "6px 0 12px", color: "var(--text-2)", fontSize: "13.5px" }}>
                      {p.description || "No production notes yet."}
                    </p>
                  </div>
                  <SAButton
                    size="sm"
                    onClick={() => {
                      setEdit({
                        title: p.title,
                        clientName: p.clientName,
                        description: p.description ?? "",
                        eventDate: p.eventDate,
                        startTime: p.startTime ? p.startTime.slice(0, 5) : "",
                        endTime: p.endTime ? p.endTime.slice(0, 5) : "",
                        venueName: p.venueName,
                        venueAddress: p.venueAddress ?? "",
                        priority: p.priority,
                        progressPercent: p.progressPercent,
                      });
                      setEditOpen(true);
                    }}
                  >
                    <Edit3 size={13} /> Edit
                  </SAButton>
                </div>
                <dl>
                  <div>
                    <dt>Client</dt>
                    <dd><strong>{p.clientName}</strong></dd>
                  </div>
                  <div>
                    <dt>Venue</dt>
                    <dd>{p.venueName} {p.venueAddress ? `(${p.venueAddress})` : ""}</dd>
                  </div>
                  <div>
                    <dt>Date</dt>
                    <dd>{longDate(p.eventDate)}</dd>
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

            {/* Schedule Section */}
            <SABentoCard className="production-section-card">
              <div className="production-section-header">
                <h3>
                  <Clock size={16} /> Schedule
                </h3>
                <div className="production-section-actions">
                  <SAButton
                    size="sm"
                    onClick={() => {
                      setScheduleForm({
                        startTime: p.startTime ? p.startTime.slice(0, 5) : "",
                        endTime: p.endTime ? p.endTime.slice(0, 5) : "",
                      });
                      setScheduleModalOpen(true);
                    }}
                  >
                    {p.startTime && p.endTime ? "Edit schedule" : "+ Add schedule"}
                  </SAButton>
                </div>
              </div>
              {p.startTime && p.endTime ? (
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <strong style={{ fontSize: "16px" }}>
                      {p.startTime.slice(0, 5)} – {p.endTime.slice(0, 5)}
                    </strong>
                    <p className="muted" style={{ margin: "4px 0 0", fontSize: "12px" }}>
                      {longDate(p.eventDate)} · Synchronized with calendar conflict engine
                    </p>
                  </div>
                  <StatusBadge tone="success">Calendar Synced</StatusBadge>
                </div>
              ) : (
                <EmptyState
                  title="No schedule set"
                  description="Event times have not been locked in yet. Add schedule times to activate calendar conflict detection."
                  action={
                    <SAButton
                      size="sm"
                      onClick={() => {
                        setScheduleForm({ startTime: "", endTime: "" });
                        setScheduleModalOpen(true);
                      }}
                    >
                      + Add schedule
                    </SAButton>
                  }
                />
              )}
            </SABentoCard>

            {/* Tasks Checklist Section */}
            <SABentoCard className="production-section-card">
              <div className="production-section-header">
                <h3>
                  <CheckSquare size={16} />
                  Operational Checklist ({tasks.data?.filter((t) => t.status === "DONE").length ?? 0}/{tasks.data?.length ?? 0})
                </h3>
                <div className="production-section-actions">
                  <SAButton size="sm" onClick={() => setTaskModalOpen(true)}>
                    <Plus size={14} /> Add task
                  </SAButton>
                </div>
              </div>
              {(!tasks.data || tasks.data.length === 0) ? (
                <EmptyState
                  title="No tasks on the checklist"
                  description="Add tasks to track venue setup, sound check, load-in and transport."
                  action={
                    <SAButton size="sm" onClick={() => setTaskModalOpen(true)}>
                      + Add task
                    </SAButton>
                  }
                />
              ) : (
                <div className="production-checklist">
                  {tasks.data.map((t) => {
                    const isDone = t.status === "DONE";
                    return (
                      <div key={t.id} className="production-checklist-item">
                        <div className="production-checklist-left">
                          <input
                            type="checkbox"
                            className="production-checkbox"
                            aria-label={`Toggle task ${t.title}`}
                            checked={isDone}
                            onChange={() =>
                              taskToggleMutation.mutate({
                                taskId: t.id,
                                done: !isDone,
                              })
                            }
                          />
                          <span className={`production-checklist-label ${isDone ? "completed" : ""}`}>
                            {t.title}
                          </span>
                        </div>
                        <div className="production-checklist-meta">
                          {t.overdue && !isDone && (
                            <StatusBadge tone="danger">Overdue</StatusBadge>
                          )}
                          {t.assigneeName && (
                            <span className="muted" style={{ fontSize: "11px" }}>
                              {t.assigneeName}
                            </span>
                          )}
                          <StatusBadge
                            tone={
                              t.priority === "URGENT" || t.priority === "HIGH"
                                ? "warning"
                                : "neutral"
                            }
                          >
                            {t.priority}
                          </StatusBadge>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </SABentoCard>

            {/* Crew Section */}
            <SABentoCard className="production-section-card">
              <div className="production-section-header">
                <h3>
                  <Users size={16} />
                  Crew ({p.members.length})
                </h3>
                <div className="production-section-actions">
                  <SAButton size="sm" onClick={() => setCrewOpen(true)}>
                    <Plus size={14} /> Assign crew
                  </SAButton>
                </div>
              </div>
              {p.members.length === 0 ? (
                <EmptyState
                  title="No crew assigned yet"
                  description="Assign technicians, operators and engineers from your People roster."
                  action={
                    <SAButton size="sm" onClick={() => setCrewOpen(true)}>
                      + Assign crew
                    </SAButton>
                  }
                />
              ) : (
                <div className="production-crew-chips">
                  {p.members.map((m) => (
                    <div key={m.id} className="production-crew-chip">
                      <div>
                        <strong>{m.employeeName}</strong>
                        <span style={{ fontSize: "11px", color: "var(--text-3)", marginLeft: "6px" }}>
                          ({m.productionRole})
                        </span>
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
                        {m.conflictOverridden ? "Conflict Overridden" : m.assignmentStatus}
                      </StatusBadge>
                      {m.assignmentStatus === "PENDING" && (
                        <div style={{ display: "flex", gap: "4px" }}>
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
                      <button
                        type="button"
                        aria-label={`Remove crew ${m.employeeName}`}
                        style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", display: "flex", alignItems: "center" }}
                        onClick={() => removeMemberMutation.mutate(m.employeeId)}
                      >
                        <X size={13} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </SABentoCard>

            {/* Equipment Section */}
            <SABentoCard className="production-section-card">
              <div className="production-section-header">
                <h3>
                  <Boxes size={16} />
                  Assigned Equipment ({(p.equipment ?? []).length})
                </h3>
                <div className="production-section-actions">
                  <SAButton
                    size="sm"
                    onClick={() => {
                      setEquipmentError("");
                      setEquipmentForm({ equipmentId: "", quantity: "1" });
                      setEquipmentModalOpen(true);
                    }}
                  >
                    <Plus size={14} /> Add equipment
                  </SAButton>
                  <SAButton
                    size="sm"
                    onClick={() => navigate(`/headquarters?production=${p.id}`)}
                  >
                    Open Headquarters
                  </SAButton>
                </div>
              </div>
              {(!p.equipment || p.equipment.length === 0) ? (
                <EmptyState
                  title="No equipment assigned"
                  description="Reserve sound, lighting, video and stage equipment from Headquarters."
                  action={
                    <SAButton
                      size="sm"
                      onClick={() => {
                        setEquipmentError("");
                        setEquipmentForm({ equipmentId: "", quantity: "1" });
                        setEquipmentModalOpen(true);
                      }}
                    >
                      + Add equipment
                    </SAButton>
                  }
                />
              ) : (
                <div className="production-equipment-grid">
                  {p.equipment.map((eq) => (
                    <div key={eq.id} className="production-equipment-card">
                      <div>
                        <strong>{eq.equipmentName}</strong>
                        <div style={{ fontSize: "11px", color: "var(--text-3)", marginTop: "2px" }}>
                          {eq.internalCode ? `${eq.internalCode} · ` : ""}{eq.quantity} {eq.unitSymbol || "units"}
                        </div>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <StatusBadge tone="neutral">{eq.status}</StatusBadge>
                        <button
                          type="button"
                          aria-label={`Remove equipment ${eq.equipmentName}`}
                          style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", display: "flex", alignItems: "center" }}
                          onClick={() => removeEquipmentMutation.mutate(eq.equipmentId)}
                        >
                          <X size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </SABentoCard>

            {/* Notes Section */}
            <SABentoCard className="production-section-card">
              <div className="production-section-header">
                <h3>Production Notes</h3>
                <div className="production-section-actions">
                  <SAButton
                    size="sm"
                    onClick={() => {
                      setNotesText(p.description || "");
                      setNotesModalOpen(true);
                    }}
                  >
                    {p.description ? "Edit notes" : "+ Add notes"}
                  </SAButton>
                </div>
              </div>
              {p.description ? (
                <p style={{ margin: 0, whiteSpace: "pre-wrap", lineHeight: 1.5, fontSize: "13.5px", color: "var(--text-2)" }}>
                  {p.description}
                </p>
              ) : (
                <EmptyState
                  title="No notes added yet"
                  description="Add production briefing notes, client contacts, and on-site instructions."
                  action={
                    <SAButton
                      size="sm"
                      onClick={() => {
                        setNotesText("");
                        setNotesModalOpen(true);
                      }}
                    >
                      + Add notes
                    </SAButton>
                  }
                />
              )}
            </SABentoCard>

            {/* Financial Summary on Overview */}
            {finance.data && (
              <SABentoCard className="production-section-card">
                <div className="production-section-header">
                  <h3>Financial Summary</h3>
                  <div className="production-section-actions">
                    <SAButton size="sm" onClick={() => setTab("finance")}>
                      View Full Ledger
                    </SAButton>
                  </div>
                </div>
                <SABentoGrid className="production-detail-grid">
                  <SABentoCard>
                    <span className="muted" style={{ fontSize: "11px" }}>Contracted Revenue</span>
                    <strong className="metric">{financeAmount(finance.data.production.contracted || 0)}</strong>
                  </SABentoCard>
                  <SABentoCard>
                    <span className="muted" style={{ fontSize: "11px" }}>Received Advance</span>
                    <strong className="metric" style={{ color: "var(--success, #22c55e)" }}>
                      {financeAmount(finance.data.received || 0)}
                    </strong>
                  </SABentoCard>
                  <SABentoCard>
                    <span className="muted" style={{ fontSize: "11px" }}>Outstanding Balance</span>
                    <strong className="metric" style={{ color: (finance.data.outstanding || 0) > 0 ? "var(--warning, #f59e0b)" : "var(--text-1)" }}>
                      {financeAmount(finance.data.outstanding || 0)}
                    </strong>
                  </SABentoCard>
                  <SABentoCard>
                    <span className="muted" style={{ fontSize: "11px" }}>Realized Margin</span>
                    <strong className="metric">
                      {financeAmount(finance.data.realizedMargin || 0)}
                    </strong>
                  </SABentoCard>
                </SABentoGrid>
              </SABentoCard>
            )}
          </div>
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
          {finance.isPending ? (
            <SkeletonCard />
          ) : finance.isError || !finance.data ? (
            <EmptyState
              title="Finance unavailable"
              description="This production's financial summary could not be loaded."
            />
          ) : (
            (() => {
              const contracted = finance.data.production.contracted || 0;
              const received = finance.data.received || 0;
              const outstanding = finance.data.outstanding || 0;
              const transactions = finance.data.transactions || [];
              const directExpenses = transactions
                .filter((t) => t.type === "PRODUCTION_EXPENSE")
                .reduce((sum, t) => sum + (t.amount || 0), 0);
              const directExpensesCount = transactions.filter(
                (t) => t.type === "PRODUCTION_EXPENSE",
              ).length;
              const crewLabor = transactions
                .filter((t) => t.type === "EMPLOYEE_EARNING")
                .reduce((sum, t) => sum + (t.amount || 0), 0);
              const crewLaborCount = transactions.filter(
                (t) => t.type === "EMPLOYEE_EARNING",
              ).length;
              const realizedMargin = finance.data.realizedMargin || 0;
              const contractedMargin = finance.data.contractedMargin || 0;

              return (
                <>
                  <div className="production-finance-dock">
                    <SAButton
                      variant={contracted > 0 ? "secondary" : "primary"}
                      disabled={contracted > 0}
                      onClick={() => {
                        setContractRequestKey(generateKey());
                        setContractForm({
                          amount: "",
                          date:
                            p.eventDate ||
                            new Date().toISOString().slice(0, 10),
                          description: `Contract for ${p.title}`,
                        });
                        setContractError("");
                        setContractOpen(true);
                      }}
                    >
                      {contracted > 0
                        ? `Contract Set (${financeAmount(contracted)})`
                        : "Set Contract"}
                    </SAButton>
                    <SAButton
                      disabled={contracted <= 0 || outstanding <= 0}
                      onClick={() => {
                        setReceiptRequestKey(generateKey());
                        setReceiptForm({
                          amount: "",
                          receiverAccount: "",
                          date: new Date().toISOString().slice(0, 10),
                          description: `Client receipt for ${p.title}`,
                          counterpartyId: "",
                          legacyType: "ADD",
                        });
                        setReceiptError("");
                        setReceiptOpen(true);
                      }}
                    >
                      Record Receipt
                    </SAButton>
                    <SAButton
                      onClick={() => {
                        setExpenseRequestKey(generateKey());
                        setExpenseForm({
                          amount: "",
                          payerAccount: "",
                          categoryCode: "TRANSPORT",
                          date: new Date().toISOString().slice(0, 10),
                          description: `Expense for ${p.title}`,
                          counterpartyId: "",
                          employeeId: "",
                        });
                        setExpenseError("");
                        setExpenseOpen(true);
                      }}
                    >
                      Log Expense
                    </SAButton>
                    <SAButton
                      onClick={() => {
                        setEarningRequestKey(generateKey());
                        setEarningForm({
                          employeeId: p.members[0]?.employeeId || "",
                          amount: "",
                          date:
                            p.eventDate ||
                            new Date().toISOString().slice(0, 10),
                          description: `Crew shift for ${p.title}`,
                        });
                        setEarningError("");
                        setEarningOpen(true);
                      }}
                    >
                      Add Crew Earning
                    </SAButton>
                    <SAButton
                      onClick={() => navigate(`/billing?productionId=${p.id}`)}
                    >
                      Create Bill
                    </SAButton>
                    <SAButton
                      variant="ghost"
                      onClick={() => navigate(`/finance?production=${p.id}`)}
                    >
                      Open Finance
                    </SAButton>
                  </div>

                  <SABentoGrid className="finance-metrics">
                    <SABentoCard>
                      <span className="eyebrow">Contracted Revenue</span>
                      <strong className="metric">
                        {financeAmount(contracted)}
                      </strong>
                      <span className="muted">
                        {contracted > 0
                          ? "Commercial contract active"
                          : "No contract set yet"}
                      </span>
                    </SABentoCard>
                    <SABentoCard>
                      <span className="eyebrow">Received</span>
                      <strong className="metric">
                        {financeAmount(received)}
                      </strong>
                      <span className="muted">
                        Outstanding {financeAmount(outstanding)}
                      </span>
                    </SABentoCard>
                    <SABentoCard>
                      <span className="eyebrow">Direct Expenses</span>
                      <strong className="metric">
                        {financeAmount(directExpenses)}
                      </strong>
                      <span className="muted">
                        {directExpensesCount} logged operational expenses
                      </span>
                    </SABentoCard>
                    <SABentoCard>
                      <span className="eyebrow">Crew Labor Costs</span>
                      <strong className="metric">
                        {financeAmount(crewLabor)}
                      </strong>
                      <span className="muted">
                        {crewLaborCount} crew shift earnings accrued
                      </span>
                    </SABentoCard>
                    <SABentoCard>
                      <span className="eyebrow">Realized Margin</span>
                      <strong className="metric">
                        {financeAmount(realizedMargin)}
                      </strong>
                      <span className="muted">
                        Contracted margin {financeAmount(contractedMargin)}
                      </span>
                    </SABentoCard>
                    <SABentoCard>
                      <span className="eyebrow">Profit Split (65% / 35%)</span>
                      {realizedMargin > 0 ? (
                        <>
                          <strong
                            className="metric"
                            style={{ fontSize: "1.1rem" }}
                          >
                            AZ: {financeAmount(realizedMargin * 0.65)} · AK:{" "}
                            {financeAmount(realizedMargin * 0.35)}
                          </strong>
                          <span className="muted">
                            Distributed on realized margin
                          </span>
                        </>
                      ) : (
                        <>
                          <strong
                            className="metric"
                            style={{ fontSize: "1.1rem" }}
                          >
                            ₹0.00
                          </strong>
                          <span className="muted">No profit realized yet</span>
                        </>
                      )}
                    </SABentoCard>
                  </SABentoGrid>

                  <SABentoCard className="production-timeline-card">
                    <h3>Financial Activity Timeline</h3>
                    <p className="subtitle">
                      Chronological audit record of all canonical double-entry
                      postings for this production.
                    </p>
                    {transactions.length === 0 ? (
                      <EmptyState
                        title="No financial activity recorded"
                        description="Use the action bar above to set the contract, record advances, log operational expenses, or accrue crew labor."
                      />
                    ) : (
                      <div className="production-timeline">
                        {transactions.map((tx) => {
                          const isReceipt = tx.type === "PRODUCTION_RECEIPT";
                          const isCost =
                            tx.type === "PRODUCTION_EXPENSE" ||
                            tx.type === "EMPLOYEE_EARNING";
                          const tone = isReceipt
                            ? "success"
                            : tx.type === "PRODUCTION_EXPENSE"
                              ? "warning"
                              : tx.type === "EMPLOYEE_EARNING"
                                ? "info"
                                : "neutral";
                          const label =
                            tx.type === "PRODUCTION_CONTRACT"
                              ? "Contract"
                              : tx.type === "PRODUCTION_RECEIPT"
                                ? "Receipt"
                                : tx.type === "PRODUCTION_EXPENSE"
                                  ? "Expense"
                                  : tx.type === "EMPLOYEE_EARNING"
                                    ? "Crew Earning"
                                    : tx.type === "INVOICE_ISSUED"
                                      ? "Invoice"
                                      : tx.type.replaceAll("_", " ");
                          return (
                            <div
                              key={tx.id}
                              className="production-timeline-item"
                            >
                              <div className="production-timeline-left">
                                <span className="production-timeline-date">
                                  {longDate(tx.date)}
                                </span>
                                <StatusBadge tone={tone}>{label}</StatusBadge>
                                <div className="production-timeline-content">
                                  <strong>{tx.description}</strong>
                                  <span>
                                    TX #{tx.transactionNo} · {tx.status}
                                  </span>
                                </div>
                              </div>
                              <div className="production-timeline-right">
                                <span
                                  className={`production-timeline-amount ${
                                    isReceipt
                                      ? "positive"
                                      : isCost
                                        ? "negative"
                                        : ""
                                  }`}
                                >
                                  {isReceipt
                                    ? `+${financeAmount(tx.amount)}`
                                    : isCost
                                      ? `-${financeAmount(tx.amount)}`
                                      : financeAmount(tx.amount)}
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </SABentoCard>
                </>
              );
            })()
          )}
        </SATabContent>
        <SATabContent value="schedule">
          <SABentoCard>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <h3>Linked calendar event</h3>
                <p>
                  {longDate(p.eventDate)} ·{" "}
                  {p.startTime && p.endTime
                    ? `${p.startTime.slice(0, 5)}–${p.endTime.slice(0, 5)}`
                    : "No schedule set"}{" "}
                  · {p.venueName}
                </p>
              </div>
              <SAButton
                size="sm"
                onClick={() => {
                  setScheduleForm({
                    startTime: p.startTime ? p.startTime.slice(0, 5) : "",
                    endTime: p.endTime ? p.endTime.slice(0, 5) : "",
                  });
                  setScheduleModalOpen(true);
                }}
              >
                {p.startTime && p.endTime ? "Edit schedule" : "+ Add schedule"}
              </SAButton>
            </div>
          </SABentoCard>
        </SATabContent>
        <SATabContent value="notes">
          <SABentoCard>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <h3>Production notes</h3>
                <p style={{ margin: "6px 0 0", whiteSpace: "pre-wrap", color: "var(--text-2)" }}>
                  {p.description || "No notes have been added."}
                </p>
              </div>
              <SAButton
                size="sm"
                onClick={() => {
                  setNotesText(p.description || "");
                  setNotesModalOpen(true);
                }}
              >
                {p.description ? "Edit notes" : "+ Add notes"}
              </SAButton>
            </div>
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
      <SAModal
        open={contractOpen}
        onOpenChange={setContractOpen}
        title="Set Contract"
        description={`Establish the contracted commercial value for ${p.title} in canonical Finance.`}
      >
        <div className="form-grid">
          <FormField label="Contract Amount (₹)" error={contractError}>
            <input
              aria-label="Contract amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 150000"
              value={contractForm.amount}
              onChange={(e) => {
                setContractError("");
                setContractForm({ ...contractForm, amount: e.target.value });
              }}
            />
          </FormField>
          <FormField label="Effective Date">
            <input
              aria-label="Contract date"
              type="date"
              required
              value={contractForm.date}
              onChange={(e) =>
                setContractForm({ ...contractForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Description / Reference">
            <input
              aria-label="Contract description"
              required
              value={contractForm.description}
              onChange={(e) =>
                setContractForm({
                  ...contractForm,
                  description: e.target.value,
                })
              }
            />
          </FormField>
        </div>
        {contractError && <p className="form-error">{contractError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setContractOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              setContractMutation.isPending ||
              !contractForm.amount ||
              Number(contractForm.amount) <= 0 ||
              !contractForm.date ||
              !contractForm.description.trim()
            }
            onClick={() => {
              if (setContractMutation.isPending) return;
              const amt = Number(contractForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setContractError("Amount must be a positive number.");
                return;
              }
              setContractMutation.mutate();
            }}
          >
            {setContractMutation.isPending ? "Setting…" : "Set Contract"}
          </SAButton>
        </div>
      </SAModal>
      <SAModal
        open={receiptOpen}
        onOpenChange={setReceiptOpen}
        title="Record Client Receipt"
        description={`Record client advance or payment for ${p.title}. Outstanding balance: ${finance.data ? financeAmount(finance.data.outstanding) : "—"}`}
      >
        <div className="form-grid">
          <FormField label="Receipt Amount (₹)" error={receiptError}>
            <input
              aria-label="Receipt amount"
              type="number"
              step="0.01"
              min="0.01"
              max={finance.data?.outstanding || undefined}
              required
              placeholder="e.g. 50000"
              value={receiptForm.amount}
              onChange={(e) => {
                setReceiptError("");
                setReceiptForm({ ...receiptForm, amount: e.target.value });
              }}
            />
          </FormField>
          <FormField label="Received By (Owner Account)">
            <select
              aria-label="Receiver owner account"
              value={receiptForm.receiverAccount}
              onChange={(e) =>
                setReceiptForm({
                  ...receiptForm,
                  receiverAccount: e.target.value,
                })
              }
            >
              <option value="" disabled>
                Select owner account...
              </option>
              <option value="AZ-2">Azeem (AZ-2)</option>
              <option value="AK-2">Akash (AK-2)</option>
            </select>
          </FormField>
          <FormField label="Date">
            <input
              aria-label="Receipt date"
              type="date"
              required
              value={receiptForm.date}
              onChange={(e) =>
                setReceiptForm({ ...receiptForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Payment Method">
            <select
              aria-label="Receipt payment mode"
              value={receiptForm.legacyType}
              onChange={(e) =>
                setReceiptForm({ ...receiptForm, legacyType: e.target.value })
              }
            >
              <option value="ADD">Client Advance / Direct (ADD)</option>
              <option value="BANK">Bank Transfer (NEFT/RTGS/IMPS)</option>
              <option value="UPI">UPI</option>
              <option value="CASH">Cash</option>
            </select>
          </FormField>
          <FormField label="Counterparty / Client">
            <select
              aria-label="Receipt counterparty"
              value={receiptForm.counterpartyId}
              onChange={(e) =>
                setReceiptForm({
                  ...receiptForm,
                  counterpartyId: e.target.value,
                })
              }
            >
              <option value="">Default ({p.clientName})</option>
              {counterparties.data?.items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName} ({c.role})
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Description">
            <input
              aria-label="Receipt description"
              required
              value={receiptForm.description}
              onChange={(e) =>
                setReceiptForm({ ...receiptForm, description: e.target.value })
              }
            />
          </FormField>
        </div>
        {receiptError && <p className="form-error">{receiptError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setReceiptOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              recordReceiptMutation.isPending ||
              !receiptForm.amount ||
              Number(receiptForm.amount) <= 0 ||
              !receiptForm.receiverAccount ||
              !receiptForm.date ||
              !receiptForm.description.trim()
            }
            onClick={() => {
              if (recordReceiptMutation.isPending) return;
              const amt = Number(receiptForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setReceiptError("Amount must be a positive number.");
                return;
              }
              if (finance.data?.outstanding && amt > finance.data.outstanding) {
                setReceiptError(
                  `Amount exceeds outstanding balance of ${financeAmount(finance.data.outstanding)}.`,
                );
                return;
              }
              recordReceiptMutation.mutate();
            }}
          >
            {recordReceiptMutation.isPending ? "Recording…" : "Record Receipt"}
          </SAButton>
        </div>
      </SAModal>
      <SAModal
        open={expenseOpen}
        onOpenChange={setExpenseOpen}
        title="Log Production Expense"
        description={`Record direct operational cost paid for ${p.title}.`}
      >
        <div className="form-grid">
          <FormField label="Amount (₹)" error={expenseError}>
            <input
              aria-label="Expense amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 4500"
              value={expenseForm.amount}
              onChange={(e) => {
                setExpenseError("");
                setExpenseForm({ ...expenseForm, amount: e.target.value });
              }}
            />
          </FormField>
          <FormField label="Paid By (Owner Account)">
            <select
              aria-label="Expense payer account"
              value={expenseForm.payerAccount}
              onChange={(e) =>
                setExpenseForm({ ...expenseForm, payerAccount: e.target.value })
              }
            >
              <option value="" disabled>
                Select owner account...
              </option>
              <option value="AZ-2">Azeem (AZ-2)</option>
              <option value="AK-2">Akash (AK-2)</option>
            </select>
          </FormField>
          <FormField label="Expense Category">
            <select
              aria-label="Expense category"
              value={expenseForm.categoryCode}
              onChange={(e) =>
                setExpenseForm({ ...expenseForm, categoryCode: e.target.value })
              }
            >
              {(
                financeConfig.data?.expenseCategories || [
                  { code: "TRANSPORT", displayName: "Transport" },
                  { code: "FOOD", displayName: "Food" },
                  { code: "RENTAL", displayName: "Rental" },
                  { code: "EQUIPMENT", displayName: "Equipment" },
                  { code: "PETROL", displayName: "Petrol" },
                  { code: "OTHER", displayName: "Other" },
                ]
              ).map((cat) => (
                <option key={cat.code} value={cat.code}>
                  {cat.displayName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Date">
            <input
              aria-label="Expense date"
              type="date"
              required
              value={expenseForm.date}
              onChange={(e) =>
                setExpenseForm({ ...expenseForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Vendor / Counterparty (Optional)">
            <select
              aria-label="Expense vendor"
              value={expenseForm.counterpartyId}
              onChange={(e) =>
                setExpenseForm({
                  ...expenseForm,
                  counterpartyId: e.target.value,
                })
              }
            >
              <option value="">None / Direct</option>
              {counterparties.data?.items.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName} ({c.role})
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Associated Crew (Optional)">
            <select
              aria-label="Expense crew member"
              value={expenseForm.employeeId}
              onChange={(e) =>
                setExpenseForm({ ...expenseForm, employeeId: e.target.value })
              }
            >
              <option value="">None / General</option>
              {employees.data?.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.displayName}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Description / Purpose">
            <input
              aria-label="Expense description"
              required
              placeholder="e.g. Venue sound technician travel"
              value={expenseForm.description}
              onChange={(e) =>
                setExpenseForm({ ...expenseForm, description: e.target.value })
              }
            />
          </FormField>
        </div>
        {expenseError && <p className="form-error">{expenseError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setExpenseOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              logExpenseMutation.isPending ||
              !expenseForm.amount ||
              Number(expenseForm.amount) <= 0 ||
              !expenseForm.payerAccount ||
              !expenseForm.date ||
              !expenseForm.description.trim()
            }
            onClick={() => {
              if (logExpenseMutation.isPending) return;
              const amt = Number(expenseForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setExpenseError("Amount must be a positive number.");
                return;
              }
              logExpenseMutation.mutate();
            }}
          >
            {logExpenseMutation.isPending ? "Logging…" : "Log Expense"}
          </SAButton>
        </div>
      </SAModal>
      <SAModal
        open={earningOpen}
        onOpenChange={setEarningOpen}
        title="Add Crew Labor Earning"
        description={`Record labor cost obligation for a crew member on ${p.title}. Accrues an employee payable obligation without marking it paid.`}
      >
        <div className="form-grid">
          <FormField label="Crew Member / Employee" error={earningError}>
            <select
              aria-label="Crew member"
              required
              value={earningForm.employeeId}
              onChange={(e) => {
                setEarningError("");
                setEarningForm({ ...earningForm, employeeId: e.target.value });
              }}
            >
              <option value="">Choose employee…</option>
              {p.members.length > 0 && (
                <optgroup label="Assigned Crew">
                  {p.members.map((m) => (
                    <option key={m.employeeId} value={m.employeeId}>
                      {m.employeeName} ({m.productionRole})
                    </option>
                  ))}
                </optgroup>
              )}
              <optgroup label="All Employees">
                {employees.data?.map((emp) => (
                  <option key={emp.id} value={emp.id}>
                    {emp.displayName} ({emp.employeeCode})
                  </option>
                ))}
              </optgroup>
            </select>
          </FormField>
          <FormField label="Earning Amount (₹)">
            <input
              aria-label="Earning amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 3500"
              value={earningForm.amount}
              onChange={(e) =>
                setEarningForm({ ...earningForm, amount: e.target.value })
              }
            />
          </FormField>
          <FormField label="Shift Date">
            <input
              aria-label="Earning shift date"
              type="date"
              required
              value={earningForm.date}
              onChange={(e) =>
                setEarningForm({ ...earningForm, date: e.target.value })
              }
            />
          </FormField>
          <FormField label="Shift Description / Reference">
            <input
              aria-label="Earning description"
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
              !earningForm.employeeId ||
              !earningForm.amount ||
              Number(earningForm.amount) <= 0 ||
              !earningForm.date ||
              !earningForm.description.trim()
            }
            onClick={() => {
              if (addEarningMutation.isPending) return;
              if (!earningForm.employeeId) {
                setEarningError("Please choose an employee.");
                return;
              }
              const amt = Number(earningForm.amount);
              if (isNaN(amt) || amt <= 0) {
                setEarningError("Amount must be a positive number.");
                return;
              }
              addEarningMutation.mutate();
            }}
          >
            {addEarningMutation.isPending ? "Adding…" : "Add Crew Earning"}
          </SAButton>
        </div>
      </SAModal>

      {/* Add Task Modal */}
      <SAModal
        open={taskModalOpen}
        onOpenChange={setTaskModalOpen}
        title="Add Operational Task"
        description={`Add an actionable task for ${p.title}.`}
      >
        <div className="form-grid">
          <FormField label="Task Title">
            <input
              aria-label="New task title"
              placeholder="e.g. Confirm generator backup"
              value={taskForm.title}
              onChange={(e) => setTaskForm({ ...taskForm, title: e.target.value })}
            />
          </FormField>
          <FormField label="Priority">
            <select
              aria-label="New task priority"
              value={taskForm.priority}
              onChange={(e) =>
                setTaskForm({ ...taskForm, priority: e.target.value as Priority })
              }
            >
              {["LOW", "NORMAL", "HIGH", "URGENT"].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </FormField>
          <FormField label="Assignee">
            <select
              aria-label="New task assignee"
              value={taskForm.assignedEmployeeId}
              onChange={(e) =>
                setTaskForm({ ...taskForm, assignedEmployeeId: e.target.value })
              }
            >
              <option value="">Unassigned</option>
              {employees.data?.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.displayName}
                </option>
              ))}
            </select>
          </FormField>
        </div>
        <div className="modal-actions">
          <SAButton onClick={() => setTaskModalOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={!taskForm.title.trim() || addTaskMutation.isPending}
            onClick={() => addTaskMutation.mutate()}
          >
            {addTaskMutation.isPending ? "Adding…" : "Add Task"}
          </SAButton>
        </div>
      </SAModal>

      {/* Add Equipment Modal */}
      <SAModal
        open={equipmentModalOpen}
        onOpenChange={(v) => {
          setEquipmentModalOpen(v);
          if (!v) {
            setEquipmentError("");
            setEquipmentForm({ equipmentId: "", quantity: "1" });
          }
        }}
        title="Assign Equipment"
        description={`Reserve inventory gear from Headquarters for ${p.title}.`}
      >
        {equipmentError && (
          <div
            className="field-error"
            role="alert"
            style={{
              padding: "8px 12px",
              marginBottom: "12px",
              background: "rgba(239, 68, 68, 0.1)",
              borderRadius: "8px",
              fontSize: "13px",
            }}
          >
            {equipmentError}
          </div>
        )}
        <div className="form-grid">
          <FormField label="Equipment">
            <select
              aria-label="Assign equipment select"
              value={equipmentForm.equipmentId}
              onChange={(e) => {
                setEquipmentError("");
                setEquipmentForm({ ...equipmentForm, equipmentId: e.target.value });
              }}
            >
              <option value="">Select equipment...</option>
              {equipmentCatalog.data?.items
                ?.filter(
                  (item) => !(p.equipment ?? []).some((e) => e.equipmentId === item.id)
                )
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} {item.internalCode ? `(${item.internalCode})` : ""}{item.available != null ? ` · ${item.available} ${item.symbol ?? "units"} available` : ""}
                  </option>
                ))}
            </select>
          </FormField>
          <FormField
            label="Quantity"
            hint={
              selectedEquipment && selectedEquipment.available != null
                ? `${selectedEquipment.available} ${selectedEquipment.symbol ?? "units"} available in inventory`
                : undefined
            }
          >
            <input
              aria-label="Assign equipment quantity"
              type="number"
              min="1"
              max={selectedEquipment?.available}
              value={equipmentForm.quantity}
              onChange={(e) => {
                setEquipmentError("");
                setEquipmentForm({ ...equipmentForm, quantity: e.target.value });
              }}
            />
          </FormField>
        </div>
        <div className="modal-actions">
          <SAButton
            onClick={() => {
              setEquipmentModalOpen(false);
              setEquipmentError("");
            }}
          >
            Cancel
          </SAButton>
          <SAButton
            variant="primary"
            disabled={
              !equipmentForm.equipmentId ||
              !equipmentForm.quantity ||
              Number(equipmentForm.quantity) <= 0 ||
              addEquipmentMutation.isPending
            }
            onClick={() => {
              if (addEquipmentMutation.isPending) return;
              if (
                selectedEquipment &&
                selectedEquipment.available != null &&
                Number(equipmentForm.quantity) > selectedEquipment.available
              ) {
                setEquipmentError(
                  `Only ${selectedEquipment.available} ${selectedEquipment.symbol ?? "units"} available in inventory.`
                );
                return;
              }
              addEquipmentMutation.mutate();
            }}
          >
            {addEquipmentMutation.isPending ? "Reserving…" : "Assign Equipment"}
          </SAButton>
        </div>
      </SAModal>

      {/* Edit Notes Modal */}
      <SAModal
        open={notesModalOpen}
        onOpenChange={setNotesModalOpen}
        title="Edit Production Notes"
        description="Update operational notes, briefing details, or setup instructions."
      >
        <FormField label="Production Notes">
          <textarea
            aria-label="Edit notes textarea"
            rows={5}
            value={notesText}
            onChange={(e) => setNotesText(e.target.value)}
          />
        </FormField>
        <div className="modal-actions">
          <SAButton onClick={() => setNotesModalOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={updateNotesMutation.isPending}
            onClick={() => updateNotesMutation.mutate()}
          >
            {updateNotesMutation.isPending ? "Saving…" : "Save Notes"}
          </SAButton>
        </div>
      </SAModal>

      {/* Edit Schedule Modal */}
      <SAModal
        open={scheduleModalOpen}
        onOpenChange={setScheduleModalOpen}
        title="Edit Production Schedule"
        description="Set start and end times for this production to activate calendar conflict detection."
      >
        <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <FormField label="Start Time">
            <input
              aria-label="Schedule start time input"
              type="time"
              value={scheduleForm.startTime}
              onChange={(e) =>
                setScheduleForm({ ...scheduleForm, startTime: e.target.value })
              }
            />
          </FormField>
          <FormField label="End Time">
            <input
              aria-label="Schedule end time input"
              type="time"
              value={scheduleForm.endTime}
              onChange={(e) =>
                setScheduleForm({ ...scheduleForm, endTime: e.target.value })
              }
            />
          </FormField>
        </div>
        <div className="modal-actions">
          <SAButton onClick={() => setScheduleModalOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={updateScheduleMutation.isPending}
            onClick={() => updateScheduleMutation.mutate()}
          >
            {updateScheduleMutation.isPending ? "Saving…" : "Save Schedule"}
          </SAButton>
        </div>
      </SAModal>
    </>
  );
}
const longDate = (v: string) => {
  if (!v) return "";
  try {
    return new Intl.DateTimeFormat("en-IN", {
      day: "numeric",
      month: "long",
      year: "numeric",
    }).format(new Date(`${v.slice(0, 10)}T00:00:00`));
  } catch {
    return v;
  }
};
const generateKey = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (c) =>
        (
          +c ^
          (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (+c / 4)))
        ).toString(16),
      );
