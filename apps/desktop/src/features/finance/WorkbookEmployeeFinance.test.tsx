import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkbookEmployeeFinance } from "./WorkbookEmployeeFinance";

const mock = vi.hoisted(() => ({ workbookEmployees: vi.fn(), workbookEmployeeEvents: vi.fn(), workbookEmployeeEvent: vi.fn(), workbookSalaryEvidence: vi.fn() }));
vi.mock("./finance.api", () => ({ financeApi: mock }));
const wrapper = (node: React.ReactNode) => <MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider></MemoryRouter>;

beforeEach(() => {
  mock.workbookEmployees.mockResolvedValue({ available: true, earned: 218550, paid: 200000, outstanding: 18550,
    reviewFacts: 2, linkedPayments: 3, workbookSha256: "workbook-hash", employees: [
      { key: "ROSHAN", name: "Roshan", earned: 218550, paid: 200000, outstanding: 18550, facts: 20 },
      { key: "AAKASH_STAFF", name: "Aakash · staff", earned: 288900, paid: 288900, outstanding: 0, facts: 10 }],
    parity: [{ sheet: "Jan 26", column: "O", employee: "Roshan", kind: "EARNING", workbook: 218550, projection: 218550, difference: 0, status: "MATCH" }] });
  mock.workbookEmployeeEvents.mockResolvedValue({ available: true, page: 0, size: 50, total: 1, items: [{
    id: "event-1", employeeKey: "ROSHAN", employeeName: "Roshan", kind: "EARNING", amount: 15000,
    date: "2026-01-10", identityStatus: "FORMULA_PAIRED", sourceColumn: "O", sourceHeading: "Roshan",
    productionRowId: "production-1", sheet: "Jan 26", sourceRow: 10, venue: "Ramada", client: "Govind" }] });
  mock.workbookEmployeeEvent.mockResolvedValue({ id: "event-1", employeeKey: "ROSHAN", employeeName: "Roshan",
    kind: "EARNING", amount: 15000, date: "2026-01-10", identityStatus: "FORMULA_PAIRED", sourceColumn: "O",
    sourceHeading: "Roshan", productionRowId: "production-1", sheet: "Jan 26", sourceRow: 10,
    venue: "Ramada", client: "Govind", workbookSha256: "workbook-hash", linkedOwnerEvidence: [],
    raw: { cells: { O: 15000 }, formulas: { O: "SUM(O5:O9)" } } });
  mock.workbookSalaryEvidence.mockResolvedValue({ available: true, page: 0, size: 50, total: 1,
    items: [{ productionRowId: "salary-row", sheet: "Jan 26", sourceRow: 546, date: "2026-07-01", name: "Aman", amount: 15000 }] });
});
afterEach(cleanup);

describe("workbook employee finance", () => {
  it("keeps historical staff values distinct from posted Finance and shows source parity", async () => {
    render(wrapper(<WorkbookEmployeeFinance/>));
    expect(await screen.findByText("₹18,550")).toBeInTheDocument();
    expect(screen.getByText(/not posted again or added to the current journal/)).toBeInTheDocument();
    expect(screen.getByText(/2 payment cells have conflicting legacy identity labels/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Source-column parity"));
    expect(screen.getByText("O3")).toBeInTheDocument();
  });

  it("filters staff, opens evidence and navigates to production", async () => {
    render(wrapper(<WorkbookEmployeeFinance/>));
    await screen.findByText("Ramada");
    await userEvent.setup().click(screen.getByRole("button", { name: "Roshan" }));
    expect(mock.workbookEmployeeEvents).toHaveBeenCalledWith(expect.objectContaining({ employee: "ROSHAN" }));
    await userEvent.setup().click(screen.getByRole("button", { name: "Evidence" }));
    expect(await screen.findByRole("link", { name: /Ramada/ })).toHaveAttribute("href", "/finance?workbookProduction=production-1");
    await userEvent.setup().click(screen.getByText("Original row cells and formulas"));
    expect(screen.getByText("SUM(O5:O9)")).toBeInTheDocument();
  });

  it("keeps month-labelled expenses inspectable without counting them as salary", async () => {
    render(wrapper(<WorkbookEmployeeFinance/>));
    await screen.findByText(/Month-labelled expenses/);
    await userEvent.setup().click(screen.getByRole("button", { name: "Inspect source rows" }));
    expect(await screen.findByRole("link", { name: "Jan 26!I546 →" })).toHaveAttribute("href", "/finance?workbookProduction=salary-row");
    expect(screen.getByText(/not treated as confirmed salary obligations/)).toBeInTheDocument();
  });
});
