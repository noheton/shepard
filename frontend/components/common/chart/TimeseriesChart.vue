<script setup lang="ts">
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import {
  BrushComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  ToolboxComponent,
  TooltipComponent,
} from "echarts/components";
import type { TimeseriesSeries } from "./types";
import { formatDualTime } from "~/utils/wallClockTime";

if (process.client) {
  use([
    CanvasRenderer,
    LineChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    DataZoomComponent,
    ToolboxComponent,
    BrushComponent,
    MarkAreaComponent,
    MarkLineComponent,
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
    /**
     * Hard lower bound for the X axis in milliseconds (epoch ms).
     * When set together with xMax, the chart is locked to exactly that time
     * range — the user can zoom in within the range but cannot scroll beyond
     * it. Intended for reference views where the chart must show only the
     * referenced data window. Takes precedence over visibleWindowMs.
     */
    xMin?: number;
    /**
     * Hard upper bound for the X axis in milliseconds (epoch ms).
     * See xMin for details.
     */
    xMax?: number;
    /**
     * Enable the brush (range-select) tool for annotation.
     * When true, a horizontal-select brush button appears in the toolbox.
     * The parent receives `brush:end` emits with the selected ns range.
     */
    brushEnabled?: boolean;
    /**
     * Existing temporal annotations to render as background overlays.
     * Each entry covers [startNs, endNs] (endNs null = point annotation).
     */
    annotations?: Array<{ startNs: number; endNs: number | null; label?: string; color?: string }>;
    /**
     * TM1b — wall-clock offset in milliseconds (= wallClockOffset / 1e6).
     * Only set when timeReference = EXPERIMENT_RELATIVE and wallClockOffset is
     * non-null. When present, the tooltip shows both the experiment-relative
     * "t+Xs" label AND the absolute UTC timestamp alongside each data point.
     * Undefined (default) = WALL_CLOCK mode or no offset; tooltip shows UTC as usual.
     */
    wallClockOffsetMs?: number;
  }>(),
  { height: "360px", showLegend: false, smooth: false, step: false, animationDuration: 0 },
);

const emit = defineEmits<{
  "brush:end": [{ startNs: number; endNs: number | null }];
}>();

const chartRef = ref<InstanceType<typeof VChart> | null>(null);

function onBrushEnd(event: any) {
  const area = event?.areas?.[0];
  if (!area?.coordRange) return;
  const [a, b] = area.coordRange as [number, number];
  const startMs = Math.min(a, b);
  const endMs = Math.max(a, b);
  const startNs = startMs * 1e6;
  // Treat selections < 1 ms as a point annotation (endNs = null)
  const endNs = (endMs - startMs) < 1 ? null : endMs * 1e6;
  emit("brush:end", { startNs, endNs });
}

function restoreZoom() {
  if (!chartRef.value) return;
  (chartRef.value as any).dispatchAction({ type: "dataZoom", start: 0, end: 100 });
  (chartRef.value as any).dispatchAction({ type: "brush", areas: [] });
}

defineExpose({ restoreZoom });

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
      let header: string;
      if (props.wallClockOffsetMs != null) {
        // TM1b — EXPERIMENT_RELATIVE mode: show both relative and absolute UTC.
        const { relative, absolute } = formatDualTime(tsMs, props.wallClockOffsetMs);
        header = `${relative} <span style="opacity:0.7;font-size:0.9em">(${absolute})</span>`;
      } else {
        const date = new Date(tsMs);
        header = date.toLocaleString("en-GB", {
          year: "2-digit",
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        });
      }
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
      ...(props.brushEnabled ? {
        brush: { type: ["lineX", "clear"] as string[], title: { lineX: "Select range", clear: "Clear" } },
      } : {}),
      saveAsImage: { title: "Save PNG", name: "timeseries" },
    },
  },
  ...(props.brushEnabled ? { brush: { xAxisIndex: 0, brushMode: "single" } } : {}),
  xAxis: {
    type: "time",
    axisLabel: {
      fontSize: 11,
      // TM1b — EXPERIMENT_RELATIVE mode: show "t+Xs" relative labels on the
      // axis ticks instead of UTC dates. The tooltip still shows both.
      ...(props.wallClockOffsetMs != null
        ? {
            formatter: (ms: number) => {
              const sec = ms / 1000;
              const sign = sec < 0 ? "−" : "+";
              return `t${sign}${Math.abs(sec).toFixed(1)}s`;
            },
          }
        : {}),
    },
    // xMin/xMax (reference range lock) take precedence over visibleWindowMs (live mode).
    // When xMin/xMax are provided, the axis is hard-clamped to the reference
    // time window. The inside dataZoom is omitted (see below) so the user
    // cannot scroll/pan beyond those bounds.
    ...(props.xMin != null && props.xMax != null
      ? { min: props.xMin, max: props.xMax }
      : props.visibleWindowMs != null && dataMaxMs.value > 0
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
    // Omit the inside (scroll/pinch) zoom when the range is locked via
    // xMin/xMax — the axis hard-clamps to those bounds but inside dataZoom
    // can still drift the view window into empty space on the sides. The
    // slider below is sufficient for within-range navigation.
    ...(props.xMin == null || props.xMax == null
      ? [{ type: "inside" as const, xAxisIndex: 0, filterMode: "none" as const }]
      : []),
    {
      type: "slider",
      xAxisIndex: 0,
      filterMode: "none",
      height: 20,
      bottom: props.showLegend ? 52 : 8,
      borderColor: "transparent",
    },
  ],
  series: [
    ...props.series.map(s => ({
      name: s.name,
      type: "line" as const,
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
    // Invisible sentinel series that carries markArea + markLine for annotation overlays.
    // Uses a dedicated series rather than attaching marks to data series so annotations
    // render even when there is no data (empty chart use case).
    ...(props.annotations?.length
      ? [{
          name: "__annotations__",
          type: "line" as const,
          silent: true,
          symbol: "none",
          lineStyle: { opacity: 0 },
          data: [],
          markArea: {
            silent: true,
            data: props.annotations
              .filter(a => a.endNs != null)
              .map(a => [
                {
                  xAxis: a.startNs / 1e6,
                  itemStyle: { color: a.color ?? "rgba(251, 140, 0, 0.12)" },
                  label: { show: !!a.label, formatter: a.label ?? "", position: "insideTopLeft", fontSize: 10 },
                },
                { xAxis: a.endNs! / 1e6 },
              ]),
          },
          markLine: {
            silent: true,
            symbol: ["none", "none"],
            data: props.annotations
              .filter(a => a.endNs == null)
              .map(a => ({
                xAxis: a.startNs / 1e6,
                lineStyle: { color: a.color ?? "#FB8C00", width: 1.5, type: "dashed" },
                label: { show: !!a.label, formatter: a.label ?? "", position: "start", fontSize: 10 },
              })),
          },
        }]
      : []),
  ],
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
      ref="chartRef"
      :option="chartOption"
      :update-options="{ notMerge: false, lazyUpdate: true }"
      :style="{ height }"
      autoresize
      @brushEnd="onBrushEnd"
    />
  </ClientOnly>
</template>
