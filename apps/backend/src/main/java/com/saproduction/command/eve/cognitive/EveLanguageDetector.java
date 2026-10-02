package com.saproduction.command.eve.cognitive;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic Language and Register Detector for EVE.
 * Enables mandatory language matching: EVE must respond in the same language and register as the user.
 */
public final class EveLanguageDetector {

  public enum UserLanguage {
    ENGLISH,
    HINDI,
    HINGLISH
  }

  public enum UserRegister {
    CASUAL,
    PROFESSIONAL,
    CONCISE
  }

  public record LanguageProfile(
      UserLanguage language,
      UserRegister register) {}

  private static final Set<String> HINGLISH_PARTICLES = Set.of(
      "ko", "ka", "ki", "ke", "mein", "mai", "se", "pe", "par",
      "hai", "hain", "tha", "thi", "hoga", "hogi", "hoge",
      "kya", "kaun", "kon", "kisko", "kiska", "kiske", "kiski", "kitna", "kitne", "kitni",
      "kahan", "kaha", "kab", "kyu", "kyun",
      "aur", "bhi", "toh",
      "aaj", "kal", "parso", "agle", "pichle", "aane", "beeta",
      "wahi", "wala", "wale", "wali", "walo",
      "sabse", "zyada", "jyada", "kam",
      "de", "dena", "karo", "karna", "batao", "bataiye", "dikhana", "dikhao",
      "gaya", "gaye", "gyi", "gya", "raha", "rahe", "rahi", "hue", "hua", "hui",
      "chahiye", "lagta", "sakte", "sakta", "sakti",
      "uska", "uske", "uski", "usme", "isme", "inme", "unka", "unke", "unki",
      "hamare", "hum", "apna", "apne", "apni", "kuch", "koi", "sirf", "abhi", "bande", "log", "logo"
  );

  private static final Set<String> CASUAL_MARKERS = Set.of(
      "yaar", "bhai", "bro", "wahi", "wala", "wale", "kon", "he", "h", "kya haal", "dikhao", "batao", "de do", "gya"
  );

  private static final Set<String> ENGLISH_MARKERS = Set.of(
      "should", "would", "could", "what", "which", "who", "where", "when", "why", "how",
      "is", "are", "was", "were", "have", "has", "had", "will", "can", "please", "for", "with",
      "from", "into", "about", "after", "before", "people", "tasks", "events", "production", "details",
      "send", "assign", "count", "list", "show", "tell", "give", "team", "members"
  );

  private EveLanguageDetector() {}

  public static LanguageProfile detect(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return new LanguageProfile(UserLanguage.ENGLISH, UserRegister.PROFESSIONAL);
    }

    String trimmed = prompt.trim();

    // 1. Check for Devanagari Unicode Block (U+0900 to U+097F)
    int devanagariCount = 0;
    for (char c : trimmed.toCharArray()) {
      if (c >= '\u0900' && c <= '\u097F') {
        devanagariCount++;
      }
    }
    if (devanagariCount >= 3) {
      UserRegister reg = detectRegister(trimmed);
      return new LanguageProfile(UserLanguage.HINDI, reg);
    }

    // 2. Tokenize Latin script words
    String[] tokens = trimmed.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
    int hinglishMatches = 0;
    int englishMatches = 0;
    int casualMatches = 0;

    for (String token : tokens) {
      if (HINGLISH_PARTICLES.contains(token)) {
        hinglishMatches++;
      }
      if (ENGLISH_MARKERS.contains(token)) {
        englishMatches++;
      }
      if (CASUAL_MARKERS.contains(token)) {
        casualMatches++;
      }
    }

    UserLanguage lang;
    if (hinglishMatches > 0 && hinglishMatches >= englishMatches) {
      lang = UserLanguage.HINGLISH;
    } else {
      lang = UserLanguage.ENGLISH;
    }

    UserRegister reg;
    if (tokens.length <= 3 && !trimmed.contains("?")) {
      reg = UserRegister.CONCISE;
    } else if (casualMatches >= 1 || (lang == UserLanguage.HINGLISH && trimmed.length() < 50)) {
      reg = UserRegister.CASUAL;
    } else {
      reg = UserRegister.PROFESSIONAL;
    }

    return new LanguageProfile(lang, reg);
  }

  private static UserRegister detectRegister(String text) {
    if (text.length() < 25) {
      return UserRegister.CONCISE;
    }
    return UserRegister.CASUAL;
  }
}
