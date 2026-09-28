package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EveRetrievalServiceTest {

  private EmployeeRepository employeeRepo;
  private EmployeeService employeeService;
  private EveMemoryService memoryService;
  private EveRetrievalService retrievalService;

  @BeforeEach
  void setUp() {
    employeeRepo = mock(EmployeeRepository.class);
    employeeService = mock(EmployeeService.class);
    memoryService = mock(EveMemoryService.class);
    retrievalService = new EveRetrievalService(employeeRepo, employeeService, memoryService);
  }

  @Test
  void resolvesByExactUuid() {
    UUID id = UUID.randomUUID();
    Employee emp = new Employee();
    emp.id = id;
    emp.employeeCode = "SA-01";
    emp.displayName = "Raj Sharma";
    emp.roleTitle = "Lead Sound Engineer";

    when(employeeRepo.findById(id)).thenReturn(Optional.of(emp));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee(id.toString());

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(id);
    assertThat(result.resolved().displayName()).isEqualTo("Raj Sharma");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.EXACT_ID);
  }

  @Test
  void resolvesByExactEmployeeCode() {
    Employee emp = new Employee();
    emp.id = UUID.randomUUID();
    emp.employeeCode = "SA-01";
    emp.displayName = "Raj Sharma";
    emp.roleTitle = "Lead Sound Engineer";

    when(employeeRepo.findByEmployeeCodeIgnoreCase(argThat(s -> s != null && s.equalsIgnoreCase("SA-01"))))
        .thenReturn(Optional.of(emp));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("sa-01");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().code()).isEqualTo("SA-01");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.EXACT_CODE);
  }

  @Test
  void resolvesByExactNormalizedDisplayNameWithWhitespaceAndCaseTolerance() {
    UUID id = UUID.randomUUID();
    EmployeeDtos.View view = new EmployeeDtos.View(
        id, "SA-01", "Raj", "Sharma", "Raj Sharma", "+919999999999", null, null,
        "Lead Sound Engineer", "Audio", "FULL_TIME", LocalDate.now(), 4500000L, "INR",
        Employee.Status.ACTIVE, null, null, Instant.now(), Instant.now(), null, 0L, 0L);

    when(employeeService.list(null, null)).thenReturn(List.of(view));

    // Notice unusual casing and excess whitespace
    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("   raJ   sHaRmA   ");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().displayName()).isEqualTo("Raj Sharma");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.EXACT_NAME);
  }

  @Test
  void test1_validMemoryAliasWithValidCanonicalEntityResolvesWithMemoryHintProvenance() {
    UUID id = UUID.randomUUID();
    Employee canonicalEmp = new Employee();
    canonicalEmp.id = id;
    canonicalEmp.employeeCode = "SA-01";
    canonicalEmp.displayName = "Raj Kumar";
    canonicalEmp.roleTitle = "Senior Tech";
    canonicalEmp.status = Employee.Status.ACTIVE;

    // Canonical search yields 0 matches for alias "Raju"
    when(employeeRepo.findById(any())).thenReturn(Optional.empty());
    when(employeeRepo.findByEmployeeCodeIgnoreCase(any())).thenReturn(Optional.empty());
    when(employeeService.list(any(), any())).thenReturn(List.of());

    // Memory contains alias "Raju" -> canonicalId
    when(memoryService.recall("Raju")).thenReturn(Optional.of(
        new EveDtos.MemoryView(UUID.randomUUID(), "VOCABULARY", "Raju", "EMPLOYEE", id, "Raj Kumar", 1.0, "OPERATOR", Instant.now(), Instant.now())));
    // Validated against canonical DB
    when(employeeRepo.findById(id)).thenReturn(Optional.of(canonicalEmp));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("Raju");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().displayName()).isEqualTo("Raj Kumar");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.MEMORY_HINT);
  }

  @Test
  void test2_staleMemoryAliasPointingToDeletedEntityReturnsNotFound() {
    UUID staleId = UUID.randomUUID();

    // Canonical search yields 0 matches
    when(employeeRepo.findById(any())).thenReturn(Optional.empty());
    when(employeeRepo.findByEmployeeCodeIgnoreCase(any())).thenReturn(Optional.empty());
    when(employeeService.list(any(), any())).thenReturn(List.of());

    // Memory has stale alias for "GhostMember" -> points to staleId
    when(memoryService.recall("GhostMember")).thenReturn(Optional.of(
        new EveDtos.MemoryView(UUID.randomUUID(), "VOCABULARY", "GhostMember", "EMPLOYEE", staleId, "Ghost Person", 1.0, "OPERATOR", Instant.now(), Instant.now())));
    // Canonical DB reports entity no longer exists
    when(employeeRepo.findById(staleId)).thenReturn(Optional.empty());

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("GhostMember");

    // Stale memory must NOT resolve or resurrect entity
    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.NOT_FOUND);
    assertThat(result.resolved()).isNull();
  }

  @Test
  void test3_memoryAliasPointingToEntityWhoseCanonicalDataChangedReturnsFreshCanonicalData() {
    UUID id = UUID.randomUUID();
    Employee canonicalEmp = new Employee();
    canonicalEmp.id = id;
    canonicalEmp.employeeCode = "SA-99";
    canonicalEmp.displayName = "Akash V2 Upgraded"; // Renamed in DB
    canonicalEmp.roleTitle = "Operations Director";
    canonicalEmp.status = Employee.Status.ACTIVE;

    when(employeeRepo.findByEmployeeCodeIgnoreCase(any())).thenReturn(Optional.empty());
    when(employeeService.list(any(), any())).thenReturn(List.of());

    // Memory still has old cached name "Akash Old"
    when(memoryService.recall("Aki")).thenReturn(Optional.of(
        new EveDtos.MemoryView(UUID.randomUUID(), "VOCABULARY", "Aki", "EMPLOYEE", id, "Akash Old", 1.0, "OPERATOR", Instant.now(), Instant.now())));
    when(employeeRepo.findById(id)).thenReturn(Optional.of(canonicalEmp));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("Aki");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    // Canonical data from DB wins over stale memory display name
    assertThat(result.resolved().displayName()).isEqualTo("Akash V2 Upgraded");
    assertThat(result.resolved().code()).isEqualTo("SA-99");
  }

  @Test
  void test4_conflictingMemoryHintVsCanonicalExactMatchAlwaysPrioritizesCanonicalExactMatch() {
    UUID canonicalId = UUID.randomUUID();
    UUID conflictingMemoryId = UUID.randomUUID();

    Employee exactMatch = new Employee();
    exactMatch.id = canonicalId;
    exactMatch.employeeCode = "SA-01";
    exactMatch.displayName = "Amaan Canonical";
    exactMatch.roleTitle = "Lead";

    // DB has exact code match for "SA-01"
    when(employeeRepo.findByEmployeeCodeIgnoreCase("SA-01")).thenReturn(Optional.of(exactMatch));

    // Memory has conflicting entry for "SA-01" pointing to another employee
    when(memoryService.recall("SA-01")).thenReturn(Optional.of(
        new EveDtos.MemoryView(UUID.randomUUID(), "VOCABULARY", "SA-01", "EMPLOYEE", conflictingMemoryId, "Someone Else", 1.0, "OPERATOR", Instant.now(), Instant.now())));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("SA-01");

    // Canonical exact code MUST win
    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(canonicalId);
    assertThat(result.resolved().displayName()).isEqualTo("Amaan Canonical");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.EXACT_CODE);
  }

  @Test
  void test5_detectsAmbiguityWhenMultipleCanonicalCandidatesMatchSearch() {
    EmployeeDtos.View v1 = new EmployeeDtos.View(
        UUID.randomUUID(), "SA-01", "Raj", "Sharma", "Raj Sharma", "+919999999991", null, null,
        "Lead Sound Engineer", "Audio", "FULL_TIME", LocalDate.now(), 4500000L, "INR",
        Employee.Status.ACTIVE, null, null, Instant.now(), Instant.now(), null, 0L, 0L);

    EmployeeDtos.View v2 = new EmployeeDtos.View(
        UUID.randomUUID(), "SA-02", "Amit", "Sharma", "Amit Sharma", "+919999999992", null, null,
        "Lighting Technician", "Lighting", "FULL_TIME", LocalDate.now(), 3500000L, "INR",
        Employee.Status.ACTIVE, null, null, Instant.now(), Instant.now(), null, 0L, 0L);

    when(employeeService.list(null, null)).thenReturn(List.of(v1, v2));
    when(employeeService.list("Sharma", null)).thenReturn(List.of(v1, v2));

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("Sharma");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.AMBIGUOUS);
    assertThat(result.candidates()).hasSize(2);
    assertThat(result.resolved()).isNull();
  }

  @Test
  void test6_modelAssistedCandidateSelectionIsStrictlyConstrainedToRealCandidates() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<EveRetrievalService.Candidate> candidates = List.of(
        new EveRetrievalService.Candidate(id1, "EMPLOYEE", "Raj Sharma", "SA-01", "Audio"),
        new EveRetrievalService.Candidate(id2, "EMPLOYEE", "Amit Sharma", "SA-02", "Lighting"));

    // Selection from within valid candidate list succeeds
    var selected = retrievalService.selectFromCandidates(candidates, "SA-02");
    assertThat(selected).isPresent();
    assertThat(selected.get().id()).isEqualTo(id2);

    // Attempting to select or invent an entity NOT in candidate list strictly fails
    var invented = retrievalService.selectFromCandidates(candidates, "Ghost Employee");
    assertThat(invented).isEmpty();

    var fabricatedId = retrievalService.selectFromCandidates(candidates, UUID.randomUUID().toString());
    assertThat(fabricatedId).isEmpty();
  }

  @Test
  void returnsNotFoundWhenNoMatchesExist() {
    when(employeeService.list(null, null)).thenReturn(List.of());
    when(employeeService.list("Nonexistent", null)).thenReturn(List.of());

    EveRetrievalService.ResolutionResult result = retrievalService.resolveEmployee("Nonexistent");

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.NOT_FOUND);
    assertThat(result.candidates()).isEmpty();
    assertThat(result.resolved()).isNull();
  }
}
