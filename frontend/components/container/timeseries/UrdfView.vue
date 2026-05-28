<script setup lang="ts">
/**
 * UrdfView — thin adapter over {@link ~/components/shapes/UrdfCanvas.vue}
 * for the VIEW_RECIPE shapes/render pipeline.
 *
 * This is the URDF sibling of {@link ~/components/container/timeseries/Trace3DView.vue}.
 * Trace3D and URDF share the same VIEW_RECIPE template kind and the same
 * shapes/render delegation pattern, but are SEPARATELY SELECTABLE renderers —
 * a user picks one or the other from ViewRecipeBuilderDialog. Future work may
 * compose them (URDF cell with Trace3D path overlaid), but that is OUT OF SCOPE.
 *
 * VIEW_RECIPE wiring:
 *   templateKind = "VIEW_RECIPE"
 *   renderer hint = "urdf"
 *   When ShapesRenderResponseIO.renderer equals "urdf" the shapes/render.vue
 *   page delegates here (and to UrdfAnimator when joint-bound channels are
 *   present in the response).
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16). Design refs:
 *   - aidocs/integrations/113-urdf-viewer.md
 *   - aidocs/data/85-coordinate-frame-tree.md
 *   - aidocs/agent-findings/trace3d-spike.md §1 (sibling Trace3D spike)
 */
import { ref } from "vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";

const urdfCanvasRef = ref<{ captureDataUrl: () => string } | null>(null);

function captureDataUrl(): string {
  return urdfCanvasRef.value?.captureDataUrl() ?? "";
}

defineExpose({ captureDataUrl });

withDefaults(
  defineProps<{
    /** URL of the .urdf file (signed Garage URL or static asset). */
    urdfUrl: string;
    /** Mesh-resolution root for `package://` URIs in the URDF. */
    packagePath?: string;
    /** Current joint values, name → radians. */
    jointValues?: Record<string, number>;
    /** Human-readable label shown in the legend. */
    label?: string;
    /** Background color for the canvas. */
    backgroundColor?: string;
  }>(),
  {
    packagePath: "",
    jointValues: () => ({}),
    label: "URDF",
    backgroundColor: "#0d0d0d",
  },
);

const emit = defineEmits<{
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  "robot-loaded": [robot: any];
  "load-error": [err: Error];
}>();
</script>

<template>
  <div class="urdf-view">
    <ClientOnly>
      <UrdfCanvas
        ref="urdfCanvasRef"
        :urdf-url="urdfUrl"
        :package-path="packagePath"
        :joint-values="jointValues"
        :background-color="backgroundColor"
        @robot-loaded="(r) => emit('robot-loaded', r)"
        @load-error="(e) => emit('load-error', e)"
      />
      <template #fallback>
        <v-skeleton-loader type="image" height="500" />
      </template>
    </ClientOnly>

    <div class="urdf-view__legend mt-2 px-1 d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-robot-industrial</v-icon>
      <span class="text-caption text-medium-emphasis">{{ label }}</span>
      <v-spacer />
      <span class="text-caption text-medium-emphasis">
        Drag to orbit · scroll to zoom · right-drag to pan
      </span>
    </div>
  </div>
</template>

<style scoped>
.urdf-view {
  width: 100%;
}
.urdf-view__legend {
  flex-wrap: wrap;
}
</style>
