import { describe, it, expect } from "vitest";
import { computeLineageState } from "~/utils/lineageState";

/**
 * Unit tests for the three-branch lineage empty-state logic in
 * CollectionLineageGraph. Pure logic test — no Vuetify mount needed.
 */
describe("computeLineageState", () => {
  // Branch (a): loading
  it("returns 'loading' when isLoading is true, regardless of items or edges", () => {
    expect(computeLineageState(true, [], false)).toBe("loading");
    expect(computeLineageState(true, [{ id: 1 }], true)).toBe("loading");
    expect(computeLineageState(true, [{ id: 1 }], false)).toBe("loading");
  });

  // Branch (b): no DataObjects
  it("returns 'no-dos' when not loading and items array is empty", () => {
    expect(computeLineageState(false, [], false)).toBe("no-dos");
    expect(computeLineageState(false, [], true)).toBe("no-dos");
  });

  // Branch (c): DataObjects exist but no edges
  it("returns 'no-edges' when items exist but hasEdges is false", () => {
    const dos = [{ id: 1 }, { id: 2 }];
    expect(computeLineageState(false, dos, false)).toBe("no-edges");
  });

  // Branch (d): graph with edges
  it("returns 'graph' when items exist and hasEdges is true", () => {
    const dos = [{ id: 1 }, { id: 2 }];
    expect(computeLineageState(false, dos, true)).toBe("graph");
  });

  // Loading takes priority over items/edges
  it("'loading' takes priority over 'graph' state", () => {
    expect(computeLineageState(true, [{ id: 1 }, { id: 2 }], true)).toBe("loading");
  });

  // 'no-dos' takes priority over edge state
  it("'no-dos' is returned even when hasEdges is true but items is empty", () => {
    // This edge case shouldn't occur in practice (no items → no edges),
    // but the logic must be sound: empty items always wins over hasEdges.
    expect(computeLineageState(false, [], true)).toBe("no-dos");
  });
});
