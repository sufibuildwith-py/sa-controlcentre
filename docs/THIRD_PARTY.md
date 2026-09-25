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
| Apache POI | https://github.com/apache/poi | Apache-2.0 | Backend Maven dependency | Demo-only XLSX staging; workbook bytes are not bundled or committed |
| Testcontainers | https://github.com/testcontainers/testcontainers-java | MIT | Maven test dependency | PostgreSQL integration tests |
| Schedule-X | https://github.com/schedule-x/schedule-x | MIT | npm lockfile; `CalendarPage.tsx`, `phase2.css` | Accessible month, week and agenda calendar engine, fully restyled to the SA visual system |
| Temporal polyfill | https://github.com/fullcalendar/temporal-polyfill | MIT | npm lockfile; `CalendarPage.tsx` | Zoned date/time values supplied to Schedule-X |
| MapLibre GL JS | https://github.com/maplibre/maplibre-gl-js | BSD-3-Clause | desktop npm lockfile; demo-only Navigator | Map rendering, markers and camera |
| OpenFreeMap | https://openfreemap.org/ | Service terms; OpenStreetMap-derived data attribution | demo tile/style service | Navigator demo basemap; on-map attribution remains enabled |
| @stomp/stompjs | https://github.com/stomp-js/stompjs | Apache-2.0 | desktop npm lockfile | Short-lived-ticket Navigator realtime client |
| Expo and selected Expo packages | https://github.com/expo/expo | MIT | `apps/navigator-mobile/package-lock.json` | React Native shell, location, background task, SecureStore and SQLite |
| Turf.js | https://github.com/Turfjs/turf | MIT | approved for individually imported modules only; no N1 runtime module required yet | Future bounded geometry helpers without the full bundle |
