package com.saproduction.command.dashboard;

import com.saproduction.command.dashboard.CommandDashboardDto.*;
import com.saproduction.command.finance.FinanceReadService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates bounded SQL projections for the Command Dashboard morning screen.
 * Reuses canonical FinanceReadService and strictly avoids duplicate accounting logic.
 * Phase 2 adds structured attention items and enriched operational context.
 */
@Service
public class CommandDashboardReadService {

  private static final Map<String, Integer> SEVERITY_PRECEDENCE =
      Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "INFO", 3);

  private final JdbcTemplate jdbc;
  private final FinanceReadService financeReads;
  private final ZoneId zone;

  public CommandDashboardReadService(
      JdbcTemplate jdbc,
      FinanceReadService financeReads,
      @Value("${app.time-zone:Asia/Kolkata}") String timeZone) {
    this.jdbc = jdbc;
    this.financeReads = financeReads;
    this.zone = ZoneId.of(timeZone);
  }

  @Transactional(readOnly = true)
  public CommandDashboard getDashboard(LocalDate requestedDate) {
    LocalDate date = requestedDate != null ? requestedDate : LocalDate.now(zone);

    Today today = queryToday(date);
    Money money = queryMoney();
    List<AttentionItem> attention = queryAttention(date, money.reconciliationStatus());
    Operations operations = queryOperations(date);
    List<FinancialActivity> recentActivity = queryRecentFinancialActivity();
    List<QuickAction> quickActions = getQuickActions();

    return new CommandDashboard(
        date, today, money, attention, operations, recentActivity, quickActions);
  }

  private Today queryToday(LocalDate date) {
    // 1. Productions scheduled on this date
    int productions =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                "SELECT count(*) FROM productions WHERE event_date = ? AND status <> 'CANCELLED'",
                Integer.class,
                date),
            0);

    // 2. Active tasks due on this date (in business timezone)
    int tasks =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM tasks
                WHERE date(due_at AT TIME ZONE 'Asia/Kolkata') = ?
                  AND status NOT IN ('DONE', 'CANCELLED')
                """,
                Integer.class,
                date),
            0);

    // 3. Attendance exceptions: recorded as ABSENT/LATE/HALF_DAY + active employees with no record
    int recordedExceptions =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                "SELECT count(*) FROM attendance_records WHERE attendance_date = ? AND status IN ('ABSENT', 'LATE', 'HALF_DAY')",
                Integer.class,
                date),
            0);

    int unrecordedEmployees =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM employees e
                WHERE e.status <> 'INACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.employee_id = e.id AND a.attendance_date = ?)
                """,
                Integer.class,
                date),
            0);

    int attendanceExceptions = recordedExceptions + unrecordedEmployees;

    // 4. Money movement on this specific business date
    var movementRow =
        jdbc.queryForMap(
            """
            SELECT
              coalesce(sum(amount) FILTER (WHERE transaction_type IN ('PRODUCTION_RECEIPT','COUNTERPARTY_RECEIPT','INVOICE_PAYMENT')), 0) AS received,
              coalesce(sum(amount) FILTER (WHERE transaction_type IN ('PRODUCTION_EXPENSE','GENERAL_EXPENSE','EMPLOYEE_EARNING','MONTHLY_SALARY_ACCRUAL','EMPLOYEE_PAYMENT','EQUIPMENT_PAYMENT')), 0) AS disbursed,
              count(*) FILTER (WHERE status = 'POSTED') AS tx_count
            FROM finance_transactions
            WHERE effective_date = ? AND status = 'POSTED'
            """,
            date);

    BigDecimal received =
        movementRow != null && movementRow.get("received") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    BigDecimal disbursed =
        movementRow != null && movementRow.get("disbursed") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    BigDecimal net = received.subtract(disbursed);
    int txCount =
        movementRow != null && movementRow.get("tx_count") instanceof Number num
            ? num.intValue()
            : 0;

    MoneyMovement moneyMovement = new MoneyMovement(received, disbursed, net, txCount);

    return new Today(productions, tasks, attendanceExceptions, moneyMovement);
  }

  private Money queryMoney() {
    // Canonical overview
    Map<String, Object> overview = financeReads.overview();
    BigDecimal businessPosition =
        overview != null && overview.get("overallResult") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;

    // Canonical owner accounts
    List<Map<String, Object>> accounts = financeReads.accounts();
    BigDecimal azeemPosition = findAccountPosition(accounts, "AZ-2");
    BigDecimal akashPosition = findAccountPosition(accounts, "AK-2");

    // Canonical reconciliation read model
    Map<String, Object> recon = financeReads.reconciliation();
    String reconciliationStatus =
        recon != null && recon.get("status") instanceof String s ? s : "UNKNOWN";
    BigDecimal employeePayable =
        recon != null && recon.get("employeePayables") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    BigDecimal invoiceReceivable =
        recon != null && recon.get("invoiceReceivables") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;

    // Direct party charge track outstanding (dual-track model)
    BigDecimal customerReceivable =
        jdbc.queryForObject(
            """
            SELECT coalesce(
              (SELECT sum(ch.amount) FROM finance_counterparty_charges ch JOIN finance_transactions t ON t.id=ch.source_transaction_id WHERE t.status='POSTED')
              -
              (SELECT sum(a.amount) FROM finance_counterparty_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE t.status='POSTED'),
              0
            )
            """,
            BigDecimal.class);
    if (customerReceivable == null) {
      customerReceivable = BigDecimal.ZERO;
    }

    BigDecimal totalReceivable = customerReceivable.add(invoiceReceivable);

    return new Money(
        businessPosition,
        azeemPosition,
        akashPosition,
        customerReceivable,
        employeePayable,
        invoiceReceivable,
        totalReceivable,
        reconciliationStatus);
  }

  private List<AttentionItem> queryAttention(LocalDate date, String reconciliationStatus) {
    List<AttentionItem> items = new ArrayList<>();

    // 1. Reconciliation issues (CRITICAL / HIGH)
    if ("BROKEN".equalsIgnoreCase(reconciliationStatus)) {
      items.add(
          new AttentionItem(
              "reconciliation-broken",
              "CRITICAL",
              "RECONCILIATION_BROKEN",
              "RECONCILIATION",
              "Finance reconciliation broken",
              "Control discrepancy detected between double-entry journal balance and physical account positions.",
              "Control discrepancy detected across journal entries and account positions.",
              "RECONCILIATION",
              null,
              null,
              1,
              "/finance?tab=RECONCILIATION",
              "tab=RECONCILIATION"));
    } else if ("WARNING".equalsIgnoreCase(reconciliationStatus)) {
      items.add(
          new AttentionItem(
              "reconciliation-warning",
              "HIGH",
              "RECONCILIATION_WARNING",
              "RECONCILIATION",
              "Finance reconciliation needs review",
              "Unresolved migration facts or balance variance in canonical ledger.",
              "Unresolved migration facts or balance variance in canonical ledger.",
              "RECONCILIATION",
              null,
              null,
              1,
              "/finance?tab=RECONCILIATION",
              "tab=RECONCILIATION"));
    }

    // 2. Overdue formal invoices (HIGH)
    var overdueInvoices =
        jdbc.queryForMap(
            """
            SELECT count(*) AS count, coalesce(sum(i.invoice_total - i.tds_amount - coalesce(p.paid_amount, 0)), 0) AS total
            FROM finance_invoices i
            JOIN finance_transactions t ON t.id = i.source_transaction_id AND t.status = 'POSTED'
            LEFT JOIN (
              SELECT a.invoice_id, sum(a.amount) AS paid_amount
              FROM finance_invoice_payment_allocations a
              JOIN finance_transactions pt ON pt.id = a.transaction_id AND pt.status = 'POSTED'
              GROUP BY a.invoice_id
            ) p ON p.invoice_id = i.id
            WHERE i.invoice_date < ?
              AND (i.invoice_total - i.tds_amount - coalesce(p.paid_amount, 0)) > 0
            """,
            date);

    int overdueInvCount =
        overdueInvoices != null && overdueInvoices.get("count") instanceof Number num
            ? num.intValue()
            : 0;
    BigDecimal overdueInvTotal =
        overdueInvoices != null && overdueInvoices.get("total") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    if (overdueInvCount > 0) {
      items.add(
          new AttentionItem(
              "overdue-invoices",
              "HIGH",
              "OVERDUE_INVOICES",
              "FINANCE",
              overdueInvCount + " invoice" + (overdueInvCount == 1 ? "" : "s") + " overdue",
              "Formal invoices with prior invoice date carry outstanding unpaid balance.",
              formatInr(overdueInvTotal)
                  + " outstanding across "
                  + overdueInvCount
                  + " overdue invoice"
                  + (overdueInvCount == 1 ? "" : "s"),
              "INVOICE",
              null,
              overdueInvTotal,
              overdueInvCount,
              "/finance?tab=INVOICES",
              "tab=INVOICES"));
    }

    // 3. Unpaid employee salary obligations (HIGH)
    var unpaidSalaries =
        jdbc.queryForMap(
            """
            SELECT count(DISTINCT o.employee_id) AS count, coalesce(sum(o.net_amount - coalesce(p.paid_amount, 0)), 0) AS total
            FROM finance_employee_obligations o
            LEFT JOIN finance_transactions t ON t.id = o.source_transaction_id
            LEFT JOIN (
              SELECT a.obligation_id, sum(a.amount) AS paid_amount
              FROM finance_employee_payment_allocations a
              JOIN finance_transactions pt ON pt.id = a.transaction_id AND pt.status = 'POSTED'
              GROUP BY a.obligation_id
            ) p ON p.obligation_id = o.id
            WHERE (t.id IS NULL OR t.status = 'POSTED')
              AND (o.net_amount - coalesce(p.paid_amount, 0)) > 0
            """);

    int unpaidEmpCount =
        unpaidSalaries != null && unpaidSalaries.get("count") instanceof Number num
            ? num.intValue()
            : 0;
    BigDecimal unpaidEmpTotal =
        unpaidSalaries != null && unpaidSalaries.get("total") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    if (unpaidEmpCount > 0 && unpaidEmpTotal.compareTo(BigDecimal.ZERO) > 0) {
      items.add(
          new AttentionItem(
              "unpaid-salary",
              "HIGH",
              "UNPAID_SALARY",
              "PAYROLL",
              "Employee payment obligations pending",
              "Approved work earnings or monthly salary accruals have not yet been disbursed to crew members.",
              formatInr(unpaidEmpTotal)
                  + " payable across "
                  + unpaidEmpCount
                  + " crew member"
                  + (unpaidEmpCount == 1 ? "" : "s"),
              "EMPLOYEE",
              null,
              unpaidEmpTotal,
              unpaidEmpCount,
              "/payroll",
              null));
    }

    // 4. Productions past date with outstanding commercial receivable (MEDIUM)
    var prodSettlement =
        jdbc.queryForMap(
            """
            SELECT count(*) AS count, coalesce(sum(f.contracted_amount - coalesce(rec.amount, 0)), 0) AS total
            FROM productions p
            JOIN finance_production_profiles f ON f.production_id = p.id
            LEFT JOIN (
              SELECT a.production_id, sum(a.amount) AS amount
              FROM finance_production_receipt_allocations a
              JOIN finance_transactions t ON t.id = a.transaction_id AND t.status = 'POSTED'
              GROUP BY a.production_id
            ) rec ON rec.production_id = p.id
            WHERE p.event_date <= ?
              AND p.status NOT IN ('CANCELLED')
              AND (f.contracted_amount - coalesce(rec.amount, 0)) > 0
            """,
            date);

    int prodSettleCount =
        prodSettlement != null && prodSettlement.get("count") instanceof Number num
            ? num.intValue()
            : 0;
    BigDecimal prodSettleTotal =
        prodSettlement != null && prodSettlement.get("total") instanceof BigDecimal bd
            ? bd
            : BigDecimal.ZERO;
    if (prodSettleCount > 0 && prodSettleTotal.compareTo(BigDecimal.ZERO) > 0) {
      items.add(
          new AttentionItem(
              "production-settlement",
              "MEDIUM",
              "PRODUCTION_SETTLEMENT_PENDING",
              "PRODUCTION",
              prodSettleCount
                  + " production"
                  + (prodSettleCount == 1 ? "" : "s")
                  + " missing settlement",
              "Productions scheduled on or before today carry uncollected contracted balances.",
              formatInr(prodSettleTotal) + " contracted balance unsettled on past productions",
              "PRODUCTION",
              null,
              prodSettleTotal,
              prodSettleCount,
              "/productions",
              null));
    }

    // 5. Overdue tasks requiring attention (MEDIUM)
    int overdueTasks =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE due_at < now() AND status NOT IN ('DONE', 'CANCELLED')",
                Integer.class),
            0);
    if (overdueTasks > 0) {
      items.add(
          new AttentionItem(
              "overdue-tasks",
              "MEDIUM",
              "OVERDUE_TASKS",
              "WORK",
              overdueTasks + " task" + (overdueTasks == 1 ? "" : "s") + " overdue",
              "Active operational tasks have passed their scheduled deadline without completion.",
              overdueTasks
                  + " operational task"
                  + (overdueTasks == 1 ? "" : "s")
                  + " past deadline require review.",
              "TASK",
              null,
              null,
              overdueTasks,
              "/work",
              "view=OVERDUE"));
    }

    // 6. Attendance exceptions today (INFO)
    int unrecorded =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM employees e
                WHERE e.status <> 'INACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.employee_id = e.id AND a.attendance_date = ?)
                """,
                Integer.class,
                date),
            0);
    if (unrecorded > 0) {
      items.add(
          new AttentionItem(
              "attendance-incomplete",
              "INFO",
              "ATTENDANCE_INCOMPLETE",
              "ATTENDANCE",
              "Attendance check required",
              "Active crew members do not yet have a check-in or leave status recorded for today.",
              unrecorded
                  + " active employee"
                  + (unrecorded == 1 ? "" : "s")
                  + " not recorded today.",
              "ATTENDANCE",
              null,
              null,
              unrecorded,
              "/attendance",
              null));
    }

    // Deterministic sorting: CRITICAL > HIGH > MEDIUM > INFO
    items.sort(Comparator.comparingInt(i -> SEVERITY_PRECEDENCE.getOrDefault(i.severity(), 99)));

    return items;
  }

  private Operations queryOperations(LocalDate date) {
    // Upcoming productions with financial profile and task counts (up to 6)
    List<DashboardProduction> productions =
        jdbc.query(
            """
            SELECT
              p.id, p.title, p.client_name, p.venue_name, p.event_date, p.start_time, p.end_time,
              p.status, p.priority, p.progress_percent,
              coalesce(f.contracted_amount, 0) AS contracted_amount,
              coalesce(rec.amount, 0) AS received_amount,
              coalesce(t.task_count, 0) AS task_count,
              coalesce(t.open_task_count, 0) AS open_task_count
            FROM productions p
            LEFT JOIN finance_production_profiles f ON f.production_id = p.id
            LEFT JOIN (
              SELECT a.production_id, sum(a.amount) AS amount
              FROM finance_production_receipt_allocations a
              JOIN finance_transactions tx ON tx.id = a.transaction_id AND tx.status = 'POSTED'
              GROUP BY a.production_id
            ) rec ON rec.production_id = p.id
            LEFT JOIN (
              SELECT
                production_id,
                count(*) AS task_count,
                count(*) FILTER (WHERE status NOT IN ('DONE', 'CANCELLED')) AS open_task_count
              FROM tasks
              WHERE production_id IS NOT NULL
              GROUP BY production_id
            ) t ON t.production_id = p.id
            WHERE p.event_date >= ? AND p.status NOT IN ('DELIVERED', 'CANCELLED')
            ORDER BY p.event_date ASC, CASE p.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END
            LIMIT 6
            """,
            this::mapProduction,
            date);

    // Pending work items / tasks partitioned by overdue, due today, and pending
    List<DashboardTask> tasks =
        jdbc.query(
            """
            SELECT
              t.id, t.title, t.priority, t.status, t.due_at,
              t.assigned_employee_id, e.display_name AS employee_name,
              t.production_id, p.title AS production_title
            FROM tasks t
            LEFT JOIN employees e ON e.id = t.assigned_employee_id
            LEFT JOIN productions p ON p.id = t.production_id
            WHERE t.status NOT IN ('DONE', 'CANCELLED')
            ORDER BY
              CASE
                WHEN t.due_at IS NOT NULL AND t.due_at < now() THEN 0
                WHEN date(t.due_at AT TIME ZONE 'Asia/Kolkata') = ? THEN 1
                ELSE 2
              END,
              CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,
              t.due_at ASC NULLS LAST,
              t.created_at DESC
            LIMIT 8
            """,
            (rs, rowNum) -> mapTask(rs, rowNum, date),
            date);

    // Attendance exceptions for today
    List<DashboardAttendanceException> exceptions =
        jdbc.query(
            """
            SELECT e.id AS employee_id, e.display_name AS employee_name, coalesce(a.status, 'UNRECORDED') AS status,
                   a.check_in_time, a.minutes_late, a.notes
            FROM employees e
            LEFT JOIN attendance_records a ON a.employee_id = e.id AND a.attendance_date = ?
            WHERE e.status <> 'INACTIVE'
              AND (a.status IN ('ABSENT', 'LATE', 'HALF_DAY') OR a.id IS NULL)
            ORDER BY CASE WHEN a.status = 'ABSENT' THEN 0 WHEN a.id IS NULL THEN 1 ELSE 2 END, e.display_name
            LIMIT 6
            """,
            this::mapAttendanceException,
            date);

    return new Operations(productions, tasks, exceptions);
  }

  private List<FinancialActivity> queryRecentFinancialActivity() {
    return jdbc.query(
        """
        SELECT t.id, t.transaction_no, t.effective_date, t.transaction_type, t.amount, t.description,
               c.display_name AS counterparty_name, e.display_name AS employee_name, p.title AS production_title, t.status
        FROM finance_transactions t
        LEFT JOIN finance_counterparties c ON c.id = t.counterparty_id
        LEFT JOIN employees e ON e.id = t.employee_id
        LEFT JOIN productions p ON p.id = t.production_id
        WHERE t.status = 'POSTED'
        ORDER BY t.effective_date DESC, t.transaction_no DESC
        LIMIT 10
        """,
        this::mapFinancialActivity);
  }

  private List<QuickAction> getQuickActions() {
    return List.of(
        new QuickAction("NEW_PRODUCTION", "New Production", "clapperboard", "/productions"),
        new QuickAction("RECORD_RECEIPT", "Record Receipt", "arrow-down-left", "/finance"),
        new QuickAction("LOG_EXPENSE", "Log Expense", "arrow-up-right", "/finance"),
        new QuickAction("DISBURSE_SALARY", "Disburse Salary", "wallet", "/payroll"),
        new QuickAction("CREATE_BILL", "Create Bill", "file-text", "/billing"),
        new QuickAction("OPEN_FINANCE", "Finance Console", "pie-chart", "/finance"));
  }

  private DashboardProduction mapProduction(ResultSet rs, int rowNum) throws SQLException {
    Time st = rs.getTime("start_time");
    Time et = rs.getTime("end_time");
    BigDecimal contracted = rs.getBigDecimal("contracted_amount");
    BigDecimal received = rs.getBigDecimal("received_amount");
    return new DashboardProduction(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("client_name"),
        rs.getString("venue_name"),
        rs.getDate("event_date").toLocalDate(),
        st != null ? st.toLocalTime() : null,
        et != null ? et.toLocalTime() : null,
        rs.getString("status"),
        rs.getString("priority"),
        rs.getInt("progress_percent"),
        contracted != null ? contracted : BigDecimal.ZERO,
        received != null ? received : BigDecimal.ZERO,
        rs.getInt("task_count"),
        rs.getInt("open_task_count"));
  }

  private DashboardTask mapTask(ResultSet rs, int rowNum, LocalDate date) throws SQLException {
    Timestamp ts = rs.getTimestamp("due_at");
    Instant dueAt = ts != null ? ts.toInstant() : null;
    boolean isOverdue = dueAt != null && dueAt.isBefore(Instant.now());
    String bucket;
    if (isOverdue) {
      bucket = "OVERDUE";
    } else if (dueAt != null && dueAt.atZone(zone).toLocalDate().equals(date)) {
      bucket = "DUE_TODAY";
    } else {
      bucket = "PENDING";
    }

    return new DashboardTask(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("priority"),
        rs.getString("status"),
        dueAt,
        rs.getString("employee_name"),
        rs.getObject("assigned_employee_id", UUID.class),
        rs.getString("production_title"),
        rs.getObject("production_id", UUID.class),
        isOverdue,
        bucket);
  }

  private DashboardAttendanceException mapAttendanceException(ResultSet rs, int rowNum)
      throws SQLException {
    Time ct = rs.getTime("check_in_time");
    int minutes = rs.getInt("minutes_late");
    return new DashboardAttendanceException(
        rs.getObject("employee_id", UUID.class),
        rs.getString("employee_name"),
        rs.getString("status"),
        ct != null ? ct.toLocalTime() : null,
        rs.wasNull() ? null : minutes,
        rs.getString("notes"));
  }

  private FinancialActivity mapFinancialActivity(ResultSet rs, int rowNum) throws SQLException {
    return new FinancialActivity(
        rs.getObject("id", UUID.class),
        rs.getLong("transaction_no"),
        rs.getDate("effective_date").toLocalDate(),
        rs.getString("transaction_type"),
        rs.getBigDecimal("amount"),
        rs.getString("description"),
        rs.getString("counterparty_name"),
        rs.getString("employee_name"),
        rs.getString("production_title"),
        rs.getString("status"));
  }

  private BigDecimal findAccountPosition(List<Map<String, Object>> accounts, String code) {
    if (accounts == null) return BigDecimal.ZERO;
    for (Map<String, Object> acc : accounts) {
      if (code.equalsIgnoreCase((String) acc.get("code"))) {
        Object pos = acc.get("position");
        if (pos instanceof BigDecimal bd) return bd;
        if (pos instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
      }
    }
    return BigDecimal.ZERO;
  }

  private String formatInr(BigDecimal amount) {
    if (amount == null) return "₹0";
    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    return fmt.format(amount);
  }
}
