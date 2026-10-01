import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ProductionsPage } from "./ProductionsPage";

vi.mock("../../lib/api", async () => {
  const actual = await vi.importActual<typeof import("../../lib/api")>("../../lib/api");
  return {
    ...actual,
    api: vi.fn(),
    json: (body: unknown) => ({
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  };
});

vi.mock("../headquarters/headquarters.api", () => ({
  headquartersApi: {
    equipment: vi.fn(),
  },
}));

vi.mock("../finance/finance.api", () => ({
  financeApi: {
    config: vi.fn().mockResolvedValue({
      accounts: [
        { id: "a1", code: "AZ-2", displayName: "Azeem", position: 500000 },
        { id: "a2", code: "AK-2", displayName: "Akash", position: 300000 },
      ],
      expenseCategories: [],
      profitSplit: [],
    }),
  },
  financeAmount: (value: number) =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(Number(value || 0)),
}));

import { ApiError, api } from "../../lib/api";
import { headquartersApi } from "../headquarters/headquarters.api";
import { financeApi } from "../finance/finance.api";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

let testQueryClient: QueryClient;

beforeEach(() => {
  testQueryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  vi.mocked(financeApi.config).mockResolvedValue({
    accounts: [
      { id: "a1", code: "AZ-2", displayName: "Azeem", position: 500000 },
      { id: "a2", code: "AK-2", displayName: "Akash", position: 300000 },
    ],
    expenseCategories: [],
    profitSplit: [],
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockProductions = [
  {
    id: "p1",
    title: "Summer Music Festival",
    clientName: "City Events",
    eventDate: "2026-11-20",
    venueName: "Palace Grounds",
    status: "CONFIRMED",
    priority: "HIGH",
    progressPercent: 40,
    unfinishedTaskCount: 3,
    members: [],
  },
];

const mockEmployees = [
  { id: "e1", displayName: "Azeem Sound", employeeCode: "EMP-001" },
  { id: "e2", displayName: "Rahul Lights", employeeCode: "EMP-002" },
];

const mockEquipment = {
  items: [
    { id: "eq1", name: "Line Array Speaker", internalCode: "SPK-01", category: "AUDIO" },
    { id: "eq2", name: "LED Par 64", internalCode: "LGT-01", category: "LIGHTING" },
  ],
  total: 2,
  page: 0,
  size: 20,
};

function renderPage(client: QueryClient = testQueryClient) {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <ProductionsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("ProductionsPage — Single Intake Flow with Financial Settlement", () => {
  it("renders list and opens single intake form with equipment directly below Venue/Address and Financial Settlement", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    // Open single intake form
    const createBtn = screen.getByRole("button", { name: /production/i });
    await userEvent.click(createBtn);

    expect(screen.getByRole("heading", { name: "Create Production" })).toBeInTheDocument();
    const venueAddress = screen.getByLabelText("Venue address");
    const equipmentSelect = screen.getByLabelText("Select equipment");
    const setContractBtn = screen.getByRole("button", { name: "Set Contract" });
    const advanceBtn = screen.getByRole("button", { name: "Advance" });
    const startTimeInput = screen.getByLabelText("Start time");

    // Verify ordering: Venue/Address -> Equipment Needed -> Financial Settlement -> Schedule
    expect(venueAddress.compareDocumentPosition(equipmentSelect) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(equipmentSelect.compareDocumentPosition(setContractBtn) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(setContractBtn.compareDocumentPosition(advanceBtn) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(advanceBtn.compareDocumentPosition(startTimeInput) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    // Verify initial Financial Settlement state
    expect(screen.getByText("Financial Settlement")).toBeInTheDocument();
    expect(screen.getByText("Contract Required")).toBeInTheDocument();
    expect(screen.getByText("Not set")).toBeInTheDocument();
    expect(screen.getByText("₹0")).toBeInTheDocument();
  });

  it("allows selecting equipment from HQ and adding it to the production draft with fractional quantity", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));
    expect(screen.getByRole("heading", { name: "Create Production" })).toBeInTheDocument();

    const eqSelect = screen.getByLabelText("Select equipment");
    expect(screen.getByRole("option", { name: /Line Array Speaker/i })).toBeInTheDocument();

    await userEvent.selectOptions(eqSelect, "eq1");
    const qtyInput = screen.getByLabelText("Equipment quantity");
    fireEvent.change(qtyInput, { target: { value: "4.5" } });

    await userEvent.click(screen.getByRole("button", { name: "+ Add Gear" }));

    expect(screen.getByText("Line Array Speaker")).toBeInTheDocument();
    expect(screen.getByText("(4.5 units)")).toBeInTheDocument();
  });

  it("requires contract before Create Production can be submitted", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    const submitBtn = screen.getByRole("button", { name: "Create Production" });
    expect(submitBtn).toBeDisabled();

    // Fill all operational required fields
    fireEvent.change(screen.getByLabelText("Production title"), { target: { value: "Tech Summit 2026" } });
    fireEvent.change(screen.getByLabelText("Client name"), { target: { value: "Global Tech Corp" } });
    fireEvent.change(screen.getByLabelText("Venue name"), { target: { value: "Convention Hall A" } });
    fireEvent.change(screen.getByLabelText("Event date"), { target: { value: "2026-11-20" } });

    // Submit is STILL disabled because contract is required!
    expect(submitBtn).toBeDisabled();

    // Open Set Contract modal and set valid contract
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));
    expect(screen.getByRole("heading", { name: "Set Contract" })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "150000" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Contract" }));

    // Now contract is set, submit button becomes enabled
    expect(screen.getByText("Contract Set")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /set contract · ₹1,50,000/i })).toBeInTheDocument();
    expect(submitBtn).toBeEnabled();
  });

  it("validates contract amount must be positive", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));

    const saveContractBtn = screen.getByRole("button", { name: "Save Contract" });
    expect(saveContractBtn).toBeDisabled();

    // Enter 0
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "0" } });
    expect(saveContractBtn).toBeDisabled();

    // Enter negative
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "-500" } });
    expect(saveContractBtn).toBeDisabled();
  });

  it("validates advance cannot exceed contract and defaults to 0", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    // Set contract to 100000
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "100000" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Contract" }));

    // Advance starts at ₹0
    expect(screen.getByText("₹0")).toBeInTheDocument();

    // Open Advance modal
    await userEvent.click(screen.getByRole("button", { name: "Advance" }));
    expect(screen.getByRole("heading", { name: "Advance" })).toBeInTheDocument();

    const saveAdvanceBtn = screen.getByRole("button", { name: "Save Advance" });

    // Enter advance greater than contract
    fireEvent.change(screen.getByLabelText("Advance amount"), { target: { value: "125000" } });
    // Button is disabled when advance exceeds contract
    expect(saveAdvanceBtn).toBeDisabled();

    // Enter valid advance
    fireEvent.change(screen.getByLabelText("Advance amount"), { target: { value: "25000" } });
    expect(saveAdvanceBtn).toBeEnabled();
    await userEvent.click(saveAdvanceBtn);

    // Shows updated advance button and badge
    expect(screen.getByRole("button", { name: /advance · ₹25,000/i })).toBeInTheDocument();
  });

  it("submits unified intake payload with contract and advance, and invalidates queries on success", async () => {
    const invalidateSpy = vi.spyOn(testQueryClient, "invalidateQueries");

    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions" && options?.method === "POST") {
        return Promise.resolve({ id: "prod-new-123" });
      }
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage(testQueryClient);

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    // Core fields
    fireEvent.change(screen.getByLabelText("Production title"), { target: { value: "Tech Summit 2026" } });
    fireEvent.change(screen.getByLabelText("Client name"), { target: { value: "Global Tech Corp" } });
    fireEvent.change(screen.getByLabelText("Venue name"), { target: { value: "Convention Hall A" } });
    fireEvent.change(screen.getByLabelText("Venue address"), { target: { value: "Whitefield, Bengaluru" } });
    fireEvent.change(screen.getByLabelText("Event date"), { target: { value: "2026-11-20" } });

    // Add equipment
    fireEvent.change(screen.getByLabelText("Select equipment"), { target: { value: "eq1" } });
    fireEvent.change(screen.getByLabelText("Equipment quantity"), { target: { value: "4" } });
    await userEvent.click(screen.getByRole("button", { name: "+ Add Gear" }));
    expect(screen.getByText("Line Array Speaker")).toBeInTheDocument();

    // Set contract
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "150000" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Contract" }));

    // Set advance
    await userEvent.click(screen.getByRole("button", { name: "Advance" }));
    fireEvent.change(screen.getByLabelText("Advance amount"), { target: { value: "50000" } });
    fireEvent.change(screen.getByLabelText("Receiver owner account"), { target: { value: "AZ-2" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Advance" }));

    // Submit
    const submitBtn = screen.getByRole("button", { name: "Create Production" });
    expect(submitBtn).toBeEnabled();
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/productions",
        expect.objectContaining({
          method: "POST",
        }),
      );
    });

    const postCall = vi
      .mocked(api)
      .mock.calls.find((call) => call[0] === "/productions" && call[1]?.method === "POST");
    expect(postCall).toBeDefined();
    const payload = JSON.parse(postCall![1]!.body as string);

    expect(payload).toMatchObject({
      title: "Tech Summit 2026",
      clientName: "Global Tech Corp",
      venueName: "Convention Hall A",
      venueAddress: "Whitefield, Bengaluru",
      eventDate: "2026-11-20",
      equipment: [
        {
          equipmentId: "eq1",
          quantity: 4,
        },
      ],
      contract: {
        amount: 150000,
        effectiveDate: "2026-11-20",
        description: "Contract for Tech Summit 2026",
      },
      advance: {
        amount: 50000,
        effectiveDate: "2026-11-20",
        receiverAccount: "AZ-2",
        description: "Client advance for Tech Summit 2026",
      },
    });
    expect(payload.contract.idempotencyKey).toBeDefined();
    expect(payload.advance.idempotencyKey).toBeDefined();

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/productions/prod-new-123");
    });

    // Invalidation check
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ["productions"] }));
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ["dashboard"] }));
    expect(invalidateSpy).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ["finance"] }));
  });

  it("submits unified intake payload without advance if advance is 0", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions" && options?.method === "POST") {
        return Promise.resolve({ id: "prod-no-advance" });
      }
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    fireEvent.change(screen.getByLabelText("Production title"), { target: { value: "Acoustic Night" } });
    fireEvent.change(screen.getByLabelText("Client name"), { target: { value: "Cafe De Bangalore" } });
    fireEvent.change(screen.getByLabelText("Venue name"), { target: { value: "Indiranagar Lounge" } });
    fireEvent.change(screen.getByLabelText("Event date"), { target: { value: "2026-11-20" } });

    // Set contract only
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "80000" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Contract" }));

    // Leave advance as default 0
    const submitBtn = screen.getByRole("button", { name: "Create Production" });
    expect(submitBtn).toBeEnabled();
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(api).toHaveBeenCalledWith(
        "/productions",
        expect.objectContaining({
          method: "POST",
        }),
      );
    });

    const postCall = vi
      .mocked(api)
      .mock.calls.find((call) => call[0] === "/productions" && call[1]?.method === "POST");
    const payload = JSON.parse(postCall![1]!.body as string);
    expect(payload.contract.amount).toBe(80000);
    expect(payload.advance).toBeUndefined();
  });

  it("retains modal open and displays finance error when onboarding fails", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions" && options?.method === "POST") {
        return Promise.reject(
          new ApiError("RECEIPT_EXCEEDS_OUTSTANDING", "Receipt exceeds the production amount outstanding."),
        );
      }
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    fireEvent.change(screen.getByLabelText("Production title"), { target: { value: "Event X" } });
    fireEvent.change(screen.getByLabelText("Client name"), { target: { value: "Client Y" } });
    fireEvent.change(screen.getByLabelText("Venue name"), { target: { value: "Venue Z" } });
    fireEvent.change(screen.getByLabelText("Event date"), { target: { value: "2026-11-20" } });

    // Set contract
    await userEvent.click(screen.getByRole("button", { name: "Set Contract" }));
    fireEvent.change(screen.getByLabelText("Contract amount"), { target: { value: "50000" } });
    await userEvent.click(screen.getByRole("button", { name: "Save Contract" }));

    const submitBtn = screen.getByRole("button", { name: "Create Production" });
    await userEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText("Receipt exceeds the production amount outstanding.")).toBeInTheDocument();
    });
    // Modal is still open and fields are retained
    expect(screen.getByLabelText("Production title")).toHaveValue("Event X");
  });
});
