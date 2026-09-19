package com.saproduction.command.attendance;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.UserRepository;
import com.saproduction.command.employee.*;
import com.saproduction.command.shared.ApiException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
  public record Input(@NotNull AttendanceRecord.Status status, LocalTime checkInTime,LocalTime checkOutTime,@PositiveOrZero Integer minutesLate,@Size(max=2000) String notes) {}
  public record RecordView(UUID id,UUID employeeId,LocalDate date,AttendanceRecord.Status status,LocalTime checkInTime,LocalTime checkOutTime,int minutesLate,String notes,Instant updatedAt) {}
  public record DayRow(EmployeeDtos.View employee,RecordView record) {}
  public record DayView(LocalDate date,List<DayRow> rows,Map<String,Long> summary) {}
  public record MonthView(UUID employeeId,LocalDate from,LocalDate to,List<RecordView> records,Map<String,Long> summary) {}
  private final AttendanceRepository attendance; private final EmployeeRepository employees; private final EmployeeService employeeService; private final UserRepository users; private final AuditService audit;
  public AttendanceService(AttendanceRepository attendance,EmployeeRepository employees,EmployeeService employeeService,UserRepository users,AuditService audit){this.attendance=attendance;this.employees=employees;this.employeeService=employeeService;this.users=users;this.audit=audit;}
  @Transactional(readOnly=true) public DayView day(LocalDate date){
    Map<UUID,AttendanceRecord> records=attendance.findAllByDate(date).stream().collect(Collectors.toMap(r->r.employee.getId(),Function.identity()));
    List<DayRow> rows=employees.findAllByStatusNotOrderByDisplayName(Employee.Status.INACTIVE).stream().map(e->new DayRow(EmployeeDtos.view(e),view(records.get(e.getId())))).toList();
    return new DayView(date,rows,summary(records.values().stream().map(r->r.status).toList()));
  }
  @Transactional(readOnly=true) public MonthView history(UUID employeeId,LocalDate from,LocalDate to){employeeService.getEntity(employeeId);var records=attendance.findAllByEmployeeIdAndDateBetweenOrderByDateDesc(employeeId,from,to);return new MonthView(employeeId,from,to,records.stream().map(this::view).toList(),summary(records.stream().map(r->r.status).toList()));}
  @Transactional public RecordView put(UUID employeeId,LocalDate date,Input input){
    Employee employee=employeeService.getEntity(employeeId); AttendanceRecord record=attendance.findByEmployeeIdAndDate(employeeId,date).orElse(null); RecordView before=view(record);
    if(record==null){record=new AttendanceRecord();record.employee=employee;record.date=date;}
    record.status=input.status();record.checkInTime=input.checkInTime();record.checkOutTime=input.checkOutTime();record.minutesLate=input.minutesLate()==null?0:input.minutesLate();record.notes=clean(input.notes());record.recordedBy=currentUserId(); attendance.saveAndFlush(record);
    RecordView after=view(record);audit.record("ATTENDANCE",before==null?"ATTENDANCE_CREATED":"ATTENDANCE_CHANGED",record.id.toString(),before,after);return after;
  }
  @Transactional public Map<String,Integer> markRemaining(LocalDate date){int marked=0;for(Employee employee:employees.findAllByStatusNotOrderByDisplayName(Employee.Status.INACTIVE)){if(attendance.findByEmployeeIdAndDate(employee.getId(),date).isEmpty()){put(employee.getId(),date,new Input(AttendanceRecord.Status.PRESENT,null,null,0,"Bulk marked present"));marked++;}}return Map.of("marked",marked);}
  private UUID currentUserId(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null)return null;return users.findByEmailIgnoreCase(auth.getName()).map(u->u.id).orElse(null);}
  public RecordView view(AttendanceRecord r){return r==null?null:new RecordView(r.id,r.employee.getId(),r.date,r.status,r.checkInTime,r.checkOutTime,r.minutesLate,r.notes,r.updatedAt);}
  private Map<String,Long> summary(Collection<AttendanceRecord.Status> statuses){Map<String,Long> result=new LinkedHashMap<>();for(var status:AttendanceRecord.Status.values())result.put(status.name(),statuses.stream().filter(status::equals).count());return result;}
  private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}
