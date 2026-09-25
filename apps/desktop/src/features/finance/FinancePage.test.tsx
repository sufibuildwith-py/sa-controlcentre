import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FinancePage } from "./FinancePage";

const mock = vi.hoisted(() => ({
  overview: vi.fn(),
  config: vi.fn(),
  reconciliation: vi.fn(),
  productions: vi.fn(),
  production: vi.fn(),
  employees: vi.fn(),
  employee: vi.fn(),
  counterparties: vi.fn(),
  invoices: vi.fn(),
  purchases: vi.fn(),
  transactions: vi.fn(),
  transaction: vi.fn(),
  post: vi.fn(),
  reverse: vi.fn(),
  rebuild: vi.fn(),
  previewWorkbook: vi.fn(),
  validateWorkbook: vi.fn(),
  workbookOwners: vi.fn(),
  workbookEmployees: vi.fn(),
  workbookPartySummary: vi.fn(),
}));
vi.mock("./finance.api", () => ({ financeApi: mock }));
vi.mock("../../lib/api", () => ({ ApiError: class extends Error {} }));
afterEach(() => {
  cleanup();
  Object.values(mock).forEach((fn) => fn.mockReset());
});

function setup() {
  mock.overview.mockResolvedValue({
    overallResult: -100,
    received: 200,
    incurredExpense: 300,
    contracted: 400,
    receivables: 200,
    postedCount: 2,
    accounts: [
      { id: "a", code: "AZ-2", displayName: "Azeem", position: -50 },
      { id: "b", code: "AK-2", displayName: "Akash", position: 25 },
    ],
  });
  mock.config.mockResolvedValue({
    accounts: [
      { id: "a", code: "AZ-2", displayName: "Azeem", position: -50 },
      { id: "b", code: "AK-2", displayName: "Akash", position: 25 },
    ],
    expenseCategories: [{ id: "c", code: "FOOD", displayName: "Food" }],
    profitSplit: [],
  });
  mock.productions.mockResolvedValue({
    items: [],
    page: 0,
    size: 50,
    total: 0,
  });
  mock.employees.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });
  mock.transactions.mockResolvedValue({
    items: [],
    page: 0,
    size: 50,
    total: 0,
  });
  mock.counterparties.mockResolvedValue({
    items: [],
    page: 0,
    size: 50,
    total: 0,
  });
  mock.invoices.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });
  mock.purchases.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });
  mock.reconciliation.mockResolvedValue({
    status: "RECONCILED",
    controlDifference: 0,
    overallResult: -100,
    azeemPosition: -50,
    akashPosition: 25,
    receivables: 0,
    employeePayables: 0,
    invoiceReceivables: 0,
    equipmentPayables: 0,
  });
  mock.workbookOwners.mockResolvedValue({ available: false });
  mock.workbookEmployees.mockResolvedValue({ available: false });
  mock.workbookPartySummary.mockResolvedValue({ available: false });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <FinancePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("Finance command surface", () => {
  it("shows signed owner positions and realized result", async () => {
    setup();
    expect(await screen.findByText("−₹100.00")).toBeInTheDocument();
    expect(screen.getByText("−₹50.00")).toBeInTheDocument();
    expect(screen.getByText("+₹25.00")).toBeInTheDocument();
  });

  it("does not permit an expense without amount, evidence and deliberate payer selection", async () => {
    setup();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Transaction" }));
    expect(
      screen.getByRole("button", { name: "Post transaction" }),
    ).toBeDisabled();
    await user.type(screen.getByLabelText("Amount · INR"), "6500");
    await user.type(
      screen.getByLabelText("Description / evidence"),
      "Food for crew",
    );
    expect(
      screen.getByRole("button", { name: "Post transaction" }),
    ).toBeDisabled();
    await user.selectOptions(await screen.findByLabelText("Paid by"), "AZ-2");
    expect(
      screen.getByRole("button", { name: "Post transaction" }),
    ).toBeEnabled();
  });

  it("shows a useful empty transaction state", async () => {
    setup();
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Transactions" }));
    expect(
      await screen.findByText("No records in this view yet."),
    ).toBeInTheDocument();
  });
});
