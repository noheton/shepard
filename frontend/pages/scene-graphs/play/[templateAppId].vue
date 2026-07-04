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
 *  3. Render the robot with UrdfCanvas (Three.js urdf-loader).
 *
 * Design: aidocs/platform/191 §decision-2. Backlog: V2CONV-B4.
 */
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import BindChannelsDialog from "~/components/scene-graph/BindChannelsDialog.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import { fetchTemplateKind } from "~/composables/useSceneGraphPlay";
import { useUrdfReferenceBlob } from "~/composables/useUrdfReferenceBlob";

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
  jointTimeseriesReferenceAppId?: string | null;
  jointChannelBindings?: { joint: string; channelSelector: string }[];
  playbackStatus?: string;
}

const loading = ref(true);
const error = ref<string | null>(null);
const envelope = ref<PlayEnvelope | null>(null);
const showBindChannels = ref(false);

const {
  objectUrl: urdfUrl,
  error: blobError,
  resolve: resolveBlob,
  revoke: revokeBlob,
} = useUrdfReferenceBlob();

// URDF-KUKA-ORANGE-2026-07-01 — provide a packagePath so `package://kuka_quantec_support/…`
// URIs in the URDF resolve against the /urdf-samples/kr210_r2700_2/ tree on
// this frontend host. This is the interim fix ahead of the annotation-driven
// URDF-PACKAGE-PATH-FROM-ANNOTATION row (aidocs/16 §SCENEGRAPH-CANVAS-MESH):
// each URDF FileReference should carry its packagePath as a semantic
// annotation and be resolved at render time. For today's single-URDF
// showcase the frontend-static default is correct; when a second URDF ships
// the annotation must land alongside it.
const urdfPackagePath = computed<string>(() => "/urdf-samples/kr210_r2700_2");

async function load() {
  loading.value = true;
  error.value = null;
  envelope.value = null;
  try {
    // SCENEGRAPH-PLAY-VIEWKIND-BRANCH (aidocs/16 §3962): this page materializes
    // MAPPING_RECIPE templates only. A VIEW_RECIPE appId reaches here when a
    // collection "hero view" points at a render-recipe (small-multiples,
    // trace-3d, …) rather than a scene-graph. Branch on templateKind so a
    // VIEW_RECIPE is handed to the /shapes/render playground instead of failing
    // the MAPPING_RECIPE-only materialize call with a raw, misleading error.
    const kind = await fetchTemplateKind(templateAppId.value);
    if (kind === "VIEW_RECIPE") {
      await navigateTo(
        `/shapes/render?templateAppId=${encodeURIComponent(templateAppId.value)}`,
      );
      return;
    }
    if (kind && kind !== "MAPPING_RECIPE") {
      error.value =
        `This template is a ${kind}, not a playable scene-graph (MAPPING_RECIPE).`;
      return;
    }
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
          <v-spacer />
          <v-btn
            v-if="envelope && !loading"
            variant="tonal"
            size="small"
            prepend-icon="mdi-link-variant"
            data-test="bind-channels-btn"
            @click="showBindChannels = true"
          >
            Bind channels
          </v-btn>
        </div>

        <BindChannelsDialog
          v-if="envelope"
          v-model="showBindChannels"
          :template-app-id="templateAppId"
          :urdf-file-reference-app-id="envelope.urdfFileReferenceAppId ?? ''"
          :joints="(envelope.joints ?? []).map(j => ({ name: j.name, type: j.type }))"
          :current-ts-ref-app-id="envelope.jointTimeseriesReferenceAppId"
          :current-bindings="envelope.jointChannelBindings"
          @applied="load"
        />

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
            <UrdfCanvas
              :urdf-url="urdfUrl"
              :package-path="urdfPackagePath"
              label="Scene-graph 3D view"
            />
            <template #fallback>
              <v-skeleton-loader type="image" height="500" />
            </template>
          </ClientOnly>
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
