package com.saproduction.command.calendar;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.shared.ApiException;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
  public record Input(
      @NotNull CalendarEvent.Type type,
      @NotBlank @Size(max = 180) String title,
      @Size(max = 4000) String description,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @Size(max = 180) String locationName,
      @Size(max = 500) String locationAddress,
      List<UUID> attendeeIds,
      boolean overrideConflicts,
      @Size(max = 500) String overrideReason) {}

  public record Attendee(UUID employeeId, String employeeName, String response) {}

  public record View(
      UUID id,
      CalendarEvent.Type type,
      String title,
      String description,
      Instant startsAt,
      Instant endsAt,
      String locationName,
      String locationAddress,
      UUID productionId,
      UUID meetingId,
      UUID taskId,
      CalendarEvent.Status status,
      List<Attendee> attendees) {}

  private final CalendarEventRepository events;
  private final SchedulingConflictService conflicts;
  private final EmployeeService employees;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public CalendarService(
      CalendarEventRepository events,
      SchedulingConflictService conflicts,
      EmployeeService employees,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.events = events;
    this.conflicts = conflicts;
    this.employees = employees;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<View> list(Instant from, Instant to) {
    if (from == null) from = Instant.now().minusSeconds(86400L * 31);
    if (to == null) to = Instant.now().plusSeconds(86400L * 62);
    return events.findAllByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(to, from).stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    return view(entity(id));
  }

  @Transactional
  public View create(Input in) {
    validateTime(in.startsAt(), in.endsAt());
    CalendarEvent e = new CalendarEvent();
    apply(e, in);
    events.saveAndFlush(e);
    for (UUID employeeId : safe(in.attendeeIds()))
      addAttendee(e, employeeId, in.overrideConflicts(), in.overrideReason());
    var result = view(e);
    audit.record("CALENDAR_EVENT", "CALENDAR_EVENT_CREATED", e.id.toString(), null, result);
    return result;
  }

  @Transactional
  public View update(UUID id, Input in) {
    validateTime(in.startsAt(), in.endsAt());
    CalendarEvent e = entity(id);
    if (e.productionId != null || e.meetingId != null)
      throw ApiException.conflict(
          "LINKED_EVENT_MANAGED_BY_OWNER", "Update this schedule from its production or meeting.");
    var before = view(e);
    apply(e, in);
    events.saveAndFlush(e);
    jdbc.update("delete from event_attendees where event_id=?", e.id);
    for (UUID employeeId : safe(in.attendeeIds()))
      addAttendee(e, employeeId, in.overrideConflicts(), in.overrideReason());
    var result = view(e);
    audit.record("CALENDAR_EVENT", "CALENDAR_EVENT_UPDATED", e.id.toString(), before, result);
    return result;
  }

  @Transactional
  public CalendarEvent syncProduction(
      UUID productionId,
      String title,
      String description,
      Instant start,
      Instant end,
      String locationName,
      String locationAddress) {
    validateTime(start, end);
    CalendarEvent e = events.findByProductionId(productionId).orElseGet(CalendarEvent::new);
    e.type = CalendarEvent.Type.PRODUCTION;
    e.title = title;
    e.description = description;
    e.startsAt = start;
    e.endsAt = end;
    e.locationName = locationName;
    e.locationAddress = locationAddress;
    e.productionId = productionId;
    e.status = CalendarEvent.Status.SCHEDULED;
    return events.saveAndFlush(e);
  }

  @Transactional
  public CalendarEvent syncMeeting(
      UUID meetingId,
      String title,
      String description,
      Instant start,
      Instant end,
      String location) {
    validateTime(start, end);
    CalendarEvent e = events.findByMeetingId(meetingId).orElseGet(CalendarEvent::new);
    e.type = CalendarEvent.Type.MEETING;
    e.title = title;
    e.description = description;
    e.startsAt = start;
    e.endsAt = end;
    e.locationName = location;
    e.meetingId = meetingId;
    e.status = CalendarEvent.Status.SCHEDULED;
    return events.saveAndFlush(e);
  }

  @Transactional
  public void syncTaskDeadline(
      UUID taskId,
      String title,
      String description,
      Instant dueAt,
      UUID employeeId,
      boolean active) {
    var existing = events.findByTaskId(taskId);
    if (dueAt == null) {
      existing.ifPresent(events::delete);
      return;
    }
    CalendarEvent e = existing.orElseGet(CalendarEvent::new);
    e.type = CalendarEvent.Type.DEADLINE;
    e.title = title;
    e.description = description;
    e.startsAt = dueAt.minusSeconds(1800);
    e.endsAt = dueAt;
    e.taskId = taskId;
    e.status = active ? CalendarEvent.Status.SCHEDULED : CalendarEvent.Status.COMPLETED;
    events.saveAndFlush(e);
    jdbc.update("delete from event_attendees where event_id=?", e.id);
    if (employeeId != null)
      jdbc.update(
          "insert into event_attendees(event_id,employee_id,response) values (?,?,'PENDING')",
          e.id,
          employeeId);
  }

  @Transactional
  public void addAttendee(CalendarEvent event, UUID employeeId, boolean override, String reason) {
    employees.getEntity(employeeId);
    var found = conflicts.find(employeeId, event.startsAt, event.endsAt, event.id);
    if (!found.isEmpty() && !override) {
      var c = found.getFirst();
      throw ApiException.conflict(
          "SCHEDULING_CONFLICT",
          "Employee already has an overlapping commitment.",
          Map.of(
              "eventId",
              c.eventId().toString(),
              "title",
              c.title(),
              "startsAt",
              c.startsAt().toString(),
              "endsAt",
              c.endsAt().toString()));
    }
    jdbc.update(
        "insert into event_attendees(event_id,employee_id,response) values (?,?,'PENDING') on conflict(event_id,employee_id) do update set updated_at=now()",
        event.id,
        employeeId);
    if (!found.isEmpty())
      audit.record(
          "CALENDAR_EVENT",
          "SCHEDULING_CONFLICT_OVERRIDDEN",
          event.id.toString(),
          found,
          Map.of("employeeId", employeeId, "reason", clean(reason)));
  }

  @Transactional
  public void removeAttendee(UUID eventId, UUID employeeId) {
    jdbc.update(
        "delete from event_attendees where event_id=? and employee_id=?", eventId, employeeId);
  }

  @Transactional
  public void updateAttendeeResponse(UUID eventId, UUID employeeId, String response) {
    int changed =
        jdbc.update(
            "update event_attendees set response=?,acknowledged_at=now(),updated_at=now() where event_id=? and employee_id=?",
            response,
            eventId,
            employeeId);
    if (changed == 0)
      throw ApiException.notFound("EVENT_ATTENDEE_NOT_FOUND", "Calendar attendee was not found.");
  }

  public CalendarEvent eventForProduction(UUID id) {
    return events
        .findByProductionId(id)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "CALENDAR_EVENT_NOT_FOUND", "Linked production event was not found."));
  }

  public CalendarEvent eventForMeeting(UUID id) {
    return events
        .findByMeetingId(id)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "CALENDAR_EVENT_NOT_FOUND", "Linked meeting event was not found."));
  }

  private CalendarEvent entity(UUID id) {
    return events
        .findById(id)
        .orElseThrow(
            () ->
                ApiException.notFound("CALENDAR_EVENT_NOT_FOUND", "Calendar event was not found."));
  }

  private void apply(CalendarEvent e, Input in) {
    e.type = in.type();
    e.title = in.title().trim();
    e.description = clean(in.description());
    e.startsAt = in.startsAt();
    e.endsAt = in.endsAt();
    e.locationName = clean(in.locationName());
    e.locationAddress = clean(in.locationAddress());
    e.status = CalendarEvent.Status.SCHEDULED;
  }

  private View view(CalendarEvent e) {
    var attendees =
        jdbc.query(
            "select a.employee_id,x.display_name,a.response from event_attendees a join employees x on x.id=a.employee_id where a.event_id=? order by x.display_name",
            (r, n) -> new Attendee(r.getObject(1, UUID.class), r.getString(2), r.getString(3)),
            e.id);
    return new View(
        e.id,
        e.type,
        e.title,
        e.description,
        e.startsAt,
        e.endsAt,
        e.locationName,
        e.locationAddress,
        e.productionId,
        e.meetingId,
        e.taskId,
        e.status,
        attendees);
  }

  private void validateTime(Instant start, Instant end) {
    if (!end.isAfter(start))
      throw ApiException.badRequest("INVALID_SCHEDULE", "End time must be after start time.");
  }

  private static <T> List<T> safe(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
