import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { api, json } from "../../lib/api";
import type {
  Employee,
  Priority,
  Production,
  TaskStatus,
  WorkTask,
} from "../../types/domain";
import {
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SADrawer,
  SAModal,
  SAProgress,
  SASegmentedControl,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
type View = "TODAY" | "TEAM" | "UPCOMING" | "OVERDUE" | "COMPLETED";
const initial = {
  title: "",
  description: "",
  assignedEmployeeId: "",
  productionId: "",
  status: "TODO" as TaskStatus,
  priority: "NORMAL" as Priority,
  startDate: new Date().toISOString().slice(0, 10),
  dueAt: "",
  progressPercent: 0,
};
export function WorkPage() {
  const location = useLocation(),
    client = useQueryClient();
  const [view, setView] = useState<View>("TODAY"),
    [open, setOpen] = useState(false),
    [editing, setEditing] = useState<WorkTask | null>(null),
    [selected, setSelected] = useState<WorkTask | null>(null),
    [form, setForm] = useState(initial),
    [progress, setProgress] = useState(0),
    [note, setNote] = useState("");
  useEffect(() => {
    if (new URLSearchParams(location.search).get("create") === "task")
      setOpen(true);
  }, [location.search]);
  const tasks = useQuery({
    queryKey: ["tasks"],
    queryFn: () => api<WorkTask[]>("/tasks"),
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const productions = useQuery({
    queryKey: ["productions"],
    queryFn: () => api<Production[]>("/productions"),
  });
  const invalidate = () => {
    client.invalidateQueries({ queryKey: ["tasks"] });
    client.invalidateQueries({ queryKey: ["dashboard"] });
    client.invalidateQueries({ queryKey: ["productions"] });
  };
  const save = useMutation({
    mutationFn: () =>
      api<WorkTask>(editing ? `/tasks/${editing.id}` : "/tasks", {
        method: editing ? "PATCH" : "POST",
        ...json({
          ...form,
          assignedEmployeeId: form.assignedEmployeeId || null,
          productionId: form.productionId || null,
          dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null,
        }),
      }),
    onSuccess: (data) => {
      invalidate();
      setOpen(false);
      if (editing) setSelected(data);
      setEditing(null);
      setForm(initial);
    },
  });
  const update = useMutation({
    mutationFn: () =>
      api<WorkTask>(`/tasks/${selected?.id}/updates`, {
        method: "POST",
        ...json({
          progressPercent: progress,
          note,
          status: progress === 100 ? "DONE" : undefined,
        }),
      }),
    onSuccess: (data) => {
      invalidate();
      setSelected(data);
    },
  });
  const filtered = (tasks.data ?? []).filter((t) =>
    view === "COMPLETED"
      ? t.status === "DONE"
      : view === "OVERDUE"
        ? t.overdue
        : view === "UPCOMING"
          ? !!t.dueAt && !t.overdue && t.status !== "DONE"
          : view === "TODAY"
            ? !!t.dueAt &&
              new Date(t.dueAt).toDateString() === new Date().toDateString() &&
              t.status !== "DONE"
            : t.status !== "DONE",
  );
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Work</h1>
          <p>Tasks, blockers and durable progress history.</p>
        </div>
        <SAButton variant="primary" onClick={() => setOpen(true)}>
          <Plus size={16} />
          Task
        </SAButton>
      </div>
      <div className="work-tabs">
        <SASegmentedControl
          value={view}
          onChange={setView}
          label="Work view"
          items={[
            { value: "TODAY", label: "Today" },
            { value: "TEAM", label: "Team" },
            { value: "UPCOMING", label: "Upcoming" },
            { value: "OVERDUE", label: "Overdue" },
            { value: "COMPLETED", label: "Completed" },
          ]}
        />
      </div>
      {tasks.isPending ? (
        <SkeletonCard />
      ) : tasks.isError ? (
        <EmptyState
          title="Work could not be loaded"
          description="Check the API and try again."
        />
      ) : !filtered.length ? (
        <EmptyState
          title={
            view === "TODAY" ? "No tasks due today" : "No work in this view"
          }
          description="Everything scheduled here is currently clear."
          action={
            <SAButton onClick={() => setOpen(true)}>Create task</SAButton>
          }
        />
      ) : (
        <div className="work-list">
          {filtered.map((t) => (
            <SABentoCard
              interactive
              key={t.id}
              className="work-row"
              onClick={() => {
                setSelected(t);
                setProgress(t.progressPercent);
              }}
            >
              <div className="work-title">
                {t.overdue && <AlertTriangle size={15} />}
                <div>
                  <strong>{t.title}</strong>
                  <span>
                    {t.productionTitle ?? "Internal"} ·{" "}
                    {t.assigneeName ?? "Unassigned"}
                  </span>
                </div>
              </div>
              <StatusBadge
                tone={
                  t.status === "BLOCKED" || t.overdue
                    ? "danger"
                    : t.status === "DONE"
                      ? "success"
                      : t.priority === "HIGH"
                        ? "warning"
                        : "neutral"
                }
              >
                {t.overdue ? "OVERDUE" : t.status.replaceAll("_", " ")}
              </StatusBadge>
              <div className="work-progress">
                <b>{t.progressPercent}%</b>
                <SAProgress value={t.progressPercent} />
                <small>
                  {t.dueAt ? new Date(t.dueAt).toLocaleString() : "No deadline"}
                </small>
              </div>
            </SABentoCard>
          ))}
        </div>
      )}
      <SAModal
        open={open}
        onOpenChange={(value) => {
          setOpen(value);
          if (!value) setEditing(null);
        }}
        title={editing ? "Edit task" : "Create task"}
        description="Tasks can stand alone or contribute to production delivery."
      >
        <div className="form-grid">
          <FormField label="Title">
            <input
              aria-label="Task title"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </FormField>
          <FormField label="Assignee">
            <select
              aria-label="Task assignee"
              value={form.assignedEmployeeId}
              onChange={(e) =>
                setForm({ ...form, assignedEmployeeId: e.target.value })
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
          <FormField label="Production">
            <select
              aria-label="Task production"
              value={form.productionId}
              onChange={(e) =>
                setForm({ ...form, productionId: e.target.value })
              }
            >
              <option value="">Internal task</option>
              {productions.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Priority">
            <select
              aria-label="Task priority"
              value={form.priority}
              onChange={(e) =>
                setForm({ ...form, priority: e.target.value as Priority })
              }
            >
              {["LOW", "NORMAL", "HIGH", "URGENT"].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </FormField>
          <FormField label="Due">
            <input
              aria-label="Task due"
              type="datetime-local"
              value={form.dueAt}
              onChange={(e) => setForm({ ...form, dueAt: e.target.value })}
            />
          </FormField>
          <FormField label="Description">
            <input
              aria-label="Task description"
              value={form.description}
              onChange={(e) =>
                setForm({ ...form, description: e.target.value })
              }
            />
          </FormField>
        </div>
        {save.error && <p className="form-error">{save.error.message}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={!form.title || save.isPending}
            onClick={() => save.mutate()}
          >
            {editing ? "Save task" : "Create task"}
          </SAButton>
        </div>
      </SAModal>
      <SADrawer
        open={!!selected}
        onOpenChange={(v) => !v && setSelected(null)}
        title={selected?.title ?? "Task"}
        description={`${selected?.assigneeName ?? "Unassigned"} · ${selected?.productionTitle ?? "Internal"}`}
      >
        <div className="task-drawer">
          <StatusBadge
            tone={selected?.status === "BLOCKED" ? "danger" : "neutral"}
          >
            {selected?.status.replaceAll("_", " ")}
          </StatusBadge>
          <FormField label={`Progress · ${progress}%`}>
            <input
              aria-label="Task progress"
              type="range"
              min="0"
              max="100"
              value={progress}
              onChange={(e) => setProgress(Number(e.target.value))}
            />
          </FormField>
          <FormField label="Update note">
            <textarea
              aria-label="Progress note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </FormField>
          <SAButton
            variant="primary"
            disabled={update.isPending}
            onClick={() => update.mutate()}
          >
            Save progress
          </SAButton>
          <SAButton
            onClick={() => {
              if (!selected) return;
              setEditing(selected);
              setForm({
                title: selected.title,
                description: selected.description ?? "",
                assignedEmployeeId: selected.assignedEmployeeId ?? "",
                productionId: selected.productionId ?? "",
                status: selected.status,
                priority: selected.priority,
                startDate: selected.startDate ?? initial.startDate,
                dueAt: selected.dueAt
                  ? new Date(selected.dueAt).toISOString().slice(0, 16)
                  : "",
                progressPercent: selected.progressPercent,
              });
              setOpen(true);
            }}
          >
            Edit task
          </SAButton>
          <h3>Progress history</h3>
          <div className="activity-list">
            {selected?.updates.map((u) => (
              <div key={u.id}>
                <strong>{u.progressPercent}%</strong>
                <span>
                  {u.note || "Progress updated"} ·{" "}
                  {new Date(u.createdAt).toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      </SADrawer>
    </>
  );
}
