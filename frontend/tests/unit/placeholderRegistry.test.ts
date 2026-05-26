import { describe, it, expect } from "vitest";
import {
  PLACEHOLDER_ENTRIES,
  EXPECTED_PLACEHOLDER_COUNT,
  findPlaceholder,
  placeholdersBySurface,
  type PlaceholderEntry,
} from "../../components/common/placeholder/placeholderRegistry";

describe("placeholderRegistry — no-UI-gap roll-out (2026-05-24)", () => {
  it("ships the documented count of placeholders", () => {
    // findings doc commits to a specific count; if this changes the doc
    // must change too (forces same-PR coupling).
    expect(EXPECTED_PLACEHOLDER_COUNT).toBe(15);
    expect(PLACEHOLDER_ENTRIES).toHaveLength(EXPECTED_PLACEHOLDER_COUNT);
  });

  it("every entry has a non-empty title, subtitle, backlogRow, designDoc", () => {
    for (const e of PLACEHOLDER_ENTRIES) {
      expect(e.title.length, `title for ${e.slug}`).toBeGreaterThan(2);
      expect(e.subtitle.length, `subtitle for ${e.slug}`).toBeGreaterThan(10);
      expect(e.backlogRow.length, `backlogRow for ${e.slug}`).toBeGreaterThan(0);
      expect(e.designDoc.length, `designDoc for ${e.slug}`).toBeGreaterThan(0);
    }
  });

  it("every endpoint starts with /v2/ or is null (designed-not-shipped)", () => {
    for (const e of PLACEHOLDER_ENTRIES) {
      if (e.endpoint !== null) {
        expect(e.endpoint, `endpoint for ${e.slug}`).toMatch(/^\/v2\//);
      }
    }
  });

  it("every designDoc path points under aidocs/", () => {
    for (const e of PLACEHOLDER_ENTRIES) {
      expect(e.designDoc, `designDoc for ${e.slug}`).toMatch(/^aidocs\//);
    }
  });

  it("slugs are unique", () => {
    const slugs = PLACEHOLDER_ENTRIES.map((e) => e.slug);
    expect(new Set(slugs).size).toBe(slugs.length);
  });

  it("designed-not-shipped entries carry the correct backend status", () => {
    const designed = PLACEHOLDER_ENTRIES.filter((e) => e.backend === "designed");
    // AI1a admin + profile + PG-COLLAPSE-002 backup
    expect(designed.length).toBeGreaterThanOrEqual(3);
    for (const e of designed) {
      expect(e.endpoint, `designed entry ${e.slug} should not have endpoint`).toBeNull();
    }
  });

  it("findPlaceholder returns the matching entry by slug", () => {
    const ai = findPlaceholder("ai-config");
    expect(ai).toBeDefined();
    expect(ai?.surface).toBe("admin");
    expect(ai?.backend).toBe("designed");
  });

  it("findPlaceholder returns undefined for unknown slugs", () => {
    expect(findPlaceholder("not-a-real-slug")).toBeUndefined();
  });

  it("placeholdersBySurface partitions the registry correctly", () => {
    const admin = placeholdersBySurface("admin");
    const profile = placeholdersBySurface("profile");
    const route = placeholdersBySurface("route");
    expect(admin.length).toBe(8);
    expect(profile.length).toBe(1);
    expect(route.length).toBe(6);
    expect(admin.length + profile.length + route.length).toBe(
      EXPECTED_PLACEHOLDER_COUNT,
    );
  });

  it("the headline four semantic-repo browser routes are registered", () => {
    // The headline is partially in the route registry; the 4-page browser
    // is: vocabularies index + vocabulary detail + predicate detail +
    // sparql. Detail pages are Vue templates (not registry entries) but
    // the two index entries must be in the registry so they're testable.
    expect(findPlaceholder("semantic-vocabularies")).toBeDefined();
    expect(findPlaceholder("semantic-sparql")).toBeDefined();
  });

  it("backlog rows reference shipped or designed features (not freetext)", () => {
    // A backlogRow should look like an ID (UPPERCASE-led mnemonic + digits +
    // optional lowercase suffix or dashes — `FS1e`, `AI1a`, `PG-COLLAPSE-002`,
    // `SEMA-V6`), not a free-text description. Guards against drift.
    const idPattern = /^[A-Z][A-Za-z0-9-]*$/;
    for (const e of PLACEHOLDER_ENTRIES) {
      expect(e.backlogRow, `backlogRow for ${e.slug}`).toMatch(idPattern);
    }
  });

  it("surface values are one of the allowed enum members", () => {
    const allowed: ReadonlyArray<PlaceholderEntry["surface"]> = [
      "admin",
      "profile",
      "route",
    ];
    for (const e of PLACEHOLDER_ENTRIES) {
      expect(allowed).toContain(e.surface);
    }
  });
});
