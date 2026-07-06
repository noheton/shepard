/**
 * COLL-TIMELINE-ANNOTATE-1 — unit tests for the pure helpers in
 * utils/collectionDataObjectTimeline.ts.
 */
import { describe, expect, it } from "vitest";
import {
  BIN_SIZE_MS,
  computeGlobalMinMs,
  computeGlobalMaxMs,
  computeBinStarts,
  MAX_BINS,
} from "~/utils/collectionDataObjectTimeline";

// ── BIN_SIZE_MS constants ─────────────────────────────────────────────────────

describe("BIN_SIZE_MS", () => {
  it("hour is 3 600 000 ms", () => {
    expect(BIN_SIZE_MS.hour).toBe(3_600_000);
  });
  it("day is 86 400 000 ms", () => {
    expect(BIN_SIZE_MS.day).toBe(86_400_000);
  });
  it("week is 7 × 86 400 000 ms", () => {
    expect(BIN_SIZE_MS.week).toBe(7 * 86_400_000);
  });
});

// ── computeGlobalMinMs ────────────────────────────────────────────────────────

describe("computeGlobalMinMs", () => {
  it("returns 0 for an empty array", () => {
    expect(computeGlobalMinMs([])).toBe(0);
  });

  it("converts nanoseconds to milliseconds", () => {
    // 1 000 000 000 ns → 1 000 ms
    expect(computeGlobalMinMs([{ timeBoundsStart: 1_000_000_000 }])).toBe(1_000);
  });

  it("returns the minimum across multiple rows", () => {
    const rows = [
      { timeBoundsStart: 10_000_000_000 }, // 10 000 ms
      { timeBoundsStart:  5_000_000_000 }, //  5 000 ms
      { timeBoundsStart: 20_000_000_000 }, // 20 000 ms
    ];
    expect(computeGlobalMinMs(rows)).toBe(5_000);
  });

  it("treats null timeBoundsStart as 0", () => {
    expect(computeGlobalMinMs([{ timeBoundsStart: null }])).toBe(0);
  });
});

// ── computeGlobalMaxMs ────────────────────────────────────────────────────────

describe("computeGlobalMaxMs", () => {
  it("returns 0 for an empty array", () => {
    expect(computeGlobalMaxMs([])).toBe(0);
  });

  it("converts nanoseconds to milliseconds", () => {
    expect(computeGlobalMaxMs([{ timeBoundsEnd: 2_000_000_000 }])).toBe(2_000);
  });

  it("returns the maximum across multiple rows", () => {
    const rows = [
      { timeBoundsEnd: 10_000_000_000 }, // 10 000 ms
      { timeBoundsEnd:  5_000_000_000 }, //  5 000 ms
      { timeBoundsEnd: 20_000_000_000 }, // 20 000 ms
    ];
    expect(computeGlobalMaxMs(rows)).toBe(20_000);
  });
});

// ── computeBinStarts ──────────────────────────────────────────────────────────

describe("computeBinStarts", () => {
  it("returns an empty array when range is zero", () => {
    expect(computeBinStarts(1000, 1000, 86_400_000)).toEqual([]);
  });

  it("returns an empty array when maxMs < minMs", () => {
    expect(computeBinStarts(2000, 1000, 86_400_000)).toEqual([]);
  });

  it("returns a single bin when range fits in one bin", () => {
    const starts = computeBinStarts(0, 60_000, 86_400_000);
    expect(starts).toHaveLength(1);
    expect(starts[0]).toBe(0);
  });

  it("returns correct bin starts for a 3-day range", () => {
    const dayMs = 86_400_000;
    const starts = computeBinStarts(0, 3 * dayMs, dayMs);
    expect(starts).toHaveLength(3);
    expect(starts).toEqual([0, dayMs, 2 * dayMs]);
  });

  it("caps at MAX_BINS (200) for a very long range", () => {
    // 10 years at hourly resolution → well above 200
    const yearMs = 365 * 86_400_000;
    const starts = computeBinStarts(0, 10 * yearMs, 3_600_000);
    expect(starts).toHaveLength(MAX_BINS);
  });

  it("first bin start equals minMs", () => {
    const starts = computeBinStarts(12_345, 12_345 + 86_400_000, 86_400_000);
    expect(starts[0]).toBe(12_345);
  });

  it("consecutive bins are separated by binSizeMs", () => {
    const dayMs = 86_400_000;
    const starts = computeBinStarts(0, 7 * dayMs, dayMs);
    for (let i = 1; i < starts.length; i++) {
      expect(starts[i]! - starts[i - 1]!).toBe(dayMs);
    }
  });
});
