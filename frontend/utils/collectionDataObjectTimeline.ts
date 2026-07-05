/**
 * COLL-TIMELINE-ANNOTATE-1 — pure helpers for the per-DataObject
 * swimlane timeline (CollectionDataObjectTimelinePane.vue).
 * Extracted here so Vitest can import them without compiling the SFC.
 */

/** Bin size in milliseconds, keyed by the BinSize union. */
export const BIN_SIZE_MS: Record<"hour" | "day" | "week", number> = {
  hour: 3_600_000,
  day: 86_400_000,
  week: 7 * 86_400_000,
};

/** Maximum number of bin columns rendered per row. */
export const MAX_BINS = 200;

/** Global start of the swimlane grid, in milliseconds.
 *  timeBoundsStart values are nanoseconds from the backend. */
export function computeGlobalMinMs(
  rows: { timeBoundsStart?: number | null }[],
): number {
  if (!rows.length) return 0;
  return Math.min(...rows.map(d => (d.timeBoundsStart ?? 0) / 1_000_000));
}

/** Global end of the swimlane grid, in milliseconds.
 *  timeBoundsEnd values are nanoseconds from the backend. */
export function computeGlobalMaxMs(
  rows: { timeBoundsEnd?: number | null }[],
): number {
  if (!rows.length) return 0;
  return Math.max(...rows.map(d => (d.timeBoundsEnd ?? 0) / 1_000_000));
}

/** Compute bin start timestamps (ms) for the grid, capped at MAX_BINS. */
export function computeBinStarts(
  minMs: number,
  maxMs: number,
  binSizeMs: number,
): number[] {
  const range = maxMs - minMs;
  if (range <= 0 || binSizeMs <= 0) return [];
  const count = Math.min(MAX_BINS, Math.ceil(range / binSizeMs));
  return Array.from({ length: count }, (_, i) => minMs + i * binSizeMs);
}
