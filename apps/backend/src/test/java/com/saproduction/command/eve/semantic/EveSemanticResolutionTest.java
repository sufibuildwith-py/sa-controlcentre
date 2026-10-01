package com.saproduction.command.eve.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.EveRetrievalRouter;
import com.saproduction.command.eve.EveRetrievalService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveSemanticResolutionTest {

  private ProductionRepository productionRepo;
  private ProductionMemberRepository memberRepo;
  private EmployeeRepository employeeRepo;
  private EmployeeService employeeService;
  private TestEmbeddingProvider embeddingProvider;
  private TestRerankerProvider rerankerProvider;
  private EveSemanticCache cache;
  private EveSemanticPolicy policy;
  private EveSemanticResolutionService semanticService;
  private EveRetrievalService retrievalService;

  private UUID mipsProdId;
  private UUID annualGalaId;
  private UUID kabirEmpId;

  @BeforeEach
  void setUp() {
    productionRepo = mock(ProductionRepository.class);
    memberRepo = mock(ProductionMemberRepository.class);
    employeeRepo = mock(EmployeeRepository.class);
    employeeService = mock(EmployeeService.class);

    embeddingProvider = new TestEmbeddingProvider();
    rerankerProvider = new TestRerankerProvider();
    cache = new EveSemanticCache();
    policy = new EveSemanticPolicy(0.20, 0.05, 20);

    semanticService = new EveSemanticResolutionService(
        embeddingProvider,
        rerankerProvider,
        cache,
        policy,
        productionRepo,
        memberRepo,
        employeeService,
        true);

    retrievalService = new EveRetrievalService(
        employeeRepo,
        employeeService,
        null,
        productionRepo,
        semanticService);

    // Setup fixture data
    mipsProdId = UUID.randomUUID();
    Production mipsProd = new Production();
    mipsProd.id = mipsProdId;
    mipsProd.title = "Cultural Event MIPS";
    mipsProd.clientName = "MIPS College";
    mipsProd.eventDate = LocalDate.of(2026, 10, 15);
    mipsProd.venueName = "Main Auditorium";
    mipsProd.status = Production.Status.PLANNING;
    mipsProd.updatedAt = Instant.now();

    annualGalaId = UUID.randomUUID();
    Production annualGala = new Production();
    annualGala.id = annualGalaId;
    annualGala.title = "Annual Corporate Gala";
    annualGala.clientName = "Apex Industries";
    annualGala.eventDate = LocalDate.of(2026, 11, 20);
    annualGala.venueName = "Grand Ballroom";
    annualGala.status = Production.Status.PLANNING;
    annualGala.updatedAt = Instant.now();

    when(productionRepo.findAll()).thenReturn(List.of(mipsProd, annualGala));
    when(productionRepo.findById(mipsProdId)).thenReturn(Optional.of(mipsProd));
    when(productionRepo.findById(annualGalaId)).thenReturn(Optional.of(annualGala));

    kabirEmpId = UUID.randomUUID();
    Employee kabir = new Employee();
    kabir.id = kabirEmpId;
    kabir.employeeCode = "SA-101";
    kabir.displayName = "Kabir Khan";
    kabir.firstName = "Kabir";
    kabir.roleTitle = "Cinematographer";
    kabir.status = Employee.Status.ACTIVE;
    kabir.updatedAt = Instant.now();

    when(employeeRepo.findAll()).thenReturn(List.of(kabir));
    when(employeeRepo.findById(kabirEmpId)).thenReturn(Optional.of(kabir));

    EmployeeDtos.View kabirView = new EmployeeDtos.View(
        kabirEmpId, "SA-101", "Kabir", "Khan", "Kabir Khan", "+919876543210", null, null,
        "Cinematographer", "Camera", "FULL_TIME", LocalDate.of(2024, 1, 1),
        5000000L, "INR", Employee.Status.ACTIVE, null, null, Instant.now(), Instant.now(), null, 1L, 1L);
    when(employeeService.list(null, null)).thenReturn(List.of(kabirView));
  }

  @Test
  @DisplayName("Resolves colloquial Hindi reference 'Mips wala event' to Cultural Event MIPS")
  void testColloquialHindiReferenceResolution() {
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("Mips wala event", null);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(mipsProdId);
    assertThat(result.resolved().displayName()).isEqualTo("Cultural Event MIPS");
    assertThat(result.provenance().matchMethod()).isEqualTo(EveRetrievalService.MatchMethod.SEMANTIC_RERANKED);
  }

  @Test
  @DisplayName("Resolves typo reference 'culturl evnt mips' to Cultural Event MIPS")
  void testTypoReferenceResolution() {
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("culturl evnt mips", null);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(mipsProdId);
    assertThat(result.resolved().displayName()).isEqualTo("Cultural Event MIPS");
  }

  @Test
  @DisplayName("Relationship resolution: 'Kabir wala production' traverses employee member to assigned production")
  void testRelationshipClueResolution() {
    ProductionMember pm = new ProductionMember();
    pm.productionId = mipsProdId;
    pm.employeeId = kabirEmpId;
    pm.productionRole = "Lead Camera";

    when(memberRepo.findByEmployeeId(kabirEmpId)).thenReturn(List.of(pm));

    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("Kabir wala production", null);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(mipsProdId);
    assertThat(result.resolved().displayName()).isEqualTo("Cultural Event MIPS");
  }

  @Test
  @DisplayName("Ambiguity enforcement: when multiple candidates score too close within margin, returns AMBIGUOUS")
  void testAmbiguityPreservedWhenMarginTooSmall() {
    UUID gala1Id = UUID.randomUUID();
    Production gala1 = new Production();
    gala1.id = gala1Id;
    gala1.title = "Annual Gala Season 1";
    gala1.clientName = "Gala Corp";
    gala1.venueName = "Ballroom A";
    gala1.updatedAt = Instant.now();

    UUID gala2Id = UUID.randomUUID();
    Production gala2 = new Production();
    gala2.id = gala2Id;
    gala2.title = "Annual Gala Season 2";
    gala2.clientName = "Gala Corp";
    gala2.venueName = "Ballroom B";
    gala2.updatedAt = Instant.now();

    when(productionRepo.findAll()).thenReturn(List.of(gala1, gala2));

    // A query matching both equally
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("Annual Gala Corp", null);

    // Because gala1 and gala2 have virtually identical textual features, margin < 0.05
    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.AMBIGUOUS);
    assertThat(result.candidates()).hasSize(2);
  }

  @Test
  @DisplayName("Nonexistent query returns NOT_FOUND")
  void testNonexistentEntityReturnsNotFound() {
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("xyz totally unrelated non-existent 123", null);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Pronoun bypass: pure pronoun is never queried through semantic resolution")
  void testPronounBypass() {
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("uska", null);
    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.NOT_FOUND);

    EveRetrievalService.ResolutionResult resultEmp = semanticService.resolveEmployee("unka", null);
    assertThat(resultEmp.status()).isEqualTo(EveRetrievalService.ResolutionStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Active conversation focus boosts candidate resolving ambiguous continuation")
  void testActiveFocusAffinity() {
    EveRetrievalRouter.SessionContext sessionContext = new EveRetrievalRouter.SessionContext();
    EveRetrievalService.Candidate candidate = new EveRetrievalService.Candidate(
        mipsProdId, "PRODUCTION", "Cultural Event MIPS", null, "Main Auditorium");
    sessionContext.setLastReferencedProduction(candidate);

    // Generic continuation query that could apply to multiple
    EveRetrievalService.ResolutionResult result = semanticService.resolveProduction("event details", sessionContext);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(mipsProdId);
  }

  @Test
  @DisplayName("Cache invalidation: updating canonical updatedAt recomputes embedding cache")
  void testCacheInvalidation() {
    Instant t1 = Instant.ofEpochMilli(1000);
    Instant t2 = Instant.ofEpochMilli(2000);

    float[] v1 = new float[]{0.1f, 0.2f};
    cache.put(mipsProdId, v1, t1, "Production: Cultural Event MIPS");
    assertThat(cache.size()).isEqualTo(1);

    // Same id and timestamp hits cache
    Optional<float[]> cached = cache.get(mipsProdId, t1, "Production: Cultural Event MIPS");
    assertThat(cached).isPresent();
    assertThat(cached.get()[0]).isEqualTo(0.1f);

    // Updated candidate with newer timestamp invalidates old entry
    Optional<float[]> invalidatedByTime = cache.get(mipsProdId, t2, "Production: Cultural Event MIPS");
    assertThat(invalidatedByTime).isEmpty();
    assertThat(cache.size()).isEqualTo(0);

    // Repopulate and test representation hash invalidation
    cache.put(mipsProdId, v1, t1, "Production: Cultural Event MIPS");
    Optional<float[]> invalidatedByText = cache.get(mipsProdId, t1, "Production: Cultural Event MIPS Renamed");
    assertThat(invalidatedByText).isEmpty();
  }

  @Test
  @DisplayName("Integration: EveRetrievalService delegates to semantic resolution when exact match fails")
  void testRetrievalServiceDelegation() {
    EveRetrievalService.ResolutionResult result = retrievalService.resolveProduction("Mips wala event", null);

    assertThat(result.status()).isEqualTo(EveRetrievalService.ResolutionStatus.RESOLVED);
    assertThat(result.resolved().id()).isEqualTo(mipsProdId);
    assertThat(result.resolved().displayName()).isEqualTo("Cultural Event MIPS");
  }
}
