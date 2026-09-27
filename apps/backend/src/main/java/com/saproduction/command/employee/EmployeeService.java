package com.saproduction.command.employee;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  private final EmployeeRepository employees;
  private final AuditService audit;
  private final DomainEventService events;
  private final JdbcTemplate jdbc;

  @Autowired
  public EmployeeService(
      EmployeeRepository employees,
      AuditService audit,
      DomainEventService events,
      @Autowired(required = false) JdbcTemplate jdbc) {
    this.employees = employees;
    this.audit = audit;
    this.events = events;
    this.jdbc = jdbc;
  }

  public EmployeeService(
      EmployeeRepository employees, AuditService audit, DomainEventService events) {
    this(employees, audit, events, null);
  }

  @Transactional(readOnly = true)
  public EmployeeDtos.PeopleSummary getSummary() {
    if (jdbc == null) {
      return new EmployeeDtos.PeopleSummary(0, 0, 0, 0, 0);
    }
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT count(*) FROM employees WHERE status = 'ACTIVE') AS active_count,
          (SELECT count(*) FROM employees WHERE status = 'ON_LEAVE') AS on_leave_count,
          (SELECT count(*) FROM attendance_records WHERE attendance_date = ? AND status IN ('PRESENT','LATE','HALF_DAY')) AS attendance_today,
          (SELECT count(*) FROM tasks WHERE status NOT IN ('DONE','CANCELLED')) AS open_tasks,
          (SELECT count(*) FROM tasks WHERE due_at < now() AND status NOT IN ('DONE','CANCELLED')) AS overdue_tasks
        """,
        (rs, rowNum) ->
            new EmployeeDtos.PeopleSummary(
                rs.getLong("active_count"),
                rs.getLong("on_leave_count"),
                rs.getLong("attendance_today"),
                rs.getLong("open_tasks"),
                rs.getLong("overdue_tasks")),
        today);
  }

  @Transactional(readOnly = true)
  public List<EmployeeDtos.View> list(String search, Employee.Status status) {
    List<Employee> raw = employees
        .findAll(
            (root, query, cb) -> {
              List<Predicate> filters = new ArrayList<>();
              if (status != null) filters.add(cb.equal(root.get("status"), status));
              if (!blank(search)) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                filters.add(
                    cb.or(
                        cb.like(cb.lower(root.get("displayName")), pattern),
                        cb.like(cb.lower(root.get("employeeCode")), pattern),
                        cb.like(cb.lower(root.get("roleTitle")), pattern)));
              }
              return cb.and(filters.toArray(Predicate[]::new));
            },
            Sort.by(Sort.Direction.ASC, "displayName"));

    if (jdbc == null || raw.isEmpty()) {
      return raw.stream().map(EmployeeDtos::view).toList();
    }

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    Map<UUID, String> attendanceToday = new HashMap<>();
    jdbc.query(
        "SELECT employee_id, status FROM attendance_records WHERE attendance_date = ?",
        rs -> {
          attendanceToday.put((UUID) rs.getObject("employee_id"), rs.getString("status"));
        },
        today);

    Map<UUID, Long> taskCounts = new HashMap<>();
    jdbc.query(
        "SELECT assigned_employee_id, count(*) FROM tasks WHERE status NOT IN ('DONE','CANCELLED') GROUP BY assigned_employee_id",
        rs -> {
          taskCounts.put((UUID) rs.getObject(1), rs.getLong(2));
        });

    Map<UUID, Long> prodCounts = new HashMap<>();
    jdbc.query(
        "SELECT pm.employee_id, count(*) FROM production_members pm JOIN productions p ON p.id = pm.production_id WHERE p.status NOT IN ('DELIVERED','CANCELLED') GROUP BY pm.employee_id",
        rs -> {
          prodCounts.put((UUID) rs.getObject(1), rs.getLong(2));
        });

    return raw.stream()
        .map(
            e -> {
              EmployeeDtos.View v = EmployeeDtos.view(e);
              return new EmployeeDtos.View(
                  v.id(),
                  v.employeeCode(),
                  v.firstName(),
                  v.lastName(),
                  v.displayName(),
                  v.phone(),
                  v.whatsappPhone(),
                  v.email(),
                  v.roleTitle(),
                  v.department(),
                  v.employmentType(),
                  v.joiningDate(),
                  v.baseSalaryMinor(),
                  v.salaryCurrency(),
                  v.status(),
                  v.profilePhotoUrl(),
                  v.notes(),
                  v.createdAt(),
                  v.updatedAt(),
                  attendanceToday.get(v.id()),
                  taskCounts.getOrDefault(v.id(), 0L),
                  prodCounts.getOrDefault(v.id(), 0L));
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public Employee getEntity(UUID id) {
    return org.hibernate.Hibernate.unproxy(
        employees
            .findById(id)
            .orElseThrow(
                () -> ApiException.notFound("EMPLOYEE_NOT_FOUND", "Employee was not found.")),
        Employee.class);
  }

  @Transactional(readOnly = true)
  public EmployeeDtos.View get(UUID id) {
    return EmployeeDtos.view(getEntity(id));
  }

  @Transactional
  public EmployeeDtos.View create(EmployeeDtos.Input in) {
    if (employees.existsByEmployeeCodeIgnoreCase(in.employeeCode()))
      throw ApiException.conflict("EMPLOYEE_CODE_EXISTS", "Employee code is already in use.");
    Employee e = new Employee();
    apply(e, in);
    try {
      employees.saveAndFlush(e);
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.conflict(
          "EMPLOYEE_CONFLICT", "Employee details conflict with an existing record.");
    }
    audit.record("EMPLOYEE", "EMPLOYEE_CREATED", e.id.toString(), null, evidence(e));
    events.emit(
        "EMPLOYEE_CREATED",
        "EMPLOYEE",
        e.id,
        Map.of("employeeId", e.id, "displayName", e.displayName));
    return EmployeeDtos.view(e);
  }

  @Transactional
  public EmployeeDtos.View update(UUID id, EmployeeDtos.Input in) {
    Employee e = getEntity(id);
    Map<String, Object> before = evidence(e);
    if (!e.employeeCode.equalsIgnoreCase(in.employeeCode())
        && employees.existsByEmployeeCodeIgnoreCase(in.employeeCode()))
      throw ApiException.conflict("EMPLOYEE_CODE_EXISTS", "Employee code is already in use.");
    long salary = e.baseSalaryMinor;
    apply(e, in);
    employees.saveAndFlush(e);
    var after = EmployeeDtos.view(e);
    audit.record("EMPLOYEE", "EMPLOYEE_EDITED", id.toString(), before, evidence(e));
    if (salary != e.baseSalaryMinor)
      audit.record(
          "EMPLOYEE",
          "SALARY_BASIS_CHANGED",
          id.toString(),
          Map.of("amountMinor", salary, "currency", "INR"),
          Map.of("amountMinor", e.baseSalaryMinor, "currency", "INR"));
    return after;
  }

  @Transactional
  public EmployeeDtos.View deactivate(UUID id) {
    Employee e = getEntity(id);
    Employee.Status before = e.status;
    e.status = Employee.Status.INACTIVE;
    employees.save(e);
    var after = EmployeeDtos.view(e);
    audit.record(
        "EMPLOYEE",
        "EMPLOYEE_DEACTIVATED",
        id.toString(),
        Map.of("status", before),
        Map.of("status", e.status));
    return after;
  }

  private void apply(Employee e, EmployeeDtos.Input in) {
    e.employeeCode = in.employeeCode().trim();
    e.firstName = in.firstName().trim();
    e.lastName = clean(in.lastName());
    e.displayName = in.displayName().trim();
    e.phone = in.phone().trim();
    e.whatsappPhone = clean(in.whatsappPhone());
    e.email = clean(in.email());
    e.roleTitle = in.roleTitle().trim();
    e.department = in.department().trim();
    e.employmentType = in.employmentType().trim();
    e.joiningDate = in.joiningDate();
    e.baseSalaryMinor = in.baseSalaryMinor();
    e.salaryCurrency = in.salaryCurrency();
    e.status = in.status() == null ? Employee.Status.ACTIVE : in.status();
    e.profilePhotoUrl = clean(in.profilePhotoUrl());
    e.notes = clean(in.notes());
  }

  private Map<String, Object> evidence(Employee e) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("employeeCode", e.employeeCode);
    value.put("displayName", e.displayName);
    value.put("roleTitle", e.roleTitle);
    value.put("department", e.department);
    value.put("employmentType", e.employmentType);
    value.put("status", e.status);
    return value;
  }

  private static String clean(String s) {
    return blank(s) ? null : s.trim();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
