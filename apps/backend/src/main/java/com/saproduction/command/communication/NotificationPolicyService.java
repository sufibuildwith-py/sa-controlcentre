package com.saproduction.command.communication;

import com.fasterxml.jackson.databind.*;
import com.saproduction.command.employee.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class NotificationPolicyService {
  private final NotificationRuleRepository rules;
  private final OutboundMessageRepository messages;
  private final OutboundMessageEventRepository history;
  private final EmployeeRepository employees;
  private final ObjectMapper json;

  public NotificationPolicyService(
      NotificationRuleRepository rules,
      OutboundMessageRepository messages,
      OutboundMessageEventRepository history,
      EmployeeRepository employees,
      ObjectMapper json) {
    this.rules = rules;
    this.messages = messages;
    this.history = history;
    this.employees = employees;
    this.json = json;
  }

  public int evaluate(OutboxEvent event) {
    NotificationRule rule = rules.findByEventType(event.eventType).orElse(null);
    if (rule == null || !rule.enabled) return 0;
    try {
      JsonNode payload = json.readTree(event.payloadJson);
      List<UUID> recipients = recipients(payload);
      int created = 0;
      for (UUID employeeId : recipients) {
        Employee employee = employees.findById(employeeId).orElse(null);
        if (employee == null || employee.whatsappPhone == null || employee.whatsappPhone.isBlank())
          continue;
        String key = event.id + ":" + employeeId + ":" + rule.channel + ":" + rule.templateKey;
        if (messages.findByIdempotencyKey(key).isPresent()) continue;
        OutboundMessage message = new OutboundMessage();
        message.employeeId = employeeId;
        message.channel = rule.channel;
        message.category = payload.path("category").asText(category(event.eventType));
        message.templateKey = rule.templateKey;
        message.templateVariablesJson =
            payload.path("variables").isMissingNode() ? "{}" : payload.path("variables").toString();
        message.bodyPreview = payload.path("bodyPreview").asText(event.eventType.replace('_', ' '));
        message.relatedType = text(payload, "relatedType");
        message.relatedId = uuid(payload, "relatedId", event.aggregateId);
        message.requiresResponse = payload.path("requiresResponse").asBoolean(false);
        message.idempotencyKey = key;
        String sendAt = payload.path("sendAt").asText();
        message.nextAttemptAt =
            sendAt.isBlank()
                ? Instant.now().plusSeconds(Math.max(0, rule.delayMinutes) * 60L)
                : Instant.parse(sendAt);
        if (message.nextAttemptAt.isBefore(Instant.now())) message.nextAttemptAt = Instant.now();
        try {
          messages.saveAndFlush(message);
          record(message, "QUEUED", "Created by " + event.eventType);
          created++;
        } catch (DataIntegrityViolationException duplicate) {
          if (messages.findByIdempotencyKey(key).isEmpty()) throw duplicate;
        }
      }
      return created;
    } catch (Exception e) {
      if (e instanceof RuntimeException runtime) throw runtime;
      throw new IllegalArgumentException("Notification policy could not read the domain event.", e);
    }
  }

  private void record(OutboundMessage message, String type, String detail) {
    OutboundMessageEvent item = new OutboundMessageEvent();
    item.outboundMessageId = message.id;
    item.eventType = type;
    item.detail = detail;
    history.save(item);
  }

  private List<UUID> recipients(JsonNode payload) {
    LinkedHashSet<UUID> result = new LinkedHashSet<>();
    if (payload.hasNonNull("employeeId"))
      result.add(UUID.fromString(payload.get("employeeId").asText()));
    if (payload.path("employeeIds").isArray())
      for (JsonNode value : payload.path("employeeIds"))
        result.add(UUID.fromString(value.asText()));
    return List.copyOf(result);
  }

  private static String category(String event) {
    if (event.startsWith("PRODUCTION")) return "ASSIGNMENTS";
    if (event.startsWith("MEETING")) return "MEETINGS";
    if (event.startsWith("ATTENDANCE")) return "ATTENDANCE";
    if (event.startsWith("SALARY") || event.startsWith("PAYROLL")) return "PAYROLL";
    if (event.startsWith("TASK")) return "TASKS";
    if (event.startsWith("LEAVE")) return "LEAVE";
    return "MANUAL";
  }

  private static String text(JsonNode node, String field) {
    String value = node.path(field).asText();
    return value.isBlank() ? null : value;
  }

  private static UUID uuid(JsonNode node, String field, UUID fallback) {
    String value = node.path(field).asText();
    return value.isBlank() ? fallback : UUID.fromString(value);
  }
}
