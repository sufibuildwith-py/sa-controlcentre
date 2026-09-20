package com.saproduction.command.leave;

import com.saproduction.command.attendance.*;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.*;
import com.saproduction.command.shared.ApiException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveService {
  public record CreateInput(@NotNull UUID employeeId,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotBlank @Size(max=64) String leaveType,@NotBlank @Size(max=2000) String reason) {}
  public record ResolveInput(@Size(max=2000) String ownerNote) {}
  public record View(UUID id,UUID employeeId,String employeeName,LocalDate startDate,LocalDate endDate,String leaveType,String reason,LeaveRequest.Status status,String ownerNote,Instant createdAt,Instant resolvedAt) {}
  private final LeaveRepository requests; private final EmployeeService employees; private final EmployeeRepository employeeRepository; private final AttendanceRepository attendance; private final AuditService audit;private final DomainEventService events;
  public LeaveService(LeaveRepository requests,EmployeeService employees,EmployeeRepository employeeRepository,AttendanceRepository attendance,AuditService audit,DomainEventService events){this.requests=requests;this.employees=employees;this.employeeRepository=employeeRepository;this.attendance=attendance;this.audit=audit;this.events=events;}
  @Transactional(readOnly=true) public List<View> list(){return requests.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();}
  @Transactional public View create(CreateInput input){if(input.endDate().isBefore(input.startDate()))throw ApiException.badRequest("INVALID_LEAVE_DATES","End date must be on or after start date.");Employee employee=employees.getEntity(input.employeeId());LeaveRequest request=new LeaveRequest();request.employee=employee;request.startDate=input.startDate();request.endDate=input.endDate();request.leaveType=input.leaveType().trim();request.reason=input.reason().trim();requests.save(request);View result=view(request);audit.record("LEAVE_REQUEST","LEAVE_REQUEST_CREATED",request.id.toString(),null,result);return result;}
  @Transactional public View approve(UUID id,ResolveInput input){LeaveRequest request=pending(id);View before=view(request);request.status=LeaveRequest.Status.APPROVED;request.ownerNote=clean(input.ownerNote());request.resolvedAt=Instant.now();if(!LocalDate.now().isBefore(request.startDate)&&!LocalDate.now().isAfter(request.endDate)){request.employee.setStatus(Employee.Status.ON_LEAVE);employeeRepository.save(request.employee);}for(LocalDate date=request.startDate;!date.isAfter(request.endDate);date=date.plusDays(1)){AttendanceRecord record=attendance.findByEmployeeIdAndDate(request.employee.getId(),date).orElse(null);if(record==null){record=new AttendanceRecord();record.employee=request.employee;record.date=date;}record.status=AttendanceRecord.Status.LEAVE;record.minutesLate=0;record.notes="Approved "+request.leaveType;attendance.save(record);}requests.save(request);View after=view(request);audit.record("LEAVE_REQUEST","LEAVE_APPROVED",id.toString(),before,after);events.emit("LEAVE_APPROVED","LEAVE_REQUEST",id,notice(request,"approved"));return after;}
  @Transactional public View reject(UUID id,ResolveInput input){LeaveRequest request=pending(id);View before=view(request);request.status=LeaveRequest.Status.REJECTED;request.ownerNote=clean(input.ownerNote());request.resolvedAt=Instant.now();requests.save(request);View after=view(request);audit.record("LEAVE_REQUEST","LEAVE_REJECTED",id.toString(),before,after);events.emit("LEAVE_REJECTED","LEAVE_REQUEST",id,notice(request,"not approved"));return after;}
  private Map<String,Object> notice(LeaveRequest request,String result){return Map.of("employeeId",request.employee.getId(),"category","LEAVE","relatedType","LEAVE_REQUEST","relatedId",request.id,"bodyPreview","Your "+request.leaveType+" leave request was "+result+".","variables",Map.of("parameters",List.of(request.leaveType,result)));}
  private LeaveRequest pending(UUID id){LeaveRequest r=requests.findById(id).orElseThrow(()->ApiException.notFound("LEAVE_REQUEST_NOT_FOUND","Leave request was not found."));if(r.status!=LeaveRequest.Status.PENDING)throw ApiException.conflict("LEAVE_ALREADY_RESOLVED","This leave request has already been resolved.");return r;}
  private View view(LeaveRequest r){return new View(r.id,r.employee.getId(),r.employee.getDisplayName(),r.startDate,r.endDate,r.leaveType,r.reason,r.status,r.ownerNote,r.createdAt,r.resolvedAt);}
  private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}
