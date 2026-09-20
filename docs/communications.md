# Communications architecture

Business services persist their mutation and a domain event in the same transaction. A PostgreSQL outbox worker safely claims events with `FOR UPDATE SKIP LOCKED`, evaluates owner-controlled notification rules, and creates idempotent outbound messages. A second claim stage sends through the configured `MessagingProvider`.

`MESSAGING_PROVIDER=console` is the offline/demo default. It creates deterministic local provider IDs and never needs network access. `MESSAGING_PROVIDER=meta` enables the backend-only WhatsApp Cloud API adapter. Meta credentials and webhook secrets must remain server environment variables and must never use a `VITE_` prefix.

Outbound lifecycle: `QUEUED → SENDING → SENT → DELIVERED → READ`, with `FAILED` as the bounded-retry terminal state. A stale `SENDING` record is failed as an unknown outcome instead of silently duplicating an external send. The unique idempotency key combines domain event, employee, channel and template.

Both Meta callbacks and the demo simulator call `InboundMessagingService`. Provider receipt IDs are unique, so duplicate callbacks do not repeat business updates or audits. Production replies update crew assignments; meeting replies update attendee state.

Webhook configuration uses `META_WHATSAPP_VERIFY_TOKEN` for the GET challenge and `META_WHATSAPP_APP_SECRET` for `X-Hub-Signature-256`. Production mode rejects unsigned callbacks.
