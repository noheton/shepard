/**
 * BrushTraceView unit tests — SPATIAL-V6-004
 *
 * The Vue component itself wraps Three.js and is not mountable in the node
 * test environment. Instead we test the pure data-handling logic that the
 * component depends on:
 *
 *   1. URL construction for the spatial payload endpoint
 *   2. Point-sorting by timestamp (the ordering invariant for the trace line)
 *   3. Position normalisation to a unit cube (the render-scale invariant)
 *
 * These are the load-bearing helpers that determine whether the 3D viewer
 * draws a correct, centred trace. Component-level rendering (loading/error/
 * empty states) is covered by the Playwright e2e suite that runs against the
 * deployed spatial container page.
 */
import { describe, it, expect } from "vitest";

// ── type mirror (not importing from component to avoid Three.js side-effects) ──

interface SpatialPoint {
  timestamp?: number;
  x: number;
  y: number;
  z: number;
  measurements: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

// ── helpers extracted from the component logic ────────────────────────────────

/** Builds the v1 payload URL for a given containerId and limit. */
function tracePayloadUrl(containerId: number, limit = 5000): string {
  return `/shepard/api/spatial-data-containers/${containerId}/payload?limit=${limit}`;
}

/**
 * Sorts points ascending by timestamp.
 * Points without a timestamp (undefined) sort to the front.
 */
function sortByTimestamp(points: SpatialPoint[]): SpatialPoint[] {
  return [...points].sort((a, b) => (a.timestamp ?? 0) - (b.timestamp ?? 0));
}

/**
 * Normalises x/y/z coordinates to a unit cube centred at origin.
 * Returns the scale factor and centre used (for test assertions).
 */
function normalisePositions(points: SpatialPoint[]): {
  normX: number[];
  normY: number[];
  normZ: number[];
  range: number;
} {
  const xs = points.map(p => p.x);
  const ys = points.map(p => p.y);
  const zs = points.map(p => p.z);
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = Math.min(...ys), yMax = Math.max(...ys);
  const zMin = Math.min(...zs), zMax = Math.max(...zs);
  const range = Math.max(xMax - xMin, yMax - yMin, zMax - zMin, 1e-9);
  const cx = (xMin + xMax) / 2;
  const cy = (yMin + yMax) / 2;
  const cz = (zMin + zMax) / 2;
  return {
    normX: xs.map(x => (x - cx) / range),
    normY: ys.map(y => (y - cy) / range),
    normZ: zs.map(z => (z - cz) / range),
    range,
  };
}

// ── fixtures ──────────────────────────────────────────────────────────────────

const THREE_POINTS: SpatialPoint[] = [
  { timestamp: 3000, x: 100, y: 200, z: 300, measurements: {} },
  { timestamp: 1000, x: 0,   y: 0,   z: 0,   measurements: {} },
  { timestamp: 2000, x: 50,  y: 100, z: 150, measurements: {} },
];

// ── URL construction ──────────────────────────────────────────────────────────

describe("tracePayloadUrl", () => {
  it("builds the correct v1 payload URL for a given containerId", () => {
    expect(tracePayloadUrl(42)).toBe(
      "/shepard/api/spatial-data-containers/42/payload?limit=5000",
    );
  });

  it("honours a custom limit", () => {
    expect(tracePayloadUrl(7, 100)).toBe(
      "/shepard/api/spatial-data-containers/7/payload?limit=100",
    );
  });

  it("uses default limit of 5000 when not provided", () => {
    expect(tracePayloadUrl(1)).toContain("limit=5000");
  });
});

// ── timestamp sort ────────────────────────────────────────────────────────────

describe("sortByTimestamp", () => {
  it("sorts points ascending by timestamp", () => {
    const sorted = sortByTimestamp(THREE_POINTS);
    expect(sorted.map(p => p.timestamp)).toEqual([1000, 2000, 3000]);
  });

  it("does not mutate the original array", () => {
    const original = [...THREE_POINTS];
    sortByTimestamp(THREE_POINTS);
    expect(THREE_POINTS).toEqual(original);
  });

  it("sorts points without timestamp to the front (timestamp treated as 0)", () => {
    const pts: SpatialPoint[] = [
      { timestamp: 5000, x: 1, y: 1, z: 1, measurements: {} },
      { x: 2, y: 2, z: 2, measurements: {} },   // no timestamp
    ];
    const sorted = sortByTimestamp(pts);
    expect(sorted[0]!.x).toBe(2); // the one without timestamp sorts first
  });
});

// ── normalisation ─────────────────────────────────────────────────────────────

describe("normalisePositions", () => {
  it("centres the trace at origin", () => {
    const { normX, normY } = normalisePositions(THREE_POINTS);
    // The mean of normalised values should be close to 0.
    const meanX = normX.reduce((s, v) => s + v, 0) / normX.length;
    const meanY = normY.reduce((s, v) => s + v, 0) / normY.length;
    expect(Math.abs(meanX)).toBeLessThan(1e-6);
    expect(Math.abs(meanY)).toBeLessThan(1e-6);
  });

  it("scales so the longest axis spans [-0.5, 0.5]", () => {
    const { normX, normY, normZ } = normalisePositions(THREE_POINTS);
    const allVals = [...normX, ...normY, ...normZ];
    const maxAbs = Math.max(...allVals.map(Math.abs));
    expect(maxAbs).toBeCloseTo(0.5, 4);
  });

  it("handles a single-point degenerate case without NaN", () => {
    const singlePoint: SpatialPoint[] = [{ x: 10, y: 20, z: 30, measurements: {} }];
    const { normX, normY, normZ, range } = normalisePositions(singlePoint);
    // Range should be 1e-9 (the guard); coords should be ~0.
    expect(range).toBeCloseTo(1e-9, 12);
    expect(normX[0]).toBeCloseTo(0, 5);
    expect(normY[0]).toBeCloseTo(0, 5);
    expect(normZ[0]).toBeCloseTo(0, 5);
  });

  it("uses the longest axis as the range denominator", () => {
    // X span = 100, Y span = 10, Z span = 1 → range = 100.
    const pts: SpatialPoint[] = [
      { x: 0,   y: 0,  z: 0, measurements: {} },
      { x: 100, y: 10, z: 1, measurements: {} },
    ];
    const { range } = normalisePositions(pts);
    expect(range).toBe(100);
  });
});
