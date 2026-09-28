package com.saproduction.command.eve;

import java.util.UUID;

/**
 * Model provider abstraction for EVE.
 * Decouples the application logic from the underlying model runtime (local, cloud, or test).
 *
 * Handles safely:
 * - timeout
 * - unavailable model
 * - malformed output
 * - context overflow
 * - low confidence
 * - prompt injection / safety policy refusal
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
      String relativeDate,
      Long amountMinor,
      String secondaryEntity,
      boolean followUp,
      double confidence,
      boolean mutation,
      String refusalReason) {

    public static EveInterpretation of(Intent intent, String entityType, String spokenEntity) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, null, false, 1.0, false, null);
    }

    public static EveInterpretation of(Intent intent, String entityType, String spokenEntity, boolean followUp) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, null, followUp, 1.0, false, null);
    }

    public static EveInterpretation withDate(Intent intent, String relativeDate) {
      return new EveInterpretation(
          intent, "DATE", null, null, relativeDate, null, null, false, 1.0, false, null);
    }

    public static EveInterpretation withAmount(Intent intent, String entityType, String spokenEntity, Long amountMinor) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, amountMinor, null, false, 1.0, false, null);
    }

    public static EveInterpretation crossDomain(Intent intent, String entityType, String spokenEntity, String secondaryEntity) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, secondaryEntity, false, 1.0, false, null);
    }

    public static EveInterpretation vocabulary(String term, String canonicalType, String canonicalName) {
      return new EveInterpretation(
          Intent.REMEMBER_VOCABULARY, canonicalType, term, null, null, null, canonicalName, false, 1.0, false, null);
    }

    public static EveInterpretation disambiguate(String choice) {
      return new EveInterpretation(
          Intent.RESOLVE_DISAMBIGUATION, null, choice, null, null, null, null, true, 1.0, false, null);
    }

    public static EveInterpretation refused(String reason) {
      return new EveInterpretation(
          Intent.BLOCKED, null, null, null, null, null, null, false, 0.0, false, reason);
    }

    public static EveInterpretation proposePayment(String employeeSpoken, Long amountMinor, String payerAccount) {
      return new EveInterpretation(
          Intent.PROPOSE_EMPLOYEE_PAYMENT,
          "EMPLOYEE",
          employeeSpoken,
          null,
          null,
          amountMinor,
          payerAccount,
          false,
          1.0,
          true,
          null);
    }
  }

  enum Intent {
    // Governed Write Execution (Phase 3)
    PROPOSE_EMPLOYEE_PAYMENT,

    // Employee domain
    READ_EMPLOYEE_FINANCE,
    READ_EMPLOYEE_360,
    READ_EMPLOYEE_ASSIGNMENTS,

    // Production domain & cross-domain reads
    READ_PRODUCTION,
    READ_PRODUCTION_CLIENT,
    READ_PRODUCTION_CREW,
    READ_PRODUCTION_EQUIPMENT,
    READ_PRODUCTION_FINANCE,
    READ_PRODUCTION_TASKS,
    CHECK_PRODUCTION_MEMBER,

    // Work / Task domain
    READ_TASKS_SUMMARY,

    // Headquarters / Equipment domain
    READ_EQUIPMENT,
    READ_EQUIPMENT_INVENTORY,
    READ_EQUIPMENT_AVAILABILITY,

    // Temporal / Schedule
    READ_SCHEDULE_BY_DATE,

    // Conversational flow & memory
    RESOLVE_DISAMBIGUATION,
    REMEMBER_VOCABULARY,

    // General / System
    READ_SYSTEM_SUMMARY,
    UNKNOWN,
    BLOCKED
  }
}
