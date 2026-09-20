# Phase 1 demo

Demo bootstrap is enabled with `APP_MODE=demo` and `DEMO_SEED=true`.

- Email: `owner@saproduction.local`
- Password: `SADemo!2026`

The default `console` messaging provider needs no Meta account and records stable provider IDs locally. Settings includes a demo-only messaging simulator for delivery states and assignment/meeting replies.

Reset the complete deterministic demo with `powershell -ExecutionPolicy Bypass -File scripts/reset-demo.ps1`.

These credentials are development-only. The bootstrap hashes the password with BCrypt and never persists the plaintext value.

Demo sequence: sign in, switch theme, use `Ctrl+K`, open People, add/edit an employee, open the employee detail, mark attendance, create and resolve a leave request, then reload to verify PostgreSQL persistence.
