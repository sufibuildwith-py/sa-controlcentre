import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowUpRight,
  Boxes,
  CalendarDays,
  CheckSquare,
  Clock,
  MapPin,
  Plus,
  Receipt,
  Search,
  Users,
  X,
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
import { ApiError, api, json } from "../../lib/api";
import { headquartersApi } from "../headquarters/headquarters.api";
import { financeApi, financeAmount } from "../finance/finance.api";
import type { Employee, Priority, Production } from "../../types/domain";

type View = "ACTIVE" | "UPCOMING" | "DELIVERED" | "ALL";

interface CrewDraft {
  employeeId: string;
  employeeName: string;
  role: string;
}

interface TaskDraft {
  title: string;
  priority: Priority;
}

interface EquipmentDraft {
  equipmentId: string;
  equipmentName: string;
  quantity: number;
}

interface ContractDraft {
  amount: string;
  date: string;
  description: string;
  idempotencyKey: string;
}

interface AdvanceDraft {
  amount: string;
  date: string;
  receiverAccount: string;
  description: string;
  idempotencyKey: string;
}

const generateKey = () =>
  "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });

const createBlankForm = () => ({
  title: "",
  clientName: "",
  description: "",
  eventDate: new Date().toISOString().slice(0, 10),
  startTime: "",
  endTime: "",
  venueName: "",
  venueAddress: "",
  priority: "NORMAL" as Priority,
  crew: [] as CrewDraft[],
  tasks: [] as TaskDraft[],
  equipment: [] as EquipmentDraft[],
  contract: {
    amount: "",
    date: "",
    description: "",
    idempotencyKey: generateKey(),
  } as ContractDraft,
  advance: {
    amount: "0",
    date: "",
    receiverAccount: "AZ-2",
    description: "",
    idempotencyKey: generateKey(),
  } as AdvanceDraft,
});

const blank = createBlankForm();

export function ProductionsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const client = useQueryClient();

  const [view, setView] = useState<View>("ACTIVE");
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(blank);

  useEffect(() => {
    if (new URLSearchParams(location.search).get("create") === "production") {
      setOpen(true);
    }
  }, [location.search]);

  const query = useQuery({
    queryKey: ["productions", search],
    queryFn: () =>
      api<Production[]>(
        `/productions?${new URLSearchParams(search ? { search } : {})}`,
      ),
  });

  const save = useMutation({
    mutationFn: () => {
      let eventDate = form.eventDate.trim();
      if (/^\d{2}-\d{2}-\d{4}$/.test(eventDate)) {
        const [d, m, y] = eventDate.split("-");
        eventDate = `${y}-${m}-${d}`;
      }
      const contractAmt = Number(form.contract.amount);
      if (isNaN(contractAmt) || contractAmt <= 0) {
        throw new Error("A positive contract amount is required to create a production.");
      }
      const advanceAmt = Number(form.advance.amount || 0);
      if (advanceAmt < 0) {
        throw new Error("Advance amount cannot be negative.");
      }
      if (advanceAmt > contractAmt) {
        throw new Error("Advance amount cannot exceed the contract amount.");
      }
      if (advanceAmt > 0 && !form.advance.receiverAccount) {
        throw new Error("Owner receiver account is required for the advance.");
      }
      const payload = {
        title: form.title.trim(),
        clientName: form.clientName.trim(),
        description: form.description?.trim() ? form.description.trim() : undefined,
        eventDate,
        startTime: form.startTime.trim() || undefined,
        endTime: form.endTime.trim() || undefined,
        venueName: form.venueName.trim(),
        venueAddress: form.venueAddress.trim() || undefined,
        priority: form.priority,
        progressPercent: 0,
        crew: form.crew.map((c) => ({
          employeeId: c.employeeId,
          productionRole: c.role,
          attendanceRequired: true,
        })),
        tasks: form.tasks.map((t) => ({
          title: t.title.trim(),
          priority: t.priority,
          status: "TODO",
        })),
        equipment: form.equipment.map((e) => ({
          equipmentId: e.equipmentId,
          quantity: e.quantity,
        })),
        contract: {
          idempotencyKey: form.contract.idempotencyKey,
          amount: contractAmt,
          effectiveDate: form.contract.date || eventDate,
          description: form.contract.description.trim() || `Contract for ${form.title.trim()}`,
        },
        advance: advanceAmt > 0 ? {
          idempotencyKey: form.advance.idempotencyKey,
          amount: advanceAmt,
          effectiveDate: form.advance.date || eventDate,
          receiverAccount: form.advance.receiverAccount,
          description: form.advance.description.trim() || `Client advance for ${form.title.trim()}`,
        } : undefined,
      };
      return api<Production>("/productions", { method: "POST", ...json(payload) });
    },
    onSuccess: (p) => {
      client.invalidateQueries({ queryKey: ["productions"] });
      client.invalidateQueries({ queryKey: ["dashboard"] });
      client.invalidateQueries({ queryKey: ["finance"] });
      setOpen(false);
      setForm(createBlankForm());
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

  const handleOpenChange = (v: boolean) => {
    setOpen(v);
    if (!v) {
      save.reset();
    }
  };

  const fieldErrors =
    save.error instanceof ApiError ? save.error.fields : undefined;

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Productions</h1>
          <p>Plan shoots, crews and delivery from one operational surface.</p>
        </div>
        <SAButton
          variant="primary"
          onClick={() => {
            save.reset();
            setOpen(true);
          }}
        >
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
                  {date(p.eventDate)} · {p.startTime ? p.startTime.slice(0, 5) : "TBD"}
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
                  {p.members?.length ?? 0} crew
                </span>
                <span>{p.unfinishedTaskCount} unfinished tasks</span>
              </footer>
            </SABentoCard>
          ))}
        </SABentoGrid>
      )}
      <ProductionForm
        open={open}
        onOpenChange={handleOpenChange}
        form={form}
        setForm={setForm}
        onSave={() => save.mutate()}
        pending={save.isPending}
        error={save.error?.message}
        fieldErrors={fieldErrors}
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
  fieldErrors,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  form: typeof blank;
  setForm: React.Dispatch<React.SetStateAction<typeof blank>>;
  onSave: () => void;
  pending: boolean;
  error?: string;
  fieldErrors?: Record<string, string>;
}) {
  const employeesQuery = useQuery({
    queryKey: ["employees"],
    queryFn: () => api<Employee[]>("/employees"),
    enabled: open,
  });

  const equipmentQuery = useQuery({
    queryKey: ["headquarters", "equipment", "intake"],
    queryFn: () => headquartersApi.equipment(0, ""),
    enabled: open,
  });

  const financeConfigQuery = useQuery({
    queryKey: ["finance", "config"],
    queryFn: () => financeApi.config(),
    enabled: open,
  });

  // Draft inputs
  const [selectedEmpId, setSelectedEmpId] = useState("");
  const [crewRole, setCrewRole] = useState("Crew");

  const [taskTitle, setTaskTitle] = useState("");
  const [taskPriority, setTaskPriority] = useState<Priority>("NORMAL");

  const [selectedEqId, setSelectedEqId] = useState("");
  const [eqQty, setEqQty] = useState("1");

  const [contractModalOpen, setContractModalOpen] = useState(false);
  const [contractModalAmount, setContractModalAmount] = useState("");
  const [contractModalDate, setContractModalDate] = useState("");
  const [contractModalDescription, setContractModalDescription] = useState("");
  const [contractModalError, setContractModalError] = useState("");

  const [advanceModalOpen, setAdvanceModalOpen] = useState(false);
  const [advanceModalAmount, setAdvanceModalAmount] = useState("");
  const [advanceModalDate, setAdvanceModalDate] = useState("");
  const [advanceModalReceiver, setAdvanceModalReceiver] = useState("");
  const [advanceModalDescription, setAdvanceModalDescription] = useState("");
  const [advanceModalError, setAdvanceModalError] = useState("");

  const field = <K extends keyof typeof blank>(
    key: K,
    value: (typeof blank)[K],
  ) => setForm((prev) => ({ ...prev, [key]: value }));

  const openContractModal = () => {
    setContractModalAmount(form.contract.amount);
    setContractModalDate(form.contract.date || form.eventDate);
    setContractModalDescription(
      form.contract.description || (form.title.trim() ? `Contract for ${form.title.trim()}` : ""),
    );
    setContractModalError("");
    setContractModalOpen(true);
  };

  const saveContract = () => {
    const amt = Number(contractModalAmount);
    if (isNaN(amt) || amt <= 0) {
      setContractModalError("Contract amount must be a positive number.");
      return;
    }
    const currentAdv = Number(form.advance.amount || 0);
    if (currentAdv > amt) {
      setForm((prev) => ({
        ...prev,
        contract: {
          ...prev.contract,
          amount: contractModalAmount,
          date: contractModalDate || prev.eventDate,
          description:
            contractModalDescription.trim() || `Contract for ${prev.title.trim()}`,
        },
        advance: {
          ...prev.advance,
          amount: contractModalAmount,
        },
      }));
    } else {
      setForm((prev) => ({
        ...prev,
        contract: {
          ...prev.contract,
          amount: contractModalAmount,
          date: contractModalDate || prev.eventDate,
          description:
            contractModalDescription.trim() || `Contract for ${prev.title.trim()}`,
        },
      }));
    }
    setContractModalOpen(false);
  };

  const openAdvanceModal = () => {
    setAdvanceModalAmount(form.advance.amount === "0" ? "" : form.advance.amount);
    setAdvanceModalDate(form.advance.date || form.eventDate);
    setAdvanceModalReceiver(
      form.advance.receiverAccount ||
        financeConfigQuery.data?.accounts?.[0]?.code ||
        "AZ-2",
    );
    setAdvanceModalDescription(
      form.advance.description ||
        (form.title.trim() ? `Client advance for ${form.title.trim()}` : ""),
    );
    setAdvanceModalError("");
    setAdvanceModalOpen(true);
  };

  const saveAdvance = () => {
    const amt = Number(advanceModalAmount || 0);
    if (isNaN(amt) || amt < 0) {
      setAdvanceModalError("Advance amount cannot be negative.");
      return;
    }
    const contractAmt = Number(form.contract.amount || 0);
    if (contractAmt > 0 && amt > contractAmt) {
      setAdvanceModalError("Advance amount cannot exceed the contract amount.");
      return;
    }
    if (amt > 0 && !advanceModalReceiver) {
      setAdvanceModalError("Owner receiver account is required.");
      return;
    }
    setForm((prev) => ({
      ...prev,
      advance: {
        ...prev.advance,
        amount: amt.toString(),
        date: advanceModalDate || prev.eventDate,
        receiverAccount: advanceModalReceiver,
        description:
          advanceModalDescription.trim() || `Client advance for ${prev.title.trim()}`,
      },
    }));
    setAdvanceModalOpen(false);
  };

  const addCrew = () => {
    if (!selectedEmpId) return;
    const emp = employeesQuery.data?.find((e) => e.id === selectedEmpId);
    if (!emp) return;
    setForm((prev) => ({
      ...prev,
      crew: [
        ...prev.crew,
        {
          employeeId: emp.id,
          employeeName: emp.displayName,
          role: crewRole.trim() || "Crew",
        },
      ],
    }));
    setSelectedEmpId("");
    setCrewRole("Crew");
  };

  const removeCrew = (empId: string) => {
    setForm((prev) => ({
      ...prev,
      crew: prev.crew.filter((c) => c.employeeId !== empId),
    }));
  };

  const addTask = () => {
    if (!taskTitle.trim()) return;
    setForm((prev) => ({
      ...prev,
      tasks: [
        ...prev.tasks,
        {
          title: taskTitle.trim(),
          priority: taskPriority,
        },
      ],
    }));
    setTaskTitle("");
    setTaskPriority("NORMAL");
  };

  const removeTask = (index: number) => {
    setForm((prev) => ({
      ...prev,
      tasks: prev.tasks.filter((_, i) => i !== index),
    }));
  };

  const addEquipment = () => {
    if (!selectedEqId) return;
    const eqList = equipmentQuery.data?.items ?? [];
    const eq = eqList.find((item) => item.id === selectedEqId);
    if (!eq) return;
    const qty = Math.max(1, Number(eqQty) || 1);
    setForm((prev) => ({
      ...prev,
      equipment: [
        ...prev.equipment,
        {
          equipmentId: eq.id,
          equipmentName: eq.name,
          quantity: qty,
        },
      ],
    }));
    setSelectedEqId("");
    setEqQty("1");
  };

  const removeEquipment = (eqId: string) => {
    setForm((prev) => ({
      ...prev,
      equipment: prev.equipment.filter((e) => e.equipmentId !== eqId),
    }));
  };

  const contractAmtNum = Number(form.contract.amount);
  const advanceAmtNum = Number(form.advance.amount || 0);

  return (
    <>
      <SAModal
        open={open}
        onOpenChange={onOpenChange}
        title="Create Production"
        description="Enter all operational details in one single intake. Crew, tasks, schedule and equipment will be orchestrated together."
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "16px", maxHeight: "70vh", overflowY: "auto", paddingRight: "4px" }}>
          {/* SECTION 1: PRODUCTION CORE */}
          <div className="form-grid">
            <FormField label="Program" error={fieldErrors?.title}>
              <input
                aria-label="Production title"
                placeholder="e.g. Sharma Wedding"
                value={form.title}
                onChange={(e) => field("title", e.target.value)}
              />
            </FormField>
            <FormField label="Client" error={fieldErrors?.clientName}>
              <input
                aria-label="Client name"
                placeholder="e.g. Sharma Family"
                value={form.clientName}
                onChange={(e) => field("clientName", e.target.value)}
              />
            </FormField>
            <FormField label="Date" error={fieldErrors?.eventDate}>
              <input
                aria-label="Event date"
                type="date"
                value={form.eventDate}
                onChange={(e) => field("eventDate", e.target.value)}
              />
            </FormField>
            <FormField label="Priority" error={fieldErrors?.priority}>
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
            <FormField label="Venue" error={fieldErrors?.venueName}>
              <input
                aria-label="Venue name"
                placeholder="e.g. Royal Orchid"
                value={form.venueName}
                onChange={(e) => field("venueName", e.target.value)}
              />
            </FormField>
            <FormField label="Address" error={fieldErrors?.venueAddress}>
              <input
                aria-label="Venue address"
                placeholder="e.g. MG Road, Bengaluru"
                value={form.venueAddress}
                onChange={(e) => field("venueAddress", e.target.value)}
              />
            </FormField>
          </div>

          {/* SECTION 2: EQUIPMENT NEEDED (OPTIONAL) - DIRECTLY BELOW VENUE/ADDRESS */}
          <div className="production-single-intake-section">
            <div className="production-single-intake-section-title">
              <span>
                <Boxes size={13} style={{ verticalAlign: "middle", marginRight: "6px" }} />
                Equipment Needed (Optional)
              </span>
              <span style={{ fontSize: "11px", fontWeight: "normal", color: "var(--text-3)" }}>
                {form.equipment.length} items
              </span>
            </div>

            {form.equipment.length > 0 && (
              <div className="intake-chip-list">
                {form.equipment.map((eq) => (
                  <span key={eq.equipmentId} className="intake-chip">
                    <strong>{eq.equipmentName}</strong> ({eq.quantity} units)
                    <button
                      type="button"
                      aria-label={`Remove equipment ${eq.equipmentName}`}
                      onClick={() => removeEquipment(eq.equipmentId)}
                    >
                      <X size={12} />
                    </button>
                  </span>
                ))}
              </div>
            )}

            <div className="intake-inline-add">
              <select
                aria-label="Select equipment"
                value={selectedEqId}
                onChange={(e) => setSelectedEqId(e.target.value)}
                style={{ flex: 1 }}
              >
                <option value="">Choose equipment...</option>
                {equipmentQuery.data?.items
                  ?.filter((item) => !form.equipment.some((e) => e.equipmentId === item.id))
                  .map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} {item.internalCode ? `(${item.internalCode})` : ""}
                    </option>
                  ))}
              </select>
              <input
                aria-label="Equipment quantity"
                type="number"
                min="0.001"
                step="0.001"
                value={eqQty}
                onChange={(e) => setEqQty(e.target.value)}
                style={{ width: "80px" }}
              />
              <SAButton size="sm" disabled={!selectedEqId} onClick={addEquipment}>
                + Add Gear
              </SAButton>
            </div>
          </div>

          {/* SECTION 3: FINANCIAL SETTLEMENT - DIRECTLY AFTER EQUIPMENT */}
          <div className="production-single-intake-section">
            <div className="production-single-intake-section-title">
              <span>
                <Receipt size={13} style={{ verticalAlign: "middle", marginRight: "6px" }} />
                Financial Settlement
              </span>
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: "normal",
                  color: contractAmtNum > 0 ? "var(--success)" : "var(--danger)",
                }}
              >
                {contractAmtNum > 0 ? "Contract Set" : "Contract Required"}
              </span>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
              <SABentoCard
                style={{
                  padding: "12px",
                  background: "var(--surface-2)",
                  border: "1px solid var(--border-subtle)",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: "8px",
                  }}
                >
                  <span
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      textTransform: "uppercase",
                      letterSpacing: "0.05em",
                    }}
                  >
                    Contract
                  </span>
                  <StatusBadge tone={contractAmtNum > 0 ? "success" : "neutral"}>
                    {contractAmtNum > 0 ? financeAmount(contractAmtNum) : "Not set"}
                  </StatusBadge>
                </div>
                <SAButton
                  type="button"
                  size="sm"
                  variant={contractAmtNum > 0 ? "secondary" : "primary"}
                  onClick={openContractModal}
                  style={{ width: "100%" }}
                >
                  {contractAmtNum > 0
                    ? `Set Contract · ${financeAmount(contractAmtNum)}`
                    : "Set Contract"}
                </SAButton>
              </SABentoCard>

              <SABentoCard
                style={{
                  padding: "12px",
                  background: "var(--surface-2)",
                  border: "1px solid var(--border-subtle)",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: "8px",
                  }}
                >
                  <span
                    style={{
                      fontSize: "12px",
                      color: "var(--text-3)",
                      textTransform: "uppercase",
                      letterSpacing: "0.05em",
                    }}
                  >
                    Advance
                  </span>
                  <StatusBadge tone={advanceAmtNum > 0 ? "success" : "neutral"}>
                    {advanceAmtNum > 0 ? financeAmount(advanceAmtNum) : "₹0"}
                  </StatusBadge>
                </div>
                <SAButton
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={openAdvanceModal}
                  style={{ width: "100%" }}
                >
                  {advanceAmtNum > 0
                    ? `Advance · ${financeAmount(advanceAmtNum)}`
                    : "Advance"}
                </SAButton>
              </SABentoCard>
            </div>
          </div>

          {/* SECTION 4: SCHEDULE (OPTIONAL) */}
          <div className="production-single-intake-section">
            <div className="production-single-intake-section-title">
              <span>
                <Clock size={13} style={{ verticalAlign: "middle", marginRight: "6px" }} />
                Schedule (Optional)
              </span>
            </div>
            <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
              <FormField label="Start Time">
                <input
                  aria-label="Start time"
                  type="time"
                  value={form.startTime}
                  onChange={(e) => field("startTime", e.target.value)}
                />
              </FormField>
              <FormField label="End Time">
                <input
                  aria-label="End time"
                  type="time"
                  value={form.endTime}
                  onChange={(e) => field("endTime", e.target.value)}
                />
              </FormField>
            </div>
          </div>

          {/* SECTION 5: CREW (OPTIONAL) */}
          <div className="production-single-intake-section">
            <div className="production-single-intake-section-title">
              <span>
                <Users size={13} style={{ verticalAlign: "middle", marginRight: "6px" }} />
                Crew Assignments (Optional)
              </span>
              <span style={{ fontSize: "11px", fontWeight: "normal", color: "var(--text-3)" }}>
                {form.crew.length} assigned
              </span>
            </div>

            {form.crew.length > 0 && (
              <div className="intake-chip-list">
                {form.crew.map((c) => (
                  <span key={c.employeeId} className="intake-chip">
                    <strong>{c.employeeName}</strong> · {c.role}
                    <button
                      type="button"
                      aria-label={`Remove crew ${c.employeeName}`}
                      onClick={() => removeCrew(c.employeeId)}
                    >
                      <X size={12} />
                    </button>
                  </span>
                ))}
              </div>
            )}

            <div className="intake-inline-add">
              <select
                aria-label="Select crew employee"
                value={selectedEmpId}
                onChange={(e) => setSelectedEmpId(e.target.value)}
                style={{ flex: 1 }}
              >
                <option value="">Choose employee...</option>
                {employeesQuery.data
                  ?.filter((e) => !form.crew.some((c) => c.employeeId === e.id))
                  .map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.displayName}
                    </option>
                  ))}
              </select>
              <input
                aria-label="Crew role"
                placeholder="Role (e.g. Lead Sound)"
                value={crewRole}
                onChange={(e) => setCrewRole(e.target.value)}
                style={{ width: "160px" }}
              />
              <SAButton size="sm" disabled={!selectedEmpId} onClick={addCrew}>
                + Add Crew
              </SAButton>
            </div>
          </div>

          {/* SECTION 6: TASKS (OPTIONAL) */}
          <div className="production-single-intake-section">
            <div className="production-single-intake-section-title">
              <span>
                <CheckSquare size={13} style={{ verticalAlign: "middle", marginRight: "6px" }} />
                Operational Tasks (Optional)
              </span>
              <span style={{ fontSize: "11px", fontWeight: "normal", color: "var(--text-3)" }}>
                {form.tasks.length} tasks
              </span>
            </div>

            {form.tasks.length > 0 && (
              <div className="intake-chip-list">
                {form.tasks.map((t, i) => (
                  <span key={i} className="intake-chip">
                    <span>{t.title}</span>
                    <StatusBadge tone={t.priority === "HIGH" || t.priority === "URGENT" ? "warning" : "neutral"}>
                      {t.priority}
                    </StatusBadge>
                    <button
                      type="button"
                      aria-label={`Remove task ${t.title}`}
                      onClick={() => removeTask(i)}
                    >
                      <X size={12} />
                    </button>
                  </span>
                ))}
              </div>
            )}

            <div className="intake-inline-add">
              <input
                aria-label="Task title"
                placeholder="e.g. Confirm venue power check"
                value={taskTitle}
                onChange={(e) => setTaskTitle(e.target.value)}
                style={{ flex: 1 }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addTask();
                  }
                }}
              />
              <select
                aria-label="Task priority"
                value={taskPriority}
                onChange={(e) => setTaskPriority(e.target.value as Priority)}
                style={{ width: "110px" }}
              >
                {["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => (
                  <option key={p}>{p}</option>
                ))}
              </select>
              <SAButton size="sm" disabled={!taskTitle.trim()} onClick={addTask}>
                + Add Task
              </SAButton>
            </div>
          </div>

          {/* SECTION 7: NOTES */}
          <div className="production-single-intake-section">
            <FormField label="Operational Notes" error={fieldErrors?.description}>
              <textarea
                aria-label="Production notes"
                rows={2}
                placeholder="Special instructions, client preferences, or setup details..."
                value={form.description}
                onChange={(e) => field("description", e.target.value)}
              />
            </FormField>
          </div>
        </div>

        {error && <p className="form-error">{error}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => onOpenChange(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              pending ||
              !form.title.trim() ||
              !form.clientName.trim() ||
              !form.venueName.trim() ||
              !form.eventDate.trim() ||
              !form.contract.amount ||
              contractAmtNum <= 0 ||
              advanceAmtNum < 0 ||
              advanceAmtNum > contractAmtNum ||
              (advanceAmtNum > 0 && !form.advance.receiverAccount)
            }
            onClick={onSave}
          >
            {pending ? "Creating…" : "Create Production"}
          </SAButton>
        </div>
      </SAModal>

      <SAModal
        open={contractModalOpen}
        onOpenChange={setContractModalOpen}
        title="Set Contract"
        description={`Establish the agreed commercial value for ${form.title.trim() || "production"} in canonical Finance.`}
      >
        <div className="form-grid">
          <FormField label="Contract Amount (₹)" error={contractModalError}>
            <input
              aria-label="Contract amount"
              type="number"
              step="0.01"
              min="0.01"
              required
              placeholder="e.g. 150000"
              value={contractModalAmount}
              onChange={(e) => {
                setContractModalError("");
                setContractModalAmount(e.target.value);
              }}
            />
          </FormField>
          <FormField label="Effective Date">
            <input
              aria-label="Contract date"
              type="date"
              required
              value={contractModalDate}
              onChange={(e) => setContractModalDate(e.target.value)}
            />
          </FormField>
          <FormField label="Description / Reference">
            <input
              aria-label="Contract description"
              required
              placeholder={`Contract for ${form.title.trim() || "production"}`}
              value={contractModalDescription}
              onChange={(e) => setContractModalDescription(e.target.value)}
            />
          </FormField>
        </div>
        {contractModalError && <p className="form-error">{contractModalError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setContractModalOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={!contractModalAmount || Number(contractModalAmount) <= 0}
            onClick={saveContract}
          >
            Save Contract
          </SAButton>
        </div>
      </SAModal>

      <SAModal
        open={advanceModalOpen}
        onOpenChange={setAdvanceModalOpen}
        title="Advance"
        description={`Record client advance or payment for ${form.title.trim() || "production"}.`}
      >
        <div className="form-grid">
          <FormField label="Advance Amount (₹)" error={advanceModalError}>
            <input
              aria-label="Advance amount"
              type="number"
              step="0.01"
              min="0"
              placeholder="e.g. 50000"
              value={advanceModalAmount}
              onChange={(e) => {
                setAdvanceModalError("");
                setAdvanceModalAmount(e.target.value);
              }}
            />
          </FormField>
          <FormField label="Received By (Owner Account)">
            <select
              aria-label="Receiver owner account"
              value={advanceModalReceiver}
              onChange={(e) => setAdvanceModalReceiver(e.target.value)}
            >
              <option value="" disabled>
                Select owner account...
              </option>
              {financeConfigQuery.data?.accounts && financeConfigQuery.data.accounts.length > 0 ? (
                financeConfigQuery.data.accounts.map((a) => (
                  <option key={a.id || a.code} value={a.code}>
                    {a.displayName} ({a.code})
                  </option>
                ))
              ) : (
                <>
                  <option value="AZ-2">Azeem (AZ-2)</option>
                  <option value="AK-2">Akash (AK-2)</option>
                </>
              )}
            </select>
          </FormField>
          <FormField label="Date">
            <input
              aria-label="Advance date"
              type="date"
              value={advanceModalDate}
              onChange={(e) => setAdvanceModalDate(e.target.value)}
            />
          </FormField>
          <FormField label="Description / Reference">
            <input
              aria-label="Advance description"
              placeholder={`Client advance for ${form.title.trim() || "production"}`}
              value={advanceModalDescription}
              onChange={(e) => setAdvanceModalDescription(e.target.value)}
            />
          </FormField>
        </div>
        {advanceModalError && <p className="form-error">{advanceModalError}</p>}
        <div className="modal-actions">
          <SAButton onClick={() => setAdvanceModalOpen(false)}>Cancel</SAButton>
          <SAButton
            variant="primary"
            disabled={
              Number(advanceModalAmount) < 0 ||
              (Number(form.contract.amount) > 0 &&
                Number(advanceModalAmount) > Number(form.contract.amount))
            }
            onClick={saveAdvance}
          >
            Save Advance
          </SAButton>
        </div>
      </SAModal>
    </>
  );
}

const date = (v: string) =>
  new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short" }).format(
    new Date(`${v}T00:00:00`),
  );
