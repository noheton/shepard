/**
 * MFFD-NDT-QUALITY-1 — pure helpers for the thermography plate-heatmap
 * renderer + the quality-chip color picker. Kept pure (no DOM, no canvas
 * touch) so the Vitest suite can assert behaviour without jsdom canvas
 * simulation.
 */

import { colormapRgb } from "~/utils/colormap";

/** Plate heatmap payload as returned by `/v2/thermography/{appId}/plate-heatmap`. */
export interface PlateHeatmap {
  imageBundleAppId: string;
  width: number;
  height: number;
  cells: number[][];
  minTemp: number;
  maxTemp: number;
  thresholdTemp: number;
  frameCount: number;
}

/** Quality-score → chip color mapping. */
export type QualityBand = "good" | "warn" | "bad" | "unknown";

/**
 * Map a quality score in [0, 1] to a UX band. Thresholds match the spec:
 * green ≥ 0.8, amber 0.5–0.8, red < 0.5. Null / NaN / out-of-range → "unknown".
 */
export function qualityBand(score: number | null | undefined): QualityBand {
  if (score == null || Number.isNaN(score)) return "unknown";
  if (score >= 0.8) return "good";
  if (score >= 0.5) return "warn";
  if (score >= 0) return "bad";
  return "unknown";
}

/** Vuetify colour token per band. */
export function qualityChipColor(band: QualityBand): string {
  switch (band) {
    case "good": return "success";
    case "warn": return "warning";
    case "bad":  return "error";
    default:     return "grey";
  }
}

/**
 * Build the rendered RGBA pixel buffer for the plate heatmap, given the
 * payload and the target canvas dimensions. Each grid cell expands to one
 * solid `cellPx × cellPx` block of pixels in the output. Returns a flat
 * Uint8ClampedArray ready to drop into a Canvas2D ImageData.
 *
 * Empty cells (all-equal temperatures) render at colormap t=0.5 so the
 * frame still shows something useful instead of a flat black.
 */
export function renderHeatmapPixels(
  heatmap: PlateHeatmap,
  cellPx: number,
): { pixels: Uint8ClampedArray; canvasWidth: number; canvasHeight: number } {
  const w = heatmap.width * cellPx;
  const h = heatmap.height * cellPx;
  const pixels = new Uint8ClampedArray(w * h * 4);
  const range = heatmap.maxTemp - heatmap.minTemp;
  for (let cy = 0; cy < heatmap.height; cy++) {
    for (let cx = 0; cx < heatmap.width; cx++) {
      const v = heatmap.cells[cy]?.[cx] ?? heatmap.minTemp;
      const t = range > 0 ? (v - heatmap.minTemp) / range : 0.5;
      const tClamped = Math.max(0, Math.min(1, t));
      const [r, g, b] = colormapRgb(tClamped, "inferno");
      const r255 = Math.round(r * 255);
      const g255 = Math.round(g * 255);
      const b255 = Math.round(b * 255);
      // Paint a solid cellPx × cellPx block at (cx*cellPx, cy*cellPx).
      for (let py = 0; py < cellPx; py++) {
        for (let px = 0; px < cellPx; px++) {
          const x = cx * cellPx + px;
          const y = cy * cellPx + py;
          const idx = (y * w + x) * 4;
          pixels[idx]     = r255;
          pixels[idx + 1] = g255;
          pixels[idx + 2] = b255;
          pixels[idx + 3] = 255;
        }
      }
    }
  }
  return { pixels, canvasWidth: w, canvasHeight: h };
}

/**
 * Find the cell coordinates from a mouse position on the rendered canvas.
 * Returns null when the position is outside the canvas. The tooltip uses
 * this to look up the exact temperature at hover.
 */
export function cellAtCanvasPosition(
  heatmap: PlateHeatmap,
  canvasX: number,
  canvasY: number,
  cellPx: number,
): { cx: number; cy: number; temp: number } | null {
  if (canvasX < 0 || canvasY < 0) return null;
  const cx = Math.floor(canvasX / cellPx);
  const cy = Math.floor(canvasY / cellPx);
  if (cx < 0 || cx >= heatmap.width || cy < 0 || cy >= heatmap.height) return null;
  const temp = heatmap.cells[cy]?.[cx] ?? heatmap.minTemp;
  return { cx, cy, temp };
}

/**
 * Format a temperature for human display — three decimals, fixed unit.
 * Centralised so the canvas tooltip + the quality-chip subtitle don't
 * drift in formatting.
 */
export function formatTemp(temp: number): string {
  if (!Number.isFinite(temp)) return "—";
  return `${temp.toFixed(2)} °C`;
}
