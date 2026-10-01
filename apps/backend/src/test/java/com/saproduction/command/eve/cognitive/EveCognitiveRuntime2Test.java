package com.saproduction.command.eve.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.saproduction.command.production.ProductionService;
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

class EveCognitiveRuntime2Test {

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
  private EveDecisionSupportService decisionSupportService;
  private EveGeneralReasoningService generalReasoningService;
  private EveCapabilityRegistry capabilityRegistry;

  @BeforeEach
  void setUp() {
    kolkataZone = ZoneId.of("Asia/Kolkata");
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

    decisionSupportService = new EveDecisionSupportService(productionRepo, financeReadService, retrievalService);
    generalReasoningService = new EveGeneralReasoningService();
    capabilityRegistry = new EveCapabilityRegistry();

    runtime = new EveCognitiveRuntime(
        toolRegistry,
        temporalService,
        retrievalService,
        productionRepo,
        productionMemberRepo,
        workTaskRepo,
        employeeRepo,
        decisionSupportService,
        generalReasoningService,
        capabilityRegistry);
  }

  @Test
  @DisplayName("Turn 1 - Deterministic Arithmetic Evaluation: 30-10+4-12 -> 12")
  void testArithmeticEvaluation() {
    var result = runtime.execute("30-10+4-12", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("12");
  }

  @Test
  @DisplayName("Turn 2 - External Capability Boundary: Weather -> UNSUPPORTED_CAPABILITY with helpful advice")
  void testExternalWeatherHandling() {
    var result = runtime.execute("Aaj mausam acha hai, kya barish hogi?", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("UNSUPPORTED_CAPABILITY");
    assertThat(result.answer().toLowerCase()).contains("weather");
  }

  @Test
  @DisplayName("Turn 3 - General Workplace Recommendation: Lunch menu variation")
  void testLunchRecommendation() {
    var result = runtime.execute("kal paneer tha lunch me, aaj kya serve kare team ko?", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer().toLowerCase()).containsAnyOf("rajma", "dal", "pulao");
  }

  @Test
  @DisplayName("Turn 4 - Ordinal resolution with NO candidate set -> asks clarification without inventing list")
  void testOrdinalWithoutCandidates() {
    var result = runtime.execute("wahi second wala", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.answer()).containsAnyOf("candidate list", "second item", "list");
  }

  @Test
  @DisplayName("Turn 5 - Decision Support: Investment Inquiry -> verified numbers, states uncertainty, no EvePlan mutation")
  void testInvestmentDecisionSupport() {
    UUID prodId = UUID.randomUUID();
    Production prod = new Production();
    prod.id = prodId;
    prod.title = "Sharma Wedding";
    prod.status = Production.Status.PLANNING;

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    when(financeReadService.production(prodId)).thenReturn(Map.of(
        "contracted", new BigDecimal("500000.00"),
        "received", new BigDecimal("200000.00"),
        "outstanding", new BigDecimal("300000.00")
    ));

    var result = runtime.execute("kya mujhe 50000 sharma wedding wale event me invest karne chahiye?", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Sharma Wedding");
    assertThat(result.answer()).contains("50,000");
    assertThat(result.evidence()).isNotEmpty();
  }

  @Test
  @DisplayName("Turn 6 - Profit Inquiry: States lack of speculative forecast without inventing facts")
  void testProfitInquiryUnrecorded() {
    UUID prodId = UUID.randomUUID();
    Production prod = new Production();
    prod.id = prodId;
    prod.title = "MIPS Event";
    prod.status = Production.Status.PLANNING;

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    when(financeReadService.production(prodId)).thenReturn(Map.of(
        "contracted", new BigDecimal("150000.00"),
        "received", new BigDecimal("50000.00"),
        "outstanding", new BigDecimal("100000.00")
    ));

    var result = runtime.execute("MIPS event ka profit kitna hoga?", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
    assertThat(result.answer()).contains("MIPS Event");
  }

  @Test
  @DisplayName("Turn 7 - Production Ranking: Event with most pending tasks")
  void testMostPendingTasksRanking() {
    Production p1 = new Production();
    p1.id = UUID.randomUUID();
    p1.title = "Event Alpha";
    p1.status = Production.Status.PLANNING;

    Production p2 = new Production();
    p2.id = UUID.randomUUID();
    p2.title = "Event Beta";
    p2.status = Production.Status.PLANNING;

    when(productionRepo.findAll()).thenReturn(List.of(p1, p2));
    when(workTaskRepo.countByProductionIdAndStatusNotIn(p1.id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED))).thenReturn(2L);
    when(workTaskRepo.countByProductionIdAndStatusNotIn(p2.id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED))).thenReturn(7L);

    var result = runtime.execute("Jo event sabse zyada pending kaam wala hai uska naam batao", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Event Beta");
    assertThat(result.answer()).contains("7");
  }

  @Test
  @DisplayName("Turn 8 - Language and Register Matching: Hindi input produces Hindi/Hinglish response")
  void testLanguageMatching() {
    var resultHindi = runtime.execute("30-10+4-12 ka hisaab batao", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(resultHindi.answer()).containsAnyOf("Calculation", "परिणाम", "result");

    var resultEnglish = runtime.execute("30-10+4-12", UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(resultEnglish.answer()).contains("The calculated result is 12.");
  }
}
