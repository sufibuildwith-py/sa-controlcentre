import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, SAButton, SABentoCard, SABentoGrid, SkeletonCard } from "../../components/ui/sa";
import { financeApi } from "./finance.api";

const money = (value: number | null | undefined) => value == null ? "—" :
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
const sourceLabel = (sheet: string, slot: string, direction: "IN" | "OUT") =>
  sheet === "az 26" ? `${slot.includes("CASH") ? "Cash" : "Bank"} ${direction === "IN" ? "in" : "out"}`
    : sheet === "az-2" ? direction === "IN" ? "Credit" : "Expense"
    : direction === "IN" ? "From" : "To";

export function WorkbookOwnerAccounts({ initialOwner = "", initialMovement = "" }: { initialOwner?: string; initialMovement?: string }) {
  const [view, setView] = useState<"SUMMARY" | "AZ" | "AK" | "TRANSFERS">(
    initialOwner === "AZ" || initialOwner === "AK" ? initialOwner : "SUMMARY");
  const [movementId, setMovementId] = useState(initialMovement);
  const [month, setMonth] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [direction, setDirection] = useState("");
  const [status, setStatus] = useState("");
  const [businessType, setBusinessType] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [sourceView, setSourceView] = useState(false);
  const summary = useQuery({ queryKey: ["finance", "workbook-owners"], queryFn: financeApi.workbookOwners });
  const owner = summary.data?.owners.find(item => item.code === view);
  const movements = useQuery({ queryKey: ["finance", "owner-movements", view, month, from, to, direction, status, businessType, search, page],
    queryFn: () => financeApi.workbookOwnerMovements(view as "AZ" | "AK", { month, from, to, direction, status, businessType, search, page, size: 50 }),
    enabled: view === "AZ" || view === "AK" });
  const transfers = useQuery({ queryKey: ["finance", "owner-transfers", page], queryFn: () => financeApi.workbookTransfers(page), enabled: view === "TRANSFERS" });
  const detail = useQuery({ queryKey: ["finance", "owner-movement", movementId], queryFn: () => financeApi.workbookOwnerMovement(movementId), enabled: !!movementId });
  const choose = (next: typeof view) => { setView(next); setPage(0); setMovementId(""); setMonth(""); setSearch(""); };
  const resetPage = (change: () => void) => { change(); setPage(0); };

  return <section className="finance-section" aria-label="Workbook owner accounts">
    <div className="finance-section-head"><div><span className="eyebrow">Historical workbook position</span><h2>Owner Accounts</h2>
      <p>Two operating positions, four source ledgers. Historical source movements are linked, never posted again.</p></div></div>
    <div className="finance-actions" role="group" aria-label="Owner account view">
      {([ ["SUMMARY", "Overview"], ["AZ", "Azeem / AZ"], ["AK", "Akash / AK"], ["TRANSFERS", "Transfers"] ] as const)
        .map(([value, label]) => <SAButton key={value} size="sm" variant={view === value ? "primary" : "secondary"} onClick={() => choose(value)}>{label}</SAButton>)}
    </div>
    {summary.isPending ? <SkeletonCard/> : summary.isError ? <EmptyState title="Owner positions unavailable" description="The workbook position could not be loaded." action={<SAButton onClick={() => summary.refetch()}>Retry</SAButton>}/>
      : !summary.data?.available ? <EmptyState title="No workbook loaded" description="Load the historical workbook to inspect real owner positions."/>
      : <>
        {view === "SUMMARY" && <>
          <SABentoGrid className="finance-metrics">{summary.data.owners.map(account => <SABentoCard key={account.code} className="finance-owner-card">
            <span className="eyebrow">{account.name} · {account.code}</span><strong className={account.position < 0 ? "finance-negative" : ""}>{money(account.position)}</strong>
            <small>Current position · workbook-backed</small><div className="finance-kpis"><div><span>{account.code === "AZ" ? "Credits incl. opening" : "Money in"}</span><strong>{money(account.moneyIn)}</strong></div><div><span>Money out</span><strong>{money(account.moneyOut)}</strong></div></div>
            <SAButton size="sm" onClick={() => choose(account.code)}>Open {account.code} →</SAButton>
          </SABentoCard>)}</SABentoGrid>
          <SABentoCard><div className="finance-kpis"><div><span>Internal transfers</span><strong>{summary.data.transferCount} · {money(summary.data.transferTotal)}</strong></div>
            <div><span>Proven business links</span><strong>{summary.data.owners.reduce((sum, item) => sum + item.linkedMovements, 0)}</strong></div>
            <div><span>Unlinked legacy movements</span><strong>{summary.data.owners.reduce((sum, item) => sum + item.unlinkedMovements, 0)}</strong></div></div></SABentoCard>
        </>}
        {owner && <>
          <SABentoCard><span className="eyebrow">{owner.name} · {owner.code}</span><h2 className={owner.position < 0 ? "finance-negative" : ""}>{money(owner.position)}</h2><p>Current position, not a debt or loss classification.</p>
            <div className="finance-kpis"><div><span>Earlier position</span><strong>{money(owner.previousPosition)}</strong></div><div><span>Later opening reference</span><strong>{money(owner.openingReference)}</strong></div>
              <div><span>{owner.code === "AZ" ? "Credits incl. opening" : "Money in"}</span><strong>{money(owner.moneyIn)}</strong></div><div><span>Money out</span><strong>{money(owner.moneyOut)}</strong></div></div>
            {owner.code === "AZ" && <p>New inflows excluding opening: {money(owner.newMoneyIn)}. The carry-forward is counted once.</p>}
            <p>{owner.continuity === "VERIFIED" ? "Earlier closing position carries into the later ledger; opening is not new income." : "Later opening reference does not match earlier closing position; continuity remains unproven."}</p>
          </SABentoCard>
          <SABentoCard><span className="eyebrow">Workbook segments</span><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Period</th><th>Source</th><th>Money in</th><th>Money out</th><th>Workbook position</th><th>Difference</th></tr></thead><tbody>
            {owner.segments.map(segment => <tr key={`${segment.sheet}-${segment.block}`}><td>{segment.label}</td><td>{segment.sheet}</td><td>{money(segment.moneyIn)}</td><td>{money(segment.moneyOut)}</td><td>{money(segment.workbookPosition)}</td><td>{money(segment.difference)}</td></tr>)}
          </tbody></table></div></SABentoCard>
          <div className="finance-section-head"><h2>Movements</h2><SAButton size="sm" onClick={() => setSourceView(!sourceView)}>{sourceView ? "Normalized view" : "Workbook view"}</SAButton></div>
          <div className="finance-actions finance-owner-filters">
            <input className="finance-search" aria-label="Search owner movements" placeholder="Search name, amount or source" value={search} onChange={event => resetPage(() => setSearch(event.target.value))}/>
            <label>Month <select value={month} onChange={event => resetPage(() => setMonth(event.target.value))}><option value="">All</option>{owner.segments.filter(item => item.monthKey).map(item => <option key={`${item.sheet}-${item.block}`} value={item.monthKey}>{item.label} · {item.sheet}</option>)}</select></label>
            <label>From <input type="date" value={from} onChange={event => resetPage(() => setFrom(event.target.value))}/></label>
            <label>To <input type="date" value={to} onChange={event => resetPage(() => setTo(event.target.value))}/></label>
            <label>Direction <select value={direction} onChange={event => resetPage(() => setDirection(event.target.value))}><option value="">All</option><option value="IN">Money in</option><option value="OUT">Money out</option></select></label>
            <label>Link <select value={status} onChange={event => resetPage(() => setStatus(event.target.value))}><option value="">All</option>{["LINKED", "LEGACY", "REVIEW_REQUIRED", "TRANSFER"].map(value => <option key={value}>{value}</option>)}</select></label>
            <label>Business <select value={businessType} onChange={event => resetPage(() => setBusinessType(event.target.value))}><option value="">All</option>{["PRODUCTION", "EMPLOYEE", "PARTY", "EQUIPMENT", "EXPENSE", "INVOICE", "TRANSFER", "OTHER"].map(value => <option key={value}>{value}</option>)}</select></label>
          </div>
          {movements.isPending ? <SkeletonCard/> : movements.isError ? <EmptyState title="Movements unavailable" description="The account source could not be loaded." action={<SAButton onClick={() => movements.refetch()}>Retry</SAButton>}/>
            : !movements.data?.total ? <EmptyState title="No matching movements" description="Try another period or filter."/>
            : <><div className="finance-table-wrap"><table className="finance-table"><thead><tr>{(sourceView ? ["Source", "Date", "Raw name", "From / Credit", "To / Expense", "Source position", "Status", ""] : ["Date", "Description", "Money in", "Money out", "Position", "Linked to", "Source", ""]).map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>
              {movements.data.items.map(row => <tr key={row.id}><td>{sourceView ? `${row.sheet} · ${row.period} · ${row.sourceCell ?? row.sourceRange}` : row.date ?? "—"}</td><td>{sourceView ? row.date ?? "—" : row.description ?? "—"}</td>
                <td>{sourceView ? row.description ?? "—" : row.direction === "IN" ? money(row.amount) : "—"}</td><td>{sourceView ? row.direction === "IN" ? <>{money(row.amount)}<small>{sourceLabel(row.sheet, row.slot, row.direction)}</small></> : "—" : row.direction === "OUT" ? money(row.amount) : "—"}</td>
                <td>{sourceView ? row.direction === "OUT" ? <>{money(row.amount)}<small>{sourceLabel(row.sheet, row.slot, row.direction)}</small></> : "—" : money(row.positionAfter)}</td><td>{sourceView ? money(row.positionAfter) : `${row.businessType} · ${row.status.replaceAll("_", " ")}`}</td>
                <td>{sourceView ? row.status.replaceAll("_", " ") : `${row.sheet} · ${row.period} · ${row.sourceCell ?? row.sourceRange}`}</td><td><SAButton size="sm" onClick={() => setMovementId(row.id)}>Evidence</SAButton></td></tr>)}
            </tbody></table></div><div className="finance-actions"><span>{movements.data.total} movements · Page {page + 1}</span><SAButton size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</SAButton><SAButton size="sm" disabled={(page + 1) * 50 >= movements.data.total} onClick={() => setPage(page + 1)}>Next</SAButton></div></>}
        </>}
        {view === "TRANSFERS" && <><SABentoCard><span className="eyebrow">One event, two source rows</span><h2>{summary.data.transferCount} internal transfers</h2><p>AZ and AK workbook entries are evidence of one transfer, not two independent business effects.</p></SABentoCard>
          {transfers.isPending ? <SkeletonCard/> : transfers.isError ? <EmptyState title="Transfers unavailable" description="Could not load transfer evidence."/> : <><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Date</th><th>From</th><th>To</th><th>Amount</th><th>AZ source</th><th>AK source</th><th></th></tr></thead><tbody>{transfers.data?.items.map(item => <tr key={item.id}><td>{item.date ?? "—"}</td><td>{item.fromAccount}</td><td>{item.toAccount}</td><td>{money(item.amount)}</td><td>{item.primarySheet}!{item.primaryRange}</td><td>{item.otherSheet}!{item.otherRange}</td><td><SAButton size="sm" onClick={() => setMovementId(item.primaryMovementId)}>Evidence</SAButton></td></tr>)}</tbody></table></div><div className="finance-actions"><SAButton size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</SAButton><span>Page {page + 1}</span><SAButton size="sm" disabled={(page + 1) * 50 >= (transfers.data?.total ?? 0)} onClick={() => setPage(page + 1)}>Next</SAButton></div></>}
        </>}
        {view === "SUMMARY" && <SABentoCard><span className="eyebrow">Source parity</span><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Control</th><th>Workbook</th><th>SA Command</th><th>Difference</th><th>Status</th></tr></thead><tbody>{summary.data.parity.map(item => <tr key={item.metric}><td>{item.metric}</td><td>{money(item.workbook)}</td><td>{money(item.projection)}</td><td>{money(item.difference)}</td><td>{item.status}</td></tr>)}</tbody></table></div></SABentoCard>}
      </>}
    {movementId && <SABentoCard className="finance-detail" aria-label="Owner movement detail"><div className="finance-section-head"><h2>Account movement</h2><SAButton size="sm" onClick={() => setMovementId("")}>Close</SAButton></div>
      {detail.isPending ? <SkeletonCard/> : detail.isError || !detail.data ? <p role="alert">Source movement could not be loaded.</p> : <><p>{detail.data.accountCode} · {detail.data.date ?? "Date not recorded"} · {detail.data.description ?? "Description not recorded"}</p>
        <div className="finance-kpis"><div><span>{detail.data.direction === "IN" ? "Money in" : "Money out"}</span><strong>{money(detail.data.amount)}</strong></div><div><span>Position after</span><strong>{money(detail.data.positionAfter)}</strong></div><div><span>Link status</span><strong>{detail.data.status.replaceAll("_", " ")}</strong></div></div>
        <p>Workbook: {detail.data.sheet} · {detail.data.period} · {detail.data.sourceCell ?? detail.data.sourceRange} · Row {detail.data.sourceRow}</p>
        {detail.data.linkedBusiness.length > 0 ? <div><h3>Linked business evidence</h3>{detail.data.linkedBusiness.map(link => <p key={link.factId}>{link.eventType.replaceAll("_", " ")} · {link.venue ?? link.client ?? ""} · {link.sheet}!{link.sourceRange} · {money(link.amount)} {link.employeeKey ? <Link to={`/finance?workbookEmployee=${link.employeeKey}`}>Open staff history →</Link> : link.sheet === "JUL+DEC" || link.sheet === "Jan 26" || link.sheet === "All Date 26" || link.sheet === "Jul-Dec" || link.sheet === "jully dec P3 Led" ? <Link to={`/finance?workbookProduction=${link.rowId}`}>Open production →</Link> : <span>Source link retained</span>}</p>)}</div> : <p>Unresolved legacy purpose; this account movement remains visible.</p>}
        {(detail.data.partyEvidence ?? []).length > 0 && <div><h3>Party ledger evidence</h3>{detail.data.partyEvidence.map(item => <p key={item.entryId}>{money(item.amount)} · <Link to={`/finance?workbookParty=${encodeURIComponent(item.partyKey)}&partyEntry=${item.entryId}`}>{item.partyName} →</Link></p>)}</div>}
        {detail.data.transferPartner.length > 0 && <div><h3>Matched transfer evidence</h3>{detail.data.transferPartner.map(partner => <p key={partner.movementId}>{partner.confidence} · <SAButton size="sm" onClick={() => setMovementId(partner.movementId)}>Open paired source</SAButton><details><summary>Match reasoning</summary><pre>{partner.evidence}</pre></details></p>)}</div>}
        <details><summary>Original workbook cells and formulas</summary><p>Workbook SHA-256: {detail.data.workbookSha256}</p><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Cell</th><th>Value</th><th>Formula</th></tr></thead><tbody>{Object.entries(detail.data.raw.cells ?? {}).map(([cell, value]) => <tr key={cell}><td>{cell}{detail.data.sourceRow}</td><td>{String(value)}</td><td>{detail.data.raw.formulas?.[cell] ?? "—"}</td></tr>)}</tbody></table></div></details>
      </>}
    </SABentoCard>}
  </section>;
}
