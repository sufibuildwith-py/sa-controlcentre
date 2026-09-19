package com.saproduction.command.work;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.calendar.CalendarService;
import com.saproduction.command.auth.UserRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkTaskService {
  public record Input(UUID productionId,UUID meetingOriginId,@NotBlank @Size(max=180) String title,@Size(max=4000) String description,UUID assignedEmployeeId,@NotNull WorkTask.Status status,@NotNull WorkTask.Priority priority,LocalDate startDate,Instant dueAt,@Min(0) @Max(100) int progressPercent){}
  public record UpdateInput(@Min(0) @Max(100) int progressPercent,@Size(max=2000) String note,WorkTask.Status status){}
  public record UpdateView(UUID id,int progressPercent,String note,Instant createdAt){}
  public record View(UUID id,UUID productionId,String productionTitle,UUID meetingOriginId,String title,String description,UUID assignedEmployeeId,String assigneeName,WorkTask.Status status,WorkTask.Priority priority,LocalDate startDate,Instant dueAt,Instant completedAt,int progressPercent,boolean overdue,List<UpdateView> updates,Instant createdAt,Instant updatedAt){}
  private final WorkTaskRepository tasks;private final TaskUpdateRepository updates;private final EmployeeService employees;private final ProductionRepository productions;private final UserRepository users;private final JdbcTemplate jdbc;private final AuditService audit;private final CalendarService calendar;
  public WorkTaskService(WorkTaskRepository tasks,TaskUpdateRepository updates,EmployeeService employees,ProductionRepository productions,UserRepository users,JdbcTemplate jdbc,AuditService audit,CalendarService calendar){this.tasks=tasks;this.updates=updates;this.employees=employees;this.productions=productions;this.users=users;this.jdbc=jdbc;this.audit=audit;this.calendar=calendar;}
  @Transactional(readOnly=true) public List<View> list(UUID assignee,UUID production,WorkTask.Status status,WorkTask.Priority priority,Instant dueFrom,Instant dueTo,Boolean overdue){Instant now=Instant.now();return tasks.findAll((root,q,cb)->{List<Predicate> p=new ArrayList<>();if(assignee!=null)p.add(cb.equal(root.get("assignedEmployeeId"),assignee));if(production!=null)p.add(cb.equal(root.get("productionId"),production));if(status!=null)p.add(cb.equal(root.get("status"),status));if(priority!=null)p.add(cb.equal(root.get("priority"),priority));if(dueFrom!=null)p.add(cb.greaterThanOrEqualTo(root.get("dueAt"),dueFrom));if(dueTo!=null)p.add(cb.lessThanOrEqualTo(root.get("dueAt"),dueTo));if(Boolean.TRUE.equals(overdue)){p.add(cb.lessThan(root.get("dueAt"),now));p.add(root.get("status").in(WorkTask.Status.DONE,WorkTask.Status.CANCELLED).not());}return cb.and(p.toArray(Predicate[]::new));},Sort.by(Sort.Order.asc("dueAt"),Sort.Order.desc("createdAt"))).stream().map(this::view).toList();}
  @Transactional(readOnly=true) public View get(UUID id){return view(entity(id));}
  @Transactional public View create(Input in){validateRefs(in);WorkTask task=new WorkTask();apply(task,in);task.createdBy=currentUser();normalize(task);tasks.saveAndFlush(task);syncDeadline(task);addHistory(task,"Task created");var result=view(task);audit.record("TASK","TASK_CREATED",task.id.toString(),null,result);return result;}
  @Transactional public View update(UUID id,Input in){validateRefs(in);WorkTask task=entity(id);if(task.status==WorkTask.Status.CANCELLED)throw ApiException.conflict("INVALID_TASK_STATE","Cancelled tasks cannot be edited.");var before=view(task);int oldProgress=task.progressPercent;WorkTask.Status oldStatus=task.status;apply(task,in);normalize(task);tasks.saveAndFlush(task);syncDeadline(task);if(oldProgress!=task.progressPercent||oldStatus!=task.status)addHistory(task,"Task updated");var result=view(task);audit.record("TASK",task.status==WorkTask.Status.DONE&&oldStatus!=WorkTask.Status.DONE?"TASK_COMPLETED":"TASK_EDITED",id.toString(),before,result);return result;}
  @Transactional public View progress(UUID id,UpdateInput in){WorkTask task=entity(id);if(task.status==WorkTask.Status.CANCELLED)throw ApiException.conflict("INVALID_TASK_STATE","Cancelled tasks cannot receive updates.");var before=view(task);WorkTask.Status oldStatus=task.status;task.progressPercent=in.progressPercent();if(in.status()!=null)task.status=in.status();else if(task.progressPercent>0&&task.status==WorkTask.Status.TODO)task.status=WorkTask.Status.IN_PROGRESS;normalize(task);tasks.saveAndFlush(task);syncDeadline(task);addHistory(task,in.note());var result=view(task);audit.record("TASK",task.status==WorkTask.Status.DONE&&oldStatus!=WorkTask.Status.DONE?"TASK_COMPLETED":"TASK_PROGRESS_UPDATED",id.toString(),before,result);return result;}
  @Transactional public View createMeetingAction(UUID meetingId,String title,String description,UUID employeeId,Instant dueAt){return create(new Input(null,meetingId,title,description,employeeId,WorkTask.Status.TODO,WorkTask.Priority.NORMAL,LocalDate.now(),dueAt,0));}
  private void addHistory(WorkTask task,String note){TaskUpdate u=new TaskUpdate();u.taskId=task.id;u.authorId=currentUser();u.progressPercent=task.progressPercent;u.note=clean(note);updates.save(u);}
  private void syncDeadline(WorkTask task){calendar.syncTaskDeadline(task.id,task.title,task.description,task.dueAt,task.assignedEmployeeId,task.status!=WorkTask.Status.DONE&&task.status!=WorkTask.Status.CANCELLED);}
  private void normalize(WorkTask task){if(task.progressPercent<0||task.progressPercent>100)throw ApiException.badRequest("INVALID_TASK_PROGRESS","Progress must be between 0 and 100.");if(task.status==WorkTask.Status.DONE){task.progressPercent=100;if(task.completedAt==null)task.completedAt=Instant.now();}else task.completedAt=null;}
  private void validateRefs(Input in){if(in.assignedEmployeeId()!=null)employees.getEntity(in.assignedEmployeeId());if(in.productionId()!=null&&!productions.existsById(in.productionId()))throw ApiException.notFound("PRODUCTION_NOT_FOUND","Production was not found.");}
  private void apply(WorkTask t,Input in){t.productionId=in.productionId();t.meetingOriginId=in.meetingOriginId();t.title=in.title().trim();t.description=clean(in.description());t.assignedEmployeeId=in.assignedEmployeeId();t.status=in.status();t.priority=in.priority();t.startDate=in.startDate();t.dueAt=in.dueAt();t.progressPercent=in.progressPercent();}
  private View view(WorkTask t){String production=t.productionId==null?null:jdbc.queryForObject("select title from productions where id=?",String.class,t.productionId);String assignee=t.assignedEmployeeId==null?null:jdbc.queryForObject("select display_name from employees where id=?",String.class,t.assignedEmployeeId);var history=updates.findAllByTaskIdOrderByCreatedAtDesc(t.id).stream().map(u->new UpdateView(u.id,u.progressPercent,u.note,u.createdAt)).toList();boolean overdue=t.dueAt!=null&&t.dueAt.isBefore(Instant.now())&&t.status!=WorkTask.Status.DONE&&t.status!=WorkTask.Status.CANCELLED;return new View(t.id,t.productionId,production,t.meetingOriginId,t.title,t.description,t.assignedEmployeeId,assignee,t.status,t.priority,t.startDate,t.dueAt,t.completedAt,t.progressPercent,overdue,history,t.createdAt,t.updatedAt);}
  private UUID currentUser(){var auth=SecurityContextHolder.getContext().getAuthentication();return auth==null?null:users.findByEmailIgnoreCase(auth.getName()).map(u->u.id).orElse(null);}
  private WorkTask entity(UUID id){return tasks.findById(id).orElseThrow(()->ApiException.notFound("TASK_NOT_FOUND","Task was not found."));}
  private static String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}
