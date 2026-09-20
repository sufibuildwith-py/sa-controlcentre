package com.saproduction.command.communication;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.meeting.MeetingService;
import com.saproduction.command.production.*;
import com.saproduction.command.shared.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundMessagingService {
  private final OutboundMessageRepository messages;
  private final OutboundMessageEventRepository history;
  private final OutboundDeliveryAttemptRepository attempts;
  private final MessageStatusMachine stateMachine;
  private final ProductionService productions;
  private final MeetingService meetings;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public InboundMessagingService(
      OutboundMessageRepository messages,
      OutboundMessageEventRepository history,
      OutboundDeliveryAttemptRepository attempts,
      MessageStatusMachine stateMachine,
      ProductionService productions,
      MeetingService meetings,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.messages = messages;
    this.history = history;
    this.attempts = attempts;
    this.stateMachine = stateMachine;
    this.productions = productions;
    this.meetings = meetings;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional
  public boolean providerStatus(
      String receiptId, String providerMessageId, String status, String error) {
    if (!receipt(receiptId, "STATUS")) return false;
    OutboundMessage message = lockMessage(providerMessageId);
    OutboundDeliveryAttempt attempt =
        attempts
            .findByProviderMessageId(providerMessageId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "MESSAGE_ATTEMPT_NOT_FOUND", "Provider delivery attempt was not found."));
    applyStatus(message, attempt, status, error);
    completeReceipt(receiptId);
    return true;
  }

  @Transactional
  public boolean providerResponse(String receiptId, String providerMessageId, String action) {
    if (!receipt(receiptId, "INTERACTION")) return false;
    OutboundMessage message = lockMessage(providerMessageId);
    OutboundDeliveryAttempt attempt =
        attempts
            .findByProviderMessageId(providerMessageId)
            .orElseThrow(
                () ->
                    ApiException.notFound(
                        "MESSAGE_ATTEMPT_NOT_FOUND", "Provider delivery attempt was not found."));
    applyResponse(message, action);
    completeReceipt(receiptId);
    return true;
  }

  @Transactional
  public void simulateStatus(UUID messageId, String status, String error) {
    OutboundMessage message =
        messages
            .findById(messageId)
            .orElseThrow(
                () -> ApiException.notFound("MESSAGE_NOT_FOUND", "Message was not found."));
    OutboundDeliveryAttempt attempt =
        attempts.findAllByOutboundMessageIdOrderByAttemptNumber(messageId).stream()
            .reduce((a, b) -> b)
            .orElse(null);
    applyStatus(message, attempt, status, error);
  }

  @Transactional
  public void simulateResponse(UUID messageId, String action) {
    OutboundMessage message =
        messages
            .findById(messageId)
            .orElseThrow(
                () -> ApiException.notFound("MESSAGE_NOT_FOUND", "Message was not found."));
    applyResponse(message, action);
  }

  private void applyStatus(
      OutboundMessage m, OutboundDeliveryAttempt attempt, String raw, String error) {
    String status = raw.toUpperCase(Locale.ROOT);
    Instant now = Instant.now();
    OutboundMessage.Status target;
    try {
      target = OutboundMessage.Status.valueOf(status);
    } catch (Exception ex) {
      throw ApiException.badRequest(
          "UNSUPPORTED_MESSAGE_STATUS", "Message status is not supported.");
    }
    if (target == OutboundMessage.Status.QUEUED || target == OutboundMessage.Status.SENDING)
      throw ApiException.badRequest(
          "UNSUPPORTED_MESSAGE_STATUS", "Message status is not supported.");
    String safeError =
        target == OutboundMessage.Status.FAILED
            ? clean(error, "Provider reported a delivery failure.")
            : null;
    if (attempt != null) applyAttempt(attempt, target, safeError, now);
    // Preserve the historical attempt, but only the current attempt drives the logical status.
    if (attempt != null && attempt.attemptNumber != m.attemptCount) return;
    boolean changed = stateMachine.apply(m, target, safeError, now);
    if (changed) {
      messages.save(m);
      record(m, status, error);
    }
  }

  private void applyAttempt(
      OutboundDeliveryAttempt a, OutboundMessage.Status target, String error, Instant now) {
    if (target == OutboundMessage.Status.FAILED) {
      if (a.status == OutboundDeliveryAttempt.Status.DELIVERED
          || a.status == OutboundDeliveryAttempt.Status.READ) return;
      a.status = OutboundDeliveryAttempt.Status.FAILED;
      a.failedAt = now;
      a.lastError = error;
    } else {
      int current = attemptRank(a.status),
          next = attemptRank(OutboundDeliveryAttempt.Status.valueOf(target.name()));
      if (next < current) return;
      a.status = OutboundDeliveryAttempt.Status.valueOf(target.name());
      if (next >= 1 && a.acceptedAt == null) a.acceptedAt = now;
      if (next >= 2 && a.deliveredAt == null) a.deliveredAt = now;
      if (next >= 3 && a.readAt == null) a.readAt = now;
    }
    attempts.save(a);
  }

  private int attemptRank(OutboundDeliveryAttempt.Status s) {
    return switch (s) {
      case SENDING -> 0;
      case SENT -> 1;
      case DELIVERED -> 2;
      case READ -> 3;
      case FAILED -> -1;
    };
  }

  private void applyResponse(OutboundMessage m, String raw) {
    if (!m.requiresResponse)
      throw ApiException.conflict(
          "MESSAGE_RESPONSE_NOT_EXPECTED", "This message does not expect a response.");
    String action = raw.toUpperCase(Locale.ROOT);
    if (m.response != null) {
      if (m.response.equals(action)) return;
      throw ApiException.conflict(
          "MESSAGE_ALREADY_RESPONDED", "This message already has a response.");
    }
    if ("PRODUCTION".equals(m.relatedType)) {
      ProductionMember.Status target =
          switch (action) {
            case "CONFIRM", "CONFIRMED" -> ProductionMember.Status.CONFIRMED;
            case "DECLINE", "DECLINED" -> ProductionMember.Status.DECLINED;
            default ->
                throw ApiException.badRequest(
                    "UNSUPPORTED_RESPONSE", "Production response is not supported.");
          };
      productions.updateMemberStatus(
          m.relatedId, m.employeeId, new ProductionService.MemberStatusInput(target));
      m.response = target.name();
    } else if ("MEETING".equals(m.relatedType)) {
      MeetingService.Response target =
          switch (action) {
            case "ACCEPT", "ACCEPTED", "ATTENDING" -> MeetingService.Response.ACCEPTED;
            case "DECLINE", "DECLINED" -> MeetingService.Response.DECLINED;
            default ->
                throw ApiException.badRequest(
                    "UNSUPPORTED_RESPONSE", "Meeting response is not supported.");
          };
      meetings.updateAttendeeResponse(
          m.relatedId, m.employeeId, new MeetingService.ResponseInput(target));
      m.response = target.name();
    } else
      throw ApiException.badRequest(
          "UNSUPPORTED_RESPONSE_TARGET", "Message response target is not supported.");
    messages.save(m);
    record(m, "RESPONSE", m.response);
    audit.record(
        "COMMUNICATION",
        "EMPLOYEE_RESPONSE_PROCESSED",
        m.id.toString(),
        null,
        Map.of("response", m.response, "relatedType", m.relatedType, "relatedId", m.relatedId));
  }

  private boolean receipt(String id, String type) {
    return jdbc.update(
            "insert into webhook_receipts(provider_event_id,event_type) values (?,?) on conflict(provider_event_id) do nothing",
            id,
            type)
        == 1;
  }

  private OutboundMessage lockMessage(String providerId) {
    var ids =
        jdbc.queryForList(
            "select outbound_message_id from outbound_delivery_attempts where provider_message_id=?",
            UUID.class,
            providerId);
    if (ids.isEmpty())
      throw ApiException.notFound(
          "MESSAGE_ATTEMPT_NOT_FOUND", "Provider delivery attempt was not found.");
    return messages.lockById(ids.getFirst()).orElseThrow();
  }

  private void completeReceipt(String id) {
    jdbc.update("update webhook_receipts set processed_at=now() where provider_event_id=?", id);
  }

  private void record(OutboundMessage message, String type, String detail) {
    OutboundMessageEvent e = new OutboundMessageEvent();
    e.outboundMessageId = message.id;
    e.eventType = type;
    e.detail = detail;
    history.save(e);
  }

  private static String clean(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.substring(0, Math.min(1000, value.length()));
  }
}
