# WhatsApp operations

SA Command keeps messaging behind the backend `MessagingProvider` boundary. Demo and offline development use `MESSAGING_PROVIDER=console`; production uses `MESSAGING_PROVIDER=meta`. Meta credentials are backend-only environment variables documented in `.env.example` and must never use a `VITE_` prefix.

## Production configuration

Set `APP_MODE=production`, `MESSAGING_PROVIDER=meta`, the Graph API version, phone-number ID, access token, app secret and webhook verify token. Configure Meta to call:

- `GET /api/v1/integrations/whatsapp/webhook` for challenge verification.
- `POST /api/v1/integrations/whatsapp/webhook` for message status and interactive replies.

Production POST callbacks require a valid `X-Hub-Signature-256` HMAC using `META_WHATSAPP_APP_SECRET`. The adapter sends approved templates to the configured phone-number ID `/messages` endpoint with bearer authentication. Provider `wamid` values are persisted for status correlation.

## Delivery and replies

The persisted lifecycle is `QUEUED → SENDING → SENT → DELIVERED → READ`, with bounded retry and terminal `FAILED`. Unique idempotency keys prevent duplicate sends. Unique provider receipt IDs prevent duplicate callbacks from repeating an audit or domain update.

Interactive production replies support confirm/decline and meeting replies support accept/decline. Meta webhooks and the demo-only simulator both call `InboundMessagingService`; neither bypasses the business domain.

See [communications.md](communications.md) for the complete event/outbox/provider architecture.
