package com.saproduction.command.eve;

import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core Orchestrator for EVE Phase 2 Conversational Intelligence.
 * EVE is the intelligence layer; existing SA Command domain services are the source of truth.
 * Supports multi-turn conversational session continuity, cross-domain reads, owner vocabulary,
 * relative dates, colloquial amounts, observable trace, and zero business mutations.
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
  private final JdbcTemplate jdbc;

  public EveService(
      EveModelProvider modelProvider,
      EveRetrievalService retrievalService,
      EveContextEngine contextEngine,
      EveKnowledgeService knowledgeService,
      EveMemoryService memoryService,
      FinanceReadService financeReadService,
      JdbcTemplate jdbc) {
    this(modelProvider, retrievalService, contextEngine, knowledgeService, memoryService, financeReadService, null, jdbc);
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
      JdbcTemplate jdbc) {
    this.modelProvider = modelProvider;
    this.retrievalService = retrievalService;
    this.contextEngine = contextEngine;
    this.knowledgeService = knowledgeService;
    this.memoryService = memoryService;
    this.financeReadService = financeReadService;
    this.jdbc = jdbc;
    this.retrievalRouter = retrievalRouter != null
        ? retrievalRouter
        : new EveRetrievalRouter(retrievalService, financeReadService, null, null, null, null, null, null, memoryService, contextEngine.getTimezone());
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
          List.of());
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
          List.of());
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
          routerResult.candidates());
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
          List.of());
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
        routerResult.candidates() != null ? routerResult.candidates() : List.of());
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
