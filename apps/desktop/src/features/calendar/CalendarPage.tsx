import { viewMonthAgenda, viewMonthGrid, viewWeek } from "@schedule-x/calendar";
import { ScheduleXCalendar, useCalendarApp } from "@schedule-x/react";
import "@schedule-x/theme-default/dist/index.css";
import "temporal-polyfill/global";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarPlus, MapPin, Users } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api, json } from "../../lib/api";
import type {
  CalendarEvent,
  CalendarEventType,
  Employee,
} from "../../types/domain";
import {
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SADrawer,
  SAModal,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
const initial = {
  type: "INTERNAL" as CalendarEventType,
  title: "",
  description: "",
  startsAt: "",
  endsAt: "",
  locationName: "",
  locationAddress: "",
  attendeeIds: [] as string[],
  overrideConflicts: false,
  overrideReason: "",
};
export function CalendarPage() {
  const location = useLocation(),
    client = useQueryClient(),
    navigate = useNavigate();
  const [open, setOpen] = useState(false),
    [form, setForm] = useState(initial),
    [selected, setSelected] = useState<CalendarEvent | null>(null);
  useEffect(() => {
    if (new URLSearchParams(location.search).get("create") === "event")
      setOpen(true);
  }, [location.search]);
  const events = useQuery({
    queryKey: ["calendar"],
    queryFn: () => api<CalendarEvent[]>("/calendar-events"),
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
  });
  const create = useMutation({
    mutationFn: () =>
      api<CalendarEvent>("/calendar-events", {
        method: "POST",
        ...json({
          ...form,
          startsAt: new Date(form.startsAt).toISOString(),
          endsAt: new Date(form.endsAt).toISOString(),
        }),
      }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["calendar"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setOpen(false);
      setForm(initial);
    },
  });
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Calendar</h1>
          <p>
            One schedule for productions, meetings, deadlines and internal work.
          </p>
        </div>
        <SAButton variant="primary" onClick={() => setOpen(true)}>
          <CalendarPlus size={16} />
          Event
        </SAButton>
      </div>
      {events.isPending ? (
        <SkeletonCard />
      ) : events.isError ? (
        <EmptyState
          title="Calendar could not be loaded"
          description="Check the API and try again."
        />
      ) : (
        <div className="calendar-layout">
          <SABentoCard className="calendar-card">
            <CalendarCanvas
              events={events.data ?? []}
              onSelect={(id) =>
                setSelected(events.data?.find((e) => e.id === id) ?? null)
              }
              onCreate={(date) => {
                setForm({
                  ...initial,
                  startsAt: `${date}T10:00`,
                  endsAt: `${date}T11:00`,
                });
                setOpen(true);
              }}
            />
          </SABentoCard>
          <aside className="agenda-stack">
            <h2>Upcoming agenda</h2>
            {(events.data ?? [])
              .filter((e) => new Date(e.endsAt) >= new Date())
              .slice(0, 6)
              .map((e) => (
                <button key={e.id} onClick={() => setSelected(e)}>
                  <time>
                    {new Date(e.startsAt).toLocaleDateString("en-IN", {
                      day: "2-digit",
                      month: "short",
                    })}
                  </time>
                  <span>
                    <strong>{e.title}</strong>
                    <small>
                      {new Date(e.startsAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}{" "}
                      · {e.type}
                    </small>
                  </span>
                </button>
              ))}
          </aside>
        </div>
      )}
      <SAModal
        open={open}
        onOpenChange={setOpen}
        title="Schedule event"
        description="Attendees are checked against the unified schedule."
      >
        <div className="form-grid">
          <FormField label="Type">
            <select
              aria-label="Event type"
              value={form.type}
              onChange={(e) =>
                setForm({ ...form, type: e.target.value as CalendarEventType })
              }
            >
              {["SHOOT", "INTERNAL", "REMINDER", "DEADLINE"].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </FormField>
          <FormField label="Title">
            <input
              aria-label="Event title"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </FormField>
          <FormField label="Starts">
            <input
              aria-label="Event starts"
              type="datetime-local"
              value={form.startsAt}
              onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
            />
          </FormField>
          <FormField label="Ends">
            <input
              aria-label="Event ends"
              type="datetime-local"
              value={form.endsAt}
              onChange={(e) => setForm({ ...form, endsAt: e.target.value })}
            />
          </FormField>
          <FormField label="Location">
            <input
              aria-label="Event location"
              value={form.locationName}
              onChange={(e) =>
                setForm({ ...form, locationName: e.target.value })
              }
            />
          </FormField>
          <FormField label="Attendees">
            <select
              multiple
              aria-label="Event attendees"
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
              {employees.data?.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.displayName}
                </option>
              ))}
            </select>
          </FormField>
        </div>
        {create.error && <p className="form-error">{create.error.message}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              !form.title || !form.startsAt || !form.endsAt || create.isPending
            }
            onClick={() => create.mutate()}
          >
            Schedule
          </SAButton>
        </div>
      </SAModal>
      <SADrawer
        open={!!selected}
        onOpenChange={(v) => !v && setSelected(null)}
        title={selected?.title ?? "Event"}
        description={
          selected
            ? `${new Date(selected.startsAt).toLocaleString()}–${new Date(selected.endsAt).toLocaleTimeString()}`
            : undefined
        }
      >
        <div className="event-detail">
          <StatusBadge
            tone={
              selected?.type === "DEADLINE"
                ? "warning"
                : selected?.type === "PRODUCTION"
                  ? "success"
                  : "info"
            }
          >
            {selected?.type}
          </StatusBadge>
          {selected?.locationName && (
            <p>
              <MapPin size={14} />
              {selected.locationName}
            </p>
          )}
          <p>{selected?.description || "No event notes."}</p>
          <h3>
            <Users size={15} />
            Attendees
          </h3>
          {selected?.attendees.map((a) => (
            <div key={a.employeeId}>
              {a.employeeName}
              <StatusBadge tone="neutral">{a.response}</StatusBadge>
            </div>
          ))}
          {selected?.productionId && (
            <SAButton
              onClick={() => navigate(`/productions/${selected.productionId}`)}
            >
              Open production
            </SAButton>
          )}
          {selected?.meetingId && (
            <SAButton
              onClick={() => navigate(`/meetings/${selected.meetingId}`)}
            >
              Open meeting
            </SAButton>
          )}
          {selected?.taskId && (
            <SAButton onClick={() => navigate("/work")}>
              Open task in Work
            </SAButton>
          )}
        </div>
      </SADrawer>
    </>
  );
}
function CalendarCanvas({
  events,
  onSelect,
  onCreate,
}: {
  events: CalendarEvent[];
  onSelect: (id: string) => void;
  onCreate: (date: string) => void;
}) {
  const calendar = useCalendarApp({
    views: [viewMonthGrid, viewWeek, viewMonthAgenda],
    defaultView: viewMonthGrid.name,
    locale: "en-IN",
    timezone: "Asia/Kolkata",
    isDark: document.documentElement.dataset.theme === "charcoal",
    calendars: {
      production: {
        colorName: "production",
        lightColors: {
          main: "#9b6b44",
          container: "#f2e8de",
          onContainer: "#39291c",
        },
        darkColors: {
          main: "#d0a37b",
          container: "#3b3027",
          onContainer: "#f3e4d5",
        },
      },
      meeting: {
        colorName: "meeting",
        lightColors: {
          main: "#65788b",
          container: "#e4eaf0",
          onContainer: "#26333f",
        },
        darkColors: {
          main: "#9cb0c2",
          container: "#29343e",
          onContainer: "#e5edf4",
        },
      },
      deadline: {
        colorName: "deadline",
        lightColors: {
          main: "#9b7a38",
          container: "#f2ead5",
          onContainer: "#3c3018",
        },
        darkColors: {
          main: "#d0b16c",
          container: "#3a3426",
          onContainer: "#f2e8cb",
        },
      },
      internal: {
        colorName: "internal",
        lightColors: {
          main: "#77776f",
          container: "#ecece8",
          onContainer: "#292925",
        },
        darkColors: {
          main: "#aaa9a1",
          container: "#30302e",
          onContainer: "#eeeeea",
        },
      },
    },
    events: events.map((e) => ({
      id: e.id,
      title: e.title,
      start: Temporal.Instant.from(e.startsAt).toZonedDateTimeISO(
        "Asia/Kolkata",
      ),
      end: Temporal.Instant.from(e.endsAt).toZonedDateTimeISO("Asia/Kolkata"),
      calendarId:
        e.type === "PRODUCTION"
          ? "production"
          : e.type === "MEETING"
            ? "meeting"
            : e.type === "DEADLINE"
              ? "deadline"
              : "internal",
      location: e.locationName ?? undefined,
    })),
    callbacks: {
      onEventClick: (event) => onSelect(String(event.id)),
      onClickDate: (date) => onCreate(date.toString()),
    },
  });
  return <ScheduleXCalendar calendarApp={calendar} />;
}
