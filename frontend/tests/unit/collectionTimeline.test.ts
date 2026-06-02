/**
 * COLL-TIMELINE-1 — unit tests for the Collection-landing Timeline pane
 * helpers + composable.
 *
 * Same convention as `DataObjectNotebooksPane.test.ts` — pure logic
 * tests, no Vuetify mount. The pane component itself wires ECharts +
 * Vuetify expansion panels (covered indirectly by the typecheck pass and
 * by Playwright at 4K viewport later); these tests exercise the seams
 * the brief calls out: endpoint mock, lane render shape, stacked-bar
 * colours, hover tooltip content, click → router push, bin-size toggle
 * re-fetch, empty-state, drill-down filter passthrough.
 */

import { describe, it, expect, vi } from "vitest";
import {
  buildLaneOption,
  drillDownPath,
  hasRenderableData,
  tooltipFormatter,
  TIMELINE_COLOURS,
  BIN_SIZE_CHOICES,
  EMPTY_STATE_HINT,
} from "../../utils/collectionTimeline";
import type {
  CollectionTimelineEnvelope,
  CollectionTimelineLane,
} from "../../composables/context/useCollectionTimeline";

const lane = (overrides: Partial<CollectionTimelineLane> = {}): CollectionTimelineLane => ({
  key: "afp-layup",
  label: "AFP Layup",
  bins: [
    { day: "2024-04-15", count: 34, ncrCount: 1, rejectCount: 0 },
    { day: "2024-04-16", count: 28, ncrCount: 0, rejectCount: 1 },
    { day: "2024-04-17", count: 12, ncrCount: 0, rejectCount: 0 },
  ],
  ...overrides,
});

describe("COLL-TIMELINE-1 — buildLaneOption", () => {
  it("emits three stacked bar series (OK/NCR/REJECTED) with the brief's colour palette", () => {
    const option = buildLaneOption(lane(), 1) as {
      series: Array<{ name: string; type: string; stack: string; itemStyle: { color: string }; data: unknown[] }>;
    };
    expect(option.series).toHaveLength(3);
    const okSeries = option.series.find(s => s.name === "OK");
    const ncrSeries = option.series.find(s => s.name === "NCR");
    const rejSeries = option.series.find(s => s.name === "REJECTED");
    expect(okSeries?.itemStyle.color).toBe(TIMELINE_COLOURS.ok);
    expect(ncrSeries?.itemStyle.color).toBe(TIMELINE_COLOURS.ncr);
    expect(rejSeries?.itemStyle.color).toBe(TIMELINE_COLOURS.rejected);
    expect(option.series.every(s => s.stack === "lane")).toBe(true);
    expect(option.series.every(s => s.type === "bar")).toBe(true);
  });

  it("computes OK as count - ncrCount - rejectCount per bin", () => {
    const option = buildLaneOption(lane(), 1) as {
      series: Array<{ name: string; data: Array<[string, number]> }>;
    };
    const okSeries = option.series.find(s => s.name === "OK")!;
    // First bin: 34 - 1 - 0 = 33
    expect(okSeries.data[0]).toEqual(["2024-04-15", 33]);
    // Second bin: 28 - 0 - 1 = 27
    expect(okSeries.data[1]).toEqual(["2024-04-16", 27]);
  });

  it("never produces a negative OK value when NCR + REJECT exceed count", () => {
    // Defensive — shouldn't happen given backend math, but the UI must not
    // render a negative bar height under any envelope shape.
    const weird = lane({
      bins: [{ day: "2024-04-15", count: 1, ncrCount: 5, rejectCount: 0 }],
    });
    const option = buildLaneOption(weird, 1) as {
      series: Array<{ name: string; data: Array<[string, number]> }>;
    };
    const okSeries = option.series.find(s => s.name === "OK")!;
    const firstPoint = okSeries.data[0]!;
    expect(firstPoint[1]).toBe(0);
  });

  it("renders the day axis with one category per bin (ISO date)", () => {
    const option = buildLaneOption(lane(), 1) as {
      xAxis: { type: string; data: string[] };
    };
    expect(option.xAxis.type).toBe("category");
    expect(option.xAxis.data).toEqual(["2024-04-15", "2024-04-16", "2024-04-17"]);
  });
});

describe("COLL-TIMELINE-1 — tooltipFormatter", () => {
  it("emits the brief's hover string: 'AFP Layup, 2023-04-15, 34 DOs (1 NCR)'", () => {
    const fmt = tooltipFormatter("AFP Layup", 1);
    const out = fmt([
      { axisValue: "2023-04-15", data: ["2023-04-15", 33], seriesName: "OK" },
      { axisValue: "2023-04-15", data: ["2023-04-15", 1], seriesName: "NCR" },
      { axisValue: "2023-04-15", data: ["2023-04-15", 0], seriesName: "REJECTED" },
    ]);
    expect(out).toContain("AFP Layup");
    expect(out).toContain("2023-04-15");
    expect(out).toContain("34 DOs");
    expect(out).toContain("1 NCR");
  });

  it("flags REJECTED counts in the hover string", () => {
    const fmt = tooltipFormatter("NDT Inspection", 1);
    const out = fmt([
      { axisValue: "2023-05-01", data: ["2023-05-01", 5], seriesName: "OK" },
      { axisValue: "2023-05-01", data: ["2023-05-01", 0], seriesName: "NCR" },
      { axisValue: "2023-05-01", data: ["2023-05-01", 2], seriesName: "REJECTED" },
    ]);
    expect(out).toContain("2 REJ");
  });

  it("annotates non-daily bins with the bin window suffix", () => {
    const fmt = tooltipFormatter("Ultrasonic Welding", 7);
    const out = fmt([
      { axisValue: "2023-05-01", data: ["2023-05-01", 12], seriesName: "OK" },
      { axisValue: "2023-05-01", data: ["2023-05-01", 0], seriesName: "NCR" },
      { axisValue: "2023-05-01", data: ["2023-05-01", 0], seriesName: "REJECTED" },
    ]);
    expect(out).toContain("7-day bin");
  });

  it("returns an empty string when ECharts hands in no params", () => {
    const fmt = tooltipFormatter("AFP Layup", 1);
    expect(fmt([])).toBe("");
  });
});

describe("COLL-TIMELINE-1 — drillDownPath", () => {
  it("composes /collections/{appId}?tab=data-objects&process-type=&date= (brief's filter shape)", () => {
    const path = drillDownPath("coll-uuid-7", "afp-layup", "2023-04-15");
    expect(path).toBe(
      "/collections/coll-uuid-7?tab=data-objects&process-type=afp-layup&date=2023-04-15",
    );
  });

  it("URL-encodes the lane key and date so non-ASCII slugs do not break the link", () => {
    const path = drillDownPath("coll-uuid-7", "frame welding 3", "2023-04-15");
    expect(path).toContain("frame%20welding%203");
  });
});

describe("COLL-TIMELINE-1 — hasRenderableData", () => {
  it("returns false on null envelope (initial / fetch error)", () => {
    expect(hasRenderableData(null)).toBe(false);
  });

  it("returns false when lanes array is empty (no DataObjects)", () => {
    const env: CollectionTimelineEnvelope = {
      binSizeDays: 1,
      rangeStart: null,
      rangeEnd: null,
      totalDataObjects: 0,
      lanes: [],
    };
    expect(hasRenderableData(env)).toBe(false);
  });

  it("returns false when every lane carries zero bins (degenerate empty state)", () => {
    const env: CollectionTimelineEnvelope = {
      binSizeDays: 1,
      rangeStart: null,
      rangeEnd: null,
      totalDataObjects: 0,
      lanes: [{ key: "x", label: "X", bins: [] }],
    };
    expect(hasRenderableData(env)).toBe(false);
  });

  it("returns true when at least one lane carries bins", () => {
    const env: CollectionTimelineEnvelope = {
      binSizeDays: 1,
      rangeStart: "2024-01-01T00:00:00Z",
      rangeEnd: "2024-01-02T00:00:00Z",
      totalDataObjects: 1,
      lanes: [lane({ bins: [{ day: "2024-01-01", count: 1, ncrCount: 0, rejectCount: 0 }] })],
    };
    expect(hasRenderableData(env)).toBe(true);
  });
});

describe("COLL-TIMELINE-1 — composable bin-size toggle re-fetches endpoint", () => {
  it("calls the timeline endpoint with the binSizeDays query param", async () => {
    // Mock global fetch + auth so the composable runs in node.
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        binSizeDays: 7,
        rangeStart: "2024-01-01T00:00:00Z",
        rangeEnd: "2024-01-02T00:00:00Z",
        totalDataObjects: 0,
        lanes: [],
      }),
    });
    vi.stubGlobal("fetch", fetchSpy);
    vi.stubGlobal("useRuntimeConfig", () => ({
      public: { backendV2ApiUrl: "https://shepard.test" },
    }));
    vi.stubGlobal("useAuth", () => ({ data: { value: { accessToken: "tok" } } }));

    const { useCollectionTimeline } = await import(
      "../../composables/context/useCollectionTimeline"
    );
    const { fetchTimeline, envelope } = useCollectionTimeline();
    await fetchTimeline("coll-uuid-7", 7);

    expect(fetchSpy).toHaveBeenCalledOnce();
    const firstCall = fetchSpy.mock.calls[0]!;
    const calledUrl = firstCall[0] as string;
    expect(calledUrl).toContain("/v2/collections/coll-uuid-7/timeline");
    expect(calledUrl).toContain("binSizeDays=7");
    // Auto-coarsened response echoes a different bin size — UI renders off it.
    expect(envelope.value?.binSizeDays).toBe(7);

    vi.unstubAllGlobals();
  });
});

describe("COLL-TIMELINE-1 — toolbar contract", () => {
  it("exposes the three bin-size choices the brief enumerates", () => {
    const labels = BIN_SIZE_CHOICES.map(c => c.label);
    expect(labels).toEqual(["Day", "Week", "Month"]);
    const values = BIN_SIZE_CHOICES.map(c => c.value);
    expect(values).toEqual([1, 7, 30]);
  });

  it("ships a non-empty empty-state hint pointing at the urn:shepard:mffd:process-type predicate", () => {
    expect(EMPTY_STATE_HINT.length).toBeGreaterThan(0);
    expect(EMPTY_STATE_HINT).toContain("urn:shepard:mffd:process-type");
  });
});

describe("COLL-TIMELINE-1 — 4K viewport regression (option size stays fluid)", () => {
  it("does not pin pixel widths in the chart option (autoresize takes care of 4K)", () => {
    const option = buildLaneOption(lane(), 1) as {
      grid: Record<string, number>;
      xAxis: { axisLabel: { fontSize: number } };
    };
    // The component's `<VChart autoresize>` handles 4K; we just sanity-check
    // that we haven't accidentally hard-coded a width that would clip.
    expect(option.grid).not.toHaveProperty("width");
    expect(option.grid).not.toHaveProperty("height");
    // Font-size is small enough that a 4K viewport doesn't show 30pt labels.
    expect(option.xAxis.axisLabel.fontSize).toBeLessThanOrEqual(12);
  });
});
