package com.saproduction.command.eve.semantic;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Governed confidence, margin, and ambiguity policy for semantic entity resolution.
 *
 * Invariants:
 * - Never blindly selects candidate #1.
 * - Enforces minimum score threshold and minimum separation margin.
 * - When scores are too close and no contextual tie-breaker exists, triggers CLARIFICATION_REQUIRED.
 * - Configured via application properties; tuned for SA Command operational semantics.
 */
@Component
public class EveSemanticPolicy {

  public enum DecisionType {
    RESOLVED,
    AMBIGUOUS,
    LOW_CONFIDENCE,
    NOT_FOUND
  }

  public record ScoredCandidate(
      EveSemanticCandidate candidate,
      double embeddingScore,
      double rerankerScore,
      double combinedScore) implements Comparable<ScoredCandidate> {

    @Override
    public int compareTo(ScoredCandidate other) {
      return Double.compare(other.combinedScore, this.combinedScore); // descending
    }
  }

  public record PolicyDecision(
      DecisionType type,
      EveSemanticCandidate resolvedCandidate,
      List<EveSemanticCandidate> candidates,
      double topScore,
      double margin,
      String reason) {

    public static PolicyDecision resolved(EveSemanticCandidate candidate, double score, double margin, String reason) {
      return new PolicyDecision(DecisionType.RESOLVED, candidate, List.of(candidate), score, margin, reason);
    }

    public static PolicyDecision ambiguous(List<EveSemanticCandidate> candidates, double topScore, double margin, String reason) {
      return new PolicyDecision(DecisionType.AMBIGUOUS, null, candidates, topScore, margin, reason);
    }

    public static PolicyDecision lowConfidence(double topScore, double minScore, String reason) {
      return new PolicyDecision(DecisionType.LOW_CONFIDENCE, null, List.of(), topScore, 0.0, reason);
    }

    public static PolicyDecision notFound(String reason) {
      return new PolicyDecision(DecisionType.NOT_FOUND, null, List.of(), 0.0, 0.0, reason);
    }
  }

  private final double minScore;
  private final double minMargin;
  private final int maxCandidates;

  public EveSemanticPolicy(
      @Value("${app.eve.semantic.resolver.min-score:0.35}") double minScore,
      @Value("${app.eve.semantic.resolver.min-margin:0.08}") double minMargin,
      @Value("${app.eve.semantic.resolver.max-candidates:20}") int maxCandidates) {
    this.minScore = minScore;
    this.minMargin = minMargin;
    this.maxCandidates = maxCandidates;
  }

  public PolicyDecision evaluate(
      List<ScoredCandidate> scoredCandidates,
      EveSemanticCandidate activeFocus) {

    if (scoredCandidates == null || scoredCandidates.isEmpty()) {
      return PolicyDecision.notFound("Zero candidates matched query criteria");
    }

    ScoredCandidate top = scoredCandidates.get(0);

    // 1. Minimum Score Gate
    if (top.combinedScore() < minScore) {
      return PolicyDecision.lowConfidence(
          top.combinedScore(),
          minScore,
          String.format("Top candidate '%s' score %.3f below minimum threshold %.3f",
              top.candidate().displayName(), top.combinedScore(), minScore));
    }

    // 2. Margin Gate for Multiple Candidates
    if (scoredCandidates.size() > 1) {
      ScoredCandidate second = scoredCandidates.get(1);
      double margin = top.combinedScore() - second.combinedScore();

      if (margin < minMargin) {
        // Check if top candidate is already in active conversation focus
        if (activeFocus != null && activeFocus.id().equals(top.candidate().id())) {
          return PolicyDecision.resolved(
              top.candidate(),
              top.combinedScore(),
              margin,
              String.format("Resolved via active conversation focus affinity (margin: %.3f)", margin));
        }

        // True ambiguity: candidates are too close to safely distinguish automatically
        List<EveSemanticCandidate> ambiguousList = scoredCandidates.stream()
            .filter(c -> c.combinedScore() >= minScore)
            .limit(Math.min(scoredCandidates.size(), 5))
            .map(ScoredCandidate::candidate)
            .toList();

        return PolicyDecision.ambiguous(
            ambiguousList,
            top.combinedScore(),
            margin,
            String.format("Ambiguous match: top score %.3f and second score %.3f differ by %.3f (min required: %.3f)",
                top.combinedScore(), second.combinedScore(), margin, minMargin));
      }
    }

    // 3. High Confidence Unique Resolution
    double finalMargin = scoredCandidates.size() > 1
        ? top.combinedScore() - scoredCandidates.get(1).combinedScore()
        : 1.0;

    return PolicyDecision.resolved(
        top.candidate(),
        top.combinedScore(),
        finalMargin,
        String.format("Resolved with confidence %.3f (margin: %.3f)", top.combinedScore(), finalMargin));
  }

  public double getMinScore() {
    return minScore;
  }

  public double getMinMargin() {
    return minMargin;
  }

  public int getMaxCandidates() {
    return maxCandidates;
  }
}
