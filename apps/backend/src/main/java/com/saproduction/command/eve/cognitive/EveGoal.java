package com.saproduction.command.eve.cognitive;

import java.util.List;
import java.util.Map;

/**
 * Semantic Intermediate Representation for EVE Cognitive Brain.
 * Captures user goals, operational intent, entity constraints, and completion criteria
 * without forcing queries into rigid sentence-specific intents.
 */
public record EveGoal(
    String goal,
    EveOperation operation,
    String entityType,
    List<String> entityReferences,
    Map<String, Object> constraints,
    String timeRange,
    EveTemporalReasoningService.DateRange dateRange,
    String requiredInformation,
    String completionCriteria,
    double confidence,
    boolean needsClarification,
    String clarificationPrompt) {

  public static EveGoal of(String goal, EveOperation operation, String entityType) {
    return new EveGoal(
        goal,
        operation,
        entityType,
        List.of(),
        Map.of(),
        null,
        null,
        "",
        "",
        1.0,
        false,
        null);
  }

  public static EveGoal clarification(String clarificationPrompt) {
    return new EveGoal(
        "CLARIFICATION_REQUIRED",
        EveOperation.LOOKUP,
        null,
        List.of(),
        Map.of(),
        null,
        null,
        "",
        "",
        0.5,
        true,
        clarificationPrompt);
  }
}
