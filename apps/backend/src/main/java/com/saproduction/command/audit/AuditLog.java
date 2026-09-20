package com.saproduction.command.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(name = "actor_type", nullable = false)
  public String actorType;

  @Column(name = "actor_id")
  public String actorId;

  @Column(name = "entity_type", nullable = false)
  public String entityType;

  @Column(name = "entity_id", nullable = false)
  public String entityId;

  @Column(nullable = false)
  public String action;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_json", columnDefinition = "jsonb")
  public String beforeJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_json", columnDefinition = "jsonb")
  public String afterJson;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  void create() {
    createdAt = Instant.now();
  }
}
