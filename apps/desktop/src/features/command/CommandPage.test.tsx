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
      severity: "HIGH",
      type: "OVERDUE_INVOICES",
      title: "3 invoices overdue",
      description: "₹4,82,000 outstanding across 3 overdue invoices",
      count: 3,
      route: "/finance?tab=INVOICES",
    },
    {
      severity: "HIGH",
      type: "UNPAID_SALARY",
      title: "Employee payment obligations pending",
      description: "₹35,000 payable across 2 crew members",
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
        startTime: "10:00",
        endTime: "18:00",
        status: "PRODUCTION",
        priority: "HIGH",
        progressPercent: 70,
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

    // Check Attention Queue
    expect(screen.getByText("Attention Queue (2)")).toBeInTheDocument();
    expect(screen.getByText("3 invoices overdue")).toBeInTheDocument();
    expect(screen.getByText("Employee payment obligations pending")).toBeInTheDocument();

    // Check Operations (default productions tab)
    expect(screen.getByText("Northstar Brand Summit")).toBeInTheDocument();

    // Check Recent Financial Activity
    expect(screen.getByText("Advance payment received")).toBeInTheDocument();
    expect(screen.getByText("Disbursement for Sept work")).toBeInTheDocument();

    // Check Quick Actions
    expect(screen.getByRole("button", { name: /New Production/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record Receipt/i })).toBeInTheDocument();
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

    expect(await screen.findByText("You’re clear")).toBeInTheDocument();
    expect(
      screen.getByText("No overdue invoices, salary arrears, or reconciliation alerts."),
    ).toBeInTheDocument();
  });

  it("renders error state with retry action", async () => {
    vi.mocked(commandApi.dashboard).mockRejectedValue(new Error("Network error"));
    renderPage();

    expect(await screen.findByText("Command telemetry unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retry/i })).toBeInTheDocument();
  });

  it("switches operations tabs between productions, tasks, and attendance", async () => {
    vi.mocked(commandApi.dashboard).mockResolvedValue(mockDashboardData);
    renderPage();

    await screen.findByText("Northstar Brand Summit");

    // Click Tasks tab
    const tasksTab = screen.getByRole("button", { name: /Tasks \(1\)/i });
    await userEvent.click(tasksTab);
    expect(await screen.findByText("Sound check & mic setup")).toBeInTheDocument();

    // Click Exceptions tab
    const exceptionsTab = screen.getByRole("button", { name: /Exceptions \(1\)/i });
    await userEvent.click(exceptionsTab);
    expect(await screen.findByText("Sanjay")).toBeInTheDocument();
    expect(screen.getByText("Late by 25 mins")).toBeInTheDocument();
  });
});
