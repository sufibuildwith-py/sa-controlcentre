package com.saproduction.command.employee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeServiceTest {
  @Test void createPersistsMinorUnitsAndWritesAudit(){
    EmployeeRepository repository=mock(EmployeeRepository.class); AuditService audit=mock(AuditService.class);
    when(repository.existsByEmployeeCodeIgnoreCase("SA-100")).thenReturn(false);
    when(repository.saveAndFlush(any(Employee.class))).thenAnswer(invocation->{Employee employee=invocation.getArgument(0);employee.id=UUID.randomUUID();employee.createdAt=Instant.now();employee.updatedAt=employee.createdAt;return employee;});
    EmployeeService service=new EmployeeService(repository,audit);

    var result=service.create(input(425_000L));

    assertEquals(425_000L,result.baseSalaryMinor());
    assertEquals("Aman Khan",result.displayName());
    verify(repository).saveAndFlush(any(Employee.class));
    verify(audit).record(eq("EMPLOYEE"),eq("EMPLOYEE_CREATED"),eq(result.id().toString()),isNull(),eq(result));
  }

  @Test void updateAuditsSalaryChanges(){
    UUID id=UUID.randomUUID();Employee existing=employee(id);existing.baseSalaryMinor=400_000L;
    EmployeeRepository repository=mock(EmployeeRepository.class);AuditService audit=mock(AuditService.class);
    when(repository.findById(id)).thenReturn(java.util.Optional.of(existing));when(repository.saveAndFlush(existing)).thenReturn(existing);
    EmployeeService service=new EmployeeService(repository,audit);

    var result=service.update(id,input(500_000L));

    assertEquals(500_000L,result.baseSalaryMinor());
    verify(audit).record("EMPLOYEE","SALARY_BASIS_CHANGED",id.toString(),400_000L,500_000L);
  }

  private static EmployeeDtos.Input input(long salary){return new EmployeeDtos.Input("SA-100","Aman","Khan","Aman Khan","+91 9876543210",null,"aman@example.com","Senior Video Editor","Production","FULL_TIME",LocalDate.of(2025,1,2),salary,"INR",Employee.Status.ACTIVE,null,"Core crew");}
  private static Employee employee(UUID id){Employee e=new Employee();e.id=id;e.employeeCode="SA-100";e.firstName="Aman";e.lastName="Khan";e.displayName="Aman Khan";e.phone="+91 9876543210";e.email="aman@example.com";e.roleTitle="Editor";e.department="Production";e.employmentType="FULL_TIME";e.joiningDate=LocalDate.of(2025,1,2);e.salaryCurrency="INR";e.status=Employee.Status.ACTIVE;e.createdAt=Instant.now();e.updatedAt=e.createdAt;return e;}
}
