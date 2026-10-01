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
import com.saproduction.command.work.WorkTask;
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

  @Test
  @DisplayName("5-Turn conversational flow: hey -> MIPS client -> uska equipment -> uska task -> thanks")
  void executesFiveTurnConversationalFlow_withGreeting_andAntecedentCarryForward() {
    UUID mipsId = UUID.randomUUID();
    var mipsCandidate = new EveRetrievalService.Candidate(
        mipsId, "PRODUCTION", "Cultural Event MIPS", "MIPS-01", "MIPS Venue");

    when(retrievalService.resolveProduction("Cultural Event MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(mipsCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Event MIPS"));

    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "Line Array Speakers", "SPK-01", new BigDecimal("6"), "pcs", "RESERVED");
    ProductionService.View mipsView = new ProductionService.View(
        mipsId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival", LocalDate.now(), null, null, "MIPS Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(), 0, List.of(eq), Instant.now(), Instant.now());
    when(productionService.get(mipsId)).thenReturn(mipsView);

    WorkTaskService.View task = new WorkTaskService.View(
        UUID.randomUUID(), mipsId, "Cultural Event MIPS", null, "Stage sound check", "Perform EQ balance",
        UUID.randomUUID(), "Rehan Ali", WorkTask.Status.IN_PROGRESS, WorkTask.Priority.HIGH, null, null, null, 50, false, List.of(), Instant.now(), Instant.now());
    when(taskService.list(null, mipsId, null, null, null, null, null))
        .thenReturn(List.of(task));

    UUID flowSessionId = UUID.randomUUID();

    // Turn 1: "hey" -> EVE greets without invoking retrieval
    var turn1 = eveService.query(new EveDtos.QueryRequest("hey", flowSessionId));
    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Hello! I am EVE");

    // Turn 2: "cultural event MIPS ka client kon hai?" -> Answers Nandhini srivastava, sets MIPS antecedent
    var turn2 = eveService.query(new EveDtos.QueryRequest("cultural event MIPS ka client kon hai?", flowSessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Nandhini srivastava");

    // Turn 3: "Aur uska equipment?" -> Resolves MIPS equipment via antecedent
    var turn3 = eveService.query(new EveDtos.QueryRequest("Aur uska equipment?", flowSessionId));
    assertThat(turn3.status()).isEqualTo("COMPLETED");
    assertThat(turn3.message().content()).contains("Cultural Event MIPS has 1 reserved equipment item: 6x Line Array Speakers");

    // Turn 4: "uska kaunsa task open hai?" -> Resolves open tasks for MIPS via antecedent
    var turn4 = eveService.query(new EveDtos.QueryRequest("uska kaunsa task open hai?", flowSessionId));
    assertThat(turn4.status()).isEqualTo("COMPLETED");
    assertThat(turn4.message().content()).contains("Stage sound check");
    assertThat(turn4.message().content()).doesNotContain("25 open tasks");

    // Turn 5: "thanks" -> Courteous signoff without retrieval
    var turn5 = eveService.query(new EveDtos.QueryRequest("thanks", flowSessionId));
    assertThat(turn5.status()).isEqualTo("COMPLETED");
    assertThat(turn5.message().content()).contains("You're welcome!");

    // STRICT INVARIANT: Pronoun tokens "uska" / "usme" must NEVER be passed as canonical search queries
    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
    verify(retrievalService, never()).resolveEmployee(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Scenario B: MIPS client -> 'usme kaunsa task open hai?' resolves production tasks")
  void executesScenarioB_withUsmeTaskQuery() {
    UUID mipsId = UUID.randomUUID();
    var mipsCandidate = new EveRetrievalService.Candidate(
        mipsId, "PRODUCTION", "Cultural Event MIPS", "MIPS-01", "MIPS Venue");

    when(retrievalService.resolveProduction("Cultural Event MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(mipsCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Event MIPS"));

    ProductionService.View mipsView = new ProductionService.View(
        mipsId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival", LocalDate.now(), null, null, "MIPS Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(mipsId)).thenReturn(mipsView);

    WorkTaskService.View task = new WorkTaskService.View(
        UUID.randomUUID(), mipsId, "Cultural Event MIPS", null, "Stage sound check", "Perform EQ balance",
        UUID.randomUUID(), "Rehan Ali", WorkTask.Status.IN_PROGRESS, WorkTask.Priority.HIGH, null, null, null, 50, false, List.of(), Instant.now(), Instant.now());
    when(taskService.list(null, mipsId, null, null, null, null, null))
        .thenReturn(List.of(task));

    UUID sessionId = UUID.randomUUID();

    var turn1 = eveService.query(new EveDtos.QueryRequest("Cultural Event MIPS ka client kaun hai?", sessionId));
    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Nandhini srivastava");

    var turn2 = eveService.query(new EveDtos.QueryRequest("usme kaunsa task open hai?", sessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Stage sound check");
    assertThat(turn2.message().content()).doesNotContain("open tasks in the system");

    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Scenario D: Employee antecedent carry-forward to production tasks ('Kabir ka production kaunsa hai?' -> 'usme kaunsa task open hai?')")
  void executesScenarioD_withEmployeeAntecedentCarryForward_toProductionTasks() {
    UUID kabirId = UUID.randomUUID();
    UUID mipsId = UUID.randomUUID();

    var kabirCandidate = new EveRetrievalService.Candidate(
        kabirId, "EMPLOYEE", "Kabir Singh", "SA-006", "Photographer");
    var mipsCandidate = new EveRetrievalService.Candidate(
        mipsId, "PRODUCTION", "Cultural Event MIPS", "MIPS-01", "MIPS Venue");

    when(retrievalService.resolveEmployee("Kabir"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir"));

    ProductionService.MemberView member = new ProductionService.MemberView(
        UUID.randomUUID(), kabirId, "Kabir Singh", "Photographer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.View mipsView = new ProductionService.View(
        mipsId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival", LocalDate.of(2026, 3, 31), null, null, "MIPS Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.list(null, null, null, null, null))
        .thenReturn(List.of(mipsView));
    when(productionService.get(mipsId)).thenReturn(mipsView);

    WorkTaskService.View task = new WorkTaskService.View(
        UUID.randomUUID(), mipsId, "Cultural Event MIPS", null, "Conduct a Meeting with the Board", "Coordinate board logistics",
        kabirId, "Kabir Singh", WorkTask.Status.TODO, WorkTask.Priority.HIGH, null, null, null, 0, false, List.of(), Instant.now(), Instant.now());
    when(taskService.list(null, mipsId, null, null, null, null, null))
        .thenReturn(List.of(task));

    UUID sessionId = UUID.randomUUID();

    // Turn 1: "Kabir ka production kaunsa hai?"
    var turn1 = eveService.query(new EveDtos.QueryRequest("Kabir ka production kaunsa hai?", sessionId));
    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Kabir Singh is currently assigned to Cultural Event MIPS");

    // Turn 2: "usme kaunsa task open hai?"
    var turn2 = eveService.query(new EveDtos.QueryRequest("usme kaunsa task open hai?", sessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Conduct a Meeting with the Board");
    assertThat(turn2.message().content()).doesNotContain("open tasks in the system");

    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Fresh session with pronoun task query: 'uska kaunsa task open hai?' without antecedent returns CLARIFICATION_REQUIRED")
  void freshSession_withPronounTaskQuery_returnsClarificationRequired() {
    UUID freshSessionId = UUID.randomUUID();

    var response = eveService.query(new EveDtos.QueryRequest("uska kaunsa task open hai?", freshSessionId));

    assertThat(response.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(response.message().content()).contains("Kis production ya event");
    assertThat(response.message().content()).doesNotContain("open tasks in the system");

    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Fresh session with pronoun query: 'Aur uska equipment?' without antecedent returns CLARIFICATION_REQUIRED")
  void freshSession_withPronounQuery_returnsClarificationRequired_withoutDatabaseLookup() {
    UUID freshSessionId = UUID.randomUUID();

    var response = eveService.query(new EveDtos.QueryRequest("Aur uska equipment?", freshSessionId));

    assertThat(response.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(response.message().content()).contains("Kis production ya event");
    assertThat(response.message().content()).doesNotContain("matching \"uska\"");

    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
    verify(retrievalService, never()).resolveEmployee(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Context Intelligence: Fresh session 'Aur uska equipment?' -> 'Mips wala event' resolves equipment, then 'usme kitne log' resolves crew")
  void executesContextClarificationFollowUp_withEntitySearchPhraseCleaning() {
    UUID mipsId = UUID.randomUUID();
    var mipsCandidate = new EveRetrievalService.Candidate(
        mipsId, "PRODUCTION", "Cultural Event MIPS", "MIPS-01", "MIPS Venue");

    when(retrievalService.resolveProduction("Mips"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(mipsCandidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Mips"));
    when(retrievalService.resolveProduction("Cultural Event MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(mipsCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Event MIPS"));

    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "Meyer Sound PA System", "HQ-PA-01", new BigDecimal("2"), "sets", "RESERVED");
    ProductionService.MemberView member1 = new ProductionService.MemberView(
        UUID.randomUUID(), UUID.randomUUID(), "Rehan Ali", "FOH Engineer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.MemberView member2 = new ProductionService.MemberView(
        UUID.randomUUID(), UUID.randomUUID(), "Kabir Singh", "Photographer", true, ProductionMember.Status.CONFIRMED, false, null);

    ProductionService.View mipsView = new ProductionService.View(
        mipsId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival", LocalDate.of(2026, 3, 31), null, null, "MIPS Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member1, member2), 0, List.of(eq), Instant.now(), Instant.now());
    when(productionService.get(mipsId)).thenReturn(mipsView);

    UUID testSessionId = UUID.randomUUID();

    // Turn 1: "Aur uska equipment?" without antecedent -> returns CLARIFICATION_REQUIRED
    var turn1 = eveService.query(new EveDtos.QueryRequest("Aur uska equipment?", testSessionId));
    assertThat(turn1.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(turn1.message().content()).contains("Kis production ya event");

    // Turn 2: "Mips wala event" -> resolves "Mips" to Cultural Event MIPS and returns its equipment
    var turn2 = eveService.query(new EveDtos.QueryRequest("Mips wala event", testSessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Cultural Event MIPS");
    assertThat(turn2.message().content()).contains("Meyer Sound PA System");

    // Turn 3: "aur usme kitne log kaam kar rahe hain?" -> continues focus on Cultural Event MIPS and returns crew
    var turn3 = eveService.query(new EveDtos.QueryRequest("aur usme kitne log kaam kar rahe hain?", testSessionId));
    assertThat(turn3.status()).isEqualTo("COMPLETED");
    assertThat(turn3.message().content()).contains("Cultural Event MIPS has 2 assigned crew members");
    assertThat(turn3.message().content()).contains("Rehan Ali");
    assertThat(turn3.message().content()).contains("Kabir Singh");

    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  @DisplayName("Context Intelligence: Multi-turn topic switching MIPS -> Kabir Singh finance -> Kabir assigned production -> crew")
  void executesMultiTurnTopicSwitching_seamlessly() {
    UUID kabirId = UUID.randomUUID();
    UUID mipsId = UUID.randomUUID();

    var kabirCandidate = new EveRetrievalService.Candidate(
        kabirId, "EMPLOYEE", "Kabir Singh", "SA-006", "Photographer");
    var mipsCandidate = new EveRetrievalService.Candidate(
        mipsId, "PRODUCTION", "Cultural Event MIPS", "MIPS-01", "MIPS Venue");

    when(retrievalService.resolveProduction("Cultural Event MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(mipsCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Event MIPS"));
    when(retrievalService.resolveEmployee("Kabir Singh"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir Singh"));
    when(retrievalService.resolveEmployee("Kabir"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir"));

    ProductionService.MemberView member = new ProductionService.MemberView(
        UUID.randomUUID(), kabirId, "Kabir Singh", "Photographer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.View mipsView = new ProductionService.View(
        mipsId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival", LocalDate.of(2026, 3, 31), null, null, "MIPS Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(mipsId)).thenReturn(mipsView);
    when(productionService.list(null, null, null, null, null)).thenReturn(List.of(mipsView));

    when(financeReads.employee(kabirId))
        .thenReturn(Map.of(
            "earned", new BigDecimal("50000.00"),
            "paid", new BigDecimal("20000.00"),
            "outstanding", new BigDecimal("30000.00")));

    UUID topicSessionId = UUID.randomUUID();

    // Turn 1: Ask about MIPS client
    var turn1 = eveService.query(new EveDtos.QueryRequest("Cultural Event MIPS ka client kaun hai?", topicSessionId));
    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Nandhini srivastava");

    // Turn 2: Explicit topic switch to Kabir Singh finance
    var turn2 = eveService.query(new EveDtos.QueryRequest("Achha Kabir Singh ko kitna dena hai?", topicSessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Kabir Singh");
    assertThat(turn2.message().content()).contains("30,000");

    // Turn 3: Contextual switch to Kabir's production
    var turn3 = eveService.query(new EveDtos.QueryRequest("Uska production kaunsa hai?", topicSessionId));
    assertThat(turn3.status()).isEqualTo("COMPLETED");
    assertThat(turn3.message().content()).contains("Cultural Event MIPS");

    // Turn 4: Contextual inquiry into crew of that production
    var turn4 = eveService.query(new EveDtos.QueryRequest("Usme kaun kaam kar raha hai?", topicSessionId));
    assertThat(turn4.status()).isEqualTo("COMPLETED");
    assertThat(turn4.message().content()).contains("Cultural Event MIPS has 1 assigned crew member");
    assertThat(turn4.message().content()).contains("Kabir Singh");
  }

  @Test
  @DisplayName("Context Intelligence: Ambiguous candidate disambiguation ('LED wale event' -> candidate selection 'haan second wala')")
  void executesCandidateDisambiguationSelection() {
    UUID prod1Id = UUID.randomUUID();
    UUID prod2Id = UUID.randomUUID();

    var cand1 = new EveRetrievalService.Candidate(prod1Id, "PRODUCTION", "LED Fashion Gala", "PROD-LED-1", "Hotel Hyatt");
    var cand2 = new EveRetrievalService.Candidate(prod2Id, "PRODUCTION", "LED Rock Concert", "PROD-LED-2", "Indira Stadium");

    when(retrievalService.resolveProduction("LED"))
        .thenReturn(EveRetrievalService.ResolutionResult.ambiguous(List.of(cand1, cand2), "LED"));
    when(retrievalService.selectFromCandidates(anyList(), anyString()))
        .thenReturn(Optional.of(cand2));

    ProductionService.View prod2View = new ProductionService.View(
        prod2Id, "LED Rock Concert", "Star Entertainment", null, LocalDate.now(), null, null, "Indira Stadium", null,
        Production.Status.PRODUCTION, Production.Priority.HIGH, 75, null, List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prod2Id)).thenReturn(prod2View);

    UUID ambigSessionId = UUID.randomUUID();

    // Turn 1: Ambiguous query returns CLARIFICATION_REQUIRED with candidates
    var turn1 = eveService.query(new EveDtos.QueryRequest("LED wale event ke details", ambigSessionId));
    assertThat(turn1.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(turn1.candidates()).hasSize(2);

    // Turn 2: Disambiguation selection "haan second wala"
    var turn2 = eveService.query(new EveDtos.QueryRequest("haan second wala", ambigSessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("LED Rock Concert");
  }
}
