<script setup lang="ts">
/**
 * MFFD-MULTIPLAYER-1 — Spatial tile (deferred-sync placeholder).
 *
 * <p>The AAC1 {@code SpatialPointsCanvas} renders either a static pointcloud
 * or a colour-by-t trajectory line. Neither shape exposes a scrubbable
 * cursor today: the pointcloud has no time, and the trajectory's time is
 * baked into its vertex colours.
 *
 * <p>For genuine sync the source component needs an animated brush-head
 * marker that reads {@code currentTime} and renders a sphere at the matching
 * trajectory sample; that is filed as {@code MFFD-MULTIPLAYER-SPATIAL-1}.
 *
 * <p>For v1 we show the playhead time numerically and link to the
 * full spatial viewer. The tile does <i>not</i> register a range — it is
 * informational, not constraining.
 *
 * <p>Tracking: {@code MFFD-MULTIPLAYER-SPATIAL-1}.
 */
import { computed } from "vue";
import { useSyncedTimeCursor } from "~/composables/context/useSyncedTimeCursor";

defineProps<{
  dataObjectAppId: string;
}>();

const cursor = useSyncedTimeCursor();
const cursorSeconds = computed(() => (cursor.currentTime.value / 1000).toFixed(2));
</script>

<template>
  <div class="spatial-tile">
    <div class="tile-label">
      <span class="title">Spatial trajectory</span>
    </div>
    <div class="cursor-readout">
      <div class="t-big">t = {{ cursorSeconds }} s</div>
      <div class="t-caption">brush-head position</div>
    </div>
    <v-alert type="info" variant="tonal" density="compact" class="defer-note">
      Animated brush marker requires a cursor binding on
      <code>SpatialPointsCanvas</code> (<code>MFFD-MULTIPLAYER-SPATIAL-1</code>).
      Open the dedicated spatial viewer for the full trajectory.
    </v-alert>
  </div>
</template>

<style scoped>
.spatial-tile {
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
.cursor-readout {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
  background: rgba(25, 118, 210, 0.04);
  border-radius: 4px;
  margin-bottom: 8px;
}
.t-big {
  font-family: ui-monospace, monospace;
  font-size: 28px;
  font-weight: 600;
  color: #1976d2;
}
.t-caption {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 4px;
}
.defer-note {
  font-size: 11px;
}
</style>
