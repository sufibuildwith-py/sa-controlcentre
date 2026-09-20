package com.saproduction.command.production;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "production_members")
public class ProductionMember {
  public enum Status {
    PENDING,
    CONFIRMED,
    DECLINED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(name = "production_id", nullable = false)
  public UUID productionId;

  @Column(name = "employee_id", nullable = false)
  public UUID employeeId;

  @Column(name = "production_role", nullable = false)
  public String productionRole;

  @Column(name = "attendance_required", nullable = false)
  public boolean attendanceRequired;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_status", nullable = false)
  public Status assignmentStatus;

  @Column(name = "conflict_overridden", nullable = false)
  public boolean conflictOverridden;

  @Column(name = "override_reason")
  public String overrideReason;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  void create() {
    var now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (assignmentStatus == null) assignmentStatus = Status.PENDING;
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
