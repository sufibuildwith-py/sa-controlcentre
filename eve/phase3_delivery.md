# EVE Phase 3 — Governed Execution Delivery Summary

Phase 3 introduces the first real business mutations through EVE in SA Command. Governed execution enforces an uncompromising architectural boundary: **EVE proposes and governs (`EvePlan`, confirmation binding, stale-plan check, canonical verification); canonical SA Command services execute (`FinancePostingService.employeePayment`).**

**Zero autonomous agent loops, zero unrestricted tool calling, zero LLM-generated SQL, zero direct JDBC/repository writes to business tables from EVE logic, and zero duplicate ledgers.**

---

## 1. Architectural Contract & Invariants

```text
Mamu
  ↓
EVE UI / Session
  ↓
Context + Retrieval + Memory + System Knowledge
  ↓
Model Provider (Pattern Match / Structured Interpretation)
  ↓
Structured EvePlan (planId, planHash, version, riskTier, parameters)
  ↓
User Review & Explicit Confirmation (desktop UI token)
  ↓
Command Gateway
  ├── schema validation (exact parameters, positive decimal, 2 decimals)
  ├── domain revalidation (payable balance check, active status, owner account)
  ├── stale-plan protection (planHash, version, balance drift)
  └── idempotency enforcement (deterministic idempotencyKey)
  ↓
Canonical SA Command Service
  └── FinancePostingService.employeePayment(...)
  ↓
Authoritative PostgreSQL Records (finance_transactions, audit_logs)
  ↓
Verification Engine
  └── Checks finance_transactions status='POSTED', allocations, audit trail
  ↓
Truthful Activity Trace & Observable Result
```

### 1.1 Non-Negotiable Invariants Upheld

1. **No Autonomous Agent Loops**: Execution never runs unbounded self-directed agent loops. Every mutation requires an explicit `EvePlan` that must be confirmed by Mamu before execution.
2. **Canonical Service Dispatch**: EVE never modifies `finance_*`, `employees`, or any business tables directly via JDBC or repository calls. All writes flow strictly through existing canonical services (`FinancePostingService.employeePayment`).
3. **No Secondary Ledger**: EVE does not maintain an internal balance cache, ledger shadow, or virtual balances. All balances are read and verified directly from PostgreSQL canonical tables.
4. **Deterministic Cryptographic Binding**: Every `EvePlan` receives a SHA-256 hash calculated over its plan ID, version, intent, risk tier, and sorted canonical action parameters (`EvePlanHasher`). Confirmation requests must supply the exact matching hash and version.
5. **Stale Plan Protection**: If employee payable balance or account preconditions drift between plan generation and confirmation, the gateway immediately rejects execution with `STALE_PLAN`.
6. **Contiguous & Bounded Actions**: Multi-action plans are strictly bounded to `MAX_ACTIONS = 10` and must have contiguous sequence numbers starting at 1. Arbitrary unregistered command names are rejected at schema validation.
7. **PostgreSQL Verification**: An action is only marked `VERIFIED` after verifying that the canonical PostgreSQL transaction was inserted with status `POSTED`, balance impact occurred, and audit logs were recorded.

---

## 2. Core Capabilities Delivered & Verified

### 2.1 Principal Write Slice: Governed Employee Payment
- **User Prompt**: Colloquial Hindi/English input such as *"Sharma ko 3000 de do"*, *"Pay Sharma 3000"*, or *"Sharma payout 3000"*.
- **Flow**:
  1. **Interpretation**: EVE matches `Intent.PROPOSE_EMPLOYEE_PAYMENT` and extracts candidate name (`"Sharma"`) and amount (`3000`).
  2. **Resolution & Precondition Check**: Resolves employee (`Raj Sharma`, `SA-01`), queries `FinanceReadService` to verify active status and outstanding payable balance ($\ge 3,000$). Resolves active owner payer account (`AZ-2` / `AK-2`).
  3. **Plan Formulation**: Constructs an `EvePlan` with `riskTier = FINANCIAL_WRITE`, `confirmationRequired = true`, SHA-256 `planHash`, and action `POST_EMPLOYEE_PAYMENT`.
  4. **Proposal Response**: Persists plan with status `PROPOSED` to `eve_plans` and `eve_plan_actions`. Returns plan to operator desktop console. **Zero mutations to business tables at proposal time.**
  5. **Operator Confirmation**: Mamu inspects the card in desktop `/eve`, reviews parameters and estimated balance impact, and clicks **Confirm & Post Payment**.
  6. **Revalidation & Execution**: Command Gateway re-verifies domain state, re-checks that `earned - paid >= amount`, executes `FinancePostingService.employeePayment`, and creates a canonical `finance_transactions` record.
  7. **PostgreSQL Verification**: Query engine inspects `finance_transactions` for `status = 'POSTED'` and `audit_logs` for `EMPLOYEE_PAYMENT`. Marks action `VERIFIED`.

### 2.2 Dedicated Persistence: Flyway Migration `V029__eve_governed_execution.sql`
- **Tables Created**:
  - `eve_plans`: Stores plan lifecycle (`plan_id`, `session_id`, `intent`, `summary`, `risk_tier`, `confirmation_required`, `plan_hash`, `version`, `status`, `created_at`, `updated_at`, `confirmed_at`, `executed_at`).
  - `eve_plan_actions`: Stores actions (`action_id`, `plan_id`, `action_seq`, `command_name`, `description`, `parameters`, `estimated_effect`, `status`, `canonical_record_id`, `execution_result`, `verification_result`, `idempotency_key`).
- **Database Invariants Enforced**:
  - `CONSTRAINT uq_eve_plan_actions_plan_seq UNIQUE (plan_id, seq)`: Guarantees unique, non-overlapping action sequencing at the database level.
  - `idempotency_key UUID UNIQUE`: Guarantees database-level unique idempotency keys per action, complementing PostgreSQL's advisory lock and `finance_transactions.idempotency_key UNIQUE`.
  - Indices created on `session_id`, `status`, and `plan_id` for zero-latency retrieval.

### 2.3 Finite Command Registry & Gateway Architecture
- **Location**: `EveCommandRegistry.java`, `EveCommandGateway.java`, `EveCommandDefinition.java`
- Finite set of registered commands (`POST_EMPLOYEE_PAYMENT`). Rejects any unregistered command string.
- Action execution enforces:
  - Contiguous sequence order starting at 1 (duplicate and discontinuous sequences rejected).
  - Bounded action count ($1 \le \text{actions} \le 10$). 0 actions rejected; >10 rejected.
  - Type-safe parameter validation (amount > 0, scale $\le 2$, employee exists and active, payer account exists and active).
  - Pre-execution domain check against live PostgreSQL database.
  - Post-execution verification query verifying exact transaction identity (employee ID, amount, payer account ID, POSTED status, employee allocations, zero foreign obligations, and audit linkage).

### 2.4 Desktop Governed Execution Console (`/eve`)
- **Location**: `EvePage.tsx`, `EvePlanCard.tsx`, `eve.api.ts`, `eve.types.ts`
- **Features**:
  - **Governed Plan Card**: Renders proposed plan inline in conversational thread with risk badge (`FINANCIAL WRITE`), cryptographic hash snippet, recipient details, amount, payer account, and estimated balance impact.
  - **One-Click Explicit Actions**: "Confirm & Post Payment" (calls `/confirm`) and "Cancel" (calls `/cancel`).
  - **Truthful Status Lifecycle**: Live visual indicators for `PROPOSED`, `EXECUTING`, `VERIFIED`, `COMPLETED`, `CANCELLED`, and `STALE_PLAN`.
  - **Stale Plan Warning**: Clear explanatory alert if balance preconditions changed between proposal and confirmation.

---

## 3. Comprehensive Verification & Test Results

### 3.1 Backend Tests (246 passing, 0 failures, 43 skipped)
- `EvePlanHasherTest.java` (4 tests):
  - Deterministic SHA-256 hash calculation.
  - Parameter tampering changes hash and triggers token rejection.
  - Plan version change invalidates hash.
  - Successful cryptographic verification.
- `EveCommandGatewayTest.java` (7 tests):
  - Finite registry rejects unregistered command.
  - Rejects plans with 0 actions.
  - Rejects plans exceeding 10 actions.
  - Accepts boundary plan with exactly 10 valid actions.
  - Validates positive amount and decimal scale.
  - Rejects non-contiguous action sequences.
  - Rejects duplicate sequence numbers.
- `EveGovernedExecutionTest.java` (13 tests):
  - Proposes employee payment plan without mutating business state.
  - Rejects overpayment exceeding payable balance.
  - Confirms, executes canonically, and verifies post-conditions.
  - Rejects tampered confirmation token or mismatched plan hash.
  - Rejects mismatched plan ID, mismatched session ID, and mismatched plan version.
  - Returns cached idempotent response for already completed plan.
  - Rejects confirmation of cancelled plan.
  - Rejects stale plan when employee balance changes before confirmation.
  - Cleanly cancels plan without executing mutations.
  - Rejects verification when transaction details do not match (wrong employee, wrong amount).
- `EvePostgresIntegrationTest.java` (4 tests against real PostgreSQL Testcontainers):
  - V029 Flyway migration execution and zero business mutations on proposal.
  - Full proposal $\to$ confirmation $\to$ PostgreSQL execution $\to$ verification cycle (`POSTED` transaction, balance deduction, audit trail, retry tolerance).
  - Multi-threaded concurrency/replay test (`concurrentPlanConfirmationExecutesExactlyOnceWithOneFinancialEffect`): Two concurrent threads attempting to confirm the exact same plan simultaneously result in exactly 1 canonical transaction, 1 allocation, and zero double mutations.
  - Live stale-plan lifecycle test (`stalePlanProducesZeroFinancialEffectAndAllowsFreshPlan`): Direct financial mutation before confirmation immediately triggers `STALE_PLAN` rejection with zero financial side effect, and allows a subsequent fresh plan to propose and post cleanly.
- All 104 EVE backend tests pass cleanly (`mvn test "-Dtest=Eve*Test,TestModelProviderTest"`).

### 3.2 Frontend Desktop Tests (86 passing across 21 test files, 0 failures)
- `EvePage.test.tsx` (8 tests):
  - Phase 1 & 2 tests (query response, multi-turn context, disambiguation candidates, session switching).
  - Phase 3 Governed Execution tests:
    - Renders proposed `EvePlanCard` with risk badge, details, and confirm/cancel buttons.
    - Confirms plan via `eveApi.confirmPlan` with planId, planVersion, planHash and shows verified state.
    - Cancels plan via `eveApi.cancelPlan` cleanly without execution.
    - Handles stale plan error and alerts the operator.
- Production build (`npm run build`): Clean compilation with zero TypeScript errors (`tsc -b && vite build` in 5.07s).

