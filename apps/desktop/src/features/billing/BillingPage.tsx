import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Download,
  FileDown,
  FilePlus2,
  Send,
  X,
  ArrowUpRight,
  Eye,
  Printer,
  Check,
  ChevronDown,
  Plus,
  Search,
} from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../lib/api";
import type { Production } from "../../types/domain";
import { financeApi } from "../finance/finance.api";
import {
  billingApi,
  type BillingCreate,
  type BillingLine,
} from "./billing.api";
import {
  EmptyState,
  MetricCard,
  SABentoCard,
  SABentoGrid,
  SAButton,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";

const today = () => new Date().toISOString().slice(0, 10);
const blankLine = (): BillingLine => ({
  quantity: 1,
  days: 1,
  description: "",
  rate: 0,
  reference: "",
});
const money = (n: number | null | undefined) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(n ?? 0));

const emptyForm = (): BillingCreate => ({
  billNumber: "",
  billDate: today(),
  financialYear: `${new Date().getFullYear()}-${String(new Date().getFullYear() + 1).slice(-2)}`,
  counterpartyId: "",
  productionId: null,
  eventName: "",
  venue: "",
  taxMode: "NONE",
  gstin: "",
  cgstRate: 0,
  sgstRate: 0,
  igstRate: 0,
  discount: 0,
  freight: 0,
  advancePaid: 0,
  notes: "",
  paymentTerms: "",
  lines: Array.from({ length: 17 }, blankLine),
});

export function BillingPage() {
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [exportingXlsx, setExportingXlsx] = useState(false);
  const [exportingPdf, setExportingPdf] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [form, setForm] = useState<BillingCreate>(emptyForm);

  // Customer picker state
  const [customerName, setCustomerName] = useState<string>("");
  const [customerSource, setCustomerSource] = useState<
    "production" | "counterparty" | "custom"
  >("production");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch, setPickerSearch] = useState("");
  const pickerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const [searchParams] = useSearchParams();
  const urlProductionId = searchParams.get("productionId");
  const urlCounterpartyId =
    searchParams.get("counterpartyId") || searchParams.get("partyId");

  const bills = useQuery({ queryKey: ["billing"], queryFn: billingApi.list });
  const parties = useQuery({
    queryKey: ["finance", "parties", "billing"],
    queryFn: () => financeApi.counterparties(0, ""),
  });
  const productions = useQuery({
    queryKey: ["productions", "billing"],
    queryFn: async () => {
      try {
        const canonical = await api<Production[]>("/productions");
        if (Array.isArray(canonical) && canonical.length > 0) {
          return canonical;
        }
      } catch {
        // Fallback
      }
      const fallback = await financeApi.productions(0, "");
      return (fallback?.items || []).map((p) => ({
        id: p.id,
        title: p.title,
        clientName: p.clientName || "",
        eventDate: p.eventDate,
        venueName: "",
      })) as unknown as Production[];
    },
  });
  const productionDetail = useQuery({
    queryKey: ["production", urlProductionId],
    queryFn: () => api<Production>(`/productions/${urlProductionId}`),
    enabled: !!urlProductionId && !selected,
  });
  const detail = useQuery({
    queryKey: ["billing", selected],
    queryFn: () => billingApi.get(selected!),
    enabled: !!selected,
  });

  const productionList = useMemo(() => {
    if (!productions.data) return [];
    if (Array.isArray(productions.data)) return productions.data;
    if (
      "items" in (productions.data as any) &&
      Array.isArray((productions.data as any).items)
    ) {
      return (productions.data as any).items as Production[];
    }
    return [];
  }, [productions.data]);

  const productionClients = useMemo(() => {
    const set = new Set<string>();
    for (const p of productionList) {
      const name = p.clientName?.trim();
      if (name) {
        set.add(name);
      }
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [productionList]);

  const otherCounterpartyNames = useMemo(() => {
    if (!parties.data?.items) return [];
    const prodSet = new Set(productionClients.map((c) => c.toLowerCase()));
    const result: { id: string; displayName: string }[] = [];
    for (const party of parties.data.items) {
      const name = party.displayName?.trim();
      if (name && !prodSet.has(name.toLowerCase())) {
        result.push({ id: party.id, displayName: name });
      }
    }
    return result.sort((a, b) => a.displayName.localeCompare(b.displayName));
  }, [parties.data, productionClients]);

  // Click outside listener for picker
  useEffect(() => {
    if (!pickerOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [pickerOpen]);

  const handleSelectClient = async (
    name: string,
    source: "production" | "counterparty" | "custom",
  ) => {
    const trimmed = name.trim();
    if (!trimmed) return;

    setCustomerName(trimmed);
    setCustomerSource(source);

    const existingParty = parties.data?.items.find(
      (p) => p.displayName.trim().toLowerCase() === trimmed.toLowerCase(),
    );

    if (existingParty) {
      setForm((prev) => ({
        ...prev,
        counterpartyId: existingParty.id,
        gstin: prev.gstin || existingParty.gstin || "",
      }));
    } else {
      try {
        const created = await financeApi.createCounterparty({
          displayName: trimmed,
          role: "CUSTOMER",
        });
        client.invalidateQueries({ queryKey: ["finance", "parties"] });
        setForm((prev) => ({
          ...prev,
          counterpartyId: created.id,
        }));
      } catch {
        // Will be created or resolved on save
      }
    }
  };

  // Synchronize detail data into form when selected bill changes
  useEffect(() => {
    if (detail.data && selected === detail.data.bill.id) {
      const b = detail.data.bill;
      const rawLines = detail.data.lines || [];
      const filledLines: BillingLine[] = Array.from({ length: 17 }, (_, i) => {
        if (i < rawLines.length) {
          const rl = rawLines[i];
          return {
            id: rl.id,
            lineNo: rl.lineNo,
            quantity: Math.max(1, Math.trunc(Number(rl.quantity ?? 1))),
            days: Math.max(1, Math.trunc(Number(rl.days ?? 1))),
            description: String(rl.description ?? ""),
            rate: Number(rl.rate ?? 0),
            reference: String(rl.reference ?? ""),
            amount: Number(rl.amount ?? 0),
          };
        }
        return blankLine();
      });

      const cust = String(b.customer ?? "");
      setCustomerName(cust);
      if (
        productionClients.some((c) => c.toLowerCase() === cust.toLowerCase())
      ) {
        setCustomerSource("production");
      } else {
        setCustomerSource("counterparty");
      }

      setForm({
        billNumber: String(b.billNumber ?? b.bill_number ?? ""),
        billDate: String(b.billDate ?? b.bill_date ?? today()),
        financialYear: String(b.financialYear ?? b.financial_year ?? ""),
        counterpartyId: String(b.counterpartyId ?? b.counterparty_id ?? ""),
        productionId: (b.productionId ?? b.production_id ?? null) as
          string | null,
        eventName: String(b.eventName ?? b.event_name ?? ""),
        venue: String(b.venue ?? ""),
        taxMode: (b.taxMode ??
          b.tax_mode ??
          "NONE") as BillingCreate["taxMode"],
        gstin: String(b.gstin ?? ""),
        cgstRate: Number(b.cgstRate ?? b.cgst_rate ?? 0),
        sgstRate: Number(b.sgstRate ?? b.sgst_rate ?? 0),
        igstRate: Number(b.igstRate ?? b.igst_rate ?? 0),
        discount: Number(b.discount ?? 0),
        freight: Number(b.freight ?? 0),
        advancePaid: Number(b.advancePaid ?? b.advance_paid ?? 0),
        notes: String(b.notes ?? ""),
        paymentTerms: String(b.paymentTerms ?? b.payment_terms ?? ""),
        lines: filledLines,
      });
    }
  }, [detail.data, selected, productionClients]);

  // Prefill fields when navigated with productionId or counterpartyId for a new bill
  useEffect(() => {
    if (!selected) {
      if (urlProductionId) {
        const prodFromList = productionList.find(
          (p) => p.id === urlProductionId,
        );
        const prod = productionDetail.data || prodFromList;
        const clientName = prod?.clientName?.trim() || null;

        if (clientName) {
          handleSelectClient(clientName, "production");
        } else if (urlCounterpartyId) {
          const party = parties.data?.items.find(
            (p) => p.id === urlCounterpartyId,
          );
          if (party) {
            handleSelectClient(party.displayName, "counterparty");
          }
        }

        setForm((prev) => ({
          ...prev,
          productionId: urlProductionId,
          eventName: prev.eventName || (prod ? prod.title : ""),
          venue:
            prev.venue ||
            (productionDetail.data?.venueName ??
              (prod as any)?.venueName ??
              ""),
          billDate:
            prev.billDate && prev.billDate !== today()
              ? prev.billDate
              : prod?.eventDate || today(),
        }));
      } else if (urlCounterpartyId) {
        const party = parties.data?.items.find(
          (p) => p.id === urlCounterpartyId,
        );
        if (party) {
          handleSelectClient(party.displayName, "counterparty");
        }
        setForm((prev) => ({
          ...prev,
          counterpartyId: urlCounterpartyId,
          gstin: prev.gstin || ((party as any)?.gstin ?? ""),
        }));
      }
    }
  }, [
    selected,
    urlProductionId,
    urlCounterpartyId,
    productionDetail.data,
    productionList,
    parties.data,
  ]);

  const handleProductionChange = (prodId: string | null) => {
    const selectedProd = productionList.find((p) => p.id === prodId);
    setForm((prev) => ({
      ...prev,
      productionId: prodId,
      eventName: prev.eventName || (selectedProd ? selectedProd.title : ""),
      venue: prev.venue || (selectedProd?.venueName ?? ""),
      billDate:
        prev.billDate && prev.billDate !== today()
          ? prev.billDate
          : selectedProd?.eventDate || prev.billDate,
    }));

    if (selectedProd?.clientName) {
      const prodClient = selectedProd.clientName.trim();
      if (!customerName.trim() || customerSource === "production") {
        handleSelectClient(prodClient, "production");
      }
    }
  };

  const selectedBill = selected && detail.data ? detail.data.bill : null;
  const currentStatus = selectedBill ? selectedBill.status : null;
  const isDraftOrNew = !selected || currentStatus === "DRAFT";

  const selectedCustomer = useMemo(() => {
    return parties.data?.items.find((p) => p.id === form.counterpartyId);
  }, [parties.data, form.counterpartyId]);

  const create = useMutation({
    mutationFn: billingApi.create,
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing"] });
      setSelected(result.bill.id);
    },
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: BillingCreate }) =>
      billingApi.update(id, body),
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const issue = useMutation({
    mutationFn: billingApi.issue,
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.invalidateQueries({ queryKey: ["finance"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const cancel = useMutation({
    mutationFn: billingApi.cancel,
    onSuccess: (result) => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const totals = useMemo(() => {
    const subtotal = form.lines.reduce(
      (sum, line) =>
        sum +
        Math.max(1, Math.trunc(Number(line.quantity || 1))) *
          Math.max(1, Math.trunc(Number(line.days || 1))) *
          Number(line.rate || 0),
      0,
    );
    const discount = Math.max(0, Number(form.discount || 0));
    const taxable = Math.max(0, subtotal - discount);
    const freight = Math.max(0, Number(form.freight || 0));
    const taxBase = taxable + freight;
    const cgstAmount = (taxBase * Number(form.cgstRate || 0)) / 100;
    const sgstAmount = (taxBase * Number(form.sgstRate || 0)) / 100;
    const igstAmount = (taxBase * Number(form.igstRate || 0)) / 100;
    const tax = cgstAmount + sgstAmount + igstAmount;
    const gross = Math.round(taxBase + tax);
    const advancePaid = Math.max(0, Number(form.advancePaid || 0));
    const balance = gross - advancePaid;
    return {
      subtotal,
      discount,
      taxable,
      freight,
      taxBase,
      cgstAmount,
      sgstAmount,
      igstAmount,
      tax,
      gross,
      advancePaid,
      balance,
    };
  }, [form]);

  const activeLines = useMemo(() => {
    return form.lines
      .filter((l) => l.description.trim().length > 0)
      .map((l) => ({
        ...l,
        description: l.description.trim(),
        quantity: Math.max(1, Math.trunc(Number(l.quantity) || 1)),
        days: Math.max(1, Math.trunc(Number(l.days) || 1)),
        rate: Number(l.rate) >= 0 ? Number(l.rate) : 0,
        reference: l.reference?.trim() || "",
      }));
  }, [form.lines]);

  const canSave = Boolean(
    form.billNumber.trim() &&
    (form.counterpartyId || customerName.trim()) &&
    activeLines.length > 0 &&
    totals.gross > 0,
  );

  function handleNewBill() {
    setSelected(null);
    setForm(emptyForm());
    setCustomerName("");
    setCustomerSource("production");
    setPickerSearch("");
    setPickerOpen(false);
  }

  const updateLine = (index: number, patch: Partial<BillingLine>) => {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line, i) =>
        i === index ? { ...line, ...patch } : line,
      ),
    }));
  };

  const handleSave = async () => {
    let finalCounterpartyId = form.counterpartyId;

    if (!finalCounterpartyId && customerName.trim()) {
      const existing = parties.data?.items.find(
        (p) =>
          p.displayName.trim().toLowerCase() ===
          customerName.trim().toLowerCase(),
      );
      if (existing) {
        finalCounterpartyId = existing.id;
      } else {
        try {
          const created = await financeApi.createCounterparty({
            displayName: customerName.trim(),
            role: "CUSTOMER",
          });
          client.invalidateQueries({ queryKey: ["finance", "parties"] });
          finalCounterpartyId = created.id;
        } catch (err: any) {
          console.error("Failed to create counterparty", err);
          return;
        }
      }
    }

    const payload: BillingCreate = {
      ...form,
      counterpartyId: finalCounterpartyId,
      billNumber: form.billNumber.trim(),
      lines: activeLines,
    };
    if (selected && currentStatus === "DRAFT") {
      update.mutate({ id: selected, body: payload });
    } else {
      create.mutate(payload);
    }
  };

  const handleExportXlsx = async (id: string) => {
    try {
      setExportingXlsx(true);
      const file = await billingApi.export(id);
      const bytes = Uint8Array.from(atob(file.base64), (char) =>
        char.charCodeAt(0),
      );
      const blob = new Blob([bytes], { type: file.contentType });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename;
      anchor.click();
      URL.revokeObjectURL(url);
    } finally {
      setExportingXlsx(false);
    }
  };

  const handleExportPdf = async (id: string) => {
    try {
      setExportingPdf(true);
      const file = await billingApi.exportPdf(id);
      const bytes = Uint8Array.from(atob(file.base64), (char) =>
        char.charCodeAt(0),
      );
      const blob = new Blob([bytes], { type: file.contentType });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename;
      anchor.click();
      URL.revokeObjectURL(url);
    } finally {
      setExportingPdf(false);
    }
  };

  const filteredBills = useMemo(() => {
    if (!bills.data) return [];
    if (!search.trim()) return bills.data;
    const q = search.toLowerCase();
    return bills.data.filter((b) => {
      const num = (b.billNumber ?? b.bill_number ?? "").toLowerCase();
      const cust = (b.customer ?? "").toLowerCase();
      return num.includes(q) || cust.includes(q);
    });
  }, [bills.data, search]);

  const allSelectableOptions = useMemo(() => {
    const map = new Map<string, string>();
    if (parties.data?.items) {
      for (const p of parties.data.items) {
        map.set(p.id, p.displayName);
      }
    }
    if (form.counterpartyId && customerName) {
      map.set(form.counterpartyId, customerName);
    }
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [parties.data, form.counterpartyId, customerName]);

  const handleSelectCounterpartyId = (id: string) => {
    const party = parties.data?.items.find((p) => p.id === id);
    if (party) {
      handleSelectClient(
        party.displayName,
        productionClients.some(
          (c) => c.toLowerCase() === party.displayName.toLowerCase(),
        )
          ? "production"
          : "counterparty",
      );
    } else if (!id) {
      setCustomerName("");
      setForm((prev) => ({ ...prev, counterpartyId: "" }));
    }
  };

  const trimmedQuery = pickerSearch.trim().toLowerCase();

  const filteredProductionClients = useMemo(() => {
    if (!trimmedQuery) return productionClients;
    return productionClients.filter((c) =>
      c.toLowerCase().includes(trimmedQuery),
    );
  }, [productionClients, trimmedQuery]);

  const filteredOtherParties = useMemo(() => {
    if (!trimmedQuery) return otherCounterpartyNames;
    return otherCounterpartyNames.filter((p) =>
      p.displayName.toLowerCase().includes(trimmedQuery),
    );
  }, [otherCounterpartyNames, trimmedQuery]);

  const selectedProduction = productionList.find(
    (p) => p.id === form.productionId,
  );
  const linkedProductionClient =
    selectedProduction?.clientName?.trim() || null;

  const exactMatch =
    productionClients.some((c) => c.toLowerCase() === trimmedQuery) ||
    otherCounterpartyNames.some(
      (p) => p.displayName.toLowerCase() === trimmedQuery,
    );

  if (bills.isPending || parties.isPending || productions.isPending)
    return <SkeletonCard />;

  const isSaving = create.isPending || update.isPending;
  const currentError =
    create.error || update.error || issue.error || cancel.error;

  return (
    <div className="billing-page">
      <header className="page-title">
        <div>
          <span className="eyebrow">Commercial workspace</span>
          <h1>Billing</h1>
          <p>
            Create customer bills, issue receivables, and export editable Excel
            and PDF documents.
          </p>
        </div>
        <SAButton
          variant={selected ? "primary" : "secondary"}
          onClick={handleNewBill}
        >
          <FilePlus2 size={15} /> New bill
        </SAButton>
      </header>

      <SABentoGrid>
        <MetricCard
          label="Bills"
          value={bills.data?.length ?? 0}
          detail="Draft and issued documents"
        />
        <MetricCard
          label="Drafts"
          value={bills.data?.filter((b) => b.status === "DRAFT").length ?? 0}
          detail="Not posted to Finance"
        />
        <MetricCard
          label="Issued"
          value={
            bills.data?.filter(
              (b) => b.status !== "DRAFT" && b.status !== "CANCELLED",
            ).length ?? 0
          }
          detail="Canonical receivables linked"
        />
        <MetricCard
          label="Current bill"
          value={money(totals.gross)}
          detail={
            selectedBill ? `Bill #${form.billNumber}` : "Live editor total"
          }
        />
      </SABentoGrid>

      <div className="billing-layout">
        <SABentoCard>
          <div className="finance-section-head">
            <div>
              <span className="eyebrow">
                {selected ? "Bill inspector / editor" : "Bill editor"}
              </span>
              <h2>
                {selected
                  ? `Bill #${form.billNumber || "Untitled"}`
                  : "New customer bill"}
              </h2>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              {currentStatus && (
                <StatusBadge
                  tone={
                    currentStatus === "PAID"
                      ? "success"
                      : currentStatus === "CANCELLED"
                        ? "danger"
                        : currentStatus === "DRAFT"
                          ? "neutral"
                          : "info"
                  }
                >
                  {currentStatus}
                </StatusBadge>
              )}
              {selected && (
                <SAButton
                  size="sm"
                  onClick={handleNewBill}
                  aria-label="Close bill"
                >
                  <X size={14} /> Close
                </SAButton>
              )}
            </div>
          </div>

          {!isDraftOrNew && (
            <div className="billing-status-banner">
              <div>
                <StatusBadge
                  tone={
                    currentStatus === "PAID"
                      ? "success"
                      : currentStatus === "CANCELLED"
                        ? "danger"
                        : "info"
                  }
                >
                  {currentStatus}
                </StatusBadge>
                <span>
                  {currentStatus === "CANCELLED"
                    ? "This bill has been cancelled and is read-only."
                    : "This bill has been posted to canonical Finance and is locked against direct edits."}
                </span>
              </div>
              {selectedBill?.canonical_invoice_id && (
                <Link
                  to="/finance?tab=INVOICES"
                  className="sa-button sa-button--secondary sa-button--sm"
                >
                  View in Finance <ArrowUpRight size={13} />
                </Link>
              )}
            </div>
          )}

          <div className="billing-form-grid">
            <label>
              Bill no. *
              <input
                disabled={!isDraftOrNew}
                value={form.billNumber}
                onChange={(e) =>
                  setForm({ ...form, billNumber: e.target.value })
                }
                placeholder="e.g. SA-2026-001"
              />
            </label>
            <label>
              Date *
              <input
                disabled={!isDraftOrNew}
                type="date"
                value={form.billDate}
                onChange={(e) => setForm({ ...form, billDate: e.target.value })}
              />
            </label>
            <label>
              Financial year *
              <input
                disabled={!isDraftOrNew}
                value={form.financialYear}
                onChange={(e) =>
                  setForm({ ...form, financialYear: e.target.value })
                }
                placeholder="2026-27"
              />
            </label>
            <label>
              GSTIN
              <input
                disabled={!isDraftOrNew}
                value={form.gstin ?? ""}
                onChange={(e) => setForm({ ...form, gstin: e.target.value })}
                placeholder="Client GSTIN"
              />
            </label>
            <label className="billing-col-2">
              Customer *
              <div className="billing-customer-picker" ref={pickerRef}>
                {/* Accessible select for form integration and tests */}
                <select
                  aria-label="Customer"
                  className="billing-customer-native-select"
                  disabled={!isDraftOrNew}
                  value={form.counterpartyId}
                  onChange={(e) => handleSelectCounterpartyId(e.target.value)}
                  tabIndex={-1}
                >
                  <option value="">Select customer</option>
                  {allSelectableOptions.map((opt) => (
                    <option key={opt.id} value={opt.id}>
                      {opt.name}
                    </option>
                  ))}
                </select>

                {/* Custom modern trigger button */}
                <button
                  type="button"
                  className={`billing-customer-trigger ${pickerOpen ? "active" : ""} ${!isDraftOrNew ? "disabled" : ""}`}
                  onClick={() => {
                    if (isDraftOrNew) setPickerOpen((prev) => !prev);
                  }}
                  disabled={!isDraftOrNew}
                  aria-expanded={pickerOpen}
                  aria-label={
                    customerName
                      ? `Customer: ${customerName}`
                      : "Select client or customer"
                  }
                  data-testid="billing-customer-trigger"
                >
                  <div className="billing-customer-trigger-info">
                    <span
                      className={`billing-customer-name ${!customerName ? "placeholder" : ""}`}
                    >
                      {customerName || "Select client / customer..."}
                    </span>
                    {customerName && (
                      <span
                        className={`billing-customer-badge ${customerSource}`}
                      >
                        {customerSource === "production"
                          ? "Production Client"
                          : customerSource === "custom"
                            ? "Custom Customer"
                            : "Counterparty"}
                      </span>
                    )}
                  </div>
                  <ChevronDown
                    size={14}
                    className={`billing-customer-chevron ${pickerOpen ? "open" : ""}`}
                  />
                </button>

                {/* Searchable Dropdown Popover */}
                {pickerOpen && isDraftOrNew && (
                  <div
                    className="billing-customer-popover"
                    role="dialog"
                    aria-label="Select Customer"
                  >
                    <div className="billing-customer-search-box">
                      <Search
                        size={14}
                        className="billing-customer-search-icon"
                      />
                      <input
                        ref={searchInputRef}
                        type="text"
                        className="billing-customer-search-input"
                        placeholder="Search client or type custom name..."
                        value={pickerSearch}
                        onChange={(e) => setPickerSearch(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && pickerSearch.trim()) {
                            e.preventDefault();
                            handleSelectClient(pickerSearch.trim(), "custom");
                            setPickerOpen(false);
                            setPickerSearch("");
                          } else if (e.key === "Escape") {
                            setPickerOpen(false);
                          }
                        }}
                        autoFocus
                      />
                      {pickerSearch && (
                        <button
                          type="button"
                          className="billing-customer-search-clear"
                          onClick={() => setPickerSearch("")}
                          aria-label="Clear search"
                        >
                          <X size={12} />
                        </button>
                      )}
                    </div>

                    <div className="billing-customer-list" role="listbox">
                      {/* Action to use search query as custom customer */}
                      {pickerSearch.trim() && !exactMatch && (
                        <div className="billing-customer-section">
                          <button
                            type="button"
                            className="billing-customer-option custom-create"
                            onClick={() => {
                              handleSelectClient(pickerSearch.trim(), "custom");
                              setPickerOpen(false);
                              setPickerSearch("");
                            }}
                          >
                            <div className="billing-customer-option-content">
                              <span className="billing-customer-option-title">
                                + Use <strong>"{pickerSearch.trim()}"</strong>
                              </span>
                              <span className="billing-customer-badge custom">
                                Custom Customer
                              </span>
                            </div>
                          </button>
                        </div>
                      )}

                      {/* Linked Production Client */}
                      {linkedProductionClient && (
                        <div className="billing-customer-section">
                          <div className="billing-customer-section-header">
                            <span>Selected Production Client</span>
                          </div>
                          <button
                            type="button"
                            className={`billing-customer-option ${customerName.toLowerCase() === linkedProductionClient.toLowerCase() ? "selected" : ""}`}
                            onClick={() => {
                              handleSelectClient(
                                linkedProductionClient,
                                "production",
                              );
                              setPickerOpen(false);
                              setPickerSearch("");
                            }}
                          >
                            <div className="billing-customer-option-content">
                              <span className="billing-customer-option-title">
                                {linkedProductionClient}
                              </span>
                              <span className="billing-customer-badge linked">
                                Linked Production
                              </span>
                            </div>
                            {customerName.toLowerCase() ===
                              linkedProductionClient.toLowerCase() && (
                              <Check
                                size={14}
                                className="billing-customer-option-check"
                              />
                            )}
                          </button>
                        </div>
                      )}

                      {/* Production Clients */}
                      <div className="billing-customer-section">
                        <div className="billing-customer-section-header">
                          <span>
                            Production Clients (
                            {filteredProductionClients.length})
                          </span>
                        </div>
                        {filteredProductionClients.length > 0 ? (
                          filteredProductionClients.map((client) => {
                            const isSelected =
                              customerName.toLowerCase() ===
                              client.toLowerCase();
                            return (
                              <button
                                key={client}
                                type="button"
                                className={`billing-customer-option ${isSelected ? "selected" : ""}`}
                                onClick={() => {
                                  handleSelectClient(client, "production");
                                  setPickerOpen(false);
                                  setPickerSearch("");
                                }}
                              >
                                <div className="billing-customer-option-content">
                                  <span className="billing-customer-option-title">
                                    {client}
                                  </span>
                                  <span className="billing-customer-badge production">
                                    Production
                                  </span>
                                </div>
                                {isSelected && (
                                  <Check
                                    size={14}
                                    className="billing-customer-option-check"
                                  />
                                )}
                              </button>
                            );
                          })
                        ) : (
                          <div className="billing-customer-empty-text">
                            No matching production clients
                          </div>
                        )}
                      </div>

                      {/* Other Counterparties */}
                      {filteredOtherParties.length > 0 && (
                        <div className="billing-customer-section">
                          <div className="billing-customer-section-header">
                            <span>Other Counterparties</span>
                          </div>
                          {filteredOtherParties.map((party) => {
                            const isSelected =
                              customerName.toLowerCase() ===
                              party.displayName.toLowerCase();
                            return (
                              <button
                                key={party.id}
                                type="button"
                                className={`billing-customer-option ${isSelected ? "selected" : ""}`}
                                onClick={() => {
                                  handleSelectClient(
                                    party.displayName,
                                    "counterparty",
                                  );
                                  setPickerOpen(false);
                                  setPickerSearch("");
                                }}
                              >
                                <div className="billing-customer-option-content">
                                  <span className="billing-customer-option-title">
                                    {party.displayName}
                                  </span>
                                  <span className="billing-customer-badge counterparty">
                                    Counterparty
                                  </span>
                                </div>
                                {isSelected && (
                                  <Check
                                    size={14}
                                    className="billing-customer-option-check"
                                  />
                                )}
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    <div className="billing-customer-footer">
                      <button
                        type="button"
                        className="billing-customer-footer-btn"
                        onClick={() => {
                          if (pickerSearch.trim()) {
                            handleSelectClient(pickerSearch.trim(), "custom");
                            setPickerOpen(false);
                            setPickerSearch("");
                          } else {
                            searchInputRef.current?.focus();
                          }
                        }}
                      >
                        <Plus size={13} /> Enter custom customer name
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </label>
            <label className="billing-col-2">
              Production / Job (optional)
              <select
                disabled={!isDraftOrNew}
                value={form.productionId ?? ""}
                onChange={(e) =>
                  handleProductionChange(e.target.value || null)
                }
              >
                <option value="">None (General Client Bill)</option>
                {productionList.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.title}
                  </option>
                ))}
              </select>
            </label>
            <label className="billing-col-2">
              Event / Project
              <input
                disabled={!isDraftOrNew}
                value={form.eventName ?? ""}
                onChange={(e) =>
                  setForm({ ...form, eventName: e.target.value })
                }
                placeholder="e.g. Annual Gala 2026"
              />
            </label>
            <label className="billing-col-2">
              Venue
              <input
                disabled={!isDraftOrNew}
                value={form.venue ?? ""}
                onChange={(e) => setForm({ ...form, venue: e.target.value })}
                placeholder="Venue location"
              />
            </label>
          </div>

          <div className="billing-lines-container">
            <div className="billing-lines">
              <div className="billing-line billing-line--header">
                <span>SR</span>
                <span>QTY</span>
                <span>DAYS</span>
                <span>PRODUCT / DESCRIPTION</span>
                <span>RATE (₹)</span>
                <span>REF</span>
                <span>AMOUNT (₹)</span>
              </div>
              {form.lines.map((line, index) => {
                const isPopulated = Boolean(line.description.trim() || Number(line.rate) > 0);
                return (
                  <div
                    className={`billing-line ${isPopulated ? "billing-line--populated" : ""}`}
                    key={index}
                  >
                    <span className="billing-line-no">{index + 1}</span>
                    <input
                      disabled={!isDraftOrNew}
                      aria-label={"Quantity " + (index + 1)}
                      className="billing-line-qty"
                      type="number"
                      min="1"
                      step="1"
                      value={line.quantity || ""}
                      onChange={(e) => {
                        const raw = e.target.value;
                        const val = raw === "" ? 0 : Math.max(1, Math.trunc(Number(raw)));
                        updateLine(index, { quantity: val });
                      }}
                    />
                    <input
                      disabled={!isDraftOrNew}
                      aria-label={"Days " + (index + 1)}
                      className="billing-line-days"
                      type="number"
                      min="1"
                      step="1"
                      value={line.days || ""}
                      onChange={(e) => {
                        const raw = e.target.value;
                        const val = raw === "" ? 0 : Math.max(1, Math.trunc(Number(raw)));
                        updateLine(index, { days: val });
                      }}
                    />
                    <input
                      disabled={!isDraftOrNew}
                      aria-label={"Description " + (index + 1)}
                      value={line.description}
                      onChange={(e) =>
                        updateLine(index, { description: e.target.value })
                      }
                      placeholder={
                        index === 0 ? "e.g. Sound System & Line Array" : ""
                      }
                    />
                    <input
                      disabled={!isDraftOrNew}
                      aria-label={"Rate " + (index + 1)}
                      className="billing-line-rate"
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.rate}
                      onChange={(e) =>
                        updateLine(index, { rate: Number(e.target.value) })
                      }
                    />
                    <input
                      disabled={!isDraftOrNew}
                      aria-label={"Reference " + (index + 1)}
                      value={line.reference}
                      onChange={(e) =>
                        updateLine(index, { reference: e.target.value })
                      }
                    />
                    <strong className="billing-line-amount">
                      {money(
                        Math.max(1, Math.trunc(Number(line.quantity || 1))) *
                          Math.max(1, Math.trunc(Number(line.days || 1))) *
                          Number(line.rate || 0),
                      )}
                    </strong>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="billing-settlement-row">
            <div className="billing-settlement-controls">
              <h4>Settlement & Tax Configuration</h4>
              <div className="billing-form-grid" style={{ marginBottom: 0 }}>
                <label>
                  GST mode
                  <select
                    disabled={!isDraftOrNew}
                    value={form.taxMode}
                    onChange={(e) => {
                      const taxMode = e.target.value as BillingCreate["taxMode"];
                      setForm({
                        ...form,
                        taxMode,
                        cgstRate: taxMode === "CGST_SGST" ? 9 : 0,
                        sgstRate: taxMode === "CGST_SGST" ? 9 : 0,
                        igstRate: taxMode === "IGST" ? 18 : 0,
                      });
                    }}
                  >
                    <option value="NONE">None</option>
                    <option value="CGST_SGST">CGST + SGST (9% + 9%)</option>
                    <option value="IGST">IGST (18%)</option>
                    <option value="CUSTOM">Custom</option>
                  </select>
                </label>
                <label>
                  CGST %
                  <input
                    disabled={
                      !isDraftOrNew ||
                      form.taxMode === "NONE" ||
                      form.taxMode === "IGST"
                    }
                    type="number"
                    min="0"
                    step="0.001"
                    value={form.cgstRate}
                    onChange={(e) =>
                      setForm({ ...form, cgstRate: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  SGST %
                  <input
                    disabled={
                      !isDraftOrNew ||
                      form.taxMode === "NONE" ||
                      form.taxMode === "IGST"
                    }
                    type="number"
                    min="0"
                    step="0.001"
                    value={form.sgstRate}
                    onChange={(e) =>
                      setForm({ ...form, sgstRate: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  IGST %
                  <input
                    disabled={
                      !isDraftOrNew ||
                      form.taxMode === "NONE" ||
                      form.taxMode === "CGST_SGST"
                    }
                    type="number"
                    min="0"
                    step="0.001"
                    value={form.igstRate}
                    onChange={(e) =>
                      setForm({ ...form, igstRate: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  Freight / Transport
                  <input
                    disabled={!isDraftOrNew}
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.freight}
                    onChange={(e) =>
                      setForm({ ...form, freight: Number(e.target.value) })
                    }
                  />
                </label>
                <label>
                  Discount
                  <input
                    disabled={!isDraftOrNew}
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.discount}
                    onChange={(e) =>
                      setForm({ ...form, discount: Number(e.target.value) })
                    }
                  />
                </label>
                <label className="billing-col-2">
                  Advance / Already received
                  <input
                    disabled={!isDraftOrNew}
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.advancePaid}
                    onChange={(e) =>
                      setForm({ ...form, advancePaid: Number(e.target.value) })
                    }
                  />
                </label>
                <label className="billing-col-2">
                  Payment terms
                  <input
                    disabled={!isDraftOrNew}
                    value={form.paymentTerms ?? ""}
                    onChange={(e) =>
                      setForm({ ...form, paymentTerms: e.target.value })
                    }
                    placeholder="e.g. Immediate, Net 15"
                  />
                </label>
                <label className="billing-col-2">
                  Notes
                  <textarea
                    disabled={!isDraftOrNew}
                    rows={2}
                    value={form.notes ?? ""}
                    onChange={(e) => setForm({ ...form, notes: e.target.value })}
                    placeholder="Internal or customer notes..."
                  />
                </label>
              </div>
            </div>

            <div className="billing-summary-panel">
              <span className="billing-summary-title">Commercial Summary</span>
              <div className="billing-summary-row">
                <span>Subtotal</span>
                <span className="val">{money(totals.subtotal)}</span>
              </div>
              {totals.discount > 0 && (
                <div className="billing-summary-row">
                  <span>Less: Discount</span>
                  <span className="val" style={{ color: "var(--danger, #c9534b)" }}>
                    -{money(totals.discount)}
                  </span>
                </div>
              )}
              {totals.freight > 0 && (
                <div className="billing-summary-row">
                  <span>Add: Freight / Transport</span>
                  <span className="val">+{money(totals.freight)}</span>
                </div>
              )}
              {totals.tax > 0 && (
                <div className="billing-summary-row">
                  <span>
                    Tax ({form.taxMode})
                  </span>
                  <span className="val">{money(totals.tax)}</span>
                </div>
              )}
              <div className="billing-summary-divider" />
              <div className="billing-summary-row prominent">
                <span>Gross Total</span>
                <span className="val">{money(totals.gross)}</span>
              </div>
              {totals.advancePaid > 0 && (
                <div className="billing-summary-row">
                  <span>Less: Advance Received</span>
                  <span className="val" style={{ color: "var(--success, #2e9f65)" }}>
                    -{money(totals.advancePaid)}
                  </span>
                </div>
              )}
              <div className="billing-net-due-box">
                <span className="label">Balance Due</span>
                <span className="amount">{money(totals.balance)}</span>
              </div>
            </div>
          </div>

          <div className="billing-actions-bar">
            {!selected ? (
              <>
                <SAButton
                  variant="primary"
                  disabled={isSaving || !canSave}
                  onClick={handleSave}
                >
                  <FilePlus2 size={15} />
                  {create.isPending ? "Saving..." : "Save Draft"}
                </SAButton>
                <SAButton
                  onClick={() => setShowPreview(true)}
                  disabled={activeLines.length === 0}
                >
                  <Eye size={15} /> Preview bill
                </SAButton>
              </>
            ) : currentStatus === "DRAFT" ? (
              <>
                <SAButton
                  variant="primary"
                  disabled={isSaving || !canSave}
                  onClick={handleSave}
                >
                  <FilePlus2 size={15} />
                  {update.isPending ? "Saving..." : "Save Draft"}
                </SAButton>
                <SAButton onClick={() => setShowPreview(true)}>
                  <Eye size={15} /> Preview bill
                </SAButton>
                <SAButton
                  onClick={() => void handleExportXlsx(selected)}
                  disabled={exportingXlsx}
                >
                  <Download size={15} />
                  {exportingXlsx ? "Exporting..." : "Export XLSX"}
                </SAButton>
                <SAButton
                  onClick={() => void handleExportPdf(selected)}
                  disabled={exportingPdf}
                >
                  <FileDown size={15} />
                  {exportingPdf ? "Exporting..." : "Export PDF"}
                </SAButton>
                <SAButton
                  variant="primary"
                  disabled={issue.isPending}
                  onClick={() => issue.mutate(selected)}
                >
                  <Send size={15} />
                  {issue.isPending ? "Issuing..." : "Issue Bill"}
                </SAButton>
                <SAButton
                  variant="danger"
                  disabled={cancel.isPending}
                  onClick={() => cancel.mutate(selected)}
                >
                  <X size={15} />
                  {cancel.isPending ? "Cancelling..." : "Cancel"}
                </SAButton>
              </>
            ) : (
              <>
                <SAButton onClick={() => setShowPreview(true)}>
                  <Eye size={15} /> Preview bill
                </SAButton>
                <SAButton
                  onClick={() => void handleExportXlsx(selected)}
                  disabled={exportingXlsx}
                >
                  <Download size={15} />
                  {exportingXlsx ? "Exporting..." : "Export XLSX"}
                </SAButton>
                <SAButton
                  onClick={() => void handleExportPdf(selected)}
                  disabled={exportingPdf}
                >
                  <FileDown size={15} />
                  {exportingPdf ? "Exporting..." : "Export PDF"}
                </SAButton>
              </>
            )}
          </div>

          {currentError && (
            <p
              style={{
                marginTop: "10px",
                color: "var(--danger, #c9534b)",
                fontSize: "13px",
              }}
              role="alert"
            >
              {currentError.message}
            </p>
          )}
        </SABentoCard>

        <div className="billing-side">
          <SABentoCard>
            <div className="billing-saved-header">
              <span className="eyebrow">Saved bills</span>
              <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                {bills.data?.length ?? 0} total
              </span>
            </div>

            <input
              className="billing-search"
              placeholder="Search bill no or customer..."
              aria-label="Search saved bills"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />

            <div className="billing-saved-list">
              {filteredBills.length ? (
                filteredBills.map((b) => (
                  <button
                    className={`billing-list-item ${b.id === selected ? "active" : ""}`}
                    key={b.id}
                    onClick={() => setSelected(b.id)}
                  >
                    <div className="billing-list-item-top">
                      <strong>{b.billNumber ?? b.bill_number}</strong>
                      <StatusBadge
                        tone={
                          b.status === "PAID"
                            ? "success"
                            : b.status === "CANCELLED"
                              ? "danger"
                              : b.status === "DRAFT"
                                ? "neutral"
                                : "info"
                        }
                      >
                        {b.status}
                      </StatusBadge>
                    </div>
                    <div className="billing-list-item-bottom">
                      <span>{b.customer}</span>
                      <strong>
                        {money(Number(b.grossTotal ?? b.gross_total ?? 0))}
                      </strong>
                    </div>
                  </button>
                ))
              ) : (
                <EmptyState
                  title={search.trim() ? "No matching bills" : "No bills yet"}
                  description={
                    search.trim()
                      ? "Try a different search term."
                      : "Save a draft to build customer bills."
                  }
                />
              )}
            </div>
          </SABentoCard>
        </div>
      </div>

      {showPreview && (
        <div
          className="billing-preview-backdrop"
          onClick={() => setShowPreview(false)}
        >
          <div
            className="billing-preview-card"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="Commercial Bill Document Preview"
          >
            <div className="billing-preview-card-header">
              <div>
                <h3>Commercial Document Preview</h3>
                <span style={{ fontSize: "11.5px", color: "var(--text-3)" }}>
                  Authentic SA Production A4 Commercial Invoice Layout
                </span>
              </div>
              <div className="billing-preview-actions">
                <SAButton size="sm" onClick={() => window.print()}>
                  <Printer size={14} /> Print
                </SAButton>
                {selected && (
                  <SAButton
                    size="sm"
                    onClick={() => void handleExportPdf(selected)}
                    disabled={exportingPdf}
                  >
                    <FileDown size={14} /> {exportingPdf ? "Exporting..." : "Download PDF"}
                  </SAButton>
                )}
                <SAButton
                  size="sm"
                  onClick={() => setShowPreview(false)}
                  aria-label="Close preview"
                >
                  <X size={14} /> Close
                </SAButton>
              </div>
            </div>

            <div className="billing-preview-document">
              <div className="billing-doc-header">
                <div className="billing-doc-brand">
                  <h2>SA PRODUCTION</h2>
                  <p className="sub">Executive Live Events & Technical Production</p>
                  <p className="desc">Sound · Intelligent Lighting · High-Resolution LED · Trussing · Power Systems</p>
                </div>
                <div className="billing-doc-meta">
                  <strong className="bill-no">INVOICE #{form.billNumber || "DRAFT"}</strong>
                  <span>Date: {form.billDate}</span>
                  <span>FY: {form.financialYear}</span>
                  <span>Status: {currentStatus || "DRAFT"}</span>
                </div>
              </div>

              <div className="billing-doc-divider" />

              <div className="billing-doc-info-grid">
                <div className="billing-doc-info-col">
                  <span className="label">BILLED TO</span>
                  <strong className="val">{selectedCustomer?.displayName || customerName || selectedBill?.customer || "Client"}</strong>
                  {form.gstin && <p className="detail">GSTIN: {form.gstin}</p>}
                  {form.paymentTerms && <p className="detail">Payment Terms: {form.paymentTerms}</p>}
                </div>
                <div className="billing-doc-info-col">
                  <span className="label">PRODUCTION & VENUE</span>
                  <strong className="val">{form.eventName || "Live Production & Event"}</strong>
                  {form.venue && <p className="detail">Venue: {form.venue}</p>}
                  {form.taxMode !== "NONE" && <p className="detail">Tax Mode: {form.taxMode}</p>}
                </div>
              </div>

              <table className="billing-doc-table">
                <thead>
                  <tr>
                    <th style={{ width: "36px", textAlign: "center" }}>#</th>
                    <th>DESCRIPTION</th>
                    <th style={{ width: "80px" }}>REF</th>
                    <th style={{ width: "50px", textAlign: "center" }}>QTY</th>
                    <th style={{ width: "50px", textAlign: "center" }}>DAYS</th>
                    <th style={{ width: "95px", textAlign: "right" }}>RATE</th>
                    <th style={{ width: "105px", textAlign: "right" }}>TOTAL</th>
                  </tr>
                </thead>
                <tbody>
                  {activeLines.length > 0 ? (
                    activeLines.map((line, idx) => (
                      <tr key={idx}>
                        <td style={{ textAlign: "center", color: "#878f9b" }}>{idx + 1}</td>
                        <td style={{ fontWeight: 600 }}>{line.description}</td>
                        <td style={{ color: "#555d69" }}>{line.reference || "—"}</td>
                        <td style={{ textAlign: "center" }}>{line.quantity}</td>
                        <td style={{ textAlign: "center" }}>{line.days}</td>
                        <td style={{ textAlign: "right" }}>{money(line.rate)}</td>
                        <td style={{ textAlign: "right", fontWeight: 700 }}>
                          {money(line.quantity * line.days * line.rate)}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={7} style={{ textAlign: "center", padding: "20px", color: "#878f9b" }}>
                        No billable line items populated yet.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>

              <div className="billing-doc-bottom">
                <div>
                  {form.notes ? (
                    <div className="billing-doc-notes-box">
                      <span className="label">Notes & Instructions</span>
                      <p>{form.notes}</p>
                    </div>
                  ) : (
                    <div className="billing-doc-notes-box">
                      <span className="label">Commercial Certification</span>
                      <p>
                        Authentic commercial invoice issued by SA Production. This document represents complete delivery of technical services.
                      </p>
                    </div>
                  )}
                </div>

                <div className="billing-doc-totals">
                  <div className="billing-doc-totals-row">
                    <span>Subtotal</span>
                    <span className="val">{money(totals.subtotal)}</span>
                  </div>
                  {totals.discount > 0 && (
                    <div className="billing-doc-totals-row">
                      <span>Less: Discount</span>
                      <span className="val" style={{ color: "#c9534b" }}>-{money(totals.discount)}</span>
                    </div>
                  )}
                  {totals.freight > 0 && (
                    <div className="billing-doc-totals-row">
                      <span>Freight & Logistics</span>
                      <span className="val">+{money(totals.freight)}</span>
                    </div>
                  )}
                  {totals.tax > 0 && (
                    <div className="billing-doc-totals-row">
                      <span>GST ({form.taxMode === "CGST_SGST" ? `${form.cgstRate + form.sgstRate}%` : `${form.igstRate}%`})</span>
                      <span className="val">{money(totals.tax)}</span>
                    </div>
                  )}
                  <div className="billing-doc-totals-row gross">
                    <span>Gross Total</span>
                    <span className="val">{money(totals.gross)}</span>
                  </div>
                  {totals.advancePaid > 0 && (
                    <div className="billing-doc-totals-row">
                      <span>Less: Advance Received</span>
                      <span className="val" style={{ color: "#2e9f65" }}>-{money(totals.advancePaid)}</span>
                    </div>
                  )}
                  <div className="billing-doc-net-due">
                    <span className="label">Net Balance Due</span>
                    <span className="amount">{money(totals.balance)}</span>
                  </div>
                </div>
              </div>

              <div className="billing-doc-footer">
                SA PRODUCTION · TECHNICAL OPERATIONS & COMMERCIAL DESK · INQUIRIES@SAPRODUCTION.COM
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
