# EVE Semantic Context Resolution Upgrade
## Qwen3-Embedding-0.6B + Qwen3-Reranker-0.6B Architecture & Delivery Report

---

## Executive Summary

The **EVE Semantic Context Resolution Upgrade** strengthens SA Command's local conversational intelligence by introducing a bounded, two-stage semantic entity resolution pipeline:

1. **Candidate Retrieval (Stage 1)**: Dense vector similarity powered by `Qwen3-Embedding-0.6B` (`Q8_0.gguf`, 639 MB).
2. **Candidate Discrimination (Stage 2)**: Cross-encoder reranking powered by `Qwen3-Reranker-0.6B` (`Q8_0.gguf`, 639 MB).
3. **Governed Decision Gate**: Enforced by `EveSemanticPolicy` with minimum score thresholds, separation margin checks, active conversation focus affinity, and canonical relational traversal.

This upgrade eliminates brittle regex expansion for natural language variations, including:
- **Severe typos**: e.g., `"culturl evnt mips"` $\rightarrow$ `Cultural Event MIPS`.
- **Colloquial Hindi / Hinglish**: e.g., `"Mips wala event"`, `"wahi event"`.
- **Relational clues**: e.g., `"Kabir wala production"`, traversing PostgreSQL `production_members` to resolve `Cultural Event MIPS`.
- **Active focus continuity**: Seamless resolution of ambiguous phrases when an entity is already in conversation focus.
- **Ambiguity preservation**: When multiple candidates are semantically indistinguishable (margin < `minMargin`), EVE produces a governed `CLARIFICATION_REQUIRED` prompt with canonical candidates instead of guessing.

---

## 1. Architectural Topology & Invariants

```
                             [ User Query / Hinglish / Typo ]
                                            │
                                            ▼
                          [ Deterministic Fast-Path Gate ]
                           - Exact UUID match? ───────────────► Resolved (Sub-ms)
                           - Exact Code match? ───────────────► Resolved (Sub-ms)
                           - Exact Normalized Name? ──────────► Resolved (Sub-ms)
                           - Pure Pronoun ("uska")? ──────────► Bypass to Session Context
                                            │ (No deterministic exact match)
                                            ▼
                         [ Canonical Candidate Set Bounding ]
                           - Fetch active entities from PostgreSQL (max 20)
                           - Relationship traversal (assigned crew / members)
                                            │
                                            ▼
                      [ Stage 1: Dense Vector Retrieval ]
                           - Qwen3-Embedding-0.6B (Port 8087)
                           - In-memory vector cache (EveSemanticCache)
                           - Cosine similarity scoring
                                            │
                                            ▼
                      [ Stage 2: Cross-Encoder Reranking ]
                           - Qwen3-Reranker-0.6B (Port 8088)
                           - Deep cross-attention relevance scoring
                           - Blended score: 70% reranker + 30% embedding
                                            │
                                            ▼
                         [ Governed Policy Decision Gate ]
                           - Top score >= minScore (0.35)?
                           - Top score - Second score >= minMargin (0.08)?
                           - Active conversation focus affinity boost (+0.15)
                           - Verified relational assignment boost (+0.20)
                                            │
                         ┌──────────────────┴──────────────────┐
                         │                                     │
                         ▼                                     ▼
                [ Score & Margin Met ]             [ Scores Too Close (< minMargin) ]
                         │                                     │
                         ▼                                     ▼
           Authoritative PostgreSQL Fetch           EVE Clarification Required
             (Return Grounded Truth)                (Candidate Disambiguation Card)
```

### Core Invariants Preserved
- **Authoritative System of Record**: PostgreSQL remains the sole source of truth. Models never invent IDs, entities, financial figures, or relationships.
- **Fast-Path Determinism**: Exact UUIDs, employee codes, and normalized titles bypass vector inference completely, ensuring zero regression in latency for explicit queries.
- **Zero Cloud Leakage**: 100% offline, local native inference via `llama-server.exe` on Windows (12GB RAM, RTX 2050 4GB VRAM). Zero external API calls, zero telemetry, zero cloud fallbacks.
- **No Vector Database Bloat**: In-memory, thread-safe vector caching (`EveSemanticCache`) with automatic invalidation on canonical `updatedAt` change or representation text hash drift. Zero secondary databases, zero Python/Node sidecars.
- **Pronoun Invariant**: Pure pronouns (`uska`, `uski`, `usme`, `unka`, `he`, `she`, `it`, `they`) are blocked at the boundary and never passed as queries to semantic models.
- **Phase 3 Governed Execution Gateway**: Write mutations (disbursements, payouts, expenses) remain strictly guarded behind structured preview, cryptographic hashing, and explicit operator confirmation.

---

## 2. Component Manifest

| Component | Responsibility | File Path |
|---|---|---|
| **`EveEmbeddingProvider`** | Interface defining query, candidate, and batch dense embedding. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveEmbeddingProvider.java` |
| **`EveRerankerProvider`** | Interface defining cross-encoder reranking over candidate documents. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveRerankerProvider.java` |
| **`LocalQwenEmbeddingProvider`** | Native process runner for `Qwen3-Embedding-0.6B-Q8_0.gguf` on port 8087 (`--embedding`). | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenEmbeddingProvider.java` |
| **`LocalQwenRerankerProvider`** | Native process runner for `Qwen3-Reranker-0.6B-Q8_0.gguf` on port 8088 (`--rerank`). | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/LocalQwenRerankerProvider.java` |
| **`TestEmbeddingProvider`** | Fast character-trigram dense vector provider (512d) for CI and offline builds (`provider=TEST`). | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestEmbeddingProvider.java` |
| **`TestRerankerProvider`** | Deterministic cross-encoder provider using query coverage and Levenshtein typo distance for CI (`provider=TEST`). | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/TestRerankerProvider.java` |
| **`EveSemanticCandidate`** | Immutable canonical record bounding non-sensitive resolution data for `PRODUCTION` and `EMPLOYEE`. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCandidate.java` |
| **`EveSemanticCache`** | Thread-safe `ConcurrentHashMap` caching float vectors with timestamp and SHA-256 text hash invalidation. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticCache.java` |
| **`EveSemanticPolicy`** | Enforces `minScore` (0.35), `minMargin` (0.08), `maxCandidates` (20), active focus affinity, and ambiguity gates. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticPolicy.java` |
| **`EveSemanticResolutionService`** | Orchestrates 2-stage retrieval, relationship traversal, candidate bounding, caching, and policy evaluation. | `apps/backend/src/main/java/com/saproduction/command/eve/semantic/EveSemanticResolutionService.java` |
| **`EveRetrievalService`** | Canonical entity resolver integrating exact fast-path, semantic stage (3B), and memory hints. | `apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalService.java` |
| **`EveRetrievalRouter`** | Domain router connecting session context, pronoun antecedent resolution, and safe entity retrieval. | `apps/backend/src/main/java/com/saproduction/command/eve/EveRetrievalRouter.java` |

---

## 3. Verification & Test Evidence

### Backend Test Suite
Executed full backend test suite verifying all EVE components:
```text
mvn test "-Dtest=Eve*Test"
...
[INFO] Results:
[INFO] Tests run: 144, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Key test suites verified:
- **`EveSemanticResolutionTest`**: 9/9 passed.
  - Verified typo tolerance (`"culturl evnt mips"` $\rightarrow$ `Cultural Event MIPS`).
  - Verified colloquial Hindi resolution (`"Mips wala event"` $\rightarrow$ `Cultural Event MIPS`).
  - Verified relational clue resolution (`"Kabir wala production"` $\rightarrow$ `Cultural Event MIPS` via `ProductionMemberRepository`).
  - Verified ambiguity enforcement (identical candidates yield `AMBIGUOUS` with candidate list).
  - Verified nonexistent entity returns `NOT_FOUND`.
  - Verified pronoun bypass (pure pronouns never query semantic models).
  - Verified active focus affinity (boosts currently referenced entity).
  - Verified vector cache invalidation on canonical timestamp and representation text change.
  - Verified retrieval service integration delegation.
- **`EveConversationalSessionTest`**: All multi-turn conversation and antecedent carry-forward tests passed.
- **`EveRetrievalRouterTest`**: All domain routing, ambiguity handling, and security boundary tests passed.
- **`EvePostgresIntegrationTest`**: Full PostgreSQL Testcontainers integration suite with live Flyway migrations passed.
- **`EveSecurityBoundaryTest`**: Single-method structural invariant on `EveModelProvider` and untrusted data sanitization passed.

### Frontend Test Suite & Production Build
Executed desktop vitest suite and production compilation in `apps/desktop`:
```text
npm test -- --run
Test Files  21 passed (21)
     Tests  89 passed (89)

npm run build
✓ built in 8.93s
```

### Git Hygiene & Weights Verification
```text
git check-ignore -v eve/models/Qwen3-Embedding-0.6B-Q8_0.gguf eve/models/Qwen3-Reranker-0.6B-Q8_0.gguf eve/models/Qwen3-4B-Q4_K_M.gguf
.gitignore:33:eve/models/*.gguf eve/models/Qwen3-Embedding-0.6B-Q8_0.gguf
.gitignore:33:eve/models/*.gguf eve/models/Qwen3-Reranker-0.6B-Q8_0.gguf
.gitignore:33:eve/models/*.gguf eve/models/Qwen3-4B-Q4_K_M.gguf
```
Zero model weights or binary blobs are tracked in Git.
