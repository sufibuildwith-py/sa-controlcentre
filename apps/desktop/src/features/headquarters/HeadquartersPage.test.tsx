import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { HeadquartersPage } from "./HeadquartersPage";

const hq = vi.hoisted(() => ({
  overview: vi.fn(),
  equipment: vi.fn(),
  movements: vi.fn(),
  attention: vi.fn(),
  config: vi.fn(),
  createEquipment: vi.fn(),
  createCategory: vi.fn(),
  createUnit: vi.fn(),
  createLocation: vi.fn(),
  stock: vi.fn(),
  reserve: vi.fn(),
  createDispatch: vi.fn(),
  confirmDispatch: vi.fn(),
  createTransfer: vi.fn(),
  confirmTransfer: vi.fn(),
  createReturn: vi.fn(),
  confirmReturn: vi.fn(),
  reconcile: vi.fn(),
}));
vi.mock("./headquarters.api", () => ({ headquartersApi: hq }));
vi.mock("../../lib/api", () => ({
  api: vi.fn(() => Promise.resolve([])),
  json: (body: unknown) => ({ body: JSON.stringify(body) }),
  ApiError: class extends Error {},
}));
afterEach(() => {
  cleanup();
  Object.values(hq).forEach((x) => x.mockReset());
});
function setup(items: unknown[] = []) {
  hq.overview.mockResolvedValue({
    controlled: 50,
    available: 30,
    reserved: 10,
    deployed: 10,
    attention: 1,
    today: [],
    activeProductions: [],
  });
  hq.equipment.mockResolvedValue({
    items,
    page: 0,
    size: 50,
    total: items.length,
  });
  hq.movements.mockResolvedValue({ items: [], page: 0, size: 50, total: 0 });
  hq.attention.mockResolvedValue([
    {
      id: "a1",
      type: "UNACCOUNTED_RETURN",
      severity: "CRITICAL",
      title: "Return is not fully accounted",
      detail: "2 units unresolved",
      createdAt: "2026-09-23T10:00:00Z",
    },
  ]);
  hq.config.mockResolvedValue({ categories: [], units: [], locations: [] });
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <HeadquartersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
describe("Headquarters command surface", () => {
  it("shows real command metrics and attention before detail", async () => {
    setup();
    expect(await screen.findByText("30")).toBeInTheDocument();
    expect(screen.getByText("Usable after commitments")).toBeInTheDocument();
    expect(screen.getByText(/1 open item/)).toBeInTheDocument();
  });
  it("provides a useful empty inventory state", async () => {
    setup();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Inventory" }));
    expect(
      await screen.findByText("No equipment registered"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add equipment" }),
    ).toBeInTheDocument();
  });
});
