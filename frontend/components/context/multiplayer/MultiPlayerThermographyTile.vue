<script setup lang="ts">
/**
 * MFFD-MULTIPLAYER-1 — Thermography tile (deferred-sync placeholder).
 *
 * <p>The AAC2 thermography pane renders a <i>composite max-projection</i>
 * plate heatmap (max temperature observed at each pixel across the entire
 * bundle). It has no time axis at all — there is nothing for the shared
 * cursor to drive on this representation.
 *
 * <p>For genuine sync the source component needs a <b>frame-strip view</b>
 * that exposes a per-frame timestamp; that is filed as
 * {@code MFFD-NDT-QUALITY-FRAMESTRIP-1}. Once it lands, this tile mounts
 * the frame-strip and writes / reads the cursor like the other tiles.
 *
 * <p>For v1 we mount a read-only quality summary so the multi-player has
 * a thermography slot, and document the deferred sync inline. The tile
 * does <i>not</i> register a range — it is informational, not constraining.
 *
 * <p>Tracking: {@code MFFD-MULTIPLAYER-THERMO-1}.
 */
import { onMounted, ref } from "vue";
import {
  qualityChipColor,
  qualityBand,
  formatTemp,
} from "~/utils/thermographyHeatmap";

const props = defineProps<{
  dataObjectAppId: string;
  imageBundleAppId: string;
}>();

interface PlateHeatmapSummary {
  frameCount?: number | null;
  minTemp?: number | null;
  maxTemp?: number | null;
  qualityScore?: number | null;
}

const summary = ref<PlateHeatmapSummary | null>(null);
const isLoading = ref(true);
const errorMessage = ref<string | null>(null);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchSummary(): Promise<void> {
  try {
    const url = `${v2BaseUrl()}/v2/thermography/${encodeURIComponent(
      props.imageBundleAppId,
    )}/plate-heatmap`;
    const res = await $fetch<PlateHeatmapSummary>(url, {
      credentials: "include",
    }).catch(err => {
      if (err?.response?.status === 404) return null;
      throw err;
    });
    summary.value = res ?? null;
  } catch (err) {
    errorMessage.value = (err as Error).message ?? "Network error";
  } finally {
    isLoading.value = false;
  }
}

const qualityChip = () => {
  const q = summary.value?.qualityScore ?? null;
  return {
    color: qualityChipColor(qualityBand(q)),
    label: q == null ? "Not analyzed" : `Quality ${q.toFixed(2)}`,
  };
};

onMounted(fetchSummary);
</script>

<template>
  <div class="thermo-tile">
    <div class="tile-label">
      <span class="title">Thermography</span>
      <v-chip
        v-if="!isLoading && !errorMessage"
        :color="qualityChip().color"
        size="x-small"
        variant="elevated"
      >
        {{ qualityChip().label }}
      </v-chip>
    </div>
    <div v-if="isLoading" class="status">Loading summary...</div>
    <v-alert
      v-else-if="errorMessage"
      type="warning"
      density="compact"
      variant="tonal"
    >
      {{ errorMessage }}
    </v-alert>
    <template v-else-if="summary">
      <div class="summary">
        <div><strong>Frames:</strong> {{ summary.frameCount ?? "—" }}</div>
        <div>
          <strong>Min/Max:</strong>
          {{ summary.minTemp != null ? formatTemp(summary.minTemp) : "—" }} /
          {{ summary.maxTemp != null ? formatTemp(summary.maxTemp) : "—" }}
        </div>
      </div>
      <v-alert
        type="info"
        variant="tonal"
        density="compact"
        class="defer-note"
      >
        Frame-by-frame scrubbing requires a frame-strip view
        (<code>MFFD-NDT-QUALITY-FRAMESTRIP-1</code>); current pane shows the
        composite max-projection only.
      </v-alert>
    </template>
    <div v-else class="status">No analysis cached.</div>
  </div>
</template>

<style scoped>
.thermo-tile {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 220px;
}
.tile-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  padding-bottom: 4px;
}
.summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 4px;
  margin-bottom: 8px;
}
.defer-note {
  font-size: 11px;
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
