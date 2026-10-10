/// <reference types="vitest/config" />
import { defineConfig, loadEnv, type Plugin } from "vite";
import { copyFileSync, existsSync, mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import react from "@vitejs/plugin-react";

function maplibreWorkerPlugin(): Plugin {
  return {
    name: "maplibre-worker-copy",
    buildStart() {
      const srcDir = resolve(__dirname, "node_modules/maplibre-gl/dist");
      const destDir = resolve(__dirname, "public/assets");
      if (existsSync(srcDir)) {
        mkdirSync(destDir, { recursive: true });
        for (const file of [
          "maplibre-gl-worker.mjs",
          "maplibre-gl-shared.mjs",
        ]) {
          const src = resolve(srcDir, file);
          const dest = resolve(destDir, file);
          if (existsSync(src)) {
            copyFileSync(src, dest);
          }
        }
      }
    },
  };
}

export default defineConfig(({ command, mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ""), ...process.env };
  const isWebBuild = mode === "web" || env.VITE_BUILD_TARGET === "web";
  if (
    command === "build" &&
    mode !== "demo" &&
    mode !== "desktop" &&
    !isWebBuild
  ) {
    if (env.VITE_APP_MODE === "demo")
      throw new Error("Use an explicit demo build for demo credentials.");
    const url = new URL(
      env.VITE_API_URL || "https://api.saproduction.in/api/v1",
    );
    if (url.protocol !== "https:" || url.username || url.password)
      throw new Error(
        "Production API must use HTTPS without embedded credentials.",
      );
    const config = JSON.parse(
      readFileSync(
        new URL("./src-tauri/tauri.conf.json", import.meta.url),
        "utf8",
      ),
    );
    const connect = config.app.security.csp
      .split(";")
      .find((part: string) => part.trim().startsWith("connect-src "));
    if (!connect?.split(/\s+/).includes(url.origin) || connect.includes("*"))
      throw new Error(
        "Add the exact production API origin to the packaged Tauri CSP before building.",
      );
  }
  return {
    plugins: [react(), maplibreWorkerPlugin()],
    clearScreen: false,
    optimizeDeps: { exclude: ["maplibre-gl"] },
    server: {
      port: 1420,
      strictPort: true,
      watch: { ignored: ["**/src-tauri/**"] },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      css: true,
      include: ["src/**/*.test.{ts,tsx}"],
    },
  };
});
