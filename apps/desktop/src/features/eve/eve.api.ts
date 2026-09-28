import { api, json } from "../../lib/api";
import type { EveMemory, EveQueryResponse, EveSession } from "./eve.types";

export const eveApi = {
  query: (prompt: string, sessionId?: string | null): Promise<EveQueryResponse> =>
    api<EveQueryResponse>("/eve/query", {
      method: "POST",
      ...json({ prompt, sessionId: sessionId ?? null }),
    }),

  listSessions: (): Promise<EveSession[]> =>
    api<EveSession[]>("/eve/sessions"),

  getSession: (id: string): Promise<EveSession> =>
    api<EveSession>(`/eve/sessions/${id}`),

  createSession: (title?: string): Promise<EveSession> =>
    api<EveSession>("/eve/sessions", {
      method: "POST",
      ...json({ title: title ?? null }),
    }),

  listMemories: (): Promise<EveMemory[]> =>
    api<EveMemory[]>("/eve/memory"),

  remember: (
    term: string,
    memoryType: string = "VOCABULARY",
    canonicalType: string = "EMPLOYEE",
    canonicalId?: string | null,
    canonicalName?: string | null,
  ): Promise<EveMemory> =>
    api<EveMemory>("/eve/memory", {
      method: "POST",
      ...json({ memoryType, term, canonicalType, canonicalId, canonicalName }),
    }),

  deleteMemory: (id: string): Promise<boolean> =>
    api<boolean>(`/eve/memory/${id}`, {
      method: "DELETE",
    }),
};
