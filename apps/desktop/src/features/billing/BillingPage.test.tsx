import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { BillingPage } from "./BillingPage";

vi.mock("./billing.api", () => ({
  billingApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    issue: vi.fn(),
    cancel: vi.fn(),
    export: vi.fn(),
    exportPdf: vi.fn(),
  },
}));

vi.mock("../finance/finance.api", () => ({
  financeApi: {
    counterparties: vi.fn(),
    productions: vi.fn(),
  },
}));

vi.mock("../../lib/api", () => ({
  api: vi.fn(),
}));

import { billingApi } from "./billing.api";
import { financeApi } from "../finance/finance.api";
import { api } from "../../lib/api";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage(initialEntry = "/billing") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <QueryClientProvider client={queryClient}>
        <BillingPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("BillingPage", () => {
  it("renders the billing workspace and 17 line items", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([]);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c1", displayName: "Mega Events" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [{ id: "p1", title: "Concert 2026" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);

    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Billing" }),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole("heading", { name: "New customer bill" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Save draft/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("No bills yet")).toBeInTheDocument();

    // Check all 17 lines exist
    expect(screen.getByLabelText("Quantity 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity 17")).toBeInTheDocument();
  });

  it("filters out blank lines when saving draft", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([]);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c1", displayName: "Mega Events" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    } as any);
    vi.mocked(billingApi.create).mockResolvedValue({
      bill: {
        id: "b1",
        bill_number: "SA-2026-001",
        bill_date: "2026-09-25",
        financial_year: "2026-27",
        customer: "Mega Events",
        status: "DRAFT",
        gross_total: 15000,
        advance_paid: 0,
      },
      lines: [
        {
          id: "l1",
          lineNo: 1,
          quantity: 1,
          days: 1,
          description: "Audio Setup",
          rate: 15000,
          amount: 15000,
          reference: "",
        },
      ],
    });

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Billing" }),
      ).toBeInTheDocument();
    });

    await user.type(
      screen.getByPlaceholderText("e.g. SA-2026-001"),
      "SA-2026-001",
    );
    await user.selectOptions(
      screen.getByRole("combobox", { name: /Customer/i }),
      "c1",
    );
    await user.type(screen.getByLabelText("Description 1"), "Audio Setup");
    await user.clear(screen.getByLabelText("Rate 1"));
    await user.type(screen.getByLabelText("Rate 1"), "15000");

    const saveBtn = screen.getByRole("button", { name: /Save draft/i });
    expect(saveBtn).toBeEnabled();
    await user.click(saveBtn);

    await waitFor(() => {
      expect(billingApi.create).toHaveBeenCalledTimes(1);
    });

    const sentPayload = vi.mocked(billingApi.create).mock.calls[0][0];
    expect(sentPayload.billNumber).toBe("SA-2026-001");
    expect(sentPayload.counterpartyId).toBe("c1");
    // Verify only the 1 populated line is sent, NOT all 17 lines
    expect(sentPayload.lines).toHaveLength(1);
    expect(sentPayload.lines[0].description).toBe("Audio Setup");
    expect(sentPayload.lines[0].rate).toBe(15000);
  });

  it("exposes full lifecycle actions for a selected draft bill", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([
      {
        id: "b1",
        bill_number: "SA-2026-001",
        bill_date: "2026-09-25",
        financial_year: "2026-27",
        customer: "Mega Events",
        status: "DRAFT",
        gross_total: 15000,
        advance_paid: 0,
      },
    ]);
    vi.mocked(billingApi.get).mockResolvedValue({
      bill: {
        id: "b1",
        bill_number: "SA-2026-001",
        bill_date: "2026-09-25",
        financial_year: "2026-27",
        counterparty_id: "c1",
        customer: "Mega Events",
        status: "DRAFT",
        gross_total: 15000,
        advance_paid: 0,
        subtotal: 15000,
        tax_amount: 0,
        tax_mode: "NONE",
        cgst_rate: 0,
        sgst_rate: 0,
        igst_rate: 0,
        discount: 0,
        freight: 0,
      },
      lines: [
        {
          id: "l1",
          lineNo: 1,
          quantity: 1,
          days: 1,
          description: "Audio Setup",
          rate: 15000,
          amount: 15000,
          reference: "",
        },
      ],
    });
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c1", displayName: "Mega Events" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    } as any);

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("SA-2026-001")).toBeInTheDocument();
    });

    // Click saved draft in sidebar
    await user.click(screen.getByText("SA-2026-001"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Bill #SA-2026-001" }),
      ).toBeInTheDocument();
    });

    // Check desired UX for saved draft: [Save Draft] [Export XLSX] [Export PDF] [Issue Bill] [Cancel]
    expect(
      screen.getByRole("button", { name: /Save draft/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Export XLSX/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Export PDF/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Issue Bill/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Cancel/i })).toBeInTheDocument();
  });

  it("exposes export actions and locks fields for an issued bill", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([
      {
        id: "b2",
        bill_number: "SA-2026-002",
        bill_date: "2026-09-25",
        financial_year: "2026-27",
        customer: "Mega Events",
        status: "ISSUED",
        gross_total: 25000,
        advance_paid: 5000,
      },
    ]);
    vi.mocked(billingApi.get).mockResolvedValue({
      bill: {
        id: "b2",
        bill_number: "SA-2026-002",
        bill_date: "2026-09-25",
        financial_year: "2026-27",
        counterparty_id: "c1",
        customer: "Mega Events",
        status: "ISSUED",
        gross_total: 25000,
        advance_paid: 5000,
        subtotal: 25000,
        tax_amount: 0,
        tax_mode: "NONE",
        canonical_invoice_id: "inv-uuid-1",
      },
      lines: [
        {
          id: "l1",
          lineNo: 1,
          quantity: 1,
          days: 1,
          description: "Stage Lighting",
          rate: 25000,
          amount: 25000,
          reference: "",
        },
      ],
    });
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c1", displayName: "Mega Events" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    } as any);

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("SA-2026-002")).toBeInTheDocument();
    });

    await user.click(screen.getByText("SA-2026-002"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Bill #SA-2026-002" }),
      ).toBeInTheDocument();
    });

    // Check desired UX for issued bill: [Export XLSX] [Export PDF]
    expect(
      screen.getByRole("button", { name: /Export XLSX/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Export PDF/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Issue Bill/i }),
    ).not.toBeInTheDocument();

    // Verify fields are locked
    expect(screen.getByPlaceholderText("e.g. SA-2026-001")).toBeDisabled();
    expect(screen.getByLabelText("Quantity 1")).toBeDisabled();

    // Verify canonical finance link is displayed
    expect(
      screen.getByRole("link", { name: /View in Finance/i }),
    ).toBeInTheDocument();
  });

  it("prefills production, matching customer, event name, venue, and date when navigated with productionId", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([]);
    vi.mocked(api).mockResolvedValue({
      id: "prod-1",
      title: "Mega Showcase",
      clientName: "Northstar Foods",
      eventDate: "2026-11-20",
      venueName: "Grand Arena",
    } as any);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c-northstar", displayName: "Northstar Foods" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [
        {
          id: "prod-1",
          title: "Mega Showcase",
          clientName: "Northstar Foods",
          eventDate: "2026-11-20",
        },
      ],
      page: 0,
      size: 50,
      total: 1,
    } as any);

    renderPage("/billing?productionId=prod-1");

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Billing" }),
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(
        screen.getByRole("combobox", { name: /Production \/ Job/i }),
      ).toHaveValue("prod-1");
      expect(screen.getByRole("combobox", { name: /Customer/i })).toHaveValue(
        "c-northstar",
      );
      expect(screen.getByPlaceholderText("e.g. Annual Gala 2026")).toHaveValue(
        "Mega Showcase",
      );
      expect(screen.getByPlaceholderText("Venue location")).toHaveValue(
        "Grand Arena",
      );
      expect(screen.getByLabelText(/Date/i)).toHaveValue("2026-11-20");
    });

    // Verify financial amounts are NOT inferred or copied
    expect(screen.getByLabelText("Rate 1")).toHaveValue(0);
    expect(screen.getAllByText("₹0.00").length).toBeGreaterThan(0);
  });

  it("prefills customer when navigated with counterpartyId", async () => {
    vi.mocked(billingApi.list).mockResolvedValue([]);
    vi.mocked(financeApi.counterparties).mockResolvedValue({
      items: [{ id: "c-sharma", displayName: "Sharma Family" }],
      page: 0,
      size: 50,
      total: 1,
    } as any);
    vi.mocked(financeApi.productions).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
    } as any);

    renderPage("/billing?counterpartyId=c-sharma");

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Billing" }),
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByRole("combobox", { name: /Customer/i })).toHaveValue(
        "c-sharma",
      );
    });

    // Verify financial amounts are NOT inferred or copied
    expect(screen.getByLabelText("Rate 1")).toHaveValue(0);
  });
});
