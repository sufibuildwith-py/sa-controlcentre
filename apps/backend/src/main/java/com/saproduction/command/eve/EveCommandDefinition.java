package com.saproduction.command.eve;

import java.util.Map;

/**
 * Contract for finite domain commands supported by the EVE Command Gateway.
 * EVE never executes arbitrary commands; every command must be registered with explicit
 * risk tier, confirmation policy, schema validation, domain validation, canonical execution,
 * and authoritative post-execution verification.
 */
public interface EveCommandDefinition {

  String commandType();

  String domain();

  String description();

  EveDtos.RiskTier riskTier();

  EveDtos.ConfirmationPolicy confirmationPolicy();

  void validateSchema(Map<String, Object> parameters);

  void validateDomain(EveDtos.EvePlanAction action, EveExecutionContext context);

  EveDtos.EveCommandResult execute(EveDtos.EvePlanAction action, EveExecutionContext context);

  EveDtos.EveVerificationResult verify(
      EveDtos.EvePlanAction action,
      EveDtos.EveCommandResult executionResult,
      EveVerificationContext context);
}
