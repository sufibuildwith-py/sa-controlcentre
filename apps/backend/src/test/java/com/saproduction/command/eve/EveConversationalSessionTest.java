package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.production.ProductionService;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.work.WorkTaskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EveConversationalSessionTest {

  private EveModelProvider modelProvider;
  private EveRetrievalService retrievalService;
  private EveContextEngine contextEngine;
  private EveKnowledgeService knowledgeService;
  private EveMemoryService memoryService;
  private FinanceReadService financeReads;
  private ProductionService productionService;
  private ProductionRepository productionRepo;
  private ProductionMemberRepository memberRepo;
  private EmployeeService employeeService;
  private WorkTaskService taskService;
  private HeadquartersService headquartersService;
  private EveRetrievalRouter retrievalRouter;
  private JdbcTemplate jdbc;
  private EveService eveService;

  private UUID sessionId;
  private UUID empId;
  private UUID prodId;

  @BeforeEach
  void setUp() {
    modelProvider = new TestModelProvider();
    retrievalService = mock(EveRetrievalService.class);
    contextEngine = new EveContextEngine("Asia/Kolkata");
    knowledgeService = new EveKnowledgeService();
    memoryService = mock(EveMemoryService.class);
    financeReads = mock(FinanceReadService.class);
    productionService = mock(ProductionService.class);
    productionRepo = mock(ProductionRepository.class);
    memberRepo = mock(ProductionMemberRepository.class);
    employeeService = mock(EmployeeService.class);
    taskService = mock(WorkTaskService.class);
    headquartersService = mock(HeadquartersService.class);
    jdbc = mock(JdbcTemplate.class);

    retrievalRouter = new EveRetrievalRouter(
        retrievalService,
        financeReads,
        productionService,
        productionRepo,
        memberRepo,
        employeeService,
        taskService,
        headquartersService,
        memoryService,
        "Asia/Kolkata");

    eveService = new EveService(
        modelProvider,
        retrievalService,
        contextEngine,
        knowledgeService,
        memoryService,
        financeReads,
        retrievalRouter,
        jdbc);

    sessionId = UUID.randomUUID();
    empId = UUID.randomUUID();
    prodId = UUID.randomUUID();

    when(jdbc.queryForObject(contains("FROM eve_sessions WHERE id = ?"), eq(Integer.class), any(UUID.class)))
        .thenReturn(1);
    when(jdbc.query(contains("FROM eve_messages WHERE session_id = ?"), any(RowMapper.class), any(UUID.class)))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("Multi-turn conversation: Royal crew -> member check -> finance check -> equipment check")
  void executesMultiTurnConversationalSessionWithContextCarryForward() {
    EveRetrievalService.Candidate prodCandidate = new EveRetrievalService.Candidate(
        prodId, "PRODUCTION", "Royal Wedding", "PROD-01", "Grand Palace");
    EveRetrievalService.Candidate empCandidate = new EveRetrievalService.Candidate(
        empId, "EMPLOYEE", "Raj Sharma", "SA-01", "Lead Sound Engineer");

    // Setup retrieval mock responses
    when(retrievalService.resolveProduction(anyString()))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prodCandidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));
    when(retrievalService.resolveProduction("Royal"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prodCandidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));
    when(retrievalService.resolveProduction("usme"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prodCandidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));
    when(retrievalService.resolveProduction("uska"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prodCandidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));

    when(retrievalService.resolveEmployee("Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(empCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));
    when(retrievalService.resolveEmployee("Raj Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(empCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Raj Sharma"));

    // Production details
    ProductionService.MemberView m = new ProductionService.MemberView(
        UUID.randomUUID(), empId, "Raj Sharma", "Lead Sound Engineer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "LED Par Cans", "DEMO-HQ-001", new BigDecimal("4"), "pcs", "RESERVED");

    ProductionService.View prodView = new ProductionService.View(
        prodId, "Royal Wedding", "Client A", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(m), 0, List.of(eq), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    // Finance details
    when(financeReads.employee(empId))
        .thenReturn(Map.of(
            "earned", new BigDecimal("100000.00"),
            "paid", new BigDecimal("60000.00"),
            "outstanding", new BigDecimal("40000.00")));

    // -------------------------------------------------------------
    // TURN 1: "Royal mein kaun gaya tha?"
    // -------------------------------------------------------------
    var turn1 = eveService.query(new EveDtos.QueryRequest("Royal mein kaun gaya tha?", sessionId));

    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Royal Wedding has 1 assigned crew member");
    assertThat(turn1.message().content()).contains("Raj Sharma (Lead Sound Engineer)");
    assertThat(turn1.trace()).isNotEmpty();

    // -------------------------------------------------------------
    // TURN 2: "Usme Sharma bhi tha?" (context references Royal)
    // -------------------------------------------------------------
    var turn2 = eveService.query(new EveDtos.QueryRequest("Usme Sharma bhi tha?", sessionId));

    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Yes, Raj Sharma was assigned to Royal Wedding as Lead Sound Engineer.");

    // -------------------------------------------------------------
    // TURN 3: "Uska payment kitna pending hai?" (context references Sharma)
    // -------------------------------------------------------------
    var turn3 = eveService.query(new EveDtos.QueryRequest("Uska payment kitna pending hai?", sessionId));

    assertThat(turn3.status()).isEqualTo("COMPLETED");
    assertThat(turn3.message().content()).contains("Raj Sharma (SA-01) currently has");
    assertThat(turn3.message().content()).contains("40,000.00 outstanding");

    // -------------------------------------------------------------
    // TURN 4: "Aur uska equipment?" (context references Royal)
    // -------------------------------------------------------------
    var turn4 = eveService.query(new EveDtos.QueryRequest("Aur uska equipment?", sessionId));

    assertThat(turn4.status()).isEqualTo("COMPLETED");
    assertThat(turn4.message().content()).contains("Royal Wedding has 1 reserved equipment item: 4x LED Par Cans (DEMO-HQ-001)");

    // Assert that NO database write operations were performed on business tables
    verify(jdbc, never()).update(startsWith("UPDATE employees"));
    verify(jdbc, never()).update(startsWith("UPDATE finance"));
    verify(jdbc, never()).update(startsWith("UPDATE productions"));
  }
}
