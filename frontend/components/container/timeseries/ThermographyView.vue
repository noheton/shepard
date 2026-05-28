<script setup lang="ts">
/**
 * ThermographyView — thin adapter over {@link ~/components/shapes/ThermographyCanvas.vue}
 * for the VIEW_RECIPE shapes/render pipeline.
 *
 * Sibling of {@link ./Trace3DView.vue} (color-mapped 3D trace) and
 * {@link ./UrdfView.vue} (robot description). Same VIEW_RECIPE template
 * kind, same shapes/render delegation pattern, but rendered for the
 * Edevis OTvis thermography family.
 *
 * VIEW_RECIPE wiring:
 *   templateKind = "VIEW_RECIPE"
 *   renderer hint = "thermography"
 *   When ShapesRenderResponseIO.renderer equals "thermography" the
 *   shapes/render.vue page delegates here.
 *
 * Tier-1 (this commit, OTVIS-VIEW-1): renders a metadata table built
 * from the parent DataObject + FileReference annotations + a Three.js
 * canvas with a placeholder plane. The canvas plumbing is in place so
 * tier-2 (frame extraction) only has to swap the placeholder texture.
 *
 * Tier-2 (OTVIS-PARSE-2 + THERMO-CHANNELS-1): plays the IR sequence
 * frame-by-frame, side-by-side amplitude + phase, with channel-bound
 * playback driven by THERMO-CHANNELS-1.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16). Design refs:
 *   - aidocs/integrations/114-process-monitoring-parser-plugin.md §5
 *   - aidocs/integrations/113-urdf-viewer.md (sibling renderer pattern)
 *   - aidocs/agent-findings/trace3d-spike.md §1 (sibling Trace3D pattern)
 */
import { ref } from "vue";
import ThermographyCanvas from "~/components/shapes/ThermographyCanvas.vue";
import ThermographyChannelPicker from "~/components/container/timeseries/ThermographyChannelPicker.vue";
import type { AnnotationMap } from "~/utils/thermographyChannelPicker";

const thermographyCanvasRef = ref<{ captureDataUrl: () => string } | null>(null);

function captureDataUrl(): string {
  return thermographyCanvasRef.value?.captureDataUrl() ?? "";
}

defineExpose({ captureDataUrl });

withDefaults(
  defineProps<{
    /**
     * Flattened annotation map from parent DataObject + FileReference.
     * Build by merging both subjects' SemanticAnnotations so all the
     * `urn:shepard:thermography:*` + `urn:shepard:mffd:*` predicates
     * land here regardless of which entity they're anchored on.
     */
    annotations?: AnnotationMap;
    /** Human-readable label shown in the legend. */
    label?: string;
    /** Background colour for the canvas. */
    backgroundColor?: string;
  }>(),
  {
    annotations: () => ({}),
    label: "Thermography",
    backgroundColor: "#0d0d0d",
  },
);
</script>

<template>
  <div class="thermography-view">
    <v-alert
      type="info"
      variant="tonal"
      density="compact"
      class="mb-2"
      prepend-icon="mdi-information-outline"
    >
      Frame data not yet available - only metadata parsed.
      Frame extraction lands with OTVIS-PARSE-2 (tier-2); see
      <code>aidocs/integrations/114</code>.
    </v-alert>

    <ClientOnly>
      <ThermographyCanvas
        ref="thermographyCanvasRef"
        :label="label"
        :background-color="backgroundColor"
      />
      <template #fallback>
        <v-skeleton-loader type="image" height="500" />
      </template>
    </ClientOnly>

    <div class="thermography-view__legend mt-2 px-1 d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-thermometer-lines</v-icon>
      <span class="text-caption text-medium-emphasis">{{ label }}</span>
      <v-spacer />
      <span class="text-caption text-medium-emphasis">
        Drag to orbit · scroll to zoom · right-drag to pan
      </span>
    </div>

    <ThermographyChannelPicker :annotations="annotations" class="mt-3" />
  </div>
</template>

<style scoped>
.thermography-view {
  width: 100%;
}
.thermography-view__legend {
  flex-wrap: wrap;
}
</style>
