/**
 * TOOLS-NAV-01 + SCENEGRAPH-NAV-01 — unit tests for the Tools landing
 * tile inventory and the appId-shape validator used by the Scene-graphs
 * "Open by appId" form.
 *
 * Pure-helper-pattern tests; no component mount. Mirrors
 * containerListPage.test.ts and PredecessorRelationshipTypeChip.test.ts.
 */
import { describe, it, expect } from "vitest";

import { TOOLS_TILES } from "../../utils/toolsLanding";
// UU2 (2026-05-31): `isPlausibleAppId` moved to `utils/idShape.ts` to live
// next to its sibling `isNumericLegacyId`. Import directly from there now.
import { isPlausibleAppId } from "../../utils/idShape";

// ── TOOLS_TILES — tile inventory ───────────────────────────────────────────

describe("TOOLS_TILES", () => {
  it("exposes the reachability-reconciler tiles", () => {
    // CLAUDE.md "every shipped feature reachable from the top-nav before beta".
    // V2CONV-B4 removed the `/scene-graphs` tile — the 3D view is now reached
    // in-context from a URDF FileReference detail page.
    const expectedRoutes = [
      "/semantic/vocabularies",
      "/semantic/sparql",
      "/shapes/validate",
      "/snapshots/diff",
      "/shapes/render",
      "/tools/form-preview", // BTKVS-B2 — form-descriptor placeholder stub
      "/aas/shells", // MISSING-aas-ui Slice 1 — AAS Shell list
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

  it("no longer exposes a Scene graphs tile (V2CONV-B4 — now in-context only)", () => {
    expect(TOOLS_TILES.find(t => t.to === "/scene-graphs")).toBeUndefined();
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
