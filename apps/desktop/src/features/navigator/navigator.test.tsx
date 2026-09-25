import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NavigatorRoster } from "./NavigatorRoster";
import { applyNavigatorEvent } from "./useNavigatorRealtime";
import {
  navigatorEnabled,
  stateFor,
  type NavigatorItem,
  type NavigatorSnapshot,
} from "./navigator.types";
afterEach(cleanup);
const now = Date.now();
const items: NavigatorItem[] = [
  {
    employeeRef: "a",
    deviceId: "d1",
    state: "LIVE",
    employeeName: "Amaan Khan",
    roleTitle: "Editor",
    recordedAt: new Date(now - 20_000).toISOString(),
    productionTitle: "Sharma Wedding",
  },
  {
    employeeRef: "b",
    deviceId: "d2",
    state: "STALE",
    employeeName: "Farhan Ali",
    roleTitle: "Camera",
    recordedAt: new Date(now - 120_000).toISOString(),
  },
  {
    employeeRef: "c",
    deviceId: "d3",
    state: "OFF_DUTY",
    employeeName: "Sarah Khan",
    roleTitle: "Producer",
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
  it("renders joined local employee identity without coordinates", () => {
    render(<NavigatorRoster items={items} onSelect={() => {}} />);
    expect(screen.getByText("Amaan Khan")).toBeVisible();
    expect(screen.queryByText(/26\.4/)).not.toBeInTheDocument();
  });
  it("filters the roster to live employees", async () => {
    render(<NavigatorRoster items={items} onSelect={() => {}} />);
    await userEvent.click(screen.getByRole("button", { name: "Live" }));
    expect(screen.getByText("Amaan Khan")).toBeVisible();
    expect(screen.queryByText("Farhan Ali")).not.toBeInTheDocument();
  });
  it("searches the roster", async () => {
    render(<NavigatorRoster items={items} onSelect={() => {}} />);
    await userEvent.type(
      screen.getByLabelText("Search Navigator team"),
      "Sarah",
    );
    expect(screen.getByText("Sarah Khan")).toBeVisible();
    expect(screen.queryByText("Amaan Khan")).not.toBeInTheDocument();
  });
  it("applies realtime location without replacing local identity", () => {
    const snapshot: NavigatorSnapshot = {
      items,
      syncedAt: new Date(0).toISOString(),
      organizationPublicId: "org",
    };
    const changed = applyNavigatorEvent(snapshot, {
      type: "LOCATION_UPDATED",
      employeeRef: "a",
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
        employeeRef: "a",
        deviceId: "d1",
      }).items[0].state,
    ).toBe("OFF_DUTY");
  });
});
