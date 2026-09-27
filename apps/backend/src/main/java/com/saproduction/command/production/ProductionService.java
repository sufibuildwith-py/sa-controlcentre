package com.saproduction.command.production;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.calendar.*;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.headquarters.HeadquartersController;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.shared.ApiException;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionService {
  public record TaskInput(
      @NotBlank @Size(max = 180) String title,
      @Size(max = 4000) String description,
      UUID assignedEmployeeId,
      WorkTask.Status status,
      WorkTask.Priority priority,
      LocalDate startDate,
      Instant dueAt) {}

  public record EquipmentInput(
      @NotNull UUID equipmentId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
      UUID productionLocationId,
      @Size(max = 500) String notes) {}

  public record EquipmentView(
      UUID id,
      UUID equipmentId,
      String equipmentName,
      String internalCode,
      BigDecimal quantity,
      String unitSymbol,
      String status) {}

  public record Input(
      @NotBlank @Size(max = 180) String title,
      @NotBlank @Size(max = 180) String clientName,
      @Size(max = 4000) String description,
      @NotNull LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      @NotBlank @Size(max = 180) String venueName,
      @Size(max = 500) String venueAddress,
      Production.Priority priority,
      @Min(0) @Max(100) Integer progressPercent) {}

  public record CreateInput(
      @NotBlank @Size(max = 180) String title,
      @NotBlank @Size(max = 180) String clientName,
      @Size(max = 4000) String description,
      @NotNull LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      @NotBlank @Size(max = 180) String venueName,
      @Size(max = 500) String venueAddress,
      Production.Priority priority,
      @Min(0) @Max(100) Integer progressPercent,
      List<MemberInput> crew,
      List<TaskInput> tasks,
      List<EquipmentInput> equipment) {}

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
      List<EquipmentView> equipment,
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
  private final WorkTaskService workTasks;
  private final HeadquartersService headquarters;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final DomainEventService events;
  private final ZoneId zone;

  public ProductionService(
      ProductionRepository productions,
      ProductionMemberRepository members,
      CalendarService calendar,
      EmployeeService employees,
      WorkTaskService workTasks,
      HeadquartersService headquarters,
      JdbcTemplate jdbc,
      AuditService audit,
      DomainEventService events,
      @Value("${app.time-zone:Asia/Kolkata}") String zone) {
    this.productions = productions;
    this.members = members;
    this.calendar = calendar;
    this.employees = employees;
    this.workTasks = workTasks;
    this.headquarters = headquarters;
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
  public View create(CreateInput in) {
    validateTime(in.startTime(), in.endTime());
    Production p = new Production();
    p.title = in.title().trim();
    p.clientName = in.clientName().trim();
    p.description = clean(in.description());
    p.eventDate = in.eventDate();
    p.startTime = in.startTime();
    p.endTime = in.endTime();
    p.venueName = in.venueName().trim();
    p.venueAddress = clean(in.venueAddress());
    p.priority = in.priority() == null ? Production.Priority.NORMAL : in.priority();
    p.progressPercent = in.progressPercent() == null ? 0 : in.progressPercent();
    p.status = Production.Status.DRAFT;
    productions.saveAndFlush(p);
    sync(p);

    if (in.crew() != null) {
      for (MemberInput member : in.crew()) {
        if (member != null && member.employeeId() != null) {
          addMember(p.id, member);
        }
      }
    }

    if (in.tasks() != null) {
      for (TaskInput task : in.tasks()) {
        if (task != null && task.title() != null && !task.title().isBlank()) {
          workTasks.create(
              new WorkTaskService.Input(
                  p.id,
                  null,
                  task.title().trim(),
                  clean(task.description()),
                  task.assignedEmployeeId(),
                  task.status() == null ? WorkTask.Status.TODO : task.status(),
                  task.priority() == null ? WorkTask.Priority.NORMAL : task.priority(),
                  task.startDate(),
                  task.dueAt(),
                  0));
        }
      }
    }

    if (in.equipment() != null && !in.equipment().isEmpty()) {
      List<HeadquartersController.ReservationLine> lines = new ArrayList<>();
      for (EquipmentInput eq : in.equipment()) {
        if (eq != null
            && eq.equipmentId() != null
            && eq.quantity() != null
            && eq.quantity().signum() > 0) {
          lines.add(
              new HeadquartersController.ReservationLine(
                  eq.equipmentId(), eq.productionLocationId(), eq.quantity()));
        }
      }
      if (!lines.isEmpty()) {
        Instant startsAt =
            p.startTime != null
                ? p.eventDate.atTime(p.startTime).atZone(zone).toInstant()
                : p.eventDate.atStartOfDay(zone).toInstant();
        Instant endsAt =
            p.endTime != null
                ? p.eventDate.atTime(p.endTime).atZone(zone).toInstant()
                : p.eventDate.atTime(23, 59, 59).atZone(zone).toInstant();
        headquarters.reserve(
            new HeadquartersController.ReservationInput(p.id, startsAt, endsAt, lines));
      }
    }

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
  public View create(Input in) {
    return create(
        new CreateInput(
            in.title(),
            in.clientName(),
            in.description(),
            in.eventDate(),
            in.startTime(),
            in.endTime(),
            in.venueName(),
            in.venueAddress(),
            in.priority(),
            in.progressPercent(),
            null,
            null,
            null));
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
            || !Objects.equals(p.startTime, in.startTime())
            || !Objects.equals(p.endTime, in.endTime());
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
      calendar
          .findEventForProduction(id)
          .ifPresent(
              event ->
                  jdbc.update(
                      "update event_attendees set response='PENDING',acknowledged_at=null,updated_at=now() where event_id=?",
                      event.id));
      if (!employeeIds.isEmpty())
        events.emit(
            "PRODUCTION_UPDATED", "PRODUCTION", id, productionNotice(p, employeeIds, true));
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
      calendar.findEventForProduction(id).ifPresent(e -> e.status = CalendarEvent.Status.CANCELLED);
      headquarters.cancelReservationForProduction(id);
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
    calendar
        .findEventForProduction(id)
        .ifPresent(
            event ->
                calendar.addAttendee(
                    event, in.employeeId(), in.overrideConflict(), in.overrideReason()));
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
    calendar.findEventForProduction(id).ifPresent(e -> calendar.removeAttendee(e.id, employeeId));
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
    calendar
        .findEventForProduction(id)
        .ifPresent(
            e ->
                calendar.updateAttendeeResponse(
                    e.id, employeeId, response(in.assignmentStatus())));
    audit.record(
        "PRODUCTION",
        "PRODUCTION_MEMBER_STATUS_UPDATED",
        id.toString(),
        before,
        memberView(member));
    return view(p);
  }

  @Transactional
  public View addEquipment(UUID id, EquipmentInput in) {
    Production p = entity(id);
    Instant startsAt =
        p.startTime != null
            ? p.eventDate.atTime(p.startTime).atZone(zone).toInstant()
            : p.eventDate.atStartOfDay(zone).toInstant();
    Instant endsAt =
        p.endTime != null
            ? p.eventDate.atTime(p.endTime).atZone(zone).toInstant()
            : p.eventDate.atTime(23, 59, 59).atZone(zone).toInstant();

    var line =
        new HeadquartersController.ReservationLine(
            in.equipmentId(), in.productionLocationId(), in.quantity());
    headquarters.reserve(
        new HeadquartersController.ReservationInput(id, startsAt, endsAt, List.of(line)));
    return view(p);
  }

  @Transactional
  public View removeEquipment(UUID id, UUID equipmentId) {
    Production p = entity(id);
    List<UUID> lineIds =
        jdbc.queryForList(
            """
            SELECT rl.id
            FROM hq_reservation_lines rl
            JOIN hq_reservations r ON r.id = rl.reservation_id
            WHERE r.production_id = ? AND rl.equipment_id = ? AND r.status <> 'CANCELLED'
            """,
            UUID.class,
            id,
            equipmentId);
    for (UUID lineId : lineIds) {
      headquarters.removeReservationLine(lineId);
    }
    return view(p);
  }

  private void sync(Production p) {
    if (p.startTime != null && p.endTime != null) {
      var event =
          calendar.syncProduction(
              p.id,
              p.title,
              p.description,
              p.eventDate.atTime(p.startTime).atZone(zone).toInstant(),
              p.eventDate.atTime(p.endTime).atZone(zone).toInstant(),
              p.venueName,
              p.venueAddress);
      var attendees = calendar.attendeeEmployeeIds(event.id);
      for (var m : members.findAllByProductionIdOrderByCreatedAt(p.id)) {
        if (!attendees.contains(m.employeeId)) {
          calendar.addAttendee(event, m.employeeId, m.conflictOverridden, m.overrideReason);
        }
      }
    } else {
      calendar.deleteProductionEvent(p.id);
    }
  }

  private Map<String, Object> productionNotice(
      Production p, List<UUID> employeeIds, boolean response) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put(
        "parameters",
        List.of(
            p.title,
            p.eventDate.toString(),
            p.startTime != null ? p.startTime.toString() : "TBD",
            p.venueName));
    variables.put("confirmPayload", "production:" + p.id + ":CONFIRM");
    variables.put("declinePayload", "production:" + p.id + ":DECLINE");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employeeIds", employeeIds);
    payload.put("category", "ASSIGNMENTS");
    payload.put("relatedType", "PRODUCTION");
    payload.put("relatedId", p.id);
    payload.put("requiresResponse", response);
    payload.put(
        "bodyPreview",
        p.title
            + " · "
            + p.eventDate
            + " · "
            + (p.startTime != null ? p.startTime : "TBD")
            + " · "
            + p.venueName);
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
    if (in.priority() != null) p.priority = in.priority();
    if (in.progressPercent() != null) p.progressPercent = in.progressPercent();
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
        loadEquipment(p.id),
        p.createdAt,
        p.updatedAt);
  }

  private List<EquipmentView> loadEquipment(UUID productionId) {
    return headquarters.equipmentForProduction(productionId).stream()
        .map(
            m ->
                new EquipmentView(
                    (UUID) m.get("id"),
                    (UUID) m.get("equipmentId"),
                    (String) m.get("equipmentName"),
                    (String) m.get("internalCode"),
                    (BigDecimal) m.get("quantity"),
                    (String) m.get("unitSymbol"),
                    (String) m.get("status")))
        .toList();
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
    if (start == null && end == null) return;
    if (start == null || end == null)
      throw ApiException.badRequest(
          "INVALID_PRODUCTION_TIME",
          "Both start time and end time must be specified if schedule is set.");
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
