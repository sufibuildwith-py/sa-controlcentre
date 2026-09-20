package com.saproduction.command.payroll;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_payments")
public class PayrollPayment {
  public enum Method {
    CASH,
    BANK_TRANSFER,
    UPI,
    CHEQUE,
    OTHER
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(name = "request_id", nullable = false, unique = true)
  public UUID requestId;

  @Column(name = "payroll_item_id", nullable = false)
  public UUID payrollItemId;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "paid_at", nullable = false)
  public Instant paidAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false)
  public Method paymentMethod;

  @Column(length = 160)
  public String reference;

  @Column(length = 500)
  public String note;

  @Column(name = "recorded_by")
  public UUID recordedBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  void create() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
