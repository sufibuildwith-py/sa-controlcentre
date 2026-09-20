package com.saproduction.command.communication;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbound_delivery_attempts")
public class OutboundDeliveryAttempt {
  public enum Status {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Column(name = "outbound_message_id", nullable = false)
  UUID outboundMessageId;

  @Column(name = "attempt_number", nullable = false)
  int attemptNumber;

  @Column(name = "provider_message_id", unique = true)
  String providerMessageId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  Status status;

  @Column(name = "started_at", nullable = false)
  Instant startedAt;

  @Column(name = "accepted_at")
  Instant acceptedAt;

  @Column(name = "delivered_at")
  Instant deliveredAt;

  @Column(name = "read_at")
  Instant readAt;

  @Column(name = "failed_at")
  Instant failedAt;

  @Column(name = "last_error", length = 1000)
  String lastError;
}
