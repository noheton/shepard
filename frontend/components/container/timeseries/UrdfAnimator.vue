<script setup lang="ts">
/**
 * UrdfAnimator — Vuetify-driven trajectory playback for a URDFRobot.
 *
 * Takes a URDF + a list of joint tracks (one channel per movable joint),
 * interpolates each track at the current cursor, and feeds the resulting
 * jointValues map to UrdfView. Uses requestAnimationFrame for the play loop.
 *
 * Controls:
 *   - play / pause toggle
 *   - scrub slider (over the aggregate [tMin, tMax] of all tracks)
 *   - speed presets: 0.1x / 0.5x / 1x / 2x / 10x + reverse
 *   - current-time + total-duration readout
 *
 * Frame budget: 30+ FPS at 100 Hz channel rate per URDF-WEBVIEW-1 (aidocs/16).
 * For tracks with > ~10000 samples per channel, the LTTB-vis-toggle pattern
 * (TS-LTTB-VIS-TOGGLE-01) can be applied upstream — this component does no
 * downsampling itself; it expects the caller to pre-sample.
 */
import { ref, computed, watch, onUnmounted } from "vue";
import UrdfView from "./UrdfView.vue";
import {
  type JointTrack,
  interpolateAt,
  jointValuesAt,
  trackTimeBounds,
  advanceCursor,
} from "~/utils/urdfAnimation";

const props = withDefaults(
  defineProps<{
    /** URDF document URL (signed Garage URL or static asset). */
    urdfUrl: string;
    /** Mesh-resolution root for `package://` URIs. */
    packagePath?: string;
    /** One JointTrack per channel-bound joint. */
    tracks: JointTrack[];
    /**
     * Conversion from the tracks' time units to milliseconds. Default = 1 (ms).
     * If samples are in nanoseconds, pass `1e-6`; for seconds, pass `1000`.
     */
    sampleTimeUnitsPerMs?: number;
    /** Optional label shown in the UrdfView legend. */
    label?: string;
  }>(),
  {
    packagePath: "",
    sampleTimeUnitsPerMs: 1,
    label: "URDF",
  },
);

// ── time-domain state ─────────────────────────────────────────────────────────

const bounds  = computed(() => trackTimeBounds(props.tracks));
const cursor  = ref<number>(0);
const hasData = computed(() => bounds.value !== null);

// Initialise / reset cursor when tracks change
watch(
  () => props.tracks,
  () => {
    if (bounds.value) cursor.value = bounds.value.tMin;
    else              cursor.value = 0;
    pause();
  },
  { immediate: true, deep: false },
);

// ── interpolated joint values for the canvas ──────────────────────────────────

const jointValues = computed<Record<string, number>>(() =>
  jointValuesAt(props.tracks, cursor.value),
);

// ── playback ──────────────────────────────────────────────────────────────────

const speedPresets = [0.1, 0.5, 1, 2, 10] as const;
const selectedSpeedIdx = ref(2); // default 1×
const isReverse = ref(false);
const isPlaying = ref(false);

const playSpeed = computed(() => {
  const base = speedPresets[selectedSpeedIdx.value] ?? 1;
  return isReverse.value ? -base : base;
});

let lastFrameTime: number | null = null;
let rafId: number | null = null;

function playFrame(timestamp: number) {
  if (!isPlaying.value || !bounds.value) { lastFrameTime = null; return; }
  if (lastFrameTime !== null) {
    const dtMs = timestamp - lastFrameTime;
    const { cursor: next, finished } = advanceCursor(
      cursor.value,
      dtMs,
      playSpeed.value,
      bounds.value,
      props.sampleTimeUnitsPerMs,
    );
    cursor.value = next;
    if (finished) {
      isPlaying.value = false;
      lastFrameTime = null;
      return;
    }
  }
  lastFrameTime = timestamp;
  rafId = requestAnimationFrame(playFrame);
}

function play() {
  if (!bounds.value) return;
  // Wrap to start (or end, if reverse) when triggered at the boundary.
  if (playSpeed.value > 0 && cursor.value >= bounds.value.tMax) cursor.value = bounds.value.tMin;
  if (playSpeed.value < 0 && cursor.value <= bounds.value.tMin) cursor.value = bounds.value.tMax;
  isPlaying.value = true;
  lastFrameTime = null;
  rafId = requestAnimationFrame(playFrame);
}

function pause() {
  isPlaying.value = false;
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null; }
}

function togglePlay() {
  if (isPlaying.value) pause(); else play();
}

function reset() {
  pause();
  if (bounds.value) cursor.value = bounds.value.tMin;
}

onUnmounted(() => { if (rafId !== null) cancelAnimationFrame(rafId); });

// ── derived UI labels ─────────────────────────────────────────────────────────

const durationMs = computed<number | null>(() => {
  const b = bounds.value;
  if (!b) return null;
  return (b.tMax - b.tMin) * props.sampleTimeUnitsPerMs;
});

const cursorMs = computed<number | null>(() => {
  const b = bounds.value;
  if (!b) return null;
  return (cursor.value - b.tMin) * props.sampleTimeUnitsPerMs;
});

function formatMs(ms: number | null): string {
  if (ms === null) return "—";
  const sign = ms < 0 ? "-" : "";
  const abs = Math.abs(ms);
  const totalS = abs / 1000;
  const m = Math.floor(totalS / 60);
  const s = totalS - m * 60;
  return `${sign}${m.toString().padStart(2, "0")}:${s.toFixed(2).padStart(5, "0")}`;
}

const cursorLabel   = computed(() => formatMs(cursorMs.value));
const durationLabel = computed(() => formatMs(durationMs.value));

const sliderMin = computed(() => bounds.value?.tMin ?? 0);
const sliderMax = computed(() => bounds.value?.tMax ?? 1);
const sliderStep = computed(() => {
  const b = bounds.value;
  if (!b) return 0.01;
  return Math.max((b.tMax - b.tMin) / 1000, Number.EPSILON);
});

function onScrubInput() {
  if (isPlaying.value) pause();
}

// ── joint summary for the per-track readout ───────────────────────────────────

interface JointReadoutRow {
  jointName: string;
  current: number | null;
  samples: number;
}
const jointReadout = computed<JointReadoutRow[]>(() =>
  props.tracks.map(t => ({
    jointName: t.jointName,
    current:   interpolateAt(t.samples, cursor.value),
    samples:   t.samples.length,
  })),
);

// ── canvas screenshot pass-through ────────────────────────────────────────────

const viewRef = ref<{ captureDataUrl: () => string } | null>(null);
function captureDataUrl(): string {
  return viewRef.value?.captureDataUrl() ?? "";
}
defineExpose({ captureDataUrl });
</script>

<template>
  <div class="urdf-animator">
    <UrdfView
      ref="viewRef"
      :urdf-url="urdfUrl"
      :package-path="packagePath"
      :joint-values="jointValues"
      :label="label"
    />

    <!-- Playback controls — Vuetify v-card with slider + speed picker -->
    <v-card v-if="hasData" variant="outlined" class="mt-3">
      <v-card-title class="text-subtitle-2 d-flex align-center ga-2 pt-3 px-4 pb-1">
        <v-icon size="small" color="primary">mdi-play-circle-outline</v-icon>
        URDF playback
        <v-spacer />
        <v-btn-toggle
          v-model="selectedSpeedIdx"
          mandatory
          density="compact"
          variant="tonal"
        >
          <v-btn :value="0" size="x-small">0.1×</v-btn>
          <v-btn :value="1" size="x-small">0.5×</v-btn>
          <v-btn :value="2" size="x-small">1×</v-btn>
          <v-btn :value="3" size="x-small">2×</v-btn>
          <v-btn :value="4" size="x-small">10×</v-btn>
        </v-btn-toggle>
        <v-btn
          size="x-small"
          variant="tonal"
          :color="isReverse ? 'warning' : undefined"
          prepend-icon="mdi-arrow-left-bold"
          class="ml-1"
          @click="isReverse = !isReverse"
        >
          Reverse
        </v-btn>
      </v-card-title>
      <v-card-text class="d-flex align-center ga-3 py-2 px-4">
        <v-btn
          :icon="isPlaying ? 'mdi-pause' : 'mdi-play'"
          size="small"
          :color="isPlaying ? 'warning' : 'primary'"
          variant="tonal"
          @click="togglePlay"
        />
        <v-btn
          icon="mdi-skip-previous"
          size="small"
          variant="text"
          density="compact"
          @click="reset"
        />
        <v-slider
          v-model="cursor"
          :min="sliderMin"
          :max="sliderMax"
          :step="sliderStep"
          color="primary"
          track-color="grey-darken-3"
          density="compact"
          hide-details
          class="flex-grow-1"
          @update:model-value="onScrubInput"
        />
        <span class="text-caption text-medium-emphasis" style="min-width:96px; text-align:right">
          {{ cursorLabel }} / {{ durationLabel }}
        </span>
      </v-card-text>
    </v-card>

    <!-- Per-joint readout — diagnostic, shown when tracks > 0 -->
    <v-card v-if="jointReadout.length > 0" variant="outlined" class="mt-2">
      <v-card-title class="text-subtitle-2 d-flex align-center ga-2 pt-3 px-4 pb-1">
        <v-icon size="small" color="info">mdi-format-list-numbered</v-icon>
        Joint values at cursor
      </v-card-title>
      <v-table density="compact">
        <thead>
          <tr>
            <th>Joint</th>
            <th>Value (rad / m)</th>
            <th>Samples</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in jointReadout" :key="row.jointName">
            <td><code>{{ row.jointName }}</code></td>
            <td class="text-caption">
              {{ row.current !== null ? row.current.toFixed(4) : "—" }}
            </td>
            <td class="text-caption">{{ row.samples }}</td>
          </tr>
        </tbody>
      </v-table>
    </v-card>

    <v-alert v-if="!hasData" type="info" variant="tonal" density="compact" class="mt-2">
      No joint tracks bound — showing static URDF. Use UrdfJointPanel to set values manually.
    </v-alert>
  </div>
</template>

<style scoped>
.urdf-animator {
  width: 100%;
}
</style>
