package com.saproduction.command.eve.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * High-performance, in-memory semantic cache for candidate embeddings.
 *
 * Cache Invalidation Invariants:
 * - Does NOT cache business truth.
 * - Embeddings are strictly keyed by candidate UUID.
 * - Invalidation occurs automatically if canonical entity's updatedAt is newer than cachedAt,
 *   or if the searchable text representation hash changes.
 * - No stale embeddings may override canonical state.
 */
@Component
public class EveSemanticCache {

  private static final Logger log = LoggerFactory.getLogger(EveSemanticCache.class);

  private static final int MAX_CAPACITY = 5000;

  public record CachedVector(
      float[] vector,
      Instant cachedAt,
      Instant canonicalUpdatedAt,
      String representationHash,
      String modelIdentity) {

    public CachedVector(float[] vector, Instant cachedAt, Instant canonicalUpdatedAt, String representationHash) {
      this(vector, cachedAt, canonicalUpdatedAt, representationHash, "default");
    }
  }

  private final Map<UUID, CachedVector> cache = new ConcurrentHashMap<>();

  public Optional<float[]> get(UUID candidateId, Instant canonicalUpdatedAt, String representationText) {
    return get(candidateId, canonicalUpdatedAt, representationText, null);
  }

  public Optional<float[]> get(UUID candidateId, Instant canonicalUpdatedAt, String representationText, String modelIdentity) {
    if (candidateId == null) {
      return Optional.empty();
    }
    CachedVector entry = cache.get(candidateId);
    if (entry == null) {
      return Optional.empty();
    }

    // Invalidation Check 0: Model identity change
    if (modelIdentity != null && entry.modelIdentity() != null && !modelIdentity.equals(entry.modelIdentity())) {
      cache.remove(candidateId);
      log.debug("Evicted embedding for candidate {} due to model identity change (cached: {}, requested: {})",
          candidateId, entry.modelIdentity(), modelIdentity);
      return Optional.empty();
    }

    // Invalidation Check 1: Canonical update timestamp is newer than when we cached the vector
    if (canonicalUpdatedAt != null && entry.canonicalUpdatedAt != null && canonicalUpdatedAt.isAfter(entry.canonicalUpdatedAt)) {
      cache.remove(candidateId);
      log.debug("Evicted stale embedding for candidate {} due to newer canonical updatedAt: {}", candidateId, canonicalUpdatedAt);
      return Optional.empty();
    }

    // Invalidation Check 2: Searchable text representation hash mismatch
    if (representationText != null) {
      String currentHash = hash(representationText);
      if (!currentHash.equals(entry.representationHash)) {
        cache.remove(candidateId);
        log.debug("Evicted stale embedding for candidate {} due to representation hash change", candidateId);
        return Optional.empty();
      }
    }

    return Optional.of(entry.vector);
  }

  public void put(UUID candidateId, float[] vector, Instant canonicalUpdatedAt, String representationText) {
    put(candidateId, vector, canonicalUpdatedAt, representationText, "default");
  }

  public void put(UUID candidateId, float[] vector, Instant canonicalUpdatedAt, String representationText, String modelIdentity) {
    if (candidateId == null || vector == null) {
      return;
    }
    if (cache.size() >= MAX_CAPACITY) {
      // Evict oldest or clear 20% to maintain bounds
      int toRemove = MAX_CAPACITY / 5;
      var it = cache.keySet().iterator();
      while (it.hasNext() && toRemove-- > 0) {
        it.next();
        it.remove();
      }
    }
    String repHash = representationText != null ? hash(representationText) : "";
    String modelId = modelIdentity != null ? modelIdentity : "default";
    cache.put(candidateId, new CachedVector(vector, Instant.now(), canonicalUpdatedAt, repHash, modelId));
  }

  public void evict(UUID candidateId) {
    if (candidateId != null) {
      cache.remove(candidateId);
    }
  }

  public void clear() {
    cache.clear();
  }

  public int size() {
    return cache.size();
  }

  /**
   * Computes the cosine similarity between two normalized or unnormalized float vectors.
   */
  public static double cosineSimilarity(float[] v1, float[] v2) {
    if (v1 == null || v2 == null || v1.length != v2.length || v1.length == 0) {
      return 0.0;
    }
    double dot = 0.0;
    double norm1 = 0.0;
    double norm2 = 0.0;
    for (int i = 0; i < v1.length; i++) {
      dot += v1[i] * v2[i];
      norm1 += v1[i] * v1[i];
      norm2 += v2[i] * v2[i];
    }
    if (norm1 == 0.0 || norm2 == 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
  }

  private static String hash(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(text.hashCode());
    }
  }
}
