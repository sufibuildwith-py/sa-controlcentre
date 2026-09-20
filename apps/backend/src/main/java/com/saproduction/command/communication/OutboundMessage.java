package com.saproduction.command.communication;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="outbound_messages")
public class OutboundMessage {
  public enum Status {QUEUED,SENDING,SENT,DELIVERED,READ,FAILED}
  @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
  UUID employeeId;String channel="WHATSAPP";String category;String templateKey;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") String templateVariablesJson;
  @Column(length=2000) String bodyPreview;String relatedType;UUID relatedId;boolean requiresResponse;String response;
  @Enumerated(EnumType.STRING) Status status=Status.QUEUED;
  String providerMessageId;String idempotencyKey;int attemptCount;Instant nextAttemptAt=Instant.now();@Column(length=1000) String lastError;
  Instant queuedAt;Instant sentAt;Instant deliveredAt;Instant readAt;Instant failedAt;Instant updatedAt;
  @PrePersist void create(){var now=Instant.now();if(queuedAt==null)queuedAt=now;if(nextAttemptAt==null)nextAttemptAt=now;updatedAt=now;}
  @PreUpdate void update(){updatedAt=Instant.now();}
}
