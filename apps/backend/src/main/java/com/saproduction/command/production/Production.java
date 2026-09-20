package com.saproduction.command.production;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "productions")
public class Production {
  public enum Status {
    DRAFT,
    PLANNING,
    PRE_PRODUCTION,
    PRODUCTION,
    POST_PRODUCTION,
    REVIEW,
    DELIVERED,
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

  @Column(nullable = false)
  public String title;

  @Column(name = "client_name", nullable = false)
  public String clientName;

  public String description;

  @Column(name = "event_date", nullable = false)
  public LocalDate eventDate;

  @Column(name = "start_time", nullable = false)
  public LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  public LocalTime endTime;

  @Column(name = "venue_name", nullable = false)
  public String venueName;

  @Column(name = "venue_address")
  public String venueAddress;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Priority priority;

  @Column(name = "progress_percent", nullable = false)
  public int progressPercent;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  void create() {
    var now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (status == null) status = Status.DRAFT;
    if (priority == null) priority = Priority.NORMAL;
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
