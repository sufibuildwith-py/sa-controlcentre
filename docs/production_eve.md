# SA Command — EVE Production Architecture & Deployment Requirements

Date: October 2026  
Status: Production Architecture Specification  
Reference: SA Command EVE Intelligence Layer

---

## 1. Architectural Principles

EVE is a strictly governed intelligence layer for SA Command. It adheres to the following non-negotiable boundaries:

1. **System of Record**: SA Command PostgreSQL and domain services (`ProductionService`, `EmployeeService`, `FinancePostingService`, etc.) are the authoritative source of truth. EVE is NOT a second ledger and maintains no independent state of record.
2. **No Arbitrary SQL / Code Execution**: EVE does not generate or execute arbitrary SQL queries against PostgreSQL. Tool invocations map exclusively to typed, audited Java domain methods in `EveCognitiveToolRegistry`.
3. **No Shell or Unrestricted Network Access**: EVE possesses no access to OS shells, subprocess spawns, or unvetted external HTTP endpoints.
4. **Governed Mutation via Plan Binding**: High-impact operations (e.g., employee payments, schedule alterations) are framed as an `EvePlan` requiring explicit human operator confirmation. The confirmation request binds the exact plan session, version, and parameters.
5. **Separation of Concerns**: Memory entries (`EveMemoryService`) and vocabulary definitions are operational preferences only—they cannot override domain ledger constraints or business rules.

---

## 2. Model Provider Architecture

EVE is decoupled from specific model backends via the `EveModelProvider` interface:

```
[User Query]
      │
      ▼
[EveService / EveCognitiveRuntime]
      │
      ├──> Intent & Entity Parsing via EveModelProvider
      │      ├── TEST (Deterministic grammar parser; 0 external dependencies)
      │      └── LOCAL_QWEN_THINKING (HTTP client to OpenAI/llama.cpp server)
      │
      ├──> Semantic Disambiguation via EveSemanticResolutionService
      │
      ├──> Canonical Tool Execution via EveCognitiveToolRegistry
      │      └── Calls ProductionService, EmployeeService, etc.
      │
      └──> Response Composition Grounded in Canonical Evidence
```

---

## 3. Production Environment Reality & Limitations

### The Local Laptop Dependency
During development, EVE utilized:
- Binary: `eve/runtime/llama-server/llama-server.exe` (Windows x64 binary)
- Model: `eve/models/Qwen3-4B-Thinking-2507.Q4_K_M.gguf` (2.7 GB quantized weights)
- Embeddings: `eve/models/Qwen3-Embedding-0.6B-Q8_0.gguf` (639 MB)
- Reranker: `eve/models/Qwen3-Reranker-0.6B-Q8_0.gguf` (639 MB)

These binaries and model files are:
1. Excluded from version control via `.gitignore`.
2. Windows-specific (`.exe`), meaning they cannot execute in standard Linux cloud containers (e.g., Railway, AWS ECS, Fly.io) without Linux builds of `llama-server`.
3. Computationally intensive, requiring substantial CPU/RAM or GPU acceleration.

### Production Policy: No Silent Fallback
SA Command **strictly forbids**:
- Silent fallback to unauthorized cloud APIs (OpenAI, Anthropic, Google) without explicit operational configuration.
- Fake production claims that EVE LLM is active when no inference runtime exists.

---

## 4. Operational Modes & Production Reality

Core SA Command (People, Productions, Attendance, Work, Meetings, Billing, Finance, Privacy Veil) is **production-ready independently** of EVE.

EVE operates in exactly one of three distinct modes:

### 1. REAL AI PROVIDER (Production EVE Intelligence)
- **Configuration**:
  ```env
  EVE_MODEL_PROVIDER=LOCAL_QWEN_THINKING
  EVE_LOCAL_MODEL_URL=https://your-dedicated-ai-host:8090
  ```
- **Nature**: Actual LLM inference powered by Qwen3-4B-Thinking or equivalent model.
- **Requirements**: Dedicated external GPU or inference host running `vLLM`, `llama.cpp`, or `ollama`.

### 2. TEST / DETERMINISTIC PROVIDER (Safe Degraded / Testing Mode Only)
- **Configuration**:
  ```env
  EVE_MODEL_PROVIDER=TEST
  ```
- **Nature**: **NOT real AI inference**. Purely grammar- and regex-based slot extraction.
- **Representation**: Must NEVER be represented as an AI model or production AI intelligence. It exists solely as a safe degraded fallback and automated test harness.

### 3. UNAVAILABLE (Explicit Failure State)
- **Nature**: If an external AI provider is configured but unreachable, times out, or fails schema validation, EVE enters an explicit `EVE_MODEL_FAILED` / `UNAVAILABLE` state.
- **Safety Invariant**: Zero silent fallback to public cloud APIs. Domain operations remain fully functional while EVE surfaces a transparent error code.
