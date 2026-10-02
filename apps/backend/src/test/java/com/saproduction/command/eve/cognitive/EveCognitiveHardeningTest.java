package com.saproduction.command.eve.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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

/**
 * Hardening & Anti-Parrot Generalization Verification Suite for EVE Cognitive Runtime 2.0.
 *
 * Verifies that EVE:
 * 1. Generalizes to completely unseen food/catering requests without memorizing food items.
 * 2. Truthfully routes unintegrated external services (stocks, flights, sports, weather) to UNSUPPORTED_CAPABILITY
 *    without closed-world ERP NOT_FOUND errors.
 * 3. Evaluates arithmetic deterministically without LLM hallucination.
 * 4. Extracts novel production names and crew names generically without fixed name dictionaries.
 * 5. Provides grounded decision support with authoritative facts while stating uncertainty, never executing mutations.
 */
class EveCognitiveHardeningTest {

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
    Instant anchor = LocalDate.of(2026, 10, 1).atStartOfDay(kolkataZone).toInstant();
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

    decisionSupportService = new EveDecisionSupportService(productionRepo, financeReadService, retrievalService, productionMemberRepo, workTaskRepo);
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
  @DisplayName("1. Generalization: Novel Chinese catering query extracts prior constraint dynamically without paneer bias")
  void test1_UnseenFoodCateringRecommendation() {
    var result = runtime.execute(
        "kal Chinese food mangwaya tha, aaj team ke liye lunch me kya mangaayein?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer().toLowerCase()).contains("chinese");
    assertThat(result.answer().toLowerCase()).containsAnyOf("dal", "pulao", "rajma", "sabzi");
    assertThat(result.evidence()).anyMatch(e -> "RECOMMENDATION".equalsIgnoreCase(e.domain()));

    // Invariant: Never searched PostgreSQL database for Chinese food
    verifyNoInteractions(productionRepo, workTaskRepo, employeeRepo);
  }

  @Test
  @DisplayName("2. External Boundary: Stock price inquiry returns UNSUPPORTED_CAPABILITY, never ERP NOT_FOUND")
  void test2_UnseenExternalStockPrice() {
    var result = runtime.execute(
        "Reliance Industries ka share price kya chal raha hai?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("UNSUPPORTED_CAPABILITY");
    assertThat(result.answer()).containsAnyOf("share price", "stock market", "live");
    assertThat(result.answer()).doesNotContain("I couldn't find relevant records matching your request in SA Command");
    assertThat(result.evidence()).anyMatch(e -> "CAPABILITY".equalsIgnoreCase(e.domain()) && e.value().contains("unsupported"));

    // Invariant: Never searched PostgreSQL database for Reliance
    verifyNoInteractions(productionRepo, workTaskRepo, employeeRepo);
  }

  @Test
  @DisplayName("3. External Boundary: Live flight tracking returns UNSUPPORTED_CAPABILITY")
  void test3_UnseenExternalFlightTracking() {
    var result = runtime.execute(
        "IndiGo flight 6E-204 ka live status kya hai?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("UNSUPPORTED_CAPABILITY");
    assertThat(result.answer().toLowerCase()).containsAnyOf("flight", "travel", "live");
    assertThat(result.answer()).doesNotContain("not found");
  }

  @Test
  @DisplayName("4. External Boundary: Live cricket score returns UNSUPPORTED_CAPABILITY")
  void test4_UnseenExternalLiveSports() {
    var result = runtime.execute(
        "India vs Australia match ka live score kya chal raha hai?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("UNSUPPORTED_CAPABILITY");
    assertThat(result.answer().toLowerCase()).containsAnyOf("sports", "score", "live");
    assertThat(result.evidence()).anyMatch(e -> "CAPABILITY".equalsIgnoreCase(e.domain()));
  }

  @Test
  @DisplayName("5. Deterministic Arithmetic: Unseen multi-operator expression 45000 - 12500 + 3200 = 35700")
  void test5_UnseenArithmeticExpression() {
    var result = runtime.execute(
        "45000 - 12500 + 3200",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("35700");
    assertThat(result.evidence()).anyMatch(e -> "CALCULATION".equalsIgnoreCase(e.domain()));
  }

  @Test
  @DisplayName("6. Analytical Generalization: Dynamically extracts unseen production 'Delhi Cultural Expo' for crew count")
  void test6_UnseenProductionCrewCount() {
    UUID prodId = UUID.randomUUID();
    Production prod = new Production();
    prod.id = prodId;
    prod.title = "Delhi Cultural Expo";
    prod.status = Production.Status.PLANNING;

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    when(productionRepo.findById(prodId)).thenReturn(Optional.of(prod));
    var cand = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Delhi Cultural Expo", "PROD-DELHI", null);
    when(retrievalService.resolveProduction("Delhi Cultural Expo")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(cand, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Delhi Cultural Expo"));

    ProductionMember m1 = new ProductionMember();
    m1.employeeId = UUID.randomUUID();
    m1.productionRole = "Sound Lead";
    ProductionMember m2 = new ProductionMember();
    m2.employeeId = UUID.randomUUID();
    m2.productionRole = "Lighting Tech";

    when(productionMemberRepo.findAllByProductionIdOrderByCreatedAt(prodId)).thenReturn(List.of(m1, m2));

    var result = runtime.execute(
        "Delhi Cultural Expo me kitne log kaam kar rahe hain?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Delhi Cultural Expo");
    assertThat(result.answer()).contains("2");
    assertThat(result.goal()).isNotNull();
    assertThat(result.goal().goal()).isEqualTo("COUNT_PRODUCTION_CREW");
  }

  @Test
  @DisplayName("7. Constraint Generalization: Dynamically extracts unseen crew member 'Ananya' and filters productions with pending tasks")
  void test7_UnseenCrewMemberFilterWithPendingTasks() {
    UUID prod1Id = UUID.randomUUID();
    Production prod1 = new Production();
    prod1.id = prod1Id;
    prod1.title = "North Zone Conclave";
    prod1.status = Production.Status.PLANNING;
    prod1.eventDate = LocalDate.of(2026, 10, 7); // Next week

    UUID prod2Id = UUID.randomUUID();
    Production prod2 = new Production();
    prod2.id = prod2Id;
    prod2.title = "South Tech Fair";
    prod2.status = Production.Status.PLANNING;
    prod2.eventDate = LocalDate.of(2026, 10, 8); // Next week

    when(productionRepo.findAll()).thenReturn(List.of(prod1, prod2));

    UUID empId = UUID.randomUUID();
    Employee ananya = new Employee();
    ananya.id = empId;
    ananya.displayName = "Ananya Roy";
    when(employeeRepo.findAll()).thenReturn(List.of(ananya));
    when(retrievalService.resolveEmployee("Ananya")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(
            new EveRetrievalService.Candidate(empId, "EMPLOYEE", "Ananya Roy", "EMP-ANANYA", null),
            EveRetrievalService.MatchMethod.BOUNDED_SEARCH,
            "Ananya"));

    ProductionMember pm = new ProductionMember();
    pm.employeeId = empId;
    pm.productionId = prod1Id;
    when(productionMemberRepo.findByEmployeeId(empId)).thenReturn(List.of(pm));
    when(productionMemberRepo.existsByProductionIdAndEmployeeId(prod1Id, empId)).thenReturn(true);
    when(productionMemberRepo.existsByProductionIdAndEmployeeId(prod2Id, empId)).thenReturn(false);

    when(workTaskRepo.countByProductionIdAndStatusNotIn(prod1Id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED))).thenReturn(3L);
    when(workTaskRepo.countByProductionIdAndStatusNotIn(prod2Id, List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED))).thenReturn(0L);

    var result = runtime.execute(
        "next week ke events jisme Ananya hai aur task pending hai",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("North Zone Conclave");
    assertThat(result.answer()).doesNotContain("South Tech Fair");
  }

  @Test
  @DisplayName("8. Governed Decision Support: Evaluates unseen production investment with arbitrary amount, states uncertainty without mutation")
  void test8_UnseenDecisionSupportInvestment() {
    UUID prodId = UUID.randomUUID();
    Production prod = new Production();
    prod.id = prodId;
    prod.title = "Kolkata Literary Meet";
    prod.status = Production.Status.PLANNING;

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    var cand = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Kolkata Literary Meet", "PROD-KLM", null);
    when(retrievalService.resolveProduction("Kolkata Literary Meet")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(cand, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Kolkata Literary Meet"));

    when(financeReadService.production(prodId)).thenReturn(Map.of(
        "contracted", new BigDecimal("800000.00"),
        "received", new BigDecimal("300000.00"),
        "outstanding", new BigDecimal("500000.00")
    ));

    var result = runtime.execute(
        "Kya mujhe 75000 Kolkata Literary Meet wale event me invest karna chahiye?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Kolkata Literary Meet");
    assertThat(result.answer()).contains("75,000");
    assertThat(result.answer()).contains("800,000");
    assertThat(result.evidence()).isNotEmpty();

    // Invariant: No mutation or payment proposal created
    verifyNoInteractions(employeeService);
  }

  @Test
  @DisplayName("9. Disambiguation Guard: Third item selection without active candidates requests clarification")
  void test9_DisambiguationWithoutCandidates_ThirdItem() {
    var result = runtime.execute(
        "wahi teesra wala",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.answer()).containsAnyOf("candidate", "list", "active");
  }

  @Test
  @DisplayName("10. General Operations: Structured workflow recommendation formulated without ERP lookups")
  void test10_GeneralOperationalWorkflowAdvice() {
    var result = runtime.execute(
        "Heavy outdoor setup hai, tasks kaise distribute karein?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).isNotEmpty();
    assertThat(result.evidence()).anyMatch(e -> "RECOMMENDATION".equalsIgnoreCase(e.domain()));

    // Invariant: Never searched PostgreSQL database
    verifyNoInteractions(productionRepo, workTaskRepo, employeeRepo);
  }

  @Test
  @DisplayName("11. Critical Failure Closure: 'kya mujhe sharma wedding wale production me 4 logo ko bhejna chahoue?' is crew allocation, NOT investment")
  void test11_SharmaWeddingCrewAllocationDecision() {
    Production prod = new Production();
    prod.id = UUID.randomUUID();
    prod.title = "Sharma Wedding";
    prod.status = Production.Status.PRODUCTION;
    prod.eventDate = LocalDate.of(2026, 10, 6);

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    when(productionRepo.findById(prod.id)).thenReturn(Optional.of(prod));

    ProductionMember m1 = new ProductionMember();
    m1.id = UUID.randomUUID();
    ProductionMember m2 = new ProductionMember();
    m2.id = UUID.randomUUID();
    when(productionMemberRepo.findAllByProductionIdOrderByCreatedAt(prod.id)).thenReturn(List.of(m1, m2));
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(prod.id), any())).thenReturn(3L);

    var res = runtime.execute(
        "kya mujhe sharma wedding wale production me 4 logo ko bhejna chahoue?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);

    assertThat(res.status()).isEqualTo("COMPLETED");
    assertThat(res.answer()).contains("Sharma Wedding");
    assertThat(res.answer()).contains("2 crew member");
    assertThat(res.answer()).contains("3 open task");
    assertThat(res.answer()).contains("4 log bhejna");

    // Critical Invariant: Zero leakage of investment or 50,000
    assertThat(res.answer()).doesNotContain("50,000");
    assertThat(res.answer()).doesNotContain("50000");
    assertThat(res.answer()).doesNotContainIgnoringCase("invest");
    assertThat(res.answer()).doesNotContainIgnoringCase("outstanding");
    assertThat(res.answer()).doesNotContain("180,000");
    assertThat(res.answer()).doesNotContainIgnoringCase("cash flow");

    // Evidence Invariants
    assertThat(res.evidence()).anyMatch(e -> "CREW".equalsIgnoreCase(e.domain()));
    assertThat(res.evidence()).anyMatch(e -> "WORK_TASK".equalsIgnoreCase(e.domain()));
    assertThat(res.evidence()).anyMatch(e -> "SYSTEM".equalsIgnoreCase(e.domain()) && e.label().contains("Staffing Requirement"));

    // Never accessed finance ledger
    verifyNoInteractions(financeReadService);
  }

  @Test
  @DisplayName("12. Session Isolation Invariant: Stale context across 5 distinct scenarios never mutates crew intent to finance")
  void test12_StaleContextScenarios_CrewAllocation() {
    Production prod = new Production();
    prod.id = UUID.randomUUID();
    prod.title = "Sharma Wedding";
    prod.status = Production.Status.PRODUCTION;
    prod.eventDate = LocalDate.of(2026, 10, 6);

    when(productionRepo.findAll()).thenReturn(List.of(prod));
    when(productionRepo.findById(prod.id)).thenReturn(Optional.of(prod));
    when(productionMemberRepo.findAllByProductionIdOrderByCreatedAt(prod.id)).thenReturn(List.of(new ProductionMember()));
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(prod.id), any())).thenReturn(1L);

    String query = "kya mujhe sharma wedding wale production me 4 logo ko bhejna chahoue?";

    // Scenario A: Fresh session
    var ctxA = new EveRetrievalRouter.SessionContext();
    var resA = runtime.execute(query, UUID.randomUUID(), "", ctxA, modelProvider);
    assertThat(resA.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // Scenario B: After unrelated finance conversation
    var ctxB = new EveRetrievalRouter.SessionContext();
    ctxB.addTurn("Kabir ko kitna dena hai?", "Kabir ko 15000 dena hai.", EveModelProvider.Intent.READ_EMPLOYEE_FINANCE);
    var resB = runtime.execute(query, UUID.randomUUID(), "[Recent turn: Employee finance Kabir]", ctxB, modelProvider);
    assertThat(resB.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // Scenario C: After investment decision-support conversation
    var ctxC = new EveRetrievalRouter.SessionContext();
    ctxC.addTurn("Kya mujhe 50000 Arora Wedding me invest karna chahiye?", "Arora Wedding finance position...", EveModelProvider.Intent.GENERAL_QUERY);
    var resC = runtime.execute(query, UUID.randomUUID(), "[Recent turn: Investment decision Arora Wedding]", ctxC, modelProvider);
    assertThat(resC.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // Scenario D: After Sharma Wedding finance conversation
    var ctxD = new EveRetrievalRouter.SessionContext();
    ctxD.addTurn("Sharma Wedding ka contract kitna hai?", "Sharma Wedding contract is 300,000", EveModelProvider.Intent.READ_PRODUCTION_FINANCE);
    var resD = runtime.execute(query, UUID.randomUUID(), "[Recent turn: Production finance Sharma Wedding]", ctxD, modelProvider);
    assertThat(resD.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // Scenario E: After Sharma Wedding crew conversation
    var ctxE = new EveRetrievalRouter.SessionContext();
    ctxE.addTurn("Sharma Wedding me kaun kaam kar raha hai?", "Kabir is assigned to Sharma Wedding", EveModelProvider.Intent.READ_PRODUCTION_CREW);
    var resE = runtime.execute(query, UUID.randomUUID(), "[Recent turn: Production crew Sharma Wedding]", ctxE, modelProvider);
    assertThat(resE.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");
  }

  @Test
  @DisplayName("13. Blind Paraphrases: Crew allocation decision generalizes across English, Hindi, varying quantities, and verbs")
  void test13_BlindParaphrases_CrewAllocation() {
    Production sharma = new Production();
    sharma.id = UUID.randomUUID();
    sharma.title = "Sharma Wedding";
    sharma.status = Production.Status.PRODUCTION;
    sharma.eventDate = LocalDate.of(2026, 10, 6);

    Production delhi = new Production();
    delhi.id = UUID.randomUUID();
    delhi.title = "Delhi Cultural Expo";
    delhi.status = Production.Status.PLANNING;
    delhi.eventDate = LocalDate.of(2026, 11, 15);

    when(productionRepo.findAll()).thenReturn(List.of(sharma, delhi));
    when(productionRepo.findById(sharma.id)).thenReturn(Optional.of(sharma));
    when(productionRepo.findById(delhi.id)).thenReturn(Optional.of(delhi));
    when(productionMemberRepo.findAllByProductionIdOrderByCreatedAt(any())).thenReturn(List.of(new ProductionMember()));
    when(workTaskRepo.countByProductionIdAndStatusNotIn(any(), any())).thenReturn(2L);

    // 1. English paraphrase with 'send' and 4 people
    var r1 = runtime.execute(
        "Should I send 4 people to the Sharma Wedding production?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(r1.status()).isEqualTo("COMPLETED");
    assertThat(r1.answer()).contains("Sharma Wedding");
    assertThat(r1.answer()).contains("4 people");
    assertThat(r1.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // 2. Hindi phrasing with ४ लोग
    var r2 = runtime.execute(
        "kya mujhe sharma wedding wale production me 4 logo ko bhejna chahiye?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(r2.status()).isEqualTo("COMPLETED");
    assertThat(r2.answer()).contains("Sharma Wedding");
    assertThat(r2.answer()).contains("4 log");
    assertThat(r2.answer()).doesNotContain("50,000").doesNotContainIgnoringCase("invest");

    // 3. Different quantity (2 people)
    var r3 = runtime.execute(
        "kya mujhe sharma wedding wale production me 2 log bhejne chahiye?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(r3.status()).isEqualTo("COMPLETED");
    assertThat(r3.answer()).contains("2 log");

    // 4. Different production and different verb ('assign')
    var r4 = runtime.execute(
        "Should we assign 6 crew members to the Delhi Cultural Expo?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(r4.status()).isEqualTo("COMPLETED");
    assertThat(r4.answer()).contains("Delhi Cultural Expo");
    assertThat(r4.answer()).contains("6 people");

    // 5. Informal Hindi slang ('bande', 'depute')
    var r5 = runtime.execute(
        "kya mujhe sharma wedding me 3 bande assign karne chahiye?",
        UUID.randomUUID(), "", new EveRetrievalRouter.SessionContext(), modelProvider);
    assertThat(r5.status()).isEqualTo("COMPLETED");
    assertThat(r5.answer()).contains("Sharma Wedding");
    assertThat(r5.answer()).contains("3 log");
  }
}
