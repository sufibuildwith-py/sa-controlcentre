package com.saproduction.command.employee;

import com.saproduction.command.attendance.AttendanceService;
import com.saproduction.command.leave.LeaveRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Strongly typed DTOs for the unified Employee 360 command layer.
 */
public final class Employee360Dto {
  private Employee360Dto() {}

  public record Employee360View(
      EmployeeDtos.View employee,
      TodaySummary today,
      MoneySummary money,
      OperationsSummary operations,
      PerformanceSummary performance,
      CommunicationSummary communication,
      NavigatorSummary navigator) {}

  public record TodaySummary(
      LocalDate date,
      AttendanceService.RecordView attendance,
      LeaveSummary activeOrPendingLeave,
      long activeTasksCount,
      long overdueTasksCount,
      long activeProductionsCount) {}

  public record LeaveSummary(
      UUID id,
      LocalDate startDate,
      LocalDate endDate,
      String leaveType,
      String reason,
      LeaveRequest.Status status) {}

  public record MoneySummary(
      BigDecimal earned,
      BigDecimal paid,
      BigDecimal outstanding,
      long baseSalaryMinor,
      String salaryCurrency,
      String currentPayrollStatus,
      String latestPayrollPeriod) {}

  public record ActiveProductionSummary(
      UUID id,
      String title,
      String role,
      LocalDate eventDate,
      String status) {}

  public record OperationsSummary(
      long activeProductionsCount,
      long completedProductionsCount,
      long tasksAssigned,
      long tasksCompleted,
      long overdueTasks,
      List<ActiveProductionSummary> activeProductions) {}

  public record PerformanceSummary(
      long attendanceRecords,
      long attended,
      long lateCount,
      Integer attendanceRate,
      Integer onTimeCompletionRate) {}

  public record CommunicationSummary(
      long totalMessages,
      Instant lastContactAt,
      boolean canMessage) {}

  public record NavigatorSummary(
      boolean enabled,
      boolean isPaired,
      String deviceStatus,
      Instant lastSyncAt) {}
}
