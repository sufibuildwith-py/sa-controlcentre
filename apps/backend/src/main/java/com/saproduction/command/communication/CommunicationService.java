package com.saproduction.command.communication;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import java.time.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationService {
  public record ManualInput(
      @NotEmpty List<UUID> employeeIds, @NotBlank @Size(max = 1600) String message) {}

  public record RuleInput(boolean enabled, @PositiveOrZero int delayMinutes) {}

  public record RuleView(
      UUID id,
      String eventType,
      String channel,
      boolean enabled,
      int delayMinutes,
      String templateKey) {}

  public record EventView(String eventType, String detail, Instant createdAt) {}

  public record AttemptView(
      int attemptNumber,
      String providerMessageId,
      OutboundDeliveryAttempt.Status status,
      Instant startedAt,
      Instant acceptedAt,
      Instant deliveredAt,
      Instant readAt,
      Instant failedAt,
      String lastError) {}

  public record MessageView(
      UUID id,
      UUID employeeId,
      String employeeName,
      String phone,
      String category,
      String templateKey,
      String bodyPreview,
      String relatedType,
      UUID relatedId,
      boolean requiresResponse,
      String response,
      OutboundMessage.Status status,
      String providerMessageId,
      int attemptCount,
      String lastError,
      Instant queuedAt,
      Instant sentAt,
      Instant deliveredAt,
      Instant readAt,
      Instant failedAt,
      List<AttemptView> attempts,
      List<EventView> activity) {}

  public record Summary(
      long delivered, long read, long awaitingResponse, long failed, long queued, long sentToday) {}

  public record Centre(
      Summary summary,
      List<MessageView> messages,
      int page,
      int size,
      long totalElements,
      int totalPages) {}

  private final OutboundMessageRepository messages;
  private final OutboundMessageEventRepository history;
  private final OutboundDeliveryAttemptRepository attempts;
  private final NotificationRuleRepository rules;
  private final EmployeeRepository employees;
  private final DomainEventService events;
  private final AuditService audit;
  private final ZoneId zone;

  public CommunicationService(
      OutboundMessageRepository messages,
      OutboundMessageEventRepository history,
      OutboundDeliveryAttemptRepository attempts,
      NotificationRuleRepository rules,
      EmployeeRepository employees,
      DomainEventService events,
      AuditService audit,
      @Value("${app.time-zone:Asia/Kolkata}") String zone) {
    this.messages = messages;
    this.history = history;
    this.attempts = attempts;
    this.rules = rules;
    this.employees = employees;
    this.events = events;
    this.audit = audit;
    this.zone = ZoneId.of(zone);
  }

  @Transactional(readOnly = true)
  public Centre list(
      String category,
      OutboundMessage.Status status,
      Boolean needsAttention,
      UUID employeeId,
      int page,
      int size) {
    int safePage = Math.max(0, page), safeSize = Math.min(100, Math.max(1, size));
    Page<OutboundMessage> values =
        messages.findAll(
            (root, q, cb) -> {
              List<Predicate> p = new ArrayList<>();
              if (category != null && !category.isBlank())
                p.add(cb.equal(root.get("category"), category));
              if (status != null) p.add(cb.equal(root.get("status"), status));
              if (employeeId != null) p.add(cb.equal(root.get("employeeId"), employeeId));
              if (Boolean.TRUE.equals(needsAttention))
                p.add(
                    cb.or(
                        cb.equal(root.get("status"), OutboundMessage.Status.FAILED),
                        cb.and(
                            cb.isTrue(root.get("requiresResponse")),
                            cb.isNull(root.get("response")))));
              return cb.and(p.toArray(Predicate[]::new));
            },
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "queuedAt")));
    Map<UUID, com.saproduction.command.employee.Employee> people = new HashMap<>();
    employees
        .findAllById(
            values.stream().map(m -> m.employeeId).collect(java.util.stream.Collectors.toSet()))
        .forEach(e -> people.put(e.id, e));
    List<MessageView> views =
        values.stream().map(m -> view(m, people.get(m.employeeId), false)).toList();
    Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
    CommunicationSummaryProjection s = messages.summarize(todayStart);
    Summary summary =
        new Summary(
            s.getDelivered(),
            s.getReadCount(),
            s.getAwaitingResponse(),
            s.getFailed(),
            s.getQueued(),
            s.getSentToday());
    return new Centre(
        summary, views, safePage, safeSize, values.getTotalElements(), values.getTotalPages());
  }

  @Transactional(readOnly = true)
  public MessageView get(UUID id) {
    OutboundMessage m = entity(id);
    return view(m, employees.findById(m.employeeId).orElse(null), true);
  }

  @Transactional
  public Map<String, Object> manual(ManualInput input) {
    LinkedHashSet<UUID> ids = new LinkedHashSet<>(input.employeeIds());
    if (ids.isEmpty())
      throw ApiException.badRequest("MESSAGE_RECIPIENTS_REQUIRED", "Choose at least one employee.");
    for (UUID id : ids)
      employees
          .findById(id)
          .orElseThrow(
              () -> ApiException.notFound("EMPLOYEE_NOT_FOUND", "Employee was not found."));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("employeeIds", ids);
    payload.put("category", "MANUAL");
    payload.put("bodyPreview", input.message().trim());
    payload.put("relatedType", "MANUAL");
    payload.put("variables", Map.of("parameters", List.of(input.message().trim())));
    UUID eventId = events.emit("MANUAL_NOTICE", "MANUAL_MESSAGE", null, payload);
    audit.record(
        "COMMUNICATION",
        "MANUAL_MESSAGE_QUEUED",
        eventId.toString(),
        null,
        Map.of("recipientCount", ids.size()));
    return Map.of("eventId", eventId, "recipientCount", ids.size());
  }

  @Transactional
  public MessageView retry(UUID id) {
    OutboundMessage message = entity(id);
    if (message.status != OutboundMessage.Status.FAILED)
      throw ApiException.conflict("MESSAGE_NOT_FAILED", "Only failed messages can be retried.");
    OutboundMessage.Status before = message.status;
    message.status = OutboundMessage.Status.QUEUED;
    message.nextAttemptAt = Instant.now();
    message.lastError = null;
    message.failedAt = null;
    messages.save(message);
    record(message, "MANUAL_RETRY", "Owner requested retry");
    audit.record(
        "COMMUNICATION",
        "MESSAGE_RETRIED",
        id.toString(),
        Map.of("status", before),
        Map.of("status", message.status, "attemptCount", message.attemptCount));
    return get(id);
  }

  @Transactional(readOnly = true)
  public List<RuleView> rules() {
    return rules.findAllByOrderByEventType().stream().map(this::rule).toList();
  }

  @Transactional
  public RuleView updateRule(UUID id, RuleInput input) {
    NotificationRule value =
        rules
            .findById(id)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "NOTIFICATION_RULE_NOT_FOUND", "Notification rule was not found."));
    RuleView before = rule(value);
    value.enabled = input.enabled();
    value.delayMinutes = input.delayMinutes();
    rules.save(value);
    RuleView after = rule(value);
    audit.record("NOTIFICATION_RULE", "NOTIFICATION_RULE_UPDATED", id.toString(), before, after);
    return after;
  }

  private MessageView view(
      OutboundMessage m,
      com.saproduction.command.employee.Employee employee,
      boolean includeHistory) {
    var activity =
        includeHistory
            ? history.findAllByOutboundMessageIdOrderByCreatedAt(m.id).stream()
                .map(e -> new EventView(e.eventType, e.detail, e.createdAt))
                .toList()
            : List.<EventView>of();
    var deliveryAttempts =
        includeHistory
            ? attempts.findAllByOutboundMessageIdOrderByAttemptNumber(m.id).stream()
                .map(
                    a ->
                        new AttemptView(
                            a.attemptNumber,
                            a.providerMessageId,
                            a.status,
                            a.startedAt,
                            a.acceptedAt,
                            a.deliveredAt,
                            a.readAt,
                            a.failedAt,
                            a.lastError))
                .toList()
            : List.<AttemptView>of();
    return new MessageView(
        m.id,
        m.employeeId,
        employee == null ? "Former employee" : employee.displayName,
        employee == null ? "" : employee.whatsappPhone,
        m.category,
        m.templateKey,
        m.bodyPreview,
        m.relatedType,
        m.relatedId,
        m.requiresResponse,
        m.response,
        m.status,
        m.providerMessageId,
        m.attemptCount,
        m.lastError,
        m.queuedAt,
        m.sentAt,
        m.deliveredAt,
        m.readAt,
        m.failedAt,
        deliveryAttempts,
        activity);
  }

  private RuleView rule(NotificationRule r) {
    return new RuleView(r.id, r.eventType, r.channel, r.enabled, r.delayMinutes, r.templateKey);
  }

  private OutboundMessage entity(UUID id) {
    return messages
        .findById(id)
        .orElseThrow(() -> ApiException.notFound("MESSAGE_NOT_FOUND", "Message was not found."));
  }

  private void record(OutboundMessage message, String type, String detail) {
    OutboundMessageEvent e = new OutboundMessageEvent();
    e.outboundMessageId = message.id;
    e.eventType = type;
    e.detail = detail;
    history.save(e);
  }
}
