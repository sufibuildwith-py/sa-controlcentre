package com.saproduction.command.payroll;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod,UUID>{Optional<PayrollPeriod> findByYearAndMonth(int year,int month);List<PayrollPeriod> findAllByOrderByYearDescMonthDesc();}
interface PayrollItemRepository extends JpaRepository<PayrollItem,UUID>{List<PayrollItem> findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(UUID id);Optional<PayrollItem> findByPayrollPeriodIdAndEmployeeId(UUID period,UUID employee);long countByPayrollPeriodIdAndPaymentStatus(UUID period,PayrollItem.PaymentStatus status);}
interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment,UUID>{List<PayrollAdjustment> findAllByPayrollItemIdOrderByCreatedAt(UUID id);}
