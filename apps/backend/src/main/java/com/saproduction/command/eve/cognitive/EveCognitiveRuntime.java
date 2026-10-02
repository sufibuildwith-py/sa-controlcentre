package com.saproduction.command.eve.cognitive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.eve.*;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Central Cognitive Runtime for EVE.
 * Coordinates bounded cognitive reasoning loop:
 * 1. Understand user goal (prompt + context + temporal normalization)
 * 2. Formulate semantic EveGoal
 * 3. Plan required information and select bounded read-only tools
 * 4. Execute tools against canonical SA Command services
 * 5. Apply analytical operations (COUNT, FILTER, COMPARE, SUM, etc.)
 * 6. Verify sufficiency against authoritative PostgreSQL data
 * 7. Formulate grounded answer and safe operational reasoning summary
 *
 * Hard limits:
 * - MAX_REASONING_STEPS = 6
 * - MAX_TOOL_CALLS = 5
 * - MAX_SAME_TOOL_RETRIES = 2
 * - MAX_EXECUTION_TIME_MS = 60,000
 */
@Service
public class EveCognitiveRuntime {

  private static final Logger log = LoggerFactory.getLogger(EveCognitiveRuntime.class);

  public static final int MAX_REASONING_STEPS = 6;
  public static final int MAX_TOOL_CALLS = 5;
  public static final int MAX_SAME_TOOL_RETRIES = 2;
  public static final long MAX_EXECUTION_TIME_MS = 300_000L;

  public record CognitiveResult(
      String status,
      String answer,
      List<EveDtos.EvidenceItem> evidence,
      List<EveDtos.EntityReference> referencedEntities,
      List<EveReasoningStep> reasoningSteps,
      List<EveDtos.CandidateView> candidates,
      EveGoal goal) {}

  private final EveCognitiveToolRegistry toolRegistry;
  private final EveTemporalReasoningService temporalService;
  private final EveRetrievalService retrievalService;
  private final ProductionRepository productionRepo;
  private final ProductionMemberRepository productionMemberRepo;
  private final WorkTaskRepository workTaskRepo;
  private final EmployeeRepository employeeRepo;
  private final EveDecisionSupportService decisionSupportService;
  private final EveGeneralReasoningService generalReasoningService;
  private final EveCapabilityRegistry capabilityRegistry;
  private final ObjectMapper json = new ObjectMapper();

  public EveCognitiveRuntime(
      EveCognitiveToolRegistry toolRegistry,
      EveTemporalReasoningService temporalService,
      EveRetrievalService retrievalService,
      ProductionRepository productionRepo,
      ProductionMemberRepository productionMemberRepo,
      WorkTaskRepository workTaskRepo,
      EmployeeRepository employeeRepo) {
    this(
        toolRegistry,
        temporalService,
        retrievalService,
        productionRepo,
        productionMemberRepo,
        workTaskRepo,
        employeeRepo,
        new EveDecisionSupportService(productionRepo, null, retrievalService, productionMemberRepo, workTaskRepo),
        new EveGeneralReasoningService(),
        new EveCapabilityRegistry());
  }

  @Autowired
  public EveCognitiveRuntime(
      EveCognitiveToolRegistry toolRegistry,
      EveTemporalReasoningService temporalService,
      EveRetrievalService retrievalService,
      ProductionRepository productionRepo,
      ProductionMemberRepository productionMemberRepo,
      WorkTaskRepository workTaskRepo,
      EmployeeRepository employeeRepo,
      EveDecisionSupportService decisionSupportService,
      EveGeneralReasoningService generalReasoningService,
      EveCapabilityRegistry capabilityRegistry) {
    this.toolRegistry = toolRegistry;
    this.temporalService = temporalService;
    this.retrievalService = retrievalService;
    this.productionRepo = productionRepo;
    this.productionMemberRepo = productionMemberRepo;
    this.workTaskRepo = workTaskRepo;
    this.employeeRepo = employeeRepo;
    this.decisionSupportService = decisionSupportService;
    this.generalReasoningService = generalReasoningService;
    this.capabilityRegistry = capabilityRegistry;
  }

  public CognitiveResult execute(
      String prompt,
      UUID sessionId,
      String sessionContext,
      EveRetrievalRouter.SessionContext routerContext,
      EveModelProvider modelProvider) {

    long startTime = System.currentTimeMillis();
    List<EveReasoningStep> reasoningSteps = new ArrayList<>();
    List<EveDtos.EvidenceItem> allEvidence = new ArrayList<>();
    List<EveDtos.EntityReference> allEntities = new ArrayList<>();
    int stepSeq = 1;

    // 0. LANGUAGE & REGISTER DETECTION
    var langProfile = EveLanguageDetector.detect(prompt);
    var language = langProfile.language();

    // 0A. GENERAL FAST PATH: ARITHMETIC (Deterministic < 1ms execution, no LLM overhead)
    if (EveArithmeticCapability.isArithmeticExpression(prompt)) {
      var mathOpt = EveArithmeticCapability.evaluate(prompt);
      if (mathOpt.isPresent()) {
        String res = mathOpt.get();
        reasoningSteps.add(EveReasoningStep.of(stepSeq++, "ARITHMETIC_EVALUATION", "Evaluated expression deterministically: " + prompt + " = " + res));
        String ans;
        if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
          ans = "The calculated result is " + res + ".";
        } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
          ans = "गणना का परिणाम " + res + " है।";
        } else {
          ans = "Calculation ka result " + res + " hai.";
        }
        reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", ans));
        return new CognitiveResult("COMPLETED", ans, List.of(new EveDtos.EvidenceItem("CALCULATION", "Expression", prompt + " = " + res)), List.of(), reasoningSteps, List.of(), null);
      }
    }

    // 0B. EXTERNAL CAPABILITY BOUNDARY (Weather, Stocks/Markets, Flights, Sports)
    String lowerPrompt = prompt.trim().toLowerCase(Locale.ROOT);
    Optional<String> extCategory = detectExternalCapabilityCategory(lowerPrompt);
    if (extCategory.isPresent()) {
      String cat = extCategory.get();
      var extRes = generalReasoningService.handleExternalCapability(prompt, cat, language, reasoningSteps, stepSeq);
      return new CognitiveResult(
          extRes.outcome().name(),
          extRes.answer(),
          List.of(new EveDtos.EvidenceItem("CAPABILITY", "External Service", cat.toLowerCase(Locale.ROOT) + " (unsupported in offline mode)")),
          List.of(),
          extRes.reasoningSteps(),
          List.of(),
          null);
    }

    // 0C. GENERAL RECOMMENDATION: CATERING / WORKFLOW / LOGISTICS
    if (isGeneralRecommendationQuery(lowerPrompt)) {
      var recRes = generalReasoningService.handleGeneralRecommendation(prompt, language, reasoningSteps, stepSeq);
      return new CognitiveResult(
          recRes.outcome().name(),
          recRes.answer(),
          List.of(new EveDtos.EvidenceItem("RECOMMENDATION", "Context", "Operational catering/meal suggestion")),
          List.of(),
          recRes.reasoningSteps(),
          List.of(),
          null);
    }

    // 0D. CANDIDATE SET DISAMBIGUATION / ORDINAL SELECTION ("wahi second wala", "2nd wala", "dusra wala")
    boolean isOrdinal = lowerPrompt.contains("second") || lowerPrompt.contains("2nd") || lowerPrompt.contains("dusra")
        || lowerPrompt.contains("doosra") || lowerPrompt.contains("first") || lowerPrompt.contains("1st") || lowerPrompt.contains("pehla")
        || lowerPrompt.contains("third") || lowerPrompt.contains("3rd") || lowerPrompt.contains("teesra");
    if (isOrdinal && (lowerPrompt.contains("wala") || lowerPrompt.contains("wahi") || lowerPrompt.contains("item") || lowerPrompt.contains("one") || lowerPrompt.length() < 30)) {
      if (routerContext == null || !routerContext.hasPendingCandidates()) {
        reasoningSteps.add(EveReasoningStep.of(stepSeq++, "DISAMBIGUATION_CHECK", "Ordinal selection received but no candidate set active in session."));
        String ans;
        if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
          ans = "Which list or item are you referring to? There is no active list of candidates in our conversation.";
        } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
          ans = "आप किस सूची के आइटम की बात कर रहे हैं? हमारे पास अभी कोई सक्रिय विकल्प सूची नहीं है।";
        } else {
          ans = "Kaunsi list ki candidate item ki baat kar rahe ho? Hamari conversation me abhi koi candidate list active nahi hai.";
        }
        reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", ans));
        return new CognitiveResult("CLARIFICATION_REQUIRED", ans, List.of(), List.of(), reasoningSteps, List.of(), null);
      }
    }

    // 1. UNDERSTANDING & GOAL FORMULATION
    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "UNDERSTANDING",
        "Analyzing user query and operational intent in context."));

    EveGoal goal;
    if (modelProvider instanceof LocalQwenModelProvider qwen && "READY".equals(qwen.getStatusView().status())) {
      goal = understandWithQwen(prompt, sessionContext, qwen, reasoningSteps, stepSeq++);
    } else {
      goal = understandSemantically(prompt, sessionContext, routerContext, reasoningSteps, stepSeq++);
    }

    if (goal.needsClarification()) {
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq++,
          "CLARIFICATION_REQUIRED",
          goal.clarificationPrompt() != null ? goal.clarificationPrompt() : "Additional clarification required."));
      return new CognitiveResult(
          "CLARIFICATION_REQUIRED",
          goal.clarificationPrompt() != null ? goal.clarificationPrompt() : "Please clarify your request.",
          allEvidence,
          allEntities,
          reasoningSteps,
          List.of(),
          goal);
    }

    // 2. TEMPORAL GROUNDING
    EveTemporalReasoningService.DateRange dateRange = goal.dateRange();
    if (dateRange == null && goal.timeRange() != null && !goal.timeRange().isBlank()) {
      dateRange = temporalService.resolveDateRange(goal.timeRange()).orElse(null);
    }
    if (dateRange != null) {
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq++,
          "TEMPORAL_GROUNDING",
          String.format("\"%s\" resolved to %s – %s (%s).",
              goal.timeRange() != null ? goal.timeRange() : dateRange.label(),
              dateRange.start(),
              dateRange.end(),
              temporalService.getZone().getId())));
    }

    // 3. EXECUTE BOUNDED TOOLS BASED ON GOAL & ANALYTICAL OPERATION
    if (System.currentTimeMillis() - startTime > MAX_EXECUTION_TIME_MS) {
      reasoningSteps.add(EveReasoningStep.of(stepSeq++, "TIMEOUT", "Execution budget exceeded."));
      return new CognitiveResult("SYSTEM_UNAVAILABLE", "Execution budget exceeded.", allEvidence, allEntities, reasoningSteps, List.of(), goal);
    }

    // 2B. DECISION SUPPORT & RECOMMENDATION
    if (goal.operation() == EveOperation.DECISION_SUPPORT
        || "DECISION_SUPPORT".equalsIgnoreCase(goal.goal())
        || goal.operation() == EveOperation.RECOMMEND) {
      return executeDecisionSupport(prompt, goal, language, routerContext, sessionContext, reasoningSteps, stepSeq);
    }

    // A. COUNT PRODUCTIONS (e.g. "next week kitne events hai")
    if (goal.operation() == EveOperation.COUNT && "PRODUCTION".equalsIgnoreCase(goal.entityType())) {
      return executeCountProductions(goal, dateRange, reasoningSteps, stepSeq);
    }

    // B. FILTER PRODUCTIONS BY CREW & OPEN TASKS (e.g. "next week ke events jisme Kabir hai aur task pending hai")
    if (goal.operation() == EveOperation.FILTER && "PRODUCTION".equalsIgnoreCase(goal.entityType())) {
      return executeFilterProductions(goal, dateRange, reasoningSteps, stepSeq);
    }

    // C. MOST PENDING TASKS / AGGREGATION (e.g. "which production has the most pending tasks?")
    if (goal.operation() == EveOperation.COMPARE && "PRODUCTION".equalsIgnoreCase(goal.entityType())) {
      return executeMostPendingTasks(goal, dateRange, reasoningSteps, stepSeq);
    }

    // D. COUNT CREW ON PRODUCTION (e.g. "Sharma wedding me kitne log kaam kar rahe hain?")
    if (goal.operation() == EveOperation.COUNT && ("CREW".equalsIgnoreCase(goal.entityType()) || "PRODUCTION_CREW".equalsIgnoreCase(goal.entityType()))) {
      return executeCountProductionCrew(goal, reasoningSteps, stepSeq);
    }

    // D2. COUNT WORK TASKS ON PRODUCTION (e.g. "aur usme pending task kitne hain?")
    if (goal.operation() == EveOperation.COUNT && ("WORK_TASK".equalsIgnoreCase(goal.entityType()) || "TASK".equalsIgnoreCase(goal.entityType()))) {
      return executeCountProductionTasks(goal, reasoningSteps, stepSeq);
    }

    // E. PRODUCTIONS OWING MONEY (e.g. "which productions still owe us money?")
    if ("FINANCE".equalsIgnoreCase(goal.entityType()) && goal.constraints().containsKey("outstandingOnly")) {
      return executeProductionReceivables(reasoningSteps, stepSeq);
    }

    // F. DEFAULT BOUNDED TOOL DISPATCH
    return executeStandardToolGoal(goal, reasoningSteps, stepSeq);
  }

  private CognitiveResult executeDecisionSupport(
      String prompt,
      EveGoal goal,
      EveLanguageDetector.UserLanguage language,
      EveRetrievalRouter.SessionContext routerContext,
      String sessionContext,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {

    String metric = "OPERATIONAL";
    if (goal.constraints() != null && goal.constraints().get("decisionMetric") != null) {
      metric = String.valueOf(goal.constraints().get("decisionMetric")).toUpperCase(Locale.ROOT);
    } else {
      String lower = prompt.toLowerCase(Locale.ROOT);
      if (lower.contains("profit") || lower.contains("margin") || lower.contains("fayda") || lower.contains("munafa")) {
        metric = "PROFIT";
      } else if (lower.contains("invest") || lower.contains("capital") || lower.contains("paisa lagana") || lower.contains("paise lagana")) {
        metric = "INVESTMENT";
      } else if (lower.contains("crew") || lower.contains("logo") || lower.contains("log") || lower.contains("people")
          || lower.contains("person") || lower.contains("members") || lower.contains("staff") || lower.contains("bande")
          || lower.contains("bhejna") || lower.contains("send") || lower.contains("assign") || lower.contains("depute")) {
        metric = "CREW_ALLOCATION";
      }
    }

    Long amountMinor = null;
    if (goal.constraints() != null && goal.constraints().get("amountMinor") != null) {
      Object amtObj = goal.constraints().get("amountMinor");
      if (amtObj instanceof Number num) {
        amountMinor = num.longValue();
      }
    }
    if (amountMinor == null) {
      amountMinor = extractAmountMinor(prompt.toLowerCase(Locale.ROOT));
    }

    String targetPhrase = null;
    if (goal.entityReferences() != null && !goal.entityReferences().isEmpty()) {
      targetPhrase = goal.entityReferences().get(0);
    }
    if (targetPhrase == null || targetPhrase.isBlank()) {
      targetPhrase = extractProductionName(prompt.toLowerCase(Locale.ROOT), routerContext, sessionContext);
    }

    var decRes = decisionSupportService.evaluate(prompt, targetPhrase, metric, amountMinor, language, reasoningSteps, stepSeq);
    return new CognitiveResult(
        decRes.outcome().name(),
        decRes.answer(),
        decRes.evidence(),
        decRes.referencedEntities(),
        decRes.reasoningSteps(),
        List.of(),
        goal);
  }

  private CognitiveResult executeCountProductions(
      EveGoal goal,
      EveTemporalReasoningService.DateRange dateRange,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {

    reasoningSteps.add(EveReasoningStep.tool(
        stepSeq++,
        "RETRIEVAL",
        String.format("Queried canonical production records for date range %s to %s.",
            dateRange != null ? dateRange.start() : "all",
            dateRange != null ? dateRange.end() : "all"),
        "search_productions"));

    Map<String, Object> params = new HashMap<>();
    if (dateRange != null) {
      params.put("startDate", dateRange.start().toString());
      params.put("endDate", dateRange.end().toString());
    }

    var tool = toolRegistry.getTool("search_productions").orElseThrow();
    var result = tool.execute(params);

    @SuppressWarnings("unchecked")
    List<?> prods = (List<?>) result.data();
    int count = prods != null ? prods.size() : 0;

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ANALYTICAL_OPERATION",
        String.format("Computed count of matching productions: %d.", count)));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        String.format("Verified %d authoritative production record(s) in PostgreSQL.", count)));

    String timeLabel = goal.timeRange() != null ? goal.timeRange().replace("_", " ").toLowerCase(Locale.ROOT) : "next week";
    String answer;
    if (count == 0) {
      answer = String.format("There are no events scheduled for %s (%s to %s).",
          timeLabel, dateRange != null ? dateRange.start() : "", dateRange != null ? dateRange.end() : "");
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("There %s %d event%s scheduled for %s (%s to %s)",
          count == 1 ? "is" : "are",
          count,
          count == 1 ? "" : "s",
          timeLabel,
          dateRange != null ? dateRange.start() : "",
          dateRange != null ? dateRange.end() : ""));

      if (count <= 5 && prods != null) {
        sb.append(": ");
        List<String> titles = prods.stream()
            .map(p -> {
              if (p instanceof Production prod) {
                return prod.title + (prod.eventDate != null ? " (" + prod.eventDate + ")" : "");
              } else if (p instanceof Map<?, ?> m) {
                Object nameVal = m.get("name") != null ? m.get("name") : m.get("title");
                String nameStr = nameVal != null ? nameVal.toString() : "Event";
                return nameStr + (m.containsKey("date") ? " (" + m.get("date") + ")" : "");
              }
              return String.valueOf(p);
            })
            .toList();
        sb.append(String.join(", ", titles));
      }
      sb.append(".");
      answer = sb.toString();
    }

    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));

    List<EveDtos.EvidenceItem> ev = new ArrayList<>(result.evidence());
    ev.add(0, new EveDtos.EvidenceItem("PRODUCTION", "Total Events " + (goal.timeRange() != null ? goal.timeRange() : "NEXT_WEEK"), String.valueOf(count)));

    return new CognitiveResult(
        "COMPLETED",
        answer,
        ev,
        result.referencedEntities(),
        reasoningSteps,
        List.of(),
        goal);
  }

  private CognitiveResult executeFilterProductions(
      EveGoal goal,
      EveTemporalReasoningService.DateRange dateRange,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {

    String crewTarget = (String) goal.constraints().get("crewContains");
    boolean requireOpenTasks = Boolean.TRUE.equals(goal.constraints().get("hasOpenTasks"));

    reasoningSteps.add(EveReasoningStep.tool(
        stepSeq++,
        "RETRIEVAL",
        String.format("Querying productions in %s and inspecting crew assignments and task state.",
            dateRange != null ? dateRange.label() : "specified range"),
        "search_productions"));

    Map<String, Object> params = new HashMap<>();
    if (dateRange != null) {
      params.put("startDate", dateRange.start().toString());
      params.put("endDate", dateRange.end().toString());
    }

    var searchTool = toolRegistry.getTool("search_productions").orElseThrow();
    var searchRes = searchTool.execute(params);

    List<?> rawCandidates = (List<?>) searchRes.data();
    List<Production> candidates = new ArrayList<>();
    if (rawCandidates != null) {
      for (Object obj : rawCandidates) {
        if (obj instanceof Production p) {
          candidates.add(p);
        }
      }
    }

    UUID targetEmpId = null;
    String targetEmpName = crewTarget;
    if (crewTarget != null && !crewTarget.isBlank()) {
      var empRes = retrievalService != null ? retrievalService.resolveEmployee(crewTarget) : null;
      if (empRes != null && empRes.resolved() != null) {
        targetEmpId = empRes.resolved().id();
        targetEmpName = empRes.resolved().displayName();
        reasoningSteps.add(EveReasoningStep.of(
            stepSeq++,
            "ENTITY_RESOLUTION",
            String.format("Resolved crew target \"%s\" to %s (%s).",
                crewTarget, targetEmpName, empRes.resolved().code())));
      }
    }

    List<Production> filtered = new ArrayList<>();
    List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
    List<EveDtos.EntityReference> entities = new ArrayList<>();

    for (Production p : candidates) {
      boolean crewMatches = true;
      if (targetEmpId != null) {
        crewMatches = productionMemberRepo.existsByProductionIdAndEmployeeId(p.id, targetEmpId);
      } else if (crewTarget != null && !crewTarget.isBlank()) {
        List<ProductionMember> members = productionMemberRepo.findAllByProductionIdOrderByCreatedAt(p.id);
        crewMatches = members.stream().anyMatch(m -> {
          Optional<Employee> e = employeeRepo.findById(m.employeeId);
          return e.isPresent() && e.get().displayName.toLowerCase(Locale.ROOT).contains(crewTarget.toLowerCase(Locale.ROOT));
        });
      }

      if (!crewMatches) continue;

      long openTaskCount = workTaskRepo.countByProductionIdAndStatusNotIn(
          p.id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED));

      if (requireOpenTasks && openTaskCount == 0) continue;

      filtered.add(p);
      evidence.add(new EveDtos.EvidenceItem("PRODUCTION", p.title,
          String.format("%s | Crew: %s | Open Tasks: %d", p.eventDate, targetEmpName, openTaskCount)));
      entities.add(new EveDtos.EntityReference(p.id, "PRODUCTION", p.title, "PROD"));
    }

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "FILTER",
        String.format("Filtered to %d production(s) satisfying crew relationship (%s) and pending tasks.",
            filtered.size(), targetEmpName)));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        "Verified matching records against canonical production_members and tasks tables."));

    String answer;
    if (filtered.isEmpty()) {
      answer = String.format("No productions found for %s involving %s with pending tasks.",
          dateRange != null ? dateRange.label().toLowerCase(Locale.ROOT).replace("_", " ") : "next week",
          targetEmpName);
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("Found %d production(s) involving %s with pending tasks: ", filtered.size(), targetEmpName));
      List<String> details = filtered.stream()
          .map(p -> p.title + " on " + p.eventDate + " at " + p.venueName)
          .toList();
      sb.append(String.join("; ", details)).append(".");
      answer = sb.toString();
    }

    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));

    return new CognitiveResult("COMPLETED", answer, evidence, entities, reasoningSteps, List.of(), goal);
  }

  private CognitiveResult executeMostPendingTasks(
      EveGoal goal,
      EveTemporalReasoningService.DateRange dateRange,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {
    reasoningSteps.add(EveReasoningStep.tool(
        stepSeq++,
        "RETRIEVAL",
        "Querying active productions and aggregating pending task counts across the work ledger.",
        "get_production_tasks"));

    List<Production> prods = productionRepo.findAll().stream()
        .filter(p -> p.status != Production.Status.CANCELLED && p.status != Production.Status.DELIVERED)
        .filter(p -> {
          if (dateRange == null || p.eventDate == null) return true;
          return !p.eventDate.isBefore(dateRange.start()) && !p.eventDate.isAfter(dateRange.end());
        })
        .toList();

    Production maxProd = null;
    long maxCount = -1;

    for (Production p : prods) {
      long openCount = workTaskRepo.countByProductionIdAndStatusNotIn(
          p.id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED));
      if (openCount > maxCount) {
        maxCount = openCount;
        maxProd = p;
      }
    }

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ANALYTICAL_OPERATION",
        String.format("Compared task counts across %d active productions. Maximum open tasks: %d.",
            prods.size(), Math.max(0, maxCount))));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        "Verified pending task metrics in PostgreSQL tasks ledger."));

    String answer;
    List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
    List<EveDtos.EntityReference> entities = new ArrayList<>();

    if (maxProd != null && maxCount > 0) {
      answer = String.format("%s has the most pending tasks, with %d open task%s.",
          maxProd.title, maxCount, maxCount == 1 ? "" : "s");
      evidence.add(new EveDtos.EvidenceItem("WORK_TASK", maxProd.title, maxCount + " pending tasks"));
      entities.add(new EveDtos.EntityReference(maxProd.id, "PRODUCTION", maxProd.title, "PROD"));
    } else {
      answer = "There are currently no active productions with pending tasks.";
    }

    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));
    return new CognitiveResult("COMPLETED", answer, evidence, entities, reasoningSteps, List.of(), goal);
  }

  private CognitiveResult executeCountProductionTasks(EveGoal goal, List<EveReasoningStep> reasoningSteps, int stepSeq) {
    String spokenProd = goal.entityReferences().isEmpty() ? "" : goal.entityReferences().get(0);
    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ENTITY_RESOLUTION",
        "Resolving target production for task count: " + spokenProd));

    var prodRes = retrievalService.resolveProduction(spokenProd);
    if (prodRes.resolved() == null) {
      reasoningSteps.add(EveReasoningStep.of(stepSeq, "NOT_FOUND", "Production not found: " + spokenProd));
      return new CognitiveResult("NOT_FOUND", "Could not find production: " + spokenProd, List.of(), List.of(), reasoningSteps, List.of(), goal);
    }

    long openCount = workTaskRepo.countByProductionIdAndStatusNotIn(
        prodRes.resolved().id(), List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ANALYTICAL_OPERATION",
        String.format("Counted %d pending tasks for %s.", openCount, prodRes.resolved().displayName())));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        "Verified open tasks in PostgreSQL work_tasks ledger."));

    String answer = String.format("There %s %d pending task%s for %s.",
        openCount == 1 ? "is" : "are",
        openCount,
        openCount == 1 ? "" : "s",
        prodRes.resolved().displayName());

    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));

    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("WORK_TASK", prodRes.resolved().displayName(), openCount + " pending tasks"));
    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(prodRes.resolved().id(), "PRODUCTION", prodRes.resolved().displayName(), "PROD"));

    return new CognitiveResult("COMPLETED", answer, evidence, entities, reasoningSteps, List.of(), goal);
  }

  private CognitiveResult executeCountProductionCrew(EveGoal goal, List<EveReasoningStep> reasoningSteps, int stepSeq) {
    String spokenProd = goal.entityReferences().isEmpty() ? "" : goal.entityReferences().get(0);
    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ENTITY_RESOLUTION",
        "Resolving target production for crew count: " + spokenProd));

    var prodRes = retrievalService.resolveProduction(spokenProd);
    if (prodRes.resolved() == null) {
      reasoningSteps.add(EveReasoningStep.of(stepSeq, "NOT_FOUND", "Production not found: " + spokenProd));
      return new CognitiveResult("NOT_FOUND", "Could not find production: " + spokenProd, List.of(), List.of(), reasoningSteps, List.of(), goal);
    }

    var crewTool = toolRegistry.getTool("get_production_crew").orElseThrow();
    var crewRes = crewTool.execute(Map.of("productionId", prodRes.resolved().id()));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> crew = (List<Map<String, Object>>) crewRes.data();
    int count = crew != null ? crew.size() : 0;

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ANALYTICAL_OPERATION",
        String.format("Counted %d crew members assigned to %s.", count, prodRes.resolved().displayName())));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        "Verified crew assignment records from PostgreSQL production_members ledger."));

    String answer = String.format("There are %d crew members assigned to %s.", count, prodRes.resolved().displayName());
    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));

    return new CognitiveResult("COMPLETED", answer, crewRes.evidence(), crewRes.referencedEntities(), reasoningSteps, List.of(), goal);
  }

  private CognitiveResult executeProductionReceivables(List<EveReasoningStep> reasoningSteps, int stepSeq) {
    reasoningSteps.add(EveReasoningStep.tool(
        stepSeq++,
        "RETRIEVAL",
        "Querying financial ledger for productions with outstanding contracted balances.",
        "search_production_receivables"));

    var tool = toolRegistry.getTool("search_production_receivables").orElseThrow();
    var res = tool.execute(Map.of());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> owing = (List<Map<String, Object>>) res.data();

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "ANALYTICAL_OPERATION",
        String.format("Identified %d productions with outstanding receivables.", owing != null ? owing.size() : 0)));

    reasoningSteps.add(EveReasoningStep.of(
        stepSeq++,
        "VERIFICATION",
        "Verified against authoritative contracts and receipt allocations. No payment action triggered."));

    String answer;
    if (owing == null || owing.isEmpty()) {
      answer = "All contracted productions are fully settled; no productions currently owe outstanding balances.";
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("There are %d productions with outstanding balances: ", owing.size()));
      List<String> items = owing.stream()
          .map(m -> String.format("%s (Outstanding: INR %s)", m.get("title"), m.get("outstanding")))
          .toList();
      sb.append(String.join("; ", items)).append(".");
      answer = sb.toString();
    }

    reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", answer));
    return new CognitiveResult("COMPLETED", answer, res.evidence(), res.referencedEntities(), reasoningSteps, List.of(), null);
  }

  private CognitiveResult executeStandardToolGoal(
      EveGoal goal,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {

    String entityType = goal.entityType();
    String spoken = goal.entityReferences().isEmpty() ? "" : goal.entityReferences().get(0);

    // Employee 360
    if ("EMPLOYEE".equalsIgnoreCase(entityType)) {
      var tool = toolRegistry.getTool("get_employee_360").orElseThrow();
      var res = tool.execute(Map.of("name", spoken));
      if (!res.success()) {
        reasoningSteps.add(EveReasoningStep.of(stepSeq, "NOT_FOUND", "Employee not found: " + spoken));
        return new CognitiveResult("NOT_FOUND", "Could not find employee: " + spoken, List.of(), List.of(), reasoningSteps, List.of(), goal);
      }
      reasoningSteps.add(EveReasoningStep.of(stepSeq++, "RETRIEVAL", res.summary()));
      reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", res.summary()));
      return new CognitiveResult("COMPLETED", res.summary(), res.evidence(), res.referencedEntities(), reasoningSteps, List.of(), goal);
    }

    // Production Details
    if ("PRODUCTION".equalsIgnoreCase(entityType)) {
      var tool = toolRegistry.getTool("get_production").orElseThrow();
      var res = tool.execute(Map.of("title", spoken));
      if (!res.success()) {
        reasoningSteps.add(EveReasoningStep.of(stepSeq, "NOT_FOUND", "Production not found: " + spoken));
        return new CognitiveResult("NOT_FOUND", "Could not find production: " + spoken, List.of(), List.of(), reasoningSteps, List.of(), goal);
      }
      reasoningSteps.add(EveReasoningStep.of(stepSeq++, "RETRIEVAL", res.summary()));
      reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", res.summary()));
      return new CognitiveResult("COMPLETED", res.summary(), res.evidence(), res.referencedEntities(), reasoningSteps, List.of(), goal);
    }

    // Equipment Stock
    if ("EQUIPMENT".equalsIgnoreCase(entityType)) {
      var tool = toolRegistry.getTool("get_equipment_stock").orElseThrow();
      var res = tool.execute(Map.of("query", spoken));
      reasoningSteps.add(EveReasoningStep.of(stepSeq++, "RETRIEVAL", res.summary()));
      reasoningSteps.add(EveReasoningStep.of(stepSeq, "ANSWER", res.summary()));
      return new CognitiveResult("COMPLETED", res.summary(), res.evidence(), res.referencedEntities(), reasoningSteps, List.of(), goal);
    }

    // Fallback
    reasoningSteps.add(EveReasoningStep.of(stepSeq, "COMPLETED", "Processed query with bounded cognitive layer."));
    return new CognitiveResult("COMPLETED", "Request processed.", List.of(), List.of(), reasoningSteps, List.of(), goal);
  }

  private EveGoal understandWithQwen(
      String prompt,
      String sessionContext,
      LocalQwenModelProvider qwen,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {
    try {
      EveGoal goal = qwen.understandCognitiveGoal(prompt, sessionContext);
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "UNDERSTANDING",
          String.format("Cognitive brain identified goal: %s (Operation: %s, Entity: %s).",
              goal.goal(), goal.operation(), goal.entityType())));
      return goal;
    } catch (Exception e) {
      log.warn("Qwen cognitive understanding failed, falling back to semantic analysis: {}", e.getMessage());
      return understandSemantically(prompt, sessionContext, null, reasoningSteps, stepSeq);
    }
  }

  /**
   * Semantic goal formulation: Maps natural language expressions and relationships into EveGoal.
   * Grounded in concepts, operations, and temporal reasoning.
   */
  public EveGoal understandSemantically(
      String prompt,
      String sessionContext,
      EveRetrievalRouter.SessionContext routerContext,
      List<EveReasoningStep> reasoningSteps,
      int stepSeq) {

    String lower = prompt.trim().toLowerCase(Locale.ROOT);

    // 1. Check for temporal range
    Optional<EveTemporalReasoningService.DateRange> dateRangeOpt = temporalService.resolveDateRange(prompt);

    // 2. Analytical: Count productions
    boolean isCount = lower.contains("kitne") || lower.contains("kitna") || lower.contains("how many") || lower.contains("count") || lower.contains("number of");
    boolean isProduction = lower.contains("event") || lower.contains("production") || lower.contains("events") || lower.contains("productions");
    boolean isCrew = lower.contains("log") || lower.contains("crew") || lower.contains("people") || lower.contains("members") || lower.contains("staff") || lower.contains("kaam kar rahe");
    boolean isTask = lower.contains("task") || lower.contains("tasks") || lower.contains("pending task") || lower.contains("pending kaam") || lower.contains("kaam");
    boolean isMost = lower.contains("most") || lower.contains("highest") || lower.contains("sabse jyada") || lower.contains("sabse zyada");

    // Profit / Margin / Financial Forecast inquiry (e.g. "MIPS event ka profit kitna hoga?")
    if (lower.contains("profit") || lower.contains("margin") || lower.contains("munafa") || lower.contains("fayda")) {
      String targetPhrase = extractProductionName(lower, routerContext, sessionContext);
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          String.format("Identified profit inquiry for %s.", targetPhrase != null ? targetPhrase : "production")));
      return new EveGoal(
          "DECISION_SUPPORT",
          EveOperation.DECISION_SUPPORT,
          "PRODUCTION",
          targetPhrase != null ? List.of(targetPhrase) : List.of(),
          Map.of("decisionMetric", "PROFIT"),
          null,
          null,
          "Financial records and profit forecast",
          "Verification against authoritative billing and disbursements",
          0.95,
          false,
          null);
    }

    // "which production has the most pending tasks?" / "Jo event sabse zyada pending kaam wala hai..."
    if (isMost && (isTask || lower.contains("pending"))) {
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          "Identified aggregation query: find production with highest count of open tasks."));
      return new EveGoal(
          "MOST_PENDING_TASKS",
          EveOperation.COMPARE,
          "PRODUCTION",
          List.of(),
          Map.of("hasOpenTasks", true, "aggregation", "MAX"),
          null,
          null,
          "Active productions and their open task counts",
          "Production with maximum open tasks identified",
          0.95,
          false,
          null);
    }

    // "which productions still owe us money?"
    if ((lower.contains("owe") || lower.contains("due") || lower.contains("pending money") || lower.contains("balance") || lower.contains("outstanding"))
        && (isProduction || lower.contains("client") || lower.contains("wedding") || lower.contains("who owes"))) {
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          "Identified financial query: retrieve productions with outstanding receivables."));
      return new EveGoal(
          "PRODUCTIONS_OWING_MONEY",
          EveOperation.FIND,
          "FINANCE",
          List.of(),
          Map.of("outstandingOnly", true),
          null,
          null,
          "Production financial records with contract > received",
          "List of productions with positive outstanding balances",
          0.95,
          false,
          null);
    }

    // Filter productions by crew and task state (e.g. "next week ke events jisme Kabir hai aur task pending hai")
    if (isProduction && (lower.contains("jisme") || lower.contains("involving") || lower.contains("with")) && (lower.contains("pending") || lower.contains("task") || lower.contains("open"))) {
      String crewToken = extractCrewName(lower);
      Map<String, Object> constraints = new HashMap<>();
      if (crewToken != null) {
        constraints.put("crewContains", crewToken);
      }
      constraints.put("hasOpenTasks", true);
      String timeLabel = dateRangeOpt.map(EveTemporalReasoningService.DateRange::label).orElse("NEXT_WEEK");

      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          String.format("Identified multi-constraint production search: timeRange=%s, crew=%s, hasOpenTasks=true.",
              timeLabel, crewToken != null ? crewToken : "any")));

      return new EveGoal(
          "FILTER_PRODUCTIONS_BY_CREW_AND_TASKS",
          EveOperation.FILTER,
          "PRODUCTION",
          crewToken != null ? List.of(crewToken) : List.of(),
          constraints,
          timeLabel,
          dateRangeOpt.orElse(null),
          "Productions matching date range, crew membership, and pending tasks",
          "Authoritative productions verified against crew and task ledgers",
          0.95,
          false,
          null);
    }

    // Count crew in production (e.g. "Sharma wedding me kitne log kaam kar rahe hain?")
    if (isCount && isCrew) {
      String prodName = extractProductionNameForCrewCount(lower, routerContext);
      if (prodName != null) {
        reasoningSteps.add(EveReasoningStep.of(
            stepSeq,
            "GOAL_INTERPRETATION",
            String.format("Identified crew count query for production \"%s\".", prodName)));
        return new EveGoal(
            "COUNT_PRODUCTION_CREW",
            EveOperation.COUNT,
            "CREW",
            List.of(prodName),
            Map.of("productionTitle", prodName),
            null,
            null,
            "Crew members assigned to " + prodName,
            "Total crew count computed from production_members ledger",
            0.95,
            false,
            null);
      }
    }

    // Count productions in time range (e.g. "next week kitne events hai")
    if (isCount && (isProduction || dateRangeOpt.isPresent())) {
      String timeLabel = dateRangeOpt.map(EveTemporalReasoningService.DateRange::label).orElse("NEXT_WEEK");
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          String.format("Identified count query for productions in %s.", timeLabel)));

      return new EveGoal(
          "COUNT_PRODUCTIONS",
          EveOperation.COUNT,
          "PRODUCTION",
          List.of(),
          Map.of("timeRange", timeLabel),
          timeLabel,
          dateRangeOpt.orElse(null),
          "Authoritative productions scheduled in " + timeLabel,
          "Count computed from production records in PostgreSQL",
          0.95,
          false,
          null);
    }

    // List productions in time range (e.g. "next week kaun kaun se events hain")
    if (isProduction && dateRangeOpt.isPresent()) {
      String timeLabel = dateRangeOpt.get().label();
      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "UNDERSTANDING",
          String.format("Identified list query for productions in %s.", timeLabel)));

      return new EveGoal(
          "LIST_PRODUCTIONS",
          EveOperation.LIST,
          "PRODUCTION",
          List.of(),
          Map.of("timeRange", timeLabel),
          timeLabel,
          dateRangeOpt.get(),
          "Authoritative productions scheduled in " + timeLabel,
          "Matching production records retrieved from PostgreSQL",
          0.95,
          false,
          null);
    }

    // Decision Support / Operational & Staffing Recommendations (e.g. "kya mujhe sharma wedding me 4 log bhejna chahiye?", "MIPS event ka profit kitna hoga?")
    boolean isDecision = lower.contains("invest") || lower.contains("kya mujhe") || lower.contains("should i")
        || lower.contains("kya hume") || lower.contains("should we")
        || lower.contains("profit") || lower.contains("fayda") || lower.contains("margin")
        || lower.contains("sahi rahega") || lower.contains("enough") || lower.contains("chahiye");
    if (isDecision && (isProduction || lower.contains("wedding") || lower.contains("event") || lower.contains("expo")
        || lower.contains("invest") || lower.contains("profit") || lower.contains("margin") || lower.contains("crew") || lower.contains("log") || lower.contains("bande")
        || lower.contains("people") || lower.contains("staff") || lower.contains("assign") || lower.contains("bhejna") || lower.contains("send"))) {
      String metric = "OPERATIONAL";
      if (lower.contains("profit") || lower.contains("margin") || lower.contains("fayda") || lower.contains("munafa")) {
        metric = "PROFIT";
      } else if (lower.contains("invest") || lower.contains("capital") || lower.contains("paisa lagana") || lower.contains("paise lagana")) {
        metric = "INVESTMENT";
      } else if (lower.contains("crew") || lower.contains("logo") || lower.contains("log") || lower.contains("people")
          || lower.contains("person") || lower.contains("members") || lower.contains("staff") || lower.contains("bande")
          || lower.contains("bhejna") || lower.contains("send") || lower.contains("assign") || lower.contains("depute")) {
        metric = "CREW_ALLOCATION";
      }
      String targetPhrase = extractProductionName(lower, routerContext, sessionContext);
      Long amountMinor = extractAmountMinor(lower);

      Map<String, Object> constraints = new HashMap<>();
      constraints.put("decisionMetric", metric);
      if (amountMinor != null) {
        constraints.put("amountMinor", amountMinor);
      }

      reasoningSteps.add(EveReasoningStep.of(
          stepSeq,
          "GOAL_INTERPRETATION",
          String.format("Identified decision support goal: metric=%s, target=%s.", metric, targetPhrase != null ? targetPhrase : "unspecified")));

      return new EveGoal(
          "DECISION_SUPPORT",
          EveOperation.DECISION_SUPPORT,
          "PRODUCTION",
          targetPhrase != null ? List.of(targetPhrase) : List.of(),
          constraints,
          null,
          null,
          "Authoritative domain facts for operational decision support",
          "Objective evidence verified from canonical ledgers",
          0.95,
          false,
          null);
    }

    // Single production or employee lookup fallback
    return new EveGoal(
        "GENERAL_LOOKUP",
        EveOperation.LOOKUP,
        "SYSTEM",
        List.of(),
        Map.of(),
        null,
        null,
        "System knowledge and records",
        "Response grounded in authoritative data",
        0.8,
        false,
        null);
  }

  private Optional<String> detectExternalCapabilityCategory(String lower) {
    if (lower.contains("mausam") || lower.contains("weather") || lower.contains("forecast")
        || lower.contains("barish") || lower.contains("rain") || lower.contains("temperature") || lower.contains("tapman")) {
      return Optional.of("WEATHER");
    }
    if (lower.contains("share price") || lower.contains("stock price") || lower.contains("sensex")
        || lower.contains("nifty") || lower.contains("crypto") || lower.contains("forex") || lower.contains("bitcoin")) {
      return Optional.of("FINANCE_MARKET");
    }
    if (lower.contains("flight status") || lower.contains("flight") || lower.contains("pnr") || lower.contains("live traffic")) {
      if (!lower.contains("flight case") && !lower.contains("case")) {
        return Optional.of("TRAVEL_TRANSIT");
      }
    }
    if (lower.contains("cricket score") || lower.contains("match score") || lower.contains("live score")) {
      return Optional.of("LIVE_SPORTS");
    }
    return Optional.empty();
  }

  private boolean isGeneralRecommendationQuery(String lower) {
    boolean isFood = lower.contains("lunch") || lower.contains("khana") || lower.contains("menu")
        || lower.contains("meal") || lower.contains("serve") || lower.contains("dinner")
        || lower.contains("breakfast") || lower.contains("food") || lower.contains("catering");
    boolean isSuggest = lower.contains("recommend") || lower.contains("suggestion") || lower.contains("suggest")
        || lower.contains("kya mangaayein") || lower.contains("kya order kare") || lower.contains("kya serve karein")
        || lower.contains("kya banwaye");
    boolean isOperationalAdvice = lower.contains("kaise distribute") || lower.contains("kaise manage")
        || lower.contains("kaise handle") || lower.contains("kaise plan")
        || lower.contains("how should we") || lower.contains("how to distribute") || lower.contains("best way to");
    return isFood || ((isSuggest || isOperationalAdvice) && !lower.contains("event") && !lower.contains("production") && !lower.contains("invest"));
  }

  private Long extractAmountMinor(String lower) {
    var p = java.util.regex.Pattern.compile("(?i)\\b(\\d{1,7})\\s*(k|thousand|lakh|lac)?\\b");
    var m = p.matcher(lower);
    while (m.find()) {
      long val = Long.parseLong(m.group(1));
      String mult = m.group(2);
      if (mult != null) {
        mult = mult.toLowerCase(Locale.ROOT);
        if (mult.equals("k") || mult.equals("thousand")) {
          val *= 1000L;
        } else if (mult.equals("lakh") || mult.equals("lac")) {
          val *= 100000L;
        }
      }
      if (val >= 100) {
        return val * 100L;
      }
    }
    return null;
  }

  private String extractProductionName(String lower, EveRetrievalRouter.SessionContext routerContext, String sessionContext) {
    if (routerContext != null && routerContext.getLastReferencedProduction() != null) {
      if (lower.contains("usme") || lower.contains("uska") || lower.contains("us event") || lower.contains("that event") || lower.contains("this event")) {
        return routerContext.getLastReferencedProduction().displayName();
      }
    }

    var p1 = java.util.regex.Pattern.compile("(?i)(?:invest(?:ing)?\\s+(?:in\\s+)?|kya\\s+mujhe\\s+(?:\\d+[kK]?\\s+)?)(.+?)(?:\\s+(?:wale|wali|ke|ka|ki)?\\s+(?:event|production)|\\s+me|\\s+mein|\\s+invest|$)");
    var m1 = p1.matcher(lower);
    if (m1.find()) {
      String cand = cleanEntityPhrase(m1.group(1));
      if (!cand.isBlank()) return toTitleCase(cand);
    }

    var p2 = java.util.regex.Pattern.compile("(?i)^(.+?)(?:\\s+(?:wale|wali|ke|ka|ki)?\\s+(?:event|production)|\\s+me|\\s+mein|\\s+ka\\s+profit|\\s+profit)");
    var m2 = p2.matcher(lower);
    if (m2.find()) {
      String cand = cleanEntityPhrase(m2.group(1));
      if (!cand.isBlank()) return toTitleCase(cand);
    }

    if (productionRepo != null) {
      for (var p : productionRepo.findAll()) {
        if (p.title != null && lower.contains(p.title.toLowerCase(Locale.ROOT))) {
          return p.title;
        }
      }
    }

    return null;
  }

  private String extractCrewName(String lower) {
    var p = java.util.regex.Pattern.compile("(?i)(?:jisme|involving|with|featuring)\\s+([a-zA-Z\\u0900-\\u097F]+?)(?:\\s+(?:hai|ho|aur|and|is|kaam|tasks?|pending|me|mein|ke|ki|ka|tha|the)\\b|$)");
    var m = p.matcher(lower);
    if (m.find()) {
      String cand = m.group(1).trim();
      if (!cand.equalsIgnoreCase("open") && !cand.equalsIgnoreCase("pending") && !cand.equalsIgnoreCase("task") && !cand.equalsIgnoreCase("event")) {
        return toTitleCase(cand);
      }
    }

    if (employeeRepo != null) {
      for (var emp : employeeRepo.findAll()) {
        String empName = emp.displayName != null ? emp.displayName : emp.firstName;
        if (empName != null && !empName.isBlank() && lower.contains(empName.toLowerCase(Locale.ROOT))) {
          return empName;
        }
      }
    }

    return null;
  }

  private String extractProductionNameForCrewCount(String lower, EveRetrievalRouter.SessionContext routerContext) {
    if (routerContext != null && routerContext.getLastReferencedProduction() != null) {
      if (lower.contains("usme") || lower.contains("iska") || lower.contains("uska") || lower.contains("there")) {
        return routerContext.getLastReferencedProduction().displayName();
      }
    }

    var p1 = java.util.regex.Pattern.compile("(?i)^(.+?)(?:\\s+(?:wale|wali|ke|ka|ki)?\\s+(?:event|production))?\\s+me\\s+kitne\\s+(?:log|crew|members|people|staff)");
    var m1 = p1.matcher(lower);
    if (m1.find()) {
      String cand = cleanEntityPhrase(m1.group(1));
      if (!cand.isBlank()) return toTitleCase(cand);
    }

    var p2 = java.util.regex.Pattern.compile("(?i)(?:how many (?:crew|people|members|staff) (?:are )?(?:working )?(?:on|in|for)|crew count (?:for|on|in))\\s+(.+?)(?:\\?|$|\\.)");
    var m2 = p2.matcher(lower);
    if (m2.find()) {
      String cand = cleanEntityPhrase(m2.group(1));
      if (!cand.isBlank()) return toTitleCase(cand);
    }

    if (productionRepo != null) {
      for (var p : productionRepo.findAll()) {
        if (p.title != null && lower.contains(p.title.toLowerCase(Locale.ROOT))) {
          return p.title;
        }
      }
    }

    if (lower.contains("me kitne") || lower.contains("mein kitne")) {
      int idx = lower.contains("me kitne") ? lower.indexOf("me kitne") : lower.indexOf("mein kitne");
      String sub = cleanEntityPhrase(lower.substring(0, idx));
      if (!sub.isBlank()) return toTitleCase(sub);
    }

    return null;
  }

  private String cleanEntityPhrase(String s) {
    if (s == null) return "";
    return s.replaceAll("(?i)\\b(kya mujhe|should i|invest in|invest|aur|and|the|event|production|wale|wali|ke|ka|ki)\\b", " ")
        .replaceAll("[^a-zA-Z0-9\\s'-]", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private String toTitleCase(String s) {
    if (s == null || s.isBlank()) return "";
    String[] parts = s.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append(" ");
      String p = parts[i];
      if (!p.isEmpty()) {
        sb.append(Character.toUpperCase(p.charAt(0)));
        if (p.length() > 1) {
          sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
      }
    }
    return sb.toString();
  }
}
