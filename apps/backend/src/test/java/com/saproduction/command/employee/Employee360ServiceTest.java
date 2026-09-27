package com.saproduction.command.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.attendance.AttendanceRecord;
import com.saproduction.command.attendance.AttendanceRepository;
import com.saproduction.command.attendance.AttendanceService;
import com.saproduction.command.employee.Employee360Dto.Employee360View;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.leave.LeaveRepository;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

class Employee360ServiceTest {

  private EmployeeService employeeService;
  private FinanceReadService financeReads;
  private AttendanceRepository attendanceRepo;
  private AttendanceService attendanceService;
  private JdbcTemplate jdbc;
  private Employee360Service service;

  private final UUID employeeId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    employeeService = mock(EmployeeService.class);
    financeReads = mock(FinanceReadService.class);
    attendanceRepo = mock(AttendanceRepository.class);
    attendanceService = mock(AttendanceService.class);
    jdbc = mock(JdbcTemplate.class);

    service =
        new Employee360Service(
            employeeService,
            financeReads,
            attendanceRepo,
            attendanceService,
            jdbc,
            "Asia/Kolkata",
            null);
  }

  @Test
  void get360AggregatesAuthoritativeDomainTelemetry() {
    Employee mockEmp = new Employee();
    mockEmp.id = employeeId;
    mockEmp.employeeCode = "SA-01";
    mockEmp.firstName = "Amaan";
    mockEmp.lastName = "Khan";
    mockEmp.displayName = "Amaan Khan";
    mockEmp.roleTitle = "Lead Sound Engineer";
    mockEmp.department = "Audio";
    mockEmp.employmentType = "FULL_TIME";
    mockEmp.joiningDate = LocalDate.of(2024, 1, 1);
    mockEmp.baseSalaryMinor = 4500000L;
    mockEmp.salaryCurrency = "INR";
    mockEmp.status = Employee.Status.ACTIVE;
    mockEmp.phone = "+919999999999";
    mockEmp.createdAt = Instant.now();
    mockEmp.updatedAt = Instant.now();

    when(employeeService.getEntity(employeeId)).thenReturn(mockEmp);

    // Mock finance read service
    when(financeReads.employee(employeeId))
        .thenReturn(
            Map.of(
                "earned", new BigDecimal("120000.00"),
                "paid", new BigDecimal("80000.00"),
                "outstanding", new BigDecimal("40000.00")));

    // Mock attendance repo
    when(attendanceRepo.findByEmployeeIdAndDate(eq(employeeId), any(LocalDate.class)))
        .thenReturn(Optional.empty());

    // Mock counts in operations & performance
    when(jdbc.queryForObject(contains("tasks WHERE assigned_employee_id = ? AND status NOT IN"), eq(Long.class), eq(employeeId)))
        .thenReturn(5L);
    when(jdbc.queryForObject(contains("tasks WHERE assigned_employee_id = ? AND due_at < now()"), eq(Long.class), eq(employeeId)))
        .thenReturn(1L);
    when(jdbc.queryForObject(contains("production_members pm JOIN productions p"), eq(Long.class), eq(employeeId)))
        .thenReturn(2L);
    when(jdbc.queryForObject(contains("status = 'DELIVERED'"), eq(Long.class), eq(employeeId)))
        .thenReturn(8L);
    when(jdbc.queryForObject(eq("SELECT count(*) FROM tasks WHERE assigned_employee_id = ?"), eq(Long.class), eq(employeeId)))
        .thenReturn(14L);
    when(jdbc.queryForObject(contains("status = 'DONE'"), eq(Long.class), eq(employeeId)))
        .thenReturn(9L);
    when(jdbc.queryForObject(contains("attendance_records WHERE employee_id = ?"), eq(Long.class), eq(employeeId)))
        .thenReturn(30L);
    when(jdbc.queryForObject(contains("status IN ('PRESENT','LATE','HALF_DAY')"), eq(Long.class), eq(employeeId)))
        .thenReturn(28L);
    when(jdbc.queryForObject(contains("status = 'LATE'"), eq(Long.class), eq(employeeId)))
        .thenReturn(2L);
    when(jdbc.queryForObject(contains("status = 'DONE' AND due_at IS NOT NULL"), eq(Long.class), eq(employeeId)))
        .thenReturn(9L);
    when(jdbc.queryForObject(contains("completed_at <= due_at"), eq(Long.class), eq(employeeId)))
        .thenReturn(8L);

    Employee360View view = service.get360(employeeId);

    assertThat(view).isNotNull();
    assertThat(view.employee().displayName()).isEqualTo("Amaan Khan");
    assertThat(view.employee().roleTitle()).isEqualTo("Lead Sound Engineer");

    // Money assertions
    assertThat(view.money().earned()).isEqualTo(new BigDecimal("120000.00"));
    assertThat(view.money().paid()).isEqualTo(new BigDecimal("80000.00"));
    assertThat(view.money().outstanding()).isEqualTo(new BigDecimal("40000.00"));
    assertThat(view.money().baseSalaryMinor()).isEqualTo(4500000L);
    assertThat(view.money().salaryCurrency()).isEqualTo("INR");

    // Today assertions
    assertThat(view.today().activeTasksCount()).isEqualTo(5L);
    assertThat(view.today().overdueTasksCount()).isEqualTo(1L);

    // Performance assertions
    assertThat(view.performance().attendanceRecords()).isEqualTo(30L);
    assertThat(view.performance().attended()).isEqualTo(28L);
    assertThat(view.performance().lateCount()).isEqualTo(2L);
    assertThat(view.performance().attendanceRate()).isEqualTo(93); // round(28 * 100 / 30) = 93
    assertThat(view.performance().onTimeCompletionRate()).isEqualTo(89); // round(8 * 100 / 9) = 89
  }

  @Test
  void get360Throws404WhenEmployeeNotFound() {
    when(employeeService.getEntity(employeeId))
        .thenThrow(ApiException.notFound("EMPLOYEE_NOT_FOUND", "Employee was not found."));

    assertThatThrownBy(() -> service.get360(employeeId))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("code", "EMPLOYEE_NOT_FOUND");
  }

  @Test
  void get360EnforcesReadOnlyTransactionalBoundary() throws NoSuchMethodException {
    var method = Employee360Service.class.getMethod("get360", UUID.class);
    var tx = method.getAnnotation(Transactional.class);
    assertThat(tx).isNotNull();
    assertThat(tx.readOnly()).isTrue();
  }

  @Test
  void get360_reflectsAuthoritativeOutboundCommunications() {
    Employee mockEmp = new Employee();
    mockEmp.id = employeeId;
    mockEmp.firstName = "Amaan";
    mockEmp.displayName = "Amaan Khan";
    mockEmp.roleTitle = "Lead Sound Engineer";
    mockEmp.department = "Audio";
    mockEmp.employmentType = "FULL_TIME";
    mockEmp.joiningDate = LocalDate.of(2024, 1, 1);
    mockEmp.baseSalaryMinor = 4_500_000L;
    mockEmp.salaryCurrency = "INR";
    mockEmp.status = Employee.Status.ACTIVE;

    when(employeeService.getEntity(employeeId)).thenReturn(mockEmp);
    when(financeReads.employee(employeeId)).thenReturn(Map.of());
    when(attendanceRepo.findByEmployeeIdAndDate(eq(employeeId), any())).thenReturn(Optional.empty());

    Instant expectedQueuedAt = Instant.parse("2026-09-27T10:15:30Z");
    when(jdbc.query(
            contains("FROM outbound_messages"),
            any(ResultSetExtractor.class),
            eq(employeeId)))
        .thenAnswer(invocation -> {
          ResultSetExtractor<?> extractor = invocation.getArgument(1);
          var rs = mock(java.sql.ResultSet.class);
          when(rs.next()).thenReturn(true);
          when(rs.getLong("msg_count")).thenReturn(7L);
          when(rs.getTimestamp("last_msg")).thenReturn(java.sql.Timestamp.from(expectedQueuedAt));
          return extractor.extractData(rs);
        });

    Employee360View view = service.get360(employeeId);

    assertThat(view.communication().totalMessages()).isEqualTo(7L);
    assertThat(view.communication().lastContactAt()).isEqualTo(expectedQueuedAt);
    assertThat(view.communication().canMessage()).isTrue();
  }
}
