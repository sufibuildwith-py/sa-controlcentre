package com.saproduction.command.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.dashboard.CommandDashboardDto.*;
import com.saproduction.command.finance.FinanceReadService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CommandDashboardReadServiceTest {

  private JdbcTemplate jdbc;
  private FinanceReadService financeReads;
  private CommandDashboardReadService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    financeReads = mock(FinanceReadService.class);
    service = new CommandDashboardReadService(jdbc, financeReads, "Asia/Kolkata");
  }

  @Test
  void dashboardAggregatesCanonicalMoneyAndPositions() {
    LocalDate date = LocalDate.of(2026, 9, 26);

    // Mock overview
    when(financeReads.overview())
        .thenReturn(
            Map.of(
                "received", new BigDecimal("500000.00"),
                "incurredExpense", new BigDecimal("350000.00"),
                "overallResult", new BigDecimal("150000.00")));

    // Mock accounts
    when(financeReads.accounts())
        .thenReturn(
            List.of(
                Map.of("code", "AZ-2", "position", new BigDecimal("-1697522.00")),
                Map.of("code", "AK-2", "position", new BigDecimal("104320.00"))));

    // Mock reconciliation
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "RECONCILED",
                "employee_payables", new BigDecimal("45000.00"),
                "invoice_receivables", new BigDecimal("180000.00")));

    // Mock direct charge receivables
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(new BigDecimal("75000.00"));

    // Mock today's counts and movement
    when(jdbc.queryForObject(contains("productions WHERE event_date"), eq(Integer.class), eq(date)))
        .thenReturn(3);
    when(jdbc.queryForObject(contains("FROM tasks"), eq(Integer.class), eq(date))).thenReturn(7);
    when(jdbc.queryForObject(
            contains("attendance_records WHERE attendance_date"), eq(Integer.class), eq(date)))
        .thenReturn(2);
    when(jdbc.queryForObject(contains("employees e"), eq(Integer.class), eq(date))).thenReturn(1);

    when(jdbc.queryForMap(contains("finance_transactions"), eq(date)))
        .thenReturn(
            Map.of(
                "received", new BigDecimal("142000.00"),
                "disbursed", new BigDecimal("30000.00"),
                "tx_count", 4L));

    // Mock attention subqueries
    when(jdbc.queryForMap(contains("finance_invoices"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_production_profiles"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(0);

    CommandDashboard result = service.getDashboard(date);

    assertThat(result.date()).isEqualTo(date);

    // Today verification
    assertThat(result.today().productions()).isEqualTo(3);
    assertThat(result.today().tasks()).isEqualTo(7);
    assertThat(result.today().attendanceExceptions()).isEqualTo(3); // 2 recorded + 1 unrecorded
    assertThat(result.today().moneyMovement().received()).isEqualTo(new BigDecimal("142000.00"));
    assertThat(result.today().moneyMovement().disbursed()).isEqualTo(new BigDecimal("30000.00"));
    assertThat(result.today().moneyMovement().net()).isEqualTo(new BigDecimal("112000.00"));
    assertThat(result.today().moneyMovement().transactionCount()).isEqualTo(4);

    // Money verification
    assertThat(result.money().businessPosition()).isEqualTo(new BigDecimal("150000.00"));
    assertThat(result.money().azeemPosition()).isEqualTo(new BigDecimal("-1697522.00"));
    assertThat(result.money().akashPosition()).isEqualTo(new BigDecimal("104320.00"));
    assertThat(result.money().customerReceivable()).isEqualTo(new BigDecimal("75000.00"));
    assertThat(result.money().invoiceReceivable()).isEqualTo(new BigDecimal("180000.00"));
    assertThat(result.money().totalReceivable())
        .isEqualTo(new BigDecimal("255000.00")); // dual-track sum
    assertThat(result.money().employeePayable()).isEqualTo(new BigDecimal("45000.00"));
    assertThat(result.money().reconciliationStatus()).isEqualTo("RECONCILED");

    // Quick actions present
    assertThat(result.quickActions()).isNotEmpty();
    assertThat(result.quickActions())
        .extracting(QuickAction::id)
        .contains("NEW_PRODUCTION", "RECORD_RECEIPT", "LOG_EXPENSE", "DISBURSE_SALARY");
  }

  @Test
  void dashboardProducesDeterministicAttentionItems() {
    LocalDate date = LocalDate.of(2026, 9, 26);

    when(financeReads.overview()).thenReturn(Map.of("overallResult", BigDecimal.ZERO));
    when(financeReads.accounts()).thenReturn(List.of());
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "BROKEN",
                "employee_payables", BigDecimal.ZERO,
                "invoice_receivables", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(BigDecimal.ZERO);

    when(jdbc.queryForMap(contains("finance_transactions"), eq(date)))
        .thenReturn(
            Map.of("received", BigDecimal.ZERO, "disbursed", BigDecimal.ZERO, "tx_count", 0L));

    // Attention subqueries with actionable items
    when(jdbc.queryForMap(contains("finance_invoices"), eq(date)))
        .thenReturn(Map.of("count", 3L, "total", new BigDecimal("482000.00")));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 2L, "total", new BigDecimal("35000.00")));
    when(jdbc.queryForMap(contains("finance_production_profiles"), eq(date)))
        .thenReturn(Map.of("count", 1L, "total", new BigDecimal("120000.00")));
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(5);

    CommandDashboard result = service.getDashboard(date);

    assertThat(result.attention()).hasSize(5);

    // 1. Critical reconciliation broken
    AttentionItem recon = result.attention().get(0);
    assertThat(recon.severity()).isEqualTo("CRITICAL");
    assertThat(recon.type()).isEqualTo("RECONCILIATION_BROKEN");

    // 2. High overdue invoices
    AttentionItem inv = result.attention().get(1);
    assertThat(inv.severity()).isEqualTo("HIGH");
    assertThat(inv.type()).isEqualTo("OVERDUE_INVOICES");
    assertThat(inv.count()).isEqualTo(3);

    // 3. High unpaid salary
    AttentionItem sal = result.attention().get(2);
    assertThat(sal.severity()).isEqualTo("HIGH");
    assertThat(sal.type()).isEqualTo("UNPAID_SALARY");
    assertThat(sal.count()).isEqualTo(2);

    // 4. Medium production settlement
    AttentionItem prod = result.attention().get(3);
    assertThat(prod.severity()).isEqualTo("MEDIUM");
    assertThat(prod.type()).isEqualTo("PRODUCTION_SETTLEMENT_PENDING");

    // 5. Medium overdue tasks
    AttentionItem tasks = result.attention().get(4);
    assertThat(tasks.severity()).isEqualTo("MEDIUM");
    assertThat(tasks.type()).isEqualTo("OVERDUE_TASKS");
    assertThat(tasks.count()).isEqualTo(5);
  }

  @Test
  void dashboardHandlesZeroDataGracefully() {
    LocalDate date = LocalDate.of(2026, 9, 26);

    when(financeReads.overview()).thenReturn(Map.of("overallResult", BigDecimal.ZERO));
    when(financeReads.accounts()).thenReturn(List.of());
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "RECONCILED",
                "employee_payables", BigDecimal.ZERO,
                "invoice_receivables", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(BigDecimal.ZERO);

    when(jdbc.queryForMap(contains("finance_transactions"), eq(date)))
        .thenReturn(
            Map.of("received", BigDecimal.ZERO, "disbursed", BigDecimal.ZERO, "tx_count", 0L));

    when(jdbc.queryForMap(contains("finance_invoices"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_production_profiles"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(0);

    CommandDashboard result = service.getDashboard(date);

    assertThat(result).isNotNull();
    assertThat(result.today().productions()).isZero();
    assertThat(result.today().tasks()).isZero();
    assertThat(result.today().attendanceExceptions()).isZero();
    assertThat(result.today().moneyMovement().received()).isEqualTo(BigDecimal.ZERO);
    assertThat(result.money().businessPosition()).isEqualTo(BigDecimal.ZERO);
    assertThat(result.attention()).isEmpty();
    assertThat(result.operations().upcomingProductions()).isEmpty();
    assertThat(result.operations().pendingWork()).isEmpty();
    assertThat(result.operations().attendanceExceptions()).isEmpty();
    assertThat(result.recentFinancialActivity()).isEmpty();
  }
}
