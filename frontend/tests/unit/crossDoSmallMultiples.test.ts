/**
 * TS-CROSS-DO-VIEW-2-FE — unit tests for the pure helpers backing
 * CollectionCrossTrackViewPane.vue. ECharts + Vuetify render path is
 * exercised by Playwright; here we cover the data adapter layer that
 * shapes server-side response into renderable cells.
 */
import { describe, expect, it } from "vitest";
import {
  applyDoCap,
  gridPosition,
  sharedXRange,
  sharedYRange,
  toCell,
  type SmallMultiplesSeries,
} from "../../utils/crossDoSmallMultiples";

const SERIES_A: SmallMultiplesSeries = {
  dataObjectAppId: "01930a2b-fe4c-7e3c-9f1d-aaaaaaaaaaaa",
  dataObjectName: "Track_244",
  channelKey: "urn:shepard:afp:tcp-temperature-c",
  channelSymbolicName: "tcp_temp",
  points: [
    { timestamp: 1_700_000_000_000_000_000, value: 180.0 },
    { timestamp: 1_700_000_001_000_000_000, value: 200.0 },
    { timestamp: 1_700_000_002_000_000_000, value: 220.0 },
  ],
};

const SERIES_EMPTY: SmallMultiplesSeries = {
  dataObjectAppId: "01930a2b-fe4c-7e3c-9f1d-bbbbbbbbbbbb",
  dataObjectName: "Track_245",
  channelKey: "urn:shepard:afp:tcp-temperature-c",
  channelSymbolicName: null,
  points: [],
};

// ── toCell ───────────────────────────────────────────────────────────────

describe("toCell", () => {
  it("rebases timestamps to within-DO relative seconds (first → 0)", () => {
    const c = toCell(SERIES_A);
    expect(c.hasData).toBe(true);
    expect(c.relativePoints[0]).toEqual([0, 180]);
    expect(c.relativePoints[1]).toEqual([1, 200]);
    expect(c.relativePoints[2]).toEqual([2, 220]);
  });

  it("falls back to truncated appId when name is missing", () => {
    const c = toCell({
      ...SERIES_A,
      dataObjectName: null,
      dataObjectAppId: "01930a2b-fe4c-7e3c-9f1d-1234567890ab",
    });
    expect(c.label).toBe("01930a2b");
  });

  it("treats empty point list as an empty placeholder cell", () => {
    const c = toCell(SERIES_EMPTY);
    expect(c.hasData).toBe(false);
    expect(c.relativePoints).toHaveLength(0);
    expect(c.channelSymbolicName).toBeNull();
  });

  it("coerces non-numeric values to NaN so echarts renders a gap", () => {
    const c = toCell({
      ...SERIES_A,
      points: [
        { timestamp: 1_700_000_000_000_000_000, value: "not-a-number" },
      ],
    });
    expect(c.relativePoints[0]![0]).toBe(0);
    expect(Number.isNaN(c.relativePoints[0]![1])).toBe(true);
  });
});

// ── sharedYRange ─────────────────────────────────────────────────────────

describe("sharedYRange", () => {
  it("returns null when every cell is empty", () => {
    expect(sharedYRange([toCell(SERIES_EMPTY)])).toBeNull();
  });

  it("computes min/max across all cells, ignoring NaN", () => {
    const cells = [
      toCell(SERIES_A),
      toCell({
        ...SERIES_A,
        dataObjectAppId: "different-do",
        points: [
          { timestamp: 1_700_000_000_000_000_000, value: 100.0 },
          { timestamp: 1_700_000_001_000_000_000, value: "skip-me" },
          { timestamp: 1_700_000_002_000_000_000, value: 250.0 },
        ],
      }),
    ];
    const range = sharedYRange(cells);
    expect(range).not.toBeNull();
    expect(range!.min).toBe(100);
    expect(range!.max).toBe(250);
  });
});

// ── sharedXRange ─────────────────────────────────────────────────────────

describe("sharedXRange", () => {
  it("returns null when every cell is empty", () => {
    expect(sharedXRange([toCell(SERIES_EMPTY)])).toBeNull();
  });

  it("locks min at 0 and uses the longest series for max", () => {
    const longer: SmallMultiplesSeries = {
      ...SERIES_A,
      dataObjectAppId: "longer-do",
      points: [
        { timestamp: 1_700_000_000_000_000_000, value: 10 },
        { timestamp: 1_700_000_007_500_000_000, value: 99 }, // +7.5s
      ],
    };
    const range = sharedXRange([toCell(SERIES_A), toCell(longer)]);
    expect(range).not.toBeNull();
    expect(range!.min).toBe(0);
    expect(range!.max).toBeCloseTo(7.5, 5);
  });
});

// ── gridPosition ─────────────────────────────────────────────────────────

describe("gridPosition", () => {
  it("walks a 4-column grid row-major", () => {
    expect(gridPosition(0, 4)).toEqual({ col: 0, row: 0 });
    expect(gridPosition(3, 4)).toEqual({ col: 3, row: 0 });
    expect(gridPosition(4, 4)).toEqual({ col: 0, row: 1 });
    expect(gridPosition(7, 4)).toEqual({ col: 3, row: 1 });
    expect(gridPosition(8, 4)).toEqual({ col: 0, row: 2 });
  });

  it("treats columns <= 0 as a single column to avoid divide-by-zero", () => {
    expect(gridPosition(0, 0)).toEqual({ col: 0, row: 0 });
    expect(gridPosition(2, -3)).toEqual({ col: 0, row: 2 });
  });
});

// ── applyDoCap ───────────────────────────────────────────────────────────

describe("applyDoCap", () => {
  it("returns input unchanged + no banner when below the cap", () => {
    const ids = ["a", "b", "c"];
    const out = applyDoCap(ids, 100);
    expect(out.kept).toEqual(ids);
    expect(out.banner).toBeNull();
  });

  it("truncates and emits a banner when above the cap", () => {
    const ids = Array.from({ length: 250 }, (_, i) => `do-${i}`);
    const out = applyDoCap(ids, 100);
    expect(out.kept).toHaveLength(100);
    expect(out.kept[0]).toBe("do-0");
    expect(out.kept[99]).toBe("do-99");
    expect(out.banner).toBe("Showing first 100 of 250 DataObjects.");
  });

  it("respects a custom cap", () => {
    const out = applyDoCap(["a", "b", "c", "d", "e"], 3);
    expect(out.kept).toEqual(["a", "b", "c"]);
    expect(out.banner).toBe("Showing first 3 of 5 DataObjects.");
  });
});
