import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { EvePage } from "./EvePage";
import { eveApi } from "./eve.api";
import type { EveQueryResponse } from "./eve.types";

vi.mock("./eve.api", () => ({
  eveApi: {
    query: vi.fn(),
    listSessions: vi.fn().mockResolvedValue([]),
    getSession: vi.fn(),
    createSession: vi.fn(),
  },
}));

vi.mock("../../lib/api", () => ({
  api: vi.fn(),
  clearSessionToken: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

import * as Tooltip from "@radix-ui/react-tooltip";

function renderEvePage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <MemoryRouter initialEntries={["/eve"]}>
      <QueryClientProvider client={queryClient}>
        <Tooltip.Provider delayDuration={0}>
          <EvePage />
        </Tooltip.Provider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("EvePage — Phase 1 Grounded Command Console", () => {
  it("renders the Eve command console with empty state and prompt suggestions", async () => {
    renderEvePage();

    expect(screen.getByText("Eve")).toBeInTheDocument();
    expect(screen.getByText("Command Composer")).toBeInTheDocument();
    expect(screen.getByText("Eve is ready")).toBeInTheDocument();
    expect(screen.getByText("How much does Sharma still need?")).toBeInTheDocument();
  });

  it("submits a query and displays grounded response, activity trace, and evidence panel", async () => {
    const mockResponse: EveQueryResponse = {
      sessionId: "session-123",
      message: {
        id: "msg-1",
        sessionId: "session-123",
        role: "ASSISTANT",
        content:
          "Raj Sharma (SA-01) currently has ₹40,000.00 outstanding. Total earned to date is ₹1,20,000.00 with ₹80,000.00 already disbursed.",
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [
        {
          id: "tr-1",
          sessionId: "session-123",
          seq: 1,
          eventType: "STARTED",
          status: "OK",
          label: "Request received",
          detail: "Processing query: How much does Sharma still need?",
          createdAt: "2026-09-28T10:00:00Z",
        },
        {
          id: "tr-2",
          sessionId: "session-123",
          seq: 2,
          eventType: "MATCHED",
          status: "OK",
          label: "Entity matched",
          detail: "Resolved to Raj Sharma (SA-01) via EXACT_NAME",
          createdAt: "2026-09-28T10:00:01Z",
        },
        {
          id: "tr-3",
          sessionId: "session-123",
          seq: 3,
          eventType: "COMPLETED",
          status: "OK",
          label: "Query completed",
          detail: "Grounded financial state verified from PostgreSQL",
          createdAt: "2026-09-28T10:00:02Z",
        },
      ],
      context: {
        owner: "Azeem Khan",
        timezone: "Asia/Kolkata",
        date: "2026-09-28",
        referencedEntities: [
          {
            id: "emp-1",
            type: "EMPLOYEE",
            name: "Raj Sharma",
            code: "SA-01",
          },
        ],
        evidence: [
          {
            domain: "FINANCE",
            label: "Total Earned",
            value: "₹1,20,000.00",
          },
          {
            domain: "FINANCE",
            label: "Outstanding Balance",
            value: "₹40,000.00",
          },
        ],
      },
      status: "COMPLETED",
      candidates: [],
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce(mockResponse);

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(
      /Ask Eve about employee finance/i,
    );
    await user.type(textarea, "How much does Sharma still need?");

    const sendButton = screen.getByRole("button", { name: /Send/i });
    await user.click(sendButton);

    await waitFor(() => {
      expect(eveApi.query).toHaveBeenCalledWith(
        "How much does Sharma still need?",
        null,
      );
    });

    // Verify assistant answer appears
    expect(
      await screen.findByText(/Raj Sharma \(SA-01\) currently has ₹40,000.00 outstanding/i),
    ).toBeInTheDocument();

    // Verify activity trace appears
    expect(screen.getByText("Request received")).toBeInTheDocument();
    expect(screen.getByText("Entity matched")).toBeInTheDocument();
    expect(screen.getByText("Query completed")).toBeInTheDocument();

    // Verify system context & evidence panel appears
    expect(screen.getByText("Raj Sharma")).toBeInTheDocument();
    expect(screen.getByText("SA-01")).toBeInTheDocument();
    expect(screen.getByText("₹40,000.00")).toBeInTheDocument();
  });

  it("handles ambiguity and renders selectable disambiguation candidates", async () => {
    const mockAmbiguityResponse: EveQueryResponse = {
      sessionId: "session-456",
      message: {
        id: "msg-amb",
        sessionId: "session-456",
        role: "ASSISTANT",
        content:
          'I found multiple matching team members for "Sharma". Please choose which employee you meant:',
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [
        {
          id: "tr-amb-1",
          sessionId: "session-456",
          seq: 1,
          eventType: "STARTED",
          status: "OK",
          label: "Request received",
          detail: "Processing query: Sharma",
          createdAt: "2026-09-28T10:00:00Z",
        },
        {
          id: "tr-amb-2",
          sessionId: "session-456",
          seq: 2,
          eventType: "BLOCKED",
          status: "BLOCKED",
          label: "Ambiguity detected",
          detail: 'Found 2 matches for "Sharma". Clarification required.',
          createdAt: "2026-09-28T10:00:01Z",
        },
      ],
      context: null,
      status: "CLARIFICATION_REQUIRED",
      candidates: [
        {
          id: "emp-1",
          type: "EMPLOYEE",
          displayName: "Raj Sharma",
          code: "SA-01",
          detail: "Lead Sound Engineer",
        },
        {
          id: "emp-2",
          type: "EMPLOYEE",
          displayName: "Amit Sharma",
          code: "SA-02",
          detail: "Lighting Technician",
        },
      ],
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce(mockAmbiguityResponse);

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "Sharma");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    expect(
      await screen.findByText(/I found multiple matching team members for "Sharma"/i),
    ).toBeInTheDocument();

    expect(screen.getByText("Raj Sharma")).toBeInTheDocument();
    expect(screen.getByText("Amit Sharma")).toBeInTheDocument();
    expect(screen.getByText(/Ambiguity detected/i)).toBeInTheDocument();
  });
});
