# SA Command architecture

The Tauri desktop hosts a React client. Production authentication is an opaque server-side session design: Tauri stores the raw token in the OS credential vault, sends it through `Authorization`, and Spring resolves only its SHA-256 digest from PostgreSQL. PostgreSQL owns users, sessions, employees, attendance, leave, productions, scheduling, tasks, meetings, payroll and audit records.

TanStack Query is the only client owner of server state. Zustand stores shell-only state such as the theme and overlay visibility. Backend services own lifecycle, overlap, progress and payroll rules; React renders API DTOs and never acts as the source of business truth.

Calendar is the canonical scheduling surface. A production owns one linked production event, a meeting owns one linked meeting event, and a task with a due time owns one linked deadline event. Source-domain updates synchronize those records transactionally. Crew and meeting participants reuse `event_attendees`, so one overlap query covers all commitments. Adjacent intervals do not conflict; any positive overlap does.

The Command dashboard is a purpose-built aggregate endpoint. It returns attendance, today's schedule, active productions, workload, payroll, communications metrics and actionable attention items in one request.

Business services emit domain events into PostgreSQL in the same transaction as their mutations. The outbox worker claims events with `FOR UPDATE SKIP LOCKED`, evaluates persisted notification rules, and creates uniquely keyed outbound messages. A second worker delivers those messages through the active `MessagingProvider`. `ConsoleMessagingProvider` keeps demo mode independent from Meta; `MetaWhatsAppProvider` owns the production HTTP boundary. Both real webhooks and the demo simulator pass through `InboundMessagingService`, closing production-assignment and meeting-response loops through the same audited business services.

Communications history is server-paginated with SQL aggregates and batched employee resolution. Payroll lists return lightweight period summaries, period detail returns employee summaries, and payment/adjustment history loads only when an employee ledger drawer opens.
