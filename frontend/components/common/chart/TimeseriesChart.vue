<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  ToolboxComponent,
  TooltipComponent,
} from "echarts/components";
import type { TimeseriesSeries } from "./types";

if (process.client) {
  use([
    CanvasRenderer,
    LineChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    DataZoomComponent,
    ToolboxComponent,
  ]);
}

const props = withDefaults(
  defineProps<{
    series: TimeseriesSeries[];
    height?: string;
    showLegend?: boolean;
    /**
     * Smooth the line via echarts spline interpolation. Useful for
     * high-frequency live-mode feeds where raw points look spiky.
     * Off by default — preserves exact-value fidelity for analytical
     * use. Live-mode charts flip this on.
     */
    smooth?: boolean;
    /**
     * Render as a step-line (echarts `step: 'end'`). Appropriate for
     * boolean / switch channels where the value jumps discretely.
     * Overrides smooth when both are true.
     */
    step?: boolean;
    /**
     * Animation duration in milliseconds for data transitions.
     * 0 (default) = instant snap. Set to e.g. 400 in live mode so each
     * data refresh animates smoothly instead of jumping.
     */
    animationDuration?: number;
    /**
     * Restrict the visible X window to the last N milliseconds of data.
     * When set, the chart clips to [latestPoint - N, latestPoint] so only
     * the intended live window is shown, even though the series data may
     * contain older anchor points needed for a smooth line start.
     * Undefined (default) = show all data.
     */
    visibleWindowMs?: number;
  }>(),
  { height: "360px", showLegend: false, smooth: false, step: false, animationDuration: 0 },
);

// Max timestamp across all series (ms). Recomputes whenever series data changes.
// Used to anchor the xAxis clip window to the latest data rather than Date.now(),
// so the right edge is the most recent sample, not a future "now" gap.
const dataMaxMs = computed(() => {
  let max = 0;
  for (const s of props.series) {
    for (const [tsNs] of s.data) {
      const ms = tsNs / 1e6;
      if (ms > max) max = ms;
    }
  }
  return max;
});

const chartOption = computed(() => ({
  backgroundColor: "transparent",
  animation: props.animationDuration > 0,
  animationDuration: props.animationDuration,
  animationDurationUpdate: props.animationDuration,
  animationEasing: "linear" as const,
  animationEasingUpdate: "linear" as const,
  tooltip: {
    trigger: "axis",
    axisPointer: { type: "cross", snap: true },
    formatter: (params: any[]) => {
      if (!params?.length) return "";
      const tsMs = params[0].value[0];
      const date = new Date(tsMs);
      const header = date.toLocaleString("en-GB", {
        year: "2-digit",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
      const rows = params
        .map(
          (p: any) =>
            `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${p.color};"></span>${p.seriesName}: <b>${typeof p.value[1] === "number" ? p.value[1].toPrecision(6) : p.value[1]}</b>`,
        )
        .join("<br/>");
      return `${header}<br/>${rows}`;
    },
  },
  legend: props.showLegend ? { type: "scroll", bottom: 0 } : { show: false },
  grid: {
    top: "8px",
    left: "12px",
    right: "24px",
    bottom: props.showLegend ? "80px" : "56px",
    containLabel: true,
  },
  toolbox: {
    right: 8,
    top: 2,
    feature: {
      dataZoom: { yAxisIndex: "none", title: { zoom: "Zoom", back: "Reset" } },
      saveAsImage: { title: "Save PNG", name: "timeseries" },
    },
  },
  xAxis: {
    type: "time",
    axisLabel: { fontSize: 11 },
    // When visibleWindowMs is set (live mode), pin the view to
    // [latestPoint − window, latestPoint] so the extra anchor data
    // fetched before the window boundary stays off-screen but still
    // allows the line to cross the left clip edge without starting mid-air.
    ...(props.visibleWindowMs != null && dataMaxMs.value > 0
      ? { min: dataMaxMs.value - props.visibleWindowMs, max: dataMaxMs.value }
      : {}),
  },
  yAxis: {
    type: "value",
    splitLine: { lineStyle: { opacity: 0.5 } },
    axisLabel: {
      fontSize: 11,
      // In step mode, format 0/1 as OFF/ON for switch/boolean channels.
      formatter: props.step
        ? (v: number) => v === 1 ? "ON" : v === 0 ? "OFF" : String(v)
        : undefined,
    },
    ...(props.step ? { min: 0, max: 1 } : {}),
  },
  dataZoom: [
    {
      type: "inside",
      xAxisIndex: 0,
      filterMode: "none",
    },
    {
      type: "slider",
      xAxisIndex: 0,
      filterMode: "none",
      height: 20,
      bottom: props.showLegend ? 52 : 8,
      borderColor: "transparent",
    },
  ],
  series: props.series.map(s => ({
    name: s.name,
    type: "line",
    symbol: "none",
    smooth: props.step ? false : props.smooth,
    step: props.step ? "end" : undefined,
    ...(s.color !== undefined
      ? { lineStyle: { width: 1.5, color: s.color }, itemStyle: { color: s.color } }
      : { lineStyle: { width: 1.5 } }),
    data: s.data.map(([tsNs, v]) => [tsNs / 1e6, v]),
    large: true,
    largeThreshold: 2000,
  })),
}));
</script>

<template>
  <ClientOnly>
    <!-- Live-refresh flicker fix: echarts otherwise rebuilds the
         canvas every time `chartOption` changes (which happens every
         tick of the live-mode setInterval). `notMerge: false` +
         `lazyUpdate: true` lets it diff in place. We DON'T pass
         `replaceMerge: ['series']` because that's a full series-array
         swap which forces a re-render; the default merge updates each
         series's `data` field in place. At 1s refresh the result is a
         smooth slide, not a re-render flash. -->
    <v-chart
      :option="chartOption"
      :update-options="{ notMerge: false, lazyUpdate: true }"
      :style="{ height }"
      autoresize
    />
  </ClientOnly>
</template>
