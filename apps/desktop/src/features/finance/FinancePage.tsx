import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, Plus } from "lucide-react";
import { Children, useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  EmptyState, FormField, MetricCard, SAButton, SABentoCard, SABentoGrid,
  SAModal, SASegmentedControl, SkeletonCard, StatusBadge,
} from "../../components/ui/sa";
import { ApiError } from "../../lib/api";
import { financeApi, type FinanceMigrationPreview, type FinanceMigrationReviewItem, type FinanceTransaction } from "./finance.api";
import { WorkbookProductionLedger } from "./WorkbookProductionLedger";
import { WorkbookOwnerAccounts } from "./WorkbookOwnerAccounts";
import { WorkbookEmployeeFinance } from "./WorkbookEmployeeFinance";
import { WorkbookPartyLedgers } from "./WorkbookPartyLedgers";
import { WorkbookGstLedger } from "./WorkbookGstLedger";
import { WorkbookPurchasesEquipment } from "./WorkbookPurchasesEquipment";
import { FinanceControlPlane } from "./FinanceControlPlane";

type Tab = "OVERVIEW" | "PRODUCTIONS" | "OWNERS" | "EMPLOYEES" | "PARTIES" | "INVOICES" | "EQUIPMENT" | "TRANSACTIONS" | "RECONCILIATION";
type Action = "contract" | "receipt" | "expense" | "earning" | "salary" | "employee-payment" | "party" | "charge" | "party-receipt" | "invoice" | "invoice-payment" | "purchase" | "purchase-payment" | "owner-credit" | "owner-debit" | "transfer" | "reverse";
type Field = { name: string; label: string; kind?: "select" | "date" | "number" | "text"; options?: { value: string; label: string }[]; required?: boolean };
const rupees = (value: number | string | null | undefined) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value ?? 0));
const signed = (value: number) => `${value >= 0 ? "+" : "−"}${rupees(Math.abs(value))}`;
const dateNow = () => new Date().toLocaleDateString("en-CA");
const tabs: { value: Tab; label: string }[] = [
  { value: "OVERVIEW", label: "Overview" }, { value: "PRODUCTIONS", label: "Production Ledger" }, { value: "OWNERS", label: "Owner Accounts" },
  { value: "EMPLOYEES", label: "Staff Finance" }, { value: "PARTIES", label: "Party Ledgers" },
  { value: "INVOICES", label: "Invoices / GST" }, { value: "EQUIPMENT", label: "Purchases & Equipment" },
  { value: "TRANSACTIONS", label: "Transactions" }, { value: "RECONCILIATION", label: "Reconciliation" },
];
const actionLabels: Record<Action, string> = {
  contract: "Set production contract", receipt: "Record production receipt", expense: "Record expense",
  earning: "Record work earning", salary: "Accrue monthly salary", "employee-payment": "Pay employee",
  party: "Add counterparty", charge: "Record party charge", "party-receipt": "Record party receipt",
  invoice: "Issue invoice", "invoice-payment": "Record invoice payment",
  purchase: "Record equipment purchase", "purchase-payment": "Pay equipment purchase",
  "owner-credit": "Credit owner account", "owner-debit": "Debit owner account", transfer: "Transfer between owner accounts",
  reverse: "Reverse transaction",
};
const paths: Record<Exclude<Action, "reverse">, string> = {
  contract: "contracts", receipt: "receipts", expense: "expenses", earning: "earnings", salary: "salaries",
  "employee-payment": "employee-payments", party: "counterparties", charge: "charges",
  "party-receipt": "party-receipts", invoice: "invoices", "invoice-payment": "invoice-payments",
  purchase: "equipment-purchases", "purchase-payment": "equipment-payments",
  "owner-credit": "owner-credits", "owner-debit": "owner-debits", transfer: "transfers",
};

function Table({ headers, children }: { headers: string[]; children: React.ReactNode }) {
  return <div className="finance-table-wrap"><table className="finance-table"><thead><tr>{headers.map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{Children.count(children) ? children : <tr><td colSpan={headers.length} className="finance-table-empty">No records in this view yet.</td></tr>}</tbody></table></div>;
}
function PageNav({ page, total, onPage }: { page: number; total: number; onPage: (page: number) => void }) {
  return <div className="finance-pagination"><span>{total} records · Page {page + 1}</span><SAButton size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</SAButton><SAButton size="sm" disabled={(page + 1) * 50 >= total} onClick={() => onPage(page + 1)}>Next</SAButton></div>;
}

const migrationActions = [
  ["ASSIGN_AZ", "Assign AZ-2"], ["ASSIGN_AK", "Assign AK-2"], ["MARK_NON_CASH", "Mark non-cash"],
  ["LINK_EXISTING", "Link posted transaction"], ["MARK_DISTINCT", "Mark distinct"],
  ["MAP_ENTITY", "Map identity"], ["IGNORE_NON_FINANCIAL", "Ignore as non-financial"],
  ["LEGACY_ADJUSTMENT", "Legacy adjustment"],
];
function MigrationReviewRow({ item, pending, onDecision }: {
  item: FinanceMigrationReviewItem; pending: boolean;
  onDecision: (decision: { sheetName: string; sourceRow: number; slot: string; action: string; target?: string; reason: string }) => void;
}) {
  const [action, setAction] = useState("");
  const [target, setTarget] = useState("");
  const [reason, setReason] = useState("");
  const targetRequired = action === "LINK_EXISTING" || action === "MAP_ENTITY";
  return <tr>
    <td>{item.sheetName}<small>Row {item.sourceRow} · {item.sourceRange}</small></td>
    <td>{item.date ?? "Date unclear"}<small>{item.rawName ?? "Name unclear"}</small></td>
    <td>{item.eventType.replaceAll("_", " ")}<small>{item.amount == null ? "—" : rupees(item.amount)}</small></td>
    <td>{item.reason.replaceAll("_", " ")}<small>{item.accountCode ?? "Account not proven"}</small><details><summary>Source evidence</summary><pre>{JSON.stringify({ evidence: item.evidence, original: item.original }, null, 2)}</pre></details></td>
    <td><select aria-label={`Decision for ${item.sheetName} row ${item.sourceRow}`} value={action} onChange={event => setAction(event.target.value)}><option value="">Choose…</option>{migrationActions.map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select>{targetRequired && <input aria-label="Verified target" placeholder={action === "LINK_EXISTING" ? "Posted transaction ID" : "Canonical identity"} value={target} onChange={event => setTarget(event.target.value)}/>}<input aria-label="Review reason" placeholder="Reason / evidence" value={reason} onChange={event => setReason(event.target.value)}/><SAButton size="sm" disabled={pending || !action || !reason.trim() || targetRequired && !target.trim()} onClick={() => onDecision({ sheetName: item.sheetName, sourceRow: item.sourceRow, slot: item.slot, action, target: targetRequired ? target.trim() : undefined, reason: reason.trim() })}>Record decision</SAButton></td>
  </tr>;
}

export function FinancePage() {
  const client = useQueryClient();
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const [tab, setTab] = useState<Tab>(params.has("workbookProduction") || params.has("production") ? "PRODUCTIONS" : params.has("owner") ? "OWNERS" : params.has("employee") || params.has("workbookEmployee") ? "EMPLOYEES" : params.has("workbookParty") || params.get("tab") === "PARTIES" ? "PARTIES" : params.has("workbookInvoice") ? "INVOICES" : params.has("workbookPurchase") ? "EQUIPMENT" : "OVERVIEW");
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [selectedProduction, setSelectedProduction] = useState(params.get("production") ?? "");
  const [selectedEmployee, setSelectedEmployee] = useState(params.get("employee") ?? "");
  const [action, setAction] = useState<Action | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const [requestKey, setRequestKey] = useState("");
  const [selectedTransaction, setSelectedTransaction] = useState("");
  const [migration, setMigration] = useState<FinanceMigrationPreview | null>(null);
  const [reviewPage, setReviewPage] = useState(0);
  const overview = useQuery({ queryKey: ["finance", "overview"], queryFn: financeApi.overview });
  const workbookEmployees = useQuery({ queryKey: ["finance", "workbook-employees"], queryFn: financeApi.workbookEmployees, enabled: import.meta.env.VITE_APP_MODE === "demo" });
  const workbookParties = useQuery({ queryKey: ["finance", "workbook-party-summary"], queryFn: financeApi.workbookPartySummary, enabled: import.meta.env.VITE_APP_MODE === "demo" });
  const workbookGst = useQuery({ queryKey: ["finance", "workbook-gst-summary"], queryFn: financeApi.workbookGstSummary, enabled: import.meta.env.VITE_APP_MODE === "demo" });
  const workbookPurchases = useQuery({ queryKey: ["finance", "workbook-purchase-summary"], queryFn: financeApi.workbookPurchaseSummary, enabled: import.meta.env.VITE_APP_MODE === "demo" });
  useEffect(() => {
    const next = new URLSearchParams(location.search);
    if (next.has("workbookProduction")) setTab("PRODUCTIONS");
    else if (next.has("owner")) setTab("OWNERS");
    else if (next.has("workbookEmployee")) setTab("EMPLOYEES");
    else if (next.has("workbookParty") || next.get("tab") === "PARTIES") setTab("PARTIES");
    else if (next.has("workbookInvoice")) setTab("INVOICES");
    else if (next.has("workbookPurchase")) setTab("EQUIPMENT");
  }, [location.search]);
  const config = useQuery({ queryKey: ["finance", "config"], queryFn: financeApi.config });
  const reconciliation = useQuery({ queryKey: ["finance", "reconciliation"], queryFn: financeApi.reconciliation, enabled: tab === "RECONCILIATION" });
  const productions = useQuery({ queryKey: ["finance", "productions", page, search], queryFn: () => financeApi.productions(page, search), enabled: tab === "PRODUCTIONS" || !!action && ["contract", "receipt", "expense", "earning", "charge", "invoice"].includes(action) });
  const production = useQuery({ queryKey: ["finance", "production", selectedProduction], queryFn: () => financeApi.production(selectedProduction), enabled: !!selectedProduction });
  const employees = useQuery({ queryKey: ["finance", "employees", page], queryFn: () => financeApi.employees(page), enabled: tab === "EMPLOYEES" || !!action && ["earning", "salary", "employee-payment"].includes(action) });
  const employee = useQuery({ queryKey: ["finance", "employee", selectedEmployee], queryFn: () => financeApi.employee(selectedEmployee), enabled: !!selectedEmployee });
  const parties = useQuery({ queryKey: ["finance", "parties", page, search], queryFn: () => financeApi.counterparties(page, search), enabled: tab === "PARTIES" || !!action && ["charge", "party-receipt", "invoice", "purchase"].includes(action) });
  const invoices = useQuery({ queryKey: ["finance", "invoices", page], queryFn: () => financeApi.invoices(page), enabled: tab === "INVOICES" || action === "invoice-payment" });
  const purchases = useQuery({ queryKey: ["finance", "purchases", page], queryFn: () => financeApi.purchases(page), enabled: tab === "EQUIPMENT" || action === "purchase-payment" });
  const transactions = useQuery({ queryKey: ["finance", "transactions", page, search], queryFn: () => financeApi.transactions(page, search), enabled: tab === "TRANSACTIONS" });
  const detail = useQuery({ queryKey: ["finance", "transaction", selectedTransaction], queryFn: () => financeApi.transaction(selectedTransaction), enabled: !!selectedTransaction });
  const open = (next: Action, defaults: Record<string, string> = {}) => {
    setForm({ date: dateNow(), ...defaults });
    setRequestKey(crypto.randomUUID());
    setAction(next);
  };
  const changeTab = (next: Tab) => { setTab(next); setPage(0); setSearch(""); };
  const post = useMutation({
    mutationFn: async () => {
      if (!action) throw new Error("Choose an action.");
      if (action === "reverse") return financeApi.reverse(form.transactionId, { idempotencyKey: requestKey, reason: form.reason.trim() });
      const body: Record<string, string | null> = { ...form };
      if (action !== "party") body.idempotencyKey = requestKey;
      for (const [key, value] of Object.entries(body)) if (value === "") body[key] = null;
      if (action === "invoice") {
        body.invoiceDate = form.date;
        delete body.date;
      }
      return financeApi.post(paths[action], body);
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["finance"] });
      setAction(null);
      setForm({});
    },
  });
  const rebuild = useMutation({ mutationFn: financeApi.rebuild, onSuccess: () => client.invalidateQueries({ queryKey: ["finance"] }) });
  const previewWorkbook = useMutation({
    mutationFn: async (file: File) => {
      if (file.size > 8_000_000) throw new Error("Choose an XLSX workbook under 8 MB.");
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
        reader.onerror = () => reject(new Error("The workbook could not be read."));
        reader.readAsDataURL(file);
      });
      return financeApi.previewWorkbook(file.name, base64);
    },
    onSuccess: result => { setMigration(result); setReviewPage(0); },
  });
  const validateWorkbook = useMutation({ mutationFn: financeApi.validateWorkbook, onSuccess: setMigration });
  const postProvenWorkbook = useMutation({ mutationFn: financeApi.postProvenWorkbook, onSuccess: result => {
    setMigration(result);
    client.invalidateQueries({ queryKey: ["finance"] });
  } });
  const review = useQuery({ queryKey: ["finance", "migration-review", migration?.batch.id, reviewPage], queryFn: () => financeApi.migrationReview(migration!.batch.id, reviewPage), enabled: !!migration });
  const decideMigration = useMutation({
    mutationFn: (decision: { sheetName: string; sourceRow: number; slot: string; action: string; target?: string; reason: string }) => financeApi.decideMigration(migration!.batch.id, decision),
    onSuccess: async () => {
      if (!migration) return;
      setMigration(await financeApi.migrationReport(migration.batch.id));
      client.invalidateQueries({ queryKey: ["finance", "migration-review", migration.batch.id] });
    },
  });
  const resetMigration = useMutation({ mutationFn: financeApi.resetMigration, onSuccess: () => { setMigration(null); setReviewPage(0); client.invalidateQueries({ queryKey: ["finance"] }); } });
  const accountOptions = (config.data?.accounts ?? []).map(a => ({ value: a.code, label: `${a.displayName} · ${a.code}` }));
  const productionOptions = (productions.data?.items ?? []).map(p => ({ value: p.id, label: p.title }));
  const employeeOptions = (employees.data?.items ?? []).map(e => ({ value: e.id, label: e.displayName }));
  const partyOptions = (parties.data?.items ?? []).map(p => ({ value: p.id, label: p.displayName }));
  const field = (name: string, label: string, kind: Field["kind"] = "text", options?: Field["options"], required = true): Field => ({ name, label, kind, options, required });
  const account = (name: string, label: string) => field(name, label, "select", accountOptions);
  const common = [field("amount", "Amount · INR", "number"), field("date", "Effective date", "date"), field("description", "Description / evidence")];
  const fields: Field[] = action === "contract" ? [field("productionId", "Production", "select", productionOptions), ...common]
    : action === "receipt" ? [field("productionId", "Production", "select", productionOptions), ...common, account("receiverAccount", "Received by")]
    : action === "expense" ? [...common, account("payerAccount", "Paid by"), field("productionId", "Production (optional)", "select", productionOptions, false), field("categoryCode", "Category", "select", (config.data?.expenseCategories ?? []).map(c => ({ value: c.code, label: c.displayName })), false)]
    : action === "earning" ? [field("employeeId", "Employee", "select", employeeOptions), field("productionId", "Production (optional)", "select", productionOptions, false), ...common]
    : action === "salary" ? [field("employeeId", "Employee", "select", employeeOptions), field("gross", "Gross · INR", "number"), field("approvedDeductions", "Approved deductions · INR", "number"), field("adjustment", "Adjustment · INR", "number"), field("date", "Effective date", "date"), field("description", "Description / evidence")]
    : action === "employee-payment" ? [field("employeeId", "Employee", "select", employeeOptions), ...common, account("payerAccount", "Paid by")]
    : action === "party" ? [field("displayName", "Party name"), field("role", "Role", "select", ["CUSTOMER", "VENDOR", "RENTAL_PROVIDER", "SUBCONTRACTOR", "OTHER", "UNKNOWN", "MIXED"].map(x => ({ value: x, label: x.replaceAll("_", " ") }))), field("gstin", "GSTIN (optional)", "text", undefined, false)]
    : action === "charge" ? [field("counterpartyId", "Party", "select", partyOptions), ...common, field("productionId", "Production (optional)", "select", productionOptions, false)]
    : action === "party-receipt" ? [field("counterpartyId", "Party", "select", partyOptions), ...common, account("receiverAccount", "Received by")]
    : action === "invoice" ? [field("invoiceNumber", "Invoice number"), field("financialYear", "Financial year"), field("counterpartyId", "Party", "select", partyOptions), field("productionId", "Production (optional)", "select", productionOptions, false), field("date", "Invoice date", "date"), field("taxMode", "Tax mode", "select", ["NONE", "CGST_SGST", "IGST", "CUSTOM"].map(x => ({ value: x, label: x }))), field("baseAmount", "Base amount · INR", "number"), field("cgstRate", "CGST %", "number"), field("sgstRate", "SGST %", "number"), field("igstRate", "IGST %", "number"), field("tdsAmount", "TDS · INR", "number")]
    : action === "invoice-payment" ? [field("invoiceId", "Invoice", "select", (invoices.data?.items ?? []).map(i => ({ value: i.id, label: i.invoiceNumber }))), ...common, account("receiverAccount", "Received by")]
    : action === "purchase" ? [field("counterpartyId", "Vendor (optional)", "select", partyOptions, false), field("date", "Purchase date", "date"), field("description", "Description"), field("subtotal", "Subtotal · INR", "number"), field("tax", "Tax · INR", "number"), field("reference", "Reference (optional)", "text", undefined, false)]
    : action === "purchase-payment" ? [field("purchaseId", "Purchase", "select", (purchases.data?.items ?? []).map(p => ({ value: p.id, label: p.description }))), ...common, account("payerAccount", "Paid by")]
    : action === "owner-credit" ? [...common, account("receiverAccount", "Credit account")]
    : action === "owner-debit" ? [...common, account("payerAccount", "Debit account")]
    : action === "transfer" ? [...common, account("payerAccount", "From"), account("receiverAccount", "To")]
    : action === "reverse" ? [field("transactionId", "Transaction ID"), field("reason", "Reason for reversal")]
    : [];
  const valid = !!action && fields.filter(f => f.required).every(f => !!form[f.name]?.trim())
    && (!fields.some(f => f.name === "amount") || Number(form.amount) > 0)
    && (!fields.some(f => f.name === "subtotal") || Number(form.subtotal) + Number(form.tax) > 0);
  return <div className="finance-page">
    <header className="page-title finance-title"><div><span className="eyebrow">Financial control plane</span><h1>Finance</h1><p>Position, commitments, payments and evidence.</p></div><div className="finance-actions"><Link className="sa-button sa-button--secondary sa-button--md" to="/payroll">Payroll</Link><SAButton variant="primary" onClick={() => open("expense")}> <Plus size={15}/> Transaction</SAButton></div></header>
    <SASegmentedControl value={tab} onChange={changeTab} label="Finance workspace" items={import.meta.env.VITE_APP_MODE === "demo" ? tabs : tabs.filter(item => item.value !== "OWNERS")}/>
    {tab === "OVERVIEW" && (overview.isPending ? <SkeletonCard/> : overview.isError || !overview.data ? <EmptyState title="Finance unavailable" description="The financial position could not be loaded." action={<SAButton onClick={() => overview.refetch()}>Try again</SAButton>}/> : <>
      <FinanceControlPlane />
      {import.meta.env.VITE_APP_MODE === "demo" && workbookEmployees.data?.available && <SABentoCard><span className="eyebrow">Historical staff · workbook-backed</span><div className="finance-kpis"><div><span>Earned</span><strong>{rupees(workbookEmployees.data.earned)}</strong></div><div><span>Paid</span><strong>{rupees(workbookEmployees.data.paid)}</strong></div><div><span>Source balance</span><strong>{signed(workbookEmployees.data.outstanding)}</strong></div></div><small>Read-only historical evidence; not added to posted Finance payables.</small></SABentoCard>}
      {import.meta.env.VITE_APP_MODE === "demo" && workbookParties.data?.available && <SABentoCard><span className="eyebrow">Party receivables · workbook-backed</span><div className="finance-kpis"><div><span>Party ledger outstanding</span><strong>{rupees(workbookParties.data.outstanding)}</strong></div><div><span>Parties outstanding</span><strong>{workbookParties.data.outstandingParties}</strong></div></div><Link to="/finance?tab=PARTIES">View party ledgers →</Link><small>Separate from production receivables; overlapping workbook evidence is not summed.</small></SABentoCard>}
      {import.meta.env.VITE_APP_MODE === "demo" && workbookGst.data?.available && <SABentoCard><span className="eyebrow">GST · workbook-backed</span><div className="finance-kpis"><div><span>GST outstanding</span><strong>{rupees(workbookGst.data.outstanding)}</strong></div><div><span>Cash received</span><strong>{rupees(workbookGst.data.cash)}</strong></div></div><Link to="/finance?workbookInvoice=1">View invoices / GST →</Link><small>TDS remains a receivable reduction, not owner-account cash.</small></SABentoCard>}
      {import.meta.env.VITE_APP_MODE === "demo" && workbookPurchases.data?.available && <SABentoCard><span className="eyebrow">Purchases · workbook-backed</span><div className="finance-kpis"><div><span>LED / Sound outstanding</span><strong>{rupees(workbookPurchases.books.reduce((sum, book) => sum + Number(book.outstanding), 0))}</strong></div><div><span>Equipment references</span><strong>{workbookPurchases.accessories.records}</strong></div></div><Link to="/finance?workbookPurchase=1">View purchases & equipment →</Link><small>Purchases and physical custody remain separate.</small></SABentoCard>}
      <div className="finance-two"><SABentoCard><span className="eyebrow">Operating picture</span><div className="finance-kpis"><div><span>Received</span><strong>{rupees(overview.data.received)}</strong></div><div><span>Incurred expense</span><strong>{rupees(overview.data.incurredExpense)}</strong></div><div><span>Contracted</span><strong>{rupees(overview.data.contracted)}</strong></div><div><span>Production receivables</span><strong>{rupees(overview.data.receivables)}</strong></div></div></SABentoCard><SABentoCard><span className="eyebrow">Record with evidence</span><h2>Financial actions</h2><p>Choose the real payer or receiver. Posted records are corrected by reversal, never silent edits.</p><div className="finance-action-grid"><SAButton onClick={() => open("receipt")}><ArrowDownLeft size={15}/> Receipt</SAButton><SAButton onClick={() => open("expense")}><ArrowUpRight size={15}/> Expense</SAButton><SAButton onClick={() => open("transfer")}><ArrowLeftRight size={15}/> Transfer</SAButton><SAButton onClick={() => open("owner-credit")}>Owner credit</SAButton><SAButton onClick={() => open("owner-debit")}>Owner debit</SAButton></div></SABentoCard></div>
    </>)}
    {tab === "OWNERS" && import.meta.env.VITE_APP_MODE === "demo" && <WorkbookOwnerAccounts key={location.search} initialOwner={params.get("owner") ?? ""} initialMovement={params.get("movement") ?? ""}/>}
    {tab === "OWNERS" && import.meta.env.VITE_APP_MODE !== "demo" && <EmptyState title="Workbook history unavailable" description="Historical owner workbook views are only available in the local demo."/>}
    {tab === "PRODUCTIONS" && import.meta.env.VITE_APP_MODE === "demo" && <WorkbookProductionLedger key={params.get("workbookProduction") ?? ""} initialId={params.get("workbookProduction") ?? ""}/>}
    {tab === "PRODUCTIONS" && <section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">Posted Finance</span><h2>Contracts & realized margins</h2></div><div className="finance-actions"><SAButton onClick={() => open("contract")}>Set contract</SAButton><SAButton onClick={() => open("receipt")}>Record receipt</SAButton><SAButton onClick={() => open("expense")}>Expense</SAButton></div></div><input className="finance-search" placeholder="Search productions" aria-label="Search productions" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}/>{productions.isPending ? <SkeletonCard/> : productions.isError ? <EmptyState title="Productions unavailable" description="Try loading this financial view again." action={<SAButton onClick={() => productions.refetch()}>Retry</SAButton>}/> : <><Table headers={["Production", "Date", "Contracted", "Received", "Expense", "Open"]}>{productions.data?.items.map(p => <tr key={p.id}><td><strong>{p.title}</strong><small>{p.clientName}</small></td><td>{p.eventDate}</td><td>{rupees(p.contracted)}</td><td>{rupees(p.received)}</td><td>{rupees(p.expense)}</td><td><SAButton size="sm" onClick={() => setSelectedProduction(p.id)}>Details</SAButton></td></tr>)}</Table><PageNav page={page} total={productions.data?.total ?? 0} onPage={setPage}/></>}{selectedProduction && <SABentoCard className="finance-detail"><div className="finance-section-head"><h2>{production.data?.production.title ?? "Production finance"}</h2><SAButton size="sm" onClick={() => setSelectedProduction("")}>Close</SAButton></div>{production.isPending ? <SkeletonCard/> : production.isError ? <p role="alert">Could not load production finances.</p> : production.data && <><div className="finance-kpis"><div><span>Outstanding</span><strong>{rupees(production.data.outstanding)}</strong></div><div><span>Contracted margin</span><strong>{signed(production.data.contractedMargin)}</strong></div><div><span>Realized margin</span><strong>{signed(production.data.realizedMargin)}</strong></div></div><div className="finance-actions"><SAButton onClick={() => open("receipt", { productionId: selectedProduction })}>Record receipt</SAButton><SAButton onClick={() => open("expense", { productionId: selectedProduction })}>Record expense</SAButton></div></>}</SABentoCard>}</section>}
    {tab === "EMPLOYEES" && import.meta.env.VITE_APP_MODE === "demo" && <WorkbookEmployeeFinance key={params.get("workbookEmployee") ?? ""} initialEmployee={params.get("workbookEmployee") ?? ""}/>}
    {tab === "EMPLOYEES" && <section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">Posted Finance · separate from workbook history</span><h2>Earned is not paid</h2></div><div className="finance-actions"><SAButton onClick={() => open("earning")}>Work earning</SAButton><SAButton onClick={() => open("salary")}>Salary accrual</SAButton><SAButton onClick={() => open("employee-payment")}>Pay employee</SAButton></div></div>{employees.isPending ? <SkeletonCard/> : employees.isError ? <EmptyState title="Payables unavailable" description="Could not load employee balances."/> : <><Table headers={["Employee", "Earned", "Paid", "Outstanding", "Open"]}>{employees.data?.items.map(e => <tr key={e.id}><td>{e.displayName}</td><td>{rupees(e.earned)}</td><td>{rupees(e.paid)}</td><td>{signed(e.earned - e.paid)}</td><td><SAButton size="sm" onClick={() => setSelectedEmployee(e.id)}>Ledger</SAButton></td></tr>)}</Table><PageNav page={page} total={employees.data?.total ?? 0} onPage={setPage}/></>}{selectedEmployee && <SABentoCard className="finance-detail"><div className="finance-section-head"><h2>{employee.data?.employee.displayName ?? "Employee ledger"}</h2><SAButton size="sm" onClick={() => setSelectedEmployee("")}>Close</SAButton></div>{employee.isPending ? <SkeletonCard/> : employee.isError ? <p role="alert">Could not load employee ledger.</p> : employee.data && <><div className="finance-kpis"><div><span>Earned</span><strong>{rupees(employee.data.earned)}</strong></div><div><span>Paid</span><strong>{rupees(employee.data.paid)}</strong></div><div><span>Outstanding</span><strong>{signed(employee.data.outstanding)}</strong></div></div><div className="finance-actions"><SAButton onClick={() => open("earning", { employeeId: selectedEmployee })}>Work earning</SAButton><SAButton onClick={() => open("employee-payment", { employeeId: selectedEmployee })}>Record payment</SAButton></div></>}</SABentoCard>}</section>}
    {tab === "PARTIES" && import.meta.env.VITE_APP_MODE === "demo" && <WorkbookPartyLedgers key={params.get("workbookParty") ?? ""} initialParty={params.get("workbookParty") ?? ""} initialEntry={params.get("partyEntry") ?? ""}/>}
    {tab === "PARTIES" && <section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">Posted Finance · separate from workbook history</span><h2>Parties & receivables</h2></div><div className="finance-actions"><SAButton onClick={() => open("party")}>Add party</SAButton><SAButton onClick={() => open("charge")}>Charge</SAButton><SAButton onClick={() => open("party-receipt")}>Receipt</SAButton></div></div><input className="finance-search" placeholder="Search parties" aria-label="Search parties" value={search} onChange={e => {setSearch(e.target.value);setPage(0);}}/>{parties.isPending ? <SkeletonCard/> : parties.isError ? <EmptyState title="Parties unavailable" description="Could not load counterparty ledgers."/> : <><Table headers={["Party", "Role", "Charged", "Received", "Outstanding"]}>{parties.data?.items.map(p => <tr key={p.id}><td>{p.displayName}</td><td>{p.role}</td><td>{rupees(p.charged)}</td><td>{rupees(p.received)}</td><td>{signed(p.charged - p.received)}</td></tr>)}</Table><PageNav page={page} total={parties.data?.total ?? 0} onPage={setPage}/></>}</section>}
    {tab === "INVOICES" && <>{import.meta.env.VITE_APP_MODE === "demo" && <WorkbookGstLedger/>}<section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">New canonical tax invoices</span><h2>Invoices & installments</h2></div><div className="finance-actions"><SAButton onClick={() => open("invoice")}>Issue invoice</SAButton><SAButton onClick={() => open("invoice-payment")}>Record installment</SAButton></div></div>{invoices.isPending ? <SkeletonCard/> : invoices.isError ? <EmptyState title="Invoices unavailable" description="Could not load invoices."/> : <><Table headers={["Invoice", "Date", "Party", "Total", "TDS", "Paid"]}>{invoices.data?.items.map(i => <tr key={i.id}><td>{i.invoiceNumber}</td><td>{i.date}</td><td>{i.counterpartyName}</td><td>{rupees(i.total)}</td><td>{rupees(i.tds)}</td><td>{rupees(i.paid)}</td></tr>)}</Table><PageNav page={page} total={invoices.data?.total ?? 0} onPage={setPage}/></>}</section></>}
    {tab === "EQUIPMENT" && <>{import.meta.env.VITE_APP_MODE === "demo" && <WorkbookPurchasesEquipment/>}<section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">New canonical purchase finance</span><h2>Equipment acquisitions</h2></div><div className="finance-actions"><SAButton onClick={() => open("purchase")}>Record purchase</SAButton><SAButton onClick={() => open("purchase-payment")}>Record payment</SAButton></div></div>{purchases.isPending ? <SkeletonCard/> : purchases.isError ? <EmptyState title="Purchases unavailable" description="Could not load equipment purchase finance."/> : <><Table headers={["Purchase", "Date", "Vendor", "Total", "Paid"]}>{purchases.data?.items.map(p => <tr key={p.id}><td>{p.description}</td><td>{p.date}</td><td>{p.counterpartyName ?? "—"}</td><td>{rupees(p.total)}</td><td>{rupees(p.paid)}</td></tr>)}</Table><PageNav page={page} total={purchases.data?.total ?? 0} onPage={setPage}/></>}</section></>}
    {tab === "TRANSACTIONS" && <section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">Immutable evidence</span><h2>Transaction journal</h2></div><SAButton onClick={() => open("reverse")}>Reverse posted entry</SAButton></div><input className="finance-search" placeholder="Search descriptions" aria-label="Search transactions" value={search} onChange={e => {setSearch(e.target.value);setPage(0);}}/>{transactions.isPending ? <SkeletonCard/> : transactions.isError ? <EmptyState title="Transactions unavailable" description="Could not load the financial journal."/> : <><Table headers={["Date", "Number", "Type", "Description", "Amount", "Status", "Open"]}>{transactions.data?.items.map((t: FinanceTransaction) => <tr key={t.id}><td>{t.date}</td><td>#{t.transactionNo}</td><td>{t.type.replaceAll("_", " ")}</td><td>{t.description}</td><td>{rupees(t.amount)}</td><td><StatusBadge tone={t.status === "POSTED" ? "success" : "neutral"}>{t.status}</StatusBadge></td><td><SAButton size="sm" onClick={() => setSelectedTransaction(t.id)}>Journal</SAButton></td></tr>)}</Table><PageNav page={page} total={transactions.data?.total ?? 0} onPage={setPage}/></>}{selectedTransaction && <SABentoCard className="finance-detail"><div className="finance-section-head"><h2>Journal evidence</h2><SAButton size="sm" onClick={() => setSelectedTransaction("")}>Close</SAButton></div>{detail.isPending ? <SkeletonCard/> : detail.isError ? <p role="alert">Could not load journal lines.</p> : detail.data && <Table headers={["Ledger", "Debit", "Credit", "Owner account"]}>{detail.data.journal.map((line, index) => <tr key={`${line.ledgerCode}-${index}`}><td>{line.ledgerCode}</td><td>{rupees(line.debit)}</td><td>{rupees(line.credit)}</td><td>{line.ownerAccount ?? "—"}</td></tr>)}</Table>}</SABentoCard>}</section>}
    {tab === "RECONCILIATION" && <section className="finance-section"><div className="finance-section-head"><div><span className="eyebrow">Financial integrity</span><h2>Control & projection</h2></div><SAButton onClick={() => reconciliation.refetch()}>Recalculate</SAButton></div>{reconciliation.isPending ? <SkeletonCard/> : reconciliation.isError || !reconciliation.data ? <EmptyState title="Integrity check unavailable" description="Could not calculate the journal controls."/> : <><SABentoGrid className="finance-metrics"><MetricCard label="Status" value={reconciliation.data.status} detail="Posted journal versus current positions"/><MetricCard label="Control difference" value={rupees(reconciliation.data.controlDifference)}/><MetricCard label="Production receivables" value={rupees(reconciliation.data.receivables)}/></SABentoGrid>{(reconciliation.data.migrationOpenCount ?? 0) > 0 && <p role="alert">The posted journal is separate from the historical workbook. {reconciliation.data.migrationOpenCount} migration facts still need resolution; workbook parity is not reconciled.</p>}<SABentoCard><div className="finance-kpis"><div><span>Employee payable</span><strong>{rupees(reconciliation.data.employeePayables)}</strong></div><div><span>Invoice receivable</span><strong>{rupees(reconciliation.data.invoiceReceivables)}</strong></div><div><span>Equipment payable</span><strong>{rupees(reconciliation.data.equipmentPayables)}</strong></div></div>{reconciliation.data.status !== "RECONCILED" && <><p role="alert">Position projection differs from posted journal evidence. Review before rebuilding.</p><SAButton disabled={rebuild.isPending} onClick={() => rebuild.mutate()}>{rebuild.isPending ? "Rebuilding…" : "Rebuild account positions"}</SAButton>{rebuild.error && <p role="alert">{rebuild.error.message}</p>}</>}</SABentoCard></>}
    {import.meta.env.VITE_APP_MODE === "demo" && <SABentoCard className="finance-migration">
      <span className="eyebrow">Demo · legacy workbook</span><h2>Import review</h2>
      <p>Cross-sheet evidence is classified before review. Only posted Finance entries appear in normal totals; historical source facts remain separate until their identity is proven.</p>
      <label className="finance-file">Choose XLSX workbook<input type="file" accept=".xlsx" disabled={previewWorkbook.isPending} onChange={e => { const file = e.target.files?.[0]; if (file) previewWorkbook.mutate(file); }}/></label>
      {previewWorkbook.isPending && <p>Reading workbook…</p>}{previewWorkbook.error && <p role="alert" className="finance-error">{previewWorkbook.error.message}</p>}
      {migration && <>
        <p>{migration.resolution.sourceRows} rows · {migration.resolution.financialRows} financial rows · {migration.resolution.duplicateSourceLinks} cross-sheet duplicate links · {migration.resolution.ownerTransferLinks} owner transfers · {migration.resolution.linkedFacts} account links · {migration.openIssueCount} review items</p>
        <p role="status">Canonical posted from this workbook: {migration.resolution.canonicalPosted}. Unallocated historical cash: {rupees(migration.resolution.unallocatedAmount)}. Workbook parity remains unverified while review items or unposted facts remain.</p>
        <SAButton disabled={validateWorkbook.isPending} onClick={() => validateWorkbook.mutate(migration.batch.id)}>Re-run deterministic resolution</SAButton>
        <SAButton disabled={postProvenWorkbook.isPending} onClick={() => {
          if (window.confirm("Post only source-proven purchases and owner transfers? Posted Finance evidence is immutable; staged migration reset will no longer be available.")) postProvenWorkbook.mutate(migration.batch.id);
        }}>Post proven entries</SAButton>
        <SAButton disabled={resetMigration.isPending} onClick={() => { if (window.confirm("Reset only staged Finance workbook data? Reviewed mappings will be preserved.")) resetMigration.mutate(); }}>Reset staging</SAButton>
        {postProvenWorkbook.error && <p role="alert" className="finance-error">{postProvenWorkbook.error.message}</p>}
        {resetMigration.error && <p role="alert" className="finance-error">{resetMigration.error.message}</p>}
        {validateWorkbook.error && <p role="alert" className="finance-error">{validateWorkbook.error.message}</p>}
        <Table headers={["Reason", "Facts", "Amount"]}>{migration.resolution.issueGroups.map(group => <tr key={group.reason}><td>{group.reason.replaceAll("_", " ")}</td><td>{group.count}</td><td>{rupees(group.amount)}</td></tr>)}</Table>
        <h3>Workbook parity</h3><Table headers={["Metric", "Workbook", "Finance", "Difference", "Status"]}>{migration.parity.map(item => <tr key={item.metric}><td>{item.metric}</td><td>{item.workbook == null ? "—" : rupees(item.workbook)}</td><td>{item.finance == null ? "Not posted" : rupees(item.finance)}</td><td>{item.difference == null ? "—" : rupees(item.difference)}</td><td>{item.status}</td></tr>)}</Table>
        <h3>Last-mile review</h3>
        {review.isPending ? <SkeletonCard/> : review.isError ? <p role="alert">Review evidence could not be loaded.</p> : <><Table headers={["Source", "Date / identity", "Event", "Evidence", "Decision"]}>{review.data?.items.map(item => <MigrationReviewRow key={item.id} item={item} pending={decideMigration.isPending} onDecision={decision => decideMigration.mutate(decision)}/>)}</Table><PageNav page={reviewPage} total={review.data?.total ?? 0} onPage={setReviewPage}/></>}
        {decideMigration.error && <p role="alert" className="finance-error">{decideMigration.error.message}</p>}
      </>}
    </SABentoCard>}</section>}
    <SAModal open={!!action} onOpenChange={v => { if (!v && !post.isPending) setAction(null); }} title={action ? actionLabels[action] : "Financial action"} description={action === "reverse" ? "This posts a compensating journal entry. The original remains visible." : "A posted financial entry cannot be silently edited."}>
      <form className="finance-form" onSubmit={e => {e.preventDefault(); if (valid && !post.isPending) post.mutate();}}>
        {fields.map(f => <FormField key={f.name} label={f.label}><>{f.kind === "select" ? <select required={f.required} value={form[f.name] ?? ""} onChange={e => setForm(current => ({...current,[f.name]:e.target.value}))}><option value="">Select…</option>{f.options?.map(o => <option value={o.value} key={o.value}>{o.label}</option>)}</select> : <input required={f.required} type={f.kind === "number" ? "number" : f.kind === "date" ? "date" : "text"} min={f.kind === "number" && f.name !== "adjustment" ? "0" : undefined} step={f.kind === "number" ? "0.01" : undefined} value={form[f.name] ?? ""} onChange={e => setForm(current => ({...current,[f.name]:e.target.value}))}/>}</></FormField>)}
        {post.error && <p className="finance-error" role="alert">{post.error instanceof ApiError ? post.error.message : "Could not record this action. Review the fields and retry."}</p>}
        <div className="finance-form-actions"><SAButton type="button" onClick={() => setAction(null)} disabled={post.isPending}>Cancel</SAButton><SAButton variant="primary" type="submit" disabled={!valid || post.isPending}>{post.isPending ? "Posting…" : action === "party" ? "Create party" : action === "reverse" ? "Confirm reversal" : "Post transaction"}</SAButton></div>
      </form>
    </SAModal>
  </div>;
}
