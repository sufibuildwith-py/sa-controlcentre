package com.saproduction.command.eve.capability;

import com.saproduction.command.eve.cognitive.EveOperation;
import com.saproduction.command.eve.system.EveInformationTopic;
import com.saproduction.command.eve.system.EveSystemConcept;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Structured, model-extracted representation of the user's information need for Cognitive Runtime 2.0.
 * Replaces hardcoded phrase classifications with principled linguistic and operational intent.
 */
public record InformationNeed(
    String rawQuery,
    Operation operation,
    EveInformationTopic topic,
    EveSystemConcept targetConcept,
    String targetEntityPhrase,
    String secondaryEntityPhrase,
    String relativeDate,
    Long amountMinor,
    String payerAccount,
    boolean isPronoun,
    boolean isFollowUp,
    double confidence,
    String reasoningSummary,
    EveOperation cognitiveOperation,
    String requestedMetric,
    Map<String, Object> constraints) {

  public enum Operation {
    READ,
    PROPOSE_ACTION,
    SYSTEM_ASSIST,
    OUT_OF_SCOPE
  }

  public InformationNeed(
      String rawQuery,
      Operation operation,
      EveInformationTopic topic,
      EveSystemConcept targetConcept,
      String targetEntityPhrase,
      String secondaryEntityPhrase,
      String relativeDate,
      Long amountMinor,
      String payerAccount,
      boolean isPronoun,
      boolean isFollowUp,
      double confidence,
      String reasoningSummary) {
    this(
        rawQuery,
        operation,
        topic,
        targetConcept,
        targetEntityPhrase,
        secondaryEntityPhrase,
        relativeDate,
        amountMinor,
        payerAccount,
        isPronoun,
        isFollowUp,
        confidence,
        reasoningSummary,
        mapToCognitiveOperation(operation, topic),
        null,
        Collections.emptyMap());
  }

  public InformationNeed {
    Objects.requireNonNull(topic, "Information topic cannot be null");
    if (operation == null) {
      operation = Operation.READ;
    }
    if (cognitiveOperation == null) {
      cognitiveOperation = mapToCognitiveOperation(operation, topic);
    }
    if (constraints == null) {
      constraints = Collections.emptyMap();
    }
    if (confidence < 0.0 || confidence > 1.0) {
      confidence = 1.0;
    }
  }

  private static EveOperation mapToCognitiveOperation(Operation op, EveInformationTopic topic) {
    if (op == Operation.PROPOSE_ACTION) return EveOperation.EXECUTE;
    if (op == Operation.SYSTEM_ASSIST) return EveOperation.GENERAL_CONVERSATION;
    if (op == Operation.OUT_OF_SCOPE) return EveOperation.EXPLAIN;
    if (topic == EveInformationTopic.GREETING) return EveOperation.GENERAL_CONVERSATION;
    return EveOperation.READ;
  }

  public static InformationNeed of(EveInformationTopic topic, EveSystemConcept concept, String targetPhrase) {
    return new InformationNeed(
        null, Operation.READ, topic, concept, targetPhrase, null, null, null, null, false, false, 1.0, null);
  }

  public static InformationNeed pronoun(EveInformationTopic topic, EveSystemConcept concept, String pronoun) {
    return new InformationNeed(
        null, Operation.READ, topic, concept, pronoun, null, null, null, null, true, true, 1.0, "Resolved via pronoun antecedent");
  }

  public static InformationNeed proposal(EveInformationTopic topic, EveSystemConcept concept, String targetPhrase, Long amountMinor, String payerAccount) {
    return new InformationNeed(
        null, Operation.PROPOSE_ACTION, topic, concept, targetPhrase, null, null, amountMinor, payerAccount, false, false, 1.0, "Governed action proposal");
  }

  public static InformationNeed unsupported(String rawQuery, String reason) {
    return new InformationNeed(
        rawQuery, Operation.OUT_OF_SCOPE, EveInformationTopic.UNSUPPORTED, null, null, null, null, null, null, false, false, 0.0, reason);
  }

  public static InformationNeed greeting(String rawQuery) {
    return new InformationNeed(
        rawQuery, Operation.SYSTEM_ASSIST, EveInformationTopic.GREETING, EveSystemConcept.SYSTEM, null, null, null, null, null, false, false, 1.0, "Conversational greeting");
  }

  public static InformationNeed cognitive(
      String rawQuery,
      EveOperation operation,
      EveInformationTopic topic,
      EveSystemConcept concept,
      String targetEntity,
      String metric,
      Map<String, Object> constraints,
      double confidence) {
    return new InformationNeed(
        rawQuery,
        Operation.READ,
        topic != null ? topic : EveInformationTopic.PRODUCTION_OVERVIEW,
        concept,
        targetEntity,
        null,
        null,
        null,
        null,
        false,
        false,
        confidence,
        "Cognitive need: " + operation,
        operation,
        metric,
        constraints != null ? constraints : Collections.emptyMap());
  }
}
