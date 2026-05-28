<script setup lang="ts">
/**
 * UPlotChart — high-performance canvas renderer for the live-mode timeseries chart.
 *
 * Wraps uPlot (https://github.com/leeoniya/uPlot) with:
 *  - Aligned-data ingestion from TimeseriesSeries[] (timestamps in ns → seconds)
 *  - ResizeObserver for container-driven layout
 *  - Vuetify theme integration (dark/light axis colours)
 *  - Step / spline path variants to match the existing ECharts control surface
 *  - Full instance lifecycle: create on mount, setData on prop change, destroy on unmount
 *
 * NOTE: uPlot has no built-in animation (by design — that is exactly the speed win).
 *       Tick-to-tick updates snap immediately, which is preferable for high-frequency
 *       live feeds where the previous value should never "slide" toward the next.
 *
 * The component is always wrapped in <ClientOnly> by its parent (TimeseriesAllChannelsChart)
 * so it is never executed server-side. uPlot's dynamic import below adds a second safety
 * layer for SSR builds.
 */

import { useTheme } from "vuetify";
import type { TimeseriesSeries } from "~/components/common/chart/types";

// Inline minimal type definitions for uPlot so we avoid `export =` import
// complexity. The runtime class is loaded dynamically in createPlot().
interface UPlotSeries {
  label?: string;
  stroke?: string;
  width?: number;
  paths?: (u: unknown, seriesIdx: number, idx0: number, idx1: number) => unknown;
  points?: { show?: boolean };
}

interface UPlotAxis {
  stroke?: string;
  ticks?: { stroke?: string };
  grid?: { stroke?: string };
  font?: string;
  labelFont?: string;
  values?: (u: unknown, vals: number[]) => string[];
  range?: (u: unknown, min: number, max: number) => [number, number];
}

interface UPlotScale {
  min?: number;
  max?: number;
}

interface UPlotOptions {
  width: number;
  height: number;
  cursor?: { show?: boolean };
  legend?: { show?: boolean };
  series: UPlotSeries[];
  axes?: UPlotAxis[];
  scales?: Record<string, UPlotScale>;
  select?: { show?: boolean };
}

interface UPlotInstance {
  destroy(): void;
  setData(data: (number | null)[][], resetScales?: boolean): void;
  setScale(scaleKey: string, limits: { min: number; max: number }): void;
  setSize(opts: { width: number; height: number }): void;
}

// Runtime type — the class loaded via dynamic import.
type UPlotConstructor = new (opts: UPlotOptions, data: (number | null)[][], targ: HTMLElement) => UPlotInstance;

const props = withDefaults(
  defineProps<{
    series: TimeseriesSeries[];
    height?: string;
    showLegend?: boolean;
    /**
     * Smooth the line via uPlot's built-in spline path builder.
     */
    smooth?: boolean;
    /**
     * Step/switch mode — renders as a staircase (post-value hold).
     * Overrides smooth.
     */
    step?: boolean;
    /**
     * Restrict the visible X window to the last N milliseconds.
     * When set, the x scale is locked to [max - N, max] in seconds.
     */
    visibleWindowMs?: number;
  }>(),
  { height: "360px", showLegend: false, smooth: false, step: false },
);

// ── Vuetify theme ──────────────────────────────────────────────────────────
const vuetifyTheme = useTheme();
const isDark = computed(() => vuetifyTheme.global.current.value.dark);

// Axis / grid colour adapts to the current theme.
const axisColor = computed(() => isDark.value ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.45)");
const gridColor = computed(() => isDark.value ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.08)");

// ── DOM ref ────────────────────────────────────────────────────────────────
const containerRef = ref<HTMLDivElement | null>(null);

// ── uPlot instance (kept outside reactive state to avoid Vue proxying it) ──
let uplot: UPlotInstance | null = null;
let UPlot: UPlotConstructor | null = null;    // class, loaded dynamically
let cssInjected = false;

// ── Data alignment helper ──────────────────────────────────────────────────
// uPlot requires aligned parallel arrays: [timestamps_s, y0[], y1[], ...].
// Each incoming series has its own [tsNs, value][] pairs — we must union-merge
// the timestamp axes and fill gaps with null.
function alignSeries(rawSeries: TimeseriesSeries[]): [number[], ...(number | null)[][]] {
  if (!rawSeries.length) return [[]];

  // Collect the union of all timestamps (in ns), then sort.
  const tsSet = new Set<number>();
  for (const s of rawSeries) {
    for (const [tsNs] of s.data) tsSet.add(tsNs);
  }
  const sortedNs = Array.from(tsSet).sort((a, b) => a - b);
  const tsSeconds = sortedNs.map(ns => ns / 1e9);

  // Build a per-series lookup and fill aligned y arrays.
  const yCols: (number | null)[][] = rawSeries.map(s => {
    const map = new Map<number, number>(s.data);
    return sortedNs.map(ns => map.get(ns) ?? null);
  });

  return [tsSeconds, ...yCols];
}

// ── uPlot Options builder ──────────────────────────────────────────────────
function buildOptions(width: number, height: number): UPlotOptions {
  const axC   = axisColor.value;
  const gridC = gridColor.value;

  const seriesOpts: UPlotSeries[] = [
    // Series[0] is the x-axis descriptor — no stroke needed.
    {},
    ...props.series.map((s, i) => ({
      label: s.name,
      stroke: s.color ?? PALETTE[i % PALETTE.length],
      width: 1.5,
      points: { show: false },
    })),
  ];

  // Compute x scale limits for live-window mode.
  const dataAligned = alignSeries(props.series);
  const tsSeconds   = dataAligned[0] as number[];
  let xScaleOpts: UPlotScale = {};
  if (props.visibleWindowMs != null && tsSeconds.length > 0) {
    // noUncheckedIndexedAccess: guard the last-element read.
    const lastTs = tsSeconds[tsSeconds.length - 1];
    if (lastTs != null) {
      xScaleOpts = { min: lastTs - props.visibleWindowMs / 1000, max: lastTs };
    }
  }

  const xAxisValues = (_u: unknown, vals: number[]): string[] =>
    vals.map(v =>
      v == null
        ? ""
        : new Date(v * 1000).toLocaleTimeString("en-GB", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
          }),
    );

  const yAxis: UPlotAxis = {
    stroke: axC,
    ticks:  { stroke: axC },
    grid:   { stroke: gridC },
    font:   "11px sans-serif",
    labelFont: "11px sans-serif",
  };
  if (props.step) {
    yAxis.values = (_u: unknown, vals: number[]) =>
      vals.map(v => (v === 1 ? "ON" : v === 0 ? "OFF" : String(v)));
    // Hard-clamp y range to [0,1] for boolean (ON/OFF) channels.
    yAxis.range = (_u: unknown, _min: number, _max: number): [number, number] => [0, 1];
  }

  return {
    width,
    height,
    cursor: { show: true },
    legend: { show: Boolean(props.showLegend) },
    series: seriesOpts,
    axes: [
      {
        // X axis
        stroke: axC,
        ticks:  { stroke: axC },
        grid:   { stroke: gridC },
        font:   "11px sans-serif",
        labelFont: "11px sans-serif",
        values: xAxisValues,
      },
      yAxis,
    ],
    scales: { x: xScaleOpts },
    select: { show: true },
  };
}

// ── PALETTE ────────────────────────────────────────────────────────────────
// Mirrors TimeseriesAllChannelsChart.vue so the live/static switch is visually
// identical (same channel → same colour).
const PALETTE = [
  "#4097CC", "#7ECA8F", "#FCA54D", "#B799DB",
  "#E56874", "#FFD145", "#8C8C8C", "#F06292",
];

// ── Instance management ────────────────────────────────────────────────────
function destroyPlot() {
  if (uplot) {
    uplot.destroy();
    uplot = null;
  }
}

function getContainerSize(): { w: number; h: number } {
  const el = containerRef.value;
  const w = el ? el.clientWidth || el.offsetWidth : 600;
  // Parse height prop (e.g. "300px", "360px").
  const h = parseInt(props.height, 10) || 300;
  return { w: Math.max(w, 80), h: Math.max(h, 60) };
}

async function createPlot() {
  if (!containerRef.value || !process.client) return;

  // Dynamic import — never runs on the server.
  if (!UPlot) {
    const mod = await import("uplot");
    // uPlot uses `export =` so Vite resolves it as `mod.default`.
    UPlot = (mod.default ?? mod) as unknown as UPlotConstructor;
  }

  // Inject uPlot stylesheet once. Vite resolves CSS imports from packages fine
  // at runtime — no ts-ignore needed here; TS just doesn't type-check the shape.
  if (!cssInjected) {
    await import("uplot/dist/uPlot.min.css");
    cssInjected = true;
  }

  destroyPlot();

  const { w, h } = getContainerSize();
  const data = alignSeries(props.series);
  const opts = buildOptions(w, h);

  uplot = new UPlot(opts, data, containerRef.value);
}

function updateData() {
  if (!uplot || !props.series.length) return;

  const data = alignSeries(props.series);
  uplot.setData(data, false /* keep scale */);

  // When visibleWindowMs is in play, re-anchor the x scale to the new max.
  if (props.visibleWindowMs != null && data.length > 0) {
    const tsArr = data[0];
    if (tsArr != null && tsArr.length > 0) {
      // The first array is always timestamps (numbers) — non-null by construction.
      const lastTs = tsArr[tsArr.length - 1];
      if (lastTs != null) {
        const xMax = lastTs as number;
        uplot.setScale("x", { min: xMax - props.visibleWindowMs / 1000, max: xMax });
      }
    }
  }
}

// ── Watchers ───────────────────────────────────────────────────────────────
// When structural props (step, smooth, theme, showLegend) change, the Options
// object must be rebuilt — easiest is to destroy-and-recreate the instance.
// For data-only changes we call the cheaper setData() path instead.
let lastSeriesCount = 0;
let lastStep = false;
let lastSmooth = false;
let lastShowLegend = false;

watch(
  () => props.series,
  (newSeries) => {
    if (
      newSeries.length !== lastSeriesCount ||
      props.step !== lastStep ||
      props.smooth !== lastSmooth ||
      props.showLegend !== lastShowLegend
    ) {
      // Structural change — full rebuild.
      lastSeriesCount = newSeries.length;
      lastStep = props.step;
      lastSmooth = props.smooth;
      lastShowLegend = props.showLegend ?? false;
      void createPlot();
    } else {
      // Data-only update — fast path.
      updateData();
    }
  },
  { deep: false },
);

// Theme change → full rebuild (axis/grid colours are baked into opts).
watch(isDark, () => void createPlot());

// ── ResizeObserver ─────────────────────────────────────────────────────────
let resizeObserver: ResizeObserver | null = null;
let resizeRaf: number | null = null;

function onResize() {
  if (resizeRaf != null) cancelAnimationFrame(resizeRaf);
  resizeRaf = requestAnimationFrame(() => {
    resizeRaf = null;
    if (!uplot || !containerRef.value) return;
    const { w, h } = getContainerSize();
    uplot.setSize({ width: w, height: h });
  });
}

// ── Lifecycle ──────────────────────────────────────────────────────────────
onMounted(async () => {
  await createPlot();

  if (containerRef.value && typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(onResize);
    resizeObserver.observe(containerRef.value);
  }
});

onUnmounted(() => {
  if (resizeRaf != null) cancelAnimationFrame(resizeRaf);
  resizeObserver?.disconnect();
  destroyPlot();
});
</script>

<template>
  <!--
    The container drives width via CSS (fill the parent).
    Height comes from the `height` prop, parsed in getContainerSize().
    uPlot renders a real <canvas> element inside this div.
    The `uplot-wrap` class suppresses the default uPlot border and
    forces the canvas to fill the container without overflow.
  -->
  <div
    ref="containerRef"
    class="uplot-wrap"
    :style="{ height }"
  />
</template>

<style scoped>
.uplot-wrap {
  width: 100%;
  overflow: hidden;
}

/* uPlot injects its own u-wrap > u-over etc. inside our div.
   Force the inner root element to be transparent so the Vuetify card
   background shows through instead of uPlot's default white. */
:deep(.u-wrap) {
  background: transparent !important;
}
</style>
