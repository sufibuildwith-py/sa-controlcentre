import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileDown, FilePlus2, Send, X, ArrowUpRight } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../lib/api";
import type { Production } from "../../types/domain";
import { financeApi } from "../finance/finance.api";
import { billingApi, type BillingCreate, type BillingLine } from "./billing.api";
import { EmptyState, MetricCard, SABentoCard, SABentoGrid, SAButton, SkeletonCard, StatusBadge } from "../../components/ui/sa";

const today = () => new Date().toISOString().slice(0, 10);
const blankLine = (): BillingLine => ({ quantity: 1, days: 1, description: "", rate: 0, reference: "" });
const money = (n: number | null | undefined) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(n ?? 0));

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
  const [form, setForm] = useState<BillingCreate>(emptyForm);

  const [searchParams] = useSearchParams();
  const urlProductionId = searchParams.get("productionId");
  const urlCounterpartyId = searchParams.get("counterpartyId") || searchParams.get("partyId");

  const bills = useQuery({ queryKey: ["billing"], queryFn: billingApi.list });
  const parties = useQuery({ queryKey: ["finance", "parties", "billing"], queryFn: () => financeApi.counterparties(0, "") });
  const productions = useQuery({ queryKey: ["finance", "productions", "billing"], queryFn: () => financeApi.productions(0, "") });
  const productionDetail = useQuery({
    queryKey: ["production", urlProductionId],
    queryFn: () => api<Production>(`/productions/${urlProductionId}`),
    enabled: !!urlProductionId && !selected,
  });
  const detail = useQuery({ queryKey: ["billing", selected], queryFn: () => billingApi.get(selected!), enabled: !!selected });

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
            quantity: Number(rl.quantity ?? 1),
            days: Number(rl.days ?? 1),
            description: String(rl.description ?? ""),
            rate: Number(rl.rate ?? 0),
            reference: String(rl.reference ?? ""),
            amount: Number(rl.amount ?? 0),
          };
        }
        return blankLine();
      });

      setForm({
        billNumber: String(b.billNumber ?? b.bill_number ?? ""),
        billDate: String(b.billDate ?? b.bill_date ?? today()),
        financialYear: String(b.financialYear ?? b.financial_year ?? ""),
        counterpartyId: String(b.counterpartyId ?? b.counterparty_id ?? ""),
        productionId: (b.productionId ?? b.production_id ?? null) as string | null,
        eventName: String(b.eventName ?? b.event_name ?? ""),
        venue: String(b.venue ?? ""),
        taxMode: (b.taxMode ?? b.tax_mode ?? "NONE") as BillingCreate["taxMode"],
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
  }, [detail.data, selected]);

  // Prefill fields when navigated with productionId or counterpartyId for a new bill
  useEffect(() => {
    if (!selected) {
      if (urlProductionId) {
        const prodFromList = productions.data?.items.find((p) => p.id === urlProductionId);
        const prod = productionDetail.data || prodFromList;
        const clientName = prod ? ("clientName" in prod ? prod.clientName : null) : null;
        let matchedCounterpartyId: string | null = null;
        if (clientName && parties.data?.items) {
          const found = parties.data.items.find(
            (c) => c.displayName.trim().toLowerCase() === clientName.trim().toLowerCase()
          );
          if (found) matchedCounterpartyId = found.id;
        }

        const partyIdToUse = matchedCounterpartyId || urlCounterpartyId || undefined;
        const party = parties.data?.items.find((p) => p.id === partyIdToUse);
        const gstin = party ? ((party as any)?.gstin ?? "") : "";

        setForm((prev) => ({
          ...prev,
          productionId: urlProductionId,
          eventName: prev.eventName || (prod ? prod.title : ""),
          venue: prev.venue || (productionDetail.data?.venueName ?? ""),
          billDate: prev.billDate && prev.billDate !== today() ? prev.billDate : (prod?.eventDate || today()),
          counterpartyId: prev.counterpartyId || matchedCounterpartyId || (urlCounterpartyId ?? ""),
          gstin: prev.gstin || gstin,
        }));
      } else if (urlCounterpartyId) {
        const party = parties.data?.items.find((p) => p.id === urlCounterpartyId);
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
    productions.data,
    parties.data,
  ]);

  const selectedBill = selected && detail.data ? detail.data.bill : null;
  const currentStatus = selectedBill ? selectedBill.status : null;
  const isDraftOrNew = !selected || currentStatus === "DRAFT";

  const create = useMutation({
    mutationFn: billingApi.create,
    onSuccess: result => {
      client.invalidateQueries({ queryKey: ["billing"] });
      setSelected(result.bill.id);
    },
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: BillingCreate }) => billingApi.update(id, body),
    onSuccess: result => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const issue = useMutation({
    mutationFn: billingApi.issue,
    onSuccess: result => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.invalidateQueries({ queryKey: ["finance"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const cancel = useMutation({
    mutationFn: billingApi.cancel,
    onSuccess: result => {
      client.invalidateQueries({ queryKey: ["billing"] });
      client.setQueryData(["billing", result.bill.id], result);
    },
  });

  const totals = useMemo(() => {
    const subtotal = form.lines.reduce((sum, line) => sum + Number(line.quantity || 0) * Number(line.days || 0) * Number(line.rate || 0), 0);
    const taxable = Math.max(0, subtotal - Number(form.discount || 0));
    const taxBase = taxable + Number(form.freight || 0);
    const tax = taxBase * Number(form.cgstRate || 0) / 100 + taxBase * Number(form.sgstRate || 0) / 100 + taxBase * Number(form.igstRate || 0) / 100;
    const gross = taxBase + tax;
    return { subtotal, tax, gross, balance: gross - Number(form.advancePaid || 0) };
  }, [form]);

  const activeLines = useMemo(() => {
    return form.lines
      .filter(l => l.description.trim().length > 0)
      .map(l => ({
        ...l,
        description: l.description.trim(),
        quantity: Number(l.quantity) > 0 ? Number(l.quantity) : 1,
        days: Number(l.days) > 0 ? Number(l.days) : 1,
        rate: Number(l.rate) >= 0 ? Number(l.rate) : 0,
        reference: l.reference?.trim() || "",
      }));
  }, [form.lines]);

  const canSave = Boolean(form.billNumber.trim() && form.counterpartyId && activeLines.length > 0 && totals.gross > 0);

  function handleNewBill() {
    setSelected(null);
    setForm(emptyForm());
  }

  const updateLine = (index: number, patch: Partial<BillingLine>) => {
    setForm(current => ({ ...current, lines: current.lines.map((line, i) => i === index ? { ...line, ...patch } : line) }));
  };

  const handleSave = () => {
    const payload: BillingCreate = {
      ...form,
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
      const bytes = Uint8Array.from(atob(file.base64), char => char.charCodeAt(0));
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
      const bytes = Uint8Array.from(atob(file.base64), char => char.charCodeAt(0));
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
    return bills.data.filter(b => {
      const num = (b.billNumber ?? b.bill_number ?? "").toLowerCase();
      const cust = (b.customer ?? "").toLowerCase();
      return num.includes(q) || cust.includes(q);
    });
  }, [bills.data, search]);

  if (bills.isPending || parties.isPending || productions.isPending) return <SkeletonCard />;

  const isSaving = create.isPending || update.isPending;
  const currentError = create.error || update.error || issue.error || cancel.error;

  return (
    <div className="billing-page">
      <header className="page-title">
        <div>
          <span className="eyebrow">Commercial workspace</span>
          <h1>Billing</h1>
          <p>Create customer bills, issue receivables, and export editable Excel and PDF documents.</p>
        </div>
        <SAButton variant={selected ? "primary" : "secondary"} onClick={handleNewBill}>
          <FilePlus2 size={15} /> New bill
        </SAButton>
      </header>

      <SABentoGrid>
        <MetricCard label="Bills" value={bills.data?.length ?? 0} detail="Draft and issued documents" />
        <MetricCard label="Drafts" value={bills.data?.filter(b => b.status === "DRAFT").length ?? 0} detail="Not posted to Finance" />
        <MetricCard label="Issued" value={bills.data?.filter(b => b.status !== "DRAFT" && b.status !== "CANCELLED").length ?? 0} detail="Canonical receivables linked" />
        <MetricCard label="Current bill" value={money(totals.gross)} detail={selectedBill ? `Bill #${form.billNumber}` : "Live editor total"} />
      </SABentoGrid>

      <div className="billing-layout">
        <SABentoCard>
          <div className="finance-section-head">
            <div>
              <span className="eyebrow">{selected ? "Bill inspector / editor" : "Bill editor"}</span>
              <h2>{selected ? `Bill #${form.billNumber || "Untitled"}` : "New customer bill"}</h2>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              {currentStatus && (
                <StatusBadge tone={currentStatus === "PAID" ? "success" : currentStatus === "CANCELLED" ? "danger" : currentStatus === "DRAFT" ? "neutral" : "info"}>
                  {currentStatus}
                </StatusBadge>
              )}
              {selected && (
                <SAButton size="sm" onClick={handleNewBill} aria-label="Close bill">
                  <X size={14} /> Close
                </SAButton>
              )}
            </div>
          </div>

          {!isDraftOrNew && (
            <div className="billing-status-banner">
              <div>
                <StatusBadge tone={currentStatus === "PAID" ? "success" : currentStatus === "CANCELLED" ? "danger" : "info"}>
                  {currentStatus}
                </StatusBadge>
                <span>
                  {currentStatus === "CANCELLED"
                    ? "This bill has been cancelled and is read-only."
                    : "This bill has been posted to canonical Finance and is locked against direct edits."}
                </span>
              </div>
              {selectedBill?.canonical_invoice_id && (
                <Link to="/finance?tab=INVOICES" className="sa-button sa-button--secondary sa-button--sm">
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
                onChange={e => setForm({ ...form, billNumber: e.target.value })}
                placeholder="e.g. SA-2026-001"
              />
            </label>
            <label>
              Date *
              <input
                disabled={!isDraftOrNew}
                type="date"
                value={form.billDate}
                onChange={e => setForm({ ...form, billDate: e.target.value })}
              />
            </label>
            <label>
              Financial year *
              <input
                disabled={!isDraftOrNew}
                value={form.financialYear}
                onChange={e => setForm({ ...form, financialYear: e.target.value })}
                placeholder="2026-27"
              />
            </label>
            <label>
              GSTIN
              <input
                disabled={!isDraftOrNew}
                value={form.gstin ?? ""}
                onChange={e => setForm({ ...form, gstin: e.target.value })}
                placeholder="Client GSTIN"
              />
            </label>
            <label className="billing-col-2">
              Customer *
              <select
                disabled={!isDraftOrNew}
                value={form.counterpartyId}
                onChange={e => setForm({ ...form, counterpartyId: e.target.value })}
              >
                <option value="">Select customer</option>
                {parties.data?.items.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.displayName}
                  </option>
                ))}
              </select>
            </label>
            <label className="billing-col-2">
              Production / Job (optional)
              <select
                disabled={!isDraftOrNew}
                value={form.productionId ?? ""}
                onChange={e => setForm({ ...form, productionId: e.target.value || null })}
              >
                <option value="">None (General Client Bill)</option>
                {productions.data?.items.map(p => (
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
                onChange={e => setForm({ ...form, eventName: e.target.value })}
                placeholder="e.g. Annual Gala 2026"
              />
            </label>
            <label className="billing-col-2">
              Venue
              <input
                disabled={!isDraftOrNew}
                value={form.venue ?? ""}
                onChange={e => setForm({ ...form, venue: e.target.value })}
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
              {form.lines.map((line, index) => (
                <div className="billing-line" key={index}>
                  <span className="billing-line-no">{index + 1}</span>
                  <input
                    disabled={!isDraftOrNew}
                    aria-label={"Quantity " + (index + 1)}
                    type="number"
                    min="0.001"
                    step="0.001"
                    value={line.quantity}
                    onChange={e => updateLine(index, { quantity: Number(e.target.value) })}
                  />
                  <input
                    disabled={!isDraftOrNew}
                    aria-label={"Days " + (index + 1)}
                    type="number"
                    min="0.001"
                    step="0.001"
                    value={line.days}
                    onChange={e => updateLine(index, { days: Number(e.target.value) })}
                  />
                  <input
                    disabled={!isDraftOrNew}
                    aria-label={"Description " + (index + 1)}
                    value={line.description}
                    onChange={e => updateLine(index, { description: e.target.value })}
                    placeholder={index === 0 ? "e.g. Sound System & Line Array" : ""}
                  />
                  <input
                    disabled={!isDraftOrNew}
                    aria-label={"Rate " + (index + 1)}
                    type="number"
                    min="0"
                    step="0.01"
                    value={line.rate}
                    onChange={e => updateLine(index, { rate: Number(e.target.value) })}
                  />
                  <input
                    disabled={!isDraftOrNew}
                    aria-label={"Reference " + (index + 1)}
                    value={line.reference}
                    onChange={e => updateLine(index, { reference: e.target.value })}
                  />
                  <strong className="billing-line-amount">
                    {money(Number(line.quantity || 0) * Number(line.days || 0) * Number(line.rate || 0))}
                  </strong>
                </div>
              ))}
            </div>
          </div>

          <div className="billing-form-grid">
            <label>
              GST mode
              <select
                disabled={!isDraftOrNew}
                value={form.taxMode}
                onChange={e => {
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
                disabled={!isDraftOrNew || form.taxMode === "NONE" || form.taxMode === "IGST"}
                type="number"
                min="0"
                step="0.001"
                value={form.cgstRate}
                onChange={e => setForm({ ...form, cgstRate: Number(e.target.value) })}
              />
            </label>
            <label>
              SGST %
              <input
                disabled={!isDraftOrNew || form.taxMode === "NONE" || form.taxMode === "IGST"}
                type="number"
                min="0"
                step="0.001"
                value={form.sgstRate}
                onChange={e => setForm({ ...form, sgstRate: Number(e.target.value) })}
              />
            </label>
            <label>
              IGST %
              <input
                disabled={!isDraftOrNew || form.taxMode === "NONE" || form.taxMode === "CGST_SGST"}
                type="number"
                min="0"
                step="0.001"
                value={form.igstRate}
                onChange={e => setForm({ ...form, igstRate: Number(e.target.value) })}
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
                onChange={e => setForm({ ...form, freight: Number(e.target.value) })}
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
                onChange={e => setForm({ ...form, discount: Number(e.target.value) })}
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
                onChange={e => setForm({ ...form, advancePaid: Number(e.target.value) })}
              />
            </label>
            <label className="billing-col-2">
              Payment terms
              <input
                disabled={!isDraftOrNew}
                value={form.paymentTerms ?? ""}
                onChange={e => setForm({ ...form, paymentTerms: e.target.value })}
                placeholder="e.g. Immediate, Net 15"
              />
            </label>
            <label className="billing-col-2">
              Notes
              <textarea
                disabled={!isDraftOrNew}
                rows={2}
                value={form.notes ?? ""}
                onChange={e => setForm({ ...form, notes: e.target.value })}
                placeholder="Internal or customer notes..."
              />
            </label>
          </div>

          <div className="billing-total-bar">
            <div>
              <span>Subtotal</span>
              <strong>{money(totals.subtotal)}</strong>
            </div>
            <div>
              <span>Tax ({form.taxMode})</span>
              <strong>{money(totals.tax)}</strong>
            </div>
            <div>
              <span>Gross Total</span>
              <strong>{money(totals.gross)}</strong>
            </div>
            <div>
              <span>Balance Due</span>
              <strong>{money(totals.balance)}</strong>
            </div>
          </div>

          <div className="billing-actions-bar">
            {!selected ? (
              <SAButton
                variant="primary"
                disabled={isSaving || !canSave}
                onClick={handleSave}
              >
                <FilePlus2 size={15} />
                {create.isPending ? "Saving..." : "Save Draft"}
              </SAButton>
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
            <p style={{ marginTop: "10px", color: "var(--danger, #c9534b)", fontSize: "13px" }} role="alert">
              {currentError.message}
            </p>
          )}
        </SABentoCard>

        <div className="billing-side">
          <SABentoCard>
            <div className="billing-saved-header">
              <span className="eyebrow">Saved bills</span>
              <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>{bills.data?.length ?? 0} total</span>
            </div>

            <input
              className="billing-search"
              placeholder="Search bill no or customer..."
              aria-label="Search saved bills"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />

            <div className="billing-saved-list">
              {filteredBills.length ? (
                filteredBills.map(b => (
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
                      <strong>{money(Number(b.grossTotal ?? b.gross_total ?? 0))}</strong>
                    </div>
                  </button>
                ))
              ) : (
                <EmptyState
                  title={search.trim() ? "No matching bills" : "No bills yet"}
                  description={search.trim() ? "Try a different search term." : "Save a draft to build customer bills."}
                />
              )}
            </div>
          </SABentoCard>
        </div>
      </div>
    </div>
  );
}
