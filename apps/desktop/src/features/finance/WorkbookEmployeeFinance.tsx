import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, SAButton, SABentoCard, SABentoGrid, SkeletonCard } from "../../components/ui/sa";
import { financeApi } from "./finance.api";

const money = (value: number | null | undefined) => value == null ? "—" :
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);
const sheets = ["Jan 26", "JUL+DEC", "Jul-Dec", "jully dec P3 Led"];

export function WorkbookEmployeeFinance({ initialEmployee = "" }: { initialEmployee?: string }) {
  const [employee, setEmployee] = useState(initialEmployee);
  const [sheet, setSheet] = useState("");
  const [kind, setKind] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [eventId, setEventId] = useState("");
  const [sourceView, setSourceView] = useState(false);
  const [salaryOpen, setSalaryOpen] = useState(false);
  const [salaryPage, setSalaryPage] = useState(0);
  const summary = useQuery({ queryKey: ["finance", "workbook-employees"], queryFn: financeApi.workbookEmployees });
  const events = useQuery({ queryKey: ["finance", "workbook-employee-events", employee, sheet, kind, from, to, search, page],
    queryFn: () => financeApi.workbookEmployeeEvents({ employee, sheet, kind, from, to, search, page, size: 50 }) });
  const detail = useQuery({ queryKey: ["finance", "workbook-employee-event", eventId],
    queryFn: () => financeApi.workbookEmployeeEvent(eventId), enabled: !!eventId });
  const salary = useQuery({ queryKey: ["finance", "workbook-salary-evidence", salaryPage],
    queryFn: () => financeApi.workbookSalaryEvidence(salaryPage), enabled: salaryOpen });
  const choose = (key: string) => { setEmployee(key); setPage(0); setEventId(""); };

  return <section className="finance-section" aria-label="Historical workbook staff finance">
    <div className="finance-section-head"><div><span className="eyebrow">Original workbook · historical evidence</span><h2>Employee Finance</h2>
      <p>Work earnings and payments are separate. Historical workbook totals are not posted again or added to the current journal.</p></div>
      <SAButton size="sm" onClick={() => setSourceView(!sourceView)}>{sourceView ? "Normalized view" : "Workbook view"}</SAButton></div>
    {summary.isPending ? <SkeletonCard/> : summary.isError ? <EmptyState title="Staff history unavailable" description="The workbook staff projection could not be loaded." action={<SAButton onClick={() => summary.refetch()}>Retry</SAButton>}/>
      : !summary.data?.available ? <EmptyState title="No workbook loaded" description="Load the historical workbook in Import review to inspect staff evidence."/>
      : <><SABentoGrid className="finance-metrics">
          <SABentoCard><span className="eyebrow">Source earnings</span><h2>{money(summary.data.earned)}</h2><small>Formula-paired staff columns only</small></SABentoCard>
          <SABentoCard><span className="eyebrow">Source payments</span><h2>{money(summary.data.paid)}</h2><small>Not a second production expense</small></SABentoCard>
          <SABentoCard><span className="eyebrow">Historical balance</span><h2>{money(summary.data.outstanding)}</h2><small>Earnings less payments · not canonical payable</small></SABentoCard>
        </SABentoGrid>
        <SABentoCard><span className="eyebrow">Staff sources</span><div className="finance-actions" role="group" aria-label="Choose employee">
          <SAButton size="sm" variant={!employee ? "primary" : "secondary"} onClick={() => choose("")}>All staff</SAButton>
          {summary.data.employees.map(person => <SAButton key={person.key} size="sm" variant={employee === person.key ? "primary" : "secondary"} onClick={() => choose(person.key)}>{person.name}</SAButton>)}
        </div>{employee && (() => { const person = summary.data.employees.find(item => item.key === employee); return person ? <div className="finance-kpis">
          <div><span>Earned</span><strong>{money(person.earned)}</strong></div><div><span>Paid</span><strong>{money(person.paid)}</strong></div>
          <div><span>Remaining</span><strong>{money(person.outstanding)}</strong></div></div> : null; })()}
          {summary.data.reviewFacts > 0 && <p role="status">{summary.data.reviewFacts} payment cells have conflicting legacy identity labels. They remain visible below but are not assigned to a person.</p>}
          <p>{summary.data.linkedPayments} source payments have proven owner-ledger links. Unlinked payments remain visible without an invented payer.</p>
        </SABentoCard>
        <div className="finance-actions finance-owner-filters">
          <input className="finance-search" aria-label="Search staff workbook events" placeholder="Search employee, venue, client, amount" value={search} onChange={event => { setSearch(event.target.value); setPage(0); }}/>
          <label>Sheet <select value={sheet} onChange={event => { setSheet(event.target.value); setPage(0); }}><option value="">All</option>{sheets.map(value => <option key={value}>{value}</option>)}</select></label>
          <label>Type <select value={kind} onChange={event => { setKind(event.target.value); setPage(0); }}><option value="">All</option><option value="EARNING">Earning</option><option value="PAYMENT">Payment</option></select></label>
          <label>From <input type="date" value={from} onChange={event => { setFrom(event.target.value); setPage(0); }}/></label>
          <label>To <input type="date" value={to} onChange={event => { setTo(event.target.value); setPage(0); }}/></label>
        </div>
        {events.isPending ? <SkeletonCard/> : events.isError ? <EmptyState title="Staff events unavailable" description="Could not load the selected workbook history." action={<SAButton onClick={() => events.refetch()}>Retry</SAButton>}/>
          : !events.data?.total ? <EmptyState title="No matching staff events" description="Try another staff member or filter."/>
          : <><div className="finance-table-wrap"><table className="finance-table"><thead><tr>{(sourceView ? ["Source", "Date", "Raw heading", "Cell", "Amount", "Balance", "Identity", ""] : ["Date", "Employee", "Type", "Amount", "Balance", "Production", "Source", ""]).map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>
            {events.data.items.map(item => <tr key={item.id}>
              <td>{sourceView ? `${item.sheet} · row ${item.sourceRow}` : item.date ?? "—"}</td>
              <td>{sourceView ? item.date ?? "—" : item.employeeName}</td>
              <td>{sourceView ? item.sourceHeading ?? "—" : item.kind === "EARNING" ? "Earned" : "Paid"}</td>
              <td>{sourceView ? `${item.sourceColumn}${item.sourceRow}` : money(item.amount)}</td>
              <td>{sourceView ? money(item.amount) : money(item.balanceAfter)}</td>
              <td>{sourceView ? money(item.balanceAfter) : item.venue ?? item.client ?? "No production recorded"}</td>
              <td>{sourceView ? item.identityStatus.replaceAll("_", " ") : `${item.sheet}!${item.sourceColumn}${item.sourceRow}`}</td>
              <td><SAButton size="sm" onClick={() => setEventId(item.id)}>Evidence</SAButton></td>
            </tr>)}</tbody></table></div><div className="finance-actions"><span>{events.data.total} source events · Page {page + 1}</span>
              <SAButton size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</SAButton>
              <SAButton size="sm" disabled={(page + 1) * 50 >= events.data.total} onClick={() => setPage(page + 1)}>Next</SAButton></div></>}
        <details><summary>Source-column parity</summary><p>Each column is compared with its workbook total; disputed payee labels remain unassigned.</p>
          <div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Sheet</th><th>Staff / type</th><th>Cell</th><th>Workbook</th><th>Projection</th><th>Difference</th></tr></thead><tbody>
            {summary.data.parity.map(item => <tr key={`${item.sheet}-${item.column}`}><td>{item.sheet}</td><td>{item.employee} · {item.kind}</td><td>{item.column}3</td><td>{money(item.workbook)}</td><td>{money(item.projection)}</td><td>{money(item.difference)}</td></tr>)}
          </tbody></table></div></details>
        <SABentoCard><span className="eyebrow">Separate source evidence</span><h3>Month-labelled expenses</h3>
          <p>The workbook has rows labelled “Month” in its expense column. They are not treated as confirmed salary obligations or payments without settlement evidence.</p>
          <SAButton size="sm" onClick={() => setSalaryOpen(!salaryOpen)}>{salaryOpen ? "Hide source rows" : "Inspect source rows"}</SAButton>
          {salaryOpen && (salary.isPending ? <SkeletonCard/> : salary.isError ? <p role="alert">Month-labelled rows could not be loaded.</p> : <>
            <div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Date</th><th>Raw name</th><th>Source expense</th><th>Workbook</th></tr></thead><tbody>
              {salary.data?.items.map(item => <tr key={item.productionRowId}><td>{item.date ?? "—"}</td><td>{item.name ?? "—"}</td><td>{money(item.amount)}</td><td><Link to={`/finance?workbookProduction=${item.productionRowId}`}>{item.sheet}!I{item.sourceRow} →</Link></td></tr>)}
            </tbody></table></div><div className="finance-actions"><span>{salary.data?.total ?? 0} source rows · Page {salaryPage + 1}</span>
              <SAButton size="sm" disabled={salaryPage === 0} onClick={() => setSalaryPage(salaryPage - 1)}>Previous</SAButton>
              <SAButton size="sm" disabled={(salaryPage + 1) * 50 >= (salary.data?.total ?? 0)} onClick={() => setSalaryPage(salaryPage + 1)}>Next</SAButton></div></>)}
        </SABentoCard>
      </>}
    {eventId && <SABentoCard className="finance-detail" aria-label="Staff event evidence"><div className="finance-section-head"><h2>Staff source event</h2><SAButton size="sm" onClick={() => setEventId("")}>Close</SAButton></div>
      {detail.isPending ? <SkeletonCard/> : detail.isError || !detail.data ? <p role="alert">Staff source evidence could not be loaded.</p> : <>
        <p>{detail.data.employeeName} · {detail.data.kind === "EARNING" ? "Earned" : "Paid"} {money(detail.data.amount)} · {detail.data.date ?? "Date not recorded"}</p>
        <p>{detail.data.sheet}!{detail.data.sourceColumn}{detail.data.sourceRow} · {detail.data.sourceHeading} · {detail.data.identityStatus.replaceAll("_", " ")}</p>
        <p>Workbook SHA-256: {detail.data.workbookSha256}</p>
        {detail.data.venue && <p>Production: <Link to={`/finance?workbookProduction=${detail.data.productionRowId}`}>{detail.data.venue} {detail.data.client ?? ""} →</Link></p>}
        {detail.data.linkedOwnerEvidence.length ? <div><h3>Proven owner payment evidence</h3>{detail.data.linkedOwnerEvidence.map(link => <p key={link.movementId}><Link to={`/finance?owner=${link.accountCode.startsWith("AZ") ? "AZ" : "AK"}&movement=${link.movementId}`}>{link.accountCode} · {link.sheet}!{link.sourceCell} →</Link> · {money(link.amount)} · {link.confidence}</p>)}</div>
          : detail.data.kind === "PAYMENT" ? <p>Paying account not proven by a unique owner-ledger match.</p> : <p>Work earning is an obligation, not a cash movement.</p>}
        <details><summary>Original row cells and formulas</summary><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Cell</th><th>Value</th><th>Formula</th></tr></thead><tbody>
          {Object.entries(detail.data.raw.cells ?? {}).map(([column,value]) => <tr key={column}><td>{column}{detail.data.sourceRow}</td><td>{String(value)}</td><td>{detail.data.raw.formulas?.[column] ?? "—"}</td></tr>)}
        </tbody></table></div></details>
      </>}
    </SABentoCard>}
  </section>;
}
