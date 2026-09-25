import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import {
  EmptyState,
  SAButton,
  SABentoCard,
  SABentoGrid,
  SASegmentedControl,
  SkeletonCard,
} from "../../components/ui/sa";
import {
  financeAmount as money,
  financeApi,
  type WorkbookPurchase,
} from "./finance.api";

type View = "PURCHASES" | "ACCESSORIES";
type Book = "ALL" | "LED" | "SOUND";
export function WorkbookPurchasesEquipment() {
  const [view, setView] = useState<View>("PURCHASES"),
    [book, setBook] = useState<Book>("ALL"),
    [kind, setKind] = useState("ALL"),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState("");
  const summary = useQuery({
    queryKey: ["finance", "workbook-purchase-summary"],
    queryFn: financeApi.workbookPurchaseSummary,
  });
  const purchases = useQuery({
    queryKey: ["finance", "workbook-purchases", book, search, page],
    queryFn: () =>
      financeApi.workbookPurchases({ book, search, page, size: 50 }),
    enabled: view === "PURCHASES",
  });
  const accessories = useQuery({
    queryKey: ["finance", "workbook-accessories", kind, search, page],
    queryFn: () =>
      financeApi.workbookAccessories({ kind, search, page, size: 50 }),
    enabled: view === "ACCESSORIES",
  });
  const detail = useQuery({
    queryKey: ["finance", "workbook-purchase", selected],
    queryFn: () => financeApi.workbookPurchase(selected),
    enabled: !!selected,
  });
  if (summary.isPending) return <SkeletonCard />;
  if (summary.isError || !summary.data?.available)
    return (
      <SABentoCard>
        <EmptyState
          title="Purchase workbook unavailable"
          description="Load the private demo workbook to inspect historical purchases."
        />
      </SABentoCard>
    );
  const data = summary.data,
    selectedBook = data.books.find((b) => b.book === book),
    total = data.books.reduce((sum, b) => sum + b.purchased, 0),
    paid = data.books.reduce((sum, b) => sum + b.paid, 0);
  return (
    <section
      className="finance-workbook"
      aria-label="Workbook purchases and equipment"
    >
      <div className="finance-section-head">
        <div>
          <p className="eyebrow">Historical workbook · purchases</p>
          <h2>Purchases & Equipment</h2>
          <p>
            Commercial purchase evidence and physical references are linked only
            when the workbook proves it.
          </p>
        </div>
      </div>
      <SABentoGrid className="finance-kpis">
        <SABentoCard>
          <span>Total purchased</span>
          <strong>{money(total)}</strong>
          <small>LED + Sound</small>
        </SABentoCard>
        <SABentoCard>
          <span>Total paid</span>
          <strong>{money(paid)}</strong>
          <small>Payment ledger entries</small>
        </SABentoCard>
        <SABentoCard>
          <span>Outstanding</span>
          <strong>{money(total - paid)}</strong>
          <small>Workbook control</small>
        </SABentoCard>
        <SABentoCard>
          <span>Equipment references</span>
          <strong>{data.accessories.records}</strong>
          <small>
            {data.accessories.accessory_quantity} accessories ·{" "}
            {data.accessories.equipment_quantity} costing qty
          </small>
        </SABentoCard>
      </SABentoGrid>
      <SABentoCard>
        <SASegmentedControl
          value={view}
          onChange={(value) => {
            setView(value as View);
            setPage(0);
            setSelected("");
          }}
          label="Purchase view"
          items={[
            { value: "PURCHASES", label: "Purchases" },
            { value: "ACCESSORIES", label: "Accessories" },
          ]}
        />
        <div className="finance-actions">
          <input
            aria-label="Search purchases and equipment"
            placeholder="Product, description, date or amount"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          {view === "PURCHASES" ? (
            <select
              aria-label="Purchase book"
              value={book}
              onChange={(e) => {
                setBook(e.target.value as Book);
                setPage(0);
              }}
            >
              <option value="ALL">All purchases</option>
              <option value="LED">LED</option>
              <option value="SOUND">Sound</option>
            </select>
          ) : (
            <select
              aria-label="Equipment reference kind"
              value={kind}
              onChange={(e) => {
                setKind(e.target.value);
                setPage(0);
              }}
            >
              <option value="ALL">All references</option>
              <option value="ACCESSORY">Accessories</option>
              <option value="EQUIPMENT_REFERENCE">Equipment costing</option>
            </select>
          )}
        </div>
        {view === "PURCHASES" ? (
          <PurchaseTable
            query={purchases}
            page={page}
            onPage={setPage}
            onSelect={setSelected}
          />
        ) : (
          <AccessoryTable query={accessories} page={page} onPage={setPage} />
        )}
      </SABentoCard>
      {selected && (
        <SABentoCard className="finance-detail">
          <div className="finance-section-head">
            <h2>Purchase evidence</h2>
            <SAButton size="sm" onClick={() => setSelected("")}>
              Close
            </SAButton>
          </div>
          {detail.isPending ? (
            <SkeletonCard />
          ) : detail.isError || !detail.data ? (
            <p role="alert">Could not load purchase evidence.</p>
          ) : (
            <>
              <p>
                {detail.data.book} · {detail.data.description}
              </p>
              <div className="finance-kpis">
                <div>
                  <span>Purchase amount</span>
                  <strong>{money(detail.data.purchaseAmount)}</strong>
                </div>
                <div>
                  <span>Allocated paid</span>
                  <strong>{money(detail.data.paid)}</strong>
                </div>
                <div>
                  <span>Outstanding</span>
                  <strong>{money(detail.data.outstanding)}</strong>
                </div>
                <div>
                  <span>Quantity</span>
                  <strong>{detail.data.quantity ?? "—"}</strong>
                </div>
              </div>
              {detail.data.hqEquipmentId ? (
                <p>
                  <Link
                    to={`/headquarters?equipment=${detail.data.hqEquipmentId}`}
                  >
                    Open matched Headquarters equipment →
                  </Link>
                </p>
              ) : (
                <p>
                  No physical asset was created from this finance record.
                  Historical purchase and physical custody remain separate.
                </p>
              )}
              <div className="finance-table-wrap">
                <table className="finance-table">
                  <thead>
                    <tr>
                      <th>Payment evidence</th>
                      <th>Amount</th>
                      <th>Owner link</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.data.payments.length ? (
                      detail.data.payments.map((payment) => (
                        <tr key={payment.id}>
                          <td>
                            {payment.date ?? payment.rawDate ?? "Legacy date"}
                            <small>
                              {payment.description ??
                                `Workbook row ${payment.sourceRow}`}
                            </small>
                          </td>
                          <td>{money(payment.amount)}</td>
                          <td>
                            {payment.accountCode && payment.ownerRowId ? (
                              <Link
                                to={`/finance?owner=${payment.accountCode.startsWith("AZ") ? "AZ" : "AK"}&movement=${payment.ownerRowId}`}
                              >
                                {payment.accountCode} →
                              </Link>
                            ) : (
                              "Legacy account unresolved"
                            )}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={3}>
                          No item-level allocation is proven. Book-level payment
                          evidence remains visible without guessing.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <Source
                raw={detail.data.raw}
                row={detail.data.sourceRow}
                sha={detail.data.workbookSha256}
              />
            </>
          )}
        </SABentoCard>
      )}
    </section>
  );
}
function PurchaseTable({
  query,
  page,
  onPage,
  onSelect,
}: {
  query: ReturnType<typeof useQuery>;
  page: number;
  onPage: (p: number) => void;
  onSelect: (id: string) => void;
}) {
  if (query.isPending) return <SkeletonCard />;
  if (query.isError)
    return <p role="alert">Could not load workbook purchases.</p>;
  const data = query.data as
    Awaited<ReturnType<typeof financeApi.workbookPurchases>> | undefined;
  if (!data?.items.length)
    return (
      <EmptyState
        title="No purchase rows match"
        description="Try a broader search."
      />
    );
  return (
    <>
      <div className="finance-table-wrap">
        <table className="finance-table">
          <thead>
            <tr>
              <th>Book / date</th>
              <th>Product</th>
              <th>Qty</th>
              <th>Purchase</th>
              <th>Paid</th>
              <th>Outstanding</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((item: WorkbookPurchase) => (
              <tr key={item.id}>
                <td>
                  {item.book}
                  <small>{item.date ?? item.rawDate ?? "Legacy date"}</small>
                </td>
                <td>{item.description}</td>
                <td>{item.quantity ?? "—"}</td>
                <td>{money(item.purchaseAmount)}</td>
                <td>{money(item.paid)}</td>
                <td>{money(item.outstanding)}</td>
                <td>
                  <SAButton size="sm" onClick={() => onSelect(item.id)}>
                    Evidence
                  </SAButton>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={data.total} onPage={onPage} />
    </>
  );
}
function AccessoryTable({
  query,
  page,
  onPage,
}: {
  query: ReturnType<typeof useQuery>;
  page: number;
  onPage: (p: number) => void;
}) {
  if (query.isPending) return <SkeletonCard />;
  if (query.isError)
    return <p role="alert">Could not load equipment references.</p>;
  const data = query.data as
    Awaited<ReturnType<typeof financeApi.workbookAccessories>> | undefined;
  if (!data?.items.length)
    return (
      <EmptyState
        title="No equipment references match"
        description="Try a broader search."
      />
    );
  return (
    <>
      <div className="finance-table-wrap">
        <table className="finance-table">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Item</th>
              <th>Quantity</th>
              <th>Workbook value</th>
              <th>Headquarters</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((item) => (
              <tr key={item.id}>
                <td>
                  {item.kind === "ACCESSORY"
                    ? "Accessory snapshot"
                    : "Equipment costing"}
                  <small>
                    {item.sourceBlock} · row {item.sourceRow}
                  </small>
                </td>
                <td>{item.description}</td>
                <td>{item.quantity}</td>
                <td>
                  {item.valueAmount == null ? "—" : money(item.valueAmount)}
                </td>
                <td>
                  {item.hqEquipmentId ? (
                    <Link to={`/headquarters?equipment=${item.hqEquipmentId}`}>
                      Matched →
                    </Link>
                  ) : (
                    "No proven match"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} total={data.total} onPage={onPage} />
    </>
  );
}
function Pager({
  page,
  total,
  onPage,
}: {
  page: number;
  total: number;
  onPage: (p: number) => void;
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
