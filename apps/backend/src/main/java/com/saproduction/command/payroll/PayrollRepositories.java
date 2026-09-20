package com.saproduction.command.payroll;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface PayrollSummaryProjection {
  UUID getId();

  int getYear();

  int getMonth();

  String getStatus();

  long getTotalMinor();

  long getPaidMinor();

  long getPaidCount();

  long getPartiallyPaidCount();

  long getUnpaidCount();

  java.time.Instant getCalculatedAt();

  java.time.Instant getApprovedAt();

  java.time.Instant getPaidAt();

  java.time.Instant getLockedAt();
}

interface PaymentTotalProjection {
  UUID getItemId();

  long getTotalPaid();
}

interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {
  Optional<PayrollPeriod> findByYearAndMonth(int year, int month);

  List<PayrollPeriod> findAllByOrderByYearDescMonthDesc();

  @Query(
      value =
          "select p.id,p.year,p.month,p.status,coalesce(sum(i.net_salary_minor),0) total_minor,coalesce(sum(coalesce(x.paid,0)),0) paid_minor,count(i.id) filter(where i.net_salary_minor>0 and coalesce(x.paid,0)>=i.net_salary_minor) paid_count,count(i.id) filter(where coalesce(x.paid,0)>0 and coalesce(x.paid,0)<i.net_salary_minor) partially_paid_count,count(i.id) filter(where i.net_salary_minor>0 and coalesce(x.paid,0)=0) unpaid_count,p.calculated_at,p.approved_at,p.paid_at,p.locked_at from payroll_periods p left join payroll_items i on i.payroll_period_id=p.id left join (select payroll_item_id,sum(amount_minor) paid from payroll_payments group by payroll_item_id) x on x.payroll_item_id=i.id group by p.id order by p.year desc,p.month desc",
      nativeQuery = true)
  List<PayrollSummaryProjection> summaries();
}

interface PayrollItemRepository extends JpaRepository<PayrollItem, UUID> {
  List<PayrollItem> findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(UUID id);

  Optional<PayrollItem> findByPayrollPeriodIdAndEmployeeId(UUID period, UUID employee);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PayrollItem> findOneById(UUID id);
}

interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, UUID> {
  List<PayrollAdjustment> findAllByPayrollItemIdOrderByCreatedAt(UUID id);
}

interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, UUID> {
  List<PayrollPayment> findAllByPayrollItemIdOrderByPaidAtAscCreatedAtAsc(UUID id);

  Optional<PayrollPayment> findByRequestId(UUID requestId);

  @Query("select coalesce(sum(p.amountMinor),0) from PayrollPayment p where p.payrollItemId=:id")
  long totalFor(UUID id);

  @Query(
      value =
          "select pp.payroll_item_id item_id,sum(pp.amount_minor) total_paid from payroll_payments pp join payroll_items i on i.id=pp.payroll_item_id where i.payroll_period_id=:periodId group by pp.payroll_item_id",
      nativeQuery = true)
  List<PaymentTotalProjection> totalsForPeriod(UUID periodId);
}
