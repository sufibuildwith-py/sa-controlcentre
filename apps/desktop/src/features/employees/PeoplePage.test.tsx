import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PeoplePage } from "./PeoplePage";

vi.mock("../../lib/api", () => ({
  api: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
}));

import { api } from "../../lib/api";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockSummary = {
  activeEmployees: 14,
  onLeaveEmployees: 2,
  attendanceToday: 11,
  openTasks: 28,
  overdueTasks: 3,
};

const mockEmployees = [
  {
    id: "e1",
    employeeCode: "SA-01",
    displayName: "Amaan Khan",
    firstName: "Amaan",
    lastName: "Khan",
    phone: "+919000000000",
    roleTitle: "Lead Sound Engineer",
    department: "Audio Engineering",
    employmentType: "FULL_TIME",
    joiningDate: "2024-01-01",
    baseSalaryMinor: 4_500_000,
    salaryCurrency: "INR",
    status: "ACTIVE",
    todayAttendance: "PRESENT",
    activeTasksCount: 4,
    activeProductionsCount: 2,
  },
];

describe("PeoplePage — Summary Strip & Enriched Card", () => {
  it("renders summary strip metrics and enriched employee operational context", async () => {
    vi.mocked(api).mockImplementation((path: string) => {
      if (path === "/employees/summary") return Promise.resolve(mockSummary);
      if (path.startsWith("/employees")) return Promise.resolve(mockEmployees);
      return Promise.resolve(null);
    });

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { container } = render(
      <MemoryRouter initialEntries={["/people"]}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path="/people" element={<PeoplePage />} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Amaan Khan")).toBeInTheDocument();
    });

    // Summary strip
    const summaryStrip = container.querySelector(".people-summary-strip") as HTMLElement;
    expect(summaryStrip).toBeInTheDocument();
    expect(within(summaryStrip).getByText("Active")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("14")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("On Leave")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("2")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("Attendance today")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("11")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("Open tasks")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("28")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("Overdue tasks")).toBeInTheDocument();
    expect(within(summaryStrip).getByText("3")).toBeInTheDocument();

    // Employee enriched card
    expect(screen.getByText("PRESENT")).toBeInTheDocument();
    expect(screen.getByText(/4\s+tasks/)).toBeInTheDocument();
    expect(screen.getByText("2 active productions")).toBeInTheDocument();
  });
});
