# EVE — Five-Phase Implementation Plan

> **Status:** Implementation design  
> **Product:** SA Command  
> **Feature:** EVE  
> **Placement:** Top-level navigation, immediately after Billing  
> **Primary operator:** SA Productions owner (Mamu)

EVE is the intelligence layer of SA Command.

It is a **new, separate section** inside the existing application. It must understand the connected system, maintain its own intelligence/context layer, surface evidence-backed suggestions, and translate Mamu's natural-language instructions into governed operations.

EVE does **not** replace People, Work, Productions, Headquarters, Finance, Billing, Calendar, Communication, Payroll or Command.

The existing SA Command domains remain the source of truth and the final authority for business rules, persistence, transactions, permissions and canonical financial state.

The guiding model is:

```
Mamu
  ↓
EVE
  ↓
Understand → Retrieve → Plan → Show
  ↓
Mamu decides / confirms
  ↓
Existing SA Command services
  ↓
PostgreSQL
  ↓
Verify
  ↓
EVE updates its intelligence/context
  ↓
Trace + result
```

---

# 1. What EVE is becoming

The original EVE idea is larger than a chatbot.

Mamu should eventually be able to describe what happened in ordinary language:

> "Aaj Sharma ko Royal event pe bheja tha, usko 3,000 diye aur Azeem ne payment ki."

or:

> "Royal event ka kaam khatam ho gaya, sabko jo payment banta tha kar diya."

EVE should understand that statement in the context of the actual SA Command system.

That means EVE must be able to understand:

- people;
- productions;
- work/tasks;
- equipment;
- schedules;
- calendar;
- communication;
- finance;
- billing;
- relationships between those domains;
- current operational state;
- relevant history;
- the owner's vocabulary and corrections.

EVE should then be able to:

```
understand
→ find real entities
→ inspect current state
→ determine what the statement means
→ identify missing/ambiguous information
→ prepare an explicit action plan
→ show Mamu what will happen
→ execute only through governed SA Command workflows
→ verify the resulting state
→ update EVE's own contextual/semantic knowledge
→ surface future suggestions
```

---

# 2. The most important boundary

## EVE is the heart of the system, but not the system of record

"Heart" means EVE develops the system-wide understanding.

It does **not** mean:

- EVE owns business data;
- EVE becomes a second ERP;
- EVE maintains a second finance ledger;
- EVE directly writes PostgreSQL;
- EVE silently performs financial activity;
- EVE replaces existing domain services;
- EVE can override business rules;
- EVE can invent entities or accounting semantics.

The architecture remains:

```
                         SA COMMAND
        ┌──────────────────────────────────────┐
        │ People                               │
        │ Work                                 │
        │ Productions                          │
        │ Headquarters / Equipment             │
        │ Finance                              │
        │ Billing                              │
        │ Calendar                             │
        │ Communication / Meetings             │
        │ Payroll                              │
        │ Command                              │
        │                                      │
        │ PostgreSQL = canonical truth         │
        └──────────────────┬───────────────────┘
                           │
                     governed access
                           │
                           ▼
                    ┌───────────────┐
                    │      EVE      │
                    │               │
                    │ Context       │
                    │ Retrieval     │
                    │ Knowledge     │
                    │ Memory        │
                    │ Planning      │
                    │ Suggestions   │
                    │ Command Gate  │
                    │ Verification  │
                    └───────┬───────┘
                            │
                            ▼
                         MAMU
                decides / confirms action
```

The existing application must continue to work normally even if EVE is unavailable.

---

# 3. Integration philosophy

EVE gets its **own product surface**.

Frontend:

```
/eve
apps/desktop/src/features/eve/
```

Navigation:

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

EVE should have its own:

- page;
- session experience;
- activity trace;
- contextual panel;
- plan presentation;
- confirmation experience;
- memory controls;
- suggestions;
- EVE-specific API/domain code;
- EVE persistence;
- knowledge;
- evaluation fixtures;
- prompts.

But it must live **inside the same SA Command shell**.

Reuse the current application shell, design language, accessibility behavior, state/query conventions and backend architecture.

No second runnable application.

No second shell.

No second business database.

No second ledger.

---

# 4. Five-phase development model

EVE will be developed and completed in exactly **five phases**.

| Phase | Name | Main outcome |
|---|---|---|
| **1** | Foundation & System Understanding | EVE exists as a separate section and gains a grounded model of the actual SA Command graph |
| **2** | Conversational Intelligence | Mamu can talk naturally to EVE; EVE can retrieve, understand and explain current system state |
| **3** | Governed Execution | EVE converts instructions into inspectable plans and executes only through canonical services after Mamu's decision |
| **4** | Continuous Intelligence & Suggestions | EVE stays synchronized with system changes, learns safe context/vocabulary and surfaces evidence-backed suggestions |
| **5** | Local-First Production Hardening | EVE becomes a stable, private, model-agnostic intelligence layer with evaluation and production hardening |

Each phase is a complete milestone.

Do not allow unrelated scope to leak from a later phase into an earlier one.

---

# Phase 1 — Foundation & System Understanding

## Goal

Build EVE as a genuinely separate section and teach it how **SA Command itself works** before giving it material execution authority.

This phase answers:

> "What is the actual system graph, and how does EVE understand it?"

## 1.1 Repository reconnaissance

Before implementation, inspect the real repository and document exact reuse points for:

### Frontend

- router;
- AppShell/navigation;
- existing SA UI primitives;
- modal/drawer patterns;
- TanStack Query setup;
- state-management conventions;
- test helpers;
- feature API conventions.

### Backend

- package layout;
- service/application boundaries;
- authentication/owner context;
- audit infrastructure;
- idempotency mechanisms;
- migration conventions;
- People/employee finance;
- Productions;
- Work/tasks;
- Headquarters/equipment;
- Finance;
- Billing;
- Calendar/communication where relevant.

No guessed class names or guessed method names.

## 1.2 EVE feature boundary

Add only EVE-specific frontend/backend/database code.

Target frontend:

```
apps/desktop/src/features/eve/
```

Target route:

```
/eve
```

Potential frontend structure:

```
EvePage.tsx
EvePage.test.tsx
eve.api.ts
eve.types.ts

components/
  EveComposer.tsx
  EveSessionList.tsx
  EveActivityTrace.tsx
  EveContextPanel.tsx
  EvePlanCard.tsx
  EveConfirmation.tsx
  EveMemoryPanel.tsx
  EveSuggestionCard.tsx
```

The exact structure may change after repository reconnaissance.

## 1.3 EVE persistence

Add EVE tables only for EVE concerns.

Potential concerns:

- sessions;
- messages;
- plans;
- commands;
- traces;
- confirmations;
- memory;
- suggestions;
- model/runtime metadata;
- correlation/error metadata.

Do not copy canonical Employee, Production, Finance, Equipment or Billing records into an EVE-owned source of truth.

## 1.4 EVE contracts

Define typed contracts for the intelligence boundary:

```
EveSession
EveMessage
EveContext
EveResolution
EvePlan
EvePlanAction
EveCommand
EveCommandResult
EveTraceEvent
EveMemory
EveSuggestion
EveVerificationResult
```

The model's output must be schema constrained.

The exact DTO design must follow the existing backend conventions.

## 1.5 System Knowledge

Create the initial machine-readable system knowledge under:

```
eve/knowledge/
```

Knowledge should describe:

- domains;
- entities;
- relationships;
- commands;
- constraints;
- required fields;
- side effects;
- verification expectations;
- important business semantics;
- terminology.

It must describe **behavior and relationships**, not just table names.

Example:

```
Employee
  ├── attendance
  ├── leave
  ├── work assignments
  ├── earnings
  └── payments

Production
  ├── client/party
  ├── schedule
  ├── venue
  ├── crew
  ├── tasks
  ├── equipment
  ├── communication
  └── financial activity
```

## 1.6 Context Engine

EVE needs a bounded context envelope that can include:

- current date/time;
- canonical application timezone;
- authenticated owner/user context;
- current route;
- current production/record;
- recent relevant actions;
- recent conversation;
- relevant canonical records;
- relevant EVE memory;
- recent system changes.

Context is not permission.

Context is not truth.

Before material mutation, mutable canonical state must be reloaded.

## 1.7 Retrieval foundation

Initial retrieval order:

```
exact identifier
→ normalized exact match
→ bounded DB search
→ bounded candidate set
→ model-assisted selection among candidates
```

Deterministic canonical retrieval comes first.

A semantic/vector layer may later improve recall, but it must not replace canonical identity/state lookup.

## 1.8 Phase 1 vertical slice

First useful interaction:

> "How much does Sharma still need?"

Flow:

```
natural language
→ interpret
→ resolve employee
→ retrieve current authoritative state
→ answer
→ record truthful trace
```

No write execution is required in Phase 1.

## Phase 1 exit criteria

```
✓ /eve exists
✓ EVE is separated from existing feature surfaces
✓ actual repository reuse map is documented
✓ EVE contracts exist
✓ initial system knowledge exists
✓ context engine exists
✓ deterministic retrieval works
✓ one grounded read flow works
✓ truthful trace works
✓ no LLM-to-SQL
✓ no arbitrary tool execution
✓ tests pass
```

---

# Phase 2 — Conversational Intelligence

## Goal

Make EVE useful for ordinary operational conversation while remaining read-oriented.

This is where EVE should begin feeling like the system's intelligence rather than another search page.

## 2.1 Model provider abstraction

Introduce an EVE-owned model boundary:

```
ModelProvider
├── LocalModelProvider
├── OptionalCloudModelProvider
└── TestModelProvider
```

The rest of EVE does not depend directly on a model vendor SDK.

The provider must handle safely:

- timeout;
- unavailable model;
- malformed output;
- context overflow;
- unsupported input;
- low-confidence interpretation.

## 2.2 Conversational interpretation

EVE should interpret natural language into a bounded internal representation:

- intent;
- entities;
- dates;
- amounts;
- relationships;
- required context;
- ambiguity state;
- answer requirements.

Example:

> "Royal ka kya pending hai?"

EVE should understand that "Royal" may refer to the relevant production and gather the relevant current state across connected domains.

## 2.3 Cross-system retrieval

EVE should become capable of grounded questions such as:

> "Royal event mein kaun gaya tha?"

> "Us production ka equipment kya hai?"

> "Sharma ka kitna payment pending hai?"

> "Client ne payment kiya ya nahi?"

> "Kaunsa task abhi open hai?"

The important property is that answers come from actual SA Command records and relationships.

## 2.4 Retrieval router

Borrow useful architectural patterns from mature open-source local/RAG systems where appropriate.

Potential retrieval paths:

```
structured DB retrieval
keyword retrieval
semantic retrieval
relationship/graph retrieval
EVE memory
```

The router must be bounded by:

- result limits;
- context limits;
- deadlines;
- deterministic fallbacks.

Do not add a retrieval system simply because it is fashionable.

Use it where it improves actual EVE behavior.

## 2.5 Owner language and memory

EVE begins remembering safe operator vocabulary:

```
"Raju" → Raj Kumar
"Royal" → Royal Event
"AK" → Akash
```

Memory must remain:

- inspectable;
- attributable;
- timestamped;
- editable;
- deletable.

Memory is a hint.

It cannot override current canonical identity or current system state.

## 2.6 EVE UI

The /eve screen should feel like a business command console.

Core surfaces:

- large natural-language composer;
- active session;
- conversation/result area;
- activity trace;
- context panel;
- referenced records;
- memory;
- suggestions;
- clear blocked/ambiguous/unavailable states.

Do not build:

- generic ChatGPT-style bubbles as the primary interaction;
- large AI avatars;
- fake thinking animations;
- neon AI graphics;
- decorative 3D.

The activity trace is central.

## Phase 2 exit criteria

```
✓ natural-language reads work
✓ cross-domain retrieval works
✓ current-context follow-ups work
✓ ambiguity results in clarification
✓ owner vocabulary can be remembered
✓ retrieved evidence/context is visible
✓ provider abstraction works
✓ bounded retrieval is tested
✓ no mutation is hidden inside read flows
```

---

# Phase 3 — Governed Execution

## Goal

EVE becomes an operational command interface.

Mamu remains in control.

EVE interprets and proposes.

The existing SA Command application decides what is valid and performs the real business mutation.

## 3.1 Command registry

Only explicitly registered commands may execute.

Each command declares:

- command type;
- input schema;
- required entities;
- risk level;
- confirmation policy;
- canonical executor;
- verification procedure;
- audit metadata.

No:

```
execute(anything)
```

## 3.2 Structured plan

Example:

User:

> "Sharma ko 3000 de do."

EVE:

```
Understanding
✓ Employee candidate identified

Checking system
✓ Current outstanding state retrieved
✓ Payer requirement checked

Plan
1. Resolve employee
2. Resolve payer
3. Prepare employee-payment command
4. Validate current state

Requires Mamu confirmation
```

The model may propose.

The model may not directly invoke arbitrary business behavior.

## 3.3 Command Gateway

The hard AI/business boundary:

```
EvePlan
   ↓
schema validation
   ↓
entity validation
   ↓
permission validation
   ↓
domain validation
   ↓
confirmation policy
   ↓
existing SA Command service
   ↓
transaction
```

## 3.4 Mamu control

The intended lifecycle is:

```
Mamu describes event
        ↓
EVE understands
        ↓
EVE retrieves
        ↓
EVE prepares exact plan
        ↓
Mamu sees proposed effects
        ↓
Mamu confirms
        ↓
canonical service executes
        ↓
EVE verifies
```

Mamu is the final operator for material actions.

## 3.5 First command

Choose the first write command based on the **actual implementation map from Phase 1**.

Start with one low-risk canonical workflow.

Required flow:

```
plan
→ gateway
→ canonical service
→ PostgreSQL
→ reload
→ verify
→ audit
→ trace
```

## 3.6 Financial command boundary

Finance is introduced only after the command gateway and verification pattern are proven.

For financial actions:

- target must be explicit;
- payer/receiver/source/destination must be explicit when required;
- current settlement state must be refreshed;
- canonical finance services must execute;
- idempotency must be preserved;
- Mamu confirmation is required initially;
- verification is mandatory.

Never manually set a balance.

Never create an EVE ledger.

Never invent payer/account semantics.

## 3.7 Multi-action plans

Later in this phase, support bounded multi-action statements such as:

> "Royal event khatam ho gaya, sabko jo payment banta tha kar diya."

EVE must expand this into individually inspectable actions.

For partial success:

```
3 succeeded
1 failed
```

Never show:

```
Completed
```

when the operation was only partially completed.

## 3.8 Confirmation integrity

Confirmation binds to:

- session ID;
- plan ID;
- plan version/hash.

If important mutable state changes before confirmation:

```
revalidate
or
invalidate and rebuild plan
```

A stale plan cannot execute.

## Phase 3 exit criteria

```
✓ command registry exists
✓ structured plans exist
✓ confirmation is plan-bound
✓ one canonical write workflow works
✓ verification uses authoritative state
✓ audit linkage works
✓ financial writes are confirmation-gated
✓ duplicate material effects are prevented
✓ multi-action execution is bounded
✓ failure and partial-success states are explicit
```

---

# Phase 4 — Continuous Intelligence & Suggestions

## Goal

EVE now becomes continuously aware of how the system changes.

This is where the "heart of the system" idea becomes operational.

After every relevant canonical change, EVE updates the parts of its own intelligence/context layer that are affected.

## 4.1 Post-change synchronization

Conceptual flow:

```
Canonical SA Command mutation
          ↓
post-change signal/event
          ↓
EVE synchronization
          ↓
affected entity/context refresh
          ↓
semantic/relationship index refresh where needed
          ↓
stale retrieval invalidation
          ↓
suggestion context updated
```

This does **not** mean copying every business row into a second EVE database.

EVE maintains derived intelligence only where it provides value.

## 4.2 What changes EVE should learn from

Examples:

### New production

```
Production created
→ EVE learns production identity/relationships
```

### Crew assignment

```
Employee assigned
→ production ↔ employee relationship updated
```

### Equipment reservation

```
Equipment reserved
→ production ↔ equipment relationship updated
→ availability-related context refreshed
```

### Payment

```
Employee payment recorded
→ employee finance context refreshed
→ relevant production context refreshed
```

### Production completion

```
Production completed
→ completion state becomes relevant to
   tasks
   equipment
   payments
   billing
   calendar
```

EVE should update **only affected knowledge/context**, not rebuild the entire world after every click.

## 4.3 Evidence-backed suggestions

EVE can now surface observations such as:

> "Royal Event is marked complete, but 4 equipment items are still reserved."

> "Sharma has ₹4,000 outstanding across completed productions."

> "Invoice INV-104 is overdue and the related production is already complete."

Every suggestion needs evidence from authoritative records.

## 4.4 Cross-domain reasoning

EVE should understand conditions such as:

```
production completed
+
equipment still reserved
```

or:

```
employee assigned
+
payment outstanding
```

or:

```
invoice issued
+
no settlement
```

or:

```
task overdue
+
production approaching
```

or:

```
crew scheduled
+
attendance conflict
```

The point is not to calculate these in a second ERP.

The point is for EVE to notice relationships across the existing system.

## 4.5 Proactive behavior

The first proactive form is always:

```
observe
→ detect
→ collect evidence
→ suggest
→ Mamu decides
```

Never:

```
observe
→ mutate silently
```

Suggestions are:

- dismissible;
- rate-limited;
- deduplicated;
- evidence-backed;
- non-mutating.

## Phase 4 exit criteria

```
✓ EVE reacts to relevant canonical changes
✓ stale intelligence/context is refreshed
✓ relationship/semantic indexing updates safely
✓ evidence-backed suggestions work
✓ suggestions are dismissible
✓ repeated suggestions are rate-limited
✓ cross-domain conditions can be detected
✓ proactive EVE never silently mutates
✓ synchronization is integration-tested
```

---

# Phase 5 — Local-First Production Hardening

## Goal

Turn EVE into a stable, private, portable intelligence layer that is ready for sustained real-world use.

## 5.1 Local-first architecture

Target:

```
SA Command Desktop
       ↓
local EVE runtime
       ├── model
       ├── retrieval
       ├── system knowledge
       ├── memory
       ├── planner
       ├── command gateway
       └── verification
       ↓
SA Command backend
       ↓
PostgreSQL
```

The local intelligence runtime never bypasses the governed business boundary.

No local model gets direct business SQL authority.

## 5.2 Local model selection

Do not hard-code a model before evaluating actual requirements.

Evaluate:

- Windows performance;
- RAM/VRAM usage;
- latency;
- context length;
- structured output reliability;
- English;
- Hindi;
- Hinglish;
- entity resolution;
- planning quality;
- privacy;
- licensing.

The exact model is a Phase 5 engineering decision, not a Phase 1 architectural assumption.

## 5.3 Borrowing from open-source AI systems

EVE may selectively borrow proven architectural ideas from systems such as:

### AnythingLLM

Useful inspiration:

- local-first model/provider handling;
- session/workspace patterns;
- local retrieval infrastructure;
- model portability;
- self-hosted/privacy-oriented operation.

### DocMind AI

Useful inspiration:

- retrieval routing;
- hybrid semantic + keyword retrieval;
- graph/relationship retrieval;
- bounded multi-agent orchestration;
- explicit deadlines;
- deterministic test fixtures;
- evaluation-oriented retrieval pipelines.

These projects should be treated as **architectural references and component sources where licensing/provenance permits**, not as foundations that replace SA Command's architecture.

Do not transplant their full application stacks into this repository.

## 5.4 Evaluation harness

Create EVE evaluation suites under:

```
eve/evals/
eve/fixtures/
```

Cover:

- entity resolution;
- English;
- Hindi;
- Hinglish;
- colloquial language;
- date interpretation;
- amount interpretation;
- retrieval;
- relationship reasoning;
- ambiguity;
- planning;
- confirmation;
- permissions;
- financial safety;
- idempotency;
- stale plans;
- verification;
- prompt injection;
- provider failures;
- partial multi-action execution;
- suggestion quality.

Core evaluation must work against deterministic fixtures and a deterministic test provider.

A live model must not be the only way to prove EVE's correctness.

## 5.5 Security hardening

Verify:

- no secrets in model context;
- least privilege;
- no arbitrary SQL;
- no arbitrary HTTP;
- no shell;
- no generic execution endpoint;
- no unrestricted loops;
- no silent cloud fallback for sensitive context;
- sanitized logs;
- prompt-injection resistance;
- safe provider failure;
- no unnecessary transcript retention.

## 5.6 Observability and reconstruction

A material EVE operation should be reconstructable as:

```
Mamu / user
   ↓
EVE session
   ↓
message
   ↓
resolved entities
   ↓
plan
   ↓
confirmation
   ↓
command
   ↓
canonical record/transaction
   ↓
verification
   ↓
result
```

The visible activity trace should correspond to real application events.

Never display:

```
✓ Payment recorded
```

before the actual payment has been verified.

## 5.7 Production readiness

Phase 5 is complete when:

```
✓ phases 1–4 remain green
✓ local-first runtime works
✓ provider abstraction is stable
✓ deterministic provider works
✓ evaluation suite passes
✓ integration tests pass
✓ security tests pass
✓ audit linkage is reconstructable
✓ financial invariants remain canonical
✓ verification is authoritative
✓ suggestions never mutate
✓ Windows/Tauri path is validated
✓ accessibility works
✓ reduced-motion behavior works
✓ failure states are terminal and understandable
✓ no arbitrary execution escape hatch exists
```

---

# 6. Permanent EVE rules

These apply across all five phases.

## 6.1 Authority

EVE may interpret.

SA Command decides what is valid.

Canonical services execute.

PostgreSQL remains canonical.

Verification decides whether the expected result actually exists.

## 6.2 Entity resolution

The order remains:

```
exact ID
→ exact/normalized name
→ bounded candidate search
→ model-assisted candidate selection
```

No safe candidate means no mutation.

Ambiguity blocks material execution.

## 6.3 Finance

Preserve all canonical distinctions:

```
earned ≠ paid
employee earning ≠ employee payment
invoice ≠ direct party charge
invoice payment → invoice settlement
party receipt → direct charge settlement
equipment assignment ≠ purchase/payment
billing export ≠ money posting
```

EVE never implements replacement accounting rules.

## 6.4 Memory

EVE memory is not business truth.

Memory can help interpret language.

Memory cannot silently rewrite canonical records.

Memory must be inspectable, editable and deletable.

## 6.5 Activity trace

The trace is not model chain-of-thought.

It is a truthful event stream such as:

```
STARTED
INTERPRETING
SEARCHING
MATCHED
VALIDATING
PLANNING
WAITING_CONFIRMATION
EXECUTING
VERIFYING
COMPLETED
BLOCKED
FAILED
```

## 6.6 Failure handling

Use explicit terminal outcomes:

```
CLARIFICATION_REQUIRED
POLICY_BLOCKED
VALIDATION_FAILED
STALE_PLAN
EXECUTION_FAILED
VERIFICATION_FAILED
MODEL_FAILED
SYSTEM_UNAVAILABLE
NEEDS_ATTENTION
```

Never convert uncertainty or failure into a successful-looking response.

---

# 7. What "EVE knows everything" means

The target is **not**:

> "Put every row in the database inside an LLM."

The target is:

> EVE can construct a current, bounded understanding of whatever part of SA Command is relevant to the owner's request.

For a production, EVE should be able to reason about:

```
client
date
schedule
venue
crew
tasks
equipment
calendar
communication
financial state
billing state
```

For an employee:

```
identity
role/state
attendance
leave
assignments
earnings
payments
relevant productions
```

For finance:

```
owner accounts
earnings
payments
expenses
receipts
party charges
invoices
settlements
transfers
reversals
reconciliation
```

And importantly, it should understand the **relationships** between these areas.

That is what makes EVE the heart of the system.

---

# 8. Example final user experience

Mamu enters EVE and says:

> "Aaj Royal event khatam ho gaya. Sharma ko 3 hazaar de diye Azeem ne."

EVE should conceptually move through:

```
UNDERSTANDING

✓ Royal → Royal Event
✓ Sharma → canonical employee
✓ "aaj" → application date
✓ ₹3,000 → employee payment
✓ Azeem → canonical payer candidate

CHECKING SYSTEM

✓ Production found
✓ Employee context found
✓ Current payment state checked
✓ Payer requirement checked
✓ No duplicate payment found

PLAN

→ Mark/interpret production completion only if requested and supported
→ Record employee payment through canonical workflow
→ Attribute payer explicitly

MAMU DECISION

[Confirm changes]

EXECUTION

→ Canonical production workflow
→ Canonical employee-payment workflow

VERIFICATION

✓ Expected production state verified
✓ Payment verified
✓ Relevant balance/state verified
✓ No duplicate financial effect

EVE UPDATE

✓ Relevant production context refreshed
✓ Sharma finance context refreshed
✓ Payer context refreshed

DONE

2 business actions verified
EVE context updated
```

The exact actions depend on what Mamu actually said and what the canonical domain services support. EVE must never invent missing facts.

---

# 9. Final architecture

The intended final system is:

```
                    ┌─────────────────────┐
                    │        MAMU         │
                    │   natural language  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │        EVE          │
                    │                     │
                    │ Interaction         │
                    │ Context             │
                    │ Retrieval           │
                    │ System Knowledge    │
                    │ Memory              │
                    │ Planner             │
                    │ Suggestions         │
                    │ Command Gateway     │
                    │ Verification        │
                    │ Activity Trace      │
                    └──────────┬──────────┘
                               │
                   governed / typed boundary
                               │
                               ▼
               ┌──────────────────────────────┐
               │     EXISTING SA COMMAND      │
               │                              │
               │ People                       │
               │ Work                         │
               │ Productions                  │
               │ Headquarters                 │
               │ Finance                      │
               │ Billing                      │
               │ Calendar                     │
               │ Communication / Payroll      │
               └──────────────┬───────────────┘
                              │
                              ▼
                       PostgreSQL
                    canonical business truth
```

The permanent relationship is:

**EVE = intelligence**

**SA Command = system of record**

**Mamu = authority over material execution**

That separation is what lets EVE become deeply integrated without contaminating the system that has already been built.
