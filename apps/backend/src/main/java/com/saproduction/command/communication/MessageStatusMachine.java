package com.saproduction.command.communication;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MessageStatusMachine {
  public boolean apply(
      OutboundMessage message, OutboundMessage.Status target, String error, Instant now) {
    if (target == OutboundMessage.Status.FAILED) {
      if (message.status == OutboundMessage.Status.DELIVERED
          || message.status == OutboundMessage.Status.READ) return false;
      message.status = target;
      message.failedAt = now;
      message.lastError = error;
      return true;
    }
    if (rank(target) < rank(message.status)) return false;
    if (target == OutboundMessage.Status.SENT && message.sentAt == null) message.sentAt = now;
    if (target == OutboundMessage.Status.DELIVERED) {
      if (message.sentAt == null) message.sentAt = now;
      if (message.deliveredAt == null) message.deliveredAt = now;
    }
    if (target == OutboundMessage.Status.READ) {
      if (message.sentAt == null) message.sentAt = now;
      if (message.deliveredAt == null) message.deliveredAt = now;
      if (message.readAt == null) message.readAt = now;
    }
    message.status = target;
    message.lastError = null;
    return true;
  }

  private int rank(OutboundMessage.Status status) {
    return switch (status) {
      case QUEUED -> 0;
      case SENDING -> 1;
      case SENT -> 2;
      case DELIVERED -> 3;
      case READ -> 4;
      case FAILED -> -1;
    };
  }
}
