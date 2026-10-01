package com.saproduction.command.eve.semantic;

import java.util.List;

/**
 * Authoritative provider contract for local dense vector embeddings.
 * Used for first-stage semantic candidate retrieval across canonical SA Command entities.
 *
 * Grounded invariants:
 * - Embedding operations are strictly local, deterministic, and non-authoritative over business data.
 * - Embeddings serve only as a similarity signal among bounded canonical candidates.
 */
public interface EveEmbeddingProvider {

  /**
   * Embeds a query string, incorporating optional task instructions as recommended by Qwen3-Embedding.
   *
   * @param query the search query (e.g. "Mips wala event")
   * @param instruction the domain retrieval instruction (e.g. "Given a user search query, retrieve relevant event and production titles")
   * @return normalized embedding vector
   */
  float[] embedQuery(String query, String instruction);

  /**
   * Embeds a single candidate document representation.
   *
   * @param text the candidate searchable representation
   * @return normalized embedding vector
   */
  float[] embedCandidate(String text);

  /**
   * Batched embedding of candidate document representations for efficient single-flight inference.
   *
   * @param texts list of candidate document representations
   * @return list of normalized embedding vectors
   */
  List<float[]> embedBatch(List<String> texts);

  /**
   * Checks if the embedding provider is healthy and ready for inference.
   */
  boolean isAvailable();

  /**
   * Returns the model identifier for operational telemetry.
   */
  String getModelName();

  /**
   * Returns the vector dimension (e.g. 1024).
   */
  int getDimension();
}
