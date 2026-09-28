package com.saproduction.command.eve;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic monetary amount parser and formatter for EVE.
 * Converts colloquial operator speech into exact minor units (paise) without floating point imprecision.
 */
public final class EveAmountParser {

  private static final Pattern K_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*k\\b");
  private static final Pattern HAZAAR_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:hazaar|hazar|thousand)\\b");
  private static final Pattern LAKH_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:lakh|lac)\\b");
  private static final Pattern PLAIN_NUMBER_PATTERN = Pattern.compile("[₹]?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)");

  private EveAmountParser() {}

  /**
   * Parses natural language amount expression into integer minor units (paise).
   * 1 Rupee = 100 paise.
   */
  public static Optional<Long> parseAmountMinor(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String clean = text.trim().toLowerCase(Locale.ROOT);

    // Named word numbers
    if (clean.contains("ek hazaar") || clean.contains("one thousand")) {
      return Optional.of(100_000L);
    }
    if (clean.contains("do hazaar") || clean.contains("two thousand")) {
      return Optional.of(200_000L);
    }
    if (clean.contains("teen hazaar") || clean.contains("three thousand")) {
      return Optional.of(300_000L);
    }
    if (clean.contains("chaar hazaar") || clean.contains("four thousand")) {
      return Optional.of(400_000L);
    }
    if (clean.contains("paanch hazaar") || clean.contains("five thousand")) {
      return Optional.of(500_000L);
    }
    if (clean.contains("das hazaar") || clean.contains("ten thousand")) {
      return Optional.of(1_000_000L);
    }
    if (clean.contains("ek lakh") || clean.contains("one lakh")) {
      return Optional.of(10_000_000L);
    }

    // Regex for "3k"
    Matcher kMatcher = K_PATTERN.matcher(clean);
    if (kMatcher.find()) {
      double thousands = Double.parseDouble(kMatcher.group(1));
      return Optional.of(Math.round(thousands * 1000.0 * 100.0));
    }

    // Regex for "3 hazaar"
    Matcher hazarMatcher = HAZAAR_PATTERN.matcher(clean);
    if (hazarMatcher.find()) {
      double thousands = Double.parseDouble(hazarMatcher.group(1));
      return Optional.of(Math.round(thousands * 1000.0 * 100.0));
    }

    // Regex for "5 lakh"
    Matcher lakhMatcher = LAKH_PATTERN.matcher(clean);
    if (lakhMatcher.find()) {
      double lakhs = Double.parseDouble(lakhMatcher.group(1));
      return Optional.of(Math.round(lakhs * 100000.0 * 100.0));
    }

    // Regex for plain number "3000" or "₹3,000"
    Matcher plainMatcher = PLAIN_NUMBER_PATTERN.matcher(clean);
    if (plainMatcher.find()) {
      try {
        String numStr = plainMatcher.group(1).replace(",", "");
        double val = Double.parseDouble(numStr);
        return Optional.of(Math.round(val * 100.0));
      } catch (NumberFormatException ignored) {
        // Fall through
      }
    }

    return Optional.empty();
  }

  public static String formatMinor(long minorUnits) {
    NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    return inr.format(minorUnits / 100.0);
  }
}
