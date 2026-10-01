package com.saproduction.command.eve.cognitive;

/**
 * Analytical and cognitive operations supported by EVE.
 * Enables general multi-record operations rather than simple single-entity retrieval.
 */
public enum EveOperation {
  READ,
  COUNT,
  FILTER,
  COMPARE,
  RANK,
  AGGREGATE,
  CALCULATE,
  EXPLAIN,
  SUMMARIZE,
  RECOMMEND,
  DECISION_SUPPORT,
  GENERAL_CONVERSATION,
  EXECUTE,
  CLARIFY,

  // Auxiliary / Backwards-compatible operational aliases
  LIST,
  SUM,
  GROUP,
  CHECK,
  FIND,
  LOOKUP,
  EXISTS,
  PROPOSE_ACTION
}
