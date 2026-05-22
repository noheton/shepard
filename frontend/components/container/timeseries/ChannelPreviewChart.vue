<script setup lang="ts">
import type { TimeseriesEntity } from "@dlr-shepard/backend-client";
import { useFetchChannelPreview } from "~/composables/container/useFetchChannelPreview";
import type { TimeseriesSeries } from "~/components/common/chart/types";

const props = defineProps<{
  channel: TimeseriesEntity;
  containerId: number;
}>();

// LTTB downsampling by default — shape-preserving and orders of magnitude
// lighter on the wire and on the chart renderer than the raw series. The
// toggle below lets the user opt back to every sample when fidelity matters
// more than performance.
const useDownsample = ref(true);
const { data, loading, downsampled, refetch } = useFetchChannelPreview(
  props.containerId,
  props.channel,
);

watch(useDownsample, v => refetch(v));

const series = computed<TimeseriesSeries[]>(() => {
  if (!data.value.length) return [];
  const label = [props.channel.device, props.channel.field]
    .filter(Boolean)
    .join(" · ");
  return [{ name: label, data: data.value, color: "#4097CC" }];
});
</script>

<template>
  <div class="channel-preview pa-2">
    <div class="d-flex align-center justify-end mb-1 mr-1" style="gap: 8px">
      <v-chip
        v-if="downsampled && !loading"
        size="x-small"
        color="primary"
        variant="tonal"
        prepend-icon="mdi-chart-bell-curve"
        title="Visual peaks preserved (Largest-Triangle-Three-Buckets). Toggle 'Full' for every sample."
      >
        Downsampled (LTTB)
      </v-chip>
      <v-switch
        v-model="useDownsample"
        :true-value="false"
        :false-value="true"
        density="compact"
        hide-details
        color="primary"
        label="Full"
        title="Fetch every raw sample (heavier, slower, identical visual shape)"
      />
    </div>
    <div v-if="loading" class="d-flex justify-center align-center" style="height:200px">
      <v-progress-circular indeterminate size="24" />
    </div>
    <div
      v-else-if="!series.length"
      class="d-flex justify-center align-center text-body-2 text-medium-emphasis"
      style="height:200px"
    >
      No data available
    </div>
    <TimeseriesChart v-else :series="series" height="200px" />
  </div>
</template>
