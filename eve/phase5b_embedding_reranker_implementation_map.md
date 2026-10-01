# EVE Phase 5B Implementation Map: Embedding & Reranker Intelligence

This document provides a comprehensive mapping of all code components, configuration properties, interfaces, data contracts, and scoring mechanisms implemented in **EVE Phase 5B**.

---

## 1. File Map & Architectural Responsibilities

### Semantic Subsystem: `com.saproduction.command.eve.semantic`

| File | Type | Architectural Responsibility |
|---|---|---|
| [`EveEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveEmbeddingProvider.java) | Interface | Declares dense embedding contracts (`embedQuery`, `embedCandidate`, `embedBatch`, `isAvailable`, `getModelName`, `getDimension`). |
| [`EveRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveRerankerProvider.java) | Interface | Declares cross-encoder reranking contracts (`rerank(query, documents, instruction, topK)` returning `RerankResult(index, score, document)`). |
| [`EveSemanticCandidate.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCandidate.java) | Record | Bounded representation of canonical entities (`PRODUCTION`, `EMPLOYEE`). Strips sensitive data; constructs search representation; transforms to `EveRetrievalService.Candidate`. |
| [`EveSemanticCache.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCache.java) | Component | In-memory `ConcurrentHashMap<UUID, CachedVector>`. Enforces two-tier cache invalidation: (1) `canonicalUpdatedAt` advancement, and (2) SHA-256 representation text hash change. |
| [`EveSemanticPolicy.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticPolicy.java) | Component | Enforces confidence gates (`minScore = 0.35`), margin gates (`minMargin = 0.08`), and active conversation focus affinity (`+0.15`). Produces typed `PolicyDecision`. |
| [`TestEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestEmbeddingProvider.java) | Component | Deterministic 512-dimension character-trigram dense vector provider for CI (`app.eve.semantic.provider=TEST`). Zero GPU requirement. |
| [`TestRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestRerankerProvider.java) | Component | Deterministic cross-encoder reranker for CI utilizing query-coverage and Levenshtein typo distance (`provider=TEST`). |
| [`LocalQwenEmbeddingProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenEmbeddingProvider.java) | Component | Manages native `llama-server.exe` on port 8087 (`--embedding`). Bounded single-flight inference with `Semaphore(1)` and 5s timeout. |
| [`LocalQwenRerankerProvider.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenRerankerProvider.java) | Component | Manages native `llama-server.exe` on port 8088 (`--rerank`). Bounded single-flight inference with `Semaphore(1)` and 5s timeout. |
| [`EveSemanticResolutionService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticResolutionService.java) | Service | End-to-end resolution coordinator: candidate generation $\rightarrow$ relational clue traversal $\rightarrow$ embedding $\rightarrow$ reranking $\rightarrow$ policy decision. |

### Integration & Domain Touchpoints

| File | Changes Made |
|---|---|
| [`EveRetrievalService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalService.java) | Added `SEMANTIC_MATCH` and `SEMANTIC_RERANKED` provenance types. Added overloaded `resolveProduction` and `resolveEmployee` with `SessionContext`. Delegated to `EveSemanticResolutionService` at step 2B/3B when deterministic matches fail. |
| [`EveRetrievalRouter.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalRouter.java) | Added `resolveEmployeeSafe` and `resolveProductionSafe` helpers to ensure full backward compatibility with single-argument test mocks. Threaded `sessionContext` through all entity lookups. |
| [`EveService.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/eve/EveService.java) | Injected optional `EveSemanticResolutionService`. Enriched `getStatus()` to expose embedding and reranker health telemetry. |
| [`ProductionMemberRepository.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/java/com/saproduction/command/production/ProductionMemberRepository.java) | Added `List<ProductionMember> findByEmployeeId(UUID employeeId)` for canonical crew relationship traversal. |
| [`application.yml`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/main/resources/application.yml) | Externalized all semantic resolution properties: ports, model names, timeouts, thresholds, and candidate limits. |

---

## 2. Configuration Properties Reference

```yaml
app:
  eve:
    semantic:
      enabled: ${EVE_SEMANTIC_ENABLED:true}
      provider: ${EVE_SEMANTIC_PROVIDER:TEST}  # Options: TEST (default for CI) or LOCAL_QWEN
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

## 3. Mathematical Formulae & Policy Scoring

1. **Stage 1 (Cosine Similarity)**:
   $$\text{sim}(\mathbf{q}, \mathbf{d}) = \frac{\mathbf{q} \cdot \mathbf{d}}{\|\mathbf{q}\|_2 \|\mathbf{d}\|_2}$$

2. **Stage 2 (Cross-Attention Relevance)**:
   $$\text{rerankScore} = \text{cross\_encoder}(\text{userQuery}, \text{candidateDocument})$$

3. **Blended Candidate Score**:
   $$\text{combinedScore} = (0.7 \times \text{rerankScore}) + (0.3 \times \text{embeddingScore})$$

4. **Contextual Affinity Boosts**:
   - Active Focus Affinity: If candidate equals `sessionContext.getActiveProduction().id()`, then:
     $$\text{score} \leftarrow \min(1.0, \text{score} + 0.15)$$
   - Relational Assignment Affinity: If candidate is assigned to referenced employee via PostgreSQL `production_members`, then:
     $$\text{score} \leftarrow \min(1.0, \text{score} + 0.20)$$

5. **Decision Classification**:
   - If $\text{topScore} < 0.35 \implies \text{LOW\_CONFIDENCE} \rightarrow \text{NOT\_FOUND}$
   - If $(\text{topScore} - \text{secondScore}) < 0.08 \implies \text{AMBIGUOUS} \rightarrow \text{CLARIFICATION\_REQUIRED}$
   - Else $\implies \text{RESOLVED}$ (authoritative PostgreSQL state loaded).

---

## 4. Benchmark Verification Suite

Located at [`EveSemanticResolutionTest.java`](file:///c:/Users/xtrar/Desktop/ERP/apps/backend/src/test/java/com/saproduction/command/eve/semantic/EveSemanticResolutionTest.java):
1. `testColloquialHindiReferenceResolution` — Verifies *"Mips wala event"* resolves to `Cultural Event MIPS`.
2. `testTypoReferenceResolution` — Verifies *"culturl evnt mips"* resolves to `Cultural Event MIPS`.
3. `testRelationshipClueResolution` — Verifies *"Kabir wala production"* traverses crew assignment and resolves to `Cultural Event MIPS`.
4. `testAmbiguityPreservedWhenMarginTooSmall` — Verifies *"Annual Gala Corp"* matching two seasons produces `AMBIGUOUS` with candidate list.
5. `testNonexistentEntityReturnsNotFound` — Verifies unrelated query returns `NOT_FOUND`.
6. `testPronounBypass` — Verifies raw pronouns (`"uska"`, `"unka"`) are rejected at the boundary.
7. `testActiveFocusAffinity` — Verifies generic continuation (`"event details"`) resolves to currently focused entity.
8. `testCacheInvalidation` — Verifies vector cache eviction on timestamp or text hash change.
9. `testRetrievalServiceDelegation` — Verifies end-to-end integration into `EveRetrievalService`.
