package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.system.EveDomain;
import java.util.List;
import java.util.Set;

/**
 * Authoritative capability contract for EVE Cognitive Runtime 2.0 (Section 7).
 * Encapsulates domain boundaries, supported operations, input contracts, safety tier, and execution properties.
 */
public record EveCapabilityDefinition(
    String id,
    EveDomain domain,
    Set<EveOperation> supportedOperations,
    List<String> requiredInputs,
    List<String> optionalInputs,
    String authoritativeSource,
    boolean readOnly,
    String evidenceType,
    long timeoutMs,
    String description) {

  public static EveCapabilityDefinition general(
      String id,
      Set<EveOperation> ops,
      String source,
      String description) {
    return new EveCapabilityDefinition(
        id,
        EveDomain.GENERAL,
        ops,
        List.of("expression"),
        List.of(),
        source,
        true,
        "DETERMINISTIC_COMPUTATION",
        500L,
        description);
  }

  public static EveCapabilityDefinition saCommand(
      String id,
      EveDomain domain,
      Set<EveOperation> ops,
      List<String> requiredInputs,
      String authoritativeSource,
      String description) {
    return new EveCapabilityDefinition(
        id,
        domain,
        ops,
        requiredInputs != null ? requiredInputs : List.of(),
        List.of("context"),
        authoritativeSource,
        true,
        "CANONICAL_POSTGRES_EVIDENCE",
        3000L,
        description);
  }

  public static EveCapabilityDefinition external(
      String id,
      Set<EveOperation> ops,
      String source,
      String description) {
    return new EveCapabilityDefinition(
        id,
        EveDomain.EXTERNAL,
        ops,
        List.of("query"),
        List.of(),
        source,
        true,
        "EXTERNAL_SERVICE",
        2000L,
        description);
  }
}
