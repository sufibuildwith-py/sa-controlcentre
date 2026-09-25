package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.*;

import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Run against an isolated PostgreSQL database through FINANCE_TEST_DB_URL; never a live owner DB. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest
@TestPropertySource(properties = {"app.mode=test", "app.demo-seed=false", "app.messaging.worker-enabled=false", "app.navigator.enabled=false"})
class FinancePostingIntegrationTest {
  private static final LocalDate DATE = LocalDate.of(2026, 9, 24);
  @Autowired FinancePostingService posting;
  @Autowired FinanceReadService reads;
  @Autowired FinanceMigrationService migration;
  @Autowired JdbcTemplate jdbc;

  @Test
  void productionPositionIsReceivedLessExpenseAndRetryDoesNotRepost() {
    UUID production = production();
    posting.contract(new FinanceCommands.Contract(UUID.randomUUID(), production, bd("1000"), DATE, "Contract"));
    var receipt = new FinanceCommands.Receipt(UUID.randomUUID(), production, bd("300"), DATE, "Advance", "AZ-2", null, "ADD");
    UUID first = (UUID) posting.receipt(receipt).get("id");
    assertThat(posting.receipt(receipt).get("id")).isEqualTo(first);
    posting.expense(new FinanceCommands.Expense(UUID.randomUUID(), bd("100"), DATE, "Transport", "AK-2", production, null, null, null, "TRANSPORT"));
    var summary = reads.production(production);
    assertThat(summary.get("received")).isEqualTo(bd("300.00"));
    assertThat(summary.get("outstanding")).isEqualTo(bd("700.00"));
    assertThat(summary.get("realizedMargin")).isEqualTo(bd("200.00"));
    assertThat(summary.get("contractedMargin")).isEqualTo(bd("900.00"));
    assertThat(reads.reconciliation().get("status")).isEqualTo("RECONCILED");
  }

  @Test
  void sameRequestIdWithDifferentFinancialPayloadConflicts() {
    UUID production = production();
    UUID key = UUID.randomUUID();
    posting.contract(new FinanceCommands.Contract(key, production, bd("50"), DATE, "Contract"));
    assertThatThrownBy(() -> posting.contract(new FinanceCommands.Contract(key, production, bd("75"), DATE, "Contract")))
        .isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions WHERE idempotency_key=?", Long.class, key)).isEqualTo(1L);
  }

  @Test
  void employeeEarningAndPaymentAreDistinct() {
    UUID employee = employee();
    posting.earning(new FinanceCommands.Earning(UUID.randomUUID(), employee, null, bd("450"), DATE, "Event work"));
    posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), employee, bd("120"), DATE, "Part payment", "AZ-2"));
    var summary = reads.employee(employee);
    assertThat(summary.get("earned")).isEqualTo(bd("450.00"));
    assertThat(summary.get("paid")).isEqualTo(bd("120.00"));
    assertThat(summary.get("outstanding")).isEqualTo(bd("330.00"));
    assertThat(reads.reconciliation().get("status")).isEqualTo("RECONCILED");
  }

  @Test
  void signedOwnerPositionsAndReversalFollowJournal() {
    BigDecimal before = accountPosition("AK-2");
    posting.ownerDebit(new FinanceCommands.OwnerMovement(UUID.randomUUID(), bd("60"), DATE, "Owner draw", "AK-2", null));
    assertThat(accountPosition("AK-2")).isEqualTo(before.subtract(bd("60.00")));
    var credit = posting.ownerCredit(new FinanceCommands.OwnerMovement(UUID.randomUUID(), bd("20"), DATE, "Owner funding", null, "AK-2"));
    assertThat(accountPosition("AK-2")).isEqualTo(before.subtract(bd("40.00")));
    posting.reverse((UUID) credit.get("id"), new FinanceCommands.Reversal(UUID.randomUUID(), "Correction"));
    assertThat(accountPosition("AK-2")).isEqualTo(before.subtract(bd("60.00")));
    assertThat(reads.reconciliation().get("controlDifference")).isEqualTo(bd("0.00"));
  }

  @Test
  void invoiceTaxAndInstallmentRemainSeparate() {
    var party = posting.createCounterparty(new FinanceCommands.Counterparty("Finance test " + UUID.randomUUID(), "CUSTOMER", null));
    UUID partyId = (UUID) party.get("id");
    posting.invoice(new FinanceCommands.Invoice(UUID.randomUUID(), "FIN-TEST-" + UUID.randomUUID(), "2026-27", DATE, partyId, null, null, "CGST_SGST", bd("1000"), bd("9"), bd("9"), bd("0"), bd("50")));
    UUID invoiceId = jdbc.queryForObject("SELECT id FROM finance_invoices WHERE counterparty_id=? ORDER BY created_at DESC LIMIT 1", UUID.class, partyId);
    assertThat(reads.invoice(invoiceId).get("remaining")).isEqualTo(bd("1130.00"));
    posting.invoicePayment(new FinanceCommands.InvoicePayment(UUID.randomUUID(), invoiceId, bd("300"), DATE, "First installment", "AZ-2"));
    assertThat(reads.invoice(invoiceId).get("remaining")).isEqualTo(bd("830.00"));
    UUID source = jdbc.queryForObject("SELECT source_transaction_id FROM finance_invoices WHERE id=?", UUID.class, invoiceId);
    assertThatThrownBy(() -> posting.reverse(source, new FinanceCommands.Reversal(UUID.randomUUID(), "Premature reversal")))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REVERSAL_HAS_DEPENDENTS");
  }

  @Test
  void concurrentReceiptsCannotOverCollect() throws Exception {
    UUID production = production();
    posting.contract(new FinanceCommands.Contract(UUID.randomUUID(), production, bd("50"), DATE, "Limited contract"));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> receiveAfter(start, production, "40"));
      var b = pool.submit(() -> receiveAfter(start, production, "30"));
      start.countDown();
      boolean first = a.get(15, TimeUnit.SECONDS), second = b.get(15, TimeUnit.SECONDS);
      assertThat(first ^ second).isTrue();
      assertThat((BigDecimal)reads.production(production).get("received")).isLessThanOrEqualTo(bd("50.00"));
    }
  }

  @Test
  void acceptanceCaseOneWorkEarningTwoPartialPayments() {
    UUID roshan = employee();
    posting.earning(new FinanceCommands.Earning(UUID.randomUUID(), roshan, null, bd("30000"), DATE, "Roshan work"));
    posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), roshan, bd("17000"), DATE, "Part one", "AZ-2"));
    assertThat(reads.employee(roshan).get("outstanding")).isEqualTo(bd("13000"));
    posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), roshan, bd("13000"), DATE, "Part two", "AK-2"));
    assertThat(reads.employee(roshan).get("outstanding")).isEqualTo(bd("0"));
  }

  @Test
  void acceptanceCaseTwoProductionMarginsAndReceiver() {
    UUID production = production();
    BigDecimal akBefore = accountPosition("AK-2");
    posting.contract(new FinanceCommands.Contract(UUID.randomUUID(), production, bd("60000"), DATE, "Production contract"));
    posting.expense(new FinanceCommands.Expense(UUID.randomUUID(), bd("10000"), DATE, "Production expense", "AZ-2", production, null, null, null, "OTHER"));
    posting.receipt(new FinanceCommands.Receipt(UUID.randomUUID(), production, bd("20000"), DATE, "Advance", "AK-2", null, "ADD"));
    var result = reads.production(production);
    assertThat(result.get("received")).isEqualTo(bd("20000"));
    assertThat(result.get("outstanding")).isEqualTo(bd("40000"));
    assertThat(result.get("realizedMargin")).isEqualTo(bd("10000"));
    assertThat(result.get("contractedMargin")).isEqualTo(bd("50000"));
    assertThat((BigDecimal)accountPosition("AK-2")).isEqualTo(akBefore.add(bd("20000")));
    @SuppressWarnings("unchecked") var split = (java.util.Map<String,BigDecimal>)result.get("profitAllocation");
    assertThat(split.get("azeem")).isEqualTo(bd("6500"));
    assertThat(split.get("akash")).isEqualTo(bd("3500"));
  }

  @Test
  void acceptanceCaseThreePayerIsMandatory() {
    BigDecimal azBefore = accountPosition("AZ-2"), akBefore = accountPosition("AK-2");
    var missing = new FinanceCommands.Expense(UUID.randomUUID(), bd("6500"), DATE, "Food", null, null, null, null, null, "FOOD");
    assertThatThrownBy(() -> posting.expense(missing)).isInstanceOf(ApiException.class)
        .extracting("code").isEqualTo("OWNER_ACCOUNT_REQUIRED");
    posting.expense(new FinanceCommands.Expense(UUID.randomUUID(), bd("6500"), DATE, "Food", "AZ-2", null, null, null, null, "FOOD"));
    assertThat(accountPosition("AZ-2")).isEqualTo(azBefore.subtract(bd("6500")));
    assertThat(accountPosition("AK-2")).isEqualTo(akBefore);
  }

  @Test
  void acceptanceCaseFourNegativeOwnerPositionFromEquipmentPayment() {
    BigDecimal before = accountPosition("AZ-2");
    posting.ownerCredit(new FinanceCommands.OwnerMovement(UUID.randomUUID(), bd("1000000"), DATE, "Opening funds", null, "AZ-2"));
    posting.purchase(new FinanceCommands.Purchase(UUID.randomUUID(), null, DATE, null, "Equipment acquisition", bd("2000000"), bd("0"), null, null));
    UUID purchaseId = jdbc.queryForObject("SELECT id FROM finance_equipment_purchases WHERE description='Equipment acquisition' ORDER BY created_at DESC LIMIT 1", UUID.class);
    posting.purchasePayment(new FinanceCommands.PurchasePayment(UUID.randomUUID(), purchaseId, bd("2000000"), DATE, "Equipment settlement", "AZ-2"));
    assertThat(accountPosition("AZ-2")).isEqualTo(before.subtract(bd("1000000")));
    assertThat(reads.purchase(purchaseId).get("remaining")).isEqualTo(bd("0"));
  }

  @Test
  void acceptanceCaseFiveInvoiceInstallmentsAndTds() {
    UUID party = (UUID) posting.createCounterparty(new FinanceCommands.Counterparty("Invoice case " + UUID.randomUUID(), "CUSTOMER", null)).get("id");
    posting.invoice(new FinanceCommands.Invoice(UUID.randomUUID(), "CASE5-" + UUID.randomUUID(), "2026-27", DATE, party, null, null, "NONE", bd("458784"), bd("0"), bd("0"), bd("0"), bd("7776")));
    UUID invoiceId = jdbc.queryForObject("SELECT id FROM finance_invoices WHERE counterparty_id=?", UUID.class, party);
    for (String amount : new String[]{"100000","51592","30000","169416"})
      posting.invoicePayment(new FinanceCommands.InvoicePayment(UUID.randomUUID(), invoiceId, bd(amount), DATE, "Installment", "AK-2"));
    assertThat(reads.invoice(invoiceId).get("paid")).isEqualTo(bd("351008"));
    assertThat(reads.invoice(invoiceId).get("remaining")).isEqualTo(bd("100000"));
  }

  @Test
  void acceptanceCaseSixRepeatedRequestPostsOnce() {
    BigDecimal before = accountPosition("AZ-2");
    var expense = new FinanceCommands.Expense(UUID.randomUUID(), bd("250"), DATE, "Retry storm", "AZ-2", null, null, null, null, "OTHER");
    UUID id = null;
    for (int i = 0; i < 20; i++) {
      UUID received = (UUID) posting.expense(expense).get("id");
      if (id == null) id = received;
      assertThat(received).isEqualTo(id);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_journal_entries WHERE transaction_id=?", Long.class, id)).isEqualTo(1L);
    assertThat(accountPosition("AZ-2")).isEqualTo(before.subtract(bd("250")));
  }

  @Test
  void acceptanceCaseSevenReversalRestoresReceivable() {
    UUID production = production();
    posting.contract(new FinanceCommands.Contract(UUID.randomUUID(), production, bd("20000"), DATE, "Contract"));
    BigDecimal akBefore = accountPosition("AK-2");
    UUID receiptId = (UUID)posting.receipt(new FinanceCommands.Receipt(UUID.randomUUID(), production, bd("10000"), DATE, "Payment", "AK-2", null, null)).get("id");
    posting.reverse(receiptId, new FinanceCommands.Reversal(UUID.randomUUID(), "Receipt correction"));
    assertThat(accountPosition("AK-2")).isEqualTo(akBefore);
    assertThat(reads.production(production).get("outstanding")).isEqualTo(bd("20000"));
    assertThat(jdbc.queryForObject("SELECT status FROM finance_transactions WHERE id=?", String.class, receiptId)).isEqualTo("REVERSED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions WHERE reversal_of=?", Long.class, receiptId)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_audit_events WHERE transaction_id=?", Long.class, receiptId)).isEqualTo(1L);
  }

  @Test
  void concurrentEmployeePaymentsCannotOverAllocate() throws Exception {
    UUID person = employee();
    posting.earning(new FinanceCommands.Earning(UUID.randomUUID(), person, null, bd("50"), DATE, "Work"));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> payAfter(start, person, "40"));
      var b = pool.submit(() -> payAfter(start, person, "30"));
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS) ^ b.get(15, TimeUnit.SECONDS)).isTrue();
      assertThat((BigDecimal)reads.employee(person).get("paid")).isLessThanOrEqualTo(bd("50"));
    }
  }

  @Test
  void fixedSalaryDeductionsAndMultiplePayments() {
    UUID person = employee();
    posting.salary(new FinanceCommands.Salary(UUID.randomUUID(), person, null, bd("30000"), bd("5000"), bd("-1000"), DATE, "Monthly salary"));
    assertThat(reads.employee(person).get("earned")).isEqualTo(bd("24000"));
    posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), person, bd("10000"), DATE, "Installment one", "AZ-2"));
    posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), person, bd("14000"), DATE, "Installment two", "AK-2"));
    assertThat(reads.employee(person).get("outstanding")).isEqualTo(bd("0"));
    assertThatThrownBy(() -> posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), person, bd("1"), DATE, "Overpayment", "AK-2")))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("EMPLOYEE_PAYMENT_EXCEEDS_PAYABLE");
  }

  @Test
  void ownerTransferMovesPositionWithoutCreatingResult() {
    BigDecimal az = accountPosition("AZ-2"), ak = accountPosition("AK-2");
    BigDecimal result = (BigDecimal)reads.overview().get("overallResult");
    posting.ownerTransfer(new FinanceCommands.OwnerMovement(UUID.randomUUID(), bd("75"), DATE, "Owner transfer", "AZ-2", "AK-2"));
    assertThat(accountPosition("AZ-2")).isEqualTo(az.subtract(bd("75")));
    assertThat(accountPosition("AK-2")).isEqualTo(ak.add(bd("75")));
    assertThat(reads.overview().get("overallResult")).isEqualTo(result);
  }

  @Test
  void failedPostingRollsBackAndPostedEvidenceIsImmutable() {
    UUID production = production();
    posting.contract(new FinanceCommands.Contract(UUID.randomUUID(), production, bd("50"), DATE, "Limited contract"));
    BigDecimal position = accountPosition("AZ-2");
    long count = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class);
    assertThatThrownBy(() -> posting.receipt(new FinanceCommands.Receipt(UUID.randomUUID(), production, bd("60"), DATE, "Too much", "AZ-2", null, null)))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("RECEIPT_EXCEEDS_OUTSTANDING");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class)).isEqualTo(count);
    assertThat(accountPosition("AZ-2")).isEqualTo(position);
    UUID posted = jdbc.queryForObject("SELECT source_transaction_id FROM finance_production_profiles WHERE production_id=?", UUID.class, production);
    assertThatThrownBy(() -> jdbc.update("UPDATE finance_transactions SET description='mutated' WHERE id=?", posted))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void projectionRebuildRestoresJournalAndDoubleReversalIsRejected() {
    UUID original = (UUID)posting.ownerCredit(new FinanceCommands.OwnerMovement(UUID.randomUUID(), bd("10"), DATE, "Fund", null, "AZ-2")).get("id");
    posting.reverse(original, new FinanceCommands.Reversal(UUID.randomUUID(), "Correction"));
    assertThatThrownBy(() -> posting.reverse(original, new FinanceCommands.Reversal(UUID.randomUUID(), "Again")))
        .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ALREADY_REVERSED");
    jdbc.update("UPDATE finance_account_positions SET position=position+1 WHERE account_id=(SELECT id FROM finance_accounts WHERE code='AZ-2')");
    assertThat(reads.reconciliation().get("status")).isEqualTo("BROKEN");
    assertThat(reads.rebuildPositions().get("status")).isEqualTo("RECONCILED");
  }

  @Test
  void workbookPreviewPreservesTrailingSheetNamesWithoutPostingMoney() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("jully dec P3 Led ");
      production.createRow(2).createCell(9).setCellValue(168630);
      var row = production.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(1).setCellValue("Test venue");
      row.createCell(4).setCellValue(60000);
      row.createCell(5).setCellValue(10000);
      var ambiguous = production.createRow(5);
      ambiguous.createCell(0).setCellValue("17,18-07");
      ambiguous.createCell(1).setCellValue("Test venue");
      ambiguous.createCell(4).setCellValue(9000);
      var quote = workbook.createSheet("Varma ji ");
      quote.createRow(0).createCell(0).setCellValue("Quotation only");
      workbook.write(output);
      bytes = output.toByteArray();
    }
    long before = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class);
    var preview = migration.preview("test.xlsx", Base64.getEncoder().encodeToString(bytes));
    UUID batch = (UUID)((java.util.Map<?,?>)preview.get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT legacy_type FROM finance_migration_rows WHERE batch_id=? AND sheet_name='jully dec P3 Led ' AND source_row=5", String.class, batch)).isEqualTo("PRODUCTION");
    assertThat(jdbc.queryForObject("SELECT legacy_type FROM finance_migration_rows WHERE batch_id=? AND sheet_name='Varma ji '", String.class, batch)).isEqualTo("QUOTATION_DRAFT");
    assertThat(migration.validate(batch).get("openIssueCount")).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT issue_code FROM finance_migration_facts WHERE batch_id=? AND row_id=(SELECT id FROM finance_migration_rows WHERE batch_id=? AND sheet_name='jully dec P3 Led ' AND source_row=6) AND slot='CONTRACT'", String.class, batch, batch)).isEqualTo("DATE_AMBIGUITY");
    assertThat(jdbc.queryForObject("SELECT status FROM finance_migration_batches WHERE id=?", String.class, batch)).isEqualTo("REVIEW_REQUIRED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class)).isEqualTo(before);
  }

  @Test
  void sameReceiptInProductionAndAkLedgerLinksOnceWithoutPostingTwice() throws Exception {
    UUID batch = migrationBatch("AK", false, false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT o.account_code FROM finance_migration_links l JOIN finance_migration_facts o ON o.id=l.owner_fact_id WHERE l.batch_id=?",String.class,batch)).isEqualTo("AK-2");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND event_type='CONTRACTED_REVENUE' AND classification='NON_CASH'",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions t JOIN finance_migration_facts f ON f.canonical_transaction_id=t.id WHERE f.batch_id=?",Long.class,batch)).isZero();
  }

  @Test
  void sameReceiptInProductionAndAzLedgerLinksOnce() throws Exception {
    UUID batch = migrationBatch("AZ", false, false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT o.account_code FROM finance_migration_links l JOIN finance_migration_facts o ON o.id=l.owner_fact_id WHERE l.batch_id=?",String.class,batch)).isEqualTo("AZ-2");
  }

  @Test
  void venueProvesReceiptIdentityWhenProductionPartyCellIsBlank() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      var row = production.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(1).setCellValue("Sarnath Venue");
      row.createCell(6).setCellValue(7500);
      var owner = workbook.createSheet("az-2");
      var ownerRow = owner.createRow(5);
      ownerRow.createCell(0).setCellValue(DATE.toString());
      ownerRow.createCell(1).setCellValue("sarnath venue");
      ownerRow.createCell(2).setCellValue(7500);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("venue.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT evidence->>'identity' FROM finance_migration_links WHERE batch_id=?",String.class,batch)).isEqualTo("VENUE_EXACT");
  }

  @Test
  void productionExtraPayIsNotMisclassifiedAsEmployeePayment() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      var header = production.createRow(0);
      header.createCell(13).setCellValue("Roshan Pay");
      header.createCell(14).setCellValue("Roshan");
      header.createCell(33).setCellValue("Extr");
      header.createCell(34).setCellValue("Extra Pay");
      header.createCell(35).setCellValue("CAm+LEd Pay");
      var row = production.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(13).setCellValue(1000);
      row.createCell(14).setCellValue(2000);
      row.createCell(34).setCellValue(300);
      row.createCell(35).setCellValue(400);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("headers.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT event_type FROM finance_migration_facts WHERE batch_id=? AND slot='PAYMENT_13'",String.class,batch)).isEqualTo("EMPLOYEE_PAYMENT");
    assertThat(jdbc.queryForObject("SELECT event_type FROM finance_migration_facts WHERE batch_id=? AND slot='EMPLOYEE_EARNING_14'",String.class,batch)).isEqualTo("EMPLOYEE_EARNING");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND event_type='PRODUCTION_EXPENSE_PAYMENT'",Long.class,batch)).isEqualTo(2L);
  }

  @Test
  void explicitOwnerColumnInExpenseSheetRemainsAttributed() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Expence");
      var row = sheet.createRow(2);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(1).setCellValue("Fuel");
      row.createCell(2).setCellValue("az");
      row.createCell(3).setCellValue(900);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("explicit-account.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT account_code FROM finance_migration_facts WHERE batch_id=? AND event_type='GENERAL_EXPENSE'",String.class,batch)).isEqualTo("AZ-2");
    assertThat(jdbc.queryForObject("SELECT classification FROM finance_migration_facts WHERE batch_id=? AND event_type='GENERAL_EXPENSE'",String.class,batch)).isEqualTo("DISTINCT");
  }

  @Test
  void negativeWorkbookAmountRemainsReviewRequiredAfterResolutionReplay() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Jan 26");
      var row = sheet.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(2).setCellValue("Correction");
      row.createCell(6).setCellValue(-500);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("negative.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT issue_code FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,batch)).isEqualTo("NEGATIVE_SOURCE_AMOUNT");
    migration.validate(batch);
    assertThat(jdbc.queryForObject("SELECT classification FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,batch)).isEqualTo("REVIEW_REQUIRED");
    assertThat(jdbc.queryForObject("SELECT issue_code FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,batch)).isEqualTo("NEGATIVE_SOURCE_AMOUNT");
  }

  @Test
  void oneOwnerRowCannotBeConsumedByTwoDomainReceipts() throws Exception {
    UUID batch = migrationBatch("AK", true, false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND issue_code='DUPLICATE_AMBIGUITY'",Long.class,batch)).isEqualTo(2L);
  }

  @Test
  void equalAmountAndDateWithDifferentPartyDoesNotLink() throws Exception {
    UUID batch = migrationBatch("AK", false, true);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND event_type='CLIENT_RECEIPT' AND issue_code='ACCOUNT_UNKNOWN'",Long.class,batch)).isEqualTo(1L);
  }

  @Test
  void oneDomainReceiptCanHaveTwoProvenOwnerAllocations() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      var receipt = production.createRow(4);
      receipt.createCell(0).setCellValue(DATE.toString());
      receipt.createCell(2).setCellValue("Split customer");
      receipt.createCell(4).setCellValue(30000);
      receipt.createCell(6).setCellValue(30000);
      var az = workbook.createSheet("az-2").createRow(5);
      az.createCell(0).setCellValue(DATE.toString());
      az.createCell(1).setCellValue("Split customer");
      az.createCell(2).setCellValue(17000);
      var ak = workbook.createSheet("Ak-2").createRow(5);
      ak.createCell(0).setCellValue(DATE.toString());
      ak.createCell(1).setCellValue("Split customer");
      ak.createCell(2).setCellValue(13000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("split.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isEqualTo(2L);
    assertThat(jdbc.queryForObject("SELECT sum(allocated_amount) FROM finance_migration_links WHERE batch_id=?",BigDecimal.class,batch)).isEqualByComparingTo("30000");
  }

  @Test
  void employeeEarningIsNonCashAndSplitPaymentLinksBothOwnerLedgers() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      var header = production.createRow(0);
      header.createCell(13).setCellValue("Roshan pay");
      header.createCell(14).setCellValue("Roshan");
      var row = production.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(13).setCellValue(30000);
      row.createCell(14).setCellValue(30000);
      var az = workbook.createSheet("az-2").createRow(5);
      az.createCell(0).setCellValue(DATE.toString());
      az.createCell(1).setCellValue("Roshan");
      az.createCell(3).setCellValue(17000);
      var ak = workbook.createSheet("Ak-2").createRow(5);
      ak.createCell(0).setCellValue(DATE.toString());
      ak.createCell(1).setCellValue("Roshan");
      ak.createCell(3).setCellValue(13000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("employee-split.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND event_type='EMPLOYEE_EARNING' AND classification='NON_CASH'",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isEqualTo(2L);
    assertThat(jdbc.queryForObject("SELECT sum(allocated_amount) FROM finance_migration_links WHERE batch_id=?",BigDecimal.class,batch)).isEqualByComparingTo("30000");
  }

  @Test
  void twoEqualOwnerCandidatesRequireReview() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      var receipt = production.createRow(4);
      receipt.createCell(0).setCellValue(DATE.toString());
      receipt.createCell(2).setCellValue("Govind");
      receipt.createCell(6).setCellValue(10000);
      var owner = workbook.createSheet("Ak-2");
      for (int i=5;i<=6;i++) {
        var o = owner.createRow(i);
        o.createCell(0).setCellValue(DATE.toString());
        o.createCell(1).setCellValue("Govind");
        o.createCell(2).setCellValue(10000);
      }
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("ambiguous.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch)).isZero();
    assertThat(jdbc.queryForObject("SELECT issue_code FROM finance_migration_facts WHERE batch_id=? AND event_type='CLIENT_RECEIPT'",String.class,batch)).isEqualTo("MULTIPLE_ACCOUNT_CANDIDATES");
  }

  @Test
  void migrationLinksCannotOverAllocateOneOwnerSource() throws Exception {
    UUID batch = migrationBatch("AK",true,false);
    var domains = jdbc.queryForList("SELECT id FROM finance_migration_facts WHERE batch_id=? AND event_type='CLIENT_RECEIPT' ORDER BY id",batch);
    UUID owner = jdbc.queryForObject("SELECT id FROM finance_migration_facts WHERE batch_id=? AND source_role='OWNER' AND direction='IN'",UUID.class,batch);
    jdbc.update("INSERT INTO finance_migration_links(batch_id,domain_fact_id,owner_fact_id,allocated_amount,confidence,evidence) VALUES(?,?,?,8000,'MANUAL','{}'::jsonb)",batch,domains.getFirst().get("id"),owner);
    assertThatThrownBy(() -> jdbc.update("INSERT INTO finance_migration_links(batch_id,domain_fact_id,owner_fact_id,allocated_amount,confidence,evidence) VALUES(?,?,?,8000,'MANUAL','{}'::jsonb)",batch,domains.get(1).get("id"),owner)).isInstanceOf(DataAccessException.class);
    assertThat(jdbc.queryForObject("SELECT sum(allocated_amount) FROM finance_migration_links WHERE owner_fact_id=?",BigDecimal.class,owner)).isEqualByComparingTo("8000");
  }

  @Test
  void oppositeOwnerLedgersWithExplicitOwnerNamesResolveOneTransfer() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var az = workbook.createSheet("az-2").createRow(5);
      az.createCell(0).setCellValue(DATE.toString());
      az.createCell(1).setCellValue("Aakash");
      az.createCell(2).setCellValue(10000);
      var ak = workbook.createSheet("Ak-2").createRow(5);
      ak.createCell(0).setCellValue(DATE.toString());
      ak.createCell(1).setCellValue("Az");
      ak.createCell(3).setCellValue(10000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("owner-transfer.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=? AND relation_type='OWNER_TRANSFER'",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND resolved_event_type='OWNER_TRANSFER'",Long.class,batch)).isEqualTo(2L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions t JOIN finance_migration_facts f ON f.canonical_transaction_id=t.id WHERE f.batch_id=?",Long.class,batch)).isZero();
  }

  @Test
  @Transactional
  void provenOwnerTransferPostsOnceAcrossBothSourceLedgers() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var az = workbook.createSheet("az-2").createRow(5);
      az.createCell(0).setCellValue(DATE.toString());
      az.createCell(1).setCellValue("Aakash");
      az.createCell(3).setCellValue(5000);
      var ak = workbook.createSheet("Ak-2").createRow(5);
      ak.createCell(0).setCellValue(DATE.toString());
      ak.createCell(1).setCellValue("Az");
      ak.createCell(2).setCellValue(5000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("transfer-post.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(((Map<?,?>)migration.postProven(batch).get("postedNow")).get("ownerTransfers")).isEqualTo(1);
    assertThat(((Map<?,?>)migration.postProven(batch).get("postedNow")).get("ownerTransfers")).isEqualTo(0);
    assertThat(jdbc.queryForObject("SELECT count(DISTINCT canonical_transaction_id) FROM finance_migration_facts WHERE batch_id=? AND canonical_transaction_id IS NOT NULL",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_journal_entries WHERE transaction_id IN (SELECT DISTINCT canonical_transaction_id FROM finance_migration_facts WHERE batch_id=?)",Long.class,batch)).isEqualTo(1L);
  }

  @Test
  @Transactional
  void onlyArithmeticallyProvenEquipmentPurchaseImportsAsNonCash() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("LED");
      var good = sheet.createRow(2);
      good.createCell(0).setCellValue(DATE.toString());
      good.createCell(1).setCellValue("Test display panel");
      good.createCell(2).setCellValue(1000);
      good.createCell(3).setCellValue(100);
      good.createCell(4).setCellValue(2);
      good.createCell(5).setCellValue(2200);
      var mismatched = sheet.createRow(3);
      mismatched.createCell(0).setCellValue(DATE.toString());
      mismatched.createCell(1).setCellValue("Unexplained charge");
      mismatched.createCell(2).setCellValue(1000);
      mismatched.createCell(4).setCellValue(2);
      mismatched.createCell(5).setCellValue(2500);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    UUID batch = (UUID)((java.util.Map<?,?>)migration.preview("purchase-post.xlsx",Base64.getEncoder().encodeToString(bytes)).get("batch")).get("id");
    assertThat(((Map<?,?>)migration.postProven(batch).get("postedNow")).get("equipmentPurchases")).isEqualTo(1);
    assertThat(((Map<?,?>)migration.postProven(batch).get("postedNow")).get("equipmentPurchases")).isEqualTo(0);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND event_type='EQUIPMENT_PURCHASE' AND canonical_transaction_id IS NOT NULL",Long.class,batch)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT issue_code FROM finance_migration_facts WHERE batch_id=? AND event_type='EQUIPMENT_PURCHASE' AND canonical_transaction_id IS NULL",String.class,batch)).isEqualTo("FORMULA_STATIC_CONFLICT");
    assertThat(jdbc.queryForObject("SELECT tax FROM finance_equipment_purchases WHERE source_transaction_id IN (SELECT canonical_transaction_id FROM finance_migration_facts WHERE batch_id=? AND canonical_transaction_id IS NOT NULL)",BigDecimal.class,batch)).isEqualByComparingTo("200.00");
  }

  @Test
  void reviewedAccountAssignmentReplaysDeterministically() throws Exception {
    UUID batch = migrationBatch("AK", false, true);
    migration.decide(batch,"Jan 26",5,"RECEIPT","ASSIGN_AK",null,"Owner confirmed historical receiver");
    assertThat(jdbc.queryForObject("SELECT account_code FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,batch)).isEqualTo("AK-2");
    migration.validate(batch);
    assertThat(jdbc.queryForObject("SELECT account_code FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,batch)).isEqualTo("AK-2");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_override_audit a JOIN finance_migration_overrides o ON o.id=a.override_id WHERE o.sheet_name='Jan 26' AND o.slot='RECEIPT'",Long.class)).isGreaterThanOrEqualTo(1L);
  }

  @Test
  void reviewedMappingSurvivesCleanMigrationBatchReset() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      production.createRow(0).createCell(0).setCellValue(UUID.randomUUID().toString());
      var receipt = production.createRow(4);
      receipt.createCell(0).setCellValue(DATE.toString());
      receipt.createCell(2).setCellValue("Unmapped historical party");
      receipt.createCell(4).setCellValue(12000);
      receipt.createCell(6).setCellValue(12000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    String encoded = Base64.getEncoder().encodeToString(bytes);
    UUID first = (UUID)((java.util.Map<?,?>)migration.preview("replay.xlsx",encoded).get("batch")).get("id");
    migration.decide(first,"Jan 26",5,"RECEIPT","ASSIGN_AZ",null,"Owner reviewed account evidence");
    migration.resetStaging();
    UUID second = (UUID)((java.util.Map<?,?>)migration.preview("replay.xlsx",encoded).get("batch")).get("id");
    assertThat(second).isNotEqualTo(first);
    assertThat(jdbc.queryForObject("SELECT account_code FROM finance_migration_facts WHERE batch_id=? AND slot='RECEIPT'",String.class,second)).isEqualTo("AZ-2");
  }

  private UUID migrationBatch(String account, boolean duplicateDomain, boolean differentParty) throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var production = workbook.createSheet("Jan 26");
      production.createRow(0).createCell(0).setCellValue(UUID.randomUUID().toString());
      var row = production.createRow(4);
      row.createCell(0).setCellValue(DATE.toString());
      row.createCell(2).setCellValue(differentParty ? "Other party" : "Govind");
      row.createCell(4).setCellValue(10000);
      row.createCell(6).setCellValue(10000);
      if (duplicateDomain) {
        var second = production.createRow(5);
        second.createCell(0).setCellValue(DATE.toString());
        second.createCell(2).setCellValue("Govind");
        second.createCell(4).setCellValue(10000);
        second.createCell(6).setCellValue(10000);
      }
      var owner = workbook.createSheet(account.equals("AZ") ? "az-2" : "Ak-2");
      var ownerRow = owner.createRow(5);
      ownerRow.createCell(0).setCellValue(DATE.toString());
      ownerRow.createCell(1).setCellValue("Govind");
      ownerRow.createCell(2).setCellValue(10000);
      workbook.write(output);
      bytes = output.toByteArray();
    }
    var result = migration.preview("synthetic.xlsx",Base64.getEncoder().encodeToString(bytes));
    return (UUID)((java.util.Map<?,?>)result.get("batch")).get("id");
  }

  private boolean payAfter(CountDownLatch start, UUID person, String amount) {
    try {
      start.await();
      posting.employeePayment(new FinanceCommands.EmployeePayment(UUID.randomUUID(), person, bd(amount), DATE, "Concurrent payment", "AZ-2"));
      return true;
    } catch (ApiException exception) {
      if (!"EMPLOYEE_PAYMENT_EXCEEDS_PAYABLE".equals(exception.code)) throw exception;
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private boolean receiveAfter(CountDownLatch start, UUID production, String amount) {
    try {
      start.await();
      posting.receipt(new FinanceCommands.Receipt(UUID.randomUUID(), production, bd(amount), DATE, "Concurrent receipt", "AZ-2", null, null));
      return true;
    } catch (ApiException exception) {
      if (!"RECEIPT_EXCEEDS_OUTSTANDING".equals(exception.code)) throw exception;
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private UUID production() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO productions(id,title,client_name,event_date,start_time,end_time,venue_name,status,priority) VALUES(?,?,?,?,'10:00','18:00','Test venue','PLANNING','NORMAL')", id, "Finance test " + id, "Finance test", DATE);
    return id;
  }

  private UUID employee() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO employees(id,employee_code,first_name,display_name,phone,role_title,department,employment_type,joining_date,base_salary_minor,salary_currency,status) VALUES(?,?, 'Test','Finance test','0000000000','Test','Operations','FULL_TIME',?,0,'INR','ACTIVE')", id, "FIN-" + id.toString().substring(0,8), DATE);
    return id;
  }

  private BigDecimal accountPosition(String code) {
    return jdbc.queryForObject("SELECT p.position FROM finance_account_positions p JOIN finance_accounts a ON a.id=p.account_id WHERE a.code=?", BigDecimal.class, code);
  }

  private static BigDecimal bd(String amount) { return new BigDecimal(amount).setScale(2); }
}
