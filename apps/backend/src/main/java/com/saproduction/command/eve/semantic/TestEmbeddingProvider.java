package com.saproduction.command.eve.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fast, deterministic embedding provider for CI and test environments.
 * Active when app.eve.semantic.provider=TEST (default).
 *
 * Employs character-trigram hashing into a normalized dense vector:
 * - Robust against typos and transliterated Hinglish variations.
 * - Zero network overhead, zero GPU requirement, sub-millisecond execution.
 */
@Component
@ConditionalOnProperty(name = "app.eve.semantic.provider", havingValue = "TEST", matchIfMissing = true)
public class TestEmbeddingProvider implements EveEmbeddingProvider {

  private static final int DIMENSION = 512;

  @Override
  public float[] embedQuery(String query, String instruction) {
    return computeVector(query != null ? query : "");
  }

  @Override
  public float[] embedCandidate(String text) {
    return computeVector(text);
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    List<float[]> results = new ArrayList<>(texts.size());
    for (String text : texts) {
      results.add(embedCandidate(text));
    }
    return results;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String getModelName() {
    return "Test-Embedding-TriGram-256d";
  }

  @Override
  public int getDimension() {
    return DIMENSION;
  }

  private float[] computeVector(String text) {
    float[] vector = new float[DIMENSION];
    if (text == null || text.isBlank()) {
      return vector;
    }

    String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
    String[] words = normalized.split("\\s+");

    // Word and character n-gram hashing
    for (String word : words) {
      if (word.isBlank()) continue;

      // Hash whole word
      int wordBucket = Math.abs(word.hashCode()) % DIMENSION;
      vector[wordBucket] += 2.0f;

      // Hash character trigrams for typo robustness
      if (word.length() >= 3) {
        for (int i = 0; i <= word.length() - 3; i++) {
          String trigram = word.substring(i, i + 3);
          int bucket = Math.abs(trigram.hashCode()) % DIMENSION;
          vector[bucket] += 1.0f;
        }
      }
    }

    // L2 normalization
    double sumSq = 0.0;
    for (float v : vector) {
      sumSq += v * v;
    }
    if (sumSq > 0.0) {
      float norm = (float) Math.sqrt(sumSq);
      for (int i = 0; i < DIMENSION; i++) {
        vector[i] /= norm;
      }
    }

    return vector;
  }
}
