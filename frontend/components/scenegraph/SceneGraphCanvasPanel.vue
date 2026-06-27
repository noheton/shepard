<script setup lang="ts">
/**
 * SCENEGRAPH-CANVAS-1 — 3D viewport for a scene graph's source URDF.
 *
 * Closes the MFFD critical-path gap: a scene created from a kr210 URDF
 * (SCENEGRAPH-CREATE-FROM-URDF) previously landed the user on a tree +
 * inspector editor with NO visual render of the robot. This panel mounts
 * the source URDF in the shared {@link ~/components/shapes/UrdfCanvas.vue}
 * renderer and exposes a manual joint panel so the operator can pose the
 * robot — the static-view tier of the URDF-WEBVIEW-1 family.
 *
 * Addressing is by `appId` only (no path/URL input). Two render sources,
 * picked for maximum fidelity:
 *   1. When the scene has a `sourceFileAppId` (minted from a URDF upload),
 *      we render the ORIGINAL file's bytes via the authenticated content
 *      endpoint ({@link ~/composables/useUrdfReferenceBlob.ts}) — this
 *      preserves the real STL/Collada mesh geometry (e.g. the kr210 cell
 *      robot looks like a robot, not a stick figure).
 *   2. When the scene was built by hand (no `sourceFileAppId`), we fall
 *      back to the reconstructed `export.urdf` (frame links only, no mesh
 *      visuals — the exporter emits one `<link>` per frame) via the
 *      existing {@link fetchSceneUrdfBlobUrl} helper, so even bespoke
 *      scenes get a render instead of an empty box.
 *
 * Timeseries-driven JOINT_ANGLE playback (binding a channel to drive a
 * joint) is the next tier — tracked as SCENEGRAPH-CANVAS-ANIM-1 in
 * aidocs/16. The UrdfAnimator component already exists; this panel keeps
 * the static manual-pose surface so the visual lands first. `package://`
 * mesh resolution for multi-file robot bundles is SCENEGRAPH-CANVAS-MESH-1.
 */
import { ref, watch, onUnmounted } from "vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import UrdfJointPanel, {
  type UrdfJointSpec,
} from "~/components/container/timeseries/UrdfJointPanel.vue";
import { extractJointSpecs, type UrdfRobotLike } from "~/utils/urdfChannelPicker";
import { useUrdfReferenceBlob } from "~/composables/useUrdfReferenceBlob";
import { fetchSceneUrdfBlobUrl } from "~/composables/context/useCollectionSceneGraphLink";

const props = defineProps<{
  /** The scene's own appId — used for the `export.urdf` fallback render. */
  sceneAppId: string;
  /** Singleton FileReference appId the scene was minted from, if any. */
  sourceFileAppId?: string | null;
  /** Scene name — shown in the canvas legend. */
  sceneName?: string | null;
}>();

const { objectUrl, loading, error, resolve, revoke } = useUrdfReferenceBlob();

const jointSpecs = ref<UrdfJointSpec[]>([]);
const jointValues = ref<Record<string, number>>({});
const loadError = ref<string | null>(null);

/** Blob URL produced by the `export.urdf` fallback (hand-built scenes). */
const fallbackUrl = ref<string | null>(null);
const fallbackLoading = ref(false);

function revokeFallback(): void {
  if (fallbackUrl.value) {
    URL.revokeObjectURL(fallbackUrl.value);
    fallbackUrl.value = null;
  }
}

/** The URL actually fed to UrdfCanvas: source-file bytes if present, else export.urdf. */
const renderUrl = computed<string | null>(
  () => objectUrl.value ?? fallbackUrl.value,
);
const isResolving = computed(() => loading.value || fallbackLoading.value);

async function loadFallback(): Promise<void> {
  revokeFallback();
  fallbackLoading.value = true;
  try {
    const { data: session } = useAuth();
    const token = session.value?.accessToken;
    if (!token) return;
    fallbackUrl.value = await fetchSceneUrdfBlobUrl(props.sceneAppId, token);
  } catch {
    fallbackUrl.value = null;
  } finally {
    fallbackLoading.value = false;
  }
}

watch(
  () => [props.sceneAppId, props.sourceFileAppId],
  ([sceneAppId, sourceFileAppId]) => {
    jointSpecs.value = [];
    jointValues.value = {};
    loadError.value = null;
    revoke();
    revokeFallback();
    if (sourceFileAppId) void resolve(sourceFileAppId);
    else if (sceneAppId) void loadFallback();
  },
  { immediate: true },
);

function onRobotLoaded(robot: UrdfRobotLike): void {
  jointSpecs.value = extractJointSpecs(robot);
  loadError.value = null;
}

function onLoadError(err: Error): void {
  loadError.value = err.message;
}

onUnmounted(() => {
  revoke();
  revokeFallback();
});
</script>

<template>
  <v-card variant="outlined" class="pa-3" data-test="scene-graph-canvas-panel">
    <div class="text-h6 mb-2 d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-robot-industrial</v-icon>
      3D view
    </div>

    <!-- Resolving the URDF bytes -->
    <div
      v-if="isResolving"
      class="text-center py-6"
      data-test="scene-graph-canvas-loading"
    >
      <v-progress-circular indeterminate color="primary" />
      <div class="text-caption text-medium-emphasis mt-2">
        {{ sourceFileAppId ? "Resolving source URDF…" : "Building view from frames…" }}
      </div>
    </div>

    <!-- Source-file fetch failure (only when a source file was expected) -->
    <v-alert
      v-else-if="error && sourceFileAppId"
      type="error"
      variant="tonal"
      density="compact"
      data-test="scene-graph-canvas-fetch-error"
    >
      {{ error.message }}
    </v-alert>

    <!-- Render + manual joint controls -->
    <template v-else-if="renderUrl">
      <ClientOnly>
        <UrdfCanvas
          :urdf-url="renderUrl"
          :joint-values="jointValues"
          :background-color="'#0d0d0d'"
          data-test="scene-graph-canvas"
          @robot-loaded="onRobotLoaded"
          @load-error="onLoadError"
        />
        <template #fallback>
          <v-skeleton-loader type="image" height="500" />
        </template>
      </ClientOnly>

      <v-alert
        v-if="!sourceFileAppId"
        type="info"
        variant="tonal"
        density="compact"
        class="mt-2"
        data-test="scene-graph-canvas-frames-only"
      >
        This scene was built directly (no URDF upload), so the view shows
        coordinate frames without mesh geometry.
      </v-alert>

      <v-alert
        v-if="loadError"
        type="warning"
        variant="tonal"
        density="compact"
        class="mt-2"
        data-test="scene-graph-canvas-render-error"
      >
        The URDF was fetched but could not be fully rendered: {{ loadError }}.
        Bundled mesh assets (<code>package://</code> meshes) are not yet
        resolved in this view — see SCENEGRAPH-CANVAS-MESH-1.
      </v-alert>

      <UrdfJointPanel
        v-if="jointSpecs.length > 0"
        v-model="jointValues"
        :joints="jointSpecs"
        class="mt-3"
        data-test="scene-graph-canvas-joints"
      />
    </template>

    <!-- Could resolve nothing -->
    <v-alert
      v-else
      type="info"
      variant="tonal"
      density="compact"
      data-test="scene-graph-canvas-empty"
    >
      No renderable geometry for this scene yet.
    </v-alert>
  </v-card>
</template>
