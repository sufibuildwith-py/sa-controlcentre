# Release guide

## Verified application builds

- Backend: `mvn -f apps/backend/pom.xml test` and `mvn -f apps/backend/pom.xml -DskipTests package`.
- Frontend: `npm --prefix apps/desktop test -- --run` and `npm --prefix apps/desktop run build`.
- Browser regression: `npm --prefix apps/desktop run test:e2e` with an installed Playwright browser.

The Vite bundle and Spring Boot JAR are not native desktop packaging evidence.

## Windows native build

Install Rust stable through rustup, Cargo, the Visual Studio C++ desktop workload with MSVC and a Windows SDK, and the WebView2 runtime. Then run `npm --prefix apps/desktop run tauri build`. Verify the generated installer/artifact, launch the packaged app, sign in, and inspect the main shell and Communications against the backend.

Current host status: WebView2 is present; Rust, Cargo and a complete MSVC/Windows SDK workload are not detected. Windows Tauri packaging is therefore blocked by the host toolchain.

## macOS native build

Use a macOS host or CI runner with Xcode command-line tools and Rust stable, then run `npm --prefix apps/desktop run tauri build`. Configure an Apple Developer signing identity, bundle identifier and signing certificate; notarize the release with App Store Connect credentials and staple the notarization ticket before distribution. The shared Tauri bundle configuration is validated, but no macOS host build, signing or notarization has been performed.

## Runtime modes

Demo mode uses `ConsoleMessagingProvider`, deterministic seed/reset data, and the Settings messaging simulator without Meta. Production mode uses `MetaWhatsAppProvider`, signed webhooks and backend-only credentials. See [demo.md](demo.md), [whatsapp.md](whatsapp.md), and `.env.example`.

Production release gates include explicit runtime mode, HTTPS API/CSP alignment, Meta startup validation when enabled, backend and frontend verification, Playwright visual regression, dependency/secret/CodeQL scans, and a native Windows Tauri build. Audit retention and the 180-day webhook-receipt policy belong in backup and operations procedures.
