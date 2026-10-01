package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.semantic.EveSemanticCache;
import com.saproduction.command.eve.semantic.EveSemanticPolicy;
import com.saproduction.command.eve.semantic.EveSemanticResolutionService;
import com.saproduction.command.eve.semantic.TestEmbeddingProvider;
import com.saproduction.command.eve.semantic.TestRerankerProvider;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.production.ProductionService;
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

/**
 * Regression and Verification Test Suite for Sharma Wedding Entity Resolution.
 * Exercises the real production entry point: EveService.query(EveDtos.QueryRequest).
 *
 * Verifies that:
 * 1. "Royal" is never injected into unrelated queries.
 * 2. Hinglish phrasing variants and typo tolerance ("shamra" -> "sharma") resolve deterministically.
 * 3. Structured PendingClarification preserves targetEntityType, originalIntent, originalEntityPhrase, candidate IDs.
 * 4. Topic switching works seamlessly after clarification.
 */
class EveSharmaWeddingResolutionTest {

  private TestModelProvider modelProvider;
  private EveRetrievalService retrievalService;
  private EveContextEngine contextEngine;
  private EveKnowledgeService knowledgeService;
  private EveMemoryService memoryService;
  private FinanceReadService financeReads;
  private ProductionService productionService;
  private ProductionRepository productionRepo;
  private ProductionMemberRepository memberRepo;
  private EmployeeService employeeService;
  private EmployeeRepository employeeRepo;
  private WorkTaskService taskService;
  private HeadquartersService headquartersService;
  private EveRetrievalRouter retrievalRouter;
  private JdbcTemplate jdbc;
  private EveService eveService;

  private UUID sessionId;
  private UUID sharmaWeddingId;
  private UUID royalWeddingId;
  private UUID royalGalaId;
  private UUID kabirId;
  private UUID rajId;

  private Production sharmaWedding;
  private Production royalWedding;
  private Production royalGala;
  private Employee kabir;
  private Employee raj;

  @BeforeEach
  void setUp() {
    modelProvider = new TestModelProvider();
    contextEngine = new EveContextEngine("Asia/Kolkata");
    knowledgeService = new EveKnowledgeService();
    memoryService = mock(EveMemoryService.class);
    financeReads = mock(FinanceReadService.class);
    productionService = mock(ProductionService.class);
    productionRepo = mock(ProductionRepository.class);
    memberRepo = mock(ProductionMemberRepository.class);
    employeeService = mock(EmployeeService.class);
    employeeRepo = mock(EmployeeRepository.class);
    taskService = mock(WorkTaskService.class);
    headquartersService = mock(HeadquartersService.class);
    jdbc = mock(JdbcTemplate.class);

    sessionId = UUID.randomUUID();
    sharmaWeddingId = UUID.randomUUID();
    royalWeddingId = UUID.randomUUID();
    royalGalaId = UUID.randomUUID();
    kabirId = UUID.randomUUID();
    rajId = UUID.randomUUID();

    // Setup Canonical Productions
    sharmaWedding = new Production();
    sharmaWedding.id = sharmaWeddingId;
    sharmaWedding.title = "Sharma Wedding";
    sharmaWedding.clientName = "Mr. Sharma";
    sharmaWedding.status = Production.Status.PRODUCTION;
    sharmaWedding.eventDate = LocalDate.now().plusDays(5);
    sharmaWedding.venueName = "Taj Palace";

    royalWedding = new Production();
    royalWedding.id = royalWeddingId;
    royalWedding.title = "Royal Wedding";
    royalWedding.clientName = "Singhania";
    royalWedding.status = Production.Status.PRODUCTION;
    royalWedding.eventDate = LocalDate.now().plusDays(10);
    royalWedding.venueName = "Umaid Bhawan";

    royalGala = new Production();
    royalGala.id = royalGalaId;
    royalGala.title = "Royal Gala";
    royalGala.clientName = "Royal Heritage Group";
    royalGala.status = Production.Status.PRODUCTION;
    royalGala.eventDate = LocalDate.now().plusDays(12);
    royalGala.venueName = "ITC Grand";

    // Setup Canonical Employees
    kabir = new Employee();
    kabir.id = kabirId;
    kabir.firstName = "Kabir";
    kabir.lastName = "Singh";
    kabir.displayName = "Kabir Singh";
    kabir.employeeCode = "SA-10";
    kabir.roleTitle = "Lead Sound Engineer";
    kabir.status = Employee.Status.ACTIVE;

    raj = new Employee();
    raj.id = rajId;
    raj.firstName = "Raj";
    raj.lastName = "Sharma";
    raj.displayName = "Raj Sharma";
    raj.employeeCode = "SA-11";
    raj.roleTitle = "Lighting Technician";
    raj.status = Employee.Status.ACTIVE;

    when(productionRepo.findAll()).thenReturn(List.of(sharmaWedding, royalWedding, royalGala));
    when(productionRepo.findById(sharmaWeddingId)).thenReturn(Optional.of(sharmaWedding));
    when(productionRepo.findById(royalWeddingId)).thenReturn(Optional.of(royalWedding));
    when(productionRepo.findById(royalGalaId)).thenReturn(Optional.of(royalGala));

    when(employeeRepo.findAll()).thenReturn(List.of(kabir, raj));
    when(employeeRepo.findById(kabirId)).thenReturn(Optional.of(kabir));
    when(employeeRepo.findById(rajId)).thenReturn(Optional.of(raj));

    EmployeeDtos.View kabirView = new EmployeeDtos.View(
        kabirId, "SA-10", "Kabir", "Singh", "Kabir Singh",
        null, null, null, "Lead Sound Engineer", null, null,
        LocalDate.now(), 5000000L, "INR", Employee.Status.ACTIVE,
        null, null, Instant.now(), Instant.now());
    EmployeeDtos.View rajView = new EmployeeDtos.View(
        rajId, "SA-11", "Raj", "Sharma", "Raj Sharma",
        null, null, null, "Lighting Technician", null, null,
        LocalDate.now(), 5000000L, "INR", Employee.Status.ACTIVE,
        null, null, Instant.now(), Instant.now());

    when(employeeService.list(any(), any())).thenAnswer(inv -> {
      String q = inv.getArgument(0);
      if (q == null || q.isBlank()) {
        return List.of(kabirView, rajView);
      }
      String l = q.toLowerCase(Locale.ROOT);
      List<EmployeeDtos.View> res = new ArrayList<>();
      if (kabir.displayName.toLowerCase(Locale.ROOT).contains(l) || kabir.firstName.toLowerCase(Locale.ROOT).contains(l)) {
        res.add(kabirView);
      }
      if (raj.displayName.toLowerCase(Locale.ROOT).contains(l) || raj.firstName.toLowerCase(Locale.ROOT).contains(l)) {
        res.add(rajView);
      }
      return res;
    });

    List<ProductionService.MemberView> sharmaMembers = List.of(
        new ProductionService.MemberView(UUID.randomUUID(), kabirId, "Kabir Singh", "Lead Sound Engineer", true, ProductionMember.Status.CONFIRMED, false, null),
        new ProductionService.MemberView(UUID.randomUUID(), rajId, "Raj Sharma", "Lighting Technician", true, ProductionMember.Status.CONFIRMED, false, null)
    );

    ProductionService.View sharmaWeddingView = new ProductionService.View(
        sharmaWeddingId, "Sharma Wedding", "Mr. Sharma", null,
        LocalDate.now().plusDays(5), null, null, "Taj Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 0, null,
        sharmaMembers, 0, List.of(), Instant.now(), Instant.now());

    ProductionService.View royalWeddingView = new ProductionService.View(
        royalWeddingId, "Royal Wedding", "Singhania", null,
        LocalDate.now().plusDays(10), null, null, "Umaid Bhawan", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 0, null,
        List.of(sharmaMembers.get(0)), 0, List.of(), Instant.now(), Instant.now());

    ProductionService.View royalGalaView = new ProductionService.View(
        royalGalaId, "Royal Gala", "Royal Heritage Group", null,
        LocalDate.now().plusDays(12), null, null, "ITC Grand", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 0, null,
        List.of(sharmaMembers.get(1)), 0, List.of(), Instant.now(), Instant.now());

    when(productionService.get(sharmaWeddingId)).thenReturn(sharmaWeddingView);
    when(productionService.get(royalWeddingId)).thenReturn(royalWeddingView);
    when(productionService.get(royalGalaId)).thenReturn(royalGalaView);

    when(productionService.list(any(), any(), any(), any(), any())).thenReturn(List.of(
        sharmaWeddingView,
        royalWeddingView,
        royalGalaView
    ));

    // Real Semantic Resolution Service with real TestEmbeddingProvider + TestRerankerProvider
    var embeddingProvider = new TestEmbeddingProvider();
    var rerankerProvider = new TestRerankerProvider();
    var cache = new EveSemanticCache();
    var policy = new EveSemanticPolicy(0.20, 0.05, 20);

    var semanticService = new EveSemanticResolutionService(
        embeddingProvider,
        rerankerProvider,
        cache,
        policy,
        productionRepo,
        memberRepo,
        employeeService,
        true);

    // Real Retrieval Service
    retrievalService = new EveRetrievalService(
        employeeRepo,
        employeeService,
        memoryService,
        productionRepo,
        semanticService);

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

    when(jdbc.queryForObject(contains("FROM eve_sessions WHERE id = ?"), eq(Integer.class), any(UUID.class)))
        .thenReturn(1);
    when(jdbc.query(contains("FROM eve_messages WHERE session_id = ?"), any(RowMapper.class), any(UUID.class)))
        .thenReturn(List.of());

    // Finance mock for Kabir Singh (FinanceReadService.employee)
    when(financeReads.employee(kabirId)).thenReturn(Map.of(
        "employee", Map.of("displayName", "Kabir Singh", "employeeCode", "SA-10"),
        "earned", new BigDecimal("50000.00"),
        "paid", new BigDecimal("35000.00"),
        "outstanding", new BigDecimal("15000.00")));
  }

  @Test
  @DisplayName("Scenario 1: Canonical English query 'crew for sharma wedding'")
  void scenario1_crewForSharmaWedding() {
    var response = eveService.query(new EveDtos.QueryRequest("crew for sharma wedding", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.message().content()).contains("Raj Sharma");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 2: Typo variant 'crew for shamra wedding'")
  void scenario2_crewForShamraWedding() {
    var response = eveService.query(new EveDtos.QueryRequest("crew for shamra wedding", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.message().content()).contains("Raj Sharma");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 3: Mixed Hinglish 'sharma wedding ke event me kon gaya hai'")
  void scenario3_sharmaWeddingKeEventMeKonGayaHai() {
    var response = eveService.query(new EveDtos.QueryRequest("sharma wedding ke event me kon gaya hai", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 4: Mixed Hinglish + Typo 'shamra wedding ke event me kon gaya he'")
  void scenario4_shamraWeddingKeEventMeKonGayaHe() {
    var response = eveService.query(new EveDtos.QueryRequest("shamra wedding ke event me kon gaya he", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).doesNotContain("couldn't find relevant records");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.message().content()).contains("Raj Sharma");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 5: Alternative English phrasing 'who works on sharma wedding'")
  void scenario5_whoWorksOnSharmaWedding() {
    var response = eveService.query(new EveDtos.QueryRequest("who works on sharma wedding", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 6: Suffix / topical variant 'sharma wedding wale event me kaun gaya'")
  void scenario6_sharmaWeddingWaleEventMeKaunGaya() {
    var response = eveService.query(new EveDtos.QueryRequest("sharma wedding wale event me kaun gaya", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 7: Prepositional / inverted phrasing 'wedding event of sharma me kaun hai'")
  void scenario7_weddingEventOfSharmaMeKaunHai() {
    var response = eveService.query(new EveDtos.QueryRequest("wedding event of sharma me kaun hai", sessionId));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).doesNotContain("Royal");
    assertThat(response.message().content()).contains("Kabir Singh");
    assertThat(response.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 8: Further Hinglish / Typo combinations")
  void scenario8_furtherCombinations() {
    // 8a: Interrogative fronted: "kon gaya sharma wedding me"
    var res1 = eveService.query(new EveDtos.QueryRequest("kon gaya sharma wedding me", UUID.randomUUID()));
    assertThat(res1.status()).isEqualTo("COMPLETED");
    assertThat(res1.message().content()).contains("Kabir Singh");

    // 8b: "who went to shamra wedding"
    var res2 = eveService.query(new EveDtos.QueryRequest("who went to shamra wedding", UUID.randomUUID()));
    assertThat(res2.status()).isEqualTo("COMPLETED");
    assertThat(res2.message().content()).contains("Kabir Singh");
  }

  @Test
  @DisplayName("Scenario 9: Multi-turn clarification follow-up preserves structured state and resolves")
  void scenario9_multiTurnClarificationFollowUp() {
    // Turn 1: User asks for an ambiguous production ("who is assigned to royal?")
    var turn1 = eveService.query(new EveDtos.QueryRequest("who is assigned to royal?", sessionId));
    assertThat(turn1.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(turn1.candidates()).hasSize(2);
    assertThat(turn1.candidates()).extracting(EveDtos.CandidateView::displayName).contains("Royal Wedding", "Royal Gala");
    assertThat(turn1.message().content()).contains("Royal");

    // Turn 2: User clarifies by mentioning "Royal Wedding wala"
    var turn2 = eveService.query(new EveDtos.QueryRequest("Royal Wedding wala", sessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Kabir Singh");
    assertThat(turn2.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Royal Wedding");

    // Verify subsequent query on a new session where user answers with full Hinglish typo query
    UUID session2 = UUID.randomUUID();
    var ambTurn1 = eveService.query(new EveDtos.QueryRequest("who is assigned to royal?", session2));
    assertThat(ambTurn1.status()).isEqualTo("CLARIFICATION_REQUIRED");

    // Turn 2: User provides "shamra wedding ke event me kon gaya he"
    var ambTurn2 = eveService.query(new EveDtos.QueryRequest("shamra wedding ke event me kon gaya he", session2));
    assertThat(ambTurn2.status()).isEqualTo("COMPLETED");
    assertThat(ambTurn2.message().content()).contains("Kabir Singh");
    assertThat(ambTurn2.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Sharma Wedding");
  }

  @Test
  @DisplayName("Scenario 10: Unrelated topic switch after clarification")
  void scenario10_unrelatedTopicSwitchAfterClarification() {
    // Turn 1: User asks ambiguous query triggering clarification
    var turn1 = eveService.query(new EveDtos.QueryRequest("who is assigned to royal?", sessionId));
    assertThat(turn1.status()).isEqualTo("CLARIFICATION_REQUIRED");

    // Turn 2: User completely switches topic to Employee Finance: "Kabir Singh ko kitna dena hai?"
    var turn2 = eveService.query(new EveDtos.QueryRequest("Kabir Singh ko kitna dena hai?", sessionId));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).doesNotContain("Royal");
    assertThat(turn2.message().content()).contains("Kabir Singh");
    assertThat(turn2.message().content()).contains("15,000"); // 15000 pending balance
    assertThat(turn2.context().referencedEntities()).extracting(EveDtos.EntityReference::name).contains("Kabir Singh");
  }
}
