# EVE Phase 4 — Implementation Map & Architecture Reference

This document maps all Phase 4 Continuous Intelligence & Evidence-Backed Proactive Suggestions components, database schema, event pipelines, policy engines, and UI layers across SA Command.

**Core Invariant: EVE is an observer of canonical system changes, not an autonomous operator. No unconfirmed writes, no secondary ledgers, AFTER_COMMIT transaction isolation, and deterministic evidence binding.**

---

## 1. Continuous Intelligence Pipeline & Component Matrix

| Stage | Component / Service | Responsibility | Test Level & File |
| :--- | :--- | :--- | :--- |
| **Domain Mutation Capture** | [`AuditService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/audit/AuditService.java) | Records business audit and publishes `DomainMutationEvent` via Spring `ApplicationEventPublisher`. | Unit: [`AuditServiceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/audit/AuditServiceTest.java) |
| **Transaction Isolation** | [`EveSignalEventListener.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalEventListener.java) | Listens via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Guarantees zero claims on rolled-back transactions. | Unit: [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) |
| **Signal Ingestion & Allowlist** | [`EveSignalService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalService.java), [`EveSignalTypes.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalTypes.java) | Validates signal type against allowlist (`FINANCE_TRANSACTION_POSTED`, `WORK_TASK_UPDATED`, etc.), enforces correlation deduplication, and persists to `eve_signals`. | Unit: [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) |
| **Continuous Observation** | [`EveObserverService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveObserverService.java) | Evaluates canonical domain state via `FinanceReadService`, `ProductionRepository`, `WorkTaskRepository`. Coordinates with policy engine to trigger or resolve suggestions. | Unit: [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) |
| **Deterministic Suggestion Policy** | [`EveSuggestionPolicy.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSuggestionPolicy.java) | Implements deterministic rule evaluation for employee payments, approaching productions, and overdue tasks. Generates structured JSONB evidence and computes stable `dedupe_key`. | Unit: [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) |
| **Suggestion Lifecycle & Cooldown** | [`EveSuggestionService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSuggestionService.java) | Manages `ACTIVE`, `DISMISSED`, `RESOLVED` transitions. Enforces 24h cooldown after dismissal. Upserts active suggestions with database-level uniqueness. | Unit: [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) |
| **REST Gateway** | [`EveController.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveController.java) | Exposes authenticated endpoints: `GET /suggestions`, `POST /suggestions/{id}/dismiss`, `POST /suggestions/{id}/resolve`, `POST /suggestions/evaluate`, `POST /signals`. | Component / Integration |
| **Desktop UI Panel** | [`EveSuggestionsPanel.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/components/EveSuggestionsPanel.tsx) | Refined Bento card with badge counter, expandable evidence items, "Dismiss", and "Investigate in EVE" conversation seed button. | Vitest: [`EvePage.test.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.test.tsx) |
| **Desktop State & Query Integration** | [`EvePage.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.tsx), [`eve.api.ts`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/eve.api.ts) | TanStack Query integration for live suggestion polling, optimistic invalidation, and seamless prompt pre-population into chat input. | Vitest: [`EvePage.test.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.test.tsx) |

---

## 2. File & Component Architecture

| Component | File Path | Phase 4 Responsibilities |
| :--- | :--- | :--- |
| **Flyway Migration** | [`V030__eve_proactive_intelligence.sql`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/resources/db/migration/V030__eve_proactive_intelligence.sql) | DDL for `eve_signals` and `eve_suggestions`. Partial unique index `uq_eve_active_suggestion_dedupe`. |
| **Domain Mutation Event** | [`DomainMutationEvent.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/audit/DomainMutationEvent.java) | Application event record containing `entityType`, `entityId`, `action`, `actorId`, and metadata payload. |
| **Signal Type Allowlist** | [`EveSignalTypes.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalTypes.java) | Finite allowlist of recognized domain signals (`FINANCE_TRANSACTION_POSTED`, `PRODUCTION_RUN_STAGE_CHANGED`, `WORK_TASK_UPDATED`, etc.). |
| **Event Listener** | [`EveSignalEventListener.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalEventListener.java) | Intercepts `DomainMutationEvent` with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. |
| **Signal Ingestion Service** | [`EveSignalService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSignalService.java) | Persists and routes signals with correlation ID deduplication. |
| **Observer Service** | [`EveObserverService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveObserverService.java) | Queries canonical state and executes suggestion evaluation and auto-resolution loops. |
| **Suggestion Policy** | [`EveSuggestionPolicy.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSuggestionPolicy.java) | Encapsulates deterministic evaluation logic, evidence creation, and cooldown checks. |
| **Suggestion Service** | [`EveSuggestionService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveSuggestionService.java) | Manages database CRUD, status updates, dismissal timestamps, and deduplication queries. |
| **DTOs & Contracts** | [`EveDtos.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveDtos.java) | `SignalView`, `EmitSignalRequest`, `SuggestionEvidenceItem`, `SuggestionView`, `DismissSuggestionRequest`, `ResolveSuggestionRequest`. |
| **REST Controller** | [`EveController.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveController.java) | Adds proactive suggestion and signal management endpoints to the EVE REST API. |
| **TypeScript Types** | [`eve.types.ts`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/eve.types.ts) | Adds `SuggestionEvidenceItem`, `EveSuggestion`, `EveSignal`, and request/response shapes. |
| **Desktop API Client** | [`eve.api.ts`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/eve.api.ts) | Adds Axios client methods for suggestion listing, dismiss, resolve, and evaluation. |
| **Desktop Suggestions Panel** | [`EveSuggestionsPanel.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/components/EveSuggestionsPanel.tsx) | Theme-compliant Bento panel showing active suggestions, expandable evidence, and investigation triggers. |
| **Desktop EVE Console** | [`EvePage.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.tsx) | Integrates `EveSuggestionsPanel` above chat conversation stream with smooth action handoff. |

---

## 3. Comprehensive Verification Matrix

| Test Class / Suite | Focus Area | Status |
| :--- | :--- | :--- |
| [`EveContinuousIntelligenceTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EveContinuousIntelligenceTest.java) | Signal allowlist, correlation deduplication, 24h dismissal cooldown, auto-resolution on balance $\le 0$, approaching production runs, overdue tasks, adversarial prompt injection safety. | PASS (10/10) |
| [`EvePostgresIntegrationTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/EvePostgresIntegrationTest.java) | Real PostgreSQL Testcontainers: V030 Flyway migration execution, `eve_signals`, `eve_suggestions`, 8 indexes, 5 foreign keys, partial unique dedupe index `uq_eve_active_suggestion_dedupe`. | PASS (6/6) |
| [`EvePage.test.tsx`](file:///c:/Users/xtrar/Desktop/ERP/apps/desktop/src/features/eve/EvePage.test.tsx) | Desktop UI rendering of proactive suggestions, counter badge, collapsible evidence items, "Investigate in EVE" prompt pre-population, and dismissal mutation. | PASS (10/10) |
| **Live HTTP E2E Verification** | Live Spring Boot port 8080 end-to-end signal emission, suggestion creation, deduplication, dismissal, resolution, and evaluate endpoints. | PASS (5/5 scenarios) |
| **Full Desktop Suite** | All 21 test files across desktop features (`npm test -- --run`). | PASS (88/88) |
| **Full Backend Suite** | All backend tests across SA Command (`mvn test`). | PASS (270/270) |
| **Desktop Production Build** | TypeScript build and Vite packaging (`npm run build`). | PASS (0 errors) |

---

## 4. Research References Cited

1. **AnythingLLM Repository** (`Mintplex-Labs/anything-llm`): Architecture reference for workspace context boundaries and read-only telemetry observation.
2. **AnythingLLM Documentation** (`docs.anythingllm.com`): Architecture reference for passive agent recommendations without autonomous execution side-effects.
3. **DocMind AI Repository**: Architecture reference for structured multi-modal evidence pipelines and provenance tracking anchored to canonical database IDs.
