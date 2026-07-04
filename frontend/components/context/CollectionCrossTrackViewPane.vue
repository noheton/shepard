<script setup lang="ts">
/**
 * TS-CROSS-DO-VIEW-2-FE — Cross-track small-multiples view for a Collection.
 *
 * For every DataObject in the Collection, resolves one channel by predicate
 * (default: `urn:shepard:afp:tcp-temperature-c`) and renders the series in
 * a 4-column grid of small multiples. Cells share x/y range so visual
 * comparison across DOs is direct.
 *
 * Reads the V102-seeded `Cross-ply TCP temperature` VIEW_RECIPE template by
 * default; falls back to a hard-coded `urn:shepard:afp:tcp-temperature-c`
 * predicate when no template is selected (the template surface is alpha,
 * full picker lands later — TPL-VIEW-EDITOR is separate scope).
 *
 * Cap: 100 DOs per request. Banner shows when truncated.
 * Hover sync: hovering a cell highlights the same x on every other cell.
 * Cell click: navigates to that DO's detail page.
 */
import { useCrossDoBulkData } from "~/composables/containers/useCrossDoBulkData";
import { useFetchAllDataObjects } from "~/composables/context/useFetchAllDataObjects";
import {
  applyDoCap,
  gridPosition,
  sharedXRange,
  sharedYRange,
  toCell,
  type NormalisedCell,
} from "~/utils/crossDoSmallMultiples";

import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
} from "echarts/components";

if (import.meta.client) {
  use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);
}

const props = withDefaults(
  defineProps<{
    collectionId: number;
    collectionAppId: string;
    /**
     * Annotation predicate IRI used to pick the channel on each DataObject.
     * Defaults to TCP temperature for the AFP-layup MFFD case. The
     * VIEW_RECIPE template seeded by V102 carries the same default.
     */
    channelPredicate?: string;
    /** LTTB target rows per series. Default 500. */
    downsampleTo?: number;
    /** Column count for the grid. Default 4 per the GAP-2 brief. */
    columns?: number;
  }>(),
  {
    channelPredicate: "urn:shepard:afp:tcp-temperature-c",
    downsampleTo: 500,
    columns: 4,
  },
);

// ── State ────────────────────────────────────────────────────────────────

const dataObjectAppIds = ref<string[]>([]);
const totalCount = ref(0);
const truncationBanner = ref<string | null>(null);
const initialError = ref<string | null>(null);

const { series, loading, error, fetchCrossDo } = useCrossDoBulkData();

const { dataObjects: allDataObjects, loading: doLoading } = useFetchAllDataObjects(
  props.collectionAppId,
  props.collectionId,
);
// doLoading tracks the DO list fetch; the template combines it with `loading`
// (cross-track fetch) via `fetching || loading`.
const fetching = doLoading;

const cells = computed<NormalisedCell[]>(() => series.value.map(toCell));
const xRange = computed(() => sharedXRange(cells.value));
const yRange = computed(() => sharedYRange(cells.value));

// Hover-sync: a reactive shared x position (in seconds) that each cell
// reflects via an echarts mark line. Null means no active hover.
const crossTime = ref<number | null>(null);

// Pick a wide time window — server-side LTTB handles density. Default to a
// year (~3.15e16 ns) which covers all reasonable MFFD test campaigns. The
// real bound lives in each DO's TimeseriesReference. Future revision: pull
// per-DO bounds from the reference start/end and union them.
const DEFAULT_START_NS = 0;
const DEFAULT_END_NS = 3_154_000_000_000_000; // ~year in ns

// ── Data flow ────────────────────────────────────────────────────────────

// React to the DO list arriving (or changing) and trigger the cross-track
// fetch. { immediate: true } handles the initial cold-cache load — fires
// once right away with [] (no-op), then again when the composable resolves.
watch(allDataObjects, async (dos) => {
  totalCount.value = dos.length;
  const appIds = dos
    .map(d => (d as unknown as { appId?: string }).appId ?? "")
    .filter(Boolean);
  const capped = applyDoCap(appIds, 100);
  dataObjectAppIds.value = capped.kept;
  truncationBanner.value = capped.banner;
  if (capped.kept.length === 0) return;
  await fetchCrossDo({
    dataObjectAppIds: capped.kept,
    channelPredicate: props.channelPredicate,
    start: DEFAULT_START_NS,
    end: DEFAULT_END_NS,
    downsampleTo: props.downsampleTo,
  });
}, { immediate: true });

// Manual reload — re-runs the cross-track fetch with the already-loaded DOs.
async function refresh(): Promise<void> {
  const kept = dataObjectAppIds.value;
  if (kept.length === 0) return;
  await fetchCrossDo({
    dataObjectAppIds: kept,
    channelPredicate: props.channelPredicate,
    start: DEFAULT_START_NS,
    end: DEFAULT_END_NS,
    downsampleTo: props.downsampleTo,
  });
}

// ── ECharts cell option builder ──────────────────────────────────────────

function cellOption(cell: NormalisedCell) {
  const xMax = xRange.value ? xRange.value.max : 1;
  const yMin = yRange.value ? yRange.value.min : 0;
  const yMax = yRange.value ? yRange.value.max : 1;
  return {
    grid: { left: 36, right: 8, top: 22, bottom: 22 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "line" },
      formatter: (params: Array<{ data?: [number, number] }>) => {
        const p = params[0]?.data;
        if (!p) return "";
        crossTime.value = p[0];
        return `t=${p[0].toFixed(2)} s<br/>v=${p[1].toFixed(2)}`;
      },
    },
    xAxis: {
      type: "value",
      min: 0,
      max: xMax,
      name: "t (s)",
      nameLocation: "middle",
      nameGap: 16,
      axisLabel: { fontSize: 9 },
      nameTextStyle: { fontSize: 9 },
    },
    yAxis: {
      type: "value",
      min: yMin,
      max: yMax,
      name: "°C",
      nameLocation: "middle",
      nameGap: 30,
      axisLabel: { fontSize: 9 },
      nameTextStyle: { fontSize: 9 },
    },
    series: [
      {
        type: "line",
        showSymbol: false,
        smooth: false,
        lineStyle: { width: 1.2 },
        data: cell.relativePoints,
        markLine:
          crossTime.value !== null
            ? {
                symbol: ["none", "none"],
                lineStyle: { color: "#888", type: "dashed", width: 1 },
                data: [{ xAxis: crossTime.value }],
                animation: false,
                label: { show: false },
              }
            : undefined,
      },
    ],
  };
}

function onCellClick(cell: NormalisedCell): void {
  navigateTo(`/dataobjects/${cell.dataObjectAppId}`);
}

function clearHover(): void {
  crossTime.value = null;
}

</script>

<template>
  <div class="cross-track-pane">
    <div class="cross-track-header">
      <div class="text-subtitle-1">Cross-track view</div>
      <div class="text-caption text-medium-emphasis">
        Predicate: <code>{{ channelPredicate }}</code> · Cap: 100 DOs · LTTB target: {{ downsampleTo }} pts/series
      </div>
      <v-btn
        size="x-small"
        variant="text"
        :loading="fetching || loading"
        :disabled="fetching || loading"
        @click="refresh"
      >
        Reload
      </v-btn>
    </div>

    <v-alert
      v-if="truncationBanner"
      type="info"
      density="compact"
      variant="tonal"
      class="mt-2 mb-2"
    >
      {{ truncationBanner }}
    </v-alert>
    <v-alert
      v-if="initialError"
      type="error"
      density="compact"
      variant="tonal"
      class="mt-2 mb-2"
    >
      {{ initialError }}
    </v-alert>
    <v-alert
      v-if="error"
      type="error"
      density="compact"
      variant="tonal"
      class="mt-2 mb-2"
    >
      {{ error }}
    </v-alert>

    <div v-if="!fetching && !loading && cells.length === 0" class="empty-hint">
      No DataObjects in this Collection — nothing to plot.
    </div>

    <div
      v-if="cells.length > 0"
      class="small-multiples-grid"
      :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }"
      data-testid="cross-track-grid"
      @mouseleave="clearHover"
    >
      <div
        v-for="(cell, i) in cells"
        :key="cell.dataObjectAppId"
        class="cell"
        :data-col="gridPosition(i, columns).col"
        :data-row="gridPosition(i, columns).row"
        :data-app-id="cell.dataObjectAppId"
        @click="onCellClick(cell)"
      >
        <div class="cell-label">
          <span class="cell-label-title">{{ cell.label }}</span>
          <span v-if="cell.channelSymbolicName" class="cell-label-channel">
            {{ cell.channelSymbolicName }}
          </span>
        </div>
        <div v-if="cell.hasData" class="cell-chart">
          <VChart :option="cellOption(cell)" autoresize />
        </div>
        <div v-else class="cell-empty">
          no matching channel
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cross-track-pane {
  width: 100%;
}
.cross-track-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.small-multiples-grid {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}
.cell {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.015);
  padding: 4px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  min-height: 130px;
}
.cell:hover {
  background: rgba(0, 0, 0, 0.04);
}
.cell-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  line-height: 1.2;
  padding: 0 4px 2px 4px;
}
.cell-label-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-label-channel {
  opacity: 0.6;
  margin-left: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-chart {
  flex: 1;
  min-height: 100px;
}
.cell-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  opacity: 0.45;
  font-style: italic;
}
.empty-hint {
  margin-top: 12px;
  font-size: 13px;
  opacity: 0.6;
}
</style>
