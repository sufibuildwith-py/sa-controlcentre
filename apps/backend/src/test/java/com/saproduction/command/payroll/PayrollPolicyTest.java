package com.saproduction.command.payroll;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.saproduction.command.attendance.*;
import com.saproduction.command.employee.Employee;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class PayrollPolicyTest {
  @Test void zeroAbsencesMeansNoDeduction(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=employee(4_200_000);when(attendance.findAllByEmployeeIdAndDateBetween(eq(e.id),any(),any())).thenReturn(List.of());assertThat(new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",26).attendanceDeduction(e,YearMonth.of(2026,9))).isZero();}
  @Test void oneAbsenceDeductsOneRoundedWorkingDay(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=employee(4_200_000);when(attendance.findAllByEmployeeIdAndDateBetween(eq(e.id),any(),any())).thenReturn(List.of(record(AttendanceRecord.Status.ABSENT)));assertThat(new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",26).attendanceDeduction(e,YearMonth.of(2026,9))).isEqualTo(161_538);}
  @Test void halfDayRoundsAtTheMinorUnitBoundary(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=employee(101);when(attendance.findAllByEmployeeIdAndDateBetween(eq(e.id),any(),any())).thenReturn(List.of(record(AttendanceRecord.Status.HALF_DAY)));assertThat(new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",2).attendanceDeduction(e,YearMonth.of(2026,9))).isEqualTo(25);}
  @Test void multipleAbsencesAccumulateBeforeRounding(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=employee(1_001);when(attendance.findAllByEmployeeIdAndDateBetween(eq(e.id),any(),any())).thenReturn(List.of(record(AttendanceRecord.Status.ABSENT),record(AttendanceRecord.Status.ABSENT),record(AttendanceRecord.Status.HALF_DAY)));assertThat(new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",3).attendanceDeduction(e,YearMonth.of(2026,9))).isEqualTo(834);}
  @Test void perWorkingDayUsesDeterministicHalfUpMinorUnits(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=new Employee();e.id=java.util.UUID.randomUUID();e.baseSalaryMinor=4_200_000;AttendanceRecord absent=new AttendanceRecord();absent.status=AttendanceRecord.Status.ABSENT;AttendanceRecord half=new AttendanceRecord();half.status=AttendanceRecord.Status.HALF_DAY;when(attendance.findAllByEmployeeIdAndDateBetween(eq(e.id),any(),any())).thenReturn(List.of(absent,half));assertThat(new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",26).attendanceDeduction(e,YearMonth.of(2026,9))).isEqualTo(242_308);}
  @Test void nonePolicyNeverInventsAttendanceRules(){AttendanceRepository attendance=mock(AttendanceRepository.class);Employee e=new Employee();e.id=java.util.UUID.randomUUID();e.baseSalaryMinor=4_200_000;assertThat(new ConfiguredPayrollPolicy(attendance,"NONE",26).attendanceDeduction(e,YearMonth.of(2026,9))).isZero();verifyNoInteractions(attendance);}
  @Test void invalidConfigurationFailsFast(){AttendanceRepository attendance=mock(AttendanceRepository.class);assertThatThrownBy(()->new ConfiguredPayrollPolicy(attendance,"UNKNOWN",26)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new ConfiguredPayrollPolicy(attendance,"PER_WORKING_DAY",0)).isInstanceOf(IllegalArgumentException.class);}
  private static Employee employee(long salary){Employee e=new Employee();e.id=java.util.UUID.randomUUID();e.baseSalaryMinor=salary;return e;}
  private static AttendanceRecord record(AttendanceRecord.Status status){AttendanceRecord row=new AttendanceRecord();row.status=status;return row;}
}
