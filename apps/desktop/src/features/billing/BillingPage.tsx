import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FilePlus2, Send, X } from "lucide-react";
import { financeApi } from "../finance/finance.api";
import { billingApi, type BillingCreate, type BillingLine } from "./billing.api";
import { EmptyState, MetricCard, SABentoCard, SABentoGrid, SAButton, SkeletonCard, StatusBadge } from "../../components/ui/sa";

const today = () => new Date().toISOString().slice(0, 10);
const blankLine = (): BillingLine => ({ quantity: 1, days: 1, description: "", rate: 0, reference: "" });
const money = (n: number) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(n);

export function BillingPage() {
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [form, setForm] = useState<BillingCreate>({
    billNumber: "", billDate: today(), financialYear: `${new Date().getFullYear()}-${String(new Date().getFullYear() + 1).slice(-2)}`,
    counterpartyId: "", productionId: null, eventName: "", venue: "", taxMode: "NONE", gstin: "",
    cgstRate: 0, sgstRate: 0, igstRate: 0, discount: 0, freight: 0, advancePaid: 0,
    notes: "", paymentTerms: "", lines: Array.from({ length: 17 }, blankLine),
  });
  const bills = useQuery({ queryKey: ["billing"], queryFn: billingApi.list });
  const parties = useQuery({ queryKey: ["finance", "parties", "billing"], queryFn: () => financeApi.counterparties(0, "") });
  const productions = useQuery({ queryKey: ["finance", "productions", "billing"], queryFn: () => financeApi.productions(0, "") });
  const detail = useQuery({ queryKey: ["billing", selected], queryFn: () => billingApi.get(selected!), enabled: !!selected });
  const create = useMutation({ mutationFn: billingApi.create, onSuccess: result => { client.invalidateQueries({ queryKey: ["billing"] }); setSelected(result.bill.id); reset(); } });
  const issue = useMutation({ mutationFn: billingApi.issue, onSuccess: result => { client.invalidateQueries({ queryKey: ["billing"] }); client.setQueryData(["billing", result.bill.id], result); setSelected(result.bill.id); } });
  const cancel = useMutation({ mutationFn: billingApi.cancel, onSuccess: result => { client.invalidateQueries({ queryKey: ["billing"] }); client.setQueryData(["billing", result.bill.id], result); } });

  const totals = useMemo(() => {
    const subtotal = form.lines.reduce((sum, line) => sum + Number(line.quantity || 0) * Number(line.days || 0) * Number(line.rate || 0), 0);
    const taxable = Math.max(0, subtotal - Number(form.discount || 0));
    const taxBase = taxable + Number(form.freight || 0);
    const tax = taxBase * Number(form.cgstRate || 0) / 100 + taxBase * Number(form.sgstRate || 0) / 100 + taxBase * Number(form.igstRate || 0) / 100;
    const gross = taxBase + tax;
    return { subtotal, tax, gross, balance: gross - Number(form.advancePaid || 0) };
  }, [form]);

  function reset() {
    setForm(current => ({ ...current, billNumber: "", counterpartyId: "", productionId: null, eventName: "", venue: "", gstin: "", discount: 0, freight: 0, advancePaid: 0, notes: "", paymentTerms: "", lines: Array.from({ length: 17 }, blankLine) }));
  }
  const updateLine = (index: number, patch: Partial<BillingLine>) => setForm(current => ({ ...current, lines: current.lines.map((line, i) => i === index ? { ...line, ...patch } : line) }));

  const exportXlsx = async (id: string) => {
    const file = await billingApi.export(id);
    const bytes = Uint8Array.from(atob(file.base64), char => char.charCodeAt(0));
    const blob = new Blob([bytes], { type: file.contentType });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url; anchor.download = file.filename; anchor.click(); URL.revokeObjectURL(url);
  };

  if (bills.isPending || parties.isPending || productions.isPending) return <SkeletonCard />;

  return (
    <div className="billing-page">
      <header className="page-title">
        <div><span className="eyebrow">Commercial workspace</span><h1>Billing</h1><p>Create customer bills, issue receivables and export editable Excel workbooks.</p></div>
        <SAButton onClick={reset}><FilePlus2 size={15} /> New bill</SAButton>
      </header>
      <SABentoGrid>
        <MetricCard label="Bills" value={bills.data?.length ?? 0} detail="Draft and issued documents" />
        <MetricCard label="Drafts" value={bills.data?.filter(b => b.status === "DRAFT").length ?? 0} detail="Not posted to Finance" />
        <MetricCard label="Issued" value={bills.data?.filter(b => b.status !== "DRAFT" && b.status !== "CANCELLED").length ?? 0} detail="Canonical receivables linked" />
        <MetricCard label="Current bill" value={money(totals.gross)} detail="Live editor total" />
      </SABentoGrid>

      <div className="billing-layout">
        <SABentoCard>
          <div className="finance-section-head"><div><span className="eyebrow">Bill editor</span><h2>New customer bill</h2></div><span className="muted">17 editable line slots</span></div>
          <div className="billing-form-grid">
            <label>Bill no.<input value={form.billNumber} onChange={e => setForm({ ...form, billNumber: e.target.value })} /></label>
            <label>Date<input type="date" value={form.billDate} onChange={e => setForm({ ...form, billDate: e.target.value })} /></label>
            <label>Financial year<input value={form.financialYear} onChange={e => setForm({ ...form, financialYear: e.target.value })} /></label>
            <label>Customer<select value={form.counterpartyId} onChange={e => setForm({ ...form, counterpartyId: e.target.value })}><option value="">Select customer</option>{parties.data?.items.map(p => <option key={p.id} value={p.id}>{p.displayName}</option>)}</select></label>
            <label>Production<select value={form.productionId ?? ""} onChange={e => setForm({ ...form, productionId: e.target.value || null })}><option value="">None</option>{productions.data?.items.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}</select></label>
            <label>Event / Project<input value={form.eventName ?? ""} onChange={e => setForm({ ...form, eventName: e.target.value })} /></label>
            <label>Venue<input value={form.venue ?? ""} onChange={e => setForm({ ...form, venue: e.target.value })} /></label>
            <label>GSTIN<input value={form.gstin ?? ""} onChange={e => setForm({ ...form, gstin: e.target.value })} /></label>
          </div>

          <div className="billing-lines">
            <div className="billing-line billing-line--header"><span>SR</span><span>QTY</span><span>DAYS</span><span>DESCRIPTION</span><span>RATE</span><span>REF</span><span>AMOUNT</span></div>
            {form.lines.map((line, index) => (
              <div className="billing-line" key={index}>
                <span>{index + 1}</span>
                <input aria-label={"Quantity " + (index + 1)} type="number" min="0.001" step="0.001" value={line.quantity} onChange={e => updateLine(index, { quantity: Number(e.target.value) })} />
                <input aria-label={"Days " + (index + 1)} type="number" min="0.001" step="0.001" value={line.days} onChange={e => updateLine(index, { days: Number(e.target.value) })} />
                <input aria-label={"Description " + (index + 1)} value={line.description} onChange={e => updateLine(index, { description: e.target.value })} placeholder="Service / product" />
                <input aria-label={"Rate " + (index + 1)} type="number" min="0" step="0.01" value={line.rate} onChange={e => updateLine(index, { rate: Number(e.target.value) })} />
                <input aria-label={"Reference " + (index + 1)} value={line.reference} onChange={e => updateLine(index, { reference: e.target.value })} />
                <strong>{money(Number(line.quantity || 0) * Number(line.days || 0) * Number(line.rate || 0))}</strong>
              </div>
            ))}
          </div>

          <div className="billing-form-grid">
            <label>GST mode<select value={form.taxMode} onChange={e => { const taxMode = e.target.value as BillingCreate["taxMode"]; setForm({ ...form, taxMode, cgstRate: taxMode === "CGST_SGST" ? 9 : 0, sgstRate: taxMode === "CGST_SGST" ? 9 : 0, igstRate: taxMode === "IGST" ? 18 : 0 }); }}><option value="NONE">None</option><option value="CGST_SGST">CGST + SGST</option><option value="IGST">IGST</option><option value="CUSTOM">Custom</option></select></label>
            <label>CGST %<input type="number" min="0" step="0.001" value={form.cgstRate} onChange={e => setForm({ ...form, cgstRate: Number(e.target.value) })} /></label>
            <label>SGST %<input type="number" min="0" step="0.001" value={form.sgstRate} onChange={e => setForm({ ...form, sgstRate: Number(e.target.value) })} /></label>
            <label>IGST %<input type="number" min="0" step="0.001" value={form.igstRate} onChange={e => setForm({ ...form, igstRate: Number(e.target.value) })} /></label>
            <label>Discount<input type="number" min="0" step="0.01" value={form.discount} onChange={e => setForm({ ...form, discount: Number(e.target.value) })} /></label>
            <label>Freight / Transport<input type="number" min="0" step="0.01" value={form.freight} onChange={e => setForm({ ...form, freight: Number(e.target.value) })} /></label>
            <label>Advance / Already received<input type="number" min="0" step="0.01" value={form.advancePaid} onChange={e => setForm({ ...form, advancePaid: Number(e.target.value) })} /></label>
            <label>Payment terms<input value={form.paymentTerms ?? ""} onChange={e => setForm({ ...form, paymentTerms: e.target.value })} /></label>
            <label className="billing-wide">Notes<textarea rows={3} value={form.notes ?? ""} onChange={e => setForm({ ...form, notes: e.target.value })} /></label>
          </div>

          <div className="billing-total-bar"><div><span>Subtotal</span><strong>{money(totals.subtotal)}</strong></div><div><span>Tax</span><strong>{money(totals.tax)}</strong></div><div><span>Gross total</span><strong>{money(totals.gross)}</strong></div><div><span>Balance due</span><strong>{money(totals.balance)}</strong></div></div>
          <div className="finance-actions"><SAButton variant="primary" disabled={create.isPending || !form.billNumber || !form.counterpartyId || !form.lines.some(line => line.description.trim() && Number(line.rate) > 0)} onClick={() => create.mutate(form)}><FilePlus2 size={15} /> Save draft</SAButton>{create.error && <span role="alert">{create.error.message}</span>}</div>
        </SABentoCard>

        <div className="billing-side">
          <SABentoCard><span className="eyebrow">Saved bills</span>{bills.data?.length ? bills.data.map(b => <button className="billing-list-item" key={b.id} onClick={() => setSelected(b.id)}><span><strong>{b.billNumber ?? b.bill_number}</strong><small>{b.customer}</small></span><StatusBadge tone={b.status === "PAID" ? "success" : b.status === "CANCELLED" ? "danger" : b.status === "DRAFT" ? "neutral" : "info"}>{b.status}</StatusBadge></button>) : <EmptyState title="No bills yet" description="Create the first customer bill from the editor." />}</SABentoCard>
          {selected && detail.data && <SABentoCard><div className="finance-section-head"><div><span className="eyebrow">Selected bill</span><h2>{String(detail.data.bill.bill_number)}</h2></div><SAButton size="sm" onClick={() => setSelected(null)}><X size={14} /></SAButton></div><p>{String(detail.data.bill.customer)}</p><div className="finance-kpis"><div><span>Status</span><strong>{String(detail.data.bill.status)}</strong></div><div><span>Gross</span><strong>{money(Number(detail.data.bill.gross_total))}</strong></div><div><span>Advance</span><strong>{money(Number(detail.data.bill.advance_paid))}</strong></div><div><span>Balance</span><strong>{money(Number(detail.data.bill.gross_total) - Number(detail.data.bill.advance_paid))}</strong></div></div><div className="finance-actions">{detail.data.bill.status === "DRAFT" && <><SAButton variant="primary" disabled={issue.isPending} onClick={() => issue.mutate(selected)}><Send size={14} /> Issue</SAButton><SAButton variant="danger" disabled={cancel.isPending} onClick={() => cancel.mutate(selected)}>Cancel</SAButton></>}<SAButton onClick={() => void exportXlsx(selected)}><Download size={14} /> Export Excel</SAButton></div>{(issue.error || cancel.error) && <p role="alert">{(issue.error ?? cancel.error)?.message}</p>}<small>Export is document generation only. Issuing is the step that links this bill to canonical Finance receivables.</small></SABentoCard>}
        </div>
      </div>
    </div>
  );
}
