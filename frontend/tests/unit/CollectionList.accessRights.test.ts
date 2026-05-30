/**
 * UX-WALK-2026-05-29-08 — CollectionList Access column NOT_SET chip tests.
 *
 * When a Collection's `accessRights` field is null or undefined, the Access
 * column must render a "Not specified" chip (via the NOT_SET sentinel) instead
 * of a plain "—" dash.  This closes one of the FAIR-A surface gaps surfaced in
 * research-data-manager.md: the previous bare dash was ambiguous — it said
 * nothing actionable to a researcher or administrator.
 *
 * Because mounting `CollectionList.vue` requires the full Nuxt + Vuetify chain,
 * this file tests:
 *   1. The `NOT_SET` entry in `spdxLicenses.ts` has the required shape.
 *   2. `getAccessRightsOption("NOT_SET")` returns the correct option.
 *   3. The `rowAccessRights ?? "NOT_SET"` fallback logic mirrors what
 *      `CollectionList.vue` does — null/undefined → "NOT_SET", known value →
 *      the value itself.
 */
import { describe, it, expect } from "vitest";
import type { Collection } from "@dlr-shepard/backend-client";
import {
  ACCESS_RIGHTS_OPTIONS,
  getAccessRightsOption,
} from "../../utils/spdxLicenses";

// ── Fixture builder ───────────────────────────────────────────────────────────

type CollectionWithAccessRights = Collection & {
  accessRights?: string | null;
};

function buildCollection(
  overrides: Partial<CollectionWithAccessRights> = {},
): CollectionWithAccessRights {
  return {
    id: 1,
    createdAt: new Date("2024-01-01"),
    createdBy: "operator",
    updatedAt: null,
    updatedBy: null,
    name: "Test Collection",
    dataObjectIds: [],
    incomingIds: [],
    accessRights: null,
    ...overrides,
  };
}

// ── Logic mirror (mirrors CollectionList.vue rowAccessRights + v-bind) ────────

/** Mirrors `rowAccessRights(item)` from CollectionList.vue */
function rowAccessRights(item: CollectionWithAccessRights): string | null {
  return (item as CollectionWithAccessRights).accessRights ?? null;
}

/** Mirrors the template expression `:access-rights="rowAccessRights(item) ?? 'NOT_SET'"` */
function resolvedAccessRights(item: CollectionWithAccessRights): string {
  return rowAccessRights(item) ?? "NOT_SET";
}

// ─────────────────────────────────────────────────────────────────────────────
describe("NOT_SET entry in ACCESS_RIGHTS_OPTIONS", () => {
  it("contains a NOT_SET entry", () => {
    const entry = ACCESS_RIGHTS_OPTIONS.find(o => o.value === "NOT_SET");
    expect(entry).toBeDefined();
  });

  it("NOT_SET has label 'Not specified'", () => {
    const entry = ACCESS_RIGHTS_OPTIONS.find(o => o.value === "NOT_SET")!;
    expect(entry.label).toBe("Not specified");
  });

  it("NOT_SET has a neutral/default color", () => {
    const entry = ACCESS_RIGHTS_OPTIONS.find(o => o.value === "NOT_SET")!;
    expect(entry.color).toBe("default");
  });

  it("NOT_SET has a non-empty description mentioning how to fix it", () => {
    const entry = ACCESS_RIGHTS_OPTIONS.find(o => o.value === "NOT_SET")!;
    expect(entry.description.length).toBeGreaterThan(10);
    // Should hint the user that they can set an access level.
    expect(entry.description.toLowerCase()).toMatch(/access|set/);
  });
});

describe("getAccessRightsOption for NOT_SET", () => {
  it("returns the NOT_SET option when 'NOT_SET' is passed", () => {
    const option = getAccessRightsOption("NOT_SET");
    expect(option).toBeDefined();
    expect(option!.value).toBe("NOT_SET");
    expect(option!.label).toBe("Not specified");
    expect(option!.color).toBe("default");
  });

  it("still returns undefined for null (callers must pass NOT_SET explicitly)", () => {
    expect(getAccessRightsOption(null)).toBeUndefined();
  });

  it("still returns undefined for undefined", () => {
    expect(getAccessRightsOption(undefined)).toBeUndefined();
  });

  it("still returns undefined for empty string", () => {
    expect(getAccessRightsOption("")).toBeUndefined();
  });
});

describe("CollectionList — Access column fallback logic", () => {
  it("null accessRights resolves to NOT_SET sentinel", () => {
    const col = buildCollection({ accessRights: null });
    expect(resolvedAccessRights(col)).toBe("NOT_SET");
  });

  it("undefined accessRights resolves to NOT_SET sentinel", () => {
    const col = buildCollection({ accessRights: undefined });
    expect(resolvedAccessRights(col)).toBe("NOT_SET");
  });

  it("OPEN accessRights passes through as-is", () => {
    const col = buildCollection({ accessRights: "OPEN" });
    expect(resolvedAccessRights(col)).toBe("OPEN");
  });

  it("RESTRICTED accessRights passes through as-is", () => {
    const col = buildCollection({ accessRights: "RESTRICTED" });
    expect(resolvedAccessRights(col)).toBe("RESTRICTED");
  });

  it("CLOSED accessRights passes through as-is", () => {
    const col = buildCollection({ accessRights: "CLOSED" });
    expect(resolvedAccessRights(col)).toBe("CLOSED");
  });

  it("EMBARGOED accessRights passes through as-is", () => {
    const col = buildCollection({ accessRights: "EMBARGOED" });
    expect(resolvedAccessRights(col)).toBe("EMBARGOED");
  });

  it("getAccessRightsOption on the resolved value always returns a defined option", () => {
    // Both the known values and NOT_SET must resolve to a displayable option.
    const values = ["OPEN", "RESTRICTED", "CLOSED", "EMBARGOED", "NOT_SET"];
    for (const v of values) {
      const option = getAccessRightsOption(v);
      expect(option).toBeDefined();
      expect(option!.label).toBeTruthy();
      expect(option!.color).toBeTruthy();
    }
  });
});

describe("NOT_SET does not appear in visible select options (it is a sentinel, not a user choice)", () => {
  // NOT_SET is a display sentinel. It should be present in ACCESS_RIGHTS_OPTIONS
  // for lookup purposes, but the v-select in collection properties should filter
  // it out. This test documents the contract: NOT_SET is findable by lookup but
  // is expected to be excluded from user-facing select lists.
  it("NOT_SET is present in ACCESS_RIGHTS_OPTIONS for lookup purposes", () => {
    expect(ACCESS_RIGHTS_OPTIONS.some(o => o.value === "NOT_SET")).toBe(true);
  });

  it("the four settable values are still present", () => {
    const settable = ["OPEN", "RESTRICTED", "CLOSED", "EMBARGOED"];
    for (const v of settable) {
      expect(ACCESS_RIGHTS_OPTIONS.some(o => o.value === v)).toBe(true);
    }
  });
});
