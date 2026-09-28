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
import type {
  EveCandidate,
  EveContext,
  EveMessage,
  EveQueryResponse,
  EveTraceEvent,
} from "./eve.types";

const SUGGESTIONS = [
  "How much does Sharma still need?",
  "Check Sharma payment status",
  "Amit Sharma outstanding balance",
  "Overview of current operations",
];

export function EvePage() {
  const [prompt, setPrompt] = useState("");
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<EveMessage[]>([]);
  const [trace, setTrace] = useState<EveTraceEvent[]>([]);
  const [context, setContext] = useState<EveContext | null>(null);
  const [candidates, setCandidates] = useState<EveCandidate[]>([]);
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
      setStatus(data.status);
      setPrompt("");
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

  useEffect(() => {
    scrollRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages, trace]);

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
    const refinedPrompt = `How much does ${candidate.displayName} (${candidate.code}) still need?`;
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
        subtitle="Operational Intelligence Layer · Grounded System Operations"
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
                <span className="text-xs uppercase tracking-wider font-semibold text-neutral-400">
                  Command Composer
                </span>
              </div>
              <span className="text-xs text-neutral-500 font-mono">
                Canonical Read Grounding · Phase 1
              </span>
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
                  className="w-full rounded-xl bg-neutral-900/60 border border-neutral-800 p-3 text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-1 focus:ring-neutral-600 focus:border-neutral-600 resize-none font-sans"
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
                <span className="text-xs text-neutral-500">Suggestions:</span>
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => {
                      setPrompt(s);
                      textareaRef.current?.focus();
                    }}
                    className="text-xs bg-neutral-800/60 hover:bg-neutral-800 border border-neutral-700/50 rounded-lg px-2.5 py-1 text-neutral-300 transition-colors"
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
                {messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex flex-col gap-1.5 p-4 rounded-xl border ${
                      msg.role === "USER"
                        ? "bg-neutral-800/40 border-neutral-700/50 self-end max-w-[85%]"
                        : "bg-neutral-900/80 border-neutral-800 self-start w-full"
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      {msg.role === "USER" ? (
                        <div className="w-5 h-5 rounded-full bg-neutral-700 flex items-center justify-center text-[10px] font-bold text-neutral-300">
                          M
                        </div>
                      ) : (
                        <div className="w-5 h-5 rounded-full bg-emerald-950 border border-emerald-700 flex items-center justify-center text-[10px] font-bold text-emerald-400">
                          E
                        </div>
                      )}
                      <span className="text-xs font-semibold text-neutral-400">
                        {msg.role === "USER" ? "Mamu" : "Eve"}
                      </span>
                      <span className="text-[10px] text-neutral-500 font-mono ml-auto">
                        {new Date(msg.createdAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </div>

                    <div className="text-sm text-neutral-200 whitespace-pre-wrap leading-relaxed">
                      {msg.content}
                    </div>

                    {/* Disambiguation candidate selection if returned */}
                    {msg.role === "ASSISTANT" && candidates.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-neutral-800 flex flex-col gap-2">
                        <span className="text-xs font-semibold text-amber-400 flex items-center gap-1.5">
                          <AlertCircle className="w-3.5 h-3.5" />
                          Disambiguation Candidates:
                        </span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-1">
                          {candidates.map((cand) => (
                            <button
                              key={cand.id}
                              type="button"
                              onClick={() => handleSelectCandidate(cand)}
                              className="flex items-center justify-between p-2.5 rounded-lg bg-neutral-800/80 hover:bg-neutral-750 border border-neutral-700/70 text-left transition-colors"
                            >
                              <div className="flex flex-col">
                                <span className="text-sm font-medium text-neutral-200">
                                  {cand.displayName}
                                </span>
                                <span className="text-xs text-neutral-400 font-mono">
                                  {cand.code} · {cand.detail}
                                </span>
                              </div>
                              <ArrowRight className="w-4 h-4 text-neutral-400" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
                {queryMutation.isPending && (
                  <div className="p-4 rounded-xl border border-neutral-800 bg-neutral-900/50 flex items-center gap-3">
                    <div className="w-4 h-4 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                    <span className="text-xs text-neutral-400 font-mono">
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
                <Activity className="w-4 h-4 text-sky-400" />
                <h3 className="text-xs uppercase tracking-wider font-semibold text-neutral-400">
                  Activity Trace
                </h3>
              </div>
              <span className="text-[10px] text-neutral-500 font-mono">
                Truthful Lifecycle Events
              </span>
            </div>

            {trace.length === 0 ? (
              <p className="text-xs text-neutral-500 italic py-4">
                No activity yet. Execute a query to view live deterministic trace events.
              </p>
            ) : (
              <div className="flex flex-col gap-2.5 max-h-[300px] overflow-y-auto pr-1">
                {trace.map((t) => (
                  <div
                    key={t.id}
                    className="flex items-start gap-2.5 p-2 rounded-lg bg-neutral-900/70 border border-neutral-800/80 text-xs"
                  >
                    <span className="text-[10px] font-mono text-neutral-500 px-1.5 py-0.5 rounded bg-neutral-800 border border-neutral-700">
                      #{t.seq}
                    </span>
                    <div className="flex flex-col flex-1 gap-0.5">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-neutral-300">
                          {t.label}
                        </span>
                        <span
                          className={`text-[9px] font-bold px-1.5 py-0.2 rounded font-mono ${
                            t.status === "OK"
                              ? "bg-emerald-950/80 text-emerald-400 border border-emerald-800"
                              : t.status === "BLOCKED"
                              ? "bg-amber-950/80 text-amber-400 border border-amber-800"
                              : "bg-red-950/80 text-red-400 border border-red-800"
                          }`}
                        >
                          {t.eventType}
                        </span>
                      </div>
                      {t.detail && (
                        <span className="text-neutral-400 text-[11px] leading-tight font-sans">
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
                <Database className="w-4 h-4 text-emerald-400" />
                <h3 className="text-xs uppercase tracking-wider font-semibold text-neutral-400">
                  System Context & Evidence
                </h3>
              </div>
              <span className="text-[10px] text-neutral-500 font-mono">
                PostgreSQL Authoritative
              </span>
            </div>

            {context ? (
              <div className="flex flex-col gap-4">
                {/* Referenced Entity */}
                {context.referencedEntities.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[11px] font-semibold text-neutral-400">
                      Resolved Entity
                    </span>
                    {context.referencedEntities.map((ent) => (
                      <div
                        key={ent.id}
                        className="flex items-center justify-between p-2.5 rounded-lg bg-neutral-900 border border-neutral-800"
                      >
                        <div className="flex items-center gap-2">
                          <UserCheck className="w-4 h-4 text-emerald-400" />
                          <span className="text-sm font-medium text-neutral-200">
                            {ent.name}
                          </span>
                        </div>
                        <span className="text-xs font-mono text-neutral-400 px-2 py-0.5 rounded bg-neutral-800">
                          {ent.code}
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Evidence Metrics */}
                {context.evidence.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[11px] font-semibold text-neutral-400">
                      Authoritative Ledger Evidence
                    </span>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {context.evidence.map((ev, idx) => (
                        <div
                          key={idx}
                          className="flex flex-col p-2.5 rounded-lg bg-neutral-900 border border-neutral-800"
                        >
                          <span className="text-[10px] font-mono text-neutral-500 uppercase">
                            {ev.label}
                          </span>
                          <span className="text-sm font-semibold text-neutral-200 mt-0.5">
                            {ev.value}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Memory & Knowledge Indicators */}
                {context.memoryHints && context.memoryHints.length > 0 && (
                  <div className="flex flex-col gap-1 p-2 rounded-lg bg-neutral-900/60 border border-neutral-800 text-[11px]">
                    <span className="font-semibold text-neutral-400">Memory Hint Active</span>
                    {context.memoryHints.map((m) => (
                      <span key={m.id} className="text-neutral-300 font-mono">
                        "{m.term}" → {m.canonicalName ?? m.canonicalType}
                      </span>
                    ))}
                  </div>
                )}

                {/* Context Metadata */}
                <div className="pt-2 border-t border-neutral-800 flex items-center justify-between text-[10px] text-neutral-500 font-mono">
                  <span>Operator: {context.owner}</span>
                  <span>Timezone: {context.timezone}</span>
                </div>
              </div>
            ) : (
              <p className="text-xs text-neutral-500 italic py-2">
                Context envelope will populate automatically with verified PostgreSQL records when a query resolves.
              </p>
            )}
          </SABentoCard>
        </div>
      </div>
    </div>
  );
}
