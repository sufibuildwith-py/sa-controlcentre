import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { CommandPage } from "./CommandPage";
import { commandApi, type CommandDashboardView } from "./command.api";

vi.mock("./command.api", () => ({
  commandApi: {
    dashboard: vi.fn(),
  },
}));

vi.mock("../../lib/api", () => ({
  api: vi.fn().mockResolvedValue({ attention: [] }),
  clearSessionToken: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockDashboardData: CommandDashboardView = {
  date: "2026-09-26",
  today: {
    productions: 3,
    tasks: 7,
    attendanceExceptions: 2,
    moneyMovement: {
      received: 142000,
      disbursed: 30000,
      net: 112000,
      transactionCount: 4,
    },
  },
  money: {
    businessPosition: 150000,
    azeemPosition: -1697522,
    akashPosition: 104320,
    customerReceivable: 75000,
    employeePayable: 45000,
    invoiceReceivable: 180000,
    totalReceivable: 255000,
    reconciliationStatus: "RECONCILED",
  },
  attention: [
    {
      id: "overdue-invoices",
      severity: "HIGH",
      type: "OVERDUE_INVOICES",
      category: "FINANCE",
      title: "3 invoices overdue",
      reason: "Formal invoices with prior invoice date carry outstanding unpaid balance.",
      description: "₹4,82,000 outstanding across 3 overdue invoices",
      entityType: "INVOICE",
      amount: 482000,
      count: 3,
      route: "/finance?tab=INVOICES",
      queryParams: "tab=INVOICES",
    },
    {
      id: "unpaid-salary",
      severity: "HIGH",
      type: "UNPAID_SALARY",
      category: "PAYROLL",
      title: "Employee payment obligations pending",
      reason: "Approved work earnings or monthly salary accruals have not yet been disbursed.",
      description: "₹35,000 payable across 2 crew members",
      entityType: "EMPLOYEE",
      amount: 35000,
      count: 2,
      route: "/payroll",
    },
  ],
  operations: {
    upcomingProductions: [
      {
        id: "prod-1",
        title: "Northstar Brand Summit",
        clientName: "Northstar Foods",
        venueName: "Grand Ballroom",
        eventDate: "2026-09-26",
        startTime: "10:00:00",
        endTime: "18:00:00",
        status: "PRODUCTION",
        priority: "HIGH",
        progressPercent: 70,
        contractedAmount: 500000,
        receivedAmount: 350000,
        taskCount: 6,
        openTaskCount: 2,
      },
    ],
    pendingWork: [
      {
        id: "task-1",
        title: "Sound check & mic setup",
        priority: "URGENT",
        status: "IN_PROGRESS",
        assignedEmployeeName: "Roshan",
        productionTitle: "Northstar Brand Summit",
        isOverdue: false,
        bucket: "DUE_TODAY",
      },
    ],
    attendanceExceptions: [
      {
        employeeId: "emp-1",
        employeeName: "Sanjay",
        status: "LATE",
        minutesLate: 25,
      },
    ],
  },
  recentFinancialActivity: [
    {
      id: "tx-1",
      transactionNo: 101,
      date: "2026-09-26",
      type: "COUNTERPARTY_RECEIPT",
      amount: 50000,
      description: "Advance payment received",
      counterpartyName: "Northstar Foods",
      status: "POSTED",
    },
    {
      id: "tx-2",
      transactionNo: 102,
      date: "2026-09-26",
      type: "EMPLOYEE_PAYMENT",
      amount: 17000,
      description: "Disbursement for Sept work",
      employeeName: "Roshan",
      status: "POSTED",
    },
  ],
  quickActions: [
    { id: "NEW_PRODUCTION", label: "New Production", icon: "clapperboard", route: "/productions" },
    { id: "RECORD_RECEIPT", label: "Record Receipt", icon: "arrow-down-left", route: "/finance" },
  ],
};

import * as Tooltip from "@radix-ui/react-tooltip";

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={["/"]}>
      <QueryClientProvider client={queryClient}>
        <Tooltip.Provider delayDuration={0}>
          <CommandPage />
        </Tooltip.Provider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("CommandPage", () => {
  it("renders unified command dashboard with real canonical telemetry", async () => {
    vi.mocked(commandApi.dashboard).mockResolvedValue(mockDashboardData);
    renderPage();

    // Check header and status
    expect(await screen.findByText("Finance Reconciled")).toBeInTheDocument();

    // Check Today metrics
    expect(screen.getByText("3")).toBeInTheDocument(); // Productions count
    expect(screen.getByText("7")).toBeInTheDocument(); // Tasks due count
    expect(screen.getByText("2")).toBeInTheDocument(); // Attendance exceptions
    expect(screen.getByText("₹1,42,000")).toBeInTheDocument(); // Money today

    // Check Business Position
    expect(screen.getByText("Business Position")).toBeInTheDocument();
    expect(screen.getByText("₹1,50,000")).toBeInTheDocument();

    // Check Owner positions
    expect(screen.getByText("Azeem (AZ-2)")).toBeInTheDocument();
    expect(screen.getByText("-₹16,97,522")).toBeInTheDocument();
    expect(screen.getByText("Akash (AK-2)")).toBeInTheDocument();
    expect(screen.getByText("₹1,04,320")).toBeInTheDocument();

    // Check Dual-Track breakdown
    expect(screen.getByText("Party Charges")).toBeInTheDocument();
    expect(screen.getByText("₹75,000")).toBeInTheDocument();
    expect(screen.getByText("Formal Invoices")).toBeInTheDocument();
    expect(screen.getByText("₹1,80,000")).toBeInTheDocument();
    expect(screen.getByText("Crew Payable")).toBeInTheDocument();
    expect(screen.getByText("₹45,000")).toBeInTheDocument();

    // Check Attention Queue with Phase 2 categories and reasons
    expect(screen.getByText("Attention Queue (2)")).toBeInTheDocument();
    expect(screen.getByText("3 invoices overdue")).toBeInTheDocument();
    expect(screen.getByText("FINANCE")).toBeInTheDocument();
    expect(screen.getByText("PAYROLL")).toBeInTheDocument();
    expect(
      screen.getByText("Formal invoices with prior invoice date carry outstanding unpaid balance."),
    ).toBeInTheDocument();
    expect(screen.getByText("Employee payment obligations pending")).toBeInTheDocument();

    // Phase 3: named action labels (not the generic "Investigate")
    expect(screen.getByText("Review Invoices")).toBeInTheDocument();
    expect(screen.getByText("Process Payroll")).toBeInTheDocument();

    // Check Operations (default productions tab)
    expect(screen.getByText("Northstar Brand Summit")).toBeInTheDocument();

    // Check Recent Financial Activity
    expect(screen.getByText("Advance payment received")).toBeInTheDocument();
    expect(screen.getByText("Disbursement for Sept work")).toBeInTheDocument();

    // Check Quick Actions (Phase 3 — same labels, polished dock)
    expect(screen.getByRole("button", { name: /New Production/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record Receipt/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Log Expense/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Disburse Salary/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create Bill/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Finance Console/i })).toBeInTheDocument();
  });

  it("renders healthy clear empty state when zero attention items", async () => {
    const clearData: CommandDashboardView = {
      ...mockDashboardData,
      attention: [],
      operations: {
        upcomingProductions: [],
        pendingWork: [],
        attendanceExceptions: [],
      },
      recentFinancialActivity: [],
    };
    vi.mocked(commandApi.dashboard).mockResolvedValue(clearData);
    renderPage();

    expect(await screen.findByText("Everything is clear")).toBeInTheDocument();
    expect(
      screen.getByText("No operational or financial exceptions need attention right now."),
    ).toBeInTheDocument();
  });

  it("renders error state with retry action", async () => {
    vi.mocked(commandApi.dashboard).mockRejectedValue(new Error("Network error"));
    renderPage();

    expect(await screen.findByText("Command data unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retry/i })).toBeInTheDocument();
  });

  it("switches operations tabs between productions, tasks, and attendance", async () => {
    vi.mocked(commandApi.dashboard).mockResolvedValue(mockDashboardData);
    renderPage();

    await screen.findByText("Northstar Brand Summit");

    // Click Tasks tab
    const tasksTab = screen.getByRole("tab", { name: /Tasks \(1\)/i });
    await userEvent.click(tasksTab);
    expect(await screen.findByText("Sound check & mic setup")).toBeInTheDocument();

    // Click Exceptions tab
    const exceptionsTab = screen.getByRole("tab", { name: /Exceptions \(1\)/i });
    await userEvent.click(exceptionsTab);
    expect(await screen.findByText("Sanjay")).toBeInTheDocument();
    expect(screen.getByText(/Late by 25 mins/)).toBeInTheDocument();
  });

  it("expands production card to reveal financial breakdown and task progress", async () => {
    vi.mocked(commandApi.dashboard).mockResolvedValue(mockDashboardData);
    renderPage();

    const prodTitle = await screen.findByText("Northstar Brand Summit");
    // Click on production card to expand
    await userEvent.click(prodTitle);

    // Verify expanded financial info is now displayed
    expect(await screen.findByText("Contracted")).toBeInTheDocument();
    expect(screen.getByText("₹5,00,000")).toBeInTheDocument(); // contracted amount
    expect(screen.getByText("Received")).toBeInTheDocument();
    expect(screen.getByText("₹3,50,000")).toBeInTheDocument(); // received amount
    expect(screen.getByText("Unsettled")).toBeInTheDocument();
    expect(screen.getAllByText("₹1,50,000")).toHaveLength(2); // Business Position + Unsettled balance
    expect(screen.getByText(/2 open of 6 operational tasks/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Open Detail/i })).toBeInTheDocument();
  });
});
