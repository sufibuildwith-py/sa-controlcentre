package com.saproduction.command.eve.cognitive;

import java.time.Instant;
import java.util.List;

/**
 * Structured reasoning summary contract.
 * Exposes a concise, user-facing operational reasoning trace.
 * Never exposes raw model thinking tokens or hidden scratchpad content.
 */
public record EveReasoningStep(
    int sequence,
    String stage,
    String summary,
    String status,
    Instant timestamp,
    String relatedTool,
    List<String> relatedEvidenceIds) {

  public static EveReasoningStep of(int sequence, String stage, String summary) {
    return new EveReasoningStep(
        sequence,
        stage,
        summary,
        "COMPLETED",
        Instant.now(),
        null,
        List.of());
  }

  public static EveReasoningStep tool(int sequence, String stage, String summary, String toolName) {
    return new EveReasoningStep(
        sequence,
        stage,
        summary,
        "COMPLETED",
        Instant.now(),
        toolName,
        List.of());
  }
}
