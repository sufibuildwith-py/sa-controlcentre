import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowUpRight,
  CalendarDays,
  MapPin,
  Plus,
  Search,
  Users,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  EmptyState,
  FormField,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SAModal,
  SAProgress,
  SASegmentedControl,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api, json } from "../../lib/api";
import type { Priority, Production } from "../../types/domain";
type View = "ACTIVE" | "UPCOMING" | "DELIVERED" | "ALL";
const blank = {
  title: "",
  clientName: "",
  description: "",
  eventDate: "2026-09-26",
  startTime: "16:30",
  endTime: "21:30",
  venueName: "",
  venueAddress: "",
  priority: "NORMAL" as Priority,
  progressPercent: 0,
};
export function ProductionsPage() {
  const navigate = useNavigate(),
    location = useLocation(),
    client = useQueryClient();
  const [view, setView] = useState<View>("ACTIVE"),
    [search, setSearch] = useState(""),
    [open, setOpen] = useState(false),
    [form, setForm] = useState(blank);
  useEffect(() => {
    if (new URLSearchParams(location.search).get("create") === "production")
      setOpen(true);
  }, [location.search]);
  const query = useQuery({
    queryKey: ["productions", search],
    queryFn: () =>
      api<Production[]>(
        `/productions?${new URLSearchParams(search ? { search } : {})}`,
      ),
  });
  const save = useMutation({
    mutationFn: () =>
      api<Production>("/productions", { method: "POST", ...json(form) }),
    onSuccess: (p) => {
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      setOpen(false);
      setForm(blank);
      navigate(`/productions/${p.id}`);
    },
  });
  const rows = (query.data ?? []).filter((p) =>
    view === "ALL" || view === "DELIVERED"
      ? view === "ALL" || p.status === "DELIVERED"
      : view === "UPCOMING"
        ? new Date(p.eventDate) >= new Date() &&
          !["DELIVERED", "CANCELLED"].includes(p.status)
        : !["DELIVERED", "CANCELLED"].includes(p.status),
  );
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Productions</h1>
          <p>Plan shoots, crews and delivery from one operational surface.</p>
        </div>
        <SAButton variant="primary" onClick={() => setOpen(true)}>
          <Plus size={16} />
          Production
        </SAButton>
      </div>
      <div className="people-toolbar">
        <label>
          <Search size={15} />
          <input
            aria-label="Search productions"
            placeholder="Search productions"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <SASegmentedControl
          value={view}
          onChange={setView}
          label="Production view"
          items={[
            { value: "ACTIVE", label: "Active" },
            { value: "UPCOMING", label: "Upcoming" },
            { value: "DELIVERED", label: "Delivered" },
            { value: "ALL", label: "All" },
          ]}
        />
      </div>
      {query.isPending ? (
        <SABentoGrid className="production-grid">
          {[1, 2, 3, 4].map((x) => (
            <SkeletonCard key={x} />
          ))}
        </SABentoGrid>
      ) : query.isError ? (
        <EmptyState
          title="Productions could not be loaded"
          description="Check the API and try again."
          action={
            <SAButton onClick={() => query.refetch()}>Try again</SAButton>
          }
        />
      ) : !rows.length ? (
        <EmptyState
          title="No productions here"
          description="Create your first production to start assigning crew and tracking work."
          action={
            <SAButton variant="primary" onClick={() => setOpen(true)}>
              Create production
            </SAButton>
          }
        />
      ) : (
        <SABentoGrid className="production-grid">
          {rows.map((p, i) => (
            <SABentoCard
              interactive
              key={p.id}
              className={
                i === 0
                  ? "production-card production-card--wide"
                  : "production-card"
              }
              onClick={() => navigate(`/productions/${p.id}`)}
              role="button"
              tabIndex={0}
            >
              <header>
                <StatusBadge
                  tone={
                    p.priority === "URGENT"
                      ? "danger"
                      : p.priority === "HIGH"
                        ? "warning"
                        : "neutral"
                  }
                >
                  {p.status.replaceAll("_", " ")}
                </StatusBadge>
                <ArrowUpRight size={16} />
              </header>
              <h2>{p.title}</h2>
              <p>{p.clientName}</p>
              <div className="production-meta">
                <span>
                  <CalendarDays size={13} />
                  {date(p.eventDate)} · {p.startTime.slice(0, 5)}
                </span>
                <span>
                  <MapPin size={13} />
                  {p.venueName}
                </span>
              </div>
              <div className="production-progress">
                <b>{p.progressPercent}%</b>
                <SAProgress value={p.progressPercent} />
              </div>
              <footer>
                <span>
                  <Users size={13} />
                  {p.members.length} crew
                </span>
                <span>{p.unfinishedTaskCount} unfinished tasks</span>
              </footer>
            </SABentoCard>
          ))}
        </SABentoGrid>
      )}
      <ProductionForm
        open={open}
        onOpenChange={setOpen}
        form={form}
        setForm={setForm}
        onSave={() => save.mutate()}
        pending={save.isPending}
        error={save.error?.message}
      />
    </>
  );
}
function ProductionForm({
  open,
  onOpenChange,
  form,
  setForm,
  onSave,
  pending,
  error,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  form: typeof blank;
  setForm: (v: typeof blank) => void;
  onSave: () => void;
  pending: boolean;
  error?: string;
}) {
  const field = <K extends keyof typeof blank>(
    key: K,
    value: (typeof blank)[K],
  ) => setForm({ ...form, [key]: value });
  return (
    <SAModal
      open={open}
      onOpenChange={onOpenChange}
      title="Create production"
      description="Add the operational details; the calendar slot is linked automatically."
    >
      <div className="form-grid">
        <FormField label="Title">
          <input
            aria-label="Production title"
            value={form.title}
            onChange={(e) => field("title", e.target.value)}
          />
        </FormField>
        <FormField label="Client">
          <input
            aria-label="Client name"
            value={form.clientName}
            onChange={(e) => field("clientName", e.target.value)}
          />
        </FormField>
        <FormField label="Date">
          <input
            aria-label="Event date"
            type="date"
            value={form.eventDate}
            onChange={(e) => field("eventDate", e.target.value)}
          />
        </FormField>
        <FormField label="Priority">
          <select
            aria-label="Production priority"
            value={form.priority}
            onChange={(e) => field("priority", e.target.value as Priority)}
          >
            {["LOW", "NORMAL", "HIGH", "URGENT"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Start">
          <input
            aria-label="Start time"
            type="time"
            value={form.startTime}
            onChange={(e) => field("startTime", e.target.value)}
          />
        </FormField>
        <FormField label="End">
          <input
            aria-label="End time"
            type="time"
            value={form.endTime}
            onChange={(e) => field("endTime", e.target.value)}
          />
        </FormField>
        <FormField label="Venue">
          <input
            aria-label="Venue name"
            value={form.venueName}
            onChange={(e) => field("venueName", e.target.value)}
          />
        </FormField>
        <FormField label="Address">
          <input
            aria-label="Venue address"
            value={form.venueAddress}
            onChange={(e) => field("venueAddress", e.target.value)}
          />
        </FormField>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div className="modal-actions">
        <SAButton onClick={() => onOpenChange(false)}>Cancel</SAButton>
        <SAButton
          variant="primary"
          disabled={
            pending || !form.title || !form.clientName || !form.venueName
          }
          onClick={onSave}
        >
          {pending ? "Creating…" : "Create production"}
        </SAButton>
      </div>
    </SAModal>
  );
}
const date = (v: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(
    new Date(`${v}T00:00:00`),
  );
