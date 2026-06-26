<script setup lang="ts">
/**
 * V2CONV-B4-FE — /scene-graphs/play/{templateAppId} — the scene-graph play
 * page. Replaces the bespoke `/scene-graphs/{appId}` editor that read the
 * now-deleted stored frames/joints graph.
 *
 * Flow:
 *  1. Materialize the MAPPING_RECIPE template via
 *     `POST /v2/mappings/{templateAppId}/materialize` — the SceneGraphPlay
 *     executor (vis-trace3d plugin) parses the bound URDF FileReference on
 *     demand and returns a VIEW play envelope (frame tree + joints +
 *     channel->joint bindings + urdfFileReferenceAppId).
 *  2. Resolve the bound URDF FileReference's bytes to a blob URL (appId only —
 *     never a path/URL the user typed).
 *  3. When a TimeseriesReference is bound + channel bindings present, load
 *     JointTrack[] via useJointTracksLoader and render UrdfAnimator for
 *     timeseries-driven playback (SCENEGRAPH-CANVAS-ANIM-1).
 *  4. Fall back to static UrdfCanvas when no tracks are available.
 *
 * Design: aidocs/platform/191 §decision-2. Backlog: V2CONV-B4, SCENEGRAPH-CANVAS-ANIM-1.
 */
import UrdfAnimator from "~/components/container/timeseries/UrdfAnimator.vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import { useUrdfReferenceBlob } from "~/composables/useUrdfReferenceBlob";
import { useJointTracksLoader } from "~/composables/useJointTracksLoader";

useHead({ title: "Scene-graph 3D view | shepard" });

const route = useRoute();
const templateAppId = computed(() => String(route.params.templateAppId ?? ""));

interface PlayEnvelope {
  kind?: string;
  robotName?: string;
  urdfFileReferenceAppId?: string;
  rootLink?: string | null;
  frames?: { name: string; parent: string | null }[];
  joints?: { name: string; type: string }[];
  /** Timeseries reference whose channels drive joint animation. */
  jointTimeseriesReferenceAppId?: string;
  jointChannelBindings?: { joint: string; channelSelector: string }[];
  playbackStatus?: string;
}

const loading = ref(true);
const error = ref<string | null>(null);
const envelope = ref<PlayEnvelope | null>(null);

const {
  objectUrl: urdfUrl,
  error: blobError,
  resolve: resolveBlob,
  revoke: revokeBlob,
} = useUrdfReferenceBlob();

const {
  tracks,
  loading: tracksLoading,
  error: tracksError,
  load: loadTracks,
} = useJointTracksLoader();

async function load() {
  loading.value = true;
  error.value = null;
  envelope.value = null;
  try {
    const result = await materializeMapping(templateAppId.value, {});
    if (result.outputKind !== "VIEW" || !result.viewModel) {
      error.value = "This template did not materialize into a 3D view.";
      return;
    }
    const env = result.viewModel as unknown as PlayEnvelope;
    envelope.value = env;
    if (!env.urdfFileReferenceAppId) {
      error.value = "The play envelope is missing its URDF FileReference appId.";
      return;
    }
    await resolveBlob(env.urdfFileReferenceAppId);
    if (blobError.value) {
      error.value = blobError.value.message;
      return;
    }
    // Load joint tracks when timeseries reference + bindings are present.
    const tsRefAppId = env.jointTimeseriesReferenceAppId;
    const bindings = env.jointChannelBindings ?? [];
    if (tsRefAppId && bindings.length > 0) {
      await loadTracks(tsRefAppId, bindings);
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Failed to materialize the 3D view.";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
onUnmounted(revokeBlob);
</script>

<template>
  <v-container fluid>
    <v-row>
      <v-col>
        <div class="d-flex align-center ga-2 mb-2">
          <v-icon color="primary">mdi-cube-scan</v-icon>
          <h2 class="text-h6">
            {{ envelope?.robotName || "Scene-graph 3D view" }}
          </h2>
          <v-chip
            v-if="envelope?.playbackStatus"
            size="small"
            variant="tonal"
            color="secondary"
            data-test="playback-status-chip"
          >
            {{ envelope.playbackStatus }}
          </v-chip>
        </div>

        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          density="compact"
          data-test="play-error"
        >
          {{ error }}
        </v-alert>

        <v-skeleton-loader v-if="loading" type="image" height="500" />

        <div v-else-if="urdfUrl" class="play-canvas">
          <ClientOnly>
            <!-- Animated: joint tracks loaded from bound TimeseriesReference -->
            <UrdfAnimator
              v-if="tracks.length > 0"
              :urdf-url="urdfUrl"
              :tracks="tracks"
              label="Scene-graph 3D view"
              data-test="urdf-animator"
            />
            <!-- Static: no timeseries reference or tracks still loading -->
            <UrdfCanvas
              v-else
              :urdf-url="urdfUrl"
              label="Scene-graph 3D view"
              data-test="urdf-canvas"
            />
            <template #fallback>
              <v-skeleton-loader type="image" height="500" />
            </template>
          </ClientOnly>

          <v-alert
            v-if="tracksLoading"
            type="info"
            variant="tonal"
            density="compact"
            class="mt-2"
            data-test="tracks-loading"
          >
            Loading channel data for playback…
          </v-alert>
          <v-alert
            v-if="tracksError"
            type="warning"
            variant="tonal"
            density="compact"
            class="mt-2"
            data-test="tracks-error"
          >
            {{ tracksError }}
          </v-alert>
        </div>

        <v-card
          v-if="envelope && !loading"
          variant="outlined"
          class="mt-3"
          data-test="play-summary"
        >
          <v-card-text>
            <div class="text-caption text-medium-emphasis">
              {{ (envelope.frames?.length ?? 0) }} frames ·
              {{ (envelope.joints?.length ?? 0) }} joints ·
              {{ (envelope.jointChannelBindings?.length ?? 0) }} joint bindings
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<style lang="scss" scoped>
.play-canvas {
  min-height: 500px;
}
</style>
