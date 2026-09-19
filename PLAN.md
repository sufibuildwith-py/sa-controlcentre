# SA Command — V1 Build Plan

> **Company:** SA Production  
> **Product:** SA Command  
> **Category:** Owner-facing production operations / ERP desktop application  
> **Platforms:** Windows 10/11 and macOS  
> **V1 target:** A complete, presentation-ready, locally runnable desktop application with real business workflows and a switchable real/simulated WhatsApp communication layer.  
> **Primary operator:** SA Production owner / administrator  
> **Employee-facing surface in V1:** WhatsApp messages and interactive responses; employees do not need to install the desktop app.  
> **Build strategy:** Reuse strong open-source and permissively licensed components wherever practical, then heavily restyle them to one unified SA Command design system. Do not rebuild solved primitives from scratch.

---

## 0. The product in one sentence

**SA Command is the owner's operating system for SA Production — one calm desktop interface for employees, attendance, work progress, productions, events, meetings, payroll, performance evidence and employee communication.**

The product must never feel like a generic admin template or a college ERP. The visual benchmark is the attached smart-home dashboard reference: floating navigation, a detached icon dock, deeply rounded shells, compact bento cards, strong information hierarchy, restrained color, extremely clean typography and smooth physical-feeling motion.

The original product direction also requires a clean Apple-like **light mode**. Therefore the V1 design system must support the same geometry in two themes:

- **SA Pearl — default presentation theme:** warm white / pearl / charcoal.
- **Reference Charcoal — secondary theme:** very close to the attached dark reference.

The layout and component geometry remain identical between themes.

---

# 1. V1 outcome

At the end of Phase 3 the owner must be able to install or run SA Command on Windows or macOS and perform this complete flow:

1. Launch SA Command and sign in.
2. See today's owner dashboard.
3. Create/edit employees and salaries.
4. Mark attendance, late arrivals, absence and leave.
5. Create a production/event.
6. Add crew members and detect scheduling conflicts.
7. Assign tasks, deadlines and progress.
8. Schedule a meeting and track invite responses.
9. View the calendar in month/week/agenda modes.
10. Calculate a monthly payroll run.
11. Add bonuses, deductions, overtime or advances.
12. Approve, mark paid and lock payroll.
13. Send event/task/meeting/payroll/attendance notifications.
14. Receive deterministic employee responses from WhatsApp or the built-in simulator.
15. See queued/sent/delivered/read/failed communication states.
16. See all important owner actions in an audit trail.
17. Return to the dashboard and see all data updated without manual refresh.
18. Run a scripted presentation using deterministic seed data even without internet or Meta credentials.

That complete scenario is the definition of **V1**, not a mock dashboard.

---

# 2. V1 scope

## 2.1 Core modules

### Command
The owner dashboard and exception centre.

### People
Employee profiles, role, contact information, joining information, salary basis and employment status.

### Attendance
Daily attendance, lateness, absence, half-day, leave and attendance history.

### Productions
SA Production's actual production/event workflow rather than a generic project-management clone.

### Work
Tasks, assignments, deadlines, progress, blockers and task updates.

### Calendar
Productions, shoots, meetings, deadlines, internal events and reminders.

### Meetings
Participants, agenda/notes, response tracking and action items.

### Payroll
Monthly payroll runs, salary snapshots, bonuses, deductions, advances, overtime, approval, payment and locking.

### Performance
Evidence-based operational metrics and owner review notes.

### Communications
WhatsApp automation, manual messages, delivery state, acknowledgements, retries and communication history.

### Audit
A searchable record of important business actions.

---

# 3. Explicit V1 non-goals

Do not build these in V1:

- Employee mobile app.
- Employee web portal.
- Biometric attendance.
- GPS/geofenced employee tracking.
- Equipment inventory.
- CRM / sales pipeline.
- GST/accounting suite.
- Customer invoices.
- Client portal.
- Full bookkeeping.
- Multi-company support.
- Complex role hierarchy.
- AI chatbot.
- AI-generated employee scores.
- Natural-language interpretation of WhatsApp replies.
- Employee surveillance.
- Automatic financial transfers.
- Complex offline multi-device conflict resolution.
- Huge analytics suite.
- A second task system specifically for meetings.
- Decorative AI/3D/particle effects with no operational purpose.

V1 architecture may leave extension points, but Codex must not expand scope unless an extension is required for the V1 foundation.

---

# 4. UI reference is a hard design contract

The attached reference image is **868 × 694 px** and should be committed to the repository as:

```text
docs/reference/sa-command-ui-reference.png
```

Do not copy the smart-home content. Copy its **visual system**.

## 4.1 What must be preserved from the reference

- A large outer rounded application shell.
- A smaller inner workspace shell.
- A floating pill navigation centered above the inner workspace.
- A detached narrow vertical icon dock on the left.
- Bento layout with deliberately unequal card sizes.
- Rounded card corners much larger than normal admin dashboards.
- Low-contrast borders rather than heavy shadows.
- Short labels and large key values.
- Small circular icon buttons.
- Tight but not cramped spacing.
- Cards that feel like physical controls/objects.
- Clear layering between app background, shell, workspace and cards.
- Animation that looks physically damped rather than playful/bouncy.
- Very limited color outside status indicators and the brand accent.
- No visual clutter from persistent toolbars.
- No conventional giant sidebar.
- No Bootstrap/AdminLTE style.
- No generic SaaS template aesthetic.

## 4.2 Reference geometry

At a nominal 1440 × 900 application viewport:

```text
Outer app padding:        28–36 px
Outer shell radius:       36–42 px
Inner workspace radius:   28–32 px
Card radius:              20–24 px
Small control radius:     14–18 px
Pills:                    999 px
Detached top nav height:  46–52 px
Left dock width:          48–56 px
Bento gap:                10–14 px
Page side breathing room: 22–30 px
```

The screenshot is not a request for pixel-for-pixel smart-home content; it is the spatial and aesthetic authority.

## 4.3 Main application layout

```text
┌────────────────────────────────────────────────────────────────────┐
│                                                                    │
│                  ╭────────────────────────────╮                    │
│                  │ Overview People Production │                    │
│                  │ Calendar Finance       +   │                    │
│                  ╰────────────────────────────╯                    │
│                                                                    │
│ ╭────╮      ╭──────────────────────────────────────────────────╮   │
│ │ ⌂  │      │ Good evening, Owner.                    avatar   │   │
│ │ ◷  │      │                                                  │   │
│ │ ✓  │      │            BENTO WORKSPACE                       │   │
│ │ ✉  │      │                                                  │   │
│ │ ⌘  │      │                                                  │   │
│ │ ⚙  │      │                                                  │   │
│ ╰────╯      ╰──────────────────────────────────────────────────╯   │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### Top floating switcher
Primary areas:

```text
Overview
People
Productions
Calendar
Finance
+
```

The plus opens **Quick Create**.

### Left floating dock
Operational shortcuts:

```text
Command
Attendance
Work
Communications
Search
Settings
```

Do not duplicate every top navigation item in the left dock. The top switcher is domain navigation; the left dock is fast operational navigation.

---

# 5. Theme system

## 5.1 SA Pearl — default

```css
:root {
  --app-bg: #ecece8;
  --shell-bg: #f6f6f2;
  --workspace-bg: #fbfbf8;
  --surface: #ffffff;
  --surface-raised: #ffffff;
  --surface-soft: #f1f1ed;

  --text-1: #171716;
  --text-2: #62625e;
  --text-3: #999991;

  --border-soft: rgba(20, 20, 18, 0.065);
  --border: rgba(20, 20, 18, 0.10);
  --border-strong: rgba(20, 20, 18, 0.15);

  --accent: #d65742;
  --success: #4f956a;
  --warning: #c58a34;
  --danger: #c9534b;
  --info: #5f7fa4;
}
```

## 5.2 Reference Charcoal

The supplied image is dominated by roughly `#1E1E1E`, `#191919` and `#2A2A2A`.

```css
[data-theme="charcoal"] {
  --app-bg: #1e1e1e;
  --shell-bg: #191919;
  --workspace-bg: #1d1d1d;
  --surface: #2a2a2a;
  --surface-raised: #2e2e2e;
  --surface-soft: #242424;

  --text-1: #f6f6f2;
  --text-2: #c1c1bb;
  --text-3: #868680;

  --border-soft: rgba(255,255,255,0.055);
  --border: rgba(255,255,255,0.09);
  --border-strong: rgba(255,255,255,0.14);

  --accent: #d94b35;
}
```

## 5.3 Color discipline

Never turn the dashboard into a rainbow.

Use accent/status color only for:

- selected controls;
- status dots;
- tiny chart accents;
- warnings;
- destructive actions;
- production stage highlights;
- calendar categories;
- key CTA buttons.

Most of the interface remains monochrome.

---

# 6. Typography

Use system-native fonts first:

```css
font-family:
  -apple-system,
  BlinkMacSystemFont,
  "Segoe UI",
  Inter,
  sans-serif;
```

Do not bundle Apple's proprietary SF Pro files.

Recommended scale:

```text
Hero greeting      32–36 / 600
Page heading       25–28 / 600
Card title         15–17 / 600
Large metric       30–42 / 500
Body               13.5–14.5 / 450
Secondary          12.5–13 / 450
Metadata           11–12 / 500
Micro label        10–11 / 600
```

Rules:

- sentence case, not ALL CAPS except tiny optional eyebrow labels;
- no excessive bold;
- short labels;
- values carry visual weight;
- avoid large blocks of text inside dashboard cards.

---

# 7. Radius, border and shadow tokens

```css
--radius-shell: 40px;
--radius-workspace: 30px;
--radius-card: 22px;
--radius-control: 16px;
--radius-pill: 999px;

--shadow-shell:
  0 24px 70px rgba(20,20,18,.08);

--shadow-card:
  0 3px 12px rgba(20,20,18,.025);

--shadow-floating:
  0 12px 34px rgba(20,20,18,.10);
```

Prefer borders over shadows. Shadows must disappear almost completely in the charcoal theme.

---

# 8. Motion system

Use **Motion for React** for state/layout transitions and CSS for simple hover/focus behavior.

Base spring:

```ts
export const appSpring = {
  type: "spring",
  stiffness: 340,
  damping: 30,
  mass: 0.8
}
```

Timing guide:

```text
Button press       90–110 ms
Hover              120–160 ms
Tooltip            140–170 ms
Card entrance      200–240 ms
Tab indicator      220–260 ms
Drawer             260–300 ms
Modal              280–320 ms
Page transition    280–340 ms
```

Card entrance:

```text
opacity:      0 -> 1
translateY:   8px -> 0
scale:        .985 -> 1
```

Card hover:

```text
translateY:   0 -> -2px
shadow:       almost flat -> subtly raised
```

Button press:

```text
scale: 1 -> .985 -> 1
```

Use shared layout animations when a bento card expands into a detail card/panel.

Every nonessential animation must support:

```css
@media (prefers-reduced-motion: reduce) { ... }
```

No:

- large elastic bounce;
- perpetual wobble;
- particles;
- meteors;
- neon pulsing;
- constant card tilt;
- cursor gimmicks across the whole product.

---

# 9. Open-source-first component strategy

## 9.1 Rule

Before Codex creates a reusable UI primitive, it must check this resource registry. Prefer importing/copying a compatible implementation and adapting it to SA Command.

Every copied/vendorized source file must include a short provenance comment:

```ts
/**
 * Adapted for SA Command.
 * Source: <URL>
 * Original license: <license>
 * Local changes: <one line>
 */
```

Maintain:

```text
docs/THIRD_PARTY.md
```

with:

```text
Component / package
Source URL
Version or retrieval date
License
Files used
Modifications
```

## 9.2 License gate

Do not assume "free" means MIT.

- **MIT / ISC / Apache-2.0**: preferred.
- **Custom licenses**: use only according to explicit terms.
- **Unknown license**: do not copy source into the project until verified.
- **FreeFrontend**: individual examples can have different attribution/license data; use only entries whose license is explicitly compatible and record the original author/source.
- **Aceternity UI**: follow its current Aceternity license. It allows use/modification in end products but restricts redistribution of source items. Do not publish its source as a standalone component library or marketplace product.

The application can be private/commercial, but our source harvesting still needs clean provenance.

---

# 10. UI/component resources to use

## 10.1 Aceternity UI — primary visual acceleration source

Main:
https://ui.aceternity.com/components

Aceternity currently provides copy/paste React + Tailwind + Motion components and is shadcn-compatible.

### Use/adapt

**Bento Grid**
https://ui.aceternity.com/components/bento-grid

Use for:
- Command dashboard grid;
- People card grid;
- Production overview;
- Communications summary.

Do not keep its default styling. Keep the grid/layout idea and rebuild surfaces with SA tokens.

**Floating Dock**
https://ui.aceternity.com/components/floating-dock

Use for:
- the detached left operational dock;
- hover enlargement kept extremely subtle.

**Animated Tabs**
https://ui.aceternity.com/components/tabs

Use for:
- top floating domain switcher;
- detail page tabs;
- employee tabs;
- production tabs.

**Animated Modal**
https://ui.aceternity.com/components/animated-modal

Use for:
- Quick Create;
- confirmations;
- payroll lock confirmation;
- manual message composer.

**Stateful Button**
https://ui.aceternity.com/components/stateful-button

Use for:
- Save;
- Send WhatsApp;
- calculate payroll;
- approve payroll;
- retry message.

**Expandable Card**
https://ui.aceternity.com/components/expandable-card

Use for:
- employee card -> detail view;
- production card -> overview;
- attention card -> detail.

**Card Hover Effect**
https://ui.aceternity.com/components/card-hover-effect

Use only the restrained tracking/selection behavior, not strong glow.

### Explicitly avoid
Aurora, meteors, shooting stars, vortex, shaders, huge parallax, 3D globe, aggressive glare, wobble cards and decorative canvas effects.

---

## 10.2 CodeFronts — CSS microinteraction source

Main motion collection:
https://codefronts.com/motion/

**Card hover collection**
https://codefronts.com/motion/css-card-hover-effects/

Use the **Minimalist Elevation / Dynamic Shadows** style as the main card-hover reference.

**Loading animations**
https://codefronts.com/motion/css-loading-animations/

Use only small muted loaders if a skeleton is inappropriate.

**Tabs**
https://codefronts.com/navigation/css-tabs/

Use for low-level CSS ideas around segmented controls and selected-state motion.

**Tooltips**
https://codefronts.com/snippets/css-tooltips/

Use for left dock labels and icon explanations.

**Progress bars**
https://codefronts.com/components/css-progress-bars/

Use as source ideas for production/task progress, but simplify them to SA's monochrome visual language.

CodeFronts is a source of **microinteraction code**, not a second design system.

---

## 10.3 FreeFrontend — selective animation reference

Main:
https://freefrontend.com/css-animations/

Use only examples marked with a compatible license, especially for:

- fade/slide reveals;
- small status transitions;
- loading details;
- control feedback;
- restrained morphing;
- lightweight CSS-only effects.

Do not bring in GSAP just because a FreeFrontend example uses it unless an equivalent cannot be done cleanly with Motion/CSS. We want one primary animation runtime.

---

# 11. Core open-source repositories

These are preferred building blocks.

## Desktop shell

**Tauri**
https://github.com/tauri-apps/tauri

Why:
- Windows + macOS desktop packaging;
- native system webviews;
- installers;
- native notifications;
- updater support;
- small desktop shell compared with bundling a full Chromium runtime.

License: MIT / Apache-2.0.

## Base accessible UI

**shadcn/ui**
https://github.com/shadcn-ui/ui

Use for:
- button;
- input;
- textarea;
- checkbox;
- radio;
- select;
- popover;
- dropdown;
- context menu;
- dialog primitives;
- sheet/drawer foundation;
- form layout;
- toast integration;
- date input foundations.

License: MIT.

## Motion

**Motion**
https://github.com/motiondivision/motion

Use for:
- shared layout;
- page/card transitions;
- spring animation;
- presence animation.

License: MIT.

## Command palette

**cmdk**
https://github.com/dip/cmdk

Use for:
- Ctrl+K / Cmd+K command palette;
- fuzzy-feeling action/entity navigation;
- accessible keyboard behavior.

License: MIT.

## Calendar

**Schedule-X**
https://github.com/schedule-x/schedule-x

Use for:
- month;
- week;
- agenda;
- event rendering;
- calendar interaction.

License: MIT.

Heavy restyling is required to make it look like SA Command.

## Icons

**Lucide**
https://github.com/lucide-icons/lucide

Use `lucide-react`.

License: ISC.

Keep icon stroke width visually consistent and avoid mixing icon libraries.

## Server state

**TanStack Query**
https://github.com/TanStack/query

Use for:
- API queries;
- caching;
- mutation state;
- background refresh;
- invalidation;
- optimistic UI where safe.

License: MIT.

## Local UI state

**Zustand**
https://github.com/pmndrs/zustand

Use only for:
- shell state;
- theme;
- selected dock mode;
- command palette state;
- non-server UI state.

Do not mirror API data into Zustand.

License: MIT.

## Forms

**React Hook Form**
https://github.com/react-hook-form/react-hook-form

Use with Zod for forms.

License: MIT.

**Zod**
https://github.com/colinhacks/zod

Use for client-side schema validation and type-safe form parsing.

## Data visualization

**Recharts**
https://github.com/recharts/recharts

Use sparingly for:
- attendance trend;
- on-time work trend;
- payroll history;
- small workload visualizations.

License: MIT.

Do not turn the home screen into a chart wall.

## Backend

**Spring Boot**
https://github.com/spring-projects/spring-boot

Use Java 21, Spring Web, Spring Security, Data JPA, Validation, Actuator as needed.

License: Apache-2.0.

## Integration testing

**Testcontainers Java**
https://github.com/testcontainers/testcontainers-java

Use real PostgreSQL containers in backend integration tests.

License: MIT.

## Database migration

**Flyway**
https://github.com/flyway/flyway

Use append-only SQL migrations.

---

# 12. Why Tauri + React + Spring Boot

## Desktop
Tauri handles desktop windows, installers, OS integration and native packaging.

## UI
React/TypeScript gives us the richest reusable open-source component ecosystem for the exact animated UI direction.

## Backend
Spring Boot gives V1 a strong server foundation for:

- payroll rules;
- transactional writes;
- scheduling conflict checks;
- audit;
- messaging outbox;
- webhook handling;
- deterministic business logic;
- future role/security expansion.

The desktop should not own these rules.

---

# 13. Final V1 stack

```text
Desktop Runtime
└── Tauri 2

Frontend
├── React
├── TypeScript
├── Vite
├── Tailwind CSS
├── shadcn/ui
├── Motion
├── TanStack Query
├── Zustand
├── React Hook Form
├── Zod
├── Schedule-X
├── Recharts
├── cmdk
└── Lucide React

Backend
├── Java 21
├── Spring Boot
├── Spring Security
├── Spring Data JPA
├── Jakarta Validation
├── Flyway
├── PostgreSQL
├── Spring Scheduler
├── Resilience4j where useful
└── Testcontainers

Communication
├── WhatsApp Business Platform / Cloud API
└── WhatsApp Webhooks

Testing
├── JUnit 5
├── Testcontainers
├── Vitest
├── React Testing Library
└── Playwright

Presentation / local infrastructure
└── Docker Compose for PostgreSQL + backend dependencies
```

---

# 14. Two runtime modes

V1 must be presentation-safe.

## 14.1 Demo mode

No Meta credentials required.

```env
APP_MODE=demo
MESSAGING_PROVIDER=console
DEMO_SEED=true
```

Behavior:

- deterministic seed data;
- local PostgreSQL;
- console/simulated WhatsApp provider;
- built-in webhook/reply simulator;
- no external network dependency for the core presentation;
- all message states can be demonstrated.

## 14.2 Real mode

```env
APP_MODE=production
MESSAGING_PROVIDER=meta
DEMO_SEED=false
```

Behavior:

- Meta WhatsApp Cloud API;
- publicly reachable HTTPS webhook;
- real approved WhatsApp templates;
- real delivery/reply updates.

The domain code must not care which provider is active.

---

# 15. Deployment shape

## Presentation topology

```text
Windows / macOS laptop

SA Command Tauri
      │
      │ HTTP localhost / configured API
      ▼
Spring Boot
      │
      ▼
PostgreSQL via Docker
      │
      └── ConsoleMessagingProvider
```

## Real topology

```text
SA Command Desktop
        │
        │ HTTPS
        ▼
Public Spring Boot API
        │
        ├── PostgreSQL
        │
        └── Meta WhatsApp Cloud API
                    │
                    ▼
                Employee
                    │
                    ▼
                Webhook
                    │
                    └────> Spring Boot
```

Closing the desktop app must not stop scheduled WhatsApp notifications in real mode.

---

# 16. Messaging architecture — do not send synchronously

Never:

```text
Owner saves event
 -> call WhatsApp
 -> wait
 -> return response
```

Use:

```text
Owner saves event
 -> database transaction
 -> domain event/outbox row
 -> commit
 -> request returns immediately
 -> background worker reads outbox
 -> creates notification/message job
 -> sends via provider
 -> updates delivery state
```

Use a **PostgreSQL-backed transactional outbox** in V1. Do not add Redis just to look "enterprise".

Redis can be introduced later only when actual throughput requires it.

---

# 17. Main domain model

## 17.1 users

```text
id
email
password_hash
display_name
role
created_at
updated_at
```

V1 role:

```text
OWNER
```

Architecture should not block future managers/admins.

---

## 17.2 employees

```text
id
employee_code
first_name
last_name
display_name
phone
whatsapp_phone
email
role_title
department
employment_type
joining_date
base_salary_minor
salary_currency
status
profile_photo_url
notes
created_at
updated_at
```

Status:

```text
ACTIVE
ON_LEAVE
INACTIVE
```

All money uses integer minor units.

---

## 17.3 attendance_records

```text
id
employee_id
date
status
check_in_time
check_out_time
minutes_late
notes
recorded_by
created_at
updated_at
```

Status:

```text
PRESENT
ABSENT
LATE
HALF_DAY
LEAVE
HOLIDAY
```

Unique constraint:

```text
(employee_id, date)
```

---

## 17.4 leave_requests

```text
id
employee_id
start_date
end_date
leave_type
reason
status
owner_note
created_at
resolved_at
```

Status:

```text
PENDING
APPROVED
REJECTED
CANCELLED
```

---

## 17.5 productions

```text
id
title
client_name
description

event_date
start_time
end_time

venue_name
venue_address

status
priority
progress_percent

created_at
updated_at
completed_at
```

State:

```text
DRAFT
PLANNING
PRE_PRODUCTION
PRODUCTION
POST_PRODUCTION
REVIEW
DELIVERED
CANCELLED
```

Normal flow:

```text
DRAFT
 -> PLANNING
 -> PRE_PRODUCTION
 -> PRODUCTION
 -> POST_PRODUCTION
 -> REVIEW
 -> DELIVERED
```

---

## 17.6 production_members

```text
id
production_id
employee_id
production_role
attendance_required
assignment_status
created_at
updated_at
```

Status:

```text
PENDING
CONFIRMED
DECLINED
```

Unique:

```text
(production_id, employee_id)
```

---

## 17.7 tasks

```text
id
production_id nullable
title
description
assigned_employee_id
created_by

status
priority

start_date
due_at
completed_at
progress_percent

created_at
updated_at
```

State:

```text
TODO
IN_PROGRESS
BLOCKED
DONE
CANCELLED
```

Progress is separate from state.

---

## 17.8 task_updates

```text
id
task_id
author_id
progress_percent
note
created_at
```

Do not overwrite all task history when progress changes.

---

## 17.9 calendar_events

Type:

```text
PRODUCTION
SHOOT
MEETING
DEADLINE
INTERNAL
REMINDER
```

Fields:

```text
id
type
title
description
starts_at
ends_at
location_name
location_address
production_id nullable
meeting_id nullable
status
created_at
updated_at
```

---

## 17.10 event_attendees

```text
event_id
employee_id
response
notified_at
acknowledged_at
```

Response:

```text
PENDING
ACCEPTED
DECLINED
```

---

## 17.11 meetings

```text
id
title
description
starts_at
ends_at
location
status
created_at
updated_at
```

### meeting_attendees

```text
meeting_id
employee_id
response
created_at
updated_at
```

### meeting_notes

```text
id
meeting_id
content
created_at
updated_at
```

Meeting action items become ordinary tasks.

---

# 18. Payroll model

## 18.1 payroll_periods

```text
id
year
month
status
created_at
calculated_at
approved_at
paid_at
locked_at
```

State:

```text
DRAFT
 -> CALCULATED
 -> APPROVED
 -> PAID
 -> LOCKED
```

`LOCKED` is terminal in normal V1 operation.

---

## 18.2 payroll_items

Historical snapshot:

```text
id
payroll_period_id
employee_id

base_salary_minor
attendance_deduction_minor
overtime_minor
bonus_minor
advance_deduction_minor
manual_adjustment_minor

net_salary_minor

payment_status
paid_at
created_at
```

Historical payroll must never read the employee's **current** salary as its historical truth.

---

## 18.3 payroll_adjustments

```text
id
payroll_item_id
type
amount_minor
reason
created_by
created_at
```

Types:

```text
BONUS
DEDUCTION
OVERTIME
ADVANCE
CORRECTION
OTHER
```

---

# 19. Performance model

Do not create an opaque algorithmic score.

Show evidence:

- attendance rate;
- late count;
- tasks assigned;
- tasks completed;
- on-time completion rate;
- overdue task count;
- active production participation;
- completed production participation;
- average task completion time.

Manual owner review:

```text
performance_reviews

id
employee_id
period_start
period_end
rating
owner_notes
created_at
```

Rating:

```text
EXCELLENT
GOOD
SATISFACTORY
NEEDS_IMPROVEMENT
```

Never infer personality, intelligence or employee value from these metrics.

---

# 20. Communication model

## 20.1 notification_rules

```text
id
event_type
channel
enabled
delay_minutes
template_key
created_at
updated_at
```

V1 events:

```text
PRODUCTION_ASSIGNED
PRODUCTION_UPDATED
EVENT_CHANGED
EVENT_REMINDER_24H
EVENT_REMINDER_2H
TASK_ASSIGNED
TASK_DUE_24H
MEETING_CREATED
MEETING_UPDATED
MEETING_REMINDER
ATTENDANCE_MISSING
LEAVE_APPROVED
LEAVE_REJECTED
SALARY_PROCESSED
MANUAL_NOTICE
```

---

## 20.2 domain_events / outbox

```text
id
event_type
aggregate_type
aggregate_id
payload_json
status
attempt_count
available_at
created_at
processed_at
last_error
```

State:

```text
PENDING
PROCESSING
PROCESSED
FAILED
```

Lock rows safely so two workers cannot process the same event.

---

## 20.3 outbound_messages

```text
id
employee_id
channel
template_key
template_variables_json
status
provider_message_id
idempotency_key
attempt_count
last_error
queued_at
sent_at
delivered_at
read_at
failed_at
```

Message state:

```text
QUEUED
SENDING
SENT
DELIVERED
READ
FAILED
```

Unique:

```text
idempotency_key
```

Recommended key:

```text
domainEventId + employeeId + channel + templateKey
```

---

# 21. WhatsApp V1

Use the official **WhatsApp Business Platform / Cloud API**.

Do not use:

- Selenium;
- WhatsApp Web automation;
- browser scraping;
- unofficial reverse-engineered WhatsApp libraries.

Official reference:
https://developers.facebook.com/docs/whatsapp/cloud-api

Use approved template messages where Meta requires them.

V1 template set:

```text
sa_production_assignment
sa_schedule_changed
sa_event_reminder
sa_task_assigned
sa_task_due
sa_meeting_invitation
sa_meeting_reminder
sa_attendance_missing
sa_leave_status
sa_salary_processed
sa_manual_notice
```

---

# 22. Employee WhatsApp interactions

V1 inbound replies are deterministic.

Example:

```text
SA Production

You have been assigned to:
Sharma Wedding

26 September
Reporting: 4:00 PM
Royal Orchid, Lucknow

[ Confirm ]
[ Not Available ]
```

Webhook flow:

```text
Employee taps button
 -> Meta webhook
 -> verify request
 -> identify message/employee
 -> identify production assignment
 -> idempotency check
 -> CONFIRMED or DECLINED
 -> audit
 -> desktop query invalidation / refresh
```

No LLM/NLP interpretation of random chat messages in V1.

---

# 23. Built-in message simulator

Presentation mode requires a real-looking simulator screen hidden behind:

```text
Settings -> Developer -> Messaging Simulator
```

Capabilities:

- inspect queued messages;
- simulate `SENT`;
- simulate `DELIVERED`;
- simulate `READ`;
- simulate `FAILED`;
- simulate `CONFIRM`;
- simulate `DECLINE`;
- simulate meeting attendance response.

This allows a full end-to-end presentation without risking API or internet failure.

The simulator must call the **same backend service layer** used by webhook handling, not directly mutate frontend state.

---

# 24. Audit model

```text
audit_logs

id
actor_type
actor_id
entity_type
entity_id
action
before_json
after_json
created_at
```

Audit:

- employee created/edited/deactivated;
- salary basis changed;
- attendance changed;
- leave approved/rejected;
- production created/rescheduled;
- crew assignment;
- scheduling conflict override;
- task status/progress change;
- meeting change;
- payroll calculated/approved/paid/locked;
- payroll adjustment;
- manual message sent;
- automation rule changed;
- failed-message retry.

Do not audit meaningless hover/navigation activity.

---

# 25. Scheduling conflict detection

When assigning an employee to a production/event/meeting:

```text
employee_id
+
proposed starts_at / ends_at
    ↓
query existing event_attendees
    ↓
overlap?
```

Overlap:

```text
existing.start < proposed.end
AND
existing.end > proposed.start
```

If conflict:

```text
Scheduling conflict

Amaan is already assigned to
Corporate Shoot
2:00 PM – 6:00 PM.

The new assignment begins at 4:30 PM.

[Cancel] [Assign Anyway]
```

Override requires explicit owner action and an audit event.

---

# 26. Dashboard / Command screen

This is the visual hero of the app and should most closely resemble the reference.

Recommended bento map at desktop width:

```text
┌───────────────────────────────────────────────────────────────────┐
│ Good evening, Owner.                             Search / Profile │
├──────────────┬─────────────────────────┬───────────────────────────┤
│ TIME / DATE  │ NEXT / TODAY            │ TEAM STATUS               │
│              │                         │                           │
├───────┬──────┼─────────────────────────┼───────────────────────────┤
│ ATT.  │ COMMS│ ACTIVE PRODUCTIONS      │ WORKLOAD / CAPACITY       │
│       │      │                         │                           │
├───────┴──────┼─────────────────────────┼───────────────────────────┤
│ PAYROLL      │ NEEDS ATTENTION                                     │
│              │                                                     │
└──────────────┴─────────────────────────────────────────────────────┘
```

Cards:

### Greeting / search row
- "Good evening, Owner."
- global search field;
- notification bell;
- owner avatar.

### Time card
- current time;
- date;
- next operational milestone.

### Today card
- next 3 events/meetings;
- click opens calendar.

### Team status
- `14 / 18 present`;
- late/absent/leave breakdown;
- circular gauge inspired by the reference temperature gauge.

### Communications
- delivered;
- awaiting response;
- failed.

### Active productions
- 2–4 production rows;
- progress;
- stage;
- due/event date.

### Workload
- simple distribution or circular workload indicator;
- flag overloaded crew.

### Payroll
- monthly estimated total;
- paid;
- pending;
- current period state.

### Needs Attention
Only exceptions:

- employee hasn't acknowledged an event change;
- overdue work;
- failed message;
- scheduling conflict;
- payroll awaiting approval;
- attendance not completed.

The dashboard must answer: **what should the owner act on now?**

---

# 27. People screen

Header:

```text
People                           + Employee

18 employees

[Search people]     Active  Leave  Inactive
```

Primary mode: bento card grid.

Employee card:

```text
AK                              ● Present

Amaan Khan
Senior Video Editor

Attendance     Work
94%            87%

7 active tasks
```

Secondary list mode is allowed for fast scanning, but must still use SA styling.

Clicking a card should use shared-layout expansion or a right-side detail surface.

---

# 28. Employee detail

Hero:

```text
[Photo]

Amaan Khan
Senior Video Editor

● Active
WhatsApp ✓

[Message] [Edit]
```

Tabs:

```text
Overview
Attendance
Work
Payroll
Performance
Communication
```

### Overview
- employment details;
- joining date;
- base salary;
- department;
- current assignments;
- upcoming production;
- active tasks.

### Attendance
- monthly summary;
- records;
- late days;
- leaves.

### Work
- active/completed tasks;
- progress history;
- overdue.

### Payroll
- recent payroll periods;
- adjustments;
- paid history.

### Performance
Evidence, not an opaque score.

### Communication
Message timeline:

```text
Today 4:31 PM
✓✓ Production assignment delivered

Today 10:42 AM
✓✓ Attendance reminder read

Yesterday
! Meeting reminder failed
```

---

# 29. Attendance screen

Top:

```text
‹   September 18, 2026   ›
```

Summary cards:

```text
Present 14
Late 2
Absent 1
Leave 1
```

Employee rows/cards:

```text
Amaan Khan

[Present] [Late] [Absent] [Leave]

Check-in 09:47
```

Bulk:

```text
Mark remaining as present
```

Rules:

- one attendance record per employee/day;
- every manual change audited;
- changing attendance after locked payroll does not rewrite locked historical payroll;
- dashboard updates immediately after mutation.

---

# 30. Productions screen

Tabs:

```text
Active
Upcoming
Delivered
All
```

Production card:

```text
Sharma Wedding

26 SEP
Royal Orchid, Lucknow

PRE-PRODUCTION

██████████████░░░ 78%

8 crew
3 unfinished tasks
```

Use bento sizing:
- high-priority/current production can span 2 columns;
- normal production uses standard card.

---

# 31. Production detail

Hero:

```text
SHARMA WEDDING

26 September
Royal Orchid
4:30 PM

PRE-PRODUCTION

███████████████░ 78%
```

Tabs:

```text
Overview
Crew
Tasks
Schedule
Notes
Activity
```

Crew:

```text
Amaan      Editor      ✓ Confirmed
Sarah      Photo       ✓ Confirmed
Rehan      Camera      ○ Awaiting
Farhan     Assistant   ✕ Unavailable
```

Operations:

- assign/unassign;
- detect conflicts;
- send/resent assignment notice;
- record acknowledgement;
- move production stage;
- update progress;
- view related tasks/events.

---

# 32. Work screen

Views:

```text
Today
Team
Upcoming
Overdue
Completed
```

Task card:

```text
Wedding teaser edit

Sharma Wedding

Amaan Khan                  78%
Due tomorrow

███████████████░░░
```

Task interaction:

- status;
- assignee;
- progress;
- deadline;
- blocker;
- note/update history;
- quick reminder.

Do not implement a heavyweight Jira board in V1.

---

# 33. Calendar screen

Use Schedule-X as the engine.

Views:

```text
Month
Week
Agenda
```

Event colors are subtle:

```text
Production  warm accent
Meeting     desaturated blue
Deadline    muted amber
Internal    neutral
```

Requirements:

- click event -> animated detail panel;
- click date -> quick create;
- production events linked back to production;
- meetings linked back to meeting;
- conflict indicators;
- today line/marker;
- upcoming agenda.

Drag/drop rescheduling is **optional** for presentation V1. If implemented, it must run conflict checks and trigger schedule-change events.

---

# 34. Meetings

Meeting detail:

```text
Weekly Production Review
Friday · 11:30 AM

Participants
Amaan · Rehan · Sarah

Agenda
✓ Last week's deliveries
✓ Sharma wedding preparation
○ Equipment preparation

Notes
...

Action items
Amaan  Finish teaser export      Sep 19
Rehan  Confirm batteries         Sep 19
```

Meeting action items create normal `tasks`.

WhatsApp invite:

```text
Production Review
Tomorrow, 11:30 AM

[Attending]
[Can't Attend]
```

Desktop shows:

```text
6 attending
1 unavailable
1 waiting
```

---

# 35. Payroll screen

Header:

```text
September 2026

₹3,42,500 payroll
₹2,60,000 paid
₹82,500 pending

[Calculate Payroll]
```

Employee item:

```text
Amaan Khan

₹42,000 Base
+ ₹3,000 Bonus

₹45,000 Net

PAID
```

Detail:

```text
Base salary                 ₹42,000
Attendance deduction             ₹0
Bonus                         ₹3,000
Overtime                           ₹0
Advance                            ₹0
Manual adjustment                  ₹0
────────────────────────────────────
NET                           ₹45,000
```

State actions:

```text
Calculate
Approve
Mark paid
Lock
```

Lock confirmation:

```text
Lock September payroll?

This period will be preserved as financial history.
Future attendance or salary changes will not alter it.

[Cancel] [Lock Payroll]
```

No hidden recalculation after lock.

---

# 36. Communications screen

Header cards:

```text
42 delivered
7 read
3 awaiting reply
1 failed
```

Filters:

```text
All
Needs Attention
Assignments
Meetings
Attendance
Payroll
Manual
```

Row:

```text
Amaan Khan
Sharma Wedding Assignment
✓✓ Delivered
4:31 PM
```

Failed row:

```text
Farhan Ali
Tomorrow's Production Reminder
! Failed
Network/provider error

[Retry]
```

---

# 37. Manual message composer

```text
Message employees

Recipients
[Amaan ×] [Rehan ×]

Type
General Notice

Message
┌────────────────────────────────────┐
│ Please arrive at the office by     │
│ 3:30 PM for equipment preparation. │
└────────────────────────────────────┘

2 employees will receive this message.

[Cancel]                        [Send]
```

Bulk send always requires confirmation.

Respect employee quiet-hours settings if implemented; urgent override must be explicit.

---

# 38. Notification settings

Owner can control automation:

```text
● Production assignment
  Send immediately

● Production schedule changed
  Send immediately + require acknowledgement

● Event reminder
  24 hours before

● Event final reminder
  2 hours before

● Task due reminder
  24 hours before

● Attendance missing
  At configured attendance cutoff

● Salary processed
  When marked paid
```

No notification should be impossible to disable except system/security notifications.

---

# 39. Command palette

Shortcut:

```text
Windows: Ctrl + K
macOS:   Cmd + K
```

Use cmdk.

Examples:

```text
> ayaan
Amaan Khan — Open Employee

> wedding
Sharma Wedding — Open Production

> attendance
Mark Today's Attendance

> payroll
Open September Payroll

> meeting
Create Meeting

> message ayaan
Message Amaan
```

V1 may use client-side search over cached entity names/actions.

Do not expose sensitive payroll breakdowns directly in casual global search results.

---

# 40. Quick Create

Top `+` opens:

```text
Add Employee
Create Production
Create Task
Schedule Event
Schedule Meeting
Record Payroll Adjustment
Send Message
```

The menu should behave like a compact Apple/Raycast action palette.

---

# 41. API shape

Base:

```text
/api/v1
```

## Auth

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

## Dashboard

```text
GET /dashboard
GET /dashboard/attention
```

## Employees

```text
GET    /employees
POST   /employees
GET    /employees/{id}
PATCH  /employees/{id}
POST   /employees/{id}/deactivate
```

## Attendance

```text
GET /attendance?date=
GET /employees/{id}/attendance
PUT /attendance/{employeeId}/{date}
```

## Leave

```text
GET   /leave-requests
POST  /leave-requests
POST  /leave-requests/{id}/approve
POST  /leave-requests/{id}/reject
```

## Productions

```text
GET    /productions
POST   /productions
GET    /productions/{id}
PATCH  /productions/{id}

POST   /productions/{id}/members
DELETE /productions/{id}/members/{employeeId}
POST   /productions/{id}/members/{employeeId}/notify
```

## Tasks

```text
GET   /tasks
POST  /tasks
GET   /tasks/{id}
PATCH /tasks/{id}
POST  /tasks/{id}/updates
POST  /tasks/{id}/remind
```

## Calendar

```text
GET   /calendar/events
POST  /calendar/events
GET   /calendar/events/{id}
PATCH /calendar/events/{id}
DELETE /calendar/events/{id}
```

## Meetings

```text
GET   /meetings
POST  /meetings
GET   /meetings/{id}
PATCH /meetings/{id}
POST  /meetings/{id}/notify
```

## Payroll

```text
GET  /payroll
GET  /payroll/{id}
POST /payroll/{year}/{month}/calculate
POST /payroll/{id}/adjustments
POST /payroll/{id}/approve
POST /payroll/{id}/mark-paid
POST /payroll/{id}/lock
```

## Communication

```text
GET  /messages
GET  /messages/{id}
POST /messages/manual
POST /messages/{id}/retry

GET   /notification-rules
PATCH /notification-rules/{id}
```

## Integrations

```text
GET  /integrations/whatsapp/webhook
POST /integrations/whatsapp/webhook
```

## Demo simulator

Available only in demo/development:

```text
POST /demo/messages/{id}/sent
POST /demo/messages/{id}/delivered
POST /demo/messages/{id}/read
POST /demo/messages/{id}/fail
POST /demo/assignments/{id}/confirm
POST /demo/assignments/{id}/decline
POST /demo/reset
```

---

# 42. API contract

Success:

```json
{
  "data": {},
  "meta": {}
}
```

Error:

```json
{
  "error": {
    "code": "EMPLOYEE_NOT_FOUND",
    "message": "Employee was not found.",
    "traceId": "01J..."
  }
}
```

Rules:

- no stack traces to desktop;
- stable error codes;
- client-safe messages;
- request validation errors map cleanly to form fields;
- trace IDs appear in logs.

---

# 43. Backend package structure

```text
apps/backend/
└── src/main/java/com/saproduction/command/
    ├── auth/
    ├── dashboard/
    ├── employee/
    ├── attendance/
    ├── leave/
    ├── production/
    ├── task/
    ├── calendar/
    ├── meeting/
    ├── payroll/
    ├── communication/
    ├── notification/
    ├── audit/
    ├── integration/
    │   └── whatsapp/
    └── shared/
        ├── exception/
        ├── security/
        ├── time/
        ├── money/
        └── persistence/
```

Inside a feature:

```text
controller
service
repository
entity
dto
mapper
events
```

Do not create excessive clean-architecture layers that slow V1 without adding safety.

---

# 44. Frontend structure

```text
apps/desktop/
├── src/
│   ├── app/
│   │   ├── router/
│   │   ├── providers/
│   │   └── store/
│   ├── components/
│   │   ├── ui/
│   │   ├── motion/
│   │   ├── charts/
│   │   └── layout/
│   ├── features/
│   │   ├── command/
│   │   ├── employees/
│   │   ├── attendance/
│   │   ├── productions/
│   │   ├── tasks/
│   │   ├── calendar/
│   │   ├── meetings/
│   │   ├── payroll/
│   │   ├── communications/
│   │   └── settings/
│   ├── lib/
│   ├── hooks/
│   ├── types/
│   └── styles/
└── src-tauri/
```

Do not dump domain components into one generic `components/` directory.

---

# 45. Repository shape

```text
SA-Command/
├── AGENTS.md
├── PLAN.md
├── README.md
├── LICENSES/
├── docker-compose.yml
├── .env.example
│
├── apps/
│   ├── desktop/
│   └── backend/
│
├── docs/
│   ├── reference/
│   │   └── sa-command-ui-reference.png
│   ├── THIRD_PARTY.md
│   ├── architecture.md
│   ├── design-system.md
│   ├── data-model.md
│   ├── whatsapp.md
│   ├── payroll.md
│   ├── demo.md
│   └── security.md
│
└── scripts/
    ├── dev.*
    ├── demo-reset.*
    └── build-release.*
```

---

# 46. Database migrations

Use Flyway and never edit a migration already used outside a disposable local database.

Suggested start:

```text
V001__users.sql
V002__employees.sql
V003__attendance_and_leave.sql
V004__productions.sql
V005__tasks.sql
V006__calendar.sql
V007__meetings.sql
V008__payroll.sql
V009__notifications_and_outbox.sql
V010__audit.sql
V011__demo_support.sql
```

Seed data should be isolated from production migrations.

---

# 47. Security

## Backend
Use:

- Spring Security;
- Argon2id or a modern secure password hashing configuration;
- short-lived access token;
- refresh-token rotation;
- TLS in real deployment;
- strict input validation;
- rate limiting on login and public webhook paths where appropriate;
- CSP/security headers for relevant web endpoints;
- central exception handling;
- request/trace IDs.

## Desktop
Do not put secrets in:

```text
localStorage
source code
Vite env bundled into the client
plain config JSON
```

Tokens should use appropriate Tauri/OS secure storage facilities.

## WhatsApp
Meta access tokens live only on the server.

Verify webhook requests according to Meta's supported verification/signature process.

Provider payloads are untrusted input.

## Payroll
Financial writes require explicit backend state validation, not only disabled buttons.

---

# 48. Privacy

Do not send owner-only notes or internal review comments over WhatsApp.

Salary message should be concise:

```text
September salary has been processed.
Net salary: ₹38,750
```

Detailed owner calculations remain inside SA Command.

Employee phone numbers and salary data are sensitive application data. Avoid unnecessary logging.

---

# 49. Failure behavior

## Network unavailable
Desktop:

```text
You're offline.
Some information may be outdated.
```

Do not show a fake success toast.

## WhatsApp fails
Business action remains valid.

Example:
salary can be marked paid even if the notification fails.

Message state becomes `FAILED`, and owner can retry.

## Backend unavailable
Disable destructive/financial mutations and show connection state.

## Duplicate provider callback
Idempotency prevents duplicate state transitions.

## Double-click Send
Idempotency and UI pending state prevent duplicate message jobs.

---

# 50. Loading and empty states

Use shape-matching skeletons.

Avoid full-screen spinners.

Empty example:

```text
No productions yet.

Create your first production to start
assigning crew and tracking work.

[Create Production]
```

Use Aceternity-style animation only for subtle entry, not marketing spectacle.

---

# 51. Responsive desktop behavior

Target:

```text
Optimal:  1440 × 900 and above
Minimum:  1180 × 720
```

Rules:

- 3-column bento can collapse to 2;
- top floating switcher remains usable;
- left dock remains detached where space allows;
- detail drawer expands as needed;
- no mobile hamburger redesign in V1;
- test Windows scaling at common 100/125/150%;
- test macOS Retina.

---

# 52. Accessibility

Required:

- visible keyboard focus;
- semantic buttons;
- semantic forms;
- accessible dialogs;
- keyboard modal close;
- keyboard calendar operation where supported;
- command palette fully keyboard-accessible;
- labels for icon-only dock buttons;
- color is never the only status signal;
- reduced-motion mode;
- reasonable text contrast;
- touch/click targets appropriate for desktop.

---

# 53. Testing

## Backend unit tests

Must cover:

- payroll arithmetic;
- payroll state machine;
- locked payroll immutability;
- production transitions;
- overlap detection;
- message idempotency;
- notification rule evaluation;
- retry policy;
- leave transitions.

## Backend integration tests

Use Testcontainers PostgreSQL for:

- real Flyway migrations;
- constraints;
- payroll transaction behavior;
- outbox transaction behavior;
- duplicate webhook handling;
- attendance uniqueness;
- employee assignment uniqueness;
- repository queries;
- API authorization.

## Frontend tests

Vitest + React Testing Library:

- forms;
- bento cards;
- theme state;
- command palette actions;
- payroll state controls;
- message states;
- error displays.

## E2E

Playwright:

1. login;
2. create employee;
3. mark attendance;
4. create production;
5. assign employee;
6. detect conflict;
7. create task;
8. schedule meeting;
9. calculate payroll;
10. add bonus;
11. approve;
12. mark paid;
13. lock;
14. send simulated message;
15. simulate acknowledgement;
16. confirm dashboard update.

---

# 54. Visual regression testing

Because UI quality is a major product requirement, add Playwright screenshots for:

- dashboard Pearl;
- dashboard Charcoal;
- People;
- employee detail;
- Productions;
- production detail;
- Calendar;
- Payroll;
- Communications;
- Quick Create;
- command palette.

Store in:

```text
apps/desktop/tests/visual/
```

Do not blindly pixel-diff against the supplied smart-home reference because content differs. Instead use it as human design authority and keep our own SA Command snapshots stable after sign-off.

---

# 55. Seed/demo data

Create deterministic fixtures for presentation:

### Employees
12–18 employees, including:
- Amaan Khan — Senior Video Editor;
- Rehan Ali — Camera Operator;
- Sarah Khan — Photographer;
- Farhan — Production Assistant;
- additional realistic production roles.

### Productions
- Sharma Wedding — active, 78%;
- Corporate Brand Film — active, 42%;
- Product Shoot — active, 88%;
- delivered production for history.

### Tasks
20–30 tasks with:
- done;
- in-progress;
- overdue;
- blocked.

### Attendance
At least 20 working days.

### Meetings
2 upcoming, 2 historical.

### Payroll
Current month calculated/draft plus one previous locked month.

### Messages
At least:
- delivered;
- read;
- waiting;
- failed;
- acknowledged;
- declined.

Use a fixed seed so demo screenshots and tests remain deterministic.

---

# 56. Logging

Structured backend logs.

Every request:

```text
traceId
method
path
status
duration
```

Never log:

- passwords;
- auth tokens;
- WhatsApp access tokens;
- raw salary exports;
- full sensitive webhook payloads when unnecessary.

Messages may log provider IDs and internal IDs.

---

# 57. PHASE 1 — Foundation + visual system + people

## Goal
By the end of Phase 1, SA Command must already **look like the final product** and have real People/Attendance functionality.

### 57.1 Repository and runtime
Build:

- monorepo/repository layout;
- Tauri 2 desktop;
- React + TS + Vite;
- Tailwind;
- Spring Boot;
- PostgreSQL;
- Docker Compose;
- Flyway;
- API client;
- environment configuration;
- basic auth;
- health endpoint;
- CI sanity checks.

### 57.2 Third-party setup
Add:

- shadcn/ui;
- Motion;
- Lucide;
- TanStack Query;
- Zustand;
- React Hook Form;
- Zod;
- cmdk.

Create `docs/THIRD_PARTY.md`.

### 57.3 Design system first
Before business screens, build:

```text
AppShell
WorkspaceShell
FloatingDomainSwitcher
OperationalDock
BentoGrid
BentoCard
MetricCard
RadialMetric
ProgressBar
StatusDot
StatusBadge
IconButton
SegmentedControl
SAButton
SAStatefulButton
SATabs
SAModal
SADrawer
Popover
Tooltip
CommandPalette
SearchField
FormField
EmptyState
SkeletonCard
Toast
ConfirmAction
```

Components adapted from Aceternity/CodeFronts must be restyled immediately; do not leave default demo styles in production files.

### 57.4 Themes
Implement:
- SA Pearl;
- Reference Charcoal;
- system-level theme persistence.

### 57.5 Shell
Implement:
- floating top switcher;
- detached dock;
- owner avatar;
- quick create;
- command palette;
- theme toggle in Settings;
- subtle page transition.

### 57.6 Fake-data dashboard
Before the backend dashboard endpoint exists, implement the full home layout with typed mock data so visual design is locked early.

### 57.7 People domain
Backend + UI:
- employee create/edit/deactivate;
- employee cards;
- employee detail;
- status;
- salary basis;
- WhatsApp number;
- search/filter.

### 57.8 Attendance/leave
- today's attendance screen;
- monthly history;
- attendance status mutation;
- leave request data model;
- owner approval/rejection;
- audit foundation.

### 57.9 Phase 1 acceptance gate

Must demonstrate:

```text
Launch Tauri
 -> sign in
 -> see high-quality dashboard shell
 -> switch Pearl/Charcoal
 -> Ctrl/Cmd+K works
 -> create employee
 -> edit employee
 -> mark attendance
 -> approve leave
 -> open employee detail
 -> restart app
 -> persisted data remains
```

Tests:
- employee validation;
- attendance uniqueness;
- basic auth;
- visual snapshots for shell/People/Attendance.

**Do not begin Phase 2 until the shell visually matches the intended reference language.**

---

# 58. PHASE 2 — Productions + work + calendar + meetings + payroll

## Goal
By the end of Phase 2, the application is a real internal operations system even before WhatsApp is turned on.

### 58.1 Production domain
Implement:
- CRUD;
- lifecycle;
- priority;
- date/location;
- progress;
- crew;
- employee roles within production;
- production detail tabs;
- activity history.

### 58.2 Scheduling conflicts
Implement:
- time overlap query;
- warning UI;
- override reason/action;
- audit event.

### 58.3 Work domain
Implement:
- task CRUD;
- assignment;
- priority;
- progress;
- updates;
- blockers;
- due dates;
- overdue calculations;
- task reminders as domain events prepared for Phase 3.

### 58.4 Calendar
Add Schedule-X and restyle it fully.

Implement:
- month;
- week;
- agenda;
- productions;
- meetings;
- deadlines;
- internal events;
- quick create;
- event detail panel.

### 58.5 Meetings
Implement:
- create/update;
- participants;
- agenda/notes;
- attendee response state;
- action item -> normal task.

### 58.6 Payroll
Implement:
- payroll period;
- calculation;
- salary snapshots;
- attendance deduction strategy/config;
- bonus;
- deduction;
- overtime;
- advance;
- manual correction;
- approval;
- paid;
- lock;
- immutable history.

Any payroll formula that is business-specific and not yet supplied must be configurable or clearly isolated in one backend policy class. Do not bury rules in UI code.

### 58.7 Dashboard backend
Replace mock data with aggregate backend endpoints:
- team status;
- today;
- active productions;
- workload;
- payroll;
- attention items.

### 58.8 Phase 2 acceptance gate

Must demonstrate:

```text
Create Sharma Wedding
 -> assign 3 employees
 -> create overlapping event
 -> conflict detected
 -> override or cancel
 -> add tasks
 -> update progress
 -> create meeting
 -> view all in calendar
 -> calculate payroll
 -> add ₹3,000 bonus
 -> approve
 -> mark paid
 -> lock
 -> modify employee salary
 -> locked payroll remains unchanged
 -> dashboard reflects operations
```

Tests:
- production transitions;
- schedule overlap;
- task history;
- payroll arithmetic;
- payroll state machine;
- locked period immutability;
- dashboard aggregates.

At the end of Phase 2, the ERP must be fully usable locally even without messaging.

---

# 59. PHASE 3 — Communication + hardening + presentation release

## Goal
Turn the operational app into a complete closed-loop SA Production system and produce a stable presentation build.

### 59.1 Domain events
Add events:

```text
EmployeeCreated
AttendanceRecorded
ProductionCreated
ProductionMemberAssigned
ProductionScheduleChanged
TaskAssigned
TaskDueSoon
TaskCompleted
MeetingCreated
MeetingRescheduled
LeaveApproved
LeaveRejected
PayrollApproved
SalaryMarkedPaid
```

### 59.2 Transactional outbox
Implement:
- insert in business transaction;
- worker polling;
- row locking;
- retry/backoff;
- error capture;
- dead/failed visibility;
- idempotency.

### 59.3 Messaging abstraction

```java
public interface MessagingProvider {
    SendResult send(MessageCommand command);
}
```

Implement:

```text
ConsoleMessagingProvider
MetaWhatsAppProvider
```

Do not let domain services depend directly on Meta classes.

### 59.4 WhatsApp integration
Implement:
- template mapping;
- provider message IDs;
- webhook verification;
- inbound interaction mapping;
- delivery states;
- retry;
- idempotency;
- employee confirmation;
- meeting response;
- production acknowledgement.

### 59.5 Messaging simulator
Implement the hidden/Developer simulator to guarantee presentation reliability.

### 59.6 Communication centre
Implement:
- summary cards;
- status filters;
- detail view;
- retry;
- manual message composer;
- bulk confirmation;
- employee communication history.

### 59.7 Notification automation screen
Implement owner toggles for:
- assignment;
- schedule update;
- 24h event reminder;
- 2h reminder;
- task due;
- meeting;
- attendance missing;
- leave decision;
- salary processed.

### 59.8 Native polish
Tauri:
- app icon;
- Windows installer;
- macOS DMG build path;
- native notifications where helpful;
- window sizing/minimum size;
- proper title bar behavior;
- production config.

### 59.9 Visual/presentation polish
- all empty states;
- all loading states;
- all error states;
- keyboard focus;
- reduced motion;
- dark/light parity;
- visual regression snapshots;
- microinteraction pass;
- responsive pass;
- remove temporary/debug UI from owner-facing screens.

### 59.10 Presentation bundle
Provide:

```text
README.md
docs/demo.md
.env.example
docker-compose.yml
one-command demo reset
deterministic seed
Windows build artifact instructions
macOS build artifact instructions
```

### 59.11 Phase 3 acceptance gate — final V1

The full presentation script in Section 60 must pass from a clean demo reset.

Required test suite:

```text
backend unit             PASS
backend integration      PASS
frontend unit            PASS
Playwright E2E           PASS
visual snapshots         reviewed
Windows Tauri build      PASS
macOS build config       valid / build tested on macOS CI or machine
```

No critical workflow may depend on an active third-party API during presentation mode.

---

# 60. Final presentation script

Reset:

```text
demo reset
```

Then present:

## Scene 1 — Command
Open SA Command.

Show:
- owner greeting;
- today's schedule;
- team attendance;
- active productions;
- payroll;
- communication state;
- attention items.

Switch briefly between Pearl and Charcoal to show the reference-inspired system.

## Scene 2 — People
Open Amaan.

Show:
- role;
- attendance;
- work;
- payroll;
- performance evidence;
- communication timeline.

## Scene 3 — Attendance
Mark Rehan late.

Return to dashboard:
- team card updates.

## Scene 4 — Production
Create:

```text
Sharma Wedding
26 September
4:30 PM
Royal Orchid, Lucknow
```

Assign:
- Amaan;
- Rehan;
- Farhan.

Show schedule conflict detection if one fixture overlaps.

## Scene 5 — Work
Create:

```text
Wedding teaser edit
Assignee: Amaan
Due: 29 September
```

Set progress 78%.

## Scene 6 — Calendar / Meeting
Create:

```text
Production Review
Tomorrow
11:30 AM
```

Invite relevant crew.

## Scene 7 — WhatsApp closed loop
Assignment notification is queued.

Open simulator:
- simulate `DELIVERED`;
- simulate Amaan `CONFIRM`;
- simulate Farhan `DECLINE`.

Back to production:

```text
Amaan      ✓ Confirmed
Rehan      Waiting
Farhan     ✕ Unavailable
```

## Scene 8 — Payroll
Open current month.

Calculate.

Add:
```text
Amaan
Performance bonus
+ ₹3,000
```

Approve -> mark paid -> lock.

Show that the salary confirmation message is queued.

## Scene 9 — Communications
Show:
- delivered;
- read;
- waiting;
- failed.

Simulate one failed message.

Retry it.

## Scene 10 — Command
Return home.

The dashboard now reflects:
- attendance change;
- new production;
- work progress;
- meeting;
- payroll;
- communication acknowledgements.

End on the Command screen.

---

# 61. Performance targets

Presentation build should aim for:

- app shell visible quickly after launch;
- common navigation interactions feel immediate;
- no obvious animation jank;
- dashboard query under normal local conditions feels instant;
- no N+1 query pattern on dashboard aggregates;
- lists remain smooth at realistic small-company employee counts;
- application does not refetch every screen on every minor UI event.

Avoid premature micro-optimization, but profile clear bottlenecks.

---

# 62. Codex operating instructions

Create `AGENTS.md` with these rules:

```md
# SA Command Engineering Rules

SA Command is production-style internal software for SA Production.
It is not a demo CRUD dashboard.

## Product authority
PLAN.md is the source of truth for V1.
The supplied UI reference in docs/reference is the visual authority.

Do not expand scope without updating PLAN.md.

## UI
Preserve:
- large rounded application shell;
- floating top segmented navigation;
- detached left icon dock;
- bento information architecture;
- SA Pearl default theme;
- Reference Charcoal theme;
- thin low-contrast borders;
- minimal shadow;
- very large radii;
- restrained physical motion.

Never introduce:
- generic admin template styling;
- dense permanent sidebar;
- neon;
- excessive gradients;
- random 3D;
- decorative particles;
- giant tables as the default screen;
- mixed icon families.

Before building a reusable primitive, check the approved resource registry
in PLAN.md and THIRD_PARTY.md.

Copied/adapted code must carry source and license provenance.

## Frontend architecture
Server data belongs in TanStack Query.
Do not mirror server entities in Zustand.

Zustand is only for application UI state.

Never place payroll, conflict or messaging business rules inside React.

## Backend
All money is integer minor units.

Locked payroll is immutable.

All externally-triggered callbacks are idempotent.

Important business mutations are audited.

WhatsApp/provider work is asynchronous via the transactional outbox.

Never block a CRUD request on a provider call.

## Integration
MessagingProvider must have:
- ConsoleMessagingProvider
- MetaWhatsAppProvider

The complete demo must run without Meta credentials.

## Security
Secrets never ship in the desktop bundle.

Provider credentials stay server-side.

Validate all DTOs and webhook input.

Do not expose stack traces to the client.

## Database
Flyway migrations are append-only after use.

Database constraints enforce business uniqueness where appropriate.

## Quality
Every finished feature requires:
- loading state;
- empty state;
- error state;
- validation;
- tests;
- accessibility pass;
- audit behavior if business-critical.

No TODO stubs count as completion.

Run relevant tests before marking a phase/task complete.
```

---

# 63. Build order inside each Codex session

Codex should follow this sequence for a feature:

```text
1. Read PLAN.md section.
2. Read existing feature conventions.
3. Inspect approved OSS/component source if relevant.
4. Verify license/provenance.
5. Add/modify database migration if needed.
6. Add backend entity/service/API.
7. Add backend tests.
8. Add frontend types/API/query.
9. Add UI using existing design primitives.
10. Add loading/empty/error states.
11. Add E2E path if business-critical.
12. Run tests.
13. Update docs/THIRD_PARTY.md when code was adapted.
14. Update phase checklist.
```

Do not ask Codex to "build the entire ERP" in one giant prompt. Work by vertical slice.

---

# 64. Recommended vertical slices

Examples:

### Employee slice
```text
migration
 -> entity
 -> repository
 -> service
 -> API
 -> query hook
 -> EmployeeCard
 -> Create/Edit form
 -> tests
```

### Production assignment slice
```text
schema
 -> conflict query
 -> service
 -> domain event
 -> API
 -> crew UI
 -> conflict modal
 -> tests
```

### Payroll lock slice
```text
state validation
 -> immutable database behavior
 -> lock endpoint
 -> confirmation modal
 -> test that future employee salary change does not mutate item
```

---

# 65. Component ownership

The following must become **our own local components**, even when adapted from OSS:

```text
SABentoGrid
SABentoCard
SAFloatingDock
SATopSwitcher
SAAnimatedTabs
SAStatefulButton
SAModal
SAExpandableCard
SAProgress
SARadialMetric
SAStatusPill
SACommandPalette
```

Do not scatter third-party visual class names across feature screens.

Features import SA components, not raw Aceternity components.

That keeps the product visually consistent and makes future refactors possible.

---

# 66. What should still be built ourselves

Open-source-first does not mean outsourcing business logic.

Build our own:

- domain models;
- payroll policy/state;
- notification rules;
- outbox;
- audit;
- scheduling conflict rules;
- dashboard aggregates;
- WhatsApp mapping;
- security model;
- feature composition;
- SA design tokens;
- production-specific workflows.

Reuse open source for:
- primitives;
- calendar engine;
- motion;
- command palette;
- form infrastructure;
- icons;
- caching;
- chart primitives;
- desktop shell.

---

# 67. Definition of Done for V1

V1 is complete only when all are true:

- [ ] Windows desktop app builds and launches.
- [ ] macOS build path is verified.
- [ ] Owner login works.
- [ ] SA Pearl matches the clean Apple-like requirement.
- [ ] Reference Charcoal preserves the attached reference's dark aesthetic.
- [ ] Floating top switcher exists.
- [ ] Detached left dock exists.
- [ ] Bento dashboard is functional, not decorative.
- [ ] People CRUD works.
- [ ] Attendance works.
- [ ] Leave state works.
- [ ] Productions work.
- [ ] Crew assignments work.
- [ ] Scheduling conflicts work.
- [ ] Tasks and progress work.
- [ ] Calendar works.
- [ ] Meetings work.
- [ ] Payroll calculation works.
- [ ] Payroll adjustment works.
- [ ] Payroll approval/payment/lock works.
- [ ] Locked payroll is immutable.
- [ ] Evidence-based performance view exists.
- [ ] Notification rules exist.
- [ ] Transactional outbox works.
- [ ] Console messaging works.
- [ ] Meta provider integration exists/configurable.
- [ ] Webhook handling is idempotent.
- [ ] Communication centre works.
- [ ] Message status history works.
- [ ] Employee interactive response works or is fully simulated in demo mode.
- [ ] Audit trail works.
- [ ] Command palette works.
- [ ] Quick Create works.
- [ ] Empty states are polished.
- [ ] Error states are polished.
- [ ] Reduced motion works.
- [ ] Keyboard navigation is usable.
- [ ] Deterministic seed exists.
- [ ] Demo reset exists.
- [ ] End-to-end presentation script passes.
- [ ] Backend tests pass.
- [ ] Frontend tests pass.
- [ ] E2E tests pass.
- [ ] Third-party provenance is documented.
- [ ] No required presentation workflow depends on live internet.

---

# 68. Future V2 hooks — design for, do not build now

Leave clean extension points for:

- equipment inventory;
- expense tracking;
- invoices;
- client portal;
- employee web companion;
- Google Calendar sync;
- automated reports;
- cloud backups;
- multiple owner/admin users;
- role-based permissions;
- WhatsApp richer workflows;
- operational intelligence / morning brief;
- AI-assisted owner search;
- workload forecasting.

V1 should not contain placeholder screens for these.

---

# 69. Final product philosophy

SA Command should feel like the owner is operating the company from a crafted desktop instrument, not filling forms in a database frontend.

The bento cards are not decoration. They are operational objects:

```text
People
Work
Productions
Calendar
Money
Communication
```

The owner changes a real business state once and the rest of the system reacts:

```text
Owner changes production time
          ↓
production record updates
          ↓
calendar updates
          ↓
conflict checks run
          ↓
domain event written
          ↓
notification jobs created
          ↓
crew receives WhatsApp update
          ↓
acknowledgements return
          ↓
Command shows who has not responded
```

That closed loop is the core difference between SA Command and a generic attendance/salary app.

Build **depth before breadth**. A smaller set of workflows that are reliable, auditable, beautifully presented and connected end-to-end is the V1 goal.

---

# 70. Resource quick index

## Visual / interaction sources
- Aceternity UI: https://ui.aceternity.com/components
- Aceternity Bento Grid: https://ui.aceternity.com/components/bento-grid
- Aceternity Floating Dock: https://ui.aceternity.com/components/floating-dock
- Aceternity Tabs: https://ui.aceternity.com/components/tabs
- Aceternity Animated Modal: https://ui.aceternity.com/components/animated-modal
- Aceternity Stateful Button: https://ui.aceternity.com/components/stateful-button
- Aceternity Expandable Card: https://ui.aceternity.com/components/expandable-card
- Aceternity Card Hover: https://ui.aceternity.com/components/card-hover-effect
- Aceternity licensing: https://ui.aceternity.com/licence
- CodeFronts Motion: https://codefronts.com/motion/
- CodeFronts Card Hover: https://codefronts.com/motion/css-card-hover-effects/
- CodeFronts Loading: https://codefronts.com/motion/css-loading-animations/
- CodeFronts Tabs: https://codefronts.com/navigation/css-tabs/
- CodeFronts Tooltips: https://codefronts.com/snippets/css-tooltips/
- CodeFronts Progress: https://codefronts.com/components/css-progress-bars/
- FreeFrontend CSS Animations: https://freefrontend.com/css-animations/

## Open-source foundations
- Tauri: https://github.com/tauri-apps/tauri
- shadcn/ui: https://github.com/shadcn-ui/ui
- Motion: https://github.com/motiondivision/motion
- cmdk: https://github.com/dip/cmdk
- Schedule-X: https://github.com/schedule-x/schedule-x
- Lucide: https://github.com/lucide-icons/lucide
- TanStack Query: https://github.com/TanStack/query
- TanStack Table if needed for secondary tabular views: https://github.com/TanStack/table
- Zustand: https://github.com/pmndrs/zustand
- React Hook Form: https://github.com/react-hook-form/react-hook-form
- Zod: https://github.com/colinhacks/zod
- Recharts: https://github.com/recharts/recharts
- Spring Boot: https://github.com/spring-projects/spring-boot
- Testcontainers Java: https://github.com/testcontainers/testcontainers-java
- Flyway: https://github.com/flyway/flyway

## Messaging
- Meta WhatsApp Cloud API: https://developers.facebook.com/docs/whatsapp/cloud-api

---

**Status:** V1 blueprint locked.  
**Execution:** 3 phases only.  
**Presentation rule:** the app must remain fully demoable without live external services.  
**Design rule:** the attached reference is the spatial/motion authority; SA Pearl is the default Apple-clean theme and Reference Charcoal preserves the exact dark reference feel.  
**Engineering rule:** reuse proven open-source primitives aggressively, but own the domain logic, provenance, visual system and operational behavior.


