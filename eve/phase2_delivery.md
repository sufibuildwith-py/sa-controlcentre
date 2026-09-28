# EVE Phase 2 — Conversational Intelligence Delivery Summary

Phase 2 elevates EVE from single-turn grounded retrieval into a true **conversational intelligence layer** over SA Command. It maintains session context across turns, resolves follow-up pronouns and references, conducts cross-domain reads across canonical systems of record, normalizes owner temporal colloquialisms and currency phrasing, handles ambiguity gracefully, and provides observable operational traces with visible evidence.

**Phase 2 performs no canonical business-state mutation. EVE may persist conversation/session state, trace events, and explicitly requested owner memory in EVE-owned persistence (`eve_sessions`, `eve_messages`, `eve_trace_events`, `eve_memory`).**

---

## 1. External Architectural References

EVE Phase 2 was informed by patterns observed in:

- **AnythingLLM repository** — https://github.com/Mintplex-Labs/anything-llm
- **AnythingLLM documentation** — https://docs.anythingllm.com/
- **DocMind AI** — https://github.com/BjornMelin/docmind-ai-llm

These repositories were used as architectural references for concepts such as bounded conversational context, retrieval/context assembly, provider abstraction, and document/knowledge grounding.

EVE does not copy their application architecture. SA Command remains a Java/Spring/PostgreSQL/React/Tauri system, and EVE remains a bounded intelligence layer over the existing canonical domain services.

---

## 2. Core Capabilities Delivered & Verified

### 2.1 Multi-Turn Conversational Session Management
- **Location**: [`EveRetrievalRouter.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalRouter.java), [`EveContextEngine.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveContextEngine.java)
- **SessionContext**:
  - Tracks `lastReferencedEmployee`, `lastReferencedProduction`, `pendingCandidates`, and `lastIntent`.
  - Supports natural follow-up inquiries using pronouns (*"usme"*, *"uska"*, *"woh"*, *"him"*, *"that event"*).
  - Verified end-to-end through `EveService` in [`EveConversationalSessionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveConversationalSessionTest.java):
    1. Turn 1: *"Royal Wedding mein kaun kaun tha?"* $\to$ Resolves production, returns crew list, stores Royal Wedding.
    2. Turn 2: *"Usme Sharma bhi tha?"* $\to$ Uses cached production context, resolves Raj Sharma, verifies assignment.
    3. Turn 3: *"Uska payment kitna pending hai?"* $\to$ Uses cached employee context, checks `FinanceReadService`, reports ₹40,000.00 outstanding.
    4. Turn 4: *"Aur uska equipment?"* $\to$ Uses cached production context, lists 4x LED Par Cans reserved from Headquarters.

### 2.2 Temporal Grounding (`Asia/Kolkata`)
- **Location**: [`EveDateTimeParser.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveDateTimeParser.java)
- Explicit timezone awareness grounded in `Asia/Kolkata` (`ZoneId.of("Asia/Kolkata")`).
- Resolves colloquial temporal expressions without arbitrary defaults:
  - *"aaj"*, *"today"* $\to$ `LocalDate.now(zone)`
  - *"kal"*, *"tomorrow"* $\to$ `LocalDate.now(zone).plusDays(1)`
  - *"yesterday"* $\to$ `LocalDate.now(zone).minusDays(1)`
  - *"parso"* $\to$ `LocalDate.now(zone).plusDays(2)`
  - Full Indian and ISO date formatting (`28-09-2026`, `2026-09-28`).

### 2.3 Currency and Amount Grounding (Integer Minor Units)
- **Location**: [`EveAmountParser.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveAmountParser.java)
- Normalizes colloquial financial numbers into exact integer paise (minor units):
  - *"3k"*, *"3 hazaar"*, *"teen hazaar"* $\to$ `300,000 paise` (`₹3,000.00`)
  - *"5 lakh"*, *"paanch lakh"* $\to$ `50,000,000 paise` (`₹5,00,000.00`)
  - *"₹40,000.00"* $\to$ `4,000,000 paise` (`₹40,000.00`)
- Enforces strict arithmetic integrity and INR currency formatting (`Locale.of("en", "IN")`).

### 2.4 Owner Memory & Explicit Vocabulary Learning
- **Location**: [`EveMemoryService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveMemoryService.java)
- **Memory Semantics**:
  - Durable owner vocabulary memory is created **only from explicit owner intent/correction** (e.g. *"Raju means Raj Kumar"* or *"Raju se mera matlab Raj Kumar hai"*).
  - Model inference alone **cannot create durable memory**. Ordinary operational queries (*"Show me Raju's pending payment"*) resolve using active context and canonical data without creating rows in `eve_memory`.
  - Memory remains a **supporting hint, never canonical business truth**.
  - **Canonical exact match beats memory**: If canonical exact data identifies an entity, canonical DB data always wins.
  - **Stale memory cannot override canonical truth**: Stale memory pointing to deleted or inactive entities results in `NOT_FOUND` or fallback.

### 2.5 Multi-Domain Canonical Cross-Reads
- Routes queries strictly through existing SA Command canonical domain services:
  - **Employee**: `EmployeeService.list(...)`, `EmployeeRepository`
  - **Finance**: `FinanceReadService.employee(id)`
  - **Production**: `ProductionService.get(id)`
  - **Crew**: `ProductionService.get(id).members()`
  - **Equipment**: `ProductionService.get(id).equipment()`
  - **Work/Tasks**: `WorkTaskService.list(...)` for open/pending operational tasks
  - **Headquarters Equipment**: `HeadquartersService.equipment(...)`
  - **Calendar**: `ProductionService.list(...)` bounded by calendar date
  - **Vocabulary**: `EveMemoryService.remember(...)`
  - **Disambiguation**: `EveRetrievalService.selectFromCandidates(...)`

### 2.6 Truthful Trace Lifecycle & Privacy
- **Location**: [`EveService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveService.java)
- Observable lifecycle events:
  $$\text{STARTED} \longrightarrow \text{INTERPRETING} \longrightarrow \text{RESOLVING} \longrightarrow \text{ROUTING} \longrightarrow \text{RETRIEVING} \longrightarrow \text{ASSEMBLING\_CONTEXT} \longrightarrow \text{COMPLETED}$$
- Terminal states: `CLARIFICATION_REQUIRED`, `NOT_FOUND`, `POLICY_BLOCKED`, `SYSTEM_UNAVAILABLE`, `MODEL_FAILED`.
- **Trace Privacy Invariant**: Trace details contain only safe operational facts (stage, sequence, label, domain, entity codes) and **never expose chain-of-thought, hidden model reasoning, raw prompts, passwords, or secrets**. Verified in [`EveServiceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveServiceTest.java) and [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java).

---

## 3. Desktop UI Console (`/eve`)

- **Location**: [`EvePage.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.tsx), [`eve.types.ts`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/eve.types.ts)
- **Features**:
  - **Session Switcher & New Session**: `+ New Session` action and session selector dropdown to manage multiple conversational threads.
  - **Continuous Thread View**: Preserves conversation history across multi-turn prompts.
  - **Interactive Disambiguation**: One-click selection for ambiguous candidate entities.
  - **Domain Evidence Badges**: Visual domain badges on evidence tiles (`FINANCE`, `PRODUCTION`, `EQUIPMENT`, `WORK`, `CALENDAR`, `VOCABULARY`).
  - **Strict SA Design Rule Adherence**: Bento card architecture, Reference Charcoal palette, thin low-contrast borders, zero neon or admin template elements.

---

## 4. Security & Safety Invariants Verified

1. **Zero Canonical Business-State Mutation**: EVE Phase 2 executes zero updates, inserts, or deletes against any business tables (`employees`, `productions`, `production_members`, `finance_*`, `tasks`, `headquarters_*`). Verified by [`EveSecurityBoundaryTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveSecurityBoundaryTest.java), [`EveConversationalSessionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveConversationalSessionTest.java), and [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java).
2. **EVE-Owned Persistence**: EVE only mutates its own dedicated persistence tables (`eve_sessions`, `eve_messages`, `eve_trace_events`, `eve_memory`).
3. **Data-as-Data Invariant**: Untrusted business text (memos, notes, titles) retrieved from the database is treated strictly as passive data and never interpreted as instructions across 12 adversarial injection vectors.
4. **Phase 3 Boundary**: Governed execution, command gateway execution, and business mutations are strictly deferred to Phase 3.

---

## 5. Verification Results

- **Focused EVE Test Suite**: 77/77 tests passed (`mvn test "-Dtest=Eve*Test,TestModelProviderTest"`): 0 failures, 0 errors.
- **PostgreSQL Integration Test**: 1/1 test passed (`EvePostgresIntegrationTest`) verifying zero business mutations across `employees`, `productions`, `production_members`, `finance_transactions`, and `tasks`.
- **Full Backend Suite**: 216/216 tests passed (`mvn test`): 0 failures, 0 errors, 43 skipped.
- **Desktop Frontend Test Suite**: 83/83 desktop tests passed across 21 test files (`npm test -- --run`).
- **Desktop Production Build**: `tsc -b && vite build` completed in 5.15s with 0 errors.
