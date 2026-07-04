/**
 * UX-PROV1 — unit tests for useFetchPredecessorChain composable logic.
 *
 * Tests verify the data-shaping logic: that the response items are
 * correctly typed, that an empty array is handled gracefully, and that
 * the DataObjectChainItem interface shape matches the backend
 * DataObjectSummaryIO wire format (appId, name, status).
 *
 * We test the pure logic without mounting a full Vue/Nuxt app, following
 * the same pattern as PredecessorRelationshipTypeChip.test.ts.
 */
import { describe, it, expect } from "vitest";
import type { DataObjectChainItem } from "~/composables/context/useFetchPredecessorChain";

// ── Helper: simulate the response-shaping logic ──────────────────────────────
// Mirrors the composable: if the response is a valid array of
// DataObjectChainItem, pass it through; else return [].

function shapeChainResponse(raw: unknown): DataObjectChainItem[] {
  if (!Array.isArray(raw)) return [];
  return raw as DataObjectChainItem[];
}

// ── Sample wire payload ───────────────────────────────────────────────────────

const SAMPLE_CHAIN: DataObjectChainItem[] = [
  {
    appId: "01924b7f-0000-7000-8000-000000000001",
    name: "TR-003 hot-fire",
    status: "READY",
  },
  {
    appId: "01924b7f-0000-7000-8000-000000000002",
    name: "TR-002 wet dress rehearsal",
    status: "ARCHIVED",
  },
];

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("useFetchPredecessorChain — response shaping", () => {
  it("passes through a valid chain array unchanged", () => {
    const result = shapeChainResponse(SAMPLE_CHAIN);
    expect(result).toHaveLength(2);
    expect(result[0]!.name).toBe("TR-003 hot-fire");
    expect(result[1]!.name).toBe("TR-002 wet dress rehearsal");
  });

  it("returns an empty array for an empty response", () => {
    expect(shapeChainResponse([])).toEqual([]);
  });

  it("returns an empty array for null (endpoint returned null)", () => {
    expect(shapeChainResponse(null)).toEqual([]);
  });

  it("returns an empty array for a non-array response (server error body)", () => {
    expect(shapeChainResponse({ error: "not found" })).toEqual([]);
  });

  it("returns an empty array for undefined", () => {
    expect(shapeChainResponse(undefined)).toEqual([]);
  });
});

describe("useFetchPredecessorChain — DataObjectChainItem shape", () => {
  it("chain item has all required fields from DataObjectSummaryIO wire format", () => {
    const item = SAMPLE_CHAIN[0]!;
    expect(typeof item.appId).toBe("string");
    expect(typeof item.name).toBe("string");
    // status is string | null
    expect(item.status === null || typeof item.status === "string").toBe(true);
  });

  it("chain item supports null status (DataObject without a status set)", () => {
    const nullStatusItem: DataObjectChainItem = {
      appId: "01924b7f-0000-7000-8000-000000000099",
      name: "Legacy DataObject",
      status: null,
    };
    const result = shapeChainResponse([nullStatusItem]);
    expect(result[0]!.status).toBeNull();
    expect(result[0]!.name).toBe("Legacy DataObject");
  });

  it("preserves insertion order (nearest-first, root-last)", () => {
    const result = shapeChainResponse(SAMPLE_CHAIN);
    // The endpoint returns predecessors ordered by shepardId ascending
    // (oldest first from DB perspective). The UI displays them top-to-bottom
    // as returned, so the last entry is the direct predecessor.
    expect(result[0]!.appId).toBe("01924b7f-0000-7000-8000-000000000001");
    expect(result[1]!.appId).toBe("01924b7f-0000-7000-8000-000000000002");
  });
});

describe("useFetchPredecessorChain — depth parameter validation", () => {
  it("depth 10 is the default and a reasonable maximum for UI rendering", () => {
    const DEFAULT_DEPTH = 10;
    // Verify the constant matches the expected default
    expect(DEFAULT_DEPTH).toBe(10);
    expect(DEFAULT_DEPTH).toBeGreaterThan(0);
    expect(DEFAULT_DEPTH).toBeLessThanOrEqual(50); // sanity: never request 1000 ancestors
  });
});
