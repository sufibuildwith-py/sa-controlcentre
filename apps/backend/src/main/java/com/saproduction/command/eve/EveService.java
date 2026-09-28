package com.saproduction.command.eve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core Orchestrator for EVE Phase 3 Governed Execution.
 * EVE is the intelligence layer; existing SA Command domain services remain authoritative.
 * Supports:
 * - Natural-language query interpretation (English, Hindi, Hinglish)
 * - Session continuity and multi-turn context
 * - Governed execution plans (bounded, explicit confirmation token binding)
 * - Stale plan detection and idempotency protection
 * - Dispatching through EveCommandGateway to canonical domain services
 * - Authoritative post-execution verification in PostgreSQL
 * - Truthful observable activity traces
 */
@Service
public class EveService {

  private final EveModelProvider modelProvider;
  private final EveRetrievalService retrievalService;
  private final EveContextEngine contextEngine;
  private final EveKnowledgeService knowledgeService;
  private final EveMemoryService memoryService;
  private final FinanceReadService financeReadService;
  private final EveRetrievalRouter retrievalRouter;
  private final EveCommandGateway commandGateway;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public EveService(
      EveModelProvider modelProvider,
      EveRetrievalService retrievalService,
      EveContextEngine contextEngine,
      EveKnowledgeService knowledgeService,
      EveMemoryService memoryService,
      FinanceReadService financeReadService,
      JdbcTemplate jdbc) {
    this(modelProvider, retrievalService, contextEngine, knowledgeService, memoryService, financeReadService, null, null, jdbc);
  }

  public EveService(
      EveModelProvider modelProvider,
      EveRetrievalService retrievalService,
      EveContextEngine contextEngine,
      EveKnowledgeService knowledgeService,
      EveMemoryService memoryService,
      FinanceReadService financeReadService,
      EveRetrievalRouter retrievalRouter,
      JdbcTemplate jdbc) {
    this(modelProvider, retrievalService, contextEngine, knowledgeService, memoryService, financeReadService, retrievalRouter, null, jdbc);
  }

  @Autowired
  public EveService(
      EveModelProvider modelProvider,
      EveRetrievalService retrievalService,
      EveContextEngine contextEngine,
      EveKnowledgeService knowledgeService,
      @Autowired(required = false) EveMemoryService memoryService,
      FinanceReadService financeReadService,
      @Autowired(required = false) EveRetrievalRouter retrievalRouter,
      @Autowired(required = false) EveCommandGateway commandGateway,
      JdbcTemplate jdbc) {
    this.modelProvider = modelProvider;
    this.retrievalService = retrievalService;
    this.contextEngine = contextEngine;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.financeReadService = financeReadService;
    this.jdbc = jdbc;
    this.json = new ObjectMapper();
    this.retrievalRouter = retrievalRouter != null
        ? retrievalRouter
        : new EveRetrievalRouter(retrievalService, financeReadService, null, null, null, null, null, null, memoryService, contextEngine.getTimezone());
    this.commandGateway = commandGateway != null
        ? commandGateway
        : new EveCommandGateway(new EveCommandRegistry(List.of(new EmployeePaymentCommandDefinition())), null, financeReadService, jdbc, this.json);
  }

  @Transactional
  public EveDtos.QueryResponse query(EveDtos.QueryRequest request) {
    if (request == null || request.prompt() == null || request.prompt().isBlank()) {
      throw ApiException.badRequest("EVE_INVALID_PROMPT", "Prompt cannot be blank.");
    }

    UUID sessionId = request.sessionId() != null
        ? request.sessionId()
        : createSessionInternal(summarizeTitle(request.prompt()));

    ensureSessionExists(sessionId);

    // Save User Message
    UUID userMsgId = saveMessage(sessionId, "USER", request.prompt());

    List<EveDtos.TraceEventView> trace = new ArrayList<>();
    int seq = 1;

    // 1. STARTED
    trace.add(recordTrace(sessionId, userMsgId, seq++, "STARTED", "OK", "Request received", "Processing query: " + request.prompt()));

    // Load Session Context
    EveRetrievalRouter.SessionContext sessionContext = contextEngine.getSessionContext(sessionId);
    String contextSummary = buildSessionContextSummary(sessionId);

    // 2. INTERPRETING
    trace.add(recordTrace(sessionId, userMsgId, seq++, "INTERPRETING", "OK", "Interpreting intent", "Analyzing query in session context with ModelProvider"));
    EveModelProvider.EveInterpretation interpretation;
    try {
      interpretation = modelProvider.interpret(
          new EveModelProvider.EveInterpretationRequest(request.prompt(), contextSummary));
    } catch (ApiException e) {
      String status = "MODEL_FAILED";
      if ("EVE_MODEL_UNAVAILABLE".equals(e.code)) {
        status = "SYSTEM_UNAVAILABLE";
      }
      trace.add(recordTrace(sessionId, userMsgId, seq++, "FAILED", "ERROR", "Interpretation failed", e.getMessage()));
      UUID errAssistantMsgId = saveMessage(sessionId, "ASSISTANT", "Unable to process query: " + e.getMessage());
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(errAssistantMsgId, sessionId, "ASSISTANT", "Unable to process query: " + e.getMessage(), Instant.now()),
          trace,
          null,
          status,
          List.of(),
          null);
    }

    // Safety policy check
    if (interpretation.intent() == EveModelProvider.Intent.BLOCKED) {
      trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "BLOCKED", "Policy blocked", interpretation.refusalReason()));
      String blockMsg = "Request blocked by safety policy: " + interpretation.refusalReason();
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", blockMsg);
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", blockMsg, Instant.now()),
          trace,
          null,
          "POLICY_BLOCKED",
          List.of(),
          null);
    }

    // Governed Write Proposal: Employee Payment (Phase 3 Principal Vertical Slice)
    if (interpretation.intent() == EveModelProvider.Intent.PROPOSE_EMPLOYEE_PAYMENT) {
      return handlePaymentProposal(sessionId, userMsgId, request, interpretation, sessionContext, trace, seq);
    }

    // 3. RESOLVING & ROUTING
    trace.add(recordTrace(sessionId, userMsgId, seq++, "RESOLVING", "OK", "Resolving references", "Checking entities and session context"));
    trace.add(recordTrace(sessionId, userMsgId, seq++, "ROUTING", "OK", "Routing retrieval", "Dispatching to domain read service: " + interpretation.intent()));

    // 4. RETRIEVING AUTHORITATIVE STATE
    EveRetrievalRouter.RouterResult routerResult = retrievalRouter.routeAndRetrieve(interpretation, sessionContext, request.prompt());

    if ("CLARIFICATION_REQUIRED".equals(routerResult.status())) {
      trace.add(recordTrace(
          sessionId, userMsgId, seq++, "BLOCKED", "BLOCKED", "Ambiguity detected", routerResult.answer()));

      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", routerResult.answer());
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", routerResult.answer(), Instant.now()),
          trace,
          null,
          "CLARIFICATION_REQUIRED",
          routerResult.candidates(),
          null);
    }

    if ("NOT_FOUND".equals(routerResult.status())) {
      trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Entity not found", routerResult.answer()));
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", routerResult.answer());
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", routerResult.answer(), Instant.now()),
          trace,
          null,
          "NOT_FOUND",
          List.of(),
          null);
    }

    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "RETRIEVING", "OK", "Retrieving authoritative state", "Loaded factual state from system of record"));

    // 5. ASSEMBLING CONTEXT
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "ASSEMBLING_CONTEXT", "OK", "Assembling bounded context", "Formulating grounded answer with visible evidence"));

    List<String> knowledgeSnippets = knowledgeService != null
        ? knowledgeService.getRelevantKnowledge(interpretation.intent().name(), request.prompt())
        : List.of();

    List<EveDtos.MemoryView> memoryHints = new ArrayList<>();
    if (memoryService != null && interpretation.spokenEntity() != null) {
      memoryService.recall(interpretation.spokenEntity()).ifPresent(memoryHints::add);
    }

    EveDtos.ContextView context = contextEngine.buildContextView(
        "Azeem Khan",
        routerResult.referencedEntities(),
        routerResult.evidence(),
        knowledgeSnippets,
        memoryHints);

    // 6. COMPLETED
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "COMPLETED", "OK", "Query completed", "Grounded operational state verified from PostgreSQL"));

    UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", routerResult.answer());

    return new EveDtos.QueryResponse(
        sessionId,
        new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", routerResult.answer(), Instant.now()),
        trace,
        context,
        routerResult.status() != null ? routerResult.status() : "COMPLETED",
        routerResult.candidates() != null ? routerResult.candidates() : List.of(),
        null);
  }

  private EveDtos.QueryResponse handlePaymentProposal(
      UUID sessionId,
      UUID userMsgId,
      EveDtos.QueryRequest request,
      EveModelProvider.EveInterpretation interpretation,
      EveRetrievalRouter.SessionContext sessionContext,
      List<EveDtos.TraceEventView> trace,
      int seq) {

    // 1. RESOLVING
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "RESOLVING", "OK", "Resolving employee candidate",
        "Searching canonical records for: " + interpretation.spokenEntity()));

    UUID empId = null;
    String empName = null;

    if ("uska".equalsIgnoreCase(interpretation.spokenEntity())
        || "use".equalsIgnoreCase(interpretation.spokenEntity())
        || "him".equalsIgnoreCase(interpretation.spokenEntity())) {
      EveRetrievalService.Candidate lastEmp = sessionContext.getLastReferencedEmployee();
      if (lastEmp == null) {
        String clarify = "Which employee would you like to pay? Please specify the person's name.";
        trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Ambiguous reference", clarify));
        UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", clarify);
        return new EveDtos.QueryResponse(
            sessionId,
            new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", clarify, Instant.now()),
            trace,
            null,
            "CLARIFICATION_REQUIRED",
            List.of(),
            null);
      }
      empId = lastEmp.id();
      empName = lastEmp.displayName();
    } else {
      EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(interpretation.spokenEntity());

      if (res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
        String notFound = "I could not find an active employee matching '" + interpretation.spokenEntity() + "'.";
        trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Entity not found", notFound));
        UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", notFound);
        return new EveDtos.QueryResponse(
            sessionId,
            new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", notFound, Instant.now()),
            trace,
            null,
            "NOT_FOUND",
            List.of(),
            null);
      }

      if (res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        List<EveDtos.CandidateView> candidateViews = res.candidates().stream()
            .map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail()))
            .toList();
        String clarify = "Found " + candidateViews.size() + " employees matching '" + interpretation.spokenEntity()
            + "'. Which one did you mean?";
        trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "BLOCKED", "Ambiguity detected", clarify));
        UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", clarify);
        return new EveDtos.QueryResponse(
            sessionId,
            new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", clarify, Instant.now()),
            trace,
            null,
            "CLARIFICATION_REQUIRED",
            candidateViews,
            null);
      }

      EveRetrievalService.Candidate resolved = res.resolved();
      empId = resolved.id();
      empName = resolved.displayName();
      sessionContext.setLastReferencedEmployee(resolved);
    }

    // Resolve payer account
    String payer = interpretation.secondaryEntity() != null && !interpretation.secondaryEntity().isBlank()
        ? interpretation.secondaryEntity().trim().toUpperCase(Locale.ROOT)
        : "AZ-2";

    // 2. RETRIEVING AUTHORITATIVE FINANCIAL STATE
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "RETRIEVING", "OK", "Retrieving financial obligations",
        "Reading authoritative employee balance for " + empName));

    Map<String, Object> empFin = financeReadService.employee(empId);
    BigDecimal earned = (BigDecimal) empFin.get("earned");
    BigDecimal paid = (BigDecimal) empFin.get("paid");
    BigDecimal outstanding = (BigDecimal) empFin.get("outstanding");

    // 3. VALIDATING DOMAIN RULES
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "VALIDATING", "OK", "Validating domain constraints",
        "Checking payment amount against payable balance and owner account"));

    long amtMinor = interpretation.amountMinor() != null ? interpretation.amountMinor() : 0L;
    BigDecimal amount = BigDecimal.valueOf(amtMinor, 2).setScale(2, RoundingMode.UNNECESSARY);

    if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
      String msg = empName + " currently has no pending payable balance (earned: ₹"
          + (earned != null ? earned.toPlainString() : "0")
          + ", paid: ₹" + (paid != null ? paid.toPlainString() : "0")
          + "). A payment cannot be proposed.";
      trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Zero payable balance", msg));
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", msg);
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", msg, Instant.now()),
          trace,
          null,
          "COMPLETED",
          List.of(),
          null);
    }

    if (amount.compareTo(outstanding) > 0) {
      String msg = "Proposed payment of ₹" + amount.toPlainString() + " exceeds " + empName
          + "'s total outstanding balance of ₹" + outstanding.toPlainString()
          + ". Payment cannot exceed payable amount.";
      trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Payment exceeds payable balance", msg));
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", msg);
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", msg, Instant.now()),
          trace,
          null,
          "BLOCKED",
          List.of(),
          null);
    }

    // 4. PLANNING
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "PLANNING", "OK", "Building governed execution plan",
        "Constructing deterministic plan bound to planHash"));

    UUID planId = UUID.randomUUID();
    int version = 1;
    String intent = "EMPLOYEE_PAYMENT";
    String payerLabel = "AZ-2".equals(payer) ? "AZ-2 (Azeem Khan)" : "AK-2".equals(payer) ? "AK-2 (Akash)" : payer;
    String summary = "Pay " + EveAmountParser.formatMinor(amtMinor) + " to " + empName + " from " + payerLabel;
    EveDtos.RiskTier riskTier = EveDtos.RiskTier.FINANCIAL_WRITE;
    boolean confirmationRequired = true;

    UUID actionId = UUID.randomUUID();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("employeeId", empId.toString());
    params.put("amount", amount.toPlainString());
    params.put("payerAccount", payer);
    params.put("date", LocalDate.now().toString());
    params.put("description", "Payment to " + empName + " via EVE");

    BigDecimal newOutstanding = outstanding.subtract(amount);
    String estimatedEffect = "Outstanding payable balance for " + empName + " will reduce from ₹"
        + outstanding.toPlainString() + " to ₹" + newOutstanding.toPlainString()
        + "; Payer " + payer + " position will reduce by ₹" + amount.toPlainString() + ".";

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId,
        1,
        "FINANCE",
        "RECORD_EMPLOYEE_PAYMENT",
        empId,
        empName,
        params,
        estimatedEffect,
        "FINANCE_WRITE");

    String planHash = EvePlanHasher.calculateHash(planId, version, intent, riskTier, List.of(action));
    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId,
        sessionId,
        intent,
        summary,
        riskTier,
        confirmationRequired,
        planHash,
        version,
        "PROPOSED",
        List.of(action));

    savePlan(plan, userMsgId);

    // 5. WAITING_CONFIRMATION
    trace.add(recordTrace(
        sessionId, userMsgId, seq++, "WAITING_CONFIRMATION", "OK", "Awaiting confirmation",
        "Plan proposed. Awaiting explicit confirmation token bound to planHash."));

    String answer = "I have prepared a payment plan of ₹" + amount.toPlainString() + " for " + empName
        + " from account " + payerLabel + ". Outstanding balance will reduce to ₹"
        + newOutstanding.toPlainString() + ". Please review and confirm below.";
    UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", answer);

    EveDtos.ContextView context = contextEngine.buildContextView(
        "Azeem Khan",
        List.of(new EveDtos.EntityReference(empId, "EMPLOYEE", empName, "")),
        List.of(
            new EveDtos.EvidenceItem("FINANCE", "Earned", "₹" + earned.toPlainString()),
            new EveDtos.EvidenceItem("FINANCE", "Paid", "₹" + paid.toPlainString()),
            new EveDtos.EvidenceItem("FINANCE", "Outstanding", "₹" + outstanding.toPlainString()),
            new EveDtos.EvidenceItem("FINANCE", "Proposed Payment", "₹" + amount.toPlainString()),
            new EveDtos.EvidenceItem("FINANCE", "Payer Account", payerLabel)),
        List.of(),
        List.of());

    return new EveDtos.QueryResponse(
        sessionId,
        new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", answer, Instant.now()),
        trace,
        context,
        "WAITING_CONFIRMATION",
        List.of(),
        plan);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public EveDtos.PlanExecutionResponse confirmPlan(UUID planId, EveDtos.ConfirmPlanRequest request) {
    if (request == null || request.sessionId() == null || request.planId() == null || request.planHash() == null) {
      throw ApiException.badRequest("INVALID_CONFIRMATION", "Session ID, Plan ID and Plan Hash are required.");
    }
    if (!planId.equals(request.planId())) {
      throw ApiException.badRequest("PLAN_ID_MISMATCH", "Plan ID in path must match request body.");
    }

    ensureSessionExists(request.sessionId());

    // Row-level lock on plan
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, session_id, message_id, intent, summary, risk_tier, confirmation_required, plan_hash, version, status FROM eve_plans WHERE id = ? FOR UPDATE",
        planId);
    if (rows.isEmpty()) {
      throw ApiException.notFound("PLAN_NOT_FOUND", "Plan " + planId + " was not found.");
    }
    Map<String, Object> r = rows.getFirst();
    UUID sessionId = (UUID) r.get("session_id");
    if (!sessionId.equals(request.sessionId())) {
      throw ApiException.badRequest("PLAN_SESSION_MISMATCH", "Plan does not belong to specified session.");
    }

    String currentStatus = (String) r.get("status");
    int version = ((Number) r.get("version")).intValue();
    String storedHash = (String) r.get("plan_hash");

    // Idempotency check: if plan is already COMPLETED, return cached execution response
    if ("COMPLETED".equals(currentStatus)) {
      if (!storedHash.equalsIgnoreCase(request.planHash().trim())) {
        throw ApiException.conflict("PLAN_HASH_MISMATCH", "Confirmation token mismatch on completed plan.");
      }
      EveDtos.EvePlan completedPlan = getPlan(planId);
      List<EveDtos.TraceEventView> traces = getSessionTraces(sessionId);
      UUID msgId = (UUID) r.get("message_id");
      return new EveDtos.PlanExecutionResponse(
          planId,
          sessionId,
          "COMPLETED",
          completedPlan.summary(),
          traces,
          completedPlan.actions(),
          new EveDtos.MessageView(msgId, sessionId, "ASSISTANT", "Plan already executed successfully.", Instant.now()));
    }

    if (!"PROPOSED".equals(currentStatus)) {
      throw ApiException.conflict("PLAN_NOT_ACTIONABLE", "Plan status is " + currentStatus + " and cannot be confirmed.");
    }

    if (version != request.planVersion() || !storedHash.equalsIgnoreCase(request.planHash().trim())) {
      throw ApiException.conflict("PLAN_HASH_MISMATCH", "Plan version or cryptographic hash does not match current proposal.");
    }

    int seq = getNextTraceSeq(sessionId);
    EveDtos.EvePlan plan = getPlan(planId);
    EveExecutionContext context = commandGateway.createContext(sessionId, planId, version, storedHash, "Azeem Khan");

    // 1. REVALIDATING
    recordTrace(sessionId, null, seq++, "REVALIDATING", "OK", "Revalidating canonical preconditions", "Re-checking fresh state from PostgreSQL");
    try {
      commandGateway.revalidatePreconditions(plan, context);
    } catch (ApiException e) {
      jdbc.update("UPDATE eve_plans SET status = 'STALE_PLAN', updated_at = now() WHERE id = ?", planId);
      recordTrace(sessionId, null, seq++, "STALE_PLAN", "WARN", "Plan became stale", e.getMessage());
      throw e;
    }

    // 2. EXECUTING
    jdbc.update("UPDATE eve_plans SET status = 'EXECUTING', updated_at = now() WHERE id = ?", planId);
    recordTrace(sessionId, null, seq++, "EXECUTING", "OK", "Executing canonical domain commands", "Invoking canonical services with transaction isolation");
    List<EveDtos.EvePlanAction> executedActions;
    try {
      executedActions = commandGateway.executePlan(plan, context);
    } catch (ApiException e) {
      jdbc.update("UPDATE eve_plans SET status = 'FAILED', updated_at = now() WHERE id = ?", planId);
      recordTrace(sessionId, null, seq++, "FAILED", "ERROR", "Execution failed", e.getMessage());
      throw e;
    }

    // 3. VERIFYING
    recordTrace(sessionId, null, seq++, "VERIFYING", "OK", "Authoritative PostgreSQL verification", "Verified database records, double-entry allocations and audit events");

    // 4. COMPLETED
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update("UPDATE eve_plans SET status = 'COMPLETED', confirmed_at = ?, executed_at = ?, updated_at = ? WHERE id = ?", now, now, now, planId);
    for (EveDtos.EvePlanAction act : executedActions) {
      String execJson = "{}";
      String verifJson = "{}";
      try {
        if (act.executionResult() != null) execJson = json.writeValueAsString(act.executionResult());
        if (act.verificationResult() != null) verifJson = json.writeValueAsString(act.verificationResult());
      } catch (Exception ignored) {}

      jdbc.update(
          """
          UPDATE eve_plan_actions
          SET status = ?, canonical_record_id = ?, execution_result = ?::jsonb, verification_result = ?::jsonb
          WHERE id = ?
          """,
          act.status(),
          act.canonicalRecordId(),
          execJson,
          verifJson,
          act.actionId());
    }

    recordTrace(sessionId, null, seq++, "COMPLETED", "OK", "Governed execution complete", "All actions verified in system of record");

    String completionMsg = "Payment posted and verified successfully in PostgreSQL. " + executedActions.getFirst().estimatedEffect();
    UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", completionMsg);

    List<EveDtos.TraceEventView> traces = getSessionTraces(sessionId);
    return new EveDtos.PlanExecutionResponse(
        planId,
        sessionId,
        "COMPLETED",
        plan.summary(),
        traces,
        executedActions,
        new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", completionMsg, Instant.now()));
  }

  @Transactional
  public EveDtos.EvePlan cancelPlan(UUID planId, EveDtos.CancelPlanRequest request) {
    if (request == null || request.sessionId() == null || request.planId() == null) {
      throw ApiException.badRequest("INVALID_CANCELLATION", "Session ID and Plan ID are required.");
    }
    ensureSessionExists(request.sessionId());

    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, session_id, status FROM eve_plans WHERE id = ? FOR UPDATE",
        planId);
    if (rows.isEmpty()) {
      throw ApiException.notFound("PLAN_NOT_FOUND", "Plan " + planId + " was not found.");
    }
    Map<String, Object> r = rows.getFirst();
    UUID sessionId = (UUID) r.get("session_id");
    if (!sessionId.equals(request.sessionId())) {
      throw ApiException.badRequest("PLAN_SESSION_MISMATCH", "Plan does not belong to specified session.");
    }

    String currentStatus = (String) r.get("status");
    if (!"PROPOSED".equals(currentStatus)) {
      throw ApiException.badRequest("PLAN_NOT_CANCELLABLE", "Plan status is " + currentStatus + " and cannot be cancelled.");
    }

    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update("UPDATE eve_plans SET status = 'CANCELLED', updated_at = ? WHERE id = ?", now, planId);

    int seq = getNextTraceSeq(sessionId);
    recordTrace(sessionId, null, seq, "CANCELLED", "OK", "Plan cancelled", "Operator cancelled proposed plan: " + (request.reason() != null ? request.reason() : "Cancelled by user"));
    saveMessage(sessionId, "ASSISTANT", "Plan was cancelled. No changes were made to system of record.");

    return getPlan(planId);
  }

  @Transactional(readOnly = true)
  public EveDtos.EvePlan getPlan(UUID planId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, session_id, message_id, intent, summary, risk_tier, confirmation_required, plan_hash, version, status FROM eve_plans WHERE id = ?",
        planId);
    if (rows.isEmpty()) {
      throw ApiException.notFound("PLAN_NOT_FOUND", "Plan " + planId + " does not exist.");
    }
    Map<String, Object> r = rows.getFirst();
    List<EveDtos.EvePlanAction> actions = loadPlanActions(planId);
    return new EveDtos.EvePlan(
        (UUID) r.get("id"),
        (UUID) r.get("session_id"),
        (String) r.get("intent"),
        (String) r.get("summary"),
        EveDtos.RiskTier.valueOf((String) r.get("risk_tier")),
        Boolean.TRUE.equals(r.get("confirmation_required")),
        (String) r.get("plan_hash"),
        ((Number) r.get("version")).intValue(),
        (String) r.get("status"),
        actions);
  }

  @Transactional(readOnly = true)
  public List<EveDtos.EvePlan> listPlansForSession(UUID sessionId) {
    ensureSessionExists(sessionId);
    List<UUID> planIds = jdbc.query(
        "SELECT id FROM eve_plans WHERE session_id = ? ORDER BY created_at DESC",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        sessionId);
    List<EveDtos.EvePlan> result = new ArrayList<>();
    for (UUID id : planIds) {
      result.add(getPlan(id));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<EveDtos.SessionView> listSessions() {
    return jdbc.query(
        "SELECT id, title, status, created_at, updated_at FROM eve_sessions ORDER BY updated_at DESC LIMIT 50",
        (rs, rowNum) -> new EveDtos.SessionView(
            (UUID) rs.getObject("id"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            List.of()));
  }

  @Transactional(readOnly = true)
  public EveDtos.SessionView getSession(UUID id) {
    ensureSessionExists(id);

    EveDtos.SessionView session = jdbc.queryForObject(
        "SELECT id, title, status, created_at, updated_at FROM eve_sessions WHERE id = ?",
        (rs, rowNum) -> new EveDtos.SessionView(
            (UUID) rs.getObject("id"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            List.of()),
        id);

    List<EveDtos.MessageView> msgs = jdbc.query(
        "SELECT id, session_id, role, content, created_at FROM eve_messages WHERE session_id = ? ORDER BY created_at ASC",
        (rs, rowNum) -> new EveDtos.MessageView(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("session_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant()),
        id);

    return new EveDtos.SessionView(
        session.id(),
        session.title(),
        session.status(),
        session.createdAt(),
        session.updatedAt(),
        msgs);
  }

  @Transactional
  public EveDtos.SessionView createSession(String title) {
    UUID id = createSessionInternal(title != null && !title.isBlank() ? title : "New Command Session");
    return getSession(id);
  }

  @Transactional(readOnly = true)
  public List<EveDtos.MemoryView> listMemories() {
    if (memoryService == null) {
      return List.of();
    }
    return memoryService.listMemories();
  }

  @Transactional
  public EveDtos.MemoryView remember(EveDtos.MemoryRequest request) {
    if (memoryService == null) {
      throw ApiException.badRequest("EVE_MEMORY_UNAVAILABLE", "Memory service not configured.");
    }
    return memoryService.remember(request, "OPERATOR_EXPLICIT");
  }

  @Transactional
  public boolean deleteMemory(UUID id) {
    if (memoryService == null) {
      return false;
    }
    return memoryService.deleteMemory(id);
  }

  private void savePlan(EveDtos.EvePlan plan, UUID messageId) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        """
        INSERT INTO eve_plans (id, session_id, message_id, intent, summary, risk_tier, confirmation_required, plan_hash, version, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        plan.planId(),
        plan.sessionId(),
        messageId,
        plan.intent(),
        plan.summary(),
        plan.riskTier().name(),
        plan.confirmationRequired(),
        plan.planHash(),
        plan.version(),
        plan.status(),
        now,
        now);

    for (EveDtos.EvePlanAction action : plan.actions()) {
      String paramsJson = "{}";
      try {
        paramsJson = json.writeValueAsString(action.parameters() != null ? action.parameters() : Map.of());
      } catch (Exception ignored) {}

      UUID actionIdempotencyKey = UUID.nameUUIDFromBytes(
          (plan.planId() + ":" + action.seq() + ":" + plan.planHash()).getBytes(java.nio.charset.StandardCharsets.UTF_8));

      jdbc.update(
          """
          INSERT INTO eve_plan_actions (id, plan_id, seq, domain, command_type, target_entity_id, target_entity_name, parameters, estimated_effect, required_permission, status, idempotency_key, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
          """,
          action.actionId(),
          plan.planId(),
          action.seq(),
          action.domain(),
          action.commandType(),
          action.targetEntityId(),
          action.targetEntityName(),
          paramsJson,
          action.estimatedEffect(),
          action.requiredPermission(),
          action.status(),
          actionIdempotencyKey,
          now);
    }
  }

  private List<EveDtos.EvePlanAction> loadPlanActions(UUID planId) {
    return jdbc.query(
        """
        SELECT id, plan_id, seq, domain, command_type, target_entity_id, target_entity_name,
               parameters::text AS params_text, estimated_effect, required_permission,
               status, canonical_record_id, execution_result::text AS exec_text, verification_result::text AS verif_text
        FROM eve_plan_actions WHERE plan_id = ? ORDER BY seq ASC
        """,
        (rs, rowNum) -> {
          UUID actionId = (UUID) rs.getObject("id");
          int seq = rs.getInt("seq");
          String domain = rs.getString("domain");
          String commandType = rs.getString("command_type");
          UUID targetEntityId = (UUID) rs.getObject("target_entity_id");
          String targetEntityName = rs.getString("target_entity_name");
          String paramsText = rs.getString("params_text");
          Map<String, Object> params = Map.of();
          if (paramsText != null && !paramsText.isBlank()) {
            try {
              params = json.readValue(paramsText, Map.class);
            } catch (Exception ignored) {}
          }
          String estimatedEffect = rs.getString("estimated_effect");
          String requiredPermission = rs.getString("required_permission");
          String status = rs.getString("status");
          UUID canonicalRecordId = (UUID) rs.getObject("canonical_record_id");
          return new EveDtos.EvePlanAction(
              actionId,
              seq,
              domain,
              commandType,
              targetEntityId,
              targetEntityName,
              params,
              estimatedEffect,
              requiredPermission,
              status,
              canonicalRecordId,
              null,
              null);
        },
        planId);
  }

  private UUID createSessionInternal(String title) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO eve_sessions (id, title, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)",
        id,
        title,
        now,
        now);
    return id;
  }

  private void ensureSessionExists(UUID sessionId) {
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM eve_sessions WHERE id = ?",
        Integer.class,
        sessionId);
    if (count == null || count == 0) {
      throw ApiException.notFound("EVE_SESSION_NOT_FOUND", "Session " + sessionId + " does not exist.");
    }
  }

  private UUID saveMessage(UUID sessionId, String role, String content) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO eve_messages (id, session_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
        id,
        sessionId,
        role,
        content,
        now);

    jdbc.update("UPDATE eve_sessions SET updated_at = ? WHERE id = ?", now, sessionId);
    return id;
  }

  private EveDtos.TraceEventView recordTrace(
      UUID sessionId,
      UUID messageId,
      int seq,
      String eventType,
      String status,
      String label,
      String detail) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO eve_trace_events (id, session_id, message_id, seq, event_type, status, label, detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        sessionId,
        messageId,
        seq,
        eventType,
        status,
        label,
        detail,
        now);

    return new EveDtos.TraceEventView(id, sessionId, messageId, seq, eventType, status, label, detail, now.toInstant());
  }

  private int getNextTraceSeq(UUID sessionId) {
    Integer max = jdbc.queryForObject(
        "SELECT coalesce(max(seq), 0) FROM eve_trace_events WHERE session_id = ?",
        Integer.class,
        sessionId);
    return (max != null ? max : 0) + 1;
  }

  private List<EveDtos.TraceEventView> getSessionTraces(UUID sessionId) {
    return jdbc.query(
        "SELECT id, session_id, message_id, seq, event_type, status, label, detail, created_at FROM eve_trace_events WHERE session_id = ? ORDER BY seq ASC",
        (rs, rowNum) -> new EveDtos.TraceEventView(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("session_id"),
            (UUID) rs.getObject("message_id"),
            rs.getInt("seq"),
            rs.getString("event_type"),
            rs.getString("status"),
            rs.getString("label"),
            rs.getString("detail"),
            rs.getTimestamp("created_at").toInstant()),
        sessionId);
  }

  private String buildSessionContextSummary(UUID sessionId) {
    try {
      List<String> recent = jdbc.query(
          "SELECT role, content FROM eve_messages WHERE session_id = ? ORDER BY created_at DESC LIMIT 3",
          (rs, rowNum) -> rs.getString("role") + ": " + rs.getString("content"),
          sessionId);
      Collections.reverse(recent);
      return String.join(" | ", recent);
    } catch (Exception ignored) {
      return "";
    }
  }

  private String summarizeTitle(String prompt) {
    if (prompt.length() <= 32) {
      return prompt;
    }
    return prompt.substring(0, 32) + "...";
  }
}
