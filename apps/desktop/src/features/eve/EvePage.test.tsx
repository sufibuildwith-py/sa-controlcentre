import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { EvePage } from "./EvePage";
import { eveApi } from "./eve.api";
import type { EvePlan, EveQueryResponse } from "./eve.types";

vi.mock("./eve.api", () => ({
  eveApi: {
    query: vi.fn(),
    listSessions: vi.fn().mockResolvedValue([]),
    getSession: vi.fn(),
    createSession: vi.fn(),
    confirmPlan: vi.fn(),
    cancelPlan: vi.fn(),
    getPlan: vi.fn(),
    listPlansForSession: vi.fn().mockResolvedValue([]),
    listSuggestions: vi.fn().mockResolvedValue([]),
    getSuggestion: vi.fn(),
    dismissSuggestion: vi.fn(),
    resolveSuggestion: vi.fn(),
    evaluateSuggestions: vi.fn().mockResolvedValue([]),
    getStatus: vi.fn().mockResolvedValue({
      modelProvider: "LOCAL_QWEN",
      status: "READY",
      modelName: "Qwen3-4B-Q4_K_M.gguf",
      modelVersion: "Q4_K_M",
    }),
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

  it("displays local intelligence status badge when status is ready", async () => {
    renderEvePage();

    await waitFor(() => {
      expect(screen.getByText(/Local Intelligence/)).toBeInTheDocument();
    });
  });

  it("submits a query and displays cognitive reasoning trace when present", async () => {
    const mockResponse: EveQueryResponse = {
      sessionId: "session-cognitive-123",
      message: {
        id: "msg-cog-1",
        sessionId: "session-cognitive-123",
        role: "ASSISTANT",
        content: "There are 3 productions scheduled for next week.",
        createdAt: "2026-10-01T10:00:00Z",
      },
      trace: [],
      reasoning: [
        {
          sequence: 1,
          stage: "GOAL_INTERPRETATION",
          summary: "Identified goal: COUNT productions with temporal constraint",
          status: "COMPLETED",
          timestamp: "2026-10-01T10:00:00.100Z",
        },
        {
          sequence: 2,
          stage: "TEMPORAL_GROUNDING",
          summary: "Resolved 'next week' to 2026-10-05 through 2026-10-11 in Asia/Kolkata",
          status: "COMPLETED",
          timestamp: "2026-10-01T10:00:00.200Z",
        },
        {
          sequence: 3,
          stage: "TOOL_EXECUTION",
          summary: "Executed search_productions with date range",
          status: "COMPLETED",
          timestamp: "2026-10-01T10:00:00.300Z",
          relatedTool: "search_productions",
        },
      ],
      status: "COMPLETED",
      candidates: [],
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce(mockResponse);

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "next week kitne events hai");

    const sendButton = screen.getByRole("button", { name: /Send/i });
    await user.click(sendButton);

    await waitFor(() => {
      expect(screen.getByText("Cognitive Reasoning Trace")).toBeInTheDocument();
    });

    expect(screen.getByText("GOAL_INTERPRETATION")).toBeInTheDocument();
    expect(screen.getByText(/Identified goal: COUNT productions/)).toBeInTheDocument();
    expect(screen.getByText("TEMPORAL_GROUNDING")).toBeInTheDocument();
    expect(screen.getByText(/Resolved 'next week' to 2026-10-05/)).toBeInTheDocument();
    expect(screen.getByText("TOOL_EXECUTION")).toBeInTheDocument();
    expect(screen.getByText("search_productions")).toBeInTheDocument();
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

    // Verify user role is displayed as 'You' (not 'Mamu')
    expect(screen.getByText("You")).toBeInTheDocument();

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

  it("handles multi-turn conversation and carries sessionId forward in subsequent queries", async () => {
    const turn1Response: EveQueryResponse = {
      sessionId: "session-multi-1",
      message: {
        id: "msg-t1",
        sessionId: "session-multi-1",
        role: "ASSISTANT",
        content: "Royal Wedding has 2 assigned crew members: Raj Sharma, Amit Kumar.",
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [
        {
          id: "tr-t1",
          sessionId: "session-multi-1",
          seq: 1,
          eventType: "COMPLETED",
          status: "OK",
          label: "Query completed",
          createdAt: "2026-09-28T10:00:01Z",
        },
      ],
      context: {
        owner: "Azeem Khan",
        timezone: "Asia/Kolkata",
        date: "2026-09-28",
        referencedEntities: [
          { id: "prod-1", type: "PRODUCTION", name: "Royal Wedding", code: "PROD-01" },
        ],
        evidence: [
          { domain: "PRODUCTION", label: "Crew Count", value: "2" },
        ],
      },
      status: "COMPLETED",
      candidates: [],
    };

    const turn2Response: EveQueryResponse = {
      sessionId: "session-multi-1",
      message: {
        id: "msg-t2",
        sessionId: "session-multi-1",
        role: "ASSISTANT",
        content: "Yes, Raj Sharma was assigned to Royal Wedding as Lead Sound Engineer.",
        createdAt: "2026-09-28T10:01:00Z",
      },
      trace: [
        {
          id: "tr-t2",
          sessionId: "session-multi-1",
          seq: 1,
          eventType: "COMPLETED",
          status: "OK",
          label: "Query completed",
          createdAt: "2026-09-28T10:01:01Z",
        },
      ],
      context: {
        owner: "Azeem Khan",
        timezone: "Asia/Kolkata",
        date: "2026-09-28",
        referencedEntities: [
          { id: "prod-1", type: "PRODUCTION", name: "Royal Wedding", code: "PROD-01" },
          { id: "emp-1", type: "EMPLOYEE", name: "Raj Sharma", code: "SA-01" },
        ],
        evidence: [
          { domain: "PRODUCTION", label: "Crew Check", value: "Assigned" },
        ],
      },
      status: "COMPLETED",
      candidates: [],
    };

    vi.mocked(eveApi.query)
      .mockResolvedValueOnce(turn1Response)
      .mockResolvedValueOnce(turn2Response);

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "Royal Wedding mein kaun kaun tha?");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    expect(
      await screen.findByText(/Royal Wedding has 2 assigned crew members/i),
    ).toBeInTheDocument();

    // Turn 2 follow-up
    await user.type(textarea, "Usme Sharma bhi tha?");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    await waitFor(() => {
      expect(eveApi.query).toHaveBeenLastCalledWith(
        "Usme Sharma bhi tha?",
        "session-multi-1",
      );
    });

    expect(
      await screen.findByText(/Yes, Raj Sharma was assigned to Royal Wedding/i),
    ).toBeInTheDocument();
  });

  it("creates a new session when + New Session is clicked", async () => {
    vi.mocked(eveApi.createSession).mockResolvedValueOnce({
      id: "new-session-789",
      title: "New Command Session",
      status: "ACTIVE",
      createdAt: "2026-09-28T10:00:00Z",
      updatedAt: "2026-09-28T10:00:00Z",
      messages: [],
    });

    renderEvePage();
    const user = userEvent.setup();

    const newSessionBtn = screen.getByRole("button", { name: /\+ New Session/i });
    await user.click(newSessionBtn);

    await waitFor(() => {
      expect(eveApi.createSession).toHaveBeenCalled();
    });

    expect(await screen.findByText(/Session new-sess/i)).toBeInTheDocument();
  });

  it("displays proposed EvePlan card with risk badge and actions, and confirms successfully", async () => {
    const mockPlan: EvePlan = {
      planId: "plan-456",
      sessionId: "session-p3",
      intent: "PROPOSE_EMPLOYEE_PAYMENT",
      summary: "Post employee payment of ₹3,000.00 to Raj Sharma (SA-01) from Azeem Khan (AZ-2)",
      riskTier: "FINANCIAL_WRITE",
      confirmationRequired: true,
      planHash: "hash-1234567890abcdef",
      version: 1,
      status: "PROPOSED",
      actions: [
        {
          actionId: "act-1",
          seq: 1,
          domain: "FINANCE",
          commandType: "POST_EMPLOYEE_PAYMENT",
          targetEntityId: "emp-1",
          targetEntityName: "Raj Sharma",
          parameters: {
            employeeId: "emp-1",
            employeeName: "Raj Sharma",
            amount: 3000,
            payerAccountId: "AZ-2",
            payerAccountName: "Azeem Khan",
          },
          estimatedEffect: "Employee payable decreases by ₹3,000.00; Azeem Khan cash balance decreases by ₹3,000.00",
          status: "PENDING",
        },
      ],
    };

    const mockResponse: EveQueryResponse = {
      sessionId: "session-p3",
      message: {
        id: "msg-p3",
        sessionId: "session-p3",
        role: "ASSISTANT",
        content: "I have prepared an execution plan to post ₹3,000.00 to Raj Sharma.",
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [],
      status: "COMPLETED",
      candidates: [],
      plan: mockPlan,
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce(mockResponse);
    vi.mocked(eveApi.confirmPlan).mockResolvedValueOnce({
      planId: "plan-456",
      sessionId: "session-p3",
      status: "COMPLETED",
      summary: "Execution completed successfully. 1 action verified.",
      message: {
        id: "msg-executed",
        sessionId: "session-p3",
        role: "ASSISTANT",
        content: "Payment of ₹3,000.00 to Raj Sharma has been posted and verified. Transaction ID: txn-789.",
        createdAt: "2026-09-28T10:01:00Z",
      },
      actions: [
        {
          ...mockPlan.actions[0],
          status: "VERIFIED",
          canonicalRecordId: "txn-789",
          verificationResult: {
            status: "VERIFIED",
            ruleName: "EMPLOYEE_PAYMENT_VERIFICATION",
            expectedState: "POSTED",
            actualState: "POSTED",
            verifiedAt: "2026-09-28T10:01:00Z",
            notes: "Verified in finance_transactions",
          },
        },
      ],
      trace: [],
    });

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "Sharma ko 3000 de do");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    expect(await screen.findByText("Governed Execution Plan")).toBeInTheDocument();
    expect(screen.getByText("FINANCIAL WRITE")).toBeInTheDocument();
    expect(screen.getByText(/Post employee payment of ₹3,000.00/i)).toBeInTheDocument();
    expect(screen.getByText("Confirm & Post Payment")).toBeInTheDocument();

    await user.click(screen.getByText("Confirm & Post Payment"));

    await waitFor(() => {
      expect(eveApi.confirmPlan).toHaveBeenCalledWith("plan-456", {
        sessionId: "session-p3",
        planId: "plan-456",
        planVersion: 1,
        planHash: "hash-1234567890abcdef",
        note: "Confirmed in UI",
      });
    });

    expect(
      await screen.findByText(/Payment of ₹3,000.00 to Raj Sharma has been posted and verified/i),
    ).toBeInTheDocument();
  });

  it("allows operator to cancel a proposed plan cleanly without execution", async () => {
    const mockPlan: EvePlan = {
      planId: "plan-cancel-1",
      sessionId: "session-cancel",
      intent: "PROPOSE_EMPLOYEE_PAYMENT",
      summary: "Post employee payment of ₹3,000.00 to Raj Sharma",
      riskTier: "FINANCIAL_WRITE",
      confirmationRequired: true,
      planHash: "hash-cancel-123",
      version: 1,
      status: "PROPOSED",
      actions: [],
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce({
      sessionId: "session-cancel",
      message: {
        id: "msg-can",
        sessionId: "session-cancel",
        role: "ASSISTANT",
        content: "Plan proposed.",
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [],
      status: "COMPLETED",
      candidates: [],
      plan: mockPlan,
    });

    vi.mocked(eveApi.cancelPlan).mockResolvedValueOnce({
      ...mockPlan,
      status: "CANCELLED",
    });

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "Sharma payout");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    expect(await screen.findByText("Cancel")).toBeInTheDocument();
    await user.click(screen.getByText("Cancel"));

    await waitFor(() => {
      expect(eveApi.cancelPlan).toHaveBeenCalledWith("plan-cancel-1", {
        sessionId: "session-cancel",
        planId: "plan-cancel-1",
        reason: "Cancelled by operator",
      });
    });

    expect(
      await screen.findByText(/Plan was cancelled. No changes were made to system of record/i),
    ).toBeInTheDocument();
  });

  it("handles stale plan error and alerts the operator when preconditions change", async () => {
    const mockPlan: EvePlan = {
      planId: "plan-stale-1",
      sessionId: "session-stale",
      intent: "PROPOSE_EMPLOYEE_PAYMENT",
      summary: "Post employee payment of ₹5,000.00 to Raj Sharma",
      riskTier: "FINANCIAL_WRITE",
      confirmationRequired: true,
      planHash: "hash-stale-123",
      version: 1,
      status: "PROPOSED",
      actions: [],
    };

    vi.mocked(eveApi.query).mockResolvedValueOnce({
      sessionId: "session-stale",
      message: {
        id: "msg-stale",
        sessionId: "session-stale",
        role: "ASSISTANT",
        content: "Plan proposed.",
        createdAt: "2026-09-28T10:00:00Z",
      },
      trace: [],
      status: "COMPLETED",
      candidates: [],
      plan: mockPlan,
    });

    vi.mocked(eveApi.confirmPlan).mockRejectedValueOnce({
      code: "STALE_PLAN",
      message: "Preconditions changed: payable balance updated",
    });

    renderEvePage();
    const user = userEvent.setup();

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    await user.type(textarea, "Sharma 5000");
    await user.click(screen.getByRole("button", { name: /Send/i }));

    expect(await screen.findByText("Confirm & Post Payment")).toBeInTheDocument();
    await user.click(screen.getByText("Confirm & Post Payment"));

    expect(
      await screen.findByText(/Execution failed: Preconditions changed: payable balance updated/i),
    ).toBeInTheDocument();
  });
});

describe("EvePage — Phase 4 Continuous Intelligence & Proactive Suggestions", () => {
  it("renders active suggestions and allows inspecting evidence and investigating", async () => {
    vi.mocked(eveApi.listSuggestions).mockResolvedValueOnce([
      {
        id: "sug-1",
        type: "OUTSTANDING_EMPLOYEE_PAYMENT",
        status: "ACTIVE",
        priority: "HIGH",
        title: "Outstanding payable balance for Rehan Ali",
        summary: "Rehan Ali has ₹6000.00 outstanding payable balance. Review or schedule payment.",
        targetDomain: "FINANCE",
        canonicalEntityType: "EMPLOYEE",
        canonicalEntityId: "emp-rehan-1",
        canonicalEntityName: "Rehan Ali",
        evidence: [
          {
            domain: "FINANCE",
            entityType: "EMPLOYEE",
            entityId: "emp-rehan-1",
            label: "Outstanding Balance",
            value: "₹6000.00",
            observedAt: "2026-09-29T00:00:00Z",
          },
        ],
        dedupeKey: "OUTSTANDING_EMPLOYEE_PAYMENT:emp-rehan-1",
        createdAt: "2026-09-29T00:00:00Z",
        updatedAt: "2026-09-29T00:00:00Z",
      },
    ]);

    renderEvePage();
    const user = userEvent.setup();

    expect(await screen.findByText("Outstanding payable balance for Rehan Ali")).toBeInTheDocument();
    expect(screen.getByText(/₹6000.00 outstanding payable balance/i)).toBeInTheDocument();
    expect(screen.getByText("HIGH")).toBeInTheDocument();

    // Inspect evidence
    const evidenceBtn = screen.getByText(/Evidence \(1\)/i);
    await user.click(evidenceBtn);
    expect(screen.getByText("Outstanding Balance")).toBeInTheDocument();
    expect(screen.getByText("₹6000.00")).toBeInTheDocument();

    // Click Investigate in EVE -> populates query input
    const investigateBtn = screen.getByText("Investigate in EVE");
    await user.click(investigateBtn);

    const textarea = screen.getByPlaceholderText(/Ask Eve about employee finance/i);
    expect(textarea).toHaveValue("Review payment for Rehan Ali");
  });

  it("allows dismissing an active suggestion", async () => {
    vi.mocked(eveApi.listSuggestions).mockResolvedValueOnce([
      {
        id: "sug-2",
        type: "APPROACHING_PRODUCTION_OPEN_TASKS",
        status: "ACTIVE",
        priority: "MEDIUM",
        title: "Cultural Event MIPS is approaching with 3 open tasks",
        summary: "Production Cultural Event MIPS has 3 open tasks remaining.",
        targetDomain: "PRODUCTION",
        canonicalEntityType: "PRODUCTION",
        canonicalEntityId: "prod-mips-1",
        canonicalEntityName: "Cultural Event MIPS",
        evidence: [],
        dedupeKey: "APPROACHING_PRODUCTION_OPEN_TASKS:prod-mips-1",
        createdAt: "2026-09-29T00:00:00Z",
        updatedAt: "2026-09-29T00:00:00Z",
      },
    ]);
    vi.mocked(eveApi.dismissSuggestion).mockResolvedValueOnce({
      id: "sug-2",
      type: "APPROACHING_PRODUCTION_OPEN_TASKS",
      status: "DISMISSED",
      priority: "MEDIUM",
      title: "Cultural Event MIPS is approaching with 3 open tasks",
      summary: "Production Cultural Event MIPS has 3 open tasks remaining.",
      targetDomain: "PRODUCTION",
      canonicalEntityType: "PRODUCTION",
      canonicalEntityId: "prod-mips-1",
      evidence: [],
      dedupeKey: "APPROACHING_PRODUCTION_OPEN_TASKS:prod-mips-1",
      createdAt: "2026-09-29T00:00:00Z",
      updatedAt: "2026-09-29T00:00:00Z",
    });

    renderEvePage();
    const user = userEvent.setup();

    expect(await screen.findByText("Cultural Event MIPS is approaching with 3 open tasks")).toBeInTheDocument();
    const dismissBtn = screen.getByRole("button", { name: "Dismiss" });
    await user.click(dismissBtn);

    expect(eveApi.dismissSuggestion).toHaveBeenCalledWith("sug-2");
  });
});
