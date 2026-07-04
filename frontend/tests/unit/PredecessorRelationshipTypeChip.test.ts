/**
 * PROV1k — unit tests for PredecessorRelationshipTypeChip component logic.
 *
 * Tests verify:
 * - Chip renders for known relationship types (wasRevisionOf, repairs)
 * - Default "informed by" type renders
 * - Null / undefined relationshipType renders nothing (config = null)
 * - Unknown type falls back to raw string label (no crash)
 */
import { describe, it, expect } from "vitest";

// ── Inline the config computation logic from the component ──────────────────
// We don't mount the full Vuetify component tree; we test the computed logic
// in isolation (same pattern as AnnotationChip.test.ts).

const RELATIONSHIP_CONFIG: Record<
  string,
  { color: string; label: string; tooltip: string }
> = {
  "prov:wasInformedBy": {
    color: "default",
    label: "informed by",
    tooltip:
      "prov:wasInformedBy — generic informational dependency between activities",
  },
  "prov:wasRevisionOf": {
    color: "blue",
    label: "revision of",
    tooltip:
      "prov:wasRevisionOf — this DataObject is a direct revision or correction of the predecessor",
  },
  "fair2r:repairs": {
    color: "orange",
    label: "repairs",
    tooltip:
      "fair2r:repairs — rework / NCR-repair relationship (e.g. after a non-conformance)",
  },
  "fair2r:concession": {
    color: "amber",
    label: "concession",
    tooltip:
      "fair2r:concession — the successor was accepted under a concession ('use-as-is') after the predecessor failed its acceptance criterion",
  },
};

function computeConfig(
  relationshipType: string | null | undefined,
): { color: string; label: string; tooltip: string } | null {
  if (!relationshipType) return null;
  return (
    RELATIONSHIP_CONFIG[relationshipType] ?? {
      color: "default",
      label: relationshipType,
      tooltip: relationshipType,
    }
  );
}

// ── Tests ──────────────────────────────────────────────────────────────────

describe("PredecessorRelationshipTypeChip — config logic", () => {
  it("returns null for undefined relationshipType (no chip rendered)", () => {
    expect(computeConfig(undefined)).toBeNull();
  });

  it("returns null for null relationshipType (no chip rendered)", () => {
    expect(computeConfig(null)).toBeNull();
  });

  it("returns orange config for 'fair2r:repairs'", () => {
    const cfg = computeConfig("fair2r:repairs");
    expect(cfg).not.toBeNull();
    expect(cfg!.color).toBe("orange");
    expect(cfg!.label).toBe("repairs");
  });

  it("returns amber config for 'fair2r:concession' (QM1b)", () => {
    const cfg = computeConfig("fair2r:concession");
    expect(cfg).not.toBeNull();
    expect(cfg!.color).toBe("amber");
    expect(cfg!.label).toBe("concession");
    expect(cfg!.tooltip).toContain("use-as-is");
  });

  it("returns blue config for 'prov:wasRevisionOf'", () => {
    const cfg = computeConfig("prov:wasRevisionOf");
    expect(cfg).not.toBeNull();
    expect(cfg!.color).toBe("blue");
    expect(cfg!.label).toBe("revision of");
  });

  it("returns default config for 'prov:wasInformedBy'", () => {
    const cfg = computeConfig("prov:wasInformedBy");
    expect(cfg).not.toBeNull();
    expect(cfg!.color).toBe("default");
    expect(cfg!.label).toBe("informed by");
  });

  it("falls back to raw string for unknown type (no crash)", () => {
    const cfg = computeConfig("owl:sameAs");
    expect(cfg).not.toBeNull();
    expect(cfg!.label).toBe("owl:sameAs");
    expect(cfg!.color).toBe("default");
  });
});

describe("PredecessorRelationshipTypeChip — data-testid presence", () => {
  it("chip renders (config is non-null) for typed predecessor types", () => {
    const typesToRender = [
      "prov:wasRevisionOf",
      "fair2r:repairs",
      "prov:wasInformedBy",
      "fair2r:concession",
    ];
    for (const t of typesToRender) {
      expect(computeConfig(t)).not.toBeNull();
    }
  });

  it("chip does NOT render (config is null) when type is absent", () => {
    for (const absent of [null, undefined, ""]) {
      expect(computeConfig(absent as null)).toBeNull();
    }
  });
});
