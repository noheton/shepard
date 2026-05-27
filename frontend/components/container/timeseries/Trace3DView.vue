<script setup lang="ts">
/**
 * Trace3DView — color-mapped 3D path from four parallel timeseries arrays.
 *
 * Accepts flat parallel arrays (X/Y/Z position + scalar value) and renders
 * them as an interactive, color-mapped 3D path via Three.js.
 *
 * This component is a thin adapter over {@link ~/components/shapes/Trace3DCanvas.vue},
 * which owns the Three.js scene, OrbitControls, BufferGeometry, and ResizeObserver.
 * This wrapper's sole responsibility is:
 *   1. The flat-array → TracePoint[] conversion.
 *   2. The colorScheme → ColormapName mapping.
 *   3. A canvas-based color-bar legend (value range + gradient swatch).
 *
 * VIEW_RECIPE wiring:
 *   This component is the intended in-tree renderer for a Trace3D VIEW_RECIPE
 *   template (templateKind = "VIEW_RECIPE", renderer hint = "trace-3d").
 *   Backend field {@code ShapesRenderResponseIO.renderer} carries the hint;
 *   when it equals "trace-3d" the shapes/render.vue page delegates here.
 *   Design refs: aidocs/agent-findings/trace3d-spike.md §1,
 *                aidocs/semantics/98-shapes-views-and-process-model.md §2.
 *   Task: #142.
 */
import { computed, ref, onMounted, watch } from "vue";
import type { ColormapName } from "~/utils/colormap";
import { colormapRgb } from "~/utils/colormap";
import Trace3DCanvas from "~/components/shapes/Trace3DCanvas.vue";

// ── prop types ────────────────────────────────────────────────────────────────

export type Trace3DColorScheme = "heat" | "cool" | "viridis";

interface TracePoint {
  x: number;
  y: number;
  z: number;
  value: number;
  t: number;
}

const props = withDefaults(
  defineProps<{
    /** X position values — must be same length as yData/zData/valueData. */
    xData: number[];
    /** Y position values. */
    yData: number[];
    /** Z position values. */
    zData: number[];
    /**
     * Scalar driving the color gradient (e.g. temperature, force).
     * All-NaN or empty array → position-index coloring.
     */
    valueData: number[];
    /** Human-readable label for the color axis shown in the legend. */
    valueLabel?: string;
    /**
     * Color gradient scheme.
     *   "heat"    — blue (low) → cyan → yellow → red (high)  [default]
     *   "cool"    — cyan → blue-white → magenta
     *   "viridis" — dark purple → teal → yellow
     */
    colorScheme?: Trace3DColorScheme;
    /** Normalized 0–1 brush range — highlights a sub-range of the trace. */
    brushRange?: { from: number; to: number };
  }>(),
  {
    valueLabel: "Value",
    colorScheme: "heat",
    brushRange: undefined,
  },
);

// ── colormap bridge ───────────────────────────────────────────────────────────

/** Maps Trace3DView's colorScheme vocabulary to Trace3DCanvas's ColormapName. */
const colormapName = computed<ColormapName>(() => {
  switch (props.colorScheme) {
    case "cool":    return "cool";
    case "viridis": return "viridis";
    case "heat":
    default:        return "heat";
  }
});

// ── flat arrays → TracePoint[] ────────────────────────────────────────────────

const tracePoints = computed<TracePoint[]>(() => {
  const n = props.xData.length;
  if (n === 0) return [];
  return Array.from({ length: n }, (_, i) => ({
    x:     props.xData[i]     ?? 0,
    y:     props.yData[i]     ?? 0,
    z:     props.zData[i]     ?? 0,
    value: props.valueData[i] ?? NaN,
    // No absolute timestamp available in this flat-array API.
    // t is used only for the brush display in Trace3DCanvas, which is not
    // exposed from this wrapper (brush is a shapes/render.vue concern).
    t:     i,
  }));
});

// ── value stats for legend labels ─────────────────────────────────────────────

const valueStats = computed<{ min: number; max: number } | null>(() => {
  const vals = props.valueData.filter(v => isFinite(v));
  if (vals.length === 0) return null;
  return { min: Math.min(...vals), max: Math.max(...vals) };
});

// ── color-bar canvas ──────────────────────────────────────────────────────────

const legendCanvasRef = ref<HTMLCanvasElement | null>(null);

function drawLegendCanvas(canvas: HTMLCanvasElement, name: ColormapName) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const { width: w, height: h } = canvas;
  const gradient = ctx.createLinearGradient(0, 0, w, 0);
  // Sample 9 stops for a smooth swatch.
  for (let i = 0; i <= 8; i++) {
    const stop = i / 8;
    const [r, g, b] = colormapRgb(stop, name);
    const toHex = (v: number): string => Math.round(v * 255).toString(16).padStart(2, "0");
    gradient.addColorStop(stop, `#${toHex(r)}${toHex(g)}${toHex(b)}`);
  }
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, w, h);
}

onMounted(() => {
  if (legendCanvasRef.value) drawLegendCanvas(legendCanvasRef.value, colormapName.value);
});

watch(colormapName, () => {
  if (legendCanvasRef.value) drawLegendCanvas(legendCanvasRef.value, colormapName.value);
});
</script>

<template>
  <div class="trace3d-view">
    <!-- 3D canvas — Three.js requires client-only (no SSR WebGL) -->
    <ClientOnly>
      <Trace3DCanvas
        :points="tracePoints"
        :colormap="colormapName"
        :label="valueLabel"
        :brush-range="props.brushRange"
      />
      <template #fallback>
        <v-skeleton-loader type="image" height="500" />
      </template>
    </ClientOnly>

    <!-- Canvas-based color-bar legend with value range labels -->
    <div class="trace3d-view__legend mt-2 px-1 d-flex align-center ga-2">
      <span class="text-caption text-medium-emphasis">
        {{ valueStats !== null ? valueStats.min.toFixed(2) : "min" }}
      </span>
      <canvas
        ref="legendCanvasRef"
        class="trace3d-view__colorbar"
        width="200"
        height="14"
        :title="`Color axis: ${valueLabel}`"
        :aria-label="`Color gradient for ${valueLabel}`"
      />
      <span class="text-caption text-medium-emphasis">
        {{ valueStats !== null ? valueStats.max.toFixed(2) : "max" }}
      </span>
      <span class="text-caption text-medium-emphasis ml-1">
        {{ valueLabel }}
      </span>
    </div>

    <!-- Empty-state guard -->
    <v-alert
      v-if="tracePoints.length === 0"
      type="info"
      variant="tonal"
      density="compact"
      class="mt-2"
    >
      No data — xData array is empty.
    </v-alert>
  </div>
</template>

<style scoped>
.trace3d-view {
  width: 100%;
}
.trace3d-view__legend {
  flex-wrap: wrap;
}
.trace3d-view__colorbar {
  border-radius: 3px;
  flex: 1 1 auto;
  max-width: 220px;
  display: block;
}
</style>
