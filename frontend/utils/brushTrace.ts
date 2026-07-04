/**
 * Brush-trace render helpers (MFFD-SPATIAL-LINESCAN-IMPORTER-1, 2026-06-02).
 *
 * Lives outside the .vue component so vitest can import the pure helpers
 * without the Three.js / WebGL component scaffold. The renderer in
 * ``components/shapes/SpatialPointsCanvas.vue`` re-exports these.
 */
import type { SpatialPoint } from "./spatialDownsample";

/**
 * Brush-trace row points carry an intensity vector in
 * ``measurements.intensities``. Each row of a TPS raw-data PNG becomes one
 * row-point on the wire; the renderer expands it into per-pixel
 * ``SpatialPoint``s at (x=column-index, y=row-index, z=0).
 */
export interface BrushTraceRowPoint extends SpatialPoint {
  measurements?: {
    intensities?: number[];
    row_index?: number;
    chunk_index?: number;
    kind?: string;
    [key: string]: unknown;
  };
}

/**
 * Expand brush-trace row-points into the per-pixel SpatialPoint stream the
 * pointcloud branch already knows how to render. Skips rows with missing or
 * empty intensity vectors.
 *
 * Optional ``maxRowIndex`` is the time-slider cursor: when set, only rows
 * whose ``row_index`` is ``<= maxRowIndex`` are emitted. This is what drives
 * the canvas's "brush sweeping along the track" animation. ``undefined`` =
 * emit all rows (no scrub).
 */
export function expandBrushTraceRows(
  rows: BrushTraceRowPoint[],
  maxRowIndex?: number,
): SpatialPoint[] {
  const out: SpatialPoint[] = [];
  let globalRowOffset = 0;
  for (const r of rows) {
    const vec = r.measurements?.intensities;
    if (!Array.isArray(vec) || vec.length === 0) continue;
    const rowIndex = (r.measurements?.row_index as number | undefined) ?? r.y ?? globalRowOffset;
    if (maxRowIndex !== undefined && rowIndex > maxRowIndex) {
      globalRowOffset += 1;
      continue;
    }
    for (let c = 0; c < vec.length; c++) {
      out.push({ x: c, y: rowIndex, z: 0, value: Number(vec[c] ?? 0) });
    }
    globalRowOffset += 1;
  }
  return out;
}

/**
 * Compute the highest ``row_index`` in a set of brush-trace rows. Used by the
 * canvas to populate the time-slider's upper bound. Returns ``0`` for an
 * empty set so a default v-slider with max=0 still renders.
 */
export function maxRowIndexOf(rows: BrushTraceRowPoint[]): number {
  let max = 0;
  let fallback = 0;
  for (const r of rows) {
    const rowIndex = (r.measurements?.row_index as number | undefined) ?? r.y ?? fallback;
    if (rowIndex > max) max = rowIndex;
    fallback += 1;
  }
  return max;
}
