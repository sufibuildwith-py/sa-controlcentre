package com.saproduction.command.eve;

import com.saproduction.command.audit.DomainMutationEvent;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transactional event listener that bridges canonical domain mutations to EVE signals.
 * STRICT TRANSACTION INVARIANT: Executes AFTER_COMMIT only.
 * If a canonical transaction rolls back, no EVE signal is ever emitted.
 */
@Component
public class EveSignalEventListener {

  private static final Logger log = LoggerFactory.getLogger(EveSignalEventListener.class);

  private final EveSignalService signalService;
  private final JdbcTemplate jdbc;

  public EveSignalEventListener(EveSignalService signalService, JdbcTemplate jdbc) {
    this.signalService = signalService;
    this.jdbc = jdbc;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDomainMutation(DomainMutationEvent event) {
    if (event == null || event.entityType() == null || event.entityId() == null) {
      return;
    }

    try {
      UUID entityId = parseUuid(event.entityId());
      if (entityId == null) return;

      String signalType = null;
      String sourceDomain = null;
      String canonicalEntityType = event.entityType();
      UUID canonicalEntityId = entityId;

      switch (event.entityType()) {
        case "FINANCE_TRANSACTION" -> {
          sourceDomain = "FINANCE";
          // The audit eventId is the transactionId. Query the canonical transaction for affected entity.
          var txRow = jdbc.queryForList(
              "SELECT transaction_type, employee_id, production_id, counterparty_id, invoice_id FROM finance_transactions WHERE id = ?",
              entityId);
          if (!txRow.isEmpty()) {
            var tx = txRow.getFirst();
            String txType = (String) tx.get("transaction_type");
            UUID empId = (UUID) tx.get("employee_id");
            UUID prodId = (UUID) tx.get("production_id");

            if ("EMPLOYEE_PAYMENT".equals(txType)) {
              signalType = EveSignalTypes.EMPLOYEE_PAYMENT_POSTED;
              if (empId != null) {
                canonicalEntityType = "EMPLOYEE";
                canonicalEntityId = empId;
              }
            } else if ("EMPLOYEE_EARNING".equals(txType) || "MONTHLY_SALARY_ACCRUAL".equals(txType)) {
              signalType = EveSignalTypes.EMPLOYEE_EARNING_CREATED;
              if (empId != null) {
                canonicalEntityType = "EMPLOYEE";
                canonicalEntityId = empId;
              }
            } else if ("PRODUCTION_CONTRACT".equals(txType) || "PRODUCTION_RECEIPT".equals(txType)) {
              signalType = EveSignalTypes.PRODUCTION_UPDATED;
              if (prodId != null) {
                canonicalEntityType = "PRODUCTION";
                canonicalEntityId = prodId;
              }
            } else if ("INVOICE_ISSUED".equals(txType)) {
              signalType = EveSignalTypes.INVOICE_CREATED;
            } else if ("INVOICE_PAYMENT".equals(txType)) {
              signalType = EveSignalTypes.INVOICE_SETTLED;
            } else if ("COUNTERPARTY_CHARGE".equals(txType)) {
              signalType = EveSignalTypes.PARTY_CHARGE_CREATED;
            } else if ("COUNTERPARTY_RECEIPT".equals(txType)) {
              signalType = EveSignalTypes.PARTY_RECEIPT_POSTED;
            } else {
              signalType = EveSignalTypes.EMPLOYEE_BALANCE_CHANGED;
            }
          }
        }
        case "PRODUCTION" -> {
          sourceDomain = "PRODUCTION";
          canonicalEntityType = "PRODUCTION";
          switch (event.action()) {
            case "PRODUCTION_CREATED" -> signalType = EveSignalTypes.PRODUCTION_CREATED;
            case "PRODUCTION_EDITED" -> signalType = EveSignalTypes.PRODUCTION_UPDATED;
            case "PRODUCTION_RESCHEDULED" -> signalType = EveSignalTypes.PRODUCTION_DATE_CHANGED;
            case "PRODUCTION_TRANSITIONED" -> signalType = EveSignalTypes.PRODUCTION_STATUS_CHANGED;
            case "PRODUCTION_MEMBER_ASSIGNED", "PRODUCTION_MEMBER_REMOVED" -> signalType = EveSignalTypes.CREW_ASSIGNMENT_CHANGED;
            default -> signalType = EveSignalTypes.PRODUCTION_UPDATED;
          }
        }
        case "TASK" -> {
          sourceDomain = "WORK";
          canonicalEntityType = "TASK";
          switch (event.action()) {
            case "TASK_CREATED" -> signalType = EveSignalTypes.TASK_CREATED;
            case "TASK_COMPLETED" -> signalType = EveSignalTypes.TASK_COMPLETED;
            case "TASK_PROGRESS_UPDATED", "TASK_EDITED" -> signalType = EveSignalTypes.TASK_PROGRESS_UPDATED;
            default -> signalType = EveSignalTypes.TASK_PROGRESS_UPDATED;
          }
        }
        default -> {
          // Unhandled domain for proactive observer, ignore
        }
      }

      if (signalType != null && sourceDomain != null) {
        String correlationId = "audit:" + event.entityType() + ":" + event.entityId() + ":" + event.action();
        signalService.emit(new EveDtos.EmitSignalRequest(
            signalType,
            sourceDomain,
            canonicalEntityType,
            canonicalEntityId,
            null,
            correlationId,
            Map.of("action", event.action(), "actorId", event.actorId())
        ));
      }
    } catch (Exception e) {
      log.warn("Error processing domain mutation event for EVE signal: {}", e.getMessage(), e);
    }
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      return null;
    }
  }
}
