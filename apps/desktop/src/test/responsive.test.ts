import { describe, it, expect } from "vitest";
import fs from "fs";
import path from "path";

describe("SA Command — Mobile Responsiveness & Desktop Preservation Verification", () => {
  const stylesDir = path.resolve(__dirname, "../styles");

  it("verifies desktop root dimensions are scoped to desktop (min-width: 1025px)", () => {
    const indexCss = fs.readFileSync(path.join(stylesDir, "index.css"), "utf-8");
    expect(indexCss).toMatch(/@media\s*\(min-width:\s*1025px\)/);
    expect(indexCss).toMatch(/min-width:\s*1180px/);
    expect(indexCss).toMatch(/min-height:\s*720px/);
  });

  it("verifies closure.css adapts the application shell, switcher, and dock for phone screens", () => {
    const closureCss = fs.readFileSync(path.join(stylesDir, "closure.css"), "utf-8");
    expect(closureCss).toMatch(/@media\s*\(max-width:\s*768px\)/);
    // Frame padding reset on mobile
    expect(closureCss).toContain(".app-frame");
    // Top switcher horizontally scrollable on mobile
    expect(closureCss).toMatch(/\.top-switcher\s*\{[^}]*overflow-x:\s*auto/s);
    // Operational dock floating bottom bar on mobile
    expect(closureCss).toMatch(/\.operational-dock\s*\{[^}]*flex-direction:\s*row/s);
    // Workspace-shell flex expansion
    expect(closureCss).toMatch(/\.workspace-shell\s*\{[^}]*flex:\s*1\s*1\s*auto/s);
  });

  it("verifies modal, drawer, command palette, and touch adaptations in index.css", () => {
    const indexCss = fs.readFileSync(path.join(stylesDir, "index.css"), "utf-8");
    // Modal max-width on mobile
    expect(indexCss).toMatch(/\.modal\s*\{[^}]*max-width:\s*calc\(100vw\s*-\s*20px\)/s);
    // Drawer as bottom sheet on mobile
    expect(indexCss).toMatch(/\.drawer\s*\{[^}]*max-height:\s*86vh/s);
    // Command dialog on mobile
    expect(indexCss).toMatch(/\.command-dialog\s*\{[^}]*max-width:\s*calc\(100vw\s*-\s*16px\)/s);
    // Horizontally scrollable tabs
    expect(indexCss).toMatch(/\.sa-tabs\s*\{[^}]*overflow-x:\s*auto/s);
    // Horizontally scrollable segmented controls
    expect(indexCss).toMatch(/\.segmented\s*\{[^}]*overflow-x:\s*auto/s);
    // iOS input auto-zoom prevention
    expect(indexCss).toMatch(/font-size:\s*16px/);
    // Page title wrapping
    expect(indexCss).toContain(".page-title");
  });

  it("verifies command dashboard responsiveness in command.css", () => {
    const commandCss = fs.readFileSync(path.join(stylesDir, "command.css"), "utf-8");
    expect(commandCss).toMatch(/@media\s*\(max-width:\s*768px\)/);
    expect(commandCss).toMatch(/@media\s*\(max-width:\s*480px\)/);
    expect(commandCss).toContain(".command-today-strip");
    expect(commandCss).toContain(".command-header-bar");
    expect(commandCss).toContain(".command-timeline-row");
  });

  it("verifies people and employee responsive adaptations in features.css", () => {
    const featuresCss = fs.readFileSync(path.join(stylesDir, "features.css"), "utf-8");
    expect(featuresCss).toMatch(/@media\s*\(max-width:\s*768px\)/);
    expect(featuresCss).toMatch(/@media\s*\(max-width:\s*560px\)/);
    expect(featuresCss).toContain(".people-grid");
    expect(featuresCss).toContain(".people-toolbar");
    expect(featuresCss).toContain(".attendance-row");
    expect(featuresCss).toContain(".production-equipment-grid");
  });

  it("verifies production and payroll responsive rules in phase2.css", () => {
    const phase2Css = fs.readFileSync(path.join(stylesDir, "phase2.css"), "utf-8");
    expect(phase2Css).toMatch(/@media\s*\(max-width:\s*768px\)/);
    expect(phase2Css).toMatch(/@media\s*\(max-width:\s*560px\)/);
    expect(phase2Css).toContain(".production-hero");
    expect(phase2Css).toContain(".payroll-row");
    expect(phase2Css).toContain(".work-row");
  });

  it("verifies navigator map and roster adaptations in navigator.css", () => {
    const navCss = fs.readFileSync(path.join(stylesDir, "navigator.css"), "utf-8");
    expect(navCss).toMatch(/@media\s*\(max-width:\s*600px\)/);
    expect(navCss).toMatch(/@media\s*\(max-width:\s*440px\)/);
    expect(navCss).toContain(".navigator-map-wrap");
    expect(navCss).toContain(".navigator-summary");
  });

  it("verifies finance and billing layout adaptations in finance.css", () => {
    const financeCss = fs.readFileSync(path.join(stylesDir, "finance.css"), "utf-8");
    expect(financeCss).toMatch(/@media\s*\(max-width:\s*768px\)/);
    expect(financeCss).toContain(".billing-layout");
    expect(financeCss).toContain(".billing-preview-document");
    expect(financeCss).toContain(".billing-line");
    expect(financeCss).toContain(".billing-doc-table");
  });

  it("verifies settings responsive adaptations in settings.css", () => {
    const settingsCss = fs.readFileSync(path.join(stylesDir, "settings.css"), "utf-8");
    expect(settingsCss).toMatch(/@media\s*\(max-width:\s*600px\)/);
    expect(settingsCss).toContain(".health-grid");
  });
});
