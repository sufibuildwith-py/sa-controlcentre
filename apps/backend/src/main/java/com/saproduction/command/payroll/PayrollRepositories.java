package com.saproduction.command.payroll;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod,UUID>{Optional<PayrollPeriod> findByYearAndMonth(int year,int month);List<PayrollPeriod> findAllByOrderByYearDescMonthDesc();}
interface PayrollItemRepository extends JpaRepository<PayrollItem,UUID>{List<PayrollItem> findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(UUID id);Optional<PayrollItem> findByPayrollPeriodIdAndEmployeeId(UUID period,UUID employee);@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PayrollItem> findOneById(UUID id);}
interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment,UUID>{List<PayrollAdjustment> findAllByPayrollItemIdOrderByCreatedAt(UUID id);}
interface PayrollPaymentRepository extends JpaRepository<PayrollPayment,UUID>{List<PayrollPayment> findAllByPayrollItemIdOrderByPaidAtAscCreatedAtAsc(UUID id);default long totalFor(UUID id){return findAllByPayrollItemIdOrderByPaidAtAscCreatedAtAsc(id).stream().mapToLong(p->p.amountMinor).sum();}}
