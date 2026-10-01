package com.saproduction.command.eve;

import java.util.List;

/**
 * Natural language response composer interface.
 * Formulates natural language explanations from grounded deterministic facts and retrieval context.
 * Kept separate from EveModelProvider to preserve the single-method interpret invariant.
 */
public interface EveResponseComposer {

  String composeResponse(EveResponseCompositionRequest request);

  record EveResponseCompositionRequest(
      String userPrompt,
      String sessionContext,
      EveModelProvider.Intent intent,
      String deterministicAnswer,
      List<EveDtos.EntityReference> referencedEntities,
      List<EveDtos.EvidenceItem> evidence,
      List<String> knowledgeSnippets) {}
}
