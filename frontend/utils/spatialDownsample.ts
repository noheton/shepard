/**
 * spatialDownsample — voxel-grid pointcloud decimation helper.
 *
 * Lifted from ``frontend/components/shapes/SpatialPointsCanvas.vue`` so the
 * pure logic is testable in the Vitest node environment (which has no Vue
 * SFC loader). The canvas component imports from here.
 *
 * MFFD W7 / GAP-5. See ``aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md``.
 */

export interface SpatialPoint {
  x: number;
  y: number;
  z: number;
  /** Optional scalar value (e.g. height, force, temperature) for colouring. */
  value?: number;
  /** Optional timestamp in nanoseconds; used by trajectory mode for time-as-colour. */
  t?: number;
}

/**
 * Voxel-grid downsample: bin points into a 3D grid of cells per axis and
 * keep the first point per occupied cell. Deterministic, fast, and
 * preserves coverage (unlike random sampling, which loses the surface
 * shape on edges).
 *
 * Returns the downsampled list. If ``points.length <= cap`` the input is
 * returned unchanged.
 */
export function voxelGridDownsample(
  points: SpatialPoint[],
  cap: number,
): SpatialPoint[] {
  if (points.length <= cap) return points;
  if (points.length === 0) return points;

  // Pick a cell count so the *expected* survivor count is near the cap.
  // Cube root: cells^3 ≈ cap means cells ≈ cbrt(cap).
  const cells = Math.max(8, Math.ceil(Math.cbrt(cap)));

  let xMin = Infinity, yMin = Infinity, zMin = Infinity;
  let xMax = -Infinity, yMax = -Infinity, zMax = -Infinity;
  for (const p of points) {
    if (p.x < xMin) xMin = p.x;
    if (p.y < yMin) yMin = p.y;
    if (p.z < zMin) zMin = p.z;
    if (p.x > xMax) xMax = p.x;
    if (p.y > yMax) yMax = p.y;
    if (p.z > zMax) zMax = p.z;
  }
  const sx = (xMax - xMin) || 1;
  const sy = (yMax - yMin) || 1;
  const sz = (zMax - zMin) || 1;

  const seen = new Set<number>();
  const out: SpatialPoint[] = [];
  for (const p of points) {
    const ix = Math.min(cells - 1, Math.floor(((p.x - xMin) / sx) * cells));
    const iy = Math.min(cells - 1, Math.floor(((p.y - yMin) / sy) * cells));
    const iz = Math.min(cells - 1, Math.floor(((p.z - zMin) / sz) * cells));
    const key = (ix * cells + iy) * cells + iz;
    if (!seen.has(key)) {
      seen.add(key);
      out.push(p);
    }
  }
  return out;
}
