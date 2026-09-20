package com.saproduction.command.communication;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_rules")
public class NotificationRule {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  String eventType;
  String channel = "WHATSAPP";
  boolean enabled;
  int delayMinutes;
  String templateKey;
  Instant createdAt;
  Instant updatedAt;

  @PrePersist
  void create() {
    var now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
