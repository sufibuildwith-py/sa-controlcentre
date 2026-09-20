import { expect, test, type Page, type Route } from "@playwright/test";

const now = "2026-09-19T12:00:00Z";
const people = [
  {
    id: "e1",
    displayName: "Amaan Khan",
    employeeCode: "SA-001",
    firstName: "Amaan",
    phone: "+91 90000 00001",
    whatsappPhone: "+91 90000 00001",
    roleTitle: "Editor",
    department: "Post",
    employmentType: "FULL_TIME",
    joiningDate: "2024-01-01",
    baseSalaryMinor: 4200000,
    salaryCurrency: "INR",
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now,
  },
  {
    id: "e2",
    displayName: "Farhan Ali",
    employeeCode: "SA-002",
    firstName: "Farhan",
    phone: "+91 90000 00002",
    whatsappPhone: "+91 90000 00002",
    roleTitle: "Assistant",
    department: "Operations",
    employmentType: "FULL_TIME",
    joiningDate: "2024-01-01",
    baseSalaryMinor: 3000000,
    salaryCurrency: "INR",
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now,
  },
];
const activity = [
  {
    eventType: "QUEUED",
    detail: "Created by PRODUCTION_ASSIGNED",
    createdAt: now,
  },
  {
    eventType: "SENT",
    detail: "Provider accepted the message",
    createdAt: now,
  },
];
const baseMessages: any[] = [
  {
    id: "m1",
    employeeId: "e1",
    employeeName: "Amaan Khan",
    phone: "+91 90000 00001",
    category: "ASSIGNMENTS",
    templateKey: "sa_production_assignment",
    bodyPreview: "Sharma Wedding Assignment",
    relatedType: "PRODUCTION",
    relatedId: "p1",
    requiresResponse: true,
    response: null,
    status: "DELIVERED",
    providerMessageId: "console-m1",
    attemptCount: 1,
    lastError: null,
    queuedAt: now,
    sentAt: now,
    deliveredAt: now,
    attempts: [
      {
        attemptNumber: 1,
        providerMessageId: "console-m1",
        status: "DELIVERED",
        startedAt: now,
        acceptedAt: now,
        deliveredAt: now,
      },
    ],
    activity,
  },
  {
    id: "m2",
    employeeId: "e2",
    employeeName: "Farhan Ali",
    phone: "+91 90000 00002",
    category: "MEETINGS",
    templateKey: "sa_meeting_invitation",
    bodyPreview: "Tomorrow's Production Review",
    relatedType: "MEETING",
    relatedId: "meet1",
    requiresResponse: true,
    response: null,
    status: "FAILED",
    providerMessageId: "console-m2",
    attemptCount: 4,
    lastError: "Deterministic simulator failure",
    queuedAt: now,
    failedAt: now,
    attempts: [
      {
        attemptNumber: 1,
        status: "FAILED",
        startedAt: now,
        failedAt: now,
        lastError: "Temporary failure",
      },
      {
        attemptNumber: 2,
        providerMessageId: "console-m2",
        status: "FAILED",
        startedAt: now,
        failedAt: now,
        lastError: "Deterministic simulator failure",
      },
    ],
    activity: [
      ...activity,
      {
        eventType: "FAILED",
        detail: "Deterministic simulator failure",
        createdAt: now,
      },
    ],
  },
];
async function ok(route: Route, data: unknown) {
  await route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ data, meta: { traceId: "phase3" } }),
  });
}
function centre(messages: any[], all = messages) {
  return {
    summary: {
      delivered: all.filter(
        (m) => m.status === "DELIVERED" || m.status === "READ",
      ).length,
      read: all.filter((m) => m.status === "READ").length,
      awaitingResponse: all.filter((m) => m.requiresResponse && !m.response)
        .length,
      failed: all.filter((m) => m.status === "FAILED").length,
      queued: all.filter((m) => m.status === "QUEUED").length,
      sentToday: 1,
    },
    messages,
    page: 0,
    size: 50,
    totalElements: messages.length,
    totalPages: 1,
  };
}
async function mock(page: Page) {
  const state = {
    messages: structuredClone(baseMessages),
    rule: {
      id: "r1",
      eventType: "PRODUCTION_ASSIGNED",
      channel: "WHATSAPP",
      enabled: true,
      delayMinutes: 0,
      templateKey: "sa_production_assignment",
    },
  };
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname,
      method = req.method();
    if (path.endsWith("/auth/me"))
      return ok(route, {
        id: "owner",
        email: "owner@sa.local",
        displayName: "Owner",
        role: "OWNER",
      });
    if (path.endsWith("/employees")) return ok(route, people);
    if (path.endsWith("/productions") || path.endsWith("/tasks"))
      return ok(route, []);
    if (path.endsWith("/notification-rules")) return ok(route, [state.rule]);
    if (path.endsWith("/system/diagnostics"))
      return ok(route, {
        api: "UP",
        database: "UP",
        appMode: "demo",
        messagingProvider: "console",
      });
    if (path.match(/\/notification-rules\/[^/]+$/) && method === "PATCH") {
      Object.assign(state.rule, req.postDataJSON());
      return ok(route, state.rule);
    }
    if (path.endsWith("/demo/messages"))
      return ok(route, centre(state.messages));
    if (path.endsWith("/messages/manual") && method === "POST") {
      const input = req.postDataJSON();
      for (const id of input.employeeIds)
        state.messages.unshift({
          ...baseMessages[0],
          id: `manual-${id}`,
          employeeId: id,
          employeeName: people.find((p) => p.id === id)!.displayName,
          category: "MANUAL",
          bodyPreview: input.message,
          status: "QUEUED",
          requiresResponse: false,
          response: null,
        });
      return ok(route, {
        eventId: "event-manual",
        recipientCount: input.employeeIds.length,
      });
    }
    const retry = path.match(/\/messages\/([^/]+)\/retry$/);
    if (retry) {
      const item = state.messages.find((m) => m.id === retry[1]);
      item.status = "QUEUED";
      item.lastError = null;
      item.activity.push({
        eventType: "MANUAL_RETRY",
        detail: "Owner requested retry",
        createdAt: now,
      });
      return ok(route, item);
    }
    if (path.endsWith("/messages")) {
      let values = state.messages;
      if (url.searchParams.get("status"))
        values = values.filter(
          (m) => m.status === url.searchParams.get("status"),
        );
      if (url.searchParams.get("needsAttention") === "true")
        values = values.filter(
          (m) => m.status === "FAILED" || (m.requiresResponse && !m.response),
        );
      return ok(route, centre(values, state.messages));
    }
    const detail = path.match(/\/messages\/([^/]+)$/);
    if (detail)
      return ok(
        route,
        state.messages.find((m) => m.id === detail[1]),
      );
    return ok(route, true);
  });
}

test("Phase 3 communications retry, detail and bulk composer", async ({
  page,
}) => {
  await mock(page);
  await page.goto("/communications");
  await expect(
    page.getByRole("heading", { name: "Communications" }),
  ).toBeVisible();
  await page.getByText("Farhan Ali", { exact: true }).click();
  await expect(
    page
      .getByRole("paragraph")
      .filter({ hasText: "Deterministic simulator failure" }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Retry message/ }).click();
  await expect(page.getByText("MANUAL RETRY")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
  await page.getByRole("button", { name: "Send message" }).click();
  await page.getByLabel("Search recipients").fill("Amaan");
  await page.getByLabel("Search recipients").press("Enter");
  await page.getByLabel("Search recipients").fill("Farhan");
  await page.getByLabel("Search recipients").press("Enter");
  await page
    .getByPlaceholder("Write a clear operational message…")
    .fill("Call time updated to 8:30 AM.");
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Queue messages" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByText("Call time updated to 8:30 AM.").first(),
  ).toBeVisible();
});

test("Phase 3 premium surfaces in Pearl and Charcoal", async ({ page }) => {
  await mock(page);
  await page.goto("/communications?attention=true");
  await expect(page.getByText("Farhan Ali")).toBeVisible();
  await expect(page).toHaveScreenshot("phase3-communications-pearl.png", {
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Charcoal" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "charcoal");
  await expect(
    page.getByText("Notification automation", { exact: true }),
  ).toBeVisible();
  await page
    .locator(".workspace-shell")
    .evaluate((element) => element.scrollTo(0, 0));
  await expect(page).toHaveScreenshot("phase3-settings-charcoal.png", {
    fullPage: true,
    animations: "disabled",
  });
});
