/**
 * Trace3DView (#142) — unit tests for the pure logic extracted from
 * frontend/components/container/timeseries/Trace3DView.vue.
 *
 * The component uses Three.js / WebGL (unavailable in Vitest node env) and
 * the Nuxt Vuetify rendering chain, so we test only the pure adapter logic:
 *   - flat-array → TracePoint[] conversion
 *   - colorScheme → ColormapName bridge
 *   - valueStats (min/max for legend labels)
 *
 * Three.js integration is exercised by the shapes/render.vue e2e smoke run.
 */
import { describe, it, expect } from "vitest";
import { colormapRgb, normalizeValues, type ColormapName } from "../../utils/colormap";

// ── Helpers mirrored from Trace3DView.vue (pure logic, no Vue runtime) ────────

type Trace3DColorScheme = "heat" | "cool" | "viridis";

interface TracePoint {
  x: number; y: number; z: number; value: number; t: number;
}

function buildTracePoints(
  xData: number[], yData: number[], zData: number[], valueData: number[],
): TracePoint[] {
  const n = xData.length;
  if (n === 0) return [];
  return Array.from({ length: n }, (_, i) => ({
    x:     xData[i]     ?? 0,
    y:     yData[i]     ?? 0,
    z:     zData[i]     ?? 0,
    value: valueData[i] ?? NaN,
    t:     i,
  }));
}

function toColormapName(scheme: Trace3DColorScheme): ColormapName {
  switch (scheme) {
    case "cool":    return "cool";
    case "viridis": return "viridis";
    case "heat":
    default:        return "heat";
  }
}

function computeValueStats(valueData: number[]): { min: number; max: number } | null {
  const vals = valueData.filter(v => isFinite(v));
  if (vals.length === 0) return null;
  return { min: Math.min(...vals), max: Math.max(...vals) };
}

// ── buildTracePoints ──────────────────────────────────────────────────────────

describe("buildTracePoints", () => {
  it("returns empty array for empty input", () => {
    expect(buildTracePoints([], [], [], [])).toHaveLength(0);
  });

  it("maps x/y/z/value at each index", () => {
    const pts = buildTracePoints([1, 2], [3, 4], [5, 6], [7, 8]);
    expect(pts).toHaveLength(2);
    expect(pts[0]).toMatchObject({ x: 1, y: 3, z: 5, value: 7, t: 0 });
    expect(pts[1]).toMatchObject({ x: 2, y: 4, z: 6, value: 8, t: 1 });
  });

  it("t is the array index (no real timestamp in flat-array API)", () => {
    const pts = buildTracePoints([0, 0, 0], [0, 0, 0], [0, 0, 0], [0, 0, 0]);
    expect(pts.map(p => p.t)).toEqual([0, 1, 2]);
  });

  it("uses NaN for out-of-bounds valueData entries", () => {
    // valueData shorter than xData — out-of-range access falls back to NaN
    const pts = buildTracePoints([1, 2, 3], [0, 0, 0], [0, 0, 0], [10]);
    expect(pts[0]!.value).toBe(10);
    expect(isNaN(pts[1]!.value)).toBe(true);
    expect(isNaN(pts[2]!.value)).toBe(true);
  });

  it("single-point path produces exactly one TracePoint", () => {
    const pts = buildTracePoints([9.9], [1.1], [2.2], [42.0]);
    expect(pts).toHaveLength(1);
    expect(pts[0]).toMatchObject({ x: 9.9, y: 1.1, z: 2.2, value: 42.0, t: 0 });
  });
});

// ── toColormapName ────────────────────────────────────────────────────────────

describe("toColormapName", () => {
  it("maps 'heat' to 'heat'", () => {
    expect(toColormapName("heat")).toBe("heat");
  });

  it("maps 'cool' to 'cool'", () => {
    expect(toColormapName("cool")).toBe("cool");
  });

  it("maps 'viridis' to 'viridis'", () => {
    expect(toColormapName("viridis")).toBe("viridis");
  });

  it("all colorScheme values produce a valid ColormapName accepted by colormapRgb", () => {
    const schemes: Trace3DColorScheme[] = ["heat", "cool", "viridis"];
    for (const scheme of schemes) {
      const name = toColormapName(scheme);
      // colormapRgb must not throw and must return RGB in [0, 1]³
      const [r, g, b] = colormapRgb(0.5, name);
      expect(r).toBeGreaterThanOrEqual(0);
      expect(r).toBeLessThanOrEqual(1);
      expect(g).toBeGreaterThanOrEqual(0);
      expect(g).toBeLessThanOrEqual(1);
      expect(b).toBeGreaterThanOrEqual(0);
      expect(b).toBeLessThanOrEqual(1);
    }
  });
});

// ── computeValueStats ─────────────────────────────────────────────────────────

describe("computeValueStats", () => {
  it("returns null for empty array", () => {
    expect(computeValueStats([])).toBeNull();
  });

  it("returns null when all values are NaN", () => {
    expect(computeValueStats([NaN, NaN])).toBeNull();
  });

  it("returns null when all values are Infinity", () => {
    expect(computeValueStats([Infinity, -Infinity])).toBeNull();
  });

  it("returns correct min/max for a uniform array", () => {
    expect(computeValueStats([5, 5, 5])).toEqual({ min: 5, max: 5 });
  });

  it("returns correct min/max for a varied array", () => {
    expect(computeValueStats([10, 3, 7, 1])).toEqual({ min: 1, max: 10 });
  });

  it("ignores NaN entries within a mixed array", () => {
    expect(computeValueStats([NaN, 2, 8, NaN])).toEqual({ min: 2, max: 8 });
  });

  it("handles negative values", () => {
    expect(computeValueStats([-5, 0, 5])).toEqual({ min: -5, max: 5 });
  });
});

// ── heat and cool colormaps (added in #142) ───────────────────────────────────

describe("colormapRgb — heat (added for Trace3DView)", () => {
  it("low end (t=0) is blue", () => {
    const [r, , b] = colormapRgb(0, "heat");
    expect(b).toBeGreaterThan(0.9);
    expect(r).toBeLessThan(0.1);
  });

  it("high end (t=1) is red", () => {
    const [r, , b] = colormapRgb(1, "heat");
    expect(r).toBeGreaterThan(0.9);
    expect(b).toBeLessThan(0.1);
  });

  it("midpoint is bright (yellow/green region)", () => {
    const [r, g] = colormapRgb(0.5, "heat");
    // At t=0.5 we should be in the cyan→yellow transition — both r and g are high
    expect(r + g).toBeGreaterThan(1.2);
  });

  it("all values in [0, 1]", () => {
    for (let i = 0; i <= 10; i++) {
      const [r, g, b] = colormapRgb(i / 10, "heat");
      expect(r).toBeGreaterThanOrEqual(0);
      expect(r).toBeLessThanOrEqual(1);
      expect(g).toBeGreaterThanOrEqual(0);
      expect(g).toBeLessThanOrEqual(1);
      expect(b).toBeGreaterThanOrEqual(0);
      expect(b).toBeLessThanOrEqual(1);
    }
  });
});

describe("colormapRgb — cool (added for Trace3DView)", () => {
  it("all values in [0, 1]", () => {
    for (let i = 0; i <= 10; i++) {
      const [r, g, b] = colormapRgb(i / 10, "cool");
      expect(r).toBeGreaterThanOrEqual(0);
      expect(r).toBeLessThanOrEqual(1);
      expect(g).toBeGreaterThanOrEqual(0);
      expect(g).toBeLessThanOrEqual(1);
      expect(b).toBeGreaterThanOrEqual(0);
      expect(b).toBeLessThanOrEqual(1);
    }
  });

  it("endpoints differ (gradient is not flat)", () => {
    const lo = colormapRgb(0, "cool");
    const hi = colormapRgb(1, "cool");
    const diff = Math.abs(lo[0] - hi[0]) + Math.abs(lo[1] - hi[1]) + Math.abs(lo[2] - hi[2]);
    expect(diff).toBeGreaterThan(0.5);
  });
});

// ── prop validation — round-trip through the full adapter ────────────────────

describe("Trace3DView adapter round-trip", () => {
  it("AFP robot trace: 5-point path produces 5 TracePoints with correct geometry", () => {
    const x = [0.0, 0.1, 0.2, 0.3, 0.4];
    const y = [0.0, 0.0, 0.1, 0.1, 0.0];
    const z = [0.0, 0.0, 0.0, 0.1, 0.2];
    const v = [20.0, 22.5, 24.0, 25.5, 30.0]; // TCP temperature °C

    const pts = buildTracePoints(x, y, z, v);
    expect(pts).toHaveLength(5);
    expect(pts[0]!.x).toBeCloseTo(0.0);
    expect(pts[4]!.x).toBeCloseTo(0.4);
    expect(pts[2]!.value).toBeCloseTo(24.0);

    const stats = computeValueStats(v);
    expect(stats).toEqual({ min: 20.0, max: 30.0 });

    // normalizeValues should map min→0, max→1
    const normed = normalizeValues(v);
    expect(normed[0]).toBeCloseTo(0.0);
    expect(normed[4]).toBeCloseTo(1.0);
  });

  it("single-channel color mapping is deterministic", () => {
    const pts = buildTracePoints([0, 1], [0, 1], [0, 1], [0, 100]);
    const normed = normalizeValues(pts.map(p => p.value));
    const c0 = colormapRgb(normed[0]!, "heat");
    const c1 = colormapRgb(normed[1]!, "heat");
    // Low value → blue; high value → red
    expect(c0[2]).toBeGreaterThan(0.9); // blue channel dominant at t=0
    expect(c1[0]).toBeGreaterThan(0.9); // red channel dominant at t=1
  });
});
