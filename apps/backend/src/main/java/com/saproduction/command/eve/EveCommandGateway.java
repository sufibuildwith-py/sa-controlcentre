package com.saproduction.command.eve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hard AI-to-Business boundary for EVE Phase 3 Governed Execution.
 * Guarantees that:
 * 1. Plans are bounded (MAX_ACTIONS = 10).
 * 2. Only finite, registered commands can execute.
 * 3. Parameters pass schema validation.
 * 4. Preconditions and canonical state are revalidated before execution.
 * 5. Mutations only occur through canonical application services (e.g. FinancePostingService).
 * 6. Every mutation undergoes post-execution authoritative verification.
 */
@Component
public class EveCommandGateway {

  public static final int MAX_ACTIONS = 10;

  private final EveCommandRegistry registry;
  private final FinancePostingService financePostingService;
  private final FinanceReadService financeReadService;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  @Autowired
  public EveCommandGateway(
      EveCommandRegistry registry,
      FinancePostingService financePostingService,
      FinanceReadService financeReadService,
      JdbcTemplate jdbc,
      ObjectMapper json) {
    this.registry = registry;
    this.financePostingService = financePostingService;
    this.financeReadService = financeReadService;
    this.jdbc = jdbc;
    this.json = json != null ? json : new ObjectMapper();
  }

  public void validatePlanSchema(EveDtos.EvePlan plan) {
    if (plan == null) {
      throw ApiException.badRequest("PLAN_REQUIRED", "Plan cannot be null.");
    }
    if (plan.actions() == null || plan.actions().isEmpty()) {
      throw ApiException.badRequest("PLAN_EMPTY", "Plan must contain at least one action.");
    }
    if (plan.actions().size() > MAX_ACTIONS) {
      throw ApiException.badRequest(
          "PLAN_EXCEEDS_MAX_ACTIONS",
          "Plan exceeds maximum limit of " + MAX_ACTIONS + " actions.");
    }

    int expectedSeq = 1;
    for (EveDtos.EvePlanAction action : plan.actions()) {
      if (action.seq() != expectedSeq++) {
        throw ApiException.badRequest(
            "PLAN_INVALID_SEQUENCE",
            "Action sequence numbers must be contiguous starting from 1.");
      }
      EveCommandDefinition def = registry.getRequired(action.commandType());
      def.validateSchema(action.parameters());
    }
  }

  public void revalidatePreconditions(EveDtos.EvePlan plan, EveExecutionContext context) {
    validatePlanSchema(plan);
    for (EveDtos.EvePlanAction action : plan.actions()) {
      EveCommandDefinition def = registry.getRequired(action.commandType());
      try {
        def.validateDomain(action, context);
      } catch (ApiException e) {
        throw ApiException.conflict("STALE_PLAN", "Preconditions changed: " + e.getMessage());
      }
    }
  }

  public List<EveDtos.EvePlanAction> executePlan(EveDtos.EvePlan plan, EveExecutionContext context) {
    validatePlanSchema(plan);

    List<EveDtos.EvePlanAction> executedActions = new ArrayList<>();
    for (EveDtos.EvePlanAction action : plan.actions()) {
      EveCommandDefinition def = registry.getRequired(action.commandType());

      // 1. Re-check domain preconditions immediately prior to execution
      def.validateDomain(action, context);

      // 2. Dispatch to canonical domain service
      EveDtos.EveCommandResult cmdResult = def.execute(action, context);

      // 3. Authoritative post-execution verification against PostgreSQL
      EveVerificationContext vCtx = new EveVerificationContext(
          context.sessionId(),
          context.planId(),
          cmdResult.canonicalRecordId(),
          context.financeReadService(),
          context.jdbc());
      EveDtos.EveVerificationResult vResult = def.verify(action, cmdResult, vCtx);

      if (!"VERIFIED".equals(vResult.status())) {
        EveDtos.EvePlanAction failedAction = new EveDtos.EvePlanAction(
            action.actionId(),
            action.seq(),
            action.domain(),
            action.commandType(),
            action.targetEntityId(),
            action.targetEntityName(),
            action.parameters(),
            action.estimatedEffect(),
            action.requiredPermission(),
            "VERIFICATION_FAILED",
            cmdResult.canonicalRecordId(),
            Map.of("status", cmdResult.status(), "message", cmdResult.message()),
            vResult);
        executedActions.add(failedAction);
        throw ApiException.conflict("VERIFICATION_FAILED", "Authoritative verification failed: " + vResult.notes());
      }

      EveDtos.EvePlanAction completedAction = new EveDtos.EvePlanAction(
          action.actionId(),
          action.seq(),
          action.domain(),
          action.commandType(),
          action.targetEntityId(),
          action.targetEntityName(),
          action.parameters(),
          action.estimatedEffect(),
          action.requiredPermission(),
          "VERIFIED",
          cmdResult.canonicalRecordId(),
          Map.of(
              "status", cmdResult.status(),
              "message", cmdResult.message(),
              "canonicalRecordId", cmdResult.canonicalRecordId() != null ? cmdResult.canonicalRecordId().toString() : ""),
          vResult);
      executedActions.add(completedAction);
    }
    return executedActions;
  }

  public EveExecutionContext createContext(UUID sessionId, UUID planId, int version, String planHash, String operator) {
    return new EveExecutionContext(
        sessionId,
        planId,
        version,
        planHash,
        operator != null ? operator : "Operator",
        financePostingService,
        financeReadService,
        jdbc,
        json);
  }
}
