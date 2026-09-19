# SA Command Engineering Rules

SA Command is production-style internal software for SA Production. It is not a demo CRUD dashboard.

## Product authority

`PLAN.md` is the source of truth for V1. The supplied UI reference in `docs/reference` is the visual authority. Do not expand scope without updating `PLAN.md`.

## UI

Preserve the large rounded application shell, floating top segmented navigation, detached left icon dock, bento information architecture, SA Pearl default theme, Reference Charcoal theme, thin low-contrast borders, minimal shadow, very large radii and restrained physical motion.

Never introduce a generic admin template, dense permanent sidebar, neon, excessive gradients, decorative 3D/particles, giant default tables or mixed icon families. Check `PLAN.md` and `docs/THIRD_PARTY.md` before building a reusable primitive. Copied or adapted code must carry provenance.

## Frontend architecture

TanStack Query owns server state. Zustand owns only application UI state. Never place business rules inside React.

## Backend

Money is integer minor units. Important business mutations are audited. Flyway migrations are append-only and constraints enforce important invariants.

## Security and quality

Secrets never ship in the desktop bundle. Validate DTOs and do not expose stack traces. Every finished feature requires loading, empty and error states, validation, tests, accessibility and audit behavior where business-critical. No TODO stub counts as completion. Run relevant tests before marking work complete.

