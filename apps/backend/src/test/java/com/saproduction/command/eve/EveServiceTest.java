package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EveServiceTest {

  private EveModelProvider modelProvider;
  private EveRetrievalService retrievalService;
  private EveContextEngine contextEngine;
  private EveKnowledgeService knowledgeService;
  private EveMemoryService memoryService;
  private FinanceReadService financeReadService;
  private JdbcTemplate jdbc;
  private EveService service;

  private final UUID employeeId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    modelProvider = mock(EveModelProvider.class);
    retrievalService = mock(EveRetrievalService.class);
    contextEngine = new EveContextEngine("Asia/Kolkata");
    knowledgeService = new EveKnowledgeService();
    memoryService = mock(EveMemoryService.class);
    financeReadService = mock(FinanceReadService.class);
    jdbc = mock(JdbcTemplate.class);

    service = new EveService(
        modelProvider,
        retrievalService,
        contextEngine,
        knowledgeService,
        memoryService,
        financeReadService,
        jdbc);

    // Mock session existence check
    when(jdbc.queryForObject(contains("FROM eve_sessions WHERE id = ?"), eq(Integer.class), any(UUID.class)))
        .thenReturn(1);
  }

  @Test
  void executesGroundedReadVerticalSliceForSharmaQuery() {
    UUID sessionId = UUID.randomUUID();
    String prompt = "How much does Sharma still need?";

    // 1. Model interpretation
    when(modelProvider.interpret(any()))
        .thenReturn(EveModelProvider.EveInterpretation.of(
            EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "Sharma"));

    // 2. Retrieval resolution
    EveRetrievalService.Candidate candidate = new EveRetrievalService.Candidate(
        employeeId, "EMPLOYEE", "Raj Sharma", "SA-01", "Lead Sound Engineer");
    when(retrievalService.resolveEmployee("Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(
            candidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));

    // 3. Finance read
    when(financeReadService.employee(employeeId))
        .thenReturn(Map.of(
            "earned", new BigDecimal("120000.00"),
            "paid", new BigDecimal("80000.00"),
            "outstanding", new BigDecimal("40000.00")));

    // Mock memory lookup
    when(memoryService.recall("Sharma")).thenReturn(Optional.of(
        new EveDtos.MemoryView(UUID.randomUUID(), "VOCABULARY", "Sharma", "EMPLOYEE", employeeId, "Raj Sharma", 1.0, "OPERATOR", Instant.now(), Instant.now())));

    EveDtos.QueryResponse response = service.query(new EveDtos.QueryRequest(prompt, sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.sessionId()).isEqualTo(sessionId);
    assertThat(response.message().role()).isEqualTo("ASSISTANT");
    assertThat(response.message().content()).contains("Raj Sharma (SA-01)");
    assertThat(response.message().content()).contains("outstanding");

    // Verify trace sequence and truthful events
    assertThat(response.trace()).hasSize(7);
    assertThat(response.trace().get(0).eventType()).isEqualTo("STARTED");
    assertThat(response.trace().get(1).eventType()).isEqualTo("INTERPRETING");
    assertThat(response.trace().get(2).eventType()).isEqualTo("RESOLVING");
    assertThat(response.trace().get(3).eventType()).isEqualTo("ROUTING");
    assertThat(response.trace().get(4).eventType()).isEqualTo("RETRIEVING");
    assertThat(response.trace().get(5).eventType()).isEqualTo("ASSEMBLING_CONTEXT");
    assertThat(response.trace().get(6).eventType()).isEqualTo("COMPLETED");

    // Verify trace detail contains safe operational facts, never hidden reasoning
    for (EveDtos.TraceEventView t : response.trace()) {
      assertThat(t.detail()).doesNotContain("chain-of-thought");
      assertThat(t.detail()).doesNotContain("prompt");
      assertThat(t.detail()).doesNotContain("password");
    }

    // Verify context contains referenced entity, evidence, knowledge, and memory hints
    assertThat(response.context()).isNotNull();
    assertThat(response.context().referencedEntities()).hasSize(1);
    assertThat(response.context().referencedEntities().get(0).name()).isEqualTo("Raj Sharma");
    assertThat(response.context().evidence()).isNotEmpty();
    assertThat(response.context().knowledgeSnippets()).isNotEmpty();
    assertThat(response.context().memoryHints()).isNotEmpty();

    // Verify database inserts occurred for messages and traces
    verify(jdbc, atLeast(2)).update(contains("INSERT INTO eve_messages"), any(), any(), any(), any(), any());
    verify(jdbc, times(7)).update(contains("INSERT INTO eve_trace_events"), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void blocksAndReturnsCandidatesWhenAmbiguityDetected() {
    UUID sessionId = UUID.randomUUID();
    String prompt = "Sharma";

    when(modelProvider.interpret(any()))
        .thenReturn(EveModelProvider.EveInterpretation.of(
            EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "Sharma"));

    List<EveRetrievalService.Candidate> candidates = List.of(
        new EveRetrievalService.Candidate(UUID.randomUUID(), "EMPLOYEE", "Raj Sharma", "SA-01", "Sound"),
        new EveRetrievalService.Candidate(UUID.randomUUID(), "EMPLOYEE", "Amit Sharma", "SA-02", "Lighting"));

    when(retrievalService.resolveEmployee("Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.ambiguous(candidates, "Sharma"));

    EveDtos.QueryResponse response = service.query(new EveDtos.QueryRequest(prompt, sessionId));

    assertThat(response.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(response.candidates()).hasSize(2);
    assertThat(response.message().content()).contains("multiple matching team members");

    // Verify trace ends with BLOCKED
    EveDtos.TraceEventView lastTrace = response.trace().get(response.trace().size() - 1);
    assertThat(lastTrace.eventType()).isEqualTo("BLOCKED");
    assertThat(lastTrace.label()).isEqualTo("Ambiguity detected");

    // Verify no finance call was made
    verifyNoInteractions(financeReadService);
  }

  @Test
  void returnsNotFoundWhenEntityDoesNotExist() {
    UUID sessionId = UUID.randomUUID();
    String prompt = "How much does UnknownPerson need?";

    when(modelProvider.interpret(any()))
        .thenReturn(EveModelProvider.EveInterpretation.of(
            EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "UnknownPerson"));

    when(retrievalService.resolveEmployee("UnknownPerson"))
        .thenReturn(EveRetrievalService.ResolutionResult.notFound("UnknownPerson"));

    EveDtos.QueryResponse response = service.query(new EveDtos.QueryRequest(prompt, sessionId));

    assertThat(response.status()).isEqualTo("NOT_FOUND");
    assertThat(response.message().content()).contains("could not find any active records matching \"UnknownPerson\"");

    verifyNoInteractions(financeReadService);
  }

  @Test
  void rejectsBlankPrompt() {
    assertThatThrownBy(() -> service.query(new EveDtos.QueryRequest("", null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Prompt cannot be blank");
  }
}
