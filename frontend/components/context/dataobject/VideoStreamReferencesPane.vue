<script setup lang="ts">
/**
 * UI3a (aidocs/85 §2.2) / UI6 (aidocs/16) — VideoStreamReferences pane.
 *
 * Shows all VideoStreamReferences for a DataObject. Each reference renders:
 *   - A native <video> element (VideoPlayer.vue) against the /download endpoint
 *   - An annotation timeline bar (VID1b-annotation) showing labelled time-range
 *     segments when annotations are present
 *   - An ffprobe-extracted metadata row (duration, resolution, codec, FPS, size)
 *   - A download button
 *
 * VID1a note: HLS segmented delivery is not yet implemented (deferred to VID1b).
 * The player uses the raw /download URL, which works for progressive MP4/WebM in
 * all modern browsers. When VID1b ships the m3u8 endpoint and hls.js is added,
 * upgrade VideoPlayer.vue to use it.
 */
import VideoPlayer from "~/components/common/VideoPlayer.vue";
import UploadFilesButton from "~/components/container/UploadFilesButton.vue";
import { xhrUploadMultipart, type XhrUploadOptions } from "~/composables/container/xhrUpload";
import {
  type VideoStreamReferenceIO,
  useFetchVideoStreamReferences,
} from "~/composables/context/useFetchVideoStreamReferences";
import { useFetchVideoAnnotations } from "~/composables/context/useFetchVideoAnnotations";

const props = defineProps<{
  dataObjectAppId: string;
  canUpload?: boolean;
}>();

const VIDEO_ACCEPT =
  "video/mp4,video/quicktime,video/webm,video/x-matroska,video/avi,.mp4,.mov,.mkv,.webm,.avi";

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function uploadVideo(
  file: File,
  options?: XhrUploadOptions,
): Promise<void> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");

  const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(props.dataObjectAppId)}/video-stream-references`;
  // Task #135 — route through XHR so the upload dialog can show progress + cancel.
  await xhrUploadMultipart<unknown>({
    url,
    fieldName: "file",
    file,
    authorization: `Bearer ${accessToken}`,
    options,
  });
}

const { references, isLoading, fetchError, refresh, downloadUrl } =
  useFetchVideoStreamReferences(props.dataObjectAppId);

/**
 * Per-reference annotation state. We lazily create a composable for each
 * reference appId when it first appears.
 */
const annotationStates = new Map<
  string,
  ReturnType<typeof useFetchVideoAnnotations>
>();

function annotationsFor(refAppId: string) {
  if (!annotationStates.has(refAppId)) {
    annotationStates.set(
      refAppId,
      useFetchVideoAnnotations(props.dataObjectAppId, refAppId),
    );
  }
  return annotationStates.get(refAppId)!;
}

/** Small palette for annotation segment colors, cycled by label. */
const SEGMENT_COLORS = [
  "#4CAF50", // green
  "#2196F3", // blue
  "#FF9800", // orange
  "#9C27B0", // purple
  "#F44336", // red
  "#00BCD4", // cyan
  "#FF5722", // deep-orange
  "#795548", // brown
];

const labelColorCache = new Map<string, string>();
let colorIndex = 0;

function colorForLabel(label: string): string {
  if (!labelColorCache.has(label)) {
    const color =
      SEGMENT_COLORS[colorIndex++ % SEGMENT_COLORS.length] ?? "#4CAF50";
    labelColorCache.set(label, color);
  }
  return labelColorCache.get(label) ?? "#4CAF50";
}

const { data: session } = useAuth();
const accessToken = computed(() => session.value?.accessToken ?? null);

/** Format duration in seconds as HH:MM:SS or MM:SS */
function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const total = Math.floor(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Format file size in bytes as human-readable string */
function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/** Format a video annotation time range as "HH:MM:SS → HH:MM:SS UTC" or "(point)" */
function formatAnnotationRange(ann: { startSeconds: number; endSeconds: number | null }): string {
  const start = formatDuration(ann.startSeconds);
  if (ann.endSeconds == null || ann.endSeconds === ann.startSeconds) {
    return `${start} (point)`;
  }
  return `${start}  →  ${formatDuration(ann.endSeconds)}`;
}

/** Format bitrate in bits/s derived from file size and duration */
function formatBitrate(
  ref: VideoStreamReferenceIO,
): string {
  if (!ref.fileSizeBytes || !ref.durationSeconds || ref.durationSeconds <= 0)
    return "—";
  const bps = (ref.fileSizeBytes * 8) / ref.durationSeconds;
  if (bps < 1000) return `${Math.round(bps)} bps`;
  if (bps < 1_000_000) return `${(bps / 1000).toFixed(1)} Kbps`;
  return `${(bps / 1_000_000).toFixed(2)} Mbps`;
}
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h5 class="text-h5">Video References</h5>
      <UploadFilesButton
        v-if="canUpload"
        :accept="VIDEO_ACCEPT"
        :multiple="true"
        button-text="Upload video"
        dialog-title="Upload video file"
        :upload-file="uploadVideo"
        @upload-finished="refresh"
      /></div>

    <v-alert v-if="fetchError" type="error" variant="tonal" density="compact">
      {{ fetchError }}
    </v-alert>

    <centered-loading-spinner v-if="isLoading" />

    <template v-else-if="references.length > 0">
      <v-card
        v-for="ref in references"
        :key="ref.appId"
        variant="outlined"
        class="video-card"
      >
        <v-card-title class="text-body-1 font-weight-medium pb-1">
          {{ ref.name ?? ref.appId }}
        </v-card-title>

        <v-card-text class="pt-0">
          <!-- Player -->
          <VideoPlayer :src="downloadUrl(ref.appId)" :access-token="accessToken" />

          <!-- Annotation timeline bar (VID1b-annotation) -->
          <template v-if="ref.durationSeconds != null && ref.durationSeconds > 0">
            <div v-if="annotationsFor(ref.appId).isLoading.value" class="mt-2">
              <v-skeleton-loader type="text" height="16" />
            </div>
            <div
              v-else-if="annotationsFor(ref.appId).annotations.value.length > 0"
              class="timeline-bar mt-2"
            >
              <v-tooltip
                v-for="ann in annotationsFor(ref.appId).annotations.value"
                :key="ann.appId"
                :text="ann.label + (ann.description ? ' — ' + ann.description : '')"
                location="top"
              >
                <template #activator="{ props: tooltipProps }">
                  <div
                    v-bind="tooltipProps"
                    class="annotation-segment"
                    :style="{
                      left: ((ann.startSeconds / ref.durationSeconds!) * 100).toFixed(3) + '%',
                      width: (
                        ann.endSeconds != null
                          ? ((ann.endSeconds - ann.startSeconds) / ref.durationSeconds!) * 100
                          : 0.5
                      ).toFixed(3) + '%',
                      backgroundColor: colorForLabel(ann.label),
                    }"
                  />
                </template>
              </v-tooltip>
              <!-- Track baseline -->
              <div class="timeline-track" />
            </div>
          </template>

          <!-- Annotation list — interval and point annotations listed as cards,
               same pattern as the timeseries "Anomalies & intervals" section. -->
          <div class="annotations-section mt-4">
            <div class="d-flex align-center ga-2 mb-2">
              <div class="text-subtitle-2">Annotations &amp; intervals</div>
              <v-chip
                v-if="annotationsFor(ref.appId).annotations.value.length > 0"
                size="x-small"
                variant="tonal"
              >
                {{ annotationsFor(ref.appId).annotations.value.length }}
              </v-chip>
            </div>

            <div v-if="annotationsFor(ref.appId).isLoading.value" class="d-flex align-center ga-2 text-medium-emphasis text-body-2">
              <v-progress-circular indeterminate size="14" width="2" />
              Loading annotations…
            </div>
            <div
              v-else-if="annotationsFor(ref.appId).annotations.value.length === 0"
              class="text-medium-emphasis text-body-2"
            >
              No annotations on this reference yet.
            </div>
            <div v-else class="d-flex flex-column ga-2">
              <div
                v-for="ann in annotationsFor(ref.appId).annotations.value"
                :key="ann.appId"
                class="annotation-card pa-3"
              >
                <div class="d-flex align-center ga-2 mb-1">
                  <v-chip
                    v-if="ann.aiGenerated"
                    size="x-small"
                    variant="tonal"
                    color="primary"
                    prepend-icon="mdi-robot-outline"
                  >AI</v-chip>
                  <div class="text-body-2 font-weight-medium">{{ ann.label }}</div>
                  <v-spacer />
                  <span
                    v-if="ann.confidence != null"
                    class="text-caption text-medium-emphasis"
                  >
                    confidence {{ Math.round(ann.confidence * 100) }}%
                  </span>
                </div>
                <div class="text-caption text-medium-emphasis text-mono">
                  {{ formatAnnotationRange(ann) }}
                </div>
                <div v-if="ann.description" class="text-body-2 mt-1">
                  {{ ann.description }}
                </div>
              </div>
            </div>
          </div>

          <!-- Metadata chips -->
          <div class="d-flex flex-wrap ga-2 pt-3">
            <v-chip
              v-if="ref.durationSeconds != null"
              size="small"
              variant="tonal"
              prepend-icon="mdi-timer-outline"
            >
              {{ formatDuration(ref.durationSeconds) }}
            </v-chip>
            <v-chip
              v-if="ref.width != null && ref.height != null"
              size="small"
              variant="tonal"
              prepend-icon="mdi-monitor-screenshot"
            >
              {{ ref.width }}&times;{{ ref.height }}
            </v-chip>
            <v-chip
              v-if="ref.videoCodec"
              size="small"
              variant="tonal"
              prepend-icon="mdi-code-tags"
            >
              {{ ref.videoCodec }}
            </v-chip>
            <v-chip
              v-if="ref.audioCodec"
              size="small"
              variant="tonal"
              prepend-icon="mdi-volume-high"
            >
              {{ ref.audioCodec }}
            </v-chip>
            <v-chip
              v-if="ref.frameRate != null"
              size="small"
              variant="tonal"
              prepend-icon="mdi-play-speed"
            >
              {{ ref.frameRate.toFixed(ref.frameRate % 1 === 0 ? 0 : 2) }} fps
            </v-chip>
            <v-chip
              v-if="ref.fileSizeBytes != null"
              size="small"
              variant="tonal"
              prepend-icon="mdi-database-outline"
            >
              {{ formatBytes(ref.fileSizeBytes) }}
            </v-chip>
            <v-chip
              v-if="ref.fileSizeBytes != null && ref.durationSeconds != null"
              size="small"
              variant="tonal"
              prepend-icon="mdi-speedometer"
            >
              {{ formatBitrate(ref) }}
            </v-chip>
          </div>
        </v-card-text>

        <v-card-actions>
          <v-btn
            :href="downloadUrl(ref.appId)"
            download
            variant="tonal"
            density="comfortable"
            prepend-icon="mdi-download-outline"
            size="small"
          >
            Download
          </v-btn>
        </v-card-actions>
      </v-card>
    </template>

    <div v-else class="text-medium-emphasis">
      No video references attached yet.
      <span v-if="canUpload">Use the upload button above to add a video file (MP4, MOV, MKV, WebM, AVI).</span>
      <span v-else>Upload a video file (MP4, MOV, MKV, WebM, AVI) to see it here.</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.video-card {
  border-radius: 8px;
}

.timeline-bar {
  position: relative;
  width: 100%;
  height: 16px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 4px;
  overflow: hidden;
}

.timeline-track {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.annotation-segment {
  position: absolute;
  top: 0;
  height: 100%;
  min-width: 2px;
  border-radius: 2px;
  opacity: 0.85;
  cursor: pointer;
  transition: opacity 0.15s;

  &:hover {
    opacity: 1;
  }
}

.annotation-card {
  background: rgba(var(--v-border-color), 0.05);
  border-left: 3px solid rgb(var(--v-theme-primary));
  border-radius: 4px;
}

.text-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  font-size: 0.85em;
}
</style>
