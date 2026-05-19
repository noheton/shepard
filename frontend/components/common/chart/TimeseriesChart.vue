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
  }>(),
  { height: "360px", showLegend: false, smooth: false, step: false },
);

const chartOption = computed(() => ({
  backgroundColor: "transparent",
  animation: false,
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
