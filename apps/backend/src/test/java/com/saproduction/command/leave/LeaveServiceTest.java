package com.saproduction.command.leave;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.attendance.*;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LeaveServiceTest {
  @Test void approvalTransitionsRequestAndFillsAttendanceRange(){
    Employee employee=employee();LeaveRequest request=new LeaveRequest();request.id=UUID.randomUUID();request.employee=employee;request.startDate=LocalDate.now().plusDays(10);request.endDate=request.startDate.plusDays(1);request.leaveType="Annual";request.reason="Family event";request.status=LeaveRequest.Status.PENDING;request.createdAt=Instant.now();
    LeaveRepository requests=mock(LeaveRepository.class);EmployeeService employees=mock(EmployeeService.class);EmployeeRepository employeeRepository=mock(EmployeeRepository.class);AttendanceRepository attendance=mock(AttendanceRepository.class);AuditService audit=mock(AuditService.class);
    when(requests.findById(request.id)).thenReturn(Optional.of(request));when(attendance.findByEmployeeIdAndDate(eq(employee.id),any(LocalDate.class))).thenReturn(Optional.empty());
    LeaveService service=new LeaveService(requests,employees,employeeRepository,attendance,audit,mock(DomainEventService.class));

    var result=service.approve(request.id,new LeaveService.ResolveInput("Approved for the event"));

    assertEquals(LeaveRequest.Status.APPROVED,result.status());assertNotNull(result.resolvedAt());assertEquals("Approved for the event",result.ownerNote());
    verify(attendance,times(2)).save(argThat(record->record.status==AttendanceRecord.Status.LEAVE&&record.employee==employee));
    verify(audit).record(eq("LEAVE_REQUEST"),eq("LEAVE_APPROVED"),eq(request.id.toString()),any(),eq(result));
  }

  private static Employee employee(){Employee e=new Employee();e.id=UUID.randomUUID();e.employeeCode="SA-100";e.firstName="Aman";e.displayName="Aman Khan";e.phone="+91 9876543210";e.roleTitle="Editor";e.department="Production";e.employmentType="FULL_TIME";e.joiningDate=LocalDate.of(2025,1,2);e.baseSalaryMinor=400_000;e.salaryCurrency="INR";e.status=Employee.Status.ACTIVE;e.createdAt=Instant.now();e.updatedAt=e.createdAt;return e;}
}
