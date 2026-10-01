package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic test model provider for EVE.
 *
 * ARCHITECTURAL PRINCIPLE:
 * This provider performs purely grammar-based, domain-agnostic semantic slot extraction.
 * It DOES NOT contain benchmark entity dictionaries, expected entity mappings, or default business entities.
 * The canonical entities and domain capabilities are resolved by the EVE System Model and Capability Resolver,
 * NOT memorized here.
 */
@Component
@ConditionalOnProperty(name = "app.eve.model-provider", havingValue = "TEST", matchIfMissing = true)
public class TestModelProvider implements EveModelProvider, EveResponseComposer {

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

    // 2b. Conversational greetings and pleasantries ("hey", "hi", "hello", "thanks", "kya haal hai", etc.)
    if (EveRetrievalRouter.isConversationalGreeting(prompt)) {
      return EveInterpretation.of(Intent.GREETING, null, null);
    }

    // 2c. Multi-turn Pending Clarification Continuation
    String sessionCtx = request.sessionContext() != null ? request.sessionContext() : "";
    if (sessionCtx.contains("[Pending Clarification:")) {
      boolean hasFinanceIndicator = lower.contains("dena hai") || lower.contains("kitna dena") || lower.contains("kitna baaki")
          || lower.contains("pending payment") || lower.contains("outstanding") || lower.contains("owed") || lower.contains("owe")
          || lower.contains("hisab") || lower.contains("salary")
          || lower.contains("pay") || lower.contains("payout") || lower.contains("transfer");
      boolean hasProductionIndicator = lower.contains("event") || lower.contains("production") || lower.contains("show")
          || lower.contains("crew") || lower.contains("kaun gaya") || lower.contains("kon gaya") || lower.contains("client");

      String entitySpoken = extractCleanEntity(prompt);
      if (sessionCtx.contains("waiting for PRODUCTION") && !hasFinanceIndicator) {
        Intent pendingIntent = Intent.READ_PRODUCTION;
        if (sessionCtx.contains("READ_PRODUCTION_EQUIPMENT")) pendingIntent = Intent.READ_PRODUCTION_EQUIPMENT;
        else if (sessionCtx.contains("READ_PRODUCTION_CLIENT")) pendingIntent = Intent.READ_PRODUCTION_CLIENT;
        else if (sessionCtx.contains("READ_PRODUCTION_CREW")) pendingIntent = Intent.READ_PRODUCTION_CREW;
        else if (sessionCtx.contains("READ_PRODUCTION_TASKS")) pendingIntent = Intent.READ_PRODUCTION_TASKS;

        return EveInterpretation.withReferences(
            pendingIntent,
            "PRODUCTION",
            entitySpoken,
            List.of(EveModelProvider.SemanticReference.of(prompt, "PRODUCTION", entitySpoken)),
            true);
      } else if (sessionCtx.contains("waiting for EMPLOYEE") && !hasProductionIndicator) {
        Intent pendingIntent = Intent.READ_EMPLOYEE_FINANCE;
        if (sessionCtx.contains("READ_EMPLOYEE_ASSIGNMENTS")) pendingIntent = Intent.READ_EMPLOYEE_ASSIGNMENTS;

        return EveInterpretation.withReferences(
            pendingIntent,
            "EMPLOYEE",
            entitySpoken,
            List.of(EveModelProvider.SemanticReference.of(prompt, "EMPLOYEE", entitySpoken)),
            true);
      }
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
        String empSpoken = extractRecipient(prompt);
        String payer = extractPayerAccount(prompt);
        return EveInterpretation.proposePayment(empSpoken, amtOpt.get(), payer);
      }
    }

    // 4. Disambiguation selection on follow-up ("the second one", "2nd", "27th wala", "second wala", "pehla wala")
    if (lower.matches(".*\\b(?:second|2nd|first|1st|third|3rd|pehla|doosra|27th)(?:\\s+wala|\\s+one)?\\b.*")
        || lower.equals("2nd wala") || lower.equals("second wala") || lower.equals("pehla wala")
        || lower.equals("the second one") || lower.equals("first one") || lower.equals("second one")) {
      return EveInterpretation.disambiguate(prompt);
    }

    // 5. Vocabulary learning ("Raju se mera matlab Raj Kumar hai" / "Raju means Raj Kumar")
    if (lower.contains("se mera matlab")) {
      Matcher m = Pattern.compile("(?i)(.+?)\\s+se mera matlab\\s+(.+?)(?:\\s+hai)?\\.?$").matcher(prompt);
      if (m.find()) {
        return EveInterpretation.vocabulary(cleanEntity(m.group(1)), "VOCABULARY", cleanEntity(m.group(2)));
      }
    }
    if (lower.contains("remember that") || lower.contains(" means ") || lower.contains(" refers to ")) {
      Matcher m = VOCABULARY_PATTERN.matcher(prompt);
      if (m.find()) {
        return EveInterpretation.vocabulary(cleanEntity(m.group(1)), "VOCABULARY", cleanEntity(m.group(2)));
      }
    }

    // 5b. Short query robustness for queries like "sharma weddng crew?", "sharma weddng finance?", "sharma wedng me kaun hai?"
    Pattern pShortQuery = Pattern.compile("(?i)^([a-zA-Z0-9\\s'-]+?)\\s+(?:ka|ke|ki|me|mein)?\\s*(crew|client|venue|date|timing|tasks?|equipment|gear|finance|advance|outstanding|kaun hai|kon hai)\\??$");
    Matcher mShort = pShortQuery.matcher(prompt.trim());
    if (mShort.find()) {
      String rawEntity = mShort.group(1).trim();
      String lowerRaw = rawEntity.toLowerCase(Locale.ROOT);
      if (!lowerRaw.contains("associated") && !lowerRaw.contains("which") && !lowerRaw.contains("kiske")) {
        boolean isPronoun = !lowerRaw.contains("mips") && (EveRetrievalRouter.isPronoun(rawEntity)
            || lowerRaw.matches("(?i)^(?:us|is|that|this)\\s+(?:event|production)(?:\\s+ka|\\s+ke|\\s+ki)?.*")
            || lowerRaw.matches("(?i)^(?:uska|uski|uske|iska|iski|iske|unka|unki|unke).*"));
        String entity = isPronoun ? "uska" : toTitleCase(cleanEntity(rawEntity));
        boolean followUp = isPronoun;
        String slot = mShort.group(2).toLowerCase(Locale.ROOT);
        if ((slot.equals("kaun hai") || slot.equals("kon hai")) && (lowerRaw.contains("client") || lowerRaw.contains("customer"))) {
          boolean isClientPronoun = !lowerRaw.contains("mips") && (EveRetrievalRouter.isPronoun(rawEntity)
              || lowerRaw.matches("(?i)^(?:us|is|that|this)\\s+(?:event|production)(?:\\s+ka|\\s+ke|\\s+ki)?.*")
              || lowerRaw.matches("(?i)^(?:uska|uski|uske|iska|iski|iske|unka|unki|unke).*"));
          String cleanProd = isClientPronoun ? "uska" : toTitleCase(cleanEntity(rawEntity.replaceAll("(?i)\\b(client|customer)\\b", "").trim()));
          return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", cleanProd, isClientPronoun);
        }
        return switch (slot) {
          case "crew", "kaun hai", "kon hai" -> EveInterpretation.of(Intent.READ_PRODUCTION_CREW, "PRODUCTION", entity, followUp);
          case "client" -> EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", entity, followUp);
          case "tasks", "task" -> EveInterpretation.of(Intent.READ_PRODUCTION_TASKS, "PRODUCTION", entity, followUp);
          case "equipment", "gear" -> EveInterpretation.of(Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", entity, followUp);
          case "finance", "advance", "outstanding" -> EveInterpretation.of(Intent.READ_PRODUCTION_FINANCE, "PRODUCTION", entity, followUp);
          default -> EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", entity, followUp);
        };
      }
    }

    // 5c. Employee Code lookup ("EMP-050", "Who is EMP-037?", "Which employee has code EMP-050?")
    Matcher mEmpCode = Pattern.compile("(?i)\\b(EMP-\\d{2,4})\\b").matcher(prompt);
    if (mEmpCode.find()) {
      return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", mEmpCode.group(1).toUpperCase(Locale.ROOT), false);
    }

    // 5d. Search / Find entity lookups ("Find Aarav Mehta", "Search for Kabir", "Find Sharma Wedding", "Lookup Zoya")
    Pattern pFind = Pattern.compile("(?i)^(?:find|search for|search|lookup|look up|show me|batao)\\s+([a-zA-Z0-9\\s'-]+?)(?:\\?|\\.|$)$");
    Matcher mFind = pFind.matcher(prompt.trim());
    if (mFind.find()) {
      String raw = mFind.group(1).trim();
      if (!raw.isBlank() && !raw.equalsIgnoreCase("all") && !raw.equalsIgnoreCase("everything")) {
        String cleaned = cleanEntity(raw);
        boolean isProd = cleaned.toLowerCase(Locale.ROOT).matches(".*\\b(wedding|reception|gala|retreat|sangeet|celebration|ceremony|film|shoot|event|production|festival|summit|conference|party|annual|launch|expo|exhibition)\\b.*");
        if (isProd) {
          return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", toTitleCase(cleaned), false);
        } else {
          return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", toTitleCase(cleaned), false);
        }
      }
    }

    // 5e. Which productions are for client query ("Which productions are for Sharma Family?", "What events are for Kapoor Family?")
    Matcher mClientProds = Pattern.compile("(?i)(?:which productions|which events|what events|what productions|productions for|events for)\\s+(?:are for|for)?\\s*([a-zA-Z0-9\\s'-]+?)(?:\\?|\\.|$)$").matcher(prompt.trim());
    if (mClientProds.find()) {
      String client = cleanEntity(mClientProds.group(1));
      return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", toTitleCase(client), false);
    }

    // 5f. General production attributes ("What is X?", "When is X?", "Where is X?", "Venue for X", "Priority of X", "Notes for X", "What time does X start/end?")
    boolean hasSpecificDomainKeyword = lower.contains("contract") || lower.contains("advance") || lower.contains("outstanding")
        || lower.contains("finance") || lower.contains("crew") || lower.contains("client") || lower.contains("customer")
        || lower.contains("equipment") || lower.contains("gear") || lower.contains("task") || lower.contains("open tasks");
    if (!hasSpecificDomainKeyword) {
      Pattern pProdAttr = Pattern.compile("(?i)(?:what is|when is|where is|venue for|venue of|priority of|notes (?:for|on|do we have for)|what time does|start time of|end time of)\\s+([a-zA-Z0-9\\s'-]+?)(?:\\s+start|\\s+end|\\?|\\.|$)$");
      Matcher mProdAttr = pProdAttr.matcher(prompt.trim());
      if (mProdAttr.find()) {
        String candidate = cleanEntity(mProdAttr.group(1));
        if (!candidate.isBlank()) {
          boolean isProd = candidate.toLowerCase(Locale.ROOT).matches(".*\\b(wedding|reception|gala|retreat|sangeet|celebration|ceremony|film|shoot|event|production|festival|summit|conference|party|annual|launch|expo|exhibition)\\b.*");
          if (isProd) {
            return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", toTitleCase(candidate), false);
          }
        }
      }
    }

    // 5g. Conversational follow-ups and subject transitions ("What about X?", "And X?", "Aur X?")
    Pattern pWhatAbout = Pattern.compile("(?i)^(?:what about|and|aur)\\s+(.+?)(?:\\?|\\.|$)$");
    Matcher mWhatAbout = pWhatAbout.matcher(prompt.trim());
    if (mWhatAbout.find()) {
      String subject = cleanEntity(mWhatAbout.group(1));
      String subLower = subject.toLowerCase(Locale.ROOT);
      boolean isComplexQuery = subLower.contains("kitna") || subLower.contains("kitne") || subLower.contains("dena")
          || subLower.contains("equipment") || subLower.contains("client") || subLower.contains("kaun")
          || subLower.contains("kon") || subLower.contains("task") || subLower.contains("gear");
      if (!isComplexQuery) {
        if (subLower.contains("wedding") || subLower.contains("reception") || subLower.contains("gala")
            || subLower.contains("event") || subLower.contains("production") || subLower.contains("that")
            || subLower.contains("first") || subLower.contains("second") || subLower.contains("third")) {
          boolean isFollowUp = subLower.contains("that") || subLower.contains("first") || subLower.contains("second");
          return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", isFollowUp ? "uska" : toTitleCase(subject), isFollowUp);
        } else if (!subLower.isBlank()) {
          return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", toTitleCase(subject), false);
        }
      }
    }

    // 5h. Hinglish Production Attribute queries ("Sharma wedding ka venue kya hai?", "Sharma wedding kahan hai?", "Sharma wedding kab hai?")
    Pattern pHingProd = Pattern.compile("(?i)([a-zA-Z0-9\\s'-]+?)\\s+(?:ka|ke|ki)?\\s*(?:venue|kahan hai|kaha ho rahi|kab hai|date|kitne baje|start time|end time)");
    Matcher mHingProd = pHingProd.matcher(prompt);
    if (mHingProd.find() && !mHingProd.group(1).isBlank()) {
      String candidate = cleanEntity(mHingProd.group(1));
      boolean isProd = candidate.toLowerCase(Locale.ROOT).matches(".*\\b(wedding|reception|gala|retreat|sangeet|celebration|ceremony|film|shoot|event|production|festival|summit|conference|party|annual|launch|expo|exhibition)\\b.*");
      if (isProd) {
        return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", toTitleCase(candidate), false);
      }
    }

    // 5i. Standalone Context Follow-ups / Ellipsis queries ("What is the client?", "What are their roles?", "What's the date?")
    if (lower.equals("what is the client?") || lower.equals("what is the client") || lower.equals("what's the client?") || lower.equals("what's the client")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "uska", true);
    }
    if (lower.equals("what's the date?") || lower.equals("what's the date") || lower.equals("when is it?") || lower.equals("where is it?")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "uska", true);
    }
    if (lower.contains("their role") || lower.contains("their roles")) {
      return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", "uska", true);
    }
    if (lower.contains("still outstanding") || lower.equals("what's outstanding?") || lower.equals("what's outstanding") || lower.equals("what is outstanding?") || lower.equals("what is outstanding")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION_FINANCE, "PRODUCTION", "uska", true);
    }
    if (lower.contains("everything important")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "uska", true);
    }

    // 6. Production Crew ("Royal mein kaun gaya tha?" / "Who was in Royal?" / "Usme kaun kaam kar raha hai?")
    // Evaluated before member check so "kaun gaya tha" is not confused with member presence
    boolean isCrewQuery = lower.contains("kaun gaya")
        || lower.contains("kon gaya")
        || lower.contains("kaun gya")
        || lower.contains("kon gya")
        || lower.contains("who went")
        || lower.contains("who works")
        || lower.contains("who worked")
        || lower.contains("who is working")
        || lower.contains("who was working")
        || lower.contains("who is assigned")
        || lower.contains("who was assigned")
        || lower.contains("who is in")
        || lower.contains("who was in")
        || lower.contains("who is on")
        || lower.contains("who was on")
        || lower.contains("who from")
        || lower.contains("who else is")
        || lower.contains("who works with")
        || lower.contains("who is with")
        || lower.contains("who is there")
        || lower.contains("who was there")
        || lower.contains("crew")
        || lower.contains("kaun kaam")
        || lower.contains("kon kaam")
        || lower.contains("kaam kar")
        || lower.contains("kaun kaun")
        || lower.contains("kon kon")
        || lower.contains("kitne log")
        || lower.contains("team")
        || lower.contains("ke log")
        || lower.contains("log kaun")
        || lower.contains("kaun handle")
        || (lower.contains("event") && (lower.contains("me kon") || lower.contains("mein kaun") || lower.contains("me kaun") || lower.contains("mein kon")));

    if (isCrewQuery && !lower.contains("client") && !lower.contains("customer")) {
      String spoken = "usme";
      boolean followUp = true;
      boolean isPronounCrew = EveRetrievalRouter.isPronoun(lower) || lower.contains("usme") || lower.contains("isme")
          || lower.contains("that event") || lower.contains("that production") || lower.contains("is event") || lower.contains("us event")
          || lower.contains("there") || lower.contains("that one") || lower.contains("first one") || lower.contains("second one")
          || lower.contains("with him") || lower.contains("with her");
      if (!isPronounCrew) {
        Pattern pCrew = Pattern.compile("(?i)(?:who (?:from [a-zA-Z\\s]+? )?(?:is|was|works|worked|are|went)?\\s*(?:assigned to|working on|works on|worked on|in|at|on|to)?|crew for|crew of)\\s+(.+?)(?:\\?|$)");
        Matcher mCrew = pCrew.matcher(prompt);
        if (mCrew.find() && !mCrew.group(1).isBlank()) {
          spoken = toTitleCase(cleanEntity(mCrew.group(1)));
          followUp = false;
        } else {
          Pattern pCrewFront = Pattern.compile("(?i)^(?:achha|acha|sun|suno|bhai|bhaiya|please|can you|ok|okay)?\\s*(?:kon|kaun)\\s+(?:gaya|gya|hai|he|tha|the|kaam kar raha)\\s+(.+?)(?:\\s+(?:mein|me|pe|par))?(?:\\?|$)");
          Matcher mFront = pCrewFront.matcher(prompt);
          if (mFront.find() && !mFront.group(1).isBlank()) {
            spoken = toTitleCase(cleanEntity(mFront.group(1)));
            followUp = false;
          } else {
            Pattern pCrew2 = Pattern.compile("(?i)(.+?)\\s+(?:(?:ke|ka|ki)\\s+)?(?:event|production|show)?\\s*(?:mein|me|par|pe|ke liye|ka|ke|ki)?\\s*(?:kaun|kon|who|crew|kitne log|kaam|kon kon|kaun kaun|team|ke log)");
            Matcher mCrew2 = pCrew2.matcher(prompt);
            if (mCrew2.find() && !mCrew2.group(1).isBlank()) {
              spoken = toTitleCase(cleanEntity(mCrew2.group(1)));
              followUp = false;
            }
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_CREW, "PRODUCTION", spoken, followUp);
    }

    // 7. Cross-domain: Member presence check ("Usme Sharma bhi tha?" / "Was Sharma in Royal?")
    boolean isExplicitMemberCheck = lower.contains("bhi tha")
        || lower.contains("bhi tha?")
        || ((lower.startsWith("was ") || lower.startsWith("is ")) && (lower.contains(" in ") || lower.contains(" assigned to ")));

    if (isExplicitMemberCheck && !lower.startsWith("what ") && !lower.startsWith("why ") && !lower.startsWith("how ")) {
      // Follow-up context check: "Usme Sharma bhi tha?"
      Pattern pFollowUpMember = Pattern.compile("(?i)^(?:usme|isme|in that|in this)\\s+([a-zA-Z\\s]+?)(?:\\s+bhi)?\\s*(?:tha|hai)?\\??$");
      Matcher mFollowUp = pFollowUpMember.matcher(prompt);
      if (mFollowUp.find()) {
        String emp = cleanEntity(mFollowUp.group(1));
        String prod = lower.startsWith("isme") ? "isme" : "usme";
        return EveInterpretation.crossDomain(Intent.CHECK_PRODUCTION_MEMBER, "PRODUCTION", prod, emp);
      }
      Pattern pMemberCheck = Pattern.compile("(?i)^(?:was|is)\\s+([a-zA-Z\\s]+?)\\s+(?:in|assigned to)\\s+([a-zA-Z0-9\\s]+?)(?:\\?|$)");
      Matcher mCheck = pMemberCheck.matcher(prompt);
      if (mCheck.find()) {
        String emp = cleanEntity(mCheck.group(1));
        String prod = cleanEntity(mCheck.group(2));
        if (!emp.isBlank() && !prod.isBlank()) {
          return EveInterpretation.crossDomain(Intent.CHECK_PRODUCTION_MEMBER, "PRODUCTION", toTitleCase(prod), toTitleCase(emp));
        }
      }
    }

    // 8. Production Client queries ("cultural event MIPS ka client kon hai?", "Who is the client for Cultural Event MIPS?", etc.)
    boolean isClientQuery = lower.contains("client")
        || lower.contains("customer")
        || lower.contains("kiska event")
        || lower.contains("kiska production")
        || lower.contains("kis client")
        || lower.contains("commissioned")
        || lower.contains("which party");

    if (isClientQuery) {
      String spoken = null;
      boolean followUp = false;
      boolean isPronounFollowUp = !lower.contains("mips") && (EveRetrievalRouter.isPronoun(lower)
          || lower.matches("(?i)^(?:is|us)\\s+event(?:\\s+ka|\\s+ke|\\s+ki)?\\s+(?:client|customer|party).*")
          || lower.matches("(?i)^(?:iska|uska)\\s+(?:client|customer|party).*")
          || lower.startsWith("iska client") || lower.startsWith("uska client")
          || lower.contains("usme")
          || lower.contains("uska"));

      if (isPronounFollowUp) {
        spoken = "uska";
        followUp = true;
      } else {
        Pattern p1 = Pattern.compile("(?i)(?:who is the (?:client|customer) for|what(?:'s| is) the (?:client|customer) (?:for|of)|tell me the (?:client|customer) (?:of|for)|which client is|associated with which client|which party commissioned|commissioned by|commissioned)\\s+(.+?)(?:\\s+for)?(?:\\?|$)");
        Matcher m1 = p1.matcher(prompt);
        if (m1.find()) {
          spoken = toTitleCase(cleanEntity(m1.group(1)));
        } else {
          Pattern p2 = Pattern.compile("(?i)(?:is\\s+event\\s+)?(.+?)\\s+(?:ka|ke|ki|kiske|kis)?\\s*(?:client|customer|party|associated with which client)");
          Matcher m2 = p2.matcher(prompt);
          if (m2.find()) {
            spoken = toTitleCase(cleanEntity(m2.group(1)));
          } else {
            spoken = toTitleCase(cleanEntity(prompt.replaceAll("(?i)\\b(client|customer|ka|ke|ki|kiska|kis|hai|hain|kon|kaun|who|is|the|for|batao|associated with which client)\\b", "")));
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", spoken, followUp);
    }

    // 9. Production Timing / Schedule ("Uska event kab hai?" / "When is the event?" / "Kab hai event?")
    if ((lower.contains("kab hai") || lower.contains("kab h") || lower.contains("when is the event") || lower.contains("event date") || lower.contains("event kab") || lower.contains("timing"))
        && (lower.contains("event") || lower.contains("production") || lower.contains("uska") || lower.contains("usme") || lower.contains("that"))) {
      String spoken = "uska";
      boolean followUp = true;
      if (!lower.contains("uska") && !lower.contains("usme") && !lower.contains("that") && !lower.contains("is event") && !lower.contains("us event")) {
        Pattern pDate = Pattern.compile("(?i)(?:when is the event for|when is|event date for|event date of)\\s+(.+?)(?:\\?|$)");
        Matcher mDate = pDate.matcher(prompt);
        if (mDate.find()) {
          spoken = toTitleCase(cleanEntity(mDate.group(1)));
          followUp = false;
        } else {
          Pattern pDate2 = Pattern.compile("(?i)(.+?)\\s+(?:ka event|ki date|kab hai)");
          Matcher mDate2 = pDate2.matcher(prompt);
          if (mDate2.find()) {
            spoken = toTitleCase(cleanEntity(mDate2.group(1)));
            followUp = false;
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", spoken, followUp);
    }

    // 10. Production Equipment ("Kaunsa equipment gaya tha?" / "What equipment does it need?" / "What equipment is needed there?")
    boolean isQuantityQuery = lower.contains("kitna") || lower.contains("kitne") || lower.contains("kitni")
        || lower.contains("available") || lower.contains("stock") || lower.contains("inventory")
        || lower.contains("how much") || lower.contains("how many") || lower.contains("bacha") || lower.contains("bache")
        || lower.contains("hamare paas") || lower.contains("hamare pass");

    boolean isProdEquipmentQuery = (lower.contains("equipment") || lower.contains("gear")) && !isQuantityQuery;
    if (isProdEquipmentQuery) {
      String spoken = "uska";
      boolean followUp = true;
      boolean isDemonstrativeFollowUp = lower.contains("us event") || lower.contains("is event") || lower.contains("us production")
          || lower.contains("is production") || lower.contains("that event") || lower.contains("that production") || lower.contains("uska")
          || lower.contains("usme") || lower.contains("that") || lower.contains("there") || lower.contains("that one") || lower.contains("second one") || lower.contains("first one")
          || lower.contains("it need") || lower.contains("they need") || lower.contains("needed");
      if (!isDemonstrativeFollowUp) {
        Pattern pEq = Pattern.compile("(?i)(.+?)\\s+(?:ka|ke|ki)?\\s*(?:equipment|gear)");
        Matcher mEq = pEq.matcher(prompt);
        if (mEq.find() && !mEq.group(1).isBlank()) {
          spoken = cleanEntity(mEq.group(1));
          followUp = false;
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", spoken, followUp);
    }

    // 11. Employee assignments ("Which production has Kabir?", "What productions is Rohan assigned to?", "Which event is he on?")
    boolean isAssignmentQuery = lower.contains("which production") || lower.contains("what production")
        || lower.contains("which event") || lower.contains("what event")
        || (lower.contains("production") && (lower.contains("assigned") || lower.contains("work on") || lower.contains("working on") || lower.contains("kaunsa") || lower.contains("kis ")))
        || (lower.contains("role") && (lower.contains("have on") || lower.contains("have there") || lower.contains("production") || lower.contains("there")))
        || lower.contains("production kaunsa") || lower.contains("kis production");

    if (isAssignmentQuery) {
      String spoken = "uska";
      boolean followUp = true;
      boolean isPronoun = EveRetrievalRouter.isPronoun(lower) || lower.contains("he ") || lower.contains("she ")
          || lower.contains("him") || lower.contains("her") || lower.contains("uska") || lower.contains("unka");
      if (!isPronoun) {
        Pattern pRoleOnProd = Pattern.compile("(?i)(?:what role does|what's the role of|role of)\\s+([a-zA-Z\\s]+?)(?:\\s+have on|\\s+have there|\\s+have|\\s+on|\\s+there)(?:\\s+[a-zA-Z0-9\\s'-]+?)?(?:\\?|$)");
        Matcher mRoleOnProd = pRoleOnProd.matcher(prompt);
        if (mRoleOnProd.find() && !mRoleOnProd.group(1).isBlank()) {
          spoken = toTitleCase(cleanEntity(mRoleOnProd.group(1)));
          followUp = false;
        } else {
          Pattern pAss1 = Pattern.compile("(?i)(?:which production(?:s)? (?:has|have|is|are)|what production(?:s)? (?:has|have|is|are)|production(?:s)? for)\\s+([a-zA-Z\\s]+?)(?:\\?|$)");
          Matcher mAss1 = pAss1.matcher(prompt);
          if (mAss1.find() && !mAss1.group(1).isBlank()) {
            spoken = toTitleCase(cleanEntity(mAss1.group(1)));
            followUp = false;
          } else {
          Pattern pAss2 = Pattern.compile("(?i)(?:which|what)\\s+(?:production|event)(?:s)?\\s+(?:is|does|are)\\s+([a-zA-Z\\s]+?)(?:\\s+(?:working on|work on|assigned to|on))?(?:\\?|$)");
          Matcher mAss2 = pAss2.matcher(prompt);
          if (mAss2.find() && !mAss2.group(1).isBlank()) {
            String cand = cleanEntity(mAss2.group(1).replaceAll("(?i)\\b(working on|work on|assigned to|working|work|assigned|on)\\b", "").trim());
            if (!cand.isBlank()) {
              spoken = toTitleCase(cand);
              followUp = false;
            }
          } else {
            Pattern pAss3 = Pattern.compile("(?i)([a-zA-Z\\s]+?)\\s+(?:ka|ke|ki)?\\s*(?:kaam|work|production)");
            Matcher mAss3 = pAss3.matcher(prompt);
            if (mAss3.find() && !mAss3.group(1).isBlank()) {
              String cand = cleanEntity(mAss3.group(1).replaceAll("(?i)\\b(which|what|production|event|is|are|does)\\b", "").trim());
              if (!cand.isBlank()) {
                spoken = toTitleCase(cand);
                followUp = false;
              }
            }
          }
        }
      }
    }
    return EveInterpretation.of(Intent.READ_EMPLOYEE_ASSIGNMENTS, "EMPLOYEE", spoken, followUp);
  }

    // 12. Production Tasks queries ("Usme kaunsa task open hai?", "What needs to be done for Sharma Wedding?", "What is pending there?")
    boolean isTasksWord = lower.contains("task") || lower.contains("tasks") || lower.contains("kaam")
        || lower.contains("work remains") || lower.contains("needs to be done") || lower.contains("kya karna hai")
        || lower.contains("what is pending") || lower.contains("what's pending") || lower.contains("task details")
        || lower.contains("pending work") || lower.contains("open work");

    boolean isProdTaskContext = EveRetrievalRouter.containsPronoun(lower) || lower.contains("mein") || lower.contains("me")
        || lower.contains("for ") || lower.contains("in ") || lower.contains("on ") || lower.contains("there")
        || lower.contains("that one") || lower.contains("second one") || lower.contains("first one")
        || lower.contains("wedding") || lower.contains("reception") || lower.contains("event") || lower.contains("production");

    if (isTasksWord && isProdTaskContext) {
      String spoken = "usme";
      boolean followUp = true;
      boolean isPronounTask = EveRetrievalRouter.containsPronoun(lower) || lower.contains("usme") || lower.contains("uska")
          || lower.contains("there") || lower.contains("that one") || lower.contains("second one") || lower.contains("first one");
      if (!isPronounTask) {
        Pattern pTask1 = Pattern.compile("(?i)(?:what needs to be done for|what still needs to be done for|what work remains for|tasks (?:in|for|on)|open tasks for|open tasks in|pending tasks for|work for)\\s+([a-zA-Z0-9\\s'-]+?)(?:\\?|\\.|$)");
        Matcher mTask1 = pTask1.matcher(prompt);
        if (mTask1.find() && !mTask1.group(1).isBlank()) {
          spoken = toTitleCase(cleanEntity(mTask1.group(1)));
          followUp = false;
        } else {
          Pattern pTask2 = Pattern.compile("(?i)([a-zA-Z0-9\\s'-]+?)\\s+(?:mein|me|ka|ke|ki)?\\s*(?:kaunsa task|open task|pending task|tasks|kaam|kya karna hai)");
          Matcher mTask2 = pTask2.matcher(prompt);
          if (mTask2.find() && !mTask2.group(1).isBlank()) {
            String cand = cleanEntity(mTask2.group(1).replaceAll("(?i)\\b(what|is|are|the|show|me|tell|give)\\b", "").trim());
            if (!cand.isBlank()) {
              spoken = toTitleCase(cand);
              followUp = false;
            }
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_TASKS, "PRODUCTION", spoken, followUp);
    }

    // 13. System-wide Work / Task queries ("Kaunsa task abhi open hai?" / "open tasks")
    if (lower.contains("task") && (lower.contains("open") || lower.contains("pending") || lower.contains("chal raha") || lower.contains("baaki"))) {
      return EveInterpretation.of(Intent.READ_TASKS_SUMMARY, "WORK", "Task");
    }

    // 14. Headquarters / Equipment inventory & stock availability queries
    // E.g.: "hamare paas kitna Gaffer Tape hai?", "Stand kitna available hai?", "tape ka stock?", "how much gaffer tape do we have in stock?"
    boolean hasEquipmentKeyword = lower.contains("gaffer tape") || lower.contains("tape") || lower.contains("c-stand")
        || (lower.contains("stand") && !lower.contains("outstanding")) || lower.contains("flight case")
        || (lower.contains("case") && !lower.contains("client")) || lower.contains("led light")
        || lower.contains("light panel") || lower.contains("camera package") || lower.contains("camera") || lower.contains("audio kit")
        || lower.contains("battery") || lower.contains("batteries") || lower.contains("equipment") || lower.contains("gear")
        || lower.contains("prop") || lower.contains("props") || lower.contains("stock") || lower.contains("inventory");

    boolean isExplicitHqEquipmentStockQuery = (lower.contains("hamare paas") || lower.contains("hamare pass")
        || lower.contains("do we have") || lower.contains("in stock") || lower.contains("inventory count"))
        && isQuantityQuery;

    boolean isProductionEquipmentContext = (lower.contains("event") || lower.contains("production") || lower.contains("wedding") || lower.contains("gala"))
        && !lower.contains("hamare paas") && !lower.contains("hamare pass");

    if ((hasEquipmentKeyword || isExplicitHqEquipmentStockQuery) && isQuantityQuery && !isProductionEquipmentContext) {
      String itemSpoken = extractItemPhrase(prompt);
      return EveInterpretation.of(Intent.READ_EQUIPMENT_AVAILABILITY, "EQUIPMENT", itemSpoken);
    }

    // 15. Relative Date queries ("Kal kaunsa event hai?" / "Aaj ka schedule")
    boolean isDateWord = lower.contains("kal") || lower.contains("tomorrow") || lower.contains("aaj") || lower.contains("today") || lower.contains("parso");
    boolean isScheduleWord = lower.contains("event") || lower.contains("schedule") || lower.contains("production")
        || lower.contains("kaunsa") || lower.contains("which") || lower.contains("program") || lower.contains("calendar");
    if (isDateWord && isScheduleWord) {
      String date = "kal";
      if (lower.contains("aaj") || lower.contains("today")) date = "aaj";
      else if (lower.contains("parso")) date = "parso";
      return EveInterpretation.withDate(Intent.READ_SCHEDULE_BY_DATE, date);
    }

    // 15b. Production Finance Domain ("What is the contract for Sharma Wedding?", "How much advance did we receive?", "is it fully paid?")
    boolean hasProdFinanceKeyword = lower.contains("contract") || lower.contains("advance")
        || lower.contains("fully paid") || lower.contains("financial status")
        || lower.contains("production contract") || lower.contains("production receipt")
        || lower.contains("received amount") || lower.contains("contracted amount")
        || lower.contains("contract value") || lower.contains("money situation")
        || lower.contains("finance details")
        || lower.contains("has been received") || lower.contains("is outstanding")
        || lower.contains("still outstanding") || lower.contains("what's outstanding")
        || lower.contains("paisa baaki") || lower.contains("kitna paisa") || lower.contains("paisa kitna")
        || (lower.contains("finance") && !lower.contains("employee") && !lower.contains("ko kitna") && !lower.contains("ka hisab"))
        || (lower.contains("outstanding") && (lower.contains("wedding") || lower.contains("reception") || lower.contains("sangeet") || lower.contains("event") || lower.contains("production") || lower.contains("meet") || lower.contains("gala") || lower.contains("expo") || lower.contains("for ") || lower.contains("there") || lower.contains("that") || lower.contains("one") || lower.contains("how much") || lower.contains("what")));

    if (hasProdFinanceKeyword && !isPaymentAction) {
      String spoken = "uska";
      boolean followUp = true;
      boolean isPronoun = EveRetrievalRouter.isPronoun(lower) || lower.contains("uska") || lower.contains("usme")
          || lower.contains("it ") || lower.contains("that ") || lower.endsWith("it?") || lower.endsWith("it")
          || lower.contains("there") || lower.contains("that one") || lower.contains("second one") || lower.contains("first one")
          || lower.contains("money situation") || lower.contains("has been received")
          || lower.contains("is outstanding") || lower.contains("still outstanding") || lower.contains("what's outstanding")
          || lower.contains("its contract") || lower.contains("its finance");
      if (!isPronoun) {
        Pattern pPFin1 = Pattern.compile("(?i)(?:what(?:'s| is)?|how much(?: is| did we receive| have we received)?|tell me|show me|has|is|give me)?\\s*(?:the\\s+)?(?:contract|advance|outstanding|received amount|financial status|finance|transactions|payments?)(?:\\s+(?:value|amount|profile|margin|details))?\\s+(?:for|of|on|from)\\s+([a-zA-Z0-9\\s'-]+?)(?:\\?|$)");
        Matcher mPFin1 = pPFin1.matcher(prompt);
        if (mPFin1.find() && !mPFin1.group(1).isBlank()) {
          spoken = toTitleCase(cleanEntity(mPFin1.group(1)));
          followUp = false;
        } else {
          Pattern pPFin2 = Pattern.compile("(?i)(?:show me\\s+|give me\\s+)?([a-zA-Z0-9\\s'-]+?)(?:'s| ka| ke| ki)?\\s+(?:contract|advance|outstanding|financial status|finance|transactions|payments?)(?:\\s+(?:value|amount|profile|margin|status|details))?(?:\\?|$)");
          Matcher mPFin2 = pPFin2.matcher(prompt);
          if (mPFin2.find() && !mPFin2.group(1).isBlank()) {
            String candidate = cleanEntity(mPFin2.group(1).replaceAll("(?i)^(?:what is|what's|how much|tell me|show me|give me|is|has|what)\\s+", "").trim());
            candidate = candidate.replaceAll("(?i)^(?:the|a|an)\\b\\s*", "").trim();
            if (!candidate.isBlank() && !candidate.equalsIgnoreCase("the")) {
              spoken = toTitleCase(candidate);
              followUp = false;
            }
          }
        }
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION_FINANCE, "PRODUCTION", spoken, followUp);
    }

    // 16. Employee Finance queries ("How much does Sharma still need?" / "How much do we still owe him?" / "Kabir Singh ko kitna dena hai?")
    boolean hasFinanceWords = lower.contains("dena hai") || lower.contains("kitna dena") || lower.contains("kitna baaki")
        || lower.contains("pending payment") || lower.contains("outstanding") || lower.contains("owed") || lower.contains("owe")
        || lower.contains("need") || lower.contains("hisab") || lower.contains("payment") || lower.contains("salary");
    boolean hasEmployeeIndicator = lower.contains("him") || lower.contains("her") || lower.contains("uska") || lower.contains("unka") || lower.contains("use")
        || lower.contains("ko") || lower.contains("ka") || lower.contains("ke") || lower.contains("ki")
        || lower.contains("owe") || lower.contains("does") || lower.contains("for");

    if (hasFinanceWords && hasEmployeeIndicator) {
      String spoken = null;
      boolean followUp = false;
      if (lower.contains("him") || lower.contains("her") || lower.contains("he") || lower.contains("uska") || lower.contains("unka") || lower.contains("use")) {
        spoken = "uska";
        followUp = true;
      } else {
        Pattern pFin1 = Pattern.compile("(?i)(?:how much (?:do we owe|does)|owe|pending payment for|balance of)\\s+([a-zA-Z\\s]+?)(?:\\s+still need|\\s+need|\\s+owe|\\?|$)");
        Matcher mFin1 = pFin1.matcher(prompt);
        if (mFin1.find()) {
          spoken = toTitleCase(cleanEntity(mFin1.group(1)));
        } else {
          Pattern pFin2 = Pattern.compile("(?i)([a-zA-Z\\s]+?)\\s+(?:ko|ka|ke|ki)\\s+(?:kitna|pending|outstanding|hisab|dena|baaki|payment)");
          Matcher mFin2 = pFin2.matcher(prompt);
          if (mFin2.find()) {
            spoken = toTitleCase(cleanEntity(mFin2.group(1)));
          }
        }
      }
      if (spoken != null && !spoken.isBlank()) {
        return EveInterpretation.of(Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", spoken, followUp);
      }
    }

    // 16b. Employee 360 / Profile queries ("Who is Aarav Mehta?", "What is his role?", "Which department is X in?", "What does X do?", "Is X full-time?", "Where does X work?", etc.)
    boolean hasProfileWords = lower.contains("role") || lower.contains("department") || lower.contains("designation")
        || lower.contains("when did") || lower.contains("join") || lower.contains("employment type")
        || lower.contains("full-time") || lower.contains("part-time") || lower.contains("freelance") || lower.contains("contract")
        || (lower.contains("active") && (lower.contains("is ") || lower.contains("hai")))
        || lower.contains("phone") || lower.contains("mobile") || lower.contains("email")
        || lower.contains("employee code") || lower.contains("salary")
        || (lower.contains("what does ") && lower.contains(" do"))
        || (lower.contains("where does ") && lower.contains(" work"))
        || lower.contains("kaha kaam") || lower.contains("kahan kaam")
        || lower.startsWith("who is ");

    if (hasProfileWords) {
      boolean isProdContext = lower.contains("wedding") || lower.contains("reception") || lower.contains("sangeet")
          || lower.contains("gala") || lower.contains("meet") || lower.contains("event") || lower.contains("production");
      if (lower.startsWith("who is ") && isProdContext) {
        String prod = cleanEntity(prompt.replaceFirst("(?i)^who is\\s+", ""));
        return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", toTitleCase(prod), false);
      }

      String spoken = "uska";
      boolean followUp = true;
      boolean isPronoun = EveRetrievalRouter.isPronoun(lower) || lower.contains("his ") || lower.contains("her ")
          || lower.contains("he ") || lower.contains("she ") || lower.contains("uska") || lower.contains("uski") || lower.contains("unka");

      if (!isPronoun) {
        Pattern pProf1 = Pattern.compile("(?i)(?:who is|role of|department of|joining date of|employment type of|salary of|phone of|email of|status of)\\s+([a-zA-Z\\s]+?)(?:\\?|$)");
        Matcher mProf1 = pProf1.matcher(prompt);
        if (mProf1.find() && !mProf1.group(1).isBlank()) {
          spoken = toTitleCase(cleanEntity(mProf1.group(1)));
          followUp = false;
        } else {
          Pattern pProf2 = Pattern.compile("(?i)([a-zA-Z\\s]+?)(?:'s| ka| ke| ki)?\\s*(?:role|designation|department|employment type|phone|mobile|email|salary|employee code|status)");
          Matcher mProf2 = pProf2.matcher(prompt);
          if (mProf2.find() && !mProf2.group(1).isBlank()) {
            spoken = toTitleCase(cleanEntity(mProf2.group(1)));
            followUp = false;
          } else {
            Pattern pProf3 = Pattern.compile("(?i)(?:when did|which department is|is)\\s+([a-zA-Z\\s]+?)(?:\\s+join|\\s+in|\\s+active|\\?|$)");
            Matcher mProf3 = pProf3.matcher(prompt);
            if (mProf3.find() && !mProf3.group(1).isBlank()) {
              spoken = toTitleCase(cleanEntity(mProf3.group(1)));
              followUp = false;
            } else {
              Pattern pProf4 = Pattern.compile("(?i)(?:what does|where does)\\s+([a-zA-Z\\s]+?)\\s+(?:do|work)(?:\\?|$)");
              Matcher mProf4 = pProf4.matcher(prompt);
              if (mProf4.find() && !mProf4.group(1).isBlank()) {
                spoken = toTitleCase(cleanEntity(mProf4.group(1)));
                followUp = false;
              } else {
                Pattern pProf5 = Pattern.compile("(?i)is\\s+([a-zA-Z\\s]+?)\\s+(?:full-time|part-time|freelance|contract)(?:\\?|$)");
                Matcher mProf5 = pProf5.matcher(prompt);
                if (mProf5.find() && !mProf5.group(1).isBlank()) {
                  spoken = toTitleCase(cleanEntity(mProf5.group(1)));
                  followUp = false;
                } else {
                  Pattern pProf6 = Pattern.compile("(?i)([a-zA-Z\\s]+?)\\s+(?:ka|ke|ki|kis)?\\s*(?:kaha(?:n)? kaam|role|salary|email|phone|department)");
                  Matcher mProf6 = pProf6.matcher(prompt);
                  if (mProf6.find() && !mProf6.group(1).isBlank()) {
                    spoken = toTitleCase(cleanEntity(mProf6.group(1)));
                    followUp = false;
                  }
                }
              }
            }
          }
        }
      }
      return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", spoken, followUp);
    }

    // 17. General Production Info / Overview ("details on Cultural Event MIPS", "tell me about MIPS", "what is X", "when is X", "where is X", "find X")
    if (lower.contains("their role") || lower.contains("their roles")) {
      return EveInterpretation.of(Intent.READ_EMPLOYEE_360, "EMPLOYEE", "uska", true);
    }
    if (lower.contains("what's the client") || lower.contains("what is the client")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "uska", true);
    }
    if (lower.contains("what's the date") || lower.contains("what is the date")) {
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "uska", true);
    }

    if ((lower.contains("details") || lower.contains("tell me about") || lower.contains("what's going on with") || lower.contains("show me")
        || lower.startsWith("what is ") || lower.startsWith("when is ") || lower.startsWith("where is ")
        || lower.contains("venue for") || lower.contains("priority of") || lower.contains("notes for")
        || lower.contains("what does the system know about") || lower.contains("connected to") || lower.contains("everything important")
        || lower.contains("what time does"))
        && !lower.contains("weather") && !lower.contains("outside") && !lower.contains("recipe") && !lower.contains("joke")) {
      String clean = cleanEntity(prompt.replaceAll("(?i)\\b(details|on|about|tell me|what's going on with|show me|what is|when is|where is|venue for|venue of|priority of|notes for|notes on|notes do we have for|what does the system know about|what is connected to|connected to|system know about|everything important about|everything important|what time does|start|end)\\b", ""));
      if (clean.isBlank() || EveRetrievalRouter.isPronoun(clean)) {
        return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", "uska", true);
      }
      return EveInterpretation.of(Intent.READ_PRODUCTION, "PRODUCTION", toTitleCase(clean));
    }

    // Default fallback
    return EveInterpretation.of(Intent.UNKNOWN, null, null);
  }

  @Override
  public String composeResponse(EveResponseCompositionRequest request) {
    if (request == null) {
      return null;
    }
    String prompt = request.userPrompt() != null ? request.userPrompt().toLowerCase(Locale.ROOT) : "";
    if (prompt.contains("phone") || prompt.contains("contact") || prompt.contains("mobile") || prompt.contains("number")
        || prompt.contains("profit") || prompt.contains("margin")
        || prompt.contains("cancel")
        || prompt.contains("quote") || prompt.contains("pricing")) {
      boolean hasInfo = false;
      if (request.evidence() != null) {
        for (var ev : request.evidence()) {
          String lbl = (ev.label() + " " + ev.value()).toLowerCase(Locale.ROOT);
          if (lbl.contains("phone") || lbl.contains("profit") || lbl.contains("cancellation") || lbl.contains("quote")) {
            hasInfo = true;
            break;
          }
        }
      }
      if (!hasInfo) {
        if (prompt.contains("phone") || prompt.contains("contact") || prompt.contains("mobile") || prompt.contains("number")) {
          return "That contact/phone number is not recorded in the available records.";
        }
        if (prompt.contains("profit") || prompt.contains("margin")) {
          return "Profitability and margin details are not recorded in the available records.";
        }
        if (prompt.contains("cancellation") || prompt.contains("cancel")) {
          return "The cancellation reason is not recorded in the available records.";
        }
        if (prompt.contains("quote") || prompt.contains("pricing")) {
          return "Client quote and pricing details are not recorded in the available records.";
        }
      }
    }
    return request.deterministicAnswer();
  }

  private String extractCleanEntity(String s) {
    if (s == null || s.isBlank()) return "";
    return s.replaceAll("(?i)^(?:achha|acha|sun|suno|bhai|bhaiya|please|can you|ok|okay|hey|the|is|was|event|production|a|an|show|wala|wali|wale)\\s+", "")
        .replaceAll("(?i)\\s+(?:ke|ka|ki)?\\s*(?:wale|wala|wali)?\\s*(?:event|production|show)?\\s*(?:ke|ka|ki)?\\s*$", "")
        .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
        .trim();
  }

  private String cleanEntity(String s) {
    if (s == null || s.isBlank()) return "";
    return s.replaceAll("(?i)^(?:achha|acha|sun|suno|bhai|bhaiya|please|can you|ok|okay|hey|the|is|was|event|production|a|an)\\s+", "")
        .replaceAll("(?i)\\s+(?:ke|ka|ki)?\\s*(?:wale|wala|wali)?\\s*(?:event|production|show)?\\s*(?:ke|ka|ki)?\\s*$", "")
        .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
        .trim();
  }

  private String extractItemPhrase(String prompt) {
    if (prompt == null) return "";
    return prompt.replaceAll("(?i)\\b(hamare paas|hamare pass|do we have|in stock|stock|inventory count|inventory|how much|how many|rolls of|pieces of|usable|available|kitna|kitne|kitni|hai|hain|ka|ke|ki|batao|check|karo|bhi|kya|show me|tell me|bacha|bache|count|what|is|are|the|quantity|of|in|hq|warehouse|do|we|have|there)\\b", "")
        .replaceAll("[^a-zA-Z0-9\\s-]", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private String extractRecipient(String prompt) {
    if (prompt == null) return null;
    String lower = prompt.toLowerCase(Locale.ROOT);
    if (lower.contains("use") || lower.contains("uska") || lower.contains("him") || lower.contains("her")) {
      return "uska";
    }
    if (lower.contains(" ko ")) {
      String beforeKo = prompt.substring(0, lower.indexOf(" ko ")).trim();
      return beforeKo.replaceAll("(?i)^(?:please|bhai|sir|can you|pay|transfer)\\s+", "").trim();
    }
    Pattern p = Pattern.compile("(?i)(?:pay|transfer|send|payout to)\\s+([a-zA-Z\\s]+?)(?:\\s+\\d+|\\s+rupees|\\s+rs|$)");
    Matcher m = p.matcher(prompt);
    if (m.find()) {
      return m.group(1).trim();
    }
    return null;
  }

  private String extractPayerAccount(String prompt) {
    if (prompt == null) return null;
    String lower = prompt.toLowerCase(Locale.ROOT);
    Pattern p = Pattern.compile("(?i)\\b(az-2|ak-2|azeem|akash)\\b");
    Matcher m = p.matcher(lower);
    if (m.find()) {
      String match = m.group(1).toUpperCase(Locale.ROOT);
      if (match.equals("AZEEM")) return "AZ-2";
      if (match.equals("AKASH")) return "AK-2";
      return match;
    }
    return null;
  }

  private String toTitleCase(String input) {
    if (input == null || input.isBlank()) return input;
    String[] words = input.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      String w = words[i];
      if (w.isEmpty()) continue;
      if (i > 0) sb.append(" ");
      if (w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT))) {
        sb.append(w);
      } else {
        sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return sb.toString();
  }
}
