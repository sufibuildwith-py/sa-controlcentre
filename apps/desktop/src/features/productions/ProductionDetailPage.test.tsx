import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ProductionDetailPage } from "./ProductionDetailPage";

vi.mock("../finance/finance.api", () => ({
  financeApi: {
    production: vi.fn(),
    config: vi.fn(),
    counterparties: vi.fn(),
    setContract: vi.fn(),
    recordReceipt: vi.fn(),
    logExpense: vi.fn(),
    addEarning: vi.fn(),
  },
  financeAmount: (value: number) =>
    `₹${Number(value || 0).toLocaleString("en-IN")}`,
}));

vi.mock("../../lib/api", () => ({
  api: vi.fn(),
  json: (body: unknown) => ({
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }),
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
}));

import { financeApi } from "../finance/finance.api";
import { api } from "../../lib/api";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockProduction = {
  id: "p1",
  title: "Northstar Annual Gala",
  clientName: "Northstar Foods",
  eventDate: "2026-10-15",
  startTime: "18:00:00",
  endTime: "23:00:00",
  venueName: "Grand Hyatt Ballroom",
  status: "PLANNING",
  priority: "NORMAL",
  progressPercent: 20,
  members: [
    {
      id: "m1",
      employeeId: "e1",
      employeeName: "John Doe",
      productionRole: "Sound Engineer",
      assignmentStatus: "CONFIRMED",
    },
  ],
  unfinishedTaskCount: 2,
};

const mockEmployees = [
  { id: "e1", displayName: "John Doe", employeeCode: "SA-01" },
  { id: "e2", displayName: "Jane Smith", employeeCode: "SA-02" },
];

const mockFinanceData = {
  production: {
    id: "p1",
    title: "Northstar Annual Gala",
    clientName: "Northstar Foods",
    contracted: 150000,
  },
  received: 50000,
  outstanding: 100000,
  incurredExpense: 12000,
  contractedMargin: 138000,
  realizedMargin: 38000,
  transactions: [
    {
      id: "tx1",
      transactionNo: 101,
      type: "PRODUCTION_CONTRACT",
      date: "2026-10-01",
      description: "Signed contract for gala",
      amount: 150000,
      status: "POSTED",
    },
    {
      id: "tx2",
      transactionNo: 102,
      type: "PRODUCTION_RECEIPT",
      date: "2026-10-05",
      description: "Client initial advance",
      amount: 50000,
      status: "POSTED",
    },
  ],
};

const mockFinanceConfig = {
  accounts: [
    { id: "a1", code: "AZ-2", displayName: "Azeem", position: 500000 },
    { id: "a2", code: "AK-2", displayName: "Akash", position: 300000 },
  ],
  expenseCategories: [
    { id: "c1", code: "TRANSPORT", displayName: "Transport" },
    { id: "c2", code: "FOOD", displayName: "Food" },
  ],
  profitSplit: [{ effectiveFrom: "2026-01-01", azeem: 65, akash: 35 }],
};

function renderPage(productionId = "p1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={[`/productions/${productionId}`]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/productions/:id" element={<ProductionDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("ProductionDetailPage — Phase 1 Production Finance", () => {
  it("renders production details, financial overview, and contextual finance actions", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      if (path.startsWith("/audit")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    const financeTab = screen.getByRole("tab", { name: /finance/i });
    await userEvent.click(financeTab);

    await waitFor(() => {
      expect(screen.getByText(/Contracted Revenue/i)).toBeInTheDocument();
      expect(screen.getAllByText("₹1,50,000").length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("₹50,000")).toBeInTheDocument();
      expect(screen.getByText(/Outstanding ₹1,00,000/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Financial Activity Timeline/i),
      ).toBeInTheDocument();
      expect(screen.getByText("Signed contract for gala")).toBeInTheDocument();
      expect(screen.getByText("Client initial advance")).toBeInTheDocument();
    });

    expect(
      screen.getByRole("button", { name: /record receipt/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /log expense/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /add crew earning/i }),
    ).toBeInTheDocument();
  });

  it("opens Record Receipt modal and calls financeApi.recordReceipt with selected owner account", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });
    vi.mocked(financeApi.recordReceipt).mockResolvedValue({
      id: "tx-new-receipt",
    });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("tab", { name: /finance/i }));

    const recordReceiptBtn = await screen.findByRole("button", {
      name: /record receipt/i,
    });
    await userEvent.click(recordReceiptBtn);

    expect(
      await screen.findByRole("heading", { name: "Record Client Receipt" }),
    ).toBeInTheDocument();

    const amountInput = screen.getByLabelText("Receipt amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "25000");

    const receiverSelect = screen.getByLabelText("Receiver owner account");
    await userEvent.selectOptions(receiverSelect, "AZ-2");

    const submitBtn = screen.getByRole("button", { name: "Record Receipt" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.recordReceipt).toHaveBeenCalledTimes(1);
      expect(financeApi.recordReceipt).toHaveBeenCalledWith(
        expect.objectContaining({
          productionId: "p1",
          amount: 25000,
          receiverAccount: "AZ-2",
        }),
      );
    });
  });

  it("opens Log Expense modal and calls financeApi.logExpense with owner and category", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });
    vi.mocked(financeApi.logExpense).mockResolvedValue({
      id: "tx-new-expense",
    });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("tab", { name: /finance/i }));

    const logExpenseBtn = await screen.findByRole("button", {
      name: /log expense/i,
    });
    await userEvent.click(logExpenseBtn);

    expect(
      await screen.findByRole("heading", { name: "Log Production Expense" }),
    ).toBeInTheDocument();

    const amountInput = screen.getByLabelText("Expense amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "4500");

    const payerSelect = screen.getByLabelText("Expense payer account");
    await userEvent.selectOptions(payerSelect, "AK-2");

    const categorySelect = screen.getByLabelText("Expense category");
    await userEvent.selectOptions(categorySelect, "TRANSPORT");

    const descInput = screen.getByLabelText("Expense description");
    await userEvent.clear(descInput);
    await userEvent.type(descInput, "Equipment transport van");

    const submitBtn = screen.getByRole("button", { name: "Log Expense" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.logExpense).toHaveBeenCalledTimes(1);
      expect(financeApi.logExpense).toHaveBeenCalledWith(
        expect.objectContaining({
          productionId: "p1",
          amount: 4500,
          payerAccount: "AK-2",
          categoryCode: "TRANSPORT",
          description: "Equipment transport van",
        }),
      );
    });
  });

  it("opens Add Crew Earning modal and calls financeApi.addEarning", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });
    vi.mocked(financeApi.addEarning).mockResolvedValue({
      id: "tx-new-earning",
    });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("tab", { name: /finance/i }));

    const addCrewEarningBtn = await screen.findByRole("button", {
      name: /add crew earning/i,
    });
    await userEvent.click(addCrewEarningBtn);

    expect(
      await screen.findByRole("heading", { name: "Add Crew Labor Earning" }),
    ).toBeInTheDocument();

    const crewSelect = screen.getByLabelText("Crew member");
    await userEvent.selectOptions(crewSelect, "e1");

    const amountInput = screen.getByLabelText("Earning amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "3500");

    const descInput = screen.getByLabelText("Earning description");
    await userEvent.clear(descInput);
    await userEvent.type(descInput, "Overnight sound engineering shift");

    const submitBtn = screen.getByRole("button", { name: "Add Crew Earning" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.addEarning).toHaveBeenCalledTimes(1);
      expect(financeApi.addEarning).toHaveBeenCalledWith(
        expect.objectContaining({
          productionId: "p1",
          employeeId: "e1",
          amount: 3500,
          description: "Overnight sound engineering shift",
        }),
      );
    });
  });

  it("sets contract for a production without existing contract", async () => {
    const uncontractedFinance = {
      ...mockFinanceData,
      production: {
        ...mockFinanceData.production,
        contracted: 0,
      },
      received: 0,
      outstanding: 0,
      transactions: [],
    };

    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(uncontractedFinance);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });
    vi.mocked(financeApi.setContract).mockResolvedValue({ id: "tx-contract" });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("tab", { name: /finance/i }));

    const setContractButtons = await screen.findAllByRole("button", {
      name: "Set Contract",
    });
    await userEvent.click(setContractButtons[0]);

    expect(
      await screen.findByRole("heading", { name: "Set Contract" }),
    ).toBeInTheDocument();

    const amountInput = screen.getByLabelText("Contract amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "200000");

    const allSetButtons = await screen.findAllByRole("button", {
      name: "Set Contract",
    });
    const submitBtn = allSetButtons[allSetButtons.length - 1];
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.setContract).toHaveBeenCalledTimes(1);
      expect(financeApi.setContract).toHaveBeenCalledWith(
        expect.objectContaining({
          productionId: "p1",
          amount: 200000,
        }),
      );
    });
  });

  it("retains the exact same idempotency key across retried submissions after a failed/ambiguous attempt", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockProduction);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    });

    // First attempt fails (e.g. network timeout), second attempt succeeds
    vi.mocked(financeApi.recordReceipt)
      .mockRejectedValueOnce(new Error("504 Gateway Timeout"))
      .mockResolvedValueOnce({ id: "tx-recovered" });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("tab", { name: /finance/i }));

    const recordReceiptBtn = await screen.findByRole("button", {
      name: /record receipt/i,
    });
    await userEvent.click(recordReceiptBtn);

    expect(
      await screen.findByRole("heading", { name: "Record Client Receipt" }),
    ).toBeInTheDocument();

    const amountInput = screen.getByLabelText("Receipt amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "25000");

    const submitBtn = screen.getByRole("button", { name: "Record Receipt" });

    // First submission attempt
    await userEvent.click(submitBtn);

    // Wait for failure error message
    const errorElements = await screen.findAllByText("504 Gateway Timeout");
    expect(errorElements.length).toBeGreaterThanOrEqual(1);
    expect(financeApi.recordReceipt).toHaveBeenCalledTimes(1);
    const firstCallKey = vi.mocked(financeApi.recordReceipt).mock.calls[0][0]
      .idempotencyKey;
    expect(firstCallKey).toBeDefined();

    // User retries within the same modal attempt
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.recordReceipt).toHaveBeenCalledTimes(2);
    });

    const secondCallKey = vi.mocked(financeApi.recordReceipt).mock.calls[1][0]
      .idempotencyKey;
    // CRITICAL IDEMPOTENCY INVARIANT: The retry must send the exact same idempotencyKey!
    expect(secondCallKey).toBe(firstCallKey);
  });
});
