<script lang="ts" setup>
/**
 * UI3a — VideoPlayer component.
 *
 * Renders a native <video> element against the provided `src` URL.
 * Because the download endpoint requires a Bearer token and <video src>
 * cannot send custom headers, this component fetches the video as a blob
 * with the supplied accessToken and uses a blob: URL for the video element.
 * The blob URL is revoked on unmount to avoid memory leaks.
 *
 * VID1a note: HLS segmented delivery is deferred to VID1b. When VID1b ships
 * the m3u8 endpoint, upgrade to hls.js and drop the blob-URL approach.
 *
 * MFFD-MULTIPLAYER-1: optional two-way `currentTime` (seconds) binding +
 * `durationLoaded` emit, used by the synchronised multi-payload player to
 * drive playback from a shared cursor. Both are inert when callers don't
 * use them — the native controls keep working exactly as before.
 *
 * Props:
 *   src           — authenticated download URL (required)
 *   accessToken   — Bearer token used to fetch the video blob (required for auth)
 *   poster        — optional poster image URL
 *   currentTime   — optional seconds; when changed externally, the player seeks
 *   showControls  — show native controls (default true)
 *   autoplay      — autoplay (default false)
 *   muted         — mute audio (default false)
 */

const props = withDefaults(
  defineProps<{
    src: string;
    accessToken?: string | null;
    poster?: string | null;
    currentTime?: number;
    showControls?: boolean;
    autoplay?: boolean;
    muted?: boolean;
  }>(),
  {
    accessToken: null,
    poster: null,
    currentTime: undefined,
    showControls: true,
    autoplay: false,
    muted: false,
  },
);

const emit = defineEmits<{
  (e: "update:currentTime" | "durationLoaded", value: number): void;
}>();

const blobSrc = ref<string | null>(null);
const loadError = ref<string | null>(null);
const isLoading = ref(true);
const videoElement = ref<HTMLVideoElement | null>(null);

/**
 * MFFD-MULTIPLAYER-1: when a caller writes a new `currentTime` prop value,
 * seek the underlying element. We compare against the element's own time so
 * the two-way-binding update emit we send back doesn't trigger a re-seek.
 */
watch(
  () => props.currentTime,
  t => {
    const el = videoElement.value;
    if (el == null || t == null) return;
    if (Math.abs(el.currentTime - t) > 0.05) {
      try {
        el.currentTime = t;
      } catch {
        // Some browsers throw if the video isn't fully loaded; ignore — the
        // next watch tick will retry once the user buffer fills.
      }
    }
  },
);

function onTimeUpdate(): void {
  const el = videoElement.value;
  if (!el) return;
  emit("update:currentTime", el.currentTime);
}

function onLoadedMetadata(): void {
  const el = videoElement.value;
  if (!el || !Number.isFinite(el.duration)) return;
  emit("durationLoaded", el.duration);
}

onMounted(async () => {
  if (!props.accessToken) {
    blobSrc.value = props.src;
    isLoading.value = false;
    return;
  }
  try {
    const response = await fetch(props.src, {
      headers: { Authorization: `Bearer ${props.accessToken}` },
    });
    if (!response.ok) {
      loadError.value = `HTTP ${response.status}`;
      return;
    }
    const blob = await response.blob();
    blobSrc.value = URL.createObjectURL(blob);
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : "Network error";
  } finally {
    isLoading.value = false;
  }
});

onUnmounted(() => {
  if (blobSrc.value?.startsWith("blob:")) {
    URL.revokeObjectURL(blobSrc.value);
  }
});
</script>

<template>
  <div class="video-player-wrapper">
    <div v-if="isLoading" class="video-loading">
      <v-progress-circular indeterminate color="white" size="32" aria-label="Loading video" />
    </div>
    <v-alert
      v-else-if="loadError"
      type="error"
      variant="tonal"
      density="compact"
      class="ma-2"
    >
      Failed to load video: {{ loadError }}
    </v-alert>
    <video
      v-else-if="blobSrc"
      ref="videoElement"
      :controls="showControls"
      :autoplay="autoplay"
      :muted="muted"
      preload="auto"
      :src="blobSrc"
      :poster="props.poster ?? undefined"
      class="video-player"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
    >
      Your browser does not support video playback.
    </video>
  </div>
</template>

<style scoped lang="scss">
.video-player-wrapper {
  width: 100%;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
  min-height: 60px;
}

.video-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
}

.video-player {
  width: 100%;
  max-height: 480px;
  display: block;
}
</style>
