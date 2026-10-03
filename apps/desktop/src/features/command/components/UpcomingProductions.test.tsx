import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { UpcomingProductions } from "./UpcomingProductions";
import type { DashboardProduction } from "../command.api";

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

const mockProductions: DashboardProduction[] = [
  {
    id: "prod-101",
    title: "Kapoor Sangeet Night",
    clientName: "Kapoor Family",
    venueName: "Taj Palace Banquet",
    eventDate: "2026-10-15",
    startTime: "18:00:00",
    endTime: "23:00:00",
    status: "PRE_PRODUCTION",
    priority: "URGENT",
    progressPercent: 45,
    contractedAmount: 750000,
    receivedAmount: 300000,
    taskCount: 12,
    openTaskCount: 5,
  },
  {
    id: "prod-102",
    title: "TechVista Annual Summit",
    clientName: "TechVista Global",
    venueName: "Convention Hall A",
    eventDate: "2026-10-22",
    status: "PLANNING",
    priority: "NORMAL",
    progressPercent: 20,
    contractedAmount: 400000,
    receivedAmount: 150000,
    taskCount: 8,
    openTaskCount: 4,
  },
];

describe("UpcomingProductions", () => {
  it("renders upcoming productions with complete canonical metadata", () => {
    render(
      <MemoryRouter>
        <UpcomingProductions productions={mockProductions} />
      </MemoryRouter>,
    );

    // Section header and count
    expect(screen.getByText("Upcoming Productions")).toBeInTheDocument();
    expect(screen.getByLabelText("2 upcoming productions")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /View all productions/i }),
    ).toBeInTheDocument();

    // Production 1: Kapoor Sangeet Night
    expect(screen.getByText("Kapoor Sangeet Night")).toBeInTheDocument();
    expect(screen.getByText("URGENT")).toBeInTheDocument();
    expect(screen.getByText(/15 Oct.*2026/)).toBeInTheDocument();
    expect(screen.getByText("Kapoor Family")).toBeInTheDocument();
    expect(screen.getByText("Taj Palace Banquet")).toBeInTheDocument();
    expect(screen.getByText("₹7,50,000")).toBeInTheDocument();
    expect(screen.getByText("₹3,00,000")).toBeInTheDocument();

    // Production 2: TechVista Annual Summit
    expect(screen.getByText("TechVista Annual Summit")).toBeInTheDocument();
    expect(screen.getByText(/22 Oct.*2026/)).toBeInTheDocument();
    expect(screen.getByText("TechVista Global")).toBeInTheDocument();
    expect(screen.getByText("Convention Hall A")).toBeInTheDocument();
    expect(screen.getByText("₹4,00,000")).toBeInTheDocument();
    expect(screen.getByText("₹1,50,000")).toBeInTheDocument();
  });

  it("navigates to production detail on card click and keyboard press", async () => {
    render(
      <MemoryRouter>
        <UpcomingProductions productions={mockProductions} />
      </MemoryRouter>,
    );

    const firstCard = screen.getByRole("button", {
      name: /Upcoming production: Kapoor Sangeet Night/i,
    });
    await userEvent.click(firstCard);
    expect(mockNavigate).toHaveBeenCalledWith("/productions/prod-101");

    // Keyboard navigation (Enter key)
    const secondCard = screen.getByRole("button", {
      name: /Upcoming production: TechVista Annual Summit/i,
    });
    secondCard.focus();
    await userEvent.keyboard("{Enter}");
    expect(mockNavigate).toHaveBeenCalledWith("/productions/prod-102");
  });

  it("navigates to productions list when clicking View all", async () => {
    render(
      <MemoryRouter>
        <UpcomingProductions productions={mockProductions} />
      </MemoryRouter>,
    );

    const viewAllBtn = screen.getByRole("button", {
      name: /View all productions/i,
    });
    await userEvent.click(viewAllBtn);
    expect(mockNavigate).toHaveBeenCalledWith("/productions");
  });

  it("renders clean empty state with action when zero upcoming productions", async () => {
    render(
      <MemoryRouter>
        <UpcomingProductions productions={[]} />
      </MemoryRouter>,
    );

    expect(screen.getByText("No upcoming productions")).toBeInTheDocument();
    expect(
      screen.getByText("No productions scheduled for upcoming dates."),
    ).toBeInTheDocument();
    const buttons = screen.getAllByRole("button", {
      name: /View all productions/i,
    });
    expect(buttons).toHaveLength(2);
    await userEvent.click(buttons[1]);
    expect(mockNavigate).toHaveBeenCalledWith("/productions");
  });
});
