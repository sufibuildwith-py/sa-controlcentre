package com.saproduction.command.calendar;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent {
  public enum Type {
    PRODUCTION,
    SHOOT,
    MEETING,
    DEADLINE,
    INTERNAL,
    REMINDER
  }

  public enum Status {
    SCHEDULED,
    CANCELLED,
    COMPLETED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Type type;

  @Column(nullable = false)
  public String title;

  public String description;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "location_name")
  public String locationName;

  @Column(name = "location_address")
  public String locationAddress;

  @Column(name = "production_id")
  public UUID productionId;

  @Column(name = "meeting_id")
  public UUID meetingId;

  @Column(name = "task_id")
  public UUID taskId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  void create() {
    var now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (status == null) status = Status.SCHEDULED;
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
