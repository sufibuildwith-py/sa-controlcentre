package com.saproduction.command.payroll;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="payroll_items")
public class PayrollItem {
  public enum PaymentStatus { PENDING,PAID }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="payroll_period_id",nullable=false) public UUID payrollPeriodId;
  @Column(name="employee_id",nullable=false) public UUID employeeId;
  @Column(name="employee_name_snapshot",nullable=false) public String employeeNameSnapshot;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name="salary_currency",nullable=false,length=3) public String salaryCurrency;
  @Column(name="base_salary_minor",nullable=false) public long baseSalaryMinor;
  @Column(name="attendance_deduction_minor",nullable=false) public long attendanceDeductionMinor;
  @Column(name="overtime_minor",nullable=false) public long overtimeMinor;
  @Column(name="bonus_minor",nullable=false) public long bonusMinor;
  @Column(name="advance_deduction_minor",nullable=false) public long advanceDeductionMinor;
  @Column(name="manual_adjustment_minor",nullable=false) public long manualAdjustmentMinor;
  @Column(name="net_salary_minor",nullable=false) public long netSalaryMinor;
  @Enumerated(EnumType.STRING) @Column(name="payment_status",nullable=false) public PaymentStatus paymentStatus;
  @Column(name="paid_at") public Instant paidAt;
  @Column(name="created_at",nullable=false) public Instant createdAt;
  @PrePersist void create(){createdAt=Instant.now();if(paymentStatus==null)paymentStatus=PaymentStatus.PENDING;}
}
