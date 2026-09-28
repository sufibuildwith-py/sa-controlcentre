# EVE — SA Command Architecture

Implementation architecture for `eve/eve_plan.md`. Eve extends SA Command; it does not replace or duplicate it.

## 1. Position

Mamu → Eve UI/session → context + retrieval + memory + system knowledge → model provider → structured plan → command gateway → existing SA Command services → PostgreSQL → verification → observable trace.

Existing SA Command domains remain authoritative for business rules, persistence, transactions, finance, payroll, production, equipment, billing, work and audit.

## 2. Repository boundary

All Eve-specific documentation, knowledge and evaluation material lives under:

```
eve/
├── eve_plan.md
├── architecture.md
├── README.md
├── knowledge/
├── prompts/
├── evals/
├── fixtures/
└── decisions/
```

The existing application remains under `apps/backend/` and `apps/desktop/`. Do not create a second runnable app, database or business ledger.

## 3. Reuse map

Current desktop foundation to reuse:

- React 19, TypeScript, Vite, Tailwind CSS, Motion
- TanStack Query, Zustand
- Radix Dialog/Popover/Tabs/Toast/Tooltip
- cmdk, Lucide React, React Hook Form, Zod, Tauri

Existing surfaces:

```
apps/desktop/src/app/App.tsx
apps/desktop/src/components/layout/AppShell.tsx
apps/desktop/src/components/layout/SACommandPalette.tsx
apps/desktop/src/components/layout/QuickCreate.tsx
apps/desktop/src/components/layout/ThemeSwitch.tsx
apps/desktop/src/components/ui/sa.tsx
```

Relevant domains:

```
apps/desktop/src/features/command/
apps/desktop/src/features/employees/
apps/desktop/src/features/finance/
apps/desktop/src/features/billing/
apps/desktop/src/features/productions/
apps/desktop/src/features/work/
apps/desktop/src/features/headquarters/
apps/desktop/src/features/calendar/
apps/desktop/src/features/payroll/
apps/desktop/src/features/communications/
```

Backend root:

```
apps/backend/src/main/java/com/saproduction/
```

Exact backend classes/methods are an E0 inspection task; do not guess names.

## 4. Runtime architecture

```
Mamu
  ↓
Eve UI / Session
  ↓
Eve Orchestrator
  ├── Context Service
  ├── Retrieval Service
  ├── Memory Service
  └── System Knowledge
          ↓
     Model Provider
          ↓
        Planner
          ↓
    Structured EvePlan
          ↓
   Command Gateway
   ├── schema validation
   ├── entity validation
   ├── permission validation
   ├── domain validation
   └── confirmation policy
          ↓
Existing SA Command services
          ↓
      PostgreSQL
          ↓
     Verification
          ↓
   Activity Trace / Audit
```

## 5. Eve responsibilities

Eve owns:

- natural-language interaction;
- session state;
- bounded context assembly;
- retrieval orchestration;
- system-knowledge representation;
- model-provider abstraction;
- structured planning;
- entity resolution;
- confirmation state;
- Eve memory;
- activity trace;
- post-command verification orchestration.

Existing domains own:

- business rules;
- authorization;
- persistence;
- transaction boundaries;
- finance posting;
- payroll;
- production semantics;
- HQ reservations;
- work/task behavior;
- billing;
- canonical audit.

Eve calls these capabilities; it does not reimplement them.

## 6. Frontend architecture

Route:

```
/eve
```

Feature target:

```
apps/desktop/src/features/eve/
```

Likely structure:

```
EvePage.tsx
EvePage.test.tsx
eve.api.ts
eve.types.ts
components/
  EveComposer.tsx
  EveActivityTrace.tsx
  EveContextPanel.tsx
  EvePlanCard.tsx
  EveConfirmation.tsx
  EveMemoryPanel.tsx
  EveSessionList.tsx
```

Use TanStack Query for server state and Zustand only for UI state.

Do not move canonical business data into Zustand.

## 7. UI sourcing

Reuse existing SA Command primitives first.

Order:

```
existing SA primitive
→ existing Radix primitive/pattern
→ Aceternity pattern
→ CodeFronts microinteraction
→ local primitive only when necessary
```

Aceternity references from the plan:

- Bento Grid;
- Expandable Card;
- Animated Tabs;
- restrained Card Hover;
- modal/overlay patterns.

CodeFronts references:

- subtle elevation;
- status transitions;
- loaders;
- segmented controls;
- tooltips;
- progress treatment.

No new animation runtime. Preserve SA Pearl, Reference Charcoal, existing typography, geometry and reduced-motion behavior.

External copied/adapted source must follow `docs/THIRD_PARTY.md`.

## 8. Eve screen

Eve is a business command console, not a generic ChatGPT clone.

```
┌─────────────────────────────────────────────────────────────┐
│ Eve                                      ● System Ready     │
│ SA Productions Intelligence Layer                           │
├─────────────────────────────────────────────────────────────┤
│ Tell me what happened.                                      │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ natural-language input                                  │ │
│ └─────────────────────────────────────────────────────────┘ │
├───────────────────────────────────┬─────────────────────────┤
│ Activity                          │ Context                 │
│ ✓ Understanding                  │ Current production      │
│ ✓ Found employee                 │ Relevant people         │
│ → Checking payment               │ Outstanding balance     │
│ → Validating                     │ Related equipment       │
└───────────────────────────────────┴─────────────────────────┘
```

The interface changes state in place. Avoid a generic full-page “processing” screen.

## 9. Observable trace

The trace is composed of actual application events, not private model chain-of-thought.

Suggested stages:

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

Each displayed event should be truthful about what the application actually did.

## 10. Context and retrieval

Model context:

```
user message
+ owner/auth context
+ current app context
+ current date/time
+ recent Eve session
+ relevant records
+ system knowledge
+ relevant memory
```

Never send the whole database to the model.

Retrieval order:

```
exact identifier
→ normalized exact match
→ bounded database search
→ bounded candidate set
→ model-assisted resolution among candidates
```

The model never invents canonical entity IDs.

## 11. System knowledge

Represent:

```
Domain
Entity
Relationship
Command
Constraint
Required field
Side effect
Verification rule
```

Recommended files:

```
eve/knowledge/
├── domains.md
├── entities.md
├── relationships.md
├── commands.md
├── finance-rules.md
├── production-rules.md
├── headquarters-rules.md
└── terminology.md
```

Knowledge explains the application to the model; executable code remains authoritative.

## 12. Model boundary

Use a provider interface:

```ts
interface EveModelProvider {
  plan(input: EveModelRequest): Promise<EveModelResponse>;
}
```

Implementations may include:

```
LocalModelProvider
OptionalCloudModelProvider
TestModelProvider
```

No vendor SDK should leak through the rest of Eve.

Model selection is intentionally deferred until benchmarking English, Hindi, Hinglish, entity resolution, structured output reliability, Windows performance, latency, memory requirements and licensing.

## 13. Planner contract

Conceptual:

```ts
type EvePlan = {
  planId: string;
  intent: EveIntent;
  entities: EveResolvedEntity[];
  actions: EveAction[];
  confidence: "LOW" | "MEDIUM" | "HIGH";
  needsClarification: boolean;
  clarificationQuestion?: string;
  requiresConfirmation: boolean;
  summary?: string;
};
```

The model cannot emit SQL, shell commands, arbitrary endpoints or unrestricted tool calls.

## 14. Entity resolution

```
"Raju"
  ↓
retrieve real candidates
  ↓
one safe candidate → resolve
multiple candidates → ask
none → ask / not found
```

Retain spoken value, canonical ID, canonical name, matching method and confidence.

Ambiguous financial/personnel references never mutate.

## 15. Command gateway

The gateway is the hard AI-to-business boundary.

```
EvePlan
  ↓
Schema validation
  ↓
Entity validation
  ↓
Permission validation
  ↓
Domain validation
  ↓
Confirmation policy
  ↓
Canonical application service
```

Use a finite command registry, never a generic execute-anything command.

Conceptual command definition:

```ts
type EveCommandDefinition = {
  type: EveCommandType;
  risk: "READ" | "LOW" | "MATERIAL";
  confirmationPolicy: "NEVER" | "REQUIRED" | "POLICY";
  requiredEntities: string[];
  execute: (context: EveExecutionContext) => Promise<EveCommandResult>;
  verify: (context: EveVerificationContext) => Promise<EveVerificationResult>;
};
```

## 16. Canonical execution

Preferred path:

```
Eve
 ↓
Command Gateway
 ↓
existing application/domain service
 ↓
repository / transaction
```

Avoid making Eve call the application's own HTTP controllers internally unless an existing architecture explicitly requires it.

## 17. Finance boundary

Finance remains canonical.

Eve should target existing workflows for:

- employee payment;
- employee earning;
- production expense;
- production receipt;
- party receipt;
- invoice payment;
- equipment purchase/payment;
- owner credit/debit/transfer;
- reversal.

Exact method signatures are an E0 inspection task.

Preserve:

```
earned ≠ paid
invoice ≠ direct party charge
invoice payment settles an invoice
party receipt settles a direct charge
equipment assignment ≠ purchase/payment
expense requires payer attribution
owner movement requires explicit owner attribution
billing export does not post money
```

Eve never invents journal entries.

## 18. Production, Work and HQ boundaries

Production/work:

```
resolve production
→ resolve people/task
→ existing conflict/rule checks
→ canonical service
→ verify
```

HQ equipment:

```
production/time context
→ existing availability logic
→ existing reservation workflow
→ verify reservation/allocation
```

No duplicate Eve inventory, task or production state.

## 19. Billing boundary

Eve may read or prepare billing actions, but Billing and Finance remain authoritative.

Preserve the current separation between billing export, formal invoices, direct party charges and settlement.

## 20. Memory boundary

Canonical database = business truth.

Eve memory = interaction/context knowledge.

Conceptual:

```ts
type EveMemory = {
  id: string;
  type:
    | "VOCABULARY"
    | "CORRECTION"
    | "PREFERENCE"
    | "DECISION"
    | "CONTEXT";
  key: string;
  value: string;
  source: "USER_CORRECTION" | "EXPLICIT_USER" | "SYSTEM";
  createdAt: string;
  updatedAt: string;
  active: boolean;
};
```

Memory must be inspectable, editable and deletable. It must not silently rewrite canonical business data.

## 21. Sessions

```
Session
 ├── messages
 ├── context snapshots
 ├── plans
 ├── confirmations
 ├── traces
 └── referenced entities
```

Use bounded recent context plus durable memory.

## 22. Confirmation policy

Confirmation is enforced by Eve policy code, not the model alone.

Initial direction:

```
READ              → no confirmation
SAFE OPERATION    → policy dependent
FINANCIAL WRITE   → confirmation initially required
AMBIGUOUS         → blocked
UNKNOWN/HIGH RISK → blocked
```

The model may request confirmation; the policy layer remains authoritative.

## 23. Idempotency

Eve must tolerate retries.

Use Eve identifiers plus canonical idempotency mechanisms where supported:

```
sessionId + planId + commandId + canonical idempotency key
```

Never create duplicate financial effects. Reuse existing FinancePostingService/idempotency behavior.

## 24. Verification

Every mutation must define a verification procedure.

Example employee payment:

```
execute
 ↓
reload authoritative payment state
 ↓
reload employee balance
 ↓
reload payer position
 ↓
check canonical finance transaction
 ↓
compare expected vs actual
 ↓
VERIFIED / NEEDS_ATTENTION
```

A successful model response is never proof of execution.

## 25. Failure model

Distinguish:

- **Clarification:** missing or ambiguous information.
- **Blocked:** policy/domain rule prevents the action.
- **Failed:** valid command did not execute.
- **Verification mismatch:** execution succeeded but expected resulting state could not be verified.

Each maps to a distinct trace/UI state.

## 26. Security

Eve must not have:

- unrestricted SQL;
- shell execution;
- arbitrary endpoint selection;
- database credentials;
- access to secrets;
- permission to bypass canonical services.

Cloud providers, if later added, receive only context allowed by an explicit data-sharing policy.

## 27. Prompt architecture

Do not create one giant prompt.

```
eve/prompts/
├── system.md
├── planner.md
├── resolver.md
├── verifier.md
└── examples/
```

Prompts are versioned like code.

Record prompt/model versions with plans sufficiently for debugging and reproduction.

## 28. Evaluation architecture

```
eve/evals/
├── planner/
├── entity-resolution/
├── command-gateway/
├── finance/
├── operations/
└── fixtures/
```

Evaluate intent accuracy, entity resolution, ambiguity detection, required fields, command selection, financial safety, duplicate resistance, verification and English/Hindi/Hinglish/date/amount understanding.

## 29. Deterministic test provider

```
TestModelProvider
  ↓
fixed EvePlan fixture
  ↓
Command Gateway
  ↓
real canonical service
  ↓
PostgreSQL
  ↓
verification
```

This allows end-to-end tests without a live model.

## 30. Offline-first boundary

```
SA Command Desktop
      │
      ▼
Local Eve Runtime
      ├── local model
      ├── retrieval
      ├── knowledge
      ├── memory
      ├── planner
      └── trace
      │
      ▼
SA Command backend
      │
      ▼
PostgreSQL
```

The local model never connects directly to PostgreSQL.

## 31. Proactive boundary

Later:

```
system signal
 ↓
Eve observer
 ↓
candidate suggestion
 ↓
policy check
 ↓
owner-visible suggestion
```

Never silently mutate from a proactive signal.

## 32. No unrestricted agent loops

Initial execution is bounded:

```
request
→ bounded retrieval
→ bounded plan
→ explicit execution
→ verification
```

Any future loop requires an explicit purpose, tool allowlist, iteration limit, timeout, trace and verification.

## 33. Observability

Internal technical trace:

```
sessionId
planId
commandId
modelProvider
modelVersion
promptVersion
latency
retrieval count
command result
verification result
error class
```

Never log credentials, secrets or unnecessary sensitive data.

## 34. Architecture decisions

- Existing domains are authoritative.
- No Eve ledger.
- No direct SQL.
- Material actions are policy-gated.
- Every mutation is verified.
- Memory is separate from business truth.
- Local model is replaceable.
- Activity UI shows observable events, not hidden reasoning.
- Reuse the existing SA design system.
- Reuse solved primitives and the documented Aceternity/CodeFronts references.

## 35. Implementation sequence

```
E0 repository reconnaissance
        ↓
Eve contracts
        ↓
session + trace persistence
        ↓
context/retrieval
        ↓
model provider interface
        ↓
planner + deterministic test provider
        ↓
read-only Eve
        ↓
command registry/gateway
        ↓
one safe write
        ↓
verification
        ↓
financial commands
        ↓
memory
        ↓
local model
        ↓
proactive suggestions
```

Do not implement all capabilities simultaneously.

## 36. First vertical slice

Read:

```
"How much does Sharma still need?"
→ retrieve
→ authoritative employee-finance state
→ answer
→ trace
```

Write:

```
"Pay Sharma 3000."
→ resolve
→ retrieve outstanding
→ resolve/ask payer
→ plan
→ confirmation
→ canonical employee-payment workflow
→ reload state
→ verify
→ trace
```

This is the initial proof that Eve works.

## 37. Architectural definition of done

Eve is architecturally correct when:

1. Natural language becomes bounded context.
2. Real entities are resolved, not invented.
3. The model emits only constrained plans.
4. Material actions are policy-gated.
5. Existing services perform mutations.
6. Finance remains canonical.
7. Retries cannot create duplicate financial effects.
8. Every mutation is verified.
9. Activity traces reflect real application events.
10. Memory stays separate from business truth.
11. Model/runtime can be swapped.
12. SA Command still functions independently of Eve.

**Eve is the intelligence/interface layer. SA Command remains the system of record.**
