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
}
