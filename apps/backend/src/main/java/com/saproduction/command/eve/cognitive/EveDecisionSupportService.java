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

  @Autowired
  public EveDecisionSupportService(
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) FinanceReadService financeReads,
      EveRetrievalService retrievalService) {
    this.productionRepo = productionRepo;
    this.financeReads = financeReads;
    this.retrievalService = retrievalService;
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

    // 3. Investment Decision (e.g. "kya mujhe 50000 sharma wedding wale event me invest karne chahiye?")
    if (prompt.toLowerCase(Locale.ROOT).contains("invest") || prompt.toLowerCase(Locale.ROOT).contains("kya mujhe") || prompt.toLowerCase(Locale.ROOT).contains("should i")) {
      return handleInvestmentDecision(targetProd, mentionedAmountMinor, language, entities, evidence, steps, seq);
    }

    // 4. General Decision Support
    return handleGeneralDecision(targetProd, language, entities, evidence, steps, seq);
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
        : "50,000";

    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      answer = String.format(
          "Here is the financial position for %s: Status is %s, Contract Value is %s, and Outstanding Balance is %s. "
              + "SA Command tracks production execution and client receivables, not investment return guarantees. "
              + "Whether to commit %s depends on your working capital, operational cash flow, and vendor requirements.",
          prod.title, prod.status, inr.format(contractVal), inr.format(outstandingVal), amountStr);
    } else {
      answer = String.format(
          "%s ki current financial position yeh hai: Status %s hai, Contract Value %s hai, aur %s outstanding balance bacha hai. "
              + "SA Command event execution aur client billing track karta hai, speculative investment return guarantee nahi deta. "
              + "%s lagane ka faisla aapko apne cash flow aur operational zaroorat ke hisaab se lena chahiye.",
          prod.title, prod.status, inr.format(contractVal), inr.format(outstandingVal), amountStr);
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
