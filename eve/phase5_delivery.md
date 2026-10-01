# EVE Phase 5 Delivery Report: Local Model Integration (Qwen3-4B-Q4_K_M)

## Executive Summary

Phase 5 transitions EVE from fixture-based semantic interpretation to a genuinely local, offline conversational intelligence system by integrating `Qwen3-4B-Q4_K_M.gguf`. The integration maintains 100% architectural integrity with Phases 1–4:
- **Local Native Inference Runtime**: Powered by a localhost-only llama.cpp native inference engine (`llama-server.exe` build b11240) managed directly within Java process lifecycle boundaries. Zero Python/Node runtimes, zero vector databases, zero external services.
- **Strict Offline Operation**: 100% offline with zero cloud API dependencies and zero remote network calls when `app.eve.model-provider=LOCAL_QWEN`.
- **Architectural Governance**: The local model operates strictly as an interpreter/cognitive parser and response formulator; PostgreSQL remains the canonical system of record, Phase 3 Command Gateway remains the sole pathway for write mutations, and Phase 4 continuous intelligence continues to tap domain telemetry.
- **Dual Model Provider Boundary**: Fast, deterministic `TestModelProvider` for automated CI/`mvn test` execution without requiring the 2.5 GB model file; `LocalQwenModelProvider` for production and native local runtime.
- **Identity Neutrality**: The user display role in the chat interface is strictly "You", while backend context and execution tracking dynamically bind to the authenticated operator principal from Spring Security (fallback "Operator").
- **Groundedness & Anti-Hallucination**: Queries requesting facts not present in evidence (e.g. phone numbers, profit margins, cancellation reasons, client quotes) return an explicit statement that the detail is not recorded.
- **Truthful Model Failure**: Model failure, timeout, or unavailable states are truthfully observable via execution trace (`MODEL_FALLBACK` with `WARN` status) rather than silently presented as successful local model intelligence.

---

## 1. Technical Reconnaissance & Runtime Architecture

### Runtime Discovery
1. **Initial Assessment**: Evaluated JNI bindings (`de.kherud:llama:4.2.0`, llama.cpp build 4916). The build lacked support for the Qwen3 architecture (`unknown model architecture: 'qwen3'`).
2. **Official Native Engine**: Discovered and deployed the official native `llama-server.exe` (build b11240) with full Qwen3 support.
3. **Execution Topology**:
   - Location: `eve/runtime/llama-server/llama-server.exe` (strictly gitignored).
   - Port: Localhost port `8089` (`http://127.0.0.1:8089`).
   - Parameters: `--port 8089 -m eve/models/Qwen3-4B-Q4_K_M.gguf -c 2048 -t 6 --reasoning off --reasoning-effort none --log-disable`.
   - Bounded Concurrency: Managed by a Java `Semaphore(1)` to enforce single-flight local inference and eliminate memory/CPU starvation.
   - Lifecycle Management: `LocalQwenModelProvider` starts, polls `/health`, monitors, and terminates the native process upon Spring container lifecycle events (`@PostConstruct` / `@PreDestroy`).

---

## 2. Implementation Inventory

### Backend Components
1. **`LocalQwenModelProvider.java`**
   - Implements `EveModelProvider` and `EveResponseComposer`.
   - Manages native `llama-server.exe` process lifecycle, health checks, prompt formatting, strict JSON schema parsing, grounded response composition, and timeout/error handling.
   - Bounded concurrency with `Semaphore(1)` ensuring robust single-flight execution.
2. **`EveResponseComposer.java`**
   - Clean interface for natural language synthesis grounded strictly in domain facts.
   - Separated from `EveModelProvider` to preserve the structural invariant asserted by `EveSecurityBoundaryTest` (that `EveModelProvider` exposes only `interpret(...)`).
3. **`TestModelProvider.java`**
   - Implements `EveModelProvider` and `EveResponseComposer`. Active when `app.eve.model-provider=TEST` (default).
   - Allows all existing test suites (289 backend tests) to execute deterministically in seconds without downloading or running the model.
   - Groundedness simulation: explicitly flags unrecorded attributes (phone, profit, cancellation reason, quotes).
4. **`EveDtos.java`**
   - Added `EveStatusView` record exposing model provider, health status, model name, model version, and diagnostic details.
5. **`EveService.java` & `EveController.java`**
   - Integrated response composition step using `EveResponseComposer` with truthful trace recording (`RESPONSE_COMPOSED`, `RESPONSE_DETERMINISTIC`, `MODEL_FALLBACK`).
   - Dynamic operator identity resolution via Spring Security principal (eliminating hardcoded personal names).
   - Exposed `GET /api/v1/eve/status` endpoint for desktop UI telemetry.
6. **`EveRetrievalService.java` & `EveRetrievalRouter.java`**
   - Token-level fallback searching in `resolveProduction` and stop-word filtering to resolve colloquial/typo queries.
   - Disambiguation over generic `NOT_FOUND`: When a pronoun follows an active production with multiple crew members, returns `CLARIFICATION_REQUIRED` with candidate crew members. If single crew member, resolves automatically.
   - Enriched production detail retrieval with client, date, venue, status, crew members, equipment reservations, and open tasks.

### Frontend Components
1. **`eve.types.ts` & `eve.api.ts`**
   - Added `EveStatusView` type and `eveApi.getStatus()` method.
2. **`EvePage.tsx`**
   - User message display name strictly `"You"`, avatar initial `"Y"`.
   - Added status badge displaying local intelligence status:
     - `INITIALIZING`: "EVE is starting its local intelligence..." (amber pulse).
     - `READY`: "Local Intelligence (Qwen3 4B) · b11240" (emerald badge).
     - `UNAVAILABLE`: "EVE's local model isn't available right now" (rose badge).
3. **`EvePage.test.tsx`**
   - Mocked `getStatus` returning `READY`.
   - Verified status badge rendering and `"You"` user role display.

---

## 3. Real HTTP Verification (10 Conversational Scenarios)

All 10 conversational scenarios were executed against the running backend with `EVE_MODEL_PROVIDER=LOCAL_QWEN` and the local `Qwen3-4B-Q4_K_M.gguf` model:

| Turn | Prompt | Intent / Route | HTTP Status | Latency | Outcome & Factual Precision |
|------|--------|----------------|-------------|---------|-----------------------------|
| 1 | `"hey eve"` | `GREETING` | `COMPLETED` | 23.6s (cold) | Welcomed authenticated operator cordially without hardcoded personal names. |
| 2 | `"details on Cultural Event MIPS"` | `READ_PRODUCTION` | `COMPLETED` | 35.6s | Retrieved client (Nandhini Srivastava), date (Sep 28), venue (MIPS), status (Draft), 3 crew, 1 equipment reservation, 3 open tasks. Session context set. |
| 3 | `"culturl evnt mips ka clint kon h"` | `READ_PRODUCTION_CLIENT` | `COMPLETED` | 22.5s | Handled typo + colloquial Hinglish; answered: client is Nandhini Srivastava. |
| 4 | `"mips wale event me kaun kaam kar raha hai?"` | `READ_PRODUCTION_CREW` | `COMPLETED` | 17.0s | Retrieved 3 crew members working on MIPS event. |
| 5 | `"usme kaunsa task open hai?"` | `READ_PRODUCTION_TASKS` | `COMPLETED` | 15.7s | Resolved pronoun "usme" to MIPS event; identified 3 open tasks. |
| 6 | `"how much do we still owe him?"` | `READ_EMPLOYEE_FINANCE` | `CLARIFICATION_REQUIRED` | 14.1s | Pronoun following active production with multiple crew members correctly returns clarification candidates. |
| 6b | `"how much do we still owe Kabir?"` | `READ_EMPLOYEE_FINANCE` | `COMPLETED` | 15.8s | Factual ledger check: Kabir Singh balance ₹0.00 (earned ₹500, paid ₹500). Context set to Kabir. |
| 7 | `"pay him 3000"` | `PROPOSE_EMPLOYEE_PAYMENT` | `COMPLETED` | 10.8s | Resolved "him" to Kabir; domain check blocked proposal because balance is ₹0. Invariant held. |
| 7b | `"Sharma ko 3000 de do"` | `PROPOSE_EMPLOYEE_PAYMENT` | `WAITING_CONFIRMATION` | 10.9s | Generated Phase 3 Plan `5d69fdc8-...` in status `PROPOSED`. Zero ledger mutations occurred. |
| 8 | `"thanks"` | `GREETING` | `COMPLETED` | 12.9s | Polite closing response acknowledging operator. |
| 9 | `"led ka client kon hai"` | `READ_PRODUCTION_CLIENT` | `CLARIFICATION_REQUIRED` | 8.6s | Returned 2 candidates ("led" vs "LED") for operator disambiguation. |
| 10 | `"xyz nonexistent production ka client kaun hai"` | `READ_PRODUCTION_CLIENT` | `NOT_FOUND` | 9.0s | Handled nonexistent entity gracefully without hallucinating. |

---

## 4. Inference Profiling & Generation Performance

- **Model Specification**: Qwen3-4B-Instruct quantized to 4-bit Medium (`Qwen3-4B-Q4_K_M.gguf`, file size: ~2.50 GB).
- **Context Length**: Configured at 2048 tokens (`-c 2048`).
- **Threads**: 6 CPU worker threads (`-t 6`).
- **Offload Configuration**: Configured with `-ngl 0` (CPU-only default, portable across all development environments). When Vulkan/CUDA GPU layers are present, `-ngl 33` offloads all 33 transformer layers to VRAM.
- **Dual-Generation Execution**:
  1. `interpret`: Cognitive JSON parsing (prompt ~200 tokens, completion ~60 tokens, `temp: 0.0`, `max_tokens: 150`). Latency: ~8–12 seconds on CPU.
  2. `composeResponse`: Grounded response synthesis (prompt ~350 tokens, completion ~100 tokens, `temp: 0.3`, `max_tokens: 350`). Latency: ~10–15 seconds on CPU.
- **Single-Flight Guard**: Guaranteed by Java `Semaphore(1)` to eliminate GPU/CPU thrashing under concurrent desktop usage.

---

## 5. Test Suite & Verification Results

1. **Backend Tests (`mvn test`)**:
   - Total Tests Run: **289**
   - Failures: **0**
   - Errors: **0**
   - Skipped: **43**
   - Status: **BUILD SUCCESS**
2. **Phase 5 Dedicated Test (`LocalQwenModelIntegrationTest`)**:
   - Total Tests: **6**
   - Status: **BUILD SUCCESS** (Verifies status diagnostics, blank prompt handling, unreachable server handling, fallback safety, null safety, and anti-hallucination unrecorded facts check).
3. **Security Invariant Test (`EveSecurityBoundaryTest`)**:
   - Total Tests: **15**
   - Status: **BUILD SUCCESS** (Structural single-method invariant on `EveModelProvider.class` preserved).
4. **PostgreSQL Integration Test (`EvePostgresIntegrationTest`)**:
   - Total Tests: **12**
   - Status: **BUILD SUCCESS** (Full Phase 1–4 database verification against live PostgreSQL container `sa-command-postgres`).
5. **Desktop Frontend Tests (`npm test -- --run`)**:
   - Total Test Files: **21 passed (21)**
   - Total Tests: **89 passed (89)**
   - Status: **SUCCESS**
6. **Desktop Production Build (`npm run build`)**:
   - Vite v6.4.3: **Built in 10.51s with zero TypeScript or bundling errors**.

---

## 6. Security & Git Hygiene Audit

1. **Model Files**: `eve/models/Qwen3-4B-Q4_K_M.gguf` is verified gitignored (`git check-ignore` confirms ignore rule).
2. **Native Runtime**: `eve/runtime/` is strictly gitignored to prevent committing platform-specific binaries.
3. **Sensitive Logs**: Model reasoning tokens (`--reasoning off`, `--reasoning-effort none`) are disabled, ensuring clean observable logs.
4. **Governed Execution**: All business mutations remain protected by the Phase 3 Command Gateway, explicit preview confirmation, and immutable double-entry ledger rules.
