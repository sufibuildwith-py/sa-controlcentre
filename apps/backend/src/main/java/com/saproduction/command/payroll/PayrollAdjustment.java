package com.saproduction.command.payroll;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payroll_adjustments")
public class PayrollAdjustment {
  public enum Type { BONUS,DEDUCTION,OVERTIME,ADVANCE,CORRECTION,OTHER }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="payroll_item_id",nullable=false) public UUID payrollItemId;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Type type;
  @Column(name="amount_minor",nullable=false) public long amountMinor;
  @Column(nullable=false) public String reason;
  @Column(name="created_by") public UUID createdBy;
  @Column(name="created_at",nullable=false) public Instant createdAt;
  @PrePersist void create(){createdAt=Instant.now();}
}
