<script setup lang="ts">
import {
  AggregateFunction,
  TimeseriesContainerApi,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { TimeseriesSeries } from "~/components/common/chart/types";
import { useFetchTimeseriesContainerStats } from "~/composables/containers/useFetchTimeseriesContainerStats";

const MAX_CHANNELS = 8;

const PALETTE = [
  "#4097CC", "#7ECA8F", "#FCA54D", "#B799DB",
  "#E56874", "#FFD145", "#8C8C8C", "#F06292",
];

const props = defineProps<{
  containerId: number;
  measurements: TimeseriesEntity[];
  /**
   * Optional curated channel selection. Each entry is the 5-tuple key
   * `measurement|device|location|symbolicName|field`. When provided
   * (and non-empty), the chart renders ONLY these channels (in this order,
   * not capped at MAX_CHANNELS — the operator chose them intentionally).
   * Undefined / empty means "show first MAX_CHANNELS in the natural order"
   * (legacy behaviour).
   */
  selectedChannelKeys?: string[];
}>();

function channelKey(ch: TimeseriesEntity): string {
  return [
    ch.measurement ?? "",
    ch.device ?? "",
    ch.location ?? "",
    ch.symbolicName ?? "",
    ch.field ?? "",
  ].join("|");
}

const series = ref<TimeseriesSeries[]>([]);
const loading = ref(false);
const error = ref(false);

// ── Live mode ────────────────────────────────────────────────────────────
// User feedback 2026-05-19: channel overview should support live refresh
// for high-frequency data (home-showcase MQTT, hot-fire DAQs). When live
// mode is on, the chart re-fetches on `liveIntervalMs` and shows the
// last `liveWindowSec` seconds (sliding). Off = legacy "fetch once".
// Pauses on document hidden (avoids burning battery / hammering the
// backend on background tabs).
const liveMode = ref(false);
const liveIntervalMs = ref(5000); // 5 s default
const liveWindowSec = ref(300);   // last 5 min default
const smoothChart = ref(false);   // off by default — step data (switches) looks wrong with spline
const stepChart = ref(false);     // step/switch mode — overrides smooth when on
let liveTimer: ReturnType<typeof setInterval> | null = null;
let liveFetchInFlight = false;    // guard against overlapping fetches at fast refresh rates

// Animation: 60% of the refresh interval, capped at 1500ms so it never outlasts
// the next tick. 0 in static mode — no animation for analytical views.
const chartAnimationMs = computed(() =>
  liveMode.value ? Math.min(Math.floor(liveIntervalMs.value * 0.6), 1500) : 0
);

// Clip the visible window to exactly liveWindowSec seconds in live mode.
// The query fetches 2x the window for anchor points (so the line doesn't
// start mid-air at the left edge), but visually we show only liveWindowSec.
const chartVisibleWindowMs = computed(() =>
  liveMode.value ? liveWindowSec.value * 1000 : undefined
);

// Auto-detect boolean channels: when all values across all series are 0 or 1,
// suggest step mode and dim the smooth toggle (step overrides smooth anyway).
const looksBoolean = computed(() => {
  if (!series.value.length) return false;
  return series.value.every(s =>
    s.data.length > 0 && s.data.every(([_, v]) => v === 0 || v === 1)
  );
});

// TS_STATS1 — ingest rate displayed in live toolbar
const { stats: containerStats, refresh: refreshStats } = useFetchTimeseriesContainerStats(props.containerId);
const ingestMBps = computed(() => {
  if (!containerStats.value || containerStats.value.ingestRateBytesPerSec === 0) return null;
  return (containerStats.value.ingestRateBytesPerSec / 1_048_576).toFixed(3);
});

// User-tunable lists for the v-select dropdowns.
const LIVE_INTERVALS = [
  { value: 100,   title: "100 ms" },
  { value: 1000,  title: "1 s" },
  { value: 5000,  title: "5 s" },
  { value: 30000, title: "30 s" },
];
const LIVE_WINDOWS = [
  { value: 60,    title: "Last 1 min" },
  { value: 300,   title: "Last 5 min" },
  { value: 3600,  title: "Last 1 h" },
  { value: 86400, title: "Last 24 h" },
];

function channelLabel(ch: TimeseriesEntity): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName]
    .filter(Boolean);
  return parts.length ? parts.join(" · ") : `Channel ${ch.id}`;
}

async function fetchChannel(ch: TimeseriesEntity): Promise<Array<[number, number]>> {
  const nowNs = Date.now() * 1e6;
  // Sliding-window start: only in live mode. Otherwise start=0 (legacy
  // "show everything" for static datasets). User feedback 2026-05-19:
  // include a tail of points just OUTSIDE the window so the line
  // doesn't visually start mid-air when the window's leading edge
  // happens to fall between two MQTT samples. Look back 2x the window
  // to catch the most-recent point before the window.
  const startNs = liveMode.value
    ? nowNs - (liveWindowSec.value * 2 * 1_000_000_000)
    : 0;
  // Bucket size: ~120 buckets on the window (smooth-spline-able
  // density without 1-px-per-bucket sparseness). Bucket sized against
  // the visible window, not the fetch span — the extra leading points
  // get the same bucket and slot in cleanly.
  const TARGET_BUCKETS = 120;
  // Allow sub-1s buckets in live mode so short windows (1 min at 100ms refresh)
  // still resolve 120 distinct buckets rather than collapsing to 60 × 1s.
  const bucketNs = liveMode.value
    ? Math.max(200_000_000, Math.floor((liveWindowSec.value * 1_000_000_000) / TARGET_BUCKETS))
    : 1_000_000_000;
  try {
    const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
      timeseriesContainerId: props.containerId,
      measurement: ch.measurement ?? "",
      device: ch.device ?? "",
      location: ch.location ?? "",
      symbolicName: ch.symbolicName ?? "",
      field: ch.field ?? "",
      start: startNs,
      end: nowNs,
      _function: AggregateFunction.Mean,
      groupBy: bucketNs,
    });
    return (result.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
  } catch {
    return [];
  }
}

async function fetchAll(silent = false) {
  if (!props.measurements.length) return;
  // Skip live-mode ticks while a previous fetch is still in-flight.
  // Without this, fast intervals (100 ms) stack requests faster than the
  // backend can respond, which increases perceived latency rather than reducing it.
  if (silent && liveFetchInFlight) return;
  if (silent) liveFetchInFlight = true;
  if (!silent) loading.value = true;
  error.value = false;
  try {
    // When a curated selection exists, render exactly those channels in
    // order. Otherwise fall back to "first MAX_CHANNELS" legacy behaviour.
    const curated = (props.selectedChannelKeys ?? []).filter(k => k.length > 0);
    let channels: TimeseriesEntity[];
    if (curated.length > 0) {
      const byKey = new Map(props.measurements.map(m => [channelKey(m), m]));
      channels = curated
        .map(k => byKey.get(k))
        .filter((m): m is TimeseriesEntity => m != null);
      if (channels.length === 0) {
        // Curated selection is stale (channels deleted / renamed). Fall back.
        channels = props.measurements.slice(0, MAX_CHANNELS);
      }
    } else {
      channels = props.measurements.slice(0, MAX_CHANNELS);
    }
    const points = await Promise.all(channels.map(fetchChannel));
    series.value = channels
      .map((ch, i) => ({
        name: channelLabel(ch),
        data: points[i] ?? [] as Array<[number, number]>,
        color: PALETTE[i % PALETTE.length],
      }))
      .filter((s): s is { name: string; data: Array<[number, number]>; color: string } => s.data.length > 0);
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
    if (silent) liveFetchInFlight = false;
  }
}

watch(() => props.measurements, () => fetchAll(), { immediate: true });
watch(() => props.selectedChannelKeys, () => fetchAll());

// Live-mode lifecycle: arm a setInterval when toggled on, clear when off
// or when the component unmounts. Pause when the tab goes background.
function startLiveTimer() {
  stopLiveTimer();
  liveTimer = setInterval(() => {
    if (typeof document !== "undefined" && document.visibilityState === "hidden") return;
    void fetchAll(true); // silent — keep existing chart visible during refresh
    void refreshStats();
  }, liveIntervalMs.value);
}
function stopLiveTimer() {
  if (liveTimer != null) {
    clearInterval(liveTimer);
    liveTimer = null;
  }
}
watch(liveMode, on => {
  if (on) {
    void fetchAll(); // immediate first tick on toggle-on
    startLiveTimer();
  } else {
    stopLiveTimer();
    void fetchAll(); // re-fetch with start=0 so the chart returns to "all data"
  }
});
watch(liveIntervalMs, () => { if (liveMode.value) startLiveTimer(); });
watch(liveWindowSec, () => { if (liveMode.value) void fetchAll(); });

if (typeof document !== "undefined") {
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && liveMode.value) void fetchAll();
  });
}

onBeforeUnmount(() => {
  stopLiveTimer();
});
</script>

<template>
  <div>
    <div v-if="loading" class="d-flex justify-center pa-8">
      <v-progress-circular indeterminate />
    </div>
    <div
      v-else-if="error"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      Could not load channel data.
    </div>
    <div
      v-else-if="!measurements.length"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      No channels in this container yet.
    </div>
    <div v-else>
      <!-- Live-mode toolbar — always visible once channels exist so
           live-feed containers (MQTT, hot-fire DAQ) don't mislead users
           with "upload a CSV" when they just need to enable live mode. -->
      <div class="d-flex flex-wrap align-center ga-3 mb-2 px-2">
        <v-switch
          v-model="liveMode"
          color="primary"
          density="compact"
          hide-details
          inset
          :label="liveMode ? 'Live' : 'Live mode off'"
        />
        <template v-if="liveMode">
          <v-select
            v-model="liveIntervalMs"
            :items="LIVE_INTERVALS"
            item-title="title"
            item-value="value"
            density="compact"
            variant="outlined"
            hide-details
            label="Refresh"
            style="max-width: 140px"
          />
          <v-select
            v-model="liveWindowSec"
            :items="LIVE_WINDOWS"
            item-title="title"
            item-value="value"
            density="compact"
            variant="outlined"
            hide-details
            label="Window"
            style="max-width: 160px"
          />
          <v-chip
            size="x-small"
            color="success"
            variant="tonal"
            class="ms-2"
          >
            <v-icon size="x-small" start>mdi-circle</v-icon>
            updating every {{ liveIntervalMs >= 1000 ? liveIntervalMs / 1000 + "s" : liveIntervalMs + "ms" }}
          </v-chip>
          <v-chip
            v-if="ingestMBps != null"
            size="x-small"
            color="info"
            variant="tonal"
            class="ms-1"
          >
            <v-icon size="x-small" start>mdi-upload-network-outline</v-icon>
            {{ ingestMBps }} MB/s
          </v-chip>
          <v-btn
            :color="stepChart ? 'primary' : undefined"
            :variant="stepChart ? 'tonal' : 'text'"
            size="x-small"
            class="ms-1"
            :title="looksBoolean && !stepChart ? 'Looks like switch/boolean data — Step mode recommended' : 'Toggle step-line mode (ON/OFF axis, no interpolation)'"
            @click="stepChart = !stepChart; if (stepChart) smoothChart = false"
          >
            <v-icon size="x-small" start>mdi-stairs</v-icon>
            Step{{ looksBoolean && !stepChart ? " ←" : "" }}
          </v-btn>
          <v-btn
            :color="smoothChart && !stepChart ? 'primary' : undefined"
            :variant="smoothChart && !stepChart ? 'tonal' : 'text'"
            :disabled="stepChart"
            size="x-small"
            class="ms-1"
            title="Toggle smooth (spline) interpolation — turn off for step/switch data"
            @click="smoothChart = !smoothChart"
          >
            <v-icon size="x-small" start>mdi-chart-bell-curve-cumulative</v-icon>
            Smooth
          </v-btn>
          <v-tooltip location="top" max-width="320">
            <template #activator="{ props: tipProps }">
              <v-icon
                v-bind="tipProps"
                size="small"
                color="info"
                class="ms-1"
                style="cursor: help"
              >
                mdi-information-outline
              </v-icon>
            </template>
            <div>
              <strong>Live mode notes:</strong>
              <ul class="my-2 ps-4">
                <li>Points are <em>aggregated</em> per bucket (mean over each interval) — not raw samples.</li>
                <li>The leading edge of the line is anchored on a point from <em>outside</em> the visible window — that point is <strong>interpolated</strong> from the most recent real sample. Helps avoid a mid-air line start when data is sparse; flagged here because the value is not at the time you see it.</li>
                <li>Smoothing is cubic-spline; the curve between points is also interpolated.</li>
              </ul>
            </div>
          </v-tooltip>
        </template>
        <v-spacer />
        <div
          v-if="measurements.length > MAX_CHANNELS"
          class="text-caption text-medium-emphasis"
        >
          Showing first {{ MAX_CHANNELS }} of {{ measurements.length }} channels
        </div>
      </div>
      <div
        v-if="!series.length"
        class="pa-4 text-body-2 text-medium-emphasis"
      >
        <template v-if="liveMode">
          Waiting for incoming data…
        </template>
        <template v-else>
          No data points yet. Enable live mode to stream incoming data, or upload a CSV.
        </template>
      </div>
      <TimeseriesChart
        v-else
        :series="series"
        height="300px"
        :show-legend="true"
        :smooth="smoothChart && !stepChart"
        :step="stepChart"
        :animation-duration="chartAnimationMs"
        :visible-window-ms="chartVisibleWindowMs"
      />
    </div>
  </div>
</template>
