import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { FinanceControlPlane } from "./FinanceControlPlane";

vi.mock("./finance.api", () => ({
  financeApi: {
    overview: vi.fn(),
    reconciliation: vi.fn(),
    rebuild: vi.fn(),
  },
}));

import { financeApi } from "./finance.api";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPlane() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={["/finance"]}>
      <QueryClientProvider client={queryClient}>
        <FinanceControlPlane />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("FinanceControlPlane", () => {
  it("renders current finance position and reconciliation controls", async () => {
    vi.mocked(financeApi.overview).mockResolvedValue({
      received: 900000,
      incurredExpense: 300000,
      overallResult: 600000,
      contracted: 1200000,
      postedCount: 42,
      receivables: 300000,
      accounts: [],
    });
    vi.mocked(financeApi.reconciliation).mockResolvedValue({
      status: "WARNING",
      controlDifference: 250,
      overallResult: 600000,
      azeemPosition: -1700000,
      akashPosition: 104000,
      receivables: 300000,
      employeePayables: 120000,
      invoiceReceivables: 180000,
      equipmentPayables: 90000,
      migrationOpenCount: 3,
    });

    renderPlane();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Financial Control" })).toBeInTheDocument();
    });

    expect(screen.getByText("WARNING")).toBeInTheDocument();
    expect(screen.getByText("Customer receivables")).toBeInTheDocument();
    expect(screen.getByText("Employee payables")).toBeInTheDocument();
    expect(screen.getByText("Invoice receivables")).toBeInTheDocument();
    expect(screen.getByText("Equipment payables")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open reconciliation/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Rebuild positions/ })).toBeInTheDocument();
  });
});
