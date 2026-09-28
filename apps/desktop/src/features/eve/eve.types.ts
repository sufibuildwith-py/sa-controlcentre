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
    | "SEARCHING"
    | "MATCHED"
    | "RETRIEVING"
    | "VALIDATING"
    | "PLANNING"
    | "COMPLETED"
    | "BLOCKED"
    | "FAILED";
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

export interface EveQueryResponse {
  sessionId: string;
  message: EveMessage;
  trace: EveTraceEvent[];
  context?: EveContext | null;
  status: "COMPLETED" | "CLARIFICATION_REQUIRED" | "NOT_FOUND" | "POLICY_BLOCKED" | "MODEL_FAILED" | "SYSTEM_UNAVAILABLE";
  candidates: EveCandidate[];
}

export interface EveSession {
  id: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  messages: EveMessage[];
}
