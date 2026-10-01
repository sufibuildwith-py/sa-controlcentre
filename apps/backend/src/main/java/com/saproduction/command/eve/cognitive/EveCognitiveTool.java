package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.EveDtos;
import java.util.List;
import java.util.Map;

/**
 * Bounded read-only cognitive tool contract.
 * Every tool maps to an authoritative, existing SA Command canonical service.
 */
public interface EveCognitiveTool {

  String name();

  String description();

  Map<String, String> parameterSchema();

  EveToolResult execute(Map<String, Object> parameters);

  record EveToolResult(
      boolean success,
      String summary,
      List<EveDtos.EvidenceItem> evidence,
      List<EveDtos.EntityReference> referencedEntities,
      Object data) {

    public static EveToolResult ok(
        String summary,
        List<EveDtos.EvidenceItem> evidence,
        List<EveDtos.EntityReference> entities,
        Object data) {
      return new EveToolResult(true, summary, evidence != null ? evidence : List.of(), entities != null ? entities : List.of(), data);
    }

    public static EveToolResult fail(String errorMessage) {
      return new EveToolResult(false, errorMessage, List.of(), List.of(), null);
    }
  }
}
