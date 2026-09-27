import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { EmployeeDetailPage } from "./EmployeeDetailPage";

vi.mock("../finance/finance.api", () => ({
  financeApi: {
    employee: vi.fn(),
    addEarning: vi.fn(),
    recordEmployeePayment: vi.fn(),
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

const mockEmployee = {
  id: "e1",
  employeeCode: "SA-01",
  displayName: "Amaan Khan",
  firstName: "Amaan",
  lastName: "Khan",
  phone: "+919000000000",
  email: "amaan@saproductions.com",
  roleTitle: "Lead Sound Engineer",
  department: "Production",
  employmentType: "FULL_TIME",
  joiningDate: "2024-01-01",
  baseSalaryMinor: 4_500_000,
  salaryCurrency: "INR",
  status: "ACTIVE",
};

const mockFinanceData = {
  employee: {
    id: "e1",
    displayName: "Amaan Khan",
  },
  earned: 65000,
  paid: 30000,
  outstanding: 35000,
  obligations: [
    {
      id: "ob-1",
      date: "2026-09-30",
      type: "FIXED_SALARY",
      amount: 45000,
      description: "Monthly salary accrual for Amaan Khan (09/2026)",
    },
    {
      id: "ob-2",
      date: "2026-10-05",
      type: "WORK_EARNING",
      amount: 20000,
      description: "Northstar Gala sound engineering shift",
    },
  ],
};

function renderPage(employeeId = "e1", search = "?tab=finance") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={[`/people/${employeeId}${search}`]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/people/:id" element={<EmployeeDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("EmployeeDetailPage — Phase 2 Employee Finance", () => {
  it("renders employee financial overview with obligations and contextual finance actions", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/e1") return Promise.resolve(mockEmployee);
      if (path === "/productions") return Promise.resolve([]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.employee).mockResolvedValue(mockFinanceData);

    renderPage("e1");

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    expect(screen.getByText("Earned")).toBeInTheDocument();
    expect(screen.getByText("₹65,000")).toBeInTheDocument();
    expect(screen.getByText("Paid")).toBeInTheDocument();
    expect(screen.getByText("₹30,000")).toBeInTheDocument();
    expect(screen.getByText("Outstanding")).toBeInTheDocument();
    expect(screen.getByText("₹35,000")).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: "Add Shift Earning" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Record Payout" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Open Finance ledger" }),
    ).toBeInTheDocument();

    // Check obligations list
    expect(
      screen.getByText("Monthly salary accrual for Amaan Khan (09/2026)"),
    ).toBeInTheDocument();
    expect(screen.getByText("Fixed Salary")).toBeInTheDocument();
    expect(
      screen.getByText("Northstar Gala sound engineering shift"),
    ).toBeInTheDocument();
    expect(screen.getByText("Shift Earning")).toBeInTheDocument();
  });

  it("opens Add Shift Earning modal and calls financeApi.addEarning", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/e1") return Promise.resolve(mockEmployee);
      if (path === "/productions")
        return Promise.resolve([
          { id: "prod-1", title: "Royal Wedding Lucknow" },
        ]);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.employee).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.addEarning).mockResolvedValue({ id: "tx-earning-1" });

    renderPage("e1");

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    const addEarningBtn = screen.getByRole("button", {
      name: "Add Shift Earning",
    });
    await userEvent.click(addEarningBtn);

    expect(
      await screen.findByRole("heading", { name: "Add Shift Earning" }),
    ).toBeInTheDocument();

    const amountInput = screen.getByLabelText("Shift earning amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "4500");

    const descInput = screen.getByLabelText("Shift earning description");
    await userEvent.clear(descInput);
    await userEvent.type(descInput, "Additional setup shift");

    const submitBtn = screen.getByRole("button", { name: "Add Earning" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.addEarning).toHaveBeenCalledTimes(1);
      expect(financeApi.addEarning).toHaveBeenCalledWith(
        expect.objectContaining({
          employeeId: "e1",
          amount: 4500,
          description: "Additional setup shift",
        }),
      );
    });
  });

  it("opens Record Payout modal and calls financeApi.recordEmployeePayment with explicit payer", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/e1") return Promise.resolve(mockEmployee);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.employee).mockResolvedValue(mockFinanceData);
    vi.mocked(financeApi.recordEmployeePayment).mockResolvedValue({
      id: "tx-payout-1",
    });

    renderPage("e1");

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    const recordPayoutBtn = screen.getByRole("button", {
      name: "Record Payout",
    });
    await userEvent.click(recordPayoutBtn);

    expect(
      await screen.findByRole("heading", { name: "Record Employee Payout" }),
    ).toBeInTheDocument();

    const payerSelect = screen.getByLabelText("Payout payer account");
    expect(payerSelect).toHaveValue("");

    const amountInput = screen.getByLabelText("Payout amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "12000");

    const descInput = screen.getByLabelText("Payout description");
    await userEvent.clear(descInput);
    await userEvent.type(descInput, "Partial payout installment");

    const submitBtn = screen.getByRole("button", { name: "Record Payout" });
    expect(submitBtn).toBeDisabled();

    // Select explicit owner account AK-2
    await userEvent.selectOptions(payerSelect, "AK-2");
    expect(submitBtn).not.toBeDisabled();
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.recordEmployeePayment).toHaveBeenCalledTimes(1);
      expect(financeApi.recordEmployeePayment).toHaveBeenCalledWith(
        expect.objectContaining({
          employeeId: "e1",
          amount: 12000,
          payerAccount: "AK-2",
          description: "Partial payout installment",
        }),
      );
    });
  });

  it("retains exact same idempotency key across retried submissions after a failed attempt in Record Payout", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/e1") return Promise.resolve(mockEmployee);
      return Promise.resolve(null);
    });

    vi.mocked(financeApi.employee).mockResolvedValue(mockFinanceData);

    // First call rejects with 504 Gateway Timeout, second succeeds
    vi.mocked(financeApi.recordEmployeePayment)
      .mockRejectedValueOnce(new Error("504 Gateway Timeout"))
      .mockResolvedValueOnce({ id: "tx-recovered" });

    renderPage("e1");

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    const recordPayoutBtn = screen.getByRole("button", {
      name: "Record Payout",
    });
    await userEvent.click(recordPayoutBtn);

    expect(
      await screen.findByRole("heading", { name: "Record Employee Payout" }),
    ).toBeInTheDocument();

    const payerSelect = screen.getByLabelText("Payout payer account");
    expect(payerSelect).toHaveValue("");
    await userEvent.selectOptions(payerSelect, "AZ-2");

    const amountInput = screen.getByLabelText("Payout amount");
    await userEvent.clear(amountInput);
    await userEvent.type(amountInput, "15000");

    const submitBtn = screen.getByRole("button", { name: "Record Payout" });

    // First attempt fails
    await userEvent.click(submitBtn);

    const errorElements = await screen.findAllByText("504 Gateway Timeout");
    expect(errorElements.length).toBeGreaterThanOrEqual(1);
    expect(financeApi.recordEmployeePayment).toHaveBeenCalledTimes(1);

    const firstKey = vi.mocked(financeApi.recordEmployeePayment).mock
      .calls[0][0].idempotencyKey;
    expect(firstKey).toBeDefined();

    // User retries within same modal attempt
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(financeApi.recordEmployeePayment).toHaveBeenCalledTimes(2);
    });

    const secondKey = vi.mocked(financeApi.recordEmployeePayment).mock
      .calls[1][0].idempotencyKey;
    // CRITICAL: idempotencyKey must be identical across retries
    expect(secondKey).toBe(firstKey);
  });

  it("renders Employee 360 command grid in overview tab and enables disbursement action with explicit payer selection", async () => {
    const mockEmployee360 = {
      employee: mockEmployee,
      today: {
        date: "2026-09-27",
        attendance: {
          id: "att-1",
          employeeId: "e1",
          date: "2026-09-27",
          status: "PRESENT" as const,
          checkInTime: "09:30:00",
          checkOutTime: "18:15:00",
          minutesLate: 0,
          updatedAt: "2026-09-27T09:30:00Z",
        },
        activeOrPendingLeave: null,
        activeTasksCount: 4,
        overdueTasksCount: 1,
        activeProductionsCount: 1,
      },
      money: {
        earned: 65000,
        paid: 30000,
        outstanding: 35000,
        baseSalaryMinor: 4_500_000,
        salaryCurrency: "INR",
        currentPayrollStatus: "PAID",
        latestPayrollPeriod: "2026-09",
      },
      operations: {
        activeProductionsCount: 1,
        completedProductionsCount: 12,
        tasksAssigned: 4,
        tasksCompleted: 12,
        overdueTasks: 1,
        activeProductions: [
          {
            id: "prod-1",
            title: "Royal Wedding Lucknow",
            role: "Audio Lead",
            eventDate: "2026-10-01",
            status: "ACTIVE",
          },
        ],
      },
      performance: {
        attendanceRecords: 26,
        attended: 25,
        lateCount: 1,
        attendanceRate: 96,
        onTimeCompletionRate: 92,
      },
      communication: {
        totalMessages: 18,
        lastContactAt: "2026-09-25T10:00:00Z",
        canMessage: true,
      },
      navigator: {
        enabled: true,
        isPaired: true,
        deviceStatus: "ONLINE",
        lastSyncAt: "2026-09-27T08:00:00Z",
      },
    };

    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/e1") return Promise.resolve(mockEmployee);
      if (path === "/employees/e1/360") return Promise.resolve(mockEmployee360);
      if (path === "/productions") return Promise.resolve([{ id: "prod-1", title: "Royal Wedding Lucknow" }]);
      return Promise.resolve(null);
    });

    renderPage("e1", "?tab=overview");

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    // Overview 360 pillars
    expect(screen.getByText("Today's Status")).toBeInTheDocument();
    expect(screen.getByText("Canonical Compensation & Ledger")).toBeInTheDocument();
    expect(screen.getByText("Operations & Tasks")).toBeInTheDocument();
    expect(screen.getByText("Royal Wedding Lucknow")).toBeInTheDocument();
    expect(screen.getByText("Performance & Device Sync")).toBeInTheDocument();

    // Verify 360 action strip
    const disburseBtn = screen.getByRole("button", { name: /Record Disbursement/i });
    expect(disburseBtn).toBeInTheDocument();
    expect(disburseBtn).not.toBeDisabled();

    // Clicking disbursement opens hardened payout modal with empty payer
    await userEvent.click(disburseBtn);

    expect(
      await screen.findByRole("heading", { name: "Record Employee Payout" }),
    ).toBeInTheDocument();

    const payerSelect = screen.getByLabelText("Payout payer account");
    expect(payerSelect).toHaveValue("");

    const submitBtn = screen.getByRole("button", { name: "Record Payout" });
    expect(submitBtn).toBeDisabled();

    // Explicitly choose AZ-2 to enable
    await userEvent.selectOptions(payerSelect, "AZ-2");
    expect(submitBtn).not.toBeDisabled();
  });
});
