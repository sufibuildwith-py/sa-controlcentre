import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";

describe("SEC-002: Vercel Deployment Security Headers", () => {
  const vercelConfigPath = path.resolve(__dirname, "../../vercel.json");

  it("vercel.json exists and is valid JSON", () => {
    expect(fs.existsSync(vercelConfigPath)).toBe(true);
    const content = fs.readFileSync(vercelConfigPath, "utf-8");
    expect(() => JSON.parse(content)).not.toThrow();
  });

  it("contains single-page application rewrites rule", () => {
    const config = JSON.parse(fs.readFileSync(vercelConfigPath, "utf-8"));
    expect(config.rewrites).toBeDefined();
    expect(
      config.rewrites.some(
        (r: { source: string; destination: string }) =>
          r.source === "/(.*)" && r.destination === "/index.html",
      ),
    ).toBe(true);
  });

  it("defines essential security headers across all routes", () => {
    const config = JSON.parse(fs.readFileSync(vercelConfigPath, "utf-8"));
    expect(config.headers).toBeDefined();
    const globalHeaderRule = config.headers.find(
      (h: { source: string }) => h.source === "/(.*)",
    );
    expect(globalHeaderRule).toBeDefined();

    const headersMap = new Map<string, string>();
    for (const h of globalHeaderRule.headers) {
      headersMap.set(h.key, h.value);
    }

    // 1. Anti-framing / clickjacking
    expect(headersMap.get("X-Frame-Options")).toBe("DENY");

    // 2. MIME sniffing protection
    expect(headersMap.get("X-Content-Type-Options")).toBe("nosniff");

    // 3. Referrer Policy
    expect(headersMap.get("Referrer-Policy")).toBe(
      "strict-origin-when-cross-origin",
    );

    // 5. Strict Transport Security (HSTS)
    expect(headersMap.get("Strict-Transport-Security")).toContain(
      "max-age=63072000",
    );

    // 6. Content Security Policy
    const csp = headersMap.get("Content-Security-Policy");
    expect(csp).toBeDefined();
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("script-src 'self'");
    expect(csp).toContain("https://sa-command-backend.onrender.com");
    expect(csp).toContain(
      "https://sa-command-backend-production.up.railway.app",
    );
    expect(csp).toContain(
      "https://sa-navigator-gateway-production.up.railway.app",
    );
    expect(csp).toContain(
      "wss://sa-navigator-gateway-production.up.railway.app",
    );
    expect(csp).toContain("https://tiles.openfreemap.org");
    expect(csp).not.toContain("https://*.onrender.com");
    expect(csp).not.toContain("unsafe-eval");
  });
});
