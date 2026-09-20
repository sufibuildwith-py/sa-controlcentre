package com.saproduction.command.employee;

import com.saproduction.command.payroll.PayrollService;
import com.saproduction.command.shared.ApiEnvelope;
import com.saproduction.command.work.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees/{id}/operations")
public class EmployeeOperationsController {
  public record Performance(
      long attendanceRecords,
      long attended,
      long lateCount,
      long tasksAssigned,
      long tasksCompleted,
      long overdueTasks,
      long activeProductions,
      long completedProductions,
      Integer attendanceRate,
      Integer onTimeCompletionRate) {}

  public record View(
      List<WorkTaskService.View> work,
      List<PayrollService.View> payroll,
      Performance performance) {}

  private final EmployeeService employees;
  private final WorkTaskService tasks;
  private final PayrollService payroll;
  private final JdbcTemplate jdbc;

  public EmployeeOperationsController(
      EmployeeService employees, WorkTaskService tasks, PayrollService payroll, JdbcTemplate jdbc) {
    this.employees = employees;
    this.tasks = tasks;
    this.payroll = payroll;
    this.jdbc = jdbc;
  }

  @GetMapping
  public ApiEnvelope<View> get(@PathVariable UUID id) {
    employees.getEntity(id);
    var work = tasks.list(id, null, null, null, null, null, null);
    var periods = payroll.listForEmployee(id);
    long attendance = count("select count(*) from attendance_records where employee_id=?", id),
        attended =
            count(
                "select count(*) from attendance_records where employee_id=? and status in ('PRESENT','LATE','HALF_DAY')",
                id),
        late =
            count(
                "select count(*) from attendance_records where employee_id=? and status='LATE'",
                id),
        assigned = count("select count(*) from tasks where assigned_employee_id=?", id),
        completed =
            count("select count(*) from tasks where assigned_employee_id=? and status='DONE'", id),
        completedWithDue =
            count(
                "select count(*) from tasks where assigned_employee_id=? and status='DONE' and due_at is not null",
                id),
        onTime =
            count(
                "select count(*) from tasks where assigned_employee_id=? and status='DONE' and due_at is not null and completed_at<=due_at",
                id),
        overdue =
            count(
                "select count(*) from tasks where assigned_employee_id=? and due_at<now() and status not in ('DONE','CANCELLED')",
                id),
        active =
            count(
                "select count(*) from production_members m join productions p on p.id=m.production_id where m.employee_id=? and p.status not in ('DELIVERED','CANCELLED')",
                id),
        done =
            count(
                "select count(*) from production_members m join productions p on p.id=m.production_id where m.employee_id=? and p.status='DELIVERED'",
                id);
    Integer attendanceRate =
        attendance == 0 ? null : (int) Math.round(attended * 100d / attendance);
    Integer completionRate =
        completedWithDue == 0 ? null : (int) Math.round(onTime * 100d / completedWithDue);
    return ApiEnvelope.of(
        new View(
            work,
            periods,
            new Performance(
                attendance,
                attended,
                late,
                assigned,
                completed,
                overdue,
                active,
                done,
                attendanceRate,
                completionRate)));
  }

  private long count(String sql, UUID id) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, id));
  }
}
