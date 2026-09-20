package com.saproduction.command.production;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.calendar.*;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionService {
  public record Input(
      @NotBlank @Size(max = 180) String title,
      @NotBlank @Size(max = 180) String clientName,
      @Size(max = 4000) String description,
      @NotNull LocalDate eventDate,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime,
      @NotBlank @Size(max = 180) String venueName,
      @Size(max = 500) String venueAddress,
      @NotNull Production.Priority priority,
      @Min(0) @Max(100) int progressPercent) {}

  public record Transition(@NotNull Production.Status status) {}

  public record MemberInput(
      @NotNull UUID employeeId,
      @NotBlank @Size(max = 120) String productionRole,
      boolean attendanceRequired,
      ProductionMember.Status assignmentStatus,
      boolean overrideConflict,
      @Size(max = 500) String overrideReason) {}

  public record MemberStatusInput(@NotNull ProductionMember.Status assignmentStatus) {}

  public record MemberView(
      UUID id,
      UUID employeeId,
      String employeeName,
      String productionRole,
      boolean attendanceRequired,
      ProductionMember.Status assignmentStatus,
      boolean conflictOverridden,
      String overrideReason) {}

  public record View(
      UUID id,
      String title,
      String clientName,
      String description,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      String venueName,
      String venueAddress,
      Production.Status status,
      Production.Priority priority,
      int progressPercent,
      Instant completedAt,
      List<MemberView> members,
      long unfinishedTaskCount,
      Instant createdAt,
      Instant updatedAt) {}

  private static final Map<Production.Status, Set<Production.Status>> NEXT =
      Map.of(
          Production.Status.DRAFT,
          Set.of(Production.Status.PLANNING, Production.Status.CANCELLED),
          Production.Status.PLANNING,
          Set.of(Production.Status.PRE_PRODUCTION, Production.Status.CANCELLED),
          Production.Status.PRE_PRODUCTION,
          Set.of(Production.Status.PRODUCTION, Production.Status.CANCELLED),
          Production.Status.PRODUCTION,
          Set.of(Production.Status.POST_PRODUCTION, Production.Status.CANCELLED),
          Production.Status.POST_PRODUCTION,
          Set.of(Production.Status.REVIEW, Production.Status.CANCELLED),
          Production.Status.REVIEW,
          Set.of(Production.Status.DELIVERED, Production.Status.CANCELLED),
          Production.Status.DELIVERED,
          Set.of(),
          Production.Status.CANCELLED,
          Set.of());
  private final ProductionRepository productions;
  private final ProductionMemberRepository members;
  private final CalendarService calendar;
  private final EmployeeService employees;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final DomainEventService events;
  private final ZoneId zone;

  public ProductionService(
      ProductionRepository productions,
      ProductionMemberRepository members,
      CalendarService calendar,
      EmployeeService employees,
      JdbcTemplate jdbc,
      AuditService audit,
      DomainEventService events,
      @Value("${app.time-zone:Asia/Kolkata}") String zone) {
    this.productions = productions;
    this.members = members;
    this.calendar = calendar;
    this.employees = employees;
    this.jdbc = jdbc;
    this.audit = audit;
    this.events = events;
    this.zone = ZoneId.of(zone);
  }

  @Transactional(readOnly = true)
  public List<View> list(
      Production.Status status,
      Production.Priority priority,
      LocalDate from,
      LocalDate to,
      String search) {
    return productions
        .findAll(
            (root, q, cb) -> {
              List<Predicate> p = new ArrayList<>();
              if (status != null) p.add(cb.equal(root.get("status"), status));
              if (priority != null) p.add(cb.equal(root.get("priority"), priority));
              if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("eventDate"), from));
              if (to != null) p.add(cb.lessThanOrEqualTo(root.get("eventDate"), to));
              if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                p.add(
                    cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("clientName")), like),
                        cb.like(cb.lower(root.get("venueName")), like)));
              }
              return cb.and(p.toArray(Predicate[]::new));
            },
            Sort.by("eventDate").ascending())
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    return view(entity(id));
  }

  @Transactional
  public View create(Input in) {
    validateTime(in.startTime(), in.endTime());
    Production p = new Production();
    apply(p, in);
    p.status = Production.Status.DRAFT;
    productions.saveAndFlush(p);
    sync(p);
    var result = view(p);
    audit.record("PRODUCTION", "PRODUCTION_CREATED", p.id.toString(), null, result);
    events.emit(
        "PRODUCTION_CREATED",
        "PRODUCTION",
        p.id,
        Map.of("title", p.title, "eventDate", p.eventDate.toString()));
    return result;
  }

  @Transactional
  public View update(UUID id, Input in) {
    validateTime(in.startTime(), in.endTime());
    Production p = entity(id);
    if (p.status == Production.Status.DELIVERED || p.status == Production.Status.CANCELLED)
      throw ApiException.conflict(
          "INVALID_PRODUCTION_STATE", "Terminal productions cannot be edited.");
    var before = view(p);
    boolean rescheduled =
        !p.eventDate.equals(in.eventDate())
            || !p.startTime.equals(in.startTime())
            || !p.endTime.equals(in.endTime());
    apply(p, in);
    productions.saveAndFlush(p);
    sync(p);
    if (rescheduled) {
      List<UUID> employeeIds =
          members.findAllByProductionIdOrderByCreatedAt(id).stream()
              .map(m -> m.employeeId)
              .toList();
      jdbc.update(
          "update production_members set assignment_status='PENDING',updated_at=now() where production_id=?",
          id);
      jdbc.update(
          "update event_attendees set response='PENDING',acknowledged_at=null,updated_at=now() where event_id=(select id from calendar_events where production_id=?)",
          id);
      if (!employeeIds.isEmpty())
        events.emit("PRODUCTION_UPDATED", "PRODUCTION", id, productionNotice(p, employeeIds, true));
    }
    var result = view(p);
    audit.record(
        "PRODUCTION",
        rescheduled ? "PRODUCTION_RESCHEDULED" : "PRODUCTION_EDITED",
        id.toString(),
        before,
        result);
    return result;
  }

  @Transactional
  public View transition(UUID id, Production.Status target) {
    Production p = entity(id);
    if (!NEXT.get(p.status).contains(target))
      throw ApiException.conflict(
          "INVALID_PRODUCTION_TRANSITION",
          "Production cannot move from " + p.status + " to " + target + ".");
    var before = p.status;
    p.status = target;
    if (target == Production.Status.DELIVERED) {
      p.progressPercent = 100;
      p.completedAt = Instant.now();
    }
    if (target == Production.Status.CANCELLED) {
      calendar.eventForProduction(id).status = CalendarEvent.Status.CANCELLED;
    }
    productions.save(p);
    var result = view(p);
    audit.record("PRODUCTION", "PRODUCTION_TRANSITIONED", id.toString(), before, result);
    return result;
  }

  @Transactional
  public View addMember(UUID id, MemberInput in) {
    Production p = entity(id);
    if (members.existsByProductionIdAndEmployeeId(id, in.employeeId()))
      throw ApiException.conflict(
          "EMPLOYEE_ALREADY_ASSIGNED", "Employee is already assigned to this production.");
    employees.getEntity(in.employeeId());
    var event = calendar.eventForProduction(id);
    calendar.addAttendee(event, in.employeeId(), in.overrideConflict(), in.overrideReason());
    ProductionMember m = new ProductionMember();
    m.productionId = id;
    m.employeeId = in.employeeId();
    m.productionRole = in.productionRole().trim();
    m.attendanceRequired = in.attendanceRequired();
    m.assignmentStatus =
        in.assignmentStatus() == null ? ProductionMember.Status.PENDING : in.assignmentStatus();
    m.conflictOverridden = in.overrideConflict();
    m.overrideReason = clean(in.overrideReason());
    members.saveAndFlush(m);
    audit.record("PRODUCTION", "PRODUCTION_MEMBER_ASSIGNED", id.toString(), null, memberView(m));
    events.emit(
        "PRODUCTION_ASSIGNED",
        "PRODUCTION",
        id,
        productionNotice(p, List.of(in.employeeId()), true));
    return view(p);
  }

  @Transactional
  public void removeMember(UUID id, UUID employeeId) {
    entity(id);
    ProductionMember member =
        members
            .findByProductionIdAndEmployeeId(id, employeeId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "PRODUCTION_MEMBER_NOT_FOUND", "Crew assignment was not found."));
    members.delete(member);
    calendar.removeAttendee(calendar.eventForProduction(id).id, employeeId);
    audit.record(
        "PRODUCTION", "PRODUCTION_MEMBER_REMOVED", id.toString(), memberView(member), null);
  }

  @Transactional
  public View updateMemberStatus(UUID id, UUID employeeId, MemberStatusInput in) {
    Production p = entity(id);
    ProductionMember member =
        members
            .findByProductionIdAndEmployeeId(id, employeeId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "PRODUCTION_MEMBER_NOT_FOUND", "Crew assignment was not found."));
    var before = memberView(member);
    member.assignmentStatus = in.assignmentStatus();
    members.save(member);
    calendar.updateAttendeeResponse(
        calendar.eventForProduction(id).id, employeeId, response(in.assignmentStatus()));
    audit.record(
        "PRODUCTION",
        "PRODUCTION_MEMBER_STATUS_UPDATED",
        id.toString(),
        before,
        memberView(member));
    return view(p);
  }

  private void sync(Production p) {
    calendar.syncProduction(
        p.id,
        p.title,
        p.description,
        p.eventDate.atTime(p.startTime).atZone(zone).toInstant(),
        p.eventDate.atTime(p.endTime).atZone(zone).toInstant(),
        p.venueName,
        p.venueAddress);
  }

  private Map<String, Object> productionNotice(
      Production p, List<UUID> employeeIds, boolean response) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put(
        "parameters",
        List.of(p.title, p.eventDate.toString(), p.startTime.toString(), p.venueName));
    variables.put("confirmPayload", "production:" + p.id + ":CONFIRM");
    variables.put("declinePayload", "production:" + p.id + ":DECLINE");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employeeIds", employeeIds);
    payload.put("category", "ASSIGNMENTS");
    payload.put("relatedType", "PRODUCTION");
    payload.put("relatedId", p.id);
    payload.put("requiresResponse", response);
    payload.put(
        "bodyPreview", p.title + " · " + p.eventDate + " · " + p.startTime + " · " + p.venueName);
    payload.put("variables", variables);
    return payload;
  }

  private void apply(Production p, Input in) {
    p.title = in.title().trim();
    p.clientName = in.clientName().trim();
    p.description = clean(in.description());
    p.eventDate = in.eventDate();
    p.startTime = in.startTime();
    p.endTime = in.endTime();
    p.venueName = in.venueName().trim();
    p.venueAddress = clean(in.venueAddress());
    p.priority = in.priority();
    p.progressPercent = in.progressPercent();
  }

  private View view(Production p) {
    return new View(
        p.id,
        p.title,
        p.clientName,
        p.description,
        p.eventDate,
        p.startTime,
        p.endTime,
        p.venueName,
        p.venueAddress,
        p.status,
        p.priority,
        p.progressPercent,
        p.completedAt,
        members.findAllByProductionIdOrderByCreatedAt(p.id).stream().map(this::memberView).toList(),
        jdbc.queryForObject(
            "select count(*) from tasks where production_id=? and status not in ('DONE','CANCELLED')",
            Long.class,
            p.id),
        p.createdAt,
        p.updatedAt);
  }

  private MemberView memberView(ProductionMember m) {
    String name =
        jdbc.queryForObject(
            "select display_name from employees where id=?", String.class, m.employeeId);
    return new MemberView(
        m.id,
        m.employeeId,
        name,
        m.productionRole,
        m.attendanceRequired,
        m.assignmentStatus,
        m.conflictOverridden,
        m.overrideReason);
  }

  private Production entity(UUID id) {
    return productions
        .findById(id)
        .orElseThrow(
            () -> ApiException.notFound("PRODUCTION_NOT_FOUND", "Production was not found."));
  }

  private void validateTime(LocalTime start, LocalTime end) {
    if (!end.isAfter(start))
      throw ApiException.badRequest(
          "INVALID_PRODUCTION_TIME", "End time must be after start time.");
  }

  private static String clean(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String response(ProductionMember.Status status) {
    return switch (status) {
      case PENDING -> "PENDING";
      case CONFIRMED -> "ACCEPTED";
      case DECLINED -> "DECLINED";
    };
  }
}
