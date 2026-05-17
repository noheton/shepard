<script setup lang="ts">
/**
 * UI3a (aidocs/85 §2.2) / UI6 (aidocs/16) — VideoStreamReferences pane.
 *
 * Shows all VideoStreamReferences for a DataObject. Each reference renders:
 *   - A native <video> element (VideoPlayer.vue) against the /download endpoint
 *   - An ffprobe-extracted metadata row (duration, resolution, codec, FPS, size)
 *   - A download button
 *
 * VID1a note: HLS segmented delivery is not yet implemented (deferred to VID1b).
 * The player uses the raw /download URL, which works for progressive MP4/WebM in
 * all modern browsers. When VID1b ships the m3u8 endpoint and hls.js is added,
 * upgrade VideoPlayer.vue to use it.
 */
import VideoPlayer from "~/components/common/VideoPlayer.vue";
import {
  type VideoStreamReferenceIO,
  useFetchVideoStreamReferences,
} from "~/composables/context/useFetchVideoStreamReferences";

const props = defineProps<{ dataObjectAppId: string }>();

const { references, isLoading, fetchError, downloadUrl } =
  useFetchVideoStreamReferences(props.dataObjectAppId);

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
    </div>

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
          <VideoPlayer :src="downloadUrl(ref.appId)" />

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
      No video references attached yet. Upload a video file (MP4, MOV, MKV, WebM, AVI)
      via the API to see it here.
    </div>
  </div>
</template>

<style scoped lang="scss">
.video-card {
  border-radius: 8px;
}
</style>
