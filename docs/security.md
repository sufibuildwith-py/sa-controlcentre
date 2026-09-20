# Security

Owner authentication uses a random opaque bearer token whose SHA-256 digest is stored in PostgreSQL. Packaged Tauri stores the raw token in the operating-system credential vault and attaches it explicitly as an `Authorization` header. Login rotates and revokes prior owner tokens; logout revokes the active token. Production tokens are never placed in browser local storage. Because authenticated mutations use an explicit bearer header rather than ambient cookies, browser CSRF does not apply to this native API path.

Login protection limits repeated failures per IP and account, returns a controlled `429`, applies a temporary cooldown, and records audit evidence without passwords.

Production secrets belong only in backend environment variables. Meta access tokens, app secrets and verify tokens must never use a `VITE_` prefix or be committed. Webhook GET verification requires the configured token; production POST callbacks require `X-Hub-Signature-256`. Callback IDs are deduplicated before business updates.

DTO validation protects write boundaries, API failures return controlled error payloads rather than stack traces, and business-critical mutations record audits. Monetary values are integer minor units. PostgreSQL constraints enforce important state, range and uniqueness invariants.

Demo credentials and reset/simulator endpoints exist only for explicit `APP_MODE=demo`. Production startup rejects demo seeding, console messaging, and incomplete Meta configuration. Production must set `APP_MODE=production`, provide unique database credentials, restrict `DESKTOP_ORIGINS`, and terminate TLS at the deployment boundary.

The Tauri capability set is intentionally minimal (`core:default`). Development CSP permits only the local API; packaged CSP permits only the configured HTTPS API origin. Backend secrets are never embedded in the desktop bundle.

Employee audit evidence is purpose-built and excludes phone, WhatsApp number, email, notes and duplicated full salary records. Financial evidence retains only fields required for accountability. Audit access is owner-only; audit and backup retention must follow the same statutory/accountability period approved by SA Production.
