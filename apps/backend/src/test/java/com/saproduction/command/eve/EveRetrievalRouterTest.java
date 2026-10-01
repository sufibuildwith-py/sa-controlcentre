package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.production.ProductionService;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EveRetrievalRouterTest {

  private EveRetrievalService retrievalService;
  private FinanceReadService financeReads;
  private ProductionService productionService;
  private ProductionRepository productionRepo;
  private ProductionMemberRepository memberRepo;
  private EmployeeService employeeService;
  private WorkTaskService taskService;
  private HeadquartersService headquartersService;
  private EveMemoryService memoryService;
  private EveRetrievalRouter router;

  @BeforeEach
  void setUp() {
    retrievalService = mock(EveRetrievalService.class);
    financeReads = mock(FinanceReadService.class);
    productionService = mock(ProductionService.class);
    productionRepo = mock(ProductionRepository.class);
    memberRepo = mock(ProductionMemberRepository.class);
    employeeService = mock(EmployeeService.class);
    taskService = mock(WorkTaskService.class);
    headquartersService = mock(HeadquartersService.class);
    memoryService = mock(EveMemoryService.class);

    router = new EveRetrievalRouter(
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
  }

  @Test
  void routesEmployeeFinanceRead() {
    UUID empId = UUID.randomUUID();
    EveRetrievalService.Candidate emp = new EveRetrievalService.Candidate(
        empId, "EMPLOYEE", "Raj Sharma", "SA-01", "Audio");
    when(retrievalService.resolveEmployee("Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(emp, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));

    when(financeReads.employee(empId))
        .thenReturn(Map.of(
            "earned", new BigDecimal("100000.00"),
            "paid", new BigDecimal("60000.00"),
            "outstanding", new BigDecimal("40000.00")));

    EveRetrievalRouter.SessionContext ctx = new EveRetrievalRouter.SessionContext();
    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "Sharma"),
        ctx,
        "How much does Sharma still need?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("40,000.00 outstanding");
    assertThat(ctx.getLastReferencedEmployee()).isEqualTo(emp);
  }

  @Test
  void routesProductionCrewRead() {
    UUID prodId = UUID.randomUUID();
    EveRetrievalService.Candidate prod = new EveRetrievalService.Candidate(
        prodId, "PRODUCTION", "Royal Wedding", "PROD-01", "Grand Palace");
    when(retrievalService.resolveProduction("Royal"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prod, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));

    ProductionService.MemberView m1 = new ProductionService.MemberView(
        UUID.randomUUID(), UUID.randomUUID(), "Raj Sharma", "Lead Sound", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.MemberView m2 = new ProductionService.MemberView(
        UUID.randomUUID(), UUID.randomUUID(), "Amit Kumar", "Lighting", true, ProductionMember.Status.CONFIRMED, false, null);

    ProductionService.View view = new ProductionService.View(
        prodId, "Royal Wedding", "Client A", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(m1, m2), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(view);

    EveRetrievalRouter.SessionContext ctx = new EveRetrievalRouter.SessionContext();
    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CREW, "PRODUCTION", "Royal"),
        ctx,
        "Royal mein kaun gaya tha?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Royal Wedding has 2 assigned crew members");
    assertThat(result.answer()).contains("Raj Sharma (Lead Sound)");
    assertThat(result.answer()).contains("Amit Kumar (Lighting)");
    assertThat(ctx.getLastReferencedProduction()).isEqualTo(prod);
  }

  @Test
  void routesCheckProductionMember() {
    UUID prodId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();
    EveRetrievalService.Candidate prod = new EveRetrievalService.Candidate(
        prodId, "PRODUCTION", "Royal Wedding", "PROD-01", "Grand Palace");
    EveRetrievalService.Candidate emp = new EveRetrievalService.Candidate(
        empId, "EMPLOYEE", "Raj Sharma", "SA-01", "Audio");

    EveRetrievalRouter.SessionContext ctx = new EveRetrievalRouter.SessionContext();
    ctx.setLastReferencedProduction(prod);

    when(retrievalService.resolveEmployee("Sharma"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(emp, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));

    ProductionService.MemberView m = new ProductionService.MemberView(
        UUID.randomUUID(), empId, "Raj Sharma", "Lead Sound", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.View view = new ProductionService.View(
        prodId, "Royal Wedding", "Client A", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(m), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(view);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.crossDomain(EveModelProvider.Intent.CHECK_PRODUCTION_MEMBER, "PRODUCTION", "usme", "Sharma"),
        ctx,
        "Usme Sharma bhi tha?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Yes, Raj Sharma was assigned to Royal Wedding as Lead Sound.");
  }

  @Test
  void routesProductionEquipmentRead() {
    UUID prodId = UUID.randomUUID();
    EveRetrievalService.Candidate prod = new EveRetrievalService.Candidate(
        prodId, "PRODUCTION", "Royal Wedding", "PROD-01", "Grand Palace");

    EveRetrievalRouter.SessionContext ctx = new EveRetrievalRouter.SessionContext();
    ctx.setLastReferencedProduction(prod);

    when(retrievalService.resolveProduction(anyString()))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(prod, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "Royal"));

    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "LED Par Cans", "DEMO-HQ-001", new BigDecimal("4"), "pcs", "RESERVED");
    ProductionService.View view = new ProductionService.View(
        prodId, "Royal Wedding", "Client A", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(), 0, List.of(eq), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(view);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", "uska", true),
        ctx,
        "Aur uska equipment?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Royal Wedding has 1 reserved equipment item: 4x LED Par Cans (DEMO-HQ-001)");
  }

  @Test
  void routesWorkTasksSummaryRead() {
    WorkTaskService.View t1 = new WorkTaskService.View(
        UUID.randomUUID(), null, null, null, "Check sound board cables", null, UUID.randomUUID(), "Raj Sharma",
        WorkTask.Status.IN_PROGRESS, WorkTask.Priority.HIGH, null, null, null, 50, false, List.of(), Instant.now(), Instant.now());

    when(taskService.list(null, null, null, null, null, null, null)).thenReturn(List.of(t1));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_TASKS_SUMMARY, "WORK", "Task"),
        new EveRetrievalRouter.SessionContext(),
        "Kaunsa task abhi open hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("There are currently 1 open tasks");
    assertThat(result.answer()).contains("Check sound board cables");
  }

  @Test
  void routesEquipmentAvailabilityRead() {
    when(headquartersService.equipment(eq(0), eq(5), eq("Stand"), isNull(), isNull()))
        .thenReturn(Map.of("items", List.of(Map.of(
            "name", "C-Stands Heavy Duty",
            "internalCode", "DEMO-HQ-007",
            "usable", new BigDecimal("10"),
            "reserved", new BigDecimal("2")))));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EQUIPMENT_AVAILABILITY, "EQUIPMENT", "Stand"),
        new EveRetrievalRouter.SessionContext(),
        "Stand kitna available hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("C-Stands Heavy Duty (DEMO-HQ-007)");
    assertThat(result.answer()).contains("10 usable units");
    assertThat(result.answer()).contains("2 reserved");
    assertThat(result.answer()).contains("8 currently available");
  }

  @Test
  void handlesAmbiguityAndDisambiguationFollowUp() {
    EveRetrievalService.Candidate c1 = new EveRetrievalService.Candidate(UUID.randomUUID(), "PRODUCTION", "Royal Wedding", "P-1", "2026-09-20");
    EveRetrievalService.Candidate c2 = new EveRetrievalService.Candidate(UUID.randomUUID(), "PRODUCTION", "Royal Gala", "P-2", "2026-09-27");

    when(retrievalService.resolveProduction("Royal"))
        .thenReturn(EveRetrievalService.ResolutionResult.ambiguous(List.of(c1, c2), "Royal"));

    EveRetrievalRouter.SessionContext ctx = new EveRetrievalRouter.SessionContext();

    // Turn 1: Ambiguity detected
    var turn1 = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION, "PRODUCTION", "Royal"),
        ctx,
        "Royal ka kya status hai?");

    assertThat(turn1.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(turn1.candidates()).hasSize(2);
    assertThat(ctx.hasPendingCandidates()).isTrue();

    // Turn 2: Follow-up selects candidate 2
    when(retrievalService.selectFromCandidates(eq(List.of(c1, c2)), eq("the second one")))
        .thenReturn(Optional.of(c2));

    ProductionService.View view = new ProductionService.View(
        c2.id(), "Royal Gala", "Client B", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PLANNING, Production.Priority.NORMAL, 20, null, List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(c2.id())).thenReturn(view);

    var turn2 = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.disambiguate("the second one"),
        ctx,
        "the second one");

    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.answer()).contains("Royal Gala");
    assertThat(ctx.hasPendingCandidates()).isFalse();
    assertThat(ctx.getLastReferencedProduction()).isEqualTo(c2);
  }

  @Test
  void routesVocabularyLearning() {
    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.vocabulary("Raju", "EMPLOYEE", "Raj Kumar"),
        new EveRetrievalRouter.SessionContext(),
        "Raju se mera matlab Raj Kumar hai");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Understood. I will remember that 'Raju' refers to Raj Kumar.");
    verify(memoryService).remember(argThat(r -> r.term().equals("Raju") && r.canonicalName().equals("Raj Kumar")), eq("OPERATOR_EXPLICIT"));
  }

  @Test
  void allowedMemoryLearning_whenExplicitlyInstructedWithMeansStoresVocabulary() {
    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.vocabulary("Raju", "EMPLOYEE", "Raj Kumar"),
        new EveRetrievalRouter.SessionContext(),
        "Raju means Raj Kumar.");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Understood. I will remember that 'Raju' refers to Raj Kumar.");
    verify(memoryService).remember(argThat(r -> r.term().equals("Raju") && r.canonicalName().equals("Raj Kumar")), eq("OPERATOR_EXPLICIT"));
  }

  @Test
  void forbiddenImplicitLearning_whenResolvingNormalQueryNoMemoryIsEverCreated() {
    UUID empId = UUID.randomUUID();
    EveRetrievalService.Candidate emp = new EveRetrievalService.Candidate(empId, "EMPLOYEE", "Raj Kumar", "SA-01", "Lead Sound");
    when(retrievalService.resolveEmployee("Raju"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(emp, EveRetrievalService.MatchMethod.MEMORY_HINT, "Raju"));
    when(financeReads.employee(empId))
        .thenReturn(Map.of("earned", BigDecimal.valueOf(10000), "paid", BigDecimal.valueOf(5000), "outstanding", BigDecimal.valueOf(5000)));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "Raju"),
        new EveRetrievalRouter.SessionContext(),
        "Show me Raju's pending payment");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Raj Kumar (SA-01)");
    // STRICT INVARIANT: Resolving a query based on evidence must NEVER silently create durable memory!
    verify(memoryService, never()).remember(any(), any());
  }

  @Test
  void staleMemory_doesNotOverrideCanonicalTruthWhenEntityNotFound() {
    when(retrievalService.resolveEmployee("Ghost"))
        .thenReturn(EveRetrievalService.ResolutionResult.notFound("Ghost"));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "Ghost"),
        new EveRetrievalRouter.SessionContext(),
        "How much does Ghost still need?");

    assertThat(result.status()).isEqualTo("NOT_FOUND");
    assertThat(result.answer()).contains("could not find any active records matching \"Ghost\"");
    verify(memoryService, never()).remember(any(), any());
  }

  @Test
  void readsProductionClient_whenClientRecorded_returnsGroundedAnswer() {
    UUID prodId = UUID.randomUUID();
    var candidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Cultural Event MIPS", "MIPS", "MIPS venue");
    when(retrievalService.resolveProduction("Cultural Event MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(candidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Cultural Event MIPS"));

    var prodView = new ProductionService.View(
        prodId, "Cultural Event MIPS", "Nandhini srivastava", "Cultural festival",
        LocalDate.of(2026, 9, 28), null, null, "MIPS", null,
        Production.Status.DRAFT, Production.Priority.NORMAL, 0, null,
        List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "Cultural Event MIPS"),
        new EveRetrievalRouter.SessionContext(),
        "cultural event MIPS ka client kon hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).isEqualTo("The client for Cultural Event MIPS is Nandhini srivastava.");
    assertThat(result.evidence()).anyMatch(e -> e.label().equals("Client") && e.value().equals("Nandhini srivastava"));
    assertThat(result.referencedEntities()).anyMatch(e -> e.id().equals(prodId) && e.type().equals("PRODUCTION"));
  }

  @Test
  void readsProductionClient_whenClientMissing_returnsExplicitNotRecorded() {
    UUID prodId = UUID.randomUUID();
    var candidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "MIPS Event", "MIPS", "Hall");
    when(retrievalService.resolveProduction("MIPS Event"))
        .thenReturn(EveRetrievalService.ResolutionResult.resolved(candidate, EveRetrievalService.MatchMethod.BOUNDED_SEARCH, "MIPS Event"));

    var prodView = new ProductionService.View(
        prodId, "MIPS Event", "", "Desc",
        LocalDate.of(2026, 9, 28), null, null, "Hall", null,
        Production.Status.DRAFT, Production.Priority.NORMAL, 0, null,
        List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "MIPS Event"),
        new EveRetrievalRouter.SessionContext(),
        "MIPS Event ka client kaun hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("I found MIPS Event, but I don't have a client recorded for it.");
    assertThat(result.evidence()).anyMatch(e -> e.label().equals("Client") && e.value().equals("Not recorded"));
  }

  @Test
  void readsProductionClient_whenProductionNotFound_returnsExplicitNotFound() {
    when(retrievalService.resolveProduction("UnknownFest"))
        .thenReturn(EveRetrievalService.ResolutionResult.notFound("UnknownFest"));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "UnknownFest"),
        new EveRetrievalRouter.SessionContext(),
        "UnknownFest ka client kaun hai?");

    assertThat(result.status()).isEqualTo("NOT_FOUND");
    assertThat(result.answer()).contains("I couldn't find a production/event matching \"UnknownFest\" in SA Command.");
  }

  @Test
  void readsProductionClient_whenAmbiguous_returnsClarificationRequired() {
    var c1 = new EveRetrievalService.Candidate(UUID.randomUUID(), "PRODUCTION", "MIPS Gala", "MIPS-1", "Venue 1");
    var c2 = new EveRetrievalService.Candidate(UUID.randomUUID(), "PRODUCTION", "MIPS Conference", "MIPS-2", "Venue 2");
    when(retrievalService.resolveProduction("MIPS"))
        .thenReturn(EveRetrievalService.ResolutionResult.ambiguous(List.of(c1, c2), "MIPS"));

    var context = new EveRetrievalRouter.SessionContext();
    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CLIENT, "PRODUCTION", "MIPS"),
        context,
        "MIPS ka client kon hai?");

    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.candidates()).hasSize(2);
    assertThat(result.answer()).contains("multiple matching productions for \"MIPS\"");
    assertThat(context.hasPendingCandidates()).isTrue();
  }

  @Test
  void readsProductionTasks_multiTurnPronoun_returnsOpenTasks() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Sharma Wedding", "SW", "Royal Orchid");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    var task1 = new WorkTaskService.View(
        UUID.randomUUID(), prodId, "Sharma Wedding", null, "Prepare camera bodies", "Check batteries",
        UUID.randomUUID(), "Rehan Ali", WorkTask.Status.TODO, WorkTask.Priority.HIGH,
        null, null, null, 0, false, List.of(), Instant.now(), Instant.now());
    var task2 = new WorkTaskService.View(
        UUID.randomUUID(), prodId, "Sharma Wedding", null, "Client review export", "Export draft",
        UUID.randomUUID(), "Amaan Khan", WorkTask.Status.IN_PROGRESS, WorkTask.Priority.NORMAL,
        null, null, null, 50, false, List.of(), Instant.now(), Instant.now());

    when(taskService.list(null, prodId, null, null, null, null, null))
        .thenReturn(List.of(task1, task2));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_TASKS, "PRODUCTION", "usme", true),
        context,
        "Usme kaunsa task open hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Sharma Wedding");
    assertThat(result.answer()).contains("Prepare camera bodies");
    assertThat(result.answer()).contains("Client review export");
    assertThat(result.evidence()).anyMatch(e -> e.label().equals("Production Open Tasks") && e.value().equals("2"));
  }

  @Test
  void readsProductionSchedule_multiTurnPronoun_returnsScheduledEvent() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Cultural Event MIPS", "MIPS", "MIPS Venue");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    var prodView = new ProductionService.View(
        prodId, "Cultural Event MIPS", "Nandhini srivastava", "Fest",
        LocalDate.of(2026, 9, 28), null, null, "MIPS Venue", null,
        Production.Status.DRAFT, Production.Priority.NORMAL, 0, null,
        List.of(), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION, "PRODUCTION", "uska", true),
        context,
        "Uska event kab hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Cultural Event MIPS");
    assertThat(result.answer()).contains("2026-09-28");
    assertThat(result.evidence()).anyMatch(e -> e.label().equals("Venue") && e.value().equals("MIPS Venue"));
  }

  @Test
  void readsProductionCrew_multiTurnPronoun_returnsAssignedCrew() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Cultural Event MIPS", "MIPS", "MIPS Venue");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    var member = new ProductionService.MemberView(
        UUID.randomUUID(), UUID.randomUUID(), "Farhan Akhtar", "Director", true, ProductionMember.Status.CONFIRMED, false, null);
    var prodView = new ProductionService.View(
        prodId, "Cultural Event MIPS", "Nandhini srivastava", "Fest",
        LocalDate.of(2026, 9, 28), null, null, "MIPS Venue", null,
        Production.Status.DRAFT, Production.Priority.NORMAL, 0, null,
        List.of(member), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_CREW, "PRODUCTION", "usme", true),
        context,
        "Usme kaun kaam kar raha hai?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Cultural Event MIPS has 1 assigned crew member");
    assertThat(result.answer()).contains("Farhan Akhtar (Director)");
  }

  @Test
  void readsEmployeeFinance_multiTurnPronounAfterProductionWithMultipleCrew_returnsClarificationRequired() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Royal Wedding", "ROYAL", "Royal Palace");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    UUID emp1Id = UUID.randomUUID();
    UUID emp2Id = UUID.randomUUID();
    var member1 = new ProductionService.MemberView(
        UUID.randomUUID(), emp1Id, "Farhan Akhtar", "Lead Photographer", true, ProductionMember.Status.CONFIRMED, false, null);
    var member2 = new ProductionService.MemberView(
        UUID.randomUUID(), emp2Id, "Sarah Jenkins", "Cinematographer", true, ProductionMember.Status.CONFIRMED, false, null);
    var prodView = new ProductionService.View(
        prodId, "Royal Wedding", "Client Royal", "Wedding",
        LocalDate.of(2026, 10, 15), null, null, "Royal Palace", null,
        Production.Status.PLANNING, Production.Priority.HIGH, 0, null,
        List.of(member1, member2), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var cand1 = new EveRetrievalService.Candidate(emp1Id, "EMPLOYEE", "Farhan Akhtar", "EMP-001", "Photographer");
    var cand2 = new EveRetrievalService.Candidate(emp2Id, "EMPLOYEE", "Sarah Jenkins", "EMP-002", "Cinematographer");
    when(retrievalService.resolveEmployee("Farhan Akhtar")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(cand1, EveRetrievalService.MatchMethod.EXACT_NAME, "Farhan Akhtar"));
    when(retrievalService.resolveEmployee("Sarah Jenkins")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(cand2, EveRetrievalService.MatchMethod.EXACT_NAME, "Sarah Jenkins"));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "him", true),
        context,
        "How much do we still owe him?");

    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.candidates()).hasSize(2);
    assertThat(result.candidates()).extracting(EveDtos.CandidateView::displayName)
        .containsExactlyInAnyOrder("Farhan Akhtar", "Sarah Jenkins");
    assertThat(context.hasPendingCandidates()).isTrue();
  }

  @Test
  void readsEmployeeFinance_multiTurnPronounAfterProductionWithSingleCrew_resolvesAutomatically() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Royal Wedding", "ROYAL", "Royal Palace");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    UUID empId = UUID.randomUUID();
    var member = new ProductionService.MemberView(
        UUID.randomUUID(), empId, "Farhan Akhtar", "Lead Photographer", true, ProductionMember.Status.CONFIRMED, false, null);
    var prodView = new ProductionService.View(
        prodId, "Royal Wedding", "Client Royal", "Wedding",
        LocalDate.of(2026, 10, 15), null, null, "Royal Palace", null,
        Production.Status.PLANNING, Production.Priority.HIGH, 0, null,
        List.of(member), 0, List.of(), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(prodView);

    var empCandidate = new EveRetrievalService.Candidate(empId, "EMPLOYEE", "Farhan Akhtar", "EMP-001", "Photographer");
    when(retrievalService.resolveEmployee("Farhan Akhtar")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(empCandidate, EveRetrievalService.MatchMethod.EXACT_NAME, "Farhan Akhtar"));
    when(financeReads.employee(empId)).thenReturn(Map.of(
        "earned", new BigDecimal("5000"),
        "paid", new BigDecimal("2000"),
        "outstanding", new BigDecimal("3000")));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", "him", true),
        context,
        "How much do we still owe him?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Farhan Akhtar");
    assertThat(result.answer()).contains("3,000");
    assertThat(context.getLastReferencedEmployee()).isEqualTo(empCandidate);
  }

  @Test
  void greeting_returnsCompleted_withoutAnyRetrievalInvocation() {
    String[] greetings = {"hey", "hi", "hello", "thanks", "thank you", "kya haal hai", "hey eve"};

    for (String g : greetings) {
      var result = router.routeAndRetrieve(
          EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.GREETING, null, null),
          new EveRetrievalRouter.SessionContext(),
          g);

      assertThat(result.status()).isEqualTo("COMPLETED");
      assertThat(result.answer()).isNotEmpty();
      assertThat(result.referencedEntities()).isEmpty();
    }

    // STRICT INVARIANT: Greetings must NEVER query canonical retrieval!
    verify(retrievalService, never()).resolveProduction(anyString());
    verify(retrievalService, never()).resolveEmployee(anyString());
  }

  @Test
  void pronounEquipmentQuery_withNoAntecedent_returnsClarificationRequired_andNeverQueriesDatabaseForPronoun() {
    var context = new EveRetrievalRouter.SessionContext();

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", "uska", true),
        context,
        "Aur uska equipment?");

    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.answer()).contains("Kis production ya event");
    assertThat(result.answer()).doesNotContain("matching \"uska\"");

    // STRICT INVARIANT: Pronoun token "uska" must NEVER be queried in canonical retrieval!
    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
    verify(retrievalService, never()).resolveEmployee(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  void pronounEquipmentQuery_withProductionAntecedent_resolvesEquipmentCorrectly() {
    UUID prodId = UUID.randomUUID();
    var prodCandidate = new EveRetrievalService.Candidate(prodId, "PRODUCTION", "Cultural Event MIPS", "MIPS", "Venue");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedProduction(prodCandidate);

    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "Audio Mixer 32ch", "MIX-01", new BigDecimal("1"), "unit", "RESERVED");
    ProductionService.View view = new ProductionService.View(
        prodId, "Cultural Event MIPS", "Nandhini", null, LocalDate.now(), null, null, "Venue", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(), 0, List.of(eq), Instant.now(), Instant.now());
    when(productionService.get(prodId)).thenReturn(view);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", "uska", true),
        context,
        "Aur uska equipment?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Cultural Event MIPS has 1 reserved equipment item");
    assertThat(result.answer()).contains("Audio Mixer 32ch");
    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  void pronounEquipmentQuery_withEmployeeAntecedentAssignedToSingleProduction_resolvesCrossDomain() {
    UUID empId = UUID.randomUUID();
    UUID prodId = UUID.randomUUID();
    var empCandidate = new EveRetrievalService.Candidate(empId, "EMPLOYEE", "Kabir Khan", "SA-02", "Cinematographer");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedEmployee(empCandidate);

    ProductionService.MemberView member = new ProductionService.MemberView(
        UUID.randomUUID(), empId, "Kabir Khan", "Cinematographer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.EquipmentView eq = new ProductionService.EquipmentView(
        UUID.randomUUID(), UUID.randomUUID(), "Sony FX6 Camera Kit", "CAM-01", new BigDecimal("2"), "pcs", "RESERVED");
    ProductionService.View view = new ProductionService.View(
        prodId, "Royal Wedding", "Client Royal", null, LocalDate.now(), null, null, "Grand Palace", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member), 0, List.of(eq), Instant.now(), Instant.now());

    when(productionService.list(null, null, null, null, null)).thenReturn(List.of(view));
    when(productionService.get(prodId)).thenReturn(view);

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", "uska", true),
        context,
        "Aur uska equipment?");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.answer()).contains("Royal Wedding");
    assertThat(result.answer()).contains("Sony FX6 Camera Kit");
    assertThat(context.getLastReferencedProduction()).isNotNull();
    assertThat(context.getLastReferencedProduction().displayName()).isEqualTo("Royal Wedding");
    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }

  @Test
  void pronounEquipmentQuery_withEmployeeAntecedentAssignedToMultipleProductions_returnsClarificationRequired() {
    UUID empId = UUID.randomUUID();
    var empCandidate = new EveRetrievalService.Candidate(empId, "EMPLOYEE", "Kabir Khan", "SA-02", "Cinematographer");
    var context = new EveRetrievalRouter.SessionContext();
    context.setLastReferencedEmployee(empCandidate);

    ProductionService.MemberView member = new ProductionService.MemberView(
        UUID.randomUUID(), empId, "Kabir Khan", "Cinematographer", true, ProductionMember.Status.CONFIRMED, false, null);
    ProductionService.View prod1 = new ProductionService.View(
        UUID.randomUUID(), "Royal Wedding", "Client A", null, LocalDate.now(), null, null, "Venue A", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member), 0, List.of(), Instant.now(), Instant.now());
    ProductionService.View prod2 = new ProductionService.View(
        UUID.randomUUID(), "Corporate Summit", "Client B", null, LocalDate.now(), null, null, "Venue B", null,
        Production.Status.PRODUCTION, Production.Priority.NORMAL, 50, null, List.of(member), 0, List.of(), Instant.now(), Instant.now());

    when(productionService.list(null, null, null, null, null)).thenReturn(List.of(prod1, prod2));

    var result = router.routeAndRetrieve(
        EveModelProvider.EveInterpretation.of(EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT, "PRODUCTION", "uska", true),
        context,
        "Aur uska equipment?");

    assertThat(result.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(result.answer()).contains("Kabir Khan is assigned to 2 productions");
    assertThat(result.candidates()).hasSize(2);
    verify(retrievalService, never()).resolveProduction(argThat(EveRetrievalRouter::isPronoun));
  }
}
