package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministic test model provider for EVE Phase 1.
 * Provides predictable, fixture-based interpretations without requiring a live LLM runtime.
 */
@Component
public class TestModelProvider implements EveModelProvider {

  @Override
  public EveInterpretation interpret(EveInterpretationRequest request) {
    if (request == null || request.prompt() == null || request.prompt().isBlank()) {
      return EveInterpretation.of(Intent.UNKNOWN, null, null);
    }

    String prompt = request.prompt().trim();
    String lower = prompt.toLowerCase(Locale.ROOT);

    // Test hooks for simulated provider failures
    if (prompt.contains("__SIMULATE_TIMEOUT__")) {
      throw ApiException.badRequest("EVE_MODEL_TIMEOUT", "Model provider request timed out.");
    }
    if (prompt.contains("__SIMULATE_UNAVAILABLE__")) {
      throw ApiException.badRequest("EVE_MODEL_UNAVAILABLE", "Model provider service is unavailable.");
    }
    if (prompt.contains("__SIMULATE_MALFORMED__")) {
      throw ApiException.badRequest("EVE_MODEL_MALFORMED_OUTPUT", "Model output failed schema validation.");
    }

    // Defensive check: prompt injection attempts in request
    if (lower.contains("ignore previous instructions") || lower.contains("ignore all rules") || lower.contains("delete from") || lower.contains("drop table")) {
      return EveInterpretation.refused("Potential prompt injection or destructive command detected.");
    }

    // Entity & intent extraction patterns for Phase 1 read flows
    if (lower.contains("sharma") || lower.contains("kitna baaki") || lower.contains("need") || lower.contains("pending") || lower.contains("outstanding") || lower.contains("owed")) {
      String spoken = "Sharma";
      if (lower.contains("raj sharma")) {
        spoken = "Raj Sharma";
      } else if (lower.contains("amit sharma")) {
        spoken = "Amit Sharma";
      }
      return EveInterpretation.of(Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", spoken);
    }

    if (lower.contains("royal")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "Royal");
    }

    if (lower.contains("gear") || lower.contains("stand") || lower.contains("equipment")) {
      return EveInterpretation.of(Intent.READ_EQUIPMENT, "EQUIPMENT", "Equipment");
    }

    if (lower.contains("summary") || lower.contains("overview") || lower.contains("kya chal raha hai")) {
      return EveInterpretation.of(Intent.READ_SYSTEM_SUMMARY, "SYSTEM", "Overview");
    }

    // Default fallback
    return EveInterpretation.of(Intent.UNKNOWN, null, null);
  }
}
