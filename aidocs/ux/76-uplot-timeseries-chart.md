---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 76 — uPlot Timeseries Chart

**Status.** Design — initial draft 2026-05-19.
**Audience.** Frontend contributors, product.
**Relates to.** `aidocs/16-dispatcher-backlog.md` (backlog item #56),
`aidocs/44-fork-vs-upstream-feature-matrix.md` (TS56 row).

---

## 1. Motivation

### 1.1 The ECharts performance problem

`TimeseriesChart.vue` wraps Apache ECharts via `vue-echarts`. This is a
competent general-purpose charting library, but it is not purpose-built
for dense time-series and it shows at scale:

**Bundle weight.** `echarts` (with tree-shaking, current imports in
`TimeseriesChart.vue`) contributes roughly 450–550 KB to the gzip
bundle. `vue-echarts` adds ~10 KB on top. Both are loaded on any page
that mounts a timeseries chart, which includes the timeseries container
page and the channel preview cards in the data-object detail view.
`uPlot` is ~45 KB minified/gzip — roughly a 10× reduction for the
charting layer.

**Render throughput at high point counts.** ECharts operates on a
JavaScript object array (`[[ts, value], ...]`) and performs full option
diff + series data reconciliation on every `setOption()`. uPlot operates
on typed `Float64Array` columns and bypasses the DOM for all rendering
(canvas only). The uPlot README documents rendering 150k points in under
150 ms on mid-range hardware; ECharts enables `large: true` mode at
2000 points (as the current component does) but the internal GC pressure
from the object-array representation accumulates at high point counts.

Concrete A/B benchmarking on shepard's actual aggregated data feeds is
scoped to TS56a (§9). The table below will be filled in during that
slice; placeholder values are included only to show the measurement
shape.

| Scenario | ECharts (measured) | uPlot (measured) | Target gain |
|---|---|---|---|
| 10k points, 1 series, cold render | TBD ms | TBD ms | ≥ 3× |
| 50k points, 4 series, cold render | TBD ms | TBD ms | ≥ 5× |
| 100k points, 8 series, cold render | TBD ms | TBD ms | ≥ 10× |
| Live mode, 1 s tick, 8 series, append | TBD ms/frame | TBD ms/frame | sub-16 ms (60 fps) |

**Live-mode frame budget.** The current `TimeseriesAllChannelsChart.vue`
live mode defaults to 5-second refresh and 100 ms at the fast end. At
100 ms the chart receives a new `series` prop every tick. Because
`chartOption` is a `computed()` that rebuilds the full ECharts option
object, `vue-echarts` calls `setOption()` on every Vue reactive update.
The `notMerge: false, lazyUpdate: true` settings mitigate full
reinitialisation but ECharts still performs a deep diff over all series
data arrays. At 50k+ aggregated points across 8 channels this diff
budget can exceed 16 ms, producing visible jank. uPlot's `setData()`
is a typed-array swap — it is designed to be called inside a
`requestAnimationFrame` callback at 60 fps with no budget concern at
normal dataset sizes.

---

## 2. uPlot API Primer

### 2.1 Data model

uPlot represents all series as **columns of `Float64Array`**, packed
into a single `AlignedData` array:

```typescript
// uPlot AlignedData: [xSeries, ySeries0, ySeries1, ...]
type AlignedData = [
  Float64Array,   // x — UNIX seconds (not milliseconds, not nanoseconds)
  ...Float64Array[], // one array per y-series, aligned to x
];
```

Key differences from the current `TimeseriesSeries[]` model:

| Aspect | Current (`TimeseriesSeries[]`) | uPlot (`AlignedData`) |
|---|---|---|
| Timestamp unit | nanoseconds (backend native) | **UNIX seconds** (float64 — fractional seconds allowed) |
| Series shape | each series owns its own `[ts_ns, value][]` array | one shared x-array; all y-series are co-indexed |
| Sparse handling | each series can have different timestamps | gaps encoded as `null` (or `NaN`) at shared x positions |
| Memory | JS object arrays (GC-heavy) | `Float64Array` (off-heap, no GC pressure) |

### 2.2 Series config and scales

```typescript
import uPlot from "uplot";

const opts: uPlot.Options = {
  width: 800,
  height: 300,
  scales: {
    x: { time: true },  // x treated as UNIX seconds
    y: { auto: true },
  },
  series: [
    {},  // index 0 is always the x-series (no visual config needed)
    {
      label: "Temperature",
      stroke: "#4097CC",
      width: 1.5,
      // step-line: set `paths` to a step builder
      // smooth: uPlot has no built-in spline; use the paths plugin (§6)
    },
  ],
  axes: [
    { /* x axis */ },
    { /* y axis */ },
  ],
};

const chart = new uPlot(opts, data, containerElement);
```

### 2.3 Updating data without full re-render

```typescript
// Streaming append — does NOT reset zoom/pan state when resetScales=false
chart.setData(newAlignedData, /* resetScales */ false);

// Sliding window: pin scale range via opts.scales.x.range
// (set at creation time; the function is called on every draw)
const opts: uPlot.Options = {
  scales: {
    x: {
      time: true,
      range: (_u, _min, _max) => {
        const latest = newAlignedData[0].at(-1) ?? 0;
        return [latest - windowSec, latest];
      },
    },
  },
  // ...
};
```

---

## 3. Component Redesign

### 3.1 New component: `UPlotChart.vue`

`UPlotChart.vue` is the **drop-in rendering primitive**, sitting at the
same level as the current `TimeseriesChart.vue`. It accepts the same
`TimeseriesSeries[]` prop interface (so callers — `TimeseriesAllChannelsChart.vue`,
`ChannelPreviewChart.vue` — need no immediate changes), converts to
`AlignedData` internally, and hands it to a bare uPlot instance.

**Props:**

```typescript
defineProps<{
  series: TimeseriesSeries[];       // same as current TimeseriesChart
  height?: string;                  // CSS height, default "360px"
  showLegend?: boolean;             // default false
  smooth?: boolean;                 // cubic-spline paths plugin; default false
  step?: boolean;                   // step-end paths; overrides smooth; default false
  animationDuration?: number;       // reserved for API compat; uPlot has no animation
  visibleWindowMs?: number;         // live-window clip in ms (matches current prop)
}>();
```

`animationDuration` is accepted but ignored: uPlot has no built-in
transition animation. The live-mode toolbar in `TimeseriesAllChannelsChart.vue`
passes this prop but the jank it was masking disappears when `setData()`
is fast, so the animation is not missed in practice.

**Events:**

```typescript
// none required for v1
// Future: emit('zoom-change', { xMin, xMax }) for persisting zoom state
```

### 3.2 Vue lifecycle integration

```vue
<script setup lang="ts">
import uPlot from "uplot";
import "uplot/dist/uPlot.min.css";
import type { TimeseriesSeries } from "./types";
import { buildAlignedData } from "./uplotAdapter";
import { buildUPlotOptions } from "./uplotOptions";

const props = withDefaults(defineProps<{ /* see §3.1 */ }>(), {
  height: "360px",
  showLegend: false,
  smooth: false,
  step: false,
  animationDuration: 0,
});

const containerRef = ref<HTMLDivElement | null>(null);
let chart: uPlot | null = null;

// Build options once on mount; rebuild only on prop changes that affect
// static config (step, smooth, showLegend). Data updates go via setData().
const currentOpts = computed(() => buildUPlotOptions(props));

onMounted(() => {
  if (!containerRef.value) return;
  const { width, height } = containerRef.value.getBoundingClientRect();
  const data = buildAlignedData(props.series, props.visibleWindowMs);
  chart = new uPlot(
    { ...currentOpts.value, width, height: parseInt(props.height) },
    data,
    containerRef.value,
  );
});

onBeforeUnmount(() => {
  chart?.destroy();
  chart = null;
});

// Data changes (live-mode ticks): use setData for zero-rebuild update.
// Scale-reset only when NOT in live mode (live mode keeps pan/zoom).
watch(
  () => props.series,
  () => {
    if (!chart) return;
    const data = buildAlignedData(props.series, props.visibleWindowMs);
    const isLive = props.visibleWindowMs != null;
    chart.setData(data, /* resetScales */ !isLive);
  },
  { deep: false }, // shallow watch — series ref replaces on each fetch tick
);

// Option-level changes (step, smooth, showLegend) require a full rebuild
// because uPlot does not support partial option mutation.
watch(currentOpts, () => {
  if (!chart || !containerRef.value) return;
  const { width } = containerRef.value.getBoundingClientRect();
  const data = buildAlignedData(props.series, props.visibleWindowMs);
  chart.destroy();
  chart = new uPlot(
    { ...currentOpts.value, width, height: parseInt(props.height) },
    data,
    containerRef.value,
  );
});

// Resize: uPlot needs explicit width/height; use ResizeObserver.
let ro: ResizeObserver | null = null;
onMounted(() => {
  if (!containerRef.value) return;
  ro = new ResizeObserver(([entry]) => {
    const { width } = entry.contentRect;
    chart?.setSize({ width, height: parseInt(props.height) });
  });
  ro.observe(containerRef.value);
});
onBeforeUnmount(() => {
  ro?.disconnect();
  ro = null;
});
</script>

<template>
  <ClientOnly>
    <div
      ref="containerRef"
      class="uplot-wrapper"
      :style="{ height }"
    />
  </ClientOnly>
</template>

<style scoped>
.uplot-wrapper {
  contain: layout style;   /* CSS containment — isolate reflow */
  width: 100%;
  overflow: hidden;
}
/* uPlot injects a <canvas> and a tooltip <div>; both must fill the wrapper */
.uplot-wrapper :deep(.uplot) {
  width: 100% !important;
}
</style>
```

---

## 4. Data Adapter

### 4.1 The alignment problem

This is the central correctness risk in the migration.

The current `TimeseriesSeries[]` model allows each series to carry its
own independent timestamp set. `TimeseriesAllChannelsChart.vue` fetches
all channels in parallel with the same `groupBy` bucket size and the same
`[start, end]` window, so in practice the server returns buckets at
identical timestamps. But this is a side effect of how the backend query
works, not a contract — if channels have different ingestion cadences or
the backend decides to skip an empty bucket, the arrays can diverge.

uPlot's `AlignedData` requires all y-arrays to be co-indexed: index `i`
of every y-array corresponds to `x[i]`. A naive zip that assumes equal
lengths will silently misalign series when the timestamps differ.

The required adapter strategy is **union merge with null-fill**:

1. Collect all unique x-values (in nanoseconds) across all series.
2. Sort them ascending.
3. Convert ns → UNIX seconds for the x `Float64Array`.
4. For each series, create a `Float64Array` of length `|union|`,
   filling positions where that series has a value, and `NaN`
   everywhere else. uPlot renders `NaN` as a gap in the line (same
   visual as ECharts's missing-point handling).

```typescript
// frontend/components/common/chart/uplotAdapter.ts

import type { TimeseriesSeries } from "./types";

const NS_TO_SEC = 1 / 1_000_000_000;

export type AlignedData = [Float64Array, ...Float64Array[]];

/**
 * Convert TimeseriesSeries[] to uPlot AlignedData.
 * Handles series with non-identical timestamp sets via union-merge + NaN fill.
 * visibleWindowMs, if set, is used ONLY for scale range (handled in opts);
 * the data array is always the full fetch span so the line doesn't start
 * mid-air at the left clip edge (same logic as current ECharts visibleWindowMs).
 */
export function buildAlignedData(
  series: TimeseriesSeries[],
): AlignedData {
  if (series.length === 0) {
    return [new Float64Array(0)];
  }

  // Step 1 — collect all unique timestamps (ns) across all series.
  const tsSet = new Set<number>();
  for (const s of series) {
    for (const [tsNs] of s.data) {
      tsSet.add(tsNs);
    }
  }

  // Step 2 — sort ascending.
  const sortedNs = Float64Array.from(tsSet).sort();
  const n = sortedNs.length;

  // Step 3 — x array in UNIX seconds.
  const xArr = new Float64Array(n);
  for (let i = 0; i < n; i++) {
    xArr[i] = sortedNs[i] * NS_TO_SEC;
  }

  // Step 4 — build a position index for O(1) lookup.
  const posIndex = new Map<number, number>();
  for (let i = 0; i < n; i++) {
    posIndex.set(sortedNs[i], i);
  }

  // Step 5 — fill y-arrays; NaN for missing positions.
  const yArrays = series.map(s => {
    const arr = new Float64Array(n).fill(NaN);
    for (const [tsNs, v] of s.data) {
      const pos = posIndex.get(tsNs);
      if (pos !== undefined) arr[pos] = v;
    }
    return arr;
  });

  return [xArr, ...yArrays];
}
```

### 4.2 Performance note

For the typical load (8 channels × 120 buckets = 960 data points),
the merge is trivial. For the 100k-point analytical view (future bulk
download), the `Set` → `Float64Array.sort()` path is still fast
(~10 ms for 100k unique timestamps). The typed arrays avoid GC pressure
that would occur with a plain-object approach.

---

## 5. Live-Mode Design

### 5.1 The streaming append pattern

uPlot's primary streaming primitive is:

```typescript
chart.setData(newData, /* resetScales */ false);
```

`resetScales: false` tells uPlot not to recompute the y-auto scale from
scratch, which prevents the vertical viewport jumping on every tick.
Pass `true` only on the initial load or when the user explicitly resets
the view.

The sliding visible window is expressed as a **scale range function**
in the options (not as data clipping, matching the current `visibleWindowMs`
approach where extra data is fetched to anchor the left edge):

```typescript
// In buildUPlotOptions():
if (visibleWindowMs != null) {
  opts.scales = {
    x: {
      time: true,
      range: (_u, _min, _max) => {
        const data = _u.data[0] as Float64Array;
        const latest = data.length > 0 ? data[data.length - 1] : 0;
        const windowSec = visibleWindowMs / 1000;
        return [latest - windowSec, latest];
      },
    },
  };
}
```

This is strictly better than the current `xAxis.min / xAxis.max` ECharts
approach because:
- The range function receives the current (fresh) data on every `setData()`.
- No separate `dataMaxMs` computed() is needed in the component.
- The scale pins to the latest data point, not to `Date.now()`, matching
  the existing intent documented in `TimeseriesChart.vue` line 64–75.

### 5.2 requestAnimationFrame gating

At 100 ms live-interval with 8 channels, up to 10 `setData()` calls per
second reach the chart. uPlot is fast enough that each call is cheap,
but coalescing them inside `requestAnimationFrame` is belt-and-suspenders
for the even-faster-than-100ms future:

```typescript
// In TimeseriesAllChannelsChart.vue (or UPlotChart.vue watch):
let rafPending = false;
let pendingData: AlignedData | null = null;

function scheduleSetData(data: AlignedData) {
  pendingData = data;
  if (rafPending) return;
  rafPending = true;
  requestAnimationFrame(() => {
    rafPending = false;
    if (pendingData && chart) {
      chart.setData(pendingData, false);
      pendingData = null;
    }
  });
}
```

The `liveFetchInFlight` guard already in `TimeseriesAllChannelsChart.vue`
(line 155–158) prevents fetch stacking at the network level. rAF gating
prevents render stacking at the draw level. Both guards are kept.

### 5.3 Boolean / step channels

uPlot does not have a built-in step-line renderer. The canonical approach
is `uPlot.paths.stepped({ align: 1 })` — uPlot ships a `stepped` paths
builder as a static class method that draws step-end lines. Wire it
per-series:

```typescript
import uPlot from "uplot";

// paths builders are static methods on the uPlot class, not named exports
const stepPaths = uPlot.paths.stepped!({ align: 1 }); // align: 1 = step-end

// In series config when props.step === true:
series: [
  {},
  {
    label: s.name,
    stroke: s.color,
    paths: stepPaths,
    // y-axis will be bounded to [0, 1] via scale config
  },
],
```

For the step boolean axis (OFF/ON labels), configure the y-axis formatter:

```typescript
axes: [
  {},  // x
  {
    values: (_u, vals) => vals.map(v => v === 1 ? "ON" : v === 0 ? "OFF" : String(v)),
    min: 0,
    max: 1,
  },
],
```

---

## 6. Feature Parity Checklist

| ECharts feature | Current implementation | uPlot equivalent | Status |
|---|---|---|---|
| Line chart | `type: 'line'` | default `series` config | Direct |
| Step-end line | `step: 'end'` | `uPlot.paths.stepped({ align: 1 })` | Direct (built-in plugin) |
| Smooth/spline | `smooth: true` | `uPlot.paths.spline()` (built-in) | Direct (built-in plugin) |
| Per-series color | `lineStyle.color` | `series[i].stroke` | Direct |
| Axis labels, font size | `axisLabel.fontSize` | `axes[].font`, `axes[].ticks` | Direct |
| Legend (scroll) | `legend: { type: 'scroll' }` | No built-in; render as custom `v-list` in parent | Workaround — see §8.1 |
| Zoom — scroll wheel | `dataZoom inside` | Built-in wheel zoom (no config needed) | Direct |
| Zoom — drag | `dataZoom inside` | Built-in drag-to-zoom via cursor plugin | Direct |
| Zoom slider (brush bar) | `dataZoom slider, height 20` | No built-in slider; drop for v1 or use custom plugin | Gap — see §8.2 |
| Tooltip (multi-series, colored swatches) | `tooltip.formatter` | Custom tooltip via `hooks.setCursor` | Workaround — see §8.3 |
| PNG export | `toolbox.saveAsImage` | `chart.canvas.toBlob()` wrapper | Write once (5 lines) |
| Visible window clip (live) | `xAxis.min / xAxis.max` via `visibleWindowMs` | `scales.x.range` function | Direct, cleaner |
| `large` mode / progressive render | `large: true, largeThreshold: 2000` | Not needed — uPlot is always fast | N/A |
| `containLabel` grid padding | `grid.containLabel` | `axes[].size` / padding props | Minor config |
| Dark mode colours | Automatic via CSS class | Manual CSS variable wiring | Risk — see §8.4 |
| Accessibility (ARIA) | Not present in ECharts canvas | Not present in uPlot canvas | Parity (both poor) |

---

## 7. Migration Plan

The migration runs in four sequential slices, each independently
deployable. At no point is there a regression window — the old
`TimeseriesChart.vue` stays live until TS56d.

### Phase 1 — TS56a: New `UPlotChart.vue` parallel to `TimeseriesChart.vue`

- Add `uplot` to `package.json` (`npm install uplot`).
- Create `frontend/components/common/chart/uplotAdapter.ts` (the
  `buildAlignedData()` function from §4).
- Create `frontend/components/common/chart/uplotOptions.ts` (the
  `buildUPlotOptions()` function that maps props → `uPlot.Options`).
- Create `frontend/components/common/chart/UPlotChart.vue` (§3.2
  lifecycle shell + ResizeObserver + dark-mode wiring).
- Write Vitest unit tests for `buildAlignedData()`:
  - equal-length series (happy path)
  - mismatched timestamps (union merge)
  - empty series array
  - single-point series
- Write a visual benchmark fixture (plain HTML page outside Nuxt, no
  framework overhead) that plots 10k / 50k / 100k points and records
  `performance.now()` before and after `new uPlot()`. This is the TS56a
  benchmark harness referenced in §1.1.
- **No production page is changed in this slice.**

### Phase 2 — TS56b: Swap `TimeseriesAllChannelsChart.vue`

- Replace `<TimeseriesChart ... />` with `<UPlotChart ... />` in
  `TimeseriesAllChannelsChart.vue`.
- Wire the live-mode rAF gate (§5.2).
- Replace the legend: uPlot does not render a legend; add a `v-list`
  (or flex chip row) above the chart showing series name + color swatch.
  This is a UX improvement over the current scrollable ECharts legend.
- Validate: live mode at 100 ms, 5 s, 30 s refresh intervals; step-mode
  boolean channels; curated channel selection.
- `TimeseriesChart.vue` is still used by `ChannelPreviewChart.vue`.

### Phase 3 — TS56c: Swap `ChannelPreviewChart.vue`

- Replace `<TimeseriesChart ... />` with `<UPlotChart ... />` in
  `ChannelPreviewChart.vue`.
- Preview charts are static (no live mode, no legend, no toolbox).
  Wire a minimal `buildUPlotOptions()` config: no toolbar, no legend,
  smaller axis fonts, no zoom slider.
- Validate: channel preview cards render correctly for single-series data.

### Phase 4 — TS56d: Remove ECharts

- Delete `frontend/components/common/chart/TimeseriesChart.vue`.
- Remove `echarts` and `vue-echarts` from `package.json`.
- Run `npm install` and verify no remaining imports of `echarts` or
  `vue-echarts` in the frontend tree (`grep -r 'vue-echarts\|from "echarts"'`).
- Run `nuxt build` and confirm the gzip bundle size reduction (target:
  ≥ 400 KB reduction).

---

## 8. Risks and Mitigations

### 8.1 Legend

uPlot renders no built-in legend. The current ECharts legend is a
scrollable legend at the bottom of the chart (enabled via `showLegend`
prop). For `TimeseriesAllChannelsChart.vue` this is acceptable: the
curated-channel toolbar already renders channel names as chips. A
`v-list` strip above the chart (color swatch + series label) is a
simpler, more Vuetify-native replacement.

For `ChannelPreviewChart.vue`, `showLegend` is not set (defaults false)
so no legend is needed.

### 8.2 DataZoom slider (bottom brush bar)

The ECharts `dataZoom slider` gives a minimap-style brush bar at the
bottom of the chart for navigating dense datasets. uPlot has no
equivalent out of the box. Options:

- **Drop for v1**: scroll-wheel zoom and drag-to-zoom cover the primary
  use case. The slider is a power-user feature. Re-evaluate after TS56b
  ships and user feedback is collected.
- **Custom plugin**: implement a `<canvas>`-based minimap as a separate
  Vue component that calls `chart.setScale('x', ...)` on brush drag.
  This is non-trivial (~200 lines) and deferred.

Recommended: drop for v1, note the gap in `aidocs/44`.

### 8.3 Tooltip

uPlot reports cursor position via `hooks.setCursor`. The tooltip must be
rendered as a custom DOM element:

```typescript
// In buildUPlotOptions():
hooks: {
  setCursor: [
    (u: uPlot) => {
      const { idx } = u.cursor;
      if (idx == null) { hideTooltip(); return; }
      const tsMs = (u.data[0][idx] ?? 0) * 1000;
      const rows = seriesDefs.map((s, i) => ({
        name: s.name,
        color: s.color ?? defaultColor(i),
        value: u.data[i + 1][idx],
      }));
      showTooltip(tsMs, rows);
    },
  ],
},
```

`showTooltip` positions a `<div>` (outside the uPlot canvas element)
using `u.valToPos(ts, 'x')` for x and `u.cursor.left` for screen offset.
This is ~50 lines and achieves the same visual result as the current
ECharts tooltip formatter.

### 8.4 Dark mode / Vuetify theming

uPlot uses hardcoded CSS class names (`u-hz`, `u-legend`, etc.) and
applies its own stylesheet (`uPlot.min.css`). It does not read Vuetify
CSS variables automatically.

Required wiring:

1. Override uPlot's CSS variables in a global SCSS scope or per-component
   `<style>`:
   ```scss
   // Light mode defaults (uPlot built-in)
   // Dark mode overrides:
   .v-theme--dark .uplot-wrapper :deep(.u-hz) {
     --u-bg: transparent;
   }
   ```
2. Watch `useTheme().current` and call `chart.redraw()` (or destroy +
   recreate) when the theme flips. uPlot does not observe CSS variable
   changes after mount.
3. Axis label colours and grid line colours must be set in
   `buildUPlotOptions()` by reading the active Vuetify theme's
   `current.value.colors`. This keeps the chart palette in sync with
   the Vuetify theme tokens rather than hardcoding `#888`.

Implementation note: theme-dependent colours should be read inside
`onMounted` (not at module load time) because SSR has no `document`
and therefore no computed CSS variable resolution.

### 8.5 Accessibility

Neither ECharts nor uPlot provides built-in ARIA for canvas-based charts.
The current chart has no `role`, `aria-label`, or accessible data table.
This is a pre-existing gap — the uPlot migration should not regress it
and ideally adds at minimum:

```html
<div
  ref="containerRef"
  class="uplot-wrapper"
  role="img"
  :aria-label="`Time series chart with ${series.length} channels`"
/>
```

A full accessible data table (`<details><summary>Data table</summary>
<table>...</table></details>`) is deferred to a separate accessibility
task.

---

## 9. Task Breakdown

### TS56a — `UPlotChart.vue` + adapter + benchmark (shippable, no production change)

**Deliverables:**
- `frontend/components/common/chart/uplotAdapter.ts`
- `frontend/components/common/chart/uplotOptions.ts`
- `frontend/components/common/chart/UPlotChart.vue`
- `frontend/components/common/chart/__tests__/uplotAdapter.test.ts`
  (Vitest unit tests for `buildAlignedData()`)
- `frontend/scripts/uplot-bench.html` (standalone benchmark harness)
- Fill in the §1.1 benchmark table with real numbers

**Acceptance criteria:**
- `vitest run` passes all `uplotAdapter` tests including the
  mismatched-timestamp union-merge case.
- `UPlotChart.vue` renders correctly in Storybook / manual smoke test
  with the same `series` prop as `TimeseriesChart.vue`.
- Benchmark numbers recorded for 10k / 50k / 100k points.

### TS56b — Swap `TimeseriesAllChannelsChart.vue` (first production change)

**Deliverables:**
- `TimeseriesAllChannelsChart.vue`: `<TimeseriesChart>` → `<UPlotChart>`
- rAF gate for live-mode `setData()` (§5.2)
- Inline legend strip (`v-list` row, color swatch + label)
- Dark-mode CSS wiring (§8.4)
- Custom tooltip (§8.3)

**Acceptance criteria:**
- Live mode at 100 ms and 5 s intervals shows smooth rendering.
- Step-mode boolean channels render correctly with ON/OFF axis.
- Curated channel selection renders in the correct order.
- Deploy + smoke test on the public hostname before closing.

### TS56c — Swap `ChannelPreviewChart.vue`

**Deliverables:**
- `ChannelPreviewChart.vue`: `<TimeseriesChart>` → `<UPlotChart>`
  (minimal config: no legend, no zoom, no toolbar, height 200px)

**Acceptance criteria:**
- Channel preview cards in the data-object detail view render correctly
  for single-series data.
- No `vue-echarts` import remains in `ChannelPreviewChart.vue`.

### TS56d — Remove ECharts dependency

**Deliverables:**
- Delete `frontend/components/common/chart/TimeseriesChart.vue`
- Remove `echarts` and `vue-echarts` from `package.json`
- CI build passes; gzip bundle reduction ≥ 400 KB confirmed in CI artifact

**Acceptance criteria:**
- `grep -r 'vue-echarts\|from "echarts"' frontend/` returns no matches.
- `nuxt build` succeeds.
- Confirmed bundle size delta in the PR description.
- `aidocs/44` TS56 row flipped to `✓ shipped`.

---

## 10. Open Questions

| # | Question | Current thinking |
|---|----------|-----------------|
| OQ1 | Should `animationDuration` be silently ignored or should the prop be removed? | Keep prop for API compat through TS56d, ignore value. Remove in TS56d when `TimeseriesChart.vue` is deleted. |
| OQ2 | Is the ECharts `toolbox.saveAsImage` feature actively used? | Unknown. Implement `chart.canvas.toBlob()` wrapper as a toolbar button in TS56b regardless — it is 5 lines and prevents a regression report. |
| OQ3 | Drop the dataZoom slider entirely or implement a custom one? | Drop for v1; revisit based on user feedback after TS56b ships. |
| OQ4 | uPlot version pin: currently 1.6.x is the latest stable. Pin to `^1.6` or exact? | Pin exact in `package.json` (`"uplot": "1.6.31"`) to avoid surprise API breaks. uPlot's major-version changes have historically been breaking. |
| OQ5 | Should `chart.js` + `vue-chartjs` also be removed from `package.json`? They appear in the deps but no current chart component uses them. | Audit in TS56d: if no component imports them, remove both (`chart.js`: 4.5.1, `vue-chartjs`: 5.3.3, `@types/chart.js`: 4.0.1). |
