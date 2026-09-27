package com.saproduction.command.employee;

import com.saproduction.command.attendance.AttendanceRecord;
import com.saproduction.command.attendance.AttendanceRepository;
import com.saproduction.command.attendance.AttendanceService;
import com.saproduction.command.employee.Employee360Dto.*;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.leave.LeaveRequest;
import com.saproduction.command.navigator.NavigatorConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates unified read telemetry for the Employee 360 command layer.
 * Strictly composes existing domain services (Employee, Finance, Attendance, Work, Leave, Production).
 */
@Service
public class Employee360Service {

  private final EmployeeService employeeService;
  private final FinanceReadService financeReads;
  private final AttendanceRepository attendanceRepo;
  private final AttendanceService attendanceService;
  private final JdbcTemplate jdbc;
  private final ZoneId zone;
  private final NavigatorConfig navigatorConfig;

  @Autowired
  public Employee360Service(
      EmployeeService employeeService,
      FinanceReadService financeReads,
      AttendanceRepository attendanceRepo,
      AttendanceService attendanceService,
      JdbcTemplate jdbc,
      @Value("${app.time-zone:Asia/Kolkata}") String timeZone,
      @Autowired(required = false) NavigatorConfig navigatorConfig) {
    this.employeeService = employeeService;
    this.financeReads = financeReads;
    this.attendanceRepo = attendanceRepo;
    this.attendanceService = attendanceService;
    this.jdbc = jdbc;
    this.zone = ZoneId.of(timeZone);
    this.navigatorConfig = navigatorConfig;
  }

  @Transactional(readOnly = true)
  public Employee360View get360(UUID id) {
    Employee employeeEntity = employeeService.getEntity(id);
    EmployeeDtos.View employeeView = EmployeeDtos.view(employeeEntity);

    LocalDate today = LocalDate.now(zone);

    // 1. TODAY SUMMARY
    AttendanceRecord todayRec =
        attendanceRepo.findByEmployeeIdAndDate(id, today).orElse(null);
    AttendanceService.RecordView todayAttendanceView =
        todayRec != null ? attendanceService.view(todayRec) : null;

    LeaveSummary activeOrPendingLeave = findActiveOrPendingLeave(id, today);

    long activeTasksCount =
        count(
            "SELECT count(*) FROM tasks WHERE assigned_employee_id = ? AND status NOT IN ('DONE','CANCELLED')",
            id);
    long overdueTasksCount =
        count(
            "SELECT count(*) FROM tasks WHERE assigned_employee_id = ? AND due_at < now() AND status NOT IN ('DONE','CANCELLED')",
            id);
    long activeProductionsCount =
        count(
            "SELECT count(*) FROM production_members pm JOIN productions p ON p.id = pm.production_id WHERE pm.employee_id = ? AND p.status NOT IN ('DELIVERED','CANCELLED')",
            id);

    TodaySummary todaySummary =
        new TodaySummary(
            today,
            todayAttendanceView,
            activeOrPendingLeave,
            activeTasksCount,
            overdueTasksCount,
            activeProductionsCount);

    // 2. MONEY SUMMARY (Authoritative canonical FinanceReadService)
    Map<String, Object> fin = financeReads.employee(id);
    BigDecimal earned = (BigDecimal) fin.getOrDefault("earned", BigDecimal.ZERO);
    BigDecimal paid = (BigDecimal) fin.getOrDefault("paid", BigDecimal.ZERO);
    BigDecimal outstanding = (BigDecimal) fin.getOrDefault("outstanding", BigDecimal.ZERO);

    PayrollStatus latestPayroll = getLatestPayroll(id);
    String payrollStatus = latestPayroll != null ? latestPayroll.status() : null;
    String payrollPeriod = latestPayroll != null ? latestPayroll.period() : null;

    MoneySummary moneySummary =
        new MoneySummary(
            earned,
            paid,
            outstanding,
            employeeView.baseSalaryMinor(),
            employeeView.salaryCurrency(),
            payrollStatus,
            payrollPeriod);

    // 3. OPERATIONS SUMMARY
    long completedProductionsCount =
        count(
            "SELECT count(*) FROM production_members pm JOIN productions p ON p.id = pm.production_id WHERE pm.employee_id = ? AND p.status = 'DELIVERED'",
            id);
    long tasksAssigned =
        count("SELECT count(*) FROM tasks WHERE assigned_employee_id = ?", id);
    long tasksCompleted =
        count("SELECT count(*) FROM tasks WHERE assigned_employee_id = ? AND status = 'DONE'", id);

    List<ActiveProductionSummary> activeProductions =
        jdbc.query(
            """
            SELECT p.id, p.title, pm.production_role, p.event_date, p.status
            FROM production_members pm
            JOIN productions p ON p.id = pm.production_id
            WHERE pm.employee_id = ? AND p.status NOT IN ('DELIVERED','CANCELLED')
            ORDER BY p.event_date ASC
            LIMIT 10
            """,
            (rs, rowNum) ->
                new ActiveProductionSummary(
                    (UUID) rs.getObject("id"),
                    rs.getString("title"),
                    rs.getString("production_role"),
                    rs.getDate("event_date").toLocalDate(),
                    rs.getString("status")),
            id);
    if (activeProductions == null) {
      activeProductions = List.of();
    }

    OperationsSummary operationsSummary =
        new OperationsSummary(
            activeProductionsCount,
            completedProductionsCount,
            tasksAssigned,
            tasksCompleted,
            overdueTasksCount,
            activeProductions);

    // 4. PERFORMANCE SUMMARY
    long attendanceRecords =
        count("SELECT count(*) FROM attendance_records WHERE employee_id = ?", id);
    long attended =
        count(
            "SELECT count(*) FROM attendance_records WHERE employee_id = ? AND status IN ('PRESENT','LATE','HALF_DAY')",
            id);
    long lateCount =
        count(
            "SELECT count(*) FROM attendance_records WHERE employee_id = ? AND status = 'LATE'",
            id);
    long completedWithDue =
        count(
            "SELECT count(*) FROM tasks WHERE assigned_employee_id = ? AND status = 'DONE' AND due_at IS NOT NULL",
            id);
    long onTime =
        count(
            "SELECT count(*) FROM tasks WHERE assigned_employee_id = ? AND status = 'DONE' AND due_at IS NOT NULL AND completed_at <= due_at",
            id);

    Integer attendanceRate =
        attendanceRecords == 0 ? null : (int) Math.round(attended * 100d / attendanceRecords);
    Integer onTimeCompletionRate =
        completedWithDue == 0 ? null : (int) Math.round(onTime * 100d / completedWithDue);

    PerformanceSummary performanceSummary =
        new PerformanceSummary(
            attendanceRecords, attended, lateCount, attendanceRate, onTimeCompletionRate);

    // 5. COMMUNICATION SUMMARY
    CommunicationSummary commsSummary = getCommunicationSummary(id);

    // 6. NAVIGATOR SUMMARY
    NavigatorSummary navSummary = getNavigatorSummary(id);

    return new Employee360View(
        employeeView,
        todaySummary,
        moneySummary,
        operationsSummary,
        performanceSummary,
        commsSummary,
        navSummary);
  }

  private long count(String sql, UUID id) {
    Long value = jdbc.queryForObject(sql, Long.class, id);
    return value != null ? value : 0L;
  }

  private record PayrollStatus(String status, String period) {}

  private PayrollStatus getLatestPayroll(UUID id) {
    try {
      PayrollStatus res =
          jdbc.query(
              """
              SELECT pi.payment_status, pp.month, pp.year
              FROM payroll_items pi
              JOIN payroll_periods pp ON pp.id = pi.payroll_period_id
              WHERE pi.employee_id = ?
              ORDER BY pp.year DESC, pp.month DESC
              LIMIT 1
              """,
              rs -> {
                if (rs.next()) {
                  String status = rs.getString("payment_status");
                  int month = rs.getInt("month");
                  int year = rs.getInt("year");
                  return new PayrollStatus(status, String.format("%02d/%d", month, year));
                }
                return new PayrollStatus("NO_PAYROLL", null);
              },
              id);
      return res != null ? res : new PayrollStatus("NO_PAYROLL", null);
    } catch (Exception ex) {
      return new PayrollStatus("NO_PAYROLL", null);
    }
  }

  private LeaveSummary findActiveOrPendingLeave(UUID id, LocalDate today) {
    try {
      return jdbc.query(
          """
          SELECT id, start_date, end_date, leave_type, reason, status
          FROM leave_requests
          WHERE employee_id = ?
            AND (status = 'PENDING' OR (status = 'APPROVED' AND start_date <= ? AND end_date >= ?))
          ORDER BY created_at DESC
          LIMIT 1
          """,
          rs -> {
            if (rs.next()) {
              return new LeaveSummary(
                  (UUID) rs.getObject("id"),
                  rs.getDate("start_date").toLocalDate(),
                  rs.getDate("end_date").toLocalDate(),
                  rs.getString("leave_type"),
                  rs.getString("reason"),
                  LeaveRequest.Status.valueOf(rs.getString("status")));
            }
            return null;
          },
          id, today, today);
    } catch (Exception ex) {
      return null;
    }
  }

  private CommunicationSummary getCommunicationSummary(UUID id) {
    try {
      return jdbc.query(
          """
          SELECT count(*) AS msg_count, max(queued_at) AS last_msg
          FROM outbound_messages
          WHERE employee_id = ?
          """,
          rs -> {
            if (rs.next()) {
              long count = rs.getLong("msg_count");
              var ts = rs.getTimestamp("last_msg");
              Instant lastContact = ts != null ? ts.toInstant() : null;
              return new CommunicationSummary(count, lastContact, true);
            }
            return new CommunicationSummary(0, null, true);
          },
          id);
    } catch (Exception ex) {
      return new CommunicationSummary(0, null, true);
    }
  }

  private NavigatorSummary getNavigatorSummary(UUID id) {
    boolean enabled = navigatorConfig != null && navigatorConfig.isEnabled();
    return new NavigatorSummary(enabled, false, enabled ? "STANDBY" : "DISABLED", null);
  }
}
