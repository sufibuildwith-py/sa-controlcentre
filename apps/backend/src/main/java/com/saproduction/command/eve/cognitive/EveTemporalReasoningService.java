package com.saproduction.command.eve.cognitive;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bounded temporal reasoning service grounded in the application's configured timezone.
 * Resolves natural language date and date-range expressions (English, Hindi, Hinglish)
 * into concrete, authoritative LocalDates and DateRanges.
 */
@Service
public class EveTemporalReasoningService {

  public record DateRange(LocalDate start, LocalDate end, String label) {
    public boolean contains(LocalDate date) {
      if (date == null) return false;
      return !date.isBefore(start) && !date.isAfter(end);
    }
  }

  private final ZoneId zone;
  private final java.time.Clock clock;

  @Autowired
  public EveTemporalReasoningService(
      @Value("${app.time-zone:Asia/Kolkata}") String timeZone) {
    this.zone = ZoneId.of(timeZone != null && !timeZone.isBlank() ? timeZone : "Asia/Kolkata");
    this.clock = java.time.Clock.system(this.zone);
  }

  public EveTemporalReasoningService(ZoneId zone, java.time.Clock clock) {
    this.zone = zone != null ? zone : ZoneId.of("Asia/Kolkata");
    this.clock = clock != null ? clock : java.time.Clock.system(this.zone);
  }

  public ZoneId getZone() {
    return zone;
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }

  /**
   * Resolves a single relative date expression into a canonical LocalDate.
   */
  public Optional<LocalDate> resolveDate(String expression) {
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }
    String normalized = expression.trim().toLowerCase(Locale.ROOT);
    LocalDate now = today();

    if (normalized.equals("aaj") || normalized.equals("today")
        || normalized.contains("aaj ka") || normalized.contains("today's")) {
      return Optional.of(now);
    }
    if (normalized.equals("yesterday") || normalized.contains("kal beeta") || normalized.contains("pichla kal")) {
      return Optional.of(now.minusDays(1));
    }
    if (normalized.equals("kal") || normalized.equals("tomorrow")
        || normalized.contains("kal ka") || normalized.contains("tomorrow's") || normalized.contains("aane wala kal")) {
      return Optional.of(now.plusDays(1));
    }
    if (normalized.equals("parso") || normalized.contains("day after tomorrow")) {
      return Optional.of(now.plusDays(2));
    }

    // Explicit weekday references (e.g. "coming friday", "this friday", "next monday")
    for (DayOfWeek dow : DayOfWeek.values()) {
      String dowName = dow.name().toLowerCase(Locale.ROOT);
      if (normalized.contains(dowName)) {
        if (normalized.contains("next") || normalized.contains("coming") || normalized.contains("agle")) {
          return Optional.of(now.with(TemporalAdjusters.next(dow)));
        } else if (normalized.contains("last") || normalized.contains("pichle")) {
          return Optional.of(now.with(TemporalAdjusters.previous(dow)));
        } else {
          return Optional.of(now.with(TemporalAdjusters.nextOrSame(dow)));
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Resolves a temporal expression into an authoritative, bounded DateRange.
   * Crucial for queries such as "next week kitne events hai", "this weekend", "next month".
   */
  public Optional<DateRange> resolveDateRange(String expression) {
    if (expression == null || expression.isBlank()) {
      return Optional.empty();
    }
    String s = expression.trim().toLowerCase(Locale.ROOT);
    LocalDate now = today();

    // 1. "today" / "aaj"
    if (s.contains("today") || s.contains("aaj")) {
      return Optional.of(new DateRange(now, now, "TODAY"));
    }

    // 2. "tomorrow" / "kal"
    if ((s.contains("tomorrow") || s.contains("kal")) && !s.contains("pichla") && !s.contains("beeta")) {
      LocalDate tmr = now.plusDays(1);
      return Optional.of(new DateRange(tmr, tmr, "TOMORROW"));
    }

    // 3. "yesterday"
    if (s.contains("yesterday") || s.contains("pichla kal") || s.contains("beeta kal")) {
      LocalDate yest = now.minusDays(1);
      return Optional.of(new DateRange(yest, yest, "YESTERDAY"));
    }

    // 4. "next week" / "agle hafte" / "coming week" / "next 7 days"
    if (s.contains("next week") || s.contains("next_week") || s.contains("agle hafte") || s.contains("coming week") || s.contains("aane wale hafte")) {
      // Monday of next week to Sunday of next week
      LocalDate nextMonday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
      LocalDate nextSunday = nextMonday.plusDays(6);
      return Optional.of(new DateRange(nextMonday, nextSunday, "NEXT_WEEK"));
    }

    // 5. "this weekend" / "is weekend"
    if (s.contains("this weekend") || s.contains("this_weekend") || s.contains("is weekend")) {
      LocalDate saturday = now.with(DayOfWeek.SATURDAY);
      LocalDate sunday = now.with(DayOfWeek.SUNDAY);
      return Optional.of(new DateRange(saturday, sunday, "THIS_WEEKEND"));
    }

    // 6. "this week" / "is hafte" / "current week"
    if (s.contains("this week") || s.contains("this_week") || s.contains("is hafte") || s.contains("current week")) {
      LocalDate monday = now.with(DayOfWeek.MONDAY);
      LocalDate sunday = now.with(DayOfWeek.SUNDAY);
      return Optional.of(new DateRange(monday, sunday, "THIS_WEEK"));
    }

    // 7. "next weekend" / "agle weekend"
    if (s.contains("next weekend") || s.contains("next_weekend") || s.contains("agle weekend")) {
      LocalDate nextSaturday = now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
      LocalDate nextSunday = nextSaturday.plusDays(1);
      return Optional.of(new DateRange(nextSaturday, nextSunday, "NEXT_WEEKEND"));
    }

    // 8. "next month" / "agle mahine"
    if (s.contains("next month") || s.contains("next_month") || s.contains("agle mahine")) {
      LocalDate nextMonthFirst = now.plusMonths(1).withDayOfMonth(1);
      LocalDate nextMonthLast = nextMonthFirst.with(TemporalAdjusters.lastDayOfMonth());
      return Optional.of(new DateRange(nextMonthFirst, nextMonthLast, "NEXT_MONTH"));
    }

    // 9. "this month" / "is mahine"
    if (s.contains("this month") || s.contains("this_month") || s.contains("is mahine")) {
      LocalDate firstDay = now.withDayOfMonth(1);
      LocalDate lastDay = now.with(TemporalAdjusters.lastDayOfMonth());
      return Optional.of(new DateRange(firstDay, lastDay, "THIS_MONTH"));
    }

    // 10. Check if it's a single date resolved as a 1-day range
    return resolveDate(expression).map(d -> new DateRange(d, d, "EXPLICIT_DATE"));
  }
}
