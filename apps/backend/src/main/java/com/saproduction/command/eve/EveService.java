package com.saproduction.command.eve;

import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EveService {

  private final EveModelProvider modelProvider;
  private final EveRetrievalService retrievalService;
  private final EveContextEngine contextEngine;
  private final EveKnowledgeService knowledgeService;
  private final EveMemoryService memoryService;
  private final FinanceReadService financeReadService;
  private final JdbcTemplate jdbc;

  @Autowired
  public EveService(
      EveModelProvider modelProvider,
      EveRetrievalService retrievalService,
      EveContextEngine contextEngine,
      EveKnowledgeService knowledgeService,
      @Autowired(required = false) EveMemoryService memoryService,
      FinanceReadService financeReadService,
      JdbcTemplate jdbc) {
    this.modelProvider = modelProvider;
    this.retrievalService = retrievalService;
    this.contextEngine = contextEngine;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.financeReadService = financeReadService;
    this.jdbc = jdbc;
  }

  @Transactional
  public EveDtos.QueryResponse query(EveDtos.QueryRequest request) {
    if (request == null || request.prompt() == null || request.prompt().isBlank()) {
      throw ApiException.badRequest("EVE_INVALID_PROMPT", "Prompt cannot be blank.");
    }

    UUID sessionId = request.sessionId() != null
        ? request.sessionId()
        : createSessionInternal(summarizeTitle(request.prompt()));

    // Verify session exists
    ensureSessionExists(sessionId);

    // Save User Message
    UUID userMsgId = saveMessage(sessionId, "USER", request.prompt());

    List<EveDtos.TraceEventView> trace = new ArrayList<>();
    int seq = 1;

    // 1. STARTED
    trace.add(recordTrace(sessionId, userMsgId, seq++, "STARTED", "OK", "Request received", "Processing query: " + request.prompt()));

    // 2. INTERPRETING
    trace.add(recordTrace(sessionId, userMsgId, seq++, "INTERPRETING", "OK", "Interpreting intent", "Analyzing query with ModelProvider"));
    EveModelProvider.EveInterpretation interpretation;
    try {
      interpretation = modelProvider.interpret(
          new EveModelProvider.EveInterpretationRequest(request.prompt(), null));
    } catch (ApiException e) {
      trace.add(recordTrace(sessionId, userMsgId, seq++, "FAILED", "ERROR", "Interpretation failed", e.getMessage()));
      UUID errAssistantMsgId = saveMessage(sessionId, "ASSISTANT", "Unable to process query: " + e.getMessage());
      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(errAssistantMsgId, sessionId, "ASSISTANT", "Unable to process query: " + e.getMessage(), Instant.now()),
          trace,
          null,
          "MODEL_FAILED",
          List.of());
    }

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
          List.of());
    }

    // 3. SEARCHING / RETRIEVAL
    String spoken = interpretation.spokenEntity() != null ? interpretation.spokenEntity() : request.prompt();
    trace.add(recordTrace(sessionId, userMsgId, seq++, "SEARCHING", "OK", "Searching entities", "Looking up canonical entity for: " + spoken));

    EveRetrievalService.ResolutionResult resolution = retrievalService.resolveEmployee(spoken);

    if (resolution.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
      trace.add(recordTrace(
          sessionId,
          userMsgId,
          seq++,
          "BLOCKED",
          "BLOCKED",
          "Ambiguity detected",
          "Found " + resolution.candidates().size() + " matches for \"" + spoken + "\". Clarification required."));

      List<EveDtos.CandidateView> candidates = resolution.candidates().stream()
          .map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail()))
          .toList();

      String clarMsg = "I found multiple matching team members for \"" + spoken + "\". Please choose which employee you meant:";
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", clarMsg);

      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", clarMsg, Instant.now()),
          trace,
          null,
          "CLARIFICATION_REQUIRED",
          candidates);
    }

    if (resolution.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
      trace.add(recordTrace(sessionId, userMsgId, seq++, "BLOCKED", "WARN", "Entity not found", "No matching records found for \"" + spoken + "\""));
      String notFoundMsg = "I could not find any active team member matching \"" + spoken + "\" in the system.";
      UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", notFoundMsg);

      return new EveDtos.QueryResponse(
          sessionId,
          new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", notFoundMsg, Instant.now()),
          trace,
          null,
          "NOT_FOUND",
          List.of());
    }

    // 4. MATCHED
    EveRetrievalService.Candidate emp = resolution.resolved();
    trace.add(recordTrace(
        sessionId,
        userMsgId,
        seq++,
        "MATCHED",
        "OK",
        "Entity matched",
        "Resolved to " + emp.displayName() + " (" + emp.code() + ") via " + resolution.provenance().matchMethod()));

    // 5. RETRIEVING AUTHORITATIVE STATE
    trace.add(recordTrace(
        sessionId,
        userMsgId,
        seq++,
        "RETRIEVING",
        "OK",
        "Retrieving authoritative state",
        "Loading current financial position from FinanceReadService"));

    Map<String, Object> empFinance = financeReadService.employee(emp.id());
    BigDecimal earned = (BigDecimal) empFinance.getOrDefault("earned", BigDecimal.ZERO);
    BigDecimal paid = (BigDecimal) empFinance.getOrDefault("paid", BigDecimal.ZERO);
    BigDecimal outstanding = (BigDecimal) empFinance.getOrDefault("outstanding", BigDecimal.ZERO);

    NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    String earnedStr = inr.format(earned);
    String paidStr = inr.format(paid);
    String outstandingStr = inr.format(outstanding);

    // Build bounded context & evidence
    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("FINANCE", "Total Earned", earnedStr),
        new EveDtos.EvidenceItem("FINANCE", "Total Paid", paidStr),
        new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", outstandingStr));

    List<String> knowledgeSnippets = knowledgeService != null
        ? knowledgeService.getRelevantKnowledge("FINANCE", "READ_EMPLOYEE_FINANCE")
        : List.of();

    List<EveDtos.MemoryView> memoryHints = new ArrayList<>();
    if (memoryService != null) {
      memoryService.recall(spoken).ifPresent(memoryHints::add);
    }

    EveDtos.ContextView context = contextEngine.buildContextView(
        "Azeem Khan",
        entities,
        evidence,
        knowledgeSnippets,
        memoryHints);

    // Grounded Answer Formulation
    String answer = String.format(
        "%s (%s) currently has %s outstanding. Total earned to date is %s with %s already disbursed.",
        emp.displayName(),
        emp.code(),
        outstandingStr,
        earnedStr,
        paidStr);

    // 6. COMPLETED
    trace.add(recordTrace(
        sessionId,
        userMsgId,
        seq++,
        "COMPLETED",
        "OK",
        "Query completed",
        "Grounded financial state verified from PostgreSQL"));

    UUID assistantMsgId = saveMessage(sessionId, "ASSISTANT", answer);

    return new EveDtos.QueryResponse(
        sessionId,
        new EveDtos.MessageView(assistantMsgId, sessionId, "ASSISTANT", answer, Instant.now()),
        trace,
        context,
        "COMPLETED",
        List.of());
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
    List<EveDtos.SessionView> sessions = jdbc.query(
        "SELECT id, title, status, created_at, updated_at FROM eve_sessions WHERE id = ?",
        (rs, rowNum) -> new EveDtos.SessionView(
            (UUID) rs.getObject("id"),
            rs.getString("title"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            List.of()),
        id);

    if (sessions.isEmpty()) {
      throw ApiException.notFound("EVE_SESSION_NOT_FOUND", "Session not found: " + id);
    }

    EveDtos.SessionView s = sessions.get(0);
    List<EveDtos.MessageView> messages = jdbc.query(
        "SELECT id, session_id, role, content, created_at FROM eve_messages WHERE session_id = ? ORDER BY created_at ASC",
        (rs, rowNum) -> new EveDtos.MessageView(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("session_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getTimestamp("created_at").toInstant()),
        id);

    return new EveDtos.SessionView(s.id(), s.title(), s.status(), s.createdAt(), s.updatedAt(), messages);
  }

  @Transactional
  public EveDtos.SessionView createSession(String title) {
    String cleanTitle = title != null && !title.isBlank() ? title.trim() : "Operational Intelligence Session";
    UUID id = createSessionInternal(cleanTitle);
    return getSession(id);
  }

  @Transactional
  public EveDtos.MemoryView remember(EveDtos.MemoryRequest request) {
    if (memoryService == null) {
      throw ApiException.badRequest("EVE_MEMORY_UNAVAILABLE", "Memory service is not available.");
    }
    return memoryService.remember(request, "OPERATOR_EXPLICIT");
  }

  @Transactional(readOnly = true)
  public List<EveDtos.MemoryView> listMemories() {
    return memoryService != null ? memoryService.listMemories() : List.of();
  }

  @Transactional
  public boolean deleteMemory(UUID id) {
    return memoryService != null && memoryService.deleteMemory(id);
  }

  private UUID createSessionInternal(String title) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO eve_sessions (id, title, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', now(), now())",
        id,
        title);
    return id;
  }

  private void ensureSessionExists(UUID sessionId) {
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM eve_sessions WHERE id = ?",
        Integer.class,
        sessionId);
    if (count == null || count == 0) {
      throw ApiException.notFound("EVE_SESSION_NOT_FOUND", "Session not found: " + sessionId);
    }
    jdbc.update("UPDATE eve_sessions SET updated_at = now() WHERE id = ?", sessionId);
  }

  private UUID saveMessage(UUID sessionId, String role, String content) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO eve_messages (id, session_id, role, content, created_at) VALUES (?, ?, ?, ?, now())",
        id,
        sessionId,
        role,
        content);
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
    Instant now = Instant.now();
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
        Timestamp.from(now));
    return new EveDtos.TraceEventView(id, sessionId, messageId, seq, eventType, status, label, detail, now);
  }

  private String summarizeTitle(String prompt) {
    String trimmed = prompt.trim();
    if (trimmed.length() <= 40) {
      return trimmed;
    }
    return trimmed.substring(0, 37) + "...";
  }
}
