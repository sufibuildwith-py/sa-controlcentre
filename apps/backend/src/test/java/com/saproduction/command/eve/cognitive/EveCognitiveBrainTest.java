package com.saproduction.command.eve.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.EveDtos;
import com.saproduction.command.eve.EveModelProvider;
import com.saproduction.command.eve.EveRetrievalRouter;
import com.saproduction.command.eve.EveRetrievalService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveCognitiveBrainTest {

  private ZoneId kolkataZone;
  private Clock testClock;
  private EveTemporalReasoningService temporalService;
  private EveCognitiveToolRegistry toolRegistry;
  private EveRetrievalService retrievalService;
  private ProductionRepository productionRepo;
  private ProductionMemberRepository productionMemberRepo;
  private WorkTaskRepository workTaskRepo;
  private EmployeeRepository employeeRepo;
  private EmployeeService employeeService;
  private FinanceReadService financeReadService;
  private EveModelProvider modelProvider;
  private EveCognitiveRuntime runtime;

  @BeforeEach
  void setUp() {
    kolkataZone = ZoneId.of("Asia/Kolkata");
    // Anchor to Thursday, 2026-10-01
    Instant anchor = LocalDate.of(2026, 10, 1)
        .atStartOfDay(kolkataZone)
        .toInstant();
    testClock = Clock.fixed(anchor, kolkataZone);
    temporalService = new EveTemporalReasoningService(kolkataZone, testClock);

    productionRepo = mock(ProductionRepository.class);
    productionMemberRepo = mock(ProductionMemberRepository.class);
    workTaskRepo = mock(WorkTaskRepository.class);
    employeeRepo = mock(EmployeeRepository.class);
    employeeService = mock(EmployeeService.class);
    financeReadService = mock(FinanceReadService.class);
    retrievalService = mock(EveRetrievalService.class);
    modelProvider = mock(EveModelProvider.class);

    toolRegistry = new EveCognitiveToolRegistry(
        productionRepo,
        productionMemberRepo,
        workTaskRepo,
        employeeRepo,
        employeeService,
        financeReadService,
        null,
        retrievalService,
        temporalService);

    runtime = new EveCognitiveRuntime(
        toolRegistry,
        temporalService,
        retrievalService,
        productionRepo,
        productionMemberRepo,
        workTaskRepo,
        employeeRepo);
  }

  private Production createProduction(String code, String title, String venue, LocalDate date, Production.Status status) {
    Production p = new Production();
    p.id = UUID.randomUUID();
    p.title = title;
    p.venueName = venue;
    p.eventDate = date;
    p.status = status;
    p.clientName = title + " Client";
    return p;
  }

  @Test
  @DisplayName("Temporal Reasoning: Accurately resolves natural date expressions in Asia/Kolkata")
  void testTemporalResolutions() {
    // Current date is 2026-10-01 (Thursday)
    EveTemporalReasoningService.DateRange today = temporalService.resolveDateRange("today").orElseThrow();
    assertThat(today.start()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(today.end()).isEqualTo(LocalDate.of(2026, 10, 1));

    EveTemporalReasoningService.DateRange tomorrow = temporalService.resolveDateRange("tomorrow").orElseThrow();
    assertThat(tomorrow.start()).isEqualTo(LocalDate.of(2026, 10, 2));

    EveTemporalReasoningService.DateRange nextWeek = temporalService.resolveDateRange("next week kitne events hai").orElseThrow();
    // Next week Monday to Sunday: 2026-10-05 to 2026-10-11
    assertThat(nextWeek.start()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(nextWeek.end()).isEqualTo(LocalDate.of(2026, 10, 11));

    EveTemporalReasoningService.DateRange thisWeekend = temporalService.resolveDateRange("this weekend").orElseThrow();
    // This Saturday to Sunday: 2026-10-03 to 2026-10-04
    assertThat(thisWeekend.start()).isEqualTo(LocalDate.of(2026, 10, 3));
    assertThat(thisWeekend.end()).isEqualTo(LocalDate.of(2026, 10, 4));
  }

  @Test
  @DisplayName("Acceptance Test 1: 'next week kitne events hai' counts authoritative productions")
  void testAcceptanceTest1_CountProductionsNextWeek() {
    Production p1 = createProduction("PROD-01", "Grand Hyatt Gala", "Grand Hyatt", LocalDate.of(2026, 10, 6), Production.Status.PRODUCTION);
    Production p2 = createProduction("PROD-02", "Mehta Sangeet", "JW Marriott", LocalDate.of(2026, 10, 8), Production.Status.PLANNING);
    Production p3 = createProduction("PROD-03", "Tech Summit Keynote", "CIDCO", LocalDate.of(2026, 10, 10), Production.Status.PRODUCTION);
    Production p4 = createProduction("PROD-04", "Past Concert", "Dome", LocalDate.of(2026, 9, 20), Production.Status.DELIVERED);

    when(productionRepo.findAll()).thenReturn(List.of(p1, p2, p3, p4));

    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "next week kitne events hai",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("3 events scheduled for next week");
    assertThat(result.answer()).contains("Grand Hyatt Gala").contains("Mehta Sangeet").contains("Tech Summit Keynote");
    assertThat(result.reasoningSteps()).isNotEmpty();

    // Verify operational reasoning trace stages
    assertThat(result.reasoningSteps().get(0).stage()).isEqualTo("UNDERSTANDING");
    assertThat(result.reasoningSteps().get(1).stage()).isEqualTo("GOAL_INTERPRETATION");
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "TEMPORAL_GROUNDING".equals(s.stage()))).isTrue();
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "ANALYTICAL_OPERATION".equals(s.stage()))).isTrue();
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "VERIFICATION".equals(s.stage()))).isTrue();
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "ANSWER".equals(s.stage()))).isTrue();
  }

  @Test
  @DisplayName("Acceptance Test 2: 'next week ke events jisme Kabir hai aur task pending hai' executes multi-domain cross join")
  void testAcceptanceTest2_MultiConstraintFilter() {
    UUID kabirId = UUID.randomUUID();
    EveRetrievalService.Candidate kabirCandidate = new EveRetrievalService.Candidate(
        kabirId, "EMPLOYEE", "Kabir Khan", "SA-02", "Audio Technician");
    when(retrievalService.resolveEmployee("Kabir"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(
            kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir"));

    Production prod1 = createProduction("PROD-10", "Royal Wedding", "Umaid Bhawan", LocalDate.of(2026, 10, 7), Production.Status.PRODUCTION);
    Production prod2 = createProduction("PROD-11", "City Marathon Concert", "Marine Drive", LocalDate.of(2026, 10, 9), Production.Status.PLANNING);

    when(productionRepo.findAll()).thenReturn(List.of(prod1, prod2));

    // Kabir assigned to prod1, but not prod2
    when(productionMemberRepo.existsByProductionIdAndEmployeeId(prod1.id, kabirId)).thenReturn(true);
    when(productionMemberRepo.existsByProductionIdAndEmployeeId(prod2.id, kabirId)).thenReturn(false);

    // prod1 has pending tasks
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(prod1.id), any())).thenReturn(2L);
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(prod2.id), any())).thenReturn(0L);

    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "next week ke events jisme Kabir hai aur task pending hai",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Royal Wedding");
    assertThat(result.answer()).doesNotContain("City Marathon Concert");
    assertThat(result.reasoningSteps()).isNotEmpty();
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "FILTER".equals(s.stage()))).isTrue();
  }

  @Test
  @DisplayName("Acceptance Test 3: 'which production has the most pending tasks?' compares task ledgers")
  void testAcceptanceTest3_ComparePendingTasks() {
    Production p1 = createProduction("PROD-20", "Mega Arena Tour", "DY Patil", LocalDate.of(2026, 10, 15), Production.Status.PRODUCTION);
    Production p2 = createProduction("PROD-21", "Club Night", "AntiSocial", LocalDate.of(2026, 10, 16), Production.Status.PLANNING);

    when(productionRepo.findAll()).thenReturn(List.of(p1, p2));
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p1.id), any())).thenReturn(7L);
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(p2.id), any())).thenReturn(2L);

    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "which production has the most pending tasks?",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Mega Arena Tour");
    assertThat(result.answer()).contains("7 open tasks");
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "ANALYTICAL_OPERATION".equals(s.stage()))).isTrue();
  }

  @Test
  @DisplayName("Acceptance Test 4: 'Sharma wedding me kitne log kaam kar rahe hain?' counts crew accurately")
  void testAcceptanceTest4_CrewCountForSharmaWedding() {
    UUID prodId = UUID.randomUUID();
    EveRetrievalService.Candidate prodCandidate = new EveRetrievalService.Candidate(
        prodId, "PRODUCTION", "Sharma Wedding", "PROD-SHARMA", "Wedding Extravaganza");

    when(retrievalService.resolveProduction(any()))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(
            prodCandidate, EveRetrievalService.MatchMethod.SEMANTIC_MATCH, "Sharma wedding"));

    Production sharmaProd = createProduction("PROD-SHARMA", "Sharma Wedding", "Taj Lands End", LocalDate.of(2026, 10, 12), Production.Status.PRODUCTION);
    sharmaProd.id = prodId;
    when(productionRepo.findById(prodId)).thenReturn(Optional.of(sharmaProd));

    ProductionMember m1 = new ProductionMember();
    m1.id = UUID.randomUUID();
    m1.productionId = prodId;
    m1.employeeId = UUID.randomUUID();
    m1.productionRole = "Lead Sound";
    m1.assignmentStatus = ProductionMember.Status.CONFIRMED;

    ProductionMember m2 = new ProductionMember();
    m2.id = UUID.randomUUID();
    m2.productionId = prodId;
    m2.employeeId = UUID.randomUUID();
    m2.productionRole = "Lighting Tech";
    m2.assignmentStatus = ProductionMember.Status.CONFIRMED;

    ProductionMember m3 = new ProductionMember();
    m3.id = UUID.randomUUID();
    m3.productionId = prodId;
    m3.employeeId = UUID.randomUUID();
    m3.productionRole = "Stage Hand";
    m3.assignmentStatus = ProductionMember.Status.CONFIRMED;

    when(productionMemberRepo.findAllByProductionIdOrderByCreatedAt(prodId))
        .thenReturn(List.of(m1, m2, m3));

    Employee emp = new Employee();
    emp.id = UUID.randomUUID();
    emp.employeeCode = "EMP-1";
    emp.displayName = "Azeem Khan";
    emp.firstName = "Azeem";
    emp.lastName = "Khan";
    emp.phone = "+919876543210";
    emp.roleTitle = "Director";
    emp.department = "Management";
    emp.status = Employee.Status.ACTIVE;
    when(employeeRepo.findById(any())).thenReturn(Optional.of(emp));

    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "Sharma wedding me kitne log kaam kar rahe hain?",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("3 crew members assigned to Sharma Wedding");
    assertThat(result.reasoningSteps().stream().anyMatch(s -> "ANALYTICAL_OPERATION".equals(s.stage()))).isTrue();
  }

  @Test
  @DisplayName("Acceptance Test 5: 'which productions still owe us money?' queries receivables strictly read-only")
  void testAcceptanceTest5_ReceivablesOwed() {
    Production p1 = createProduction("PROD-30", "Oberoi Banquet", "The Oberoi", LocalDate.of(2026, 10, 18), Production.Status.DELIVERED);

    when(productionRepo.findAll()).thenReturn(List.of(p1));
    when(financeReadService.productions(any(Integer.class), any(Integer.class), any())).thenReturn(Map.of(
        "items", List.of(Map.of(
            "id", p1.id,
            "title", "Oberoi Banquet",
            "contracted", new BigDecimal("150000.00"),
            "received", new BigDecimal("100000.00")
        ))
    ));

    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "which productions still owe us money?",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Oberoi Banquet");
    assertThat(result.answer()).contains("50000");
    assertThat(result.reasoningSteps().stream().anyMatch(s -> s.summary().contains("receivables"))).isTrue();
  }

  @Test
  @DisplayName("Bounded Execution: Hard limit prevents runaway loops")
  void testBoundedExecutionSafety() {
    EveCognitiveRuntime.CognitiveResult result = runtime.execute(
        "list productions",
        UUID.randomUUID(),
        "",
        null,
        modelProvider);

    assertThat(result.reasoningSteps().size()).isLessThanOrEqualTo(EveCognitiveRuntime.MAX_REASONING_STEPS);
  }
}
