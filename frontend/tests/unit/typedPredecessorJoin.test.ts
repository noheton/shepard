/**
 * APISIMP-TYPED-PREDECESSOR-NUMERIC-ID
 * Verifies that the predecessorRelationshipTypesMap computation
 * keys by predecessorAppId (UUID v7), not the deprecated numeric predecessorId.
 */
import { describe, it, expect } from "vitest";

interface TypedPredecessorSummary {
  predecessorAppId: string;
  predecessorId?: number;
  predecessorName: string;
  predecessorStatus: string | null;
  relationshipType: string;
}

/** Mirrors the computed in index.vue — keyed by appId. */
function buildRelationshipTypesMap(
  summaries: TypedPredecessorSummary[],
): Map<string, string> {
  const map = new Map<string, string>();
  for (const tp of summaries) {
    if (
      tp.predecessorAppId &&
      tp.relationshipType &&
      tp.relationshipType !== "prov:wasInformedBy"
    ) {
      map.set(tp.predecessorAppId, tp.relationshipType);
    }
  }
  return map;
}

describe("typedPredecessorJoin — appId-keyed map", () => {
  it("keys by predecessorAppId, not numeric predecessorId", () => {
    const summaries: TypedPredecessorSummary[] = [
      {
        predecessorAppId: "018f1234-0000-7000-8000-000000000001",
        predecessorId: 42,
        predecessorName: "TR-004",
        predecessorStatus: "READY",
        relationshipType: "prov:wasRevisionOf",
      },
    ];
    const map = buildRelationshipTypesMap(summaries);
    expect(map.has("018f1234-0000-7000-8000-000000000001")).toBe(true);
    expect(map.get("018f1234-0000-7000-8000-000000000001")).toBe(
      "prov:wasRevisionOf",
    );
    // Numeric key must NOT be present
    expect(map.has(42 as unknown as string)).toBe(false);
  });

  it("works when predecessorId is absent (post-L2b-only instance)", () => {
    const summaries: TypedPredecessorSummary[] = [
      {
        predecessorAppId: "018f5678-0000-7000-8000-000000000002",
        // predecessorId absent
        predecessorName: "TR-005",
        predecessorStatus: null,
        relationshipType: "fair2r:repairs",
      },
    ];
    const map = buildRelationshipTypesMap(summaries);
    expect(map.get("018f5678-0000-7000-8000-000000000002")).toBe(
      "fair2r:repairs",
    );
  });

  it("excludes default prov:wasInformedBy from the map (sparse)", () => {
    const summaries: TypedPredecessorSummary[] = [
      {
        predecessorAppId: "018fabc0-0000-7000-8000-000000000003",
        predecessorName: "TR-006",
        predecessorStatus: "PUBLISHED",
        relationshipType: "prov:wasInformedBy",
      },
    ];
    const map = buildRelationshipTypesMap(summaries);
    expect(map.size).toBe(0);
  });

  it("handles multiple predecessors mixed default/typed", () => {
    const summaries: TypedPredecessorSummary[] = [
      {
        predecessorAppId: "018fdef0-0000-7000-8000-000000000004",
        predecessorName: "TR-007",
        predecessorStatus: "READY",
        relationshipType: "prov:wasInformedBy",
      },
      {
        predecessorAppId: "018fdef0-0000-7000-8000-000000000005",
        predecessorName: "TR-008",
        predecessorStatus: "READY",
        relationshipType: "prov:wasRevisionOf",
      },
    ];
    const map = buildRelationshipTypesMap(summaries);
    expect(map.size).toBe(1);
    expect(map.get("018fdef0-0000-7000-8000-000000000005")).toBe(
      "prov:wasRevisionOf",
    );
  });
});
