# Security

Owner authentication uses a server-side JDBC session and an HttpOnly, SameSite cookie. The desktop client sends credentials with API requests but does not store session secrets or provider credentials in local storage or the bundle.

Production secrets belong only in backend environment variables. Meta access tokens, app secrets and verify tokens must never use a `VITE_` prefix or be committed. Webhook GET verification requires the configured token; production POST callbacks require `X-Hub-Signature-256`. Callback IDs are deduplicated before business updates.

DTO validation protects write boundaries, API failures return controlled error payloads rather than stack traces, and business-critical mutations record audits. Monetary values are integer minor units. PostgreSQL constraints enforce important state, range and uniqueness invariants.

Demo credentials and the authenticated reset endpoint exist only for `APP_MODE=demo`. Production must set `APP_MODE=production`, disable demo seeding, provide unique database credentials, restrict `DESKTOP_ORIGINS`, and terminate TLS at the deployment boundary.

The Tauri capability set is intentionally minimal (`core:default`). Its content-security policy allows the packaged UI to connect to the local backend and does not embed backend secrets.
