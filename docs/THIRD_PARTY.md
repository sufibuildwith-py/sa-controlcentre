# Third-party software and provenance

SA Command uses packages as dependencies and does not vendor Aceternity or CodeFronts source. The SA components are original local compositions built with the packages below and the interaction/geometry requirements in `PLAN.md`.

| Package / influence | Source | License | Version policy / files | SA Command use |
|---|---|---|---|---|
| Tauri | https://github.com/tauri-apps/tauri | MIT / Apache-2.0 | npm/Cargo lockfiles; `apps/desktop/src-tauri` | Desktop runtime and packaging |
| React | https://github.com/facebook/react | MIT | npm lockfile; desktop application | UI runtime |
| Tailwind CSS | https://github.com/tailwindlabs/tailwindcss | MIT | npm lockfile; generated utility CSS | Styling foundation |
| Motion | https://github.com/motiondivision/motion | MIT | npm lockfile; SA layout components | Shared-layout indicators and reduced-motion-aware hover/press; page/modal entrances use original local CSS |
| cmdk | https://github.com/pacocoursey/cmdk | MIT | npm lockfile; `SACommandPalette.tsx` | Accessible command menu behavior |
| Lucide React | https://github.com/lucide-icons/lucide | ISC | npm lockfile | Single icon family |
| TanStack Query | https://github.com/TanStack/query | MIT | npm lockfile | Server-state cache and mutations |
| Zustand | https://github.com/pmndrs/zustand | MIT | npm lockfile | Theme and shell UI state only |
| React Hook Form | https://github.com/react-hook-form/react-hook-form | MIT | npm lockfile | Form state |
| Zod | https://github.com/colinhacks/zod | MIT | npm lockfile | Client validation |
| Radix Dialog / Popover / Tooltip / Toast | https://github.com/radix-ui/primitives | MIT | npm lockfile; SA wrappers | Focus-safe accessible overlays |
| Aceternity Bento/Dock/Tabs | https://ui.aceternity.com/components | Aceternity license | Design reference only; retrieved 2026-09-18 | Layout and motion inspiration; no source copied |
| CodeFronts motion references | https://codefronts.com/motion/ | Site/example-specific | Design reference only; retrieved 2026-09-18 | Subtle hover/timing inspiration; no source copied |
| Spring Boot | https://github.com/spring-projects/spring-boot | Apache-2.0 | Maven lock by resolved POM | Backend runtime, web, validation, data and security |
| Flyway | https://github.com/flyway/flyway | Apache-2.0 | Maven dependency | Append-only PostgreSQL migrations |
| Testcontainers | https://github.com/testcontainers/testcontainers-java | MIT | Maven test dependency | PostgreSQL integration tests |
