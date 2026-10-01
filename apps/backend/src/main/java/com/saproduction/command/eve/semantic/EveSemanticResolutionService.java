package com.saproduction.command.eve.semantic;

import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.EveRetrievalRouter;
import com.saproduction.command.eve.EveRetrievalService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Two-stage semantic entity resolution service for EVE.
 *
 * Pipeline:
 * 1. Bounded canonical candidate generation from PostgreSQL (max 20).
 * 2. Deterministic exact check bypass (UUID, code, exact name).
 * 3. Context-enriched query embedding & cosine similarity retrieval (Stage 1).
 * 4. Cross-encoder reranking & relevance scoring (Stage 2).
 * 5. Governed confidence and ambiguity decision gate.
 * 6. Canonical entity resolution with authoritative UUID.
 *
 * Non-Authoritative Invariants:
 * - The semantic models can NEVER invent UUIDs, database records, or relationships.
 * - All resolutions are strictly constrained to canonical SA Command entities.
 * - Business text is treated strictly as data with clear delimiters.
 */
@Service
public class EveSemanticResolutionService {

  private static final Logger log = LoggerFactory.getLogger(EveSemanticResolutionService.class);

  private final EveEmbeddingProvider embeddingProvider;
  private final EveRerankerProvider rerankerProvider;
  private final EveSemanticCache cache;
  private final EveSemanticPolicy policy;
  private final ProductionRepository productionRepo;
  private final ProductionMemberRepository memberRepo;
  private final EmployeeService employeeService;
  private final boolean enabled;

  @Autowired
  public EveSemanticResolutionService(
      EveEmbeddingProvider embeddingProvider,
      EveRerankerProvider rerankerProvider,
      EveSemanticCache cache,
      EveSemanticPolicy policy,
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) ProductionMemberRepository memberRepo,
      EmployeeService employeeService,
      @Value("${app.eve.semantic.enabled:true}") boolean enabled) {
    this.embeddingProvider = embeddingProvider;
    this.rerankerProvider = rerankerProvider;
    this.cache = cache;
    this.policy = policy;
    this.productionRepo = productionRepo;
    this.memberRepo = memberRepo;
    this.employeeService = employeeService;
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public EveEmbeddingProvider getEmbeddingProvider() {
    return embeddingProvider;
  }

  public EveRerankerProvider getRerankerProvider() {
    return rerankerProvider;
  }

  /**
   * Semantically resolves a natural language reference to a canonical Production.
   */
  public EveRetrievalService.ResolutionResult resolveProduction(
      String userUtterance,
      EveRetrievalRouter.SessionContext sessionContext) {

    if (!enabled || userUtterance == null || userUtterance.isBlank() || EveRetrievalRouter.isPronoun(userUtterance)) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }
    if (productionRepo == null) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    log.info("EVE_SEMANTIC: Starting production resolution for utterance='{}'", userUtterance);

    // 1. Generate Canonical Candidate Set from Database (filter cancelled unless requested)
    List<Production> allProds = productionRepo.findAll();
    if (allProds.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    List<EveSemanticCandidate> candidates = new ArrayList<>();
    for (Production p : allProds) {
      if (p.status == Production.Status.CANCELLED) {
        continue;
      }
      Instant updated = p.updatedAt != null ? p.updatedAt : Instant.EPOCH;
      candidates.add(EveSemanticCandidate.forProduction(
          p.id, p.title, p.clientName,
          p.eventDate != null ? p.eventDate.toString() : "",
          p.venueName, p.status != null ? p.status.name() : "", updated));
    }

    if (candidates.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    // Check relationship clues (e.g. "Kabir wala production", "jisme Kabir hai")
    Set<UUID> relationshipMatches = prioritizeEmployeeAssignedProductions(userUtterance, candidates);

    // 2. Contextual Query Construction
    String contextualQuery = buildContextualQuery("PRODUCTION", userUtterance, sessionContext);
    String instruction = "Retrieve canonical event or production records matching the user reference.";

    // 3. Stage 1: Dense Vector Similarity across candidate universe
    List<EveSemanticPolicy.ScoredCandidate> scoredList = scoreCandidatesWithEmbedding(
        contextualQuery, instruction, candidates);

    if (scoredList.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    // Bound to top candidates after vector similarity ranking
    int maxC = Math.min(scoredList.size(), policy.getMaxCandidates());
    List<EveSemanticPolicy.ScoredCandidate> boundedScoredList = scoredList.subList(0, maxC);

    // Active focus representation if present
    EveSemanticCandidate activeFocus = null;
    if (sessionContext != null && sessionContext.getActiveProduction() != null) {
      UUID focusId = sessionContext.getActiveProduction().candidate().id();
      for (EveSemanticPolicy.ScoredCandidate sc : boundedScoredList) {
        if (sc.candidate().id().equals(focusId)) {
          activeFocus = sc.candidate();
          break;
        }
      }
    }

    // 4. Stage 2: Cross-Encoder Reranking
    List<EveSemanticPolicy.ScoredCandidate> rerankedList = rerankCandidates(
        userUtterance, userUtterance, boundedScoredList, 10);

    // 4B. Canonical Relationship Affinity Boost
    if (!relationshipMatches.isEmpty()) {
      List<EveSemanticPolicy.ScoredCandidate> relationshipBoosted = new ArrayList<>();
      for (EveSemanticPolicy.ScoredCandidate sc : rerankedList) {
        if (relationshipMatches.contains(sc.candidate().id())) {
          double boostedScore = Math.min(1.0, sc.combinedScore() + 0.20);
          relationshipBoosted.add(new EveSemanticPolicy.ScoredCandidate(
              sc.candidate(), sc.embeddingScore(), sc.rerankerScore(), boostedScore));
        } else {
          relationshipBoosted.add(sc);
        }
      }
      Collections.sort(relationshipBoosted);
      rerankedList = relationshipBoosted;
    }

    // 5. Governed Policy Decision Gate
    EveSemanticPolicy.PolicyDecision decision = policy.evaluate(rerankedList, activeFocus);

    return toResolutionResult(decision, userUtterance);
  }

  /**
   * Semantically resolves a natural language reference to a canonical Employee.
   */
  public EveRetrievalService.ResolutionResult resolveEmployee(
      String userUtterance,
      EveRetrievalRouter.SessionContext sessionContext) {

    if (!enabled || userUtterance == null || userUtterance.isBlank() || EveRetrievalRouter.isPronoun(userUtterance)) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    log.info("EVE_SEMANTIC: Starting employee resolution for utterance='{}'", userUtterance);

    // 1. Generate Canonical Candidate Set from Database (filter inactive employees)
    List<EmployeeDtos.View> employees = employeeService.list(null, null);
    if (employees.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    List<EveSemanticCandidate> candidates = new ArrayList<>();
    for (EmployeeDtos.View e : employees) {
      if (e.status() == com.saproduction.command.employee.Employee.Status.INACTIVE) {
        continue;
      }
      candidates.add(EveSemanticCandidate.forEmployee(
          e.id(), e.displayName(), e.firstName(), e.employeeCode(), e.roleTitle(),
          e.status() != null ? e.status().name() : "",
          e.updatedAt() != null ? e.updatedAt() : Instant.EPOCH));
    }

    if (candidates.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    // 2. Contextual Query Construction
    String contextualQuery = buildContextualQuery("EMPLOYEE", userUtterance, sessionContext);
    String instruction = "Retrieve employee and team member records matching the user reference.";

    // 3. Stage 1: Dense Vector Similarity across all active candidate employees
    List<EveSemanticPolicy.ScoredCandidate> scoredList = scoreCandidatesWithEmbedding(
        contextualQuery, instruction, candidates);

    if (scoredList.isEmpty()) {
      return EveRetrievalService.ResolutionResult.notFound(userUtterance);
    }

    // Bound to top candidates after vector similarity ranking
    int maxC = Math.min(scoredList.size(), policy.getMaxCandidates());
    List<EveSemanticPolicy.ScoredCandidate> boundedScoredList = scoredList.subList(0, maxC);

    // Active focus representation if present
    EveSemanticCandidate activeFocus = null;
    if (sessionContext != null && sessionContext.getActiveEmployee() != null) {
      UUID focusId = sessionContext.getActiveEmployee().candidate().id();
      for (EveSemanticPolicy.ScoredCandidate sc : boundedScoredList) {
        if (sc.candidate().id().equals(focusId)) {
          activeFocus = sc.candidate();
          break;
        }
      }
    }

    // 4. Stage 2: Cross-Encoder Reranking
    List<EveSemanticPolicy.ScoredCandidate> rerankedList = rerankCandidates(
        userUtterance, userUtterance, boundedScoredList, 10);

    // 5. Governed Policy Decision Gate
    EveSemanticPolicy.PolicyDecision decision = policy.evaluate(rerankedList, activeFocus);

    return toResolutionResult(decision, userUtterance);
  }

  // --------------------------------------------------------------------------
  // Core Two-Stage Retrieval Logic
  // --------------------------------------------------------------------------

  private List<EveSemanticPolicy.ScoredCandidate> scoreCandidatesWithEmbedding(
      String query, String instruction, List<EveSemanticCandidate> candidates) {

    if (!embeddingProvider.isAvailable()) {
      log.warn("Embedding provider is unavailable; bypassing Stage 1 vector search");
      return List.of();
    }

    float[] queryVec = embeddingProvider.embedQuery(query, instruction);
    String modelName = embeddingProvider.getModelName();

    List<EveSemanticCandidate> toEmbedBatch = new ArrayList<>();
    Map<UUID, float[]> candidateVectors = new LinkedHashMap<>();

    for (EveSemanticCandidate c : candidates) {
      Optional<float[]> cached = cache.get(c.id(), c.updatedAt(), c.searchableText(), modelName);
      if (cached.isPresent()) {
        candidateVectors.put(c.id(), cached.get());
      } else {
        toEmbedBatch.add(c);
      }
    }

    if (!toEmbedBatch.isEmpty()) {
      List<String> texts = toEmbedBatch.stream().map(EveSemanticCandidate::searchableText).toList();
      List<float[]> newVectors = embeddingProvider.embedBatch(texts);
      for (int i = 0; i < toEmbedBatch.size() && i < newVectors.size(); i++) {
        EveSemanticCandidate c = toEmbedBatch.get(i);
        float[] vec = newVectors.get(i);
        candidateVectors.put(c.id(), vec);
        cache.put(c.id(), vec, c.updatedAt(), c.searchableText(), modelName);
      }
    }

    List<EveSemanticPolicy.ScoredCandidate> scored = new ArrayList<>();
    for (EveSemanticCandidate c : candidates) {
      float[] vec = candidateVectors.get(c.id());
      if (vec != null) {
        double sim = EveSemanticCache.cosineSimilarity(queryVec, vec);
        scored.add(new EveSemanticPolicy.ScoredCandidate(c, sim, 0.0, sim));
      }
    }

    Collections.sort(scored);
    return scored;
  }

  private List<EveSemanticPolicy.ScoredCandidate> rerankCandidates(
      String rawUtterance,
      String contextualQuery,
      List<EveSemanticPolicy.ScoredCandidate> candidates,
      int topK) {

    int count = Math.min(candidates.size(), topK);
    List<EveSemanticPolicy.ScoredCandidate> topSubset = candidates.subList(0, count);

    if (!rerankerProvider.isAvailable()) {
      log.debug("Reranker provider not available, relying solely on Stage 1 embedding scores");
      return topSubset;
    }

    List<String> docTexts = topSubset.stream()
        .map(sc -> sc.candidate().searchableText())
        .toList();

    String instruction = "Score the relevance of this SA Command entity to the user's operational query.";
    List<EveRerankerProvider.RerankResult> rerankResults = rerankerProvider.rerank(
        rawUtterance, docTexts, instruction, topK);

    Map<Integer, Double> rerankScoreMap = new HashMap<>();
    double maxRerank = 0.0;
    for (EveRerankerProvider.RerankResult rr : rerankResults) {
      rerankScoreMap.put(rr.index(), rr.score());
      if (rr.score() > maxRerank) {
        maxRerank = rr.score();
      }
    }

    List<EveSemanticPolicy.ScoredCandidate> blended = new ArrayList<>();
    for (int i = 0; i < topSubset.size(); i++) {
      EveSemanticPolicy.ScoredCandidate sc = topSubset.get(i);
      double rawRerankScore = rerankScoreMap.getOrDefault(i, 0.0);

      double finalScore;
      if (sc.embeddingScore() < 0.20 || rawRerankScore < 0.05) {
        // Disallow neural reranker from boosting unrelated entities with weak semantic vectors or zero relevance
        finalScore = Math.min(sc.embeddingScore(), rawRerankScore);
      } else {
        // Governed blend: 40% dense semantic similarity + 60% cross-encoder relevance discrimination
        finalScore = (sc.embeddingScore() * 0.4) + (rawRerankScore * 0.6);
      }

      log.info("EVE_SEMANTIC: Rerank candidate [{}] embed={} rerank_raw={} final={}",
          sc.candidate().displayName(),
          String.format(Locale.ROOT, "%.3f", sc.embeddingScore()),
          String.format(Locale.ROOT, "%.6f", rawRerankScore),
          String.format(Locale.ROOT, "%.3f", finalScore));
      blended.add(new EveSemanticPolicy.ScoredCandidate(sc.candidate(), sc.embeddingScore(), rawRerankScore, finalScore));
    }

    Collections.sort(blended);
    return blended;
  }

  private EveRetrievalService.ResolutionResult toResolutionResult(
      EveSemanticPolicy.PolicyDecision decision, String spokenValue) {

    switch (decision.type()) {
      case RESOLVED -> {
        EveRetrievalService.Candidate c = decision.resolvedCandidate().toRetrievalCandidate();
        log.info("EVE_SEMANTIC: Successfully resolved '{}' to canonical [{}: {}] (score: {})",
            spokenValue, c.type(), c.displayName(), decision.topScore());
        return new EveRetrievalService.ResolutionResult(
            EveRetrievalService.ResolutionStatus.RESOLVED,
            c,
            List.of(c),
            new EveRetrievalService.ResolutionProvenance(
                spokenValue,
                c.id(),
                c.displayName(),
                EveRetrievalService.MatchMethod.SEMANTIC_RERANKED,
                decision.topScore()));
      }
      case AMBIGUOUS -> {
        List<EveRetrievalService.Candidate> candidateList = decision.candidates().stream()
            .map(EveSemanticCandidate::toRetrievalCandidate)
            .toList();
        log.info("EVE_SEMANTIC: Ambiguous match for '{}' across {} candidates (reason: {})", spokenValue, candidateList.size(), decision.reason());
        return new EveRetrievalService.ResolutionResult(
            EveRetrievalService.ResolutionStatus.AMBIGUOUS,
            null,
            candidateList,
            new EveRetrievalService.ResolutionProvenance(
                spokenValue,
                null,
                null,
                EveRetrievalService.MatchMethod.SEMANTIC_RERANKED,
                decision.topScore()));
      }
      case LOW_CONFIDENCE, NOT_FOUND -> {
        log.info("EVE_SEMANTIC: No confident resolution for '{}' (reason: {})", spokenValue, decision.reason());
        return EveRetrievalService.ResolutionResult.notFound(spokenValue);
      }
      default -> {
        return EveRetrievalService.ResolutionResult.notFound(spokenValue);
      }
    }
  }

  private String buildContextualQuery(
      String targetType,
      String userUtterance,
      EveRetrievalRouter.SessionContext sessionContext) {

    StringBuilder sb = new StringBuilder();
    sb.append("Target: ").append(targetType);

    if (sessionContext != null) {
      if (sessionContext.hasPendingClarification()) {
        sb.append(" | Pending intent: ").append(sessionContext.getPendingClarification().originalIntent());
      }
      if (sessionContext.getActiveProduction() != null) {
        sb.append(" | Active production: ").append(sessionContext.getActiveProduction().candidate().displayName());
      }
      if (sessionContext.getActiveEmployee() != null) {
        sb.append(" | Active employee: ").append(sessionContext.getActiveEmployee().candidate().displayName());
      }
    }

    sb.append(" | Reference: ").append(userUtterance);
    return sb.toString();
  }

  private Set<UUID> prioritizeEmployeeAssignedProductions(
      String userUtterance,
      List<EveSemanticCandidate> candidates) {

    Set<UUID> allAssigned = new HashSet<>();
    if (memberRepo == null || userUtterance == null) {
      return allAssigned;
    }
    String lower = userUtterance.toLowerCase(Locale.ROOT);

    // Check if utterance references a known employee name
    List<EmployeeDtos.View> employees = employeeService.list(null, null);
    for (EmployeeDtos.View emp : employees) {
      String name = emp.displayName().toLowerCase(Locale.ROOT);
      String first = emp.firstName() != null ? emp.firstName().toLowerCase(Locale.ROOT) : "";

      if ((!name.isBlank() && lower.contains(name)) || (!first.isBlank() && first.length() >= 3 && lower.contains(first))) {
        List<ProductionMember> members = memberRepo.findByEmployeeId(emp.id());
        Set<UUID> assignedProdIds = new HashSet<>();
        for (ProductionMember pm : members) {
          assignedProdIds.add(pm.productionId);
        }

        if (!assignedProdIds.isEmpty()) {
          allAssigned.addAll(assignedProdIds);
          for (int i = 0; i < candidates.size(); i++) {
            EveSemanticCandidate c = candidates.get(i);
            if (assignedProdIds.contains(c.id())) {
              String augmentedText = c.searchableText() + " | Assigned: " + emp.displayName() + (emp.firstName() != null ? " " + emp.firstName() : "");
              candidates.set(i, new EveSemanticCandidate(c.id(), c.type(), c.displayName(), c.code(), augmentedText, c.metadata(), c.updatedAt()));
            }
          }
          candidates.sort((c1, c2) -> {
            boolean c1Assigned = assignedProdIds.contains(c1.id());
            boolean c2Assigned = assignedProdIds.contains(c2.id());
            return Boolean.compare(c2Assigned, c1Assigned); // assigned productions first
          });
          log.info("EVE_SEMANTIC: Prioritized {} productions assigned to employee '{}'", assignedProdIds.size(), emp.displayName());
          break;
        }
      }
    }
    return allAssigned;
  }
}
