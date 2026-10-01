package com.saproduction.command.eve;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic policy rules for Phase 4 Proactive Suggestions.
 * Governs eligibility, priority, deduplication, cooldowns, and auto-resolution.
 * The LLM never invents evidence or overrides policy decisions.
 */
@Component
public class EveSuggestionPolicy {

  public static final String OUTSTANDING_EMPLOYEE_PAYMENT = "OUTSTANDING_EMPLOYEE_PAYMENT";
  public static final String APPROACHING_PRODUCTION_OPEN_TASKS = "APPROACHING_PRODUCTION_OPEN_TASKS";
  public static final String OVERDUE_TASK = "OVERDUE_TASK";

  public static final Duration DEFAULT_COOLDOWN = Duration.ofHours(24);

  public String buildDedupeKey(String suggestionType, UUID entityId) {
    return suggestionType + ":" + entityId;
  }

  public String calculatePriority(String type, Object metric) {
    if (OUTSTANDING_EMPLOYEE_PAYMENT.equals(type)) {
      if (metric instanceof BigDecimal balance) {
        return balance.compareTo(new BigDecimal("5000.00")) >= 0 ? "HIGH" : "MEDIUM";
      }
      return "MEDIUM";
    }
    if (APPROACHING_PRODUCTION_OPEN_TASKS.equals(type)) {
      if (metric instanceof Long daysRemaining) {
        return daysRemaining <= 3 ? "HIGH" : "MEDIUM";
      }
      return "MEDIUM";
    }
    if (OVERDUE_TASK.equals(type)) {
      return "HIGH";
    }
    return "MEDIUM";
  }

  public boolean isCooldownActive(Instant dismissedAt, Duration cooldown) {
    if (dismissedAt == null) return false;
    Duration effectiveCooldown = cooldown != null ? cooldown : DEFAULT_COOLDOWN;
    return Instant.now().isBefore(dismissedAt.plus(effectiveCooldown));
  }
}
