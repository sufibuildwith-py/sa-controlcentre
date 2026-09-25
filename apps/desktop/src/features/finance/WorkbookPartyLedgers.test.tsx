import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkbookPartyLedgers } from "./WorkbookPartyLedgers";

const mock = vi.hoisted(() => ({ workbookPartySummary: vi.fn(), workbookParties: vi.fn(), workbookParty: vi.fn(), workbookPartyEntries: vi.fn(), workbookPartyEntry: vi.fn() }));
vi.mock("./finance.api", () => ({ financeApi: mock }));
const wrapper = (node: React.ReactNode) => <MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider></MemoryRouter>;

beforeEach(() => {
  mock.workbookPartySummary.mockResolvedValue({ available: true, partyBlocks: 22, parties: 9, business: 9259885.6, received: 7543045.6, outstanding: 1716840,
    duplicateBlocks: 1, parityMismatches: 0, outstandingParties: 7, settledParties: 1, negativeParties: 1, discountSettlement: 10205.6, unlinkedPayments: 202 });
  mock.workbookParties.mockResolvedValue({ available: true, page: 0, size: 50, total: 1, items: [{ key: "rinku bhaiya", name: "Rinku Bhaiya", business: 1301520, received: 1301520, outstanding: 0, blocks: 1, entries: 112, lastActivity: "2026-08-01", productionLinks: 2, ownerLinks: 0 }] });
  mock.workbookParty.mockResolvedValue({ key: "rinku bhaiya", name: "Rinku Bhaiya", role: "BUSINESS_PARTY", business: 1301520, received: 1301520, outstanding: 0, blocks: [{ id: "block-1", blockIndex: 2, name: "Rinku Bhaiya", disposition: "ACTIVE_SOURCE", sourceRange: "शीट4 !I1:P1001", workbookAmount: 1301520, projectionAmount: 1301520, workbookPayment: 1301520, projectionPayment: 1301520, workbookBalance: 0, projectionBalance: 0, parityStatus: "MATCH" }] });
  mock.workbookPartyEntries.mockResolvedValue({ available: true, page: 0, size: 50, total: 1, items: [{ id: "entry-1", rawDate: "2026-08-01", date: "2026-08-01", venue: "Ramada", service: "LED wall", rate: "10000", amount: 10000, payment: 0, sourceRow: 11, blockId: "block-1", blockIndex: 2, partyName: "Rinku Bhaiya", disposition: "ACTIVE_SOURCE", productionLinks: 1, ownerLinks: 0 }] });
  mock.workbookPartyEntry.mockResolvedValue({ id: "entry-1", rawDate: "2026-08-01", venue: "Ramada", amount: 10000, payment: 0, sourceRow: 11, blockIndex: 2, partyName: "Rinku Bhaiya", duplicateOfEntryId: null, workbookSha256: "hash", links: [{ relationType: "PRODUCTION_CHARGE", amount: 10000, confidence: "EXACT", factId: "fact-1", linkedRowId: "production-1", accountCode: null, sheet: "Jan 26", sourceCell: "E11" }], raw: { cells: { I: "Ramada", M: 10000 }, formulas: { M: "SUM(M4:M11)" } } });
});
afterEach(cleanup);

describe("workbook party ledgers", () => {
  it("shows source-backed totals without folding them into company receivables", async () => {
    render(wrapper(<WorkbookPartyLedgers/>));
    expect(await screen.findByText("Rinku Bhaiya")).toBeInTheDocument();
    expect(screen.getByText(/not consolidated company receivables/i)).toBeInTheDocument();
    expect(screen.getByText(/duplicate snapshot excluded/i)).toBeInTheDocument();
  });
  it("opens real party source parity, entry evidence and production navigation", async () => {
    render(wrapper(<WorkbookPartyLedgers/>));
    await userEvent.setup().click(await screen.findByRole("button", { name: "Ledger" }));
    expect(await screen.findByText("Ramada")).toBeInTheDocument();
    expect(screen.getByText("MATCH")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Evidence" }));
    expect(await screen.findByRole("link", { name: /Jan 26!E11/ })).toHaveAttribute("href", "/finance?workbookProduction=production-1");
    await userEvent.setup().click(screen.getByText("Original cells and formulas"));
    expect(screen.getByText("SUM(M4:M11)")).toBeInTheDocument();
  });
  it("sends balance and search filters to the paged party endpoint", async () => {
    render(wrapper(<WorkbookPartyLedgers/>));
    await screen.findByText("Rinku Bhaiya");
    await userEvent.setup().selectOptions(screen.getByLabelText(/Balance/), "SETTLED");
    await userEvent.setup().type(screen.getByRole("textbox", { name: "Search party history" }), "Rinku");
    expect(mock.workbookParties).toHaveBeenLastCalledWith(expect.objectContaining({ status: "SETTLED", search: "Rinku", page: 0 }));
  });
});
