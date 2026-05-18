<script setup lang="ts">
import {
  AggregateFunction,
  TimeseriesContainerApi,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { TimeseriesSeries } from "~/components/common/chart/types";

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

function channelLabel(ch: TimeseriesEntity): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName]
    .filter(Boolean);
  return parts.length ? parts.join(" · ") : `Channel ${ch.id}`;
}

async function fetchChannel(ch: TimeseriesEntity): Promise<Array<[number, number]>> {
  const endNs = Date.now() * 1e6;
  try {
    const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
      timeseriesContainerId: props.containerId,
      measurement: ch.measurement ?? "",
      device: ch.device ?? "",
      location: ch.location ?? "",
      symbolicName: ch.symbolicName ?? "",
      field: ch.field ?? "",
      start: 0,
      end: endNs,
      _function: AggregateFunction.Mean,
      groupBy: 1_000_000_000,
    });
    return (result.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
  } catch {
    return [];
  }
}

async function fetchAll() {
  if (!props.measurements.length) return;
  loading.value = true;
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
  }
}

watch(() => props.measurements, fetchAll, { immediate: true });
watch(() => props.selectedChannelKeys, fetchAll);
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
    <div
      v-else-if="!series.length"
      class="pa-4 text-body-2 text-medium-emphasis"
    >
      Channels have no data points yet. Upload a CSV to get started.
    </div>
    <div v-else>
      <div
        v-if="measurements.length > MAX_CHANNELS"
        class="text-caption text-medium-emphasis mb-2 px-2"
      >
        Showing first {{ MAX_CHANNELS }} of {{ measurements.length }} channels — expand rows below to preview individual channels.
      </div>
      <TimeseriesChart :series="series" height="300px" :show-legend="true" />
    </div>
  </div>
</template>
