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

    // 3. Governed Execution: Employee Payment Proposal ("Sharma ko 3000 de do", "Pay Sharma 3000", etc.)
    boolean isPaymentAction = lower.contains("de do")
        || lower.contains("pay")
        || lower.contains("payout")
        || lower.contains("transfer")
        || lower.contains("bhejo")
        || lower.contains("payment kar");
    boolean isReadQuery = lower.contains("kitna")
        || lower.contains("how much")
        || lower.contains("need")
        || lower.contains("pending")
        || lower.contains("outstanding")
        || lower.contains("history")
        || lower.contains("check");

    if (isPaymentAction && !isReadQuery) {
      java.util.Optional<Long> amtOpt = EveAmountParser.parseAmountMinor(prompt);
      if (amtOpt.isPresent()) {
        String empSpoken = "Sharma";
        if (lower.contains("rehan ali")) {
          empSpoken = "Rehan Ali";
        } else if (lower.contains("rehan")) {
          empSpoken = "Rehan";
        } else if (lower.contains("farhan")) {
          empSpoken = "Farhan";
        } else if (lower.contains("kabir")) {
          empSpoken = "Kabir";
        } else if (lower.contains("sarah")) {
          empSpoken = "Sarah";
        } else if (lower.contains("raj sharma")) {
          empSpoken = "Raj Sharma";
        } else if (lower.contains("amit sharma")) {
          empSpoken = "Amit Sharma";
        } else if (lower.contains("raj kumar") || lower.contains("raju")) {
          empSpoken = "Raj Kumar";
        } else if (lower.contains("sunil")) {
          empSpoken = "Sunil";
        } else if (lower.contains("vipin")) {
          empSpoken = "Vipin";
        } else if (lower.contains("karan")) {
          empSpoken = "Karan";
        } else if (lower.contains("sharma")) {
          empSpoken = "Sharma";
        } else if (lower.contains("use") || lower.contains("uska") || lower.contains("him")) {
          empSpoken = "uska";
        } else if (lower.startsWith("pay ")) {
          String[] parts = prompt.trim().split("\\s+");
          if (parts.length >= 2) {
            empSpoken = parts[1];
          }
        } else if (lower.contains(" ko ")) {
          String beforeKo = prompt.substring(0, lower.indexOf(" ko ")).trim();
          String[] parts = beforeKo.split("\\s+");
          empSpoken = parts[parts.length - 1];
        }

        String payer = null;
        if (lower.contains("az-2") || lower.contains("azeem")) {
          payer = "AZ-2";
        } else if (lower.contains("ak-2") || lower.contains("akash")) {
          payer = "AK-2";
        }

        return EveInterpretation.proposePayment(empSpoken, amtOpt.get(), payer);
      }
    }

    // 4. Disambiguation selection on follow-up ("the second one", "2nd", "Royal Gala", "27th wala")
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

    // 6. Production Client queries ("cultural event MIPS ka client kon hai?", "MIPS ka client kaun hai?", "Who is the client for Cultural Event MIPS?", "MIPS kis client ke liye hai?", etc.)
    boolean isClientQuery = lower.contains("client")
        || lower.contains("customer")
        || lower.contains("kiska event")
        || lower.contains("kiska production")
        || lower.contains("kis client");

    if (isClientQuery) {
      String spoken = "Cultural Event MIPS";
      boolean followUp = false;
      if (lower.startsWith("is event ka") || lower.startsWith("iska client") || lower.startsWith("uska client") || lower.contains("us event ka") || (lower.contains("uska") && !lower.contains("mips") && !lower.contains("wedding") && !lower.contains("royal")) || (lower.contains("usme") && !lower.contains("mips") && !lower.contains("wedding") && !lower.contains("royal"))) {
        spoken = "uska";
        followUp = true;
      } else if (lower.contains("cultural event mips")) {
        spoken = "Cultural Event MIPS";
      } else if (lower.contains("mips")) {
        spoken = "MIPS";
      } else if (lower.contains("royal wedding")) {
        spoken = "Royal Wedding";
      } else if (lower.contains("corporate brand film")) {
        spoken = "Corporate Brand Film";
      } else if (lower.contains("heritage campaign")) {
        spoken = "Heritage Campaign";
      } else if (lower.contains("royal gala")) {
        spoken = "Royal Gala";
      } else if (lower.contains("royal")) {
        spoken = "Royal";
      } else {
        Pattern p1 = Pattern.compile("(?i)(?:who is the (?:client|customer) for|what(?:'s| is) the (?:client|customer) (?:for|of)|tell me the (?:client|customer) (?:of|for)|which client is)\\s+(.+?)(?:\\s+for)?(?:\\?|$)");
        Matcher m1 = p1.matcher(prompt);
        if (m1.find()) {
          spoken = m1.group(1).trim().replaceAll("(?i)^(?:event|production|the event|the production)\\s+", "");
        } else {
          Pattern p2 = Pattern.compile("(?i)(.+?)\\s+(?:ka|ke|ki|kiske|kis)?\\s*(?:client|customer|party)");
          Matcher m2 = p2.matcher(prompt);
          if (m2.find()) {
            spoken = m2.group(1).trim().replaceAll("(?i)^(?:is|was|the|event|production)\\s+", "");
          } else {
            Pattern p3 = Pattern.compile("(?i)(.+?)\\s+kis\\s+client");
            Matcher m3 = p3.matcher(prompt);
            if (m3.find()) {
              spoken = m3.group(1).trim();
            }
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", spoken, followUp);
    }

    // 7. Production Timing / Schedule follow-up ("Uska event kab hai?" / "When is the event?" / "Kab hai event?")
    if ((lower.contains("kab hai") || lower.contains("kab h") || lower.contains("when is the event") || lower.contains("event date") || lower.contains("event kab") || lower.contains("timing"))
        && (lower.contains("event") || lower.contains("production") || lower.contains("uska") || lower.contains("usme") || lower.contains("that") || lower.contains("mips") || lower.contains("wedding"))) {
      String spoken = "uska";
      boolean followUp = true;
      if (!lower.contains("uska") && !lower.contains("usme") && !lower.contains("that")) {
        Pattern pDate = Pattern.compile("(?i)(?:when is the event for|when is|event date for|event date of)\\s+(.+?)(?:\\?|$)");
        Matcher mDate = pDate.matcher(prompt);
        if (mDate.find()) {
          spoken = mDate.group(1).trim();
          followUp = false;
        } else if (lower.contains("mips")) {
          spoken = "Cultural Event MIPS";
          followUp = false;
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", spoken, followUp);
    }

    // 8. Production Crew ("Royal mein kaun gaya tha?" / "Who was in Royal?" / "Usme kaun kaam kar raha hai?")
    if ((lower.contains("kaun gaya") || lower.contains("who went") || lower.contains("crew") || lower.contains("kaun kaam") || lower.contains("who is working") || lower.contains("who is assigned") || lower.contains("kaun kaun"))
        && (lower.contains("usme") || lower.contains("isme") || lower.contains("royal") || lower.contains("wedding") || lower.contains("gala") || lower.contains("production") || lower.contains("event") || lower.contains("mips"))) {
      String spoken = "Royal";
      boolean followUp = false;
      if (lower.contains("usme") || lower.contains("isme") || lower.contains("that event") || lower.contains("that production")) {
        spoken = "usme";
        followUp = true;
      } else if (lower.contains("royal wedding")) spoken = "Royal Wedding";
      else if (lower.contains("royal gala")) spoken = "Royal Gala";
      else if (lower.contains("mips")) spoken = "Cultural Event MIPS";
      return EveInterpretation.of(Intent.READ_PRODUCTION_CREW, "PRODUCTION", spoken, followUp);
    }

    // 9. Follow-up: Production Equipment ("Kaunsa equipment gaya tha?" / "Aur uska equipment?")
    if ((lower.contains("equipment") || lower.contains("gear")) && (lower.contains("uska") || lower.contains("royal") || lower.contains("that") || lower.contains("production") || lower.contains("event"))) {
      String spoken = lower.contains("royal") ? "Royal" : "uska";
      return EveInterpretation.of(Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", spoken, true);
    }

    // 10. Follow-up: Employee assignments ("Sharma ka kaam kis production pe tha?" / "Uska production kaunsa hai?")
    if ((lower.contains("which production") || lower.contains("production kaunsa") || lower.contains("kis production")) ||
        (lower.contains("sharma") && lower.contains("production")) ||
        (lower.contains("rehan") && lower.contains("production"))) {
      String spoken = "uska";
      if (lower.contains("rehan")) {
        spoken = "Rehan Ali";
      } else if (lower.contains("sharma")) {
        spoken = "Sharma";
      }
      return EveInterpretation.of(Intent.READ_EMPLOYEE_ASSIGNMENTS, "EMPLOYEE", spoken, true);
    }

    // 11. Production Tasks queries ("Usme kaunsa task open hai?", "Sharma Wedding mein kaunsa task open hai?")
    if (lower.contains("task") && (lower.contains("usme") || lower.contains("isme") || (lower.contains("production") && !lower.contains("open tasks")) || (lower.contains("event") && !lower.contains("open tasks")) || lower.contains("wedding") || lower.contains("mips"))) {
      String spoken = "usme";
      boolean followUp = true;
      if (lower.contains("mips")) {
        spoken = "MIPS";
        followUp = false;
      } else if (lower.contains("sharma wedding")) {
        spoken = "Sharma Wedding";
        followUp = false;
      } else if (lower.contains("royal")) {
        spoken = "Royal";
        followUp = false;
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_TASKS, "PRODUCTION", spoken, followUp);
    }

    // 11. Work / Task queries ("Kaunsa task abhi open hai?" / "open tasks")
    if (lower.contains("task") && (lower.contains("open") || lower.contains("pending") || lower.contains("chal raha") || lower.contains("baaki"))) {
      return EveInterpretation.of(Intent.READ_TASKS_SUMMARY, "WORK", "Task");
    }

    // 12. Headquarters / Equipment availability ("Stand kitna available hai?")
    if (lower.contains("stand") || lower.contains("c-stand") || (lower.contains("equipment") && lower.contains("available"))) {
      return EveInterpretation.of(Intent.READ_EQUIPMENT_AVAILABILITY, "EQUIPMENT", "Stand");
    }

    // 13. Relative Date queries ("Kal kaunsa event hai?" / "Aaj ka schedule")
    if (lower.contains("kal") || lower.contains("tomorrow") || lower.contains("aaj") || lower.contains("today") || lower.contains("parso")) {
      String date = "kal";
      if (lower.contains("aaj") || lower.contains("today")) date = "aaj";
      else if (lower.contains("parso")) date = "parso";
      return EveInterpretation.withDate(Intent.READ_SCHEDULE_BY_DATE, date);
    }

    // 14. Employee Finance queries ("How much does Sharma still need?" / "How much does Rehan Ali need?")
    if (lower.contains("sharma") || lower.contains("rehan") || lower.contains("kitna baaki") || lower.contains("need") || lower.contains("pending") || lower.contains("outstanding") || lower.contains("owed") || lower.contains("uska payment")) {
      String spoken = "Sharma";
      if (lower.contains("rehan ali")) {
        spoken = "Rehan Ali";
      } else if (lower.contains("rehan")) {
        spoken = "Rehan";
      } else if (lower.contains("sarah")) {
        spoken = "Sarah";
      } else if (lower.contains("amaan")) {
        spoken = "Amaan";
      } else if (lower.contains("farhan")) {
        spoken = "Farhan";
      } else if (lower.contains("kabir")) {
        spoken = "Kabir";
      } else if (lower.contains("raj sharma")) {
        spoken = "Raj Sharma";
      } else if (lower.contains("amit sharma")) {
        spoken = "Amit Sharma";
      } else if (lower.contains("sharma")) {
        spoken = "Sharma";
      } else if (lower.contains("uska") && !lower.contains("sharma")) {
        spoken = "uska";
      }
      return EveInterpretation.of(Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", spoken);
    }

    // 15. General Production Info
    if (lower.contains("royal")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "Royal");
    }

    // 16. Overview / System Summary
    if (lower.contains("summary") || lower.contains("overview") || lower.contains("kya chal raha hai")) {
      return EveInterpretation.of(Intent.READ_SYSTEM_SUMMARY, "SYSTEM", "Overview");
    }

    // Default fallback
    return EveInterpretation.of(Intent.UNKNOWN, null, null);
  }
}
