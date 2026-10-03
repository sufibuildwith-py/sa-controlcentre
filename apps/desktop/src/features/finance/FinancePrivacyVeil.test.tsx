import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { FinanceRouteGuard } from "./FinanceRouteGuard";
import { FloatingDomainSwitcher } from "../../components/layout/AppShell";
import { DeveloperSettings } from "../settings/DeveloperSettings";
import { ProductionDetailPage } from "../productions/ProductionDetailPage";
import { api, ApiError } from "../../lib/api";

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

vi.mock("../headquarters/headquarters.api", () => ({
  headquartersApi: {
    equipment: vi.fn(),
  },
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

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function createTestClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
}

describe("Finance Privacy Veil — Frontend Tests", () => {
  describe("FinanceRouteGuard", () => {
    it("redirects to root when finance is locked", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: false,
            expiresAt: null,
          });
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={["/finance"]}>
            <Routes>
              <Route path="/" element={<div>Overview Dashboard</div>} />
              <Route
                path="/finance"
                element={
                  <FinanceRouteGuard>
                    <div>Secret Finance Page</div>
                  </FinanceRouteGuard>
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Overview Dashboard")).toBeInTheDocument();
      });
      expect(screen.queryByText("Secret Finance Page")).not.toBeInTheDocument();
    });

    it("renders children when finance is unlocked", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: true,
            expiresAt: "2026-10-03T18:00:00Z",
          });
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={["/finance"]}>
            <Routes>
              <Route path="/" element={<div>Overview Dashboard</div>} />
              <Route
                path="/finance"
                element={
                  <FinanceRouteGuard>
                    <div>Secret Finance Page</div>
                  </FinanceRouteGuard>
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Secret Finance Page")).toBeInTheDocument();
      });
    });
  });

  describe("FloatingDomainSwitcher", () => {
    it("hides Finance and Billing tabs when locked", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: false,
            expiresAt: null,
          });
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={["/"]}>
            <FloatingDomainSwitcher />
          </MemoryRouter>
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Overview")).toBeInTheDocument();
      });

      expect(screen.queryByText("Finance")).not.toBeInTheDocument();
      expect(screen.queryByText("Billing")).not.toBeInTheDocument();
      expect(screen.getByText("Productions")).toBeInTheDocument();
      expect(screen.getByText("People")).toBeInTheDocument();
    });

    it("shows Finance and Billing tabs when unlocked", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: true,
            expiresAt: "2026-10-03T18:00:00Z",
          });
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={["/"]}>
            <FloatingDomainSwitcher />
          </MemoryRouter>
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Finance")).toBeInTheDocument();
      });

      expect(screen.getByText("Billing")).toBeInTheDocument();
    });
  });

  describe("DeveloperSettings & DonateDeveloperModal", () => {
    it("shows Donate Developer button when locked, opens modal, and simulates $2 donation on wrong PIN", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: false,
            expiresAt: null,
          });
        }
        if (path === "/finance-access/unlock") {
          return Promise.reject(
            new ApiError("INVALID_PIN", "Invalid finance unlock code."),
          );
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <DeveloperSettings />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Developer Options")).toBeInTheDocument();
      });

      const donateBtn = screen.getByRole("button", { name: /Donate Developer/i });
      expect(donateBtn).toBeInTheDocument();

      // Open donation modal
      await userEvent.click(donateBtn);

      expect(screen.getByText("Developer Donation")).toBeInTheDocument();
      expect(
        screen.getByText("Support the developer with a simulated $2 UPI contribution."),
      ).toBeInTheDocument();

      // Enter 6-digit wrong PIN
      const pinInput = screen.getByLabelText("6-Digit UPI PIN");
      await userEvent.type(pinInput, "123456");

      const confirmBtn = screen.getByRole("button", { name: /Donate \$2/i });
      await userEvent.click(confirmBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/₹160 \(\$2\) donated to the developer!/i),
        ).toBeInTheDocument();
      });

      expect(
        screen.getByText(/Invalid authorization code. Finance access was not granted./i),
      ).toBeInTheDocument();
    });

    it("unlocks finance when valid PIN is submitted and shows active lock controls", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: true,
            expiresAt: "2026-10-03T18:00:00Z",
          });
        }
        if (path === "/finance-access/lock") {
          return Promise.resolve(undefined);
        }
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <DeveloperSettings />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Finance Unlocked")).toBeInTheDocument();
      });

      const lockBtn = screen.getByRole("button", { name: /Lock Finance/i });
      expect(lockBtn).toBeInTheDocument();

      await userEvent.click(lockBtn);

      expect(api).toHaveBeenCalledWith("/finance-access/lock", {
        method: "POST",
      });
    });
  });

  describe("ProductionDetailPage Privacy", () => {
    it("hides Finance tab, Create Bill, and financial summary when locked", async () => {
      vi.mocked(api).mockImplementation((path: string) => {
        if (path === "/finance-access/status") {
          return Promise.resolve({
            eligible: true,
            unlocked: false,
            expiresAt: null,
          });
        }
        if (path.startsWith("/productions/p1")) {
          return Promise.resolve({
            id: "p1",
            title: "Summer Wedding",
            clientName: "Sharma Family",
            eventDate: "2026-10-15",
            status: "PLANNING",
            priority: "NORMAL",
            progressPercent: 20,
            members: [],
            unfinishedTaskCount: 0,
            equipment: [],
          });
        }
        if (path === "/employees") return Promise.resolve([]);
        if (path.startsWith("/tasks")) return Promise.resolve([]);
        return Promise.resolve(null);
      });

      const client = createTestClient();
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={["/productions/p1"]}>
            <Routes>
              <Route path="/productions/:id" element={<ProductionDetailPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Summer Wedding")).toBeInTheDocument();
      });

      expect(screen.queryByRole("tab", { name: /finance/i })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /create bill/i })).not.toBeInTheDocument();
      expect(screen.queryByText(/Financial Summary/i)).not.toBeInTheDocument();
    });
  });
});
