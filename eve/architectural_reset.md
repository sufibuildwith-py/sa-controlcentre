# EVE Architectural Reset — Stopping the Parrot

## Overview
This document records the architectural reset of EVE, replacing phrase-memorization and regex classification with a typed, capability-based information-resolution architecture.

EVE is the contextual command and intelligence core of SA Command. It operates as an authoritative, grounded system assistant—not a conversational chatbot that hallucinates or defaults across domain boundaries.

---

## 1. Core Architectural Distinctions

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             ARCHITECTURAL SEPARATION                             │
├─────────────────────────────────────────────────┬────────────────────────────────┤
│ WHAT IT IS NOT                                  │ WHAT IT IS                     │
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ SYSTEM MODEL ≠ PHRASE DICTIONARY                │ Typed schema of concepts,      │
│                                                 │ capabilities, relationships &  │
│                                                 │ authoritative state endpoints  │
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ INFORMATION NEED ≠ HARDCODED USER SENTENCE      │ Canonical domain intention,    │
│                                                 │ target concept, and semantic   │
│                                                 │ reference slot extraction      │
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ SEMANTIC RETRIEVAL ≠ CAPABILITY AUTHORITY       │ Candidate finder & reranker;   │
│                                                 │ NEVER decides domain rights or │
│                                                 │ executes mutations             │
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ QWEN / LLM ≠ BUSINESS TRUTH                     │ Local language understanding & │
│                                                 │ phrasing; NEVER source of truth│
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ CANONICAL SERVICE = BUSINESS TRUTH              │ PostgreSQL + domain services   │
│                                                 │ (FinanceReadService, HQ, etc.) │
├─────────────────────────────────────────────────┼────────────────────────────────┤
│ PHASE 3 GATEWAY = MUTATION AUTHORITY            │ Two-phase governed execution:  │
│                                                 │ proposal -> hash -> confirm    │
└─────────────────────────────────────────────────┴────────────────────────────────┘
```

---

## 2. End-to-End Processing Pipeline

```
User Utterance (Hinglish / English)
       │
       ▼
[Language Understanding Provider] (TestModelProvider / LocalQwenModelProvider)
       │ Extracts: Intent, Concept, Operation, Spoken Entities, Antecedent Status
       ▼
[InformationNeed]
       │ Typed domain descriptor: Operation (READ/PROPOSE/SYSTEM), Concept, Topic, Target Slots
       ▼
[EveCapabilityResolver] + [EveSystemModel]
       │ Matches InformationNeed to Registered Domain Capability
       │ Checks safety boundaries & disambiguation prerequisites
       ▼
[EveRetrievalRouter] + [EveRetrievalService]
       │ Semantic candidate retrieval & reranking (Qwen3-Embedding / Qwen3-Reranker)
       │ Safe resolution: Exact match -> Semantic match -> Clarification / NOT_FOUND
       ▼
[Authoritative Domain Services] (PostgreSQL System of Record)
       │ HeadquartersService (HQ inventory)
       │ FinanceReadService (Employee balances, payroll ledger)
       │ ProductionService (Events, schedules, crew, assigned gear)
       │ WorkTaskService (Tasks and operational assignments)
       ▼
[EveResponseComposer]
       │ Synthesizes factually grounded response with explicit citations & evidence items
       ▼
Structured Response (Trace + Evidence + Entities + Message)
```

---

## 3. Real Domain Architecture in SA Command

1. **Headquarters Equipment (`HeadquartersService`):**
   - Authoritative inventory tables: `hq_equipment`, `hq_inventory_positions`, `hq_reservations`, `hq_units`.
   - Seeding includes:
     - `DEMO-HQ-005`: Gaffer Tape (Consumable, 120 usable rolls)
     - `DEMO-HQ-007`: C-Stands Heavy Duty (Quantity, 10 units, 8 available)
     - `DEMO-HQ-001`: Flight Cases (Quantity, 60 units)
   - Capabilities: `READ_EQUIPMENT_STOCK`, `READ_EQUIPMENT_RESERVATIONS`.

2. **Production Equipment (`ProductionService`):**
   - Tracks reservations and gear assigned to a specific production via `productionService.get(prodId).equipment()`.
   - Capabilities: `READ_PRODUCTION_EQUIPMENT`.

3. **Employee Finance (`FinanceReadService`):**
   - Authoritative financial position for employees (earned, paid, outstanding) via `financeReadService.employee(empId)`.
   - Capabilities: `READ_EMPLOYEE_FINANCE`, `READ_EMPLOYEE_PAYROLL`.

4. **Production Operations (`ProductionService` & `ProductionMemberRepository`):**
   - Production details, schedule, client associations, assigned crew, member presence.
   - Capabilities: `READ_PRODUCTION_OVERVIEW`, `READ_PRODUCTION_SCHEDULE`, `READ_PRODUCTION_CLIENT`, `READ_PRODUCTION_CREW`, `CHECK_MEMBER_PRESENCE`.

5. **Governed Execution Gateway (`EveCommandGateway`):**
   - Phase 3 governed mutations (e.g. employee payouts).
   - Enforces two-phase cryptographic confirmation: Plan Proposal -> SHA-256 Plan Hash -> User Confirmation -> Execution.

---

## 4. Architectural References & Provenance

The design patterns and retrieval-augmented architectures in EVE are informed by vetted, production-grade local AI systems documented in:
- `docs/reference/anything-llm`: Local multi-tenant RAG, modular vector-store/embedding decoupling, and deterministic fallback.
- `docs/reference/docmind-ai-llm`: Structured slot extraction, schema-driven context routing, and strict verification boundaries.

---

## 5. Verification & Empirical Evidence

### Backend Test Execution
- **Core EVE Suite:** 212 tests executed across 18 suites:
  - `EveCapabilityGeneralizationTest` (36 tests): 36 passed, 0 failures
  - `TestModelProviderTest` (20 tests): 20 passed, 0 failures
  - `EveConversationalSessionTest` (9 tests): 9 passed, 0 failures
  - `EveCommandGatewayTest` (14 tests): 14 passed, 0 failures
  - `EveSemanticResolutionBenchmarkTest` (14 tests): 14 passed, 0 failures
  - `EveSemanticResolutionTest` (9 tests): 9 passed, 0 failures
  - `EveContextEngineTest`, `EveContinuousIntelligenceTest`, `EveControllerTest`, `EveGovernedExecutionTest`, `EveKnowledgeServiceTest`, `EveLanguageParsingTest`, `EveMemoryServiceTest`, `EvePlanHasherTest`, `EveRetrievalRouterTest`, `EveRetrievalServiceTest`, `EveSecurityBoundaryTest`, `EveServiceTest`
  - **Result:** 212 passed, 0 failures, 0 errors, 0 skipped.
- **PostgreSQL Integration Suite (`EvePostgresIntegrationTest`):**
  - Real Testcontainers `postgres:17-alpine` container with all Flyway migrations applied.
  - **Result:** 12 passed, 0 failures, 0 errors, 0 skipped.
- **Full Backend Suite (`mvn test`):**
  - **Result:** 372 run, 0 failures, 0 errors, 43 skipped (skipped tests are intentional non-active integration profiles). Build status: `BUILD SUCCESS`.

### Frontend Test Execution & Build
- **Desktop Vitest Suite (`npm test -- --run`):**
  - **Result:** 21 test files passed, 89 total tests passed, 0 failures.
- **Desktop Production Build (`npm run build`):**
  - **Result:** Clean TypeScript compilation and Vite bundle generation (`built in 5.36s`).

### Measured Metrics
- **Cross-Domain Fallthroughs:** 0 cross-domain fallthroughs observed in 212 tested EVE cases.
- **Unseen Generalization:** Tested on 20+ unseen Hinglish and English paraphrases across 4 core domains without any phrase-specific dictionary matching.
- **Truthful Non-Existent Entity Safety:** 100% of tested non-existent entities ("Unicorn Sparkles", "xyz totally unrelated", "weather in Mumbai") returned truthful `NOT_FOUND` without cross-domain bleed into employee finance or unrelated productions.
