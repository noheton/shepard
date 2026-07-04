<script lang="ts" setup>
/**
 * UI3a + MFFD-VIDEOREF-SCALE-1 — VideoPlayer component.
 *
 * Plays a video by handing the URL directly to a native HTML5 `<video>`
 * element so the browser can do its own Range requests and scrub
 * natively. This is the structural fix for the previous blob-fetch
 * approach that downloaded the entire video before playing (fatal on
 * multi-GB MFFD MP4s — froze the tab on a 6 GB welding video).
 *
 * Auth: the browser cannot inject a custom `Authorization` header on
 * `<video src>`, so the JWT travels as `?access_token=…` per
 * MFFD-VIDEOREF-SCALE-1 — `JWTFilter` reads the query param as a
 * fallback when no Authorization header is present (RFC 6750 §2.3).
 *
 * MFFD-MULTIPLAYER-1: optional two-way `currentTime` (seconds) binding +
 * `durationLoaded` emit, used by the synchronised multi-payload player to
 * drive playback from a shared cursor. Both are inert when callers don't
 * use them — the native controls keep working exactly as before.
 *
 * Props:
 *   src           — authenticated download URL (required)
 *   accessToken   — Bearer token; appended as `?access_token=` when given (AAE1)
 *   poster        — optional poster image URL
 *   currentTime   — optional seconds; when changed externally, the player seeks (AAE2)
 *   showControls  — show native controls (default true)
 *   autoplay      — autoplay (default false)
 *   muted         — mute audio (default false)
 */
import { withAccessTokenQueryParam } from "~/utils/videoUrl";

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

const playerError = ref<string | null>(null);
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

const playableSrc = computed(() =>
  withAccessTokenQueryParam(props.src, props.accessToken),
);

function onError(e: Event) {
  const t = e.target as HTMLVideoElement | null;
  const code = t?.error?.code;
  const map: Record<number, string> = {
    1: "Playback aborted by the user.",
    2: "Network error while loading the video.",
    3: "Video decoding error (codec not supported by the browser).",
    4: "Video format not supported.",
  };
  playerError.value =
    (code != null ? map[code] : undefined) ?? "Video could not be played.";
}
</script>

<template>
  <div class="video-player-wrapper">
    <v-alert
      v-if="playerError"
      type="error"
      variant="tonal"
      density="compact"
      class="ma-2"
    >
      {{ playerError }}
    </v-alert>
    <video
      v-else
      ref="videoElement"
      :controls="showControls"
      :autoplay="autoplay"
      :muted="muted"
      preload="metadata"
      :src="playableSrc"
      :poster="props.poster ?? undefined"
      class="video-player"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
      @error="onError"
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

.video-player {
  width: 100%;
  max-height: 480px;
  display: block;
}
</style>
