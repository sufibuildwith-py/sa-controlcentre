import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, SAButton, SABentoCard, SkeletonCard } from "../../components/ui/sa";
import { financeApi } from "./finance.api";

const money = (amount: number | null | undefined) => amount == null ? "—" :
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(amount);

export function WorkbookProductionLedger({ initialId = "" }: { initialId?: string }) {
  const [sheet, setSheet] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(initialId);
  const rows = useQuery({ queryKey: ["finance", "workbook-productions", sheet, search, page],
    queryFn: () => financeApi.workbookProductions(page, sheet, search) });
  const detail = useQuery({ queryKey: ["finance", "workbook-production", selectedId],
    queryFn: () => financeApi.workbookProduction(selectedId), enabled: !!selectedId });
  const selected = detail.data;

  return <section className="finance-section" aria-label="Historical production workbook">
    <div className="finance-section-head"><div><span className="eyebrow">Original workbook</span><h2>Production ledger</h2>
      <p>Historical source rows and calculated position in INR. Sheets can overlap; rows are not summed across sheets.</p></div></div>
    <div className="finance-actions">
      <label>Sheet <select value={sheet} onChange={event => { setSheet(event.target.value); setPage(0); }}>
        <option value="">All production sheets</option>
        {(rows.data?.sheets ?? []).map(name => <option key={name} value={name}>{name}</option>)}
      </select></label>
      <input className="finance-search" aria-label="Search workbook production rows" placeholder="Search venue, client or service" value={search}
        onChange={event => { setSearch(event.target.value); setPage(0); }}/>
    </div>
    {sheet && rows.data?.sourceControl && <SABentoCard><span className="eyebrow">{sheet} · workbook row {rows.data.sourceControl.sourceRow}</span>
      <div className="finance-kpis"><div><span>Contracted</span><strong>{money(rows.data.sourceControl.contracted)}</strong></div>
        <div><span>Add</span><strong>{money(rows.data.sourceControl.add)}</strong></div>
        <div><span>Payment</span><strong>{money(rows.data.sourceControl.payment)}</strong></div>
        <div><span>Balance</span><strong>{money(rows.data.sourceControl.balance)}</strong></div>
        <div><span>Expense</span><strong>{money(rows.data.sourceControl.expense)}</strong></div>
        <div><span>Workbook result</span><strong>{money(rows.data.sourceControl.legacyBudget)}</strong></div></div>
    </SABentoCard>}
    {rows.isPending ? <SkeletonCard/> : rows.isError ? <EmptyState title="Workbook ledger unavailable" description="The production rows could not be loaded." action={<SAButton onClick={() => rows.refetch()}>Retry</SAButton>}/>
      : !rows.data?.available ? <EmptyState title="No workbook loaded" description="Load the historical workbook in Finance import review to see original production rows."/>
      : !rows.data.total ? <EmptyState title="No matching production rows" description="Try a different sheet or search."/>
      : <><div className="finance-table-wrap"><table className="finance-table"><thead><tr>
        {["Source", "Date", "Venue / client", "Contracted", "Received", "Outstanding", "Expense", "Workbook result", ""].map(label => <th key={label}>{label}</th>)}
      </tr></thead><tbody>{rows.data.items.map(row => <tr key={row.id}>
        <td><strong>{row.sheet}</strong><small>Row {row.sourceRow}</small></td><td>{row.date ?? "—"}</td>
        <td><strong>{row.venue ?? "—"}</strong><small>{row.client ?? "—"}</small></td>
        <td>{money(row.total)}</td><td>{money(row.received)}</td><td>{money(row.outstanding)}</td><td>{money(row.expense)}</td><td>{money(row.legacyBudget)}</td>
        <td><SAButton size="sm" onClick={() => setSelectedId(row.id)}>Evidence</SAButton></td>
      </tr>)}</tbody></table></div>
      <div className="finance-actions"><span>{rows.data.total} source rows</span><SAButton size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</SAButton><span>Page {page + 1}</span><SAButton size="sm" disabled={(page + 1) * 50 >= rows.data.total} onClick={() => setPage(page + 1)}>Next</SAButton></div></>}
    {selectedId && <SABentoCard className="finance-detail">
      <div className="finance-section-head"><h2>Workbook evidence</h2><SAButton size="sm" onClick={() => setSelectedId("")}>Close</SAButton></div>
      {detail.isPending ? <SkeletonCard/> : detail.isError || !selected ? <p role="alert">Could not load source evidence.</p> : <>
        <p>{selected.sheet}!{selected.sourceRange} · {selected.venue ?? "Venue not recorded"} · {selected.client ?? "Client not recorded"}</p>
        <div className="finance-kpis"><div><span>Contracted</span><strong>{money(selected.total)}</strong></div><div><span>Add + payment</span><strong>{money(selected.add)} + {money(selected.payment)}</strong></div><div><span>Received</span><strong>{money(selected.received)}</strong></div><div><span>Outstanding</span><strong>{money(selected.outstanding)}</strong></div><div><span>Expense</span><strong>{money(selected.expense)}</strong></div><div><span>Workbook result</span><strong>{money(selected.legacyBudget)}</strong></div></div>
        <p>Balance: {selected.balanceParity}{selected.sourceBalance !== null && ` · source ${money(selected.sourceBalance)}`}. Result: {selected.budgetParity}{selected.sourceBudget !== null && ` · source ${money(selected.sourceBudget)}`}.</p>
        <details><summary>Original cells and formulas</summary><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Cell</th><th>Value</th><th>Formula</th></tr></thead><tbody>
          {Object.entries(selected.cells).map(([cell, value]) => <tr key={cell}><td>{cell}{selected.sourceRow}</td><td>{String(value)}</td><td>{selected.formulas[cell] ?? "—"}</td></tr>)}
        </tbody></table></div></details>
        {selected.sourceFacts.length > 0 && <p>Financial facts: {selected.sourceFacts.map(fact => `${fact.event_type} (${fact.classification})`).join(", ")}</p>}
        {(selected.employeeEvidence ?? []).length > 0 && <><h3>Staff earnings and payments</h3><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Employee</th><th>Type</th><th>Amount</th><th>Source</th><th></th></tr></thead><tbody>
          {(selected.employeeEvidence ?? []).map(item => <tr key={item.id}><td>{item.name}</td><td>{item.kind === "EARNING" ? "Earned" : "Paid"}{item.identityStatus === "IDENTITY_REVIEW" ? " · identity review" : ""}</td><td>{money(item.amount)}</td><td>{item.sourceColumn}{selected.sourceRow}</td><td><Link to={`/finance?workbookEmployee=${item.employeeKey}`}>Staff history →</Link></td></tr>)}
        </tbody></table></div></>}
        {(selected.partyEvidence ?? []).length > 0 && <><h3>Party ledger evidence</h3>{selected.partyEvidence.map(item => <p key={item.entryId}>{item.relationType.replaceAll("_", " ")} · {money(item.amount)} · <Link to={`/finance?workbookParty=${encodeURIComponent(item.partyKey)}&partyEntry=${item.entryId}`}>{item.partyName} →</Link></p>)}</>}
        {selected.linkedOwnerEvidence.length > 0 && <><h3>Owner ledger evidence</h3><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Account</th><th>Source</th><th>Matched amount</th><th>Evidence</th></tr></thead><tbody>
          {selected.linkedOwnerEvidence.map(link => <tr key={link.movementId}><td>{link.accountCode}</td><td><Link to={`/finance?owner=${link.accountCode.startsWith("AZ") ? "AZ" : "AK"}&movement=${link.movementId}`}>{link.sheet}!{link.sourceRange}{link.sourceCell ? ` · ${link.sourceCell}` : ""} →</Link></td><td>{money(link.amount)}</td><td>{link.confidence.replaceAll("_", " ")}</td></tr>)}
        </tbody></table></div></>}
      </>}
    </SABentoCard>}
  </section>;
}
