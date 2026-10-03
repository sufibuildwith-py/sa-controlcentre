import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NavigatorRoster } from "./NavigatorRoster";
import { AddEmployeeModal } from "./AddEmployeeModal";
import { MakeTeamModal } from "./MakeTeamModal";
import { applyNavigatorEvent } from "./useNavigatorRealtime";
import {
  isValidCoordinate,
  navigatorEnabled,
  resolveInitialMapCenter,
  stateFor,
  VARANASI_HQ,
  type NavigatorItem,
  type NavigatorSnapshot,
} from "./navigator.types";

const navApi = vi.hoisted(() => ({
  live: vi.fn(),
  pairing: vi.fn(),
  teams: vi.fn(),
  createTeam: vi.fn(),
  deleteTeam: vi.fn(),
}));

const apiMock = vi.hoisted(() => vi.fn());

vi.mock("./navigator.api", () => ({
  navigatorApi: navApi,
}));

vi.mock("../../lib/api", () => ({
  api: apiMock,
}));

afterEach(() => {
  cleanup();
  Object.values(navApi).forEach((x) => x.mockReset());
  apiMock.mockReset();
});

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

const now = Date.now();
const items: NavigatorItem[] = [
  {
    employeeRef: "emp-1",
    deviceId: "d1",
    state: "LIVE",
    employeeName: "Amaan Khan",
    roleTitle: "Editor",
    latitude: 25.321,
    longitude: 82.981,
    recordedAt: new Date(now - 20_000).toISOString(),
    productionId: "prod-1",
    productionTitle: "Sharma Wedding",
    teamName: "Camera Unit A",
  },
  {
    employeeRef: "emp-2",
    deviceId: "d2",
    state: "STALE",
    employeeName: "Farhan Ali",
    roleTitle: "Camera",
    latitude: 25.325,
    longitude: 82.985,
    recordedAt: new Date(now - 120_000).toISOString(),
    productionId: "prod-1",
    productionTitle: "Sharma Wedding",
    teamName: "Camera Unit A",
  },
  {
    employeeRef: "emp-3",
    deviceId: "d3",
    state: "LIVE",
    employeeName: "Pooja Verma",
    roleTitle: "Drone Pilot",
    latitude: 25.33,
    longitude: 82.99,
    recordedAt: new Date(now - 15_000).toISOString(),
    productionId: "prod-1",
    productionTitle: "Sharma Wedding",
    teamName: "Drone Crew",
  },
  {
    employeeRef: "emp-4",
    deviceId: "d4",
    state: "OFF_DUTY",
    employeeName: "Sarah Khan",
    roleTitle: "Producer",
    recordedAt: new Date(now - 400_000).toISOString(),
    productionId: "prod-1",
    productionTitle: "Sharma Wedding",
  },
  {
    employeeRef: "emp-5",
    deviceId: "d5",
    state: "OFFLINE",
    employeeName: "Vikram Singh",
    roleTitle: "Grip",
    recordedAt: new Date(now - 600_000).toISOString(),
  },
];

describe("Navigator desktop", () => {
  it("is excluded unless the explicit demo flag is enabled", () =>
    expect(navigatorEnabled).toBe(false));

  it("uses one shared state threshold model", () => {
    expect(
      stateFor(true, true, new Date(now - 60_000).toISOString(), now),
    ).toBe("LIVE");
    expect(
      stateFor(true, true, new Date(now - 61_000).toISOString(), now),
    ).toBe("STALE");
    expect(
      stateFor(true, true, new Date(now - 301_000).toISOString(), now),
    ).toBe("OFFLINE");
    expect(stateFor(false, true, undefined, now)).toBe("OFF_DUTY");
    expect(stateFor(false, false, undefined, now)).toBe("UNPAIRED");
  });

  describe("Dynamic Map Centering Algorithm", () => {
    it("validates coordinates correctly", () => {
      expect(isValidCoordinate(25.3176, 82.9739)).toBe(true);
      expect(isValidCoordinate(0, 0)).toBe(false);
      expect(isValidCoordinate(undefined, 82.9739)).toBe(false);
      expect(isValidCoordinate(25.3176, null as unknown as number)).toBe(false);
      expect(isValidCoordinate(100, 82.9739)).toBe(false); // Lat > 90
      expect(isValidCoordinate(25.3176, 200)).toBe(false); // Lng > 180
    });

    it("Priority 1: centers on latest LIVE employee with coordinates", () => {
      const center = resolveInitialMapCenter(items);
      expect(center).toEqual([82.99, 25.33]);
    });

    it("Priority 2: centers on latest historical employee location if no LIVE coords", () => {
      const staleOnlyItems: NavigatorItem[] = [
        {
          employeeRef: "emp-2",
          deviceId: "d2",
          state: "STALE",
          latitude: 25.325,
          longitude: 82.985,
          recordedAt: new Date(now - 120_000).toISOString(),
        },
      ];
      const center = resolveInitialMapCenter(staleOnlyItems);
      expect(center).toEqual([82.985, 25.325]);
    });

    it("Priority 3: falls back to SA Productions Varanasi HQ [82.9739, 25.3176]", () => {
      const emptyItemsCenter = resolveInitialMapCenter([]);
      expect(emptyItemsCenter).toEqual(VARANASI_HQ);
      expect(emptyItemsCenter).toEqual([82.9739, 25.3176]);

      const itemsWithNoCoords: NavigatorItem[] = [
        {
          employeeRef: "emp-x",
          deviceId: "dx",
          state: "LIVE",
          recordedAt: new Date().toISOString(),
        },
      ];
      const fallbackCenter = resolveInitialMapCenter(itemsWithNoCoords);
      expect(fallbackCenter).toEqual(VARANASI_HQ);
    });

    it("ensures hardcoded Kanpur [80.3319, 26.4499] is never returned as fallback", () => {
      const center = resolveInitialMapCenter([]);
      const KANPUR: [number, number] = [80.3319, 26.4499];
      expect(center).not.toEqual(KANPUR);
      expect(center).toEqual(VARANASI_HQ);
    });
  });

  describe("Navigator Roster and Team Grouping", () => {
    it("renders joined local employee identity and team grouping", () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      expect(screen.getByText("Amaan Khan")).toBeVisible();
      expect(screen.getByText("Camera Unit A")).toBeVisible();
      expect(screen.getByText("Drone Crew")).toBeVisible();
      expect(screen.getByText("General Crew")).toBeVisible();
      expect(
        screen.getByText("GENERAL FIELD TEAM / UNASSIGNED"),
      ).toBeVisible();
      expect(screen.getByText("Vikram Singh")).toBeVisible();
    });

    it("filters the roster to live employees", async () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      await userEvent.click(screen.getByRole("button", { name: "Live" }));
      expect(screen.getByText("Amaan Khan")).toBeVisible();
      expect(screen.getByText("Pooja Verma")).toBeVisible();
      expect(screen.queryByText("Farhan Ali")).not.toBeInTheDocument();
      expect(screen.queryByText("Sarah Khan")).not.toBeInTheDocument();
      expect(screen.queryByText("Vikram Singh")).not.toBeInTheDocument();
    });

    it("filters the roster to needs attention", async () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      await userEvent.click(
        screen.getByRole("button", { name: "Needs attention" }),
      );
      expect(screen.getByText("Farhan Ali")).toBeVisible();
      expect(screen.getByText("Vikram Singh")).toBeVisible();
      expect(screen.queryByText("Amaan Khan")).not.toBeInTheDocument();
      expect(screen.queryByText("Sarah Khan")).not.toBeInTheDocument();
    });

    it("filters the roster to off duty", async () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      await userEvent.click(screen.getByRole("button", { name: "Off duty" }));
      expect(screen.getByText("Sarah Khan")).toBeVisible();
      expect(screen.queryByText("Amaan Khan")).not.toBeInTheDocument();
      expect(screen.queryByText("Vikram Singh")).not.toBeInTheDocument();
    });

    it("filters the roster to production members", async () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      await userEvent.click(
        screen.getByRole("button", { name: "Production" }),
      );
      expect(screen.getByText("Amaan Khan")).toBeVisible();
      expect(screen.getByText("Farhan Ali")).toBeVisible();
      expect(screen.getByText("Pooja Verma")).toBeVisible();
      expect(screen.getByText("Sarah Khan")).toBeVisible();
      expect(screen.queryByText("Vikram Singh")).not.toBeInTheDocument();
    });

    it("searches the roster by employee name and team name", async () => {
      render(<NavigatorRoster items={items} onSelect={() => {}} />);
      const searchInput = screen.getByLabelText("Search Navigator team");

      await userEvent.type(searchInput, "Sarah");
      expect(screen.getByText("Sarah Khan")).toBeVisible();
      expect(screen.queryByText("Amaan Khan")).not.toBeInTheDocument();

      await userEvent.clear(searchInput);
      expect(screen.getByText("Amaan Khan")).toBeVisible();

      await userEvent.type(searchInput, "Drone");
      expect(screen.getByText("Pooja Verma")).toBeVisible();
      expect(screen.queryByText("Farhan Ali")).not.toBeInTheDocument();
    });

    it("focuses a team on header click and allows clearing focus", async () => {
      const handleFocusTeam = vi.fn();
      const { rerender } = render(
        <NavigatorRoster
          items={items}
          focusedTeam={null}
          onFocusTeam={handleFocusTeam}
          onSelect={() => {}}
        />,
      );

      const teamHeader = screen.getByRole("button", {
        name: /Camera Unit A/i,
      });
      await userEvent.click(teamHeader);

      expect(handleFocusTeam).toHaveBeenCalledWith({
        teamName: "Camera Unit A",
        employeeRefs: ["emp-1", "emp-2"],
      });

      rerender(
        <NavigatorRoster
          items={items}
          focusedTeam={{
            teamName: "Camera Unit A",
            employeeRefs: ["emp-1", "emp-2"],
          }}
          onFocusTeam={handleFocusTeam}
          onSelect={() => {}}
        />,
      );

      expect(screen.getByText(/Focusing/i)).toBeVisible();
      expect(
        screen.getAllByText("Camera Unit A").length,
      ).toBeGreaterThanOrEqual(1);

      expect(screen.getByText("Amaan Khan")).toBeVisible();
      expect(screen.getByText("Farhan Ali")).toBeVisible();
      expect(screen.queryByText("Pooja Verma")).not.toBeInTheDocument();
      expect(screen.queryByText("Vikram Singh")).not.toBeInTheDocument();

      await userEvent.click(
        screen.getByRole("button", { name: "Clear team filter" }),
      );
      expect(handleFocusTeam).toHaveBeenCalledWith(null);
    });
  });

  describe("Realtime Event Handlers", () => {
    it("applies realtime location without replacing local identity", () => {
      const snapshot: NavigatorSnapshot = {
        items,
        syncedAt: new Date(0).toISOString(),
        organizationPublicId: "org",
      };
      const changed = applyNavigatorEvent(snapshot, {
        type: "LOCATION_UPDATED",
        employeeRef: "emp-1",
        deviceId: "d1",
        sessionId: "s",
        latitude: 26.4,
        longitude: 80.3,
        recordedAt: new Date().toISOString(),
      });
      expect(changed.items[0]).toMatchObject({
        employeeName: "Amaan Khan",
        state: "LIVE",
        latitude: 26.4,
      });
    });

    it("turns stopped sessions into off-duty state", () => {
      const snapshot: NavigatorSnapshot = {
        items,
        syncedAt: new Date(0).toISOString(),
        organizationPublicId: "org",
      };
      expect(
        applyNavigatorEvent(snapshot, {
          type: "TRACKING_STOPPED",
          employeeRef: "emp-1",
          deviceId: "d1",
        }).items[0].state,
      ).toBe("OFF_DUTY");
    });
  });

  describe("AddEmployeeModal", () => {
    it("searches active employees and generates pairing code", async () => {
      apiMock.mockImplementation((url: string) => {
        if (url.startsWith("/employees")) {
          return Promise.resolve([
            {
              id: "emp-10",
              displayName: "Rohan Gupta",
              roleTitle: "Sound Engineer",
            },
          ]);
        }
        return Promise.resolve([]);
      });
      navApi.pairing.mockResolvedValue({
        code: "948201",
        expiresAt: new Date(Date.now() + 900_000).toISOString(),
        qrPayload: "sa-nav://pair?code=948201",
      });

      const client = createTestQueryClient();
      render(
        <QueryClientProvider client={client}>
          <AddEmployeeModal
            open={true}
            onOpenChange={() => {}}
            pairedItems={items}
          />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Rohan Gupta")).toBeVisible();
      });

      const pairBtn = screen.getByRole("button", {
        name: /Pair Device/i,
      });
      await userEvent.click(pairBtn);

      await waitFor(() => {
        expect(screen.getByText(/948\s*201/)).toBeVisible();
        expect(screen.getByText(/Expires/i)).toBeVisible();
      });

      expect(navApi.pairing).toHaveBeenCalledWith("emp-10");
    });
  });

  describe("MakeTeamModal", () => {
    it("creates a team under selected production with selected members", async () => {
      apiMock.mockImplementation((url: string) => {
        if (url.startsWith("/productions")) {
          return Promise.resolve([
            { id: "prod-100", title: "Royal Palace Gala", status: "CONFIRMED" },
          ]);
        }
        if (url.startsWith("/employees")) {
          return Promise.resolve([
            {
              id: "emp-20",
              displayName: "Deepak Joshi",
              roleTitle: "Cinematographer",
            },
            {
              id: "emp-21",
              displayName: "Ananya Roy",
              roleTitle: "Gaffer",
            },
          ]);
        }
        return Promise.resolve([]);
      });
      navApi.createTeam.mockResolvedValue({
        productionId: "prod-100",
        productionTitle: "Royal Palace Gala",
        teams: [
          {
            teamName: "Lighting Unit",
            memberCount: 1,
            members: [],
          },
        ],
      });

      const client = createTestQueryClient();
      const onOpenChange = vi.fn();
      render(
        <QueryClientProvider client={client}>
          <MakeTeamModal open={true} onOpenChange={onOpenChange} />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText(/Royal Palace Gala/i)).toBeVisible();
        expect(screen.getByText("Deepak Joshi")).toBeVisible();
        expect(screen.getByText("Ananya Roy")).toBeVisible();
      });

      const teamNameInput = screen.getByLabelText("Team Name");
      await userEvent.type(teamNameInput, "Lighting Unit");

      const deepakCheckbox = screen.getByRole("checkbox", {
        name: /Deepak Joshi/i,
      });
      await userEvent.click(deepakCheckbox);

      const submitBtn = screen.getByRole("button", {
        name: /Create Team/i,
      });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(navApi.createTeam).toHaveBeenCalledWith(
          "prod-100",
          "Lighting Unit",
          ["emp-20"],
        );
        expect(onOpenChange).toHaveBeenCalledWith(false);
      });
    });

    it("displays useful backend error message when create team fails", async () => {
      apiMock.mockImplementation((url: string) => {
        if (url.startsWith("/productions")) {
          return Promise.resolve([
            { id: "prod-100", title: "Royal Palace Gala", status: "CONFIRMED" },
          ]);
        }
        if (url.startsWith("/employees")) {
          return Promise.resolve([
            { id: "emp-20", displayName: "Deepak Joshi", roleTitle: "Cinematographer" },
          ]);
        }
        return Promise.resolve([]);
      });
      navApi.createTeam.mockRejectedValue(
        new Error("Employee already has an overlapping commitment."),
      );

      const client = createTestQueryClient();
      render(
        <QueryClientProvider client={client}>
          <MakeTeamModal open={true} onOpenChange={() => {}} />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Deepak Joshi")).toBeVisible();
      });

      await userEvent.type(screen.getByLabelText("Team Name"), "Lighting Unit");
      await userEvent.click(screen.getByRole("checkbox", { name: /Deepak Joshi/i }));
      await userEvent.click(screen.getByRole("button", { name: /Create Team/i }));

      await waitFor(() => {
        expect(
          screen.getByText("Employee already has an overlapping commitment."),
        ).toBeVisible();
      });
    });

    it("displays field-level validation errors when returned by backend", async () => {
      apiMock.mockImplementation((url: string) => {
        if (url.startsWith("/productions")) {
          return Promise.resolve([
            { id: "prod-100", title: "Royal Palace Gala", status: "CONFIRMED" },
          ]);
        }
        if (url.startsWith("/employees")) {
          return Promise.resolve([
            { id: "emp-20", displayName: "Deepak Joshi", roleTitle: "Cinematographer" },
          ]);
        }
        return Promise.resolve([]);
      });
      const errorWithFields = new Error("Validation failed");
      (errorWithFields as any).fields = {
        teamName: "Team name must be 120 characters or less",
      };
      navApi.createTeam.mockRejectedValue(errorWithFields);

      const client = createTestQueryClient();
      render(
        <QueryClientProvider client={client}>
          <MakeTeamModal open={true} onOpenChange={() => {}} />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText("Deepak Joshi")).toBeVisible();
      });

      await userEvent.type(screen.getByLabelText("Team Name"), "Overly Long Name");
      await userEvent.click(screen.getByRole("checkbox", { name: /Deepak Joshi/i }));
      await userEvent.click(screen.getByRole("button", { name: /Create Team/i }));

      await waitFor(() => {
        expect(
          screen.getByText("Team name must be 120 characters or less"),
        ).toBeVisible();
      });
    });
  });
});
