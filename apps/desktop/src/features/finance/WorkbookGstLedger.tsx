import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SkeletonCard,
  StatusBadge,
} from "../../components/ui/sa";
import {
  financeAmount as money,
  financeApi,
  type WorkbookGstInvoice,
} from "./finance.api";

export function WorkbookGstLedger() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState("");
  const summary = useQuery({
    queryKey: ["finance", "workbook-gst-summary"],
    queryFn: financeApi.workbookGstSummary,
  });
  const invoices = useQuery({
    queryKey: ["finance", "workbook-gst", search, status, page],
    queryFn: () =>
      financeApi.workbookGstInvoices({ search, status, page, size: 50 }),
  });
  const detail = useQuery({
    queryKey: ["finance", "workbook-gst-invoice", selected],
    queryFn: () => financeApi.workbookGstInvoice(selected),
    enabled: !!selected,
  });
  if (summary.isPending) return <SkeletonCard />;
  if (summary.isError || !summary.data?.available)
    return (
      <SABentoCard>
        <EmptyState
          title="GST workbook unavailable"
          description="Load the private demo workbook to inspect historical GST records."
        />
      </SABentoCard>
    );
  const data = summary.data,
    current = detail.data;
  return (
    <section className="finance-workbook" aria-label="Workbook GST invoices">
      <div className="finance-section-head">
        <div>
          <p className="eyebrow">Historical workbook · GST</p>
          <h2>Invoices / GST</h2>
          <p>
            Tax, TDS and cash settlements remain distinct historical evidence.
          </p>
        </div>
        <StatusBadge tone={data.parityMismatches ? "warning" : "success"}>
          {data.parityMismatches
            ? `${data.parityMismatches} parity review`
            : "Workbook parity"}
        </StatusBadge>
      </div>
      <SABentoGrid className="finance-kpis">
        <SABentoCard>
          <span>Invoices</span>
          <strong>{data.invoiceCount}</strong>
          <small>Real GST rows</small>
        </SABentoCard>
        <SABentoCard>
          <span>Invoice total</span>
          <strong>{money(data.total)}</strong>
          <small>
            Base {money(data.base)} · Tax {money(data.tax)}
          </small>
        </SABentoCard>
        <SABentoCard>
          <span>Cash received</span>
          <strong>{money(data.cash)}</strong>
          <small>TDS reduction {money(data.tds)}</small>
        </SABentoCard>
        <SABentoCard>
          <span>Outstanding</span>
          <strong>{money(data.outstanding)}</strong>
          <small>Workbook-compatible</small>
        </SABentoCard>
      </SABentoGrid>
      <SABentoCard>
        <div className="finance-actions">
          <input
            aria-label="Search GST invoices"
            placeholder="Invoice, party, GSTIN or amount"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <select
            aria-label="GST status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All invoices</option>
            <option value="OUTSTANDING">Outstanding</option>
            <option value="SETTLED">Settled</option>
            <option value="LEGACY">Parity review</option>
          </select>
        </div>
        {invoices.isPending ? (
          <SkeletonCard />
        ) : invoices.isError ? (
          <p role="alert">Could not load GST invoices.</p>
        ) : !invoices.data?.items.length ? (
          <EmptyState
            title="No GST invoices match"
            description="Try a broader search or filter."
          />
        ) : (
          <>
            <div className="finance-table-wrap">
              <table className="finance-table">
                <thead>
                  <tr>
                    <th>Invoice</th>
                    <th>Party / GSTIN</th>
                    <th>Total</th>
                    <th>TDS</th>
                    <th>Cash</th>
                    <th>Outstanding</th>
                    <th>Links</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {invoices.data.items.map((item: WorkbookGstInvoice) => (
                    <tr key={item.id}>
                      <td>
                        {item.invoiceNumber}
                        <small>
                          {item.date ?? item.rawDate ?? "Legacy date"}
                        </small>
                      </td>
                      <td>
                        {item.partyName}
                        <small>{item.gstin ?? "GSTIN unavailable"}</small>
                      </td>
                      <td>{money(item.total)}</td>
                      <td>{money(item.tds)}</td>
                      <td>{money(item.cash)}</td>
                      <td>{money(item.outstanding)}</td>
                      <td>
                        {item.partyKey ? "Party" : "Legacy"}
                        {item.productionRowId ? " · Production" : ""}
                      </td>
                      <td>
                        <SAButton
                          size="sm"
                          onClick={() => setSelected(item.id)}
                        >
                          Evidence
                        </SAButton>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={page} total={invoices.data.total} onPage={setPage} />
          </>
        )}
      </SABentoCard>
      {selected && (
        <SABentoCard className="finance-detail">
          <div className="finance-section-head">
            <h2>Invoice evidence</h2>
            <SAButton size="sm" onClick={() => setSelected("")}>
              Close
            </SAButton>
          </div>
          {detail.isPending ? (
            <SkeletonCard />
          ) : detail.isError || !current ? (
            <p role="alert">Could not load invoice evidence.</p>
          ) : (
            <>
              <div className="finance-kpis">
                <div>
                  <span>Base</span>
                  <strong>{money(current.base)}</strong>
                </div>
                <div>
                  <span>Tax</span>
                  <strong>
                    {money(current.igst + current.cgst + current.sgst)}
                  </strong>
                </div>
                <div>
                  <span>TDS</span>
                  <strong>{money(current.tds)}</strong>
                </div>
                <div>
                  <span>Outstanding</span>
                  <strong>{money(current.outstanding)}</strong>
                </div>
              </div>
              <p>
                {current.parityStatus === "MATCH"
                  ? "Workbook balance matches the SA Command projection."
                  : "Workbook control is retained for review."}
              </p>
              <div className="finance-table-wrap">
                <table className="finance-table">
                  <thead>
                    <tr>
                      <th>Settlement</th>
                      <th>Amount</th>
                      <th>Owner evidence</th>
                    </tr>
                  </thead>
                  <tbody>
                    {current.settlements.map((s) => (
                      <tr key={s.id}>
                        <td>
                          {s.kind === "TDS"
                            ? "TDS reduction"
                            : `Cash · ${s.slot}`}
                        </td>
                        <td>{money(s.amount)}</td>
                        <td>
                          {s.accountCode && s.ownerRowId ? (
                            <Link
                              to={`/finance?owner=${s.accountCode.startsWith("AZ") ? "AZ" : "AK"}&movement=${s.ownerRowId}`}
                            >
                              {s.accountCode} →
                            </Link>
                          ) : (
                            "Legacy account unresolved"
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {current.partyKeyLink && (
                <p>
                  <Link to={`/finance?workbookParty=${current.partyKeyLink}`}>
                    Open linked Party Ledger →
                  </Link>
                </p>
              )}
              {current.productionRowId && (
                <p>
                  <Link
                    to={`/finance?workbookProduction=${current.productionRowId}`}
                  >
                    Open linked Production →
                  </Link>
                </p>
              )}
              <Source
                raw={current.raw}
                row={current.sourceRow}
                sha={current.workbookSha256}
              />
            </>
          )}
        </SABentoCard>
      )}
    </section>
  );
}
function Pager({
  page,
  total,
  onPage,
}: {
  page: number;
  total: number;
  onPage: (page: number) => void;
}) {
  return (
    <div className="finance-actions">
      <span>
        {total} records · Page {page + 1}
      </span>
      <SAButton size="sm" disabled={!page} onClick={() => onPage(page - 1)}>
        Previous
      </SAButton>
      <SAButton
        size="sm"
        disabled={(page + 1) * 50 >= total}
        onClick={() => onPage(page + 1)}
      >
        Next
      </SAButton>
    </div>
  );
}
function Source({
  raw,
  row,
  sha,
}: {
  raw: { cells: Record<string, unknown>; formulas: Record<string, string> };
  row: number;
  sha: string;
}) {
  return (
    <details>
      <summary>Original workbook cells and formulas</summary>
      <p>Workbook SHA-256: {sha}</p>
      <div className="finance-table-wrap">
        <table className="finance-table">
          <thead>
            <tr>
              <th>Cell</th>
              <th>Value</th>
              <th>Formula</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(raw.cells).map(([cell, value]) => (
              <tr key={cell}>
                <td>
                  {cell}
                  {row}
                </td>
                <td>{String(value)}</td>
                <td>{raw.formulas?.[cell] ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
