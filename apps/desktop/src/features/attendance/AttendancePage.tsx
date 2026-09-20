import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CalendarPlus,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Plus,
} from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SAStatefulButton,
  SAModal,
  StatusBadge,
} from "../../components/ui/sa";
import { api, json } from "../../lib/api";
import type {
  AttendanceDay,
  AttendanceRecord,
  AttendanceStatus,
  Employee,
  LeaveRequest,
} from "../../types/domain";
import { initials } from "../employees/PeoplePage";
const iso = (d: Date) => d.toISOString().slice(0, 10);
const today = iso(new Date());
const statuses: AttendanceStatus[] = [
  "PRESENT",
  "LATE",
  "ABSENT",
  "HALF_DAY",
  "LEAVE",
];
export function AttendancePage() {
  const [date, setDate] = useState(today);
  const [leaveOpen, setLeaveOpen] = useState(false);
  const client = useQueryClient();
  const day = useQuery({
    queryKey: ["attendance", date],
    queryFn: () => api<AttendanceDay>(`/attendance?date=${date}`),
  });
  const leaves = useQuery({
    queryKey: ["leaves"],
    queryFn: () => api<LeaveRequest[]>("/leave-requests"),
  });
  const mark = useMutation({
    mutationFn: ({
      employeeId,
      status,
    }: {
      employeeId: string;
      status: AttendanceStatus;
    }) =>
      api<AttendanceRecord>(`/attendance/${employeeId}/${date}`, {
        method: "PUT",
        ...json({
          status,
          minutesLate: status === "LATE" ? 15 : 0,
          checkInTime:
            status === "PRESENT" || status === "LATE"
              ? new Date().toTimeString().slice(0, 5)
              : null,
        }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["attendance", date] });
      client.invalidateQueries({ queryKey: ["employee"] });
    },
  });
  const remaining = useMutation({
    mutationFn: () =>
      api<{ marked: number }>(`/attendance/${date}/mark-remaining-present`, {
        method: "POST",
      }),
    onSuccess: () =>
      client.invalidateQueries({ queryKey: ["attendance", date] }),
  });
  const move = (amount: number) => {
    const d = new Date(`${date}T12:00:00`);
    d.setDate(d.getDate() + amount);
    setDate(iso(d));
  };
  const summary = day.data?.summary;
  return (
    <>
      <WorkspaceHeader
        title="Attendance"
        subtitle="Record daily state once; Command and employee history reconcile from the same source."
      />
      <div className="attendance-date">
        <SAButton aria-label="Previous date" onClick={() => move(-1)}>
          <ChevronLeft size={16} />
        </SAButton>
        <div>
          <strong>
            {new Intl.DateTimeFormat("en-IN", {
              weekday: "long",
              month: "long",
              day: "numeric",
              year: "numeric",
            }).format(new Date(`${date}T12:00:00`))}
          </strong>
          {date !== today && (
            <button onClick={() => setDate(today)}>Return to today</button>
          )}
        </div>
        <SAButton aria-label="Next date" onClick={() => move(1)}>
          <ChevronRight size={16} />
        </SAButton>
      </div>
      <div className="attendance-summary">
        {(["PRESENT", "LATE", "ABSENT", "LEAVE"] as AttendanceStatus[]).map(
          (status) => (
            <SABentoCard key={status}>
              <span>{status[0] + status.slice(1).toLowerCase()}</span>
              <strong>{summary?.[status] ?? "—"}</strong>
            </SABentoCard>
          ),
        )}
      </div>
      <div className="attendance-actions">
        <SAStatefulButton
          variant="primary"
          pending={remaining.isPending}
          onClick={() => remaining.mutate()}
        >
          <Check size={15} />
          Mark remaining present
        </SAStatefulButton>
        <SAButton onClick={() => setLeaveOpen(true)}>
          <CalendarPlus size={15} />
          Record leave
        </SAButton>
      </div>
      {day.isPending ? (
        <div className="attendance-list">
          <SABentoCard>Loading today’s team…</SABentoCard>
        </div>
      ) : day.isError ? (
        <EmptyState
          title="Attendance unavailable"
          description="The daily record could not be loaded."
          action={<SAButton onClick={() => day.refetch()}>Try again</SAButton>}
        />
      ) : (
        <div className="attendance-list">
          {day.data?.rows.map((row) => (
            <AttendanceRow
              key={row.employee.id}
              employee={row.employee}
              record={row.record}
              pending={
                mark.isPending && mark.variables?.employeeId === row.employee.id
              }
              onMark={(status) =>
                mark.mutate({ employeeId: row.employee.id, status })
              }
            />
          ))}
        </div>
      )}
      <LeavePanel requests={leaves.data ?? []} isLoading={leaves.isPending} />
      <LeaveForm
        open={leaveOpen}
        onOpenChange={setLeaveOpen}
        employees={day.data?.rows.map((r) => r.employee) ?? []}
      />
    </>
  );
}
function AttendanceRow({
  employee,
  record,
  pending,
  onMark,
}: {
  employee: Employee;
  record: AttendanceRecord | null;
  pending: boolean;
  onMark: (s: AttendanceStatus) => void;
}) {
  return (
    <SABentoCard className="attendance-row">
      <div className="attendance-person">
        <div className="employee-avatar">{initials(employee.displayName)}</div>
        <div>
          <strong>{employee.displayName}</strong>
          <span>{employee.roleTitle}</span>
        </div>
      </div>
      <div
        className="attendance-statuses"
        aria-label={`Attendance for ${employee.displayName}`}
      >
        {statuses.map((status) => (
          <button
            key={status}
            className={record?.status === status ? "active" : ""}
            disabled={pending}
            onClick={() => onMark(status)}
          >
            {status === "HALF_DAY"
              ? "Half-day"
              : status[0] + status.slice(1).toLowerCase()}
          </button>
        ))}
      </div>
      <div className="check-time">
        <Clock3 size={14} />
        <span>
          {record?.checkInTime ? record.checkInTime.slice(0, 5) : "—"}
        </span>
        {pending && <small>Saving</small>}
      </div>
    </SABentoCard>
  );
}
function LeavePanel({
  requests,
  isLoading,
}: {
  requests: LeaveRequest[];
  isLoading: boolean;
}) {
  const client = useQueryClient();
  const [notes, setNotes] = useState<Record<string, string>>({});
  const resolve = useMutation({
    mutationFn: ({
      id,
      action,
    }: {
      id: string;
      action: "approve" | "reject";
    }) =>
      api<LeaveRequest>(`/leave-requests/${id}/${action}`, {
        method: "POST",
        ...json({ ownerNote: notes[id] ?? "" }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["leaves"] });
      client.invalidateQueries({ queryKey: ["attendance"] });
      client.invalidateQueries({ queryKey: ["employees"] });
    },
  });
  const pending = requests.filter((r) => r.status === "PENDING");
  return (
    <section className="leave-section">
      <div className="section-heading">
        <div>
          <h2>Leave requests</h2>
          <p>
            Owner decisions update attendance records for the approved range.
          </p>
        </div>
        <StatusBadge tone={pending.length ? "warning" : "success"}>
          {pending.length} pending
        </StatusBadge>
      </div>
      {isLoading ? (
        <SABentoCard>Loading requests…</SABentoCard>
      ) : !pending.length ? (
        <SABentoCard>
          <EmptyState
            title="No pending leave"
            description="New requests recorded on behalf of employees will appear here."
          />
        </SABentoCard>
      ) : (
        <div className="leave-grid">
          {pending.map((r) => (
            <SABentoCard key={r.id} className="leave-card">
              <div>
                <StatusBadge tone="warning">Pending</StatusBadge>
                <span>{r.leaveType}</span>
              </div>
              <h3>{r.employeeName}</h3>
              <p>
                {friendly(r.startDate)} — {friendly(r.endDate)}
              </p>
              <blockquote>{r.reason}</blockquote>
              <input
                value={notes[r.id] ?? ""}
                onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })}
                placeholder="Optional owner note"
                aria-label={`Owner note for ${r.employeeName}`}
              />
              <footer>
                <SAStatefulButton
                  size="sm"
                  pending={
                    resolve.isPending &&
                    resolve.variables?.id === r.id &&
                    resolve.variables?.action === "reject"
                  }
                  onClick={() => resolve.mutate({ id: r.id, action: "reject" })}
                >
                  Reject
                </SAStatefulButton>
                <SAStatefulButton
                  size="sm"
                  variant="primary"
                  pending={
                    resolve.isPending &&
                    resolve.variables?.id === r.id &&
                    resolve.variables?.action === "approve"
                  }
                  onClick={() =>
                    resolve.mutate({ id: r.id, action: "approve" })
                  }
                >
                  Approve
                </SAStatefulButton>
              </footer>
            </SABentoCard>
          ))}
        </div>
      )}
    </section>
  );
}
const leaveSchema = z
  .object({
    employeeId: z.string().uuid(),
    startDate: z.string().min(1),
    endDate: z.string().min(1),
    leaveType: z.string().min(1),
    reason: z.string().min(3),
  })
  .refine((v) => v.endDate >= v.startDate, {
    path: ["endDate"],
    message: "End date must be on or after start date",
  });
type LeaveValues = z.infer<typeof leaveSchema>;
function LeaveForm({
  open,
  onOpenChange,
  employees,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  employees: Employee[];
}) {
  const client = useQueryClient();
  const form = useForm<LeaveValues>({
    resolver: zodResolver(leaveSchema),
    defaultValues: {
      employeeId: "",
      startDate: today,
      endDate: today,
      leaveType: "Personal",
      reason: "",
    },
  });
  const create = useMutation({
    mutationFn: (values: LeaveValues) =>
      api<LeaveRequest>("/leave-requests", { method: "POST", ...json(values) }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["leaves"] });
      onOpenChange(false);
      form.reset();
    },
  });
  return (
    <SAModal
      open={open}
      onOpenChange={onOpenChange}
      title="Record leave request"
      description="Create a request on behalf of an employee for owner review."
    >
      <form
        className="form-grid"
        onSubmit={form.handleSubmit((v) => create.mutate(v))}
      >
        <FormField
          label="Employee"
          error={form.formState.errors.employeeId?.message}
        >
          <select {...form.register("employeeId")}>
            <option value="">Select employee</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.displayName}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Leave type">
          <select {...form.register("leaveType")}>
            <option>Personal</option>
            <option>Sick</option>
            <option>Annual</option>
            <option>Emergency</option>
          </select>
        </FormField>
        <FormField label="Start date">
          <input type="date" {...form.register("startDate")} />
        </FormField>
        <FormField
          label="End date"
          error={form.formState.errors.endDate?.message}
        >
          <input type="date" {...form.register("endDate")} />
        </FormField>
        <FormField label="Reason">
          <textarea {...form.register("reason")} />
        </FormField>
        <div className="modal-actions">
          <SAButton type="button" onClick={() => onOpenChange(false)}>
            Cancel
          </SAButton>
          <SAStatefulButton
            type="submit"
            variant="primary"
            pending={create.isPending}
          >
            <Plus size={15} />
            Create request
          </SAStatefulButton>
        </div>
      </form>
    </SAModal>
  );
}
const friendly = (d: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(
    new Date(`${d}T00:00:00`),
  );
