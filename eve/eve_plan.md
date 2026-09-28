# EVE — SA Command Intelligence Layer Plan

> **Status:** Planning only — no Eve implementation yet  
> **Product:** SA Command  
> **Feature:** Eve  
> **Placement:** Top-level navigation, immediately after Billing  
> **Primary operator:** SA Production owner  
> **Core idea:** Let the owner describe what happened in natural language; Eve translates that into governed, inspectable actions against the existing SA Command system.

---

## 0. The idea

Eve is **not a chatbot** and not a replacement for SA Command's existing modules.

Eve is an intelligence layer over the system.

The owner should be able to say things like:

> "Aaj Sharma ko Royal event pe bheja tha, usko 3,000 diye aur Azeem ne payment ki."

or:

> "Royal event ka kaam khatam ho gaya, sabko jo payment banta tha kar diya."

Eve should understand the operational context, locate the relevant existing records, build a structured action plan, validate it against SA Command's business rules, execute through the existing domain/application services, and verify the resulting state.

The existing SA Command system remains the authority.

**LLM proposes. Existing business rules validate. Existing services execute. Database remains canonical. Eve observes and explains the operation.**

---

# 1. Non-negotiable architectural rule

## Do not rebuild SA Command for Eve.

Eve must reuse the current system.

Do not create:

- a second people database;
- a second production database;
- a second finance ledger;
- a second payroll engine;
- a second equipment system;
- direct LLM-to-SQL writes;
- AI-specific duplicate business rules;
- a parallel "Eve ERP".

Instead:

```
Mamu
  ↓
Eve
  ↓
Context + Retrieval
  ↓
Structured Plan
  ↓
Existing SA Command Commands / Services
  ↓
Existing Database
  ↓
Verification
  ↓
Visible Activity Trace
```

Eve should make the existing system easier to operate, not create another system to maintain.

---

# 2. Current repository foundation to reuse

The current repository already provides the foundation Eve needs.

## Desktop

`apps/desktop`

Current stack includes:

- React 19
- TypeScript
- Vite
- Tailwind CSS
- Motion
- TanStack Query
- Zustand
- Radix Dialog / Popover / Tabs / Toast / Tooltip
- cmdk
- Lucide React
- React Hook Form
- Zod
- Tauri

Do not add a new frontend framework or animation runtime for Eve.

## Existing shell

Reuse:

- `apps/desktop/src/components/layout/AppShell.tsx`
- `apps/desktop/src/components/layout/SACommandPalette.tsx`
- `apps/desktop/src/components/layout/QuickCreate.tsx`
- `apps/desktop/src/components/layout/ThemeSwitch.tsx`
- `apps/desktop/src/components/ui/sa.tsx`

Eve should inherit the same SA Command shell, spacing, themes, radii, typography and accessibility behavior.

## Existing feature architecture

Existing domains include:

- command
- employees
- attendance
- productions
- headquarters
- finance
- billing
- work
- calendar
- meetings
- payroll
- communications
- navigator
- settings

Eve should call into these domains rather than duplicating their behavior.

---

# 3. First Eve architecture

```text
                         ┌────────────────────┐
                         │       MAMU         │
                         │ natural language   │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │        EVE         │
                         │ interaction layer  │
                         └─────────┬──────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
              ▼                    ▼                    ▼
       Context Engine       Retrieval Engine      Eve Memory
              │                    │                    │
              └────────────────────┼────────────────────┘
                                   ▼
                         ┌────────────────────┐
                         │  SYSTEM KNOWLEDGE │
                         │ schema + rules +   │
                         │ relationships     │
                         └─────────┬──────────┘
                                   ▼
                         ┌────────────────────┐
                         │      PLANNER       │
                         │ structured actions │
                         └─────────┬──────────┘
                                   ▼
                         ┌────────────────────┐
                         │  COMMAND GATEWAY   │
                         │ deterministic      │
                         │ validation         │
                         └─────────┬──────────┘
                                   ▼
                         ┌────────────────────┐
                         │ EXISTING SERVICES  │
                         │ Finance / People / │
                         │ Production / HQ /  │
                         │ Work / Calendar... │
                         └─────────┬──────────┘
                                   ▼
                         ┌────────────────────┐
                         │    VERIFICATION    │
                         │ state + invariants │
                         └─────────┬──────────┘
                                   ▼
                         ┌────────────────────┐
                         │   ACTIVITY TRACE   │
                         │ visible + audited  │
                         └────────────────────┘
```

---

# 4. Eve is not "trained on the database"

The first implementation should **not** fine-tune a model on the complete SA Command database.

Instead Eve gets structured access to:

1. current application data;
2. system schema/domain knowledge;
3. business rules;
4. entity relationships;
5. recent operational context;
6. owner vocabulary;
7. historical examples where useful;
8. previous Eve interactions and corrections.

The model should retrieve the smallest relevant context for each request.

Example:

```text
Mamu:
"Sharma ko 3000 de diye."

            ↓

Resolve "Sharma"
            ↓
Employee records
            ↓
Find relevant current obligations
            ↓
Find active/recent production context
            ↓
Resolve payer if known
            ↓
Check duplicate/conflict
            ↓
Build plan
```

Do not dump the whole database into every model request.

---

# 5. System knowledge layer

Eve needs a machine-readable description of how SA Command works.

This should eventually cover:

## People

- employees
- roles
- employment state
- attendance
- leave
- employee finance
- communication
- assignments
- performance evidence

## Productions

- client
- event
- date
- schedule
- venue
- crew
- work
- equipment
- production finance
- billing

## Work

- tasks
- assignments
- deadlines
- progress
- blockers

## Money

- owner accounts
- contracts
- receipts
- expenses
- earnings
- salaries
- employee payments
- party charges
- party receipts
- invoices
- invoice payments
- equipment purchases/payments
- transfers
- reversals
- reconciliation

## Headquarters

- equipment
- availability
- reservations
- assignments
- returns

## Calendar / communication

- events
- meetings
- reminders
- WhatsApp communication state

This knowledge should describe **relationships and permitted actions**, not merely table names.

---

# 6. Business rules Eve must understand

Examples of rules that must remain deterministic:

```text
earned ≠ paid

employee payment ≠ employee earning

invoice ≠ direct party charge

invoice payment settles an invoice

party receipt settles a direct party charge

equipment assignment ≠ equipment purchase

creating equipment ≠ paying an invoice

production expense requires a payer

owner account movement requires explicit owner selection

billing export does not post money

formal invoice and direct charge are separate commercial tracks

existing finance posting/idempotency rules remain authoritative
```

Eve may interpret language.

Eve must not invent accounting rules.

---

# 7. Structured action plan

The model should produce a constrained action representation rather than executable SQL.

Example:

```json
{
  "intent": "EMPLOYEE_PAYMENT",
  "entities": {
    "employee": "Sharma",
    "production": "Royal Event",
    "amount": 3000,
    "payer": "AZ-2",
    "date": "2026-09-28"
  },
  "actions": [
    {
      "type": "RECORD_EMPLOYEE_PAYMENT",
      "employeeId": "...",
      "amountMinor": 300000,
      "payerAccount": "AZ-2"
    }
  ],
  "confidence": "HIGH",
  "requiresConfirmation": false
}
```

The exact DTO/schema is to be designed during implementation.

The model must never be allowed to choose arbitrary backend endpoints or generate arbitrary SQL.

---

# 8. Command gateway

Introduce an Eve-specific command boundary between model output and existing business services.

Conceptually:

```text
Eve Plan
   ↓
Eve Command Gateway
   ↓
Schema validation
   ↓
Permission validation
   ↓
Domain validation
   ↓
Existing service
   ↓
Transaction
```

Possible command families:

- `CREATE_PRODUCTION`
- `ASSIGN_EMPLOYEE`
- `CREATE_TASK`
- `UPDATE_TASK`
- `RECORD_EMPLOYEE_EARNING`
- `RECORD_EMPLOYEE_PAYMENT`
- `RECORD_EXPENSE`
- `RECORD_RECEIPT`
- `CREATE_PARTY_CHARGE`
- `RECORD_PARTY_RECEIPT`
- `CREATE_INVOICE`
- `RECORD_INVOICE_PAYMENT`
- `ASSIGN_EQUIPMENT`
- `RETURN_EQUIPMENT`
- `MARK_ATTENDANCE`
- `CREATE_MEETING`
- `CREATE_CALENDAR_EVENT`

This is illustrative. Only expose commands that already have safe canonical application/domain workflows.

---

# 9. Verification is mandatory

After execution Eve should verify the resulting system state.

Example:

```text
EXECUTE
  ↓
transaction succeeds
  ↓
reload authoritative state
  ↓
check expected changes
  ↓
check duplicate/conflict conditions
  ↓
check relevant reconciliation/invariants
  ↓
PASS / NEEDS ATTENTION
```

For a financial action, verification should include the relevant canonical finance state rather than trusting the model's output.

For equipment assignment, verify the actual reservation/allocation state.

For production changes, verify the linked crew/work/calendar/equipment records where applicable.

---

# 10. Eve activity trace

Do not use a generic:

> Loading...

or:

> Processing...

screen.

The UI should expose **structured system activity**, not hidden model chain-of-thought.

Example:

```text
EVE

Understanding
✓ Interpreted "Sharma" as Sharma Kumar
✓ Interpreted ₹3,000 as an employee payment
✓ Interpreted "Azeem" as AZ-2

Checking system
✓ Employee found
✓ Relevant production found
✓ Existing outstanding amount checked
✓ No duplicate payment found

Preparing changes
→ Creating employee payment
→ Linking payment to AZ-2
→ Updating employee balance

Verification
✓ Payment recorded
✓ Employee balance updated
✓ Owner position updated

Done
3 records changed
```

These are **observable application events**, not an attempt to expose private model reasoning.

The event model should support:

- started
- interpreted
- searching
- matched
- validating
- preparing
- executing
- verifying
- completed
- blocked
- failed
- needs-confirmation

---

# 11. Confidence and confirmation

Eve should not always execute automatically.

Initial trust model:

### Read

```
"How much does Sharma still need?"
```

Retrieve and answer.

### Prepare

```
"Pay Sharma 3000."
```

Show:

```
I will:
→ Pay Sharma ₹3,000
→ From Azeem
→ Against his outstanding payment

[Confirm]
```

### Execute

After deterministic validation and an appropriate trust policy:

```
"Paid Sharma 3k."
```

Execute directly.

### Multi-action

For:

```
"Royal event khatam ho gaya, sabka jo payment banta tha kar diya."
```

Build a multi-step plan and show the proposed changes before execution unless the policy explicitly allows direct execution.

Financially material or ambiguous actions should remain confirmation-gated until the trust model is proven.

---

# 12. Ambiguity handling

Never silently guess important identities.

Example:

```text
I found two possible people:

1. Raj Kumar
2. Raju Verma

Which one do you mean?
```

Once the owner clarifies, Eve can store the vocabulary mapping:

```text
"Raju" → Raj Kumar
```

The mapping belongs to Eve's memory/context layer, not to the foundation database unless it becomes a real business entity attribute.

---

# 13. Eve memory

Memory should start small and inspectable.

Potential memory categories:

## Vocabulary

```
"Raju" → Raj Kumar
"Royal" → Royal Event
"AK" → Akash
```

## Preferences

How the owner normally phrases or groups operational information.

## Corrections

When Eve misunderstood an entity/action and the owner corrected it.

## Recent context

Recent production, people and conversations useful for follow-up commands.

## Decisions

Explicit owner instructions that should persist.

Every stored memory should be:

- inspectable;
- editable;
- attributable;
- deletable;
- separate from canonical business data.

---

# 14. Offline-first target

Eve should be designed local-first.

Target architecture:

```text
┌──────────────────────────────────────┐
│           SA COMMAND DESKTOP         │
│                                      │
│  ┌────────────────────────────────┐  │
│  │          EVE RUNTIME           │  │
│  │                                │  │
│  │ local model                    │  │
│  │ retrieval                      │  │
│  │ system knowledge               │  │
│  │ memory                         │  │
│  │ planner                        │  │
│  │ command gateway                │  │
│  │ verification                   │  │
│  └───────────────┬────────────────┘  │
│                  │                   │
│                  ▼                   │
│           SA Command backend         │
│                  │                   │
│                  ▼                   │
│             PostgreSQL               │
└──────────────────────────────────────┘
```

The exact local model/runtime is intentionally **not chosen in this plan**.

Do not commit the project to a model vendor before evaluating:

- model quality;
- Windows performance;
- RAM/VRAM requirements;
- context length;
- structured output reliability;
- Hindi/Hinglish understanding;
- latency;
- privacy;
- licensing.

Cloud escalation may exist later as an explicit optional path, but offline/local should remain the architectural target.

---

# 15. Frontend placement

Add Eve as a top-level route immediately after Billing.

Current navigation concept:

```
Command
People
Work
Productions
Money
Calendar
Communication
Billing
Eve
```

Likely route:

```
/eve
```

Do not create a new shell.

Reuse:

```
AppShell
SA navigation
SA themes
SA typography
SA cards
SA buttons
SA overlays
Motion
TanStack Query
Lucide
```

---

# 16. Eve UI concept

Eve should feel like a **command console for the business**, not ChatGPT.

Suggested layout:

```text
┌──────────────────────────────────────────────────────────────┐
│ Eve                                      ● System Ready      │
│ SA Productions Intelligence Layer                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Tell me what happened.                                      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Aaj Royal event ka kaam khatam ho gaya...              │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│                         [ Execute ]                          │
│                                                              │
├────────────────────────────────────┬─────────────────────────┤
│ ACTIVITY                           │ CONTEXT                 │
│                                    │                         │
│ ✓ Understanding                    │ Royal Event             │
│ ✓ Found production                 │ 8 crew                  │
│ → Checking payments                │ ₹42,000 pending         │
│ → Checking equipment               │                         │
│                                    │ Today                   │
│                                    │ 3 productions           │
│                                    │ 7 payments              │
└────────────────────────────────────┴─────────────────────────┘
```

Important UX characteristics:

- calm;
- dense enough to be useful;
- large input;
- visible action trace;
- contextual side panel;
- clear confirmation state;
- no fake "AI magic";
- no giant chat bubble UI;
- no generic assistant avatar dominating the screen.

---

# 17. UI component sourcing

The repository already establishes an open-source-first strategy.

Primary visual reference:

**Aceternity UI**

https://ui.aceternity.com/components

Use it selectively for patterns such as:

- Bento Grid;
- Floating Dock;
- Animated Tabs;
- Animated Modal;
- Stateful Button;
- Expandable Card;
- restrained Card Hover behavior.

Aceternity's current library is React + Tailwind + Motion and provides copy/paste components; use the geometry/interaction patterns and adapt them to SA Command rather than importing a new visual language. citeturn0search0turn0search1

Secondary microinteraction reference:

**CodeFronts**

https://codefronts.com/motion/

Use only for small CSS interaction ideas:

- subtle elevation;
- status transitions;
- loaders;
- segmented controls;
- tooltips;
- progress treatment.

CodeFronts remains a microinteraction reference, not a second design system.

Do not add a new animation runtime.

Do not introduce:

- neon;
- particles;
- aurora;
- perpetual glowing;
- excessive gradients;
- decorative 3D;
- large parallax;
- bouncing assistant characters;
- gimmicky cursor effects.

---

# 18. Component reuse priority

Before building a new Eve UI primitive:

1. Reuse an existing SA Command component.
2. Reuse/adapt an existing component pattern already present in the repository.
3. Check the documented Aceternity / CodeFronts references.
4. Check existing Radix primitives.
5. Build locally only when no appropriate primitive exists.

Any copied source must follow `docs/THIRD_PARTY.md` provenance rules.

Do not vendor an entire component library into the repository just for Eve.

---

# 19. Backend shape

Initial backend organization should likely live under an Eve feature/domain rather than spreading AI code through unrelated domains.

Conceptual package:

```
.../eve/
  EveController
  EveService
  EveContextService
  EveRetrievalService
  EvePlanner
  EveCommandGateway
  EveVerificationService
  EveMemoryService
  EveTraceService
  api/
  domain/
```

Exact package names should follow the repository's current backend conventions after inspecting the existing feature/service structure.

The planner must remain separated from execution.

---

# 20. API concept

Initial API surface should be small.

Possible:

```
POST /api/v1/eve/sessions
POST /api/v1/eve/sessions/{id}/messages

GET  /api/v1/eve/sessions/{id}
GET  /api/v1/eve/sessions/{id}/events

POST /api/v1/eve/plans/{id}/confirm
POST /api/v1/eve/plans/{id}/reject

GET  /api/v1/eve/memory
PATCH /api/v1/eve/memory/{id}
DELETE /api/v1/eve/memory/{id}
```

Do not implement this API until the existing backend conventions and authentication/audit model are inspected.

---

# 21. Data model principles

Do not duplicate business data.

Eve-specific persistence should contain only Eve concerns, such as:

- sessions;
- messages;
- structured plans;
- execution traces;
- memory;
- model/runtime metadata;
- confirmation decisions;
- errors.

Business records remain in existing tables.

Every Eve-created business mutation must remain traceable back to:

```
Eve session
→ Eve plan
→ command
→ canonical business transaction/entity
```

For financial operations, the canonical finance transaction remains the source of truth.

---

# 22. Safety boundaries

Eve must not:

- execute arbitrary SQL;
- invent employees;
- invent productions;
- invent payments;
- infer an owner account silently for a material transaction;
- bypass finance posting services;
- bypass equipment reservation logic;
- mutate data because of low-confidence language;
- modify canonical business records merely to make its response look correct;
- expose private model chain-of-thought;
- claim an action succeeded without verification.

When uncertain:

```
ASK
```

When validation fails:

```
BLOCK
```

When execution succeeds:

```
VERIFY
```

---

# 23. Auditability

Every executed Eve action should be explainable after the fact.

Minimum trace:

```
who initiated
when
input
interpreted entities
planned commands
confirmation state
executed commands
affected canonical records
verification result
failure/block reason
```

The owner should be able to inspect:

> "Why did Eve create this payment?"

and see a concise operational trace based on actual system events.

---

# 24. Development phases

## Phase E0 — Repository reconnaissance

Before coding:

- inspect current AppShell/navigation;
- inspect current domain service APIs;
- inspect finance command/posting paths;
- inspect production/work/people/HQ mutation paths;
- inspect auth and audit behavior;
- inspect existing tests;
- inspect current UI primitives;
- inspect existing third-party provenance rules.

**Deliverable:** implementation map with exact reuse points.

No feature code yet.

---

## Phase E1 — Eve shell + read-only intelligence

Build:

- /eve route;
- Eve page;
- input/composer;
- session UI;
- activity trace UI;
- context panel;
- read-only retrieval;
- basic structured intent detection.

First useful interaction:

> "How much does Sharma still need?"

No mutations.

Goal: prove Eve can understand and retrieve existing data.

---

## Phase E2 — Plan mode

Add:

- structured action plan;
- entity resolution;
- validation preview;
- confirmation UI;
- deterministic activity events.

Example:

```
User
 ↓
Eve understands
 ↓
Plan
 ↓
Show changes
 ↓
Confirm
```

No automatic financial mutation without confirmation.

---

## Phase E3 — Safe command execution

Connect Eve to existing domain services.

Start with low-risk operational actions:

- production assignment;
- task creation/update;
- calendar actions;
- equipment assignment where existing workflow is authoritative.

Then carefully introduce financial actions.

---

## Phase E4 — Financial command execution

Financial actions must use canonical finance services.

Initial candidates:

- employee payment;
- employee earning;
- production expense;
- production receipt;
- party receipt;
- invoice payment.

Every financial action:

```
plan
→ validation
→ confirmation
→ canonical posting
→ verification
→ trace
```

No separate Eve ledger.

---

## Phase E5 — Memory

Add:

- owner vocabulary;
- corrections;
- recent context;
- explicit preferences;
- inspectable memory UI.

Memory must never silently rewrite canonical business records.

---

## Phase E6 — Local/offline runtime

Evaluate and integrate the local model/runtime only after the application-level Eve architecture works with a replaceable model provider.

Provider boundary:

```
EvePlanner
    ↓
ModelProvider
    ├── LocalProvider
    └── OptionalCloudProvider
```

The rest of Eve should not know which model is running.

---

## Phase E7 — Proactive Eve

Only after trust is established.

Examples:

> "Royal Event is marked complete but 4 equipment items are still reserved."

> "Sharma has an outstanding payment that wasn't settled after today's production."

> "This production has no assigned crew for tomorrow."

Proactive behavior must create **suggestions**, not silent mutations.

---

# 25. Testing strategy

Eve needs tests at four levels.

## Planner tests

Natural-language input → structured plan.

Include:

- English;
- Hindi;
- Hinglish;
- short statements;
- multi-action statements;
- ambiguous names;
- colloquial money language;
- dates like "today", "kal", "parso".

## Gateway tests

Plan → allowed/blocked command.

Verify:

- invalid entities;
- missing required payer;
- invalid financial state;
- duplicate command;
- unsupported command;
- unauthorized action.

## Integration tests

Eve command → real PostgreSQL → existing canonical services.

Especially:

- employee payments;
- finance balances;
- equipment reservation;
- production assignment;
- task updates.

## UI tests

Verify:

- trace events;
- confirmation;
- blocked states;
- errors;
- loading/empty states;
- reduced motion;
- keyboard access.

---

# 26. Acceptance examples

### Example A — employee payment

Input:

> "Sharma ko 3000 de diye, Azeem ne."

Eve should:

1. resolve Sharma;
2. resolve Azeem/AZ-2;
3. identify relevant employee payment context;
4. detect ambiguity if more than one Sharma exists;
5. produce a structured payment command;
6. use canonical employee-payment/finance services;
7. verify the resulting balance;
8. show the activity trace.

---

### Example B — production assignment

Input:

> "Raju ko kal Royal event pe bhej dena."

Eve should:

1. resolve Raju;
2. resolve Royal Event;
3. interpret "kal" using application timezone/date semantics;
4. check assignment/scheduling conflicts;
5. prepare assignment;
6. ask for clarification if conflicts or identity ambiguity exist;
7. execute through existing production/work services;
8. verify assignment.

---

### Example C — ambiguous person

Input:

> "Raju ko 5000 diye."

If two Rajus exist:

```
I found two possible people:

1. Raj Kumar
2. Raju Verma

Which one do you mean?
```

No mutation.

---

### Example D — unsupported shortcut

Input:

> "Azeem ka account 10 hazaar badha do."

Eve should not invent a finance posting.

It should ask what real business event caused the movement or route the owner to the appropriate canonical workflow.

---

# 27. What Eve should eventually feel like

Not:

> "Ask AI anything."

Instead:

> **"Tell Eve what happened."**

The owner describes the day's work in normal language.

Eve turns that into the structured operational record the application already knows how to maintain.

The system should feel like:

```
Mamu speaks naturally
        ↓
Eve understands
        ↓
Eve checks the real system
        ↓
Eve shows what it intends to change
        ↓
SA Command executes the change
        ↓
Eve verifies it
        ↓
Everything remains auditable
```

---

# 28. Definition of success

Eve is successful when Mamu no longer needs to learn:

- which screen contains employee payments;
- where to create a production;
- where to assign a person;
- where to record an expense;
- how to find an outstanding payment;
- which finance tab contains a receipt;
- which fields must be filled in which order.

Instead he can describe what happened.

**But the underlying SA Command system remains deterministic, auditable and trustworthy.**

That is the point of Eve.

---

# 29. Explicit non-goals for the first Eve release

Do not build:

- a general-purpose ChatGPT clone;
- autonomous unrestricted agent loops;
- direct database/SQL agent;
- model fine-tuning pipeline;
- employee surveillance;
- automatic financial decisions;
- automatic financial transfers without governed commands;
- generic RAG over every database table;
- a second accounting system;
- a second CRM;
- a voice assistant in the first release;
- animated AI avatars;
- decorative 3D/particle effects;
- cloud-only architecture.

---

# 30. First implementation instruction

Before writing Eve code, inspect the existing repository and produce a concrete reuse map for:

1. navigation and routing;
2. existing UI primitives;
3. current People/Employee 360 queries;
4. production creation and assignment services;
5. Work/task commands;
6. Headquarters/equipment reservation commands;
7. Finance posting commands;
8. Billing commands;
9. audit infrastructure;
10. authentication/owner permissions;
11. existing test patterns.

Then implement the smallest vertical slice:

```
Natural language
→ retrieve
→ understand
→ show structured plan
→ confirm
→ execute one existing safe command
→ verify
→ show trace
```

Do not build the full autonomous Eve in one pass.

---

## Current design references

- SA Command engineering rules: `AGENTS.md`
- SA Command V1 architecture/design source of truth: `PLAN.md`
- Third-party provenance: `docs/THIRD_PARTY.md`
- Current application shell: `apps/desktop/src/components/layout/AppShell.tsx`
- Current UI primitives: `apps/desktop/src/components/ui/sa.tsx`
- Current router: `apps/desktop/src/app/App.tsx`
- Command dashboard: `apps/desktop/src/features/command/`
- Finance: `apps/desktop/src/features/finance/`
- Billing: `apps/desktop/src/features/billing/`
- Productions: `apps/desktop/src/features/productions/`
- Employees: `apps/desktop/src/features/employees/`
- Work: `apps/desktop/src/features/work/`
- Headquarters: `apps/desktop/src/features/headquarters/`

External UI references:

- Aceternity UI: https://ui.aceternity.com/components
- CodeFronts Motion: https://codefronts.com/motion/
**Eve must extend the current SA Command architecture. It must not replace it.**
