package com.saproduction.command.eve;

import com.saproduction.command.eve.capability.InformationNeed;
import com.saproduction.command.eve.system.EveInformationTopic;
import com.saproduction.command.eve.system.EveSystemConcept;
import java.util.List;
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

  record SemanticReference(
      String text,
      String referenceType,
      String entityType,
      String searchPhrase,
      String relationship) {
    public static SemanticReference of(String text, String entityType, String searchPhrase) {
      return new SemanticReference(text, "SEARCH_PHRASE", entityType, searchPhrase, null);
    }

    public static SemanticReference active(String text, String entityType) {
      return new SemanticReference(text, "ACTIVE_ENTITY", entityType, null, null);
    }

    public static SemanticReference candidate(String text, String searchPhrase) {
      return new SemanticReference(text, "CANDIDATE_SELECTION", null, searchPhrase, null);
    }
  }

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
      String refusalReason,
      List<SemanticReference> references,
      boolean continuation,
      boolean requiresClarification) {

    public EveInterpretation(
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
      this(
          intent,
          entityType,
          spokenEntity,
          resolvedEntityId,
          relativeDate,
          amountMinor,
          secondaryEntity,
          followUp,
          confidence,
          mutation,
          refusalReason,
          List.of(),
          followUp,
          false);
    }

    public static EveInterpretation of(Intent intent, String entityType, String spokenEntity) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, null, false, 1.0, false, null);
    }

    public static EveInterpretation of(Intent intent, String entityType, String spokenEntity, boolean followUp) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, null, followUp, 1.0, false, null);
    }

    public static EveInterpretation withReferences(
        Intent intent,
        String entityType,
        String spokenEntity,
        List<SemanticReference> references,
        boolean continuation) {
      return new EveInterpretation(
          intent, entityType, spokenEntity, null, null, null, null, continuation, 1.0, false, null,
          references != null ? references : List.of(), continuation, false);
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

    public InformationNeed toInformationNeed(String rawQuery) {
      InformationNeed.Operation op;
      EveInformationTopic top;
      EveSystemConcept concept;

      switch (intent) {
        case READ_EQUIPMENT_AVAILABILITY, READ_EQUIPMENT -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EQUIPMENT_STOCK;
          concept = EveSystemConcept.EQUIPMENT;
        }
        case READ_EQUIPMENT_INVENTORY -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EQUIPMENT_OVERVIEW;
          concept = EveSystemConcept.EQUIPMENT;
        }
        case READ_PRODUCTION_CLIENT -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.CLIENT_INFO;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_PRODUCTION_CREW -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.CREW_MEMBERS;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_PRODUCTION_EQUIPMENT -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EQUIPMENT_ASSIGNED;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_PRODUCTION_TASKS -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.PRODUCTION_TASKS;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_PRODUCTION -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.PRODUCTION_OVERVIEW;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_PRODUCTION_FINANCE -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.PRODUCTION_FINANCE;
          concept = EveSystemConcept.PRODUCTION;
        }
        case CHECK_PRODUCTION_MEMBER -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.MEMBER_PRESENCE;
          concept = EveSystemConcept.PRODUCTION;
        }
        case READ_EMPLOYEE_FINANCE -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EMPLOYEE_FINANCE;
          concept = EveSystemConcept.EMPLOYEE;
        }
        case READ_EMPLOYEE_ASSIGNMENTS -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EMPLOYEE_ASSIGNMENTS;
          concept = EveSystemConcept.EMPLOYEE;
        }
        case READ_EMPLOYEE_360 -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.EMPLOYEE_PROFILE;
          concept = EveSystemConcept.EMPLOYEE;
        }
        case READ_TASKS_SUMMARY -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.TASK_SUMMARY;
          concept = EveSystemConcept.WORK_TASK;
        }
        case READ_SCHEDULE_BY_DATE -> {
          op = InformationNeed.Operation.READ;
          top = EveInformationTopic.SCHEDULE_DATE;
          concept = EveSystemConcept.CALENDAR_DATE;
        }
        case PROPOSE_EMPLOYEE_PAYMENT -> {
          op = InformationNeed.Operation.PROPOSE_ACTION;
          top = EveInformationTopic.PAYMENT_PROPOSAL;
          concept = EveSystemConcept.FINANCE_RECORD;
        }
        case GREETING -> {
          op = InformationNeed.Operation.SYSTEM_ASSIST;
          top = EveInformationTopic.GREETING;
          concept = EveSystemConcept.SYSTEM;
        }
        case RESOLVE_DISAMBIGUATION -> {
          op = InformationNeed.Operation.SYSTEM_ASSIST;
          top = EveInformationTopic.DISAMBIGUATION_CHOICE;
          concept = EveSystemConcept.SYSTEM;
        }
        case REMEMBER_VOCABULARY -> {
          op = InformationNeed.Operation.SYSTEM_ASSIST;
          top = EveInformationTopic.VOCABULARY_DEFINITION;
          concept = EveSystemConcept.SYSTEM;
        }
        case READ_SYSTEM_SUMMARY -> {
          op = InformationNeed.Operation.SYSTEM_ASSIST;
          top = EveInformationTopic.SYSTEM_OVERVIEW;
          concept = EveSystemConcept.SYSTEM;
        }
        default -> {
          op = InformationNeed.Operation.OUT_OF_SCOPE;
          top = EveInformationTopic.UNSUPPORTED;
          concept = EveSystemConcept.SYSTEM;
        }
      }

      boolean pronoun = followUp || EveRetrievalRouter.isPronoun(spokenEntity);
      return new InformationNeed(
          rawQuery,
          op,
          top,
          concept,
          spokenEntity,
          secondaryEntity,
          relativeDate,
          amountMinor,
          secondaryEntity,
          pronoun,
          followUp,
          confidence,
          refusalReason);
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
    GREETING,
    GENERAL_QUERY,
    UNKNOWN,
    BLOCKED
  }
}
