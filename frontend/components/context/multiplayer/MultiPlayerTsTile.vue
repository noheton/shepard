<script setup lang="ts">
/**
 * MFFD-MULTIPLAYER-1 — Timeseries tile for the multi-payload synchronised player.
 *
 * <p>Renders one annotated channel for the DataObject as a small ECharts line.
 * Mirrors the AAA3 cross-track cell idiom: tooltip writes {@code currentTime}
 * into the shared cursor; an echarts mark-line reads it back to draw the
 * cursor on this cell. When this tile is the source of the hover, every
 * other tile in the grid updates in lock-step.
 *
 * <p>The tile registers its playable range with the shared cursor so the
 * intersected range used by the toolbar scrubber accounts for the
 * timeseries' actual time bounds (TS-V11 {@code timeBoundsStart/End}).
 *
 * <p>Time units: this tile uses DO-relative milliseconds in the cursor;
 * the chart's x-axis renders seconds-from-start internally for readability.
 *
 * <p>Data fetch: reuses the cross-DO bulk endpoint with a single-DO request
 * — this is the same pipeline {@code CollectionCrossTrackViewPane} uses and
 * already handles LTTB downsampling server-side.
 */
import { computed, onMounted, onUnmounted, watch } from "vue";
import { useCrossDoBulkData, nsToIso } from "~/composables/containers/useCrossDoBulkData";
import { useSyncedTimeCursor } from "~/composables/context/useSyncedTimeCursor";

import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";

if (import.meta.client) {
  use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);
}

const props = withDefaults(
  defineProps<{
    dataObjectAppId: string;
    /** Predicate IRI used to pick the channel. Defaults to TCP temperature. */
    channelPredicate?: string;
    /** LTTB target rows. Default 500. */
    downsampleTo?: number;
  }>(),
  {
    channelPredicate: "urn:shepard:afp:tcp-temperature-c",
    downsampleTo: 500,
  },
);

const cursor = useSyncedTimeCursor();
const { series, loading, error, fetchCrossDo } = useCrossDoBulkData();

const DEFAULT_START_NS = 0;
const DEFAULT_END_NS = 3_154_000_000_000_000;
const DEFAULT_START_ISO = nsToIso(DEFAULT_START_NS);
const DEFAULT_END_ISO = nsToIso(DEFAULT_END_NS);

const cell = computed(() => series.value[0] ?? null);

// Convert series points to [tSeconds, value] pairs (seconds-from-first-sample).
const points = computed<Array<[number, number]>>(() => {
  const c = cell.value;
  if (!c || !c.points || c.points.length === 0) return [];
  const firstNs = c.points[0]!.timestamp;
  return c.points.map(p => [
    (p.timestamp - firstNs) / 1e9,
    typeof p.value === "number" ? p.value : Number(p.value),
  ]);
});

const tMaxS = computed(() => {
  const pts = points.value;
  if (pts.length === 0) return 1;
  const last = pts[pts.length - 1];
  return last ? last[0] : 1;
});

// Register playable range. ms in cursor = seconds * 1000.
let unregister: (() => void) | null = null;
watch(
  () => tMaxS.value,
  tEnd => {
    if (unregister) unregister();
    if (tEnd > 0) {
      unregister = cursor.registerRange({
        id: `ts:${props.dataObjectAppId}`,
        start: 0,
        end: tEnd * 1000,
      });
    }
  },
);

onUnmounted(() => {
  unregister?.();
});

onMounted(async () => {
  await fetchCrossDo({
    dataObjectAppIds: [props.dataObjectAppId],
    channelPredicate: props.channelPredicate,
    start: DEFAULT_START_ISO,
    end: DEFAULT_END_ISO,
    downsampleTo: props.downsampleTo,
  });
});

// Echarts option. Tooltip formatter writes the cursor; markLine reads it.
const option = computed(() => {
  const pts = points.value;
  const cursorS = cursor.currentTime.value / 1000;
  return {
    grid: { left: 40, right: 8, top: 16, bottom: 28 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "line" },
      formatter: (params: Array<{ data?: [number, number] }>) => {
        const p = params[0]?.data;
        if (!p) return "";
        cursor.seek(p[0] * 1000);
        return `t=${p[0].toFixed(2)} s<br/>v=${p[1].toFixed(2)}`;
      },
    },
    xAxis: {
      type: "value",
      min: 0,
      max: tMaxS.value,
      name: "t (s)",
      nameLocation: "middle",
      nameGap: 18,
      axisLabel: { fontSize: 10 },
    },
    yAxis: {
      type: "value",
      name: cell.value?.channelSymbolicName ?? "value",
      nameLocation: "middle",
      nameGap: 32,
      axisLabel: { fontSize: 10 },
      scale: true,
    },
    series: [
      {
        type: "line",
        showSymbol: false,
        smooth: false,
        lineStyle: { width: 1.4 },
        data: pts,
        markLine: {
          symbol: ["none", "none"],
          lineStyle: { color: "#1976d2", type: "solid", width: 1.5 },
          data: [{ xAxis: cursorS }],
          animation: false,
          label: { show: false },
        },
      },
    ],
  };
});
</script>

<template>
  <div class="ts-tile">
    <div class="tile-label">
      <span class="title">Timeseries</span>
      <span v-if="cell?.channelSymbolicName" class="channel">
        {{ cell.channelSymbolicName }}
      </span>
    </div>
    <div v-if="loading" class="status">Loading channel...</div>
    <v-alert
      v-else-if="error"
      type="warning"
      density="compact"
      variant="tonal"
    >
      {{ error }}
    </v-alert>
    <div v-else-if="points.length === 0" class="status">
      No matching channel for predicate <code>{{ channelPredicate }}</code>.
    </div>
    <div v-else class="chart">
      <VChart :option="option" autoresize />
    </div>
  </div>
</template>

<style scoped>
.ts-tile {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 220px;
}
.tile-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  padding-bottom: 4px;
}
.channel {
  font-weight: 400;
  opacity: 0.7;
}
.chart {
  flex: 1;
  min-height: 180px;
}
.status {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.6;
  font-style: italic;
}
</style>
