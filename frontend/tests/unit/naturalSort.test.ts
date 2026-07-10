/**
 * UIRULE-DROPDOWN-SEARCH-SORT — unit tests for the shared natural-ordering
 * primitive that every dropdown's :items now runs through.
 *
 * The contract: numeric segments compare numerically (so "Track 2" precedes
 * "Track 10", NOT the lexicographic "10" < "2"), comparison is
 * case-insensitive, the input array is never mutated, and an object selector
 * lets callers sort {title,value} option shapes.
 */
import { describe, it, expect } from "vitest";
import { naturalSort, naturalCompare } from "~/utils/naturalSort";

describe("naturalCompare", () => {
  it("orders numeric segments numerically, not lexicographically", () => {
    expect(naturalCompare("Track 2", "Track 10")).toBeLessThan(0);
    expect(naturalCompare("Track 10", "Track 2")).toBeGreaterThan(0);
    expect(naturalCompare("PlyGroup 2", "PlyGroup 10")).toBeLessThan(0);
  });

  it("is case-insensitive", () => {
    expect(naturalCompare("apple", "Apple")).toBe(0);
    expect(naturalCompare("BANANA", "banana")).toBe(0);
  });

  it("tolerates null / undefined", () => {
    expect(naturalCompare(null as unknown as string, "")).toBe(0);
    expect(naturalCompare(undefined as unknown as string, "")).toBe(0);
  });
});

describe("naturalSort — string/number option arrays", () => {
  it("orders the canonical Track 10/2/1 example naturally", () => {
    expect(naturalSort(["Track 10", "Track 2", "Track 1"])).toEqual([
      "Track 1",
      "Track 2",
      "Track 10",
    ]);
  });

  it("orders channel-style names with bracketed numeric suffixes", () => {
    const input = [
      "TapeForce_TapeActForce[10]",
      "TapeForce_TapeActForce[2]",
      "TapeForce_TapeActForce[1]",
    ];
    expect(naturalSort(input)).toEqual([
      "TapeForce_TapeActForce[1]",
      "TapeForce_TapeActForce[2]",
      "TapeForce_TapeActForce[10]",
    ]);
  });

  it("orders zero-based numeric-suffix names (area0..area10) naturally", () => {
    expect(naturalSort(["area10", "area2", "area0", "area1"])).toEqual([
      "area0",
      "area1",
      "area2",
      "area10",
    ]);
  });

  it("is case-insensitive across a mixed-case list", () => {
    expect(naturalSort(["beta", "Alpha", "gamma", "Delta"])).toEqual([
      "Alpha",
      "beta",
      "Delta",
      "gamma",
    ]);
  });

  it("does NOT mutate the source array", () => {
    const source = ["Track 10", "Track 2", "Track 1"];
    const copy = [...source];
    naturalSort(source);
    expect(source).toEqual(copy);
  });
});

describe("naturalSort — object option arrays via a label selector", () => {
  it("orders {title,value} options by their title", () => {
    const items = [
      { title: "Ply 10", value: "c" },
      { title: "Ply 2", value: "b" },
      { title: "Ply 1", value: "a" },
    ];
    expect(naturalSort(items, i => i.title).map(i => i.value)).toEqual([
      "a",
      "b",
      "c",
    ]);
  });

  it("preserves values while reordering (stable value/label pairing)", () => {
    const items = [
      { title: "Frame 12", value: 12 },
      { title: "Frame 3", value: 3 },
    ];
    const sorted = naturalSort(items, i => i.title);
    expect(sorted[0]).toEqual({ title: "Frame 3", value: 3 });
    expect(sorted[1]).toEqual({ title: "Frame 12", value: 12 });
  });
});
