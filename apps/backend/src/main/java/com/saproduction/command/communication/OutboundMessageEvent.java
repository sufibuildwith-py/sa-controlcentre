package com.saproduction.command.communication;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="outbound_message_events")
public class OutboundMessageEvent {
  @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
  UUID outboundMessageId;String eventType;@Column(length=1000) String detail;Instant createdAt;
  @PrePersist void create(){createdAt=Instant.now();}
}
