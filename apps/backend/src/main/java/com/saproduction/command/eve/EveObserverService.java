package com.saproduction.command.eve;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Continuous Intelligence Observer for EVE Phase 4.
 * Observes committed canonical mutations, performs bounded authoritative retrieval,
 * validates evidence against deterministic policy, and manages the lifecycle of suggestions.
 * Never executes mutations autonomously.
 */
@Service
public class EveObserverService {

  private static final Logger log = LoggerFactory.getLogger(EveObserverService.class);

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final FinanceReadService financeReadService;
  private final ProductionRepository productionRepository;
  private final WorkTaskRepository workTaskRepository;
  private final EveSuggestionPolicy policy;

  public EveObserverService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      FinanceReadService financeReadService,
      ProductionRepository productionRepository,
      WorkTaskRepository workTaskRepository,
      EveSuggestionPolicy policy) {
    this.jdbc = jdbc;
    this.json = json;
    this.financeReadService = financeReadService;
    this.productionRepository = productionRepository;
    this.workTaskRepository = workTaskRepository;
    this.policy = policy;
  }

  @Transactional
  public void observeSignal(EveDtos.SignalView signal) {
    if (signal == null) return;
    String type = signal.signalType();

    if (EveSignalTypes.EMPLOYEE_PAYMENT_POSTED.equals(type)
        || EveSignalTypes.EMPLOYEE_EARNING_CREATED.equals(type)
        || EveSignalTypes.EMPLOYEE_BALANCE_CHANGED.equals(type)) {
      evaluateEmployeePayable(signal.canonicalEntityId(), signal.id());
    } else if (EveSignalTypes.PRODUCTION_CREATED.equals(type)
        || EveSignalTypes.PRODUCTION_UPDATED.equals(type)
        || EveSignalTypes.PRODUCTION_STATUS_CHANGED.equals(type)
        || EveSignalTypes.PRODUCTION_DATE_CHANGED.equals(type)
        || EveSignalTypes.CREW_ASSIGNMENT_CHANGED.equals(type)) {
      evaluateProductionTasks(signal.canonicalEntityId(), signal.id());
    } else if (EveSignalTypes.TASK_CREATED.equals(type)
        || EveSignalTypes.TASK_COMPLETED.equals(type)
        || EveSignalTypes.TASK_DUE_DATE_CHANGED.equals(type)
        || EveSignalTypes.TASK_PROGRESS_UPDATED.equals(type)) {
      evaluateTask(signal.canonicalEntityId(), signal.id());
    }
  }

  @Transactional
  public void evaluateEmployeePayable(UUID employeeId, UUID signalId) {
    if (employeeId == null) return;
    String dedupeKey = policy.buildDedupeKey(EveSuggestionPolicy.OUTSTANDING_EMPLOYEE_PAYMENT, employeeId);

    Map<String, Object> empFin;
    try {
      empFin = financeReadService.employee(employeeId);
    } catch (Exception e) {
      log.debug("Employee financial record not found or inaccessible for ID: {}", employeeId);
      return;
    }

    BigDecimal earned = (BigDecimal) empFin.get("earned");
    BigDecimal paid = (BigDecimal) empFin.get("paid");
    BigDecimal outstanding = (BigDecimal) empFin.get("outstanding");
    String employeeName = (String) empFin.get("displayName");
    if (employeeName == null) {
      employeeName = "Employee " + employeeId;
    }

    if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) > 0) {
      if (isDedupeInCooldown(dedupeKey)) {
        return;
      }

      String priority = policy.calculatePriority(EveSuggestionPolicy.OUTSTANDING_EMPLOYEE_PAYMENT, outstanding);
      String title = "Outstanding payable balance for " + employeeName;
      String summary = employeeName + " has ₹" + outstanding.toPlainString()
          + " outstanding payable balance. Review or schedule payment.";

      List<EveDtos.SuggestionEvidenceItem> evidence = List.of(
          new EveDtos.SuggestionEvidenceItem("FINANCE", "EMPLOYEE", employeeId, "Employee Name", employeeName, Instant.now()),
          new EveDtos.SuggestionEvidenceItem("FINANCE", "EMPLOYEE", employeeId, "Outstanding Balance", "₹" + outstanding.toPlainString(), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("FINANCE", "EMPLOYEE", employeeId, "Total Earned", "₹" + (earned != null ? earned.toPlainString() : "0.00"), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("FINANCE", "EMPLOYEE", employeeId, "Total Paid", "₹" + (paid != null ? paid.toPlainString() : "0.00"), Instant.now())
      );

      upsertSuggestion(
          EveSuggestionPolicy.OUTSTANDING_EMPLOYEE_PAYMENT,
          priority,
          title,
          summary,
          signalId,
          "FINANCE",
          "EMPLOYEE",
          employeeId,
          employeeName,
          evidence,
          dedupeKey);
    } else {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
    }
  }

  @Transactional
  public void evaluateProductionTasks(UUID productionId, UUID signalId) {
    if (productionId == null) return;
    String dedupeKey = policy.buildDedupeKey(EveSuggestionPolicy.APPROACHING_PRODUCTION_OPEN_TASKS, productionId);

    Optional<Production> optProd = productionRepository.findById(productionId);
    if (optProd.isEmpty()) {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
      return;
    }

    Production p = optProd.get();
    if (p.status == Production.Status.DELIVERED || p.status == Production.Status.CANCELLED) {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
      return;
    }

    LocalDate today = LocalDate.now();
    long daysRemaining = ChronoUnit.DAYS.between(today, p.eventDate);

    // Look at productions within next 14 days or imminent
    if (daysRemaining < -1 || daysRemaining > 14) {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
      return;
    }

    long openTasksCount = workTaskRepository.countByProductionIdAndStatusNotIn(
        productionId,
        List.of(WorkTask.Status.DONE, WorkTask.Status.CANCELLED));

    if (openTasksCount > 0) {
      if (isDedupeInCooldown(dedupeKey)) {
        return;
      }

      String priority = policy.calculatePriority(EveSuggestionPolicy.APPROACHING_PRODUCTION_OPEN_TASKS, daysRemaining);
      String title = p.title + " is approaching with " + openTasksCount + " open tasks";
      String summary = "Production '" + p.title + "' is scheduled for " + p.eventDate
          + " at " + p.venueName + " with " + openTasksCount + " tasks remaining open.";

      List<EveDtos.SuggestionEvidenceItem> evidence = List.of(
          new EveDtos.SuggestionEvidenceItem("PRODUCTION", "PRODUCTION", productionId, "Production Title", p.title, Instant.now()),
          new EveDtos.SuggestionEvidenceItem("PRODUCTION", "PRODUCTION", productionId, "Event Date", p.eventDate.toString(), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("PRODUCTION", "PRODUCTION", productionId, "Days Remaining", String.valueOf(daysRemaining), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("PRODUCTION", "PRODUCTION", productionId, "Venue", p.venueName, Instant.now()),
          new EveDtos.SuggestionEvidenceItem("WORK", "TASK_GROUP", productionId, "Open Tasks Count", String.valueOf(openTasksCount), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("PRODUCTION", "PRODUCTION", productionId, "Current Status", p.status.name(), Instant.now())
      );

      upsertSuggestion(
          EveSuggestionPolicy.APPROACHING_PRODUCTION_OPEN_TASKS,
          priority,
          title,
          summary,
          signalId,
          "PRODUCTION",
          "PRODUCTION",
          productionId,
          p.title,
          evidence,
          dedupeKey);
    } else {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
    }
  }

  @Transactional
  public void evaluateTask(UUID taskId, UUID signalId) {
    if (taskId == null) return;
    String dedupeKey = policy.buildDedupeKey(EveSuggestionPolicy.OVERDUE_TASK, taskId);

    Optional<WorkTask> optTask = workTaskRepository.findById(taskId);
    if (optTask.isEmpty()) {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
      return;
    }

    WorkTask task = optTask.get();
    Instant now = Instant.now();
    boolean isOverdue = task.status != WorkTask.Status.DONE
        && task.status != WorkTask.Status.CANCELLED
        && task.dueAt != null
        && task.dueAt.isBefore(now);

    if (isOverdue) {
      if (isDedupeInCooldown(dedupeKey)) {
        // Still evaluate production if linked
        if (task.productionId != null) {
          evaluateProductionTasks(task.productionId, signalId);
        }
        return;
      }

      String priority = policy.calculatePriority(EveSuggestionPolicy.OVERDUE_TASK, null);
      String title = "Task '" + task.title + "' is overdue";
      String summary = "Task '" + task.title + "' was due on " + task.dueAt + " and is currently " + task.status + ".";

      List<EveDtos.SuggestionEvidenceItem> evidence = List.of(
          new EveDtos.SuggestionEvidenceItem("WORK", "TASK", taskId, "Task Title", task.title, Instant.now()),
          new EveDtos.SuggestionEvidenceItem("WORK", "TASK", taskId, "Status", task.status.name(), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("WORK", "TASK", taskId, "Due At", task.dueAt.toString(), Instant.now()),
          new EveDtos.SuggestionEvidenceItem("WORK", "TASK", taskId, "Priority", task.priority.name(), Instant.now())
      );

      upsertSuggestion(
          EveSuggestionPolicy.OVERDUE_TASK,
          priority,
          title,
          summary,
          signalId,
          "WORK",
          "TASK",
          taskId,
          task.title,
          evidence,
          dedupeKey);
    } else {
      resolveActiveSuggestionByDedupeKey(dedupeKey);
    }

    // If task belongs to a production, evaluate the production's task state
    if (task.productionId != null) {
      evaluateProductionTasks(task.productionId, signalId);
    }
  }

  @Transactional
  public void evaluateAll() {
    // 1. Evaluate employees with active records
    List<UUID> employeeIds = jdbc.query(
        "SELECT id FROM employees ORDER BY display_name",
        (rs, rowNum) -> rs.getObject("id", UUID.class));
    for (UUID empId : employeeIds) {
      evaluateEmployeePayable(empId, null);
    }

    // 2. Evaluate approaching productions
    List<UUID> productionIds = jdbc.query(
        "SELECT id FROM productions WHERE status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY event_date ASC LIMIT 50",
        (rs, rowNum) -> rs.getObject("id", UUID.class));
    for (UUID prodId : productionIds) {
      evaluateProductionTasks(prodId, null);
    }

    // 3. Evaluate tasks with due date
    List<UUID> taskIds = jdbc.query(
        "SELECT id FROM tasks WHERE status NOT IN ('DONE', 'CANCELLED') AND due_at < now() LIMIT 50",
        (rs, rowNum) -> rs.getObject("id", UUID.class));
    for (UUID taskId : taskIds) {
      evaluateTask(taskId, null);
    }
  }

  private void upsertSuggestion(
      String type,
      String priority,
      String title,
      String summary,
      UUID sourceSignalId,
      String targetDomain,
      String canonicalEntityType,
      UUID canonicalEntityId,
      String canonicalEntityName,
      List<EveDtos.SuggestionEvidenceItem> evidence,
      String dedupeKey) {
    String evidenceJson = writeJson(evidence);

    // Check if active suggestion exists
    var existing = jdbc.query(
        "SELECT id FROM eve_suggestions WHERE dedupe_key = ? AND status = 'ACTIVE' LIMIT 1",
        (rs, rowNum) -> rs.getObject("id", UUID.class),
        dedupeKey);

    if (!existing.isEmpty()) {
      // Update existing active suggestion in-place with fresh evidence
      jdbc.update(
          """
          UPDATE eve_suggestions SET
            priority = ?,
            title = ?,
            summary = ?,
            evidence = ?::jsonb,
            updated_at = now()
          WHERE id = ?
          """,
          priority,
          title,
          summary,
          evidenceJson,
          existing.getFirst());
    } else {
      // Insert new active suggestion
      UUID suggestionId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO eve_suggestions (
            id, type, status, priority, title, summary, source_signal_id,
            target_domain, canonical_entity_type, canonical_entity_id, canonical_entity_name,
            evidence, dedupe_key, created_at, updated_at
          ) VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), now())
          """,
          suggestionId,
          type,
          priority,
          title,
          summary,
          sourceSignalId,
          targetDomain,
          canonicalEntityType,
          canonicalEntityId,
          canonicalEntityName,
          evidenceJson,
          dedupeKey);
    }
  }

  private void resolveActiveSuggestionByDedupeKey(String dedupeKey) {
    jdbc.update(
        "UPDATE eve_suggestions SET status = 'RESOLVED', resolved_at = now(), updated_at = now() "
            + "WHERE dedupe_key = ? AND status = 'ACTIVE'",
        dedupeKey);
  }

  private boolean isDedupeInCooldown(String dedupeKey) {
    List<Timestamp> dismissedTimes = jdbc.query(
        "SELECT dismissed_at FROM eve_suggestions WHERE dedupe_key = ? AND status = 'DISMISSED' ORDER BY dismissed_at DESC LIMIT 1",
        (rs, rowNum) -> rs.getTimestamp("dismissed_at"),
        dedupeKey);
    if (dismissedTimes.isEmpty() || dismissedTimes.getFirst() == null) {
      return false;
    }
    return policy.isCooldownActive(dismissedTimes.getFirst().toInstant(), null);
  }

  private String writeJson(Object obj) {
    try {
      return json.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}
