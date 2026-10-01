package com.saproduction.command.eve.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fast, deterministic cross-encoder reranker for CI and test environments.
 * Active when app.eve.semantic.provider=TEST (default).
 *
 * Computes Jaccard/Dice cross-relevance scoring between query tokens and document representations.
 * Preserves ranking fidelity without model download or GPU overhead.
 */
@Component
@ConditionalOnProperty(name = "app.eve.semantic.provider", havingValue = "TEST", matchIfMissing = true)
public class TestRerankerProvider implements EveRerankerProvider {

  private static final Set<String> STOP_WORDS = Set.of(
      "the", "a", "an", "in", "on", "at", "to", "for", "of", "with", "is", "are",
      "what", "how", "who", "where", "and", "or", "by", "from", "this", "that",
      // Metadata headers (do not match schema labels against entity content)
      "production", "employee",
      // Common Hinglish functional / query words
      "wala", "wali", "wale", "ka", "ki", "ke", "ko", "hai", "hain", "kiska", "kiske",
      "kaun", "kaunsa", "kaunsi", "me", "mein", "pe", "par", "se", "bhai", "kya"
  );

  @Override
  public List<RerankResult> rerank(String query, List<String> documents, String instruction, int topK) {
    if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
      return List.of();
    }

    Set<String> queryTokens = extractTokens(query);
    List<RerankResult> scored = new ArrayList<>(documents.size());

    for (int i = 0; i < documents.size(); i++) {
      String doc = documents.get(i);
      Set<String> docTokens = extractTokens(doc);

      // Compute weighted query coverage with typo and consonant tolerance
      int intersection = 0;
      for (String qt : queryTokens) {
        if (docTokens.contains(qt)) {
          intersection += 2;
        } else {
          String qCons = qt.replaceAll("[aeiou]", "");
          for (String dt : docTokens) {
            String dCons = dt.replaceAll("[aeiou]", "");
            if (qCons.length() >= 2 && qCons.equals(dCons)) {
              intersection += 2;
              break;
            } else if (Math.min(qt.length(), dt.length()) >= 4 && editDistance(qt, dt) <= 1) {
              intersection += 2;
              break;
            } else if (Math.min(qt.length(), dt.length()) >= 6 && editDistance(qt, dt) <= 2) {
              intersection += 2;
              break;
            } else if (Math.min(qt.length(), dt.length()) >= 4 && (dt.startsWith(qt) || qt.startsWith(dt))) {
              intersection += 1;
              break;
            }
          }
        }
      }

      double maxPossible = Math.max(1, queryTokens.size() * 2.0);
      double score = queryTokens.isEmpty() ? 0.0 : Math.min(1.0, (double) intersection / maxPossible);
      scored.add(new RerankResult(i, score, doc));
    }

    // Sort descending by score
    scored.sort((a, b) -> Double.compare(b.score(), a.score()));

    int limit = Math.min(topK > 0 ? topK : documents.size(), scored.size());
    return Collections.unmodifiableList(scored.subList(0, limit));
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String getModelName() {
    return "Test-CrossEncoder-Reranker";
  }

  private Set<String> extractTokens(String text) {
    Set<String> tokens = new HashSet<>();
    if (text == null) return tokens;
    String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
    for (String word : clean.split("\\s+")) {
      if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
        tokens.add(word);
      }
    }
    return tokens;
  }

  private static int editDistance(String s1, String s2) {
    int m = s1.length(), n = s2.length();
    int[] dp = new int[n + 1];
    for (int j = 0; j <= n; j++) dp[j] = j;
    for (int i = 1; i <= m; i++) {
      int prev = dp[0];
      dp[0] = i;
      for (int j = 1; j <= n; j++) {
        int temp = dp[j];
        if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
          dp[j] = prev;
        } else {
          dp[j] = 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
        }
        prev = temp;
      }
    }
    return dp[n];
  }
}
