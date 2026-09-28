package com.saproduction.command.eve;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic date/time language parser grounded in the application's configured timezone.
 * Resolves relative temporal expressions into canonical LocalDates.
 */
public final class EveDateTimeParser {

  private final ZoneId zone;

  public EveDateTimeParser(String timeZone) {
    this.zone = ZoneId.of(timeZone != null && !timeZone.isBlank() ? timeZone : "Asia/Kolkata");
  }

  public ZoneId getZone() {
    return zone;
  }

  public LocalDate today() {
    return LocalDate.now(zone);
  }

  /**
   * Resolves relative date expression into a canonical LocalDate.
   *
   * Expressions supported:
   * - "aaj", "today" -> today
   * - "kal", "tomorrow" -> today + 1 day
   * - "yesterday", "kal beeta hua" -> today - 1 day
   * - "parso", "day after tomorrow" -> today + 2 days
   */
  public Optional<LocalDate> resolveRelativeDate(String expression) {
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }
    String normalized = expression.trim().toLowerCase(Locale.ROOT);
    LocalDate now = today();

    if (normalized.equals("aaj") || normalized.equals("today") || normalized.contains("aaj ka") || normalized.contains("today's")) {
      return Optional.of(now);
    }
    if (normalized.equals("yesterday") || normalized.contains("kal beeta") || normalized.contains("pichla kal")) {
      return Optional.of(now.minusDays(1));
    }
    if (normalized.equals("kal") || normalized.equals("tomorrow") || normalized.contains("kal ka") || normalized.contains("tomorrow's") || normalized.contains("aane wala kal")) {
      return Optional.of(now.plusDays(1));
    }
    if (normalized.equals("parso") || normalized.contains("day after tomorrow")) {
      return Optional.of(now.plusDays(2));
    }

    return Optional.empty();
  }
}
