import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CalendarClock, MapPin, Plus, Users } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../../lib/api";
import type { Employee, Meeting } from "../../types/domain";
import {
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SAModal,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
const initial = {
  title: "",
  description: "",
  agenda: "",
  startsAt: "",
  endsAt: "",
  location: "",
  attendeeIds: [] as string[],
  overrideConflicts: false,
  overrideReason: "",
};
export function MeetingsPage() {
  const location = useLocation(),
    navigate = useNavigate(),
    client = useQueryClient();
  const [open, setOpen] = useState(false),
    [form, setForm] = useState(initial);
  useEffect(() => {
    if (new URLSearchParams(location.search).get("create") === "meeting")
      setOpen(true);
  }, [location.search]);
  const meetings = useQuery({
    queryKey: ["meetings"],
    queryFn: () => api<Meeting[]>("/meetings"),
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const create = useMutation({
    mutationFn: () =>
      api<Meeting>("/meetings", {
        method: "POST",
        ...json({
          ...form,
          startsAt: new Date(form.startsAt).toISOString(),
          endsAt: new Date(form.endsAt).toISOString(),
        }),
      }),
    onSuccess: (m) => {
      client.invalidateQueries({ queryKey: ["meetings"] });
      client.invalidateQueries({ queryKey: ["calendar"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setOpen(false);
      navigate(`/meetings/${m.id}`);
    },
  });
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Meetings</h1>
          <p>
            Participants, notes and action items tied to the shared schedule.
          </p>
        </div>
        <SAButton variant="primary" onClick={() => setOpen(true)}>
          <Plus size={16} />
          Meeting
        </SAButton>
      </div>
      {meetings.isPending ? (
        <SkeletonCard />
      ) : meetings.isError ? (
        <EmptyState
          title="Meetings could not be loaded"
          description="Check the API and try again."
        />
      ) : (
        <SABentoGrid className="meeting-grid">
          {meetings.data?.map((m) => (
            <SABentoCard
              interactive
              key={m.id}
              className="meeting-card"
              onClick={() => navigate(`/meetings/${m.id}`)}
            >
              <StatusBadge
                tone={
                  m.status === "CANCELLED"
                    ? "danger"
                    : new Date(m.startsAt) < new Date()
                      ? "neutral"
                      : "info"
                }
              >
                {m.status}
              </StatusBadge>
              <h2>{m.title}</h2>
              <p>
                <CalendarClock size={14} />
                {new Date(m.startsAt).toLocaleString()}
              </p>
              <p>
                <MapPin size={14} />
                {m.location ?? "Location to confirm"}
              </p>
              <footer>
                <Users size={14} />
                {m.attendees.length} participants
              </footer>
            </SABentoCard>
          ))}
        </SABentoGrid>
      )}
      <MeetingForm
        open={open}
        setOpen={setOpen}
        form={form}
        setForm={setForm}
        employees={employees.data ?? []}
        save={() => create.mutate()}
        pending={create.isPending}
        error={create.error?.message}
      />
    </>
  );
}
function MeetingForm({
  open,
  setOpen,
  form,
  setForm,
  employees,
  save,
  pending,
  error,
  mode = "create",
}: {
  open: boolean;
  setOpen: (v: boolean) => void;
  form: typeof initial;
  setForm: (v: typeof initial) => void;
  employees: Employee[];
  save: () => void;
  pending: boolean;
  error?: string;
  mode?: "create" | "edit";
}) {
  return (
    <SAModal
      open={open}
      onOpenChange={setOpen}
      title={mode === "edit" ? "Edit meeting" : "Schedule meeting"}
      description="The meeting is linked to Calendar and participant conflicts are checked."
    >
      <div className="form-grid">
        <FormField label="Title">
          <input
            aria-label="Meeting title"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
        </FormField>
        <FormField label="Location">
          <input
            aria-label="Meeting location"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
          />
        </FormField>
        <FormField label="Starts">
          <input
            aria-label="Meeting starts"
            type="datetime-local"
            value={form.startsAt}
            onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
          />
        </FormField>
        <FormField label="Ends">
          <input
            aria-label="Meeting ends"
            type="datetime-local"
            value={form.endsAt}
            onChange={(e) => setForm({ ...form, endsAt: e.target.value })}
          />
        </FormField>
        <FormField label="Participants">
          <select
            aria-label="Meeting participants"
            multiple
            value={form.attendeeIds}
            onChange={(e) =>
              setForm({
                ...form,
                attendeeIds: Array.from(
                  e.target.selectedOptions,
                  (x) => x.value,
                ),
              })
            }
          >
            {employees.map((x) => (
              <option key={x.id} value={x.id}>
                {x.displayName}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Agenda">
          <textarea
            aria-label="Meeting agenda"
            value={form.agenda}
            onChange={(e) => setForm({ ...form, agenda: e.target.value })}
          />
        </FormField>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div className="modal-actions">
        <SAButton onClick={() => setOpen(false)}>Cancel</SAButton>
        <SAButton
          variant="primary"
          disabled={!form.title || !form.startsAt || !form.endsAt || pending}
          onClick={save}
        >
          {mode === "edit" ? "Save meeting" : "Create meeting"}
        </SAButton>
      </div>
    </SAModal>
  );
}
export function MeetingDetailPage() {
  const { id } = useParams(),
    navigate = useNavigate(),
    client = useQueryClient();
  const [note, setNote] = useState(""),
    [editOpen, setEditOpen] = useState(false),
    [edit, setEdit] = useState(initial),
    [actionOpen, setActionOpen] = useState(false),
    [action, setAction] = useState({
      title: "",
      description: "",
      assignedEmployeeId: "",
      dueAt: "",
    });
  const meeting = useQuery({
    queryKey: ["meeting", id],
    queryFn: () => api<Meeting>(`/meetings/${id}`),
    enabled: !!id,
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const addNote = useMutation({
    mutationFn: () =>
      api<Meeting>(`/meetings/${id}/notes`, {
        method: "POST",
        ...json({ content: note }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["meeting", id], data);
      setNote("");
    },
  });
  const addAction = useMutation({
    mutationFn: () =>
      api(`/meetings/${id}/actions`, {
        method: "POST",
        ...json({
          ...action,
          assignedEmployeeId: action.assignedEmployeeId || null,
          dueAt: action.dueAt ? new Date(action.dueAt).toISOString() : null,
        }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["tasks"] });
      setActionOpen(false);
    },
  });
  const updateMeeting = useMutation({
    mutationFn: () =>
      api<Meeting>(`/meetings/${id}`, {
        method: "PATCH",
        ...json({
          ...edit,
          startsAt: new Date(edit.startsAt).toISOString(),
          endsAt: new Date(edit.endsAt).toISOString(),
        }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["meeting", id], data);
      client.invalidateQueries({ queryKey: ["meetings"] });
      client.invalidateQueries({ queryKey: ["calendar"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setEditOpen(false);
    },
  });
  const respond = useMutation({
    mutationFn: ({
      employeeId,
      response,
    }: {
      employeeId: string;
      response: "ACCEPTED" | "DECLINED";
    }) =>
      api<Meeting>(`/meetings/${id}/attendees/${employeeId}`, {
        method: "PATCH",
        ...json({ response }),
      }),
    onSuccess: (data) => {
      client.setQueryData(["meeting", id], data);
      client.invalidateQueries({ queryKey: ["calendar"] });
    },
  });
  if (meeting.isPending) return <SkeletonCard />;
  if (!meeting.data)
    return (
      <EmptyState
        title="Meeting unavailable"
        description="The meeting could not be loaded."
      />
    );
  const m = meeting.data;
  return (
    <>
      <button className="back-link" onClick={() => navigate("/meetings")}>
        <ArrowLeft size={15} />
        Meetings
      </button>
      <SABentoCard className="meeting-hero">
        <div>
          <span className="eyebrow">{m.status}</span>
          <h1>{m.title}</h1>
          <p>
            {new Date(m.startsAt).toLocaleString()} · {m.location}
          </p>
        </div>
        <div className="avatar-stack">
          {m.attendees.map((a) => (
            <span key={a.employeeId} title={a.employeeName}>
              {a.employeeName
                .split(" ")
                .map((x) => x[0])
                .join("")
                .slice(0, 2)}
            </span>
          ))}
        </div>
        <SAButton
          onClick={() => {
            setEdit({
              title: m.title,
              description: m.description ?? "",
              agenda: m.agenda ?? "",
              startsAt: new Date(m.startsAt).toISOString().slice(0, 16),
              endsAt: new Date(m.endsAt).toISOString().slice(0, 16),
              location: m.location ?? "",
              attendeeIds: m.attendees.map((a) => a.employeeId),
              overrideConflicts: false,
              overrideReason: "",
            });
            setEditOpen(true);
          }}
        >
          Edit meeting
        </SAButton>
      </SABentoCard>
      <SABentoGrid className="meeting-detail">
        <SABentoCard>
          <h3>Agenda</h3>
          <p className="pre-line">{m.agenda || "No agenda recorded."}</p>
          <h3>Participants</h3>
          {m.attendees.map((a) => (
            <div className="participant" key={a.employeeId}>
              <span>{a.employeeName}</span>
              <StatusBadge tone="neutral">{a.response}</StatusBadge>
              {a.response === "PENDING" && (
                <div className="compact-actions">
                  <SAButton
                    size="sm"
                    onClick={() =>
                      respond.mutate({
                        employeeId: a.employeeId,
                        response: "ACCEPTED",
                      })
                    }
                  >
                    Accept
                  </SAButton>
                  <SAButton
                    size="sm"
                    onClick={() =>
                      respond.mutate({
                        employeeId: a.employeeId,
                        response: "DECLINED",
                      })
                    }
                  >
                    Decline
                  </SAButton>
                </div>
              )}
            </div>
          ))}
        </SABentoCard>
        <SABentoCard>
          <div className="section-heading">
            <div>
              <h2>Notes</h2>
            </div>
          </div>
          {m.notes.map((n) => (
            <blockquote key={n.id}>
              {n.content}
              <small>{new Date(n.createdAt).toLocaleString()}</small>
            </blockquote>
          ))}
          <FormField label="Add note">
            <textarea
              aria-label="Meeting note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </FormField>
          <SAButton
            disabled={!note || addNote.isPending}
            onClick={() => addNote.mutate()}
          >
            Save note
          </SAButton>
        </SABentoCard>
        <SABentoCard className="meeting-actions">
          <div>
            <h3>Action items</h3>
            <p>Create normal Work tasks from this meeting.</p>
          </div>
          <SAButton variant="primary" onClick={() => setActionOpen(true)}>
            Create action item
          </SAButton>
        </SABentoCard>
      </SABentoGrid>
      <MeetingForm
        open={editOpen}
        setOpen={setEditOpen}
        form={edit}
        setForm={setEdit}
        employees={employees.data ?? []}
        save={() => updateMeeting.mutate()}
        pending={updateMeeting.isPending}
        error={updateMeeting.error?.message}
        mode="edit"
      />
      <SAModal
        open={actionOpen}
        onOpenChange={setActionOpen}
        title="Meeting action item"
        description="This becomes a normal task in Work."
      >
        <FormField label="Task title">
          <input
            aria-label="Action title"
            value={action.title}
            onChange={(e) => setAction({ ...action, title: e.target.value })}
          />
        </FormField>
        <FormField label="Assignee">
          <select
            aria-label="Action assignee"
            value={action.assignedEmployeeId}
            onChange={(e) =>
              setAction({ ...action, assignedEmployeeId: e.target.value })
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
        <FormField label="Due">
          <input
            aria-label="Action due"
            type="datetime-local"
            value={action.dueAt}
            onChange={(e) => setAction({ ...action, dueAt: e.target.value })}
          />
        </FormField>
        <div className="modal-actions">
          <SAButton onClick={() => setActionOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={!action.title || addAction.isPending}
            onClick={() => addAction.mutate()}
          >
            Create task
          </SAButton>
        </div>
      </SAModal>
    </>
  );
}
