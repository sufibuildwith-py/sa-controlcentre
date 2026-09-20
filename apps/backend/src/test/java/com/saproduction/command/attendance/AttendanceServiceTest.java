package com.saproduction.command.attendance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.UserRepository;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AttendanceServiceTest {
  @Test void putCreatesOneDailyRecordAndAuditEntry(){
    UUID employeeId=UUID.randomUUID();Employee employee=employee(employeeId);LocalDate date=LocalDate.of(2026,9,18);
    AttendanceRepository attendance=mock(AttendanceRepository.class);EmployeeRepository employees=mock(EmployeeRepository.class);EmployeeService employeeService=mock(EmployeeService.class);UserRepository users=mock(UserRepository.class);AuditService audit=mock(AuditService.class);
    when(employeeService.getEntity(employeeId)).thenReturn(employee);when(attendance.findByEmployeeIdAndDate(employeeId,date)).thenReturn(Optional.empty());
    when(attendance.saveAndFlush(any(AttendanceRecord.class))).thenAnswer(invocation->{AttendanceRecord record=invocation.getArgument(0);record.id=UUID.randomUUID();record.createdAt=Instant.now();record.updatedAt=record.createdAt;return record;});
    AttendanceService service=new AttendanceService(attendance,employees,employeeService,users,audit,mock(DomainEventService.class));

    var result=service.put(employeeId,date,new AttendanceService.Input(AttendanceRecord.Status.LATE,LocalTime.of(9,24),null,24,"Traffic"));

    assertEquals(AttendanceRecord.Status.LATE,result.status());assertEquals(24,result.minutesLate());assertEquals(date,result.date());
    verify(audit).record(eq("ATTENDANCE"),eq("ATTENDANCE_CREATED"),eq(result.id().toString()),isNull(),eq(result));
  }

  private static Employee employee(UUID id){Employee e=new Employee();e.id=id;e.employeeCode="SA-100";e.firstName="Aman";e.displayName="Aman Khan";e.phone="+91 9876543210";e.roleTitle="Editor";e.department="Production";e.employmentType="FULL_TIME";e.joiningDate=LocalDate.of(2025,1,2);e.baseSalaryMinor=400_000;e.salaryCurrency="INR";e.status=Employee.Status.ACTIVE;e.createdAt=Instant.now();e.updatedAt=e.createdAt;return e;}
}
