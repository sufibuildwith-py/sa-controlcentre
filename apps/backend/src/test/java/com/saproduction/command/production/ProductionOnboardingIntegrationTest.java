package com.saproduction.command.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProductionOnboardingIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
    properties.add("app.demo-seed", () -> false);
    properties.add("app.mode", () -> "test");
    properties.add("app.messaging.worker-enabled", () -> false);
    properties.add("app.navigator.enabled", () -> false);
  }

  @Autowired ProductionOnboardingService onboardingService;
  @Autowired ProductionService productionService;
  @Autowired FinancePostingService postingService;
  @Autowired FinanceReadService readService;
  @Autowired JdbcTemplate jdbc;

  private static final LocalDate DATE = LocalDate.of(2026, 10, 15);

  private ProductionOnboardingRequest buildRequest(
      String title,
      BigDecimal contractAmount,
      BigDecimal advanceAmount,
      String receiverAccount,
      UUID contractKey,
      UUID advanceKey) {
    return new ProductionOnboardingRequest(
        title,
        "Client " + title,
        "Operational notes for " + title,
        DATE,
        null,
        null,
        "Grand Palace",
        "Residency Road",
        Production.Priority.NORMAL,
        0,
        List.of(),
        List.of(),
        List.of(),
        new ProductionOnboardingRequest.ContractInput(
            contractKey != null ? contractKey : UUID.randomUUID(),
            contractAmount,
            DATE,
            "Contract for " + title),
        advanceAmount != null
            ? new ProductionOnboardingRequest.AdvanceInput(
                advanceKey != null ? advanceKey : UUID.randomUUID(),
                advanceAmount,
                DATE,
                receiverAccount,
                "Advance for " + title)
            : null);
  }

  @Test
  void testA_createProductionWithContractAndZeroAdvance() {
    var req = buildRequest("Test A Gala", new BigDecimal("100000.00"), BigDecimal.ZERO, null, null, null);
    var view = onboardingService.onboard(req);

    assertThat(view).isNotNull();
    assertThat(view.title()).isEqualTo("Test A Gala");

    // Production exists
    assertThat(jdbc.queryForObject("SELECT count(*) FROM productions WHERE id=?", Long.class, view.id()))
        .isEqualTo(1L);

    // Contract exists in canonical finance profile and transactions
    BigDecimal profileContract =
        jdbc.queryForObject(
            "SELECT contracted_amount FROM finance_production_profiles WHERE production_id=?",
            BigDecimal.class,
            view.id());
    assertThat(profileContract).isEqualByComparingTo("100000.00");

    Long contractTxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM finance_transactions WHERE production_id=? AND transaction_type='PRODUCTION_CONTRACT' AND status='POSTED'",
            Long.class,
            view.id());
    assertThat(contractTxCount).isEqualTo(1L);

    // No receipt transaction
    Long receiptTxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM finance_transactions WHERE production_id=? AND transaction_type='PRODUCTION_RECEIPT' AND status='POSTED'",
            Long.class,
            view.id());
    assertThat(receiptTxCount).isZero();

    // Canonical FinanceReadService read model
    var financeSummary = readService.production(view.id());
    assertThat((BigDecimal) financeSummary.get("received")).isEqualByComparingTo("0.00");
    assertThat((BigDecimal) financeSummary.get("outstanding")).isEqualByComparingTo("100000.00");
    assertThat((BigDecimal) financeSummary.get("contractedMargin")).isEqualByComparingTo("100000.00");
  }

  @Test
  void testB_createProductionWithContractAndPartialAdvance() {
    var req =
        buildRequest(
            "Test B Wedding",
            new BigDecimal("100000.00"),
            new BigDecimal("25000.00"),
            "AZ-2",
            null,
            null);
    var view = onboardingService.onboard(req);

    assertThat(view).isNotNull();

    // Both contract and receipt exist
    Long contractTxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM finance_transactions WHERE production_id=? AND transaction_type='PRODUCTION_CONTRACT' AND status='POSTED'",
            Long.class,
            view.id());
    assertThat(contractTxCount).isEqualTo(1L);

    Long receiptTxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM finance_transactions WHERE production_id=? AND transaction_type='PRODUCTION_RECEIPT' AND status='POSTED'",
            Long.class,
            view.id());
    assertThat(receiptTxCount).isEqualTo(1L);

    // Receipt allocation
    BigDecimal allocation =
        jdbc.queryForObject(
            "SELECT amount FROM finance_production_receipt_allocations WHERE production_id=?",
            BigDecimal.class,
            view.id());
    assertThat(allocation).isEqualByComparingTo("25000.00");

    // Canonical FinanceReadService read model
    var financeSummary = readService.production(view.id());
    assertThat((BigDecimal) financeSummary.get("received")).isEqualByComparingTo("25000.00");
    assertThat((BigDecimal) financeSummary.get("outstanding")).isEqualByComparingTo("75000.00");
  }

  @Test
  void testC_createProductionWithContractAndFullAdvance() {
    var req =
        buildRequest(
            "Test C Concert",
            new BigDecimal("100000.00"),
            new BigDecimal("100000.00"),
            "AK-2",
            null,
            null);
    var view = onboardingService.onboard(req);

    assertThat(view).isNotNull();

    var financeSummary = readService.production(view.id());
    assertThat((BigDecimal) financeSummary.get("received")).isEqualByComparingTo("100000.00");
    assertThat((BigDecimal) financeSummary.get("outstanding")).isEqualByComparingTo("0.00");
  }

  @Test
  void testD_createProductionWithAdvanceExceedingContractIsRejected() {
    var req =
        buildRequest(
            "Test D Overcollected",
            new BigDecimal("100000.00"),
            new BigDecimal("100001.00"),
            "AZ-2",
            null,
            null);

    assertThatThrownBy(() -> onboardingService.onboard(req))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exceed");

    // Verify atomic rollback: no production or finance rows were committed
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM productions WHERE title='Test D Overcollected'", Long.class);
    assertThat(count).isZero();
  }

  @Test
  void testE_duplicateRetryRequestIsIdempotentWithoutDuplicates() {
    UUID contractKey = UUID.randomUUID();
    UUID advanceKey = UUID.randomUUID();

    var req =
        buildRequest(
            "Test E Idempotent",
            new BigDecimal("120000.00"),
            new BigDecimal("40000.00"),
            "AZ-2",
            contractKey,
            advanceKey);

    var first = onboardingService.onboard(req);
    var second = onboardingService.onboard(req);

    // Must return the exact same production
    assertThat(second.id()).isEqualTo(first.id());

    // Exactly 1 production, 1 contract, 1 receipt
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM productions WHERE title='Test E Idempotent'", Long.class))
        .isEqualTo(1L);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance_transactions WHERE idempotency_key=?",
                Long.class,
                contractKey))
        .isEqualTo(1L);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance_transactions WHERE idempotency_key=?",
                Long.class,
                advanceKey))
        .isEqualTo(1L);
  }

  @Test
  void testF_financeFailureDuringOnboardingRollsBackProductionAtomically() {
    // Missing receiver account when advance > 0
    var req =
        buildRequest(
            "Test F Failure",
            new BigDecimal("100000.00"),
            new BigDecimal("50000.00"),
            null, // missing receiver account!
            null,
            null);

    assertThatThrownBy(() -> onboardingService.onboard(req))
        .isInstanceOf(ApiException.class);

    // Verify zero trace of the production
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM productions WHERE title='Test F Failure'", Long.class);
    assertThat(count).isZero();
  }

  @Test
  void testG_existingProductionDetailSetContractStillWorks() {
    // Create an operational production directly (simulating pre-existing or direct flow)
    var input =
        new ProductionService.CreateInput(
            "Test G Legacy",
            "Client G",
            null,
            DATE,
            null,
            null,
            "Venue G",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    var prod = productionService.create(input);

    // Set contract via canonical FinancePostingService
    UUID key = UUID.randomUUID();
    postingService.contract(
        new FinanceCommands.Contract(
            key, prod.id(), new BigDecimal("80000.00"), DATE, "Contract G"));

    var summary = readService.production(prod.id());
    assertThat((BigDecimal) summary.get("received")).isEqualByComparingTo("0.00");
    assertThat((BigDecimal) summary.get("outstanding")).isEqualByComparingTo("80000.00");
  }

  @Test
  void testH_existingRecordReceiptStillWorksAndEnforcesOutstanding() {
    var req = buildRequest("Test H Receipts", new BigDecimal("100000.00"), BigDecimal.ZERO, null, null, null);
    var view = onboardingService.onboard(req);

    // Record receipt 1: 25000
    postingService.receipt(
        new FinanceCommands.Receipt(
            UUID.randomUUID(),
            view.id(),
            new BigDecimal("25000.00"),
            DATE,
            "Receipt 1",
            "AZ-2",
            null,
            "ADD"));

    var summary = readService.production(view.id());
    assertThat((BigDecimal) summary.get("received")).isEqualByComparingTo("25000.00");
    assertThat((BigDecimal) summary.get("outstanding")).isEqualByComparingTo("75000.00");

    // Attempt receipt of 80000 (exceeds outstanding of 75000) -> MUST BE REJECTED
    assertThatThrownBy(
            () ->
                postingService.receipt(
                    new FinanceCommands.Receipt(
                        UUID.randomUUID(),
                        view.id(),
                        new BigDecimal("80000.00"),
                        DATE,
                        "Over receipt",
                        "AK-2",
                        null,
                        "ADD")))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("RECEIPT_EXCEEDS_OUTSTANDING");

    // Receipt of 75000 succeeds
    postingService.receipt(
        new FinanceCommands.Receipt(
            UUID.randomUUID(),
            view.id(),
            new BigDecimal("75000.00"),
            DATE,
            "Receipt 2",
            "AK-2",
            null,
            "ADD"));

    summary = readService.production(view.id());
    assertThat((BigDecimal) summary.get("received")).isEqualByComparingTo("100000.00");
    assertThat((BigDecimal) summary.get("outstanding")).isEqualByComparingTo("0.00");
  }

  @Test
  void testI_financeProductionReadEndpointReturnsExactCanonicalNumbers() {
    var req =
        buildRequest(
            "Test I Read Model",
            new BigDecimal("150000.00"),
            new BigDecimal("50000.00"),
            "AZ-2",
            null,
            null);
    var view = onboardingService.onboard(req);

    var res = readService.production(view.id());
    @SuppressWarnings("unchecked")
    var base = (java.util.Map<String, Object>) res.get("production");
    assertThat((BigDecimal) base.get("contracted")).isEqualByComparingTo("150000.00");
    assertThat((BigDecimal) res.get("received")).isEqualByComparingTo("50000.00");
    assertThat((BigDecimal) res.get("outstanding")).isEqualByComparingTo("100000.00");
    assertThat((BigDecimal) res.get("incurredExpense")).isEqualByComparingTo("0.00");
    assertThat((BigDecimal) res.get("contractedMargin")).isEqualByComparingTo("150000.00");
    assertThat((BigDecimal) res.get("realizedMargin")).isEqualByComparingTo("50000.00");
  }
}
