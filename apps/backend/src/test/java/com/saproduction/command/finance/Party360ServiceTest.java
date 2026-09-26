package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Party360ServiceTest {

  @Mock JdbcTemplate jdbc;
  @Mock AuditService audit;

  FinanceReadService reads;

  @BeforeEach
  void setUp() {
    reads = new FinanceReadService(jdbc, audit);
  }

  @Test
  void party360AggregatesDualTrackTotalsAndContext() {
    UUID partyId = UUID.randomUUID();

    Map<String, Object> partyRow = new HashMap<>();
    partyRow.put("id", partyId);
    partyRow.put("displayName", "Reliance Events Ltd");
    partyRow.put("legalName", "Reliance Events Private Limited");
    partyRow.put("role", "CUSTOMER");
    partyRow.put("gstin", "27AABCR1234F1Z5");
    partyRow.put("notes", "Corporate client");
    partyRow.put("active", true);

    when(jdbc.queryForList(startsWith("SELECT id,display_name AS \"displayName\""), eq(partyId)))
        .thenReturn(List.of(partyRow));

    // Direct charges: 50,000 charged, 20,000 received -> 30,000 outstanding
    when(jdbc.queryForObject(
            contains("FROM finance_counterparty_charges ch JOIN finance_transactions t"),
            eq(BigDecimal.class),
            eq(partyId)))
        .thenReturn(new BigDecimal("50000.00"));

    when(jdbc.queryForObject(
            contains(
                "FROM finance_counterparty_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id JOIN finance_counterparty_charges ch"),
            eq(BigDecimal.class),
            eq(partyId)))
        .thenReturn(new BigDecimal("20000.00"));

    // Formal invoices: 100,000 total, 40,000 paid -> 60,000 outstanding
    when(jdbc.queryForObject(
            contains(
                "FROM finance_invoices i JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE i.counterparty_id=?"),
            eq(BigDecimal.class),
            eq(partyId)))
        .thenReturn(new BigDecimal("100000.00"));

    when(jdbc.queryForObject(
            contains(
                "FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id JOIN finance_invoices i"),
            eq(BigDecimal.class),
            eq(partyId)))
        .thenReturn(new BigDecimal("40000.00"));

    // Charges list
    UUID chargeId = UUID.randomUUID();
    Map<String, Object> charge = new HashMap<>();
    charge.put("id", chargeId);
    charge.put("date", java.sql.Date.valueOf(LocalDate.of(2026, 8, 15)));
    charge.put("amount", new BigDecimal("50000.00"));
    charge.put("description", "Direct stage audio fee");
    charge.put("paid", new BigDecimal("20000.00"));
    charge.put("outstanding", new BigDecimal("30000.00"));
    when(jdbc.queryForList(
            argThat(sql -> sql != null && sql.contains("finance_counterparty_charges ch")),
            eq(partyId)))
        .thenReturn(List.of(charge));

    // Invoices list
    UUID invoiceId = UUID.randomUUID();
    Map<String, Object> invoice = new HashMap<>();
    invoice.put("id", invoiceId);
    invoice.put("invoiceNumber", "SA-2026-089");
    invoice.put("financialYear", "2026-27");
    invoice.put("date", java.sql.Date.valueOf(LocalDate.of(2026, 9, 1)));
    invoice.put("total", new BigDecimal("100000.00"));
    invoice.put("paid", new BigDecimal("40000.00"));
    invoice.put("outstanding", new BigDecimal("60000.00"));
    invoice.put("billId", UUID.randomUUID());
    invoice.put("billStatus", "PARTIALLY_PAID");
    when(jdbc.queryForList(
            argThat(sql -> sql != null && sql.contains("FROM finance_invoices i")), eq(partyId)))
        .thenReturn(List.of(invoice));

    // Bills list
    Map<String, Object> bill = new HashMap<>();
    bill.put("id", UUID.randomUUID());
    bill.put("billNumber", "SA-2026-089");
    bill.put("status", "PARTIALLY_PAID");
    bill.put("grossTotal", new BigDecimal("100000.00"));
    when(jdbc.queryForList(
            argThat(sql -> sql != null && sql.contains("FROM billing_bills b")), eq(partyId)))
        .thenReturn(List.of(bill));

    // Productions list
    UUID prodId = UUID.randomUUID();
    Map<String, Object> prod = new HashMap<>();
    prod.put("id", prodId);
    prod.put("title", "Annual Corporate Summit");
    prod.put("venueName", "Jio Convention Centre");
    when(jdbc.queryForList(
            argThat(sql -> sql != null && sql.contains("FROM productions p")),
            eq(partyId),
            eq(partyId),
            eq(partyId),
            eq(partyId)))
        .thenReturn(List.of(prod));

    // Timeline list
    Map<String, Object> t1 = new HashMap<>();
    t1.put("id", UUID.randomUUID());
    t1.put("transactionNo", 101L);
    t1.put("type", "INVOICE_PAYMENT");
    t1.put("track", "FORMAL_INVOICE");
    t1.put("amount", new BigDecimal("40000.00"));
    t1.put("ownerAccount", "AZ-2");
    when(jdbc.queryForList(
            argThat(sql -> sql != null && sql.contains("FROM finance_transactions t")),
            eq(partyId),
            eq(partyId)))
        .thenReturn(List.of(t1));

    Map<String, Object> result = reads.party360(partyId);

    assertThat(result).isNotNull();
    assertThat(result.get("party")).isEqualTo(partyRow);

    // Verify dual-track authoritatively calculated balances
    assertThat(result.get("chargeTotal")).isEqualTo(new BigDecimal("50000.00"));
    assertThat(result.get("chargeReceived")).isEqualTo(new BigDecimal("20000.00"));
    assertThat(result.get("chargeOutstanding")).isEqualTo(new BigDecimal("30000.00"));

    assertThat(result.get("invoiceTotal")).isEqualTo(new BigDecimal("100000.00"));
    assertThat(result.get("invoicePaid")).isEqualTo(new BigDecimal("40000.00"));
    assertThat(result.get("invoiceOutstanding")).isEqualTo(new BigDecimal("60000.00"));

    // Verify aggregate totals
    assertThat(result.get("totalOutstanding"))
        .isEqualTo(new BigDecimal("90000.00")); // 30,000 + 60,000
    assertThat(result.get("totalReceived"))
        .isEqualTo(new BigDecimal("60000.00")); // 20,000 + 40,000

    // Verify collections
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> charges = (List<Map<String, Object>>) result.get("charges");
    assertThat(charges).hasSize(1);
    assertThat(charges.getFirst().get("outstanding")).isEqualTo(new BigDecimal("30000.00"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> invoices = (List<Map<String, Object>>) result.get("invoices");
    assertThat(invoices).hasSize(1);
    assertThat(invoices.getFirst().get("billStatus")).isEqualTo("PARTIALLY_PAID");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> timeline = (List<Map<String, Object>>) result.get("timeline");
    assertThat(timeline).hasSize(1);
    assertThat(timeline.getFirst().get("track")).isEqualTo("FORMAL_INVOICE");
    assertThat(timeline.getFirst().get("ownerAccount")).isEqualTo("AZ-2");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> prods = (List<Map<String, Object>>) result.get("productions");
    assertThat(prods).hasSize(1);
    assertThat(prods.getFirst().get("title")).isEqualTo("Annual Corporate Summit");
  }

  @Test
  void party360ThrowsNotFoundWhenPartyDoesNotExist() {
    UUID nonExistent = UUID.randomUUID();
    when(jdbc.queryForList(
            startsWith("SELECT id,display_name AS \"displayName\""), eq(nonExistent)))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> reads.party360(nonExistent))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("FINANCE_RECORD_NOT_FOUND");
  }

  @Test
  void partyReceiptRejectsInvalidOrMissingPayer() {
    var posting =
        new FinancePostingService(
            jdbc,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            audit);
    var cmd =
        new FinanceCommands.PartyReceipt(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 9, 26),
            "Receipt without valid owner",
            "INVALID_OWNER");

    assertThatThrownBy(() -> posting.partyReceipt(cmd))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("OWNER_ACCOUNT_REQUIRED");
  }

  @Test
  void invoicePaymentRejectsInvalidOrMissingPayer() {
    var posting =
        new FinancePostingService(
            jdbc,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            audit);
    var cmd =
        new FinanceCommands.InvoicePayment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 9, 26),
            "Payment without valid owner",
            "UNKNOWN");

    assertThatThrownBy(() -> posting.invoicePayment(cmd))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("OWNER_ACCOUNT_REQUIRED");
  }

  @Test
  void partyReceiptRejectsWhenExceedingChargeOutstanding() {
    var posting =
        new FinancePostingService(
            jdbc,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            audit);
    UUID partyId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    when(jdbc.query(
            contains("FROM finance_accounts WHERE code=? AND active"),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq("AZ-2")))
        .thenReturn(List.of(accountId));

    when(jdbc.queryForObject(
            startsWith("SELECT count(*) FROM finance_counterparties"), eq(Long.class), eq(partyId)))
        .thenReturn(1L);

    // No charges exist -> available outstanding is 0
    when(jdbc.queryForList(
            contains("SELECT c.id,c.amount FROM finance_counterparty_charges c"), eq(partyId)))
        .thenReturn(Collections.emptyList());

    var cmd =
        new FinanceCommands.PartyReceipt(
            UUID.randomUUID(),
            partyId,
            new BigDecimal("5000.00"),
            LocalDate.of(2026, 9, 26),
            "Payment exceeding charges",
            "AZ-2");

    assertThatThrownBy(() -> posting.partyReceipt(cmd))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("PARTY_RECEIPT_EXCEEDS_OUTSTANDING");
  }

  @Test
  void invoicePaymentRejectsWhenExceedingInvoiceBalance() {
    var posting =
        new FinancePostingService(
            jdbc,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            audit);
    UUID invoiceId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    when(jdbc.query(
            contains("FROM finance_accounts WHERE code=? AND active"),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq("AK-2")))
        .thenReturn(List.of(accountId));

    when(jdbc.queryForList(
            startsWith("SELECT counterparty_id,production_id FROM finance_invoices"),
            eq(invoiceId)))
        .thenReturn(
            List.of(
                Map.of("counterparty_id", UUID.randomUUID(), "production_id", UUID.randomUUID())));

    // Invoice total 10,000, already paid 10,000 -> 0 remaining
    Map<String, Object> invoice = new HashMap<>();
    invoice.put("invoice_total", new BigDecimal("10000.00"));
    invoice.put("tds_amount", BigDecimal.ZERO);
    when(jdbc.queryForList(contains("SELECT i.invoice_total,i.tds_amount"), eq(invoiceId)))
        .thenReturn(List.of(invoice));

    when(jdbc.queryForObject(
            contains("FROM finance_invoice_payment_allocations a"),
            eq(BigDecimal.class),
            eq(invoiceId)))
        .thenReturn(new BigDecimal("10000.00"));

    var cmd =
        new FinanceCommands.InvoicePayment(
            UUID.randomUUID(),
            invoiceId,
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 9, 26),
            "Over-payment attempt",
            "AK-2");

    assertThatThrownBy(() -> posting.invoicePayment(cmd))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("INVOICE_PAYMENT_EXCEEDS_REMAINING");
  }
}
