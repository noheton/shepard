<script setup lang="ts">
/**
 * ThermographyView — thin adapter over {@link ~/components/shapes/ThermographyCanvas.vue}
 * for the VIEW_RECIPE shapes/render pipeline.
 *
 * Tier-1 (OTVIS-VIEW-1): annotations-only metadata table + Three.js placeholder.
 * Tier-2 (PLACEHOLDER-thermography-canvas): when `fileReferenceAppId` is
 * supplied the view fetches the first decoded frame via
 * POST /v2/shapes/render (frames index + first frame PNG) and feeds the
 * resulting blob URL into ThermographyCanvas. A compact frame-scrubber
 * nav strip is shown when the archive has more than one frame. The full
 * scrubber with channel toggle lives in DataObjectOtvisViewer (the
 * DataObject-detail surface); this adapter keeps it minimal so the
 * VIEW_RECIPE render page stays focused on context.
 *
 * Tasks: OTVIS-VIEW-1 / PLACEHOLDER-thermography-canvas (aidocs/16).
 * Design refs:
 *   - aidocs/integrations/114-process-monitoring-parser-plugin.md §5
 *   - aidocs/integrations/113-urdf-viewer.md (sibling renderer pattern)
 *   - aidocs/agent-findings/trace3d-spike.md §1 (sibling Trace3D pattern)
 */
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import ThermographyCanvas from "~/components/shapes/ThermographyCanvas.vue";
import ThermographyChannelPicker from "~/components/container/timeseries/ThermographyChannelPicker.vue";
import type { AnnotationMap } from "~/utils/thermographyChannelPicker";
import { parseAspectRatio, THERMOGRAPHY_PREDICATES } from "~/utils/thermographyChannelPicker";
import {
  resolveChannel,
  buildRenderUrl,
  buildOtvisIndexBody,
  buildOtvisFrameBody,
  parseFramesIndex,
  type OtvisFrameInfo,
} from "~/utils/otvisViewer";

const thermographyCanvasRef = ref<{ captureDataUrl: () => string } | null>(null);

function captureDataUrl(): string {
  return thermographyCanvasRef.value?.captureDataUrl() ?? "";
}

defineExpose({ captureDataUrl });

const props = withDefaults(
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
    /**
     * appId of the singleton FileReference carrying the .OTvis archive.
     * When provided, the view fetches the first decoded frame via
     * POST /v2/shapes/render and feeds the blob URL into ThermographyCanvas.
     * When absent, the canvas shows the checkerboard placeholder.
     */
    fileReferenceAppId?: string | null;
  }>(),
  {
    annotations: () => ({}),
    label: "Thermography",
    backgroundColor: "#0d0d0d",
    fileReferenceAppId: null,
  },
);

// ── aspect ratio from annotation ─────────────────────────────────────────────
const aspectRatio = computed<number>(() =>
  parseAspectRatio(props.annotations?.[THERMOGRAPHY_PREDICATES.resolution]),
);

// ── OTvis frame fetch ────────────────────────────────────────────────────────

const frames = ref<OtvisFrameInfo[]>([]);
const currentFrameIndex = ref(0);
const currentChannel = ref("phase");
const frameUrl = ref<string | null>(null);
const isLoadingIndex = ref(false);
const isLoadingFrame = ref(false);
const fetchError = ref<string | null>(null);

let lastObjectUrl: string | null = null;

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

const currentFrameInfo = computed<OtvisFrameInfo | null>(
  () => frames.value[currentFrameIndex.value] ?? null,
);
const availableChannels = computed<string[]>(() => currentFrameInfo.value?.channels ?? []);
const frameCount = computed(() => frames.value.length);

async function fetchIndex(fileRefAppId: string) {
  isLoadingIndex.value = true;
  fetchError.value = null;
  frames.value = [];
  frameUrl.value = null;
  try {
    const res = await fetch(buildRenderUrl(v2BaseUrl()), {
      method: "POST",
      headers: { ...authHeaders(), Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify(buildOtvisIndexBody(fileRefAppId)),
    });
    if (!res.ok) {
      if (res.status === 422) {
        fetchError.value = "File is not a decodable Edevis OTvis archive.";
        return;
      }
      throw new Error(`HTTP ${res.status}`);
    }
    const vm = (await res.json()) as { channelBindings?: unknown[] | null };
    frames.value = parseFramesIndex(vm.channelBindings as Parameters<typeof parseFramesIndex>[0]);
    currentFrameIndex.value = 0;
    currentChannel.value = frames.value[0]?.defaultChannel ?? "phase";
    await loadFrame(fileRefAppId);
  } catch (e) {
    fetchError.value = `Failed to load OTvis frames — ${String((e as Error).message ?? e)}`;
  } finally {
    isLoadingIndex.value = false;
  }
}

async function loadFrame(fileRefAppId: string) {
  if (frames.value.length === 0) return;
  isLoadingFrame.value = true;
  try {
    const ch = resolveChannel(currentFrameInfo.value, currentChannel.value);
    const res = await fetch(buildRenderUrl(v2BaseUrl()), {
      method: "POST",
      headers: { ...authHeaders(), Accept: "image/png", "Content-Type": "application/json" },
      body: JSON.stringify(buildOtvisFrameBody(fileRefAppId, currentFrameIndex.value, ch)),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    if (lastObjectUrl) URL.revokeObjectURL(lastObjectUrl);
    lastObjectUrl = URL.createObjectURL(blob);
    frameUrl.value = lastObjectUrl;
  } catch (e) {
    fetchError.value = `Failed to render frame — ${String((e as Error).message ?? e)}`;
  } finally {
    isLoadingFrame.value = false;
  }
}

watch([currentFrameIndex, currentChannel], () => {
  if (!props.fileReferenceAppId) return;
  const valid = resolveChannel(currentFrameInfo.value, currentChannel.value);
  if (valid !== currentChannel.value) { currentChannel.value = valid; return; }
  void loadFrame(props.fileReferenceAppId);
});

watch(() => props.fileReferenceAppId, (appId) => {
  if (appId) void fetchIndex(appId);
  else { frames.value = []; frameUrl.value = null; fetchError.value = null; }
}, { immediate: false });

onMounted(() => {
  if (props.fileReferenceAppId) void fetchIndex(props.fileReferenceAppId);
});

onUnmounted(() => {
  if (lastObjectUrl) URL.revokeObjectURL(lastObjectUrl);
});
</script>

<template>
  <div class="thermography-view">
    <!-- Frame-data alert: only show when no fileReferenceAppId is supplied -->
    <v-alert
      v-if="!fileReferenceAppId && !isLoadingIndex"
      type="info"
      variant="tonal"
      density="compact"
      class="mb-2"
      prepend-icon="mdi-information-outline"
    >
      Frame data not yet available — only metadata parsed.
      Frame extraction lands with OTVIS-PARSE-2 (tier-2); see
      <code>aidocs/integrations/114</code>.
    </v-alert>

    <v-alert
      v-if="fetchError"
      type="error"
      variant="tonal"
      density="compact"
      class="mb-2"
      closable
      data-test="thermo-view-error"
      @click:close="fetchError = null"
    >
      {{ fetchError }}
    </v-alert>

    <ClientOnly>
      <ThermographyCanvas
        ref="thermographyCanvasRef"
        :label="label"
        :background-color="backgroundColor"
        :frame-url="frameUrl"
        :aspect-ratio="aspectRatio"
      />
      <template #fallback>
        <v-skeleton-loader type="image" height="500" />
      </template>
    </ClientOnly>

    <!-- Frame scrubber strip (compact, only shown when archive has >1 frame) -->
    <template v-if="fileReferenceAppId && frameCount > 1">
      <div class="d-flex align-center ga-3 mt-2 px-1">
        <v-btn-toggle
          v-if="availableChannels.length > 1"
          v-model="currentChannel"
          density="compact"
          color="primary"
          mandatory
          data-test="thermo-view-channel-toggle"
        >
          <v-btn v-for="ch in availableChannels" :key="ch" :value="ch" size="x-small">
            {{ ch }}
          </v-btn>
        </v-btn-toggle>
        <span class="text-caption text-medium-emphasis">Frame {{ currentFrameIndex + 1 }} / {{ frameCount }}</span>
        <v-slider
          v-model="currentFrameIndex"
          :min="0"
          :max="frameCount - 1"
          :step="1"
          density="compact"
          hide-details
          color="primary"
          class="flex-grow-1"
          data-test="thermo-view-scrubber"
        />
        <v-progress-circular
          v-if="isLoadingFrame"
          size="16"
          width="2"
          indeterminate
          class="flex-shrink-0"
        />
      </div>
    </template>

    <div class="thermography-view__legend mt-2 px-1 d-flex align-center ga-2">
      <v-icon size="small" color="primary">mdi-thermometer-lines</v-icon>
      <span class="text-caption text-medium-emphasis">{{ label }}</span>
      <v-spacer />
      <v-chip v-if="fileReferenceAppId && frameCount > 0" size="x-small" variant="tonal" color="info">
        {{ frameCount }} frames · {{ currentChannel }}
      </v-chip>
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
