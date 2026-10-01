# EVE Semantic Context Resolution Implementation Map

This document maps all architectural components, configuration flags, interface contracts, and lifecycle patterns introduced or modified in the **EVE Semantic Context Resolution Upgrade** (`Qwen3-Embedding-0.6B` + `Qwen3-Reranker-0.6B`).

---

## 1. File Map & Responsibilities

### Core Semantic Subsystem (`com.saproduction.command.eve.semantic`)

| File | Type | Primary Role & Invariants |
|---|---|---|
| [`EveEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveEmbeddingProvider.java) | Interface | Defines query, candidate, and batch dense embedding. Methods: `embedQuery`, `embedCandidate`, `embedBatch`, `isAvailable`, `getModelName`, `getDimension`. |
| [`EveRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveRerankerProvider.java) | Interface | Defines cross-encoder document reranking. Methods: `rerank(query, documents, instruction, topK)`, `isAvailable`, `getModelName`. Exposes `RerankResult(int index, double score, String document)`. |
| [`EveSemanticCandidate.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCandidate.java) | Record | Bounded, non-sensitive canonical candidate representation for `PRODUCTION` and `EMPLOYEE`. Never holds credentials or sensitive financial numbers. Converts to `EveRetrievalService.Candidate`. |
| [`EveSemanticCache.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCache.java) | Component | In-memory `ConcurrentHashMap<UUID, CachedVector>`. Automatically invalidates on `canonicalUpdatedAt.isAfter(entry.canonicalUpdatedAt)` or SHA-256 text representation hash change. Provides static `cosineSimilarity`. |
| [`EveSemanticPolicy.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticPolicy.java) | Component | Governed confidence and margin policy. Evaluates candidate list against `minScore` (0.35) and `minMargin` (0.08). Applies active focus affinity boost (+0.15). Produces `RESOLVED`, `AMBIGUOUS`, `LOW_CONFIDENCE`, or `NOT_FOUND`. |
| [`TestEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestEmbeddingProvider.java) | Component | Deterministic, character-trigram dense vector generator (512 dimensions) for CI and offline builds (`provider=TEST`). Zero GPU requirement, sub-millisecond execution. |
| [`TestRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestRerankerProvider.java) | Component | Deterministic cross-encoder provider using token-level query coverage and Levenshtein typo distance (`provider=TEST`). |
| [`LocalQwenEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenEmbeddingProvider.java) | Component | Manages native `llama-server.exe` process for `Qwen3-Embedding-0.6B-Q8_0.gguf` on port 8087 (`--embedding`). Enforces `Semaphore(1)` single-flight execution and 5s timeout boundaries. |
| [`LocalQwenRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenRerankerProvider.java) | Component | Manages native `llama-server.exe` process for `Qwen3-Reranker-0.6B-Q8_0.gguf` on port 8088 (`--rerank`). Enforces `Semaphore(1)` single-flight execution and 5s timeout boundaries. |
| [`EveSemanticResolutionService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticResolutionService.java) | Service | Orchestrates end-to-end two-stage semantic resolution: Candidate bounding $\rightarrow$ Relationship traversal (via `ProductionMemberRepository`) $\rightarrow$ Dense embedding $\rightarrow$ Cross-encoder reranking $\rightarrow$ Policy evaluation. |

### Integration Touchpoints

| File | Changes Made |
|---|---|
| [`EveRetrievalService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalService.java) | Added `SEMANTIC_MATCH` and `SEMANTIC_RERANKED` match methods; added overloaded `resolveProduction` and `resolveEmployee` taking `SessionContext`; delegated to `EveSemanticResolutionService` at Stage 2B/3B after exact matches fail. |
| [`EveRetrievalRouter.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalRouter.java) | Wired `SessionContext` into entity resolution; added `resolveEmployeeSafe` and `resolveProductionSafe` to gracefully preserve 1-arg mock compatibility in existing unit test suites. |
| [`ProductionMemberRepository.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/production/ProductionMemberRepository.java) | Added `List<ProductionMember> findByEmployeeId(UUID employeeId)` for canonical relational clue traversal. |
| [`application.yml`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/resources/application.yml) | Added `app.eve.semantic.*` configuration block with defaults for provider, model paths, ports, thresholds, and server timeouts. |

---

## 2. Configuration Properties

```yaml
app:
  eve:
    semantic:
      enabled: ${EVE_SEMANTIC_ENABLED:true}
      provider: ${EVE_SEMANTIC_PROVIDER:TEST}  # TEST (default, for CI) or LOCAL_QWEN
      models-dir: ${EVE_MODELS_DIR:../../eve/models}
      embedding:
        model: ${EVE_EMBEDDING_MODEL:Qwen3-Embedding-0.6B-Q8_0.gguf}
        port: ${EVE_EMBEDDING_PORT:8087}
        dimension: 1024
        server-binary: ${EVE_LLAMA_SERVER_BINARY:../../eve/runtime/llama-server/llama-server.exe}
        timeout-ms: 5000
      reranker:
        model: ${EVE_RERANKER_MODEL:Qwen3-Reranker-0.6B-Q8_0.gguf}
        port: ${EVE_RERANKER_PORT:8088}
        server-binary: ${EVE_LLAMA_SERVER_BINARY:../../eve/runtime/llama-server/llama-server.exe}
        timeout-ms: 5000
      resolver:
        min-score: 0.35
        min-margin: 0.08
        max-candidates: 20
```

---

## 3. Two-Stage Scoring Formula

In `EveSemanticResolutionService`:
1. **Stage 1 (Embedding Similarity)**:
   $$\text{embeddingScore} = \cos(\mathbf{q}, \mathbf{c}) = \frac{\mathbf{q} \cdot \mathbf{c}}{\|\mathbf{q}\|_2 \|\mathbf{c}\|_2}$$
2. **Stage 2 (Reranker Cross-Attention)**:
   $$\text{rerankerScore} = \text{cross\_encoder}(\text{rawQuery}, \text{candidateDocument})$$
3. **Blended Score**:
   $$\text{combinedScore} = (0.7 \times \text{rerankerScore}) + (0.3 \times \text{embeddingScore})$$
4. **Relational / Focus Affinity Boosts**:
   - If candidate is in active conversation focus: $\text{score} \leftarrow \min(1.0, \text{score} + 0.15)$
   - If candidate matches canonical PostgreSQL assignment (e.g. employee $\rightarrow$ production): $\text{score} \leftarrow \min(1.0, \text{score} + 0.20)$
5. **Decision Invariant**:
   - If $\text{topScore} < 0.35 \implies \text{LOW\_CONFIDENCE} \rightarrow \text{NOT\_FOUND}$
   - If $\text{topScore} - \text{secondScore} < 0.08 \implies \text{AMBIGUOUS} \rightarrow \text{CLARIFICATION\_REQUIRED}$
   - Else $\implies \text{RESOLVED}$ (authoritative PostgreSQL state returned).
