import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WorkbookProductionLedger } from "./WorkbookProductionLedger";

const mock = vi.hoisted(() => ({ workbookProductions: vi.fn(), workbookProduction: vi.fn() }));
vi.mock("./finance.api", () => ({ financeApi: mock }));

describe("historical production ledger", () => {
  it("shows the source row, formulas and linked account evidence", async () => {
    mock.workbookProductions.mockResolvedValue({ available: true, sheets: ["Jan 26"], total: 1, page: 0, size: 50, items: [{
      id: "row-1", sheet: "Jan 26", sourceRow: 5, sourceRange: "A5:U5", date: "2026-01-04",
      venue: "Surya Hotel", client: "Vineet Event", total: 7000, received: 7000,
      outstanding: 0, expense: 2500, legacyBudget: 4500,
    }] });
    mock.workbookProduction.mockResolvedValue({ sheet: "Jan 26", sourceRow: 5, sourceRange: "A5:U5",
      venue: "Surya Hotel", client: "Vineet Event", total: 7000, add: 0, payment: 7000,
      received: 7000, outstanding: 0, expense: 2500, legacyBudget: 4500,
      sourceBalance: 0, balanceParity: "MATCH", sourceBudget: 4500, budgetParity: "MATCH",
      cells: { E: 7000, G: 7000, J: 4500 }, formulas: { J: "G5-I5" }, sourceFacts: [],
      linkedOwnerEvidence: [{ movementId: "movement-1", accountCode: "AK-2", sheet: "Ak-2", sourceRange: "A20:H20", amount: 7000, confidence: "EXACT" }],
    });
    render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WorkbookProductionLedger/></QueryClientProvider></MemoryRouter>);
    expect(await screen.findByText("Surya Hotel")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Evidence" }));
    expect(await screen.findByText("Jan 26!A5:U5 · Surya Hotel · Vineet Event")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Original cells and formulas"));
    expect(screen.getByText("G5-I5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ak-2!A20:H20/ })).toHaveAttribute("href", "/finance?owner=AK&movement=movement-1");
  });
});
afterEach(cleanup);
