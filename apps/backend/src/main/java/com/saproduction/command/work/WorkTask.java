package com.saproduction.command.work;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class WorkTask {
  public enum Status {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE,
    CANCELLED
  }

  public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(name = "production_id")
  public UUID productionId;

  @Column(name = "meeting_origin_id")
  public UUID meetingOriginId;

  @Column(nullable = false)
  public String title;

  public String description;

  @Column(name = "assigned_employee_id")
  public UUID assignedEmployeeId;

  @Column(name = "created_by")
  public UUID createdBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Priority priority;

  @Column(name = "start_date")
  public LocalDate startDate;

  @Column(name = "due_at")
  public Instant dueAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(name = "progress_percent", nullable = false)
  public int progressPercent;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  void create() {
    var now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (status == null) status = Status.TODO;
    if (priority == null) priority = Priority.NORMAL;
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
