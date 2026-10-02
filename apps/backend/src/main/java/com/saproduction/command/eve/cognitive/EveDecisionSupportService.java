package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.EveDtos;
import com.saproduction.command.eve.EveRetrievalService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Governed Decision Support Service for EVE Cognitive Runtime 2.0 (Section 11).
 *
 * Invariants:
 * 1. Identifies the decision and relevant entities.
 * 2. Retrieves authoritative evidence from PostgreSQL.
 * 3. Clearly states which metrics are authoritatively available vs uncertain/missing.
 * 4. NEVER invents facts (e.g. ROI, profit margins, cancellation guarantees).
 * 5. NEVER automatically creates an EvePlan or mutation.
 * 6. Empowers the human operator with grounded evidence to make the decision.
 */
@Service
public class EveDecisionSupportService {

  private static final Logger log = LoggerFactory.getLogger(EveDecisionSupportService.class);

  private final ProductionRepository productionRepo;
  private final FinanceReadService financeReads;
  private final EveRetrievalService retrievalService;
  private final com.saproduction.command.production.ProductionMemberRepository memberRepo;
  private final com.saproduction.command.work.WorkTaskRepository taskRepo;

  @Autowired
  public EveDecisionSupportService(
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) FinanceReadService financeReads,
      EveRetrievalService retrievalService,
      @Autowired(required = false) com.saproduction.command.production.ProductionMemberRepository memberRepo,
      @Autowired(required = false) com.saproduction.command.work.WorkTaskRepository taskRepo) {
    this.productionRepo = productionRepo;
    this.financeReads = financeReads;
    this.retrievalService = retrievalService;
    this.memberRepo = memberRepo;
    this.taskRepo = taskRepo;
  }

  public EveDecisionSupportService(
      ProductionRepository productionRepo,
      FinanceReadService financeReads,
      EveRetrievalService retrievalService) {
    this(productionRepo, financeReads, retrievalService, null, null);
  }

  public record DecisionSupportResult(
      String answer,
      EveOutcome outcome,
      List<EveDtos.EntityReference> referencedEntities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveReasoningStep> reasoningSteps) {}

  public DecisionSupportResult evaluate(
      String prompt,
      String entityPhrase,
      String requestedMetric,
      Long mentionedAmountMinor,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {

    int seq = startSeq;
    List<EveDtos.EntityReference> entities = new ArrayList<>();
    List<EveDtos.EvidenceItem> evidence = new ArrayList<>();

    steps.add(EveReasoningStep.of(
        seq++,
        "DECISION_ANALYSIS",
        "Analyzing operational context and required authoritative evidence for decision support."));

    // 1. Resolve Target Production if mentioned
    Production targetProd = null;
    if (entityPhrase != null && !entityPhrase.isBlank() && retrievalService != null) {
      var res = retrievalService.resolveProduction(entityPhrase);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        var cand = res.resolved();
        if (productionRepo != null) {
          targetProd = productionRepo.findById(cand.id()).orElse(null);
        }
        entities.add(new EveDtos.EntityReference(cand.id(), "PRODUCTION", cand.displayName(), cand.code()));
      }
    }

    if (targetProd == null && productionRepo != null) {
      // Fallback: check if prompt contains any production title
      for (var p : productionRepo.findAll()) {
        if (p.title != null && prompt.toLowerCase(Locale.ROOT).contains(p.title.toLowerCase(Locale.ROOT))) {
          targetProd = p;
          entities.add(new EveDtos.EntityReference(p.id, "PRODUCTION", p.title, "PROD"));
          break;
        }
      }
    }

    // 2. Metric: Profit calculation / inquiry (e.g. "MIPS event ka profit kitna hoga?")
    if ("PROFIT".equalsIgnoreCase(requestedMetric) || prompt.toLowerCase(Locale.ROOT).contains("profit") || prompt.toLowerCase(Locale.ROOT).contains("margin")) {
      return handleProfitInquiry(targetProd, language, entities, evidence, steps, seq);
    }

    // 3. Crew / Staffing Allocation Decision (e.g. "kya mujhe sharma wedding me 4 logo ko bhejna chahiye?", "should I assign 5 crew members?")
    if ("CREW_ALLOCATION".equalsIgnoreCase(requestedMetric)) {
      Integer requestedCount = extractRequestedCount(prompt);
      return handleCrewAllocationDecision(targetProd, requestedCount, language, entities, evidence, steps, seq);
    }

    // 4. Financial Investment Decision (ONLY when investment/capital is explicitly mentioned)
    if ("INVESTMENT".equalsIgnoreCase(requestedMetric) || prompt.toLowerCase(Locale.ROOT).contains("invest") || prompt.toLowerCase(Locale.ROOT).contains("capital")) {
      return handleInvestmentDecision(targetProd, mentionedAmountMinor, language, entities, evidence, steps, seq);
    }

    // 5. General Decision Support
    return handleGeneralDecision(targetProd, language, entities, evidence, steps, seq);
  }

  private Integer extractRequestedCount(String prompt) {
    if (prompt == null) return null;
    var p = java.util.regex.Pattern.compile("(?i)\\b(\\d+)\\s*(?:logo|log|people|person|members?|crew|staff|bande)\\b");
    var m = p.matcher(prompt);
    if (m.find()) {
      try {
        return Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignored) {}
    }
    return null;
  }

  private DecisionSupportResult handleCrewAllocationDecision(
      Production prod,
      Integer requestedCount,
      EveLanguageDetector.UserLanguage language,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveReasoningStep> steps,
      int seq) {

    if (prod == null) {
      String msg = language == EveLanguageDetector.UserLanguage.ENGLISH
          ? "I couldn't identify the production for this crew decision. Please specify the event name."
          : "Main event ka naam confirm nahi kar paayi. Kripya event ka naam batayein.";
      return new DecisionSupportResult(msg, EveOutcome.CLARIFICATION_REQUIRED, entities, evidence, steps);
    }

    int currentCrewCount = 0;
    if (memberRepo != null) {
      try {
        currentCrewCount = memberRepo.findAllByProductionIdOrderByCreatedAt(prod.id).size();
      } catch (Exception ignored) {}
    }

    long openTasks = 0;
    if (taskRepo != null) {
      try {
        openTasks = taskRepo.countByProductionIdAndStatusNotIn(prod.id, List.of(com.saproduction.command.work.WorkTask.Status.DONE, com.saproduction.command.work.WorkTask.Status.CANCELLED));
      } catch (Exception ignored) {}
    }

    steps.add(EveReasoningStep.tool(
        seq++,
        "RETRIEVAL",
        String.format("Retrieved assigned crew (%d members) and open tasks (%d tasks) for production: %s",
            currentCrewCount, openTasks, prod.title),
        "ProductionMemberRepository / WorkTaskRepository"));

    evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Status", String.valueOf(prod.status)));
    if (prod.eventDate != null) {
      evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Event Date", prod.eventDate.toString()));
    }
    evidence.add(new EveDtos.EvidenceItem("CREW", "Current Assigned Members", String.valueOf(currentCrewCount)));
    evidence.add(new EveDtos.EvidenceItem("WORK_TASK", "Pending Tasks", String.valueOf(openTasks)));
    evidence.add(new EveDtos.EvidenceItem("SYSTEM", "Staffing Requirement Quota", "Not authoritatively defined in SA Command"));

    steps.add(EveReasoningStep.of(
        seq++,
        "EVIDENCE_EVALUATION",
        String.format("Authoritative data shows %d assigned crew member(s) and %d open task(s). Fixed staffing requirement quota is unrecorded.",
            currentCrewCount, openTasks)));

    String dateStr = prod.eventDate != null ? prod.eventDate.toString() : "scheduled date";
    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      if (requestedCount != null) {
        answer = String.format(
            "For %s (Date: %s, Status: %s), there are currently %d crew member(s) assigned and %d open task(s). "
                + "SA Command does not maintain an authoritative staffing requirement or quota for this production, "
                + "so I cannot determine with certainty whether sending %d people is the right number. "
                + "That operational decision depends on your on-site scope and role requirements.",
            prod.title, dateStr, prod.status, currentCrewCount, openTasks, requestedCount);
      } else {
        answer = String.format(
            "For %s (Date: %s, Status: %s), there are currently %d crew member(s) assigned and %d open task(s). "
                + "SA Command does not maintain an authoritative staffing requirement or quota for this production, "
                + "so that operational staffing decision depends on your on-site scope and role requirements.",
            prod.title, dateStr, prod.status, currentCrewCount, openTasks);
      }
    } else {
      if (requestedCount != null) {
        answer = String.format(
            "%s ke liye (Date: %s, Status: %s), abhi %d crew member(s) assigned hain aur %d open task(s) hain. "
                + "SA Command me is production ke liye koi authoritative staffing quota ya fixed requirement defined nahi hai, "
                + "isliye main nischit roop se nahi keh sakti ki %d log bhejna sahi rahega ya nahi. "
                + "Yeh operational faisla on-site kaam aur role requirements ke hisaab se lena hoga.",
            prod.title, dateStr, prod.status, currentCrewCount, openTasks, requestedCount);
      } else {
        answer = String.format(
            "%s ke liye (Date: %s, Status: %s), abhi %d crew member(s) assigned hain aur %d open task(s) hain. "
                + "SA Command me is production ke liye koi fixed staffing quota defined nahi hai, "
                + "isliye yeh staffing faisla on-site kaam aur requirements ke hisaab se lena hoga.",
            prod.title, dateStr, prod.status, currentCrewCount, openTasks);
      }
    }

    return new DecisionSupportResult(answer, EveOutcome.COMPLETED, entities, evidence, steps);
  }

  private DecisionSupportResult handleProfitInquiry(
      Production prod,
      EveLanguageDetector.UserLanguage language,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveReasoningStep> steps,
      int seq) {

    if (prod == null) {
      String msg = language == EveLanguageDetector.UserLanguage.ENGLISH
          ? "I couldn't identify which production you're asking about. Please specify the event name."
          : "Main samajh nahi paayi aap kis event ke baare mein pooch rahe hain. Kripya event ka naam batayein.";
      return new DecisionSupportResult(msg, EveOutcome.CLARIFICATION_REQUIRED, entities, evidence, steps);
    }

    steps.add(EveReasoningStep.tool(
        seq++,
        "RETRIEVAL",
        "Retrieved financial ledger records for production: " + prod.title,
        "FinanceReadService"));

    BigDecimal contractVal = BigDecimal.ZERO;
    BigDecimal advanceVal = BigDecimal.ZERO;
    BigDecimal outstandingVal = BigDecimal.ZERO;

    if (financeReads != null) {
      try {
        var fin = financeReads.production(prod.id);
        if (fin != null) {
          contractVal = fin.get("contracted") instanceof BigDecimal b ? b : BigDecimal.ZERO;
          advanceVal = fin.get("received") instanceof BigDecimal b ? b : BigDecimal.ZERO;
          outstandingVal = fin.get("outstanding") instanceof BigDecimal b ? b : BigDecimal.ZERO;
        }
      } catch (Exception ignored) {}
    }

    NumberFormat inr = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    evidence.add(new EveDtos.EvidenceItem("FINANCE", "Contract Value", inr.format(contractVal)));
    evidence.add(new EveDtos.EvidenceItem("FINANCE", "Advance Received", inr.format(advanceVal)));
    evidence.add(new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", inr.format(outstandingVal)));
    evidence.add(new EveDtos.EvidenceItem("SYSTEM", "Profit Metric Status", "Not authoritatively forecasted"));

    steps.add(EveReasoningStep.of(
        seq++,
        "EVIDENCE_EVALUATION",
        String.format("Authoritative revenue recorded: %s (Contract) with %s advance. Profit margin forecast is unrecorded.",
            inr.format(contractVal), inr.format(advanceVal))));

    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      answer = String.format(
          "For %s, the contracted billing value is %s with %s advance received and %s outstanding balance. "
              + "However, SA Command tracks client billing and vendor disbursements, so an authoritative profit margin forecast is not recorded for this event.",
          prod.title, inr.format(contractVal), inr.format(advanceVal), inr.format(outstandingVal));
    } else {
      answer = String.format(
          "%s ke liye contracted value %s hai, jisme se %s advance mila hai aur %s outstanding balance hai. "
              + "Lekin SA Command me live client payments aur expenses track hote hain, is event ka koi speculative profit forecast recorded nahi hai.",
          prod.title, inr.format(contractVal), inr.format(advanceVal), inr.format(outstandingVal));
    }

    return new DecisionSupportResult(answer, EveOutcome.INSUFFICIENT_EVIDENCE, entities, evidence, steps);
  }

  private DecisionSupportResult handleInvestmentDecision(
      Production prod,
      Long mentionedAmountMinor,
      EveLanguageDetector.UserLanguage language,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveReasoningStep> steps,
      int seq) {

    if (prod == null) {
      String msg = language == EveLanguageDetector.UserLanguage.ENGLISH
          ? "I couldn't identify the production for this decision. Please specify the event name."
          : "Main event ka naam confirm nahi kar paayi. Kripya event ka naam batayein.";
      return new DecisionSupportResult(msg, EveOutcome.CLARIFICATION_REQUIRED, entities, evidence, steps);
    }

    steps.add(EveReasoningStep.tool(
        seq++,
        "RETRIEVAL",
        "Retrieved production status and financial position for: " + prod.title,
        "ProductionService / FinanceReadService"));

    BigDecimal contractVal = BigDecimal.ZERO;
    BigDecimal advanceVal = BigDecimal.ZERO;
    BigDecimal outstandingVal = BigDecimal.ZERO;

    if (financeReads != null) {
      try {
        var fin = financeReads.production(prod.id);
        if (fin != null) {
          contractVal = fin.get("contracted") instanceof BigDecimal b ? b : BigDecimal.ZERO;
          advanceVal = fin.get("received") instanceof BigDecimal b ? b : BigDecimal.ZERO;
          outstandingVal = fin.get("outstanding") instanceof BigDecimal b ? b : BigDecimal.ZERO;
        }
      } catch (Exception ignored) {}
    }

    NumberFormat inr = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Status", String.valueOf(prod.status)));
    evidence.add(new EveDtos.EvidenceItem("FINANCE", "Contract Value", inr.format(contractVal)));
    evidence.add(new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", inr.format(outstandingVal)));

    steps.add(EveReasoningStep.of(
        seq++,
        "DECISION_SYNTHESIS",
        "Synthesizing financial evidence. Investment involves commercial risk; operator decision required."));

    String amountStr = mentionedAmountMinor != null && mentionedAmountMinor > 0
        ? inr.format(BigDecimal.valueOf(mentionedAmountMinor).divide(BigDecimal.valueOf(100)))
        : null;

    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      String commitNotice = amountStr != null
          ? "Whether to commit " + amountStr
          : "Whether to commit financial capital";
      answer = String.format(
          "Here is the financial position for %s: Status is %s, Contract Value is %s, and Outstanding Balance is %s. "
              + "SA Command tracks production execution and client receivables, not investment return guarantees. "
              + "%s depends on your working capital, operational cash flow, and vendor requirements.",
          prod.title, prod.status, inr.format(contractVal), inr.format(outstandingVal), commitNotice);
    } else {
      String commitNotice = amountStr != null
          ? amountStr + " lagane ka faisla"
          : "Capital lagane ka faisla";
      answer = String.format(
          "%s ki current financial position yeh hai: Status %s hai, Contract Value %s hai, aur %s outstanding balance bacha hai. "
              + "SA Command event execution aur client billing track karta hai, speculative investment return guarantee nahi deta. "
              + "%s aapko apne cash flow aur operational zaroorat ke hisaab se lena chahiye.",
          prod.title, prod.status, inr.format(contractVal), inr.format(outstandingVal), commitNotice);
    }

    return new DecisionSupportResult(answer, EveOutcome.COMPLETED, entities, evidence, steps);
  }

  private DecisionSupportResult handleGeneralDecision(
      Production prod,
      EveLanguageDetector.UserLanguage language,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveReasoningStep> steps,
      int seq) {

    String answer = language == EveLanguageDetector.UserLanguage.ENGLISH
        ? "I can provide verified financial numbers, crew status, and pending tasks to help you decide, but I cannot make operational or financial commitments on your behalf."
        : "Main aapko verified financial figures, crew status, aur pending tasks provide kar sakti hoon taaki aap sahi decision le sakein, par decision final aapko hi lena hoga.";

    return new DecisionSupportResult(answer, EveOutcome.COMPLETED, entities, evidence, steps);
  }
}
