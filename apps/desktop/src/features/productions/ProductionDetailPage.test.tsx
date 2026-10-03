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

vi.mock("../headquarters/headquarters.api", () => ({
  headquartersApi: {
    equipment: vi.fn(),
  },
}));

vi.mock("../finance/financeAccess.api", () => ({
  useFinanceAccess: () => ({
    data: { eligible: true, unlocked: true, expiresAt: null },
    isLoading: false,
  }),
}));

import { financeApi } from "../finance/finance.api";
import { headquartersApi } from "../headquarters/headquarters.api";
import { api, ApiError } from "../../lib/api";

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
    expect(receiverSelect).toHaveValue("");

    const submitBtn = screen.getByRole("button", { name: "Record Receipt" });
    expect(submitBtn).toBeDisabled();

    await userEvent.selectOptions(receiverSelect, "AZ-2");
    expect(submitBtn).not.toBeDisabled();
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

  it(
    "opens Log Expense modal and calls financeApi.logExpense with owner and category",
    async () => {
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
    expect(payerSelect).toHaveValue("");

    const categorySelect = screen.getByLabelText("Expense category");
    await userEvent.selectOptions(categorySelect, "TRANSPORT");

    const descInput = screen.getByLabelText("Expense description");
    await userEvent.clear(descInput);
    await userEvent.type(descInput, "Equipment transport van");

    const submitBtn = screen.getByRole("button", { name: "Log Expense" });
    expect(submitBtn).toBeDisabled();

    await userEvent.selectOptions(payerSelect, "AK-2");
    expect(submitBtn).not.toBeDisabled();
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
  }, 15000);

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

    const receiverSelect = screen.getByLabelText("Receiver owner account");
    await userEvent.selectOptions(receiverSelect, "AZ-2");

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

describe("ProductionDetailPage — Production V2 Command Sheet", () => {
  const mockV2Production = {
    ...mockProduction,
    description: "Annual gala audio and lighting production",
    equipment: [
      {
        id: "pe1",
        equipmentId: "eq1",
        equipmentName: "Digital Mixing Console",
        internalCode: "DMC-32",
        quantity: 1,
        unitSymbol: "unit",
        status: "CONFIRMED",
      },
    ],
  };

  const mockV2Tasks = [
    {
      id: "task-1",
      title: "Stage speaker cabling",
      priority: "HIGH",
      status: "TODO",
      progressPercent: 0,
      overdue: false,
      assigneeName: "John Doe",
    },
  ];

  const mockCatalog = {
    items: [
      { id: "eq1", name: "Digital Mixing Console", internalCode: "DMC-32", available: 1, symbol: "pcs" },
      { id: "eq2", name: "Subwoofer 18-inch", internalCode: "SUB-18", available: 5, symbol: "pcs" },
    ],
    total: 2,
    page: 0,
    size: 50,
  };

  it("renders all Command Sheet sections on the Overview tab", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve(mockV2Tasks);
      if (path.startsWith("/audit")) return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Northstar Annual Gala")).toBeInTheDocument();
    });

    // Check Command Sheet sections
    expect(screen.getByRole("heading", { name: "Operational Brief" })).toBeInTheDocument();
    expect(screen.getAllByText("Annual gala audio and lighting production").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole("heading", { name: /schedule/i })).toBeInTheDocument();
    expect(screen.getByText("18:00 – 23:00")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /operational checklist/i })).toBeInTheDocument();
    expect(screen.getByText("Stage speaker cabling")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /crew/i })).toBeInTheDocument();
    expect(screen.getAllByText("John Doe").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole("heading", { name: /assigned equipment/i })).toBeInTheDocument();
    expect(screen.getByText("Digital Mixing Console")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Production Notes" })).toBeInTheDocument();
  });

  it("toggles task completion status directly from the operational checklist", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks") && !options?.method) return Promise.resolve(mockV2Tasks);
      if (path === "/tasks/task-1/updates" && options?.method === "POST") {
        return Promise.resolve({ id: "task-1", status: "DONE", progressPercent: 100 });
      }
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Stage speaker cabling")).toBeInTheDocument();
    });

    const checkbox = screen.getByLabelText("Toggle task Stage speaker cabling");
    expect(checkbox).not.toBeChecked();

    await userEvent.click(checkbox);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/tasks/task-1/updates",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            progressPercent: 100,
            status: "DONE",
            note: "Completed from checklist",
          }),
        }),
      );
    });
  });

  it("adds a new task directly from the Command Sheet checklist", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks") && !options?.method) return Promise.resolve(mockV2Tasks);
      if (path === "/tasks" && options?.method === "POST") {
        return Promise.resolve({ id: "task-2", title: "Truss safety checks", priority: "URGENT", status: "TODO" });
      }
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Stage speaker cabling")).toBeInTheDocument();
    });

    const addTaskBtns = screen.getAllByRole("button", { name: /add task/i });
    await userEvent.click(addTaskBtns[0]);

    expect(screen.getByRole("heading", { name: "Add Operational Task" })).toBeInTheDocument();

    const titleInput = screen.getByLabelText("New task title");
    await userEvent.type(titleInput, "Truss safety checks");

    const prioritySelect = screen.getByLabelText("New task priority");
    await userEvent.selectOptions(prioritySelect, "URGENT");

    const createBtn = screen.getByRole("button", { name: "Add Task" });
    await userEvent.click(createBtn);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/tasks",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            productionId: "p1",
            title: "Truss safety checks",
            priority: "URGENT",
            status: "TODO",
            progressPercent: 0,
          }),
        }),
      );
    });
  });

  it("adds equipment directly from the Command Sheet", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve(mockV2Tasks);
      if (path === "/productions/p1/equipment" && options?.method === "POST") {
        return Promise.resolve({ ...mockV2Production });
      }
      return Promise.resolve(null);
    });

    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockCatalog as any);
    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Digital Mixing Console")).toBeInTheDocument();
    });

    const addEqBtn = screen.getByRole("button", { name: "Add equipment" });
    await userEvent.click(addEqBtn);

    expect(screen.getByRole("heading", { name: "Assign Equipment" })).toBeInTheDocument();

    const eqSelect = screen.getByLabelText("Assign equipment select");
    await userEvent.selectOptions(eqSelect, "eq2");

    const qtyInput = screen.getByLabelText("Assign equipment quantity");
    await userEvent.clear(qtyInput);
    await userEvent.type(qtyInput, "2");

    const submitBtn = screen.getByRole("button", { name: "Assign Equipment" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/productions/p1/equipment",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            equipmentId: "eq2",
            quantity: 2,
          }),
        }),
      );
    });
  });

  it("displays error message and keeps modal open when equipment assignment fails", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve(mockV2Tasks);
      if (path === "/productions/p1/equipment" && options?.method === "POST") {
        return Promise.reject(
          new ApiError("CONFLICT", "Selected equipment is no longer available in the requested quantity"),
        );
      }
      return Promise.resolve(null);
    });

    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockCatalog as any);
    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Digital Mixing Console")).toBeInTheDocument();
    });

    const addEqBtn = screen.getByRole("button", { name: "Add equipment" });
    await userEvent.click(addEqBtn);

    expect(screen.getByRole("heading", { name: "Assign Equipment" })).toBeInTheDocument();

    const eqSelect = screen.getByLabelText("Assign equipment select");
    await userEvent.selectOptions(eqSelect, "eq2");

    const submitBtn = screen.getByRole("button", { name: "Assign Equipment" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(
        screen.getByText("Selected equipment is no longer available in the requested quantity"),
      ).toBeInTheDocument();
    });

    // Modal is still open for correction
    expect(screen.getByRole("heading", { name: "Assign Equipment" })).toBeInTheDocument();
  });

  it("validates equipment quantity against available stock", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve(mockV2Tasks);
      return Promise.resolve(null);
    });

    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockCatalog as any);
    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("Digital Mixing Console")).toBeInTheDocument();
    });

    const addEqBtn = screen.getByRole("button", { name: "Add equipment" });
    await userEvent.click(addEqBtn);

    const eqSelect = screen.getByLabelText("Assign equipment select");
    await userEvent.selectOptions(eqSelect, "eq2");

    const qtyInput = screen.getByLabelText("Assign equipment quantity");
    await userEvent.clear(qtyInput);
    await userEvent.type(qtyInput, "99");

    const submitBtn = screen.getByRole("button", { name: "Assign Equipment" });
    await userEvent.click(submitBtn);

    expect(
      screen.getByText("Only 5 pcs available in inventory."),
    ).toBeInTheDocument();

    // API should NOT have been called for /productions/p1/equipment
    expect(api).not.toHaveBeenCalledWith(
      "/productions/p1/equipment",
      expect.anything(),
    );
  });

  it("updates schedule directly from the Command Sheet", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions/p1" && options?.method === "PATCH") {
        return Promise.resolve({ ...mockV2Production, startTime: "17:00:00", endTime: "23:30:00" });
      }
      if (path === "/productions/p1") return Promise.resolve(mockV2Production);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      if (path.startsWith("/tasks")) return Promise.resolve(mockV2Tasks);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.production).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.config).mockResolvedValue(mockFinanceConfig);
    vi.mocked(financeApi.counterparties).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });

    renderPage("p1");

    await waitFor(() => {
      expect(screen.getByText("18:00 – 23:00")).toBeInTheDocument();
    });

    const editScheduleBtn = screen.getByRole("button", { name: "Edit schedule" });
    await userEvent.click(editScheduleBtn);

    expect(screen.getByRole("heading", { name: "Edit Production Schedule" })).toBeInTheDocument();

    const startInput = screen.getByLabelText("Schedule start time input");
    await userEvent.clear(startInput);
    await userEvent.type(startInput, "17:00");

    const endInput = screen.getByLabelText("Schedule end time input");
    await userEvent.clear(endInput);
    await userEvent.type(endInput, "23:30");

    const saveBtn = screen.getByRole("button", { name: "Save Schedule" });
    await userEvent.click(saveBtn);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/productions/p1",
        expect.objectContaining({
          method: "PATCH",
          body: expect.stringContaining('"startTime":"17:00"'),
        }),
      );
    });
  });
});
