<script setup lang="ts">
/**
 * PROV1d — per-Collection activity sparkline dashboard.
 *
 * Fetches `GET /v2/provenance/stats?scope=collection&id={appId}` and
 * renders three panels:
 *
 *   1. Header strip — total activity count + distinct-agent count + a
 *      small horizontal stacked bar for the action-kind histogram.
 *   2. Sparkline — vanilla SVG bar / area chart of bucketed counts.
 *      Overlay second SVG path = cumulative integral (PROV1c).
 *   3. (Implicit via the picker) time-range presets that re-fetch the
 *      window.
 *
 * Vanilla SVG (no Chart.js) by design — see `aidocs/55 §5`:
 *   "lightweight SVG, no Chart.js dep".
 */
import { useFetchProvenanceStats } from "~/composables/context/useFetchProvenanceStats";

const props = defineProps<{
  /** The Collection's UUID v7 `appId` (see `aidocs/25` L2). */
  collectionAppId: string | null | undefined;
}>();

const DAY_MILLIS = 24 * 60 * 60 * 1000;

/** Preset window options for the picker. */
const RANGES = [
  { title: "Last 7 days", days: 7 },
  { title: "Last 30 days", days: 30 },
  { title: "Last 90 days", days: 90 },
  { title: "Last year", days: 365 },
];

const selectedDays = ref<number>(30);

const { stats, isLoading, refresh } = useFetchProvenanceStats({
  scope: "collection",
  id: props.collectionAppId ?? undefined,
  sinceMillis: Date.now() - selectedDays.value * DAY_MILLIS,
});

watch(
  () => props.collectionAppId,
  newAppId => {
    if (newAppId) {
      refresh(Date.now() - selectedDays.value * DAY_MILLIS);
    }
  },
);

function onRangeChange(days: number) {
  selectedDays.value = days;
  refresh(Date.now() - days * DAY_MILLIS, Date.now());
}

/** Total activities in the window. Renders `—` until the fetch lands. */
const totalCount = computed(() => stats.value?.totalCount ?? 0);
const distinctAgents = computed(() => stats.value?.distinctAgents ?? 0);

/**
 * Per-action-kind histogram, normalised into an array sorted by
 * decreasing count for the stacked bar. The backend returns an
 * open-shape Map<String,Long> per `ProvenanceStatsIO`, so we don't
 * hard-code the four HTTP-verb kinds here.
 */
interface ActionKindSlice {
  kind: string;
  count: number;
  color: string;
}

const ACTION_KIND_COLORS: Record<string, string> = {
  CREATE: "#4caf50", // green
  UPDATE: "#2196f3", // blue
  DELETE: "#f44336", // red
  READ: "#9e9e9e", // grey
  EXECUTE: "#ff9800", // orange
};
const DEFAULT_COLOR = "#9e9e9e";

const actionKindSlices = computed<ActionKindSlice[]>(() => {
  const totals = stats.value?.totalsByActionKind ?? {};
  return Object.entries(totals)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => ({
      kind,
      count: Number(count),
      color: ACTION_KIND_COLORS[kind] ?? DEFAULT_COLOR,
    }))
    .sort((a, b) => b.count - a.count);
});

const actionKindTotal = computed(() =>
  actionKindSlices.value.reduce((sum, s) => sum + s.count, 0),
);

function sliceWidthPct(count: number): number {
  const total = actionKindTotal.value;
  if (total === 0) return 0;
  return (count / total) * 100;
}

/**
 * SVG sparkline geometry.
 *
 * We render two coordinated paths inside one viewBox:
 *
 *   - A bar chart of per-bucket activity counts.
 *   - A polyline of the running cumulative count (PROV1c).
 *
 * The backend emits sparse buckets (empty days are NOT filled) — we
 * project them onto a continuous timeline by computing each bucket's
 * X position from `(bucketStart - sinceMillis) / windowMillis`. Empty
 * intervals stay empty visually.
 */
const SVG_W = 600;
const SVG_H = 120;
const PAD_X = 8;
const PAD_Y_TOP = 4;
const PAD_Y_BOTTOM = 16;

interface Bar {
  x: number;
  y: number;
  width: number;
  height: number;
  bucketStartMillis: number;
  count: number;
}

const sparklineBars = computed<Bar[]>(() => {
  const s = stats.value;
  if (!s || !s.buckets || s.buckets.length === 0) return [];

  const sinceMs = s.sinceMillis;
  const untilMs = s.untilMillis;
  const windowMs = Math.max(1, untilMs - sinceMs);
  const maxCount = Math.max(1, ...s.buckets.map(b => b[1] ?? 0));

  const innerW = SVG_W - PAD_X * 2;
  const innerH = SVG_H - PAD_Y_TOP - PAD_Y_BOTTOM;

  // Width of one bucket bar — keep at least 2 px, no more than 24 px.
  const totalBuckets = Math.max(1, Math.floor(windowMs / s.bucketMillis));
  const barW = Math.min(24, Math.max(2, innerW / totalBuckets - 1));

  return s.buckets.map(entry => {
    const bucketStartMillis = entry[0] ?? 0;
    const count = entry[1] ?? 0;
    const xFrac = (bucketStartMillis - sinceMs) / windowMs;
    const x = PAD_X + xFrac * innerW;
    const h = (count / maxCount) * innerH;
    const y = PAD_Y_TOP + (innerH - h);
    return { x, y, width: barW, height: h, bucketStartMillis, count };
  });
});

const cumulativePath = computed<string>(() => {
  const s = stats.value;
  if (!s || !s.cumulative || s.cumulative.length === 0) return "";
  const sinceMs = s.sinceMillis;
  const untilMs = s.untilMillis;
  const windowMs = Math.max(1, untilMs - sinceMs);
  const innerW = SVG_W - PAD_X * 2;
  const innerH = SVG_H - PAD_Y_TOP - PAD_Y_BOTTOM;
  const maxRunning = Math.max(1, ...s.cumulative.map(b => b[1] ?? 0));

  return s.cumulative
    .map((entry, idx) => {
      const t = entry[0] ?? 0;
      const running = entry[1] ?? 0;
      const xFrac = (t - sinceMs) / windowMs;
      const x = PAD_X + xFrac * innerW;
      const y = PAD_Y_TOP + (innerH - (running / maxRunning) * innerH);
      return `${idx === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
});

/** Hover state for the tooltip. */
const hoverBar = ref<Bar | null>(null);

function formatBucketLabel(bucketStartMillis: number): string {
  const d = new Date(bucketStartMillis);
  // ISO date — locale-independent, accessible, no surprises for non-English UIs.
  return d.toISOString().slice(0, 10);
}

const isEmpty = computed(
  () => !isLoading.value && (stats.value?.totalCount ?? 0) === 0,
);

/**
 * Bucket-width caveat: the backend auto-flips daily → weekly at > 90-day
 * windows. Communicate this in the tooltip + the empty-state hint.
 */
const bucketLabel = computed<string>(() => {
  const w = stats.value?.bucketMillis ?? DAY_MILLIS;
  if (w >= 7 * DAY_MILLIS) return "weekly";
  return "daily";
});
</script>

<template>
  <v-card class="activity-sparkline-card pa-4" elevation="0" variant="outlined">
    <div class="d-flex align-center justify-space-between mb-3">
      <div class="d-flex align-center ga-2">
        <v-icon icon="mdi-chart-timeline-variant" size="20" />
        <span class="text-subtitle-1 font-weight-medium">Activity</span>
      </div>
      <div style="min-width: 180px">
        <v-select
          :model-value="selectedDays"
          :items="RANGES"
          item-title="title"
          item-value="days"
          density="compact"
          variant="outlined"
          hide-details
          @update:model-value="onRangeChange"
        />
      </div>
    </div>

    <CenteredLoadingSpinner v-if="isLoading && !stats" />

    <template v-else-if="isEmpty">
      <div class="text-medium-emphasis pa-4 text-center">
        <p class="mb-1">No recorded activity in the selected window.</p>
        <p class="text-caption">
          Older activity may have been pruned — shepard keeps provenance rows
          for 2 years by default (operator-configurable via
          <code>shepard.provenance.retention-days</code>).
        </p>
      </div>
    </template>

    <template v-else>
      <!-- Panel 1: Header strip — totals + distinct agents + action-kind histogram. -->
      <div class="d-flex flex-wrap ga-4 mb-4">
        <div class="stat-tile">
          <div class="text-h5 font-weight-medium">{{ totalCount }}</div>
          <div class="text-caption text-medium-emphasis">
            {{ totalCount === 1 ? "activity" : "activities" }}
          </div>
        </div>
        <div class="stat-tile">
          <div class="text-h5 font-weight-medium">{{ distinctAgents }}</div>
          <div class="text-caption text-medium-emphasis">
            distinct contributor{{ distinctAgents === 1 ? "" : "s" }}
          </div>
        </div>
        <div class="stat-tile flex-grow-1" style="min-width: 240px">
          <div
            v-if="actionKindSlices.length > 0"
            class="action-bar"
            role="img"
            :aria-label="
              'Action-kind histogram: ' +
              actionKindSlices.map(s => `${s.kind} ${s.count}`).join(', ')
            "
          >
            <div
              v-for="slice in actionKindSlices"
              :key="slice.kind"
              class="action-bar-slice"
              :style="{
                width: sliceWidthPct(slice.count) + '%',
                backgroundColor: slice.color,
              }"
              :title="`${slice.kind}: ${slice.count}`"
            />
          </div>
          <div class="d-flex flex-wrap ga-2 mt-1">
            <div
              v-for="slice in actionKindSlices"
              :key="slice.kind"
              class="text-caption d-flex align-center ga-1"
            >
              <span
                class="legend-swatch"
                :style="{ backgroundColor: slice.color }"
              />
              <span>{{ slice.kind }} ({{ slice.count }})</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Panel 2: Sparkline + cumulative integral overlay. -->
      <div class="sparkline-container" @mouseleave="hoverBar = null">
        <svg
          :viewBox="`0 0 ${SVG_W} ${SVG_H}`"
          class="sparkline-svg"
          role="img"
          :aria-label="`Activity over time, ${bucketLabel} buckets`"
        >
          <!-- Axis baseline. -->
          <line
            :x1="PAD_X"
            :x2="SVG_W - PAD_X"
            :y1="SVG_H - PAD_Y_BOTTOM"
            :y2="SVG_H - PAD_Y_BOTTOM"
            stroke="rgba(0,0,0,0.12)"
            stroke-width="1"
          />

          <!-- Bars — per-bucket activity count. -->
          <rect
            v-for="(bar, idx) in sparklineBars"
            :key="idx"
            :x="bar.x"
            :y="bar.y"
            :width="bar.width"
            :height="bar.height"
            fill="#1976d2"
            opacity="0.85"
            @mouseenter="hoverBar = bar"
          />

          <!-- Cumulative-integral overlay. -->
          <path
            v-if="cumulativePath"
            :d="cumulativePath"
            fill="none"
            stroke="#ff9800"
            stroke-width="1.5"
            stroke-linejoin="round"
            opacity="0.9"
          />
        </svg>
        <div v-if="hoverBar" class="sparkline-tooltip">
          {{ formatBucketLabel(hoverBar.bucketStartMillis) }} —
          {{ hoverBar.count }}
          {{ hoverBar.count === 1 ? "activity" : "activities" }}
        </div>
      </div>

      <div class="d-flex justify-space-between mt-2">
        <div class="text-caption text-medium-emphasis">
          <span
            class="legend-swatch"
            style="background-color: #1976d2"
          />
          per-{{ bucketLabel === "weekly" ? "week" : "day" }} count
        </div>
        <div class="text-caption text-medium-emphasis">
          <span
            class="legend-swatch"
            style="background-color: #ff9800"
          />
          cumulative total
        </div>
      </div>
    </template>
  </v-card>
</template>

<style scoped>
.stat-tile {
  min-width: 110px;
}
.action-bar {
  display: flex;
  width: 100%;
  height: 12px;
  border-radius: 6px;
  overflow: hidden;
  background-color: rgba(0, 0, 0, 0.04);
}
.action-bar-slice {
  height: 100%;
}
.legend-swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  vertical-align: middle;
  margin-right: 4px;
}
.sparkline-container {
  position: relative;
  width: 100%;
}
.sparkline-svg {
  width: 100%;
  height: 120px;
  display: block;
}
.sparkline-tooltip {
  position: absolute;
  top: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.75);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  pointer-events: none;
}
</style>
