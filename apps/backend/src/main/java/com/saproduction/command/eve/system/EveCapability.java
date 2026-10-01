package com.saproduction.command.eve.system;

import java.util.Objects;

/**
 * Definition of an authoritative, bounded capability in SA Command.
 * Invariants:
 * - Every capability maps strictly to existing domain services and repositories.
 * - Capabilities are non-authoritative wrappers over canonical services.
 * - No capability may invent database records or bypass Phase 3 governance.
 */
public record EveCapability(
    String id,
    EveDomain domain,
    EveSystemConcept primaryConcept,
    EveInformationTopic topic,
    String description,
    SafetyClass safetyClass) {

  public enum SafetyClass {
    READ_ONLY,
    GOVERNED_WRITE_PROPOSAL
  }

  public EveCapability {
    Objects.requireNonNull(id, "Capability id cannot be null");
    Objects.requireNonNull(domain, "Domain cannot be null");
    Objects.requireNonNull(topic, "Topic cannot be null");
    Objects.requireNonNull(safetyClass, "Safety class cannot be null");
  }
}
