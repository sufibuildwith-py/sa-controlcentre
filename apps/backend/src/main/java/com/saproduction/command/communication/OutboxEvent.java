package com.saproduction.command.communication;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  public enum Status {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  String eventType;
  String aggregateType;
  UUID aggregateId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  String payloadJson;

  @Enumerated(EnumType.STRING)
  Status status = Status.PENDING;

  int attemptCount;
  Instant availableAt = Instant.now();
  Instant processingStartedAt;
  Instant createdAt;
  Instant processedAt;

  @Column(length = 1000)
  String lastError;

  @PrePersist
  void create() {
    if (createdAt == null) createdAt = Instant.now();
    if (availableAt == null) availableAt = createdAt;
  }
}
