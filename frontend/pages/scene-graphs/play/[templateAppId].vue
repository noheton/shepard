<script setup lang="ts">
/**
 * SCENEGRAPH-CANVAS-ANIM-1 — /scene-graphs/play/{templateAppId}
 *
 * When the play envelope carries playbackStatus="DECLARED" + a
 * jointTimeseriesReferenceAppId + jointChannelBindings, this page:
 *   1. Fetches the TimeseriesReference (GET /v2/references/{appId}) to
 *      extract the container appId + time range.
 *   2. Lists channels + bulk-fetches the bound joint channels.
 *   3. Builds JointTrack[] and drives UrdfAnimator for timeseries playback.
 *
 * Falls back to the static UrdfCanvas when playbackStatus="STATIC_POSE"
 * or when tracks cannot be loaded (best-effort degradation).
 *
 * Design: aidocs/platform/191 §decision-2. Backlog: SCENEGRAPH-CANVAS-ANIM-1.
 */
import { TimeseriesContainerChannelListingApi } from "@dlr-shepard/backend-client";
import UrdfAnimator from "~/components/container/timeseries/UrdfAnimator.vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import { useUrdfReferenceBlob } from "~/composables/useUrdfReferenceBlob";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import {
  fetchChannelListByAppId,
  fetchBulkTraceByAppId,
} from "~/utils/shapesRenderChannels";
import {
  parseChannelBindings,
  buildJointTracksFromByRole,
} from "~/utils/sceneGraphPlayTracks";
import type { JointTrack } from "~/utils/urdfAnimation";

useHead({ title: "Scene-graph 3D view | shepard" });

const route = useRoute();
const templateAppId = computed(() => String(route.params.templateAppId ?? ""));

// v2 timeseries channel API — must be registered during setup.
const channelApi = useV2ShepardApi(TimeseriesContainerChannelListingApi);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  return explicit && explicit.length > 0
    ? explicit.replace(/\/$/, "")
    : (config.backendApiUrl as string)
        .replace(/\/shepard\/api\/?$/, "")
        .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const h: Record<string, string> = { Accept: "application/json" };
  if (session.value?.accessToken)
    h["Authorization"] = `Bearer ${session.value.accessToken}`;
  return h;
}

interface PlayEnvelope {
  kind?: string;
  robotName?: string;
  urdfFileReferenceAppId?: string;
  rootLink?: string | null;
  frames?: { name: string; parent: string | null }[];
  joints?: { name: string; type: string }[];
  jointChannelBindings?: { joint: string; channelSelector: string }[];
  jointTimeseriesReferenceAppId?: string;
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

// ── animation state ────────────────────────────────────────────────────────────
const jointTracks = ref<JointTrack[]>([]);
const isLoadingTracks = ref(false);
const tracksError = ref<string | null>(null);

const isDeclared = computed(
  () =>
    envelope.value?.playbackStatus === "DECLARED" &&
    !!envelope.value.jointTimeseriesReferenceAppId &&
    (envelope.value.jointChannelBindings?.length ?? 0) > 0,
);

async function loadJointTracks(env: PlayEnvelope): Promise<void> {
  const tsRefAppId = env.jointTimeseriesReferenceAppId;
  const bindings = env.jointChannelBindings ?? [];
  if (!tsRefAppId || bindings.length === 0) return;

  isLoadingTracks.value = true;
  tracksError.value = null;
  try {
    // 1. Resolve container appId + time range from the TimeseriesReference.
    const refResp = await fetch(
      `${v2BaseUrl()}/v2/references/${encodeURIComponent(tsRefAppId)}`,
      { headers: authHeaders() },
    );
    if (!refResp.ok) {
      tracksError.value = `Could not load joint TimeseriesReference (HTTP ${refResp.status})`;
      return;
    }
    const refData = (await refResp.json()) as {
      payload?: {
        timeseriesContainerAppId?: string | null;
        start?: number | null;
        end?: number | null;
      };
    };
    const containerAppId = refData.payload?.timeseriesContainerAppId ?? null;
    const startNs = refData.payload?.start ?? null;
    const endNs = refData.payload?.end ?? null;
    if (!containerAppId || startNs == null || endNs == null) {
      tracksError.value =
        "Joint TimeseriesReference is missing container or time range — animation unavailable.";
      return;
    }

    // 2. Parse the stored channelSelectors (JSON-stringified 5-tuples).
    const roleChannels = parseChannelBindings(
      bindings as { joint: string; channelSelector: string }[],
    );
    if (roleChannels.length === 0) {
      tracksError.value = "No parseable channel selectors in joint bindings.";
      return;
    }

    // 3. Fetch channel list + bulk data.
    const channelList = await fetchChannelListByAppId(
      channelApi.value,
      containerAppId,
    );
    const { byRole } = await fetchBulkTraceByAppId(
      channelApi.value,
      containerAppId,
      roleChannels,
      startNs,
      endNs,
      channelList,
    );

    // 4. Build JointTracks (timestamps ns → ms).
    const tracks = buildJointTracksFromByRole(byRole);
    jointTracks.value = tracks;
    if (tracks.length === 0) {
      tracksError.value =
        "Channel selectors did not match any channels in the container — check annotations.";
    }
  } catch (e) {
    tracksError.value =
      e instanceof Error ? e.message : "Failed to load joint animation data.";
  } finally {
    isLoadingTracks.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = null;
  envelope.value = null;
  jointTracks.value = [];
  tracksError.value = null;
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
    if (
      env.playbackStatus === "DECLARED" &&
      env.jointTimeseriesReferenceAppId &&
      (env.jointChannelBindings?.length ?? 0) > 0
    ) {
      await loadJointTracks(env);
    }
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "Failed to materialize the 3D view.";
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
            :color="isDeclared ? 'primary' : 'secondary'"
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

        <v-skeleton-loader
          v-if="loading || isLoadingTracks"
          type="image"
          height="500"
        />

        <div v-else-if="urdfUrl" class="play-canvas">
          <ClientOnly>
            <!-- Animation path: DECLARED + tracks fetched successfully -->
            <UrdfAnimator
              v-if="jointTracks.length > 0"
              :urdf-url="urdfUrl"
              :tracks="jointTracks"
              :label="envelope?.robotName || 'URDF'"
              data-test="urdf-animator"
            />
            <!-- Static path: STATIC_POSE or tracks unavailable -->
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
        </div>

        <v-alert
          v-if="tracksError && !loading && !isLoadingTracks"
          type="warning"
          variant="tonal"
          density="compact"
          class="mt-2"
          data-test="tracks-error"
        >
          Animation unavailable: {{ tracksError }}
        </v-alert>

        <v-alert
          v-else-if="envelope?.playbackStatus === 'STATIC_POSE' && !loading"
          type="info"
          variant="tonal"
          density="compact"
          class="mt-2"
          data-test="static-pose-hint"
        >
          Static pose — configure a joint TimeseriesReference and channel bindings in the template to enable animation.
        </v-alert>

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
              {{ (envelope.jointChannelBindings?.length ?? 0) }} bindings ·
              {{ jointTracks.length }} tracks loaded
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
