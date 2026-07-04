<script setup lang="ts">
/**
 * MFFD-MULTIPLAYER-1 — Synchronised multi-payload player.
 *
 * <p>Mounts above the DataObject detail tabs and renders a grid of
 * payload-kind tiles (timeseries, video, thermography, spatial). Every tile
 * reads and writes the same {@code currentTime} via the
 * {@link useSyncedTimeCursor} provide/inject pair — scrubbing the toolbar
 * scrubber moves every tile's cursor, hovering a TS tile moves the video
 * playhead, etc.
 *
 * <p>The pane is lazy: it instantiates only when its containing expansion
 * panel opens. The parent page enforces the "≥ 2 distinct payload kinds"
 * gate before mounting so DOs with a single payload don't see a useless
 * one-tile player.
 *
 * <p>Tile order is hardcoded for v1 (TS, video, thermography, spatial); a
 * future revision will read {@code mffd:multi-player:tile-order} from the
 * DO's semantic annotations to let researchers reorder per-DO. Tracking:
 * {@code MFFD-MULTIPLAYER-CONFIG-1}.
 *
 * <p>Reuse: this pane builds on the AAA3 {@code crossTime} hover-sync idiom
 * (graduated to {@code provide}/{@code inject} so deeply-nested tiles can
 * subscribe without prop drilling) and the AAC2 / AAC1 source components
 * for thermography and spatial. See the tile JSDoc for the deferred-sync
 * details on those two.
 */
import { computed, ref } from "vue";
import { provideSyncedTimeCursor } from "~/composables/context/useSyncedTimeCursor";
import MultiPlayerTsTile from "./MultiPlayerTsTile.vue";
import MultiPlayerVideoTile from "./MultiPlayerVideoTile.vue";
import MultiPlayerThermographyTile from "./MultiPlayerThermographyTile.vue";
import MultiPlayerSpatialTile from "./MultiPlayerSpatialTile.vue";

export type MultiPlayerTileKind = "ts" | "video" | "thermo" | "spatial";

withDefaults(
  defineProps<{
    dataObjectAppId: string;
    /**
     * Which tiles to render. The parent decides based on which payload
     * kinds are present on the DataObject. Order is preserved.
     */
    tiles: MultiPlayerTileKind[];
    /** Optional thermography bundle appId; required when "thermo" is in tiles. */
    thermographyBundleAppId?: string | null;
  }>(),
  {
    thermographyBundleAppId: null,
  },
);

const cursor = provideSyncedTimeCursor();

const PLAYBACK_RATES = [0.5, 1, 2, 4] as const;

const scrubberValue = computed({
  get: () => cursor.currentTime.value,
  set: (v: number) => cursor.seek(v),
});

const scrubberRange = computed(() => {
  const r = cursor.range.value;
  if (!r) return { min: 0, max: 1, disabled: true };
  return { min: r.start, max: r.end, disabled: false };
});

const formattedTime = computed(() => (cursor.currentTime.value / 1000).toFixed(2));
const formattedEnd = computed(() => {
  const r = cursor.range.value;
  return r ? (r.end / 1000).toFixed(2) : "—";
});

const selectedRate = ref<number>(1);
function changeRate(r: number): void {
  selectedRate.value = r;
  cursor.setRate(r);
}

function onRewind(): void {
  const r = cursor.range.value;
  cursor.seek(r ? r.start : 0);
}
</script>

<template>
  <div class="multi-player" data-testid="multi-player-pane">
    <div class="toolbar">
      <v-btn
        :icon="cursor.isPlaying.value ? 'mdi-pause' : 'mdi-play'"
        size="small"
        variant="tonal"
        :aria-label="cursor.isPlaying.value ? 'Pause' : 'Play'"
        data-testid="multi-player-play"
        @click="cursor.togglePlay()"
      />
      <v-btn
        icon="mdi-skip-backward"
        size="small"
        variant="text"
        aria-label="Rewind to start"
        data-testid="multi-player-rewind"
        @click="onRewind"
      />
      <div class="rate-group">
        <v-btn
          v-for="r in PLAYBACK_RATES"
          :key="r"
          size="x-small"
          :variant="selectedRate === r ? 'flat' : 'text'"
          :color="selectedRate === r ? 'primary' : undefined"
          :data-testid="`multi-player-rate-${r}`"
          @click="changeRate(r)"
        >
          {{ r }}×
        </v-btn>
      </div>
      <div class="scrubber-wrap">
        <v-slider
          v-model="scrubberValue"
          :min="scrubberRange.min"
          :max="scrubberRange.max"
          :disabled="scrubberRange.disabled"
          density="compact"
          hide-details
          color="primary"
          data-testid="multi-player-scrubber"
        />
      </div>
      <div class="time-readout">
        <code>{{ formattedTime }} / {{ formattedEnd }} s</code>
      </div>
    </div>
    <div
      v-if="cursor.constrainingTileCount.value === 0"
      class="no-range-banner"
    >
      No tile has reported a playable range yet — scrubber is inactive until
      timeseries / video metadata loads.
    </div>
    <div class="tile-grid" :data-tile-count="tiles.length">
      <template v-for="kind in tiles" :key="kind">
        <div class="tile" :data-kind="kind">
          <MultiPlayerTsTile
            v-if="kind === 'ts'"
            :data-object-app-id="dataObjectAppId"
          />
          <MultiPlayerVideoTile
            v-else-if="kind === 'video'"
            :data-object-app-id="dataObjectAppId"
          />
          <MultiPlayerThermographyTile
            v-else-if="kind === 'thermo' && thermographyBundleAppId"
            :data-object-app-id="dataObjectAppId"
            :image-bundle-app-id="thermographyBundleAppId"
          />
          <MultiPlayerSpatialTile
            v-else-if="kind === 'spatial'"
            :data-object-app-id="dataObjectAppId"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.multi-player {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 8px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 6px 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.02);
}
.rate-group {
  display: flex;
  gap: 2px;
}
.scrubber-wrap {
  flex: 1;
  min-width: 200px;
}
.time-readout {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  opacity: 0.85;
  min-width: 160px;
  text-align: right;
}
.no-range-banner {
  font-size: 12px;
  opacity: 0.65;
  font-style: italic;
  padding: 4px 8px;
}
.tile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}
.tile {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 4px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.015);
  min-height: 240px;
  display: flex;
  flex-direction: column;
}
</style>
