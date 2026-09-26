import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Party360Drawer } from "./Party360Drawer";
import { financeApi, Party360View } from "./finance.api";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("./finance.api", () => ({
  financeApi: {
    party360: vi.fn(),
    recordInvoicePayment: vi.fn(),
    recordPartyReceipt: vi.fn(),
  },
}));

const mockParty360Data: Party360View = {
  party: {
    id: "party-uuid-1",
    displayName: "Acme Entertainment",
    legalName: "Acme Entertainment Pvt Ltd",
    role: "CLIENT",
    gstin: "27AABCU9603R1ZM",
    notes: "VIP Client",
    active: true,
  },
  chargeTotal: 8000, // ₹8,000.00
  chargeReceived: 2000, // ₹2,000.00
  chargeOutstanding: 6000, // ₹6,000.00
  invoiceTotal: 15000, // ₹15,000.00
  invoicePaid: 5000, // ₹5,000.00
  invoiceOutstanding: 10000, // ₹10,000.00
  totalOutstanding: 16000, // ₹16,000.00
  totalReceived: 7000, // ₹7,000.00
  charges: [
    {
      id: "charge-1",
      date: "2026-03-01",
      amount: 8000,
      paid: 2000,
      outstanding: 6000,
      description: "Direct production charge - Studio Rental",
      productionId: "prod-1",
      productionTitle: "Red Carpet Gala 2026",
    },
  ],
  invoices: [
    {
      id: "inv-1",
      invoiceNumber: "INV-2026-001",
      date: "2026-03-05",
      financialYear: "2025-2026",
      taxMode: "TAX_INVOICE",
      total: 15000,
      baseAmount: 15000,
      cgstAmount: 0,
      sgstAmount: 0,
      igstAmount: 0,
      tds: 0,
      paid: 5000,
      outstanding: 10000,
      billStatus: "PARTIALLY_PAID",
      productionId: "prod-1",
      productionTitle: "Red Carpet Gala 2026",
    },
  ],
  bills: [],
  productions: [
    {
      id: "prod-1",
      title: "Red Carpet Gala 2026",
      clientName: "Acme Entertainment",
      eventDate: "2026-03-15",
      venueName: "Grand Ballroom",
      status: "CONFIRMED",
    },
  ],
  timeline: [
    {
      id: "tx-1",
      transactionNo: 101,
      type: "PARTY_CHARGE",
      date: "2026-03-01",
      description: "Direct charge for equipment setup",
      amount: 8000,
      status: "POSTED",
      track: "DIRECT_CHARGE",
      ownerAccount: "AZ-2",
      productionId: "prod-1",
      productionTitle: "Red Carpet Gala 2026",
    },
    {
      id: "tx-2",
      transactionNo: 102,
      type: "INVOICE",
      date: "2026-03-05",
      description: "Formal Invoice INV-2026-001",
      amount: 15000,
      status: "POSTED",
      track: "FORMAL_INVOICE",
      productionId: "prod-1",
      productionTitle: "Red Carpet Gala 2026",
      invoiceId: "inv-1",
    },
  ],
};

function renderDrawer(partyId: string = "party-uuid-1", onClose = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return {
    onClose,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <Party360Drawer partyId={partyId} onClose={onClose} />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

describe("Party360Drawer Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(financeApi.party360).mockResolvedValue(mockParty360Data);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders drawer with party details and authoritative dual-track KPIs", async () => {
    renderDrawer();

    expect(await screen.findByText("Acme Entertainment")).toBeInTheDocument();
    expect(screen.getByText(/27AABCU9603R1ZM/)).toBeInTheDocument();
    expect(screen.getByText("CLIENT")).toBeInTheDocument();

    // Dual-track KPI cards
    // Formal Invoices track
    expect(screen.getAllByText(/₹15,000\.00/).length).toBeGreaterThanOrEqual(1); // Formal Invoiced
    expect(screen.getAllByText(/₹10,000\.00/).length).toBeGreaterThanOrEqual(1); // Invoice Balance

    // Direct Charges track
    expect(screen.getAllByText(/₹8,000\.00/).length).toBeGreaterThanOrEqual(1); // Direct Charges
    expect(screen.getAllByText(/₹6,000\.00/).length).toBeGreaterThanOrEqual(1); // Direct Balance

    // Aggregated presentation
    expect(screen.getByTestId("total-outstanding")).toHaveTextContent(
      "₹16,000.00",
    );
    expect(screen.getByTestId("total-received")).toHaveTextContent("₹7,000.00");
  });

  it("switches tabs between Timeline, Invoices, Charges, and Productions", async () => {
    const user = userEvent.setup();
    renderDrawer();

    await screen.findByText("Acme Entertainment");

    // Timeline tab is active by default
    expect(screen.getByTestId("timeline-pane")).toBeInTheDocument();
    expect(screen.getByText("Formal Invoice INV-2026-001")).toBeInTheDocument();
    expect(
      screen.getByText("Direct charge for equipment setup"),
    ).toBeInTheDocument();

    // Switch to Invoices tab
    await user.click(screen.getByTestId("tab-invoices"));
    expect(screen.getByTestId("invoices-pane")).toBeInTheDocument();
    expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
    expect(screen.getByText("PARTIALLY_PAID")).toBeInTheDocument();

    // Switch to Charges tab
    await user.click(screen.getByTestId("tab-charges"));
    expect(screen.getByTestId("charges-pane")).toBeInTheDocument();
    expect(
      screen.getByText("Direct production charge - Studio Rental"),
    ).toBeInTheDocument();

    // Switch to Productions tab
    await user.click(screen.getByTestId("tab-productions"));
    expect(screen.getByTestId("productions-pane")).toBeInTheDocument();
    expect(screen.getByText("Red Carpet Gala 2026")).toBeInTheDocument();
    expect(screen.getByText(/Grand Ballroom/)).toBeInTheDocument();
  });

  it("navigates to billing module when clicking 'Create Bill'", async () => {
    const user = userEvent.setup();
    renderDrawer();

    await screen.findByText("Acme Entertainment");

    const createBillBtn = screen.getByTestId("create-bill-btn");
    await user.click(createBillBtn);

    expect(mockNavigate).toHaveBeenCalledWith(
      "/billing?counterpartyId=party-uuid-1",
    );
  });

  it("Record Settlement modal requires explicit target and explicit owner account without defaulting", async () => {
    const user = userEvent.setup();
    renderDrawer();

    await screen.findByText("Acme Entertainment");

    // Open General Settlement
    const openSettlementBtn = screen.getByTestId("record-settlement-btn");
    await user.click(openSettlementBtn);

    expect(screen.getByTestId("settlement-modal")).toBeInTheDocument();

    // Verify submit button is disabled initially because owner account is not selected
    const submitBtn = screen.getByTestId("submit-settlement-btn");
    expect(submitBtn).toBeDisabled();

    // Verify payer account select has no default value
    const payerSelect = screen.getByTestId("payer-account-select");
    expect((payerSelect as HTMLSelectElement).value).toBe("");

    // Select explicit owner account AZ-2
    await user.selectOptions(payerSelect, "AZ-2");

    // Now submit button should be enabled
    expect(submitBtn).not.toBeDisabled();
  });

  it("posts invoice payment when Formal Invoice target is chosen", async () => {
    const user = userEvent.setup();
    vi.mocked(financeApi.recordInvoicePayment).mockResolvedValue({
      id: "alloc-1",
      invoiceId: "inv-1",
      amount: 1000000,
    } as any);

    renderDrawer();
    await screen.findByText("Acme Entertainment");

    // Open settlement modal
    await user.click(screen.getByTestId("record-settlement-btn"));

    // Target is Formal Invoice by default when unpaid invoices exist
    expect(screen.getByTestId("target-invoice-btn")).toHaveClass("active");
    expect(screen.getByTestId("invoice-select")).toBeInTheDocument();

    // Select owner account
    await user.selectOptions(
      screen.getByTestId("payer-account-select"),
      "AZ-2",
    );

    // Confirm settlement
    await user.click(screen.getByTestId("submit-settlement-btn"));

    await waitFor(() => {
      expect(financeApi.recordInvoicePayment).toHaveBeenCalledTimes(1);
    });

    const callArgs = vi.mocked(financeApi.recordInvoicePayment).mock
      .calls[0][0];
    expect(callArgs.invoiceId).toBe("inv-1");
    expect(callArgs.amount).toBe(10000); // 10000 INR
    expect(callArgs.receiverAccount).toBe("AZ-2");
    expect(callArgs.idempotencyKey).toBeDefined();

    // CRITICAL DUAL-TRACK INVARIANT: partyReceipt must NOT be called for invoice settlement!
    expect(financeApi.recordPartyReceipt).not.toHaveBeenCalled();
  });

  it("posts party receipt when Direct Charge target is chosen", async () => {
    const user = userEvent.setup();
    vi.mocked(financeApi.recordPartyReceipt).mockResolvedValue({
      id: "rec-1",
      counterpartyId: "party-uuid-1",
      amount: 600000,
    } as any);

    renderDrawer();
    await screen.findByText("Acme Entertainment");

    // Open settlement modal
    await user.click(screen.getByTestId("record-settlement-btn"));

    // Switch target to Direct Charge
    await user.click(screen.getByTestId("target-charge-btn"));
    expect(screen.getByTestId("target-charge-btn")).toHaveClass("active");

    // Select owner account AK-2
    await user.selectOptions(
      screen.getByTestId("payer-account-select"),
      "AK-2",
    );

    // Confirm settlement
    await user.click(screen.getByTestId("submit-settlement-btn"));

    await waitFor(() => {
      expect(financeApi.recordPartyReceipt).toHaveBeenCalledTimes(1);
    });

    const callArgs = vi.mocked(financeApi.recordPartyReceipt).mock.calls[0][0];
    expect(callArgs.counterpartyId).toBe("party-uuid-1");
    expect(callArgs.amount).toBe(6000); // 6000 INR
    expect(callArgs.receiverAccount).toBe("AK-2");
    expect(callArgs.idempotencyKey).toBeDefined();

    // CRITICAL DUAL-TRACK INVARIANT: invoicePayment must NOT be called for direct charge settlement!
    expect(financeApi.recordInvoicePayment).not.toHaveBeenCalled();
  });

  it("retains the exact same idempotency key across retried submissions after a failed attempt", async () => {
    const user = userEvent.setup();
    vi.mocked(financeApi.recordInvoicePayment)
      .mockRejectedValueOnce(new Error("Network timeout: retryable"))
      .mockResolvedValueOnce({
        id: "alloc-2",
        invoiceId: "inv-1",
        amount: 1000000,
      } as any);

    renderDrawer();
    await screen.findByText("Acme Entertainment");

    // Open settlement modal
    await user.click(screen.getByTestId("record-settlement-btn"));

    // Select owner account
    await user.selectOptions(
      screen.getByTestId("payer-account-select"),
      "AZ-2",
    );

    // First submission attempt (fails)
    await user.click(screen.getByTestId("submit-settlement-btn"));

    expect(await screen.findByTestId("settlement-error")).toHaveTextContent(
      "Network timeout: retryable",
    );
    expect(financeApi.recordInvoicePayment).toHaveBeenCalledTimes(1);
    const firstCallKey = vi.mocked(financeApi.recordInvoicePayment).mock
      .calls[0][0].idempotencyKey;
    expect(firstCallKey).toBeDefined();

    // User retries within the same modal attempt
    await user.click(screen.getByTestId("submit-settlement-btn"));

    await waitFor(() => {
      expect(financeApi.recordInvoicePayment).toHaveBeenCalledTimes(2);
    });

    const secondCallKey = vi.mocked(financeApi.recordInvoicePayment).mock
      .calls[1][0].idempotencyKey;

    // CRITICAL IDEMPOTENCY INVARIANT: The retry must send the exact same idempotencyKey!
    expect(secondCallKey).toBe(firstCallKey);
  });

  it("generates a fresh idempotency key for a new intentional settlement attempt", async () => {
    const user = userEvent.setup();
    vi.mocked(financeApi.recordInvoicePayment).mockResolvedValue({
      id: "alloc-1",
      invoiceId: "inv-1",
      amount: 10000,
    } as any);

    renderDrawer();
    await screen.findByText("Acme Entertainment");

    // First modal attempt
    await user.click(screen.getByTestId("record-settlement-btn"));
    await user.selectOptions(
      screen.getByTestId("payer-account-select"),
      "AZ-2",
    );
    await user.click(screen.getByTestId("submit-settlement-btn"));

    await waitFor(() => {
      expect(financeApi.recordInvoicePayment).toHaveBeenCalledTimes(1);
    });
    const firstAttemptKey = vi.mocked(financeApi.recordInvoicePayment).mock
      .calls[0][0].idempotencyKey;

    // Second separate modal attempt
    await user.click(screen.getByTestId("record-settlement-btn"));
    await user.selectOptions(
      screen.getByTestId("payer-account-select"),
      "AZ-2",
    );
    await user.click(screen.getByTestId("submit-settlement-btn"));

    await waitFor(() => {
      expect(financeApi.recordInvoicePayment).toHaveBeenCalledTimes(2);
    });
    const secondAttemptKey = vi.mocked(financeApi.recordInvoicePayment).mock
      .calls[1][0].idempotencyKey;

    expect(secondAttemptKey).not.toBe(firstAttemptKey);
  });
});
