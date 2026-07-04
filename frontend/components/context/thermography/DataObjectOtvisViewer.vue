<script setup lang="ts">
/**
 * OTVIS-VIEWER — Edevis OTvis active-lock-in thermography frame viewer.
 *
 * <p>Surfaces decoded amplitude/phase heatmap frames from an `.OTvis`
 * archive carried by a singleton FileReference (FR1b) on an NDT-scan
 * DataObject. Distinct from {@link DataObjectThermographyPane} (GAP-7),
 * which works on a FileBundleReference of pre-rendered TIFFs and shows a
 * single composite plate-heatmap + quality score. This viewer shows the
 * actual decoded lock-in result frames: amplitude, phase, and (for raw
 * frames) calibrated temperature, with a frame scrubber.
 *
 * <p>Per the "UI never asks for paths/URLs" rule, the viewer takes the
 * FileReference `appId` and the backend resolves the bytes; the colour
 * map lives server-side so the component just blits the returned PNG.
 *
 * <p>Phase is the default channel for lock-in frames — it is the canonical
 * NDT defect signal (least sensitive to surface emissivity and uneven
 * heating). The backend reports `defaultChannel` per frame.
 *
 * Task: OTVIS-VIEWER (aidocs/16). Design: aidocs/integrations/114.
 */
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import {
  resolveChannel,
  buildRenderUrl,
  buildOtvisIndexBody,
  buildOtvisFrameBody,
  parseFramesIndex,
  type OtvisFrameInfo,
} from "~/utils/otvisViewer";

const props = defineProps<{
  /** appId of the singleton FileReference carrying the .OTvis archive. */
  fileReferenceAppId: string;
  /** Display name of the reference (for the header). */
  referenceName?: string;
}>();

type FrameInfo = OtvisFrameInfo;
interface FramesIndex {
  fileReferenceAppId: string;
  frameCount: number;
  frames: FrameInfo[];
  partialReason?: string | null;
}

/** Shape of the POST /v2/shapes/render JSON view-model (channel bindings). */
interface RenderViewModel {
  channelBindings?: Array<{
    role?: string | null;
    channelSelector?: string | null;
    unit?: string | null;
  }> | null;
}

const index = ref<FramesIndex | null>(null);
const isLoading = ref(false);
const errorMessage = ref<string | null>(null);

const currentFrame = ref(0);
const channel = ref<string>("phase");
const imageUrl = ref<string | null>(null);
const isFrameLoading = ref(false);
let lastObjectUrl: string | null = null;

const frameInfo = computed<FrameInfo | null>(
  () => index.value?.frames?.[currentFrame.value] ?? null,
);
const availableChannels = computed<string[]>(
  () => frameInfo.value?.channels ?? [],
);
const frameCount = computed<number>(() => index.value?.frameCount ?? 0);

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

async function fetchIndex() {
  errorMessage.value = null;
  isLoading.value = true;
  try {
    // V2CONV-A7-THERMO — frames catalogue via POST /v2/shapes/render
    // (file-rooted, params.mode=index). Replaces GET /v2/thermography/otvis/{appId}/frames.
    const res = await fetch(buildRenderUrl(v2BaseUrl()), {
      method: "POST",
      headers: {
        ...authHeaders(),
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(buildOtvisIndexBody(props.fileReferenceAppId)),
    });
    if (!res.ok) {
      if (res.status === 422) {
        errorMessage.value = "This file is not a decodable Edevis OTvis archive.";
        return;
      }
      throw new Error(`HTTP ${res.status}`);
    }
    const vm = (await res.json()) as RenderViewModel;
    const frames = parseFramesIndex(vm.channelBindings);
    index.value = {
      fileReferenceAppId: props.fileReferenceAppId,
      frameCount: frames.length,
      frames,
    };
    currentFrame.value = 0;
    channel.value = frames[0]?.defaultChannel ?? "amplitude";
    await loadFrameImage();
  } catch (e) {
    errorMessage.value = `Failed to load OTvis frames — ${String((e as Error).message)}`;
  } finally {
    isLoading.value = false;
  }
}

async function loadFrameImage() {
  if (!index.value || frameCount.value === 0) return;
  isFrameLoading.value = true;
  try {
    // V2CONV-A7-THERMO — frame heatmap PNG via POST /v2/shapes/render
    // (file-rooted, params.frame/channel, Accept: image/png).
    const res = await fetch(buildRenderUrl(v2BaseUrl()), {
      method: "POST",
      headers: {
        ...authHeaders(),
        Accept: "image/png",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(
        buildOtvisFrameBody(props.fileReferenceAppId, currentFrame.value, channel.value),
      ),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    if (lastObjectUrl) URL.revokeObjectURL(lastObjectUrl);
    lastObjectUrl = URL.createObjectURL(blob);
    imageUrl.value = lastObjectUrl;
  } catch (e) {
    errorMessage.value = `Failed to render frame — ${String((e as Error).message)}`;
  } finally {
    isFrameLoading.value = false;
  }
}

watch([currentFrame, channel], () => {
  // Keep the channel valid for the new frame (raw frames have no amplitude/phase).
  const valid = resolveChannel(frameInfo.value, channel.value);
  if (valid !== channel.value) {
    channel.value = valid;
    return; // the channel change re-triggers this watcher
  }
  loadFrameImage();
});

onMounted(fetchIndex);
onUnmounted(() => {
  if (lastObjectUrl) URL.revokeObjectURL(lastObjectUrl);
});
</script>

<template>
  <div class="d-flex flex-column ga-4 pa-4 otvis-viewer" data-test="otvis-viewer">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h5 class="text-h5">
        Thermography Frames
        <span v-if="referenceName" class="text-body-2 text-medium-emphasis">
          — {{ referenceName }}
        </span>
      </h5>
      <v-btn-toggle
        v-if="availableChannels.length > 1"
        v-model="channel"
        density="comfortable"
        color="primary"
        mandatory
        data-test="otvis-channel-toggle"
      >
        <v-btn
          v-for="ch in availableChannels"
          :key="ch"
          :value="ch"
          size="small"
        >
          {{ ch }}
        </v-btn>
      </v-btn-toggle>
    </div>

    <v-alert
      v-if="errorMessage"
      type="error"
      variant="tonal"
      :text="errorMessage"
      closable
      data-test="otvis-error"
      @click:close="errorMessage = null"
    />

    <v-alert
      v-if="index?.partialReason"
      type="warning"
      variant="tonal"
      density="compact"
      data-test="otvis-partial"
      :text="`Some frames were tolerated with issues: ${index.partialReason}`"
    />

    <centered-loading-spinner v-if="isLoading" />

    <template v-else-if="index && frameCount > 0">
      <div class="d-flex flex-wrap ga-3 text-body-2">
        <span><strong>Frames:</strong> {{ frameCount }}</span>
        <span v-if="frameInfo"><strong>Kind:</strong> {{ frameInfo.kind }}</span>
        <span><strong>Channel:</strong> {{ channel }}</span>
      </div>

      <div class="otvis-canvas-frame" data-test="otvis-image-frame">
        <v-progress-circular
          v-if="isFrameLoading"
          indeterminate
          class="otvis-frame-spinner"
        />
        <img
          v-if="imageUrl"
          :src="imageUrl"
          class="otvis-image"
          :alt="`OTvis frame ${currentFrame} (${channel})`"
          data-test="otvis-image"
        >
      </div>

      <div v-if="frameCount > 1" class="d-flex align-center ga-3">
        <span class="text-caption">Frame {{ currentFrame + 1 }} / {{ frameCount }}</span>
        <v-slider
          v-model="currentFrame"
          :min="0"
          :max="frameCount - 1"
          :step="1"
          density="compact"
          hide-details
          color="primary"
          class="flex-grow-1"
          data-test="otvis-scrubber"
        />
      </div>

      <p class="text-caption text-medium-emphasis">
        <strong>Phase</strong> images reveal subsurface defects (delamination,
        porosity) with the least sensitivity to surface emissivity and uneven
        heating — the canonical CFRP NDT channel. <strong>Amplitude</strong>
        shows the thermal-response magnitude. Drag the scrubber to step through
        decoded lock-in result frames.
      </p>
    </template>

    <v-alert
      v-else-if="!errorMessage"
      type="info"
      variant="tonal"
      data-test="otvis-empty"
      text="No decoded frames in this OTvis archive."
    />
  </div>
</template>

<style scoped>
.otvis-canvas-frame {
  position: relative;
  display: inline-block;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  background: #111;
  padding: 4px;
  min-height: 120px;
}
.otvis-image {
  display: block;
  image-rendering: pixelated;
  max-width: 100%;
  height: auto;
}
.otvis-frame-spinner {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>
