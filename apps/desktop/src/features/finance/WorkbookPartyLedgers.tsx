import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, SAButton, SABentoCard, SkeletonCard } from "../../components/ui/sa";
import { financeApi } from "./finance.api";

const money = (value: number | null | undefined) => value == null ? "—" :
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value);

export function WorkbookPartyLedgers({ initialParty = "", initialEntry = "" }: { initialParty?: string; initialEntry?: string }) {
  const [key, setKey] = useState(initialParty);
  const [selectedEntry, setSelectedEntry] = useState(initialEntry);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [link, setLink] = useState("");
  const [block, setBlock] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [view, setView] = useState<"NORMALIZED" | "WORKBOOK">("NORMALIZED");
  const summary = useQuery({ queryKey: ["finance", "workbook-party-summary"], queryFn: financeApi.workbookPartySummary });
  const parties = useQuery({ queryKey: ["finance", "workbook-parties", search, status, link, page],
    queryFn: () => financeApi.workbookParties({ search, status, link, page, size: 50 }), enabled: !key });
  const party = useQuery({ queryKey: ["finance", "workbook-party", key], queryFn: () => financeApi.workbookParty(key), enabled: !!key });
  const entries = useQuery({ queryKey: ["finance", "workbook-party-entries", key, block, from, to, search, page],
    queryFn: () => financeApi.workbookPartyEntries(key, { block, from, to, search, page, size: 50 }), enabled: !!key });
  const detail = useQuery({ queryKey: ["finance", "workbook-party-entry", selectedEntry],
    queryFn: () => financeApi.workbookPartyEntry(selectedEntry), enabled: !!selectedEntry });
  const openParty = (next: string) => { setKey(next); setBlock(""); setSearch(""); setPage(0); setSelectedEntry(""); };
  const current = detail.data;

  return <section className="finance-section" aria-label="Historical party ledgers">
    <div className="finance-section-head"><div><span className="eyebrow">Original workbook · शीट4</span><h2>{key ? party.data?.name ?? "Party ledger" : "Party ledgers"}</h2>
      <p>Historical business, receipts and balances. Workbook evidence is read-only and separate from posted Finance.</p></div>
      {key && <SAButton size="sm" onClick={() => openParty("")}>All parties</SAButton>}</div>
    {summary.isPending ? <SkeletonCard/> : summary.isError ? <EmptyState title="Party history unavailable" description="Could not load the workbook projection." action={<SAButton onClick={() => summary.refetch()}>Retry</SAButton>}/> : !summary.data?.available ?
      <EmptyState title="No party workbook loaded" description="Load the historical workbook in Finance import review."/> : <>
      <SABentoCard><span className="eyebrow">Party ledger position · not consolidated company receivables</span>
        <div className="finance-kpis"><div><span>Business</span><strong>{money(key ? party.data?.business : summary.data.business)}</strong></div>
          <div><span>Received</span><strong>{money(key ? party.data?.received : summary.data.received)}</strong></div>
          <div><span>Outstanding</span><strong>{money(key ? party.data?.outstanding : summary.data.outstanding)}</strong></div>
          {!key && <><div><span>Outstanding parties</span><strong>{summary.data.outstandingParties}</strong></div>
            <div><span>Settled</span><strong>{summary.data.settledParties}</strong></div></>}</div>
        {!key && <small>{summary.data.partyBlocks} source blocks · {summary.data.duplicateBlocks} duplicate snapshot excluded from totals · {summary.data.parityMismatches} block parity differences · {summary.data.unlinkedPayments} payments without proven owner account. Workbook Payment includes {money(summary.data.discountSettlement)} explicitly labelled Discount, not confirmed cash.</small>}
      </SABentoCard>
      <div className="finance-actions">
        <input className="finance-search" aria-label="Search party history" placeholder="Search party, venue, service or amount" value={search} onChange={event => {setSearch(event.target.value);setPage(0);}}/>
        {!key ? <><label>Balance <select value={status} onChange={event => {setStatus(event.target.value);setPage(0);}}><option value="">All</option><option value="OUTSTANDING">Outstanding</option><option value="SETTLED">Settled</option><option value="NEGATIVE">Negative</option></select></label>
          <label>Evidence <select value={link} onChange={event => {setLink(event.target.value);setPage(0);}}><option value="">All</option><option value="PRODUCTION">Production linked</option><option value="OWNER">Owner linked</option><option value="FULLY_LINKED">Both</option><option value="LEGACY">Legacy</option></select></label></>
          : <><label>Source block <select value={block} onChange={event => {setBlock(event.target.value);setPage(0);}}><option value="">All blocks</option>{party.data?.blocks.map(item => <option value={item.id} key={item.id}>Block {item.blockIndex}{item.disposition === "DUPLICATE_SNAPSHOT" ? " · duplicate evidence" : ""}</option>)}</select></label>
            <label>From <input type="date" value={from} onChange={event => {setFrom(event.target.value);setPage(0);}}/></label><label>To <input type="date" value={to} onChange={event => {setTo(event.target.value);setPage(0);}}/></label>
            <SAButton size="sm" onClick={() => setView(view === "NORMALIZED" ? "WORKBOOK" : "NORMALIZED")}>{view === "NORMALIZED" ? "Workbook view" : "Normalized view"}</SAButton></>}
      </div>
      {!key ? parties.isPending ? <SkeletonCard/> : parties.isError ? <EmptyState title="Party list unavailable" description="Could not load party history." action={<SAButton onClick={() => parties.refetch()}>Retry</SAButton>}/> :
        <><div className="finance-table-wrap"><table className="finance-table"><thead><tr>{["Party","Business","Received","Outstanding","Entries","Last activity","Links",""].map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>
          {(parties.data?.items ?? []).map(item => <tr key={item.key}><td><strong>{item.name}</strong><small>{item.blocks} source blocks</small></td><td>{money(item.business)}</td><td>{money(item.received)}</td><td>{money(item.outstanding)}</td><td>{item.entries}</td><td>{item.lastActivity ?? "Legacy date"}</td><td>{item.productionLinks} production · {item.ownerLinks} owner</td><td><SAButton size="sm" onClick={() => openParty(item.key)}>Ledger</SAButton></td></tr>)}</tbody></table></div>
          <Page page={page} total={parties.data?.total ?? 0} onPage={setPage}/></> :
        <>{party.isPending ? <SkeletonCard/> : party.isError ? <p role="alert">Could not load this party.</p> : <>
          <SABentoCard><span className="eyebrow">Source blocks and parity</span><div className="finance-table-wrap"><table className="finance-table"><thead><tr>{["Block","Source","Excel amount","Calculated amount","Excel payment","Calculated payment","Excel balance","Calculated balance","Status"].map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>{party.data?.blocks.map(item => <tr key={item.id}><td>{item.blockIndex}</td><td>{item.sourceRange}<small>{item.disposition === "DUPLICATE_SNAPSHOT" ? "Duplicate evidence · excluded from totals" : item.disposition}</small></td><td>{money(item.workbookAmount)}</td><td>{money(item.projectionAmount)}</td><td>{money(item.workbookPayment)}</td><td>{money(item.projectionPayment)}</td><td>{money(item.workbookBalance)}</td><td>{money(item.projectionBalance)}</td><td>{item.parityStatus}</td></tr>)}</tbody></table></div><details><summary>Original block headings and control formulas</summary>{party.data?.blocks.map(item => <p key={item.id}>Block {item.blockIndex}: {Object.entries(item.layout?.columns ?? {}).map(([heading,column]) => `${column} ${heading}`).join(" · ")}<br/>{Object.entries(item.layout?.controlFormulas ?? {}).map(([column,formula]) => `${column}3 ${formula}`).join(" · ") || "Formula not staged"}</p>)}</details></SABentoCard>
          {entries.isPending ? <SkeletonCard/> : entries.isError ? <EmptyState title="Party entries unavailable" description="Could not load this ledger." action={<SAButton onClick={() => entries.refetch()}>Retry</SAButton>}/> : <><div className="finance-table-wrap"><table className="finance-table"><thead><tr>{["Date","Venue / source","Service","Rate","Amount","Payment","Block balance","Links",""].map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>{entries.data?.items.map(item => <tr key={item.id}><td>{item.rawDate ?? "—"}</td><td><strong>{item.venue ?? "—"}</strong><small>{view === "WORKBOOK" ? `शीट4 · ${item.sourceRow} · block ${item.blockIndex}` : item.disposition === "DUPLICATE_SNAPSHOT" ? "Duplicate source" : ""}</small></td><td>{item.service ?? "—"}</td><td>{item.rate ?? "—"}</td><td>{money(item.amount)}</td><td>{money(item.payment)}</td><td>{money(item.balanceAfter)}</td><td>{item.productionLinks} production · {item.ownerLinks} owner</td><td><SAButton size="sm" onClick={() => setSelectedEntry(item.id)}>Evidence</SAButton></td></tr>)}</tbody></table></div><Page page={page} total={entries.data?.total ?? 0} onPage={setPage}/></>}
        </>}</>}
      {selectedEntry && <SABentoCard className="finance-detail"><div className="finance-section-head"><h2>Party source evidence</h2><SAButton size="sm" onClick={() => setSelectedEntry("")}>Close</SAButton></div>
        {detail.isPending ? <SkeletonCard/> : detail.isError || !current ? <p role="alert">Could not load source evidence.</p> : <>
          <p>{current.partyName} · {current.rawDate ?? "Legacy date"} · {current.venue ?? "Venue not recorded"}</p>
          <div className="finance-kpis"><div><span>Business amount</span><strong>{money(current.amount)}</strong></div><div><span>Payment</span><strong>{money(current.payment)}</strong></div><div><span>Block balance after</span><strong>{money(current.balanceAfter)}</strong></div><div><span>Source</span><strong>Block {current.blockIndex} · row {current.sourceRow}</strong></div></div>
          {current.receiptEvidenceCount && <p>One historical receipt · {current.receiptEvidenceCount} workbook source{current.receiptEvidenceCount === 1 ? "" : "s"}. Linked evidence is not added to received again.</p>}
          {current.settlementKind === "DISCOUNT" && <p>Workbook Discount: reduces this legacy balance but is not classified as a cash receipt.</p>}
          {current.duplicateOfEntryId && <p>Duplicate workbook representation; excluded from party totals.</p>}
          {current.links.length ? <div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Linked evidence</th><th>Amount</th><th>Source</th></tr></thead><tbody>{current.links.map(item => <tr key={`${item.factId}-${item.relationType}`}><td>{item.relationType.replaceAll("_"," ")}</td><td>{money(item.amount)}</td><td>{item.accountCode ? <Link to={`/finance?owner=${item.accountCode.startsWith("AZ") ? "AZ" : "AK"}&movement=${item.factId}`}>{item.sheet}!{item.sourceCell} →</Link> : <Link to={`/finance?workbookProduction=${item.linkedRowId}`}>{item.sheet}!{item.sourceCell} →</Link>}</td></tr>)}</tbody></table></div> : <p>Unlinked legacy evidence. No production or owner identity has been assumed.</p>}
          <details><summary>Original cells and formulas</summary><p>Workbook SHA-256: {current.workbookSha256}</p><div className="finance-table-wrap"><table className="finance-table"><thead><tr><th>Cell</th><th>Value</th><th>Formula</th></tr></thead><tbody>{Object.entries(current.raw.cells ?? {}).map(([cell,value]) => <tr key={cell}><td>{cell}{current.sourceRow}</td><td>{String(value)}</td><td>{current.raw.formulas?.[cell] ?? "—"}</td></tr>)}</tbody></table></div></details>
        </>}
      </SABentoCard>}
    </>}
  </section>;
}

function Page({page,total,onPage}:{page:number;total:number;onPage:(next:number)=>void}) {
  return <div className="finance-actions"><span>{total} records · Page {page+1}</span><SAButton size="sm" disabled={page===0} onClick={()=>onPage(page-1)}>Previous</SAButton><SAButton size="sm" disabled={(page+1)*50>=total} onClick={()=>onPage(page+1)}>Next</SAButton></div>;
}
