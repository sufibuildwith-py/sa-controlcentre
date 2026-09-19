package com.saproduction.command.payroll;
import com.saproduction.command.employee.Employee;
import java.time.YearMonth;
public interface PayrollCalculationPolicy {String name();long attendanceDeduction(Employee employee,YearMonth period);}
