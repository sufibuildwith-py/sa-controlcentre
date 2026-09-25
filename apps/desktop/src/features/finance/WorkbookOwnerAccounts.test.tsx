import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkbookOwnerAccounts } from "./WorkbookOwnerAccounts";

const mock = vi.hoisted(() => ({ workbookOwners: vi.fn(), workbookOwnerMovements: vi.fn(), workbookOwnerMovement: vi.fn(), workbookTransfers: vi.fn() }));
vi.mock("./finance.api", () => ({ financeApi: mock }));
const summary = { available: true, transferCount: 7, transferTotal: 185090, parity: [
  { metric: "Final AZ reference", workbook: -1697522, projection: -1697522, difference: 0, status: "MATCH" }], owners: [
  { code: "AZ", name: "Azeem", previousPosition: 258849, openingReference: 258849, continuity: "VERIFIED", position: -1697522,
    moneyIn: 2457885, moneyOut: 4155407, linkedMovements: 68, unlinkedMovements: 300, segments: [
      { sheet: "az 26", block: "AZ_CASH", label: "Earlier · Cash", moneyIn: 4131368, moneyOut: 3953118, workbookPosition: 178250, difference: 0 },
      { sheet: "az-2", block: "AZ_CURRENT", label: "Current history", moneyIn: 2457885, moneyOut: 4155407, workbookPosition: -1697522, difference: 0 }] },
  { code: "AK", name: "Akash", previousPosition: 0, openingReference: 35987, continuity: "NOT_PROVEN_BY_EARLIER_SHEET", position: 104095,
    moneyIn: 529760, moneyOut: 425665, linkedMovements: 141, unlinkedMovements: 853, segments: [
      { sheet: "Ak-2", block: "AK_10", label: "Sep", monthKey: "2026-09", moneyIn: 529760, moneyOut: 425665, workbookPosition: 104095, difference: 0 }] }] };
const wrapper = (node: React.ReactNode) => <MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider></MemoryRouter>;

beforeEach(() => {
  mock.workbookOwners.mockResolvedValue(summary);
  mock.workbookOwnerMovements.mockResolvedValue({ available: true, page: 0, size: 50, total: 1, items: [{
    id: "owner-1", rowId: "row-1", sheet: "az-2", sourceRow: 20, sourceRange: "A20:D20", slot: "AZ_CURRENT_OUT",
    date: "2026-08-10", description: "petrol", amount: 6500, direction: "OUT", positionAfter: -12000,
    period: "Current history", status: "LEGACY", businessType: "OTHER", owner: "AZ" }] });
  mock.workbookOwnerMovement.mockResolvedValue({ accountCode: "AZ-2", date: "2026-08-10", description: "petrol", amount: 6500,
    direction: "OUT", status: "LEGACY", sheet: "az-2", sourceRow: 20, sourceRange: "A20:D20", workbookSha256: "workbook-hash",
    linkedBusiness: [], transferPartner: [], raw: { cells: { B: "petrol", D: 6500, E: -12000 }, formulas: { E: "C20-D20" } } });
  mock.workbookTransfers.mockResolvedValue({ available: true, total: 1, page: 0, size: 50, items: [{ id: "transfer-1", date: "2026-09-01", fromAccount: "AZ-2", toAccount: "AK-2", amount: 590, primaryMovementId: "owner-1", primarySheet: "az-2", primaryRange: "A9:D9", otherSheet: "Ak-2", otherRange: "F11:I11" }] });
});
afterEach(cleanup);

describe("workbook owner accounts", () => {
  it("shows real parity, negative position and unlinked movement evidence", async () => {
    render(wrapper(<WorkbookOwnerAccounts/>));
    expect((await screen.findAllByText("-₹16,97,522")).length).toBeGreaterThan(0);
    expect(screen.getByText("7 · ₹1,85,090")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Open AZ →" }));
    expect(await screen.findByText("petrol")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Evidence" }));
    expect(await screen.findByText(/Unresolved legacy purpose/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Original workbook cells and formulas"));
    expect(screen.getByText("C20-D20")).toBeInTheDocument();
  });

  it("normalizes AK month filtering and displays paired transfer sources", async () => {
    render(wrapper(<WorkbookOwnerAccounts/>));
    await screen.findAllByText("-₹16,97,522");
    await userEvent.setup().click(screen.getByRole("button", { name: "Akash / AK" }));
    await userEvent.setup().selectOptions(screen.getByLabelText("Month"), "2026-09");
    expect(mock.workbookOwnerMovements).toHaveBeenCalledWith("AK", expect.objectContaining({ month: "2026-09" }));
    await userEvent.setup().click(screen.getByRole("button", { name: "Transfers" }));
    expect(await screen.findByText("az-2!A9:D9")).toBeInTheDocument();
    expect(screen.getByText("Ak-2!F11:I11")).toBeInTheDocument();
    expect(screen.getByText("₹590")).toBeInTheDocument();
  });

  it("opens proven production evidence from an owner movement", async () => {
    mock.workbookOwnerMovement.mockResolvedValueOnce({ accountCode: "AK-2", date: "2026-07-07", description: "Govind", amount: 10000,
      direction: "IN", status: "LINKED", sheet: "Ak-2", sourceRow: 20, sourceRange: "A20:E20", workbookSha256: "workbook-hash",
      raw: { cells: { B: "Govind", C: 10000 }, formulas: {} }, transferPartner: [], linkedBusiness: [{
        factId: "fact-1", eventType: "CLIENT_RECEIPT", amount: 10000, rowId: "production-row-8", sheet: "JUL+DEC",
        sourceRow: 8, sourceRange: "A8:U8", confidence: "EXACT" }] });
    render(wrapper(<WorkbookOwnerAccounts initialOwner="AK" initialMovement="owner-20"/>));
    expect(await screen.findByRole("link", { name: "Open production →" })).toHaveAttribute("href", "/finance?workbookProduction=production-row-8");
  });
});
