import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  ClipboardCheck,
  Plus,
  Search,
  Settings2,
  Truck,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  EmptyState,
  FormField,
  MetricCard,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SAModal,
  SASegmentedControl,
  SAStatefulButton,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import { api, ApiError } from "../../lib/api";
import type { Production } from "../../types/domain";
import { headquartersApi } from "./headquarters.api";
import type { HqEquipment, HqTab } from "./headquarters.types";

type Workflow =
  "STOCK" | "ADJUSTMENT" | "RESERVATION" | "DISPATCH" | "TRANSFER" | "RETURN";
const equipmentBlank = {
  name: "",
  internalCode: "",
  description: "",
  categoryId: "",
  unitId: "",
  trackingMode: "QUANTITY",
  minimumReserve: "0",
  defaultLocationId: "",
  ownership: "SA_OWNED",
};
const operationBlank = {
  equipmentId: "",
  productionId: "",
  sourceLocationId: "",
  destinationLocationId: "",
  quantity: "",
  startsAt: "",
  endsAt: "",
  reason: "",
  returned: "",
  damaged: "",
  missing: "",
  consumed: "",
};
const n = (value: string) => Number(value || 0);

export function HeadquartersPage() {
  const client = useQueryClient();
  const locationState = useLocation();
  const [tab, setTab] = useState<HqTab>("OVERVIEW"),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [equipmentOpen, setEquipmentOpen] = useState(false),
    [configOpen, setConfigOpen] = useState(false),
    [workflow, setWorkflow] = useState<Workflow | null>(null),
    [equipmentForm, setEquipmentForm] = useState(equipmentBlank),
    [operation, setOperation] = useState(operationBlank),
    [categoryName, setCategoryName] = useState(""),
    [unit, setUnit] = useState({ name: "", symbol: "" }),
    [location, setLocation] = useState({
      name: "",
      locationType: "HEADQUARTERS",
    });
  const overview = useQuery({
    queryKey: ["headquarters", "overview"],
    queryFn: headquartersApi.overview,
  });
  const inventory = useQuery({
    queryKey: ["headquarters", "equipment", page, search],
    queryFn: () => headquartersApi.equipment(page, search),
  });
  const config = useQuery({
    queryKey: ["headquarters", "config"],
    queryFn: headquartersApi.config,
  });
  const movements = useQuery({
    queryKey: ["headquarters", "movements"],
    queryFn: () => headquartersApi.movements(),
  });
  const attention = useQuery({
    queryKey: ["headquarters", "attention"],
    queryFn: headquartersApi.attention,
  });
  const productions = useQuery({
    queryKey: ["productions"],
    queryFn: () => api<Production[]>("/productions"),
  });
  useEffect(() => {
    if (new URLSearchParams(locationState.search).get("create") === "equipment")
      setEquipmentOpen(true);
  }, [locationState.search]);
  const refresh = () =>
    client.invalidateQueries({ queryKey: ["headquarters"] });
  const saveEquipment = useMutation({
    mutationFn: () =>
      headquartersApi.createEquipment({
        ...equipmentForm,
        categoryId: equipmentForm.categoryId || null,
        defaultLocationId: equipmentForm.defaultLocationId || null,
        minimumReserve: n(equipmentForm.minimumReserve),
      }),
    onSuccess: () => {
      refresh();
      setEquipmentOpen(false);
      setEquipmentForm(equipmentBlank);
    },
  });
  const setup = useMutation({
    mutationFn: async () => {
      if (categoryName.trim())
        await headquartersApi.createCategory({ name: categoryName });
      if (unit.name.trim() && unit.symbol.trim())
        await headquartersApi.createUnit({ ...unit, decimalAllowed: false });
      if (location.name.trim())
        await headquartersApi.createLocation({
          ...location,
          parentId: null,
          productionId: null,
        });
    },
    onSuccess: () => {
      refresh();
      setCategoryName("");
      setUnit({ name: "", symbol: "" });
      setLocation({ name: "", locationType: "HEADQUARTERS" });
    },
  });
  const execute = useMutation({
    mutationFn: async () => {
      const quantity = n(operation.quantity),
        line = { equipmentId: operation.equipmentId, quantity };
      if (workflow === "STOCK")
        return headquartersApi.stock(operation.equipmentId, {
          locationId: operation.destinationLocationId,
          quantity,
          reason: operation.reason,
          idempotencyKey: crypto.randomUUID(),
        });
      if (workflow === "ADJUSTMENT")
        return headquartersApi.adjust(operation.equipmentId, {
          locationId: operation.destinationLocationId,
          difference: quantity,
          reason: operation.reason,
          idempotencyKey: crypto.randomUUID(),
        });
      if (workflow === "RESERVATION")
        return headquartersApi.reserve({
          productionId: operation.productionId,
          startsAt: new Date(operation.startsAt).toISOString(),
          endsAt: new Date(operation.endsAt).toISOString(),
          lines: [line],
        });
      if (workflow === "DISPATCH") {
        const key = crypto.randomUUID(),
          created = await headquartersApi.createDispatch({
            productionId: operation.productionId,
            sourceLocationId: operation.sourceLocationId,
            destinationLocationId: operation.destinationLocationId,
            scheduledAt: null,
            notes: operation.reason,
            lines: [line],
          });
        return headquartersApi.confirmDispatch(created.id, key);
      }
      if (workflow === "TRANSFER") {
        const key = crypto.randomUUID(),
          created = await headquartersApi.createTransfer({
            sourceProductionId: null,
            destinationProductionId: operation.productionId || null,
            sourceLocationId: operation.sourceLocationId,
            destinationLocationId: operation.destinationLocationId,
            notes: operation.reason,
            lines: [line],
          });
        return headquartersApi.confirmTransfer(created.id, key);
      }
      const key = crypto.randomUUID(),
        created = await headquartersApi.createReturn({
          productionId: operation.productionId,
          sourceLocationId: operation.sourceLocationId,
          destinationLocationId: operation.destinationLocationId,
          notes: operation.reason,
          lines: [
            {
              equipmentId: operation.equipmentId,
              assetId: null,
              expected: quantity,
              returned: n(operation.returned),
              transferred: 0,
              consumed: n(operation.consumed),
              damaged: n(operation.damaged),
              missing: n(operation.missing),
            },
          ],
        });
      return headquartersApi.confirmReturn(created.id, key);
    },
    onSuccess: () => {
      refresh();
      setWorkflow(null);
      setOperation(operationBlank);
    },
  });
  const rows = inventory.data?.items ?? [],
    cfg = config.data;
  return (
    <div className="hq-page">
      <div className="page-title hq-title">
        <div>
          <span className="eyebrow">Physical operations</span>
          <h1>Headquarters</h1>
          <p>Equipment, commitments, custody and reconciliation.</p>
        </div>
        <div className="hq-title-actions">
          <SAButton onClick={() => setConfigOpen(true)}>
            <Settings2 size={15} />
            Configure
          </SAButton>
          <SAButton variant="primary" onClick={() => setEquipmentOpen(true)}>
            <Plus size={15} />
            Equipment
          </SAButton>
        </div>
      </div>
      <SASegmentedControl
        value={tab}
        onChange={setTab}
        label="Headquarters workspace"
        items={[
          { value: "OVERVIEW", label: "Overview" },
          { value: "INVENTORY", label: "Inventory" },
          { value: "PRODUCTIONS", label: "Productions" },
          { value: "MOVEMENTS", label: "Movements" },
          {
            value: "ATTENTION",
            label: `Attention${attention.data?.length ? ` · ${attention.data.length}` : ""}`,
          },
        ]}
      />
      {tab === "OVERVIEW" && (
        <Overview
          loading={overview.isPending}
          error={overview.isError}
          data={overview.data}
          onRetry={() => overview.refetch()}
          onWorkflow={setWorkflow}
        />
      )}
      {tab === "INVENTORY" && (
        <Inventory
          loading={inventory.isPending}
          error={inventory.isError}
          rows={rows}
          total={inventory.data?.total ?? 0}
          page={page}
          search={search}
          onSearch={(v) => {
            setSearch(v);
            setPage(0);
          }}
          onPage={setPage}
          onAdd={() => setEquipmentOpen(true)}
          onStock={(e) => {
            setOperation({
              ...operationBlank,
              equipmentId: e.id,
              destinationLocationId: e.id ? (cfg?.locations[0]?.id ?? "") : "",
            });
            setWorkflow("STOCK");
          }}
          onAdjust={(e) => {
            setOperation({
              ...operationBlank,
              equipmentId: e.id,
              destinationLocationId: cfg?.locations[0]?.id ?? "",
            });
            setWorkflow("ADJUSTMENT");
          }}
        />
      )}
      {tab === "PRODUCTIONS" && (
        <Productions
          data={overview.data?.activeProductions ?? []}
          onReserve={() => setWorkflow("RESERVATION")}
          onDispatch={() => setWorkflow("DISPATCH")}
          onTransfer={() => setWorkflow("TRANSFER")}
          onReturn={() => setWorkflow("RETURN")}
        />
      )}
      {tab === "MOVEMENTS" && (
        <Movements
          loading={movements.isPending}
          rows={movements.data?.items ?? []}
        />
      )}
      {tab === "ATTENTION" && (
        <Attention loading={attention.isPending} rows={attention.data ?? []} />
      )}
      <SAModal
        open={equipmentOpen}
        onOpenChange={setEquipmentOpen}
        title="Add equipment"
        description="Create the definition first. Stock is posted separately as ledger evidence."
      >
        <form
          className="hq-form"
          onSubmit={(e) => {
            e.preventDefault();
            saveEquipment.mutate();
          }}
        >
          <FormField label="Equipment name">
            <input
              required
              value={equipmentForm.name}
              onChange={(e) =>
                setEquipmentForm({ ...equipmentForm, name: e.target.value })
              }
            />
          </FormField>
          <FormField label="Internal code">
            <input
              value={equipmentForm.internalCode}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  internalCode: e.target.value,
                })
              }
            />
          </FormField>
          <FormField label="Category">
            <select
              value={equipmentForm.categoryId}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  categoryId: e.target.value,
                })
              }
            >
              <option value="">None</option>
              {cfg?.categories.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Tracking">
            <select
              value={equipmentForm.trackingMode}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  trackingMode: e.target.value,
                })
              }
            >
              <option>QUANTITY</option>
              <option>SERIALIZED</option>
              <option>CONSUMABLE</option>
            </select>
          </FormField>
          <FormField label="Unit">
            <select
              required
              value={equipmentForm.unitId}
              onChange={(e) =>
                setEquipmentForm({ ...equipmentForm, unitId: e.target.value })
              }
            >
              <option value="">Select unit</option>
              {cfg?.units.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name} ({x.symbol})
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Default storage">
            <select
              value={equipmentForm.defaultLocationId}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  defaultLocationId: e.target.value,
                })
              }
            >
              <option value="">None</option>
              {cfg?.locations.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Ownership">
            <select
              value={equipmentForm.ownership}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  ownership: e.target.value,
                })
              }
            >
              <option>SA_OWNED</option>
              <option>RENTED</option>
              <option>VENDOR_SUPPLIED</option>
              <option>CLIENT_SUPPLIED</option>
              <option>OTHER</option>
            </select>
          </FormField>
          <FormField label="Minimum reserve">
            <input
              type="number"
              min="0"
              step="1"
              value={equipmentForm.minimumReserve}
              onChange={(e) =>
                setEquipmentForm({
                  ...equipmentForm,
                  minimumReserve: e.target.value,
                })
              }
            />
          </FormField>
          {saveEquipment.error && <InlineError error={saveEquipment.error} />}
          <SAStatefulButton
            variant="primary"
            pending={saveEquipment.isPending}
            type="submit"
          >
            Create equipment
          </SAStatefulButton>
        </form>
      </SAModal>
      <SAModal
        open={configOpen}
        onOpenChange={setConfigOpen}
        title="Headquarters configuration"
        description="Owner-defined catalogue structure and physical locations."
      >
        <form
          className="hq-form"
          onSubmit={(e) => {
            e.preventDefault();
            setup.mutate();
          }}
        >
          <FormField label="New category">
            <input
              placeholder="Optional"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
            />
          </FormField>
          <div className="hq-form-row">
            <FormField label="New unit">
              <input
                placeholder="Optional"
                value={unit.name}
                onChange={(e) => setUnit({ ...unit, name: e.target.value })}
              />
            </FormField>
            <FormField label="Symbol">
              <input
                placeholder="pcs"
                value={unit.symbol}
                onChange={(e) => setUnit({ ...unit, symbol: e.target.value })}
              />
            </FormField>
          </div>
          <div className="hq-form-row">
            <FormField label="New location">
              <input
                placeholder="Optional"
                value={location.name}
                onChange={(e) =>
                  setLocation({ ...location, name: e.target.value })
                }
              />
            </FormField>
            <FormField label="Location type">
              <select
                value={location.locationType}
                onChange={(e) =>
                  setLocation({ ...location, locationType: e.target.value })
                }
              >
                <option>HEADQUARTERS</option>
                <option>WAREHOUSE</option>
                <option>STORAGE_ZONE</option>
                <option>PRODUCTION_LOCATION</option>
                <option>TRANSIT</option>
                <option>WORKSHOP</option>
                <option>EXTERNAL_CUSTODY</option>
                <option>OTHER</option>
              </select>
            </FormField>
          </div>
          {setup.error && <InlineError error={setup.error} />}
          <SAStatefulButton
            variant="primary"
            pending={setup.isPending}
            type="submit"
          >
            Save configuration
          </SAStatefulButton>
        </form>
      </SAModal>
      <OperationModal
        mode={workflow}
        onClose={() => setWorkflow(null)}
        value={operation}
        setValue={setOperation}
        equipment={rows}
        locations={cfg?.locations ?? []}
        productions={productions.data ?? []}
        pending={execute.isPending}
        error={execute.error}
        onSubmit={() => execute.mutate()}
      />
    </div>
  );
}

function Overview({
  loading,
  error,
  data,
  onRetry,
  onWorkflow,
}: {
  loading: boolean;
  error: boolean;
  data: any;
  onRetry: () => void;
  onWorkflow: (v: Workflow) => void;
}) {
  if (loading)
    return (
      <SABentoGrid className="hq-metrics">
        {[1, 2, 3, 4].map((x) => (
          <SkeletonCard key={x} />
        ))}
      </SABentoGrid>
    );
  if (error)
    return (
      <EmptyState
        title="Headquarters could not be loaded"
        description="Operational inventory remains unchanged. Try the request again."
        action={<SAButton onClick={onRetry}>Try again</SAButton>}
      />
    );
  return (
    <>
      <SABentoGrid className="hq-metrics">
        <MetricCard
          label="Available"
          value={data?.available ?? 0}
          detail="Usable after commitments"
        />
        <MetricCard
          label="Deployed"
          value={data?.deployed ?? 0}
          detail="At production locations"
        />
        <MetricCard
          label="Reserved"
          value={data?.reserved ?? 0}
          detail="Upcoming commitments"
        />
        <MetricCard
          label="Attention"
          value={data?.attention ?? 0}
          detail="Owner decisions required"
        />
      </SABentoGrid>
      <SABentoGrid className="hq-command-grid">
        <SABentoCard className="hq-attention-card">
          <header>
            <div>
              <span className="eyebrow">Needs attention</span>
              <h2>Operational exceptions</h2>
            </div>
            <AlertTriangle size={18} />
          </header>
          {data?.attention ? (
            <p>
              {data.attention} open item{data.attention === 1 ? "" : "s"}{" "}
              require review.
            </p>
          ) : (
            <p className="muted">Everything is reconciled. No false urgency.</p>
          )}
        </SABentoCard>
        <SABentoCard>
          <span className="eyebrow">Today</span>
          <h2>Logistics</h2>
          {data?.today?.length ? (
            data.today.map((x: any) => (
              <div className="hq-list-row" key={x.id}>
                <Truck size={15} />
                <span>{x.reference}</span>
                <StatusBadge>{x.status}</StatusBadge>
              </div>
            ))
          ) : (
            <p className="muted">
              Nothing is scheduled to leave Headquarters today.
            </p>
          )}
        </SABentoCard>
        <SABentoCard className="hq-actions">
          <span className="eyebrow">Operate</span>
          <h2>Post truthful movement</h2>
          <div>
            <SAButton onClick={() => onWorkflow("RESERVATION")}>
              Reserve
            </SAButton>
            <SAButton onClick={() => onWorkflow("DISPATCH")}>Dispatch</SAButton>
            <SAButton onClick={() => onWorkflow("TRANSFER")}>Transfer</SAButton>
            <SAButton onClick={() => onWorkflow("RETURN")}>Return</SAButton>
          </div>
        </SABentoCard>
        <SABentoCard>
          <span className="eyebrow">Position</span>
          <h2>{data?.controlled ?? 0} controlled</h2>
          <p className="muted">
            Owned and external custody remain distinct in the ledger.
          </p>
        </SABentoCard>
      </SABentoGrid>
    </>
  );
}
function Inventory({
  loading,
  error,
  rows,
  total,
  page,
  search,
  onSearch,
  onPage,
  onAdd,
  onStock,
  onAdjust,
}: {
  loading: boolean;
  error: boolean;
  rows: HqEquipment[];
  total: number;
  page: number;
  search: string;
  onSearch: (v: string) => void;
  onPage: (p: number) => void;
  onAdd: () => void;
  onStock: (e: HqEquipment) => void;
  onAdjust: (e: HqEquipment) => void;
}) {
  return (
    <section className="hq-section">
      <div className="hq-toolbar">
        <label>
          <Search size={15} />
          <input
            aria-label="Search equipment"
            placeholder="Search equipment or code"
            value={search}
            onChange={(e) => onSearch(e.target.value)}
          />
        </label>
        <span>{total} definitions</span>
      </div>
      {loading ? (
        <SkeletonCard />
      ) : error ? (
        <EmptyState
          title="Inventory could not be loaded"
          description="No stock operation was changed."
        />
      ) : !rows.length ? (
        <EmptyState
          title="No equipment registered"
          description="Build the SA Productions equipment catalogue as it actually exists."
          action={
            <SAButton variant="primary" onClick={onAdd}>
              Add equipment
            </SAButton>
          }
        />
      ) : (
        <div className="hq-table-wrap">
          <table className="hq-table">
            <thead>
              <tr>
                <th>Equipment</th>
                <th>Tracking</th>
                <th>Controlled</th>
                <th>Available</th>
                <th>Reserved</th>
                <th>Ownership</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e) => (
                <tr key={e.id}>
                  <td>
                    <strong>{e.name}</strong>
                    <small>
                      {e.internalCode || e.category || "Uncategorised"}
                    </small>
                  </td>
                  <td>
                    <StatusBadge>{label(e.trackingMode)}</StatusBadge>
                  </td>
                  <td>
                    {e.controlled} {e.symbol}
                  </td>
                  <td>
                    {e.available} {e.symbol}
                  </td>
                  <td>
                    {e.reserved} {e.symbol}
                  </td>
                  <td>{label(e.ownership)}</td>
                  <td>
                    <div className="hq-row-actions">
                      <SAButton size="sm" onClick={() => onStock(e)}>
                        Stock in
                      </SAButton>
                      <SAButton size="sm" onClick={() => onAdjust(e)}>
                        Adjust
                      </SAButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="hq-pagination">
        <SAButton disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </SAButton>
        <span>Page {page + 1}</span>
        <SAButton
          disabled={(page + 1) * 50 >= total}
          onClick={() => onPage(page + 1)}
        >
          Next
        </SAButton>
      </div>
    </section>
  );
}
function Productions({
  data,
  onReserve,
  onDispatch,
  onTransfer,
  onReturn,
}: {
  data: Array<{ id: string; title: string; quantity: number }>;
  onReserve: () => void;
  onDispatch: () => void;
  onTransfer: () => void;
  onReturn: () => void;
}) {
  return (
    <SABentoGrid className="hq-command-grid">
      <SABentoCard className="hq-actions">
        <span className="eyebrow">Production logistics</span>
        <h2>Plan, move and reconcile</h2>
        <div>
          <SAButton onClick={onReserve}>Create reservation</SAButton>
          <SAButton onClick={onDispatch}>Confirm dispatch</SAButton>
          <SAButton onClick={onTransfer}>Direct transfer</SAButton>
          <SAButton onClick={onReturn}>Record return</SAButton>
        </div>
      </SABentoCard>
      <SABentoCard>
        <span className="eyebrow">Active productions</span>
        {data.length ? (
          data.map((x) => (
            <div className="hq-list-row" key={x.id}>
              <ClipboardCheck size={15} />
              <span>{x.title}</span>
              <b>{x.quantity}</b>
            </div>
          ))
        ) : (
          <p className="muted">
            No equipment is currently positioned at active productions.
          </p>
        )}
      </SABentoCard>
    </SABentoGrid>
  );
}
function Movements({ loading, rows }: { loading: boolean; rows: any[] }) {
  if (loading) return <SkeletonCard />;
  return (
    <section className="hq-section">
      <div className="hq-section-heading">
        <div>
          <span className="eyebrow">Evidence</span>
          <h2>Movement ledger</h2>
        </div>
      </div>
      {!rows.length ? (
        <EmptyState
          title="No movements recorded"
          description="Stock-in, dispatches, transfers and returns will appear here as immutable evidence."
        />
      ) : (
        <div className="hq-table-wrap">
          <table className="hq-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Movement</th>
                <th>Equipment</th>
                <th>Quantity</th>
                <th>From</th>
                <th>To</th>
                <th>Recorded by</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((x) => (
                <tr key={x.id}>
                  <td>{dateTime(x.recordedAt)}</td>
                  <td>
                    <StatusBadge>{label(x.movementType)}</StatusBadge>
                  </td>
                  <td>{x.equipment}</td>
                  <td>
                    {x.quantity} {x.symbol}
                  </td>
                  <td>{x.sourceLocation || "—"}</td>
                  <td>{x.destinationLocation || "—"}</td>
                  <td>{x.recordedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
function Attention({ loading, rows }: { loading: boolean; rows: any[] }) {
  if (loading) return <SkeletonCard />;
  return (
    <section className="hq-attention-list">
      {!rows.length ? (
        <EmptyState
          title="Nothing needs attention"
          description="Inventory commitments, custody and returns are currently reconciled."
        />
      ) : (
        rows.map((x) => (
          <SABentoCard key={x.id} className="hq-attention-row">
            <AlertTriangle size={18} />
            <div>
              <StatusBadge
                tone={
                  x.severity === "CRITICAL"
                    ? "danger"
                    : x.severity === "WARNING"
                      ? "warning"
                      : "info"
                }
              >
                {label(x.type)}
              </StatusBadge>
              <h3>{x.title}</h3>
              <p>{x.detail}</p>
            </div>
            <time>{dateTime(x.createdAt)}</time>
          </SABentoCard>
        ))
      )}
    </section>
  );
}
function OperationModal({
  mode,
  onClose,
  value,
  setValue,
  equipment,
  locations,
  productions,
  pending,
  error,
  onSubmit,
}: {
  mode: Workflow | null;
  onClose: () => void;
  value: typeof operationBlank;
  setValue: (v: typeof operationBlank) => void;
  equipment: HqEquipment[];
  locations: Array<{ id: string; name: string }>;
  productions: Production[];
  pending: boolean;
  error: Error | null;
  onSubmit: () => void;
}) {
  const physical =
      mode && !["STOCK", "ADJUSTMENT", "RESERVATION"].includes(mode),
    needsProduction = mode && !["STOCK", "ADJUSTMENT"].includes(mode);
  return (
    <SAModal
      open={!!mode}
      onOpenChange={(v) => !v && onClose()}
      title={mode ? label(mode) : "Operation"}
      description={
        mode === "RESERVATION"
          ? "A reservation commits capacity but does not move equipment."
          : physical
            ? "Confirmation posts immutable movement evidence only after backend validation."
            : mode === "ADJUSTMENT"
              ? "Post a signed correction with a required reason. The original ledger remains intact."
              : "Post opening or received stock as ledger evidence."
      }
    >
      <form
        className="hq-form"
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit();
        }}
      >
        <FormField label="Equipment">
          <select
            required
            value={value.equipmentId}
            onChange={(e) =>
              setValue({ ...value, equipmentId: e.target.value })
            }
          >
            <option value="">Select equipment</option>
            {equipment.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
        </FormField>
        {needsProduction && (
          <FormField label="Production">
            <select
              required
              value={value.productionId}
              onChange={(e) =>
                setValue({ ...value, productionId: e.target.value })
              }
            >
              <option value="">Select production</option>
              {productions.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.title}
                </option>
              ))}
            </select>
          </FormField>
        )}
        {physical && (
          <FormField label="Source location">
            <select
              required
              value={value.sourceLocationId}
              onChange={(e) =>
                setValue({ ...value, sourceLocationId: e.target.value })
              }
            >
              <option value="">Select source</option>
              {locations.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </FormField>
        )}
        {mode !== "RESERVATION" && (
          <FormField
            label={
              mode === "STOCK"
                ? "Stock location"
                : mode === "ADJUSTMENT"
                  ? "Inventory location"
                  : "Destination location"
            }
          >
            <select
              required
              value={value.destinationLocationId}
              onChange={(e) =>
                setValue({ ...value, destinationLocationId: e.target.value })
              }
            >
              <option value="">Select location</option>
              {locations.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
          </FormField>
        )}
        <FormField
          label={
            mode === "RETURN"
              ? "Expected quantity"
              : mode === "ADJUSTMENT"
                ? "Signed difference"
                : "Quantity"
          }
        >
          <input
            required
            type="number"
            min={mode === "ADJUSTMENT" ? undefined : "0.001"}
            step="0.001"
            value={value.quantity}
            onChange={(e) => setValue({ ...value, quantity: e.target.value })}
          />
        </FormField>
        {mode === "RESERVATION" && (
          <div className="hq-form-row">
            <FormField label="Starts">
              <input
                required
                type="datetime-local"
                value={value.startsAt}
                onChange={(e) =>
                  setValue({ ...value, startsAt: e.target.value })
                }
              />
            </FormField>
            <FormField label="Ends">
              <input
                required
                type="datetime-local"
                value={value.endsAt}
                onChange={(e) => setValue({ ...value, endsAt: e.target.value })}
              />
            </FormField>
          </div>
        )}
        {mode === "RETURN" && (
          <div className="hq-dispositions">
            <FormField label="Returned">
              <input
                type="number"
                min="0"
                value={value.returned}
                onChange={(e) =>
                  setValue({ ...value, returned: e.target.value })
                }
              />
            </FormField>
            <FormField label="Damaged">
              <input
                type="number"
                min="0"
                value={value.damaged}
                onChange={(e) =>
                  setValue({ ...value, damaged: e.target.value })
                }
              />
            </FormField>
            <FormField label="Missing">
              <input
                type="number"
                min="0"
                value={value.missing}
                onChange={(e) =>
                  setValue({ ...value, missing: e.target.value })
                }
              />
            </FormField>
            <FormField label="Consumed">
              <input
                type="number"
                min="0"
                value={value.consumed}
                onChange={(e) =>
                  setValue({ ...value, consumed: e.target.value })
                }
              />
            </FormField>
          </div>
        )}
        {mode !== "RESERVATION" && (
          <FormField label="Reason / notes">
            <textarea
              required={mode === "STOCK" || mode === "ADJUSTMENT"}
              value={value.reason}
              onChange={(e) => setValue({ ...value, reason: e.target.value })}
            />
          </FormField>
        )}
        {error && <InlineError error={error} />}
        <SAStatefulButton type="submit" variant="primary" pending={pending}>
          Confirm {mode ? label(mode).toLowerCase() : "operation"}
        </SAStatefulButton>
      </form>
    </SAModal>
  );
}
function InlineError({ error }: { error: Error }) {
  const e = error as ApiError;
  return (
    <div className="hq-inline-error" role="alert">
      <AlertTriangle size={15} />
      <span>{e.message}</span>
    </div>
  );
}
const label = (v: string) =>
  v
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (x) => x.toUpperCase());
const dateTime = (v: string) =>
  new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(v));
