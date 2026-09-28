# EVE Phase 3 — Implementation Map & Architecture Reference

This document maps all Phase 3 Governed Execution components, contracts, canonical domain dispatch, verification engines, and tests across SA Command.

**Core Invariant: EVE proposes and governs (`EvePlan`, confirmation binding, stale-plan check, canonical verification); canonical SA Command services execute (`FinancePostingService.employeePayment`). Zero direct SQL writes or secondary ledgers from EVE.**

---

## 1. Governed Execution Dispatch & Verification Matrix

| Execution Step | Component / Service | Responsibility | Test Coverage | Test Level |
| :--- | :--- | :--- | :--- | :--- |
| **Colloquial Intent Parsing** | `TestModelProvider.java` / `EveModelProvider.java` | Interprets *"Sharma ko 3000 de do"*, *"Pay Sharma 3000"* to `Intent.PROPOSE_EMPLOYEE_PAYMENT` with candidate name and amount. | [`TestModelProviderTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/TestModelProviderTest.java), [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java) | Unit |
| **Precondition & Payer Check** | `EveService.handlePaymentProposal` | Resolves employee via `EveRetrievalService`, checks active status, verifies outstanding payable balance ($\ge 3,000$) via `FinanceReadService`, selects active owner account (`AZ-2` / `AK-2`). | [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java) | Unit + Integration |
| **Plan Construction & Hashing** | `EvePlanHasher.java` | Generates SHA-256 hash binding `planId`, `version`, `intent`, `riskTier`, and sorted canonical action parameters. | [`EvePlanHasherTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePlanHasherTest.java) | Unit |
| **Plan Persistence** | `V029__eve_governed_execution.sql` | Persists proposed plan to `eve_plans` and `eve_plan_actions` with status `PROPOSED`. **Zero business mutations.** | [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Real PostgreSQL Integration |
| **Desktop Review & Confirmation** | `EvePage.tsx`, `EvePlanCard.tsx` | Presents plan card with cryptographic hash, risk badge (`FINANCIAL WRITE`), action details, and "Confirm & Post Payment" / "Cancel" buttons. | [`EvePage.test.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.test.tsx) | Component Test |
| **Gateway Schema Validation** | `EveCommandGateway.java` | Enforces finite registered command registry, bounded actions ($\le 10$), contiguous seq starting at 1, positive amount with scale $\le 2$. | [`EveCommandGatewayTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveCommandGatewayTest.java) | Unit |
| **Stale Plan Protection** | `EveService.confirmPlan`, `EmployeePaymentCommandDefinition.java` | Locks plan row (`FOR UPDATE`), verifies planHash and version match, re-queries canonical employee payable balance to ensure no drift. | [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java) | Unit |
| **Canonical Service Dispatch** | `EmployeePaymentCommandDefinition.execute` | Dispatches directly to canonical `FinancePostingService.employeePayment` with deterministic idempotency key. | [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java), [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Unit + Real PostgreSQL |
| **Authoritative DB Verification** | `EmployeePaymentCommandDefinition.verify` | Directly queries canonical `finance_transactions` to verify `status = 'POSTED'` and `audit_logs` for `EMPLOYEE_PAYMENT`. | [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java), [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Unit + Real PostgreSQL |
| **Idempotency Tolerance** | `EveService.confirmPlan` | Safe replay on network retries without posting duplicate financial transactions. | [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Real PostgreSQL Integration |

---

## 2. File & Component Architecture

| Component | File Path | Phase 3 Responsibilities |
| :--- | :--- | :--- |
| **Flyway Migration** | [`V029__eve_governed_execution.sql`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/resources/db/migration/V029__eve_governed_execution.sql) | DDL for `eve_plans` and `eve_plan_actions` with indices on `session_id`, `status`, and `plan_id`. |
| **DTOs & Contracts** | [`EveDtos.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveDtos.java) | `EvePlan`, `EvePlanAction`, `ConfirmPlanRequest`, `CancelPlanRequest`, `PlanExecutionResponse`, `EveVerificationResult`. |
| **Plan Hasher** | [`EvePlanHasher.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EvePlanHasher.java) | Computes and validates SHA-256 cryptographic binding across plan metadata and canonical action parameters. |
| **Command Definition Interface** | [`EveCommandDefinition.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveCommandDefinition.java) | 4-phase contract: `validateSchema`, `validateDomain`, `execute`, `verify`. |
| **Employee Payment Command** | [`EmployeePaymentCommandDefinition.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EmployeePaymentCommandDefinition.java) | Validates employee existence, active status, payable balance, dispatches to `FinancePostingService.employeePayment`, and verifies PostgreSQL transaction. |
| **Command Registry** | [`EveCommandRegistry.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveCommandRegistry.java) | Finite registry containing only allowed commands (`POST_EMPLOYEE_PAYMENT`). Rejects arbitrary command names. |
| **Command Gateway** | [`EveCommandGateway.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveCommandGateway.java) | Validates bounds ($\le 10$ actions), contiguous sequence, schema, domain preconditions, executes via registry, and verifies canonical DB records. |
| **Execution Contexts** | [`EveExecutionContext.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveExecutionContext.java), [`EveVerificationContext.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveVerificationContext.java) | Typed parameter containers holding canonical services and DB query engines. |
| **Orchestrator** | [`EveService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveService.java) | Handles payment proposal generation, confirmation with row-locking and stale detection, plan cancellation, and trace progression (`REVALIDATING` $\to$ `EXECUTING` $\to$ `VERIFYING` $\to$ `COMPLETED`). |
| **REST Controller** | [`EveController.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveController.java) | Endpoints: `POST /api/eve/plans/{id}/confirm`, `POST /api/eve/plans/{id}/cancel`, `GET /api/eve/plans/{id}`, `GET /api/eve/sessions/{id}/plans`. |
| **Desktop Plan Card** | [`EvePlanCard.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/components/EvePlanCard.tsx) | Refined SA Pearl / Charcoal component displaying cryptographic hash, risk badge, parameters, action buttons, and verified/stale badges. |
| **Desktop Console** | [`EvePage.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.tsx) | Connects plan queries, confirmation mutation, cancellation mutation, and renders `EvePlanCard` in the intelligence stream. |

---

## 3. Comprehensive Verification Matrix

| Test Class | Focus Area | Status |
| :--- | :--- | :--- |
| [`EvePlanHasherTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePlanHasherTest.java) | SHA-256 determinism, parameter tampering detection, version modification detection, and verification logic. | PASS (4/4) |
| [`EveCommandGatewayTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveCommandGatewayTest.java) | Finite registry enforcement, max 10 actions boundary (0 invalid, 10 valid, 11 rejected), positive amount & scale $\le 2$ schema checks, contiguous action sequencing, duplicate sequence rejection. | PASS (7/7) |
| [`EveGovernedExecutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveGovernedExecutionTest.java) | Governed employee payment proposal without business mutation, overpayment prevention, canonical execution and verification, tampered token rejection, wrong plan ID / session ID / version rejection, already executed idempotency caching, cancelled plan confirmation rejection, stale plan rejection, clean cancellation, and unrelated transaction verification rejection. | PASS (13/13) |
| [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Real PostgreSQL Testcontainers: V029 Flyway migration execution, zero mutations on plan proposal, canonical `finance_transactions` row with `POSTED`, balance update, payer position deduction, audit trail creation, retry tolerance, multi-threaded concurrency/replay safety proving exactly one material financial effect, and stale-plan lifecycle test. | PASS (4/4) |
| [`EvePage.test.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.test.tsx) | Desktop UI rendering of proposed plan card, explicit confirmation binding dispatch, clean cancellation, and stale plan warning. | PASS (8/8) |
| **All EVE Backend Tests** | All EVE test classes (`mvn test "-Dtest=Eve*Test,TestModelProviderTest"`). | PASS (104/104) |
| **Full Desktop Suite** | All 21 test files across desktop features (`npm test -- --run`). | PASS (86/86) |
| **Full Backend Suite** | All backend tests across SA Command (`mvn test`). | PASS (246/246) |
| **Desktop Production Build** | TypeScript build and Vite packaging (`npm run build`). | PASS (0 errors) |

