package com.saproduction.command.payroll;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_periods")
public class PayrollPeriod {
  public enum Status {
    DRAFT,
    CALCULATED,
    APPROVED,
    PAID,
    LOCKED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(nullable = false)
  public int year;

  @Column(nullable = false)
  public int month;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "calculated_at")
  public Instant calculatedAt;

  @Column(name = "approved_at")
  public Instant approvedAt;

  @Column(name = "paid_at")
  public Instant paidAt;

  @Column(name = "locked_at")
  public Instant lockedAt;

  @PrePersist
  void create() {
    createdAt = Instant.now();
    if (status == null) status = Status.DRAFT;
  }
}
