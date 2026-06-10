<script setup lang="ts">
/**
 * MFFD-NDT-QUALITY-1 — thermography quality + plate-heatmap pane.
 *
 * <p>Surfaces on the DataObject detail page when the DO carries at least one
 * FileBundleReference whose name suggests thermography (`*.tif`-bearing).
 * The parent decides whether to mount this pane — passing the
 * {@code imageBundleAppId} props removes the discovery responsibility
 * from this component so the panel stays cheap to render.
 *
 * <p>The composite plate-heatmap is fetched via the generic
 * {@code POST /v2/shapes/render} (file-rooted, ThermographyHeatmapShape,
 * Accept: application/json — V2CONV-A7-THERMO). A 422 ("render.not-analyzed")
 * means the bundle has not been analyzed yet.
 *
 * <p>Analysis is no longer a user-triggered REST call — it runs at upload time
 * via the thermography {@code FileFormatPlugin.parse} side-effect
 * (V2CONV-A7-THERMO). The former "Re-analyze" button is therefore removed; the
 * pane is read-only and surfaces the cached heatmap.
 */
import { ref, computed, onMounted, watch } from "vue";
import {
  renderHeatmapPixels,
  cellAtCanvasPosition,
  formatTemp,
  buildPlateHeatmapBody,
  type PlateHeatmap,
} from "~/utils/thermographyHeatmap";

const props = defineProps<{
  /** appId of the parent DataObject — used to look up the DO-level quality score. */
  dataObjectAppId: string;
  /** appId of the FileBundleReference carrying the TIFFs. */
  imageBundleAppId: string;
}>();

const emit = defineEmits<{
  (e: "numberOfEntriesChanged", value: number): void;
}>();

const heatmap = ref<PlateHeatmap | null>(null);
const isLoading = ref(false);
const errorMessage = ref<string | null>(null);
const tooltipText = ref<string | null>(null);
const tooltipX = ref(0);
const tooltipY = ref(0);

const canvasRef = ref<HTMLCanvasElement | null>(null);
const CELL_PX = 8;

const chipColor = computed(() => (heatmap.value ? "success" : "grey"));
const chipText = computed(() => (heatmap.value ? "Analyzed" : "Not analyzed"));

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchHeatmap() {
  errorMessage.value = null;
  isLoading.value = true;
  try {
    // V2CONV-A7-THERMO — plate-heatmap via POST /v2/shapes/render (file-rooted,
    // Accept: application/json). Replaces GET /v2/thermography/{appId}/plate-heatmap.
    // A 422 ("render.not-analyzed") means the bundle was never analyzed — degrade.
    const url = `${v2BaseUrl()}/v2/shapes/render`;
    const res = await $fetch<PlateHeatmap>(url, {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json" },
      body: buildPlateHeatmapBody(props.imageBundleAppId),
    }).catch(err => {
      if (err?.response?.status === 422 || err?.response?.status === 404) return null;
      throw err;
    });
    heatmap.value = res ?? null;
    emit("numberOfEntriesChanged", heatmap.value ? 1 : 0);
  } catch (err) {
    errorMessage.value = `Failed to load heatmap — ${String((err as Error).message)}`;
  } finally {
    isLoading.value = false;
  }
}

function drawCanvas() {
  const canvas = canvasRef.value;
  const hm = heatmap.value;
  if (!canvas || !hm) return;
  const { pixels, canvasWidth, canvasHeight } = renderHeatmapPixels(hm, CELL_PX);
  canvas.width = canvasWidth;
  canvas.height = canvasHeight;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const img = ctx.createImageData(canvasWidth, canvasHeight);
  img.data.set(pixels);
  ctx.putImageData(img, 0, 0);
}

function onCanvasMove(ev: MouseEvent) {
  const canvas = canvasRef.value;
  const hm = heatmap.value;
  if (!canvas || !hm) return;
  const rect = canvas.getBoundingClientRect();
  // Map page-to-canvas in canvas-internal coordinates (account for CSS scaling).
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;
  const cx = (ev.clientX - rect.left) * scaleX;
  const cy = (ev.clientY - rect.top) * scaleY;
  const hit = cellAtCanvasPosition(hm, cx, cy, CELL_PX);
  if (hit) {
    tooltipText.value = `(${hit.cx}, ${hit.cy}) → ${formatTemp(hit.temp)}`;
    tooltipX.value = ev.clientX - rect.left + 12;
    tooltipY.value = ev.clientY - rect.top + 12;
  } else {
    tooltipText.value = null;
  }
}

function onCanvasLeave() {
  tooltipText.value = null;
}

onMounted(() => {
  fetchHeatmap();
});

watch(heatmap, () => drawCanvas(), { flush: "post" });
</script>

<template>
  <div class="d-flex flex-column ga-4 pa-4 thermography-pane">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h5 class="text-h5">Thermography NDT</h5>
      <div class="d-flex align-center ga-2">
        <v-chip
          :color="chipColor"
          variant="elevated"
          density="comfortable"
          data-test="quality-chip"
        >
          {{ chipText }}
        </v-chip>
      </div>
    </div>

    <v-alert
      v-if="errorMessage"
      type="error"
      variant="tonal"
      :text="errorMessage"
      closable
      @click:close="errorMessage = null"
    />

    <centered-loading-spinner v-if="isLoading" />

    <template v-else-if="heatmap">
      <div class="d-flex flex-wrap ga-3 text-body-2">
        <span><strong>Frames:</strong> {{ heatmap.frameCount }}</span>
        <span><strong>Min:</strong> {{ formatTemp(heatmap.minTemp) }}</span>
        <span><strong>Max:</strong> {{ formatTemp(heatmap.maxTemp) }}</span>
        <span>
          <strong>Threshold:</strong> {{ formatTemp(heatmap.thresholdTemp) }}
        </span>
      </div>

      <div class="thermography-canvas-frame" data-test="heatmap-frame">
        <canvas
          ref="canvasRef"
          class="thermography-canvas"
          data-test="heatmap-canvas"
          @mousemove="onCanvasMove"
          @mouseleave="onCanvasLeave"
        />
        <div
          v-if="tooltipText"
          class="thermography-tooltip"
          :style="{ left: `${tooltipX}px`, top: `${tooltipY}px` }"
          data-test="heatmap-tooltip"
        >
          {{ tooltipText }}
        </div>
      </div>

      <p class="text-caption text-medium-emphasis">
        Plate heatmap shows the maximum temperature observed at each pixel
        location across the entire bundle. Hot-spots stand out in bright
        yellow; cold zones in deep purple. Hover to read exact values.
      </p>
    </template>

    <v-alert
      v-else
      type="info"
      variant="tonal"
      data-test="not-analyzed-alert"
    >
      No analysis cached for this thermography bundle. The composite plate
      heatmap is computed automatically when the thermography files are
      uploaded; re-upload the bundle to (re-)generate it.
    </v-alert>
  </div>
</template>

<style scoped>
.thermography-canvas-frame {
  position: relative;
  display: inline-block;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  background: #111;
  padding: 4px;
}
.thermography-canvas {
  display: block;
  image-rendering: pixelated;
  max-width: 100%;
  height: auto;
}
.thermography-tooltip {
  position: absolute;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  pointer-events: none;
  white-space: nowrap;
}
</style>
