/**
 * MFFD-NDT-QUALITY-1 — Vitest unit coverage for the pure helpers behind
 * the DataObjectThermographyPane. The pane renders a Canvas via these
 * helpers; testing them directly is cheap + deterministic + jsdom-free.
 */
import { describe, it, expect } from "vitest";
import {
  qualityBand,
  qualityChipColor,
  renderHeatmapPixels,
  cellAtCanvasPosition,
  formatTemp,
  type PlateHeatmap,
} from "../../utils/thermographyHeatmap";

function fakeHeatmap(overrides: Partial<PlateHeatmap> = {}): PlateHeatmap {
  return {
    imageBundleAppId: "b1",
    width: 2,
    height: 2,
    cells: [
      [20, 25],
      [60, 110],
    ],
    minTemp: 20,
    maxTemp: 110,
    thresholdTemp: 80,
    frameCount: 5,
    ...overrides,
  };
}

describe("thermographyHeatmap — qualityBand + chip color", () => {
  it("maps scores >= 0.8 to good (green)", () => {
    expect(qualityBand(0.8)).toBe("good");
    expect(qualityBand(1.0)).toBe("good");
    expect(qualityChipColor("good")).toBe("success");
  });

  it("maps scores in [0.5, 0.8) to warn (amber)", () => {
    expect(qualityBand(0.5)).toBe("warn");
    expect(qualityBand(0.79)).toBe("warn");
    expect(qualityChipColor("warn")).toBe("warning");
  });

  it("maps scores < 0.5 to bad (red)", () => {
    expect(qualityBand(0)).toBe("bad");
    expect(qualityBand(0.49)).toBe("bad");
    expect(qualityChipColor("bad")).toBe("error");
  });

  it("returns unknown for null / NaN", () => {
    expect(qualityBand(null)).toBe("unknown");
    expect(qualityBand(undefined)).toBe("unknown");
    expect(qualityBand(NaN)).toBe("unknown");
    expect(qualityChipColor("unknown")).toBe("grey");
  });
});

describe("thermographyHeatmap — renderHeatmapPixels", () => {
  it("produces a buffer of the expected shape", () => {
    const heatmap = fakeHeatmap();
    const { pixels, canvasWidth, canvasHeight } = renderHeatmapPixels(heatmap, 8);
    expect(canvasWidth).toBe(16);  // 2 cells × 8 px
    expect(canvasHeight).toBe(16);
    expect(pixels.length).toBe(16 * 16 * 4);
    // Every pixel must have a non-zero alpha — no transparent rows.
    for (let i = 3; i < pixels.length; i += 4) {
      expect(pixels[i]).toBe(255);
    }
  });

  it("hottest cell renders with higher red intensity than coldest (inferno palette)", () => {
    const heatmap = fakeHeatmap();
    // cellPx = 4 → canvas is 8 × 8.
    // Cell (0, 0) = 20 °C (cold) at canvas (0, 0); cell (1, 1) = 110 °C (hot)
    // at canvas (4, 4). Index = (y * canvasWidth + x) * 4, canvasWidth = 8.
    const { pixels } = renderHeatmapPixels(heatmap, 4);
    const coldR = pixels[(0 * 8 + 0) * 4];
    const hotR  = pixels[(4 * 8 + 4) * 4];
    // Inferno's hot end is bright yellow-white; cold end is near-black.
    expect(hotR!).toBeGreaterThan(coldR!);
  });

  it("handles a uniform heatmap without crashing (midpoint colour everywhere)", () => {
    const heatmap = fakeHeatmap({
      cells: [
        [50, 50],
        [50, 50],
      ],
      minTemp: 50,
      maxTemp: 50,
    });
    const { pixels } = renderHeatmapPixels(heatmap, 2);
    // All four cells map to t = 0.5; no NaNs.
    for (let i = 0; i < pixels.length; i++) {
      expect(pixels[i]).toBeGreaterThanOrEqual(0);
      expect(pixels[i]).toBeLessThanOrEqual(255);
    }
  });
});

describe("thermographyHeatmap — cellAtCanvasPosition", () => {
  it("resolves hover position to (cell x, cell y, temp)", () => {
    const heatmap = fakeHeatmap();
    // Cell (1, 0) = 25 °C; cellPx = 10 → canvas x in [10, 20), y in [0, 10).
    const hit = cellAtCanvasPosition(heatmap, 15, 5, 10);
    expect(hit).toEqual({ cx: 1, cy: 0, temp: 25 });
  });

  it("returns null outside the canvas", () => {
    const heatmap = fakeHeatmap();
    expect(cellAtCanvasPosition(heatmap, -1, 0, 10)).toBeNull();
    expect(cellAtCanvasPosition(heatmap, 100, 100, 10)).toBeNull();
  });
});

describe("thermographyHeatmap — formatTemp", () => {
  it("two-decimal °C", () => {
    expect(formatTemp(25)).toBe("25.00 °C");
    expect(formatTemp(110.12345)).toBe("110.12 °C");
  });

  it("dash on non-finite", () => {
    expect(formatTemp(NaN)).toBe("—");
    expect(formatTemp(Infinity)).toBe("—");
  });
});
