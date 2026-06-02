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
 * Props:
 *   src         — authenticated download URL (required)
 *   accessToken — Bearer token; appended as `?access_token=` when given
 *   poster      — optional poster image URL
 */
import { withAccessTokenQueryParam } from "~/utils/videoUrl";

const props = defineProps<{
  src: string;
  accessToken?: string | null;
  poster?: string | null;
}>();

const playerError = ref<string | null>(null);

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
      controls
      preload="metadata"
      :src="playableSrc"
      :poster="props.poster ?? undefined"
      class="video-player"
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
