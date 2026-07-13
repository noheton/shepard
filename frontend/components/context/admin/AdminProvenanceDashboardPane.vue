<script setup lang="ts">
/**
 * AdminProvenanceDashboardPane — instance-wide provenance dashboard (PROV1e).
 *
 * Two panels:
 *   1. Stats + sparkline — fetches `GET /v2/provenance/stats?scope=instance`
 *      and renders totals / action-kind histogram / daily-bucket bar sparkline.
 *   2. Activity feed — renders `AdminActivityLogPane` (server-side filtered,
 *      paginated, includes client-side text + action-kind chip filtering).
 *
 * Admin-only: the `/admin/provenance` page guards on `instance-admin` before
 * mounting this pane; the backend also rejects non-admins on scope=instance.
 *
 * Design: `aidocs/workflows/55-provenance-and-activity-overhaul.md §PROV1e`
 */
import { useFetchProvenanceStats } from "~/composables/context/useFetchProvenanceStats";

const DAY_MILLIS = 24 * 60 * 60 * 1000;

// UIRULE-DROPDOWN-SEARCH-SORT exception: 4-option time-range enum in deliberate
// ascending order (7d→1y) — kept as v-select, not natural-sorted.
const RANGES = [
  { title: "Last 7 days", days: 7 },
  { title: "Last 30 days", days: 30 },
  { title: "Last 90 days", days: 90 },
  { title: "Last year", days: 365 },
];
const selectedDays = ref<number>(30);

const { stats, isLoading, refresh } = useFetchProvenanceStats({
  scope: "instance",
  sinceMillis: Date.now() - selectedDays.value * DAY_MILLIS,
});

function onRangeChange(days: number) {
  selectedDays.value = days;
  refresh(Date.now() - days * DAY_MILLIS, Date.now());
}

// ── Action-kind histogram ────────────────────────────────────────────────────

interface ActionKindSlice {
  kind: string;
  count: number;
  color: string;
}

const ACTION_KIND_COLORS: Record<string, string> = {
  CREATE: "#4caf50",
  UPDATE: "#2196f3",
  DELETE: "#f44336",
  READ: "#9e9e9e",
  EXECUTE: "#ff9800",
};
const DEFAULT_COLOR = "#9e9e9e";

const actionKindSlices = computed<ActionKindSlice[]>(() => {
  const totals = stats.value?.totalsByActionKind ?? {};
  return Object.entries(totals)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => ({ kind, count: Number(count), color: ACTION_KIND_COLORS[kind] ?? DEFAULT_COLOR }))
    .sort((a, b) => b.count - a.count);
});

const actionKindTotal = computed(() =>
  actionKindSlices.value.reduce((sum, s) => sum + s.count, 0),
);

function sliceWidthPct(count: number): number {
  const total = actionKindTotal.value;
  return total === 0 ? 0 : (count / total) * 100;
}

// ── Content census tiles ─────────────────────────────────────────────────────

interface CensusTile {
  key: string;
  label: string;
  icon: string;
  count: number;
}

const CENSUS_META: Record<string, { label: string; icon: string }> = {
  dataObjects: { label: "Data Objects", icon: "mdi-cube-outline" },
  fileReferences: { label: "File References", icon: "mdi-file-outline" },
  timeseriesReferences: { label: "Timeseries References", icon: "mdi-chart-line" },
  structuredDataReferences: { label: "Structured Data", icon: "mdi-code-json" },
  spatialDataReferences: { label: "Spatial Data", icon: "mdi-map-outline" },
  labJournalEntries: { label: "Lab Journal Entries", icon: "mdi-notebook-outline" },
};

const censusTiles = computed<CensusTile[]>(() => {
  const census = stats.value?.contentCensus;
  if (!census) return [];
  return Object.entries(census)
    .filter(([key]) => CENSUS_META[key] !== undefined)
    .map(([key, count]) => {
      const meta = CENSUS_META[key]!;
      return {
        key,
        label: meta.label,
        icon: meta.icon,
        count: Number(count),
      };
    })
    .sort((a, b) => b.count - a.count);
});

// ── SVG sparkline ────────────────────────────────────────────────────────────

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

  const sinceMs = new Date(s.since).getTime();
  const untilMs = new Date(s.until).getTime();
  const windowMs = Math.max(1, untilMs - sinceMs);
  const maxCount = Math.max(1, ...s.buckets.map((b) => b[1] ?? 0));

  const innerW = SVG_W - PAD_X * 2;
  const innerH = SVG_H - PAD_Y_TOP - PAD_Y_BOTTOM;

  const totalBuckets = Math.max(1, Math.floor(windowMs / s.bucketMillis));
  const barW = Math.min(24, Math.max(2, innerW / totalBuckets - 1));

  return s.buckets.map((entry) => {
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
  const sinceMs = new Date(s.since).getTime();
  const untilMs = new Date(s.until).getTime();
  const windowMs = Math.max(1, untilMs - sinceMs);
  const innerW = SVG_W - PAD_X * 2;
  const innerH = SVG_H - PAD_Y_TOP - PAD_Y_BOTTOM;
  const maxRunning = Math.max(1, ...s.cumulative.map((b) => b[1] ?? 0));

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

const hoverBar = ref<Bar | null>(null);

function formatBucketLabel(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

const bucketLabel = computed<string>(() => {
  const w = stats.value?.bucketMillis ?? DAY_MILLIS;
  return w >= 7 * DAY_MILLIS ? "weekly" : "daily";
});

const isEmpty = computed(
  () => !isLoading.value && (stats.value?.totalCount ?? 0) === 0,
);

// ── Byte totals ──────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`;
  if (mb >= 1) return `${mb.toFixed(0)} MB`;
  return `${bytes} B`;
}

const fileBytesLabel = computed<string | null>(() => {
  const b = stats.value?.byteTotals?.fileBytes;
  return b != null ? formatBytes(Number(b)) : null;
});
</script>

<template>
  <div class="d-flex flex-column ga-6">
    <h4 class="text-h4">Provenance Dashboard</h4>

    <!-- ── Stats card ──────────────────────────────────────────────────── -->
    <v-card variant="outlined">
      <v-card-title>
        <div class="d-flex align-center justify-space-between">
          <div class="d-flex align-center ga-2">
            <v-icon icon="mdi-chart-timeline-variant" size="20" />
            <span class="text-subtitle-1 font-weight-medium">Instance Activity</span>
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
      </v-card-title>

      <v-card-text>
        <CenteredLoadingSpinner v-if="isLoading && !stats" />

        <template v-else-if="isEmpty">
          <div class="text-medium-emphasis pa-4 text-center">
            <p class="mb-1">No recorded activity in the selected window.</p>
            <p class="text-caption">
              Older activity may have been pruned — shepard keeps provenance rows
              for 2 years by default (configurable via
              <code>shepard.provenance.retention-days</code>).
            </p>
          </div>
        </template>

        <template v-else>
          <!-- Totals strip -->
          <div class="d-flex flex-wrap ga-4 mb-4">
            <div class="stat-tile">
              <div class="text-h5 font-weight-medium">{{ stats?.totalCount ?? 0 }}</div>
              <div class="text-caption text-medium-emphasis">activities</div>
            </div>
            <div class="stat-tile">
              <div class="text-h5 font-weight-medium">{{ stats?.distinctAgents ?? 0 }}</div>
              <div class="text-caption text-medium-emphasis">distinct contributors</div>
            </div>
            <div v-if="fileBytesLabel" class="stat-tile">
              <div class="text-h5 font-weight-medium">{{ fileBytesLabel }}</div>
              <div class="text-caption text-medium-emphasis">stored files (lower bound)</div>
            </div>

            <!-- Action-kind histogram bar -->
            <div v-if="actionKindSlices.length > 0" class="stat-tile flex-grow-1" style="min-width: 240px">
              <div
                class="action-bar mb-1"
                role="img"
                :aria-label="'Action-kind histogram: ' + actionKindSlices.map(s => `${s.kind} ${s.count}`).join(', ')"
              >
                <div
                  v-for="slice in actionKindSlices"
                  :key="slice.kind"
                  class="action-bar-slice"
                  :style="{ width: sliceWidthPct(slice.count) + '%', backgroundColor: slice.color }"
                  :title="`${slice.kind}: ${slice.count}`"
                />
              </div>
              <div class="d-flex flex-wrap ga-2 mt-1">
                <div
                  v-for="slice in actionKindSlices"
                  :key="slice.kind"
                  class="text-caption d-flex align-center ga-1"
                >
                  <span class="legend-swatch" :style="{ backgroundColor: slice.color }" />
                  <span>{{ slice.kind }} ({{ slice.count }})</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Sparkline + cumulative overlay -->
          <div class="sparkline-container" @mouseleave="hoverBar = null">
            <svg
              :viewBox="`0 0 ${SVG_W} ${SVG_H}`"
              class="sparkline-svg"
              role="img"
              :aria-label="`Instance activity over time, ${bucketLabel} buckets`"
            >
              <line
                :x1="PAD_X"
                :x2="SVG_W - PAD_X"
                :y1="SVG_H - PAD_Y_BOTTOM"
                :y2="SVG_H - PAD_Y_BOTTOM"
                stroke="rgba(0,0,0,0.12)"
                stroke-width="1"
              />
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
            <div class="text-caption text-medium-emphasis d-flex align-center ga-1">
              <span class="legend-swatch" style="background-color: #1976d2" />
              per-{{ bucketLabel === "weekly" ? "week" : "day" }} count
            </div>
            <div class="text-caption text-medium-emphasis d-flex align-center ga-1">
              <span class="legend-swatch" style="background-color: #ff9800" />
              cumulative total
            </div>
          </div>
        </template>
      </v-card-text>
    </v-card>

    <!-- ── Content census ──────────────────────────────────────────────── -->
    <div v-if="censusTiles.length > 0">
      <h5 class="text-h6 mb-3">Instance Content</h5>
      <v-row dense>
        <v-col
          v-for="tile in censusTiles"
          :key="tile.key"
          cols="6"
          sm="4"
          md="2"
        >
          <v-card variant="tonal" class="pa-3 text-center">
            <v-icon :icon="tile.icon" size="24" class="mb-1 text-medium-emphasis" />
            <div class="text-h6 font-weight-bold">{{ tile.count.toLocaleString() }}</div>
            <div class="text-caption text-medium-emphasis">{{ tile.label }}</div>
          </v-card>
        </v-col>
      </v-row>
    </div>

    <!-- ── Activity feed ───────────────────────────────────────────────── -->
    <AdminActivityLogPane />
  </div>
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
