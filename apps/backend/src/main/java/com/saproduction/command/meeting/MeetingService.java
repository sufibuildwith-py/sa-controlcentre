package com.saproduction.command.meeting;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.calendar.*;
import com.saproduction.command.shared.ApiException;
import com.saproduction.command.work.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
  public record Input(@NotBlank @Size(max=180) String title,@Size(max=4000) String description,@Size(max=4000) String agenda,@NotNull Instant startsAt,@NotNull Instant endsAt,@Size(max=250) String location,List<UUID> attendeeIds,boolean overrideConflicts,@Size(max=500) String overrideReason){}
  public record NoteInput(@NotBlank @Size(max=8000) String content){}
  public record ActionInput(@NotBlank @Size(max=180) String title,@Size(max=4000) String description,UUID assignedEmployeeId,Instant dueAt){}
  public enum Response {PENDING,ACCEPTED,DECLINED}
  public record ResponseInput(@NotNull Response response){}
  public record Attendee(UUID employeeId,String employeeName,String response){}
  public record Note(UUID id,String content,Instant createdAt,Instant updatedAt){}
  public record View(UUID id,String title,String description,String agenda,Instant startsAt,Instant endsAt,String location,Meeting.Status status,List<Attendee> attendees,List<Note> notes,Instant createdAt,Instant updatedAt){}
  private final MeetingRepository meetings;private final MeetingNoteRepository notes;private final CalendarService calendar;private final WorkTaskService tasks;private final JdbcTemplate jdbc;private final AuditService audit;
  public MeetingService(MeetingRepository meetings,MeetingNoteRepository notes,CalendarService calendar,WorkTaskService tasks,JdbcTemplate jdbc,AuditService audit){this.meetings=meetings;this.notes=notes;this.calendar=calendar;this.tasks=tasks;this.jdbc=jdbc;this.audit=audit;}
  @Transactional(readOnly=true) public List<View> list(){return meetings.findAllByOrderByStartsAtDesc().stream().map(this::view).toList();}
  @Transactional(readOnly=true) public View get(UUID id){return view(entity(id));}
  @Transactional public View create(Input in){validate(in);Meeting m=new Meeting();apply(m,in);meetings.saveAndFlush(m);CalendarEvent event=calendar.syncMeeting(m.id,m.title,m.description,m.startsAt,m.endsAt,m.location);for(UUID employee:safe(in.attendeeIds())){calendar.addAttendee(event,employee,in.overrideConflicts(),in.overrideReason());upsertAttendee(m.id,employee);}var result=view(m);audit.record("MEETING","MEETING_CREATED",m.id.toString(),null,result);return result;}
  @Transactional public View update(UUID id,Input in){validate(in);Meeting m=entity(id);if(m.status==Meeting.Status.CANCELLED)throw ApiException.conflict("INVALID_MEETING_STATE","Cancelled meetings cannot be edited.");var before=view(m);apply(m,in);meetings.saveAndFlush(m);CalendarEvent event=calendar.syncMeeting(m.id,m.title,m.description,m.startsAt,m.endsAt,m.location);jdbc.update("delete from event_attendees where event_id=?",event.id);jdbc.update("delete from meeting_attendees where meeting_id=?",id);for(UUID employee:safe(in.attendeeIds())){calendar.addAttendee(event,employee,in.overrideConflicts(),in.overrideReason());upsertAttendee(id,employee);}var result=view(m);audit.record("MEETING","MEETING_EDITED",id.toString(),before,result);return result;}
  @Transactional public View cancel(UUID id){Meeting m=entity(id);if(m.status!=Meeting.Status.SCHEDULED)throw ApiException.conflict("INVALID_MEETING_STATE","Only scheduled meetings can be cancelled.");m.status=Meeting.Status.CANCELLED;calendar.eventForMeeting(id).status=CalendarEvent.Status.CANCELLED;meetings.save(m);var result=view(m);audit.record("MEETING","MEETING_CANCELLED",id.toString(),null,result);return result;}
  @Transactional public View addNote(UUID id,NoteInput in){Meeting m=entity(id);MeetingNote note=new MeetingNote();note.meetingId=id;note.content=in.content().trim();notes.save(note);audit.record("MEETING","MEETING_NOTE_ADDED",id.toString(),null,Map.of("noteId",note.id));return view(m);}
  @Transactional public WorkTaskService.View action(UUID id,ActionInput in){Meeting m=entity(id);var task=tasks.createMeetingAction(id,in.title(),in.description(),in.assignedEmployeeId(),in.dueAt());audit.record("MEETING","MEETING_ACTION_CREATED",id.toString(),null,Map.of("taskId",task.id()));return task;}
  @Transactional public View updateAttendeeResponse(UUID id,UUID employeeId,ResponseInput in){Meeting m=entity(id);String before=jdbc.query("select response from meeting_attendees where meeting_id=? and employee_id=?",r->r.next()?r.getString(1):null,id,employeeId);if(before==null)throw ApiException.notFound("MEETING_ATTENDEE_NOT_FOUND","Meeting attendee was not found.");jdbc.update("update meeting_attendees set response=?,updated_at=now() where meeting_id=? and employee_id=?",in.response().name(),id,employeeId);calendar.updateAttendeeResponse(calendar.eventForMeeting(id).id,employeeId,in.response().name());audit.record("MEETING","MEETING_ATTENDEE_RESPONSE_UPDATED",id.toString(),Map.of("employeeId",employeeId,"response",before),Map.of("employeeId",employeeId,"response",in.response()));return view(m);}
  private void upsertAttendee(UUID meeting,UUID employee){jdbc.update("insert into meeting_attendees(meeting_id,employee_id,response) values (?,?,'PENDING') on conflict(meeting_id,employee_id) do update set updated_at=now()",meeting,employee);}
  private View view(Meeting m){var attendees=jdbc.query("select a.employee_id,e.display_name,a.response from meeting_attendees a join employees e on e.id=a.employee_id where a.meeting_id=? order by e.display_name",(r,n)->new Attendee(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),m.id);var meetingNotes=notes.findAllByMeetingIdOrderByCreatedAtDesc(m.id).stream().map(n->new Note(n.id,n.content,n.createdAt,n.updatedAt)).toList();return new View(m.id,m.title,m.description,m.agenda,m.startsAt,m.endsAt,m.location,m.status,attendees,meetingNotes,m.createdAt,m.updatedAt);}
  private void apply(Meeting m,Input in){m.title=in.title().trim();m.description=clean(in.description());m.agenda=clean(in.agenda());m.startsAt=in.startsAt();m.endsAt=in.endsAt();m.location=clean(in.location());if(m.status==null)m.status=Meeting.Status.SCHEDULED;}
  private void validate(Input in){if(!in.endsAt().isAfter(in.startsAt()))throw ApiException.badRequest("INVALID_MEETING_TIME","End time must be after start time.");}
  private Meeting entity(UUID id){return meetings.findById(id).orElseThrow(()->ApiException.notFound("MEETING_NOT_FOUND","Meeting was not found."));}
  private static <T> List<T> safe(List<T> list){return list==null?List.of():list;}
  private static String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}
