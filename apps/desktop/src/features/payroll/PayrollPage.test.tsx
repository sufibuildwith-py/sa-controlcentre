import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { PayrollPeriod } from "../../types/domain";
import { PayrollPage } from "./PayrollPage";

const apiMock = vi.fn();
vi.mock("../../lib/api", () => ({
  api: (...args: unknown[]) => apiMock(...args),
  json: (body: unknown) => ({
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }),
}));
afterEach(() => {
  cleanup();
  apiMock.mockReset();
});

const fixture = (locked = false): PayrollPeriod => ({
  id: "period-1",
  year: 2026,
  month: 9,
  status: locked ? "LOCKED" : "APPROVED",
  policy: "PER_WORKING_DAY",
  totalMinor: 4_000_000,
  paidMinor: 1_500_000,
  pendingMinor: 2_500_000,
  remainingMinor: 2_500_000,
  paidCount: 0,
  partiallyPaidCount: 1,
  unpaidCount: 0,
  calculatedAt: "2026-09-01T00:00:00Z",
  approvedAt: "2026-09-02T00:00:00Z",
  paidAt: null,
  lockedAt: locked ? "2026-09-30T00:00:00Z" : null,
  items: [
    {
      id: "item-1",
      employeeId: "employee-1",
      employeeName: "Amaan Khan",
      employeeRole: "Senior Video Editor",
      salaryCurrency: "INR",
      baseSalaryMinor: 4_200_000,
      attendanceDeductionMinor: 0,
      overtimeMinor: 0,
      bonusMinor: 300_000,
      advanceDeductionMinor: 500_000,
      manualAdjustmentMinor: 0,
      grossEarnings: 4_500_000,
      deductions: 500_000,
      netSalaryMinor: 4_000_000,
      netPayable: 4_000_000,
      totalPaid: 1_500_000,
      remaining: 2_500_000,
      paymentStatus: "PARTIALLY_PAID",
      paidAt: null,
      lastPayment: {
        id: "payment-1",
        amountMinor: 1_500_000,
        paidAt: "2026-09-18T10:00:00Z",
        paymentMethod: "UPI",
        reference: "UPI-492821",
        note: "First part",
        createdAt: "2026-09-18T10:00:00Z",
      },
      payments: [
        {
          id: "payment-1",
          amountMinor: 1_500_000,
          paidAt: "2026-09-18T10:00:00Z",
          paymentMethod: "UPI",
          reference: "UPI-492821",
          note: "First part",
          createdAt: "2026-09-18T10:00:00Z",
        },
      ],
      adjustments: [
        {
          id: "adjustment-1",
          type: "ADVANCE",
          amountMinor: 500_000,
          reason: "Salary advance already received",
          createdAt: "2026-09-05T10:00:00Z",
        },
      ],
    },
  ],
});

function renderPayroll(data: PayrollPeriod) {
  apiMock.mockImplementation((path: string) =>
    Promise.resolve(
      path === "/payroll"
        ? [data]
        : path.includes("/payments")
          ? data.items[0]
          : data,
    ),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/payroll/${data.id}`]}>
        <Routes>
          <Route path="/payroll/:id" element={<PayrollPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("payroll ledger", () => {
  it("opens payment blank and loads ledger history on demand", async () => {
    renderPayroll(fixture());
    const user = userEvent.setup();
    expect(await screen.findByText("PARTIALLY PAID")).toBeInTheDocument();
    await user.click(
      screen.getAllByRole("button", { name: "Record payment" })[0],
    );
    expect(screen.getByLabelText("Payment employee")).toHaveValue("");
    expect(screen.getByLabelText("Payment amount")).toHaveValue(null);
    expect(
      screen.getAllByRole("button", { name: "Record payment" }).at(-1),
    ).toBeDisabled();
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "View details" }));
    expect(
      await screen.findByText("UPI-492821", { exact: false }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Salary advance already received"),
    ).toBeInTheDocument();
  });
  it("opens adjustment with deliberate blank financial fields", async () => {
    const data = fixture();
    data.status = "CALCULATED";
    renderPayroll(data);
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "Add adjustment" }),
    );
    expect(screen.getByLabelText("Adjustment employee")).toHaveValue("");
    expect(screen.getByLabelText("Adjustment type")).toHaveValue("");
    expect(screen.getByLabelText("Adjustment amount")).toHaveValue(null);
    expect(screen.getByLabelText("Adjustment reason")).toHaveValue("");
    expect(
      screen.getAllByRole("button", { name: "Add adjustment" }).at(-1),
    ).toBeDisabled();
  });
  it("keeps locked history readable and removes mutation actions", async () => {
    renderPayroll(fixture(true));
    const user = userEvent.setup();
    expect(
      await screen.findByText("Immutable financial history"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Record payment" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "View details" }));
    expect(screen.getByText("Payment history")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add adjustment" }),
    ).not.toBeInTheDocument();
  });

  it("submits payment with explicit owner payer account and retains requestId on retry", async () => {
    const data = fixture();
    renderPayroll(data);
    const user = userEvent.setup();

    // Mock API POST /payroll/.../payments
    let capturedBody: Record<string, unknown> | null = null;
    let callCount = 0;
    apiMock.mockImplementation(
      (path: string, options?: { method?: string; body?: string }) => {
        if (options?.method === "POST" && path.includes("/payments")) {
          callCount++;
          capturedBody = JSON.parse(options.body!);
          if (callCount === 1) {
            // First attempt fails with 504 Gateway Timeout
            return Promise.reject(new Error("504 Gateway Timeout"));
          }
          return Promise.resolve(data);
        }
        return Promise.resolve(
          path === "/payroll"
            ? [data]
            : path.includes("/payments")
              ? data.items[0]
              : data,
        );
      },
    );

    expect(await screen.findByText("PARTIALLY PAID")).toBeInTheDocument();
    const recordButtons = screen.getAllByRole("button", {
      name: "Record payment",
    });
    await user.click(recordButtons[0]);

    // Select employee
    await user.selectOptions(
      screen.getByLabelText("Payment employee"),
      "item-1",
    );

    // Select payer account
    const payerSelect = screen.getByLabelText("Payer owner account");
    expect(payerSelect).toHaveValue("AZ-2");
    await user.selectOptions(payerSelect, "AK-2");

    // Fill amount (e.g. 10000 of 25000 remaining)
    const amountInput = screen.getByLabelText("Payment amount");
    await user.clear(amountInput);
    await user.type(amountInput, "10000");

    const submitBtn = screen
      .getAllByRole("button", { name: "Record payment" })
      .at(-1)!;

    // First attempt fails
    await user.click(submitBtn);

    expect(await screen.findByText("504 Gateway Timeout")).toBeInTheDocument();
    expect(callCount).toBe(1);
    expect(capturedBody).not.toBeNull();
    const firstRequestId = (capturedBody as unknown as { requestId: string })
      .requestId;
    expect(firstRequestId).toBeDefined();
    expect(
      (capturedBody as unknown as { payerAccount: string }).payerAccount,
    ).toBe("AK-2");
    expect(
      (capturedBody as unknown as { amountMinor: number }).amountMinor,
    ).toBe(1_000_000);

    // Second attempt (retry within the same modal session)
    await user.click(submitBtn);

    expect(callCount).toBe(2);
    const secondRequestId = (capturedBody as unknown as { requestId: string })
      .requestId;
    // CRITICAL: requestId MUST be retained on retry
    expect(secondRequestId).toBe(firstRequestId);
  });
});
