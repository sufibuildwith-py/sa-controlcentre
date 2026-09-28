package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic test model provider for EVE Phase 2 Conversational Intelligence.
 * Provides predictable, fixture-based interpretations for multi-turn cross-domain conversations,
 * relative dates, colloquial language, follow-up references, and failure simulation.
 */
@Component
public class TestModelProvider implements EveModelProvider {

  private static final Pattern VOCABULARY_PATTERN = Pattern.compile("(?i)(?:remember that|se mera matlab|define)?\\s*['\"]?([^'\"]+?)['\"]?\\s*(?:means|is|hai|refers to)\\s*['\"]?([^'\"]+?)['\"]?\\.?$");

  @Override
  public EveInterpretation interpret(EveInterpretationRequest request) {
    if (request == null || request.prompt() == null || request.prompt().isBlank()) {
      return EveInterpretation.of(Intent.UNKNOWN, null, null);
    }

    String prompt = request.prompt().trim();
    String lower = prompt.toLowerCase(Locale.ROOT);

    // 1. Test hooks for simulated provider failures
    if (prompt.contains("__SIMULATE_TIMEOUT__")) {
      throw ApiException.badRequest("EVE_MODEL_TIMEOUT", "Model provider request timed out.");
    }
    if (prompt.contains("__SIMULATE_UNAVAILABLE__")) {
      throw ApiException.badRequest("EVE_MODEL_UNAVAILABLE", "Model provider service is unavailable.");
    }
    if (prompt.contains("__SIMULATE_MALFORMED__")) {
      throw ApiException.badRequest("EVE_MODEL_MALFORMED_OUTPUT", "Model output failed schema validation.");
    }

    // 2. Defensive check: prompt injection attempts in request
    if (lower.contains("ignore previous instructions")
        || lower.contains("ignore all rules")
        || lower.contains("delete from")
        || lower.contains("drop table")
        || lower.contains("admin access")) {
      return EveInterpretation.refused("Potential prompt injection or destructive command detected.");
    }

    // 3. Disambiguation selection on follow-up ("the second one", "2nd", "Royal Gala", "27th wala")
    if (lower.contains("second") || lower.contains("2nd") || lower.contains("first") || lower.contains("1st") || lower.contains("27th") || lower.contains("wala")) {
      return EveInterpretation.disambiguate(prompt);
    }

    // 4. Vocabulary learning ("Raju se mera matlab Raj Kumar hai" / "Raju means Raj Kumar")
    if (lower.contains("se mera matlab") || lower.contains("remember that") || lower.contains(" means ") || lower.contains(" refers to ")) {
      if (lower.contains("raju") && lower.contains("raj kumar")) {
        return EveInterpretation.vocabulary("Raju", "EMPLOYEE", "Raj Kumar");
      }
      if (lower.contains("royal") && lower.contains("royal gala")) {
        return EveInterpretation.vocabulary("Royal", "PRODUCTION", "Royal Gala");
      }
      Matcher m = VOCABULARY_PATTERN.matcher(prompt);
      if (m.find()) {
        return EveInterpretation.vocabulary(m.group(1).trim(), "VOCABULARY", m.group(2).trim());
      }
    }

    // 5. Cross-domain: Member presence check ("Usme Sharma bhi tha?" / "Was Sharma in Royal?")
    if (lower.contains("usme") && (lower.contains("sharma") || lower.contains("raj"))) {
      return EveInterpretation.crossDomain(Intent.CHECK_PRODUCTION_MEMBER, "PRODUCTION", "usme", "Sharma");
    }
    if (lower.contains("sharma") && lower.contains("tha") && lower.contains("royal")) {
      return EveInterpretation.crossDomain(Intent.CHECK_PRODUCTION_MEMBER, "PRODUCTION", "Royal", "Sharma");
    }

    // 6. Production Crew ("Royal mein kaun gaya tha?" / "Who was in Royal?")
    if ((lower.contains("kaun gaya") || lower.contains("who went") || lower.contains("crew")) && (lower.contains("royal") || lower.contains("wedding") || lower.contains("gala") || lower.contains("production") || lower.contains("event"))) {
      String spoken = "Royal";
      if (lower.contains("royal wedding")) spoken = "Royal Wedding";
      else if (lower.contains("royal gala")) spoken = "Royal Gala";
      return EveInterpretation.of(Intent.READ_PRODUCTION_CREW, "PRODUCTION", spoken);
    }

    // 7. Follow-up: Production Equipment ("Kaunsa equipment gaya tha?" / "Aur uska equipment?")
    if ((lower.contains("equipment") || lower.contains("gear")) && (lower.contains("uska") || lower.contains("royal") || lower.contains("that") || lower.contains("production") || lower.contains("event"))) {
      String spoken = lower.contains("royal") ? "Royal" : "uska";
      return EveInterpretation.of(Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", spoken, true);
    }

    // 8. Follow-up: Employee assignments ("Sharma ka kaam kis production pe tha?" / "Which production?")
    if ((lower.contains("which production") || lower.contains("production kaunsa") || lower.contains("kis production")) ||
        (lower.contains("sharma") && lower.contains("production"))) {
      String spoken = lower.contains("sharma") ? "Sharma" : "uska";
      return EveInterpretation.of(Intent.READ_EMPLOYEE_ASSIGNMENTS, "EMPLOYEE", spoken, true);
    }

    // 9. Work / Task queries ("Kaunsa task abhi open hai?" / "open tasks")
    if (lower.contains("task") && (lower.contains("open") || lower.contains("pending") || lower.contains("chal raha") || lower.contains("baaki"))) {
      return EveInterpretation.of(Intent.READ_TASKS_SUMMARY, "WORK", "Task");
    }

    // 10. Headquarters / Equipment availability ("Stand kitna available hai?")
    if (lower.contains("stand") || lower.contains("c-stand") || (lower.contains("equipment") && lower.contains("available"))) {
      return EveInterpretation.of(Intent.READ_EQUIPMENT_AVAILABILITY, "EQUIPMENT", "Stand");
    }

    // 11. Relative Date queries ("Kal kaunsa event hai?" / "Aaj ka schedule")
    if (lower.contains("kal") || lower.contains("tomorrow") || lower.contains("aaj") || lower.contains("today") || lower.contains("parso")) {
      String date = "kal";
      if (lower.contains("aaj") || lower.contains("today")) date = "aaj";
      else if (lower.contains("parso")) date = "parso";
      return EveInterpretation.withDate(Intent.READ_SCHEDULE_BY_DATE, date);
    }

    // 12. Employee Finance queries ("How much does Sharma still need?" / "Sharma ka kitna baaki hai?")
    if (lower.contains("sharma") || lower.contains("kitna baaki") || lower.contains("need") || lower.contains("pending") || lower.contains("outstanding") || lower.contains("owed") || lower.contains("uska payment")) {
      String spoken = "Sharma";
      if (lower.contains("raj sharma")) {
        spoken = "Raj Sharma";
      } else if (lower.contains("amit sharma")) {
        spoken = "Amit Sharma";
      } else if (lower.contains("uska") && !lower.contains("sharma")) {
        spoken = "uska";
      }
      return EveInterpretation.of(Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", spoken);
    }

    // 13. General Production Info
    if (lower.contains("royal")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "Royal");
    }

    // 14. Overview / System Summary
    if (lower.contains("summary") || lower.contains("overview") || lower.contains("kya chal raha hai")) {
      return EveInterpretation.of(Intent.READ_SYSTEM_SUMMARY, "SYSTEM", "Overview");
    }

    // Default fallback
    return EveInterpretation.of(Intent.UNKNOWN, null, null);
  }
}
