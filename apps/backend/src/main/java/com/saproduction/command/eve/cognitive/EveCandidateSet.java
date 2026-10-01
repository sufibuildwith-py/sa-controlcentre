package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.EveRetrievalService;
import java.time.Instant;
import java.util.*;

/**
 * Structured Candidate Set State for EVE disambiguation and ordinal selection.
 * Enables deterministic resolution for follow-ups such as "wahi second wala" or "wedding wala".
 */
public record EveCandidateSet(
    List<EveRetrievalService.Candidate> candidates,
    String candidateType,
    String candidateSource,
    Instant timestamp) {

  public EveCandidateSet {
    candidates = candidates != null ? List.copyOf(candidates) : List.of();
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }

  public static EveCandidateSet empty() {
    return new EveCandidateSet(List.of(), null, null, Instant.now());
  }

  public static EveCandidateSet of(List<EveRetrievalService.Candidate> list, String type, String source) {
    return new EveCandidateSet(list, type, source, Instant.now());
  }

  public boolean isEmpty() {
    return candidates.isEmpty();
  }

  public int size() {
    return candidates.size();
  }

  /**
   * Resolves a 1-based index (e.g. 1 for "first", 2 for "second").
   */
  public Optional<EveRetrievalService.Candidate> getBy1BasedIndex(int index) {
    if (index >= 1 && index <= candidates.size()) {
      return Optional.of(candidates.get(index - 1));
    }
    return Optional.empty();
  }

  /**
   * Resolves an ordinal hint such as "first", "pehle wala", "second", "second wala", "2nd", "wahi 2nd wala".
   */
  public Optional<EveRetrievalService.Candidate> resolveOrdinalHint(String hint) {
    if (hint == null || hint.isBlank() || candidates.isEmpty()) {
      return Optional.empty();
    }
    String s = hint.trim().toLowerCase(Locale.ROOT);

    if (s.contains("first") || s.contains("1st") || s.contains("pehla") || s.contains("pehle") || s.matches(".*\\b1\\b.*")) {
      return getBy1BasedIndex(1);
    }
    if (s.contains("second") || s.contains("2nd") || s.contains("doosra") || s.contains("dusra") || s.matches(".*\\b2\\b.*")) {
      return getBy1BasedIndex(2);
    }
    if (s.contains("third") || s.contains("3rd") || s.contains("teesra") || s.contains("tisra") || s.matches(".*\\b3\\b.*")) {
      return getBy1BasedIndex(3);
    }
    if (s.contains("fourth") || s.contains("4th") || s.contains("chautha") || s.matches(".*\\b4\\b.*")) {
      return getBy1BasedIndex(4);
    }

    return Optional.empty();
  }
}
