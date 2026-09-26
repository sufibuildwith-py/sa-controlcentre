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

    // Mock reconciliation with real FinanceReadService camelCase contract
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "RECONCILED",
                "employeePayables", new BigDecimal("45000.00"),
                "invoiceReceivables", new BigDecimal("180000.00")));

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
        .isEqualTo(new BigDecimal("255000.00")); // dual-track sum (75000 + 180000)
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
                "employeePayables", BigDecimal.ZERO,
                "invoiceReceivables", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(BigDecimal.ZERO);

    when(jdbc.queryForMap(contains("finance_transactions"), eq(date)))
        .thenReturn(
            Map.of("received", BigDecimal.ZERO, "disbursed", BigDecimal.ZERO, "tx_count", 0L));

    when(jdbc.queryForMap(contains("finance_invoices"), eq(date)))
        .thenReturn(Map.of("count", 3L, "total", new BigDecimal("482000.00")));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 2L, "total", new BigDecimal("35000.00")));
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(5);

    CommandDashboard result = service.getDashboard(date);

    assertThat(result.attention()).hasSize(4);

    // 1. Critical reconciliation broken
    AttentionItem recon = result.attention().get(0);
    assertThat(recon.severity()).isEqualTo("CRITICAL");
    assertThat(recon.type()).isEqualTo("RECONCILIATION_BROKEN");
    assertThat(recon.category()).isEqualTo("RECONCILIATION");
    assertThat(recon.reason()).containsIgnoringCase("discrepancy detected");
    assertThat(recon.route()).isEqualTo("/finance?tab=RECONCILIATION");

    // 2. High overdue invoices
    AttentionItem inv = result.attention().get(1);
    assertThat(inv.severity()).isEqualTo("HIGH");
    assertThat(inv.type()).isEqualTo("OVERDUE_INVOICES");
    assertThat(inv.category()).isEqualTo("FINANCE");
    assertThat(inv.amount()).isEqualTo(new BigDecimal("482000.00"));
    assertThat(inv.count()).isEqualTo(3);
    assertThat(inv.queryParams()).isEqualTo("tab=INVOICES");

    // 3. High unpaid salary
    AttentionItem sal = result.attention().get(2);
    assertThat(sal.severity()).isEqualTo("HIGH");
    assertThat(sal.type()).isEqualTo("UNPAID_SALARY");
    assertThat(sal.category()).isEqualTo("PAYROLL");
    assertThat(sal.amount()).isEqualTo(new BigDecimal("35000.00"));
    assertThat(sal.count()).isEqualTo(2);
    assertThat(sal.route()).isEqualTo("/payroll");

    // 4. Medium overdue tasks (real-time exception)
    AttentionItem tasks = result.attention().get(3);
    assertThat(tasks.severity()).isEqualTo("MEDIUM");
    assertThat(tasks.type()).isEqualTo("OVERDUE_TASKS");
    assertThat(tasks.category()).isEqualTo("WORK");
    assertThat(tasks.count()).isEqualTo(5);
    assertThat(tasks.queryParams()).isEqualTo("view=OVERDUE");
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
                "employeePayables", BigDecimal.ZERO,
                "invoiceReceivables", BigDecimal.ZERO));
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

  @Test
  void reconciliationDefensivelyHandlesMissingOrNullKeysWithoutNpe() {
    LocalDate date = LocalDate.of(2026, 9, 26);

    when(financeReads.overview()).thenReturn(Map.of("overallResult", new BigDecimal("10000.00")));
    when(financeReads.accounts()).thenReturn(List.of());

    // Case 1: Reconciliation map with explicit null values for optional payables/receivables
    Map<String, Object> reconWithNulls = new HashMap<>();
    reconWithNulls.put("status", "RECONCILED");
    reconWithNulls.put("employeePayables", null);
    reconWithNulls.put("invoiceReceivables", null);
    when(financeReads.reconciliation()).thenReturn(reconWithNulls);

    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(new BigDecimal("50000.00"));
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

    assertThat(result.money().employeePayable()).isEqualTo(BigDecimal.ZERO);
    assertThat(result.money().invoiceReceivable()).isEqualTo(BigDecimal.ZERO);
    // totalReceivable is customerReceivable (50000) + fallback 0
    assertThat(result.money().totalReceivable()).isEqualTo(new BigDecimal("50000.00"));
    assertThat(result.money().reconciliationStatus()).isEqualTo("RECONCILED");

    // Case 2: Reconciliation map completely null
    when(financeReads.reconciliation()).thenReturn(null);
    CommandDashboard nullReconResult = service.getDashboard(date);

    assertThat(nullReconResult.money().employeePayable()).isEqualTo(BigDecimal.ZERO);
    assertThat(nullReconResult.money().invoiceReceivable()).isEqualTo(BigDecimal.ZERO);
    assertThat(nullReconResult.money().totalReceivable()).isEqualTo(new BigDecimal("50000.00"));
    assertThat(nullReconResult.money().reconciliationStatus()).isEqualTo("UNKNOWN");
  }

  @Test
  void dashboardEnrichesOperationsContext() {
    LocalDate date = LocalDate.of(2026, 9, 26);
    when(financeReads.overview()).thenReturn(Map.of("overallResult", BigDecimal.ZERO));
    when(financeReads.accounts()).thenReturn(List.of());
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "RECONCILED",
                "employeePayables", BigDecimal.ZERO,
                "invoiceReceivables", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(BigDecimal.ZERO);
    when(jdbc.queryForMap(contains("finance_transactions"), eq(date)))
        .thenReturn(Map.of("received", BigDecimal.ZERO, "disbursed", BigDecimal.ZERO, "tx_count", 0L));
    when(jdbc.queryForMap(contains("finance_invoices"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_production_profiles"), eq(date)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(0);

    UUID prodId = UUID.randomUUID();
    DashboardProduction mockProd =
        new DashboardProduction(
            prodId,
            "Summit 2026",
            "Acme Corp",
            "Convention Hall",
            date,
            null,
            null,
            "PRODUCTION",
            "HIGH",
            65,
            new BigDecimal("500000.00"),
            new BigDecimal("350000.00"),
            8,
            3);

    when(jdbc.query(contains("FROM productions p"), any(org.springframework.jdbc.core.RowMapper.class), eq(date)))
        .thenReturn(List.of(mockProd));

    UUID taskId = UUID.randomUUID();
    DashboardTask mockTask =
        new DashboardTask(
            taskId,
            "Stage Setup",
            "URGENT",
            "IN_PROGRESS",
            java.time.Instant.now().minusSeconds(3600),
            "Roshan",
            UUID.randomUUID(),
            "Summit 2026",
            prodId,
            true,
            "OVERDUE");

    when(jdbc.query(contains("FROM tasks t"), any(org.springframework.jdbc.core.RowMapper.class), eq(date)))
        .thenReturn(List.of(mockTask));

    CommandDashboard result = service.getDashboard(date);

    assertThat(result.operations().upcomingProductions()).hasSize(1);
    DashboardProduction p = result.operations().upcomingProductions().get(0);
    assertThat(p.contractedAmount()).isEqualTo(new BigDecimal("500000.00"));
    assertThat(p.receivedAmount()).isEqualTo(new BigDecimal("350000.00"));
    assertThat(p.taskCount()).isEqualTo(8);
    assertThat(p.openTaskCount()).isEqualTo(3);

    assertThat(result.operations().pendingWork()).hasSize(1);
    DashboardTask t = result.operations().pendingWork().get(0);
    assertThat(t.isOverdue()).isTrue();
    assertThat(t.bucket()).isEqualTo("OVERDUE");
    assertThat(t.productionTitle()).isEqualTo("Summit 2026");
  }

  @Test
  void taskOverdueStateIsRealTimeIndependentOfSelectedDate() {
    LocalDate historicalDate = LocalDate.of(2026, 8, 1);
    when(financeReads.overview()).thenReturn(Map.of("overallResult", BigDecimal.ZERO));
    when(financeReads.accounts()).thenReturn(List.of());
    when(financeReads.reconciliation())
        .thenReturn(
            Map.of(
                "status", "RECONCILED",
                "employeePayables", BigDecimal.ZERO,
                "invoiceReceivables", BigDecimal.ZERO));
    when(jdbc.queryForObject(contains("finance_counterparty_charges"), eq(BigDecimal.class)))
        .thenReturn(BigDecimal.ZERO);
    when(jdbc.queryForMap(contains("finance_transactions"), eq(historicalDate)))
        .thenReturn(Map.of("received", BigDecimal.ZERO, "disbursed", BigDecimal.ZERO, "tx_count", 0L));
    when(jdbc.queryForMap(contains("finance_invoices"), eq(historicalDate)))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));
    when(jdbc.queryForMap(contains("finance_employee_obligations")))
        .thenReturn(Map.of("count", 0L, "total", BigDecimal.ZERO));

    // Tasks due on historical date = 4
    when(jdbc.queryForObject(contains("FROM tasks"), eq(Integer.class), eq(historicalDate)))
        .thenReturn(4);

    // Live overdue tasks in physical reality = 2
    when(jdbc.queryForObject(contains("tasks WHERE due_at < now()"), eq(Integer.class)))
        .thenReturn(2);

    CommandDashboard result = service.getDashboard(historicalDate);

    // Selected date query bounds tasks due on that date
    assertThat(result.today().tasks()).isEqualTo(4);

    // Attention queue retains live real-time overdue exception (due_at < now())
    assertThat(result.attention()).hasSize(1);
    AttentionItem item = result.attention().get(0);
    assertThat(item.type()).isEqualTo("OVERDUE_TASKS");
    assertThat(item.count()).isEqualTo(2);

    // Does NOT contain speculative PRODUCTION_SETTLEMENT_PENDING
    assertThat(result.attention())
        .noneMatch(a -> "PRODUCTION_SETTLEMENT_PENDING".equals(a.type()));
  }
}
