package com.saproduction.command.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class CommandDashboardDto {

  public record CommandDashboard(
      LocalDate date,
      Today today,
      Money money,
      List<AttentionItem> attention,
      Operations operations,
      List<FinancialActivity> recentFinancialActivity,
      List<QuickAction> quickActions) {}

  public record Today(
      int productions, int tasks, int attendanceExceptions, MoneyMovement moneyMovement) {}

  public record MoneyMovement(
      BigDecimal received, BigDecimal disbursed, BigDecimal net, int transactionCount) {}

  public record Money(
      BigDecimal businessPosition,
      BigDecimal azeemPosition,
      BigDecimal akashPosition,
      BigDecimal customerReceivable,
      BigDecimal employeePayable,
      BigDecimal invoiceReceivable,
      BigDecimal totalReceivable,
      String reconciliationStatus) {}

  public record AttentionItem(
      String severity,
      String type,
      String title,
      String description,
      Integer count,
      UUID entityId,
      String route) {}

  public record Operations(
      List<DashboardProduction> upcomingProductions,
      List<DashboardTask> pendingWork,
      List<DashboardAttendanceException> attendanceExceptions) {}

  public record DashboardProduction(
      UUID id,
      String title,
      String clientName,
      String venueName,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      String status,
      String priority,
      int progressPercent) {}

  public record DashboardTask(
      UUID id,
      String title,
      String priority,
      String status,
      Instant dueAt,
      String assignedEmployeeName,
      String productionTitle) {}

  public record DashboardAttendanceException(
      UUID employeeId,
      String employeeName,
      String status,
      LocalTime checkInTime,
      Integer minutesLate,
      String notes) {}

  public record FinancialActivity(
      UUID id,
      Long transactionNo,
      LocalDate date,
      String type,
      BigDecimal amount,
      String description,
      String counterpartyName,
      String employeeName,
      String productionTitle,
      String status) {}

  public record QuickAction(String id, String label, String icon, String route) {}
}
