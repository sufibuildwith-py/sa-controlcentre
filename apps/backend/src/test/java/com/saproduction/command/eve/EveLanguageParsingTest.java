package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EveLanguageParsingTest {

  private EveDateTimeParser dateTimeParser;

  @BeforeEach
  void setUp() {
    dateTimeParser = new EveDateTimeParser("Asia/Kolkata");
  }

  @Test
  void parsesRelativeDatesInKolkataTimezone() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    assertThat(dateTimeParser.resolveRelativeDate("aaj")).contains(today);
    assertThat(dateTimeParser.resolveRelativeDate("today")).contains(today);
    assertThat(dateTimeParser.resolveRelativeDate("kal")).contains(today.plusDays(1));
    assertThat(dateTimeParser.resolveRelativeDate("tomorrow")).contains(today.plusDays(1));
    assertThat(dateTimeParser.resolveRelativeDate("yesterday")).contains(today.minusDays(1));
    assertThat(dateTimeParser.resolveRelativeDate("parso")).contains(today.plusDays(2));
  }

  @Test
  void returnsEmptyForUnrecognizedDates() {
    assertThat(dateTimeParser.resolveRelativeDate("someday")).isEmpty();
    assertThat(dateTimeParser.resolveRelativeDate("")).isEmpty();
    assertThat(dateTimeParser.resolveRelativeDate(null)).isEmpty();
  }

  @Test
  void parsesColloquialAmountsToExactMinorUnits() {
    // 3k / 3 hazaar / teen hazaar -> 3,000 INR = 300,000 paise
    assertThat(EveAmountParser.parseAmountMinor("3k")).contains(300_000L);
    assertThat(EveAmountParser.parseAmountMinor("3 hazaar")).contains(300_000L);
    assertThat(EveAmountParser.parseAmountMinor("teen hazaar")).contains(300_000L);
    assertThat(EveAmountParser.parseAmountMinor("₹3,000")).contains(300_000L);
    assertThat(EveAmountParser.parseAmountMinor("3000")).contains(300_000L);

    // 500 INR = 50,000 paise
    assertThat(EveAmountParser.parseAmountMinor("500")).contains(50_000L);

    // 5 lakh = 50,000,000 paise
    assertThat(EveAmountParser.parseAmountMinor("5 lakh")).contains(50_000_000L);
  }

  @Test
  void formatsMinorUnitsCorrectly() {
    String formatted = EveAmountParser.formatMinor(300_000L);
    assertThat(formatted).contains("3,000");
  }
}
