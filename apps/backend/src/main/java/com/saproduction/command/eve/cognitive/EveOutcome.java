package com.saproduction.command.eve.cognitive;

/**
 * Natural Failure and Outcome Taxonomy for EVE Cognitive Runtime 2.0.
 * Eliminates monolithic NOT_FOUND collapses and provides granular, principled status classifications.
 */
public enum EveOutcome {
  /** Request successfully resolved with authoritative evidence or deterministic computation. */
  COMPLETED,

  /** Ambiguity detected or missing antecedent requires user clarification. */
  CLARIFICATION_REQUIRED,

  /** Target domain entity genuinely does not exist in authoritative records. */
  NOT_FOUND,

  /** Request maps to an external capability (e.g. weather) that is not integrated in local environment. */
  UNSUPPORTED_CAPABILITY,

  /** Entity exists, but requested metric (e.g. speculative profit forecast) is not authoritatively recorded. */
  INSUFFICIENT_EVIDENCE,

  /** Input parameters or constraint values failed schema or domain validation. */
  VALIDATION_FAILED,

  /** Required local runtime or backing service is unreachable. */
  SYSTEM_UNAVAILABLE,

  /** Local model failed to parse or produce a valid cognitive response. */
  MODEL_FAILED,

  /** Action or query blocked by security, safety, or role-based boundary. */
  POLICY_BLOCKED,

  /** Cached or session state has expired relative to canonical system of record. */
  STALE_DATA,

  /** Governed mutation proposal formulated, requires explicit user confirmation token. */
  EXECUTION_REQUIRED
}
