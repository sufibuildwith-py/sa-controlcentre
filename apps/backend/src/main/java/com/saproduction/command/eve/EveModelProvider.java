package com.saproduction.command.eve;

import java.util.UUID;

/**
 * Model provider abstraction for EVE.
 * Decouples the application logic from the underlying model runtime (local, cloud, or test).
 */
public interface EveModelProvider {

  EveInterpretation interpret(EveInterpretationRequest request);

  record EveInterpretationRequest(
      String prompt,
      String sessionContext) {}

  record EveInterpretation(
      Intent intent,
      String entityType,
      String spokenEntity,
      UUID resolvedEntityId,
      double confidence,
      boolean mutation,
      String refusalReason) {

    public static EveInterpretation of(Intent intent, String entityType, String spokenEntity) {
      return new EveInterpretation(intent, entityType, spokenEntity, null, 1.0, false, null);
    }

    public static EveInterpretation refused(String reason) {
      return new EveInterpretation(Intent.BLOCKED, null, null, null, 0.0, false, reason);
    }
  }

  enum Intent {
    READ_EMPLOYEE_FINANCE,
    READ_EMPLOYEE_360,
    READ_PRODUCTION,
    READ_EQUIPMENT,
    READ_SYSTEM_SUMMARY,
    UNKNOWN,
    BLOCKED
  }
}
