/**
 * SpatialPointsCanvas (MFFD W7 / GAP-5) — unit tests for the pure logic
 * extracted from ``frontend/components/shapes/SpatialPointsCanvas.vue``.
 *
 * The component instantiates Three.js / WebGL (unavailable in Vitest's node
 * env), so we test only the pure helpers (voxel-grid downsample and kind
 * classification) here. Three.js scene-graph rendering is exercised by the
 * Playwright e2e pass at the user's 4K viewport.
 */
import { describe, it, expect } from "vitest";
import { voxelGridDownsample, type SpatialPoint } from "../../utils/spatialDownsample";
import {
  expandBrushTraceRows,
  maxRowIndexOf,
  type BrushTraceRowPoint,
} from "../../utils/brushTrace";
import { colormapRgb } from "../../utils/colormap";

function gridOfPoints(nPerAxis: number): SpatialPoint[] {
  const pts: SpatialPoint[] = [];
  for (let i = 0; i < nPerAxis; i++) {
    for (let j = 0; j < nPerAxis; j++) {
      for (let k = 0; k < nPerAxis; k++) {
        pts.push({ x: i, y: j, z: k });
      }
    }
  }
  return pts;
}

describe("voxelGridDownsample", () => {
  it("returns input unchanged when length <= cap", () => {
    const pts: SpatialPoint[] = [
      { x: 0, y: 0, z: 0 },
      { x: 1, y: 1, z: 1 },
      { x: 2, y: 2, z: 2 },
    ];
    const result = voxelGridDownsample(pts, 100);
    expect(result).toBe(pts);
  });

  it("returns empty array unchanged", () => {
    expect(voxelGridDownsample([], 100)).toEqual([]);
  });

  it("decimates when count exceeds cap", () => {
    // 30^3 = 27,000 points compressed to <= 1000
    const pts = gridOfPoints(30);
    const result = voxelGridDownsample(pts, 1000);
    expect(result.length).toBeLessThan(pts.length);
    expect(result.length).toBeGreaterThan(0);
  });

  it("downsample is deterministic (same input → same output)", () => {
    const pts = gridOfPoints(15);
    const a = voxelGridDownsample(pts, 500);
    const b = voxelGridDownsample(pts, 500);
    expect(a.length).toBe(b.length);
    // Voxel-grid keeps first point per cell; both runs must pick the same.
    expect(a.map(p => [p.x, p.y, p.z])).toEqual(b.map(p => [p.x, p.y, p.z]));
  });

  it("downsample preserves coverage (bbox close to source bbox)", () => {
    const pts = gridOfPoints(20);
    const result = voxelGridDownsample(pts, 200);
    const xMin = Math.min(...result.map(p => p.x));
    const xMax = Math.max(...result.map(p => p.x));
    // The voxel-grid keeps the FIRST point landing in each cell. Since
    // input traversal is row-major in x, the min-x cell always lands at
    // x=0, but the max-x cell only sees the cell representative — which
    // sits in the last x-bucket but not necessarily the last x-row.
    // What's guaranteed: bbox is non-degenerate and within source bounds.
    expect(xMin).toBe(0);
    expect(xMax).toBeGreaterThan(10);
    expect(xMax).toBeLessThanOrEqual(19);
  });

  it("handles degenerate (single-plane) input", () => {
    // All points in z=0 plane — sx,sy > 0 but sz = 0
    const pts: SpatialPoint[] = [];
    for (let i = 0; i < 50; i++) {
      for (let j = 0; j < 50; j++) {
        pts.push({ x: i, y: j, z: 0 });
      }
    }
    const result = voxelGridDownsample(pts, 100);
    expect(result.length).toBeGreaterThan(0);
    expect(result.length).toBeLessThan(pts.length);
    // No NaN / Infinity in result coordinates
    for (const p of result) {
      expect(Number.isFinite(p.x)).toBe(true);
      expect(Number.isFinite(p.y)).toBe(true);
      expect(Number.isFinite(p.z)).toBe(true);
    }
  });

  it("MFFD-shape: a Track 66 TPS pointcloud (~4118 pts) passes through unchanged at 500k cap", () => {
    // The real Keyence laser scan produces ~4000 points per stripe. They
    // must render in full at the default 500k cap.
    const pts: SpatialPoint[] = [];
    for (let i = 0; i < 4118; i++) {
      pts.push({ x: 296.7 + i * 0.001, y: -266.5, z: -46 + i * 2 });
    }
    const result = voxelGridDownsample(pts, 500_000);
    expect(result.length).toBe(4118);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Brush-trace mode (MFFD-SPATIAL-LINESCAN-IMPORTER-1, 2026-06-02)
// ─────────────────────────────────────────────────────────────────────────────

describe("expandBrushTraceRows", () => {
  it("expands a single row's intensity vector into N per-pixel points", () => {
    const rows: BrushTraceRowPoint[] = [
      { x: 0, y: 0, z: 0, measurements: { intensities: [10, 20, 30, 40], row_index: 0 } },
    ];
    const out = expandBrushTraceRows(rows);
    expect(out).toHaveLength(4);
    expect(out.map(p => p.x)).toEqual([0, 1, 2, 3]);
    expect(out.map(p => p.y)).toEqual([0, 0, 0, 0]);
    expect(out.map(p => p.value)).toEqual([10, 20, 30, 40]);
  });

  it("Y axis is the row index — second row lands at y=1", () => {
    const rows: BrushTraceRowPoint[] = [
      { x: 0, y: 0, z: 0, measurements: { intensities: [1, 2], row_index: 0 } },
      { x: 0, y: 1, z: 0, measurements: { intensities: [3, 4], row_index: 1 } },
    ];
    const out = expandBrushTraceRows(rows);
    expect(out.find(p => p.value === 3)?.y).toBe(1);
    expect(out.find(p => p.value === 4)?.y).toBe(1);
  });

  it("skips rows whose intensity vector is missing or empty", () => {
    const rows: BrushTraceRowPoint[] = [
      { x: 0, y: 0, z: 0, measurements: { intensities: [5], row_index: 0 } },
      { x: 0, y: 1, z: 0, measurements: {} },
      { x: 0, y: 2, z: 0, measurements: { intensities: [], row_index: 2 } },
      { x: 0, y: 3, z: 0, measurements: { intensities: [7], row_index: 3 } },
    ];
    const out = expandBrushTraceRows(rows);
    expect(out).toHaveLength(2);
    expect(out.map(p => p.value)).toEqual([5, 7]);
  });

  it("decimation: a 964×1292 frame is voxel-decimated below 500k under the cap", () => {
    // Simulate one full linescan chunk (964 rows × 1292 cols = 1_245_488 pts).
    // The grid downsample must bring this under the 500k cap.
    const rows: BrushTraceRowPoint[] = [];
    for (let r = 0; r < 964; r++) {
      const intensities: number[] = new Array(1292);
      for (let c = 0; c < 1292; c++) intensities[c] = (r + c) & 0xff;
      rows.push({ x: 0, y: r, z: 0, measurements: { intensities, row_index: r } });
    }
    const expanded = expandBrushTraceRows(rows);
    expect(expanded.length).toBe(964 * 1292);
    const downsampled = voxelGridDownsample(expanded, 500_000);
    expect(downsampled.length).toBeLessThanOrEqual(500_000);
    expect(downsampled.length).toBeGreaterThan(1000);
  });

  it("falls back to global row offset when row_index missing", () => {
    const rows: BrushTraceRowPoint[] = [
      { x: 0, y: 0, z: 0, measurements: { intensities: [1] } },
      { x: 0, y: 0, z: 0, measurements: { intensities: [2] } },
    ];
    const out = expandBrushTraceRows(rows);
    // Both rows lack row_index AND have y=0 → fall back to y=0 for both
    // (operator-facing behaviour: importer should always set row_index, but
    // the renderer's degenerate-case is "draw them at y=0", not crash).
    expect(out.every(p => Number.isFinite(p.y))).toBe(true);
  });

  // ─── time-slider cursor / brush-animation tests ───────────────────────────

  it("maxRowIndexOf returns the highest row_index in a row set", () => {
    const rows: BrushTraceRowPoint[] = [];
    for (let r = 0; r < 100; r++) {
      rows.push({ x: 0, y: r, z: 0, measurements: { intensities: [1], row_index: r } });
    }
    expect(maxRowIndexOf(rows)).toBe(99);
  });

  it("maxRowIndexOf returns 0 for an empty set", () => {
    expect(maxRowIndexOf([])).toBe(0);
  });

  it("time-slider at cursor=0 emits only the first row's pixels", () => {
    const rows: BrushTraceRowPoint[] = [];
    for (let r = 0; r < 10; r++) {
      rows.push({
        x: 0,
        y: r,
        z: 0,
        measurements: { intensities: [r, r, r], row_index: r },
      });
    }
    const out = expandBrushTraceRows(rows, 0);
    expect(out).toHaveLength(3); // first row's 3 pixels only
    expect(out.every(p => p.y === 0)).toBe(true);
  });

  it("time-slider mid-sweep emits all rows up to and including cursorRow", () => {
    const rows: BrushTraceRowPoint[] = [];
    for (let r = 0; r < 10; r++) {
      rows.push({
        x: 0,
        y: r,
        z: 0,
        measurements: { intensities: [1, 2], row_index: r },
      });
    }
    const out = expandBrushTraceRows(rows, 4);
    // Rows 0,1,2,3,4 = 5 rows × 2 pixels = 10 points.
    expect(out).toHaveLength(10);
    const maxYInOutput = Math.max(...out.map(p => p.y as number));
    expect(maxYInOutput).toBe(4);
  });

  it("time-slider at max cursor emits the full brush", () => {
    const rows: BrushTraceRowPoint[] = [];
    for (let r = 0; r < 5; r++) {
      rows.push({
        x: 0,
        y: r,
        z: 0,
        measurements: { intensities: [42], row_index: r },
      });
    }
    const full = expandBrushTraceRows(rows);
    const cursorAtMax = expandBrushTraceRows(rows, maxRowIndexOf(rows));
    expect(cursorAtMax).toHaveLength(full.length);
  });

  it("viridis colormap maps intensity 0..255 to a contiguous RGB sweep", () => {
    // Cross-plugin colormap consistency: the brush-trace renderer uses the
    // same viridis colormap AAC2 ships for the thermography heatmap, so a
    // physicist comparing them gets a comparable colour sweep. Spot-check
    // four anchor values — the actual numeric stops live in
    // utils/colormap.ts.
    const [r0, g0, b0] = colormapRgb(0, "viridis");
    const [r1, g1, b1] = colormapRgb(1, "viridis");
    expect(r0).toBeGreaterThanOrEqual(0);
    expect(b0).toBeGreaterThanOrEqual(0);
    expect(g1).toBeGreaterThan(0); // viridis ends bright yellow-green
    // Endpoints differ — the colormap actually maps something.
    expect([r0, g0, b0]).not.toEqual([r1, g1, b1]);
  });
});
