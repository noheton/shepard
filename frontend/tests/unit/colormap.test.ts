import { describe, it, expect } from "vitest";
import { colormapRgb, normalizeValues, lerpSeries } from "../../utils/colormap";

describe("colormapRgb", () => {
  it("returns near-black at t=0 for inferno", () => {
    const [r, g, b] = colormapRgb(0, "inferno");
    expect(r).toBeLessThan(0.05);
    expect(g).toBeLessThan(0.05);
    expect(b).toBeLessThan(0.05);
  });

  it("returns near-yellow at t=1 for inferno", () => {
    const [r, , b] = colormapRgb(1, "inferno");
    expect(r).toBeGreaterThan(0.9);
    expect(b).toBeGreaterThan(0.5);
  });

  it("clamps t below 0", () => {
    expect(colormapRgb(-1, "inferno")).toEqual(colormapRgb(0, "inferno"));
  });

  it("clamps t above 1", () => {
    expect(colormapRgb(2, "inferno")).toEqual(colormapRgb(1, "inferno"));
  });

  it("viridis t=0 is dark purple", () => {
    const [r, g, b] = colormapRgb(0, "viridis");
    expect(b).toBeGreaterThan(r);
    expect(b).toBeGreaterThan(g);
  });

  it("viridis t=1 is bright yellow", () => {
    const [r, g, b] = colormapRgb(1, "viridis");
    expect(r).toBeGreaterThan(0.9);
    expect(g).toBeGreaterThan(0.8);
    expect(b).toBeLessThan(0.25);
  });

  it("plasma midpoint has strong red", () => {
    const [r] = colormapRgb(0.5, "plasma");
    expect(r).toBeGreaterThan(0.6);
  });

  it("all colormaps return values in [0, 1]", () => {
    for (const name of ["inferno", "viridis", "plasma"] as const) {
      for (let t = 0; t <= 1; t += 0.1) {
        const [r, g, b] = colormapRgb(t, name);
        expect(r).toBeGreaterThanOrEqual(0);
        expect(r).toBeLessThanOrEqual(1);
        expect(g).toBeGreaterThanOrEqual(0);
        expect(g).toBeLessThanOrEqual(1);
        expect(b).toBeGreaterThanOrEqual(0);
        expect(b).toBeLessThanOrEqual(1);
      }
    }
  });

  it("defaults to inferno when name is omitted", () => {
    expect(colormapRgb(0.5)).toEqual(colormapRgb(0.5, "inferno"));
  });
});

describe("normalizeValues", () => {
  it("normalizes [0, 5, 10] to [0, 0.5, 1]", () => {
    expect(normalizeValues([0, 5, 10])).toEqual([0, 0.5, 1]);
  });

  it("returns [0.5, ...] when all values are equal", () => {
    expect(normalizeValues([7, 7, 7])).toEqual([0.5, 0.5, 0.5]);
  });

  it("returns empty array for empty input", () => {
    expect(normalizeValues([])).toEqual([]);
  });

  it("handles negative values", () => {
    const result = normalizeValues([-10, 0, 10]);
    expect(result[0]).toBe(0);
    expect(result[1]).toBe(0.5);
    expect(result[2]).toBe(1);
  });
});

describe("lerpSeries", () => {
  const pts: [number, number][] = [[0, 0], [100, 10], [200, 20]];

  it("returns exact value at knot points", () => {
    expect(lerpSeries(pts, 0)).toBe(0);
    expect(lerpSeries(pts, 100)).toBe(10);
    expect(lerpSeries(pts, 200)).toBe(20);
  });

  it("interpolates linearly between knots", () => {
    expect(lerpSeries(pts, 50)).toBe(5);
    expect(lerpSeries(pts, 150)).toBe(15);
  });

  it("clamps below first timestamp", () => {
    expect(lerpSeries(pts, -50)).toBe(0);
  });

  it("clamps above last timestamp", () => {
    expect(lerpSeries(pts, 300)).toBe(20);
  });

  it("returns 0 for empty series", () => {
    expect(lerpSeries([], 50)).toBe(0);
  });

  it("single-point series always returns that value", () => {
    expect(lerpSeries([[50, 99]], 0)).toBe(99);
    expect(lerpSeries([[50, 99]], 100)).toBe(99);
    expect(lerpSeries([[50, 99]], 50)).toBe(99);
  });
});
