import { useMutation, useQuery } from "@tanstack/react-query";
import { useState, useRef, useEffect } from "react";
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Bot,
  CheckCircle2,
  Clock,
  CornerDownLeft,
  Database,
  Search,
  Sparkles,
  UserCheck,
} from "lucide-react";
import { WorkspaceHeader } from "../../components/layout/AppShell";
import {
  CardHeader,
  EmptyState,
  SABentoCard,
  SABentoGrid,
  SAButton,
  SkeletonCard,
  Tooltip,
} from "../../components/ui/sa";
import { eveApi } from "./eve.api";
import { EvePlanCard } from "./components/EvePlanCard";
import type {
  EveCandidate,
  EveContext,
  EveMessage,
  EvePlan,
  EveQueryResponse,
  EveTraceEvent,
  PlanExecutionResponse,
} from "./eve.types";

const SUGGESTIONS = [
  "How much does Sharma still need?",
  "Sharma ko 3000 de do",
  "Royal Wedding mein kaun kaun tha?",
  "Usme Sharma bhi tha?",
  "Aur uska equipment?",
  "Kaunsa task abhi open hai?",
  "Kal kaunsa event hai?",
  "Raju se mera matlab Raj Kumar hai",
];

export function EvePage() {
  const [prompt, setPrompt] = useState("");
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<EveMessage[]>([]);
  const [trace, setTrace] = useState<EveTraceEvent[]>([]);
  const [context, setContext] = useState<EveContext | null>(null);
  const [candidates, setCandidates] = useState<EveCandidate[]>([]);
  const [activePlan, setActivePlan] = useState<EvePlan | null>(null);
  const [status, setStatus] = useState<string>("READY");

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const sessionQuery = useQuery({
    queryKey: ["eve", "sessions"],
    queryFn: () => eveApi.listSessions(),
    staleTime: 30000,
  });

  const queryMutation = useMutation({
    mutationFn: (text: string) => eveApi.query(text, activeSessionId),
    onSuccess: (data: EveQueryResponse) => {
      setActiveSessionId(data.sessionId);
      setMessages((prev) => [...prev, data.message]);
      setTrace(data.trace);
      if (data.context) {
        setContext(data.context);
      }
      setCandidates(data.candidates ?? []);
      if (data.plan) {
        setActivePlan(data.plan);
      }
      setStatus(data.status);
      setPrompt("");
      sessionQuery.refetch();
    },
    onError: (err: any) => {
      setStatus("FAILED");
      setMessages((prev) => [
        ...prev,
        {
          id: `err-${Date.now()}`,
          sessionId: activeSessionId ?? "err",
          role: "ASSISTANT",
          content: `Unable to complete query: ${err.message ?? "Unknown error"}`,
          createdAt: new Date().toISOString(),
        },
      ]);
    },
  });

  const confirmMutation = useMutation({
    mutationFn: (plan: EvePlan) =>
      eveApi.confirmPlan(plan.planId, {
        sessionId: plan.sessionId,
        planId: plan.planId,
        planVersion: plan.version,
        planHash: plan.planHash,
        note: "Confirmed in UI",
      }),
    onSuccess: (data: PlanExecutionResponse) => {
      setMessages((prev) => [...prev, data.message]);
      setTrace(data.trace);
      setActivePlan((prev) => (prev ? { ...prev, status: "COMPLETED", actions: data.actions } : null));
      setStatus("COMPLETED");
      sessionQuery.refetch();
    },
    onError: (err: any) => {
      setStatus("FAILED");
      if (err.code === "STALE_PLAN" || err.message?.includes("stale") || err.message?.includes("Preconditions changed")) {
        setActivePlan((prev) => (prev ? { ...prev, status: "STALE_PLAN" } : null));
      }
      setMessages((prev) => [
        ...prev,
        {
          id: `err-${Date.now()}`,
          sessionId: activeSessionId ?? "err",
          role: "ASSISTANT",
          content: `Execution failed: ${err.message ?? "Unknown error"}`,
          createdAt: new Date().toISOString(),
        },
      ]);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (plan: EvePlan) =>
      eveApi.cancelPlan(plan.planId, {
        sessionId: plan.sessionId,
        planId: plan.planId,
        reason: "Cancelled by operator",
      }),
    onSuccess: (data: EvePlan) => {
      setActivePlan(data);
      setMessages((prev) => [
        ...prev,
        {
          id: `cancel-${Date.now()}`,
          sessionId: activeSessionId ?? "cancel",
          role: "ASSISTANT",
          content: "Plan was cancelled. No changes were made to system of record.",
          createdAt: new Date().toISOString(),
        },
      ]);
      sessionQuery.refetch();
    },
  });

  useEffect(() => {
    scrollRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages, trace]);

  const handleNewSession = async () => {
    try {
      const sess = await eveApi.createSession("New Command Session");
      setActiveSessionId(sess.id);
      setMessages([]);
      setTrace([]);
      setContext(null);
      setCandidates([]);
      setActivePlan(null);
      setStatus("READY");
      sessionQuery.refetch();
    } catch {
      setActiveSessionId(null);
      setMessages([]);
      setTrace([]);
      setContext(null);
      setCandidates([]);
      setActivePlan(null);
      setStatus("READY");
    }
  };

  const handleSelectSession = async (sessionId: string) => {
    if (!sessionId) {
      handleNewSession();
      return;
    }
    setActiveSessionId(sessionId);
    setActivePlan(null);
    try {
      const sess = await eveApi.getSession(sessionId);
      setMessages(sess.messages ?? []);
      setTrace([]);
      setCandidates([]);
      setStatus("READY");
      const plans = await eveApi.listPlansForSession(sessionId);
      const pendingPlan = plans.find((p) => p.status === "PROPOSED") || plans[0] || null;
      if (pendingPlan) {
        setActivePlan(pendingPlan);
      }
    } catch {
      // ignore
    }
  };

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!prompt.trim() || queryMutation.isPending) return;

    const userMsg: EveMessage = {
      id: `usr-${Date.now()}`,
      sessionId: activeSessionId ?? "temp",
      role: "USER",
      content: prompt.trim(),
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setCandidates([]);
    queryMutation.mutate(prompt.trim());
  };

  const handleSelectCandidate = (candidate: EveCandidate) => {
    const refinedPrompt = candidate.displayName;
    setPrompt(refinedPrompt);
    const userMsg: EveMessage = {
      id: `usr-${Date.now()}`,
      sessionId: activeSessionId ?? "temp",
      role: "USER",
      content: refinedPrompt,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setCandidates([]);
    queryMutation.mutate(refinedPrompt);
  };

  return (
    <div className="eve-workspace flex flex-col gap-6 p-6">
      <WorkspaceHeader
        title="Eve"
        subtitle="Operational Intelligence Layer · Grounded Multi-Domain Operations · Phase 2"
      />

      {/* Main Grid Console */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Composer & Conversation Stream (7 Cols) */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* Natural Language Composer */}
          <SABentoCard className="p-5 flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-emerald-500" />
                <span className="text-xs uppercase tracking-wider font-semibold text-[var(--text-3)]">
                  Command Composer
                </span>
                {activeSessionId && (
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[var(--surface-soft)] text-[var(--text-2)] border border-[var(--border)]">
                    Session {activeSessionId.slice(0, 8)}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                {sessionQuery.data && sessionQuery.data.length > 0 && (
                  <select
                    value={activeSessionId ?? ""}
                    onChange={(e) => handleSelectSession(e.target.value)}
                    aria-label="Select Session"
                    className="text-[11px] bg-[var(--surface)] border border-[var(--border)] rounded-lg px-2 py-1 text-[var(--text-1)] font-mono focus:outline-none"
                  >
                    <option value="">Active Session</option>
                    {sessionQuery.data.slice(0, 5).map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.title} ({s.id.slice(0, 8)})
                      </option>
                    ))}
                  </select>
                )}
                <button
                  type="button"
                  onClick={handleNewSession}
                  className="text-[11px] font-medium text-[var(--text-2)] hover:text-[var(--text-1)] px-2.5 py-1 rounded-lg bg-[var(--surface-soft)] hover:bg-[var(--surface-raised)] border border-[var(--border)] transition-colors"
                >
                  + New Session
                </button>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="flex flex-col gap-3">
              <div className="relative">
                <textarea
                  ref={textareaRef}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      handleSubmit();
                    }
                  }}
                  rows={3}
                  disabled={queryMutation.isPending}
                  placeholder="Ask Eve about employee finance, production status, tasks, or equipment (e.g. 'How much does Sharma still need?')..."
                  className="w-full rounded-xl bg-[var(--surface-soft)] border border-[var(--border)] p-3 text-sm text-[var(--text-1)] placeholder:text-[var(--text-3)] focus:outline-none focus:ring-1 focus:ring-[var(--border-strong)] focus:border-[var(--border-strong)] resize-none font-sans"
                />
                <div className="absolute right-3 bottom-3 flex items-center gap-2">
                  <SAButton
                    type="submit"
                    disabled={!prompt.trim() || queryMutation.isPending}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium"
                  >
                    <span>Send</span>
                    <CornerDownLeft className="w-3.5 h-3.5" />
                  </SAButton>
                </div>
              </div>

              {/* Suggestions */}
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <span className="text-xs text-[var(--text-3)]">Suggestions:</span>
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => {
                      setPrompt(s);
                      textareaRef.current?.focus();
                    }}
                    className="text-xs bg-[var(--surface-soft)] hover:bg-[var(--surface)] border border-[var(--border)] rounded-lg px-2.5 py-1 text-[var(--text-2)] hover:text-[var(--text-1)] transition-colors"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </form>
          </SABentoCard>

          {/* Conversation & Results Stream */}
          <SABentoCard className="p-5 flex flex-col gap-4 min-h-[350px]">
            <CardHeader
              eyebrow="Intelligence Stream"
              title="Operational Conversation"
            />

            {messages.length === 0 ? (
              <EmptyState
                title="Eve is ready"
                description="Type an operational question above or choose a suggestion to inspect canonical system state."
              />
            ) : (
              <div className="flex flex-col gap-4 overflow-y-auto max-h-[500px] pr-2">
                {messages.map((msg, index) => (
                  <div
                    key={msg.id}
                    className={`flex flex-col gap-1.5 p-4 rounded-xl border ${
                      msg.role === "USER"
                        ? "bg-[var(--surface-soft)] border-[var(--border)] self-end max-w-[85%]"
                        : "bg-[var(--surface)] border-[var(--border-soft)] self-start w-full shadow-sm"
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      {msg.role === "USER" ? (
                        <div className="w-5 h-5 rounded-full bg-[var(--surface-raised)] border border-[var(--border)] flex items-center justify-center text-[10px] font-bold text-[var(--text-1)]">
                          M
                        </div>
                      ) : (
                        <div className="w-5 h-5 rounded-full bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-[10px] font-bold text-emerald-600 dark:text-emerald-400">
                          E
                        </div>
                      )}
                      <span className="text-xs font-semibold text-[var(--text-2)]">
                        {msg.role === "USER" ? "Mamu" : "Eve"}
                      </span>
                      <span className="text-[10px] text-[var(--text-3)] font-mono ml-auto">
                        {new Date(msg.createdAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </div>

                    <div className="text-sm text-[var(--text-1)] whitespace-pre-wrap leading-relaxed">
                      {msg.content}
                    </div>

                    {/* Disambiguation candidate selection if returned */}
                    {msg.role === "ASSISTANT" && candidates.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-[var(--border)] flex flex-col gap-2">
                        <span className="text-xs font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                          <AlertCircle className="w-3.5 h-3.5" />
                          Disambiguation Candidates:
                        </span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-1">
                          {candidates.map((cand) => (
                            <button
                              key={cand.id}
                              type="button"
                              onClick={() => handleSelectCandidate(cand)}
                              className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-soft)] hover:bg-[var(--surface-raised)] border border-[var(--border)] text-left transition-colors"
                            >
                              <div className="flex flex-col">
                                <span className="text-sm font-medium text-[var(--text-1)]">
                                  {cand.displayName}
                                </span>
                                <span className="text-xs text-[var(--text-3)] font-mono">
                                  {cand.code} · {cand.detail}
                                </span>
                              </div>
                              <ArrowRight className="w-4 h-4 text-[var(--text-3)]" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Active EvePlan Card if present on latest assistant message */}
                    {msg.role === "ASSISTANT" && activePlan && index === messages.length - 1 && (
                      <div className="mt-4">
                        <EvePlanCard
                          plan={activePlan}
                          onConfirm={(p) => confirmMutation.mutate(p)}
                          onCancel={(p) => cancelMutation.mutate(p)}
                          isConfirming={confirmMutation.isPending}
                          isCancelling={cancelMutation.isPending}
                        />
                      </div>
                    )}
                  </div>
                ))}
                {queryMutation.isPending && (
                  <div className="p-4 rounded-xl border border-[var(--border)] bg-[var(--surface-soft)] flex items-center gap-3">
                    <div className="w-4 h-4 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                    <span className="text-xs text-[var(--text-2)] font-mono">
                      Querying PostgreSQL canonical records...
                    </span>
                  </div>
                )}
                <div ref={scrollRef} />
              </div>
            )}
          </SABentoCard>
        </div>

        {/* Right Column: Activity Trace & System Context Panel (5 Cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          {/* Truthful Observable Activity Trace */}
          <SABentoCard className="p-5 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-4 h-4 text-sky-500" />
                <h3 className="text-xs uppercase tracking-wider font-semibold text-[var(--text-3)]">
                  Activity Trace
                </h3>
              </div>
              <span className="text-[10px] text-[var(--text-3)] font-mono">
                Truthful Lifecycle Events
              </span>
            </div>

            {trace.length === 0 ? (
              <p className="text-xs text-[var(--text-3)] italic py-4">
                No activity yet. Execute a query to view live deterministic trace events.
              </p>
            ) : (
              <div className="flex flex-col gap-2.5 max-h-[300px] overflow-y-auto pr-1">
                {trace.map((t) => (
                  <div
                    key={t.id}
                    className="flex items-start gap-2.5 p-2 rounded-lg bg-[var(--surface-soft)] border border-[var(--border-soft)] text-xs"
                  >
                    <span className="text-[10px] font-mono text-[var(--text-3)] px-1.5 py-0.5 rounded bg-[var(--surface)] border border-[var(--border)]">
                      #{t.seq}
                    </span>
                    <div className="flex flex-col flex-1 gap-0.5">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-[var(--text-1)]">
                          {t.label}
                        </span>
                        <span
                          className={`text-[9px] font-bold px-1.5 py-0.2 rounded font-mono ${
                            t.status === "OK"
                              ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20"
                              : t.status === "BLOCKED"
                              ? "bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20"
                              : "bg-red-500/10 text-red-600 dark:text-red-400 border border-red-500/20"
                          }`}
                        >
                          {t.eventType}
                        </span>
                      </div>
                      {t.detail && (
                        <span className="text-[var(--text-2)] text-[11px] leading-tight font-sans">
                          {t.detail}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </SABentoCard>

          {/* Grounded Evidence & System Context Panel */}
          <SABentoCard className="p-5 flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4 text-emerald-500" />
                <h3 className="text-xs uppercase tracking-wider font-semibold text-[var(--text-3)]">
                  System Context & Evidence
                </h3>
              </div>
              <span className="text-[10px] text-[var(--text-3)] font-mono">
                PostgreSQL Authoritative
              </span>
            </div>

            {context ? (
              <div className="flex flex-col gap-4">
                {/* Referenced Entity */}
                {context.referencedEntities.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[11px] font-semibold text-[var(--text-2)]">
                      Resolved Entity
                    </span>
                    {context.referencedEntities.map((ent) => (
                      <div
                        key={ent.id}
                        className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-soft)] border border-[var(--border)]"
                      >
                        <div className="flex items-center gap-2">
                          <UserCheck className="w-4 h-4 text-emerald-500" />
                          <span className="text-sm font-medium text-[var(--text-1)]">
                            {ent.name}
                          </span>
                        </div>
                        <span className="text-xs font-mono text-[var(--text-2)] px-2 py-0.5 rounded bg-[var(--surface)] border border-[var(--border)]">
                          {ent.code}
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Evidence Metrics */}
                {context.evidence.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[11px] font-semibold text-[var(--text-2)]">
                      Authoritative Ledger Evidence
                    </span>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {context.evidence.map((ev, idx) => (
                        <div
                          key={idx}
                          className="flex flex-col p-2.5 rounded-lg bg-[var(--surface-soft)] border border-[var(--border)]"
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] font-mono text-[var(--text-3)] uppercase">
                              {ev.label}
                            </span>
                            <span
                              className={`text-[9px] font-mono px-1.5 py-0.5 rounded border ${
                                ev.domain === "FINANCE"
                                  ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20"
                                  : ev.domain === "PRODUCTION"
                                  ? "bg-sky-500/10 text-sky-600 dark:text-sky-400 border-sky-500/20"
                                  : ev.domain === "EQUIPMENT" || ev.domain === "HEADQUARTERS"
                                  ? "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20"
                                  : ev.domain === "WORK" || ev.domain === "TASK"
                                  ? "bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20"
                                  : ev.domain === "CALENDAR"
                                  ? "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20"
                                  : "bg-[var(--surface)] text-[var(--text-2)] border-[var(--border)]"
                              }`}
                            >
                              {ev.domain}
                            </span>
                          </div>
                          <span className="text-sm font-semibold text-[var(--text-1)] mt-1">
                            {ev.value}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Memory & Knowledge Indicators */}
                {context.memoryHints && context.memoryHints.length > 0 && (
                  <div className="flex flex-col gap-1 p-2 rounded-lg bg-[var(--surface-soft)] border border-[var(--border)] text-[11px]">
                    <span className="font-semibold text-[var(--text-2)]">Memory Hint Active</span>
                    {context.memoryHints.map((m) => (
                      <span key={m.id} className="text-[var(--text-1)] font-mono">
                        "{m.term}" → {m.canonicalName ?? m.canonicalType}
                      </span>
                    ))}
                  </div>
                )}

                {/* Context Metadata */}
                <div className="pt-2 border-t border-[var(--border)] flex items-center justify-between text-[10px] text-[var(--text-3)] font-mono">
                  <span>Operator: {context.owner}</span>
                  <span>Timezone: {context.timezone}</span>
                </div>
              </div>
            ) : (
              <p className="text-xs text-[var(--text-3)] italic py-2">
                Context envelope will populate automatically with verified PostgreSQL records when a query resolves.
              </p>
            )}
          </SABentoCard>
        </div>
      </div>
    </div>
  );
}
