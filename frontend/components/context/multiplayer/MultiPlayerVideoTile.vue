<script setup lang="ts">
/**
 * MFFD-MULTIPLAYER-1 — Video tile for the multi-payload synchronised player.
 *
 * <p>Wraps {@code VideoPlayer.vue} with the cursor two-way binding it gained
 * for this feature. When the user scrubs the shared cursor, the video seeks;
 * when the user uses the native video controls, the cursor advances.
 *
 * <p>The tile registers its playable range with the shared cursor as soon as
 * the video element reports its duration. Until then, the tile does not
 * constrain the intersected range — that lets the other tiles' ranges drive
 * the toolbar scrubber even before the video buffers enough to know its
 * length.
 *
 * <p>Cursor units are DO-relative milliseconds; the video element uses
 * seconds. This tile converts at the binding boundary.
 */
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import VideoPlayer from "~/components/common/VideoPlayer.vue";
import { useSyncedTimeCursor } from "~/composables/context/useSyncedTimeCursor";
import { useFetchVideoStreamReferences } from "~/composables/context/useFetchVideoStreamReferences";

const props = defineProps<{
  dataObjectAppId: string;
}>();

const cursor = useSyncedTimeCursor();

const accessTokenRef = computed(() => {
  const session = useAuth().data.value as { accessToken?: string } | null;
  return session?.accessToken ?? null;
});

const { references, isLoading, fetchError, downloadUrl } =
  useFetchVideoStreamReferences(props.dataObjectAppId);

/** First video reference wins — most DOs have one video angle in v1. */
const videoRef = computed(() => references.value[0] ?? null);
const videoUrl = computed(() =>
  videoRef.value ? downloadUrl(videoRef.value.appId) : null,
);

const videoCurrentTime = ref(0);
let unregister: (() => void) | null = null;

// Cursor → video: convert ms to seconds.
const cursorSeconds = computed(() => cursor.currentTime.value / 1000);

// Video → cursor: when the video reports a new time, write it back. The
// VideoPlayer's internal watcher won't re-seek because of the 50ms epsilon.
function onVideoTimeUpdate(t: number): void {
  videoCurrentTime.value = t;
  const newCursorMs = Math.round(t * 1000);
  if (Math.abs(cursor.currentTime.value - newCursorMs) > 50) {
    cursor.seek(newCursorMs);
  }
}

function onDurationLoaded(d: number): void {
  if (!Number.isFinite(d) || d <= 0) return;
  if (unregister) unregister();
  unregister = cursor.registerRange({
    id: `video:${props.dataObjectAppId}`,
    start: 0,
    end: d * 1000,
  });
}

// React to cursor changes by pushing them as a prop into VideoPlayer. The
// VideoPlayer watches its own prop and seeks the underlying element.
watch(cursorSeconds, t => {
  videoCurrentTime.value = t;
});

onUnmounted(() => {
  unregister?.();
});

onMounted(() => {
  /* fetch happens inside the composable */
});
</script>

<template>
  <div class="video-tile">
    <div class="tile-label">
      <span class="title">Video</span>
      <span v-if="videoRef?.name" class="ref-name">{{ videoRef.name }}</span>
    </div>
    <div v-if="isLoading" class="status">Loading video...</div>
    <v-alert
      v-else-if="fetchError"
      type="warning"
      density="compact"
      variant="tonal"
    >
      {{ fetchError }}
    </v-alert>
    <div v-else-if="!videoUrl" class="status">
      No video reference on this DataObject.
    </div>
    <div v-else class="player-wrap">
      <VideoPlayer
        :src="videoUrl"
        :access-token="accessTokenRef"
        :current-time="videoCurrentTime"
        :show-controls="true"
        :muted="true"
        @update:current-time="onVideoTimeUpdate"
        @duration-loaded="onDurationLoaded"
      />
    </div>
  </div>
</template>

<style scoped>
.video-tile {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 220px;
}
.tile-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  padding-bottom: 4px;
}
.ref-name {
  font-weight: 400;
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}
.player-wrap {
  flex: 1;
  min-height: 180px;
}
.status {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.6;
  font-style: italic;
}
</style>
