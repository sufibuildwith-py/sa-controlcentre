import { expect, test, type Route } from "@playwright/test";
const reply = (route: Route, data: unknown) =>
  route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ data }),
  });
test.beforeEach(async ({ page }) => {
  await page.route("**/api/v1/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/auth/me"))
      return reply(route, {
        id: "owner",
        email: "owner@sa.local",
        displayName: "Owner",
        role: "OWNER",
      });
    if (path.endsWith("/dashboard"))
      return reply(route, {
        team: {
          employees: 0,
          present: 0,
          late: 0,
          absent: 0,
          leave: 0,
          incomplete: 0,
        },
        today: [],
        productions: [],
        workload: [],
        attention: [],
        communications: {},
      });
    if (path.endsWith("/headquarters/overview"))
      return reply(route, {
        controlled: 420,
        available: 286,
        reserved: 74,
        deployed: 60,
        attention: 2,
        today: [
          {
            id: "d1",
            reference: "DSP-DEMO-001",
            status: "PLANNED",
            type: "DISPATCH",
          },
        ],
        activeProductions: [
          { id: "p1", title: "Synthetic Production", quantity: 60 },
        ],
      });
    if (path.includes("/headquarters/equipment"))
      return reply(route, {
        items: [
          {
            id: "e1",
            name: "Demo Flight Case",
            internalCode: "DEMO-HQ-001",
            category: "Demo Operations",
            trackingMode: "QUANTITY",
            symbol: "pc",
            ownership: "SA_OWNED",
            controlled: 60,
            available: 32,
            reserved: 20,
            updatedAt: "2026-09-23T10:00:00Z",
          },
        ],
        page: 0,
        size: 50,
        total: 1,
      });
    if (path.endsWith("/headquarters/config"))
      return reply(route, {
        categories: [{ id: "c1", name: "Demo Operations" }],
        units: [
          { id: "u1", name: "Piece", symbol: "pc", decimalAllowed: false },
        ],
        locations: [
          {
            id: "l1",
            name: "Demo Main Warehouse",
            locationType: "HEADQUARTERS",
          },
        ],
      });
    if (path.endsWith("/headquarters/movements"))
      return reply(route, { items: [], page: 0, size: 50, total: 0 });
    if (path.endsWith("/headquarters/attention"))
      return reply(route, [
        {
          id: "a1",
          type: "UNACCOUNTED_RETURN",
          severity: "CRITICAL",
          title: "Return is not fully accounted",
          detail: "2 units remain unresolved",
          createdAt: "2026-09-23T10:00:00Z",
        },
      ]);
    if (path.endsWith("/productions")) return reply(route, []);
    return reply(route, []);
  });
});
test("Headquarters overview and inventory remain operational at desktop viewport", async ({
  page,
}) => {
  await page.goto("/headquarters");
  await expect(
    page.getByRole("heading", { name: "Headquarters" }),
  ).toBeVisible();
  await expect(
    page.getByText("What needs attention", { exact: false }),
  ).toHaveCount(0);
  await expect(page.getByText("286")).toBeVisible();
  await page.getByRole("button", { name: "Inventory" }).click();
  await expect(page.getByText("Demo Flight Case")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/headquarters-1440x900.png",
    fullPage: true,
  });
  await page.evaluate(() =>
    localStorage.setItem(
      "sa-command-ui",
      JSON.stringify({ state: { theme: "charcoal" }, version: 0 }),
    ),
  );
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Headquarters" }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.dataset.theme),
  ).toBe("charcoal");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/headquarters-charcoal-1440x900.png",
    fullPage: true,
  });
});
