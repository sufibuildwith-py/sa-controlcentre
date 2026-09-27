import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ProductionsPage } from "./ProductionsPage";

vi.mock("../../lib/api", () => ({
  api: vi.fn(),
  json: (body: unknown) => ({
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }),
}));

vi.mock("../headquarters/headquarters.api", () => ({
  headquartersApi: {
    equipment: vi.fn(),
  },
}));

import { api } from "../../lib/api";
import { headquartersApi } from "../headquarters/headquarters.api";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
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

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ProductionsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("ProductionsPage — Single Intake Flow", () => {
  it("renders list and opens single intake form with all composite sections", async () => {
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
    expect(screen.getByLabelText("Production title")).toBeInTheDocument();
    expect(screen.getByLabelText("Client name")).toBeInTheDocument();
    expect(screen.getByLabelText("Venue name")).toBeInTheDocument();
    expect(screen.getByLabelText("Start time")).toBeInTheDocument();
    expect(screen.getByLabelText("End time")).toBeInTheDocument();
    expect(screen.getByLabelText("Select crew employee")).toBeInTheDocument();
    expect(screen.getByLabelText("Task title")).toBeInTheDocument();
    expect(screen.getByLabelText("Select equipment")).toBeInTheDocument();
    expect(screen.getByLabelText("Production notes")).toBeInTheDocument();
  });

  it("submits unified intake payload with schedule, crew, tasks, and equipment", async () => {
    vi.mocked(api).mockImplementation((path: string, options?: any) => {
      if (path === "/productions" && options?.method === "POST") {
        return Promise.resolve({ id: "prod-new-123" });
      }
      if (path.startsWith("/productions")) return Promise.resolve(mockProductions);
      if (path === "/employees") return Promise.resolve(mockEmployees);
      return Promise.resolve(null);
    });
    vi.mocked(headquartersApi.equipment).mockResolvedValue(mockEquipment as any);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Summer Music Festival")).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /production/i }));

    // Core fields
    fireEvent.change(screen.getByLabelText("Production title"), { target: { value: "Tech Summit 2026" } });
    fireEvent.change(screen.getByLabelText("Client name"), { target: { value: "Global Tech Corp" } });
    fireEvent.change(screen.getByLabelText("Venue name"), { target: { value: "Convention Hall A" } });
    fireEvent.change(screen.getByLabelText("Venue address"), { target: { value: "Whitefield, Bengaluru" } });

    // Optional schedule
    fireEvent.change(screen.getByLabelText("Start time"), { target: { value: "09:00" } });
    fireEvent.change(screen.getByLabelText("End time"), { target: { value: "18:00" } });

    // Add crew
    fireEvent.change(screen.getByLabelText("Select crew employee"), { target: { value: "e1" } });
    fireEvent.change(screen.getByLabelText("Crew role"), { target: { value: "FOH Audio Engineer" } });
    await userEvent.click(screen.getByRole("button", { name: "+ Add Crew" }));

    expect(screen.getByText("Azeem Sound")).toBeInTheDocument();
    expect(screen.getByText(/FOH Audio Engineer/)).toBeInTheDocument();

    // Add task
    fireEvent.change(screen.getByLabelText("Task title"), { target: { value: "Soundcheck speaker arrays" } });
    fireEvent.change(screen.getByLabelText("Task priority"), { target: { value: "HIGH" } });
    await userEvent.click(screen.getByRole("button", { name: "+ Add Task" }));

    expect(screen.getByText("Soundcheck speaker arrays")).toBeInTheDocument();

    // Add equipment
    fireEvent.change(screen.getByLabelText("Select equipment"), { target: { value: "eq1" } });
    fireEvent.change(screen.getByLabelText("Equipment quantity"), { target: { value: "4" } });
    await userEvent.click(screen.getByRole("button", { name: "+ Add Gear" }));

    expect(screen.getByText("Line Array Speaker")).toBeInTheDocument();
    expect(screen.getByText(/4 units/)).toBeInTheDocument();

    // Notes
    fireEvent.change(screen.getByLabelText("Production notes"), { target: { value: "VIP arrivals at 8:30am" } });

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
      description: "VIP arrivals at 8:30am",
      startTime: "09:00",
      endTime: "18:00",
      venueName: "Convention Hall A",
      venueAddress: "Whitefield, Bengaluru",
      priority: "NORMAL",
      progressPercent: 0,
      crew: [
        {
          employeeId: "e1",
          productionRole: "FOH Audio Engineer",
          attendanceRequired: true,
        },
      ],
      tasks: [
        {
          title: "Soundcheck speaker arrays",
          priority: "HIGH",
          status: "TODO",
        },
      ],
      equipment: [
        {
          equipmentId: "eq1",
          quantity: 4,
        },
      ],
    });

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/productions/prod-new-123");
    });
  }, 15000);
});
