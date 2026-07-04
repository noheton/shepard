/**
 * TS-CROSS-DO-VIEW-2-FE — pure helpers for the cross-DataObject small-multiples
 * pane. Lives outside the .vue file so Vitest can exercise the logic without a
 * Vuetify/echarts render path.
 *
 * Shape per series matches the {@code CrossDoSeries} type in
 * {@code composables/containers/useCrossDoBulkData.ts}.
 */

export interface SmallMultiplesPoint {
  timestamp: number; // absolute UTC nanoseconds
  value: unknown;
}

export interface SmallMultiplesSeries {
  dataObjectAppId: string;
  dataObjectName: string | null;
  channelKey: string;
  channelSymbolicName: string | null;
  points: SmallMultiplesPoint[];
}

export interface NormalisedCell {
  /** UUID v7 string for routing on click. */
  dataObjectAppId: string;
  /** Human-readable label shown above each cell. */
  label: string;
  /** Symbolic channel name (or null when no channel matched). */
  channelSymbolicName: string | null;
  /**
   * Series re-based to within-DO relative seconds. The first point becomes
   * t=0; subsequent points carry their offset from the first as a positive
   * number of seconds.
   */
  relativePoints: Array<[number, number]>;
  /**
   * Whether the cell has any plottable data. False means render the empty
   * placeholder ("no matching channel" or "no data in window").
   */
  hasData: boolean;
}

/**
 * Convert one server-side series into a renderable small-multiples cell.
 * - Empty series → cell with `hasData=false`.
 * - First timestamp becomes t=0; subsequent points carry `(t_ns - t0_ns) / 1e9` seconds.
 * - Non-numeric values are coerced to NaN so echarts plots a gap.
 */
export function toCell(s: SmallMultiplesSeries): NormalisedCell {
  const label = s.dataObjectName ?? s.dataObjectAppId.slice(0, 8);
  if (!s.points || s.points.length === 0) {
    return {
      dataObjectAppId: s.dataObjectAppId,
      label,
      channelSymbolicName: s.channelSymbolicName,
      relativePoints: [],
      hasData: false,
    };
  }
  const t0 = s.points[0]!.timestamp;
  const relativePoints = s.points.map<[number, number]>(p => {
    const tSec = (p.timestamp - t0) / 1e9;
    const v = typeof p.value === "number" ? p.value : Number.NaN;
    return [tSec, v];
  });
  return {
    dataObjectAppId: s.dataObjectAppId,
    label,
    channelSymbolicName: s.channelSymbolicName,
    relativePoints,
    hasData: true,
  };
}

/**
 * Compute the shared y-axis range across all cells so each small-multiple is
 * directly comparable. Empty cells contribute nothing. Returns null when no
 * data anywhere.
 */
export function sharedYRange(cells: NormalisedCell[]): { min: number; max: number } | null {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  let hits = 0;
  for (const c of cells) {
    for (const [, v] of c.relativePoints) {
      if (Number.isFinite(v)) {
        if (v < min) min = v;
        if (v > max) max = v;
        hits++;
      }
    }
  }
  if (hits === 0) return null;
  return { min, max };
}

/**
 * Compute the shared x-axis range (in seconds) across all cells. Negative
 * extents collapse to zero so the grid stays aligned on its left edge.
 */
export function sharedXRange(cells: NormalisedCell[]): { min: number; max: number } | null {
  let max = Number.NEGATIVE_INFINITY;
  let hits = 0;
  for (const c of cells) {
    for (const [t] of c.relativePoints) {
      if (Number.isFinite(t)) {
        if (t > max) max = t;
        hits++;
      }
    }
  }
  if (hits === 0) return null;
  return { min: 0, max: Math.max(max, 0) };
}

/**
 * Compute the (column, row) for cell index `i` given a fixed column count.
 * Used for the 4-column grid layout in v1 and easy to widen later.
 */
export function gridPosition(i: number, columns: number): { col: number; row: number } {
  const c = Math.max(1, columns | 0);
  return { col: i % c, row: Math.floor(i / c) };
}

/**
 * Brief: cap at 100 DOs in v1. Apply the cap (silent truncation in the
 * input list) and emit a banner-ready string when truncation happened.
 */
export function applyDoCap(
  appIds: string[],
  cap = 100,
): { kept: string[]; banner: string | null } {
  if (appIds.length <= cap) return { kept: appIds.slice(), banner: null };
  return {
    kept: appIds.slice(0, cap),
    banner: `Showing first ${cap} of ${appIds.length} DataObjects.`,
  };
}
