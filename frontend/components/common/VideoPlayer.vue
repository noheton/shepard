<script lang="ts" setup>
/**
 * UI3a — VideoPlayer component.
 *
 * Renders a native <video> element against the provided `src` URL.
 * VID1a delivers video via a raw download endpoint (progressive HTTP); HLS
 * segmented delivery is deferred to VID1b. This component will be upgraded to
 * use hls.js when VID1b ships the `/hls/master.m3u8` endpoint and hls.js is
 * added to package.json.
 *
 * Props:
 *   src  — authenticated download URL (required)
 *   poster — optional poster image URL
 */

const props = defineProps<{
  src: string;
  poster?: string | null;
}>();
</script>

<template>
  <div class="video-player-wrapper">
    <video
      controls
      preload="metadata"
      :src="props.src"
      :poster="props.poster ?? undefined"
      class="video-player"
    >
      Your browser does not support video playback.
      <a :href="props.src" download>Download the video</a> instead.
    </video>
  </div>
</template>

<style scoped lang="scss">
.video-player-wrapper {
  width: 100%;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
}

.video-player {
  width: 100%;
  max-height: 480px;
  display: block;
}
</style>
