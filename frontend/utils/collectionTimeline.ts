/**
 * COLL-TIMELINE-1 — pure helpers for the Collection-landing Timeline pane.
 *
 * Kept out of the Vue component so the Vitest suite can exercise the
 * chart-option builder + drill-down router target without mounting Vuetify.
 */

import type {
  CollectionTimelineEnvelope,
  CollectionTimelineLane,
} from "~/composables/context/useCollectionTimeline";

/** Bar colours — green = OK, amber = NCR-pending, red = REJECTED. */
export const TIMELINE_COLOURS = {
  ok: "#43a047",
  ncr: "#fb8c00",
  rejected: "#e53935",
};

/**
 * COLL-TIMELINE-LIVE-1 — SSE event types that should trigger a timeline
 * bin refresh. HEARTBEAT is already dropped by useCollectionEvents before
 * reaching handlers; COLLECTION_UPDATED is metadata-only and does not
 * change the bin aggregate.
 */
export const LIVE_REFRESH_EVENTS = new Set([
  "DATA_OBJECT_CREATED",
  "DATA_OBJECT_UPDATED",
  "DATA_OBJECT_DELETED",
]);

export interface TimelineEChartsSeries {
  name: string;
  type: "bar";
  stack: string;
  itemStyle: { color: string };
  data: Array<[string, number]>;
}

/**
 * Build the per-lane ECharts options for a single swimlane. Three stacked
 * series (OK / NCR / REJECTED) over a categorical day x-axis.
 *
 * Returns a plain object the consumer can hand to `<VChart :option=>`.
 * Sized for a single lane (one row); the pane stacks N of these vertically
 * to form the swimlane.
 */
export function buildLaneOption(
  lane: CollectionTimelineLane,
  binSizeDays: number,
): Record<string, unknown> {
  const days = lane.bins.map(b => b.day);
  const okData: Array<[string, number]> = lane.bins.map(b => [
    b.day,
    Math.max(0, b.count - b.ncrCount - b.rejectCount),
  ]);
  const ncrData: Array<[string, number]> = lane.bins.map(b => [b.day, b.ncrCount]);
  const rejData: Array<[string, number]> = lane.bins.map(b => [b.day, b.rejectCount]);

  return {
    grid: { left: 36, right: 8, top: 8, bottom: 28 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: tooltipFormatter(lane.label, binSizeDays),
    },
    xAxis: {
      type: "category",
      data: days,
      axisLabel: {
        fontSize: 9,
        interval: "auto",
        rotate: days.length > 30 ? 45 : 0,
      },
    },
    yAxis: {
      type: "value",
      name: "DOs",
      nameLocation: "middle",
      nameGap: 28,
      axisLabel: { fontSize: 9 },
      nameTextStyle: { fontSize: 9 },
      minInterval: 1,
    },
    series: [
      {
        name: "OK",
        type: "bar",
        stack: "lane",
        itemStyle: { color: TIMELINE_COLOURS.ok },
        data: okData,
      },
      {
        name: "NCR",
        type: "bar",
        stack: "lane",
        itemStyle: { color: TIMELINE_COLOURS.ncr },
        data: ncrData,
      },
      {
        name: "REJECTED",
        type: "bar",
        stack: "lane",
        itemStyle: { color: TIMELINE_COLOURS.rejected },
        data: rejData,
      },
    ],
  };
}

/**
 * Tooltip formatter exposed for testing. Builds the
 * "AFP Layup, 2023-04-15, 34 DOs (1 NCR)" string the brief calls out as the
 * required hover payload.
 */
export function tooltipFormatter(
  laneLabel: string,
  binSizeDays: number,
): (params: Array<{ axisValue: string; data: [string, number]; seriesName: string }>) => string {
  return params => {
    if (!params || params.length === 0) return "";
    const first = params[0];
    if (!first) return "";
    const day = first.axisValue;
    let ok = 0;
    let ncr = 0;
    let rej = 0;
    for (const p of params) {
      const v = Array.isArray(p.data) ? Number(p.data[1] ?? 0) : Number(p.data ?? 0);
      if (p.seriesName === "OK") ok = v;
      else if (p.seriesName === "NCR") ncr = v;
      else if (p.seriesName === "REJECTED") rej = v;
    }
    const total = ok + ncr + rej;
    const suffix =
      binSizeDays > 1 ? ` (${binSizeDays}-day bin)` : "";
    const flags: string[] = [];
    if (ncr > 0) flags.push(`${ncr} NCR`);
    if (rej > 0) flags.push(`${rej} REJ`);
    const flagPart = flags.length > 0 ? ` (${flags.join(", ")})` : "";
    return `${laneLabel}, ${day}${suffix} — ${total} DOs${flagPart}`;
  };
}

/**
 * Compose the drill-down route a click on a bin should navigate to.
 *
 * Per the brief: `/collections/{appId}/data-objects?process-type=...&date=...`.
 * The data-objects list page doesn't honour those query params today, so
 * the link degrades into the un-filtered list — the params are still
 * appended so a future `COLL-TIMELINE-DRILLDOWN-FILTER-1` ship can read
 * them without breaking any existing link.
 */
export function drillDownPath(
  collectionAppId: string,
  laneKey: string,
  isoDay: string,
): string {
  return (
    `/collections/${encodeURIComponent(collectionAppId)}` +
    `?tab=data-objects` +
    `&process-type=${encodeURIComponent(laneKey)}` +
    `&date=${encodeURIComponent(isoDay)}`
  );
}

/**
 * Quick boolean: does the envelope carry anything renderable? Used by the
 * pane to flip between the chart view and the empty-state placeholder.
 */
export function hasRenderableData(env: CollectionTimelineEnvelope | null): boolean {
  if (!env) return false;
  if (!env.lanes || env.lanes.length === 0) return false;
  return env.lanes.some(l => l.bins && l.bins.length > 0);
}

/**
 * Bin-size toggle helpers. Returns valid bin-size choices the toolbar
 * surfaces. The server's auto-coarsening will still snap upward when a
 * Collection is wider than the cap; the toolbar value is the user's
 * preference.
 */
export const BIN_SIZE_CHOICES = [
  { value: 1, label: "Day" },
  { value: 7, label: "Week" },
  { value: 30, label: "Month" },
];

/**
 * Empty-state copy. Surfaced when the envelope reports zero lanes — which
 * (given the backend coalesces unannotated DOs into an `unclassified` lane)
 * only happens when the Collection has zero non-deleted DataObjects. The
 * advice therefore points at creating DataObjects, not at annotating them.
 *
 * (Once at least one DataObject exists, the Timeline always shows at least
 * the `unclassified` lane. To split that lane into process-step swimlanes,
 * annotate the DOs with `urn:shepard:mffd:process-type` — but that's a
 * different problem from "the pane is blank".)
 */
export const EMPTY_STATE_HINT =
  "No DataObjects in this Collection yet. Create one (via the Data Objects " +
  "section above or a template) to populate the Timeline. Annotate the " +
  "DataObjects with `urn:shepard:mffd:process-type` to split the default " +
  "`unclassified` lane into process-step swimlanes.";
