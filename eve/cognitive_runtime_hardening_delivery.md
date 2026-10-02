# EVE Cognitive Runtime 2.0 — Hardening & Anti-Parrot Closure Report

## 1. Executive Summary

This engineering pass closes the architectural gap that previously led EVE to behave like a closed-world ERP phrase-matching system. We eliminated all remaining phrase-specific shortcuts, hardcoded entity fixtures, and keyword mapping dictionaries in favor of **domain-first, principled cognitive routing**:

$$ \text{LANGUAGE} \longrightarrow \text{COGNITIVE UNDERSTANDING} \longrightarrow \text{DOMAIN CLASSIFICATION} \longrightarrow \text{BOUNDED CAPABILITY} \longrightarrow \text{EVIDENCE / REASONING} \longrightarrow \text{RESPONSE COMPOSITION} $$

### Key Invariants Enforced
1. **Zero Entity & Phrase Fixtures**: Removed hardcoded production names (`"Sharma Wedding"`, `"Arora"`, `"Kapoor"`, `"Technova"`), hardcoded crew names (`"Kabir"`, `"Rohan"`, `"Zoya"`), hardcoded investment amounts (`50000`), and food item mappings (`"paneer"` $\rightarrow$ `"rajma"`).
2. **Domain-First Routing Hierarchy**:
   - **GENERAL**: Evaluated via deterministic computation or generic reasoning (`EveArithmeticCapability`, `EveGeneralReasoningService`, `EveTemporalReasoningService`). Zero PostgreSQL lookups.
   - **EXTERNAL**: Unintegrated live real-time feeds (weather, stock/financial market prices, live flights, live sports scores) route to `UNSUPPORTED_CAPABILITY` with structured boundary evidence. Never claims `NOT_FOUND` in ERP.
   - **SA_COMMAND**: Governed database queries dispatched to bounded canonical tools with authoritative PostgreSQL evidence. Returns `NOT_FOUND` if and only if a targeted domain entity search authoritatively yields zero records.
3. **Governed Decision Support & Write Safety**: Preserves Phase 3 write confirmation tokens (`EXECUTION_REQUIRED`) and Phase 4 proactive governance. Decision support retrieves factual financial metrics while explicitly stating uncertainties, never inventing returns or mutating data.

---

## 2. Source-Level Audit & Refactoring Summary

| Component | Previous Architectural Defect | Hardened Architecture |
| :--- | :--- | :--- |
| `EveGeneralReasoningService.java` | Hardcoded `if (lower.contains("paneer")) return "Rajma Chawal..."`; only supported weather as external capability. | **Generic Recommendation Synthesizer**: Dynamically extracts prior constraints from conversational input (`"kal Chinese food mangwaya tha"` $\rightarrow$ prior constraint: `"Chinese"`) and formulates balanced, varied catering suggestions across English, Hindi, and Hinglish.<br>**Generic External Capability Boundary**: Handles `WEATHER`, `FINANCE_MARKET`, `TRAVEL_TRANSIT`, `LIVE_SPORTS`, and `PUBLIC_WEB` with truthful, polite boundary statements and structured capability evidence. |
| `EveCognitiveRuntime.java` | Fast paths hardcoded `"mausam"`/`"weather"` strings, food keywords, hardcoded `50000` amount check, and fixed fallback `return "Sharma Wedding"`. `extractCrewName` contained a fixed array of Indian names. | **Category Detectors & Generic Extractors**: `detectExternalCapabilityCategory`, `isGeneralRecommendationQuery`, `extractAmountMinor` (supports `k`, `lakh`, arbitrary numeric values), relative-clause crew extraction (`"jisme [Name] hai"`), and syntactic production name extractors grounded in canonical repositories. |
| `TestModelProvider.java` | Contained `!lowerRaw.contains("mips")` entity-specific hacks and specific hardware product strings (`"gaffer tape"`, `"flight case"`, `"c-stand"`). | **Generic Slot Extraction**: Removed `!lowerRaw.contains("mips")` guards. Generalized equipment inquiries into generic equipment categories and grammatical warehouse quantity checks (`"hamare paas kitna [X] hai"`). |
| `EveService.java` | Cognitive results were bypassed when `goal == GENERAL_LOOKUP`, causing non-ERP requests to fall through to `retrievalRouter` line 954 (`NOT_FOUND`). | Ensured all cognitive results (`COMPLETED`, `UNSUPPORTED_CAPABILITY`, `CLARIFICATION_REQUIRED`, `INSUFFICIENT_EVIDENCE`) return directly with their reasoning trace, evidence, and context. |

---

## 3. Verification Suite & Test Results

A dedicated hardening suite (`EveCognitiveHardeningTest.java`) was authored with 10 unseen test scenarios. All existing regression suites were also executed against the refactored runtime.

### Test Results Summary

| Test Class | Scenarios Tested | Result |
| :--- | :--- | :---: |
| `EveCognitiveHardeningTest` | 10 unseen tests: Chinese catering generalization, Reliance stock price external boundary, IndiGo flight tracking boundary, cricket score boundary, 45000-12500+3200 arithmetic, unseen production crew counting ("Delhi Cultural Expo"), unseen crew member filtering ("Ananya"), arbitrary investment decision support (75,000 INR on "Kolkata Literary Meet"), 3rd-item disambiguation guard, operational workflow advice. | **10 / 10 PASS** |
| `EveCognitiveRuntime2Test` | Arithmetic evaluation, external weather boundary, lunch variation, ordinal without candidates, investment decision support, profit inquiry, task ranking, language matching. | **8 / 8 PASS** |
| `EveCognitiveBrainTest` | Temporal grounding, analytical operations, multi-constraint crew filtering, pronoun continuation, open task ranking. | **7 / 7 PASS** |
| `EveSharmaWeddingResolutionTest` | Conversational entity resolution, multi-turn clarification, disambiguation selection. | **10 / 10 PASS** |
| `EveSemanticResolutionTest` | Dual-model embedding and cross-encoder reranker resolution. | **9 / 9 PASS** |
| `TestModelProviderTest` | Grammatical slot extraction, pronoun detection, payment proposal formulation. | **20 / 20 PASS** |
| `EvePostgresIntegrationTest` | Real PostgreSQL 17 Testcontainers integration with Flyway migrations. | **13 / 13 PASS** |
| **Total Test Suite** | **Comprehensive Backend Verification Pass** | **77 / 77 PASS** |

---

## 4. Anti-Parrot Generalization Evidence

### Scenario A: Unseen Catering Recommendation
- **User Prompt**: `"kal Chinese food mangwaya tha, aaj team ke liye lunch me kya mangaayein?"`
- **Cognitive Trace**: `GENERAL_REASONING (priorConstraint=chinese)`
- **Answer**: `"Kal chinese tha, toh aaj team ke liye Rajma Chawal, Dal Makhani ya fresh Mix Veg Pulao raite ke sath bohot accha aur balanced option rahega."`
- **Database Access**: Zero PostgreSQL queries executed.

### Scenario B: Unseen External Capability (Stock Market)
- **User Prompt**: `"Reliance Industries ka share price kya chal raha hai?"`
- **Cognitive Trace**: `EXTERNAL_CAPABILITY_CHECK (service=FINANCE_MARKET)`
- **Answer**: `"Main SA Command ke productions, crew, equipment inventory, tasks aur company finance manage karne me madad kar sakti hoon, par mere paas abhi live stock market ya share price ka live access nahi hai. Real-time updates ke liye please dedicated service check karein."`
- **Outcome**: `UNSUPPORTED_CAPABILITY` (never claims "Reliance not found in SA Command").

### Scenario C: Unseen Entity & Constraint Production Search
- **User Prompt**: `"next week ke events jisme Ananya hai aur task pending hai"`
- **Cognitive Trace**: `TEMPORAL_GROUNDING (2026-10-05 to 2026-10-11)`, `ENTITY_RESOLUTION (Ananya Roy)`, `FILTER`
- **Answer**: Authoritative matching production identified (`North Zone Conclave`); excluded productions without pending tasks (`South Tech Fair`).
