import { expect, test, type Page, type Route } from "@playwright/test";

const now = "2026-09-21T07:30:00.000Z";
const items = [
  {
    employeeRef: "e1",
    deviceId: "d1",
    sessionId: "s1",
    state: "LIVE",
    latitude: 26.45,
    longitude: 80.33,
    accuracyMeters: 8,
    recordedAt: now,
    trackingStartedAt: now,
    pairedAt: now,
    employeeName: "Amaan Khan",
    roleTitle: "Senior Video Editor",
    productionTitle: "Sharma Wedding",
  },
  {
    employeeRef: "e2",
    deviceId: "d2",
    sessionId: "s2",
    state: "LIVE",
    latitude: 26.452,
    longitude: 80.335,
    accuracyMeters: 6,
    recordedAt: now,
    trackingStartedAt: now,
    pairedAt: now,
    employeeName: "Rehan Ali",
    roleTitle: "Camera Operator",
    productionTitle: "Sharma Wedding",
  },
  {
    employeeRef: "e3",
    deviceId: "d3",
    sessionId: "s3",
    state: "STALE",
    latitude: 26.447,
    longitude: 80.326,
    accuracyMeters: 14,
    recordedAt: new Date(Date.parse(now) - 120_000).toISOString(),
    trackingStartedAt: now,
    pairedAt: now,
    employeeName: "Farhan Khan",
    roleTitle: "Assistant",
    productionTitle: "Sharma Wedding",
  },
  {
    employeeRef: "e4",
    deviceId: "d4",
    state: "OFF_DUTY",
    pairedAt: now,
    employeeName: "Sarah Ahmed",
    roleTitle: "Producer",
    productionTitle: "Sharma Wedding",
  },
];

async function reply(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(
      status < 400
        ? { data, meta: {} }
        : { error: { code: "ERROR", message: "Unavailable" } },
    ),
  });
}

async function mock(page: Page) {
  await page.route("https://tiles.openfreemap.org/styles/liberty", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        version: 8,
        sources: {},
        layers: [
          {
            id: "background",
            type: "background",
            paint: { "background-color": "#e5e3dc" },
          },
        ],
      }),
    }),
  );

  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/auth/me")) {
      return reply(route, {
        id: "owner",
        email: "owner@sa.local",
        displayName: "Owner",
        role: "OWNER",
      });
    }
    if (path.endsWith("/dashboard")) {
      return reply(route, {
        team: {
          employees: 4,
          present: 4,
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
    }
    if (path.endsWith("/navigator/live")) {
      return reply(route, {
        items,
        syncedAt: now,
        organizationPublicId: "org",
      });
    }
    if (path.endsWith("/navigator/realtime-ticket")) {
      return reply(route, {
        ticket: "test",
        organizationPublicId: "org",
        expiresAt: now,
      });
    }
    if (path.includes("/navigator/simulator/")) {
      return reply(route, { running: path.endsWith("start") });
    }
    if (path.endsWith("/system/diagnostics")) {
      return reply(route, {
        api: "UP",
        database: "UP",
        appMode: "demo",
        messagingProvider: "console",
      });
    }
    if (path.endsWith("/notification-rules")) return reply(route, []);
    if (path.endsWith("/demo/messages")) {
      return reply(route, { messages: [], summary: {}, page: {} });
    }
    if (path.endsWith("/messages")) {
      return reply(route, { messages: [], summary: {}, page: {} });
    }
    if (
      ["/employees", "/productions", "/meetings", "/tasks"].some((suffix) =>
        path.endsWith(suffix),
      )
    ) {
      return reply(route, []);
    }
    return reply(route, {});
  });
}

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date(now));
  await mock(page);
  await page.goto("/");
});

test("Navigator shows four operational states and employee context", async ({
  page,
}) => {
  await page.goto("/navigator");
  await expect(
    page.getByRole("heading", { name: "Navigator", exact: true }),
  ).toBeVisible();
  for (const label of [
    "Amaan Khan",
    "Rehan Ali",
    "Farhan Khan",
    "Sarah Ahmed",
  ]) {
    await expect(page.getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByText("STALE", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: /^AK Amaan Khan/ }).click();
  await expect(
    page.getByText("Sharma Wedding", { exact: true }).first(),
  ).toBeVisible();
  await expect(page).toHaveScreenshot("navigator-pearl.png", {
    animations: "disabled",
  });
});

test("Navigator simulator is demo-only and Charcoal remains polished", async ({
  page,
}) => {
  await page.goto("/settings");
  await page.getByRole("button", { name: "Charcoal" }).click({ force: true });
  await page
    .getByRole("button", { name: "Start simulation" })
    .click({ force: true });
  await expect(page.getByText("RUNNING", { exact: true })).toBeVisible();
  await expect(page).toHaveScreenshot("navigator-settings-charcoal.png", {
    animations: "disabled",
  });
});
