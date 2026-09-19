package com.saproduction.command.payroll;
import com.saproduction.command.attendance.*;
import com.saproduction.command.employee.Employee;
import java.math.*;
import java.time.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class ConfiguredPayrollPolicy implements PayrollCalculationPolicy {
  private final AttendanceRepository attendance;private final String strategy;private final int configuredWorkingDays;
  public ConfiguredPayrollPolicy(AttendanceRepository attendance,@Value("${app.payroll.policy:PER_WORKING_DAY}") String strategy,@Value("${app.payroll.working-days:26}") int configuredWorkingDays){if(!Set.of("PER_WORKING_DAY","NONE").contains(strategy))throw new IllegalArgumentException("Unsupported payroll policy: "+strategy);if(configuredWorkingDays<1)throw new IllegalArgumentException("Payroll working days must be positive.");this.attendance=attendance;this.strategy=strategy;this.configuredWorkingDays=configuredWorkingDays;}
  public String name(){return strategy;}
  public long attendanceDeduction(Employee employee,YearMonth period){if("NONE".equals(strategy))return 0;var rows=attendance.findAllByEmployeeIdAndDateBetween(employee.id,period.atDay(1),period.atEndOfMonth());BigDecimal unpaid=rows.stream().map(r->switch(r.status){case ABSENT->BigDecimal.ONE;case HALF_DAY->new BigDecimal("0.5");default->BigDecimal.ZERO;}).reduce(BigDecimal.ZERO,BigDecimal::add);if(unpaid.signum()==0)return 0;return BigDecimal.valueOf(employee.baseSalaryMinor).multiply(unpaid).divide(BigDecimal.valueOf(configuredWorkingDays),0,RoundingMode.HALF_UP).longValueExact();}
}
