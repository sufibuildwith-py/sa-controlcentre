package com.saproduction.command.eve.semantic;

import java.util.List;

/**
 * Authoritative provider contract for local cross-encoder reranking.
 * Used for second-stage candidate discrimination to compute fine-grained relevance scores.
 *
 * Grounded invariants:
 * - Rerankers are cross-encoders evaluating (query, document) pairs.
 * - Score is a ranking signal, never an authoritative truth probability.
 * - Bounded input: maximum top-K candidate list (e.g. <= 10).
 */
public interface EveRerankerProvider {

  /**
   * Represents a scored candidate item returned by the cross-encoder reranker.
   */
  record RerankResult(int index, double score, String document) {}

  /**
   * Reranks a bounded list of candidate documents against the contextual query.
   *
   * @param query the contextual query
   * @param documents list of candidate representations (max top-K, bounded)
   * @param instruction task-specific instruction for cross-encoder scoring
   * @param topK maximum number of ranked results to return
   * @return sorted list of RerankResult in descending order of relevance score
   */
  List<RerankResult> rerank(String query, List<String> documents, String instruction, int topK);

  /**
   * Checks if the reranker provider is healthy and ready for inference.
   */
  boolean isAvailable();

  /**
   * Returns the model identifier for operational telemetry.
   */
  String getModelName();
}
