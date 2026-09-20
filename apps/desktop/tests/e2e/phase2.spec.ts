import { expect, test, type Page, type Route } from "@playwright/test";
import { mkdir } from "node:fs/promises";

const now = new Date("2026-09-19T13:00:00+05:30");
const ids = {
  amaan: "11111111-1111-1111-1111-111111111111",
  rehan: "22222222-2222-2222-2222-222222222222",
  farhan: "33333333-3333-3333-3333-333333333333",
  production: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  task: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  meeting: "cccccccc-cccc-cccc-cccc-cccccccccccc",
  payroll: "dddddddd-dddd-dddd-dddd-dddddddddddd",
  item: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
};
const employees = [
  [ids.amaan, "SA-001", "Amaan Khan", "Senior Video Editor", 4200000],
  [ids.rehan, "SA-002", "Rehan Ali", "Cinematographer", 4600000],
  [ids.farhan, "SA-003", "Farhan Sheikh", "Photographer", 3900000],
].map(([id, employeeCode, displayName, roleTitle, baseSalaryMinor]) => ({
  id,
  employeeCode,
  firstName: String(displayName).split(" ")[0],
  lastName: String(displayName).split(" ")[1],
  displayName,
  phone: "+91 90000 00000",
  whatsappPhone: "+91 90000 00000",
  email: `${String(displayName).split(" ")[0].toLowerCase()}@sa.local`,
  roleTitle,
  department: "Production",
  employmentType: "FULL_TIME",
  joiningDate: "2024-01-01",
  baseSalaryMinor,
  salaryCurrency: "INR",
  status: "ACTIVE",
  profilePhotoUrl: null,
  notes: null,
  createdAt: now.toISOString(),
  updatedAt: now.toISOString(),
}));
const member = (employee: any, overridden = false) => ({
  id: `member-${employee.id}`,
  employeeId: employee.id,
  employeeName: employee.displayName,
  productionRole: "Crew",
  attendanceRequired: true,
  assignmentStatus: "PENDING",
  conflictOverridden: overridden,
  overrideReason: overridden ? "Owner approved overlapping commitment" : null,
});
function fixtures(seed = true) {
  const production: any = {
    id: ids.production,
    title: "Sharma Wedding",
    clientName: "Sharma Family",
    description: "Wedding film and photography coverage.",
    eventDate: "2026-09-26",
    startTime: "16:30:00",
    endTime: "21:30:00",
    venueName: "Royal Orchid, Lucknow",
    venueAddress: "Lucknow",
    status: "PRE_PRODUCTION",
    priority: "HIGH",
    progressPercent: 78,
    completedAt: null,
    members: seed ? [member(employees[0]), member(employees[1])] : [],
    unfinishedTaskCount: 1,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
  };
  const task: any = {
    id: ids.task,
    productionId: ids.production,
    productionTitle: "Sharma Wedding",
    meetingOriginId: null,
    title: "Wedding teaser edit",
    description: "Prepare the first client cut.",
    assignedEmployeeId: ids.amaan,
    assigneeName: "Amaan Khan",
    status: "IN_PROGRESS",
    priority: "HIGH",
    startDate: "2026-09-19",
    dueAt: "2026-09-29T12:30:00.000Z",
    completedAt: null,
    progressPercent: seed ? 78 : 0,
    overdue: false,
    updates: [
      {
        id: "update-1",
        progressPercent: seed ? 78 : 0,
        note: seed ? "First cut ready" : "Task created",
        createdAt: now.toISOString(),
      },
    ],
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
  };
  const meeting: any = {
    id: ids.meeting,
    title: "Production Review",
    description: "Review delivery readiness.",
    agenda: "Teaser cut\nCrew confirmations\nDelivery checklist",
    startsAt: "2026-09-20T06:00:00.000Z",
    endsAt: "2026-09-20T07:00:00.000Z",
    location: "Edit suite",
    status: "SCHEDULED",
    attendees: [
      {
        employeeId: ids.amaan,
        employeeName: "Amaan Khan",
        response: "ACCEPTED",
      },
      { employeeId: ids.rehan, employeeName: "Rehan Ali", response: "PENDING" },
    ],
    notes: [
      {
        id: "note-1",
        content: "Client review link is ready.",
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      },
    ],
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
  };
  const item: any = {
    id: ids.item,
    employeeId: ids.amaan,
    employeeName: "Amaan Khan",
    employeeRole: "Senior Video Editor",
    salaryCurrency: "INR",
    baseSalaryMinor: 4200000,
    attendanceDeductionMinor: 161538,
    overtimeMinor: 0,
    bonusMinor: seed ? 300000 : 0,
    advanceDeductionMinor: 0,
    manualAdjustmentMinor: 0,
    grossEarnings: seed ? 4500000 : 4200000,
    deductions: 161538,
    netSalaryMinor: seed ? 4338462 : 4038462,
    netPayable: seed ? 4338462 : 4038462,
    totalPaid: 0,
    remaining: seed ? 4338462 : 4038462,
    paymentStatus: "UNPAID",
    paidAt: null,
    lastPayment: null,
    payments: [],
    adjustments: seed
      ? [
          {
            id: "adjust-1",
            type: "BONUS",
            amountMinor: 300000,
            reason: "Performance bonus",
            createdAt: now.toISOString(),
          },
        ]
      : [],
  };
  const payroll: any = {
    id: ids.payroll,
    year: 2026,
    month: 9,
    status: "CALCULATED",
    policy: "PER_WORKING_DAY",
    totalMinor: item.netSalaryMinor,
    paidMinor: 0,
    pendingMinor: item.netSalaryMinor,
    remainingMinor: item.netSalaryMinor,
    paidCount: 0,
    partiallyPaidCount: 0,
    unpaidCount: 1,
    items: [item],
    calculatedAt: now.toISOString(),
    approvedAt: null,
    paidAt: null,
    lockedAt: null,
  };
  const state: any = {
    productions: seed ? [production] : [],
    tasks: seed ? [task] : [],
    meetings: seed ? [meeting] : [],
    payroll,
    production,
    task,
    meeting,
  };
  return state;
}
async function ok(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify({ data, meta: { traceId: "phase2-e2e" } }),
  });
}
async function fail(
  route: Route,
  code: string,
  message: string,
  fields: Record<string, string> = {},
) {
  await route.fulfill({
    status: 409,
    contentType: "application/json",
    body: JSON.stringify({
      error: { code, message, fields, traceId: "phase2-e2e" },
    }),
  });
}
async function mockApi(page: Page, state: any) {
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request(),
      method = req.method(),
      path = new URL(req.url()).pathname;
    if (path.endsWith("/auth/me") || path.endsWith("/auth/login"))
      return ok(route, {
        id: "owner",
        email: "owner@sa.local",
        displayName: "Owner",
        role: "OWNER",
      });
    if (path.endsWith("/notification-rules") && method === "GET")
      return ok(route, []);
    if (path.endsWith("/demo/messages") && method === "GET")
      return ok(route, {
        summary: {
          delivered: 0,
          read: 0,
          awaitingResponse: 0,
          failed: 0,
          queued: 0,
          sentToday: 0,
        },
        messages: [],
      });
    if (path.endsWith("/system/diagnostics"))
      return ok(route, {
        api: "UP",
        database: "UP",
        appMode: "demo",
        messagingProvider: "console",
      });
    if (path.endsWith("/employees") && method === "GET")
      return ok(route, employees);
    const employee = path.match(/\/employees\/([^/]+)$/);
    if (employee) {
      if (method === "PATCH") {
        const e = employees.find((x) => x.id === employee[1])!;
        Object.assign(e, req.postDataJSON());
        return ok(route, e);
      }
      return ok(
        route,
        employees.find((x) => x.id === employee[1]),
      );
    }
    if (path.match(/\/employees\/[^/]+\/operations$/))
      return ok(route, {
        work: state.tasks,
        payroll: [state.payroll],
        performance: {
          attendanceRecords: 22,
          attended: 21,
          lateCount: 1,
          tasksAssigned: 8,
          tasksCompleted: 6,
          overdueTasks: 1,
          activeProductions: 2,
          completedProductions: 4,
          attendanceRate: 95,
          onTimeCompletionRate: 83,
        },
      });
    if (path.endsWith("/productions") && method === "GET")
      return ok(route, state.productions);
    if (path.endsWith("/productions") && method === "POST") {
      Object.assign(state.production, req.postDataJSON());
      state.productions = [state.production];
      return ok(route, state.production, 201);
    }
    if (path.match(/\/productions\/[^/]+$/) && method === "GET")
      return ok(route, state.production);
    if (path.match(/\/productions\/[^/]+$/) && method === "PATCH") {
      Object.assign(state.production, req.postDataJSON());
      return ok(route, state.production);
    }
    if (path.match(/\/productions\/[^/]+\/members$/) && method === "POST") {
      const body = req.postDataJSON(),
        e = employees.find((x) => x.id === body.employeeId)!;
      if (e.id === ids.farhan && !body.overrideConflict)
        return fail(
          route,
          "SCHEDULING_CONFLICT",
          "Employee already has an overlapping commitment.",
          {
            title: "Product photography",
            startsAt: "2026-09-26T12:00:00.000Z",
            endsAt: "2026-09-26T14:00:00.000Z",
          },
        );
      if (!state.production.members.some((m: any) => m.employeeId === e.id))
        state.production.members.push(member(e, body.overrideConflict));
      return ok(route, state.production);
    }
    if (path.includes("/transition") && method === "POST") {
      state.production.status = req.postDataJSON().status;
      return ok(route, state.production);
    }
    if (path.endsWith("/tasks") && method === "GET")
      return ok(route, state.tasks);
    if (path.endsWith("/tasks") && method === "POST") {
      Object.assign(state.task, req.postDataJSON(), {
        id: ids.task,
        productionTitle: "Sharma Wedding",
        assigneeName: "Amaan Khan",
        updates: [
          {
            id: "update-1",
            progressPercent: 0,
            note: "Task created",
            createdAt: now.toISOString(),
          },
        ],
        overdue: false,
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      });
      state.tasks = [state.task];
      return ok(route, state.task, 201);
    }
    if (path.match(/\/tasks\/[^/]+\/updates$/) && method === "POST") {
      const b = req.postDataJSON();
      state.task.progressPercent = b.progressPercent;
      state.task.status = b.status ?? "IN_PROGRESS";
      state.task.updates.unshift({
        id: `update-${state.task.updates.length + 1}`,
        progressPercent: b.progressPercent,
        note: b.note,
        createdAt: now.toISOString(),
      });
      return ok(route, state.task);
    }
    if (path.endsWith("/meetings") && method === "GET")
      return ok(route, state.meetings);
    if (path.endsWith("/meetings") && method === "POST") {
      Object.assign(state.meeting, req.postDataJSON(), {
        id: ids.meeting,
        status: "SCHEDULED",
        attendees: [],
        notes: [],
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      });
      state.meetings = [state.meeting];
      return ok(route, state.meeting, 201);
    }
    if (path.match(/\/meetings\/[^/]+$/) && method === "GET")
      return ok(route, state.meeting);
    if (path.match(/\/meetings\/[^/]+\/actions$/) && method === "POST") {
      state.tasks.push({
        ...state.task,
        id: "action-task",
        title: req.postDataJSON().title,
        meetingOriginId: ids.meeting,
        priority: "NORMAL",
      });
      return ok(route, state.tasks.at(-1), 201);
    }
    if (path.endsWith("/calendar-events"))
      return ok(route, [
        {
          id: "cal-production",
          type: "PRODUCTION",
          title: state.production.title,
          startsAt: "2026-09-26T11:00:00.000Z",
          endsAt: "2026-09-26T16:00:00.000Z",
          locationName: state.production.venueName,
          productionId: ids.production,
          meetingId: null,
          taskId: null,
          status: "SCHEDULED",
          attendees: [],
        },
        {
          id: "cal-meeting",
          type: "MEETING",
          title: state.meeting.title,
          startsAt: state.meeting.startsAt,
          endsAt: state.meeting.endsAt,
          locationName: state.meeting.location,
          productionId: null,
          meetingId: ids.meeting,
          taskId: null,
          status: "SCHEDULED",
          attendees: [],
        },
        {
          id: "cal-task",
          type: "DEADLINE",
          title: state.task.title,
          startsAt: "2026-09-29T12:00:00.000Z",
          endsAt: "2026-09-29T12:30:00.000Z",
          productionId: null,
          meetingId: null,
          taskId: ids.task,
          status: "SCHEDULED",
          attendees: [],
        },
      ]);
    if (path.endsWith("/payroll") && method === "GET")
      return ok(route, [state.payroll]);
    if (path.match(/\/payroll\/[^/]+$/) && method === "GET")
      return ok(route, state.payroll);
    if (path.endsWith(`/items/${ids.item}/payments`) && method === "GET")
      return ok(route, state.payroll.items[0]);
    if (path.endsWith("/adjustments") && method === "POST") {
      state.payroll.items[0].bonusMinor = 300000;
      state.payroll.items[0].netSalaryMinor += 300000;
      state.payroll.items[0].netPayable += 300000;
      state.payroll.items[0].grossEarnings += 300000;
      state.payroll.items[0].remaining += 300000;
      state.payroll.totalMinor = state.payroll.items[0].netSalaryMinor;
      state.payroll.pendingMinor = state.payroll.totalMinor;
      state.payroll.remainingMinor = state.payroll.totalMinor;
      return ok(route, state.payroll);
    }
    if (path.endsWith("/approve") && method === "POST") {
      state.payroll.status = "APPROVED";
      return ok(route, state.payroll);
    }
    if (path.endsWith(`/items/${ids.item}/payments`) && method === "POST") {
      const body = route.request().postDataJSON();
      const payment = {
        id: `payment-${state.payroll.items[0].payments.length + 1}`,
        amountMinor: body.amountMinor,
        paidAt: body.paidAt,
        paymentMethod: body.paymentMethod,
        reference: body.reference,
        note: body.note,
        createdAt: now.toISOString(),
      };
      state.payroll.items[0].payments.push(payment);
      state.payroll.items[0].lastPayment = payment;
      state.payroll.items[0].totalPaid += body.amountMinor;
      state.payroll.items[0].remaining = Math.max(
        state.payroll.items[0].netPayable - state.payroll.items[0].totalPaid,
        0,
      );
      state.payroll.paidMinor = state.payroll.items[0].totalPaid;
      state.payroll.pendingMinor = state.payroll.items[0].remaining;
      state.payroll.remainingMinor = state.payroll.items[0].remaining;
      state.payroll.items[0].paymentStatus =
        state.payroll.items[0].remaining === 0 ? "PAID" : "PARTIALLY_PAID";
      state.payroll.partiallyPaidCount =
        state.payroll.items[0].remaining === 0 ? 0 : 1;
      state.payroll.paidCount = state.payroll.items[0].remaining === 0 ? 1 : 0;
      state.payroll.unpaidCount = 0;
      if (state.payroll.items[0].remaining === 0) state.payroll.status = "PAID";
      return ok(route, state.payroll);
    }
    if (path.endsWith("/lock") && method === "POST") {
      state.payroll.status = "LOCKED";
      state.payroll.lockedAt = now.toISOString();
      return ok(route, state.payroll);
    }
    if (path.endsWith("/audit"))
      return ok(route, [
        {
          id: "audit-1",
          action: "SCHEDULING_CONFLICT_OVERRIDDEN",
          createdAt: now.toISOString(),
          actorId: "owner",
        },
      ]);
    if (path.endsWith("/dashboard"))
      return ok(route, {
        team: {
          employees: 3,
          present: 2,
          late: 1,
          absent: 0,
          leave: 0,
          incomplete: 0,
        },
        today: [],
        productions: state.productions.map((p: any) => ({
          id: p.id,
          title: p.title,
          status: p.status,
          progressPercent: p.progressPercent,
          eventDate: p.eventDate,
        })),
        workload: employees.map((e: any) => ({
          employeeId: e.id,
          employeeName: e.displayName,
          active: state.tasks.filter(
            (t: any) => t.assignedEmployeeId === e.id && t.status !== "DONE",
          ).length,
          overdue: 0,
        })),
        payroll: {
          id: ids.payroll,
          year: 2026,
          month: 9,
          status: state.payroll.status,
          totalMinor: state.payroll.totalMinor,
          paidMinor: state.payroll.paidMinor,
          pendingMinor: state.payroll.pendingMinor,
          partiallyPaidCount: state.payroll.partiallyPaidCount,
          unpaidCount: state.payroll.unpaidCount,
        },
        attention: [],
        communications: {
          queued: 0,
          sent: 0,
          delivered: 0,
          read: 0,
          failed: 0,
          awaitingResponse: 0,
        },
      });
    return ok(route, []);
  });
}
async function shot(page: Page, name: string) {
  await expect(page.locator(".workspace-page")).toHaveCSS("opacity", "1");
  if (process.env.VISUAL_REVIEW === "1") {
    await mkdir(".visual-review/phase2", { recursive: true });
    await page.screenshot({
      path: `.visual-review/phase2/${name}`,
      fullPage: true,
      animations: "disabled",
    });
  } else
    await expect(page).toHaveScreenshot(`phase2-${name}`, {
      fullPage: true,
      animations: "disabled",
    });
}

test("Phase 2 connected owner flow", async ({ page }) => {
  await page.clock.setFixedTime(now);
  const state = fixtures(false);
  await mockApi(page, state);
  await page.goto("/productions?create=production");
  await page.getByLabel("Production title").fill("Sharma Wedding");
  await page.getByLabel("Client name").fill("Sharma Family");
  await page.getByLabel("Venue name").fill("Royal Orchid, Lucknow");
  await page.getByRole("button", { name: "Create production" }).click();
  await expect(
    page.getByRole("heading", { name: "Sharma Wedding" }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "Crew" }).click();
  for (const name of ["Amaan Khan", "Rehan Ali"]) {
    await page.getByRole("button", { name: "Assign crew" }).click();
    await page.getByLabel("Crew employee").selectOption({ label: name });
    await page.getByRole("button", { name: "Assign", exact: true }).click();
  }
  await page.getByRole("button", { name: "Assign crew" }).click();
  await page
    .getByLabel("Crew employee")
    .selectOption({ label: "Farhan Sheikh" });
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Scheduling conflict" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).last().click();
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await page.getByRole("button", { name: "Assign anyway" }).click();
  await expect(page.getByText("Conflict overridden")).toBeVisible();
  await page.goto("/work?create=task");
  await page.getByLabel("Task title").fill("Wedding teaser edit");
  await page.getByLabel("Task assignee").selectOption(ids.amaan);
  await page.getByLabel("Task production").selectOption(ids.production);
  await page.getByRole("button", { name: "Create task" }).click();
  await page.getByRole("button", { name: "Team" }).click();
  await page.getByText("Wedding teaser edit", { exact: true }).click();
  await page.getByLabel("Task progress").fill("78");
  await page.getByLabel("Progress note").fill("First cut ready");
  await page.getByRole("button", { name: "Save progress" }).click();
  await expect(page.getByText("78%").last()).toBeVisible();
  await page.goto("/meetings?create=meeting");
  await page.getByLabel("Meeting title").fill("Production Review");
  await page.getByLabel("Meeting location").fill("Edit suite");
  await page.getByLabel("Meeting starts").fill("2026-09-20T11:30");
  await page.getByLabel("Meeting ends").fill("2026-09-20T12:30");
  await page.getByRole("button", { name: "Create meeting" }).click();
  await page.getByRole("button", { name: "Create action item" }).click();
  await page.getByLabel("Action title").fill("Send review export");
  await page.getByRole("button", { name: "Create task" }).click();
  await page.goto(`/payroll/${ids.payroll}`);
  await page.getByRole("button", { name: "Add adjustment" }).first().click();
  await page.getByLabel("Adjustment employee").selectOption(ids.item);
  await page.getByLabel("Adjustment type").selectOption("BONUS");
  await page.getByLabel("Adjustment amount").fill("3000");
  await page.getByLabel("Adjustment reason").fill("Performance bonus");
  await page.getByRole("button", { name: "Add adjustment" }).click();
  await page.getByRole("button", { name: "Approve payroll" }).click();
  await page.getByRole("button", { name: "Record payment" }).first().click();
  await page.getByLabel("Payment employee").selectOption(ids.item);
  await page.getByLabel("Payment amount").fill("10000");
  await page.getByLabel("Payment method").selectOption("UPI");
  await page.getByLabel("Payment reference").fill("UPI-PARTIAL");
  await page.getByRole("button", { name: "Record payment" }).last().click();
  await expect(page.getByText("PARTIALLY PAID").first()).toBeVisible();
  await page.getByRole("button", { name: "Record payment" }).first().click();
  await page.getByLabel("Payment employee").selectOption(ids.item);
  await page.getByLabel("Payment amount").fill("33384.62");
  await page.getByLabel("Payment reference").fill("BANK-FINAL");
  await page.getByRole("button", { name: "Record payment" }).last().click();
  await expect(page.getByText("PAID").first()).toBeVisible();
  await page.getByRole("button", { name: "View details" }).click();
  await expect(page.locator(".payment-timeline > div")).toHaveCount(2);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Lock payroll" }).click();
  await page.getByRole("button", { name: "Lock payroll" }).last().click();
  await expect(page.getByText("Immutable financial history")).toBeVisible();
  await page.goto("/");
  await expect(page.getByText("Sharma Wedding")).toBeVisible();
  await expect(page.getByText("LOCKED")).toBeVisible();
});

test("Phase 2 visual surfaces in Pearl and Charcoal", async ({ page }) => {
  test.slow();
  await page.clock.setFixedTime(now);
  const state = fixtures(true);
  await mockApi(page, state);
  for (const [path, name] of [
    ["/productions", "productions-pearl.png"],
    [`/productions/${ids.production}`, "production-detail.png"],
    ["/meetings", "meetings.png"],
    [`/meetings/${ids.meeting}`, "meeting-detail.png"],
    ["/payroll", "payroll.png"],
    [`/payroll/${ids.payroll}`, "payroll-detail.png"],
  ]) {
    await page.goto(path);
    await shot(page, name);
  }
  const ledgerPayment = {
    id: "payment-visual",
    amountMinor: 1500000,
    paidAt: "2026-09-18T10:00:00.000Z",
    paymentMethod: "UPI",
    reference: "UPI-492821",
    note: "First salary payment",
    createdAt: "2026-09-18T10:00:00.000Z",
  };
  state.payroll.status = "APPROVED";
  state.payroll.items[0].payments = [ledgerPayment];
  state.payroll.items[0].lastPayment = ledgerPayment;
  state.payroll.items[0].totalPaid = ledgerPayment.amountMinor;
  state.payroll.items[0].remaining =
    state.payroll.items[0].netPayable - ledgerPayment.amountMinor;
  state.payroll.items[0].paymentStatus = "PARTIALLY_PAID";
  state.payroll.paidMinor = ledgerPayment.amountMinor;
  state.payroll.remainingMinor = state.payroll.items[0].remaining;
  state.payroll.partiallyPaidCount = 1;
  state.payroll.unpaidCount = 0;
  await page.goto(`/payroll/${ids.payroll}`);
  await page.getByRole("button", { name: "View details" }).click();
  await shot(page, "payroll-ledger-drawer.png");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Record payment" }).first().click();
  await shot(page, "payroll-payment-modal.png");
  await page.keyboard.press("Escape");
  await page.goto("/work");
  await page.getByRole("button", { name: "Team" }).click();
  await shot(page, "work.png");
  await page.goto("/calendar");
  await shot(page, "calendar-month.png");
  await page.getByRole("button", { name: "Select View" }).click();
  await page.getByRole("button", { name: "Select View Week" }).click();
  await shot(page, "calendar-week.png");
  await page.goto(`/productions/${ids.production}`);
  await page.getByRole("tab", { name: "Crew" }).click();
  await page.getByRole("button", { name: "Assign crew" }).click();
  await page.getByLabel("Crew employee").selectOption(ids.farhan);
  await page.getByRole("button", { name: "Assign", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Scheduling conflict" }),
  ).toBeVisible();
  await shot(page, "conflict-modal.png");
  await page.keyboard.press("Escape");
  await page.goto("/settings");
  await page.getByRole("button", { name: "Charcoal" }).click();
  await page.goto("/productions");
  await shot(page, "productions-charcoal.png");
  await page.goto("/calendar");
  await shot(page, "calendar-charcoal.png");
});
