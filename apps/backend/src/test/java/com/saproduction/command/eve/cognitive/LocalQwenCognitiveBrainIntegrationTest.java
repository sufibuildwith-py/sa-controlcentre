package com.saproduction.command.eve.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.*;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.io.File;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalQwenCognitiveBrainIntegrationTest {

  private LocalQwenModelProvider qwenProvider;
  private EveTemporalReasoningService temporalService;
  private EveCognitiveToolRegistry toolRegistry;
  private EveCognitiveRuntime cognitiveRuntime;

  private ProductionRepository productionRepo;
  private ProductionMemberRepository memberRepo;
  private WorkTaskRepository workTaskRepo;
  private EmployeeRepository employeeRepo;
  private EmployeeService employeeService;
  private FinanceReadService financeReadService;
  private EveRetrievalService retrievalService;

  private UUID sessionId;
  private Employee kabir;
  private Employee rohan;
  private Production sharmaWedding;
  private Production aroraWedding;
  private Production techSummit;

  @BeforeAll
  void setUpAll() {
    System.out.println("=================================================================");
    System.out.println("STARTING LOCAL QWEN COGNITIVE BRAIN REAL INFERENCE SERVER (8090)");
    System.out.println("=================================================================");

    File model = new File("eve/models/Qwen3-4B-Thinking-2507.Q4_K_M.gguf");
    File server = new File("eve/runtime/llama-server/llama-server.exe");
    if (!model.exists()) {
      model = new File("../../eve/models/Qwen3-4B-Thinking-2507.Q4_K_M.gguf");
      server = new File("../../eve/runtime/llama-server/llama-server.exe");
    }

    qwenProvider = new LocalQwenModelProvider(
        model.getPath(),
        server.getPath(),
        "http://127.0.0.1:8090",
        8090,
        6,
        4096,
        0,
        1024,
        "LOCAL_QWEN_THINKING");

    qwenProvider.init();
    System.out.println("Local Qwen Provider Status: " + qwenProvider.getStatusView());
    assertThat(qwenProvider.getStatusView().status())
        .as("Qwen3-4B-Thinking-2507 server must start successfully on port 8090")
        .isEqualTo("READY");

    // Fixed temporal anchor: 2026-10-01 (Thursday) in Asia/Kolkata
    // "Next week" = Monday 2026-10-05 to Sunday 2026-10-11
    ZoneId kolkataZone = ZoneId.of("Asia/Kolkata");
    Instant anchor = LocalDate.of(2026, 10, 1).atStartOfDay(kolkataZone).toInstant();
    Clock fixedClock = Clock.fixed(anchor, kolkataZone);
    temporalService = new EveTemporalReasoningService(kolkataZone, fixedClock);

    productionRepo = mock(ProductionRepository.class);
    memberRepo = mock(ProductionMemberRepository.class);
    workTaskRepo = mock(WorkTaskRepository.class);
    employeeRepo = mock(EmployeeRepository.class);
    employeeService = mock(EmployeeService.class);
    financeReadService = mock(FinanceReadService.class);
    retrievalService = mock(EveRetrievalService.class);

    toolRegistry = new EveCognitiveToolRegistry(
        productionRepo,
        memberRepo,
        workTaskRepo,
        employeeRepo,
        employeeService,
        financeReadService,
        null,
        retrievalService,
        temporalService);

    cognitiveRuntime = new EveCognitiveRuntime(
        toolRegistry,
        temporalService,
        retrievalService,
        productionRepo,
        memberRepo,
        workTaskRepo,
        employeeRepo);
  }

  @BeforeEach
  void seedAuthoritativeData() {
    sessionId = UUID.randomUUID();

    // 1. Employees
    kabir = new Employee();
    kabir.id = UUID.randomUUID();
    kabir.employeeCode = "SA-EMP-KABIR";
    kabir.firstName = "Kabir";
    kabir.lastName = "Verma";
    kabir.displayName = "Kabir Verma";
    kabir.roleTitle = "Cinematographer";
    kabir.department = "CAMERA";
    kabir.status = Employee.Status.ACTIVE;

    rohan = new Employee();
    rohan.id = UUID.randomUUID();
    rohan.employeeCode = "SA-EMP-ROHAN";
    rohan.firstName = "Rohan";
    rohan.lastName = "Sharma";
    rohan.displayName = "Rohan Sharma";
    rohan.roleTitle = "Gaffer";
    rohan.department = "LIGHTING";
    rohan.status = Employee.Status.ACTIVE;

    when(employeeRepo.findById(kabir.id)).thenReturn(Optional.of(kabir));
    when(employeeRepo.findById(rohan.id)).thenReturn(Optional.of(rohan));

    EveRetrievalService.Candidate kabirCandidate = new EveRetrievalService.Candidate(
        kabir.id, "EMPLOYEE", "Kabir Verma", kabir.employeeCode, "Cinematographer");
    when(retrievalService.resolveEmployee("Kabir"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Kabir"));
    when(retrievalService.resolveEmployee("kabir"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(kabirCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "kabir"));

    // 2. Productions
    sharmaWedding = new Production();
    sharmaWedding.id = UUID.randomUUID();
    sharmaWedding.title = "Sharma Wedding";
    sharmaWedding.venueName = "Taj Lands End";
    sharmaWedding.eventDate = LocalDate.of(2026, 10, 6); // Next week Tuesday
    sharmaWedding.status = Production.Status.PRODUCTION;
    sharmaWedding.clientName = "Mr. Sharma";

    aroraWedding = new Production();
    aroraWedding.id = UUID.randomUUID();
    aroraWedding.title = "Arora Wedding";
    aroraWedding.venueName = "JW Marriott";
    aroraWedding.eventDate = LocalDate.of(2026, 10, 9); // Next week Friday
    aroraWedding.status = Production.Status.PLANNING;
    aroraWedding.clientName = "Mrs. Arora";

    techSummit = new Production();
    techSummit.id = UUID.randomUUID();
    techSummit.title = "Tech Summit 2026";
    techSummit.venueName = "Bandra Kurla Complex";
    techSummit.eventDate = LocalDate.of(2026, 10, 10); // Next week Saturday
    techSummit.status = Production.Status.PRODUCTION;
    techSummit.clientName = "Tech Corp";

    when(productionRepo.findAll()).thenReturn(List.of(sharmaWedding, aroraWedding, techSummit));
    when(productionRepo.findById(sharmaWedding.id)).thenReturn(Optional.of(sharmaWedding));
    when(productionRepo.findById(aroraWedding.id)).thenReturn(Optional.of(aroraWedding));
    when(productionRepo.findById(techSummit.id)).thenReturn(Optional.of(techSummit));

    EveRetrievalService.Candidate sharmaCandidate = new EveRetrievalService.Candidate(
        sharmaWedding.id, "PRODUCTION", "Sharma Wedding", "PROD-SHARMA", "Taj Lands End");
    when(retrievalService.resolveProduction("Sharma Wedding"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(sharmaCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma Wedding"));
    when(retrievalService.resolveProduction("sharma wedding"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(sharmaCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "sharma wedding"));

    // 3. Crew
    when(memberRepo.existsByProductionIdAndEmployeeId(sharmaWedding.id, kabir.id)).thenReturn(true);
    when(memberRepo.existsByProductionIdAndEmployeeId(aroraWedding.id, kabir.id)).thenReturn(false);
    when(memberRepo.existsByProductionIdAndEmployeeId(techSummit.id, kabir.id)).thenReturn(false);

    // 4. Work Tasks
    // Sharma Wedding: 3 pending tasks
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(sharmaWedding.id), any())).thenReturn(3L);
    // Arora Wedding: 1 pending task
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(aroraWedding.id), any())).thenReturn(1L);
    // Tech Summit: 0 pending tasks
    when(workTaskRepo.countByProductionIdAndStatusNotIn(eq(techSummit.id), any())).thenReturn(0L);
  }

  @AfterAll
  void tearDownAll() {
    if (qwenProvider != null) {
      qwenProvider.destroy();
    }
  }

  @Test
  @DisplayName("Real Qwen Test 1: 'next week kitne events hai'")
  void test1_RealQwen_NextWeekKitneEventsHai() {
    System.out.println("\n>>> TEST 1: 'next week kitne events hai'");
    long start = System.currentTimeMillis();

    var resp = cognitiveRuntime.execute("next week kitne events hai", sessionId, "", null, qwenProvider);
    long latency = System.currentTimeMillis() - start;

    System.out.printf("Test 1 Latency: %d ms | Status: %s\nAnswer: %s\n", latency, resp.status(), resp.answer());
    System.out.println("Model-Generated Goal: " + resp.goal());

    assertThat(resp.status()).isEqualTo("COMPLETED");
    assertThat(resp.answer()).contains("3");
    assertThat(resp.reasoningSteps()).isNotEmpty();
    assertThat(resp.reasoningSteps()).anyMatch(s -> "TEMPORAL_GROUNDING".equals(s.stage()));
    assertThat(resp.reasoningSteps()).anyMatch(s -> "search_productions".equals(s.relatedTool()));
  }

  @Test
  @DisplayName("Real Qwen Test 2: 'next week ke events jisme Kabir hai aur task pending hai'")
  void test2_RealQwen_NextWeekKabirPendingTasks() {
    System.out.println("\n>>> TEST 2: 'next week ke events jisme Kabir hai aur task pending hai'");
    long start = System.currentTimeMillis();

    var resp = cognitiveRuntime.execute("next week ke events jisme Kabir hai aur task pending hai", sessionId, "", null, qwenProvider);
    long latency = System.currentTimeMillis() - start;

    System.out.printf("Test 2 Latency: %d ms | Status: %s\nAnswer: %s\n", latency, resp.status(), resp.answer());
    System.out.println("Model-Generated Goal: " + resp.goal());

    assertThat(resp.status()).isEqualTo("COMPLETED");
    assertThat(resp.answer()).contains("Sharma Wedding");
    assertThat(resp.reasoningSteps()).isNotEmpty();
    assertThat(resp.reasoningSteps()).anyMatch(s -> "TEMPORAL_GROUNDING".equals(s.stage()));
  }

  @Test
  @DisplayName("Real Qwen Test 3: Novel paraphrase 'Agle hafte kaunsa event sabse zyada kaam pending lekar ja raha hai?'")
  void test3_RealQwen_NovelParaphraseMostPendingTasks() {
    System.out.println("\n>>> TEST 3: 'Agle hafte kaunsa event sabse zyada kaam pending lekar ja raha hai?'");
    long start = System.currentTimeMillis();

    var resp = cognitiveRuntime.execute("Agle hafte kaunsa event sabse zyada kaam pending lekar ja raha hai?", sessionId, "", null, qwenProvider);
    long latency = System.currentTimeMillis() - start;

    System.out.printf("Test 3 Latency: %d ms | Status: %s\nAnswer: %s\n", latency, resp.status(), resp.answer());
    System.out.println("Model-Generated Goal: " + resp.goal());

    assertThat(resp.status()).isEqualTo("COMPLETED");
    assertThat(resp.answer()).contains("Sharma Wedding");
    assertThat(resp.reasoningSteps()).isNotEmpty();
  }

  @Test
  @DisplayName("Real Qwen Test 4: Unresolved pronoun 'Uska budget kitna hai?' -> CLARIFICATION_REQUIRED")
  void test4_RealQwen_UnresolvedPronounClarification() {
    System.out.println("\n>>> TEST 4: 'Uska budget kitna hai?' (no antecedent)");
    long start = System.currentTimeMillis();

    var resp = cognitiveRuntime.execute("Uska budget kitna hai?", sessionId, "", null, qwenProvider);
    long latency = System.currentTimeMillis() - start;

    System.out.printf("Test 4 Latency: %d ms | Status: %s\nAnswer: %s\n", latency, resp.status(), resp.answer());
    System.out.println("Model-Generated Goal: " + resp.goal());

    assertThat(resp.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(resp.answer()).isNotBlank();
  }

  @Test
  @DisplayName("Real Qwen Test 5: Contextual continuation 'Sharma Wedding ka crew kaun hai?' -> 'aur usme pending task kitne hain?'")
  void test5_RealQwen_ContextualPronounContinuation() {
    System.out.println("\n>>> TEST 5: Contextual continuation");
    String contextSummary = "[Active Focus: PRODUCTION Sharma Wedding (PROD-SHARMA). Recent query: \"Sharma Wedding ka crew kaun hai?\"]";

    long start = System.currentTimeMillis();
    var resp = cognitiveRuntime.execute("aur usme pending task kitne hain?", sessionId, contextSummary, null, qwenProvider);
    long latency = System.currentTimeMillis() - start;

    System.out.printf("Test 5 Latency: %d ms | Status: %s\nAnswer: %s\n", latency, resp.status(), resp.answer());
    System.out.println("Model-Generated Goal: " + resp.goal());

    assertThat(resp.status()).isEqualTo("COMPLETED");
    assertThat(resp.answer()).contains("3");
    assertThat(resp.answer()).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Phase 3 Safety Invariant: Governed write proposal interpretation never directly mutates")
  void test6_Safety_GovernedWriteProposal() {
    System.out.println("\n>>> TEST 6: Real Qwen Governed Write Proposal 'Pay Kabir 3000'");

    var interpretation = qwenProvider.interpret(new EveModelProvider.EveInterpretationRequest("Pay Kabir 3000", ""));
    System.out.println("Model Interpretation Intent: " + interpretation.intent());
    System.out.println("Spoken Entity: " + interpretation.spokenEntity());
    System.out.println("Amount Minor: " + interpretation.amountMinor());

    assertThat(interpretation.intent()).isEqualTo(EveModelProvider.Intent.PROPOSE_EMPLOYEE_PAYMENT);
    assertThat(interpretation.spokenEntity()).containsIgnoringCase("Kabir");
    assertThat(interpretation.amountMinor()).isEqualTo(300000L); // 3000 in minor units (paisa)
  }
}
