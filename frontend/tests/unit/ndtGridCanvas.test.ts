import { describe, it, expect } from "vitest";
import {
  buildCellLookup,
  cellKey,
  cellValueRange,
  computeMeanDtColours,
  computePassFailColours,
  resolveColourMap,
  type NdtGridCell,
} from "~/utils/ndtGridCanvas";

// ── fixtures ────────────────────────────────────────────────────────────────

function makeCell(
  row: string,
  col: string,
  opts: Partial<NdtGridCell> = {},
): NdtGridCell {
  return {
    row,
    col,
    dataObjectAppId: `app-${row}-${col}`,
    status: "RESOLVED",
    ...opts,
  };
}

// ── resolveColourMap ─────────────────────────────────────────────────────────

describe("resolveColourMap", () => {
  it("passes through known names unchanged", () => {
    expect(resolveColourMap("inferno")).toBe("inferno");
    expect(resolveColourMap("viridis")).toBe("viridis");
    expect(resolveColourMap("plasma")).toBe("plasma");
    expect(resolveColourMap("heat")).toBe("heat");
    expect(resolveColourMap("cool")).toBe("cool");
  });

  it("maps hot → heat", () => {
    expect(resolveColourMap("hot")).toBe("heat");
  });

  it("falls back to inferno for unknown names", () => {
    expect(resolveColourMap("jet")).toBe("inferno");
    expect(resolveColourMap("")).toBe("inferno");
    expect(resolveColourMap("rainbow")).toBe("inferno");
  });
});

// ── cellKey ──────────────────────────────────────────────────────────────────

describe("cellKey", () => {
  it("produces unique keys", () => {
    const k1 = cellKey("L8", "S4-M13");
    const k2 = cellKey("L18", "S4-M13");
    const k3 = cellKey("L8", "S5-M1");
    expect(k1).not.toBe(k2);
    expect(k1).not.toBe(k3);
    expect(k2).not.toBe(k3);
  });

  it("is deterministic", () => {
    expect(cellKey("L8", "S4-M13")).toBe(cellKey("L8", "S4-M13"));
  });
});

// ── buildCellLookup ──────────────────────────────────────────────────────────

describe("buildCellLookup", () => {
  it("returns empty map for no cells", () => {
    expect(buildCellLookup([])).toEqual(new Map());
  });

  it("finds a cell by row+col", () => {
    const cells = [makeCell("L8", "S4-M13"), makeCell("L18", "S4-M13")];
    const lookup = buildCellLookup(cells);
    expect(lookup.get(cellKey("L8", "S4-M13"))?.dataObjectAppId).toBe("app-L8-S4-M13");
    expect(lookup.get(cellKey("L18", "S4-M13"))?.dataObjectAppId).toBe("app-L18-S4-M13");
    expect(lookup.get(cellKey("L8", "S5-M1"))).toBeUndefined();
  });

  it("last cell wins on duplicate keys", () => {
    const c1 = makeCell("L8", "S4-M13", { dataObjectAppId: "first" });
    const c2 = makeCell("L8", "S4-M13", { dataObjectAppId: "second" });
    const lookup = buildCellLookup([c1, c2]);
    expect(lookup.get(cellKey("L8", "S4-M13"))?.dataObjectAppId).toBe("second");
  });
});

// ── cellValueRange ───────────────────────────────────────────────────────────

describe("cellValueRange", () => {
  it("returns null for empty cells", () => {
    expect(cellValueRange([])).toBeNull();
  });

  it("returns null when no cells have a value", () => {
    const cells = [makeCell("L8", "S4"), makeCell("L18", "S4")];
    expect(cellValueRange(cells)).toBeNull();
  });

  it("computes min/max across valued cells", () => {
    const cells = [
      makeCell("L8", "S4", { value: 3.5 }),
      makeCell("L18", "S4", { value: 7.2 }),
      makeCell("L8", "S5", { value: 1.1 }),
      makeCell("L18", "S5"), // no value
    ];
    const r = cellValueRange(cells);
    expect(r).not.toBeNull();
    expect(r!.min).toBeCloseTo(1.1);
    expect(r!.max).toBeCloseTo(7.2);
  });

  it("returns same min and max for a single-valued cell", () => {
    const r = cellValueRange([makeCell("L8", "S4", { value: 5.0 })]);
    expect(r!.min).toBe(5.0);
    expect(r!.max).toBe(5.0);
  });
});

// ── computeMeanDtColours ──────────────────────────────────────────────────────

describe("computeMeanDtColours", () => {
  it("returns empty map for cells with no values", () => {
    const cells = [makeCell("L8", "S4"), makeCell("L18", "S4")];
    expect(computeMeanDtColours(cells, "inferno").size).toBe(0);
  });

  it("generates CSS rgb strings for valued cells", () => {
    const cells = [
      makeCell("L8", "S4", { value: 0 }),
      makeCell("L18", "S4", { value: 10 }),
    ];
    const colours = computeMeanDtColours(cells, "inferno");
    expect(colours.size).toBe(2);
    const low = colours.get(cellKey("L8", "S4"))!;
    const high = colours.get(cellKey("L18", "S4"))!;
    expect(low).toMatch(/^rgb\(\d+,\d+,\d+\)$/);
    expect(high).toMatch(/^rgb\(\d+,\d+,\d+\)$/);
    // High value should differ from low value for a 2-stop range
    expect(low).not.toBe(high);
  });

  it("uses t=0.5 (midpoint colour) when all values are equal", () => {
    const cells = [
      makeCell("L8", "S4", { value: 5 }),
      makeCell("L18", "S4", { value: 5 }),
    ];
    const colours = computeMeanDtColours(cells, "viridis");
    expect(colours.size).toBe(2);
    const a = colours.get(cellKey("L8", "S4"))!;
    const b = colours.get(cellKey("L18", "S4"))!;
    expect(a).toBe(b); // both midpoint
  });

  it("accepts heat colourMap (no error)", () => {
    const cells = [makeCell("L8", "S4", { value: 1 }), makeCell("L18", "S4", { value: 2 })];
    expect(() => computeMeanDtColours(cells, "heat")).not.toThrow();
  });

  it("skips cells missing value without crashing", () => {
    const cells = [
      makeCell("L8", "S4", { value: 1 }),
      makeCell("L18", "S4"), // no value
      makeCell("L8", "S5", { value: 2 }),
    ];
    const colours = computeMeanDtColours(cells, "plasma");
    expect(colours.size).toBe(2);
    expect(colours.has(cellKey("L18", "S4"))).toBe(false);
  });
});

// ── computePassFailColours ────────────────────────────────────────────────────

describe("computePassFailColours", () => {
  it("maps OK → green, NOK → red, missing → grey", () => {
    const cells = [
      makeCell("L8", "S4", { quality: "OK" }),
      makeCell("L18", "S4", { quality: "NOK" }),
      makeCell("L8", "S5", { quality: undefined }),
    ];
    const colours = computePassFailColours(cells);
    expect(colours.get(cellKey("L8", "S4"))).toBe("#4caf50");
    expect(colours.get(cellKey("L18", "S4"))).toBe("#f44336");
    expect(colours.get(cellKey("L8", "S5"))).toBe("#9e9e9e");
  });

  it("accepts PASS synonym for OK", () => {
    const cells = [makeCell("L8", "S4", { quality: "PASS" })];
    expect(computePassFailColours(cells).get(cellKey("L8", "S4"))).toBe("#4caf50");
  });

  it("accepts FAIL synonym for NOK", () => {
    const cells = [makeCell("L8", "S4", { quality: "FAIL" })];
    expect(computePassFailColours(cells).get(cellKey("L8", "S4"))).toBe("#f44336");
  });

  it("is case-insensitive", () => {
    const cells = [
      makeCell("L8", "S4", { quality: "ok" }),
      makeCell("L18", "S4", { quality: "nok" }),
    ];
    const colours = computePassFailColours(cells);
    expect(colours.get(cellKey("L8", "S4"))).toBe("#4caf50");
    expect(colours.get(cellKey("L18", "S4"))).toBe("#f44336");
  });

  it("returns empty map for no cells", () => {
    expect(computePassFailColours([]).size).toBe(0);
  });
});
