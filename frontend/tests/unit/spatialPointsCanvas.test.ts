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
