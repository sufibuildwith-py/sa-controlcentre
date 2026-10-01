# EVE Cognitive Runtime 2.0 — Architecture & Delivery Report

## Executive Summary

EVE Cognitive Runtime 2.0 upgrades EVE from a closed-world entity router into an open-world, context-aware cognitive operator within SA Command. 

### Core Capabilities Delivered:
1. **Open-World Capability Routing**:
   - **General Capabilities**: Arithmetic expressions (`30-10+4-12` $\rightarrow$ `12`), general workplace advice (catering/lunch menu suggestions), temporal reasoning, pleasantries.
   - **External Capabilities**: External services like live weather (`Aaj mausam acha hai, kya barish hogi?`) routed to `UNSUPPORTED_CAPABILITY` with natural advice, never incorrectly claiming entity not found.
   - **SA Command Canonical Capabilities**: Production, crew, tasks, finance, and equipment queries strictly bounded to authoritative PostgreSQL state.
2. **Deterministic Fast Paths (< 1ms)**:
   - Evaluates pure mathematical expressions safely via a recursive-descent parser without invoking the heavy local LLM (~200s latency eliminated).
3. **Decision Support & Uncertainty Boundaries**:
   - Queries asking whether to invest (`kya mujhe 50000 sharma wedding wale event me invest karne chahiye?`) or asking for speculative profit (`MIPS event ka profit kitna hoga?`) present verified ledger facts, explicitly clarify that speculative forecasts are unrecorded (`INSUFFICIENT_EVIDENCE`), and never fabricate facts or trigger unconfirmed mutations.
4. **Natural Context Continuity & Pronoun Resolution**:
   - Follow-up questions like `"aur uska equipment?"` resolve `"uska"` to the active production in context, switching operations from CLIENT to EQUIPMENT without leaking irrelevant fields.
   - Orthogonal domain switches (e.g., asking about an employee right after a production) cleanly shift focus without cross-contamination.
5. **Candidate Set Tracking & Disambiguation**:
   - Tracks active candidate lists across turns with 1-based index and Hindi/Hinglish ordinal hints (`wahi second wala`, `dusra wala`, `pehla`).
   - If an ordinal is referenced without an active list in session, asks `"Kaunsi list ki second item ki baat kar rahe ho?"` instead of hallucinating.
6. **Mandatory Language & Register Matching**:
   - Automatically detects user language (`ENGLISH`, `HINDI`, `HINGLISH`) and register (`CASUAL`, `PROFESSIONAL`, `CONCISE`) and responds in the same language.
7. **Governed Write Safety**:
   - Phase 3 write actions (e.g., `Pay Kabir 3000`) continue to generate strict proposals requiring explicit confirmation tokens; zero unconfirmed mutations exist.

---

## File Map

| File | Purpose |
| :--- | :--- |
| [`EveOperation.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveOperation.java) | Typed cognitive operations: `READ, COUNT, FILTER, COMPARE, RANK, AGGREGATE, CALCULATE, EXPLAIN, SUMMARIZE, RECOMMEND, DECISION_SUPPORT, GENERAL_CONVERSATION, EXECUTE, CLARIFY`. |
| [`EveOutcome.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveOutcome.java) | Principled outcome taxonomy: `COMPLETED, CLARIFICATION_REQUIRED, NOT_FOUND, UNSUPPORTED_CAPABILITY, INSUFFICIENT_EVIDENCE, VALIDATION_FAILED, SYSTEM_UNAVAILABLE, MODEL_FAILED, POLICY_BLOCKED, STALE_DATA, EXECUTION_REQUIRED`. |
| [`EveArithmeticCapability.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveArithmeticCapability.java) | Safe, deterministic recursive-descent math parser (< 1ms execution, no eval/scripting). |
| [`EveLanguageDetector.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveLanguageDetector.java) | Detects Hindi (Devanagari), Hinglish (transliterated particles), English, and register. |
| [`EveCandidateSet.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveCandidateSet.java) | Structured candidate state with 1-based index and ordinal hint resolution. |
| [`EveCognitiveState.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveCognitiveState.java) | Continuous session state tracking subject, domain, operation, active entities, and evidence. |
| [`EveCapabilityRegistry.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveCapabilityRegistry.java) | Finite registry of General, SA Command, and External capabilities. |
| [`EveGeneralReasoningService.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveGeneralReasoningService.java) | Workplace recommendations, weather capability boundaries, and conversational pleasantries. |
| [`EveDecisionSupportService.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveDecisionSupportService.java) | Evaluates profit and investment inquiries with verified ledger figures. |
| [`EveCognitiveRuntime.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/cognitive/EveCognitiveRuntime.java) | Primary cognitive coordinator wiring fast paths, local Qwen, bounded tools, and reasoning. |
| [`EveService.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveService.java) | Dispatches incoming prompts to Cognitive Runtime 2.0 while preserving Phase 3 safety. |
| [`EveRetrievalService.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalService.java) | Disambiguation candidate selection updated with Hindi ordinal keywords (`dusra`, `pehla`). |
| [`EveCognitiveRuntime2Test.java`](file:///C:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/cognitive/EveCognitiveRuntime2Test.java) | Unit & black-box suite validating Cognitive Runtime 2.0 behaviors. |

---

## Verification Evidence

1. **Cognitive Runtime 2.0 Suite**:
   ```
   [INFO] Running com.saproduction.command.eve.cognitive.EveCognitiveRuntime2Test
   [INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.765 s
   [INFO] BUILD SUCCESS
   ```
2. **Cognitive Brain Suite**:
   ```
   [INFO] Running com.saproduction.command.eve.cognitive.EveCognitiveBrainTest
   [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.868 s
   [INFO] BUILD SUCCESS
   ```
3. **Semantic Resolution Suite**:
   ```
   [INFO] Running com.saproduction.command.eve.semantic.EveSemanticResolutionTest
   [INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.465 s
   [INFO] BUILD SUCCESS
   ```
4. **Sharma Wedding Multi-Turn Disambiguation Suite**:
   ```
   [INFO] Running com.saproduction.command.eve.EveSharmaWeddingResolutionTest
   [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.255 s
   [INFO] BUILD SUCCESS
   ```
5. **PostgreSQL End-to-End Integration Suite**:
   ```
   [INFO] Running com.saproduction.command.eve.EvePostgresIntegrationTest
   [INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 17.77 s
   [INFO] BUILD SUCCESS
   ```
6. **Desktop Frontend Build**:
   ```
   vite v6.4.3 building for production...
   ✓ built in 6.44s
   ```
