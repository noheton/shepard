/**
 * Pure helpers for the NdtGridCanvas component (MFFD-RENDER-NDT-GRID slice 3).
 *
 * All functions are side-effect-free so they are directly unit-testable.
 */
import { colormapRgb, type ColormapName } from "./colormap";

export interface NdtGridCell {
  row: string;
  col: string;
  dataObjectAppId: string;
  value?: number;
  quality?: string;
  status: string;
}

export interface NdtGridEnvelope {
  kind: string;
  collectionAppId: string;
  rowDimension: string;
  columnDimension: string;
  colourMode: string;
  colourMap: string;
  rows: string[];
  columns: string[];
  cells: NdtGridCell[];
}

/**
 * Map a possibly-unknown colourMap string to a supported ColormapName.
 * "hot" → "heat" (closest perceptual match available in colormap.ts).
 */
export function resolveColourMap(colourMap: string): ColormapName {
  const known: ColormapName[] = ["inferno", "viridis", "plasma", "heat", "cool"];
  if ((known as string[]).includes(colourMap)) return colourMap as ColormapName;
  if (colourMap === "hot") return "heat";
  return "inferno";
}

/** Canonical key for a grid cell. Package-private for helper use. */
export function cellKey(row: string, col: string): string {
  return `${row}:::${col}`;
}

/** Build a lookup map from cellKey(row, col) → cell. */
export function buildCellLookup(cells: NdtGridCell[]): Map<string, NdtGridCell> {
  const lookup = new Map<string, NdtGridCell>();
  for (const cell of cells) {
    lookup.set(cellKey(cell.row, cell.col), cell);
  }
  return lookup;
}

/**
 * Compute CSS colour strings for mean-delta-t mode.
 * Values are normalised min→max (0→1) and mapped through the colormap.
 * When all values are equal the midpoint colour (t=0.5) is used uniformly.
 * Returns a map keyed by cellKey(row, col).
 */
export function computeMeanDtColours(
  cells: NdtGridCell[],
  colourMap: ColormapName,
): Map<string, string> {
  const result = new Map<string, string>();
  const valued = cells.filter(c => c.value !== undefined && c.value !== null);
  if (valued.length === 0) return result;

  const nums = valued.map(c => c.value as number);
  const min = Math.min(...nums);
  const max = Math.max(...nums);
  const range = max - min;

  for (const cell of valued) {
    const t = range > 0 ? ((cell.value as number) - min) / range : 0.5;
    const [r, g, b] = colormapRgb(t, colourMap);
    result.set(
      cellKey(cell.row, cell.col),
      `rgb(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)})`,
    );
  }
  return result;
}

/**
 * Compute CSS colour strings for pass-fail mode.
 * "OK" / "PASS" → green-500; "NOK" / "FAIL" → red-500; else → grey-400.
 * Returns a map keyed by cellKey(row, col).
 */
export function computePassFailColours(
  cells: NdtGridCell[],
): Map<string, string> {
  const result = new Map<string, string>();
  for (const cell of cells) {
    const q = (cell.quality ?? "").toUpperCase();
    const colour =
      q === "OK" || q === "PASS" ? "#4caf50"
      : q === "NOK" || q === "FAIL" ? "#f44336"
      : "#9e9e9e";
    result.set(cellKey(cell.row, cell.col), colour);
  }
  return result;
}

/** Value range (min/max) across all cells, or null if no cells carry a value. */
export function cellValueRange(
  cells: NdtGridCell[],
): { min: number; max: number } | null {
  const nums = cells
    .filter(c => c.value !== undefined && c.value !== null)
    .map(c => c.value as number);
  if (nums.length === 0) return null;
  return { min: Math.min(...nums), max: Math.max(...nums) };
}
