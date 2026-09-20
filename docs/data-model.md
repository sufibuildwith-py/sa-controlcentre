# V1 data model

All identifiers are UUIDs and all timestamps that cross a time-zone boundary are `TIMESTAMPTZ`. Production dates and wall-clock times remain separate because they describe a local event day in `Asia/Kolkata`.

- `productions` owns lifecycle, priority, progress, venue and local schedule.
- `production_members` links an employee once per production and records role, response state and audited conflict override evidence.
- `calendar_events` is the canonical schedule projection. Unique foreign keys link one event to a production, meeting or task deadline.
- `event_attendees` supplies cross-domain scheduling membership and response state.
- `tasks` optionally link to a production or originating meeting. `task_updates` is append-only progress history.
- `meetings`, `meeting_attendees` and `meeting_notes` own meeting content. Meeting action items are normal `tasks`; there is no parallel action-item model.
- `payroll_periods` owns the period state. `payroll_items` stores employee-name, currency and base-salary snapshots. `payroll_adjustments` stores typed integer-minor-unit changes with a reason and creator. `payroll_payments` is the append-only employee payment ledger with positive integer-minor-unit amounts, payment time, method, reference, note and recorder.
- `notification_rules` owns enablement, delay and template selection for each supported domain event.
- `outbox_events` durably records domain events, claim state, attempts, availability and bounded-retry errors.
- `outbound_messages` owns recipient, category, template variables, delivery/response state, timestamps and the unique idempotency key.
- `outbound_message_events` is the append-only communication timeline. `webhook_receipts` uniquely deduplicates provider callbacks.

Database checks enforce valid enums, ordered intervals, progress bounds, non-negative retry/delay values and unique period/assignment/link/message/receipt invariants. Query indexes cover production status/date/priority, attendee lookup, task assignee/due/status/production, meeting time, calendar interval/link, payroll access, outbox/message claims, recipient timelines, provider IDs and related-domain lookups.
