# Phase 1 closure — 2026-09-19

## Implementation

Reference-based calibration preserves the rounded shell, floating segmented navigation crossing its top edge, detached overlapping dock, compact search/profile header, asymmetric bento layout, restrained borders/shadows and Pearl/Charcoal themes. The original user reference remains in `docs/reference/sa-command-ui-reference.png`.

The routed tree no longer sits inside an exiting `AnimatePresence` container. Keyed route and card entrances now use visible resting CSS defaults, so animation completion is never required to reveal content. Motion remains for shared indicators and restrained hover/press, with reduced-motion support.

Radix owns overlay presence and unmounting. Explicit opener tracking restores focus for globally opened dialogs and nested Quick Create/command palette surfaces. Escape closes only the top surface and leaves no pointer-blocking backdrop. Radial metrics fit their cards and labels.

Real PostgreSQL validation exposed and fixed two backend defects: the currency mapping now matches the existing CHAR(3) migration; lazy employee relationships use proxy-safe access/DTO unwrapping, preventing missing identifiers/details in attendance and leave. No existing migration was modified and no Phase 2 domain was added.

Significant files: `AppShell.tsx`, `SACommandPalette.tsx`, `sa.tsx`, `AppProviders.tsx`, `styles/closure.css`, Playwright Phase 1 tests/snapshots, employee entity/DTO/service, attendance/leave services, and `PostgresApplicationTest.java`.

## Visual verification

All eight candidate renders were opened and visually inspected at 1440 × 900 against the supplied reference before snapshot approval. Smart-home content was not copied.

| Screen | Result |
|---|---|
| Command — SA Pearl | VERIFIED |
| Command — Reference Charcoal | VERIFIED |
| People | VERIFIED |
| Employee Detail | VERIFIED |
| Attendance | VERIFIED |
| Quick Create | VERIFIED |
| Command Palette | VERIFIED |
| Settings | VERIFIED |

Checked shell/nav/dock overlap, inset workspace, compact header/profile/search, card proportions, typography, tonal hierarchy, radii, borders, shadows and ring bounds. Automated geometry checks also pass at 1180 × 720 in both themes with 100% and 150% emulated pixel density. This does not replace native Windows display-scaling verification.

## Regression results

Commands are from the repository root unless noted.

| Validation | Command | Result |
|---|---|---|
| Frontend unit | `npm --prefix apps/desktop test -- --run` | 6/6; 3 files |
| Typecheck + production UI | `npm --prefix apps/desktop run build` | PASS |
| Browser functional | `npm --prefix apps/desktop run test:e2e` | 7/7 |
| Browser visual | Same command | 1/1 visual scenario; 8/8 approved screenshot comparisons |
| Backend tests + package | `mvn -f apps/backend/pom.xml "-Dapi.version=1.44" verify` | 9/9; 0 failures/errors/skipped |
| Native prerequisite inventory | `npm --prefix apps/desktop run tauri -- info` | BLOCKED as below |

No lint script is configured. UI build reports a nonfatal large-JavaScript-chunk warning (~695 KB minified). No debug logging or temporary diagnostic code remains. Generated build, test-result and candidate-review folders are ignored. The repository has no initial commit; no commit was created during closure.

Browser coverage includes sign-in/reload restoration, employee create/edit, attendance/history, leave approval/rejection, reload/theme persistence, command palette, nested modal focus and pointer cleanup, repeated route visibility in normal/reduced motion, and ring sizing. Browser data is explicitly mocked. The separate real HTTP integration exercises login/session reuse, create/edit/read persistence, seeded attendance identity/details, history, approval-to-attendance propagation, rejection and audit records.

## PostgreSQL / Testcontainers — VERIFIED

Docker Desktop 29.6.2 is usable. `docker compose up -d postgres` starts the local PostgreSQL service. Testcontainers ran PostgreSQL 17.11 with all four Flyway migrations from an empty database, Hibernate schema validation, and deterministic bootstrap counts (1 owner, 12 employees, 12 attendance records, 1 leave request); a second bootstrap run preserved those counts. The unique employee/date attendance constraint is tested separately. The packaged backend was also launched against Compose PostgreSQL.

The `-Dapi.version=1.44` override is necessary for the pinned Testcontainers client with this host's newer Docker minimum API version. The two PostgreSQL tests ran, not skipped. Testcontainers disposes its temporary databases; the local Compose database remains available.

## Tauri native packaging — BLOCKED BY HOST TOOLCHAIN

WebView2 153.0.4234.48 is installed. Rust, Cargo and rustup are absent, and no Visual Studio installation contains the required MSVC/Windows SDK components. Install Rust stable with the `x86_64-pc-windows-msvc` toolchain, Visual Studio Build Tools with Desktop development with C++ (MSVC x64/x86 and Windows SDK), then run:

```powershell
npm --prefix apps/desktop run tauri -- build
```

After packaging, launch the generated Windows installer/application, verify API connection and repeat native keyboard/focus/scaling smoke checks. No Windows native artifact or native launch was verified; Vite build is not native packaging.

## Phase status

PHASE 1 COMPLETE. Reference comparison and software closure checks are complete with no known remaining Phase 1 software defect. Native packaging remains a host-only validation blocker. Phase 2 has not started.
