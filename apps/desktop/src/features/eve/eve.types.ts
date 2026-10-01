export interface EveMessage {
  id: string;
  sessionId: string;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  createdAt: string;
}

export interface EveTraceEvent {
  id: string;
  sessionId: string;
  messageId?: string | null;
  seq: number;
  eventType:
    | "STARTED"
    | "INTERPRETING"
    | "RESOLVING"
    | "ROUTING"
    | "RETRIEVING"
    | "ASSEMBLING_CONTEXT"
    | "SEARCHING"
    | "MATCHED"
    | "VALIDATING"
    | "PLANNING"
    | "WAITING_CONFIRMATION"
    | "REVALIDATING"
    | "EXECUTING"
    | "VERIFYING"
    | "COMPLETED"
    | "BLOCKED"
    | "STALE_PLAN"
    | "FAILED"
    | "CANCELLED";
  status: "OK" | "WARN" | "BLOCKED" | "ERROR";
  label: string;
  detail?: string | null;
  createdAt: string;
}

export interface EveCandidate {
  id: string;
  type: string;
  displayName: string;
  code: string;
  detail: string;
}

export interface EveEntityReference {
  id: string;
  type: string;
  name: string;
  code: string;
}

export interface EveEvidenceItem {
  domain: string;
  label: string;
  value: string;
}

export interface EveMemory {
  id: string;
  memoryType: string;
  term: string;
  canonicalType: string;
  canonicalId?: string | null;
  canonicalName?: string | null;
  confidence: number;
  source: string;
  createdAt: string;
  updatedAt: string;
}

export interface EveContext {
  owner: string;
  timezone: string;
  date: string;
  referencedEntities: EveEntityReference[];
  evidence: EveEvidenceItem[];
  knowledgeSnippets?: string[];
  memoryHints?: EveMemory[];
}

export interface EveVerificationResult {
  status: string;
  ruleName: string;
  expectedState: string;
  actualState: string;
  verifiedAt: string;
  notes?: string;
}

export interface EvePlanAction {
  actionId: string;
  seq: number;
  domain: string;
  commandType: string;
  targetEntityId?: string;
  targetEntityName?: string;
  parameters: Record<string, unknown>;
  estimatedEffect?: string;
  requiredPermission?: string;
  status: "PENDING" | "EXECUTED" | "VERIFIED" | "FAILED" | "VERIFICATION_FAILED";
  canonicalRecordId?: string;
  executionResult?: Record<string, unknown>;
  verificationResult?: EveVerificationResult;
}

export interface EvePlan {
  planId: string;
  sessionId: string;
  intent: string;
  summary: string;
  riskTier: "READ" | "SAFE_OPERATION" | "FINANCIAL_WRITE" | "BLOCKED";
  confirmationRequired: boolean;
  planHash: string;
  version: number;
  status: "PROPOSED" | "CONFIRMED" | "EXECUTING" | "COMPLETED" | "FAILED" | "CANCELLED" | "STALE_PLAN";
  actions: EvePlanAction[];
}

export interface ConfirmPlanRequest {
  sessionId: string;
  planId: string;
  planVersion: number;
  planHash: string;
  note?: string;
}

export interface CancelPlanRequest {
  sessionId: string;
  planId: string;
  reason?: string;
}

export interface PlanExecutionResponse {
  planId: string;
  sessionId: string;
  status: string;
  summary: string;
  trace: EveTraceEvent[];
  actions: EvePlanAction[];
  message: EveMessage;
}

export interface EveReasoningStep {
  sequence: number;
  stage: string;
  summary: string;
  status: "IN_PROGRESS" | "COMPLETED" | "FAILED" | "SKIPPED" | string;
  timestamp: string;
  relatedTool?: string;
  relatedEvidenceIds?: string[];
}

export interface EveQueryResponse {
  sessionId: string;
  message: EveMessage;
  trace: EveTraceEvent[];
  context?: EveContext | null;
  status:
    | "COMPLETED"
    | "WAITING_CONFIRMATION"
    | "CLARIFICATION_REQUIRED"
    | "NOT_FOUND"
    | "POLICY_BLOCKED"
    | "MODEL_FAILED"
    | "SYSTEM_UNAVAILABLE"
    | "BLOCKED"
    | "STALE_PLAN";
  candidates: EveCandidate[];
  plan?: EvePlan | null;
  reasoning?: EveReasoningStep[];
}

export interface EveSession {
  id: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  messages: EveMessage[];
}

export interface SuggestionEvidenceItem {
  domain: string;
  entityType: string;
  entityId: string;
  label: string;
  value: string;
  observedAt: string;
}

export interface EveSuggestion {
  id: string;
  type: string;
  status: "ACTIVE" | "DISMISSED" | "RESOLVED" | "EXPIRED" | "SUPERSEDED" | "STALE";
  priority: "LOW" | "MEDIUM" | "HIGH";
  title: string;
  summary: string;
  sourceSignalId?: string;
  targetDomain: string;
  canonicalEntityType: string;
  canonicalEntityId: string;
  canonicalEntityName?: string;
  evidence: SuggestionEvidenceItem[];
  dedupeKey: string;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
  dismissedAt?: string;
  resolvedAt?: string;
  dismissedBy?: string;
  metadata?: Record<string, unknown>;
}

export interface EveSignal {
  id: string;
  signalType: string;
  sourceDomain: string;
  canonicalEntityType: string;
  canonicalEntityId: string;
  canonicalVersion?: number;
  actorId?: string;
  correlationId?: string;
  metadata?: Record<string, unknown>;
  status: string;
  occurredAt: string;
  processedAt?: string;
  failureReason?: string;
}

export interface EveStatusView {
  modelProvider: string;
  status: "INITIALIZING" | "READY" | "UNAVAILABLE" | string;
  modelName: string;
  modelVersion: string;
  details?: string;
}

