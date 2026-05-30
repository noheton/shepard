/**
 * TOOLS-NAV-01 + SCENEGRAPH-NAV-01 — unit tests for the Tools landing
 * tile inventory and the appId-shape validator used by the Scene-graphs
 * "Open by appId" form.
 *
 * Pure-helper-pattern tests; no component mount. Mirrors
 * containerListPage.test.ts and PredecessorRelationshipTypeChip.test.ts.
 */
import { describe, it, expect } from "vitest";

import { TOOLS_TILES, isPlausibleAppId } from "../../utils/toolsLanding";

// ── TOOLS_TILES — tile inventory ───────────────────────────────────────────

describe("TOOLS_TILES", () => {
  it("exposes the six tiles called out by the reachability reconciler", () => {
    // The reconciler's six routes (CLAUDE.md "every shipped feature
    // reachable from the top-nav before beta", 2026-05-30 finding).
    const expectedRoutes = [
      "/semantic/vocabularies",
      "/semantic/sparql",
      "/shapes/validate",
      "/snapshots/diff",
      "/scene-graphs",
      "/shapes/render",
    ];
    expect(TOOLS_TILES.map(t => t.to).sort()).toEqual(expectedRoutes.sort());
  });

  it("every tile has a title + description + icon + route", () => {
    for (const tile of TOOLS_TILES) {
      expect(tile.title.length).toBeGreaterThan(0);
      expect(tile.description.length).toBeGreaterThan(0);
      expect(tile.icon).toMatch(/^mdi-/);
      expect(tile.to.startsWith("/")).toBe(true);
    }
  });

  it("tile routes are unique", () => {
    const routes = TOOLS_TILES.map(t => t.to);
    expect(new Set(routes).size).toBe(routes.length);
  });

  it("includes the new Scene graphs tile (SCENEGRAPH-NAV-01)", () => {
    const scene = TOOLS_TILES.find(t => t.to === "/scene-graphs");
    expect(scene).toBeDefined();
    expect(scene!.title).toBe("Scene graphs");
  });
});

// ── isPlausibleAppId ──────────────────────────────────────────────────────

describe("isPlausibleAppId", () => {
  it("accepts a canonical UUID v7 shape", () => {
    expect(isPlausibleAppId("0197b6a2-7b4c-7000-8a3b-1234567890ab")).toBe(true);
  });

  it("accepts an uppercase input (normalised before testing)", () => {
    expect(isPlausibleAppId("0197B6A2-7B4C-7000-8A3B-1234567890AB")).toBe(true);
  });

  it("trims surrounding whitespace", () => {
    expect(isPlausibleAppId("  0197b6a2-7b4c-7000-8a3b-1234567890ab  ")).toBe(true);
  });

  it("rejects too-short input", () => {
    expect(isPlausibleAppId("0197b6a2-7b4c")).toBe(false);
  });

  it("rejects non-hex characters", () => {
    expect(isPlausibleAppId("zzz7b6a2-7b4c-7000-8a3b-1234567890ab")).toBe(false);
  });

  it("rejects missing hyphens", () => {
    expect(isPlausibleAppId("0197b6a27b4c70008a3b1234567890ab")).toBe(false);
  });

  it("rejects empty / null / undefined", () => {
    expect(isPlausibleAppId("")).toBe(false);
    expect(isPlausibleAppId(null)).toBe(false);
    expect(isPlausibleAppId(undefined)).toBe(false);
  });

  it("rejects whitespace-only input", () => {
    expect(isPlausibleAppId("   ")).toBe(false);
  });
});
