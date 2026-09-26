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

  /**
   * Phase 2 Structured Attention Model:
   * - id: deterministic identifier
   * - severity: CRITICAL > HIGH > MEDIUM > INFO
   * - category: RECONCILIATION | FINANCE | PAYROLL | PRODUCTION | WORK | ATTENDANCE
   * - title: What is wrong? (short alert headline)
   * - reason: Why is it surfaced? (rationale explanation)
   * - description: Operational context / amount summary
   * - entityType: Domain entity type (INVOICE, EMPLOYEE, PRODUCTION, TASK, RECONCILIATION, ATTENDANCE)
   * - entityId: Target domain entity UUID if single entity, or null if aggregate
   * - amount: Associated monetary value in INR, or null if N/A
   * - count: Affected item count
   * - route: Canonical navigation target URL
   * - queryParams: Optional deep link query parameters
   */
  public record AttentionItem(
      String id,
      String severity,
      String type,
      String category,
      String title,
      String reason,
      String description,
      String entityType,
      UUID entityId,
      BigDecimal amount,
      Integer count,
      String route,
      String queryParams) {}

  public record Operations(
      List<DashboardProduction> upcomingProductions,
      List<DashboardTask> pendingWork,
      List<DashboardAttendanceException> attendanceExceptions) {}

  /**
   * Phase 2 Enriched Production context:
   * includes contracted financial commitment, received amount, and task progress.
   */
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
      int progressPercent,
      BigDecimal contractedAmount,
      BigDecimal receivedAmount,
      int taskCount,
      int openTaskCount) {}

  /**
   * Phase 2 Enriched Task context:
   * includes explicit overdue status, task bucket, and links to production and assignee.
   */
  public record DashboardTask(
      UUID id,
      String title,
      String priority,
      String status,
      Instant dueAt,
      String assignedEmployeeName,
      UUID assignedEmployeeId,
      String productionTitle,
      UUID productionId,
      boolean isOverdue,
      String bucket) {}

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
